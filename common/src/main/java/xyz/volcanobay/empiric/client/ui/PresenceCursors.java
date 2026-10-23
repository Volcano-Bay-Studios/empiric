package xyz.volcanobay.empiric.client.ui;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.ui.UiScreenType;
import xyz.volcanobay.empiric.api.ui.surface.WorldSurface;
import xyz.volcanobay.empiric.client.ui.surface.SurfaceInput;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.ui.UiCanvas;
import xyz.volcanobay.empiric.core.ui.UiPoint;
import xyz.volcanobay.empiric.core.ui.UiRoot;
import xyz.volcanobay.empiric.core.ui.Widget;
import xyz.volcanobay.empiric.networking.packet.UiPointerC2SPacket;
import xyz.volcanobay.empiric.networking.packet.UiPointerS2CPacket;
import xyz.volcanobay.empiric.system.UiSystem;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Other players' pointers on screens with presence. The local pointer is sent relative to the screen's content, so it
 * lands on the same widget for everyone whatever their window size, and whether they see the screen in a window or on
 * a world surface.
 */
public final class PresenceCursors {

    private static final int SEND_INTERVAL = 2;
    private static final int KEEP_ALIVE = 20;
    private static final int EXPIRE = 60;
    /**
     * How fast a shown cursor closes the gap to its latest position, per second.
     */
    private static final double FOLLOW = 18;

    private static final Map<Key, Map<Integer, Cursor>> CURSORS = new HashMap<>();
    private static Level level;
    private static long ticks;
    private static Key sent;
    private static float sentX;
    private static float sentY;
    private static long sentAt;

    private record Key(UiScreenType<?> screen, Module<?> module) {
    }

    private record Pointer(Key key, float x, float y) {
    }

    private static final class Cursor {
        private final String name;
        private final int color;
        private float x;
        private float y;
        private float shownX;
        private float shownY;
        private long seen;
        private long drawn;

        private Cursor(int player, String name, float x, float y) {
            this.name = name;
            this.color = 0xFF000000 | Mth.hsvToRgb(Mth.frac(player * 0.618034f), 0.55f, 1);
            this.shownX = x;
            this.shownY = y;
        }
    }

    private PresenceCursors() {
    }

    public static void clear() {
        CURSORS.clear();
        sent = null;
    }

    /**
     * Whether anyone else's pointer is on this screen, so a surface showing it should keep redrawing.
     */
    public static boolean any(UiScreenType<?> screen, Module<?> module) {
        Map<Integer, Cursor> cursors = CURSORS.get(new Key(screen, module));
        return cursors != null && !cursors.isEmpty();
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != level) {
            level = minecraft.level;
            clear();
        }
        ticks++;
        expire();
        Pointer pointer = current(minecraft);
        if (sent != null && (pointer == null || !pointer.key().equals(sent))) {
            send(sent, Float.NaN, Float.NaN);
            sent = null;
        }
        if (pointer == null) {
            return;
        }
        boolean moved = sent == null || Math.abs(pointer.x() - sentX) >= 0.5f || Math.abs(pointer.y() - sentY) >= 0.5f;
        if ((moved && ticks - sentAt >= SEND_INTERVAL) || ticks - sentAt >= KEEP_ALIVE) {
            send(pointer.key(), pointer.x(), pointer.y());
            sent = pointer.key();
            sentX = pointer.x();
            sentY = pointer.y();
            sentAt = ticks;
        }
    }

    private static void expire() {
        Iterator<Map<Integer, Cursor>> screens = CURSORS.values().iterator();
        while (screens.hasNext()) {
            Map<Integer, Cursor> cursors = screens.next();
            cursors.values().removeIf(cursor -> ticks - cursor.seen > EXPIRE);
            if (cursors.isEmpty()) {
                screens.remove();
            }
        }
    }

    private static Pointer current(Minecraft minecraft) {
        if (minecraft.screen instanceof UiScreenHost host && host.root() != null) {
            return pointer(host.screenType(), host.module(), host.root());
        }
        if (minecraft.screen instanceof UiMenuScreen screen) {
            return pointer(screen.getMenu().screenType(), screen.getMenu().module(), screen.getMenu().root());
        }
        if (minecraft.screen != null) {
            return null;
        }
        WorldSurface surface = SurfaceInput.pointed();
        if (surface == null) {
            return null;
        }
        return pointer(surface.screen(), surface.root().source(), surface.root());
    }

    private static Pointer pointer(UiScreenType<?> screen, Module<?> module, UiRoot root) {
        if (screen == null || module == null || !screen.presence() || !module.isAttached()) {
            return null;
        }
        UiPoint local = root.content().toLocal(root.pointerX(), root.pointerY());
        return new Pointer(new Key(screen, module), (float) local.x(), (float) local.y());
    }

    private static void send(Key key, float x, float y) {
        if (Minecraft.getInstance().getConnection() == null || !key.module().isAttached()) {
            return;
        }
        PacketTarget.server().send(new UiPointerC2SPacket(UiSystem.encode(key.screen(), key.module()), x, y));
    }

    public static void handle(UiPointerS2CPacket packet) {
        UiSystem.Target target = UiSystem.decode(packet.data(), Side.CLIENT);
        if (target == null) {
            return;
        }
        Key key = new Key(target.screen(), target.module());
        if (Float.isNaN(packet.x()) || Float.isNaN(packet.y())) {
            Map<Integer, Cursor> cursors = CURSORS.get(key);
            if (cursors != null) {
                cursors.remove(packet.player());
            }
            return;
        }
        Cursor cursor = CURSORS.computeIfAbsent(key, ignored -> new HashMap<>())
                .computeIfAbsent(packet.player(), player -> new Cursor(player, packet.name(), packet.x(), packet.y()));
        cursor.x = packet.x();
        cursor.y = packet.y();
        cursor.seen = ticks;
    }

    /**
     * Draws the other pointers on a screen over everything else in its root.
     */
    public static void render(UiCanvas canvas, UiScreenType<?> screen, Module<?> module, UiRoot root) {
        if (screen == null || !screen.presence()) {
            return;
        }
        Map<Integer, Cursor> cursors = CURSORS.get(new Key(screen, module));
        if (cursors == null || cursors.isEmpty()) {
            return;
        }
        canvas.overlay();
        long now = Util.getMillis();
        Widget content = root.content();
        for (Cursor cursor : cursors.values()) {
            double seconds = cursor.drawn == 0 ? 0 : (now - cursor.drawn) / 1000.0;
            float follow = (float) (1 - Math.exp(-FOLLOW * seconds));
            cursor.shownX += (cursor.x - cursor.shownX) * follow;
            cursor.shownY += (cursor.y - cursor.shownY) * follow;
            cursor.drawn = now;
            UiPoint point = content.toRoot(cursor.shownX, cursor.shownY);
            draw(canvas, cursor, (float) point.x(), (float) point.y());
        }
    }

    private static void draw(UiCanvas canvas, Cursor cursor, float x, float y) {
        int shadow = 0xC0000000;
        canvas.quad(x - 1, y - 2, x - 1, y + 11, x + 3, y + 8, x + 8.5f, y + 8, shadow, shadow, shadow, shadow);
        canvas.nextLayer();
        canvas.quad(x, y, x, y + 9, x + 3, y + 6.5f, x + 6.5f, y + 6.5f, cursor.color, cursor.color, cursor.color, cursor.color);
        int left = Math.round(x) + 6;
        int top = Math.round(y) + 9;
        int width = canvas.font().width(cursor.name);
        int height = canvas.font().lineHeight();
        canvas.fill(left - 2, top - 1, left + width + 2, top + height, shadow);
        canvas.nextLayer();
        canvas.text(cursor.name, left, top, cursor.color, false);
    }
}

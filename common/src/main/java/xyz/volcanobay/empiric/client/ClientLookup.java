package xyz.volcanobay.empiric.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

import java.util.function.Consumer;

public final class ClientLookup {

    private ClientLookup() {
    }

    public static Level level() {
        return Minecraft.getInstance().level;
    }

    public static HitResult hitResult() {
        return Minecraft.getInstance().hitResult;
    }

    public static Player player() {
        return Minecraft.getInstance().player;
    }

    public static void blockChanged(BlockPos pos) {
        Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    public static void forEachEntity(Consumer<Entity> action) {
        if (Minecraft.getInstance().level != null) {
            Minecraft.getInstance().level.entitiesForRendering().forEach(action);
        }
    }
}

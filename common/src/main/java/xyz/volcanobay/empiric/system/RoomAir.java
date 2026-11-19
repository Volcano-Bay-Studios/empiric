package xyz.volcanobay.empiric.system;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.core.spatial.ManagedSpatialNetwork.SpatialPos;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Which air cells belong to a sealed atmosphere volume, as one bit mask per temperature section. The solver leaves the faces that touch such air to
 * {@link AtmosphereThermalSystem}, which exchanges the room's heat with the walls, so they aren't also given the open air surface loss.
 * The masks of a level are built from the members of its sealed {@link AtmosphereNetwork}s and are rebuilt after any of them changes.
 */
final class RoomAir {

    private static final Map<Level, Masks> CACHE = new WeakHashMap<>();
    private static int version;
    private static boolean enabled = true;

    private RoomAir() {
    }

    /**
     * To be called whenever an atmosphere network changes its members.
     */
    static void changed() {
        version++;
    }

    static void setEnabled(boolean value) {
        enabled = value;
        version++;
    }

    private static long key(int chunkX, int chunkZ, int section) {
        return ((long) chunkX & 0x3FFFFFFL) << 38 | ((long) chunkZ & 0x3FFFFFFL) << 12 | section;
    }

    /**
     * The room air cells of a section by cell index, or null if the section has none.
     */
    static long[] mask(ServerLevel level, int chunkX, int chunkZ, int section) {
        TemperatureSystem.guardCompute("RoomAir.mask");
        if (!enabled || !AtmosphereSystem.ATMOSPHERE.isEnabled()) {
            return null;
        }
        Masks masks = CACHE.get(level);
        if (masks == null || masks.version != version) {
            masks = build(level);
            CACHE.put(level, masks);
        }
        return masks.sections.isEmpty() ? null : masks.sections.get(key(chunkX, chunkZ, section));
    }

    private static Masks build(ServerLevel level) {
        Masks masks = new Masks(version);
        SpatialNetworkMap<AtmosphereNetwork> map = SpatialNetworkSystem.map(level, AtmosphereSystem.ATMOSPHERE);
        for (NetworkAttachment attachment : map.attachments()) {
            if (attachment.id() < 0) {
                continue;
            }
            AtmosphereNetwork network = attachment.modules().get(AtmosphereSystem.ATMOSPHERE.moduleType());
            if (network == null) {
                continue;
            }
            for (SpatialPos member : network.members()) {
                int chunkX = (int) Math.floorDiv(member.x(), 32L);
                int chunkZ = (int) Math.floorDiv(member.z(), 32L);
                int section = level.getSectionIndex((int) Math.floorDiv(member.y(), 2L));
                long[] mask = masks.sections.computeIfAbsent(key(chunkX, chunkZ, section), k -> new long[TemperatureModule.CELLS >> 6]);
                int index = TemperatureSection.indexOf(member.x(), member.y(), member.z());
                mask[index >> 6] |= 1L << index;
            }
        }
        return masks;
    }

    private static final class Masks {

        final int version;
        final Long2ObjectOpenHashMap<long[]> sections = new Long2ObjectOpenHashMap<>();

        Masks(int version) {
            this.version = version;
        }
    }
}

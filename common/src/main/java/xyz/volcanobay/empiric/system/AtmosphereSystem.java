package xyz.volcanobay.empiric.system;

import xyz.volcanobay.empiric.api.module.ItemModuleType;
import xyz.volcanobay.empiric.api.module.EntityModuleType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.EmpiricApi;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.module.BloodstreamModule;
import xyz.volcanobay.empiric.api.atmosphere.module.ItemVolumeModule;
import xyz.volcanobay.empiric.api.atmosphere.module.VolumeNetwork;
import xyz.volcanobay.empiric.api.atmosphere.LevelAtmosphere;
import xyz.volcanobay.empiric.api.data.DimensionDataType;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.entry.DataEntrySystem;
import xyz.volcanobay.empiric.core.module.ModuleSystem;

public final class AtmosphereSystem {

    public static final SpatialNetworkType<VolumeNetwork> VOLUME = new SpatialNetworkType<>(Empiric.id("volume"), VolumeNetwork.class, VolumeNetwork::new);
    /**
     * The air of each dimension, which also sets the dimension's ambient temperature.
     */
    public static final DimensionDataType<LevelAtmosphere> LEVEL_ATMOSPHERE = new DimensionDataType<>(Empiric.id("level_atmosphere"), LevelAtmosphere.class, LevelAtmosphere::new);
    public static final SpatialNetworkType<AtmosphereNetwork> ATMOSPHERE = new SpatialNetworkType<>(Empiric.id("atmosphere"), AtmosphereNetwork.class, AtmosphereNetwork::new) {
        @Override
        public boolean allowDefaultNetwork() {
            return true;
        }

        @Override
        public boolean shouldConnect(BlockState firstState, BlockState secondState, Level level, OctLevel octLevel, OctPos first, OctPos second) {
            return firstState.isAir() && secondState.isAir();
        }

        @Override
        public boolean isMember(long x, long y, long z, OctLevel octLevel, Level level) {
            return octLevel.getBlockState(new OctPos(x, y, z)).isAir();
        }
    };
    public static final EntityModuleType<BloodstreamModule> BLOODSTREAM = EntityModuleType.builder(Empiric.id("bloodstream"), BloodstreamModule.class, BloodstreamModule::new)
            .autoAttach(attachment -> attachment.entity() instanceof LivingEntity)
            .requires(VOLUME.moduleType(), ATMOSPHERE.moduleType())
            .build();
    public static final ItemModuleType<ItemVolumeModule> ITEM_VOLUME = ItemModuleType.builder(Empiric.id("item_volume"), ItemVolumeModule.class, ItemVolumeModule::new).build();

    private AtmosphereSystem() {
    }

    public static void registerData(DataEntrySystem data) {
        LEVEL_ATMOSPHERE.register(data);
    }

    public static LevelAtmosphere levelAtmosphere(Level level) {
        return LEVEL_ATMOSPHERE.get(level);
    }

    public static void register(ModuleSystem modules) {
        SpatialNetworkSystem.register(modules, VOLUME);
        SpatialNetworkSystem.register(modules, ATMOSPHERE);
        modules.register(BLOODSTREAM);
        modules.register(ITEM_VOLUME);
    }

    public static void subscribe(String modId) {
        EmpiricApi.subscribe(modId, BLOODSTREAM);
        EmpiricApi.subscribe(modId, ITEM_VOLUME);
    }

    public static void unsubscribe(String modId) {
        EmpiricApi.unsubscribe(modId, BLOODSTREAM);
        EmpiricApi.unsubscribe(modId, ITEM_VOLUME);
    }
}

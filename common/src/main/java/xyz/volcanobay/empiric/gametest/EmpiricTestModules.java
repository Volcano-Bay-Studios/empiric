package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockEntityAttachment;
import xyz.volcanobay.empiric.api.attachment.BlockModule;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ItemStackAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.api.attachment.OctModule;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.module.BlockEntityModuleType;
import xyz.volcanobay.empiric.api.module.BlockModuleType;
import xyz.volcanobay.empiric.api.module.ChunkModuleType;
import xyz.volcanobay.empiric.api.module.EmpiricDropPolicies;
import xyz.volcanobay.empiric.api.module.EntityModuleType;
import xyz.volcanobay.empiric.api.module.ItemModuleType;
import xyz.volcanobay.empiric.api.module.LevelModuleType;
import xyz.volcanobay.empiric.api.module.OctModuleType;
import xyz.volcanobay.empiric.api.module.ServerModuleType;
import xyz.volcanobay.empiric.api.behavior.BlockChangeBehavior;
import xyz.volcanobay.empiric.api.behavior.ItemRenderBehavior;
import xyz.volcanobay.empiric.api.behavior.NetworkBehavior;
import xyz.volcanobay.empiric.api.behavior.ServerTickBehavior;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.plugin.EmpiricPlugin;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;
import xyz.volcanobay.empiric.system.TemperatureSystem;
import xyz.volcanobay.empiric.system.AtmosphereSystem;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class EmpiricTestModules implements EmpiricPlugin {

    public static final String SUBSCRIBER = "empiric_gametest";

    public static BlockEntityModuleType<Ticker> TICKER;
    public static EntityModuleType<Tag> TAG;
    public static ItemModuleType<Count> COUNT;
    public static ChunkModuleType<Note> NOTE;
    public static LevelModuleType<Toggle> TOGGLE;
    public static LevelModuleType<Watcher> WATCHER;
    public static BlockModuleType<Marker> MARKER;
    public static BlockModuleType<Transient> TRANSIENT;
    public static BlockModuleType<GlassMark> GLASS;
    public static OctModuleType<OctMarker> OCT_MARKER;
    public static ItemModuleType<Glow> GLOW;
    public static ChunkModuleType<Dial> DIAL;
    public static LevelModuleType<Toggle> DEPENDENCY;
    public static LevelModuleType<Toggle> DEPENDENT;
    public static EntityModuleType<Tag> KEPT;
    public static EntityModuleType<Tag> MORTAL;
    public static EntityModuleType<Tag> GROUNDED;
    public static EntityModuleType<Tag> POCKET;
    public static EntityModuleType<Tag> SESSION;
    public static ServerModuleType<Global> GLOBAL;
    public static ServerModuleType<Global> SCRATCH;
    public static final SpatialNetworkType<PipeNetwork> PIPES = new SpatialNetworkType<>(ResourceLocation.fromNamespaceAndPath("empiric", "test_pipes"), PipeNetwork.class, PipeNetwork::new);
    public static final SpatialNetworkType<PipeNetwork> PROBES = new SpatialNetworkType<>(ResourceLocation.fromNamespaceAndPath("empiric", "test_probes"), PipeNetwork.class, PipeNetwork::new);

    public static boolean enabled() {
        return System.getProperty("fabric-api.gametest") != null || Boolean.getBoolean("neoforge.gameTestServer");
    }

    @Override
    public void registerSpecs(SpecRegistry specs) {
        specs.fixer(FixedSample.class, 0, data -> data.rename("old", "renamed"));
    }

    @Override
    public void registerModules(ModuleSystem modules) {
        TICKER = modules.register(BlockEntityModuleType.builder(Empiric.id("test_ticker"), Ticker.class, Ticker::new).build());
        TAG = modules.register(EntityModuleType.builder(Empiric.id("test_tag"), Tag.class, Tag::new)
                .autoAttach(attachment -> attachment.entity().getType() == EntityType.PIG)
                .build());
        COUNT = modules.register(ItemModuleType.builder(Empiric.id("test_count"), Count.class, Count::new).build());
        NOTE = modules.register(ChunkModuleType.builder(Empiric.id("test_note"), Note.class, Note::new).build());
        TOGGLE = modules.register(LevelModuleType.builder(Empiric.id("test_toggle"), Toggle.class, Toggle::new).build());
        WATCHER = modules.register(LevelModuleType.builder(Empiric.id("test_watcher"), Watcher.class, Watcher::new)
                .autoAttach()
                .build());
        MARKER = modules.register(BlockModuleType.builder(Empiric.id("test_marker"), Marker.class, Marker::new).build());
        TRANSIENT = modules.register(BlockModuleType.builder(Empiric.id("test_transient"), Transient.class, Transient::new).drop(EmpiricDropPolicies.UNLOAD).build());
        GLASS = modules.register(BlockModuleType.builder(Empiric.id("test_glass"), GlassMark.class, GlassMark::new)
                .autoAttach(attachment -> attachment.state().is(Blocks.GLASS))
                .build());
        OCT_MARKER = modules.register(OctModuleType.builder(Empiric.id("test_oct_marker"), OctMarker.class, OctMarker::new).build());
        GLOW = modules.register(ItemModuleType.builder(Empiric.id("test_glow"), Glow.class, Glow::new).build());
        DIAL = modules.register(ChunkModuleType.builder(Empiric.id("test_dial"), Dial.class, Dial::new).build());
        DEPENDENCY = modules.register(LevelModuleType.builder(Empiric.id("test_dependency"), Toggle.class, Toggle::new).build());
        DEPENDENT = modules.register(LevelModuleType.builder(Empiric.id("test_dependent"), Toggle.class, Toggle::new).requires(DEPENDENCY).build());
        KEPT = modules.register(EntityModuleType.builder(Empiric.id("test_kept"), Tag.class, Tag::new).build());
        MORTAL = modules.register(EntityModuleType.builder(Empiric.id("test_mortal"), Tag.class, Tag::new).drop(EmpiricDropPolicies.DEATH).build());
        GROUNDED = modules.register(EntityModuleType.builder(Empiric.id("test_grounded"), Tag.class, Tag::new).drop(EmpiricDropPolicies.LEVEL_CHANGE).build());
        POCKET = modules.register(EntityModuleType.builder(Empiric.id("test_pocket"), Tag.class, Tag::new).drop(EmpiricDropPolicies.INVENTORY_CLEAR).build());
        SESSION = modules.register(EntityModuleType.builder(Empiric.id("test_session"), Tag.class, Tag::new).drop(EmpiricDropPolicies.UNLOAD).build());
        GLOBAL = modules.register(ServerModuleType.builder(Empiric.id("test_global"), Global.class, Global::new).autoAttach().build());
        SCRATCH = modules.register(ServerModuleType.builder(Empiric.id("test_scratch"), Global.class, Global::new).drop(EmpiricDropPolicies.UNLOAD).build());
        SpatialNetworkSystem.register(modules, PIPES);
        SpatialNetworkSystem.register(modules, PROBES);
        AtmosphereSystem.subscribe(SUBSCRIBER);
        TemperatureSystem.subscribe(SUBSCRIBER);
        for (ModuleType<?, ?> type : new ModuleType<?, ?>[]{TICKER, TAG, COUNT, NOTE, TOGGLE, WATCHER, MARKER, TRANSIENT, GLASS, OCT_MARKER, GLOW, DIAL, KEPT, MORTAL, GROUNDED, POCKET, SESSION, GLOBAL, SCRATCH, PIPES.moduleType()}) {
            modules.subscribe(SUBSCRIBER, type);
        }
    }

    public static class Ticker extends Module<BlockEntityAttachment> implements ServerTickBehavior, NetworkBehavior {

        public boolean detached;

        @Data(watch = true)
        public int ticks;

        @Data
        public List<String> strings = new ArrayList<>();

        @Override
        public void serverTick() {
            ticks++;
        }

        @Override
        protected void onDetach() {
            detached = true;
        }
    }

    public static class Tag extends Module<EntityAttachment> implements NetworkBehavior {

        public boolean detached;

        @Data
        public String note = "fresh";

        @Override
        protected void onDetach() {
            detached = true;
        }
    }

    public static class Count extends Module<ItemStackAttachment> {

        @Data
        public int count;
    }

    public static class Dial extends Module<ChunkAttachment> implements NetworkBehavior {

        @Data(clientMutable = true)
        public int setting;
        @Data
        public int locked;
    }

    public static class Note extends Module<ChunkAttachment> {

        @Data
        public String note = "";
    }

    public static class Global extends Module<ServerAttachment> implements NetworkBehavior {

        @Data
        public int value;
    }

    public static class Toggle extends Module<LevelAttachment> {

        @Data
        public int value;
    }

    public static class Marker extends BlockModule implements ServerTickBehavior, NetworkBehavior {

        public boolean detached;
        public int stateChanges;

        @Data
        public int ticks;
        @Data
        public String label = "";

        @Override
        public void serverTick() {
            ticks++;
        }

        @Override
        public void onStateChanged(BlockState oldState, BlockState newState) {
            stateChanges++;
        }

        @Override
        protected void onDetach() {
            detached = true;
        }
    }

    public static class GlassMark extends BlockModule {
    }

    public static class Transient extends BlockModule {

        @Data
        public int value;
    }

    public static class OctMarker extends OctModule {

        public boolean detached;

        @Data
        public String label = "";

        @Override
        protected void onDetach() {
            detached = true;
        }
    }

    public static class PipeNetwork extends NetworkModule implements NetworkBehavior {

        @Data
        public int size;
        public int merges;
        public int splits;

        @Override
        public void update() {
            size = members().size();
        }

        @Override
        public void onMerged(NetworkModule absorbed) {
            merges++;
        }

        @Override
        public void onSplit(NetworkModule split) {
            splits++;
        }
    }

    public static class Glow extends Module<ItemStackAttachment> implements ItemRenderBehavior {

        @Data
        public float scale = 1.5f;
    }

    public static class Watcher extends Module<LevelAttachment> implements BlockChangeBehavior, NetworkBehavior {

        public final Set<BlockPos> changed = new HashSet<>();

        @Override
        public void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState) {
            changed.add(pos);
        }
    }
}

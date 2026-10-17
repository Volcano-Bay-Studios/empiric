package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import xyz.volcanobay.empiric.api.EmpiricApi;
import xyz.volcanobay.empiric.api.attachment.BlockAttachment;
import xyz.volcanobay.empiric.api.attachment.ChunkAttachment;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.system.AtmosphereSystem;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.VolumeHelper;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.system.TemperatureSystem;
import xyz.volcanobay.empiric.client.ItemModuleCache;
import xyz.volcanobay.empiric.core.data.tree.TreeDataBuffer;
import xyz.volcanobay.empiric.data.JsonNodes;
import com.google.gson.JsonElement;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import xyz.volcanobay.empiric.registry.EmpiricBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import xyz.volcanobay.empiric.api.attachment.EmpiricAttachments;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.module.EntityModuleType;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.Encoding;
import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.data.EmpiricBuffers;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncS2CPacket;
import xyz.volcanobay.empiric.registry.EmpiricDataComponents;
import xyz.volcanobay.empiric.system.BlockModuleSystem;
import xyz.volcanobay.empiric.system.SpatialModuleStore;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import xyz.volcanobay.empiric.core.data.layout.Accessors;
import xyz.volcanobay.empiric.system.DebugSubscriptionSystem;

import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.api.atmosphere.LevelAtmosphere;
import xyz.volcanobay.empiric.system.AtmosphereThermalSystem;
import xyz.volcanobay.empiric.core.data.DataException;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.module.ModuleEvent;
import xyz.volcanobay.empiric.core.module.ModuleEvents;
import xyz.volcanobay.empiric.core.module.SyncedFields;
import xyz.volcanobay.empiric.api.network.NetworkEvents;
import xyz.volcanobay.empiric.system.ModuleSyncSystem;
import xyz.volcanobay.empiric.networking.handler.EmpiricServerPacketHandler;
import xyz.volcanobay.empiric.networking.packet.ModuleSyncC2SPacket;
import xyz.volcanobay.empiric.system.DataSyncSystem;

import net.minecraft.world.level.ChunkPos;
import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;

import java.lang.invoke.MethodHandles;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class EmpiricGameTests {

    private static final String STRUCTURE = "empiric:gametest/empty";

    @GameTestGenerator
    public Collection<TestFunction> tests() {
        return List.of(
                test("data_round_trip", EmpiricGameTests::dataRoundTrip),
                test("block_entity_module", EmpiricGameTests::blockEntityModule),
                test("entity_module", EmpiricGameTests::entityModule),
                test("entity_save_load", EmpiricGameTests::entitySaveLoad),
                test("item_stack_module", EmpiricGameTests::itemStackModule),
                test("chunk_module", EmpiricGameTests::chunkModule),
                test("subscription_toggle", EmpiricGameTests::subscriptionToggle),
                test("block_change", EmpiricGameTests::blockChange),
                test("sync_packet", EmpiricGameTests::syncPacket),
                test("block_module", EmpiricGameTests::blockModule),
                test("block_auto_attach", EmpiricGameTests::blockAutoAttach),
                isolated("block_chunk_reload", EmpiricGameTests::blockChunkReload),
                test("oct_place_and_remove", EmpiricGameTests::octPlaceAndRemove),
                test("oct_entity_tick_and_save", EmpiricGameTests::octEntityTickAndSave),
                test("oct_module", EmpiricGameTests::octModule),
                isolated("spatial_network", EmpiricGameTests::spatialNetwork),
                test("oct_multiblock", EmpiricGameTests::octMultiblock),
                isolated("block_transient_unload", EmpiricGameTests::blockTransientUnload),
                test("temperature_conduction", EmpiricGameTests::temperatureConduction),
                test("chemistry_volume", EmpiricGameTests::chemistryVolume),
                test("atmosphere_network", EmpiricGameTests::atmosphereNetwork),
                test("level_atmosphere_data", EmpiricGameTests::levelAtmosphereData),
                test("room_default_atmosphere", EmpiricGameTests::roomDefaultAtmosphere),
                test("room_breach", EmpiricGameTests::roomBreach),
                test("room_breach_into_planet", EmpiricGameTests::roomBreachIntoPlanet),
                test("planet_seal_and_reopen", EmpiricGameTests::planetSealAndReopen),
                test("room_merge_keeps_air", EmpiricGameTests::roomMergeKeepsAir),
                test("room_split_shares_air", EmpiricGameTests::roomSplitSharesAir),
                isolated("room_heat_exchange", EmpiricGameTests::roomHeatExchange),
                test("bloodstream_and_items", EmpiricGameTests::bloodstreamAndItems),
                test("block_data", EmpiricGameTests::blockData),
                test("json_specs", EmpiricGameTests::jsonSpecs),
                test("temperature_insulation", EmpiricGameTests::temperatureInsulation),
                test("item_render_lookup", EmpiricGameTests::itemRenderLookup),
                test("generated_accessors", EmpiricGameTests::generatedAccessors),
                test("temperature_octs", EmpiricGameTests::temperatureOcts),
                isolated("debug_commands", EmpiricGameTests::debugCommands),
                isolated("debug_auto_subscribe", EmpiricGameTests::debugAutoSubscribe),
                test("unreadable_module_stays_dormant", EmpiricGameTests::unreadableModule),
                test("temperature_section_sync", EmpiricGameTests::temperatureSectionSync),
                isolated("network_persistence", EmpiricGameTests::networkPersistence),
                test("network_stops_at_unloaded_chunks", EmpiricGameTests::networkUnloadedChunks),
                test("client_mutable_fields", EmpiricGameTests::clientMutableFields),
                isolated("module_dependencies", EmpiricGameTests::moduleDependencies),
                test("json_fixers", EmpiricGameTests::jsonFixers),
                isolated("data_entry_sync", EmpiricGameTests::dataEntrySync),
                test("module_listeners", EmpiricGameTests::moduleListeners),
                isolated("type_listener_subscribes", EmpiricGameTests::typeListenerSubscribes),
                test("custom_module_event", EmpiricGameTests::customModuleEvent),
                test("synced_event", EmpiricGameTests::syncedEvent),
                test("temperature_active_cells", EmpiricGameTests::temperatureActiveCells),
                test("disable_suppresses_mod_subscriptions", EmpiricGameTests::disableSuppresses),
                isolated("network_viewers_dropped", EmpiricGameTests::networkViewersDropped),
                isolated("player_drop_policies", EmpiricGameTests::playerDropPolicies),
                test("entity_level_change", 400, EmpiricGameTests::entityLevelChange),
                isolated("server_module", EmpiricGameTests::serverModule));
    }

    private static TestFunction test(String name, Consumer<GameTestHelper> function) {
        return test(name, 100, function);
    }

    private static TestFunction test(String name, int timeout, Consumer<GameTestHelper> function) {
        return new TestFunction("empiric", "empiric." + name, STRUCTURE, Rotation.NONE, timeout, 0, true, false, 1, 1, false, function);
    }

    private static TestFunction isolated(String name, Consumer<GameTestHelper> function) {
        return new TestFunction("empiric_isolated", "empiric." + name, STRUCTURE, Rotation.NONE, 100, 0, true, false, 1, 1, false, function);
    }

    private static void octPlaceAndRemove(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel octLevel = LevelOctExtension.get(level);
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockState plain = EmpiricTestBlocks.PLAIN_OCT.get().defaultBlockState();
        helper.assertTrue(octLevel.setOctState(absolute, 3, plain), "setting an oct state failed");
        helper.assertTrue(level.getBlockState(absolute).is(EmpiricBlocks.OCT_BLOCK.get()), "placing an oct didn't create the full oct block");
        helper.assertTrue(octLevel.getOctState(absolute, 3) == plain, "oct state wasn't stored");
        helper.assertTrue(octLevel.getOctState(absolute, 0).isAir(), "other octants should stay empty");
        AABB bounds = level.getBlockState(absolute).getShape(level, absolute).bounds();
        helper.assertTrue(bounds.minX >= 0.5 && bounds.minY >= 0.5 && bounds.maxZ <= 0.5, "shape doesn't sit in octant 3: " + bounds);
        octLevel.setOctState(absolute, 4, plain);
        octLevel.setOctState(absolute, 3, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(absolute).is(EmpiricBlocks.OCT_BLOCK.get()), "oct block vanished while an octant was still filled");
        octLevel.setOctState(absolute, 4, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(absolute).isAir(), "emptying every octant didn't remove the oct block");
        helper.succeed();
    }

    private static void octEntityTickAndSave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel octLevel = LevelOctExtension.get(level);
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 1, 3));
        octLevel.setOctState(absolute, 5, EmpiricTestBlocks.TEST_OCT.get().defaultBlockState());
        EmpiricTestBlocks.TestOctEntity entity = (EmpiricTestBlocks.TestOctEntity) octLevel.getOctEntity(absolute, 5);
        helper.assertTrue(entity != null, "ticking oct block didn't create its oct entity");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(entity.ticks >= 3, "oct entity only ticked " + entity.ticks + " times");
            BlockEntity blockEntity = level.getBlockEntity(absolute);
            CompoundTag tag = blockEntity.saveWithFullMetadata(level.registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(absolute, level.getBlockState(absolute), tag, level.registryAccess());
            helper.assertTrue(loaded instanceof FullOctBlockEntity, "saved oct block entity didn't load back");
            helper.assertTrue(((FullOctBlockEntity) loaded).getOctState(5).is(EmpiricTestBlocks.TEST_OCT.get()), "oct state was lost when saving");
            helper.succeed();
        });
    }

    private static void octModule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctLevel octLevel = LevelOctExtension.get(level);
        BlockPos absolute = helper.absolutePos(new BlockPos(2, 1, 1));
        OctPos pos = new OctPos(absolute, 6);
        octLevel.setOctState(absolute, 6, EmpiricTestBlocks.PLAIN_OCT.get().defaultBlockState());
        EmpiricTestModules.OctMarker marker = EmpiricTestModules.OCT_MARKER.attach(level, pos);
        helper.assertTrue(OctAttachment.find(level, pos) == marker.attachment(), "oct spatial lookup didn't find the attachment");
        helper.assertTrue(OctAttachment.find(level, new OctPos(absolute, 7)) == null, "a neighbouring octant shouldn't have an attachment");
        octLevel.setOctState(absolute, 6, EmpiricTestBlocks.TEST_OCT.get().defaultBlockState());
        helper.assertTrue(marker.detached, "replacing the oct didn't detach its module");
        EmpiricTestModules.OctMarker second = EmpiricTestModules.OCT_MARKER.attach(level, pos);
        level.setBlockAndUpdate(absolute, Blocks.STONE.defaultBlockState());
        helper.assertTrue(second.detached, "replacing the whole oct block didn't detach the oct module");
        helper.assertTrue(OctAttachment.find(level, pos) == null, "oct attachment wasn't pruned");
        helper.succeed();
    }

    private static OctPos placePipe(ServerLevel level, OctPos pos) {
        LevelOctExtension.get(level).setOctState(pos.pos(), pos.octant(), EmpiricTestBlocks.PIPE.get().defaultBlockState());
        EmpiricTestBlocks.PIPE.get().place(pos, level);
        return pos;
    }

    private static void spatialNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctPos origin = new OctPos(helper.absolutePos(new BlockPos(1, 1, 1)), 0);
        List<OctPos> line = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            line.add(placePipe(level, origin.relative(Direction.EAST, i)));
        }
        var map = EmpiricTestModules.PIPES.get(level);
        List<String> events = new ArrayList<>();
        EmpiricTestModules.PIPES.listen("pipe_events", NetworkEvents.MERGED, (kept, absorbed) -> events.add("merged"));
        EmpiricTestModules.PIPES.listen("pipe_events", NetworkEvents.SPLIT, (original, split) -> events.add("split"));
        EmpiricTestModules.PIPES.listen("pipe_events", NetworkEvents.UPDATED, (updated, ignored) -> events.add("updated"));
        EmpiricTestModules.PipeNetwork network = map.getNetwork(line.get(0));
        helper.assertTrue(network != null, "pipe line didn't form a network");
        helper.assertTrue(network.size == 5, "network has " + network.size + " members instead of 5");
        helper.assertTrue(map.getNetwork(line.get(4)) == network, "both ends of the pipe should share a network");
        OctPos middle = line.get(2);
        EmpiricTestBlocks.PIPE.get().destroy(middle, level);
        LevelOctExtension.get(level).setOctState(middle.pos(), middle.octant(), Blocks.AIR.defaultBlockState());
        EmpiricTestModules.PipeNetwork left = map.getNetworkSoft(line.get(0).getLongX(), line.get(0).getLongY(), line.get(0).getLongZ());
        EmpiricTestModules.PipeNetwork right = map.getNetworkSoft(line.get(4).getLongX(), line.get(4).getLongY(), line.get(4).getLongZ());
        helper.assertTrue(left != null && right != null && left != right, "breaking the middle pipe didn't split the network");
        helper.assertTrue(left.size == 2 && right.size == 2, "split networks have " + left.size + " and " + right.size + " members");
        helper.assertTrue(left.splits + right.splits >= 1, "split callback wasn't called");
        placePipe(level, middle);
        EmpiricTestModules.PipeNetwork merged = map.getNetworkSoft(line.get(0).getLongX(), line.get(0).getLongY(), line.get(0).getLongZ());
        helper.assertTrue(merged != null && merged == map.getNetworkSoft(line.get(4).getLongX(), line.get(4).getLongY(), line.get(4).getLongZ()), "replacing the middle pipe didn't merge the networks");
        helper.assertTrue(merged.size == 5, "merged network has " + merged.size + " members");
        helper.assertTrue(merged.merges >= 1, "merge callback wasn't called");
        EmpiricTestModules.PIPES.unlisten("pipe_events");
        helper.assertTrue(events.contains("split") && events.contains("merged"), "network listeners saw " + events);
        EmpiricApi.unsubscribe(EmpiricTestModules.SUBSCRIBER, EmpiricTestModules.PIPES.moduleType());
        helper.assertTrue(map.getNetwork(line.get(0)) == null, "an unsubscribed network type still made networks");
        EmpiricApi.subscribe(EmpiricTestModules.SUBSCRIBER, EmpiricTestModules.PIPES.moduleType());
        for (OctPos pos : line) {
            EmpiricTestBlocks.PIPE.get().destroy(pos, level);
            LevelOctExtension.get(level).setOctState(pos.pos(), pos.octant(), Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    private static void octMultiblock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos above = center.above();
        level.setBlockAndUpdate(center, EmpiricTestBlocks.MULTIBLOCK.get().defaultBlockState());
        OctLevel octLevel = LevelOctExtension.get(level);
        helper.assertTrue(level.getBlockState(above).is(EmpiricBlocks.OCT_BLOCK.get()), "multiblock didn't fill the block above with octs");
        for (int octant = 0; octant < 8; octant++) {
            boolean lower = (octant & 2) == 0;
            BlockPos source = OctBlockComplement.getMultiblockCenter(octLevel, above, octant);
            helper.assertTrue(lower == center.equals(source), "octant " + octant + " has the wrong complement");
        }
        AABB bounds = level.getBlockState(above).getShape(level, above).bounds();
        helper.assertTrue(bounds.maxY <= 0.5 + 1.0E-6, "complement shape pokes past the multiblock shape: " + bounds);
        level.setBlockAndUpdate(center, Blocks.AIR.defaultBlockState());
        helper.assertTrue(level.getBlockState(above).isAir(), "removing the multiblock didn't clear its octs");
        helper.succeed();
    }

    private static void blockTransientUnload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        EmpiricTestModules.TRANSIENT.attach(level, absolute).value = 3;
        ChunkAttachment chunk = ChunkAttachment.of(level.getChunkAt(absolute));
        CompoundTag tag = new CompoundTag();
        SpatialModuleStore.saveAll(chunk, tag);
        SpatialModuleStore.unloadAll(chunk);
        chunk.pendingSpatial(SpatialModuleStore.readAll(tag));
        SpatialModuleStore.loadAll(chunk);
        helper.assertTrue(EmpiricTestModules.TRANSIENT.get(level, absolute) == null, "a block module that drops on unload survived the chunk unloading");
        helper.succeed();
    }

    private static void temperatureConduction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hot = helper.absolutePos(new BlockPos(1, 1, 2));
        BlockPos next = hot.east();
        BlockPos oct = next.east();
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        LevelOctExtension.get(level).setOctState(oct, 0, EmpiricTestBlocks.PLAIN_OCT.get().defaultBlockState());
        int ambient = TemperatureSystem.ambient(level);
        TemperatureSystem.set(level, hot, ambient + 250);
        helper.assertTrue(TemperatureSystem.get(level, hot) == ambient + 250, "setting a block's temperature didn't stick");
        helper.succeedWhen(() -> {
            TemperatureSystem.set(level, hot, ambient + 250);
            helper.assertTrue(TemperatureSystem.get(level, next) > ambient, "heat didn't conduct into the next stone block");
            helper.assertTrue(TemperatureSystem.get(level, new OctPos(oct, 0)) > ambient, "heat didn't reach the filled octant");
            helper.assertTrue(TemperatureSystem.get(level, new OctPos(oct, 7)) == ambient, "heat entered an empty octant");
            helper.assertTrue(TemperatureSystem.get(level, hot.above()) == ambient, "heat entered the air");
        });
    }

    private static void chemistryVolume(GameTestHelper helper) {
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        helper.assertTrue(oxygen != null, "the chemistry registry didn't load oxygen");
        AtmosphereVolume volume = new AtmosphereVolume();
        volume.updateVolume(1);
        volume.setTemperature(300);
        volume.addChemical(oxygen, 10, Volume.State.GAS);
        helper.assertTrue(volume.getGasPressure() > 0, "a volume with gas has no pressure");
        AtmosphereVolume other = new AtmosphereVolume();
        other.updateVolume(1);
        volume.transferAmount(other, 4, Volume.State.GAS);
        helper.assertTrue(Math.abs(other.getChemicalAmount(oxygen, Volume.State.GAS) - 4) < 1.0E-6, "transfer moved the wrong amount");
        for (Encoding encoding : Encoding.values()) {
            RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
            EmpiricBuffers.of(buffer).put(volume, encoding);
            AtmosphereVolume read = EmpiricBuffers.of(buffer).take(AtmosphereVolume.class, encoding);
            helper.assertTrue(read.equals(volume), encoding + " round trip changed the volume");
        }
        helper.succeed();
    }

    private static void atmosphereNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    if (x != 2 || y != 2 || z != 2) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
        OctPos inside = new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0);
        AtmosphereNetwork network = AtmosphereSystem.ATMOSPHERE.get(level).getNetwork(inside);
        helper.assertTrue(network != null, "sealed air didn't form an atmosphere network");
        helper.assertTrue(Math.abs(network.getVolume().getVolumeLiters() - 1000) < 1.0E-6, "a one block air pocket should hold 1000 liters, not " + network.getVolume().getVolumeLiters());
        helper.assertTrue(VolumeHelper.getVolumeSoft(level, inside) == network.getVolume(), "VolumeHelper didn't find the atmosphere");
        helper.succeed();
    }

    private static void levelAtmosphereData(GameTestHelper helper) {
        LevelAtmosphere overworld = AtmosphereSystem.LEVEL_ATMOSPHERE.get(Level.OVERWORLD.location());
        LevelAtmosphere nether = AtmosphereSystem.LEVEL_ATMOSPHERE.get(Level.NETHER.location());
        LevelAtmosphere end = AtmosphereSystem.LEVEL_ATMOSPHERE.get(Level.END.location());
        helper.assertTrue(overworld.composition.containsKey("empiric:nitrogen"), "the overworld atmosphere wasn't loaded");
        helper.assertTrue(nether.temperature > overworld.temperature, "the nether should be hotter than the overworld");
        helper.assertTrue(end.temperature < overworld.temperature, "the end should be colder than the overworld");
        helper.assertTrue(TemperatureSystem.ambient(helper.getLevel()) == overworld.ambient(), "ambient temperature didn't come from the level atmosphere");
        helper.succeed();
    }

    private static AtmosphereNetwork sealedRoom(GameTestHelper helper) {
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    if (x != 2 || y != 2 || z != 2) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        return AtmosphereSystem.ATMOSPHERE.get(helper.getLevel()).getNetwork(new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0));
    }

    private static void roomDefaultAtmosphere(GameTestHelper helper) {
        AtmosphereNetwork room = sealedRoom(helper);
        helper.assertTrue(room != null, "sealed air didn't form an atmosphere network");
        LevelAtmosphere atmosphere = AtmosphereSystem.levelAtmosphere(helper.getLevel());
        double pressure = room.getVolume().getGasPressure();
        helper.assertTrue(Math.abs(pressure - atmosphere.pressure * 1000) < atmosphere.pressure * 10, "a new room should start at the level's pressure, got " + pressure + " Pa, " + room.getVolume().getVolumeLiters() + " L, " + room.getVolume().getTemperature() + " K, planet " + room.isWorldAtmosphere() + " id " + room.id());
        helper.assertTrue(Math.abs(room.getVolume().getTemperature() - atmosphere.kelvin()) < 1.0E-6, "a new room should start at the level's temperature");
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        helper.assertTrue(room.getVolume().getChemicalAmount(oxygen, Volume.State.GAS) > 0, "a new room should hold oxygen");
        helper.succeed();
    }

    private static void roomBreach(GameTestHelper helper) {
        var map = AtmosphereSystem.ATMOSPHERE.get(helper.getLevel());
        OctPos inside = new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0);
        AtmosphereNetwork room = sealedRoom(helper);
        helper.assertTrue(room != null && !room.isWorldAtmosphere(), "sealed air didn't form a room");
        int id = room.id();
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
        AtmosphereNetwork breached = map.getNetwork(inside);
        helper.assertTrue(breached != null && breached.isWorldAtmosphere(), "breaking the roof into undiscovered air didn't open the room, got " + (breached == null ? "nothing" : "id " + breached.id()));
        helper.assertTrue(map.existing(id) == null, "the breached room's network is still around");
        helper.succeed();
    }

    private static void roomBreachIntoPlanet(GameTestHelper helper) {
        var map = AtmosphereSystem.ATMOSPHERE.get(helper.getLevel());
        OctPos inside = new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0);
        AtmosphereNetwork room = sealedRoom(helper);
        helper.assertTrue(room != null && !room.isWorldAtmosphere(), "sealed air didn't form a room");
        int id = room.id();
        AtmosphereNetwork planet = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(2, 5, 2)), 0));
        helper.assertTrue(planet != null && planet.isWorldAtmosphere(), "the air above the room isn't the planet atmosphere");
        helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR);
        AtmosphereNetwork breached = map.getNetwork(inside);
        helper.assertTrue(breached == planet, "breaking the roof into the cached planet atmosphere didn't merge the room into it, got " + (breached == null ? "nothing" : "id " + breached.id()));
        helper.assertTrue(map.existing(id) == null, "the breached room's network is still around");
        helper.succeed();
    }

    private static void planetSealAndReopen(GameTestHelper helper) {
        var map = AtmosphereSystem.ATMOSPHERE.get(helper.getLevel());
        BlockPos roof = new BlockPos(2, 3, 2);
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    if ((x != 2 || y != 2 || z != 2) && !roof.equals(new BlockPos(x, y, z))) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    }
                }
            }
        }
        OctPos inside = new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0);
        OctPos above = new OctPos(helper.absolutePos(new BlockPos(2, 5, 2)), 0);
        AtmosphereNetwork planet = map.getNetwork(inside);
        helper.assertTrue(planet != null && planet.isWorldAtmosphere(), "an open room should be part of the planet atmosphere");

        helper.setBlock(roof, Blocks.STONE);
        AtmosphereNetwork room = map.getNetwork(inside);
        helper.assertTrue(room != null && !room.isWorldAtmosphere(), "sealing the roof didn't split a room off the cached planet atmosphere");
        helper.assertTrue(room.members().size() == 8, "the sealed room has " + room.members().size() + " octants instead of 8");
        helper.assertTrue(map.getNetwork(above) == planet, "the air outside left the planet atmosphere");
        LevelAtmosphere atmosphere = AtmosphereSystem.levelAtmosphere(helper.getLevel());
        double pressure = room.getVolume().getGasPressure();
        helper.assertTrue(Math.abs(pressure - atmosphere.pressure * 1000) < atmosphere.pressure * 10, "a room sealed off the planet should start at the level's pressure, got " + pressure + " Pa");

        int id = room.id();
        helper.setBlock(roof, Blocks.AIR);
        helper.assertTrue(map.getNetwork(inside) == planet, "reopening the roof didn't merge the room back into the planet atmosphere");
        helper.assertTrue(map.existing(id) == null, "the reopened room's network is still around");
        helper.succeed();
    }

    /**
     * Fills x 1 to 5, y 1 to 3, z 1 to 3 with stone except for the given air cells.
     */
    private static void stoneShell(GameTestHelper helper, BlockPos... air) {
        List<BlockPos> open = List.of(air);
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!open.contains(pos)) {
                        helper.setBlock(pos, Blocks.STONE);
                    }
                }
            }
        }
    }

    private static void roomMergeKeepsAir(GameTestHelper helper) {
        var map = AtmosphereSystem.ATMOSPHERE.get(helper.getLevel());
        stoneShell(helper, new BlockPos(2, 2, 2), new BlockPos(4, 2, 2));
        AtmosphereNetwork left = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0));
        AtmosphereNetwork right = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(4, 2, 2)), 0));
        helper.assertTrue(left != null && right != null && left != right && !left.isWorldAtmosphere() && !right.isWorldAtmosphere(), "the two cells didn't form separate rooms");
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        left.getVolume().addChemical(oxygen, 5, Volume.State.GAS);
        left.getVolume().setTemperature(400);
        double expected = left.getVolume().getChemicalAmount(oxygen, Volume.State.GAS) + right.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        double low = right.getVolume().getTemperature();
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.AIR);
        AtmosphereNetwork merged = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0));
        helper.assertTrue(merged != null && merged == map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(4, 2, 2)), 0)), "breaking the divider didn't merge the rooms");
        double amount = merged.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        helper.assertTrue(Math.abs(amount - expected) < 1.0E-6, "the merged room holds " + amount + " oxygen instead of " + expected);
        double temperature = merged.getVolume().getTemperature();
        helper.assertTrue(temperature > low + 1 && temperature < 399, "the merged air should mix to between " + low + " K and 400 K, got " + temperature + " K");
        helper.succeed();
    }

    private static void roomSplitSharesAir(GameTestHelper helper) {
        var map = AtmosphereSystem.ATMOSPHERE.get(helper.getLevel());
        stoneShell(helper, new BlockPos(2, 2, 2), new BlockPos(3, 2, 2), new BlockPos(4, 2, 2));
        AtmosphereNetwork room = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0));
        helper.assertTrue(room != null && !room.isWorldAtmosphere() && room.members().size() == 24, "the three cells didn't form one room");
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        room.getVolume().addChemical(oxygen, 6, Volume.State.GAS);
        room.getVolume().setTemperature(350);
        double total = room.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.STONE);
        AtmosphereNetwork left = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0));
        AtmosphereNetwork right = map.getNetwork(new OctPos(helper.absolutePos(new BlockPos(4, 2, 2)), 0));
        helper.assertTrue(left != null && right != null && left != right, "filling the middle cell didn't split the room");
        double leftAmount = left.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        double rightAmount = right.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        helper.assertTrue(Math.abs(leftAmount + rightAmount - total) < 1.0E-6, "the split rooms hold " + (leftAmount + rightAmount) + " oxygen instead of " + total);
        helper.assertTrue(Math.abs(leftAmount - rightAmount) < 1.0E-6, "equal halves got " + leftAmount + " and " + rightAmount + " oxygen");
        helper.assertTrue(left.getVolume().getTemperature() == 350 && right.getVolume().getTemperature() == 350, "the split rooms didn't keep the room's temperature");
        helper.succeed();
    }

    private static void roomHeatExchange(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        AtmosphereNetwork room = sealedRoom(helper);
        helper.assertTrue(room != null, "sealed air didn't form an atmosphere network");
        BlockPos wall = helper.absolutePos(new BlockPos(2, 2, 1));
        int ambient = TemperatureSystem.ambient(level);
        double start = room.getVolume().getTemperature();
        for (int i = 0; i < 300; i++) {
            TemperatureSystem.set(level, wall, ambient + 500);
            AtmosphereThermalSystem.exchange(room);
        }
        helper.assertTrue(room.getVolume().getTemperature() > start + 1, "a heated wall didn't warm the room, " + start + " K to " + room.getVolume().getTemperature() + " K");
        BlockPos opposite = helper.absolutePos(new BlockPos(2, 2, 3));
        helper.assertTrue(TemperatureSystem.get(level, opposite) > ambient, "the warm air didn't pass heat to the opposite wall");
        TemperatureSystem.unsubscribe(EmpiricTestModules.SUBSCRIBER);
        double held = room.getVolume().getTemperature();
        for (int i = 0; i < 50; i++) {
            AtmosphereThermalSystem.exchange(room);
        }
        TemperatureSystem.subscribe(EmpiricTestModules.SUBSCRIBER);
        helper.assertTrue(room.getVolume().getTemperature() == held, "heat moved while the temperature system wasn't subscribed");
        helper.succeed();
    }

    private static void bloodstreamAndItems(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        helper.assertTrue(VolumeHelper.getBloodstreamVolume(pig) != null, "living entity didn't get a bloodstream");
        pig.discard();
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        ItemStack tank = new ItemStack(Items.BUCKET);
        ItemVolume volume = new ItemVolume(10);
        volume.addChemical(oxygen, 2, Volume.State.GAS);
        VolumeHelper.setItemVolume(tank, volume);
        ItemVolume read = VolumeHelper.getItemVolume(tank);
        helper.assertTrue(read != null && Math.abs(read.getVolumeLiters() - 10) < 1.0E-6, "item volume lost its capacity");
        helper.assertTrue(Math.abs(read.getChemicalAmount(oxygen, Volume.State.GAS) - 2) < 1.0E-6, "item volume lost its oxygen");
        helper.succeed();
    }

    private static void blockData(GameTestHelper helper) {
        var data = TemperatureSystem.BLOCK_DATA;
        helper.assertTrue(!data.entries().entries().isEmpty(), "no temperature block data was loaded");
        helper.assertTrue(data.get(Blocks.IRON_BLOCK).conductivity == 1.0, "iron should conduct fully");
        helper.assertTrue(data.get(Blocks.STONE).conductivity == 0.5, "stone should come from the base stone tag");
        helper.assertTrue(data.get(Blocks.WHITE_WOOL).conductivity == 0.02, "wool should insulate, got " + data.get(Blocks.WHITE_WOOL).conductivity);
        helper.assertTrue(data.get(Blocks.SPONGE) == data.defaultValue(), "an unlisted block should use the default");
        helper.succeed();
    }

    private static void jsonSpecs(GameTestHelper helper) {
        SpecSample sample = new SpecSample();
        sample.positions.add(new BlockPos(-7, 64, 12));
        sample.offset.set(1.5, 2, -3);
        sample.tag.putString("key", "value");
        sample.state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST);
        TreeDataBuffer writer = TreeDataBuffer.writer();
        writer.put(sample, Encoding.KEYED);
        JsonElement json = JsonNodes.toJson(writer.result());
        helper.assertTrue(json.getAsJsonObject().get("positions").getAsJsonArray().get(0).getAsJsonArray().size() == 3, "block positions should be written as [x, y, z]: " + json);
        helper.assertTrue(json.getAsJsonObject().get("location").getAsString().equals("minecraft:stone"), "resource locations should be strings: " + json);
        SpecSample read = TreeDataBuffer.reader(JsonNodes.toNode(json)).take(SpecSample.class, Encoding.KEYED);
        helper.assertTrue(EmpiricApi.specs().spec(SpecSample.class).same(sample, read), "json round trip changed the sample: " + json);
        helper.succeed();
    }

    private static void temperatureInsulation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos hot = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.IRON_BLOCK);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.WHITE_WOOL);
        helper.onEachTick(() -> TemperatureSystem.set(level, hot, TemperatureSystem.ambient(level) + 250));
        helper.runAfterDelay(40, () -> {
            int stone = TemperatureSystem.get(level, hot.east());
            int wool = TemperatureSystem.get(level, hot.west());
            helper.assertTrue(stone > wool, "stone (" + stone + ") should have taken more heat than wool (" + wool + ")");
            helper.succeed();
        });
    }

    private static void generatedAccessors(GameTestHelper helper) {
        try {
            Supplier<?> constant = Accessors.bind(Supplier.class, MethodHandles.constant(String.class, "value"));
            helper.assertTrue(constant.getClass().isHidden(), "accessors should be generated hidden classes");
            helper.assertTrue("value".equals(constant.get()), "generated supplier returned the wrong value");
            AccessorTarget target = new AccessorTarget();
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Accessors.LongSetter setter = Accessors.bind(Accessors.LongSetter.class, lookup.findSetter(AccessorTarget.class, "wide", long.class));
            Accessors.LongGetter getter = Accessors.bind(Accessors.LongGetter.class, lookup.findGetter(AccessorTarget.class, "wide", long.class));
            setter.set(target, Long.MIN_VALUE + 7);
            helper.assertTrue(target.wide == Long.MIN_VALUE + 7 && getter.get(target) == target.wide, "generated long accessors didn't round trip");
            Accessors.DoubleSetter doubles = Accessors.bind(Accessors.DoubleSetter.class, lookup.findSetter(AccessorTarget.class, "real", double.class));
            doubles.set(target, -2.5);
            helper.assertTrue(target.real == -2.5, "generated double setter didn't write");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        SpecSample sample = new SpecSample();
        sample.positions.add(new BlockPos(1, 2, 3));
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        EmpiricBuffers.of(buffer).put(sample, Encoding.PACKED);
        SpecSample read = EmpiricBuffers.of(buffer).take(SpecSample.class, Encoding.PACKED);
        helper.assertTrue(EmpiricApi.specs().spec(SpecSample.class).same(sample, read), "a round trip through generated accessors changed the sample");
        helper.succeed();
    }

    private static void temperatureOcts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos block = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        OctPos corner = new OctPos(block, 0);
        int ambient = TemperatureSystem.ambient(level);
        TemperatureSystem.set(level, corner, ambient + 200);
        helper.assertTrue(TemperatureSystem.get(level, corner) == ambient + 200, "a single oct didn't keep its temperature");
        for (int octant = 1; octant < 8; octant++) {
            helper.assertTrue(TemperatureSystem.get(level, new OctPos(block, octant)) == ambient, "heating one oct heated octant " + octant + " too");
        }
        helper.assertTrue(TemperatureSystem.get(level, block) == ambient + 25, "the block average should be an eighth of one oct, got " + TemperatureSystem.get(level, block));
        LevelOctExtension.get(level).setOctState(block.east(), 0, EmpiricTestBlocks.PLAIN_OCT.get().defaultBlockState());
        helper.assertTrue(TemperatureSystem.conductivity(level, new OctPos(block.east(), 0).getLongX(), new OctPos(block.east(), 0).getLongY(), new OctPos(block.east(), 0).getLongZ()) > 0, "a filled oct should conduct");
        helper.assertTrue(TemperatureSystem.conductivity(level, new OctPos(block.east(), 1).getLongX(), new OctPos(block.east(), 1).getLongY(), new OctPos(block.east(), 1).getLongZ()) < 0, "an empty oct in an oct block should count as air");
        helper.succeedWhen(() -> {
            helper.assertTrue(TemperatureSystem.get(level, new OctPos(block, 1)) > ambient, "heat didn't spread to the neighbouring oct inside the block");
        });
    }

    private static int command(GameTestHelper helper, String command) {
        ServerLevel level = helper.getLevel();
        CommandSourceStack source = level.getServer().createCommandSourceStack()
                .withLevel(level)
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 1, 2))))
                .withPermission(4)
                .withSuppressedOutput();
        try {
            return level.getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("/" + command + " failed: " + e.getMessage(), e);
        }
    }

    private static void debugCommands(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        String at = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        String to = (pos.getX() + 1) + " " + (pos.getY() + 1) + " " + (pos.getZ() + 1);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.assertTrue(command(helper, "empiric modules") > 0, "module list was empty");
        helper.assertTrue(command(helper, "empiric modules subscribed") > 0, "no modules showed as subscribed");
        command(helper, "empiric modules empiric:temperature");
        command(helper, "empiric temperature set " + at + " 12.0");
        helper.assertTrue(TemperatureSystem.get(helper.getLevel(), pos) == 120, "temperature set didn't apply");
        command(helper, "empiric temperature add " + at + " 1.0 3");
        helper.assertTrue(TemperatureSystem.get(helper.getLevel(), new OctPos(pos, 3)) == 130, "temperature add on one octant didn't apply");
        command(helper, "empiric temperature get " + at);
        helper.assertTrue(command(helper, "empiric temperature fill " + at + " " + to + " 4.0") == 8, "temperature fill didn't cover 8 blocks");
        helper.assertTrue(command(helper, "empiric temperature chunk " + at) > 0, "temperature chunk found no heat");
        command(helper, "empiric temperature clear " + at);
        helper.assertTrue(TemperatureSystem.get(helper.getLevel(), pos) == TemperatureSystem.ambient(helper.getLevel()), "temperature clear left heat behind");
        command(helper, "empiric network");
        command(helper, "empiric network empiric:test_pipes");
        placePipe(helper.getLevel(), new OctPos(pos.above(2), 0));
        helper.assertTrue(command(helper, "empiric network empiric:test_pipes get " + pos.getX() + " " + (pos.getY() + 2) + " " + pos.getZ()) >= 0, "network get didn't find the pipe");
        command(helper, "empiric network empiric:test_pipes update " + pos.getX() + " " + (pos.getY() + 2) + " " + pos.getZ());
        command(helper, "empiric atmosphere get " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ());
        command(helper, "empiric atmosphere add empiric:oxygen 5 gas at " + pos.getX() + " " + (pos.getY() + 1) + " " + pos.getZ());
        helper.assertTrue(command(helper, "empiric chemistry chemicals") > 0, "no chemicals were listed");
        command(helper, "empiric chemistry reactions");
        helper.assertTrue(command(helper, "empiric data") > 0, "no data entry types were listed");
        helper.assertTrue(command(helper, "empiric data type empiric:temperature") > 0, "no temperature entries were listed");
        command(helper, "empiric data block " + at);
        helper.assertTrue(command(helper, "empiric oct get " + at) == 8, "oct get didn't list 8 octants");
        command(helper, "empiric dump chunk " + at);
        command(helper, "empiric dump level");
        EmpiricTestBlocks.PIPE.get().destroy(new OctPos(pos.above(2), 0), helper.getLevel());
        LevelOctExtension.get(helper.getLevel()).setOctState(pos.above(2), 0, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    private static void debugAutoSubscribe(GameTestHelper helper) {
        var probes = EmpiricTestModules.PROBES.moduleType();
        helper.assertTrue(!probes.isEnabled(), "nothing should be subscribed to the probe network yet");
        command(helper, "empiric network empiric:test_probes");
        helper.assertTrue(probes.isEnabled(), "using a network command didn't subscribe to it");
        helper.assertTrue(EmpiricApi.subscribers(probes).contains(DebugSubscriptionSystem.SUBSCRIBER), "the command subscribed under the wrong id: " + EmpiricApi.subscribers(probes));
        command(helper, "empiric modules empiric:test_probes unsubscribe");
        helper.assertTrue(!probes.isEnabled(), "unsubscribing through the command didn't disable the module");
        command(helper, "empiric modules empiric:test_probes subscribe tester");
        helper.assertTrue(EmpiricApi.subscribers(probes).contains("tester"), "subscribing with a custom id didn't apply");
        command(helper, "empiric modules empiric:test_probes subscribe");
        helper.assertTrue(command(helper, "empiric modules empiric:test_probes disable") == 2, "disable should have removed both subscribers");
        helper.assertTrue(!probes.isEnabled(), "disable left the module enabled");
        helper.succeed();
    }

    private static void unreadableModule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelChunk chunk = level.getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        EmpiricTestModules.DIAL.detach(attachment);
        RegistryFriendlyByteBuf raw = EmpiricBuffers.create();
        DataBuffer buffer = EmpiricBuffers.of(raw);
        byte[] garbage = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        buffer.putVarInt(1);
        buffer.putString(EmpiricTestModules.DIAL.id());
        buffer.putInt(garbage.length);
        buffer.putBytes(garbage, 0, garbage.length);
        ModuleSystem modules = EmpiricApi.modules();
        modules.load(attachment, buffer, modules.specs().context(Encoding.KEYED));
        helper.assertTrue(EmpiricTestModules.DIAL.get(attachment) == null, "an unreadable module shouldn't be attached");
        byte[] kept = attachment.modules().dormant().get(EmpiricTestModules.DIAL.id());
        helper.assertTrue(kept != null && java.util.Arrays.equals(kept, garbage), "the unreadable module's bytes weren't kept");
        boolean threw = false;
        try {
            EmpiricTestModules.DIAL.attach(attachment);
        } catch (DataException e) {
            threw = true;
        }
        helper.assertTrue(threw && attachment.modules().dormant().containsKey(EmpiricTestModules.DIAL.id()), "attaching over unreadable data should fail and keep the data");
        EmpiricTestModules.DIAL.detach(attachment);
        helper.succeed();
    }

    private static void temperatureSectionSync(GameTestHelper helper) {
        short[] values = new short[TemperatureModule.CELLS];
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < values.length; i++) {
            values[i] = (short) (TemperatureModule.MIN + random.nextInt(TemperatureModule.MAX - TemperatureModule.MIN + 1));
        }
        byte[] encoded = TemperatureModule.encode(values);
        helper.assertTrue(encoded.length < ModuleSyncS2CPacket.LIMIT, "a noisy section encoded to " + encoded.length + " bytes");
        helper.assertTrue(java.util.Arrays.equals(TemperatureModule.decode(encoded), values), "noisy section didn't round trip");
        short[] sparse = new short[TemperatureModule.CELLS];
        sparse[100] = 42;
        sparse[101] = -42;
        sparse[102] = TemperatureModule.MAX;
        sparse[103] = TemperatureModule.MIN;
        byte[] small = TemperatureModule.encode(sparse);
        helper.assertTrue(small.length < 32, "a sparse section should be tiny, got " + small.length + " bytes");
        helper.assertTrue(java.util.Arrays.equals(TemperatureModule.decode(small), sparse), "sparse section didn't round trip");
        helper.succeed();
    }

    private static void networkPersistence(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 3; x++) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    if (x != 2 || y != 2 || z != 2) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
        OctPos inside = new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0);
        var map = AtmosphereSystem.ATMOSPHERE.get(level);
        AtmosphereNetwork network = map.getNetwork(inside);
        helper.assertTrue(network != null, "sealed air didn't form an atmosphere network");
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        network.getVolume().addChemical(oxygen, 7, Volume.State.GAS);
        double expected = network.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        var saved = map.save();
        map.unload();
        helper.assertTrue(map.attachments().isEmpty(), "unloading should drop the network modules");
        for (var entry : saved) {
            map.load(entry);
        }
        AtmosphereNetwork restored = map.getNetwork(inside);
        helper.assertTrue(restored != null && restored != network, "the network wasn't restored as a new module");
        double amount = restored.getVolume().getChemicalAmount(oxygen, Volume.State.GAS);
        helper.assertTrue(Math.abs(amount - expected) < 1.0E-6, "the restored network has " + amount + " oxygen instead of " + expected + " (id " + restored.id() + ", " + restored.getVolume().getVolumeLiters() + " L)");
        helper.succeed();
    }

    private static void networkUnloadedChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int far = 1_000_000;
        helper.assertTrue(level.getChunkSource().getChunkNow(far >> 4, far >> 4) == null, "the far chunk was already loaded");
        OctPos pos = new OctPos(new BlockPos(far, 100, far), 0);
        helper.assertTrue(AtmosphereSystem.ATMOSPHERE.get(level).getNetwork(pos) == null, "a network formed in an unloaded chunk");
        helper.assertTrue(level.getChunkSource().getChunkNow(far >> 4, far >> 4) == null, "network discovery loaded a chunk");
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static void clientMutableFields(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        player.teleportTo(origin.getX(), origin.getY(), origin.getZ());
        ChunkAttachment attachment = ChunkAttachment.of(level.getChunkAt(origin));
        EmpiricTestModules.Dial dial = EmpiricTestModules.DIAL.attach(attachment);
        dial.setting = 5;
        dial.locked = 9;
        BitSet both = new BitSet();
        both.set(0, 2);
        ModuleSyncC2SPacket packet = ModuleSyncC2SPacket.of(dial, both);
        dial.setting = 0;
        dial.locked = 0;
        BitSet applied = EmpiricServerPacketHandler.apply(player, packet);
        helper.assertTrue(dial.setting == 5, "the client mutable field wasn't applied");
        helper.assertTrue(dial.locked == 0, "a field that isn't client mutable was changed by the client");
        helper.assertTrue(applied.cardinality() == 1, "only one field should have been applied, got " + applied);
        dial.setting = 0;
        player.teleportTo(origin.getX() + 500, origin.getY(), origin.getZ());
        helper.assertTrue(EmpiricServerPacketHandler.apply(player, packet).isEmpty() && dial.setting == 0, "an out of range player changed the module");
        EmpiricTestModules.DIAL.detach(attachment);
        player.discard();
        helper.succeed();
    }

    private static void moduleDependencies(GameTestHelper helper) {
        var dependency = EmpiricTestModules.DEPENDENCY;
        var dependent = EmpiricTestModules.DEPENDENT;
        helper.assertTrue(!dependency.isEnabled() && !dependent.isEnabled(), "dependency test modules started enabled");
        EmpiricApi.subscribe("tester", dependent);
        helper.assertTrue(dependent.isEnabled() && dependency.isEnabled(), "subscribing didn't enable the dependency");
        helper.assertTrue(EmpiricApi.subscribers(dependency).contains(ModuleSystem.requiredBy(dependent)), "the dependency has the wrong subscriber: " + EmpiricApi.subscribers(dependency));
        command(helper, "empiric modules empiric:test_dependency disable");
        helper.assertTrue(dependency.isEnabled(), "disable removed a dependency that's still required");
        EmpiricApi.unsubscribe("tester", dependent);
        helper.assertTrue(!dependent.isEnabled() && !dependency.isEnabled(), "unsubscribing didn't release the dependency");
        helper.succeed();
    }

    private static void jsonFixers(GameTestHelper helper) {
        DataNode node = DataNode.object();
        node.put("old", DataNode.of(5L));
        FixedSample read = TreeDataBuffer.reader(node).take(FixedSample.class, Encoding.KEYED);
        helper.assertTrue(read.renamed == 5, "the fixer didn't rename the old JSON key, got " + read.renamed);
        DataNode current = DataNode.object();
        current.put("_version", DataNode.of(1L));
        current.put("renamed", DataNode.of(3L));
        helper.assertTrue(TreeDataBuffer.reader(current).take(FixedSample.class, Encoding.KEYED).renamed == 3, "current JSON shouldn't be fixed");
        helper.succeed();
    }

    private static void dataEntrySync(GameTestHelper helper) {
        var packets = DataSyncSystem.packets();
        helper.assertTrue(packets.size() >= 3, "expected block data and both chemistry registries, got " + packets.size());
        int chemicals = ChemicalRegistry.getAllChemicals().size();
        for (var packet : packets) {
            DataSyncSystem.apply(packet);
        }
        helper.assertTrue(TemperatureSystem.BLOCK_DATA.get(Blocks.IRON_BLOCK).conductivity == 1.0, "synced block data lost iron's conductivity");
        helper.assertTrue(TemperatureSystem.BLOCK_DATA.get(Blocks.WHITE_WOOL).conductivity == 0.02, "synced block data lost the wool tag rule");
        helper.assertTrue(ChemicalRegistry.getAllChemicals().size() == chemicals && ChemicalRegistry.getChemical("empiric:oxygen") != null, "synced chemicals didn't match");
        helper.succeed();
    }

    private static void moduleListeners(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkAttachment attachment = ChunkAttachment.of(level.getChunkAt(helper.absolutePos(new BlockPos(3, 1, 3))));
        List<String> events = new ArrayList<>();
        EmpiricApi.listen(ModuleEvents.ATTACHED, (module, ignored) -> {
            if (module.attachment() == attachment && module.type() == EmpiricTestModules.NOTE) {
                events.add("attached");
            }
        });
        EmpiricApi.listen(ModuleEvents.DETACHED, (module, ignored) -> {
            if (module.attachment() == attachment && module.type() == EmpiricTestModules.NOTE) {
                events.add("detached");
            }
        });
        EmpiricTestModules.NOTE.detach(attachment);
        events.clear();
        EmpiricTestModules.NOTE.attach(attachment);
        EmpiricTestModules.NOTE.detach(attachment);
        helper.assertTrue(events.equals(List.of("attached", "detached")), "listeners saw " + events);
        helper.succeed();
    }

    private static void typeListenerSubscribes(GameTestHelper helper) {
        ChunkAttachment attachment = ChunkAttachment.of(helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1))));
        String owner = ModuleSystem.listenerId("listen_test");
        List<String> events = new ArrayList<>();
        EmpiricTestModules.NOTE.listen("listen_test", ModuleEvents.ATTACHED, (note, ignored) -> {
            if (note.attachment() == attachment) {
                events.add("attached");
            }
        });
        helper.assertTrue(EmpiricApi.subscribers(EmpiricTestModules.NOTE).contains(owner), "listening didn't subscribe " + owner);
        EmpiricTestModules.NOTE.detach(attachment);
        EmpiricTestModules.NOTE.attach(attachment);
        helper.assertTrue(events.equals(List.of("attached")), "the type listener saw " + events);
        helper.assertTrue(EmpiricTestModules.NOTE.unlisten("listen_test"), "unlisten found no listeners");
        Set<String> subscribers = EmpiricApi.subscribers(EmpiricTestModules.NOTE);
        helper.assertTrue(!subscribers.contains(owner), "unlisten left " + owner + " subscribed");
        helper.assertTrue(subscribers.contains(EmpiricTestModules.SUBSCRIBER), "unlisten dropped the mod's own subscription");
        EmpiricTestModules.NOTE.detach(attachment);
        EmpiricTestModules.NOTE.attach(attachment);
        helper.assertTrue(events.size() == 1, "a removed listener still fired");
        helper.succeed();
    }

    private static void customModuleEvent(GameTestHelper helper) {
        ModuleEvent<String> ping = ModuleEvent.create(Empiric.id("test_ping")::toString);
        ChunkAttachment attachment = ChunkAttachment.of(helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1))));
        EmpiricTestModules.Note note = EmpiricTestModules.NOTE.attach(attachment);
        List<String> notes = new ArrayList<>();
        List<String> watchers = new ArrayList<>();
        EmpiricTestModules.NOTE.listen("ping_test", ping, (module, payload) -> notes.add(payload));
        EmpiricTestModules.WATCHER.listen("ping_test", ping, (module, payload) -> watchers.add(payload));
        EmpiricTestModules.NOTE.fire(note, ping, "hello");
        EmpiricTestModules.NOTE.unlisten("ping_test");
        EmpiricTestModules.WATCHER.unlisten("ping_test");
        helper.assertTrue(notes.equals(List.of("hello")), "the note listener saw " + notes);
        helper.assertTrue(watchers.isEmpty(), "a listener on another type saw " + watchers);
        helper.succeed();
    }

    private static void syncedEvent(GameTestHelper helper) {
        LevelAttachment attachment = LevelAttachment.of(helper.getLevel());
        EmpiricTestModules.Watcher watcher = EmpiricTestModules.WATCHER.get(attachment);
        List<SyncedFields> synced = new ArrayList<>();
        EmpiricTestModules.WATCHER.listen("synced_test", ModuleEvents.SYNCED, (module, fields) -> {
            if (module == watcher) {
                synced.add(fields);
            }
        });
        ModuleSyncSystem.apply(ModuleSyncS2CPacket.full(watcher).get(0).data(), Side.SERVER);
        EmpiricTestModules.WATCHER.unlisten("synced_test");
        helper.assertTrue(synced.size() == 1, "applying a sync fired SYNCED " + synced.size() + " times");
        helper.assertTrue(synced.get(0).isFull(), "a full sync reported only " + synced.get(0).fields());
        helper.succeed();
    }

    private static void temperatureActiveCells(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos block = helper.absolutePos(new BlockPos(1, 1, 3));
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.STONE);
        OctPos oct = new OctPos(block, 0);
        int ambient = TemperatureSystem.ambient(level);
        TemperatureSystem.set(level, oct, ambient + 1);
        LevelChunk chunk = level.getChunkAt(block);
        TemperatureModule module = TemperatureSystem.TEMPERATURE.get(chunk);
        helper.assertTrue(module != null, "heating an oct didn't attach the temperature module");
        int section = level.getSectionIndex(block.getY());
        int index = TemperatureModule.index((int) Math.floorMod(oct.getLongX(), 32L), (int) Math.floorMod(oct.getLongY(), 32L), (int) Math.floorMod(oct.getLongZ(), 32L));
        helper.assertTrue((module.active(section)[index >> 6] & (1L << index)) != 0, "a heated oct wasn't marked active");
        TemperatureSystem.set(level, oct, ambient);
        TemperatureSystem.propagate(module);
        helper.assertTrue(module.sections.get(section) == null || (module.active(section)[index >> 6] & (1L << index)) == 0, "a cold oct stayed active");
        helper.succeed();
    }

    private static void disableSuppresses(GameTestHelper helper) {
        var probes = EmpiricTestModules.PROBES.moduleType();
        EmpiricApi.subscribe("some_mod", probes);
        helper.assertTrue(command(helper, "empiric modules empiric:test_probes disable") == 1, "disable should have removed the mod's subscription");
        helper.assertTrue(!probes.isEnabled(), "disable left the module enabled");
        helper.assertTrue(DebugSubscriptionSystem.suppressed(probes).contains("some_mod"), "the mod's removed subscription wasn't recorded");
        command(helper, "empiric modules empiric:test_probes subscribe some_mod");
        helper.assertTrue(probes.isEnabled() && DebugSubscriptionSystem.suppressed(probes).isEmpty(), "subscribing again didn't restore the mod");
        EmpiricApi.unsubscribe("some_mod", probes);
        helper.succeed();
    }

    @SuppressWarnings("removal")
    private static void networkViewersDropped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        OctPos pipe = placePipe(level, new OctPos(helper.absolutePos(new BlockPos(2, 2, 2)), 0));
        var map = EmpiricTestModules.PIPES.get(level);
        EmpiricTestModules.PipeNetwork network = map.getNetwork(pipe);
        helper.assertTrue(network != null, "the pipe didn't form a network");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NetworkAttachment attachment = network.attachment();
        attachment.addViewer(player);
        SpatialNetworkSystem.chunkDropped(player, new ChunkPos(pipe.pos()));
        helper.assertTrue(attachment.isViewer(player), "viewers should only be checked at the end of the tick");
        SpatialNetworkSystem.processDropped();
        helper.assertTrue(!attachment.isViewer(player), "a player tracking none of the network's chunks stayed a viewer");
        EmpiricTestBlocks.PIPE.get().destroy(pipe, level);
        LevelOctExtension.get(level).setOctState(pipe.pos(), pipe.octant(), Blocks.AIR.defaultBlockState());
        player.discard();
        helper.succeed();
    }

    public static class AccessorTarget {
        private long wide;
        private double real;
    }

    private static void itemRenderLookup(GameTestHelper helper) {
        ItemStack plain = new ItemStack(Items.STICK);
        helper.assertTrue(ItemModuleCache.renderers(plain).isEmpty(), "a stack without modules shouldn't have renderers");
        ItemStack glowing = new ItemStack(Items.STICK);
        EmpiricTestModules.GLOW.modify(glowing, glow -> glow.scale = 2);
        helper.assertTrue(ItemModuleCache.renderers(glowing).size() == 1, "the glow module wasn't found as a renderer");
        helper.assertTrue(ItemModuleCache.renderers(glowing.copy()) == ItemModuleCache.renderers(glowing), "copies should share the cached renderers");
        helper.succeed();
    }

    private static void blockModule(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 3);
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.OAK_STAIRS.defaultBlockState());
        EmpiricTestModules.Marker marker = EmpiricTestModules.MARKER.attach(level, absolute);
        helper.assertTrue(BlockAttachment.find(level, absolute) == marker.attachment(), "spatial lookup didn't find the block attachment");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(marker.ticks >= 2, "block module only ticked " + marker.ticks + " times");
            helper.setBlock(pos, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
            helper.assertTrue(marker.isAttached() && marker.stateChanges == 1, "a state change of the same block should keep the module and notify it");
            helper.setBlock(pos, Blocks.DIRT);
            helper.assertTrue(marker.detached && !marker.isAttached(), "replacing the block didn't detach the module");
            helper.assertTrue(BlockAttachment.find(level, absolute) == null, "empty block attachment wasn't pruned");
            helper.succeed();
        });
    }

    private static void blockAutoAttach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 1, 1);
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.GLASS);
        helper.assertTrue(EmpiricTestModules.GLASS.get(level, absolute) != null, "placing glass didn't auto attach the glass module");
        helper.setBlock(pos, Blocks.AIR);
        helper.assertTrue(EmpiricTestModules.GLASS.get(level, absolute) == null, "removing the glass didn't detach the glass module");
        helper.setBlock(pos, Blocks.STONE);
        helper.assertTrue(BlockAttachment.find(level, absolute) == null, "placing a block without rules created an attachment");
        helper.succeed();
    }

    private static void blockChunkReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, Blocks.STONE);
        EmpiricTestModules.Marker marker = EmpiricTestModules.MARKER.attach(level, absolute);
        marker.label = "kept";
        ChunkAttachment chunk = ChunkAttachment.of(level.getChunkAt(absolute));
        CompoundTag tag = new CompoundTag();
        SpatialModuleStore.saveAll(chunk, tag);
        helper.assertTrue(tag.contains(BlockModuleSystem.STORE.tag()), "chunk save is missing its block modules");
        SpatialModuleStore.unloadAll(chunk);
        helper.assertTrue(!marker.isAttached() && BlockAttachment.find(level, absolute) == null, "unloading the chunk left the block module behind");
        chunk.pendingSpatial(SpatialModuleStore.readAll(tag));
        SpatialModuleStore.loadAll(chunk);
        EmpiricTestModules.Marker loaded = EmpiricTestModules.MARKER.get(level, absolute);
        helper.assertTrue(loaded != null && loaded != marker, "reloading the chunk didn't restore the block module");
        helper.assertTrue("kept".equals(loaded.label), "reloaded block module lost its data");
        EmpiricTestModules.MARKER.detach(level, absolute);
        helper.succeed();
    }

    private static void dataRoundTrip(GameTestHelper helper) {
        SpecSample sample = new SpecSample();
        sample.stack = new ItemStack(Items.IRON_SWORD);
        sample.state = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
        sample.offset.set(1.5, 2, -3);
        sample.path.add(new Vector3d(4, 5, 6));
        sample.tag.putString("key", "value");
        sample.positions.add(new BlockPos(-7, 64, 12));
        sample.motion = new Vec3(0.25, -1, 3);
        Spec<SpecSample> spec = EmpiricApi.specs().spec(SpecSample.class);
        for (Encoding encoding : Encoding.values()) {
            RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
            DataBuffer data = EmpiricBuffers.of(buffer);
            data.put(sample, encoding);
            SpecSample read = data.take(SpecSample.class, encoding);
            helper.assertTrue(spec.same(sample, read), encoding + " round trip changed the sample");
            helper.assertTrue(data.readable() == 0, encoding + " left " + data.readable() + " bytes unread");
        }
        SpecSample existing = new SpecSample();
        Vector3d offset = existing.offset;
        RegistryFriendlyByteBuf buffer = EmpiricBuffers.create();
        EmpiricBuffers.of(buffer).put(sample, Encoding.PACKED);
        EmpiricBuffers.of(buffer).takeInto(existing, Encoding.PACKED);
        helper.assertTrue(existing.offset == offset, "final vector was replaced instead of updated");
        helper.assertTrue(offset.equals(sample.offset), "final vector wasn't updated");
        helper.succeed();
    }

    private static void blockEntityModule(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(blockEntity != null, "chest has no block entity");
        EmpiricTestModules.Ticker ticker = EmpiricTestModules.TICKER.attach(blockEntity);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(ticker.ticks >= 4, "ticker only ticked " + ticker.ticks + " times");
            CompoundTag tag = blockEntity.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.assertTrue(tag.contains(WorldModuleSystem.TAG), "block entity save is missing its modules");
            helper.destroyBlock(pos);
            helper.assertTrue(ticker.detached, "breaking the chest didn't detach the ticker");
            helper.assertTrue(!ticker.isAttached(), "ticker is still attached");
            helper.succeed();
        });
    }

    private static void entityModule(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        EmpiricTestModules.Tag tag = EmpiricTestModules.TAG.get(pig);
        helper.assertTrue(tag != null, "auto attach rule didn't add the tag to a pig");
        pig.discard();
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(tag.detached, "discarding the pig didn't detach the tag");
            helper.assertTrue(!tag.isAttached(), "tag is still attached");
            helper.succeed();
        });
    }

    private static void entitySaveLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        EmpiricTestModules.TAG.get(pig).note = "saved";
        CompoundTag tag = pig.saveWithoutId(new CompoundTag());
        tag.remove("UUID");
        pig.discard();
        Pig copy = EntityType.PIG.create(level);
        copy.load(tag);
        Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 2)));
        copy.setPos(pos.x, pos.y, pos.z);
        level.addFreshEntity(copy);
        EmpiricTestModules.Tag loaded = EmpiricTestModules.TAG.get(copy);
        helper.assertTrue(loaded != null, "loaded pig lost its tag");
        helper.assertTrue("saved".equals(loaded.note), "loaded tag note was " + loaded.note);
        copy.discard();
        helper.succeed();
    }

    private static void itemStackModule(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.STICK);
        EmpiricTestModules.COUNT.modify(stack, count -> count.count = 7);
        helper.assertTrue(EmpiricTestModules.COUNT.get(stack).count == 7, "stack didn't keep its count");
        ItemStack copy = stack.copy();
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, copy), "copied stack doesn't stack with the original");
        EmpiricTestModules.COUNT.modify(copy, count -> count.count = 9);
        helper.assertTrue(EmpiricTestModules.COUNT.get(stack).count == 7, "changing the copy changed the original");
        helper.assertTrue(EmpiricTestModules.COUNT.get(copy).count == 9, "copy didn't keep its own count");
        helper.assertTrue(!ItemStack.isSameItemSameComponents(stack, copy), "different counts still stack together");
        EmpiricTestModules.COUNT.remove(copy);
        helper.assertTrue(!copy.has(EmpiricDataComponents.MODULES.get()), "removing the last module left the component behind");
        helper.succeed();
    }

    private static void chunkModule(GameTestHelper helper) {
        LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(BlockPos.ZERO));
        ChunkAttachment attachment = ChunkAttachment.of(chunk);
        helper.assertTrue(attachment.isActive(), "loaded chunk isn't active");
        EmpiricTestModules.Note note = EmpiricTestModules.NOTE.attach(attachment);
        note.note = "here";
        boolean[] seen = {false};
        EmpiricAttachments.CHUNK.forEachLoaded(Side.SERVER, loaded -> {
            if (loaded == attachment) {
                seen[0] = true;
            }
        });
        helper.assertTrue(seen[0], "chunk isn't listed as loaded");
        CompoundTag tag = new CompoundTag();
        WorldModuleSystem.save(attachment, tag);
        helper.assertTrue(tag.contains(WorldModuleSystem.TAG), "chunk save is missing its modules");
        EmpiricTestModules.NOTE.detach(attachment);
        helper.succeed();
    }

    private static void subscriptionToggle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelAttachment attachment = LevelAttachment.of(level);
        EmpiricTestModules.Toggle toggle = EmpiricTestModules.TOGGLE.attach(attachment);
        toggle.value = 5;
        EmpiricApi.unsubscribe(EmpiricTestModules.SUBSCRIBER, EmpiricTestModules.TOGGLE);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(EmpiricTestModules.TOGGLE.get(attachment) == null, "unsubscribed module is still attached");
            helper.assertTrue(attachment.modules().dormant().containsKey(EmpiricTestModules.TOGGLE.id()), "unsubscribed module didn't keep its data");
            EmpiricApi.subscribe(EmpiricTestModules.SUBSCRIBER, EmpiricTestModules.TOGGLE);
        });
        helper.runAfterDelay(4, () -> {
            EmpiricTestModules.Toggle revived = EmpiricTestModules.TOGGLE.get(attachment);
            helper.assertTrue(revived != null, "resubscribing didn't revive the module");
            helper.assertTrue(revived != toggle && revived.value == 5, "revived module lost its data");
            EmpiricTestModules.TOGGLE.detach(attachment);
            helper.succeed();
        });
    }

    private static void blockChange(GameTestHelper helper) {
        EmpiricTestModules.Watcher watcher = EmpiricTestModules.WATCHER.get(helper.getLevel());
        helper.assertTrue(watcher != null, "watcher wasn't auto attached to the level");
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.STONE);
        helper.assertTrue(watcher.changed.contains(helper.absolutePos(pos)), "watcher didn't see the block change");
        helper.succeed();
    }

    private static void syncPacket(GameTestHelper helper) {
        ModuleSystem modules = EmpiricApi.modules();
        LevelAttachment attachment = LevelAttachment.of(helper.getLevel());
        EmpiricTestModules.Watcher watcher = EmpiricTestModules.WATCHER.get(attachment);
        ModuleSyncS2CPacket packet = ModuleSyncS2CPacket.full(watcher).get(0);
        DataBuffer data = EmpiricBuffers.buffer(packet.data());
        ModuleSystem.Target target = modules.readTarget(data, Side.SERVER, modules.specs().context(Encoding.PACKED));
        helper.assertTrue(target.attachment() == attachment, "sync packet points at the wrong attachment");
        helper.assertTrue(target.type() == EmpiricTestModules.WATCHER, "sync packet points at the wrong module type");
        helper.succeed();
    }

    private static void playerDropPolicies(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<EntityModuleType<EmpiricTestModules.Tag>> types = List.of(EmpiricTestModules.KEPT, EmpiricTestModules.MORTAL, EmpiricTestModules.GROUNDED, EmpiricTestModules.POCKET, EmpiricTestModules.SESSION);
        for (EntityModuleType<EmpiricTestModules.Tag> type : types) {
            type.attach(player).note = "kept";
        }
        player.getInventory().add(new ItemStack(Items.STONE));
        player.getInventory().clearContent();
        helper.assertTrue(EmpiricTestModules.POCKET.get(player) == null, "clearing the inventory didn't drop the module");
        helper.assertTrue(EmpiricTestModules.KEPT.get(player) != null, "clearing the inventory dropped an unrelated module");
        EmpiricTestModules.POCKET.attach(player).note = "kept";

        CompoundTag tag = player.saveWithoutId(new CompoundTag());
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        WorldModuleSystem.read(EntityAttachment.of(pig), tag);
        helper.assertTrue(EmpiricTestModules.KEPT.get(pig) != null && "kept".equals(EmpiricTestModules.KEPT.get(pig).note), "the saved player lost a kept module");
        helper.assertTrue(EmpiricTestModules.SESSION.get(pig) == null, "a module that drops on unload was saved");
        pig.discard();

        ServerPlayer returned = server.getPlayerList().respawn(player, true, Entity.RemovalReason.CHANGED_DIMENSION);
        for (EntityModuleType<EmpiricTestModules.Tag> type : types) {
            EmpiricTestModules.Tag kept = type.get(returned);
            helper.assertTrue(kept != null && "kept".equals(kept.note), type.id() + " didn't survive leaving the end");
        }
        helper.assertTrue(EmpiricTestModules.KEPT.get(player) == null, "the replaced player kept its modules");

        ServerPlayer respawned = server.getPlayerList().respawn(returned, false, Entity.RemovalReason.KILLED);
        helper.assertTrue(EmpiricTestModules.MORTAL.get(respawned) == null, "a module that drops on death survived a respawn");
        for (EntityModuleType<EmpiricTestModules.Tag> type : List.of(EmpiricTestModules.KEPT, EmpiricTestModules.GROUNDED, EmpiricTestModules.POCKET, EmpiricTestModules.SESSION)) {
            helper.assertTrue(type.get(respawned) != null, type.id() + " didn't survive death");
        }

        ServerLevel nether = server.getLevel(Level.NETHER);
        respawned.changeDimension(new DimensionTransition(nether, new Vec3(0.5, 100, 0.5), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
        helper.assertTrue(respawned.level() == nether, "the player didn't change level");
        helper.assertTrue(EmpiricTestModules.GROUNDED.get(respawned) == null, "a module that drops on level change survived one");
        EmpiricTestModules.Tag kept = EmpiricTestModules.KEPT.get(respawned);
        helper.assertTrue(kept != null && "kept".equals(kept.note), "a kept module didn't survive a level change");
        helper.assertTrue(EmpiricTestModules.SESSION.get(respawned) != null, "a level change counted as an unload");
        server.getPlayerList().remove(respawned);
        helper.succeed();
    }

    private static void entityLevelChange(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        EmpiricTestModules.KEPT.attach(pig).note = "kept";
        EmpiricTestModules.GROUNDED.attach(pig);
        EmpiricTestModules.SESSION.attach(pig);
        Pig other = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 2));
        EmpiricTestModules.Tag copy = EmpiricTestModules.KEPT.copy(pig, other);
        helper.assertTrue(copy != null && "kept".equals(copy.note), "copying a module between entities lost its data");
        copy.note = "changed";
        helper.assertTrue("kept".equals(EmpiricTestModules.KEPT.get(pig).note), "a copied module shares state with its source");

        nether.setChunkForced(0, 0, true);
        Entity moved = pig.changeDimension(new DimensionTransition(nether, new Vec3(0.5, 100, 0.5), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
        helper.assertTrue(moved != null && moved != pig, "the pig didn't change level");
        helper.succeedWhen(() -> {
            EmpiricTestModules.Tag kept = EmpiricTestModules.KEPT.get(moved);
            helper.assertTrue(kept != null && "kept".equals(kept.note), "a kept module didn't follow the entity to another level");
            helper.assertTrue(EmpiricTestModules.GROUNDED.get(moved) == null, "a module that drops on level change followed the entity");
            helper.assertTrue(EmpiricTestModules.SESSION.get(moved) != null, "an entity level change counted as an unload");
            moved.discard();
            nether.setChunkForced(0, 0, false);
        });
    }

    private static void serverModule(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        EmpiricTestModules.Global global = EmpiricTestModules.GLOBAL.get(server);
        helper.assertTrue(global != null, "the server module didn't auto attach");
        helper.assertTrue(EmpiricTestModules.GLOBAL.get(helper.getLevel()) == global, "the server module differs by lookup");
        global.value = 7;
        EmpiricTestModules.SCRATCH.attach(server).value = 3;
        ServerAttachment attachment = ServerAttachment.of(Side.SERVER);
        byte[] bytes = WorldModuleSystem.encode(attachment);
        EmpiricTestModules.GLOBAL.detach(server);
        EmpiricTestModules.SCRATCH.detach(server);
        EmpiricCore.modules().load(attachment, EmpiricBuffers.buffer(bytes), EmpiricCore.modules().specs().context(Encoding.KEYED));
        EmpiricTestModules.Global loaded = EmpiricTestModules.GLOBAL.get(server);
        helper.assertTrue(loaded != null && loaded.value == 7, "the server module didn't round trip");
        helper.assertTrue(EmpiricTestModules.SCRATCH.get(server) == null, "a server module that drops on unload was saved");
        EmpiricTestModules.SCRATCH.attach(server);
        command(helper, "empiric dump server");
        helper.assertTrue(command(helper, "empiric drops server unload") == 1, "dropping on unload by command didn't drop the scratch module");
        helper.assertTrue(EmpiricTestModules.GLOBAL.get(server) != null, "dropping on unload by command dropped a kept module");
        helper.succeed();
    }
}

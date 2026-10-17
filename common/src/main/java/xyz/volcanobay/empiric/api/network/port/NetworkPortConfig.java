package xyz.volcanobay.empiric.api.network.port;

import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import xyz.volcanobay.empiric.api.network.SpatialNetworkable;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.*;

import java.lang.Math;
import java.util.HashMap;
import java.util.Map;

public class NetworkPortConfig {
    private final Map<String, Map<SpatialNetworkType<?>, NetworkPort<?>>> networks = new HashMap<>();

    public void addPort(String key, NetworkPort<?> networkPort) {
        networks.computeIfAbsent(key, k -> new HashMap<>()).put(networkPort.getNetworkType(), networkPort);
    }

    public void addPort(String key, int x, int y, int z, Direction direction, SpatialNetworkType<?> networkType) {
        addPort(key, new NetworkPort<>(new Vector3i(x, y, z), direction, networkType));
    }

    public void addPort(String key, Direction direction, SpatialNetworkType<?> holder) {
        addPort(key, 0, 0, 0, direction, holder);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <T extends NetworkModule> NetworkPort<T> getPort(String key, SpatialNetworkType<T> networkType) {
        Map<SpatialNetworkType<?>, NetworkPort<?>> networkTypeNetworkPortMap = networks.get(key);
        if (networkTypeNetworkPortMap != null) {
            return (NetworkPort<T>) networkTypeNetworkPortMap.get(networkType);
        }
        return null;
    }

    public <T extends NetworkModule> T get(Level level, OctPos pos, String key, SpatialNetworkType<T> networkType) {
        NetworkPort<T> port = getPort(key, networkType);
        if (port != null) {
            SpatialNetworkMap<T> networkMap = port.getNetworkType().get(level);
            OctLevel octLevel = LevelOctExtension.get(level);

            BlockState state = octLevel.getBlockState(pos);
            Direction facing = Direction.NORTH;
            if (state.hasProperty(BlockStateProperties.FACING)) {
                facing = state.getValue(DirectionalBlock.FACING);
            }
            OctPos edge = getEdgeOct(level, pos, port);

            Direction relative = rotateRelative(facing, port.getFacing());
            OctPos target = edge.relative(relative);
            BlockState targetState = octLevel.getBlockState(target);
            if (targetState.getBlock() instanceof SpatialNetworkable spatialNetworkable) {
                if (spatialNetworkable.allowPort(targetState, octLevel.getBlockState(edge), level, octLevel, target, edge)) {
                    return networkMap.getNetwork(target.getLongX(), target.getLongY(), target.getLongZ());
                }
            }
        }
        return null;
    }

    public <T extends NetworkModule> Vector3d getEdgePos(Level level, OctPos pos, String key, SpatialNetworkType<T> networkType) {
        NetworkPort<T> port = getPort(key, networkType);
        if (port != null) {
            OctLevel octLevel = LevelOctExtension.get(level);
            BlockState state = octLevel.getBlockState(pos);
            Direction facing = Direction.NORTH;
            OctPos edge = getEdgeOct(level, pos, port);
            if (state.hasProperty(BlockStateProperties.FACING)) {
                facing = state.getValue(DirectionalBlock.FACING);
            }
            Vec3i normal = facing.getNormal();
            return new Vector3d((edge.getLongX() / 2d) + 0.25d, (edge.getLongY() / 2d) + 0.25d, (edge.getLongZ() / 2d) + 0.25d);
        }
        return null;
    }

    private static <T extends NetworkModule> @NotNull OctPos getEdgeOct(Level level, OctPos pos, NetworkPort<T> port) {
        OctLevel octLevel = LevelOctExtension.get(level);

        BlockState state = octLevel.getBlockState(pos);
        Direction facing = Direction.NORTH;
        if (state.hasProperty(BlockStateProperties.FACING)) {
            facing = state.getValue(DirectionalBlock.FACING);
        }
        Direction relative = rotateRelative(facing, port.getFacing());
        Vector3ic edgePos = port.getPos();
        Vector3f dest = new Vector3f();
        relative.getRotation().transform(edgePos.x(), edgePos.y(), edgePos.z(), dest);

        return new OctPos(pos.getLongX() + (long) dest.x, pos.getLongY() + (long) dest.y, pos.getLongZ() + (long) dest.z).relative(relative);
    }

    private static Direction rotateRelative(Direction input, Direction targetDir) {
        Vector3f resultVec = input.step();
        Quaternionf rotation = new Quaternionf();

        switch (targetDir) {
            case SOUTH -> rotation.rotateY((float) Math.PI);
            case EAST -> rotation.rotateY((float) -Math.PI / 2);
            case WEST -> rotation.rotateY((float) Math.PI / 2);
            case UP -> rotation.rotateX((float) Math.PI / 2);
            case DOWN -> rotation.rotateX((float) -Math.PI / 2);
            case NORTH -> {}
        }

        rotation.transform(resultVec);
        return Direction.getNearest(resultVec.x(), resultVec.y(), resultVec.z());
    }
}

package xyz.volcanobay.empiric.api.network.port;

import net.minecraft.core.Direction;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import org.joml.Vector3i;
import org.joml.Vector3ic;

public class NetworkPort<T extends NetworkModule> {
    private final Vector3i pos;
    private final Direction facing;
    private final SpatialNetworkType<T> networkType;

    public NetworkPort(Vector3i pos, Direction facing, SpatialNetworkType<T> networkType) {
        this.pos = pos;
        this.facing = facing;
        this.networkType = networkType;
    }

    public Vector3ic getPos() {
        return pos;
    }

    public Direction getFacing() {
        return facing;
    }

    public SpatialNetworkType<T> getNetworkType() {
        return networkType;
    }
}

package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class OctUtil {

    public static byte getOctant(BlockPos pos, double x, double y, double z) {
        byte oct = 0;
        if (x - pos.getX() - 0.5d > 0.0d) {
            oct |= 1;
        }
        if (y - pos.getY() - 0.5d > 0.0d) {
            oct |= 2;
        }
        if (z - pos.getZ() - 0.5d > 0.0d) {
            oct |= 4;
        }
        return oct;
    }

    public static byte getOctant(BlockPos pos, Vec3 location) {
        byte oct = 0;
        if (location.x - pos.getX() - 0.5d > 0.0d) {
            oct |= 1;
        }
        if (location.y - pos.getY() - 0.5d > 0.0d) {
            oct |= 2;
        }
        if (location.z - pos.getZ() - 0.5d > 0.0d) {
            oct |= 4;
        }
        return oct;
    }

    public static byte getOctant(Vec3 location) {
        byte oct = 0;
        if (location.x - Math.floor(location.x) - 0.5d > 0.0d) {
            oct |= 1;
        }
        if (location.y - Math.floor(location.y) - 0.5d > 0.0d) {
            oct |= 2;
        }
        if (location.z - Math.floor(location.z) - 0.5d > 0.0d) {
            oct |= 4;
        }
        return oct;
    }

    public static byte getOctant(BlockHitResult blockHit) {
        Vec3i direction = blockHit.getDirection().getNormal();
        Vec3 location = blockHit.getLocation();
        return OctUtil.getOctant(
                blockHit.getBlockPos(),
                location.x - direction.getX() * 0.01d,
                location.y - direction.getY() * 0.01d,
                location.z - direction.getZ() * 0.01d
        );
    }

    public static byte getOctant(Vec3 location, Direction normal) {
        Vec3i direction = normal.getNormal();
        return OctUtil.getOctant(
                BlockPos.containing(location.add(-direction.getX() * 0.01f, -direction.getY() * 0.01f, -direction.getZ() * 0.01f)),
                location.x - direction.getX() * 0.01d,
                location.y - direction.getY() * 0.01d,
                location.z - direction.getZ() * 0.01d
        );
    }

    public static BlockPos getBlockPos(Vec3 location, Direction normal) {
        Vec3i direction = normal.getNormal();
        return BlockPos.containing(location.add(-direction.getX() * 0.01f, -direction.getY() * 0.01f, -direction.getZ() * 0.01f));
    }

    public static byte getTargetedOctant(Player player, BlockPos pos) {
        double range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue() + 1;
        HitResult hit = player.pick(range, 1, false);
        Vec3 location = hit.getLocation();
        if (hit instanceof BlockHitResult blockHit) {
            location = location.relative(blockHit.getDirection(), -0.001d);
        }
        return OctUtil.getOctant(pos, location);
    }

    public static Vector3f getLocalPosition(BlockPos position, Vec3 location, int octant) {
        Vector3f localLoc = new Vector3f(
                (float) (location.x - position.getX()),
                (float) (location.y - position.getY()),
                (float) (location.z - position.getZ())
        );
        if ((octant & 1) != 0) {
            localLoc.x -= 0.5f;
        }
        if ((octant & 2) != 0) {
            localLoc.y -= 0.5f;
        }
        if ((octant & 4) != 0) {
            localLoc.z -= 0.5f;
        }
        return localLoc;
    }
}

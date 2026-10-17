package xyz.volcanobay.empiric.api.oct;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.api.oct.OctUtil;

import java.util.stream.IntStream;

public record OctPos(BlockPos pos, byte octant) {
    public static final Codec<OctPos> CODEC = Codec.INT_STREAM
            .comapFlatMap(
                    intStream -> Util.fixedSize(intStream, 4).map(intArray -> new OctPos(new BlockPos(intArray[0], intArray[1], intArray[2]), (byte) intArray[3])),
                    octPos -> IntStream.of(octPos.pos().getX(), octPos.pos().getY(), octPos.pos.getZ(), octPos.octant)
            )
            .stable();
    public static final StreamCodec<ByteBuf, OctPos> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OctPos::pos,
            ByteBufCodecs.BYTE, OctPos::octant,
            OctPos::new
    );

    public OctPos(BlockPos pos, int octant) {
        this(pos, (byte) octant);
    }

    public OctPos(Vec3 pos) {
        this(BlockPos.containing(pos), OctUtil.getOctant(pos));
    }

    public OctPos(BlockHitResult result) {
        this(result.getBlockPos(), OctUtil.getOctant(result));
    }

    public static int longToBlock(long longCoord) {
        int blockCoord = (int) (longCoord / 2);
        return (longCoord < 0 && longCoord % 2 != 0) ? blockCoord - 1 : blockCoord;
    }

    public OctPos(long longX, long longY, long longZ) {
        this(new BlockPos(longToBlock(longX), longToBlock(longY), longToBlock(longZ)),
                (byte) (((longX % 2) & 1) | (((longY % 2) & 1) << 1) | (((longZ % 2) & 1) << 2)));
    }

    public long getLongX() {
        return (long) this.pos.getX() * 2 + ((this.octant & 1) != 0 ? 1 : 0);
    }

    public long getLongY() {
        return (long) this.pos.getY() * 2 + ((this.octant & 2) != 0 ? 1 : 0);
    }

    public long getLongZ() {
        return (long) this.pos.getZ() * 2 + ((this.octant & 4) != 0 ? 1 : 0);
    }

    public OctPos relative(Direction direction) {
        return relative(direction, 1);
    }

    public OctPos relative(Direction direction, int distance) {
        if (distance == 0) {
            return this;
        }
        return new OctPos(
                this.getLongX() + (long) direction.getStepX() * distance,
                this.getLongY() + (long) direction.getStepY() * distance,
                this.getLongZ() + (long) direction.getStepZ() * distance
        );
    }
    public double distSqr(OctPos otherPos) {
        long dx = getLongX() - otherPos.getLongX();
        long dy = getLongY() - otherPos.getLongY();
        long dz = getLongZ() - otherPos.getLongZ();
        return Math.sqrt((double)dx * dx + (double)dy * dy + (double)dz * dz) / 2.0;
    }
    public double distManhattan(OctPos otherPos) {
        long dx = getLongX() - otherPos.getLongX();
        long dy = getLongY() - otherPos.getLongY();
        long dz = getLongZ() - otherPos.getLongZ();
        return (Math.abs(dx)+Math.abs(dy)+Math.abs(dz)) / 2.0;
    }

    public Vec3 sub(OctPos other) {
        return new Vec3(getLongX() - other.getLongX(), getLongY() - other.getLongY(), getLongZ() - other.getLongZ());
    }

    public Direction dir(OctPos other) {
        return Direction.getNearest(getLongX() - other.getLongX(), getLongY() - other.getLongY(), getLongZ() - other.getLongZ());
    }
}

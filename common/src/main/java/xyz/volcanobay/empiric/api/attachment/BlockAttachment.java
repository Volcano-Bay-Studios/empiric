package xyz.volcanobay.empiric.api.attachment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.BlockModuleSystem;
import xyz.volcanobay.empiric.system.SpatialModuleStore;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.function.Consumer;

public final class BlockAttachment extends SpatialAttachment {

    private final BlockPos pos;

    public BlockAttachment(Level level, BlockPos pos) {
        super(level);
        this.pos = pos.immutable();
    }

    public static BlockAttachment get(Level level, BlockPos pos) {
        return BlockModuleSystem.get(level, pos);
    }

    public static BlockAttachment find(Level level, BlockPos pos) {
        return BlockModuleSystem.find(level, pos);
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return level.getBlockState(pos);
    }

    @Override
    public long x() {
        return pos.getX();
    }

    @Override
    public long y() {
        return pos.getY();
    }

    @Override
    public long z() {
        return pos.getZ();
    }

    @Override
    public BlockPos blockPos() {
        return pos;
    }

    @Override
    public SpatialModuleStore<?> store() {
        return BlockModuleSystem.STORE;
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.BLOCK;
    }

    public record Key(Level level, BlockPos pos) {
    }

    public static final class Type extends AttachmentType<BlockAttachment, Key> {

        Type() {
            super("empiric:block");
        }

        @Override
        public BlockAttachment get(Key key) {
            return BlockModuleSystem.get(key.level(), key.pos());
        }

        @Override
        public BlockAttachment find(Key key) {
            return BlockModuleSystem.find(key.level(), key.pos());
        }

        @Override
        public void writeKey(DataBuffer buffer, BlockAttachment attachment, DataContext context) {
            buffer.putSignedVarInt(attachment.pos.getX());
            buffer.putSignedVarInt(attachment.pos.getY());
            buffer.putSignedVarInt(attachment.pos.getZ());
        }

        @Override
        public BlockAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            BlockPos pos = new BlockPos(buffer.takeSignedVarInt(), buffer.takeSignedVarInt(), buffer.takeSignedVarInt());
            Level level = WorldModuleSystem.keyLevel(side);
            if (level == null) {
                return null;
            }
            return side == Side.SERVER ? BlockModuleSystem.find(level, pos) : BlockModuleSystem.get(level, pos);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<BlockAttachment> action) {
            BlockModuleSystem.STORE.forEach(side, action);
        }
    }
}

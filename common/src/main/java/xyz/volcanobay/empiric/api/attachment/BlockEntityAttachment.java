package xyz.volcanobay.empiric.api.attachment;

import foundry.veil.api.network.VeilPacketManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.ext.BlockEntityExtension;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.function.Consumer;

public final class BlockEntityAttachment extends PersistentAttachment {

    private final BlockEntity blockEntity;
    private final ModuleHolder modules = new ModuleHolder(this);

    public BlockEntityAttachment(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    public static BlockEntityAttachment of(BlockEntity blockEntity) {
        return ((BlockEntityExtension) blockEntity).empiric$attachment();
    }

    public BlockEntity blockEntity() {
        return blockEntity;
    }

    @Override
    public Level level() {
        return blockEntity.getLevel();
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.BLOCK_ENTITY;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public VeilPacketManager.PacketSink syncTarget() {
        return VeilPacketManager.tracking(blockEntity);
    }

    @Override
    public Vec3 position() {
        return Vec3.atCenterOf(blockEntity.getBlockPos());
    }

    public static final class Type extends AttachmentType<BlockEntityAttachment, BlockEntity> {

        Type() {
            super("empiric:block_entity");
        }

        @Override
        public BlockEntityAttachment get(BlockEntity blockEntity) {
            return of(blockEntity);
        }

        @Override
        public void writeKey(DataBuffer buffer, BlockEntityAttachment attachment, DataContext context) {
            BlockPos pos = attachment.blockEntity.getBlockPos();
            buffer.putSignedVarInt(pos.getX());
            buffer.putSignedVarInt(pos.getY());
            buffer.putSignedVarInt(pos.getZ());
        }

        @Override
        public BlockEntityAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            BlockPos pos = new BlockPos(buffer.takeSignedVarInt(), buffer.takeSignedVarInt(), buffer.takeSignedVarInt());
            Level level = WorldModuleSystem.keyLevel(side);
            if (level == null || level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                return null;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            return blockEntity == null ? null : of(blockEntity);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<BlockEntityAttachment> action) {
            WorldModuleSystem.forEachBlockEntity(side, action);
        }
    }
}

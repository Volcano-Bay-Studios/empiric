package xyz.volcanobay.empiric.api.attachment;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.attachment.AttachmentType;
import xyz.volcanobay.empiric.core.data.DataBuffer;
import xyz.volcanobay.empiric.core.data.DataContext;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.system.OctModuleSystem;
import xyz.volcanobay.empiric.system.SpatialModuleStore;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.function.Consumer;

public final class OctAttachment extends SpatialAttachment {

    private final OctPos pos;

    public OctAttachment(Level level, OctPos pos) {
        super(level);
        this.pos = pos;
    }

    public static OctAttachment get(Level level, OctPos pos) {
        return OctModuleSystem.get(level, pos);
    }

    public static OctAttachment find(Level level, OctPos pos) {
        return OctModuleSystem.find(level, pos);
    }

    public OctPos pos() {
        return pos;
    }

    public BlockState state() {
        return LevelOctExtension.get(level).getOctState(pos);
    }

    @Override
    public long x() {
        return pos.getLongX();
    }

    @Override
    public long y() {
        return pos.getLongY();
    }

    @Override
    public long z() {
        return pos.getLongZ();
    }

    @Override
    public BlockPos blockPos() {
        return pos.pos();
    }

    @Override
    public SpatialModuleStore<?> store() {
        return OctModuleSystem.STORE;
    }

    @Override
    public AttachmentType<?, ?> attachmentType() {
        return EmpiricAttachments.OCT;
    }

    public record Key(Level level, OctPos pos) {
    }

    public static final class Type extends AttachmentType<OctAttachment, Key> {

        Type() {
            super("empiric:oct");
        }

        @Override
        public OctAttachment get(Key key) {
            return OctModuleSystem.get(key.level(), key.pos());
        }

        @Override
        public OctAttachment find(Key key) {
            return OctModuleSystem.find(key.level(), key.pos());
        }

        @Override
        public void writeKey(DataBuffer buffer, OctAttachment attachment, DataContext context) {
            buffer.putSignedVarLong(attachment.x());
            buffer.putSignedVarLong(attachment.y());
            buffer.putSignedVarLong(attachment.z());
        }

        @Override
        public OctAttachment readKey(DataBuffer buffer, Side side, DataContext context) {
            OctPos pos = new OctPos(buffer.takeSignedVarLong(), buffer.takeSignedVarLong(), buffer.takeSignedVarLong());
            Level level = WorldModuleSystem.keyLevel(side);
            if (level == null) {
                return null;
            }
            return side == Side.SERVER ? OctModuleSystem.find(level, pos) : OctModuleSystem.get(level, pos);
        }

        @Override
        public void forEachLoaded(Side side, Consumer<OctAttachment> action) {
            OctModuleSystem.STORE.forEach(side, action);
        }
    }
}

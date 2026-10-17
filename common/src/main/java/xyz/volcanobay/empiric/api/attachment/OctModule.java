package xyz.volcanobay.empiric.api.attachment;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.module.Module;

public abstract class OctModule extends Module<OctAttachment> {

    public final OctPos pos() {
        return attachment().pos();
    }

    public final Level level() {
        return attachment().level();
    }

    public final BlockState state() {
        return attachment().state();
    }

    public boolean survives(BlockState oldState, BlockState newState) {
        return oldState.is(newState.getBlock());
    }

    public void onStateChanged(BlockState oldState, BlockState newState) {
    }
}

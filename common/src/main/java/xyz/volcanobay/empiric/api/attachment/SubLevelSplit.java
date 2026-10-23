package xyz.volcanobay.empiric.api.attachment;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import xyz.volcanobay.empiric.api.behavior.SplitBehavior;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Blocks Sable split off {@link #from()} into the new sub-level {@link #into()}. Handed to {@link SplitBehavior}s.
 */
public final class SubLevelSplit {

    private final SubLevelAttachment from;
    private final SubLevelAttachment into;
    private final List<BlockPos> blocks;
    private final Set<BlockPos> moved;
    private final BlockPos anchor;
    private final BlockPos target;

    public SubLevelSplit(SubLevelAttachment from, SubLevelAttachment into, List<BlockPos> blocks) {
        this.from = from;
        this.into = into;
        this.blocks = List.copyOf(blocks);
        this.moved = new HashSet<>(blocks);
        this.anchor = blocks.get(0);
        this.target = into.subLevel().getPlot().getCenterBlock();
    }

    /**
     * The sub-level the blocks left. It keeps the rest.
     */
    public SubLevelAttachment from() {
        return from;
    }

    /**
     * The new sub-level the blocks moved to.
     */
    public SubLevelAttachment into() {
        return into;
    }

    /**
     * Where the moved blocks were, in the plot of {@link #from()}.
     */
    public List<BlockPos> blocks() {
        return blocks;
    }

    /**
     * Whether the block at {@code pos} in the plot of {@link #from()} moved.
     */
    public boolean moved(BlockPos pos) {
        return moved.contains(pos);
    }

    /**
     * Where a moved block at {@code pos} in the plot of {@link #from()} is now, in the plot of {@link #into()}.
     */
    public BlockPos newPos(BlockPos pos) {
        return target.offset(pos.subtract(anchor));
    }

    /**
     * The share of the combined mass that went to {@link #into()}, from 0 to 1. Handy for dividing up amounts.
     */
    public double massFraction() {
        double moved = mass(into);
        double total = moved + mass(from);
        return total <= 0 ? 0 : moved / total;
    }

    private static double mass(SubLevelAttachment attachment) {
        return attachment.subLevel() instanceof ServerSubLevel server ? server.getSelfMassTracker().getMass() : 0;
    }

    /**
     * Attaches a copy of {@code module}, with its data and children, to {@link #into()}, replacing one already there.
     */
    @SuppressWarnings("unchecked")
    public <M extends Module<SubLevelAttachment>> M copy(M module) {
        ModuleType<?, SubLevelAttachment> type = module.type();
        type.detach(into);
        WorldModuleSystem.copy(from, into, candidate -> candidate == type);
        return (M) type.get(into);
    }

    /**
     * Like {@link #copy}, and then detaches {@code module} from {@link #from()}.
     */
    public <M extends Module<SubLevelAttachment>> M move(M module) {
        M copy = copy(module);
        module.type().detach(from);
        return copy;
    }
}

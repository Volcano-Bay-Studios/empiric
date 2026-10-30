package xyz.volcanobay.empiric.api.part.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctUtil;
import xyz.volcanobay.empiric.api.part.PartOctBlock;
import xyz.volcanobay.empiric.api.part.PartTooltip;
import xyz.volcanobay.empiric.api.part.Piece;
import xyz.volcanobay.empiric.api.part.Selection;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipBehavior;
import xyz.volcanobay.empiric.api.ui.tooltip.TooltipSource;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

/**
 * The floating tooltip of the aimed-at piece, from its type's {@link PartTooltip} hooks.
 */
public final class PartTooltips implements TooltipSource {

    public static final PartTooltips INSTANCE = new PartTooltips();

    private PartTooltips() {
    }

    @Override
    public @Nullable Target find(Level level, BlockHitResult hit, Player viewer) {
        BlockPos pos = hit.getBlockPos();
        OctLevel oct = LevelOctExtension.get(level);
        if (!(oct.getOctState(pos, OctUtil.getOctant(hit)).getBlock() instanceof PartOctBlock)) {
            return null;
        }
        Selection selection = Selection.of(oct, pos, viewer, hit);
        if (selection == null) {
            return null;
        }
        Piece piece = selection.piece();
        for (PartTooltip hook : piece.type.hooks(PartTooltip.class)) {
            TooltipBehavior behavior = hook.tooltip(new PartTooltip.Ref(level, pos.immutable(), piece.id));
            if (behavior != null) {
                AABB box = new AABB(piece.x, piece.y, piece.z, piece.maxX(), piece.maxY(), piece.maxZ());
                return new Target(piece.id, new AABB(box.minX / 16, box.minY / 16, box.minZ / 16, box.maxX / 16, box.maxY / 16, box.maxZ / 16)
                        .move(pos), behavior);
            }
        }
        return null;
    }
}

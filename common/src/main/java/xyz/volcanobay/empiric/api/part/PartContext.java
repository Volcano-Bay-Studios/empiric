package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import xyz.volcanobay.empiric.api.oct.OctLevel;

/**
 * A player using a piece.
 *
 * @param pos   The block that was clicked; the workspace and selection are in its pixels
 * @param stack What the player holds in the hand used, possibly empty
 */
public record PartContext(Level level, OctLevel oct, BlockPos pos, Workspace workspace, Selection selection, Player player,
                          InteractionHand hand, ItemStack stack) {

    public Piece piece() {
        return selection.piece();
    }

    public boolean isClientSide() {
        return level.isClientSide;
    }
}

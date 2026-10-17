package xyz.volcanobay.empiric.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.system.EntityDropSystem;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Shadow
    @Final
    public Player player;

    @Shadow
    public abstract boolean isEmpty();

    @Inject(method = "dropAll", at = @At("TAIL"))
    private void empiric$dropAll(CallbackInfo ci) {
        EntityDropSystem.inventoryCleared(player);
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void empiric$clearContent(CallbackInfo ci) {
        EntityDropSystem.inventoryCleared(player);
    }

    @Inject(method = "clearOrCountMatchingItems", at = @At("RETURN"))
    private void empiric$clearMatching(Predicate<ItemStack> predicate, int maxCount, Container crafting, CallbackInfoReturnable<Integer> cir) {
        if (maxCount != 0 && cir.getReturnValue() > 0 && isEmpty()) {
            EntityDropSystem.inventoryCleared(player);
        }
    }
}

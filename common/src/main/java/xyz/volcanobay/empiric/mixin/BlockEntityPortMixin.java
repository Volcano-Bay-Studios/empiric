package xyz.volcanobay.empiric.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import xyz.volcanobay.empiric.api.network.port.NetworkPortConfig;
import xyz.volcanobay.empiric.api.network.port.NetworkPortConfigHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BlockEntity.class)
public class BlockEntityPortMixin implements NetworkPortConfigHolder {

    @Unique
    private NetworkPortConfig empiric$portConfig;

    @Override
    public NetworkPortConfig getPortConfig() {
        if (empiric$portConfig == null) {
            empiric$portConfig = new NetworkPortConfig();
        }
        return empiric$portConfig;
    }
}

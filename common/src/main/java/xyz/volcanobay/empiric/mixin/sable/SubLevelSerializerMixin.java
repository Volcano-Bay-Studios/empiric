package xyz.volcanobay.empiric.mixin.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.volcanobay.empiric.system.SubLevelModuleSystem;

import java.util.List;
import java.util.UUID;

@Mixin(value = SubLevelSerializer.class, remap = false)
public class SubLevelSerializerMixin {

    @Inject(method = "toData", at = @At("HEAD"))
    private static void empiric$save(ServerSubLevel subLevel, List<UUID> dependencies, CallbackInfoReturnable<SubLevelData> cir) {
        SubLevelModuleSystem.save(subLevel);
    }

    @Inject(method = "fullyLoad", at = @At("HEAD"))
    private static void empiric$beginLoad(ServerLevel level, SubLevelData data, CallbackInfoReturnable<ServerSubLevel> cir) {
        SubLevelModuleSystem.beginLoad();
    }

    @Inject(method = "fullyLoad", at = @At("RETURN"))
    private static void empiric$endLoad(ServerLevel level, SubLevelData data, CallbackInfoReturnable<ServerSubLevel> cir) {
        SubLevelModuleSystem.endLoad(cir.getReturnValue());
    }
}

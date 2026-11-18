package xyz.volcanobay.empiric.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class GuardMixinPlugin implements IMixinConfigPlugin {

    private boolean enabled;

    @Override
    public void onLoad(String mixinPackage) {
        enabled = System.getProperty("fabric-api.gametest") != null || Boolean.getBoolean("neoforge.gameTestServer") || Boolean.getBoolean("fabric.development") || isRunFromDirectory();
    }

    private static boolean isRunFromDirectory() {
        try {
            return Files.isDirectory(Path.of(GuardMixinPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (URISyntaxException | RuntimeException e) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return enabled;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}

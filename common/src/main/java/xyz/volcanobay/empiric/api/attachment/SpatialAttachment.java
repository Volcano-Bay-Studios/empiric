package xyz.volcanobay.empiric.api.attachment;

import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import xyz.volcanobay.empiric.core.module.ModuleHolder;
import xyz.volcanobay.empiric.system.SpatialModuleStore;

public abstract class SpatialAttachment implements WorldAttachment {

    protected final Level level;
    private final ModuleHolder modules = new ModuleHolder(this);

    protected SpatialAttachment(Level level) {
        this.level = level;
    }

    public abstract long x();

    public abstract long y();

    public abstract long z();

    @Override
    public Vec3 position() {
        return Vec3.atCenterOf(blockPos());
    }

    public abstract BlockPos blockPos();

    public abstract SpatialModuleStore<?> store();

    @Override
    public Level level() {
        return level;
    }

    @Override
    public ModuleHolder modules() {
        return modules;
    }

    @Override
    public PacketTarget syncTarget() {
        return PacketTarget.tracking((ServerLevel) level, blockPos());
    }
}

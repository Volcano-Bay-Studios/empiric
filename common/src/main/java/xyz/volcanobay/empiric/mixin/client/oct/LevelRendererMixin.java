package xyz.volcanobay.empiric.mixin.client.oct;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.api.part.client.PartOutline;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctBlockComplement;
import xyz.volcanobay.empiric.api.oct.OctMultiblock;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctUtil;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.oct.FullOctBlock;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.jetbrains.annotations.Nullable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    
    @Shadow
    @Final
    private Minecraft minecraft;
    
    @Shadow
    @Nullable
    private ClientLevel level;
    
    @Shadow
    public static void renderLineBox(VertexConsumer consumer,
                                     double minX,
                                     double minY,
                                     double minZ,
                                     double maxX,
                                     double maxY,
                                     double maxZ,
                                     float red,
                                     float green,
                                     float blue,
                                     float alpha) {
    }
    
    @Shadow
    @Final
    private RenderBuffers renderBuffers;
    
    @Inject(method = "renderHitOutline", at = @At("HEAD"), cancellable = true)
    private void renderPartOutline(PoseStack pose, VertexConsumer consumer, Entity entity, double camX, double camY, double camZ,
                                   BlockPos pos, BlockState state, CallbackInfo ci) {
        if (state.getBlock() instanceof FullOctBlock && PartOutline.render(pose, consumer, entity, camX, camY, camZ, pos)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "renderHitOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape getOctShape(BlockState instance,
                                   BlockGetter blockGetter,
                                   BlockPos blockPos,
                                   CollisionContext collisionContext,
                                   Operation<VoxelShape> original) {
        if (instance.getBlock() instanceof OctMultiblock multiblock) {
            return multiblock.getMultiblockOutline(instance, blockGetter, blockPos);
        }
        if (instance.getBlock() instanceof FullOctBlock || !(blockGetter instanceof Level)) {
            BlockEntity blockEntity = blockGetter.getBlockEntity(blockPos);
            if (blockEntity instanceof FullOctBlockEntity octBlockEntity) {
                BlockHitResult hitResult = (BlockHitResult) this.minecraft.hitResult;
                byte octant = OctUtil.getOctant(hitResult);
                BlockState octState = octBlockEntity.getOctState(octant);
                OctLevel octLevel = ((LevelOctExtension) blockGetter).empiric$getOctLevel();
                BlockPos center = OctBlockComplement.getMultiblockCenter(octLevel, blockPos, octant);
                if (center != null) {
                    BlockState centerState = blockGetter.getBlockState(center);
                    if (centerState.getBlock() instanceof OctMultiblock multiblock) {
                        // Outline the whole multiblock, not just the looked-at oct
                        return multiblock.getMultiblockOutline(centerState, blockGetter, center)
                                .move(center.getX() - blockPos.getX(), center.getY() - blockPos.getY(), center.getZ() - blockPos.getZ());
                    }
                }
                Block block = octState.getBlock();
                if (block instanceof OctBlock octBlock) {
                    VoxelShape outline = octBlock.getHitOutline(octState, octLevel, blockPos, octant, collisionContext);
                    if (outline != null) {
                        return outline;
                    }
                    return octBlock.getFullShape(octState, octLevel, blockPos, octant, collisionContext).get(octant);
                }
            }
        }
        
        return original.call(instance, blockGetter, blockPos, collisionContext);
    }
    
}

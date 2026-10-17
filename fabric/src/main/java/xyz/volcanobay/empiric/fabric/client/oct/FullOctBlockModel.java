package xyz.volcanobay.empiric.fabric.client.oct;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;

import java.util.List;
import java.util.function.Supplier;

public class FullOctBlockModel implements BakedModel, FabricBakedModel {

    private final TextureAtlasSprite defaultParticleSprite;

    public FullOctBlockModel(TextureAtlasSprite defaultParticleSprite) {
        this.defaultParticleSprite = defaultParticleSprite;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier, RenderContext context) {
        BlockModelShaper shaper = Minecraft.getInstance().getModelManager().getBlockModelShaper();
        FullOctBlockEntity.get(blockView, pos).ifPresent(be -> {
            for (int i = 0; i < 8; ++i) {
                BlockState octState = be.getOctState(i);
                if (octState.isAir() || octState.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                float x = ((i & 1) != 0 ? 0.5f : 0) - 0.25f;
                float y = ((i & 2) != 0 ? 0.5f : 0) - 0.25f;
                float z = ((i & 4) != 0 ? 0.5f : 0) - 0.25f;
                RenderMaterial material = RendererAccess.INSTANCE.getRenderer().materialFinder()
                        .blendMode(BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(octState)))
                        .find();
                context.pushTransform(quad -> {
                    for (int vertex = 0; vertex < 4; vertex++) {
                        quad.pos(vertex, quad.x(vertex) + x, quad.y(vertex) + y, quad.z(vertex) + z);
                    }
                    quad.material(material);
                    return true;
                });
                ((FabricBakedModel) shaper.getBlockModel(octState)).emitBlockQuads(blockView, octState, pos, randomSupplier, context);
                context.popTransform();
            }
        });
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction direction, RandomSource random) {
        return List.of();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return false;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return defaultParticleSprite;
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}

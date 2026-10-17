package xyz.volcanobay.empiric.neoforge.client;

import com.mojang.math.Transformation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class FullOctBlockModel implements BakedModel {
    public static final ModelProperty<BakedModel> OCT_0_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_1_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_2_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_3_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_4_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_5_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_6_MODEL = new ModelProperty<>();
    public static final ModelProperty<BakedModel> OCT_7_MODEL = new ModelProperty<>();

    public static final ModelProperty<BlockState> OCT_0_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_1_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_2_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_3_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_4_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_5_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_6_STATE = new ModelProperty<>();
    public static final ModelProperty<BlockState> OCT_7_STATE = new ModelProperty<>();

    public static final ModelProperty<BakedModel>[] OCT_MODELS = new ModelProperty[]{
            OCT_0_MODEL, OCT_1_MODEL, OCT_2_MODEL, OCT_3_MODEL, OCT_4_MODEL, OCT_5_MODEL, OCT_6_MODEL, OCT_7_MODEL
    };

    public static final ModelProperty<BlockState>[] OCT_STATES = new ModelProperty[]{
            OCT_0_STATE, OCT_1_STATE, OCT_2_STATE, OCT_3_STATE, OCT_4_STATE, OCT_5_STATE, OCT_6_STATE, OCT_7_STATE
    };

    public static final IQuadTransformer[] OCT_TRANSFORMS = buildOctTransforms();
    
    private static IQuadTransformer[] buildOctTransforms() {
        IQuadTransformer[] result = new IQuadTransformer[8];
        for (int i = 0; i < 8; ++i) {
            float x = ((i & 1) != 0 ? 0.5f : 0) - 0.25f;
            float y = ((i & 2) != 0 ? 0.5f : 0) - 0.25f;
            float z = ((i & 4) != 0 ? 0.5f : 0) - 0.25f;
            result[i] = QuadTransformers.applying(new Transformation(new Vector3f(x, y, z), null, null, null));
        }
        return result;
    }
    
    private final RandomSource random = RandomSource.create();
    private final TextureAtlasSprite defaultParticleSprite;
    
    public FullOctBlockModel(TextureAtlasSprite defaultParticleSprite) {
        this.defaultParticleSprite = defaultParticleSprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state,
                                    @Nullable Direction direction,
                                    RandomSource randomSource) {
        return List.of();
    }
    
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        BlockModelShaper shaper = Minecraft.getInstance().getModelManager().getBlockModelShaper();
        return FullOctBlockEntity.get(level, pos).map(be -> {
            ModelData.Builder builder = modelData.derive();
            for (int i = 0; i < 8; ++i) {
                BlockState octState = be.getOctState(i);
                if (octState.isAir() || octState.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                BakedModel model = shaper.getBlockModel(octState);
                builder.with(OCT_MODELS[i], model).with(OCT_STATES[i], octState);
            }
            return builder.build();
        }).orElse(modelData);
    }
    
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>();
        for (int i = 0; i < 8; ++i) {
            BakedModel model = data.get(OCT_MODELS[i]);
            if (model == null) {
                continue;
            }
            BlockState octState = data.get(OCT_STATES[i]);
            if (renderType != null && !model.getRenderTypes(octState, rand, data).contains(renderType)) {
                continue;
            }
            for (BakedQuad quad : model.getQuads(octState, side, rand, data, renderType)) {
                quads.add(OCT_TRANSFORMS[i].process(quad));
            }
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.union(IntStream.range(0, 8).boxed().map(i -> {
            BakedModel model = data.get(OCT_MODELS[i]);
            if (model == null) {
                return ChunkRenderTypeSet.none();
            }
            return model.getRenderTypes(data.get(OCT_STATES[i]), rand, data);
        }).toList());
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
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        BakedModel model = null;
        int sprite = this.random.nextInt(8) + 1;
        for (int i = 0; i < 16 && sprite > 0; i++) {
            BakedModel model1 = data.get(OCT_MODELS[i % 8]);
            if (model1 == null) {
                continue;
            }
            model = model1;
            sprite--;
        }
        if (model == null) {
            return BakedModel.super.getParticleIcon(data);
        }
        return model.getParticleIcon(data);
    }
    
    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}

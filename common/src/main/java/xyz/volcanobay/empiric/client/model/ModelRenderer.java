package xyz.volcanobay.empiric.client.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.vertex.VertexArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.behavior.RenderBehavior;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.model.RenderOrder;
import xyz.volcanobay.empiric.api.model.TranslucentDraw;
import xyz.volcanobay.empiric.model.ModelGeometry;
import xyz.volcanobay.empiric.model.ModelRenderKind;
import xyz.volcanobay.empiric.model.ModelStats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Loads registered models on first use and draws them. Everything runs on the render thread.
 * <p>
 * Anything still queued when the level has finished rendering is dropped, so a model has to be queued every frame.
 * Resource reloads call {@link #invalidate()}.
 */
public final class ModelRenderer {

    private static final ResourceLocation SHADER = Empiric.id("model");
    private static final Map<EmpiricModel, LoadedModel> MODELS = new HashMap<>();
    private static final List<ModelMesh> PENDING = new ArrayList<>();
    private static final List<TranslucentEntry> TRANSLUCENT = new ArrayList<>();
    private static long sequence;
    private static int instances;
    private static int drawCalls;

    private ModelRenderer() {
    }

    /**
     * Queues the model, or one of its parts, to be drawn at the next stage that draws its meshes.
     * The pose is relative to the camera, as a block entity renderer's pose stack is.
     */
    public static void queue(EmpiricModel model, @Nullable String part, Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        queue(model, part, pose, normal, light, overlay, tint, RenderOrder.DEFAULT);
    }

    /**
     * Queues the model with its translucent meshes placed by the order; see {@link RenderOrder}.
     */
    public static void queue(EmpiricModel model, @Nullable String part, Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint, RenderOrder order) {
        double depth = -1;
        for (ModelMesh mesh : resolve(model).meshes(part)) {
            if (mesh.kind() == ModelRenderKind.TRANSLUCENT) {
                if (depth < 0) {
                    depth = depth(pose, order.sortPoint());
                }
                TRANSLUCENT.add(TranslucentEntry.mesh(depth, order.layer(), sequence++, mesh, order.cull(), pose, normal, light, overlay, tint));
            } else if (mesh.add(pose, normal, light, overlay, tint)) {
                PENDING.add(mesh);
            }
            instances++;
        }
    }

    /**
     * Queues custom translucent geometry; see {@link TranslucentDraw}.
     */
    public static void queueCustom(Vector3fc sortPoint, int layer, Consumer<TranslucentDraw.Context> draw) {
        TRANSLUCENT.add(TranslucentEntry.custom(sortPoint.lengthSquared(), layer, sequence++, draw));
    }

    private static double depth(Matrix4fc pose, @Nullable Vector3fc sortPoint) {
        if (sortPoint != null) {
            return sortPoint.lengthSquared();
        }
        return pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32();
    }

    /**
     * Draws the model, or one of its parts, now, with the projection that's currently set.
     */
    public static void drawNow(EmpiricModel model, @Nullable String part, Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint) {
        drawNow(model, part, pose, normal, light, overlay, tint, RenderOrder.Cull.DEFAULT);
    }

    /**
     * Draws the model now with only some of its faces, so a hollow translucent model can be drawn back, contents, front.
     */
    public static void drawNow(EmpiricModel model, @Nullable String part, Matrix4fc pose, Matrix3fc normal, int light, int overlay, int tint, RenderOrder.Cull cull) {
        ModelMesh[] meshes = resolve(model).meshes(part);
        ShaderProgram shader = shader();
        if (meshes.length == 0 || shader == null) {
            return;
        }
        begin(shader, RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
        for (ModelMesh mesh : meshes) {
            setup(shader, mesh);
            cull(mesh, cull);
            mesh.drawSingle(pose, normal, light, overlay, tint);
            instances++;
            drawCalls++;
        }
        end();
    }

    public static Set<String> partNames(EmpiricModel model) {
        return resolve(model).partNames();
    }

    /**
     * Called at each stage of level rendering.
     */
    public static void renderStage(RenderBehavior.Stage stage, Matrix4fc view, Matrix4fc projection) {
        switch (stage) {
            case AFTER_BLOCK_ENTITIES -> flush(view, projection, false);
            case AFTER_TRANSLUCENT_BLOCKS -> flushTranslucent(view, projection);
            case AFTER_LEVEL -> finishFrame();
            default -> {
            }
        }
    }

    /**
     * Frees every loaded model so the next draw loads it again. Used when resources reload.
     */
    public static void invalidate() {
        for (ModelMesh mesh : PENDING) {
            mesh.clear();
        }
        PENDING.clear();
        TRANSLUCENT.clear();
        for (LoadedModel model : MODELS.values()) {
            model.close();
        }
        MODELS.clear();
        ModelStats.clear();
    }

    /**
     * Draws everything translucent far to near, models and custom draws together. Neighbouring instances of the same
     * mesh share one instanced draw.
     */
    private static void flushTranslucent(Matrix4fc view, Matrix4fc projection) {
        if (TRANSLUCENT.isEmpty()) {
            return;
        }
        ShaderProgram shader = shader();
        List<TranslucentEntry> entries = new ArrayList<>(TRANSLUCENT);
        TRANSLUCENT.clear();
        entries.sort(TranslucentEntry.ORDER);
        Context context = new Context(view, projection);
        boolean begun = false;
        int i = 0;
        while (i < entries.size()) {
            TranslucentEntry entry = entries.get(i);
            if (entry.custom != null) {
                if (begun) {
                    end();
                    begun = false;
                }
                try {
                    entry.custom.accept(context);
                } catch (RuntimeException e) {
                    Empiric.LOGGER.error("A translucent draw failed", e);
                }
                end();
                i++;
                continue;
            }
            int run = i + 1;
            while (run < entries.size() && entry.batchesWith(entries.get(run))) {
                run++;
            }
            if (shader != null && entry.mesh != null) {
                if (!begun) {
                    begin(shader, view, projection);
                    begun = true;
                }
                setup(shader, entry.mesh);
                cull(entry.mesh, entry.cull);
                entry.mesh.drawBatch(entries, i, run);
                drawCalls++;
            }
            i = run;
        }
        if (begun) {
            end();
        }
    }

    private static void cull(ModelMesh mesh, RenderOrder.Cull cull) {
        switch (cull) {
            case DEFAULT -> GL11C.glCullFace(GL11C.GL_BACK);
            case BACK_FACES_ONLY -> {
                RenderSystem.enableCull();
                GL11C.glCullFace(GL11C.GL_FRONT);
            }
            case FRONT_FACES_ONLY -> {
                RenderSystem.enableCull();
                GL11C.glCullFace(GL11C.GL_BACK);
            }
        }
    }

    /**
     * @return A context for custom draws with the matrices currently set
     */
    public static TranslucentDraw.Context immediateContext() {
        return new Context(new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()));
    }

    private static void flush(Matrix4fc view, Matrix4fc projection, boolean translucent) {
        ShaderProgram shader = PENDING.isEmpty() ? null : shader();
        if (shader == null) {
            return;
        }
        boolean begun = false;
        Iterator<ModelMesh> pending = PENDING.iterator();
        while (pending.hasNext()) {
            ModelMesh mesh = pending.next();
            if ((mesh.kind() == ModelRenderKind.TRANSLUCENT) != translucent) {
                continue;
            }
            if (!begun) {
                begin(shader, view, projection);
                begun = true;
            }
            setup(shader, mesh);
            mesh.flush();
            drawCalls++;
            pending.remove();
        }
        if (begun) {
            end();
        }
    }

    private static void finishFrame() {
        for (ModelMesh mesh : PENDING) {
            mesh.clear();
        }
        PENDING.clear();
        TRANSLUCENT.clear();
        ModelStats.frame(instances, drawCalls);
        instances = 0;
        drawCalls = 0;
    }

    @Nullable
    private static ShaderProgram shader() {
        return VeilRenderSystem.renderer().getShaderManager().getShader(SHADER);
    }

    private static void begin(ShaderProgram shader, Matrix4fc view, Matrix4fc projection) {
        bindLevel(shader, view, projection);
    }

    /**
     * Binds a shader with the view and projection, and the block atlas, overlay and lightmap as samplers 0 to 2.
     */
    static void bindLevel(ShaderProgram shader, Matrix4fc view, Matrix4fc projection) {
        Minecraft minecraft = Minecraft.getInstance();
        GameRenderer renderer = minecraft.gameRenderer;
        renderer.lightTexture().turnOnLightLayer();
        int light = RenderSystem.getShaderTexture(2);
        renderer.overlayTexture().setupOverlayColor();
        int overlay = RenderSystem.getShaderTexture(1);
        renderer.overlayTexture().teardownOverlayColor();

        RenderSystem.enableDepthTest();
        shader.bind();
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, projection);
        shader.setTexture("Sampler0", GL11C.GL_TEXTURE_2D, minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId());
        shader.setTexture("Sampler1", GL11C.GL_TEXTURE_2D, overlay);
        shader.setTexture("Sampler2", GL11C.GL_TEXTURE_2D, light);
        shader.bindSamplers(0);
    }

    private static void setup(ShaderProgram shader, ModelMesh mesh) {
        ModelRenderKind kind = mesh.kind();
        if (mesh.doubleSided()) {
            RenderSystem.disableCull();
        } else {
            RenderSystem.enableCull();
        }
        shader.getUniformSafe("CutoutThreshold").setFloat(switch (kind) {
            case SOLID -> 0.0f;
            case CUTOUT -> 0.1f;
            case TRANSLUCENT -> 0.001f;
        });
        shader.getUniformSafe("Opaque").setFloat(kind == ModelRenderKind.SOLID ? 1.0f : 0.0f);
        if (kind == ModelRenderKind.TRANSLUCENT) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // like vanilla's translucent terrain: what's drawn far to near writes depth, so where two objects' order is
            // off the farther one is hidden rather than painted over the nearer
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
    }

    private static void end() {
        VertexArray.unbind();
        ShaderProgram.unbind();
        RenderSystem.activeTexture(GL13C.GL_TEXTURE0);
        GL11C.glCullFace(GL11C.GL_BACK);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
    }

    private record Context(Matrix4fc view, Matrix4fc projection) implements TranslucentDraw.Context {
        @Override
        public void begin(ShaderProgram shader) {
            bindLevel(shader, view, projection);
        }
    }

    private static LoadedModel resolve(EmpiricModel model) {
        LoadedModel loaded = MODELS.get(model);
        if (loaded == null) {
            loaded = load(model);
            MODELS.put(model, loaded);
        }
        return loaded;
    }

    private static LoadedModel load(EmpiricModel model) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Function<ResourceLocation, TextureAtlasSprite> sprites = minecraft.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            ModelGeometry geometry = ModelLoader.load(model, minecraft.getResourceManager(), sprites);
            LoadedModel baked = ModelMeshBaker.bake(geometry, sprites, model.isDoubleSided());
            ModelStats.loaded(model.id(), new ModelStats.Info(baked.partNames().size(), baked.meshCount(), baked.vertices(), false));
            return baked;
        } catch (Exception e) {
            Empiric.LOGGER.error("Couldn't load model {}", model.id(), e);
            ModelStats.loaded(model.id(), new ModelStats.Info(0, 0, 0, true));
            return LoadedModel.EMPTY;
        }
    }
}

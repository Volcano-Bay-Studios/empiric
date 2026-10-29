package xyz.volcanobay.empiric.model;

import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reads Wavefront {@code .obj} text, and the {@code .mtl} libraries it names, into a {@link ModelGeometry}.
 * <p>
 * One unit is one block. Each {@code g} or {@code o} becomes a named part. A material's {@code map_Kd} must name a
 * sprite that's already in the block atlas. A material with {@code d} below 1 is translucent. Anything else the
 * format has is ignored.
 */
public final class ObjParser {

    private static final ResourceLocation MISSING = ResourceLocation.withDefaultNamespace("missingno");
    private static final Material DEFAULT_MATERIAL = new Material(MISSING, ModelRenderKind.CUTOUT);

    private ObjParser() {
    }

    /**
     * @param obj              the {@code .obj} text
     * @param namespace        the namespace for textures that don't name one
     * @param materialLibraries gives the text of a {@code .mtl} file by the name the {@code .obj} uses, or null if missing
     * @throws IllegalArgumentException with the line number if the file is malformed
     */
    public static ModelGeometry parse(String obj, String namespace, Function<String, String> materialLibraries) {
        Parser parser = new Parser(namespace, materialLibraries);
        String[] lines = obj.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            try {
                parser.line(lines[i]);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("OBJ line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new ModelGeometry(parser.parts);
    }

    private record Material(ResourceLocation texture, ModelRenderKind kind) {
    }

    private static final class Parser {

        private final String namespace;
        private final Function<String, String> materialLibraries;
        private final List<Vector3f> positions = new ArrayList<>();
        private final List<Vector3f> normals = new ArrayList<>();
        private final List<Vector2f> uvs = new ArrayList<>();
        private final Map<String, Material> materials = new HashMap<>();
        private final Map<String, List<ModelFace>> parts = new LinkedHashMap<>();
        private String group = ModelGeometry.DEFAULT_PART;
        private Material material = DEFAULT_MATERIAL;

        Parser(String namespace, Function<String, String> materialLibraries) {
            this.namespace = namespace;
            this.materialLibraries = materialLibraries;
        }

        void line(String raw) {
            int comment = raw.indexOf('#');
            String text = (comment >= 0 ? raw.substring(0, comment) : raw).trim();
            if (text.isEmpty()) {
                return;
            }
            String[] tokens = text.split("\\s+");
            switch (tokens[0]) {
                case "v" -> positions.add(new Vector3f(number(tokens, 1), number(tokens, 2), number(tokens, 3)));
                case "vn" -> normals.add(new Vector3f(number(tokens, 1), number(tokens, 2), number(tokens, 3)));
                case "vt" -> uvs.add(new Vector2f(number(tokens, 1), tokens.length > 2 ? 1 - number(tokens, 2) : 0));
                case "g", "o" -> group = tokens.length > 1 ? rest(text, tokens[0]) : ModelGeometry.DEFAULT_PART;
                case "usemtl" -> material = materials.getOrDefault(rest(text, tokens[0]), DEFAULT_MATERIAL);
                case "mtllib" -> {
                    for (int i = 1; i < tokens.length; i++) {
                        library(tokens[i]);
                    }
                }
                case "f" -> face(tokens);
                default -> {
                }
            }
        }

        private static String rest(String text, String tag) {
            return text.substring(tag.length()).trim();
        }

        private static float number(String[] tokens, int index) {
            if (index >= tokens.length) {
                throw new IllegalArgumentException("'" + tokens[0] + "' is missing a value");
            }
            return Float.parseFloat(tokens[index]);
        }

        private static int index(String token, int size) {
            int value = Integer.parseInt(token);
            int resolved = value > 0 ? value - 1 : size + value;
            if (value == 0 || resolved < 0 || resolved >= size) {
                throw new IllegalArgumentException("index " + token + " is out of range");
            }
            return resolved;
        }

        private void face(String[] tokens) {
            int count = tokens.length - 1;
            if (count < 3) {
                throw new IllegalArgumentException("a face needs at least 3 vertices");
            }
            Vector3f[] position = new Vector3f[count];
            Vector2f[] uv = new Vector2f[count];
            Vector3f[] normal = new Vector3f[count];
            for (int i = 0; i < count; i++) {
                String[] parts = tokens[i + 1].split("/", -1);
                position[i] = positions.get(index(parts[0], positions.size()));
                uv[i] = parts.length > 1 && !parts[1].isEmpty() ? uvs.get(index(parts[1], uvs.size())) : null;
                normal[i] = parts.length > 2 && !parts[2].isEmpty() ? normals.get(index(parts[2], normals.size())) : null;
            }
            if (count <= 4) {
                emit(position, uv, normal, 0, 1, 2, count == 4 ? 3 : -1);
                return;
            }
            for (int i = 1; i < count - 1; i++) {
                emit(position, uv, normal, 0, i, i + 1, -1);
            }
        }

        private void emit(Vector3f[] position, Vector2f[] uv, Vector3f[] normal, int a, int b, int c, int d) {
            Vector3f flat = new Vector3f(position[b]).sub(position[a]).cross(new Vector3f(position[c]).sub(position[a]));
            if (flat.lengthSquared() > 0) {
                flat.normalize();
            } else {
                flat.set(0, 1, 0);
            }
            int[] corners = d >= 0 ? new int[]{a, b, c, d} : new int[]{a, b, c};
            List<ModelVertex> vertices = new ArrayList<>(corners.length);
            for (int corner : corners) {
                Vector3f vertexNormal = normal[corner] != null ? new Vector3f(normal[corner]).normalize() : new Vector3f(flat);
                Vector2f vertexUv = uv[corner] != null ? uv[corner] : new Vector2f();
                vertices.add(new ModelVertex(new Vector3f(position[corner]), vertexNormal, vertexUv.x, vertexUv.y));
            }
            parts.computeIfAbsent(group, name -> new ArrayList<>())
                    .add(new ModelFace(vertices, material.texture(), false, material.kind(), true));
        }

        private void library(String name) {
            String text = materialLibraries.apply(name);
            if (text == null) {
                return;
            }
            String current = null;
            ResourceLocation texture = MISSING;
            ModelRenderKind kind = ModelRenderKind.CUTOUT;
            for (String raw : text.split("\\R")) {
                int comment = raw.indexOf('#');
                String line = (comment >= 0 ? raw.substring(0, comment) : raw).trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] tokens = line.split("\\s+");
                switch (tokens[0]) {
                    case "newmtl" -> {
                        if (current != null) {
                            materials.put(current, new Material(texture, kind));
                        }
                        current = rest(line, tokens[0]);
                        texture = MISSING;
                        kind = ModelRenderKind.CUTOUT;
                    }
                    case "map_Kd" -> texture = texture(tokens[tokens.length - 1]);
                    case "d" -> kind = number(tokens, 1) < 1 ? ModelRenderKind.TRANSLUCENT : ModelRenderKind.CUTOUT;
                    case "Tr" -> kind = number(tokens, 1) > 0 ? ModelRenderKind.TRANSLUCENT : ModelRenderKind.CUTOUT;
                    default -> {
                    }
                }
            }
            if (current != null) {
                materials.put(current, new Material(texture, kind));
            }
        }

        private ResourceLocation texture(String path) {
            String clean = path.replace('\\', '/');
            if (clean.endsWith(".png")) {
                clean = clean.substring(0, clean.length() - 4);
            }
            int colon = clean.indexOf(':');
            String space = colon >= 0 ? clean.substring(0, colon) : namespace;
            String name = colon >= 0 ? clean.substring(colon + 1) : clean;
            if (name.startsWith("textures/")) {
                name = name.substring("textures/".length());
            }
            ResourceLocation id = ResourceLocation.tryBuild(space, name);
            return id != null ? id : MISSING;
        }
    }
}

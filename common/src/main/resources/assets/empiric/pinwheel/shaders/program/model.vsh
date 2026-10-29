// Vertices are the vanilla block format. Attribute 3, the block lightmap coordinates, isn't used.
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 4) in vec3 Normal;

// Per instance data. The layout must match ModelMesh.
layout(location = 5) in vec4 PoseColumn0;
layout(location = 6) in vec4 PoseColumn1;
layout(location = 7) in vec4 PoseColumn2;
layout(location = 8) in vec4 PoseColumn3;
layout(location = 9) in vec3 NormalColumn0;
layout(location = 10) in vec3 NormalColumn1;
layout(location = 11) in vec3 NormalColumn2;
layout(location = 12) in ivec2 LightOverlay;
layout(location = 13) in vec4 Tint;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vertexColor;
out vec4 overlayColor;
out vec2 texCoord0;

// Matches vanilla's block shading for axis aligned faces, and blends smoothly for the rest.
float diffuse(vec3 normal) {
    vec3 squared = normal * normal;
    return squared.x * 0.6 + squared.z * 0.8 + squared.y * (normal.y > 0.0 ? 1.0 : 0.5);
}

void main() {
    mat4 pose = mat4(PoseColumn0, PoseColumn1, PoseColumn2, PoseColumn3);
    mat3 normalMatrix = mat3(NormalColumn0, NormalColumn1, NormalColumn2);
    gl_Position = ProjMat * ModelViewMat * pose * vec4(Position, 1.0);

    float shade = Color.r > 0.5 ? diffuse(normalize(normalMatrix * Normal)) : 1.0;
    ivec2 light = ivec2(LightOverlay.x & 0xFFFF, (LightOverlay.x >> 16) & 0xFFFF);
    vec4 lightColor = texture(Sampler2, clamp(vec2(light) / 256.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));
    vertexColor = vec4(lightColor.rgb * shade, 1.0) * Tint;

    ivec2 overlay = ivec2(LightOverlay.y & 0xFFFF, (LightOverlay.y >> 16) & 0xFFFF);
    overlayColor = texelFetch(Sampler1, overlay, 0);
    texCoord0 = UV0;
}

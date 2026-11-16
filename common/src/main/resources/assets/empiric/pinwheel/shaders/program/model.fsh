uniform sampler2D Sampler0;

// Fragments with less alpha than this are dropped, and Opaque forces the rest to full alpha.
uniform float CutoutThreshold;
uniform float Opaque;
// 0 draws normally, 1 shows the world space normal as a colour and 2 shows the face shade as grey.
uniform float DebugView;

in vec4 vertexColor;
in vec4 backColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 debugNormal;
in float debugShade;
in float debugBackShade;

out vec4 fragColor;

void main() {
    // bfs
    vec3 normal = gl_FrontFacing ? debugNormal : -debugNormal;
    if (DebugView > 1.5) {
        fragColor = vec4(vec3(gl_FrontFacing ? debugShade : debugBackShade), 1.0);
        return;
    }
    if (DebugView > 0.5) {
        fragColor = vec4(normalize(normal) * 0.5 + 0.5, 1.0);
        return;
    }
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < CutoutThreshold) {
        discard;
    }
    if (Opaque > 0.5) {
        color.a = 1.0;
    }
    color *= gl_FrontFacing ? vertexColor : backColor;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    fragColor = color;
}

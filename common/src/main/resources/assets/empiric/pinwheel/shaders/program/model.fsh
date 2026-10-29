uniform sampler2D Sampler0;

// Fragments with less alpha than this are dropped, and Opaque forces the rest to full alpha.
uniform float CutoutThreshold;
uniform float Opaque;

in vec4 vertexColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < CutoutThreshold) {
        discard;
    }
    if (Opaque > 0.5) {
        color.a = 1.0;
    }
    color *= vertexColor;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    fragColor = color;
}

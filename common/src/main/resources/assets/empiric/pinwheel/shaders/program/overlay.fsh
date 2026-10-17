const float DISCARD_ALPHA = 0.3;

in vec4 vertexColor;

out vec4 fragColor;

void main() {
    if (vertexColor.a < DISCARD_ALPHA) {
        discard;
    }
    fragColor = vertexColor;
}

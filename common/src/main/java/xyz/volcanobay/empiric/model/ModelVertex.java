package xyz.volcanobay.empiric.model;

import org.joml.Vector3f;

/**
 * A vertex in block units. {@code u} and {@code v} are fractions of the face's texture, unless the face's
 * {@link ModelFace#atlasUv()} says they're already in atlas space.
 */
public record ModelVertex(Vector3f position, Vector3f normal, float u, float v) {
}

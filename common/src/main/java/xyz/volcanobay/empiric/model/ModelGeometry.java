package xyz.volcanobay.empiric.model;

import java.util.List;
import java.util.Map;

/**
 * A parsed model, split into named parts. Faces that belong to no named part are in {@link #DEFAULT_PART}.
 */
public record ModelGeometry(Map<String, List<ModelFace>> parts) {

    public static final String DEFAULT_PART = "";

    public int faceCount() {
        int count = 0;
        for (List<ModelFace> faces : parts.values()) {
            count += faces.size();
        }
        return count;
    }
}

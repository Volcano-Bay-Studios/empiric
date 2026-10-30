package xyz.volcanobay.empiric.api.part.trait;

import xyz.volcanobay.empiric.api.part.Hitbox;

import java.util.List;

public record Hitboxes(List<Hitbox> boxes) {

    public Hitboxes {
        boxes = List.copyOf(boxes);
    }

    public static Hitboxes of(Hitbox... boxes) {
        return new Hitboxes(List.of(boxes));
    }
}

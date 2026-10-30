package xyz.volcanobay.empiric.api.part.trait;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import xyz.volcanobay.empiric.api.part.HitLayer;
import xyz.volcanobay.empiric.api.part.Hitbox;
import xyz.volcanobay.empiric.api.part.OrientedBox;
import xyz.volcanobay.empiric.api.part.PartContext;
import xyz.volcanobay.empiric.api.part.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Buttons, dials and the like on a part, used with an empty hand.
 */
public record Controls(List<Control> controls) implements HitLayer {

    public record Control(String id, Hitbox box, Action action) {

        /**
         * @param box In the part's own pixels, before rotation
         */
        public Control(String id, AABB box, Action action) {
            this(id, Hitbox.of(box), action);
        }
    }

    @FunctionalInterface
    public interface Action {
        InteractionResult use(PartContext context, Control control);
    }

    public Controls {
        controls = List.copyOf(controls);
    }

    @Override
    public List<AABB> boxes(Piece piece) {
        List<AABB> boxes = new ArrayList<>(controls.size());
        for (Control control : controls) {
            boxes.add(control.box().in(piece).bounds());
        }
        return boxes;
    }

    @Override
    public List<OrientedBox> hitBoxes(Piece piece) {
        List<OrientedBox> boxes = new ArrayList<>(controls.size());
        for (Control control : controls) {
            boxes.add(control.box().in(piece));
        }
        return boxes;
    }

    @Override
    public double bias() {
        return 0.05;
    }

    @Override
    public boolean takesItems() {
        return false;
    }

    @Override
    public InteractionResult use(PartContext context) {
        int index = context.selection().index();
        if (index < 0 || index >= controls.size()) {
            return InteractionResult.PASS;
        }
        Control control = controls.get(index);
        return control.action().use(context, control);
    }
}

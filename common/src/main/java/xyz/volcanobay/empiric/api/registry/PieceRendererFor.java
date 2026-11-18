package xyz.volcanobay.empiric.api.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the {@code PieceRenderer} for the part trait with this id. The class needs a public constructor with
 * no parameters. It is only loaded on the client, and the registration code reads this annotation without initializing the
 * class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PieceRendererFor {

    /**
     * The id of the trait, as in {@code empiric:hitboxes}.
     */
    String value();
}

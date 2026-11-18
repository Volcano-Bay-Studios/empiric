package xyz.volcanobay.empiric.api.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The priority of a {@code PartInteraction} registered with {@code @Register}, on its class, field or method. Higher
 * goes first. Without it the priority is 0.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface InteractionPriority {

    int value();
}

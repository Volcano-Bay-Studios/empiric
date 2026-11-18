package xyz.volcanobay.empiric.api.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The renderer of a registered entity or block entity. The renderer class needs a constructor that takes
 * {@code EntityRendererProvider.Context} or {@code BlockEntityRendererProvider.Context}. It is only loaded on the
 * client, and the registration code reads this annotation without initializing the class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientRenderer {

    Class<?> value();
}

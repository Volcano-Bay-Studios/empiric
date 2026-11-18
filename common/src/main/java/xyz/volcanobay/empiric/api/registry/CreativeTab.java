package xyz.volcanobay.empiric.api.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Puts the item of a registered block or item into creative tabs. Ids without a namespace are in {@code minecraft}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface CreativeTab {

    String[] value();
}

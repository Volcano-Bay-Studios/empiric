package xyz.volcanobay.empiric.api.module;

/**
 * Implemented by a module that keeps its data in a form that isn't the saved one. {@link #beforeSave} runs right before the module is written
 * through the world module system, and {@link #afterSave} right after, so the saved copy doesn't have to be kept.
 */
public interface SaveHook {

    void beforeSave();

    void afterSave();
}

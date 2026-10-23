package xyz.volcanobay.empiric.api.behavior;

import xyz.volcanobay.empiric.api.attachment.SubLevelSplit;
import xyz.volcanobay.empiric.core.module.Behavior;

/**
 * A sub-level module that decides what happens to it when Sable splits blocks off its sub-level into a new one.
 * Modules without it stay on the original sub-level.
 */
public interface SplitBehavior extends Behavior {

    /**
     * Called on the server once the blocks have moved. The module stays where it is unless it uses
     * {@link SubLevelSplit#copy} or {@link SubLevelSplit#move}, or attaches something to {@link SubLevelSplit#into()} itself.
     */
    void onSplit(SubLevelSplit split);
}

package xyz.volcanobay.empiric.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;

import java.util.function.Consumer;

/**
 * Builds game test functions for mods that use Empiric, on Empiric's empty test structure. A mod still registers them
 * itself: a {@code fabric-gametest} entrypoint on Fabric, and a {@code RegisterGameTestsEvent} listener on NeoForge,
 * each pointing at a class with a {@code @GameTestGenerator} method that returns these.
 */
public final class EmpiricTests {

    public static final String STRUCTURE = "empiric:gametest/empty";
    public static final int TIMEOUT = 100;

    private EmpiricTests() {
    }

    /**
     * A test named {@code modId.name}, run in a batch with the other tests of the mod.
     */
    public static TestFunction function(String modId, String name, Consumer<GameTestHelper> function) {
        return function(modId, name, TIMEOUT, function);
    }

    public static TestFunction function(String modId, String name, int timeout, Consumer<GameTestHelper> function) {
        return new TestFunction(modId, modId + "." + name, STRUCTURE, Rotation.NONE, timeout, 0, true, false, 1, 1, false, function);
    }

    /**
     * A test in a batch of its own ({@code modId_isolated}), for tests that can't share a server with the others.
     */
    public static TestFunction isolated(String modId, String name, Consumer<GameTestHelper> function) {
        return new TestFunction(modId + "_isolated", modId + "." + name, STRUCTURE, Rotation.NONE, TIMEOUT, 0, true, false, 1, 1, false, function);
    }
}

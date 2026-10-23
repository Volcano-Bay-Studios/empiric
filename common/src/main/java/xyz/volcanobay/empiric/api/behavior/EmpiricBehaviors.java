package xyz.volcanobay.empiric.api.behavior;

import xyz.volcanobay.empiric.core.module.BehaviorKey;
import xyz.volcanobay.empiric.core.module.ModuleSystem;

public final class EmpiricBehaviors {

    public static BehaviorKey<ServerTickBehavior> SERVER_TICK;
    public static BehaviorKey<ClientTickBehavior> CLIENT_TICK;
    public static BehaviorKey<RenderBehavior> RENDER;
    public static BehaviorKey<BlockChangeBehavior> BLOCK_CHANGE;
    public static BehaviorKey<NetworkBehavior> NETWORK;
    public static BehaviorKey<ItemRenderBehavior> ITEM_RENDER;
    public static BehaviorKey<SplitBehavior> SPLIT;

    private EmpiricBehaviors() {
    }

    public static void register(ModuleSystem modules) {
        SERVER_TICK = modules.registerBehavior(ServerTickBehavior.class);
        CLIENT_TICK = modules.registerBehavior(ClientTickBehavior.class);
        RENDER = modules.registerBehavior(RenderBehavior.class);
        BLOCK_CHANGE = modules.registerBehavior(BlockChangeBehavior.class);
        NETWORK = modules.registerBehavior(NetworkBehavior.class);
        ITEM_RENDER = modules.registerBehavior(ItemRenderBehavior.class);
        SPLIT = modules.registerBehavior(SplitBehavior.class);
    }
}

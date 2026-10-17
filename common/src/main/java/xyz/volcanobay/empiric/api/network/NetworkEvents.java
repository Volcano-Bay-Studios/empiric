package xyz.volcanobay.empiric.api.network;

import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.module.ModuleEvent;

public final class NetworkEvents {

    /**
     * Fired on the network that was kept, with the network it absorbed. The absorbed one is detached right after.
     */
    public static final ModuleEvent<NetworkModule> MERGED = ModuleEvent.create(Empiric.id("network_merged")::toString);
    /**
     * Fired on the original network, with the network that split off from it.
     */
    public static final ModuleEvent<NetworkModule> SPLIT = ModuleEvent.create(Empiric.id("network_split")::toString);
    public static final ModuleEvent<Void> UPDATED = ModuleEvent.create(Empiric.id("network_updated")::toString);

    private NetworkEvents() {
    }
}

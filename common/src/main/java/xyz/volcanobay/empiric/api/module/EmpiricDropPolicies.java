package xyz.volcanobay.empiric.api.module;

import xyz.volcanobay.empiric.core.module.DropPolicy;

import java.util.List;

public final class EmpiricDropPolicies {

    public static final DropPolicy UNLOAD = DropPolicy.UNLOAD;
    public static final DropPolicy DEATH = new DropPolicy("empiric:death");
    public static final DropPolicy LEVEL_CHANGE = new DropPolicy("empiric:level_change");
    public static final DropPolicy INVENTORY_CLEAR = new DropPolicy("empiric:inventory_clear");

    public static final List<DropPolicy> ALL = List.of(UNLOAD, DEATH, LEVEL_CHANGE, INVENTORY_CLEAR);

    private EmpiricDropPolicies() {
    }
}

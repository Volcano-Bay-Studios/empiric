package xyz.volcanobay.empiric.api.part;

import net.minecraft.resources.ResourceLocation;

/**
 * Names one kind of trait a {@link PartType} can carry, and the class its config has. Get one from
 * {@link PartTraits#register}.
 */
public record TraitKey<T>(ResourceLocation id, Class<T> type) {

    @Override
    public String toString() {
        return id.toString();
    }
}

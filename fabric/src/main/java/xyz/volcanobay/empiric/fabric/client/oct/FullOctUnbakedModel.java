package xyz.volcanobay.empiric.fabric.client.oct;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class FullOctUnbakedModel implements UnbakedModel {

    @Override
    public Collection<ResourceLocation> getDependencies() {
        return List.of();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> resolver) {
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState state) {
        TextureAtlasSprite particleSprite = spriteGetter.apply(
                new Material(InventoryMenu.BLOCK_ATLAS, ResourceLocation.fromNamespaceAndPath("minecraft", "block/stone"))
        );
        return new FullOctBlockModel(particleSprite);
    }
}

package xyz.volcanobay.empiric.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.ArrayList;
import java.util.List;

@SelfSpec
public class SpecSample {

    @Data
    public ItemStack stack = new ItemStack(Items.DIAMOND, 3);
    @Data
    public BlockState state = Blocks.OAK_STAIRS.defaultBlockState();
    @Data
    public final Vector3d offset = new Vector3d();
    @Data
    public final List<Vector3d> path = new ArrayList<>();
    @Data
    public CompoundTag tag = new CompoundTag();
    @Data
    public ResourceLocation location = ResourceLocation.withDefaultNamespace("stone");
    @Data
    public Holder<Block> holder = Blocks.DIRT.builtInRegistryHolder();
    @Data
    public Block block = Blocks.GLASS;
    @Data
    public List<BlockPos> positions = new ArrayList<>();
    @Data
    public Vec3 motion = Vec3.ZERO;
}

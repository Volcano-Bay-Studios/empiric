package xyz.volcanobay.empiric.oct;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import xyz.volcanobay.empiric.api.oct.OctBlock;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctUtil;
import xyz.volcanobay.empiric.ext.LevelOctExtension;

public final class OctClientEffects {

    private static final RandomSource random = RandomSource.create();

    private OctClientEffects() {
    }

    public static void playBreak(Minecraft client, Level level, BlockPos pos, int octant, BlockState state) {
        OctLevel octLevel = LevelOctExtension.get(level);
        if (state.getBlock() instanceof OctBlock octBlock) {
            SoundType soundType = octBlock.getSoundType(state, octLevel, pos, octant, null);
            level.playLocalSound(pos, soundType.getBreakSound(), SoundSource.BLOCKS,
                    (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f, false);
        }
        spawnBreakParticles(client, state, level, pos, octant);
    }

    public static void spawnBreakParticles(Minecraft client, BlockState state, Level level, BlockPos pos, int octant) {
        OctLevel octLevel = LevelOctExtension.get(level);
        if (state.getBlock() instanceof OctBlock octBlock) {
            VoxelShape voxelshape = octBlock.getShape(state, octLevel, pos, octant, CollisionContext.empty());
            voxelshape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                double dx = Math.min(1.0f, x1 - x0);
                double dy = Math.min(1.0f, y1 - y0);
                double dz = Math.min(1.0f, z1 - z0);
                int cx = Math.max(2, Mth.ceil(dx / 0.25f));
                int cy = Math.max(2, Mth.ceil(dy / 0.25f));
                int cz = Math.max(2, Mth.ceil(dz / 0.25f));

                for (int i = 0; i < cx; ++i) {
                    for (int j = 0; j < cy; ++j) {
                        for (int k = 0; k < cz; ++k) {
                            double vx = ((double) i + (double) 0.5F) / (double) cx;
                            double vy = ((double) j + (double) 0.5F) / (double) cy;
                            double vz = ((double) k + (double) 0.5F) / (double) cz;
                            double px = vx * dx + x0 + ((octant & 1) != 0 ? 0.5f : 0);
                            double py = vy * dy + y0 + ((octant & 2) != 0 ? 0.5f : 0);
                            double pz = vz * dz + z0 + ((octant & 4) != 0 ? 0.5f : 0);
                            TerrainParticle particle = new TerrainParticle((ClientLevel) level,
                                    pos.getX() + px, pos.getY() + py, pos.getZ() + pz,
                                    vx - 0.5f, vy - 0.5f, vz - 0.5f,
                                    state, pos);
                            client.particleEngine.add(particle);
                        }
                    }
                }
            });
        }
    }

    public static boolean addHitEffects(ClientLevel level, BlockPos pos, Direction side, ParticleEngine manager) {
        if (!(Minecraft.getInstance().hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) {
            return false;
        }
        int octant = OctUtil.getOctant(hit);
        BlockState octState = LevelOctExtension.get(level).getOctState(pos, octant);
        if (!(octState.getBlock() instanceof OctBlock octBlock)) {
            return true;
        }
        if (octState.getRenderShape() != RenderShape.INVISIBLE && octState.shouldSpawnTerrainParticles()) {
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            AABB aabb = octBlock.getShape(
                    octState,
                    LevelOctExtension.get(level),
                    pos,
                    octant,
                    CollisionContext.empty()
            ).bounds();

            double px = x + random.nextDouble() * (aabb.maxX - aabb.minX - 0.2f) + 0.1f + aabb.minX + ((octant & 1) != 0 ? 0.5f : 0);
            double py = y + random.nextDouble() * (aabb.maxY - aabb.minY - 0.2f) + 0.1f + aabb.minY + ((octant & 2) != 0 ? 0.5f : 0);
            double pz = z + random.nextDouble() * (aabb.maxZ - aabb.minZ - 0.2f) + 0.1f + aabb.minZ + ((octant & 4) != 0 ? 0.5f : 0);
            double octX = ((octant & 1) != 0 ? 0.5f : 0);
            double octY = ((octant & 2) != 0 ? 0.5f : 0);
            double octZ = ((octant & 4) != 0 ? 0.5f : 0);
            if (side == Direction.DOWN) {
                py = y + aabb.minY + octY - 0.1f;
            }

            if (side == Direction.UP) {
                py = y + aabb.maxY + octY + 0.1f;
            }

            if (side == Direction.NORTH) {
                pz = z + aabb.minZ + octZ - 0.1f;
            }

            if (side == Direction.SOUTH) {
                pz = z + aabb.maxZ + octZ + 0.1f;
            }

            if (side == Direction.WEST) {
                px = x + aabb.minX + octX - 0.1f;
            }

            if (side == Direction.EAST) {
                px = x + aabb.maxX + octX + 0.1f;
            }

            TerrainParticle particle = new TerrainParticle(level, px, py, pz, 0, 0, 0, octState, pos);
            manager.add(particle.setPower(0.2f).scale(0.6f));
        }
        return true;
    }
}

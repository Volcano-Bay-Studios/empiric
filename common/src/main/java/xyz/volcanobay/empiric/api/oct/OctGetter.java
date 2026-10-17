package xyz.volcanobay.empiric.api.oct;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface OctGetter {
    BlockGetter blockGetter();

    /**
     * Get octant block state located at given position.
     *
     * @param pos    World position
     * @param octant Block octant position
     * @return Block state of an oct block
     */
    BlockState getOctState(BlockPos pos, int octant);

    /**
     * Get octant or full block state located at given position.
     *
     * @param pos    World position
     * @param octant Block octant position
     * @return Block state of regular or oct block
     */
    BlockState getBlockState(BlockPos pos, int octant);

    /**
     * Get octant entity located at given position.
     *
     * @param pos    World position
     * @param octant Block octant position
     * @return Oct entity or null
     */
    @Nullable OctBlockEntity getOctEntity(BlockPos pos, int octant);

    /**
     * Get octant block state located at given position.
     *
     * @param octPos Oct Position
     * @return Block state of an oct block
     */
    default BlockState getOctState(OctPos octPos) {
        return this.getOctState(octPos.pos(), octPos.octant());
    }

    /**
     * Get octant or full block state located at given position.
     *
     * @param octPos Oct Position
     * @return Block state of regular or oct block
     */
    default BlockState getBlockState(OctPos octPos) {
        return this.getBlockState(octPos.pos(), octPos.octant());
    }

    /**
     * Get octant entity located at given position.
     *
     * @param octPos Oct Position
     * @return Oct entity or null
     */
    default @Nullable OctBlockEntity getOctEntity(OctPos octPos) {
        return this.getOctEntity(octPos.pos(), octPos.octant());
    }

    /**
     * Get octant entity at given position and cast it to requested type.
     *
     * @param pos    World position
     * @param octant Block octant position
     * @param type   Type of entity
     * @param <T>    Entity type
     * @return Oct entity of requested type or empty optional if it doesn't exist or is of a different type.
     */
    default <T extends OctBlockEntity> Optional<T> getOctEntity(BlockPos pos, int octant, Class<T> type) {
        OctBlockEntity be = this.getOctEntity(pos, octant);
        if (type.isInstance(be)) {
            return Optional.of(type.cast(be));
        }
        return Optional.empty();
    }
}

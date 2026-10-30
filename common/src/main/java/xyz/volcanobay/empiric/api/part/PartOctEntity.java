package xyz.volcanobay.empiric.api.part;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.api.oct.OctBlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One octant of a block, storing the pieces whose minimum corner is in it.
 */
public class PartOctEntity extends OctBlockEntity {

    private final List<Piece> pieces = new ArrayList<>();
    private boolean settlePending;

    public PartOctEntity(BlockPos worldPosition, int octantPosition, BlockState blockState) {
        super(worldPosition, octantPosition, blockState);
    }

    public int octant() {
        return octantPosition;
    }

    public BlockPos pos() {
        return worldPosition;
    }

    public @Nullable Level level() {
        return level;
    }

    public List<Piece> pieces() {
        return Collections.unmodifiableList(pieces);
    }

    public boolean isEmpty() {
        return pieces.isEmpty();
    }

    public @Nullable Piece find(UUID id) {
        for (Piece piece : pieces) {
            if (piece.id.equals(id)) {
                return piece;
            }
        }
        return null;
    }

    void add(Piece piece) {
        pieces.add(piece);
    }

    void remove(UUID id) {
        pieces.removeIf(piece -> piece.id.equals(id));
    }

    /**
     * Saves and syncs it; call after changing a piece's stack or runtime status.
     */
    public void changed() {
        setChanged();
        sendData();
    }

    void requestSettle() {
        settlePending = true;
    }

    @Override
    public boolean isTicked() {
        return true;
    }

    @Override
    public void tick() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (settlePending) {
            settlePending = false;
            Placement.settle(level, worldPosition);
        }
        for (Piece piece : new ArrayList<>(pieces)) {
            for (PartTicker ticker : piece.type.hooks(PartTicker.class)) {
                ticker.tick(this, piece);
            }
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Piece piece : pieces) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("id", piece.id);
            entry.putString("type", piece.type.id().toString());
            entry.putByte("x", (byte) piece.x);
            entry.putByte("y", (byte) piece.y);
            entry.putByte("z", (byte) piece.z);
            entry.putByte("rotation", (byte) piece.rotation);
            entry.putInt("status", piece.runtime.status);
            if (!piece.stack.isEmpty()) {
                entry.put("stack", piece.stack.save(registries));
            }
            list.add(entry);
        }
        tag.put("pieces", list);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        List<Piece> loaded = new ArrayList<>();
        ListTag list = tag.getList("pieces", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                Piece piece = read(entry, registries);
                if (piece != null) {
                    Piece old = find(piece.id);
                    if (old != null) {
                        piece.runtime.copyFrom(old.runtime);
                    }
                    piece.runtime.status = entry.getInt("status");
                    loaded.add(piece);
                }
            } catch (RuntimeException e) {
                Empiric.LOGGER.warn("Skipping a part that couldn't be read at {}: {}", worldPosition, e.toString());
            }
        }
        pieces.clear();
        pieces.addAll(loaded);
    }

    private @Nullable Piece read(CompoundTag entry, HolderLookup.Provider registries) {
        PartType type = PartTypes.get(ResourceLocation.tryParse(entry.getString("type")));
        if (type == null) {
            Empiric.LOGGER.warn("Dropping unknown part {} at {}", entry.getString("type"), worldPosition);
            return null;
        }
        if (!entry.hasUUID("id")) {
            return null;
        }
        ItemStack stack = entry.contains("stack") ? ItemStack.parseOptional(registries, entry.getCompound("stack")) : ItemStack.EMPTY;
        if (entry.contains("anchor") && entry.getByte("anchor") != octantPosition && stack.isEmpty()) {
            return null;
        }
        if (stack.isEmpty()) {
            stack = type.newStack();
        }
        return new Piece(entry.getUUID("id"), type, entry.getByte("x"), entry.getByte("y"), entry.getByte("z"), entry.getByte("rotation"), stack);
    }
}

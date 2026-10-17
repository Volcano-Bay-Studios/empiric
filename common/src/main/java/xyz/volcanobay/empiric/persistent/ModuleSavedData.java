package xyz.volcanobay.empiric.persistent;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;
import xyz.volcanobay.empiric.system.WorldModuleSystem;

public class ModuleSavedData extends SavedData {

    private static final String NAME = "empiric_modules";

    private final ServerLevel level;
    private byte[] loaded;
    private byte[] statics;
    private byte[] server;
    private ListTag networks;
    private ListTag unknownNetworks = new ListTag();
    private boolean networksLoaded;

    private ModuleSavedData(ServerLevel level) {
        this.level = level;
    }

    public static ModuleSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(
                () -> new ModuleSavedData(level),
                (tag, registries) -> load(level, tag),
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE), NAME);
    }

    private static ModuleSavedData load(ServerLevel level, CompoundTag tag) {
        ModuleSavedData data = new ModuleSavedData(level);
        if (tag.contains("modules")) {
            data.loaded = tag.getByteArray("modules");
        }
        if (tag.contains("statics")) {
            data.statics = tag.getByteArray("statics");
        }
        if (tag.contains("server")) {
            data.server = tag.getByteArray("server");
        }
        if (tag.contains("networks")) {
            data.networks = tag.getList("networks", Tag.TAG_COMPOUND);
        }
        return data;
    }

    public byte[] takeLoaded() {
        byte[] bytes = loaded;
        loaded = null;
        return bytes;
    }

    public void loadNetworks() {
        if (networks != null) {
            unknownNetworks = SpatialNetworkSystem.load(level, networks);
            networks = null;
        }
        networksLoaded = true;
    }

    public byte[] takeServer() {
        byte[] bytes = server;
        server = null;
        return bytes;
    }

    public byte[] takeStatics() {
        byte[] bytes = statics;
        statics = null;
        return bytes;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        LevelAttachment attachment = LevelAttachment.of(level);
        if (loaded != null) {
            tag.putByteArray("modules", loaded);
        } else if (!attachment.modules().isEmpty()) {
            tag.putByteArray("modules", WorldModuleSystem.encode(attachment));
        }
        if (networks != null) {
            tag.put("networks", networks);
        } else if (networksLoaded) {
            ListTag saved = SpatialNetworkSystem.save(level, unknownNetworks);
            if (!saved.isEmpty()) {
                tag.put("networks", saved);
            }
        }
        if (level.dimension() == Level.OVERWORLD) {
            tag.putByteArray("statics", statics != null ? statics : WorldModuleSystem.encodeStatics());
            ServerAttachment global = ServerAttachment.of(Side.SERVER);
            if (server != null) {
                tag.putByteArray("server", server);
            } else if (!global.modules().isEmpty()) {
                tag.putByteArray("server", WorldModuleSystem.encode(global));
            }
        }
        return tag;
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}

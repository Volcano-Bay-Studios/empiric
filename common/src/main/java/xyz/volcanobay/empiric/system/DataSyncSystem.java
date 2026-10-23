package xyz.volcanobay.empiric.system;

import com.google.gson.JsonParser;
import xyz.volcanobay.empiric.api.network.PacketTarget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntryType;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.data.JsonNodes;
import xyz.volcanobay.empiric.networking.packet.DataEntriesS2CPacket;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataSyncSystem {

    private DataSyncSystem() {
    }

    public static List<DataEntriesS2CPacket> packets() {
        List<DataEntriesS2CPacket> packets = new ArrayList<>();
        for (DataEntryType<?> type : EmpiricCore.data().types()) {
            packets.add(new DataEntriesS2CPacket(DataEntriesS2CPacket.ENTRIES, type.id(), encode(type)));
        }
        return packets;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, String> encode(DataEntryType type) {
        Map<String, String> encoded = new LinkedHashMap<>();
        for (DataEntry<?> entry : (Iterable<DataEntry<?>>) type.entries()) {
            DataNode node = type.write(entry.value());
            if (entry.extras() != null) {
                entry.extras().entries().forEach(node::put);
            }
            encoded.put(entry.id(), JsonNodes.toJson(node).toString());
        }
        return encoded;
    }

    public static void syncTo(PacketTarget sink) {
        for (DataEntriesS2CPacket packet : packets()) {
            sink.send(packet);
        }
    }

    public static void broadcast() {
        MinecraftServer server = WorldModuleSystem.server();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                syncTo(PacketTarget.player(player));
            } catch (UnsupportedOperationException e) {
                continue;
            }
        }
    }

    public static void apply(DataEntriesS2CPacket packet) {
        DataEntryType<?> type = EmpiricCore.data().type(packet.target());
        if (type == null) {
            Empiric.LOGGER.warn("The server sent data for unknown entry type {}", packet.target());
            return;
        }
        Map<String, DataNode> files = new LinkedHashMap<>();
        packet.entries().forEach((id, json) -> files.put(id, JsonNodes.toNode(JsonParser.parseString(json))));
        type.load(files).forEach((id, error) -> Empiric.LOGGER.error("Couldn't load synced {} entry {}: {}", type.id(), id, error.getMessage()));
    }
}

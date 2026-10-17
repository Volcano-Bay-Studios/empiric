package xyz.volcanobay.empiric.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.system.DataSyncSystem;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.data.tree.DataNode;
import xyz.volcanobay.empiric.core.entry.DataEntryType;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DataEntryLoader implements PreparableReloadListener {

    public static final DataEntryLoader INSTANCE = new DataEntryLoader();

    private DataEntryLoader() {
    }

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> prepare(manager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(DataEntryLoader::apply, gameExecutor);
    }

    private static Map<DataEntryType<?>, Map<String, DataNode>> prepare(ResourceManager manager) {
        Map<DataEntryType<?>, Map<String, DataNode>> prepared = new HashMap<>();
        for (DataEntryType<?> type : EmpiricCore.data().types()) {
            FileToIdConverter converter = FileToIdConverter.json(type.directory());
            Map<String, DataNode> files = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> resource : converter.listMatchingResources(manager).entrySet()) {
                ResourceLocation id = converter.fileToId(resource.getKey());
                try (Reader reader = resource.getValue().openAsReader()) {
                    JsonReader json = new JsonReader(reader);
                    json.setLenient(true);
                    JsonElement element = JsonParser.parseReader(json);
                    files.put(id.toString(), JsonNodes.toNode(element));
                } catch (Exception e) {
                    Empiric.LOGGER.error("Couldn't read {} entry {}", type.id(), id, e);
                }
            }
            prepared.put(type, files);
        }
        return prepared;
    }

    private static void apply(Map<DataEntryType<?>, Map<String, DataNode>> prepared) {
        prepared.forEach((type, files) -> {
            Map<String, Exception> errors = type.load(files);
            errors.forEach((id, error) -> Empiric.LOGGER.error("Couldn't load {} entry {}: {}", type.id(), id, error.getMessage()));
            Empiric.LOGGER.info("Loaded {} {} entries", type.entries().size(), type.id());
        });
        DataSyncSystem.broadcast();
    }

    @Override
    public String getName() {
        return "EmpiricDataEntries";
    }
}

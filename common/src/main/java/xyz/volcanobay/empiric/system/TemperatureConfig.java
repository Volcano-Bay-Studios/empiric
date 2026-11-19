package xyz.volcanobay.empiric.system;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import xyz.volcanobay.empiric.Empiric;
import xyz.volcanobay.empiric.gametest.EmpiricTestModules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class TemperatureConfig {

    public static final double DEFAULT_TIME_SCALE = 1000;
    public static final double MAX_TIME_SCALE = 1_000_000;
    private static final String FILE = "config/empiric.json";
    private static final Gson GSON = new Gson();

    private static double defaultScale = DEFAULT_TIME_SCALE;
    private static Map<ResourceLocation, Double> dimensions = new HashMap<>();
    private static Path file;

    private TemperatureConfig() {
    }

    public static void load(MinecraftServer server) {
        file = null;
        dimensions = new HashMap<>();
        if (EmpiricTestModules.enabled()) {
            defaultScale = 1;
            return;
        }
        defaultScale = DEFAULT_TIME_SCALE;
        file = server.getServerDirectory().resolve(FILE);
        if (!Files.exists(file)) {
            save();
            return;
        }
        try {
            parse(Files.readString(file));
        } catch (IOException exception) {
            Empiric.LOGGER.error("Could not read {}, using the default temperature settings", file, exception);
        }
    }

    public static double timeScale(ResourceLocation dimension) {
        Double scale = dimensions.get(dimension);
        return scale != null ? scale : defaultScale;
    }

    public static boolean hasDimension(ResourceLocation dimension) {
        return dimensions.containsKey(dimension);
    }

    public static void setDefault(double scale) {
        defaultScale = clamp(scale);
        save();
    }

    public static void setDimension(ResourceLocation dimension, Double scale) {
        if (scale == null) {
            dimensions.remove(dimension);
        } else {
            dimensions.put(dimension, clamp(scale));
        }
        save();
    }

    @VisibleForTesting
    public static void parse(String json) {
        defaultScale = DEFAULT_TIME_SCALE;
        dimensions = new HashMap<>();
        double parsedDefault = defaultScale;
        Map<ResourceLocation, Double> parsedDimensions = new HashMap<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonElement temperature = root.getAsJsonObject().get("temperature");
            if (temperature == null) {
                return;
            }
            JsonObject section = temperature.getAsJsonObject();
            if (section.has("timeScale")) {
                Double value = entry("timeScale", section.get("timeScale"));
                if (value != null) {
                    parsedDefault = value;
                }
            }
            if (section.has("dimensions")) {
                for (Map.Entry<String, JsonElement> entry : section.getAsJsonObject("dimensions").entrySet()) {
                    ResourceLocation dimension = ResourceLocation.tryParse(entry.getKey());
                    if (dimension == null) {
                        Empiric.LOGGER.error("Ignoring the temperature dimension \"{}\" in {}, it is not a valid id", entry.getKey(), FILE);
                        continue;
                    }
                    Double value = entry(entry.getKey(), entry.getValue());
                    if (value != null) {
                        parsedDimensions.put(dimension, value);
                    }
                }
            }
        } catch (RuntimeException exception) {
            Empiric.LOGGER.error("Could not read {}, using the default temperature settings", FILE, exception);
            return;
        }
        defaultScale = parsedDefault;
        dimensions = parsedDimensions;
    }

    private static Double entry(String name, JsonElement element) {
        double value;
        try {
            value = element.getAsDouble();
        } catch (RuntimeException exception) {
            Empiric.LOGGER.error("Ignoring the temperature time scale \"{}\" in {}, it is not a number", name, FILE);
            return null;
        }
        if (Double.isNaN(value) || value < 0) {
            Empiric.LOGGER.error("Ignoring the temperature time scale \"{}\" in {}, it must be 0 or more", name, FILE);
            return null;
        }
        return clamp(value);
    }

    private static double clamp(double scale) {
        if (Double.isNaN(scale)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_TIME_SCALE, scale));
    }

    private static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private static void save() {
        if (file == null) {
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("{\n");
        text.append("  \"temperature\": {\n");
        text.append("    // Seconds of heat transfer per real second. 1 is real life. At 1000, dirt and stone visibly warm their neighbours.\n");
        text.append("    // Higher values cost more substeps for good conductors such as copper (capped at 64 per tick).\n");
        text.append("    \"timeScale\": ").append(number(defaultScale)).append(",\n");
        text.append("    // Per-dimension overrides, by dimension id.\n");
        text.append("    \"dimensions\": {");
        if (!dimensions.isEmpty()) {
            text.append('\n');
            Map<String, Double> sorted = new TreeMap<>();
            for (Map.Entry<ResourceLocation, Double> entry : dimensions.entrySet()) {
                sorted.put(entry.getKey().toString(), entry.getValue());
            }
            int remaining = sorted.size();
            for (Map.Entry<String, Double> entry : sorted.entrySet()) {
                text.append("      ").append(GSON.toJson(entry.getKey())).append(": ").append(number(entry.getValue())).append(--remaining > 0 ? ",\n" : "\n");
            }
            text.append("    ");
        } else {
            text.append("\n    ");
        }
        text.append("}\n");
        text.append("  }\n");
        text.append("}\n");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text.toString());
        } catch (IOException exception) {
            Empiric.LOGGER.error("Could not write {}", file, exception);
        }
    }
}

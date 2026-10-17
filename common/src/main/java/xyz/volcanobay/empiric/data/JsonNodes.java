package xyz.volcanobay.empiric.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import xyz.volcanobay.empiric.core.data.tree.DataNode;

import java.util.Map;

public final class JsonNodes {

    private JsonNodes() {
    }

    public static DataNode toNode(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return DataNode.NULL;
        }
        if (element.isJsonObject()) {
            DataNode node = DataNode.object();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                node.put(entry.getKey(), toNode(entry.getValue()));
            }
            return node;
        }
        if (element.isJsonArray()) {
            DataNode node = DataNode.array();
            for (JsonElement child : element.getAsJsonArray()) {
                node.add(toNode(child));
            }
            return node;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return DataNode.of(primitive.getAsBoolean());
        }
        if (primitive.isNumber()) {
            return DataNode.of(primitive.getAsNumber());
        }
        return DataNode.of(primitive.getAsString());
    }

    public static JsonElement toJson(DataNode node) {
        return switch (node.kind()) {
            case OBJECT -> {
                JsonObject object = new JsonObject();
                node.entries().forEach((key, value) -> object.add(key, toJson(value)));
                yield object;
            }
            case ARRAY -> {
                JsonArray array = new JsonArray();
                node.elements().forEach(value -> array.add(toJson(value)));
                yield array;
            }
            case STRING -> new JsonPrimitive(node.asString());
            case NUMBER -> new JsonPrimitive(node.asNumber());
            case BOOLEAN -> new JsonPrimitive(node.asBoolean());
            case NULL -> JsonNull.INSTANCE;
        };
    }
}

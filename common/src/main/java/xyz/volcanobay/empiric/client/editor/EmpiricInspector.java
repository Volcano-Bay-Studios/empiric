package xyz.volcanobay.empiric.client.editor;

import foundry.veil.api.client.editor.SingleWindowInspector;
import imgui.ImGui;
import imgui.flag.ImGuiTreeNodeFlags;
import imgui.type.ImInt;
import net.minecraft.network.chat.Component;
import xyz.volcanobay.empiric.client.overlay.NetworkOverlay;
import xyz.volcanobay.empiric.client.overlay.TemperatureOverlay;

import java.util.Map;
import java.util.TreeMap;

/**
 * Veil editor window for the client side debug overlays. Needs ImGuiMC, register it only when
 * {@code Veil.IMGUIMC} is set.
 */
public final class EmpiricInspector extends SingleWindowInspector {

    private static final Component TITLE = Component.literal("Empiric");
    private static final int MAX_RADIUS = 128;

    private final ImInt temperatureRadius = new ImInt();

    @Override
    public Component getDisplayName() {
        return TITLE;
    }

    @Override
    protected void renderComponents() {
        if (ImGui.collapsingHeader("Temperature overlay", ImGuiTreeNodeFlags.DefaultOpen)) {
            TemperatureOverlay temperature = TemperatureOverlay.INSTANCE;
            temperatureRadius.set(temperature.radius());
            if (ImGui.sliderInt("Radius (blocks)", temperatureRadius.getData(), 0, MAX_RADIUS)) {
                temperature.show(temperatureRadius.get());
            }
            ImGui.text("Meshes: " + temperature.meshCount());
            if (ImGui.button("Hide##temperature")) {
                temperature.show(0);
            }
        }
        if (ImGui.collapsingHeader("Network overlays", ImGuiTreeNodeFlags.DefaultOpen)) {
            Map<String, Integer> counts = new TreeMap<>(NetworkOverlay.INSTANCE.counts());
            if (counts.isEmpty()) {
                ImGui.textDisabled("None shown. Use /empiric network to show one.");
            }
            for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                ImGui.text(entry.getKey() + ": " + entry.getValue() + " volumes");
            }
            if (!counts.isEmpty() && ImGui.button("Clear##network")) {
                NetworkOverlay.INSTANCE.clear();
            }
        }
    }
}

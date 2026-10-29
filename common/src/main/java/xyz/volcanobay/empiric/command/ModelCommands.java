package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.model.EmpiricModel;
import xyz.volcanobay.empiric.api.model.EmpiricModels;
import xyz.volcanobay.empiric.model.ModelStats;

import java.util.List;
import java.util.Map;

public final class ModelCommands {

    private ModelCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("model")
                .then(Commands.literal("list").executes(ModelCommands::list));
    }

    /**
     * Load and draw figures come from the client, so on a dedicated server models only ever read as not loaded.
     */
    private static int list(CommandContext<CommandSourceStack> context) {
        List<EmpiricModel> models = EmpiricModels.all();
        Map<ResourceLocation, ModelStats.Info> stats = ModelStats.models();
        EmpiricCommands.send(context, EmpiricCommands.label(models.size() + " models, " + ModelStats.instances()
                + " instances in " + ModelStats.drawCalls() + " draws last frame"));
        for (EmpiricModel model : models) {
            ModelStats.Info info = stats.get(model.id());
            MutableComponent line = Component.literal(" " + model.id() + (model.isObj() ? " (obj) " : " "));
            if (info == null) {
                line.append(Component.literal("not loaded").withStyle(ChatFormatting.DARK_GRAY));
            } else if (info.failed()) {
                line.append(EmpiricCommands.hover("failed", "The reason is in the log", ChatFormatting.RED));
            } else {
                line.append(EmpiricCommands.label(info.parts() + " parts, " + info.meshes() + " meshes, " + info.vertices() + " vertices"));
            }
            EmpiricCommands.send(context, line);
        }
        return models.size();
    }
}

package xyz.volcanobay.empiric.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.data.BlockDataType;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntryType;
import xyz.volcanobay.empiric.data.JsonNodes;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class DataCommands {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.literal("No data entry type " + id));
    private static final SuggestionProvider<CommandSourceStack> TYPES = (context, builder) ->
            SharedSuggestionProvider.suggest(EmpiricCore.data().types().stream().map(DataEntryType::id), builder);
    private static final SuggestionProvider<CommandSourceStack> ENTRIES = (context, builder) -> {
        DataEntryType<?> type = EmpiricCore.data().type(ResourceLocationArgument.getId(context, "type").toString());
        return SharedSuggestionProvider.suggest(type == null ? Set.of() : type.entries().stream().map(DataEntry::id).toList(), builder);
    };

    private DataCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("data")
                .executes(DataCommands::types)
                .then(Commands.literal("block").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(DataCommands::block)))
                .then(Commands.literal("type").then(Commands.argument("type", ResourceLocationArgument.id()).suggests(TYPES)
                        .executes(DataCommands::entries)
                        .then(Commands.argument("entry", ResourceLocationArgument.id()).suggests(ENTRIES)
                                .executes(DataCommands::entry))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String json(DataEntryType type, Object value) {
        return GSON.toJson(JsonNodes.toJson(type.write(value)));
    }

    private static DataEntryType<?> type(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = ResourceLocationArgument.getId(context, "type").toString();
        DataEntryType<?> type = EmpiricCore.data().type(id);
        if (type == null) {
            throw UNKNOWN.create(id);
        }
        return type;
    }

    private static int types(CommandContext<CommandSourceStack> context) {
        List<DataEntryType<?>> types = EmpiricCore.data().types();
        MutableComponent line = EmpiricCommands.label("Data entry types (" + types.size() + "): ");
        for (DataEntryType<?> type : types) {
            line.append(EmpiricCommands.hover(type.id(), "data/<namespace>/" + type.directory() + "/*.json\ndefault " + json(type, type.defaultValue()), ChatFormatting.AQUA))
                    .append(EmpiricCommands.label(" " + type.entries().size() + "  "));
        }
        EmpiricCommands.send(context, line);
        return types.size();
    }

    private static int entries(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        DataEntryType<?> type = type(context);
        List<? extends DataEntry<?>> entries = type.entries().stream().sorted(Comparator.comparing(DataEntry::id)).toList();
        MutableComponent line = EmpiricCommands.label(type.id() + " (" + entries.size() + "): ");
        for (DataEntry<?> entry : entries) {
            line.append(EmpiricCommands.hover(entry.id() + " ", json(type, entry.value()) + "\n" + extras(entry), ChatFormatting.AQUA));
        }
        EmpiricCommands.send(context, line);
        return entries.size();
    }

    private static int entry(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        DataEntryType<?> type = type(context);
        String id = ResourceLocationArgument.getId(context, "entry").toString();
        DataEntry<?> entry = type.entry(id);
        if (entry == null) {
            context.getSource().sendFailure(Component.literal(type.id() + " has no entry " + id));
            return 0;
        }
        EmpiricCommands.send(context, EmpiricCommands.label(type.id() + " " + id + ":"));
        EmpiricCommands.send(context, Component.literal(json(type, entry.value())).withStyle(ChatFormatting.WHITE));
        EmpiricCommands.send(context, Component.literal(extras(entry)).withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    private static int block(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BlockState state = context.getSource().getLevel().getBlockState(pos);
        List<BlockDataType<?>> types = BlockDataType.types();
        EmpiricCommands.send(context, EmpiricCommands.label("Block data for " + state.getBlock().getName().getString() + " at " + EmpiricCommands.describe(pos)));
        for (BlockDataType<?> type : types) {
            Object value = type.get(state);
            boolean fallback = value == type.defaultValue();
            EmpiricCommands.send(context, Component.literal(" " + type.entries().id() + " ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(json(type.entries(), value)).withStyle(fallback ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE))
                    .append(EmpiricCommands.label(fallback ? " (default)" : "")));
        }
        return types.size();
    }

    private static String extras(DataEntry<?> entry) {
        return entry.extras() == null ? "" : GSON.toJson(JsonNodes.toJson(entry.extras()));
    }
}

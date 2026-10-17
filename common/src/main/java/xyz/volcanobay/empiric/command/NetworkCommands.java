package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import xyz.volcanobay.empiric.api.network.NetworkAttachment;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.network.SpatialNetworkMap;
import xyz.volcanobay.empiric.api.network.SpatialNetworkType;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.system.NetworkOverlaySystem;
import xyz.volcanobay.empiric.system.SpatialNetworkSystem;

import java.util.List;
import java.util.function.Function;

public final class NetworkCommands {

    private static final int LIST_LIMIT = 20;
    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.literal("No spatial network type " + id));
    private static final SuggestionProvider<CommandSourceStack> TYPES = (context, builder) ->
            SharedSuggestionProvider.suggest(SpatialNetworkSystem.types().stream().map(type -> type.id().toString()), builder);

    private NetworkCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("network")
                .executes(NetworkCommands::types)
                .then(Commands.argument("type", ResourceLocationArgument.id()).suggests(TYPES)
                        .executes(NetworkCommands::list)
                        .then(octTarget("get", NetworkCommands::get))
                        .then(octTarget("show", NetworkCommands::show).executes(context -> show(context, null)))
                        .then(Commands.literal("hide").executes(NetworkCommands::hide))
                        .then(octTarget("update", NetworkCommands::update)));
    }

    static ArgumentBuilder<CommandSourceStack, ?> octTarget(String name, OctCommand command) {
        return Commands.literal(name).then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(context -> command.run(context, new OctPos(BlockPosArgument.getLoadedBlockPos(context, "pos"), 0)))
                .then(Commands.argument("octant", IntegerArgumentType.integer(0, 7))
                        .executes(context -> command.run(context, new OctPos(BlockPosArgument.getLoadedBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "octant"))))));
    }

    private static SpatialNetworkType<?> type(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = ResourceLocationArgument.getId(context, "type").toString();
        for (SpatialNetworkType<?> type : SpatialNetworkSystem.types()) {
            if (type.id().toString().equals(id)) {
                EmpiricCommands.ensureEnabled(context.getSource(), type.moduleType());
                return type;
            }
        }
        throw UNKNOWN.create(id);
    }

    private static MutableComponent describe(NetworkModule network) {
        int members = network.members().size();
        return EmpiricCommands.hover("#" + network.id(), EmpiricCommands.print(network), ChatFormatting.AQUA)
                .append(EmpiricCommands.label(" " + members + " octs"));
    }

    private static int types(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        EmpiricCommands.send(context, EmpiricCommands.label("Spatial network types (" + SpatialNetworkSystem.types().size() + ")"));
        for (SpatialNetworkType<?> type : SpatialNetworkSystem.types()) {
            int count = type.get(level).attachments().size();
            EmpiricCommands.send(context, Component.literal(" ")
                    .append(Component.literal((type.isEnabled() ? "● " : "○ ") + type.id()).withStyle(type.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY))
                    .append(EmpiricCommands.label("  " + count + " networks here")));
        }
        return SpatialNetworkSystem.types().size();
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        SpatialNetworkType<?> type = type(context);
        SpatialNetworkMap<?> map = type.get(context.getSource().getLevel());
        List<NetworkAttachment> attachments = map.attachments();
        EmpiricCommands.send(context, EmpiricCommands.label(type.id() + " networks (" + attachments.size() + ")"));
        int shown = 0;
        for (NetworkAttachment attachment : attachments) {
            NetworkModule network = attachment.modules().get(type.moduleType());
            if (network == null) {
                continue;
            }
            if (shown++ >= LIST_LIMIT) {
                EmpiricCommands.send(context, EmpiricCommands.label(" and " + (attachments.size() - LIST_LIMIT) + " more"));
                break;
            }
            EmpiricCommands.send(context, Component.literal(" ").append(describe(network)));
        }
        return attachments.size();
    }

    private static NetworkModule find(CommandContext<CommandSourceStack> context, OctPos pos, Function<SpatialNetworkMap<?>, NetworkModule> lookup) throws CommandSyntaxException {
        SpatialNetworkType<?> type = type(context);
        NetworkModule network = lookup.apply(type.get(context.getSource().getLevel()));
        if (network == null) {
            context.getSource().sendFailure(Component.literal("No " + type.id() + " network at " + EmpiricCommands.describe(pos.pos()) + " octant " + pos.octant()));
        }
        return network;
    }

    private static int get(CommandContext<CommandSourceStack> context, OctPos pos) throws CommandSyntaxException {
        NetworkModule network = find(context, pos, map -> map.getNetwork(pos));
        if (network == null) {
            return 0;
        }
        EmpiricCommands.send(context, EmpiricCommands.label("Network at " + EmpiricCommands.describe(pos.pos()) + ": ").append(describe(network)));
        return network.id();
    }

    private static int show(CommandContext<CommandSourceStack> context, OctPos pos) throws CommandSyntaxException {
        SpatialNetworkType<?> type = type(context);
        if (pos != null) {
            NetworkModule network = find(context, pos, map -> map.getNetwork(pos));
            if (network == null) {
                return 0;
            }
            int count = NetworkOverlaySystem.show(context.getSource().getPlayerOrException(), type, pos);
            EmpiricCommands.send(context, EmpiricCommands.label("Showing " + count + " octs of ").append(describe(network)));
            return count;
        }
        int count = NetworkOverlaySystem.show(context.getSource().getPlayerOrException(), type, null);
        EmpiricCommands.send(context, EmpiricCommands.label("Showing " + count + " " + type.id() + " octs within " + NetworkOverlaySystem.RADIUS + " blocks"
                + (count >= NetworkOverlaySystem.MEMBER_LIMIT ? " (limit reached)" : "")));
        return count;
    }

    private static int hide(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        SpatialNetworkType<?> type = type(context);
        if (!NetworkOverlaySystem.hide(context.getSource().getPlayerOrException(), type)) {
            context.getSource().sendFailure(Component.literal("Not showing " + type.id() + " networks"));
            return 0;
        }
        EmpiricCommands.send(context, EmpiricCommands.label("Hid " + type.id() + " networks"));
        return 1;
    }

    private static int update(CommandContext<CommandSourceStack> context, OctPos pos) throws CommandSyntaxException {
        SpatialNetworkType<?> type = type(context);
        SpatialNetworkMap<?> map = type.get(context.getSource().getLevel());
        map.updatePoint(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        NetworkModule network = map.getNetworkSoft(pos.getLongX(), pos.getLongY(), pos.getLongZ());
        MutableComponent line = EmpiricCommands.label("Updated " + EmpiricCommands.describe(pos.pos()) + " octant " + pos.octant() + ", ");
        line.append(network == null ? EmpiricCommands.label("no network there now") : describe(network));
        MutableComponent result = line;
        context.getSource().sendSuccess(() -> result, true);
        return network == null ? 0 : 1;
    }

    @FunctionalInterface
    interface OctCommand {
        int run(CommandContext<CommandSourceStack> context, OctPos pos) throws CommandSyntaxException;
    }
}

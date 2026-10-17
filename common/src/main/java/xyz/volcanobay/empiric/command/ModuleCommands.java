package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.ModuleSystem;
import xyz.volcanobay.empiric.core.module.ModuleType;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.system.DebugSubscriptionSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ModuleCommands {

    private static final DynamicCommandExceptionType UNKNOWN = new DynamicCommandExceptionType(id -> Component.literal("No module type " + id));
    private static final SuggestionProvider<CommandSourceStack> SUBSCRIBERS = (context, builder) -> {
        ModuleType<?, ?> type = EmpiricCore.modules().type(ResourceLocationArgument.getId(context, "module").toString());
        return SharedSuggestionProvider.suggest(type == null ? Set.of() : EmpiricCore.modules().subscribers(type), builder);
    };

    private ModuleCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("modules")
                .executes(context -> list(context, false))
                .then(Commands.literal("subscribed").executes(context -> list(context, true)))
                .then(Commands.argument("module", ResourceLocationArgument.id()).suggests(EmpiricCommands.MODULE_SUGGESTIONS)
                        .executes(ModuleCommands::info)
                        .then(Commands.literal("subscribe")
                                .executes(context -> subscribe(context, DebugSubscriptionSystem.SUBSCRIBER))
                                .then(Commands.argument("subscriber", StringArgumentType.word())
                                        .executes(context -> subscribe(context, StringArgumentType.getString(context, "subscriber")))))
                        .then(Commands.literal("unsubscribe")
                                .executes(context -> unsubscribe(context, DebugSubscriptionSystem.SUBSCRIBER))
                                .then(Commands.argument("subscriber", StringArgumentType.word()).suggests(SUBSCRIBERS)
                                        .executes(context -> unsubscribe(context, StringArgumentType.getString(context, "subscriber")))))
                        .then(Commands.literal("disable").executes(ModuleCommands::disable)));
    }

    static ModuleType<?, ?> type(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String id = ResourceLocationArgument.getId(context, "module").toString();
        ModuleType<?, ?> type = EmpiricCore.modules().type(id);
        if (type == null) {
            throw UNKNOWN.create(id);
        }
        return type;
    }

    private static String tooltip(ModuleType<?, ?> type) {
        ModuleSystem modules = EmpiricCore.modules();
        Set<String> subscribers = modules.subscribers(type);
        return type.id()
                + "\nattaches to " + type.attachmentType().id()
                + "\nbehaviours " + type.behaviors()
                + "\nsubscribers " + (subscribers.isEmpty() ? "none" : String.join(", ", subscribers))
                + (DebugSubscriptionSystem.suppressed(type).isEmpty() ? "" : "\ndisabled by command for " + String.join(", ", DebugSubscriptionSystem.suppressed(type)))
                + "\nserver " + modules.count(Side.SERVER, type) + ", client " + modules.count(Side.CLIENT, type);
    }

    private static MutableComponent line(ModuleType<?, ?> type) {
        ModuleSystem modules = EmpiricCore.modules();
        Set<String> subscribers = modules.subscribers(type);
        MutableComponent line = EmpiricCommands.hover((type.isEnabled() ? "● " : "○ ") + type.id(), tooltip(type), type.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY);
        line.append(EmpiricCommands.label("  " + modules.count(Side.SERVER, type) + " loaded"));
        if (!subscribers.isEmpty()) {
            line.append(Component.literal("  " + String.join(", ", subscribers)).withStyle(ChatFormatting.WHITE));
        }
        return line;
    }

    private static int list(CommandContext<CommandSourceStack> context, boolean subscribedOnly) {
        List<ModuleType<?, ?>> types = EmpiricCore.modules().types().stream()
                .filter(type -> !subscribedOnly || type.isEnabled())
                .toList();
        EmpiricCommands.send(context, EmpiricCommands.label((subscribedOnly ? "Subscribed modules" : "Modules") + " (" + types.size() + ")"));
        if (types.isEmpty()) {
            EmpiricCommands.send(context, Component.literal(" none").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (ModuleType<?, ?> type : types) {
            EmpiricCommands.send(context, Component.literal(" ").append(line(type)));
        }
        return types.size();
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ModuleType<?, ?> type = type(context);
        EmpiricCommands.send(context, line(type));
        EmpiricCommands.send(context, EmpiricCommands.label(" attaches to " + type.attachmentType().id() + ", behaviours " + type.behaviors()));
        return type.isEnabled() ? 1 : 0;
    }

    private static int subscribe(CommandContext<CommandSourceStack> context, String subscriber) throws CommandSyntaxException {
        ModuleType<?, ?> type = type(context);
        boolean changed = DebugSubscriptionSystem.subscribe(type, subscriber);
        if (!changed) {
            context.getSource().sendFailure(Component.literal(subscriber + " is already subscribed to " + type.id()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Subscribed " + subscriber + " to ").append(line(type)), true);
        return 1;
    }

    private static int unsubscribe(CommandContext<CommandSourceStack> context, String subscriber) throws CommandSyntaxException {
        ModuleType<?, ?> type = type(context);
        boolean changed = DebugSubscriptionSystem.unsubscribe(type, subscriber);
        if (!changed) {
            context.getSource().sendFailure(Component.literal(subscriber + " isn't subscribed to " + type.id()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Unsubscribed " + subscriber + " from ").append(line(type)), true);
        return 1;
    }

    private static int disable(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ModuleType<?, ?> type = type(context);
        Set<String> subscribers = EmpiricCore.modules().subscribers(type);
        if (subscribers.isEmpty()) {
            context.getSource().sendFailure(Component.literal(type.id() + " is already disabled"));
            return 0;
        }
        int removed = 0;
        List<String> required = new ArrayList<>();
        for (String subscriber : subscribers) {
            if (subscriber.startsWith("required_by:")) {
                required.add(subscriber.substring("required_by:".length()));
                continue;
            }
            if (DebugSubscriptionSystem.unsubscribe(type, subscriber)) {
                removed++;
            }
        }
        int count = removed;
        context.getSource().sendSuccess(() -> Component.literal("Removed " + count + " subscribers from ").append(line(type)), true);
        if (!required.isEmpty()) {
            context.getSource().sendFailure(Component.literal(type.id() + " stays enabled because " + String.join(", ", required) + " requires it, disable those first"));
        }
        return removed;
    }
}

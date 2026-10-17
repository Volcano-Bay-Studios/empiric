package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import xyz.volcanobay.empiric.api.attachment.EntityAttachment;
import xyz.volcanobay.empiric.api.attachment.LevelAttachment;
import xyz.volcanobay.empiric.api.attachment.ServerAttachment;
import xyz.volcanobay.empiric.api.module.EmpiricDropPolicies;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.attachment.Attachment;
import xyz.volcanobay.empiric.core.module.DropPolicy;
import xyz.volcanobay.empiric.core.module.Side;

public final class DropCommands {

    private DropCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("drops")
                .then(target(Commands.literal("server"), context -> ServerAttachment.of(Side.SERVER)))
                .then(target(Commands.literal("level"), context -> LevelAttachment.of(context.getSource().getLevel())))
                .then(Commands.literal("entity").then(target(Commands.argument("target", EntityArgument.entity()),
                        context -> EntityAttachment.of(EntityArgument.getEntity(context, "target")))));
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T target(T builder, Target target) {
        return builder.executes(context -> list(context, target.get(context)))
                .then(Commands.argument("policy", StringArgumentType.greedyString())
                        .suggests((context, suggestions) -> SharedSuggestionProvider.suggest(EmpiricDropPolicies.ALL.stream().map(DropPolicy::id), suggestions))
                        .executes(context -> drop(context, target.get(context))));
    }

    private static int list(CommandContext<CommandSourceStack> context, Attachment attachment) {
        MutableComponent line = EmpiricCommands.label("Drop policies: ");
        int[] count = {0};
        attachment.modules().forEach(module -> {
            boolean kept = module.type().drops().isEmpty();
            line.append(EmpiricCommands.hover(module.type().id() + " ", kept ? "never dropped" : "drops on " + module.type().drops(), kept ? ChatFormatting.GREEN : ChatFormatting.AQUA));
            count[0]++;
        });
        if (count[0] == 0) {
            line.append(Component.literal("no modules").withStyle(ChatFormatting.DARK_GRAY));
        }
        EmpiricCommands.send(context, line);
        return count[0];
    }

    private static int drop(CommandContext<CommandSourceStack> context, Attachment attachment) {
        String id = StringArgumentType.getString(context, "policy");
        DropPolicy policy = EmpiricDropPolicies.ALL.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (policy == null) {
            context.getSource().sendFailure(Component.literal("Unknown drop policy " + id));
            return 0;
        }
        int dropped = EmpiricCore.modules().drop(attachment, policy);
        EmpiricCommands.send(context, Component.literal("Dropped " + dropped + " module(s) on " + policy));
        return dropped;
    }

    @FunctionalInterface
    private interface Target {
        Attachment get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
}

package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import xyz.volcanobay.empiric.core.data.spec.ObjectSpec;
import xyz.volcanobay.empiric.core.module.Module;
import xyz.volcanobay.empiric.system.TrackingSystem;

import java.util.Map;
import java.util.UUID;

public final class TrackingCommands {

    private TrackingCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tracking").executes(TrackingCommands::list);
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        Map<Module<?>, Map<UUID, TrackingSystem.View>> snapshot = TrackingSystem.snapshot();
        if (snapshot.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No tracked module has been synced to a player"));
            return 0;
        }
        for (Map.Entry<Module<?>, Map<UUID, TrackingSystem.View>> entry : snapshot.entrySet()) {
            Module<?> module = entry.getKey();
            ObjectSpec<Object> spec = module.spec();
            MutableComponent line = EmpiricCommands.label(module.type().id() + " on " + module.attachment().attachmentType().id() + ": ");
            for (Map.Entry<UUID, TrackingSystem.View> viewer : entry.getValue().entrySet()) {
                ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayer(viewer.getKey());
                TrackingSystem.View view = viewer.getValue();
                StringBuilder fields = new StringBuilder("module ").append(view.module());
                for (int i = 0; i < spec.synced().length; i++) {
                    fields.append("\n").append(spec.synced()[i].id()).append(" ").append(view.field(i));
                    if (!view.entries(i).isEmpty()) {
                        fields.append(" ").append(view.entries(i).keySet());
                    }
                }
                String name = player == null ? viewer.getKey().toString() : player.getGameProfile().getName();
                line.append(EmpiricCommands.hover(name + " " + view.module() + " ", fields.toString(), colorOf(view)));
            }
            EmpiricCommands.send(context, line);
        }
        return snapshot.size();
    }

    private static ChatFormatting colorOf(TrackingSystem.View view) {
        return switch (view.module()) {
            case SYNC -> ChatFormatting.GREEN;
            case PAUSE -> ChatFormatting.YELLOW;
            case CLEAR -> ChatFormatting.RED;
        };
    }
}

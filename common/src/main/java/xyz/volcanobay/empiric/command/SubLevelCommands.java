package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import org.joml.Vector3dc;
import xyz.volcanobay.empiric.api.attachment.SubLevelAttachment;

import java.util.UUID;

/**
 * {@code /empiric sublevel [id]}: lists the Sable sub-levels in the current level with their modules, or dumps one.
 * Registered only when Sable is installed.
 */
public final class SubLevelCommands {

    private SubLevelCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("sublevel")
                .executes(SubLevelCommands::list)
                .then(Commands.argument("id", UuidArgument.uuid())
                        .suggests((context, builder) -> {
                            SubLevelContainer container = SubLevelContainer.getContainer(context.getSource().getLevel());
                            if (container != null) {
                                for (SubLevel subLevel : container.getAllSubLevels()) {
                                    builder.suggest(subLevel.getUniqueId().toString());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(SubLevelCommands::dump));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        SubLevelContainer container = SubLevelContainer.getContainer(context.getSource().getLevel());
        if (container == null || container.getLoadedCount() == 0) {
            context.getSource().sendFailure(Component.literal("No sub-levels are loaded here"));
            return 0;
        }
        int count = 0;
        for (SubLevel subLevel : container.getAllSubLevels()) {
            count += EmpiricCommands.dump(context, SubLevelAttachment.of(subLevel), describe(subLevel));
        }
        return count;
    }

    private static int dump(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "id");
        SubLevelAttachment attachment = SubLevelAttachment.find(context.getSource().getLevel(), id);
        if (attachment == null) {
            context.getSource().sendFailure(Component.literal("No sub-level " + id + " is loaded here"));
            return 0;
        }
        return EmpiricCommands.dump(context, attachment, describe(attachment.subLevel()));
    }

    private static String describe(SubLevel subLevel) {
        Vector3dc position = subLevel.logicalPose().position();
        String name = subLevel.getName() == null ? subLevel.getUniqueId().toString() : subLevel.getName();
        return "sub-level " + name + " @ " + Math.round(position.x()) + " " + Math.round(position.y()) + " " + Math.round(position.z());
    }
}

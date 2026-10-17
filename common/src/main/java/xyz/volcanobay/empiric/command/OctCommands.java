package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import xyz.volcanobay.empiric.api.attachment.OctAttachment;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.ext.LevelOctExtension;
import xyz.volcanobay.empiric.oct.FullOctBlockEntity;

public final class OctCommands {

    private OctCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("oct")
                .then(Commands.literal("get").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(OctCommands::get)))
                .then(NetworkCommands.octTarget("dump", OctCommands::dump));
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        BlockEntity blockEntity = level.getBlockEntity(pos);
        OctLevel octLevel = LevelOctExtension.get(level);
        String kind = blockEntity instanceof FullOctBlockEntity ? "oct block" : "full block";
        EmpiricCommands.send(context, EmpiricCommands.label("Octs at " + EmpiricCommands.describe(pos) + " (" + kind + ")"));
        for (int octant = 0; octant < 8; octant++) {
            OctPos oct = new OctPos(pos, octant);
            BlockState state = octLevel.getBlockState(oct);
            OctAttachment attachment = OctAttachment.find(level, oct);
            MutableComponent line = EmpiricCommands.label(" " + octant + " (" + (octant & 1) + " " + ((octant >> 1) & 1) + " " + ((octant >> 2) & 1) + ") ")
                    .append(EmpiricCommands.hover(state.getBlock().getName().getString(), state.toString(), state.isAir() ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE));
            if (attachment != null) {
                attachment.modules().forEach(module -> line.append(Component.literal(" ")).append(EmpiricCommands.hover(module.type().id(), EmpiricCommands.print(module), ChatFormatting.AQUA)));
            }
            EmpiricCommands.send(context, line);
        }
        return 8;
    }

    private static int dump(CommandContext<CommandSourceStack> context, OctPos pos) {
        OctAttachment attachment = OctAttachment.find(context.getSource().getLevel(), pos);
        if (attachment == null) {
            context.getSource().sendFailure(Component.literal("No oct modules at " + EmpiricCommands.describe(pos.pos()) + " octant " + pos.octant()));
            return 0;
        }
        return EmpiricCommands.dump(context, attachment, "oct " + EmpiricCommands.describe(pos.pos()) + " octant " + pos.octant());
    }
}

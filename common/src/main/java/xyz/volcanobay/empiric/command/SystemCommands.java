package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import xyz.volcanobay.empiric.core.EmpiricCore;
import xyz.volcanobay.empiric.core.module.Side;
import xyz.volcanobay.empiric.core.system.SystemPhase;
import xyz.volcanobay.empiric.core.system.UnitInfo;

import java.util.Comparator;
import java.util.List;

public final class SystemCommands {

    private SystemCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("systems")
                .executes(context -> list(context, Side.SERVER))
                .then(Commands.literal("client").executes(context -> list(context, Side.CLIENT)));
    }

    private static int phaseOrder(String phase) {
        if (phase.equals(SystemPhase.DEFAULT)) {
            return 0;
        }
        return phase.equals(SystemPhase.LATE) ? 1 : 2;
    }

    private static MutableComponent line(UnitInfo info) {
        String tooltip = (info.parallel() ? "runs on worker threads" : "pinned to the main thread: " + String.join(", ", info.pinReasons()))
                + "\nreads " + info.reads() + "\nwrites " + info.writes();
        MutableComponent line = EmpiricCommands.hover(info.id(), tooltip, info.parallel() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
        line.append(EmpiricCommands.label(String.format("  wave %d  %s  %d tuples  avg %.1f µs  last %.1f µs", info.wave(), info.parallel() ? "parallel" : "main",
                info.querySize(), info.averageNanos() / 1000.0, info.lastNanos() / 1000.0)));
        return line;
    }

    private static int list(CommandContext<CommandSourceStack> context, Side side) {
        List<UnitInfo> infos = EmpiricCore.engine().systems().describe(side).stream()
                .sorted(Comparator.comparingInt((UnitInfo info) -> phaseOrder(info.phase())).thenComparing(UnitInfo::phase).thenComparingInt(UnitInfo::wave))
                .toList();
        EmpiricCommands.send(context, EmpiricCommands.label("Systems on " + side.name().toLowerCase() + " (" + infos.size() + ")"));
        if (infos.isEmpty()) {
            EmpiricCommands.send(context, Component.literal(" none").withStyle(ChatFormatting.DARK_GRAY));
        }
        String phase = null;
        for (UnitInfo info : infos) {
            if (!info.phase().equals(phase)) {
                phase = info.phase();
                EmpiricCommands.send(context, Component.literal(" " + phase).withStyle(ChatFormatting.AQUA));
            }
            EmpiricCommands.send(context, Component.literal("  ").append(line(info)));
        }
        return infos.size();
    }
}

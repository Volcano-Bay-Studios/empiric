package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import xyz.volcanobay.empiric.api.network.PacketTarget;
import xyz.volcanobay.empiric.api.oct.OctLevel;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.api.temperature.TemperatureModule;
import xyz.volcanobay.empiric.api.temperature.TemperatureSection;
import xyz.volcanobay.empiric.api.temperature.ThermalMaterial;
import xyz.volcanobay.empiric.networking.packet.TemperatureOverlayS2CPacket;
import xyz.volcanobay.empiric.system.TemperatureConfig;
import xyz.volcanobay.empiric.system.TemperatureSystem;

import java.util.Arrays;

public final class TemperatureCommands {

    private static final int FILL_LIMIT = 32768;
    private static final SimpleCommandExceptionType NO_OVERLAY = new SimpleCommandExceptionType(Component.literal("This client can't show the temperature overlay"));

    private TemperatureCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("temperature")
                .then(Commands.literal("get").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .executes(TemperatureCommands::get)))
                .then(Commands.literal("set").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(TemperatureModule.MIN / 10.0, TemperatureModule.MAX / 10.0))
                                .executes(context -> change(context, false, -1))
                                .then(Commands.argument("octant", IntegerArgumentType.integer(0, 7))
                                        .executes(context -> change(context, false, IntegerArgumentType.getInteger(context, "octant")))))))
                .then(Commands.literal("add").then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(-TemperatureModule.MAX / 10.0, TemperatureModule.MAX / 10.0))
                                .executes(context -> change(context, true, -1))
                                .then(Commands.argument("octant", IntegerArgumentType.integer(0, 7))
                                        .executes(context -> change(context, true, IntegerArgumentType.getInteger(context, "octant")))))))
                .then(Commands.literal("fill").then(Commands.argument("from", BlockPosArgument.blockPos())
                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(TemperatureModule.MIN / 10.0, TemperatureModule.MAX / 10.0))
                                        .executes(TemperatureCommands::fill)))))
                .then(Commands.literal("chunk")
                        .executes(context -> chunk(context, BlockPos.containing(context.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> chunk(context, BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context, BlockPos.containing(context.getSource().getPosition())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> clear(context, BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                .then(Commands.literal("show")
                        .executes(context -> show(context, 32))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 128))
                                .executes(context -> show(context, IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("hide").executes(TemperatureCommands::hide))
                .then(Commands.literal("timescale")
                        .executes(TemperatureCommands::timeScale)
                        .then(Commands.literal("clear").executes(TemperatureCommands::clearTimeScale))
                        .then(Commands.literal("default")
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1_000_000))
                                        .executes(TemperatureCommands::setDefaultTimeScale)))
                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 1_000_000))
                                .executes(TemperatureCommands::setTimeScale)));
    }

    private static void enable(CommandContext<CommandSourceStack> context) {
        EmpiricCommands.ensureEnabled(context.getSource(), TemperatureSystem.TEMPERATURE);
    }

    /**
     * Commands take degrees Celsius with one decimal, the system works in tenths.
     */
    private static int tenths(CommandContext<CommandSourceStack> context) {
        return (int) Math.round(DoubleArgumentType.getDouble(context, "value") * 10);
    }

    private static String material(ThermalMaterial material) {
        if (material == null) {
            return "air";
        }
        return String.format("k %.3g W/mK, rho %.0f kg/m3, c %.0f J/kgK, eps %.2f", material.conductivity(), material.density(), material.specificHeat(), material.emissivity());
    }

    private static boolean isActive(ServerLevel level, OctPos oct) {
        TemperatureModule module = TemperatureSystem.module(level, (int) Math.floorDiv(oct.getLongX(), 32L), (int) Math.floorDiv(oct.getLongZ(), 32L), false);
        if (module == null) {
            return false;
        }
        int section = level.getSectionIndex((int) Math.floorDiv(oct.getLongY(), 2L));
        long[] mask = module.activeMask(section);
        int index = TemperatureSection.indexOf(oct.getLongX(), oct.getLongY(), oct.getLongZ());
        return mask != null && (mask[index >> 6] & (1L << index)) != 0;
    }

    private static int timeScale(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        ResourceLocation dimension = level.dimension().location();
        String source = TemperatureSystem.isTimeScaleOverridden() ? "code override" : TemperatureConfig.hasDimension(dimension) ? "dimension override" : "default";
        double scale = TemperatureSystem.timeScale(level);
        EmpiricCommands.send(context, EmpiricCommands.label("Time scale in " + dimension + ": " + scale + " (" + source + ")"));
        return (int) scale;
    }

    private static int setTimeScale(CommandContext<CommandSourceStack> context) {
        ResourceLocation dimension = context.getSource().getLevel().dimension().location();
        double value = DoubleArgumentType.getDouble(context, "value");
        TemperatureConfig.setDimension(dimension, value);
        context.getSource().sendSuccess(() -> Component.literal("Set the time scale in " + dimension + " to " + value), true);
        return 1;
    }

    private static int setDefaultTimeScale(CommandContext<CommandSourceStack> context) {
        double value = DoubleArgumentType.getDouble(context, "value");
        TemperatureConfig.setDefault(value);
        context.getSource().sendSuccess(() -> Component.literal("Set the default time scale to " + value), true);
        return 1;
    }

    private static int clearTimeScale(CommandContext<CommandSourceStack> context) {
        ResourceLocation dimension = context.getSource().getLevel().dimension().location();
        TemperatureConfig.setDimension(dimension, null);
        context.getSource().sendSuccess(() -> Component.literal("Removed the time scale override in " + dimension), true);
        return 1;
    }

    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        enable(context);
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        StringBuilder octs = new StringBuilder("octant  heat  kelvin  active  material  state");
        for (int octant = 0; octant < 8; octant++) {
            OctPos oct = new OctPos(pos, octant);
            BlockState state = OctLevel.get(level).getBlockState(oct);
            octs.append('\n').append(octant).append(" (").append(octant & 1).append(' ').append((octant >> 1) & 1).append(' ').append((octant >> 2) & 1).append(")  ")
                    .append(TemperatureModule.format(TemperatureSystem.get(level, oct))).append("  ")
                    .append(String.format("%.3f K", TemperatureSystem.kelvin(level, oct))).append("  ")
                    .append(isActive(level, oct) ? "active" : "asleep").append("  ")
                    .append(material(TemperatureSystem.material(level, oct))).append("  ")
                    .append(state.getBlock().getName().getString());
        }
        MutableComponent line = EmpiricCommands.label("Temperature at " + EmpiricCommands.describe(pos) + ": ")
                .append(EmpiricCommands.hover(TemperatureModule.format(TemperatureSystem.get(level, pos)) + " average", octs.toString(), ChatFormatting.GOLD))
                .append(EmpiricCommands.label(", " + material(TemperatureSystem.material(level, new OctPos(pos, 0)))));
        EmpiricCommands.send(context, line);
        return TemperatureSystem.get(level, pos);
    }

    private static int change(CommandContext<CommandSourceStack> context, boolean add, int octant) throws CommandSyntaxException {
        enable(context);
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        int value = tenths(context);
        for (int i = 0; i < 8; i++) {
            if (octant != -1 && i != octant) {
                continue;
            }
            OctPos oct = new OctPos(pos, i);
            TemperatureSystem.set(level, oct, add ? TemperatureSystem.get(level, oct) + value : value);
        }
        String target = octant == -1 ? EmpiricCommands.describe(pos) : EmpiricCommands.describe(pos) + " octant " + octant;
        context.getSource().sendSuccess(() -> Component.literal((add ? "Added " + TemperatureModule.format(value) + " to " : "Set ") + target + (add ? "" : " to " + TemperatureModule.format(value))
                + ", now " + TemperatureModule.format(TemperatureSystem.get(level, pos)) + " average"), true);
        return 1;
    }

    private static int fill(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        enable(context);
        ServerLevel level = context.getSource().getLevel();
        BlockPos from = BlockPosArgument.getLoadedBlockPos(context, "from");
        BlockPos to = BlockPosArgument.getLoadedBlockPos(context, "to");
        int value = tenths(context);
        long volume = (long) (Math.abs(from.getX() - to.getX()) + 1) * (Math.abs(from.getY() - to.getY()) + 1) * (Math.abs(from.getZ() - to.getZ()) + 1);
        if (volume > FILL_LIMIT) {
            context.getSource().sendFailure(Component.literal("Too many blocks, " + volume + " is over " + FILL_LIMIT));
            return 0;
        }
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            TemperatureSystem.set(level, pos, value);
            count++;
        }
        int filled = count;
        context.getSource().sendSuccess(() -> Component.literal("Set " + filled + " blocks to " + TemperatureModule.format(value)), true);
        return filled;
    }

    private static TemperatureModule module(CommandContext<CommandSourceStack> context, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        TemperatureModule module = TemperatureSystem.module(context.getSource().getLevel(), chunk.x, chunk.z, false);
        if (module == null) {
            context.getSource().sendFailure(Component.literal("Chunk " + chunk.x + " " + chunk.z + " has no temperature data"));
        }
        return module;
    }

    private static int chunk(CommandContext<CommandSourceStack> context, BlockPos pos) {
        enable(context);
        TemperatureModule module = module(context, pos);
        if (module == null) {
            return 0;
        }
        ServerLevel level = context.getSource().getLevel();
        int cells = 0;
        int max = 0;
        StringBuilder sections = new StringBuilder("section  cells  largest offset");
        int[] keys = module.sections.keySet().toIntArray();
        Arrays.sort(keys);
        double resting = module.restingKelvin();
        for (int section : keys) {
            int sectionCells = 0;
            int sectionMax = 0;
            double[] values = module.sections.get(section);
            for (int i = 0; i < TemperatureModule.CELLS; i++) {
                int value = TemperatureModule.deltaTenths(values[i], resting);
                if (value != 0) {
                    sectionCells++;
                    sectionMax = Math.max(sectionMax, Math.abs(value));
                }
            }
            cells += sectionCells;
            max = Math.max(max, sectionMax);
            sections.append("\ny ").append((level.getMinSection() + section) * 16).append("  ").append(sectionCells).append("  ").append(TemperatureModule.format(sectionMax));
        }
        ChunkPos chunk = module.chunk().getPos();
        MutableComponent line = EmpiricCommands.label("Chunk " + chunk.x + " " + chunk.z + ": ")
                .append(EmpiricCommands.hover(module.sections.size() + " sections", sections.toString(), ChatFormatting.AQUA))
                .append(EmpiricCommands.label(", " + cells + " octs off the resting temperature (" + TemperatureModule.format(TemperatureModule.tenths(resting)) + "), largest offset " + TemperatureModule.format(max)));
        EmpiricCommands.send(context, line);
        return cells;
    }

    private static int clear(CommandContext<CommandSourceStack> context, BlockPos pos) {
        enable(context);
        TemperatureModule module = module(context, pos);
        if (module == null) {
            return 0;
        }
        LevelChunk chunk = module.chunk();
        TemperatureSystem.TEMPERATURE.detach(chunk);
        context.getSource().sendSuccess(() -> Component.literal("Cleared temperature in chunk " + chunk.getPos().x + " " + chunk.getPos().z), true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        enable(context);
        sendOverlay(context, radius);
        EmpiricCommands.send(context, EmpiricCommands.label("Showing temperature within " + radius + " blocks"));
        return radius;
    }

    private static int hide(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        sendOverlay(context, 0);
        EmpiricCommands.send(context, EmpiricCommands.label("Hid temperature"));
        return 1;
    }

    private static void sendOverlay(CommandContext<CommandSourceStack> context, int radius) throws CommandSyntaxException {
        try {
            PacketTarget.player(context.getSource().getPlayerOrException()).send(new TemperatureOverlayS2CPacket(radius));
        } catch (UnsupportedOperationException e) {
            throw NO_OVERLAY.create();
        }
    }
}

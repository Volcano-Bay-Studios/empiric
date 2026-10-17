package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.server.level.ServerLevel;
import xyz.volcanobay.empiric.system.AtmosphereSystem;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.module.AtmosphereNetwork;
import xyz.volcanobay.empiric.api.atmosphere.module.VolumeNetwork;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalComponent;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.oct.OctPos;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public final class AtmosphereCommands {

    private static final DynamicCommandExceptionType UNKNOWN_CHEMICAL = new DynamicCommandExceptionType(id -> Component.literal("No chemical " + id));
    private static final DynamicCommandExceptionType UNKNOWN_STATE = new DynamicCommandExceptionType(state -> Component.literal("No state " + state + ", use gas, liquid or solid"));
    private static final SuggestionProvider<CommandSourceStack> STATES = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(Volume.State.values()).map(state -> state.name().toLowerCase(Locale.ROOT)), builder);

    private AtmosphereCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("atmosphere")
                .then(NetworkCommands.octTarget("get", AtmosphereCommands::get))
                .then(change("add", true))
                .then(change("set", false));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> change(String name, boolean add) {
        return Commands.literal(name).then(Commands.argument("chemical", ResourceLocationArgument.id()).suggests(ChemicalRegistry.chemicalsSuggestionProvider())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .then(NetworkCommands.octTarget("at", (context, pos) -> change(context, pos, add, Volume.State.GAS)))
                        .then(Commands.argument("state", StringArgumentType.word()).suggests(STATES)
                                .then(NetworkCommands.octTarget("at", (context, pos) -> change(context, pos, add, state(context)))))));
    }

    private static Volume.State state(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "state");
        for (Volume.State state : Volume.State.values()) {
            if (state.name().equalsIgnoreCase(name)) {
                return state;
            }
        }
        throw UNKNOWN_STATE.create(name);
    }

    private static NetworkModule network(CommandContext<CommandSourceStack> context, OctPos pos) {
        EmpiricCommands.ensureEnabled(context.getSource(), AtmosphereSystem.VOLUME.moduleType());
        EmpiricCommands.ensureEnabled(context.getSource(), AtmosphereSystem.ATMOSPHERE.moduleType());
        ServerLevel level = context.getSource().getLevel();
        VolumeNetwork volume = AtmosphereSystem.VOLUME.get(level).getNetwork(pos);
        if (volume != null) {
            return volume;
        }
        AtmosphereNetwork atmosphere = AtmosphereSystem.ATMOSPHERE.get(level).getNetwork(pos);
        if (atmosphere == null) {
            context.getSource().sendFailure(Component.literal("No volume or atmosphere at " + EmpiricCommands.describe(pos.pos()) + " octant " + pos.octant()));
        }
        return atmosphere;
    }

    private static Volume volume(NetworkModule network) {
        if (network instanceof VolumeNetwork volume) {
            return volume.getVolume();
        }
        return ((AtmosphereNetwork) network).getVolume();
    }

    private static MutableComponent describe(NetworkModule network) {
        Volume volume = volume(network);
        StringBuilder chemicals = new StringBuilder("chemical  state  moles");
        for (Map.Entry<Chemical, ChemicalComponent> entry : volume.getChemicalComponentsList().entrySet()) {
            for (Volume.State state : Volume.State.values()) {
                double amount = volume.getChemicalAmount(entry.getKey(), state);
                if (amount > 0) {
                    chemicals.append('\n').append(entry.getKey().getName()).append("  ").append(state.name().toLowerCase(Locale.ROOT)).append("  ").append(String.format("%.3f", amount));
                }
            }
        }
        return EmpiricCommands.hover(network.type().id() + " #" + network.id(), EmpiricCommands.print(network), ChatFormatting.AQUA)
                .append(EmpiricCommands.label(String.format(" %.1f L, pressure %.2f, %.1f K, ", volume.getVolumeLiters(), volume.getGasPressure(), volume.getTemperature())))
                .append(EmpiricCommands.hover(volume.getChemicalComponentsList().size() + " chemicals", chemicals.toString(), ChatFormatting.GOLD));
    }

    private static int get(CommandContext<CommandSourceStack> context, OctPos pos) {
        NetworkModule network = network(context, pos);
        if (network == null) {
            return 0;
        }
        EmpiricCommands.send(context, EmpiricCommands.label("Volume at " + EmpiricCommands.describe(pos.pos()) + ": ").append(describe(network)));
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> context, OctPos pos, boolean add, Volume.State state) throws CommandSyntaxException {
        Chemical chemical = ChemicalRegistry.getChemical(ResourceLocationArgument.getId(context, "chemical"));
        if (chemical == null) {
            throw UNKNOWN_CHEMICAL.create(ResourceLocationArgument.getId(context, "chemical"));
        }
        NetworkModule network = network(context, pos);
        if (network == null) {
            return 0;
        }
        double amount = DoubleArgumentType.getDouble(context, "amount");
        Volume volume = volume(network);
        if (add) {
            volume.addChemical(chemical, amount, state);
        } else {
            volume.setChemical(chemical, amount, state);
        }
        network.markDirty();
        MutableComponent line = Component.literal((add ? "Added " : "Set ") + chemical.getName() + " " + state.name().toLowerCase(Locale.ROOT) + (add ? " " : " to ") + amount + ", ").append(describe(network));
        context.getSource().sendSuccess(() -> line, true);
        return 1;
    }
}

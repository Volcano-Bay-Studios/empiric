package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.VolumeHelper;
import xyz.volcanobay.empiric.api.atmosphere.volume.BloodstreamVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.network.NetworkModule;
import xyz.volcanobay.empiric.api.oct.OctPos;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntryType;
import xyz.volcanobay.empiric.system.AtmosphereSystem;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ChemistryCommands {

    private static final Target ENTITY = ChemistryCommands::entities;
    private static final Target ITEM = ChemistryCommands::items;
    private static final Target BLOCK = (context, action) -> network(context, new OctPos(BlockPosArgument.getLoadedBlockPos(context, "pos"), 0), action);
    private static final Target OCT = (context, action) -> network(context, new OctPos(BlockPosArgument.getLoadedBlockPos(context, "pos"), IntegerArgumentType.getInteger(context, "octant")), action);

    private ChemistryCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("chemistry")
                .then(Commands.literal("chemicals").executes(context -> list(context, "Chemicals", ChemicalRegistry.CHEMICALS, ChemistryCommands::describe)))
                .then(Commands.literal("reactions").executes(context -> list(context, "Reactions", ChemicalRegistry.REACTIONS, ChemistryCommands::describe)))
                .then(targets(Commands.literal("add"), (node, target) -> amount(node, target, true)))
                .then(targets(Commands.literal("set"), (node, target) -> amount(node, target, false)))
                .then(targets(Commands.literal("get"), (node, target) -> node.executes(context -> get(context, target))));
    }

    /**
     * Something that holds chemicals. The same commands reach entities (their bloodstream), held items, blocks and
     * single octs (their volume or atmosphere network).
     */
    @FunctionalInterface
    private interface Target {
        /**
         * Runs the action on every volume the command's target resolves to.
         * @return How many volumes there were
         */
        int run(CommandContext<CommandSourceStack> context, BiConsumer<String, Volume> action) throws CommandSyntaxException;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> targets(LiteralArgumentBuilder<CommandSourceStack> verb, BiConsumer<ArgumentBuilder<CommandSourceStack, ?>, Target> tail) {
        for (String name : List.of("entity", "item")) {
            ArgumentBuilder<CommandSourceStack, ?> selector = Commands.argument("targets", EntityArgument.entities());
            tail.accept(selector, name.equals("entity") ? ENTITY : ITEM);
            verb.then(Commands.literal(name).then(selector));
        }
        ArgumentBuilder<CommandSourceStack, ?> block = Commands.argument("pos", BlockPosArgument.blockPos());
        tail.accept(block, BLOCK);
        verb.then(Commands.literal("block").then(block));
        ArgumentBuilder<CommandSourceStack, ?> octant = Commands.argument("octant", IntegerArgumentType.integer(0, 7));
        tail.accept(octant, OCT);
        verb.then(Commands.literal("oct").then(Commands.argument("pos", BlockPosArgument.blockPos()).then(octant)));
        return verb;
    }

    private static void amount(ArgumentBuilder<CommandSourceStack, ?> node, Target target, boolean add) {
        node.then(Commands.argument("chemical", ResourceLocationArgument.id()).suggests(ChemicalRegistry.chemicalsSuggestionProvider())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(context -> change(context, target, add, null))
                        .then(Commands.argument("state", StringArgumentType.word()).suggests(AtmosphereCommands.STATES)
                                .executes(context -> change(context, target, add, AtmosphereCommands.state(context))))));
    }

    private static int entities(CommandContext<CommandSourceStack> context, BiConsumer<String, Volume> action) throws CommandSyntaxException {
        EmpiricCommands.ensureEnabled(context.getSource(), AtmosphereSystem.BLOODSTREAM);
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            if (entity instanceof LivingEntity living) {
                Volume volume = VolumeHelper.getBloodstreamVolume(living);
                if (volume != null) {
                    action.accept(entity.getDisplayName().getString(), volume);
                    count++;
                }
            }
        }
        if (count == 0) {
            context.getSource().sendFailure(Component.literal("No target has a bloodstream"));
        }
        return count;
    }

    private static int items(CommandContext<CommandSourceStack> context, BiConsumer<String, Volume> action) throws CommandSyntaxException {
        EmpiricCommands.ensureEnabled(context.getSource(), AtmosphereSystem.ITEM_VOLUME);
        int count = 0;
        for (Entity entity : EntityArgument.getEntities(context, "targets")) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            ItemStack stack = living.getMainHandItem();
            if (stack.isEmpty()) {
                continue;
            }
            String name = entity.getDisplayName().getString() + "'s " + stack.getHoverName().getString();
            AtmosphereSystem.ITEM_VOLUME.modify(stack, module -> action.accept(name, module.volume));
            count++;
        }
        if (count == 0) {
            context.getSource().sendFailure(Component.literal("No target is holding an item"));
        }
        return count;
    }

    private static int network(CommandContext<CommandSourceStack> context, OctPos pos, BiConsumer<String, Volume> action) {
        NetworkModule network = AtmosphereCommands.network(context, pos);
        if (network == null) {
            return 0;
        }
        action.accept(EmpiricCommands.describe(pos.pos()) + " octant " + pos.octant(), AtmosphereCommands.volume(network));
        network.markDirty();
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> context, Target target, boolean add, Volume.State requested) throws CommandSyntaxException {
        Chemical chemical = AtmosphereCommands.chemical(context);
        double amount = DoubleArgumentType.getDouble(context, "amount");
        Set<Volume.State> states = EnumSet.noneOf(Volume.State.class);
        int count = target.run(context, (name, volume) -> {
            // Bodies and held items have no meaningful pressure, so they take the room temperature phase
            Volume.State state = requested != null ? requested
                    : volume instanceof BloodstreamVolume || volume instanceof ItemVolume ? chemical.defaultState()
                    : AtmosphereCommands.phaseIn(chemical, volume);
            states.add(state);
            if (add) {
                volume.addChemical(chemical, amount, state);
            } else {
                volume.setChemical(chemical, amount, state);
            }
            volume.markDirty();
        });
        if (count > 0) {
            String message = (add ? "Added " : "Set ") + chemical.getName() + " " + states.stream().map(state -> state.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining("/")) + (add ? " " : " to ") + amount
                    + (add ? " to " : " in ") + count + (count == 1 ? " volume" : " volumes");
            context.getSource().sendSuccess(() -> Component.literal(message), true);
        }
        return count;
    }

    private static int get(CommandContext<CommandSourceStack> context, Target target) throws CommandSyntaxException {
        return target.run(context, (name, volume) -> EmpiricCommands.send(context, EmpiricCommands.label(name + ": ").append(AtmosphereCommands.describe(volume))));
    }

    private static String describe(Chemical chemical) {
        return chemical.getName()
                + String.format("\nboils %.1f °C, freezes %.1f °C, %s at room temperature", chemical.getBoilTemp(), chemical.getFreezeTemp(), chemical.defaultState().name().toLowerCase(Locale.ROOT))
                + String.format("\nmolecular weight %.3f", chemical.getMolecularWeight())
                + String.format("\ndensity liquid %.3f, solid %.3f", chemical.getDensityLiquid(), chemical.getDensitySolid());
    }

    private static <T> int list(CommandContext<CommandSourceStack> context, String name, DataEntryType<T> type, Function<T, String> tooltip) {
        List<DataEntry<T>> entries = type.entries().stream().sorted(Comparator.comparing(DataEntry::id)).toList();
        MutableComponent line = EmpiricCommands.label(name + " (" + entries.size() + "): ");
        for (DataEntry<T> entry : entries) {
            line.append(EmpiricCommands.hover(entry.id() + " ", tooltip.apply(entry.value()), ChatFormatting.AQUA));
        }
        EmpiricCommands.send(context, line);
        return entries.size();
    }

    private static String describe(ChemicalReaction reaction) {
        return "reactants " + reaction.getReactants()
                + "\nproducts " + reaction.getProducts()
                + "\nrequirements " + reaction.getRequirements().count()
                + String.format("\nenergy %.2f", reaction.getEnergyOutput());
    }
}

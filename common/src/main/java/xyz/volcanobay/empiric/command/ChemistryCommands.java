package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.core.entry.DataEntry;
import xyz.volcanobay.empiric.core.entry.DataEntryType;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class ChemistryCommands {

    private ChemistryCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("chemistry")
                .then(Commands.literal("chemicals").executes(context -> list(context, "Chemicals", ChemicalRegistry.CHEMICALS, ChemistryCommands::describe)))
                .then(Commands.literal("reactions").executes(context -> list(context, "Reactions", ChemicalRegistry.REACTIONS, ChemistryCommands::describe)));
    }

    private static String describe(Chemical chemical) {
        return chemical.getName()
                + String.format("\nboils %.1f K, freezes %.1f K", chemical.getBoilTemp(), chemical.getFreezeTemp())
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

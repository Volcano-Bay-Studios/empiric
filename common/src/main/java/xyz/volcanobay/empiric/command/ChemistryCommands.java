package xyz.volcanobay.empiric.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public final class ChemistryCommands {

    private ChemistryCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("chemistry")
                .then(Commands.literal("chemicals").executes(context -> list(context, "Chemicals", ChemicalRegistry.CHEMICALS_KEY, ChemistryCommands::describe)))
                .then(Commands.literal("reactions").executes(context -> list(context, "Reactions", ChemicalRegistry.CHEMICAL_REACTIONS_KEY, ChemistryCommands::describe)));
    }

    private static String describe(Chemical chemical) {
        return chemical.getName()
                + String.format("\nboils %.1f K, freezes %.1f K", chemical.getBoilTemp(), chemical.getFreezeTemp())
                + String.format("\nmolecular weight %.3f", chemical.getMolecularWeight())
                + String.format("\ndensity liquid %.3f, solid %.3f", chemical.getDensityLiquid(), chemical.getDensitySolid());
    }

    private static <T> int list(CommandContext<CommandSourceStack> context, String name, ResourceKey<Registry<T>> key, Function<T, String> tooltip) {
        Optional<Registry<T>> registry = ChemicalRegistry.registryAccess().registry(key);
        if (registry.isEmpty()) {
            context.getSource().sendFailure(Component.literal(name + " aren't loaded"));
            return 0;
        }
        List<ResourceLocation> ids = registry.get().keySet().stream().sorted().toList();
        MutableComponent line = EmpiricCommands.label(name + " (" + ids.size() + "): ");
        for (ResourceLocation id : ids) {
            line.append(EmpiricCommands.hover(id + " ", tooltip.apply(registry.get().get(id)), ChatFormatting.AQUA));
        }
        EmpiricCommands.send(context, line);
        return ids.size();
    }

    private static String describe(ChemicalReaction reaction) {
        return "reactants " + reaction.getReactants()
                + "\nproducts " + reaction.getProducts()
                + "\nrequirements " + reaction.getRequirements().size()
                + String.format("\nenergy %.2f", reaction.getEnergyOutput());
    }
}

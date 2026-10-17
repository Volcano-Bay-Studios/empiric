package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.data.spec.SimpleSpec;

public final class ChemistrySpecs {

    public static final Spec<Chemical> CHEMICAL = SimpleSpec.of((buffer, chemical) -> buffer.putString(chemical.getName()), buffer -> {
        String name = buffer.takeString();
        Chemical chemical = ChemicalRegistry.getChemical(name);
        return chemical != null ? chemical : Chemical.unknown(name);
    });

    private ChemistrySpecs() {
    }

    public static void register(SpecRegistry registry) {
        registry.register(Chemical.class, CHEMICAL);
    }
}

package xyz.volcanobay.empiric.api.chemistry;

import xyz.volcanobay.empiric.core.data.Spec;
import xyz.volcanobay.empiric.core.data.SpecRegistry;
import xyz.volcanobay.empiric.core.registry.Register;
import xyz.volcanobay.empiric.core.data.spec.SimpleSpec;

public final class ChemistrySpecs {

    @Register
    public static final Spec<Chemical> CHEMICAL = SimpleSpec.of((buffer, chemical) -> buffer.putString(chemical.getName()), buffer -> {
        String name = buffer.takeString();
        Chemical chemical = ChemicalRegistry.getChemical(name);
        return chemical != null ? chemical : Chemical.unknown(name);
    });

    private ChemistrySpecs() {
    }

    /** @deprecated registration is automatic, so this does nothing. */
    @Deprecated
    public static void register(SpecRegistry registry) {
    }
}

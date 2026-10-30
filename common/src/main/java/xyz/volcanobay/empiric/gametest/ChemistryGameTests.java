package xyz.volcanobay.empiric.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.TestFunction;
import xyz.volcanobay.empiric.api.atmosphere.Volume;
import xyz.volcanobay.empiric.api.atmosphere.volume.AtmosphereVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.ItemVolume;
import xyz.volcanobay.empiric.api.atmosphere.volume.PlanetAtmosphereVolume;
import xyz.volcanobay.empiric.api.chemistry.Chemical;
import xyz.volcanobay.empiric.api.chemistry.ChemicalReaction;
import xyz.volcanobay.empiric.api.chemistry.ChemicalRegistry;
import xyz.volcanobay.empiric.api.chemistry.sim.PhaseChange;
import xyz.volcanobay.empiric.api.chemistry.sim.Reactions;
import xyz.volcanobay.empiric.api.chemistry.sim.Solutions;
import xyz.volcanobay.empiric.api.chemistry.sim.VolumeExchange;
import xyz.volcanobay.empiric.system.AtmosphereSystem;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Volumes on their own: what they hold, how heat and gas move between them, and how their contents change state.
 */
final class ChemistryGameTests {

    private static final double R = 8.314;

    private ChemistryGameTests() {
    }

    static List<TestFunction> tests(BiFunction<String, Consumer<GameTestHelper>, TestFunction> test) {
        return List.of(
                test.apply("volume_occupied_by_liquid", ChemistryGameTests::occupiedByLiquid),
                test.apply("volume_read_does_not_create", ChemistryGameTests::readDoesNotCreate),
                test.apply("volume_clone_is_deep", ChemistryGameTests::cloneIsDeep),
                test.apply("volume_transfer_mixes_heat", ChemistryGameTests::transferMixesHeat),
                test.apply("volume_add_heat", ChemistryGameTests::addHeat),
                test.apply("exchange_small_volume_stable", ChemistryGameTests::smallVolumeStable),
                test.apply("exchange_with_reservoir", ChemistryGameTests::exchangeWithReservoir),
                test.apply("exchange_heat_never_overshoots", ChemistryGameTests::heatNeverOvershoots),
                test.apply("boiling_clamps_temperature", ChemistryGameTests::boilingClamps),
                test.apply("sealed_vapour_equilibrium", ChemistryGameTests::sealedVapour),
                test.apply("solution_saturates", ChemistryGameTests::solutionSaturates),
                test.apply("reaction_needs_its_heat", ChemistryGameTests::reactionNeedsHeat),
                test.apply("reaction_water_metal", ChemistryGameTests::waterMetal),
                test.apply("reaction_catalyst", ChemistryGameTests::catalyst),
                test.apply("reaction_pressure", ChemistryGameTests::pressure));
    }

    private static Chemical water(GameTestHelper helper) {
        Chemical water = ChemicalRegistry.getChemical("empiric:water");
        helper.assertTrue(water != null, "the chemistry registry didn't load water");
        return water;
    }

    private static Chemical oxygen(GameTestHelper helper) {
        Chemical oxygen = ChemicalRegistry.getChemical("empiric:oxygen");
        helper.assertTrue(oxygen != null, "the chemistry registry didn't load oxygen");
        return oxygen;
    }

    private static double waterMoles(Chemical water, double ml) {
        return ml * water.getDensityLiquid() / water.getMolecularWeight();
    }

    /**
     * A 250 mL vessel holding some water under air at 1 atm.
     */
    private static ItemVolume vessel(GameTestHelper helper, double waterMl, double kelvin) {
        ItemVolume volume = new ItemVolume(0.25);
        volume.setTemperature(kelvin);
        Chemical water = water(helper);
        volume.addChemical(water, waterMoles(water, waterMl), Volume.State.LIQUID);
        volume.addChemical(oxygen(helper), 101325 * volume.getGasSpace() / (R * kelvin), Volume.State.GAS);
        return volume;
    }

    private static void close(GameTestHelper helper, double actual, double expected, double tolerance, String message) {
        helper.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + " but was " + actual);
    }

    private static void occupiedByLiquid(GameTestHelper helper) {
        ItemVolume volume = new ItemVolume(0.25);
        volume.setTemperature(293.15);
        Chemical water = water(helper);
        volume.addChemical(water, waterMoles(water, 100), Volume.State.LIQUID);
        close(helper, volume.getOccupiedVolume(), 1.0E-4, 1.0E-6, "100 mL of water takes the wrong room");
        close(helper, volume.getGasSpace(), 1.5E-4, 1.0E-6, "the gas space is wrong");
        volume.addChemical(water, waterMoles(water, 1000), Volume.State.LIQUID);
        close(helper, volume.getGasSpace(), 0.25E-5, 1.0E-9, "an overfull vessel should keep 1% for gas");
        helper.succeed();
    }

    private static void readDoesNotCreate(GameTestHelper helper) {
        ItemVolume volume = new ItemVolume(0.25);
        helper.assertTrue(volume.getChemicalAmount(water(helper), Volume.State.LIQUID) == 0, "an empty volume has water");
        helper.assertTrue(volume.getChemicalComponentsList().isEmpty(), "reading an amount added an entry");
        volume.addChemical(water(helper), 1, Volume.State.LIQUID);
        volume.addChemical(water(helper), -2, Volume.State.LIQUID);
        helper.assertTrue(volume.getChemicalComponentsList().isEmpty(), "an entry that ran out wasn't dropped");
        close(helper, volume.getStateTotal(null), 0, 1.0E-12, "taking more than there was went negative");
        volume.setChemical(water(helper), 3, Volume.State.LIQUID);
        volume.setChemical(water(helper), 1, Volume.State.LIQUID);
        close(helper, volume.getStateTotal(null), 1, 1.0E-12, "setting an amount lost track");
        helper.succeed();
    }

    private static void cloneIsDeep(GameTestHelper helper) {
        ItemVolume volume = vessel(helper, 50, 293.15);
        double before = volume.getChemicalAmount(water(helper), Volume.State.LIQUID);
        Volume clone = volume.clone();
        clone.addChemical(water(helper), 1, Volume.State.LIQUID);
        close(helper, volume.getChemicalAmount(water(helper), Volume.State.LIQUID), before, 1.0E-12, "changing a clone changed the original");
        helper.succeed();
    }

    private static void transferMixesHeat(GameTestHelper helper) {
        Chemical water = water(helper);
        ItemVolume hot = new ItemVolume(0.25);
        hot.setTemperature(353.15);
        hot.addChemical(water, waterMoles(water, 100), Volume.State.LIQUID);
        ItemVolume cold = new ItemVolume(0.25);
        cold.setTemperature(293.15);
        cold.addChemical(water, waterMoles(water, 100), Volume.State.LIQUID);
        hot.transferChemical(water, cold, waterMoles(water, 100), Volume.State.LIQUID);
        close(helper, cold.getTemperature(), 323.15, 0.01, "equal amounts of water should meet in the middle");

        // a whiff of hot gas barely warms a beaker of water
        ItemVolume gas = new ItemVolume(0.25);
        gas.setTemperature(500);
        gas.addChemical(oxygen(helper), 0.001, Volume.State.GAS);
        gas.transferAmount(cold, 0.001, Volume.State.GAS);
        helper.assertTrue(cold.getTemperature() < 323.2, "a little gas heated the water like a liquid would");
        helper.succeed();
    }

    private static void addHeat(GameTestHelper helper) {
        Chemical water = water(helper);
        ItemVolume volume = new ItemVolume(0.25);
        volume.setTemperature(293.15);
        double moles = waterMoles(water, 100);
        volume.addChemical(water, moles, Volume.State.LIQUID);
        close(helper, volume.heatCapacity(), moles * water.getHeatCapacity(Volume.State.LIQUID), 1.0E-9, "wrong heat capacity");
        volume.addHeat(volume.heatCapacity() * 10);
        close(helper, volume.getTemperature(), 303.15, 1.0E-9, "heat changed the temperature by the wrong amount");
        helper.succeed();
    }

    private static void smallVolumeStable(GameTestHelper helper) {
        Chemical oxygen = oxygen(helper);
        ItemVolume small = new ItemVolume(0.25);
        small.setTemperature(293.15);
        small.addChemical(oxygen, 3 * 101325 * small.getGasSpace() / (R * 293.15), Volume.State.GAS);
        AtmosphereVolume room = new AtmosphereVolume();
        room.updateVolume(1);
        room.setTemperature(293.15);
        room.addChemical(oxygen, 101325 / (R * 293.15), Volume.State.GAS);
        double total = small.getStateTotal(Volume.State.GAS) + room.getStateTotal(Volume.State.GAS);
        for (int i = 0; i < 2000; i++) {
            VolumeExchange.equalize(small, room, 50, 0, 0.5);
        }
        double after = small.getStateTotal(Volume.State.GAS) + room.getStateTotal(Volume.State.GAS);
        close(helper, after, total, total * 1.0E-9, "exchange lost or made gas");
        close(helper, small.getGasPressure() / room.getGasPressure(), 1, 0.01, "the pressures didn't even out");
        helper.succeed();
    }

    private static void exchangeWithReservoir(GameTestHelper helper) {
        PlanetAtmosphereVolume air = new PlanetAtmosphereVolume();
        air.updateVolume(1000);
        air.fill(AtmosphereSystem.levelAtmosphere(helper.getLevel()));
        helper.assertTrue(air.isReservoir(), "open air isn't a reservoir");
        double outside = air.getGasPressure();
        helper.assertTrue(outside > 50_000, "the level's air has no pressure");
        Chemical water = water(helper);

        // pure steam over water mixes with the air until it is mostly air
        ItemVolume vessel = new ItemVolume(0.25);
        vessel.setTemperature(293.15);
        vessel.addChemical(water, waterMoles(water, 100), Volume.State.LIQUID);
        vessel.addChemical(water, 2 * outside * vessel.getGasSpace() / (R * 293.15), Volume.State.GAS);
        for (int i = 0; i < 200; i++) {
            VolumeExchange.equalize(vessel, air, 1, 0.05, 0.5);
        }
        close(helper, vessel.getGasPressure() / outside, 1, 0.01, "the vessel didn't vent to the air's pressure");
        double steam = vessel.getChemicalAmount(water, Volume.State.GAS) / vessel.getStateTotal(Volume.State.GAS);
        helper.assertTrue(steam < 0.05, "the steam didn't diffuse out, " + steam + " of the gas is still steam");
        close(helper, air.getGasPressure(), outside, 1.0E-6, "the open air changed");
        helper.succeed();
    }

    private static void heatNeverOvershoots(GameTestHelper helper) {
        ItemVolume volume = vessel(helper, 100, 293.15);
        double joules = VolumeExchange.heat(volume, 473.15, Double.POSITIVE_INFINITY, 1.0E9, 10);
        helper.assertTrue(joules > 0, "no heat went into the cold volume");
        helper.assertTrue(volume.getTemperature() <= 473.15 + 1.0E-6, "a huge conductance overshot the source");
        ItemVolume other = vessel(helper, 100, 293.15);
        double capacity = other.heatCapacity();
        VolumeExchange.heat(other, 353.15, capacity, 1.0E9, 10);
        close(helper, other.getTemperature(), 323.15, 0.01, "two equal bodies should meet in the middle");
        helper.succeed();
    }

    private static void boilingClamps(GameTestHelper helper) {
        Chemical water = water(helper);
        // the air goes in at 120 °C too, so the headspace is at 1 atm
        ItemVolume volume = vessel(helper, 100, 393.15);
        double before = volume.getChemicalAmount(water, Volume.State.LIQUID);
        boolean boiling = PhaseChange.step(volume, 0.5);
        helper.assertTrue(boiling, "water at 120 °C under 1 atm didn't boil");
        helper.assertTrue(volume.getTemperature() <= 373.15 + 0.5, "boiling didn't hold the water at its boiling point, it is " + volume.getTemperature());
        double boiled = before - volume.getChemicalAmount(water, Volume.State.LIQUID);
        helper.assertTrue(boiled > 0, "no water boiled off");
        // the heat that went is the latent heat of what boiled
        helper.assertTrue(boiled * water.gethVapKJ() * 1000 < 20 * before * water.getHeatCapacity(Volume.State.LIQUID) * 1.05,
                "more water boiled than the heat above boiling could pay for");
        helper.succeed();
    }

    private static void sealedVapour(GameTestHelper helper) {
        Chemical water = water(helper);
        ItemVolume volume = vessel(helper, 100, 333.15);
        for (int i = 0; i < 200; i++) {
            PhaseChange.step(volume, 0.5);
            volume.setTemperature(333.15);
        }
        double partial = volume.getChemicalAmount(water, Volume.State.GAS) * R * volume.getTemperature() / volume.getGasSpace();
        double expected = water.vapourPressure(volume.getTemperature());
        close(helper, partial / expected, 1, 0.05, "the vapour over sealed water didn't settle at its vapour pressure");
        helper.succeed();
    }

    private static void reactionNeedsHeat(GameTestHelper helper) {
        Chemical water = water(helper);
        Chemical hydrogen = ChemicalRegistry.getChemical("empiric:hydrogen");
        helper.assertTrue(hydrogen != null, "the chemistry registry didn't load hydrogen");
        ItemVolume cold = new ItemVolume(0.25);
        cold.setTemperature(293.15);
        cold.addChemical(hydrogen, 0.002, Volume.State.GAS);
        cold.addChemical(oxygen(helper), 0.001, Volume.State.GAS);
        helper.assertTrue(!Reactions.step(cold, 0, 10), "hydrogen and oxygen reacted at room temperature");

        ItemVolume hot = new ItemVolume(0.25);
        hot.setTemperature(900);
        hot.addChemical(hydrogen, 0.002, Volume.State.GAS);
        hot.addChemical(oxygen(helper), 0.001, Volume.State.GAS);
        for (int i = 0; i < 40; i++) {
            Reactions.step(hot, 0, 0.5);
        }
        close(helper, hot.getChemicalAmount(water, Volume.State.GAS), 0.002, 1.0E-4, "the hot mix didn't turn into water vapour");
        helper.assertTrue(hot.getTemperature() > 900, "burning hydrogen gave off no heat");
        helper.succeed();
    }

    private static Chemical chemical(GameTestHelper helper, String id) {
        Chemical chemical = ChemicalRegistry.getChemical("empiric:" + id);
        helper.assertTrue(chemical != null, "the chemistry registry didn't load " + id);
        return chemical;
    }

    private static double moles(ItemVolume volume, Chemical chemical) {
        double total = 0;
        for (Volume.State state : Volume.State.values()) {
            total += volume.getChemicalAmount(chemical, state);
        }
        return total;
    }

    private static void waterMetal(GameTestHelper helper) {
        ItemVolume volume = vessel(helper, 50, 293.15);
        volume.addChemical(chemical(helper, "lithium"), 0.02, Volume.State.SOLID);
        for (int i = 0; i < 120; i++) {
            Reactions.step(volume, 0, 0.5);
        }
        close(helper, moles(volume, chemical(helper, "lithium_hydroxide")), 0.02, 0.002, "the lithium didn't turn into hydroxide");
        close(helper, moles(volume, chemical(helper, "hydrogen")), 0.01, 0.001, "the lithium gave off the wrong amount of hydrogen");
        helper.assertTrue(volume.getTemperature() > 293.15, "lithium in water gave off no heat");
        helper.succeed();
    }

    private static void catalyst(GameTestHelper helper) {
        Chemical peroxide = chemical(helper, "hydrogen_peroxide");
        Chemical oxygen = oxygen(helper);
        ItemVolume bare = vessel(helper, 0, 293.15);
        ItemVolume catalysed = vessel(helper, 0, 293.15);
        bare.addChemical(peroxide, 0.05, Volume.State.LIQUID);
        catalysed.addChemical(peroxide, 0.05, Volume.State.LIQUID);
        catalysed.addChemical(chemical(helper, "manganese_dioxide"), 0.01, Volume.State.SOLID);
        double before = moles(bare, oxygen);
        for (int i = 0; i < 40; i++) {
            Reactions.step(bare, 0, 0.5);
            Reactions.step(catalysed, 0, 0.5);
        }
        close(helper, moles(bare, peroxide), 0.05, 1.0E-9, "peroxide decomposed without its catalyst");
        close(helper, moles(bare, oxygen), before, 1.0E-9, "oxygen appeared without the catalyst");
        helper.assertTrue(moles(catalysed, peroxide) < 0.005, "the catalyst didn't decompose the peroxide");
        close(helper, moles(catalysed, chemical(helper, "manganese_dioxide")), 0.01, 1.0E-9, "the catalyst was used up");
        helper.succeed();
    }

    private static void pressure(GameTestHelper helper) {
        ChemicalReaction haber = ChemicalRegistry.getReaction(ResourceLocation.parse("empiric:haber_process"));
        helper.assertTrue(haber != null, "the chemistry registry didn't load the haber process");
        Chemical nitrogen = chemical(helper, "nitrogen");
        Chemical hydrogen = chemical(helper, "hydrogen");
        double kelvin = 700;
        double[] atmospheres = {1, 5};
        boolean[] expected = {false, true};
        for (int i = 0; i < 2; i++) {
            ItemVolume volume = new ItemVolume(0.25);
            volume.setTemperature(kelvin);
            double total = atmospheres[i] * 101325 * volume.getGasSpace() / (R * kelvin);
            volume.addChemical(nitrogen, total / 4, Volume.State.GAS);
            volume.addChemical(hydrogen, total * 3 / 4, Volume.State.GAS);
            volume.addChemical(chemical(helper, "iron"), 0.05, Volume.State.SOLID);
            helper.assertTrue(Reactions.canRun(volume, haber) == expected[i], "the haber process at " + atmospheres[i] + " atm should " + (expected[i] ? "" : "not ") + "run");
        }
        helper.succeed();
    }

    private static void solutionSaturates(GameTestHelper helper) {
        Chemical water = water(helper);
        Chemical salt = new Chemical("empiric:test_salt", 1413, 801, 0, 28, 1.55, 2.16, 58.44, false, 0xffffff).withSolubility(6);
        ItemVolume still = vessel(helper, 100, 293.15);
        ItemVolume stirred = vessel(helper, 100, 293.15);
        still.addChemical(salt, 1, Volume.State.SOLID);
        stirred.addChemical(salt, 1, Volume.State.SOLID);
        for (int i = 0; i < 10; i++) {
            Solutions.step(still, 0, 0.5);
            Solutions.step(stirred, 1, 0.5);
        }
        double slow = still.getChemicalAmount(salt, Volume.State.LIQUID);
        double fast = stirred.getChemicalAmount(salt, Volume.State.LIQUID);
        helper.assertTrue(fast >= slow * 2, "stirring didn't speed up dissolving: " + fast + " against " + slow);
        for (int i = 0; i < 2000; i++) {
            Solutions.step(still, 0, 0.5);
        }
        close(helper, still.getChemicalAmount(salt, Volume.State.LIQUID), 0.6, 0.006, "100 mL should hold 0.6 mol at 6 mol/L");
        close(helper, still.getChemicalAmount(salt, Volume.State.SOLID), 0.4, 0.006, "the rest should stay solid");
        helper.assertTrue(Solutions.isDissolved(still, salt) && !Solutions.isDissolved(still, water), "dissolved wrongly reported");
        helper.succeed();
    }
}

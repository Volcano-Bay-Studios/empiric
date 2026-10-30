package xyz.volcanobay.empiric.api.part;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PartInteractions {

    private record Entry(int priority, PartInteraction interaction) {
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static volatile List<PartInteraction> sorted = List.of();

    private PartInteractions() {
    }

    /**
     * @param priority Higher goes first
     */
    public static synchronized void register(int priority, PartInteraction interaction) {
        ENTRIES.add(new Entry(priority, interaction));
        List<Entry> entries = new ArrayList<>(ENTRIES);
        entries.sort(Comparator.comparingInt(Entry::priority).reversed());
        sorted = entries.stream().map(Entry::interaction).toList();
    }

    public static List<PartInteraction> all() {
        return sorted;
    }
}

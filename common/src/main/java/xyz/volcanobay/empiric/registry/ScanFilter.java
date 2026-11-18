package xyz.volcanobay.empiric.registry;

import xyz.volcanobay.empiric.core.registry.ScanResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class ScanFilter {

    private ScanFilter() {
    }

    public static ScanResult keepClasses(ScanResult scan, Predicate<String> keep) {
        Set<String> kept = new HashSet<>();
        List<ScanResult.ScannedClass> classes = new ArrayList<>();
        for (ScanResult.ScannedClass scanned : scan.classes()) {
            if (keep.test(scanned.name())) {
                kept.add(scanned.name());
                classes.add(scanned);
            }
        }
        List<ScanResult.ScannedElement> elements = new ArrayList<>();
        for (ScanResult.ScannedElement element : scan.elements()) {
            if (kept.contains(element.className())) {
                elements.add(element);
            }
        }
        List<ScanResult.ScannedMethod> methods = new ArrayList<>();
        for (ScanResult.ScannedMethod method : scan.methods()) {
            if (kept.contains(method.className())) {
                methods.add(method);
            }
        }
        return new ScanResult(classes, elements, methods);
    }
}

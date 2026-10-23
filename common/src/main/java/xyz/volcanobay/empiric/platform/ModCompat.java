package xyz.volcanobay.empiric.platform;

/**
 * Optional mod checks. They look for a class file without loading it, so they work in mixin plugins too.
 */
public final class ModCompat {

    public static final boolean SABLE = present("dev/ryanhcode/sable/Sable.class");

    private ModCompat() {
    }

    public static boolean present(String classFile) {
        return ModCompat.class.getClassLoader().getResource(classFile) != null;
    }
}

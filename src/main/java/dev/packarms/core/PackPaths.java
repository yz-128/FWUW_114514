package dev.packarms.core;

import java.util.Locale;

final class PackPaths {
    private PackPaths() { }

    static String normalize(String path) {
        if (path == null) throw new NullPointerException("path");
        String value = path.replace('\\', '/');
        StringBuilder result = new StringBuilder(value.length());
        for (String component : value.split("/")) {
            if (component.isEmpty() || component.equals(".")) continue;
            if (component.equals("..") || component.indexOf('\0') >= 0 || component.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Unsafe pack path: " + path);
            }
            if (!result.isEmpty()) result.append('/');
            result.append(component);
        }
        return result.toString();
    }

    static String folded(String path) { return path.toLowerCase(Locale.ROOT); }

    static boolean hidden(String name) {
        for (String part : name.split("/")) if (part.startsWith(".")) return true;
        return false;
    }
}

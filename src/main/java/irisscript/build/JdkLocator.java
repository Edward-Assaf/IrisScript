package irisscript.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class JdkLocator {

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    private static final String JAVA_EXECUTABLE = WINDOWS ? "java.exe" : "java";

    private static final String JAVAC_EXECUTABLE = WINDOWS ? "javac.exe" : "javac";

    private JdkLocator() {
    }

    public record Toolchain(Path home, Path java, Path javac) {
    }

    public static Toolchain locate() throws IOException {
        Set<Path> candidateHomes = new LinkedHashSet<>();
        List<String> checkedLocations = new ArrayList<>();
        // 1. Explicit IrisScript configuration.
        addHome(candidateHomes, System.getenv("IRISSCRIPT_JDK_HOME"));
        // 2. Standard Java configuration.
        addHome(candidateHomes, System.getenv("JAVA_HOME"));
        // 3. The JDK running the current JVM, if it is a full JDK.
        addCurrentJavaHomes(candidateHomes);
        // 4. Search PATH for a matching java/javac pair.
        for (Path home : candidateHomes) {
            Toolchain toolchain = validateHome(home);
            if (toolchain != null) {
                return toolchain;
            }
            checkedLocations.add(home.toString());
        }
        for (Path bin : getPathEntries()) {
            Toolchain toolchain = validateBin(bin);
            if (toolchain != null) {
                return toolchain;
            }
            checkedLocations.add(bin.toString());
        }
        String checked = checkedLocations.isEmpty() ? "(no candidate locations were available)"
                : String.join(System.lineSeparator() + "  ", checkedLocations);

        throw new IOException("No complete JDK installation was found."
                + System.lineSeparator()
                + "Checked locations:"
                + System.lineSeparator()
                + "  " + checked
                + System.lineSeparator()
                + "A valid JDK must contain both "
                + JAVA_EXECUTABLE + " and "
                + JAVAC_EXECUTABLE + " in its bin directory."
                + System.lineSeparator()
                + "Set JAVA_HOME or IRISSCRIPT_JDK_HOME "
                + "to the JDK root directory if necessary.");
    }

    private static void addHome(Set<Path> homes, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            homes.add(Path.of(value).toAbsolutePath().normalize());
        } catch (RuntimeException ignored) {// An invalid candidate should not stop discovery.
        }
    }

    private static void addCurrentJavaHomes(Set<Path> homes) {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return;
        }
        try {
            Path current = Path.of(javaHome).toAbsolutePath().normalize();
            addHome(homes, current.toString());
            // Some Java installations use a nested JRE directory.
            // Its parent may be the full JDK root.
            Path parent = current.getParent();
            if (parent != null) {
                addHome(homes, parent.toString());
            }
        } catch (RuntimeException ignored) {// Continue with other discovery strategies.
        }
    }

    private static Toolchain validateHome(Path home) {
        return validateBin(home.resolve("bin"), home);
    }

    private static Toolchain validateBin(Path bin) {
        Path normalizedBin = bin.toAbsolutePath().normalize();
        Path home = normalizedBin.getParent();
        if (home == null) {
            return null;
        }
        return validateBin(normalizedBin, home);
    }

    private static Toolchain validateBin(Path bin, Path home) {
        Path java = bin.resolve(JAVA_EXECUTABLE);
        Path javac = bin.resolve(JAVAC_EXECUTABLE);
        if (!isRunnableFile(java) || !isRunnableFile(javac)) {
            return null;
        }
        return new Toolchain(home.toAbsolutePath().normalize(), java.toAbsolutePath().normalize(),
                javac.toAbsolutePath().normalize());
    }

    private static boolean isRunnableFile(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        /*
         * Files.isExecutable() is useful on Unix-like systems.
         * On Windows, executable permission checks are not reliable
         * across all filesystems, so validate file presence here.
         */
        return WINDOWS || Files.isExecutable(path);
    }

    private static List<Path> getPathEntries() {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return List.of();
        }
        String separator = System.getProperty("path.separator");
        List<Path> entries = new ArrayList<>();
        for (String entry : path.split(
                java.util.regex.Pattern.quote(separator))) {
            if (entry.isBlank()) {
                continue;
            }
            try {
                Path directory = Path.of(entry).toAbsolutePath().normalize();
                if (!entries.contains(directory)) {
                    entries.add(directory);
                }
            } catch (RuntimeException ignored) {// Skip invalid PATH entries.
            }
        }
        return entries;
    }

}
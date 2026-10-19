package irisscript.codegen;

import java.nio.file.Path;
import java.util.Objects;

public record GeneratedSource(String fileName, String source) {

    public GeneratedSource {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Path path = Path.of(fileName).normalize();
        if (path.isAbsolute() || path.startsWith("..") || !fileName.endsWith(".java")) {
            throw new IllegalArgumentException("fileName must be a safe relative .java path");
        }
    }

}
package irisscript.source;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class SourceManager {

    private final List<SourceFile> files = new ArrayList<>();

    private final Map<Path, Integer> fileIDPathMapping = new HashMap<>();

    public SourceFile load(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        Path normalizedPath = path.toAbsolutePath().normalize();
        Integer existingID = fileIDPathMapping.get(normalizedPath);
        if (existingID != null) {
            return get(existingID);
        }
        String contents = Files.readString(normalizedPath, StandardCharsets.UTF_8);
        SourceFile source = register(normalizedPath.toString(), contents);
        fileIDPathMapping.put(normalizedPath, source.getID());
        return source;
    }

    public SourceFile register(String name, String contents) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(contents, "contents must not be null");
        int id = files.size();
        SourceFile source = new SourceFile(id, name, contents);
        files.add(source);
        return source;
    }

    public SourceFile get(int sourceID) {
        if (sourceID < 0 || sourceID >= files.size()) {
            throw new IllegalArgumentException("sourceID is out of the bounds of the files array");
        }
        return files.get(sourceID);
    }

    public int getNumberOfFiles() {
        return files.size();
    }

}
package com.riprod.patchly.engine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EnginePurityTest {

    @Test
    void purePackagesNeverImportHytale() throws IOException {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/riprod/patchly/engine"),
                Path.of("src/main/java/com/riprod/patchly/registry"),
                Path.of("src/main/java/com/riprod/patchly/source"));

        List<String> offenders = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path file : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                    for (String line : Files.readAllLines(file)) {
                        if (line.trim().startsWith("import com.hypixel")) {
                            offenders.add(file + " -> " + line.trim());
                        }
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(), "pure packages must not import Hytale types: " + offenders);
    }
}

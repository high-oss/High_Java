// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every hand-written {@code .java} and {@code .gradle.kts} file carries the
 * SPDX header. {@code generated/} is exempt — the generator owns it, and its
 * header (if any) is the generator's, not ours.
 */
class LicenseHeaderTest {

    private static final String COPYRIGHT = "Copyright (c) 2026 Truestock";
    private static final String SPDX = "SPDX-License-Identifier: MIT";

    @Test
    void everyHandWrittenSourceFileCarriesTheSpdxHeader() throws IOException {
        Path root = repoRoot();
        List<Path> files = new ArrayList<>();
        for (String dir : new String[] {"src", "scripts", "examples"}) {
            Path dirPath = root.resolve(dir);
            if (!Files.isDirectory(dirPath)) continue;
            try (Stream<Path> walk = Files.walk(dirPath)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> hasRelevantExtension(p.toString()))
                    .forEach(files::add);
            }
        }
        for (String buildFile : new String[] {"build.gradle.kts", "settings.gradle.kts"}) {
            Path p = root.resolve(buildFile);
            if (Files.isRegularFile(p)) files.add(p);
        }

        assertTrue(files.size() > 0, "expected to find hand-written source files to check");

        List<String> missing = new ArrayList<>();
        for (Path file : files) {
            String head = readHead(file);
            if (!head.contains(COPYRIGHT) || !head.contains(SPDX)) {
                missing.add(root.relativize(file).toString());
            }
        }

        assertTrue(missing.isEmpty(), "missing licence header:\n" + String.join("\n", missing));
    }

    @Test
    void theLicenseFileIsMitAndNamesTheCopyrightHolder() throws IOException {
        String licence = Files.readString(repoRoot().resolve("LICENSE"));
        assertTrue(licence.contains("MIT License"));
        assertTrue(licence.contains(COPYRIGHT));
        assertTrue(licence.contains("WITHOUT WARRANTY OF ANY KIND"));
    }

    private static boolean hasRelevantExtension(String name) {
        return name.endsWith(".java") || name.endsWith(".gradle.kts") || name.endsWith(".sh");
    }

    private static String readHead(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int len = Math.min(bytes.length, 400);
            return new String(bytes, 0, len, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Walks up from the working directory to find the repo root (has settings.gradle.kts). */
    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("settings.gradle.kts"))) return dir;
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate repo root from " + Paths.get("").toAbsolutePath());
    }
}

// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Conformance point 15: {@link ErrorCodes#ALL} and {@link Environments} must
 * not drift from the pinned spec. Hand-coding both is fine; letting them
 * drift silently is not.
 */
class SpecSyncTest {

    private static JsonNode pinnedSpec() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        if (dir == null) throw new IllegalStateException("could not locate repo root");
        Path specPath = dir.resolve("generated/openapi.json");
        return new ObjectMapper().readTree(Files.readString(specPath));
    }

    @Test
    void errorCodesMatchesTheXErrorCodesExtensionInThePinnedSpec() throws IOException {
        JsonNode spec = pinnedSpec();
        JsonNode codeNode = spec.at("/components/schemas/Error/properties/code");
        JsonNode xErrorCodes = codeNode.get("x-error-codes");
        List<String> fromSpec = new ArrayList<>();
        for (JsonNode item : xErrorCodes) fromSpec.add(item.asText());

        assertEquals(fromSpec, ErrorCodes.ALL);
    }

    @Test
    void environmentsHasOneEntryPerServerWithTheHostTheSpecDeclares() throws IOException {
        JsonNode spec = pinnedSpec();
        Map<String, String> fromSpec = new LinkedHashMap<>();
        for (JsonNode server : spec.get("servers")) {
            String env = server.get("x-environment").asText();
            String origin = URI.create(server.get("url").asText()).resolve("/").toString();
            // Strip the trailing slash `resolve("/")` leaves, to compare with our origin-only hosts.
            fromSpec.put(env, origin.substring(0, origin.length() - 1));
        }

        Map<String, String> fromCode = new LinkedHashMap<>();
        for (Environment env : Environment.values()) {
            fromCode.put(env.wireValue(), Environments.get(env).api());
        }

        assertEquals(fromSpec, fromCode);
    }
}

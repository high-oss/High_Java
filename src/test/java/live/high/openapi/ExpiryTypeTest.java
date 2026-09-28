// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link ExpiryType} to the pinned spec's declared enum, the same
 * regression the Node SDK's review caught: a facade that widens a spec enum
 * to a plain string lets a value the API rejects compile.
 */
class ExpiryTypeTest {

    @Test
    void matchesTheEnumThePinnedSpecDeclaresForTheTypePathParameter() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isRegularFile(dir.resolve("settings.gradle.kts"))) {
            dir = dir.getParent();
        }
        JsonNode spec = new ObjectMapper().readTree(Files.readString(dir.resolve("generated/openapi.json")));
        JsonNode parameters = spec.at("/paths/~1scrips~1{symbol}~1{type}~1expiries/get/parameters");

        JsonNode typeParam = null;
        for (JsonNode p : parameters) {
            if ("type".equals(p.get("name").asText())) typeParam = p;
        }

        List<String> fromSpec = new ArrayList<>();
        for (JsonNode v : typeParam.at("/schema/enum")) fromSpec.add(v.asText());

        List<String> fromCode = new ArrayList<>();
        for (ExpiryType type : ExpiryType.values()) fromCode.add(type.wireValue());

        assertEquals(fromSpec, fromCode);
    }
}

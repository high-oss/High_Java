// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.Map;
import live.high.openapi.HighClientOptions;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.generated.model.Profile;
import live.high.openapi.support.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProfileResourceTest {

    private TestServer server;

    @AfterEach
    void closeServer() {
        if (server != null) server.close();
    }

    private ProfileResource profile() {
        ResolvedConfig config = ResolvedConfig.resolve(
            HighClientOptions.builder().baseUrl(server.baseUrl()).accessToken("tok").build(), Map.of());
        return new ProfileResource(config);
    }

    @Test
    void getFetchesTheMaskedProfile() throws IOException {
        server = TestServer.start((ex, i) -> TestServer.json(ex, 200, "{\"requestId\":\"r\",\"data\":"
            + "{\"clientId\":\"HS1234\",\"name\":\"Asha Verma\",\"email\":\"asha.verma@example.com\","
            + "\"mobile\":\"9876543210\",\"pan\":\"XXXXXX234F\",\"aadhaar\":\"XXXXXXXX9012\","
            + "\"depository\":\"CDSL\",\"boCode\":\"1208160000123456\",\"dpCode\":\"12081600\","
            + "\"isPOAApproved\":true,\"isNRI\":false,\"isMTFEnabled\":true,"
            + "\"segmentsAllowed\":[\"NSE_CASH\",\"NSE_FNO\"],"
            + "\"address\":{\"address\":\"12, MG Road\",\"city\":\"Bengaluru\",\"state\":\"Karnataka\",\"pinCode\":\"560001\"},"
            + "\"bankAccounts\":[{\"bankName\":\"HDFC Bank\",\"accountNumber\":\"XXXXXX4321\","
            + "\"ifscCode\":\"HDFC0000001\",\"branch\":\"MG Road\",\"isPrimary\":true}]}}"));
        Profile profile = profile().get();
        TestServer.ReceivedRequest req = server.requests().get(0);
        assertEquals("GET", req.method());
        assertEquals("/v1/profile", req.path());
        assertEquals("HS1234", profile.getClientId());
        assertEquals(Boolean.TRUE, profile.getIsMTFEnabled());
        assertEquals(Boolean.FALSE, profile.getIsNRI());
        assertEquals("560001", profile.getAddress().getPinCode());
        assertEquals("XXXXXX4321", profile.getBankAccounts().get(0).getAccountNumber());
        assertEquals(Boolean.TRUE, profile.getBankAccounts().get(0).getIsPrimary());
    }
}

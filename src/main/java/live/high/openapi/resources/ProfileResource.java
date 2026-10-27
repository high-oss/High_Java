// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;
import live.high.openapi.generated.model.Profile;

public final class ProfileResource extends BaseResource {

    private static final TypeReference<Profile> PROFILE = new TypeReference<>() {};

    public ProfileResource(ResolvedConfig config) {
        super(config);
    }

    /**
     * The account holder's identity, demat and bank details. PAN, Aadhaar and
     * bank account numbers are masked to their last four characters.
     */
    public Profile get() {
        return get("/profile", Transport.AuthKind.BEARER, PROFILE);
    }

    public CompletableFuture<Profile> getAsync() {
        return getAsync("/profile", Transport.AuthKind.BEARER, PROFILE);
    }
}

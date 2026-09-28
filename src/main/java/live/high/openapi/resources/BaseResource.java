// Copyright (c) 2026 Truestock
// SPDX-License-Identifier: MIT

package live.high.openapi.resources;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import live.high.openapi.ResolvedConfig;
import live.high.openapi.Transport;

/**
 * Shared plumbing for every resource: builds a {@link Transport.RequestSpec}
 * and sends it. Every operation gets a synchronous form and a {@code
 * ...Async} form; both funnel through {@link Transport}, so there is exactly
 * one implementation of retry, timeout, cancellation and redaction (contract
 * §5–7).
 */
abstract class BaseResource {

    final ResolvedConfig config;

    BaseResource(ResolvedConfig config) {
        this.config = config;
    }

    <T> T get(String path, Transport.AuthKind auth, TypeReference<T> type) {
        return Transport.send(config, new Transport.RequestSpec("GET", path, auth, Map.of(), null), type);
    }

    <T> CompletableFuture<T> getAsync(String path, Transport.AuthKind auth, TypeReference<T> type) {
        return Transport.sendAsync(config, new Transport.RequestSpec("GET", path, auth, Map.of(), null), type);
    }

    <T> T get(String path, Transport.AuthKind auth, Map<String, String> query, TypeReference<T> type) {
        return Transport.send(config, new Transport.RequestSpec("GET", path, auth, query, null), type);
    }

    <T> CompletableFuture<T> getAsync(String path, Transport.AuthKind auth, Map<String, String> query, TypeReference<T> type) {
        return Transport.sendAsync(config, new Transport.RequestSpec("GET", path, auth, query, null), type);
    }

    <T> T write(String method, String path, Object body, TypeReference<T> type) {
        return Transport.send(config, new Transport.RequestSpec(method, path, Transport.AuthKind.BEARER, Map.of(), body), type);
    }

    <T> CompletableFuture<T> writeAsync(String method, String path, Object body, TypeReference<T> type) {
        return Transport.sendAsync(config, new Transport.RequestSpec(method, path, Transport.AuthKind.BEARER, Map.of(), body), type);
    }
}

#!/usr/bin/env bash
# Copyright (c) 2026 Truestock
# SPDX-License-Identifier: MIT
#
# Regenerates generated/ from the spec commit pinned in spec.lock.json.
#
# Reads the spec AT THE PINNED COMMIT via `git show`, not from the working
# tree of ../spec — a dirty spec checkout must never leak into generated code.
#
# Models come from OpenAPI Generator (java, `native` library, Jackson
# serialization). Only two schemas are hand-written instead of generated:
# Error and its `message` field's ErrorMessage wrapper. That is the one
# `oneOf: [string, array<string>]` schema in the whole spec, and the
# generator emits code that does not compile for it (`List<String>.class`
# is not legal Java, and neither is the accessor name it derives,
# `getList<String>()` — a known limitation of the generator's oneOf template
# for generic member types). The SDK does not need a typed Error model
# anyway: HighApiError is built straight off the raw JSON response body, the
# same way the Node SDK's errors.ts does.
#
# The generated Api*.java / ApiClient operation-invoking classes are not
# used either. This SDK's HTTP layer implements its own retry, cancellation,
# timeout and redaction behaviour (contract §5–7), which those classes do
# not: a second, uncontrolled HTTP client sitting in the jar would be a
# credential-leak and duplicate-order footgun waiting to be imported by
# mistake. Only the model/ POJOs plus the small support classes they need to
# compile (ApiClient's static url-encoding helpers, JSON's ObjectMapper
# configuration, the RFC3339 date module) are kept.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

LOCK_REPO=$(node -e "console.log(require('./spec.lock.json').repo)" 2>/dev/null || python3 -c "import json;print(json.load(open('spec.lock.json'))['repo'])" 2>/dev/null || grep -o '"repo": *"[^"]*"' spec.lock.json | sed 's/.*: *"//;s/"$//')
LOCK_COMMIT=$(grep -o '"commit": *"[^"]*"' spec.lock.json | sed 's/.*: *"//;s/"$//')
LOCK_FILE=$(grep -o '"file": *"[^"]*"' spec.lock.json | sed 's/.*: *"//;s/"$//')

SPEC_REPO_ABS="$(cd "$LOCK_REPO" && pwd)"

echo "Reading ${LOCK_FILE} at ${LOCK_COMMIT} from ${SPEC_REPO_ABS}"
git -C "$SPEC_REPO_ABS" show "${LOCK_COMMIT}:${LOCK_FILE}" > generated/openapi.json

rm -rf .regen-tmp
mkdir -p .regen-tmp

MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd):/local" \
  openapitools/openapi-generator-cli:latest-release generate \
  -i /local/generated/openapi.json \
  -g java \
  --library native \
  -o /local/.regen-tmp \
  --model-package live.high.openapi.generated.model \
  --api-package live.high.openapi.generated.api \
  --invoker-package live.high.openapi.generated \
  --global-property models,supportingFiles,modelDocs=false,modelTests=false \
  --additional-properties=serializationLibrary=jackson,hideGenerationTimestamp=true,dateLibrary=java8

rm -rf generated/src
mkdir -p generated/src/main/java/live/high/openapi/generated/model

for f in ApiClient.java ApiException.java ApiResponse.java Configuration.java JSON.java \
         Pair.java RFC3339DateFormat.java RFC3339InstantDeserializer.java \
         RFC3339JavaTimeModule.java ServerConfiguration.java ServerVariable.java; do
  cp ".regen-tmp/src/main/java/live/high/openapi/generated/$f" \
     "generated/src/main/java/live/high/openapi/generated/$f"
done

for f in .regen-tmp/src/main/java/live/high/openapi/generated/model/*.java; do
  base=$(basename "$f")
  case "$base" in
    # The one oneOf schema in the spec (Error.message); see the header comment.
    Error.java|ErrorMessage.java|AbstractOpenApiSchema.java) continue ;;
    # Envelope wrappers we unwrap generically, and the redirect-consent-flow
    # models for operations this SDK deliberately does not wrap (contract §1).
    *200Response.java|Envelope.java|Consent.java) continue ;;
    *) cp "$f" "generated/src/main/java/live/high/openapi/generated/model/$base" ;;
  esac
done

rm -rf .regen-tmp
echo "Generated from spec commit ${LOCK_COMMIT:0:7}."

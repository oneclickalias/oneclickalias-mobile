# API models

Request and response types of the OneClickAlias user APIs, generated from
the server's OpenAPI specs (generator `tools/kotlingen`) and copied here
verbatim:

```
api/
  CONTRACTS_VERSION   pinned contracts revision (ref + resolved commit)
  kotlin/             generated Kotlin models (kotlinx.serialization), used by android/
    com/oneclickalias/contracts/auth/AuthModels.kt   paths /auth, /devices
    com/oneclickalias/contracts/ams/AmsModels.kt     paths /api
```

Only the publicly reachable user API is covered (the same paths the website
and the browser extension call through `https://oneclickalias.com`); admin
and internal endpoints are not part of the generated code. The files
are types only, without server logic, and are distributed with this repo
under its GPL-3.0 license.

## Updating

`scripts/sync-api-models.sh` reads the files from a `contracts` checkout with
`git archive` at the pinned commit, so the branch or uncommitted changes in
that checkout do not matter. `CONTRACTS_DIR` points to the checkout (default
`../contracts` relative to this repo's root).

```bash
scripts/sync-api-models.sh               # copy the pinned revision into api/kotlin/
scripts/sync-api-models.sh --check       # exit 1 if api/kotlin/ differs from the pin
scripts/sync-api-models.sh --pin v0.5.0  # pin a new tag or commit, then copy
```

Rules:

- Never edit generated files by hand; change the spec, release it, then
  re-pin and copy here. `--check` catches hand edits.
- The apps use API request/response types only from `api/kotlin/`, never
  hand-written copies.
- The Android build compiles `api/kotlin/` as an extra source directory; it
  needs no access to the specs. ktlint skips the folder.
- The generated `Error` class shadows `kotlin.Error`; import it with an
  alias, e.g. `import com.oneclickalias.contracts.auth.Error as AuthError`.
- The generated enums are strict (an unknown value fails decoding). The app
  reads error bodies tolerantly instead of decoding `Error` directly.

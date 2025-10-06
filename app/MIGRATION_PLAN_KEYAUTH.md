KeyAuthRepository migration plan

Goal
- Safely migrate `KeyAuthRepository` internals from direct `SecurePreferences` usage to the `SecurePrefsAdapter` pattern (or a controlled internal wrapper) with zero runtime regressions.

Constraints
- Do NOT change `KeyAuthRepository` external behavior or persistence layout without an explicit PreferencesMigration tool and tests.
- `initialize()` sequence and behavior must remain identical.
- Stored session tokens MUST NOT be passed into `initialize()` to avoid "session not found" failures.
- Keep diagnostic/test-only `SecurePreferences` APIs available for migration tests and PreferencesMigration.

Success criteria
- All unit tests and existing diagnostic tests remain green.
- No change in serialized storage format unless a migration step is added and tested.
- CI build (assembleDebug / unit tests) stays green.

High-level plan (phased)

Phase 0 — Discovery (non-invasive)
- Inventory all usages of `SecurePreferences` inside `KeyAuthRepository`.
- Identify methods that need adapter equivalents (storeSessionToken, getSessionToken, storeHWID, getStoredHWID, isSessionTokenValid, setDeviceRegistered, getDeviceTrustLevel, setDeviceTrustLevel, getEncryptionStatus, getStorageInfo, etc.).
- Mark which methods are diagnostic/test-only (`getEncryptionStatus`, `getStorageInfo`) and must remain on `SecurePreferences`.

Phase 1 — Adapter contract & shim
- Define a narrow internal interface `KeyAuthSessionStore` that exposes only the session/registration APIs KeyAuth needs (no diagnostic APIs).
  - Example: storeSessionToken(sessionId, expiry), getSessionToken(), clearSessionToken(), storeHWID(), getStoredHWID(), isDeviceRegistered(), setDeviceRegistered(hwid, license), getDeviceTrustLevel(), setDeviceTrustLevel(level), clearAll(), etc.
- Implement `KeyAuthSessionStoreImpl` that delegates to `SecurePrefsAdapterImpl` by default.
- Provide a factory or constructor parameter in `KeyAuthRepository` to accept `KeyAuthSessionStore`. Default constructor uses `KeyAuthSessionStoreImpl(SecurePrefsAdapterImpl(context))`.
- Keep `KeyAuthRepository`’s public API unchanged.

Phase 2 — Tests & compatibility
- Add unit tests for `KeyAuthSessionStoreImpl` covering happy path and edge cases.
- Add integration unit tests that instantiate `KeyAuthRepository` with the default `KeyAuthSessionStoreImpl` and verify identical behavior compared to baseline (golden tests, mocks, or snapshot of a few key flows).
- For diagnostic APIs still on `SecurePreferences`, keep them untouched; where tests rely on them, mock `SecurePreferences` or `KeyAuthSessionStore` as appropriate.

Phase 3 — Gradual switch and verification
- Replace internal `SecurePreferences` fields in `KeyAuthRepository` with `KeyAuthSessionStore` param. Keep a secondary constructor that accepts the old `SecurePreferences` (bridge) to ease change.
- Run full unit tests, Robolectric tests, and smoke tests.
- Run manual end-to-end checks if applicable (OTA, HWID flows).

Phase 4 — Cleanup (optional, follow-on)
- If desired, and after extensive testing, remove diagnostic-only `SecurePreferences` usage in KeyAuth internals by implementing equivalents (or keeping `SecurePreferences` strictly for diagnostics).
- Update detekt/lint baselines if imports or file names change.

Edge cases and risks
- Storage format change: must be accompanied by `PreferencesMigration.migrateIfNeeded()` and tests that show migration correctness.
- Race conditions during migration: ensure the repository initialization sequence remains ordered and thread-safe.
- Tests that rely on diagnostic methods will still need `SecurePreferences` available. Consider exposing a test-only constructor that accepts a `SecurePreferences` instance.

Rollback plan
- If tests fail after a change, revert the KeyAuthRepository constructor signature and re-introduce the previous `SecurePreferences` usage. Keep the adapter implementation alongside to continue migrating callers.

Acceptance checklist
- [ ] Define `KeyAuthSessionStore` interface (file + doc comment)
- [ ] Implement `KeyAuthSessionStoreImpl` delegating to `SecurePrefsAdapterImpl`
- [ ] Add tests for `KeyAuthSessionStoreImpl`
- [ ] Modify `KeyAuthRepository` to accept `KeyAuthSessionStore` via constructor while preserving default behavior
- [ ] Run full unit test suite and Robolectric tests
- [ ] Update README-SecurePrefsAdapter.md with notes about KeyAuth migration

Estimated effort
- Design + small API + impl + unit tests: 1–2 work days
- Full integration testing + migration of diagnostic calls: additional 1–2 days depending on test scope

Notes
- This plan intentionally avoids removing `SecurePreferences` diagnostic APIs. Removing or changing those will increase risk and require an explicit `PreferencesMigration` implementation and tests.
- Keep changes small and reversible.

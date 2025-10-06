SecurePrefsAdapter - Usage & Migration Guide

Purpose

This document explains when to use the SecurePrefsAdapter (and its implementation) vs the legacy SecurePreferences class.

When to use the adapter

- New code (UI, ViewModels, non-KeyAuth utilities): prefer injecting `SecurePrefsAdapter` and use the adapter API.
- Tests: mock `SecurePrefsAdapter` when you only need basic preference behaviour (save/get/clear for license/session flags).
- SessionService & centralized session handling: use `SecurePrefsAdapterImpl` which implements `SessionStore`.

When to keep using SecurePreferences directly

- KeyAuth internals: `KeyAuthRepository` and direct KeyAuth API code must keep using the existing `SecurePreferences` until a dedicated, fully-tested migration plan is implemented. Do not change these files casually.
- PreferencesMigration: migration helpers that rely on internal diagnostic methods (for example, `getStorageInfo()`, `isEncrypted()` or `getEncryptionStatus()`) should continue to use `SecurePreferences`.
- Low-level diagnostic tests: tests that call `SecurePreferences` test-only methods should continue to use `SecurePreferences`.

Migration policy (conservative)

1. Identify low-risk callers: tests/utilities that use only the adapter-exposed methods (remember license, auto-login, save/get license, session token store/get/clear).
2. Create `SecurePrefsAdapterImpl(context)` and pass it into the consumer (ViewModel/utility) — do not change `KeyAuthRepository` or other core auth classes.
3. Run unit tests after each small batch. Keep diffs small and well-scoped.
4. For `KeyAuthRepository` migration, write a dedicated compatibility test suite that reproduces session-not-found and session-restore scenarios. Only migrate `KeyAuthRepository` after those tests are green.

Notes for reviewers

- The adapter intentionally does not expose diagnostic/test-only methods present on `SecurePreferences`. Tests that require those diagnostics should keep using `SecurePreferences`.
- `SecurePrefsAdapterImpl` is a thin adapter delegating to `SecurePreferences` so current persistence behaviour remains unchanged.

Contacts

For migration questions or to approve a `KeyAuthRepository` migration, contact the maintainer or open a PR describing the migration tests and rollback strategy.

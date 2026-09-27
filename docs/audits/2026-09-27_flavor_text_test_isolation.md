# Forest Run — Flavor-text JVM test isolation (2026-09-27)

## Exact-head failure

Android validation run 36311697173 on `5c79ca07` compiled the test sources and executed 1,139 JVM tests, but `FlavorTextManagerTest.invalid entries are rejected and values are sanitized` failed at its first assertion: it expected `activeCountForTest() == 0` after three rejected spawn attempts. Source inspection shows the three malformed attempts are ignored by `spawn`; the tested `FlavorTextManager` is an application-wide singleton, however, and the test class only cleared it in `@After`. Another test class could leave active entries before the first `FlavorTextManagerTest` method.

## Minimal correction

Add a matching `@Before` clear so every test method begins with the empty singleton assumed by its assertions; retain `@After` cleanup. No production behavior, encounter rewards, text validation, or release contract is changed. The failure was unrelated to the high-score patch; avoid attributing a test-order state leak to that code.

Require exact resulting HEAD's full JVM/host and API-35 validation before closure. Physical-device and human acceptance remain separate.

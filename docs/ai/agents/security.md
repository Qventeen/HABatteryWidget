# Security and secrets

- The HA token goes only through `TokenStore` (`EncryptedTokenStore`, AES-GCM
  key in the Android Keystore). Never log it; never put it in an exception, a
  `SyncError` or UI state.
- Never commit signing keys, tokens or other secrets. Use placeholders for real
  hosts and credentials.
- Release signing reads four `habattery.release.*` Gradle properties from
  `~/.gradle/gradle.properties` (or `HABATTERY_RELEASE_*` env variables). The
  key lives outside the repo (README, "Release key"). Never read or print these
  values.
- The old `app/release.keystore` is in public git history: do not trust or use
  it.
- Plain `http://` is allowed only for local addresses (`UrlPolicy`).

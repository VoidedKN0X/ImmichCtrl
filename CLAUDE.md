# ImmichCtrl

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "ImmichCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.

## Encryption at rest

- Each offline album's list (`filesDir/offline/<account>/<album>/album.json`) is sealed with AES-256-GCM (`data/crypto/SealedFile`, marker `IMMICHCTRL-REST1`) under the data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `immichctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so workers run with the screen locked). It's independent of the app lock, the login key (`KeystoreCrypto`) and `LockVault`: don't mix them.
- Not encrypted, on purpose: the offline photo and video files (Coil and the video player open them by path), the SQLite databases (`SyncDb`, `backup.db`, WorkManager; no SQLCipher), the image and map caches, `cache/incoming` and `cache/shared`, and the DataStore settings.
- Readers accept both sealed and ordinary lists (`SealedFile.isSealed`). Without a data key the app reads and writes ordinary files as before.
- Only `OfflineStore.setAccount` (background thread) asks the Keystore for the data key (`DataKeyStore.get`); everything else uses `DataKeyStore.peek()`, which never touches the disk or the Keystore, so it's safe on the main thread. Keep it that way.
- An ordinary list only becomes a sealed one through `SealedFile.sealInPlace` (temp file, sync, read back, compare, atomic rename), under `manifestLock`. Never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`). A sealed list that can't be opened is left alone and its album isn't listed.
- `SealedFileTest` covers the format, the migration step and the key rules (`./gradlew :app:testDebugUnitTest`; CI doesn't run unit tests).

## Dependency verification

Every library and plugin download is checked against `gradle/verification-metadata.xml` (SHA-256 only, no PGP). When you add, remove or update a dependency or Gradle plugin, run the manual **Dependency verification metadata** workflow (`gh api -X POST repos/VoidedKN0X/ImmichCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (gzipped and base64-encoded between the BEGIN/END markers), check the diff (only Maven Central and Google's Maven) and commit it in the same push. Keep the `<trusted-artifacts>` entries for `-sources.jar` and `-javadoc.jar`. Never turn verification off or to lenient in CI.

`settings.gradle.kts` lists only `google()` and `mavenCentral()` for plugins, not `gradlePluginPortal()`: the portal serves different files for the Kotlin plugin markers, which made verification fail whenever Maven Central had a hiccup. Don't add it back unfiltered (see the comment in that file).

## Build and signing (CI)

`.github/workflows/build.yml` has four jobs: `version` (is this a release?), `build` (Gradle, **no secrets**, `UNSIGNED_RELEASE=true` gives `app-release-unsigned.apk`; runs every check), `sign` (no checkout, no Gradle: `zipalign` + `apksigner` with the release key, then checks the signer against `EXPECTED_CERT_SHA256` and that the APK's contents didn't change) and `release` (attestation, release notes, publish).

- Never give the `build` job a secret, and never run Gradle or check out the repo in `sign`. New build checks (tests, lint, APK checks) go in `build`.
- `EXPECTED_CERT_SHA256` in `build.yml` must match the fingerprint in `SECURITY.md` and `README.md`; the build fails if it doesn't.
- No Gradle cache in any workflow (`cache-disabled: true`): a cache saved by a failed run used to break every later build.

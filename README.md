# OneClickAlias Mobile

Native mobile apps for [OneClickAlias](https://oneclickalias.com): email
aliases in one click. No username, no password — an account is a 16-digit
account number.

## Layout

| Path       | Content                                                     |
|------------|-------------------------------------------------------------|
| `android/` | Android app (Kotlin, Jetpack Compose, Material 3)           |
| `api/`     | API models generated from the server's OpenAPI specs        |
| `docs/`    | Platform-neutral flow descriptions (`flows.md`)             |

An iOS app will follow in `ios/`.

## Android

Requirements: JDK 17 or newer and the Android SDK (platform 37,
build tools 36). The Gradle wrapper downloads everything else.

```bash
cd android
./gradlew assembleDebug                    # app/build/outputs/apk/debug/app-debug.apk
./gradlew ktlintCheck lint test            # checks and JVM tests, no device needed
```

The app depends only on free software (AndroidX, Kotlin libraries, OkHttp)
and runs on devices without Google Play Services.

### Endpoints

The app talks to three origins, compiled in as `BuildConfig` fields. All
default to `https://oneclickalias.com`.

| Gradle property | BuildConfig field | Paths |
|---|---|---|
| `ocaApiBase`  | `API_BASE`  | `/api/*` |
| `ocaAuthBase` | `AUTH_BASE` | `/auth/*`, `/devices` |
| `ocaWebBase`  | `WEB_BASE`  | website links |

Values must be a bare origin (`http(s)://host[:port]`, no path, no trailing
slash); the build fails otherwise. Example for a server running on your
computer, with the phone connected over USB:

```bash
adb reverse tcp:8080 tcp:8080
adb reverse tcp:8081 tcp:8081
./gradlew assembleDebug \
  -PocaAuthBase=http://localhost:8080 \
  -PocaApiBase=http://localhost:8081
```

Only the debug build allows cleartext HTTP, and only to `localhost` and
`127.0.0.1`. Release builds are HTTPS-only with system CAs. Tests assume the
defaults, so run `test` without the overrides.

## Verifying the APK

Release APKs are signed with this certificate (SHA-256, also in
`android/release-signing-cert.sha256`):

```
5D:93:DA:F9:A7:EB:6A:8A:65:94:8B:DE:0B:D9:79:E6:01:63:DA:F7:B0:41:E8:4D:02:F9:8C:85:41:93:04:67
```

Check a downloaded APK with `apksigner verify --print-certs oneclickalias-X.Y.Z.apk`.

## License

GPL-3.0-only, see [LICENSE](LICENSE). The bundled Geist and Geist Mono fonts
are licensed under the SIL Open Font License 1.1
(`android/app/src/main/assets/licenses/Geist-OFL.txt`).

# Releasing Nivo Launcher

Two GitHub Actions workflows live in `.github/workflows/`:

| Workflow | Runs on | What it does |
|---|---|---|
| `build.yml` | every push to `main`, every pull request, or by hand | Unit tests + a debug APK, downloadable from the run's **Artifacts** for 14 days |
| `release.yml` | a pushed `v*` tag, or by hand for an existing tag | Unit tests, builds the APK, publishes it (plus a `.sha256`) to **Releases** |

## Cut a release
```
git tag v1.0.0
git push origin v1.0.0
```
Or on GitHub: **Releases → Draft a new release → choose a tag → type a new tag such as `v1.0.0` → Publish**.
The workflow attaches `NivoLauncher-1.0.0.apk` to that release and adds generated release notes.

* `v1.2.3` → versionName `1.2.3`, versionCode `10203` (each part must stay below 100), so every newer tag can update an older install.
* A tag with a suffix (`v1.3.0-beta1`) is published as a **pre-release**.
* To rebuild an existing tag: **Actions → Release → Run workflow** and enter the tag.

## One-time setup: signing (strongly recommended)
Without these secrets the workflow still publishes, but a **debug-signed** APK named `…-debug.apk`. Android will refuse
to install a later build over it if the key differs, so set up signing before you share builds.

1. Create a keystore once and keep it (and its passwords) safe. Losing it means users must uninstall to update:
   ```
   keytool -genkeypair -v -keystore release.jks -alias nivo -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Base64-encode it: `base64 -w0 release.jks` (macOS: `base64 -i release.jks`).
3. In the repo: **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | the base64 output from step 2 |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias (`nivo` above) |
| `SIGNING_KEY_PASSWORD` | key password (optional; defaults to the store password) |
| `MET_CONTACT` | optional: contact email/URL sent to MET Norway in the weather User-Agent |

The workflow writes these into a temporary `keystore.properties` / `local.properties` (both git-ignored), the same
files a local release build uses, and deletes them when the job ends.

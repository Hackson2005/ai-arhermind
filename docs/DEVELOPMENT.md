# Development

User-facing start: **[HOW_TO_RUN.md](HOW_TO_RUN.md)**. Readiness: **[STATUS.md](STATUS.md)**.

This page is the extra detail for people changing the code.

## Desktop

```bash
./build.sh          # needs Maven and JDK 11+; downloads Java dependencies
./run-desktop.sh
```

`build.sh` / `build.bat` use Maven from `PATH`. The Maven shade plugin packages
the Playwright Java dependency into the runnable `AetherMind.jar`.
`uninstall.bat` still deletes `C:\AetherMind` — do not run it against this git checkout.

If `./build.sh` says a JDK is missing:

```bash
sudo apt install openjdk-21-jdk    # Debian / Kali / Ubuntu
```

Compile targets Java 11. The JAR includes `knowledge.json` from `shared/`.
Install the Playwright Chromium browser once with:

```powershell
mvn compile exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

The browser binary is installed separately from the JAR.

## Java knowledge collector

Build the project, then run the collector from the repository root. Tavily is
optional; without `TAVILY_API_KEY` the search tool uses DuckDuckGo:

```powershell
java -cp AetherMind.jar ContinuousSucker
```

It searches the configured topics immediately, saves successful results under
`knowledge/`, and repeats every six hours. Stop it with Ctrl+C. Failed searches
are logged rather than written as knowledge.

## Web

```bash
./run-web.sh
# http://127.0.0.1:8080/web/
```

`aether.js` fetches `knowledge.json` next to `index.html`, then `../shared/knowledge.json`. Always run `./sync-knowledge.sh` after editing the shared map. There is no bundler.

## Android

Open **`android/`** in Android Studio. `app/build.gradle.kts` adds `../../shared` as an assets source so `knowledge.json` is packaged automatically.

No Gradle wrapper in git. Do not commit `local.properties`, `*.jks`, or `android/app/build/`.

## Knowledge changes

1. Edit `shared/knowledge.json` only
2. `./sync-knowledge.sh`
3. `python3 test_match.py`
4. Rebuild desktop JAR / Android app; refresh the web tab

## Version string

`0.9` is still duplicated. When bumping, update:

- `AetherMindApp.java` (title + boot)
- `web/index.html`
- `android/app/build.gradle.kts` (`versionName`)
- `android/.../strings.xml` (`app_subtitle`)
- `android/.../MainActivity.kt` (boot text)
- `shared/knowledge.json` (`version`)
- `README.md`, `CHANGELOG.md`, `ROADMAP.md`, `docs/STATUS.md`, `docs/HOW_TO_RUN.md`

## Layout

```
aether/
├── shared/knowledge.json
├── AetherMindApp.java
├── AetherBrain.java
├── build.sh / run-desktop.sh / run-web.sh / sync-knowledge.sh
├── test_match.py
├── android/
├── web/
└── docs/
```

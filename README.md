# AI ARHERMIND

> **No cloud. No API. Pure local intelligence.**

AI ARHERMIND uses a local keyword-based engine with short-term memory on Desktop, Android, and Web, backed by one shared knowledge file. The desktop app can also search and browse the web for questions the local knowledge does not match.

**Current version: v0.9**

**Start here:** [docs/HOW_TO_RUN.md](docs/HOW_TO_RUN.md) · **Is it working?** [docs/STATUS.md](docs/STATUS.md)

```bash
./run-web.sh
# open http://127.0.0.1:8080/web/
```

The desktop app opens a local login dialog at startup. Enter the configured
user name and password to open the chat; canceling closes the app. The password
is verified from a salted hash stored outside the repository in
`~/.aethermind/login.properties` (the file must contain `password.salt` and
`password.hash` values encoded as Base64). The optional Windows voice unlock
phrase is configured there as `voice.phrase`. Do not commit this local file or
publish its contents.
On Windows, the login also offers Windows Hello verification (face recognition
when configured, or another Windows Hello method); password sign-in remains
available as a fallback. It also offers an optional local speech-phrase unlock:
say your configured phrase when prompted. This recognizes the phrase, not the
speaker, so it is not biometric or secure against someone who knows or records
the phrase.
Speech is processed by the local Windows recognizer; the app does not save audio.
The desktop chat's **ADD PICTURE** button inserts a local image preview into the
conversation. Pictures are display-only: the text-based Aether engine does not
analyze them, send them online, or restore them from chat history.
The desktop app automatically searches the web when its local knowledge has no
match. It uses DuckDuckGo without configuration, or Tavily when
`TAVILY_API_KEY` is set. Result pages are browsed with Jina Reader; matched
messages remain local.

---

## Platforms

| Platform | Language | Entry point |
|----------|----------|-------------|
| Desktop | Java (Swing) | `AetherMindApp.java` + `AetherBrain.java` |
| Android | Kotlin | `android/` |
| Web | Vanilla JS / HTML / CSS | `web/` |

The Android app requires a device biometric to unlock at launch. Android presents
its system prompt and uses an enrolled face or another supported biometric; the
app does not access or store biometric data. Set up a biometric in device
settings before opening the app.

All three load [`shared/knowledge.json`](shared/knowledge.json). No platform needs the internet at runtime.

---

## Python web tools

`tools/` provides Jina Reader page browsing, Tavily web search with a DuckDuckGo
fallback, and a Playwright browser for JavaScript-heavy pages. `continuous_sucker.py`
periodically searches configured topics and writes browsed content to Markdown
files in `knowledge/`. Tavily is optional; set `TAVILY_API_KEY` to use it.

Install from the repository root in PowerShell:

```powershell
py -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
python -m playwright install chromium
```

On macOS/Linux, replace the activation command with
`source .venv/bin/activate`. The search tool uses DuckDuckGo if `TAVILY_API_KEY`
is unset or Tavily is unavailable.

The Java desktop also exposes optional web search through
`AetherBrain.webSearchAndBrowse(query, maxResults)`. It requires internet
access; Tavily is optional, and DuckDuckGo is used when `TAVILY_API_KEY` is
unset or Tavily is unavailable. Search results are browsed with Jina Reader.

The Java desktop can also browse JavaScript-rendered pages using
`AetherBrain.browseWithPlaywright(url)`. Build with Maven and a JDK 11 or newer,
then install Chromium once with:

```powershell
mvn compile exec:java "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

The Maven build packages Playwright into the runnable desktop JAR. The Playwright
browser binaries are installed separately and require internet access.

To run the Java scheduled knowledge collector, set `TAVILY_API_KEY` and run
`build.bat` (Windows) or `./build.sh` (macOS/Linux) first. Then start it from the
repository root; it runs immediately, saves successful results in `knowledge/`,
and repeats every six hours:

```powershell
java -cp AetherMind.jar ContinuousSucker
```

Stop it with Ctrl+C. Search failures are logged and are not saved as knowledge.

Start the background knowledge collector (stop with Ctrl+C):

```powershell
python continuous_sucker.py --topic "artificial intelligence" --topic "Java" --interval-minutes 60 --max-results 3
```

Example OpenAI-compatible tool loop (install the provider's `openai` SDK
separately if needed):

```python
import json
from openai import OpenAI
from tools import TOOL_DEFINITIONS, execute_tool

client = OpenAI()  # Or set base_url for your OpenAI-compatible service.
messages = [{"role": "user", "content": "Find and summarize recent Python news."}]

while True:
    response = client.chat.completions.create(
        model="gpt-4o-mini",
        messages=messages,
        tools=TOOL_DEFINITIONS,
        tool_choice="auto",
    )
    assistant_message = response.choices[0].message
    messages.append(assistant_message.model_dump(exclude_none=True))
    if not assistant_message.tool_calls:
        print(assistant_message.content or "")
        break
    for call in assistant_message.tool_calls:
        result = execute_tool(call.function.name, json.loads(call.function.arguments))
        messages.append(
            {
                "role": "tool",
                "tool_call_id": call.id,
                "content": result if isinstance(result, str) else json.dumps(result),
            }
        )
```

---

## Features

- **Keyword engine** — 51 topics, whole-word match, longest key first
- **Short-term memory** — last 8 user lines; “what did I say earlier?” recalls them
- **Saved chat** — restored on relaunch (file / SharedPreferences / localStorage)
- **Status indicator** — ONLINE / THINKING... / OFFLINE
- **Typewriter animation** — all three surfaces
- **Exit** — type `exit` or `quit`

---

## Documentation

| Doc | What it covers |
|-----|----------------|
| [docs/HOW_TO_RUN.md](docs/HOW_TO_RUN.md) | Run Web, Desktop, and Android |
| [docs/STATUS.md](docs/STATUS.md) | What is ready vs blocked right now |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | Build details, version bump, layout |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Engine and per-platform wiring |
| [docs/KNOWLEDGE.md](docs/KNOWLEDGE.md) | How to edit the shared topic map |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Rules for changing the brain and UI |
| [ROADMAP.md](ROADMAP.md) | Planned work from v1.0 onward |
| [CHANGELOG.md](CHANGELOG.md) | Shipped versions |
| [android/HOW_TO_BUILD_APK.md](android/HOW_TO_BUILD_APK.md) | Debug and signed APK |

---

## How the engine works

1. Input is lowercased
2. Scan `shared/knowledge.json` for a **whole-word** key, **longest first**
3. If none match, `remember` / `earlier` / `before` echoes the previous user line
4. Otherwise a random fallback line

No LLM, no weights, no network. Details: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Project structure

```
aether/
├── shared/knowledge.json       # Source of truth for every platform
├── AetherMindApp.java          # Desktop UI
├── AetherBrain.java            # Desktop brain
├── run-web.sh / run-desktop.sh / build.sh
├── test_match.py
├── android/
└── web/
```

---

## Tech stack

| Layer | Desktop | Android | Web |
|-------|---------|---------|-----|
| Language | Java 11+ | Kotlin 2.0.21 | Vanilla JS (ES6) |
| UI | Swing | RecyclerView + ViewBinding | HTML5 + CSS3 |
| Build | `./build.sh` | Gradle / AGP 8.5.2 | None |
| Min runtime | JDK 11+ to compile | Android 8.0 (API 26) | Any modern browser |
| Dependencies | None | AndroidX, Coroutines, Material | None |

---

## License

No `LICENSE` file is in the repository yet. Contact the author for terms before redistributing.

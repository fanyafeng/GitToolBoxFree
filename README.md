# GitToolBoxFree

<p align="center">
  <a href="README.md"><b>English</b></a> | <a href="README_CN.md"><b>中文文档</b></a>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License"></a>
  <a href="https://www.jetbrains.com/"><img src="https://img.shields.io/badge/Platform-IntelliJ%20%7C%20Android%20Studio-orange.svg" alt="Platform"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Language-Java%2017%2B-green.svg" alt="Language"></a>
  <a href="https://github.com/fanyafeng/GitToolBoxFree/releases"><img src="https://img.shields.io/github/v/release/fanyafeng/GitToolBoxFree?color=brightgreen&label=Release" alt="Release"></a>
</p>

---

**GitToolBoxFree** is a **completely free, ultra-lightweight, high-performance, and bilingual (English / 简体中文)** Git productivity enhancement plugin for Android Studio and IntelliJ IDEA.

Designed to eliminate bloating, paywalls, and editing lag, GitToolBoxFree delivers a buttery-smooth 120fps native coding experience with zero keystroke delay.

---

## ✨ Features

### 1. ⚡ Inline Blame (Zero-Lag Annotation)
- **Instant Line-End Attribution**: Displays the author, relative time (e.g. *3 days ago*, *yesterday 14:20*, *just now* / *3天前*, *刚刚*), and commit message directly at the end of the current cursor line.
- **Typing Suppression**: Listens to active document edits and automatically hides inline annotations during typing, ensuring 100% native IDE typing responsiveness without distracting text jumps.
- **Same-Line Zero-Overhead Bypass**: Horizontal cursor movements or typing on the same line skip all allocations and queries in 0ms.
- **Non-Blocking Background Debounce**: Debounce scheduling runs entirely on background thread pools (`POOLED_THREAD`), never blocking the Swing Event Dispatch Thread (EDT).

### 2. 🌿 Status Bar Repository & Ahead/Behind Indicator
- **Live Branch & Sync Metrics**: Displays the current branch and ahead/behind commit counts in real-time on the IDE status bar (e.g. `🌿 main [↑2 ahead | ↓1 behind]`).
- **Multi-Module & Multi-Repo Intelligent Tracking**: Automatically senses the active editor tab and seamlessly tracks the corresponding sub-repository.
- **In-Memory Upstream Detection**: Instant 0ms memory check for local branches without upstream remotes—no unnecessary external `git rev-list` processes.
- **Quick Action Menu**: Left-click the status bar indicator to immediately trigger **Pull**, **Push**, or **Fetch & Refresh**.

### 3. 🔄 Silent Background Auto-Fetch
- Periodically executes background `git fetch --prune` to keep ahead/behind counts accurate.
- **Safe Rebase/Merge Evasion**: Automatically pauses during ongoing rebase or merge operations to protect your workspace.
- **Lifecycle Bound**: Tied to project disposal tree to guarantee zero memory leaks or zombie background tasks when closing projects.

### 4. 🌐 Bilingual Settings (English & 简体中文)
- Built-in one-click language toggle between **English** and **简体中文**.
- Configurable under **Settings / Preferences -> Version Control -> GitToolBoxFree**:
  - Customizable Blame Template (supports `{author}`, `{time_ago}`, `{date}`, `{message}`, `{hash}`)
  - Adjustable Debounce Delay (50ms – 2000ms)
  - Toggle Avatar Icon (`👤`), Status Bar Widget, and Auto-Fetch intervals

---

## 🚀 Performance Highlights

| Aspect | Traditional Approaches | GitToolBoxFree Solution |
| :--- | :--- | :--- |
| **Typing Responsiveness** | Frequent micro-stutters & Inlay jumps | Document-listener typing suppression + instant same-line bypass (0ms) |
| **Porcelain Output Parsing** | `String.matches()` regex on each line | Zero-regex linear character scanning (1000x faster, zero GC) |
| **Untracked / Clean Files** | Repeated external process invocations | Negative caching stores empty entries; 0 extra process spawns |
| **Editor Repainting** | Allocates `Graphics` & `Color` every frame | Pre-computed fonts and colors; zero allocation during paint |
| **Status Bar Updates** | Spams `git rev-list` on every file save | 1.2s debounce + diff-guard prevents status bar layout churn |

---

## 📦 Installation

### Method 1: Install from Release Zip (Recommended)
1. Download the latest `GitToolBoxFree-1.0.0.zip` from [Releases](https://github.com/fanyafeng/GitToolBoxFree/releases).
2. Open Android Studio or IntelliJ IDEA.
3. Open Settings / Preferences:
   - macOS: `Android Studio` -> `Settings...` -> `Plugins`
   - Windows/Linux: `File` -> `Settings` -> `Plugins`
4. Click the **⚙️ (gear icon)** at the top right of the Plugins tab and select **Install Plugin from Disk...**.
5. Choose the downloaded `GitToolBoxFree-1.0.0.zip` file and confirm.
6. Restart your IDE.

---

## 🛠️ Building from Source

This project uses standard Gradle:

```bash
# Clone the repository
git clone git@github.com:fanyafeng/GitToolBoxFree.git
cd GitToolBoxFree

# Build the plugin distribution package
./gradlew buildPlugin
```

The compiled plugin zip will be located at:
```text
build/distributions/GitToolBoxFree-1.0.0.zip
```

---

## 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).
Contributions, bug reports, and pull requests are welcome!

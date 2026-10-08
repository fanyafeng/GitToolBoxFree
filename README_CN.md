# GitToolBoxFree (中文说明)

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

**GitToolBoxFree** 是一款专为 Android Studio 及 IntelliJ IDEA 打造的**完全免费、轻量级、极致流畅、中英双语** Git 效率增强插件。

彻底解决同类插件臃肿、收费、卡顿以及中文本地化支持不足等痛点，提供丝滑的原生编码体验。

---

## ✨ 核心特性

### 1. ⚡ 行内代码责任人 (Inline Blame)
- **实时显示**：光标停留在代码行末，即时以浅灰半透明字体展示该行的最后提交人、自然中文相对时间（如“刚刚”、“昨天 14:20”、“3天前”）及提交信息。
- **打字输入 0 阻塞**：接入文档变动监听，在敲击键盘输入与删除代码期间自动隐藏 Inlay，保持 IDE 原生 120fps 打字响应。
- **同行移动 0 开销拦截**：同一行内左右移动光标或打字时不触发任何重复销毁与重建。
- **后台防抖与合流**：光标移动防抖完全在后台线程池（`POOLED_THREAD`）执行，绝不占用 Swing 事件分发主线程 (EDT)。

### 2. 🌿 状态栏仓库指示器 (Status Bar)
- **分支与超前/落后指示**：底栏实时显示当前分支名称及超前/落后远程的提交计数（如 `🌿 main [↑2 超前 | ↓1 落后]`）。
- **多模块/多仓库智能跟随**：自动识别当前激活的编辑文件，随编辑文件切换自适应展示对应子仓库的 Git 状态。
- **0 进程纯内存判定**：无远程追踪分支时自动在内存中判定，0 外部进程开销。
- **便捷快捷菜单**：点击状态栏指示器即可快速弹出菜单：立即拉取 (Pull)、推送 (Push) 或刷新状态 (Fetch)。

### 3. 🔄 静默后台自动拉取 (Auto Fetch)
- 后台周期性静默执行 `git fetch --prune`，实时保持远程落后/超前计数的准确。
- **智能避让机制**：当检测到正在进行 Rebase 或 Merge 时自动避让；任务绑定项目生命周期，工程关闭时自动释放，杜绝内存泄漏。

### 4. 🌐 界面中英双语自由切换 (Bilingual Support)
- 原生支持 **简体中文** 与 **English** 双语界面。
- 可在 **Settings (设置) -> Version Control (版本控制) -> GitToolBoxFree** 中自由配置：
  - 显示模板（支持 `{author}`、`{time_ago}`、`{date}`、`{message}`、`{hash}` 等变量）
  - 光标防抖延迟（50ms ~ 2000ms 可调）
  - 头像图标开关、状态栏组件开关及自动拉取间隔

---

## 🚀 性能设计亮点

| 优化维度 | 传统方案 | GitToolBoxFree 优化方案 |
| :--- | :--- | :--- |
| **打字输入** | 易产生微卡顿、行末提示跳动 | 监听文档编辑事件，打字期间 0 干扰，同行移动 0 CPU 开销 |
| **瓷器格式解析** | 使用正则表达式按行匹配 | 纯字符扫描与指针定位（Zero-Regex Parsing），吞吐量提升数百倍 |
| **未追踪文件处理** | 每次光标移动都调用外部 git blame 报错 | 引入负缓存（Negative Caching），失败与未追踪文件 0 额外进程开销 |
| **编辑器重绘** | 每次 paint 分配 Graphics 与 Color 堆对象 | 预加载字体与色值，绘制期 0 对象分配，高刷满帧运行 |
| **状态栏更新** | 仓库变动时频繁轰炸 git 进程 | 1200ms 防抖合并 + 差分检测，内容无变化时不触发底栏 Re-layout |

---

## 📦 安装使用

### 本地磁盘离线安装（推荐）
1. 前往本仓库 [Releases](https://github.com/fanyafeng/GitToolBoxFree/releases) 页面下载最新的 `GitToolBoxFree-1.0.0.zip`。
2. 打开 Android Studio 或 IntelliJ IDEA。
3. 进入设置：
   - macOS: `Android Studio` -> `Settings...` -> `Plugins`
   - Windows/Linux: `File` -> `Settings` -> `Plugins`
4. 点击插件页面右上角的 **⚙️ (齿轮图标)**，选择 **Install Plugin from Disk...**。
5. 选中下载的 `GitToolBoxFree-1.0.0.zip` 并确认。
6. 重启 IDE 即可生效！

---

## 🛠️ 源码构建

本项目采用标准 Gradle 构建系统：

```bash
# 克隆仓库
git clone git@github.com:fanyafeng/GitToolBoxFree.git
cd GitToolBoxFree

# 构建插件安装包
./gradlew buildPlugin
```

构建生成的插件 Zip 包位于：
```text
build/distributions/GitToolBoxFree-1.0.0.zip
```

---

## 📄 开源协议

本项目采用 [Apache License 2.0](LICENSE) 协议开源。
欢迎任何形式的 Issue、PR 和 Star ⭐️ 支持！

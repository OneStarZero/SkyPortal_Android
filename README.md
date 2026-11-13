<div align="center">
  <!-- 注意：如果你根目录的图标不叫 icon.png，请把下面这行里的 icon.png 改成你的文件名 -->
  <img src="./ic_launcher.png" alt="SkyPortal Icon" width="120" height="120" style="border-radius: 20%;">
  
  <h1>SkyPortal - 光遇传送点解析器</h1>
  
  <p>一款基于 Android 原生与 WebView 混合架构的《光·遇》高级数据解析工具。</p>

  <!-- 徽章 -->
  <p>
    <img src="https://img.shields.io/badge/Platform-Android%207.0%2B-green.svg" alt="Android Version">
    <img src="https://img.shields.io/badge/Version-0.1.0-blue.svg" alt="Version">
    <img src="https://img.shields.io/badge/License-MIT-purple.svg" alt="License">
    <img src="https://img.shields.io/badge/Language-Java%20%7C%20JavaScript-orange.svg" alt="Language">
  </p>
</div>

---

## 📖 目录
- [✨ 核心特性](#-核心特性)
- [📸 界面预览](#-界面预览)
- [🚀 使用方法](#-使用方法)
- [🛠️ 技术架构](#️-技术架构)
- [💻 开发者指南](#-开发者指南)
- [❓ 常见问题 (FAQ)](#-常见问题-faq)
- [⚠️ 免责声明](#️-免责声明)

---

## ✨ 核心特性

- 📱 **零门槛操作**：无需繁琐地手动提取 APK，应用底层直连 Android 系统 `PackageManager`，**一键读取手机内已安装的应用**。
- ⚡ **毫秒级解包**：原生层（Java）直接通过 ZIP 引擎提取目标文件 `Objects.level.bin`，无需全量解压，杜绝内存溢出。
- 🌍 **智能双语词典**：内置海量本地中英对照词库，并支持从云端（GitHub Raw）实时拉取最新翻译，废弃关卡与季节活动地图也能精准识别。
- 🎨 **沉浸式 UI 体验**：基于极光背景的毛玻璃设计，配备丝滑的弹性动画、Web Audio 动态音效与物理水波纹点击反馈。
- 📋 **一键提取坐标**：自动过滤并分类展示“可用/禁用”传送点，**长按卡片即可自动复制坐标**，并伴有手机震动反馈。
- 💾 **数据深度导出**：解析完成后支持将二进制 `.bin` 文件一键转换为完整的 JSON 格式，并保存至手机本地。
- 📦 **极致轻量**：开启 R8 代码混淆与 Shrink Resources 资源压缩，APK 体积极小，启动瞬间完成。

---

---

## 🚀 使用方法

1. 前往本仓库的 **Releases** 页面，或前往 **Actions** 下载最新构建的 `app-release.apk`。
2. 安装并打开 **SkyPortal**。
3. 点击顶部绿色的 **「📱 读取已安装应用」** 按钮。
4. 在弹出的系统应用列表中，找到并点击《光·遇》（国服或国际服均可）。
5. 稍等片刻，网页会自动列出该版本 APK 内包含的所有地图关卡（如：`Prairie_ButterflyFields` 蝴蝶平原）。
6. 点击你想查看的关卡，解析成功后，系统会询问你是否导出 JSON。
7. 随后即可在主界面查看可用与禁用的传送点坐标，**长按卡片 1 秒即可复制**！

---

## 🛠️ 技术架构

本项目采用**高度解耦**的前后端分离架构，极大提升了性能与安全性：

- **原生层 (Native)**：Android (Java) - 负责应用权限管理、`PackageManager` 应用扫描、APK Zip 流定点读取、JS Bridge 桥接。
- **视图层 (View)**：WebView (HTML/CSS/JS) - 负责极光 UI 渲染、CSS 动画、Web Audio API 音效合成。
- **解析层 (Parser)**：纯前端 JavaScript (`DataView`) - 完美复刻 Python 的 `TGCL` 二进制结构解析（包含 Float、Int、矩阵向量、字符串池偏移计算等）。
- **云端构建 (CI/CD)**：GitHub Actions - 配置了极速 Gradle 缓存，自动化编译、混淆并打包 APK。

---

## 💻 开发者指南

如果你想自己修改 UI 或添加功能，可以 Fork 本项目自行编译：

1. 将项目克隆到本地。
2. **前端修改**：在 `app/src/main/assets/index.html` 中尽情修改界面与 JavaScript 逻辑。
3. **原生修改**：在 `app/src/main/java/com/sky/portal/MainActivity.java` 中修改 Java 桥接代码。
4. **提交推送**：将代码 Push 到 GitHub 的 `main` 分支。
5. **自动打包**：GitHub Actions 会自动运行（或手动在 Actions 页面点击 `Run workflow`），几分钟后即可在 Actions 产物中下载最新的 APK。

---

## ❓ 常见问题 (FAQ)

**Q: 为什么点击“读取应用”后，列表里没有《光·遇》？**
A: 请确保你的手机已经安装了《光·遇》（国服包名为 `com.netease.sky`，国际服为 `com.tgc.sky.android`）。如果已安装，请检查系统设置中是否限制了 SkyPortal 的“获取应用列表”权限（Android 11+ 需注意）。

**Q: 为什么部分关卡的传送点解析不出来？**
A: 游戏版本更新可能导致二进制文件结构微调，或者该关卡本身没有配置 `LevelLink` 传送点数据。你可以尝试点击主界面的“导入 JSON”手动载入已知的配置文件。

**Q: 导出的 JSON 文件在哪里？**
A: 默认保存在你手机的文件管理器中，通常是 `Download` 文件夹。

---

## ⚠️ 免责声明

本项目仅供学习、逆向工程交流与个人测试使用。**请勿将本工具用于任何商业用途或违反《光·遇》游戏用户协议的行为。** 因使用本工具产生的一切账号风险或数据丢失等后果，由使用者自行承担。

---
<div align="center">
  <i>Powered by <b>OneStarZero</b> & Sky-Portal Community</i><br>
  <i>Made with ❤️ for Sky Players</i>
</div>

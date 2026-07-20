<div align="center">

# 拾光课程表（Yngu196 维护分支）

![GitHub License](https://img.shields.io/github/license/Yngu196/shiguangschedule)

本项目fork自[拾光课程表](https://github.com/XingHeYuZhuan/shiguangschedule)，在保留原项目全部能力的基础上，针对个人使用习惯做了若干交互和功能改造。

</div>

## 预览图

|       周课表页面(支持长按调整课程块位置或者高度)      |         课表页面个性化配置(深色模式演示)         |               小组件选择               |
| :-------------------------------: | :-------------------------------: | :-------------------------------: |
| ![图片1](/picture/Screenshot_1.png) | ![图片2](/picture/Screenshot_2.png) | ![图片3](/picture/Screenshot_3.png) |

## 本分支新增与改造

### 界面调整

- **移除底部多 Tab 导航栏**：原「今日课表 / 课表 / 我的」三 Tab 结构，改为「主页 + 悬浮按钮 + 顶栏齿轮入口」。
- **悬浮按钮切换「本周 / 今日」**：在主页右下角悬浮按钮点击，即可在周课表视图与今日课表视图之间无缝切换。
- **设置入口改至顶栏齿轮图标**：右上角 `⚙` 图标进入 `SettingsScreen`，并补齐返回箭头，避免出现栈错乱导致的崩溃（修复了上游 `Navigation.kt` 中 `isMainScreen` 误含 `Destination.Settings` 的问题）。

### 新增

- **新增「下课倒计时」4x1 小组件**：
  - 课内模式：「「课程名」」+ 大字「还有 N 分钟」+ 小字「下课 HH:MM」
  - 课间模式：「当前未上课」+ 下一节「「课程名」HH:MM 上课」
  - 无课/假期模式：居中提示「当前无课 / 今日课程已结束 / 假期中」
  - **整分钟对齐刷新**：组件内部通过 `AlarmManager.setExact` 在每个整分钟边界触发 UI 刷新，无需依赖 WorkManager 周期任务。
  - 风格沿用现有 4 种原生小组件的圆角卡片背景，融入桌面。
- 新增从.xls导入（目前只适用于一所或几所学校，如果你希望兼容你所在的学校，请提供.xls/.xlsx格式的课程信息）

### 课程提醒（已修复）

- 修复 `CourseNotificationWorker.setAlarmInternal()` 在缺少精确闹钟权限时静默 return 导致所有闹钟丢失的问题：现改为降级策略，权限不足时仍使用非精确闹钟兜底，避免用户感知不到提醒。
- 修复桌面小组件中 `ScheduleWidgetProvider` 三 PendingIntent 标志互斥的潜在崩溃，保留 `FLAG_IMMUTABLE` 兼容性。

## 沿用上游的功能

- 今日课表 / 周课表切换、长按调高度、个性化配置（深色、背景图、课程块颜色/圆角/间距/透明度）。
- 多种规格桌面小组件（超小 2x1 / 紧凑 2x2 / 近日 4x2 / 垂直列表 4xN）。
- 全局课程管理、JSON/ICS 导入导出、WebView 教务适配器导入。
- 课前提醒 + 整年节假日过滤、简体中文 / 繁体中文 / 英语三语。

## 编译与运行

- **JDK**：21（`jdk-21.0.10.7-hotspot`）
- **Gradle**：8.13（在 `.idea/gradle.xml` 中显式指定 `gradle.java.home`）
- **AGP**：8.7.3
- **Kotlin**：项目 `gradle/libs.versions.toml` 中定义
- **构建命令**：
  ```bash
  # Windows PowerShell
  .\gradlew assembleDevDebug --no-daemon
  ```

首次构建若遇到 `net.ltgt.gradle.incap:incap:0.2` 下载中断，重试即可。

## 相关链接

- 上游主仓库：<https://github.com/XingHeYuZhuan/shiguangschedule>
- 本分支：<https://github.com/Yngu196/shiguangschedule>
- 适配脚本仓库：<https://github.com/XingHeYuZhuan/shiguang_warehouse>
- 浏览器测试插件：<https://github.com/XingHeYuZhuan/shiguang_Tester>


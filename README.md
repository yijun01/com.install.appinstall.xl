# [更名] 安装防护模块（com.install.appinstall.xl）

基于 Android 底层 Hook 技术的安装防护模块[原:伪造安装模块]，实现应用虚假安装数据，拦截应用恶意的应用安装检测，绕过强制安装限制，保护应用列表隐私。

[![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![Xposed](https://img.shields.io/badge/Xposed-Module-34a853?logo=android&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![LSPosed](https://img.shields.io/badge/LSPosed-Supported-34a853?logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![LSPatch](https://img.shields.io/badge/LSPatch-Supported-34a853?logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![Root](https://img.shields.io/badge/Root-Supported-34a853?logo=lock&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![GitHub](https://img.shields.io/badge/GitHub-OpenSource-34a853?logo=github&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![](https://img.shields.io/github/v/release/yijun01/com.install.appinstall.xl?style=flat-square&logo=android&logoColor=white&color=3DDC84)](https://github.com/yijun01/com.install.appinstall.xl)

---

### 核心作用

拦截 PackageManager / 文件 / 命令行 / 网络等多维度安装检测，返回自定义假装结果（已安装/未安装），防止应用因检测特定包而限制功能、精确控制启动第三方应用/网页、杜绝泛滥频繁的系统权限申请等。

### 举个栗子

应用 A 强制要求安装 应用B 才能使用核心功能。本模块可假装“应用B 已安装”，无需实际安装即可正常使用 应用A。

---

## ✨ 适用场景

- 保护隐私：拒绝应用恶意查询已安装应用列表
- 绕过限制：突破“必须安装指定 APP”的强制要求
- 环境检测：规避应用的环境/网络状态检测
- 便捷使用：支持 LSPatch 非 ROOT 方案
- 整治跳转：拦截恶意间唤起应用和网页跳转
- 限制破除：解除截屏/录屏限制和界面锁定
<img alt="安装防护模块7" src="https://github.com/user-attachments/assets/113f412f-65c8-4c33-8a49-40b1e92e519b" />
<img alt="安装防护模块8" src="https://github.com/user-attachments/assets/78fbf8c9-b75d-4276-882d-d35a6c88d86f" />
<img alt="安装防护模块9" src="https://github.com/user-attachments/assets/9373d1f3-fc50-4543-976f-4df6079964e9" />
<img alt="安装防护模块10" src="https://github.com/user-attachments/assets/d9631f3b-40b8-4577-b999-bcab4844a7ee" />
<img alt="安装防护模块11" src="https://github.com/user-attachments/assets/02f89c9d-1e6c-46e4-9382-09c7e2941223" />

---

## 🎨 便捷入口操作

| 操作方式 | 功能效果 |
| :--- | :--- |
| **点击悬浮窗** | 弹出配置面板：切换全局模式、配置更多设置 |
| **长按悬浮窗** | 隐藏悬浮窗(音量键：双击/三击)恢复、清理包缓存 |
| **双击悬浮窗** | 添加自定义包名（支持添加/排除/）、独立设置 |
| **拖拽悬浮窗** | 自由调整位置，自动保存位置 |
| **进阶版操作** | 配置导入导出、实时日志、调试模式(音量键：+-+-)调出 |
<img alt="安装防护模块12" src="https://github.com/user-attachments/assets/0df9394d-2b1f-49e0-bba9-7870b57defa5" />

---

## 🚀 核心功能介绍：

### 安装防护

| 功能类别 | 描述说明 |
| :--- | :--- |
| **模式设置** | 全局设置或独立设置“已安装/未安装/跟随全局”模式。 |
| **捕获包名** | 目标应用查询包时同步捕获并列出相应包名，便于预配置规则。 |
| **配置文件** | 悬浮窗位置、拦截状态、设置模式等自动持久化，重启不丢失。 |
| **配置导出** | 路径：`/storage/emulated/0/Android/data/目标包名/files/installcf_包名_时间戳.json` |
| **配置导入** | 新文件默认在`/storage/emulated/0/Android/data/目标包名/files/`路径下才能识别。 |
>配置导入导出支持`个性化选择配置`以及支持使用`文件选择器`（注：不支持导出含自动捕获的包名）
<img alt="安装防护模块5" src="https://github.com/user-attachments/assets/e4143932-0b70-4f18-9eb8-5c486a86f48e" />

### 权限防护

| 功能类别 | 描述说明 |
| :--- | :--- |
| **权限虚假授权** | 授权应用列表权限，支持自定义授权其余权限。 |
| **虚假分享回调** | 虚假分享 官方SDK 回调，使应用认为分享成功。 |
<img alt="安装防护模块4" src="https://github.com/user-attachments/assets/6c1b9141-dbee-470c-9a02-522e94c8ee22" />

### 第三方启动跳转/退出拦截

| 功能类别 | 描述说明 |
| :--- | :--- |
| **启动拦截** | 提供精细三种控制：真实启动 / 虚假启动/ 取消启动。 |
| **退出拦截** | 拦截因查询的包触发的退出，(普通/超强)两种模式适配不同场景。 |
| **黑/白名单** | 同步记录操作，启动拦截支持 `*` 模糊匹配符、`!` 反向排除符。 |
<img alt="安装防护模块3" src="https://github.com/user-attachments/assets/4edbb368-fa62-4431-9954-8320f3fd4481" />

### 基础环境隐藏

| 功能类别 | 描述说明 |
| :--- | :--- |
| **痕迹隐藏** | 隐藏 Root、ADB、Xposed框架等。 |
| **网络隐藏** | 简单隐藏 VPN 连接及SSL代理配置。 |
<img alt="安装防护模块2" src="https://github.com/user-attachments/assets/559cb68b-9ffc-4146-9491-16a03a18de89" />

### 辅助功能

| 功能类别 | 描述说明 |
| :--- | :--- |
| **SELinux 状态伪装** | 伪装 SELinux 模式，绕过系统环境完整性检测。 |
| **禁止截屏/录屏阻断** | 强行阻断 `FLAG_SECURE`，允许截屏/录屏。 |
| **应用返回键逻辑替换** | 应对强制锁定界面无法退出(仅适配多Activity架构)。 |
<img alt="安装防护模块1" src="https://github.com/user-attachments/assets/9ab7cd7b-2125-4f83-a1ed-b7f2120d883d" />

---


## 📋 前置条件

| 设备条件 | 操作要求 |
| :--- | :--- |
| **ROOT 设备** | 安装 LSPosed / EdXposed 框架 |
| **非 ROOT 设备** | 安装 LSPatch 框架（无需解锁 BL） |
| **系统版本** | Android 8.0（API 26）~ 16（API 36） |
| **目标应用** | 未加固（加固应用屏蔽 XP 模块） |

---

## 🛠 安装教程

### ROOT 方案（LSPosed）

1. 下载 [Releases](https://github.com/yijun01/com.install.appinstall.xl/releases) 最新 APK
2. 安装后打开 LSPosed → 模块 → 开启本模块
3. 勾选目标应用（⚠️ 禁止勾选系统应用/分身应用）
4. 重启目标应用或设备

### 非 ROOT 方案（LSPatch）

1. 打开 LSPatch → 添加应用 → 选择「内嵌模式/本地模式」
2. 勾选本模块，制作新 APK
3. 安装制作后的 APK 即可生效
> ⚠️ 目标应用须无加固、无签名校验，否则制作失败。

---

## ⚠️ 注意事项

- ❌ 严禁对**系统应用**启用，否则系统崩溃
- ❌ 未适配**分身应用**，可能导致崩溃
- 🚫 加固应用**可能无效**，请取消作用域
- 📱 无响应/失效：清理包缓存 → 重启应用/设备
- 📁 配置文件路径：默认创建在`/data/data/目标包名/files/installcf_包名.json`
- 📁 导出配置文件：默认在`/storage/emulated/0/Android/data/目标包名/files/installcf_包名_时间戳.json`
> 📁 导入导出亦可使用`系统文件选择器`
---

## ❓ 常见问题

### Q：模块启用后无悬浮窗？
- A：重启目标应用（冷启动）
- A：检查是否误隐藏悬浮窗（双击/三击音量键恢复）
- A：确认 LSPosed 已勾选目标应用或确认已嵌入本模块

### Q应用仍提示「未安装指定应用」？
- A：切换「已安装」模式并重启
- A：清理包列表后重启应用
- A：检查是否为加固应用或自研检测
- A：无法捕获包名可双击悬浮窗手动添加

### Q：拦截退出无效？
- A：确认「拦截退出」已开启
- A：部分应用自研退出方式，可反馈包名适配

### Q：分身应用崩溃？
- A：本模块未适配分身应用，不建议使用

### Q：目标应用崩溃/闪退？
- A：确认是否为加固应用
- A：可能存在自身网络检测/环境检测或 XP 模块检测

---

## 📜 免责声明

1. 本工具**仅限个人学习、技术研究与非商业测试使用**，严禁商业运营、非法用途及任何侵害他人权益的行为。使用者需自行承担因使用/操作不当等所有风险(如设备异常、数据丢失、账户封禁、法律纠纷等)与法律法规责任，本项目作者不承担任何法律民事刑事的法律责任(包括连带连坐等)以及社会道德与法治。

2. 互联网上出现与本项目名称、功能、内容相似或雷同作品均与本项目无关，请仔细甄别。由此引发的财产损失、权益纠纷等的所有损失，与本项目作者无关且不承担责任(*1)。

3. 本项目永久**公益免费**且基础版开源，未有任何收费项目、未有捐赠打赏、未有线下社会合作、未有商业运营等交易入口。任何以本项目名义收费/捐赠均为假冒。

4. 本项目**未有任何官方交流群/频道/私人联系方式**等直接沟通渠道，唯一官方渠道为本仓库 [Issues](https://github.com/yijun01/com.install.appinstall.xl/issues)板块，本项目作者不会采用其他沟通渠道私自或间接联系任何人，任何自称作者、官方的联系方式均为假冒。。

5. 项目作者保留对本项目进行更新、修改、删除、终止、归档、下架等全部操作权利，基于本项目衍生的复刻版、修改版、分支版本等，均与原项目无任何关联，不属于官方范畴，作者不对其负责。

6. 请遵守本项目内的许可证：**GNU General Public License v3.0 (GPL-3.0) **开源协议。

 **协议总纲：**
- 必须保留原版权声明与许可声明。
- 修改后的项目必须完整开源。
- 必须使用相同的许可证（GPL-3.0）分发。
- 禁止商用、禁止用于任何盈利场景。

---

## 🔗 项目链接

- **作者主页**：[https://github.com/yijun01/com.install.appinstall.xl](https://github.com/yijun01/com.install.appinstall.xl)
- **LSPosed 仓库**：[https://modules.lsposed.org/module/com.install.appinstall.xl](https://modules.lsposed.org/module/com.install.appinstall.xl)

---

## 📊 数据统计

[![](https://img.shields.io/github/downloads/yijun01/com.install.appinstall.xl/total?logo=github&label=Total%20Downloads&labelColor=2dba4e&color=2dba4e)](https://github.com/yijun01/com.install.appinstall.xl)
⭐ [![](https://img.shields.io/github/stars/yijun01/com.install.appinstall.xl?style=social&theme=dark)](https://github.com/yijun01/com.install.appinstall.xl) ⭐

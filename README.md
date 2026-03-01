# 伪造安装模块（com.install.appinstall.xl）
基于Android底层Hook技术的应用防护XP模块，核心实现应用安装状态伪造，拦截恶意安装检测，绕过应用强制安装限制，保护设备应用列表隐私。


[![Android](https://img.shields.io/badge/Android-3DDC84?logo=android&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl) [![Xposed](https://img.shields.io/badge/Xposed-Module-34a853?logo=android&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl) [![LSPosed](https://img.shields.io/badge/LSPosed-Supported-34a853?logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl) [![LSPatch](https://img.shields.io/badge/LSPatch-Supported-34a853?logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl) [![Root](https://img.shields.io/badge/Root-Supported-34a853?logo=lock&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl) [![GitHub](https://img.shields.io/badge/GitHub-OpenSource-34a853?logo=github&logoColor=white)](https://github.com/yijun01/com.install.appinstall.xl)
[![](https://img.shields.io/github/v/release/yijun01/com.install.appinstall.xl?style=flat-square&logo=android&logoColor=white&color=3DDC84)](https://github.com/yijun01/com.install.appinstall.xl)


### 核心作用
拦截应用的PackageManager/文件/命令行/网络等多维度安装检测，返回自定义伪造结果（已安装/未安装），防止应用因检测特定包而限制功能、强制退出或强制推送下载。

### 举个栗子
打开软件A后，被要求强制下载安装软件B才能使用核心功能，使用本模块可伪造“软件B已安装”的状态，无需实际安装即可正常使用软件A。

---

## ✨ 适用场景
- 保护隐私：拒绝应用恶意查询设备已安装应用列表
- 绕过限制：突破应用“必须安装指定APP才能使用”的强制要求
- 反检测：规避应用商店/第三方应用的安装状态检测
- 便捷使用：无需ROOT也可通过LSPatch实现功能（非ROOT方案）

## 🚀 核心功能
### 核心能力
1. **双模式状态伪造**：自由切换「已安装/未安装」模式，自动捕获目标应用的包检测请求并返回伪造结果
2. **全场景检测拦截**：支持PackageManager查询、文件系统、命令行（pm/dumpsys）、网络请求等检测方式拦截，适配应用插件检测
3. **检测退出拦截**：拦截应用因检测到伪造包触发的退出行为，支持**静默拦截/手动确认**双模式
4. **自动权限伪造**：无需手动授权，自动伪造QUERY_ALL_PACKAGES等检测相关核心权限
5. **配置持久化**：悬浮窗位置、拦截状态、伪造模式等配置自动保存，重启应用/设备不丢失
6. **启动拦截**：拦截宿主启动第三方应用/链接，返回虚假启动、真实启动、取消启动，能精确控制启动状态
7. **自定义添加**：支持手动添加包名，防止应用绕过拦截实现查询状态
8. **导入导出**：支持将当前宿主的配置文件进行导入导出，可导出安装状态、自定义包名、功能启用相关状态
注:**不支持导入导出自动捕获包名**

![伪造安装01](https://github.com/user-attachments/assets/fdde2c55-9321-4d12-9232-0672d5f69f03)

# 🎨 便捷操作
- **悬浮窗快捷配置**：点击切换安装状态，长按隐藏/清理包列表，双击添加包名
- **历史记录记忆**：重复检测场景自动静默拦截，无需重复确认
- **配置自动保存**：悬浮窗位置、拦截状态等配置自动持久化，重启不丢失
![伪造安装02](https://github.com/user-attachments/assets/25125782-7138-41e6-b099-fe128dedd434)
![伪造安装03](https://github.com/user-attachments/assets/94c96a89-7526-4274-bca5-6fc211a3be16)


## 📋 前置条件
1. 已ROOT设备：安装**LSPosed框架**（推荐）/EdXposed框架
2. 未ROOT设备：安装**LSPatch框架**（无需解锁BL/ROOT）
3. 系统版本：Android 8.0+（API 26）~ Android 16（API 36）
4. 目标应用：未加固（加固应用会屏蔽XP模块，导致功能失效）

## 🛠 安装教程
### 1. ROOT方案（LSPosed/EdXposed）
1. 下载最新版APK：[Releases 页面](https://github.com/yijun01/com.install.appinstall.xl/releases)
2. 安装APK后，打开LSPosed → 模块 → 找到「伪造安装模块」→ 开启模块
3. 勾选需要适配的**目标应用**（仅勾选第三方应用，禁止勾选系统应用/分身应用）
4. 重启目标应用（或重启设备），模块即可生效

### 2. 非ROOT方案（LSPatch）
1. 打开LSPatch → 添加应用 → 选择「内嵌模式/本地模式」
2. 找到「伪造安装模块」并勾选，制作新的应用安装包
3. 安装制作后的新APK，启动后模块自动生效（无ROOT/无框架也可使用）
> 注意：制作前请确认目标应用**无加固、无签名校验**，否则会制作失败

## 📌 功能使用指南
### 悬浮窗操作（核心操作入口）
| 操作方式       | 功能效果                                                         |
|----------------|------------------------------------------------------------------|
| 点击悬浮窗     | 弹出配置面板：切换伪造模式（已安装/未安装）、配置更多设置    |
| 长按悬浮窗     | 弹出菜单：隐藏悬浮窗（重启应用恢复）、清理包列表    |
| 双击悬浮窗     | 弹出配置面板：添加自定义包名，支持添加伪造/排除伪造的包名         |
| 拖拽悬浮窗     | 自由调整位置，调整后自动保存，重启后保持最新位置                 |

### 核心功能使用
1. **伪造安装状态**：点击悬浮窗→切换「已安装/未安装」→重启目标应用/刷新页面，立即生效
2. **拦截退出行为**：配置面板开启「拦截退出」，应用因检测伪造包触发退出时会自动拦截（默认静默拦截）
3. **清理检测记录**：长按悬浮窗→「清理包列表」，清空已捕获的检测包记录、伪造缓存，解决检测异常问题
4. ~~**重置配置**：长按悬浮窗→「重置配置」，恢复模块默认设置，捕获的记录回到初始状态~~
5. **添加包名**：双击悬浮窗→「输入包名」→选择「添加或排除」→「保存」，即可实时生效配置
6. **配置更多设置**：控制各种开关状态、配置相关深度功能

## ⚠️ 注意事项
1. ❌ 严禁对**系统应用**启用模块，会导致系统崩溃、功能异常
2. 📱 目标应用无响应/检测失效：清理包列表 → 刷新应用 → 重启设备/或取消对该应用作用本模块
3. 📁 模块配置文件路径：`/data/data/目标应用包名/files/install_fake_config.json`（卸载应用/清理数据会自动删除)
4. 🚫 加固应用会屏蔽XP模块，本模块对加固应用**可能无效**
5. 📌 部分应用采用自研检测技术，模块可能无法完全拦截（可反馈日志与应用优化适配）
5. ❌ 本模块未针对分身应用适配，可能会对分身应用无法使用**异常崩溃**

## ❓ 常见问题排查
### Q：模块启用后无悬浮窗？
A：1. 确认目标应用已重启(冷启动)；2. 检查是否误隐藏悬浮窗（重启应用即可恢复）；3. 确认LSPosed中已勾选目标应用

### Q：应用仍提示「未安装指定应用」？
A：1. 切换「已安装」模式并重启目标应用；2. 清理包列表后重新打开应用；3. 检查目标应用是否为加固应用；4. 目标应用可能采用自研检测技术；5. 无法捕获包名可双击悬浮窗输入指定包名(添加/排除)

### Q：拦截退出功能无效？
A：1. 确认配置面板中「拦截退出」已开启；2. 部分应用退出方式为自研，可反馈应用包名优化适配

### Q：悬浮窗无法点击/被其他弹窗覆盖？
A：1. 悬浮窗已尽努力最大化提高层级，若被应用原生对话框/弹窗覆盖，关闭对应弹窗即可正常操作

### Q：系统或第三方应用的分身应用崩溃/闪退？
A：1. 模块并未对分身应用进行适配，不建议作用于分身应用。


## 📜 免责声明
1.本项目相关内容与工具仅限**个人学习、技术研究与非商业测试使用**，严禁用于商业运营、非法用途及任何侵害他人权益的行为，使用者因不当使用导致设备异常、数据丢失、法律纠纷等一切后果，均由使用者自行承担，项目作者不承担任何法律与连带责任。

2.网络上若出现与**本项目**名称、功能、内容相似或雷同的作品，均与本项目无任何关联，请使用者仔细甄别真伪，由此引发的财产损失、权益纠纷等问题，本项目及作者不承担任何责任。

3.本项目在作者项目主页**全程免费**开源、无任何收费项目、无捐赠打赏、无线下合作、无商业变现途径，任何以本项目名义进行收费、售卖、诱导打赏等行为均为假冒，与本项目及作者无关。

4.本项目未创建任何交流群、社交频道、私人联系方式等沟通渠道，唯一官方反馈渠道仅为**本项目GitHub仓库的[Issues板块](https://github.com/yijun01/com.install.appinstall.xl/issues)**，其余任何自称作者、官方的联系方式均为假冒。

5.项目作者**保留**对本项目进行更新、修改、删除、终止、归档、下架等全部合法操作权利，基于本项目衍生的复刻版、修改版、分支版本等，均与原项目无任何关联，不属于官方范畴，作者不对其负责。


作者主页(项目)：
https://github.com/yijun01/com.install.appinstall.xl

---
# 数据统计

📦 **Download**  📦

[![](https://img.shields.io/github/downloads/yijun01/com.install.appinstall.xl/total?logo=github&label=Total%20Downloads&labelColor=2dba4e&color=2dba4e)](https://github.com/yijun01/com.install.appinstall.xl)

⭐ **Star** ⭐ 

[![](https://starchart.cc/yijun01/com.install.appinstall.xl.svg?theme=dark)](https://github.com/yijun01/com.install.appinstall.xl)

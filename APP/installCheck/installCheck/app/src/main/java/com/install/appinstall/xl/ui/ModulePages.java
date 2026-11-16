package com.install.appinstall.xl.ui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.install.appinstall.xl.MainActivity;
import com.install.appinstall.xl.util.ToastUtil;
import com.install.appinstall.xl.util.Update;

/** ModulePages —— 模块新主页push */
public class ModulePages {

    // ============================================================
    //  数据：功能板块
    // ============================================================
    private static final String[][] MODULE_SECTIONS = {
        {
            "核心拦截",
            "基础拦截 - 挡住约82%应用查询安装状态",
            "文件保护 - 不让应用读取系统安装列表",
            "反射防御 - 监控隐藏的应用检测方法",
            "网络拦截 - 拦截在线应用检测请求",
            "数据防护 - 生成虚假的应用信息"
        },
        {
            "应用包管理",
            "安装查询 - 假装应用已安装/未安装",
            "应用列表 - 拦截已安装应用列表",
            "权限列表 - 假装授权应用权限",
            "SELinux - 伪装系统强制模式",
            "假装分享 - 可假装已分享成功",
            "网络代理 - 隐藏网络代理模式",
            "屏幕防护 - 消除禁止留存界面",
            "系统包过滤 - 不干扰系统应用"
        },
        {
            "退出拦截",
            "普通拦截 - 阻止应用直接退出常用方式",
            "闪退拦截 - 全局捕获异常阻止崩溃闪退",
            "超强拦截 - 监控各种主流页面退出方式",
            "按钮拦截 - 挡住少部分点击退出结束按钮",
            "弹窗拦截 - 标准弹窗可以移除不可取消状态"
        },
        {
            "数据保护",
            "目录保护 - 假装存在应用目录",
            "文件检测 - 假装应用文件存在",
            "命令拦截 - 拦截目录检测命令",
            "痕迹隐藏 - 拦截应用注入检测",
            "明细数据 - 虚假生成安装时间/来源"
        },
        {
            "启动相关",
            "应用状态 - 假装应用已启用(捕获包)",
            "启动拦截 - 控制应用间跳转(捕获包)",
            "意图查询 - 假装应用启动能力(捕获包)",
            "组件查询 - 假装应用可被调用(捕获包)",
            "插件拦截 - 挡住安装检测插件(捕获包)",
            "返回功能 - 替换返回键,阻断禁止返回"
        },
        {
            "悬浮窗",
            "状态显示 - 悬浮窗显示当前模式",
            "实时切换 - 点击切换安装状态",
            "拦截开关 - 控制退出拦截功能",
            "位置记忆 - 记住悬浮窗拖动位置",
            "悬浮窗控制 - 长按隐藏本次显示",
            "自定义包名 - 双击悬浮窗可配置"
        },
        {
            "配置持久化",
            "智能学习 - 记住用户的选择",
            "独立配置 - 每个应用单独设置",
            "自动保存 - 配置自动存储持久化",
            "包名捕获 - 独立自动记忆应用包名"
        },
        {
            "关于模块",
            "开发思路 - 永恒之蓝(小淋)",
            "使用方法 - 支持LSPosed、LSPatch",
            "拦截效果 - 能挡住大部分安装检测(约82%)",
            "开发声明 - 仅限学习测试，禁止商用及付费！"
        }
    };

    /** 每个板块的一句话说明 */
    private static final String[] SECTION_NOTES = {
        "负责拦截各种安装检测手段，确保应用无法发现真实安装状态。",
        "管理应用包信息，假装安装状态和包列表，让检测工具看到你希望它们看到的内容。",
        "防止应用检测到虚假信息后直接退出，保持应用正常运行的同时提供用户选择。",
        "拦截文件系统、命令行输出等底层信息，应对更深入的检测手段。",
        "控制应用间的启动和跳转，假装应用的启动能力和组件状态。",
        "应用宿主内可视化控制和状态显示，方便用户实时查看和切换模块状态。",
        "自动保存用户设置，智能学习用户选择，提供个性化的拦截体验。",
        "模块基本信息和使用说明，请严格遵守使用规范。严禁引流盈利以及所有商业等行为。"
    };

    // ============================================================
    //  主页
    // ============================================================
    public static class ModuleHomePage extends PageStack.Page {
        private TextView mStatusSub;
        private TextView mIconSub;
        private Activity mA;

        @Override
        public String key() {
            return "module_home";
        }

        @Override
        public String title() {
            return "安装防护模块";
        }

        @Override
        public View build(final Activity a) {
            mA = a;
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "安装防护模块",
                                             false, null, null, 0), new LinearLayout.LayoutParams(
                             ViewGroup.LayoutParams.MATCH_PARENT,
                             ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiKit.dividerLine(a), new LinearLayout.LayoutParams(
                             ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dpI(a, 1))));
            LinearLayout content = (LinearLayout) UiComponents.paddedContent(a);

            // ---- 状态卡---
            UiComponents.CardGroup status = UiComponents.cardGroup(a, "模块状态", true);
            View statusRow = UiComponents.settingRow(a, "模块激活状态",
                isModuleActivated(a) ? "✅ 已激活" : "❌ 未激活",
                null, new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack s = PageStack.get();
                        if (s != null) s.push(new StatusSheetPage());
                    }
                });
            mStatusSub = UiComponents.subTitleOf(statusRow);
            if (mStatusSub != null) mStatusSub.setTextColor(UiKit.accent(a));
            View iconRow = UiComponents.settingRow(a, "桌面图标",
                isIconVisible(a) ? "✅ 显示中" : "❌ 已隐藏",
                null, new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack s = PageStack.get();
                        if (s != null) s.push(new IconSheetPage());
                    }
                });
            mIconSub = UiComponents.subTitleOf(iconRow);
            status.addRow(statusRow);
            status.addRow(iconRow);
            content.addView(status.root);

            // ---- 功能板块入口 ----
            UiComponents.CardGroup sections = UiComponents.cardGroup(a, "💠 功能", true);
            int secCount = Math.min(MODULE_SECTIONS.length, SECTION_NOTES.length);
            for (int i = 0; i < secCount; i++) {
                final int idx = i;
                // 副标题用板块说明
                sections.addRow(UiComponents.settingRow(a, "💠 " + MODULE_SECTIONS[i][0],
                                    SECTION_NOTES[i], null, new View.OnClickListener() {
                                        public void onClick(View v) {
                                            PageStack s = PageStack.get();
                                            if (s != null) s.push(new SectionSheetPage(idx));
                                        }
                                    }));
            }
            content.addView(sections.root);
            root.addView(UiComponents.scroll(a, content), new LinearLayout.LayoutParams(
                             ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        @Override
        public void onShow() {
            // 从图标半屏返回时刷新两行状态
            if (mA == null) return;
            if (mStatusSub != null) {
                mStatusSub.setText(isModuleActivated(mA) ? "✅ 已激活" : "❌ 未激活");
            }
            if (mIconSub != null) {
                mIconSub.setText(isIconVisible(mA) ? "✅ 显示中" : "❌ 已隐藏");
            }
        }
    }

    // ============================================================
    //  功能板块半屏
    // ============================================================
    public static class SectionSheetPage extends PageStack.Page {
        private final int mIndex;

        public SectionSheetPage(int index) {
            mIndex = index;
        }

        @Override
        public String key() {
            return "module_section_" + mIndex;
        }

        @Override
        public String title() {
            return "功能板块";
        }

        @Override
        public boolean halfScreen() {
            return true;
        }

        @Override
        public boolean sheetCancelable() {
            return true;
        }

        @Override
        public View build(Activity a) {
            String[] sec = MODULE_SECTIONS[mIndex];
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "💠 " + sec[0], null));
            LinearLayout content = (LinearLayout) UiComponents.paddedContent(a);
            UiComponents.CardGroup card = UiComponents.cardGroup(a, sec[0], true);
            View noteRow = UiComponents.settingRow(a, "功能说明", SECTION_NOTES[mIndex], null, null);
            TextView noteSub = UiComponents.subTitleOf(noteRow);
            if (noteSub != null) UiComponents.selectable(noteSub);
            card.addRow(noteRow);

            // 功能清单
            for (int j = 1; j < sec.length; j++) {
                String[] parts = sec[j].split(" - ", 2);
                String t = parts.length == 2 ? parts[0].trim() : sec[j];
                String s = parts.length == 2 ? parts[1].trim() : "";
                card.addRow(UiComponents.settingRow(a, t, s, null, null));
            }
            content.addView(card.root);

            // "关于模块"版本信息与声明（版本号真实获取）
            if (mIndex == MODULE_SECTIONS.length - 1) {
                TextView ver = UiKit.text(a, "安装防护模块 " + versionName(a)
                                          + " · 永恒之蓝(小淋)", 12, UiKit.textSub(a));
                ver.setGravity(Gravity.CENTER);
                UiComponents.selectable(ver);
                content.addView(ver);

                TextView declare = UiKit.text(a,
                                              "#移除强制安装第三方应用\n"
                                              + "#严禁任何商业活动仅个人免费使用！\n"
                                              + "#永恒之蓝(小淋)",
                                              11, UiKit.textSub(a));
                declare.setGravity(Gravity.CENTER);
                declare.setLineSpacing(UiKit.dpI(a, 2), 1.1f);
                UiComponents.selectable(declare);
                content.addView(declare);
            }

            root.addView(UiComponents.scrollNoFooter(a, content), new LinearLayout.LayoutParams(
                             ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    // ============================================================
    //  模块状态详情
    // ============================================================
    public static class StatusSheetPage extends PageStack.Page {
        @Override
        public String key() {
            return "module_status";
        }

        @Override
        public String title() {
            return "模块状态详情";
        }

        @Override
        public boolean halfScreen() {
            return true;
        }

        @Override
        public boolean sheetCancelable() {
            return true;
        }

        @Override
        public View build(Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "模块状态详情", null));

            boolean isActivated = isModuleActivated(a);
            StringBuilder sb = new StringBuilder();
            if (isActivated) {
                sb.append("模块当前运行状态：已激活\n");
                sb.append("✅ 所有Hook功能已生效\n");
                sb.append("✅ 可以在Xposed框架中查看\n");
                sb.append("✅ 应用启动时自动加载\n\n");
                sb.append("如果状态显示异常，请：\n");
                sb.append("1. 重启手机\n");
                sb.append("2. 检查Xposed/LSPosed激活状态\n");
                sb.append("(若使用LSPatch内嵌/本地模式无需激活)\nTips:宿主内进入本页面也展示未激活\n");
                sb.append("3. 重新启用模块");
            } else {
                sb.append("模块当前运行状态：未激活\n");
                sb.append("❌ Hook功能无法生效\n");
                sb.append("❌ 需要激活Xposed模块\n\n");
                sb.append("请按以下步骤操作：\n");
                sb.append("1. 打开Xposed/LSPosed应用\n");
                sb.append("2. 找到并启用本模块\n");
                sb.append("3. 重启目标应用或手机\n");
                sb.append("(若使用LSPatch内嵌/本地模式无需激活)\nTips:宿主内进入本页面也展示未激活\n");
                sb.append("4. 返回此处检查状态");
            }

            TextView msg = UiKit.text(a, sb.toString(), 14, UiKit.textMain(a));
            msg.setLineSpacing(UiKit.dpI(a, 2), 1.1f);
            int ph = UiKit.dpI(a, UiKit.PAGE_MARGIN);
            msg.setPadding(ph, UiKit.dpI(a, 10), ph, UiKit.dpI(a, 10));
            UiComponents.selectable(msg);
            UiComponents.CardGroup card = UiComponents.cardGroup(a, "模块状态详情", true);
            card.content.addView(msg);
            LinearLayout box = (LinearLayout) UiComponents.paddedContent(a);
            box.addView(card.root);

            TextView ok = UiComponents.primaryButton(a, "知道了", new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack s = PageStack.get();
                        if (s != null) s.pop();
                    }
                });
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.setMargins(ph, 0, ph, UiKit.dpI(a, 16));
            ok.setLayoutParams(blp);

            root.addView(UiComponents.scrollNoFooter(a, box), new LinearLayout.LayoutParams(
                             ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            root.addView(ok);
            return root;
        }
    }

    // ============================================================
    //  桌面图标控制
    // ============================================================
    public static class IconSheetPage extends PageStack.Page {
        @Override
        public String key() {
            return "module_icon";
        }

        @Override
        public String title() {
            return "桌面图标";
        }

        @Override
        public boolean halfScreen() {
            return true;
        }

        @Override
        public boolean sheetCancelable() {
            return true;
        }

        @Override
        public View build(Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "桌面图标", null));
            TextView msg = UiKit.text(a, "隐藏后桌面图标不会显示，重新进入模块需要：\n"
                                      + "在 LSPosed 模块列表里打开本模块，或重新安装 APK 覆盖打开。\n"
                                      + "切换后可能需要重启桌面（或等待几秒）生效。", 13, UiKit.textSub(a));
            int ph = UiKit.dpI(a, UiKit.PAGE_MARGIN);
            msg.setPadding(ph, UiKit.dpI(a, 12), ph, UiKit.dpI(a, 12));
            UiComponents.selectable(msg);
            boolean shown = isIconVisible(a);
            TextView showBtn = UiComponents.primaryButton(a, "显示桌面图标",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (!UiGuard.tap("icon_toggle", 600L)) return;
                        setLauncherIconVisibility(v.getContext(), true);
                        ToastUtil.show(v.getContext(), "已恢复桌面图标");
                        PageStack s = PageStack.get();
                        if (s != null) s.pop();
                    }
                });
            TextView hideBtn = UiComponents.ghostButton(a, "隐藏桌面图标",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (!UiGuard.tap("icon_toggle", 600L)) return;
                        setLauncherIconVisibility(v.getContext(), false);
                        ToastUtil.show(v.getContext(), "已隐藏桌面图标，重启桌面后生效");
                        PageStack s = PageStack.get();
                        if (s != null) s.pop();
                    }
                });
            TextView current = UiKit.text(a, shown ? "当前：显示中" : "当前：已隐藏",
                                          12, UiKit.accent(a));
            current.setPadding(ph, 0, ph, UiKit.dpI(a, 6));
            // 正文包卡片
            UiComponents.CardGroup card = UiComponents.cardGroup(a, "桌面图标", true);
            card.content.addView(msg);
            card.content.addView(current);
            LinearLayout box = (LinearLayout) UiComponents.paddedContent(a);
            box.addView(card.root);
            LinearLayout buttons = UiKit.hbox(a);
            buttons.setPadding(ph, 0, ph, UiKit.dpI(a, 16));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bp.rightMargin = UiKit.dpI(a, 8);
            showBtn.setLayoutParams(bp);
            buttons.addView(showBtn);
            LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            hideBtn.setLayoutParams(bp2);
            buttons.addView(hideBtn);
            root.addView(UiComponents.scrollNoFooter(a, box), new LinearLayout.LayoutParams(
                             ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            root.addView(buttons);
            return root;
        }
    }

    // ============================================================
    //  工具
    // ============================================================
    /** XP激活状态 */
    private static boolean isModuleActivated(Activity a) {
        if (a instanceof MainActivity) {
            return ((MainActivity) a).isModuleActivated();
        }
        return false;
    }

    /** 桌面图标显示状态 */
    private static boolean isIconVisible(Context c) {
        try {
            int state = c.getPackageManager().getComponentEnabledSetting(
                new ComponentName(c.getPackageName(), c.getPackageName() + ".MainHome"));
            return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 显示/隐藏桌面图标 */
    private static void setLauncherIconVisibility(Context c, boolean visible) {
        try {
            ComponentName alias = new ComponentName(c.getPackageName(), c.getPackageName() + ".MainHome");
            int newState = visible ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            c.getPackageManager().setComponentEnabledSetting(alias, newState,
                                                             PackageManager.DONT_KILL_APP);
        } catch (Throwable t) {
        }
    }

    /* 获取/拼接版本号*/
    private static String versionName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            if (pi != null && pi.versionName != null) return "v" + pi.versionName;
        } catch (Throwable t) {
        }
        return "";
    }

}

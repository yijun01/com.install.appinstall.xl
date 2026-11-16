package com.install.appinstall.xl.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.AntiDetection;
import com.install.appinstall.xl.util.DialogUi;
import com.install.appinstall.xl.util.ReaLog;
import com.install.appinstall.xl.util.Selinuxhook;
import com.install.appinstall.xl.util.ShareHook;
import com.install.appinstall.xl.util.Update;
import com.install.appinstall.xl.util.More;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UiTabs —— 首页各标签页的内容
 */
 
public final class UiTabs {

    private UiTabs() {}

    // ============================================================
    //  公共
    // ============================================================
    /** 当前应用是否已处于临时隐藏状态（false=显示中） */
    static boolean isTempHidden() {
        try {
            Boolean shown = HookInit.floatingShownMap.getOrDefault(app(), Boolean.TRUE);
            return shown == null || !shown.booleanValue();
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 当前应用是否已处于长期隐藏状态 */
    static boolean isPermHidden() {
        try {
            Boolean hidden = HookInit.permanentHiddenMap.getOrDefault(app(), Boolean.FALSE);
            return hidden != null && hidden.booleanValue();
        } catch (Throwable ignored) {
        }
        return false;
    }

    static String app() {
        try {
            HookInit h = ReaLog.sHookInit;
            String a = h != null ? h.getCurrentTargetApp() : "";
            return a == null ? "" : a;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * 统一写操作日志：新界面所有开关/增删都走这里。 分类必须用 ReaLog 已注册的分类，否则日志会落到未分组里、在「全部」视图看不见： launch_intercept /
     * exit_intercept / permission / anti_detection（及 class/file/pm/proc/ stacktrace/root/adb/dev）/
     * share_fake / vpn / network / back_key / selinux / system / misc。
     */
    static void log(String category, String detail) {
        try {
            ReaLog.log(category, detail);
        } catch (Throwable ignored) {
        }
    }

    public static void save(Activity a) {
        try {
            // 全局刷新开关：写过多一份数据，所有已打开的页面 onShow 时都会重读
            PageStack.bumpDataVersion();
        } catch (Throwable ignored) {
        }
        try {
            if (ReaLog.sHookInit == null) return;
            ReaLog.sHookInit.saveConfigToFile();
            // 配置变化后立刻刷新悬浮窗显示，避免状态滞后
            if (a != null) ReaLog.sHookInit.uiRefreshFloating(a);
        } catch (Throwable ignored) {
        }
    }

    static void toast(Activity a, String msg) {
        try {
            com.install.appinstall.xl.util.ToastUtil.showUnique(a, msg);
        } catch (Throwable ignored) {
        }
    }

    /** 支持长按配置的开关行 */
    static LinearLayout sw(final Activity a, String title, String desc,
            boolean checked, final UiComponents.OnChecked listener) {
        String safeTitle = UiKit.orDefault(title, "未命名项");
        String safeDesc = UiKit.isBlank(desc) ? "" : desc.trim();
        String hint = (safeDesc.length() == 0)
                ? "长按可配置参数" : safeDesc + "\n长按可配置参数";
        LinearLayout row = UiComponents.switchRow(a, safeTitle, hint, checked, listener);
        final String lt = safeTitle;
        final String ls = desc == null ? "" : desc;
        final boolean lc = checked;
        final UiComponents.MiniSwitch[] swRef = new UiComponents.MiniSwitch[1];
        int swLast = row.getChildCount() - 1;
        if (swLast >= 0 && row.getChildAt(swLast) instanceof UiComponents.MiniSwitch) {
            swRef[0] = (UiComponents.MiniSwitch) row.getChildAt(swLast);
        }
        row.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                PageStack s = PageStack.get();
                boolean nowChecked = swRef[0] != null ? swRef[0].isChecked() : lc;
                if (s != null) s.push(new UiPages.DetailSheetPage(lt, ls, nowChecked, listener));
                UiKit.haptic(v);
                return true;
            }
        });
        return row;
    }

    /** 布尔开关行（一级 Map）：默认记到「模块状态」分类 */
    static LinearLayout boolRow(final Activity a, String title, String desc,
            final Map<String, Boolean> map, boolean defVal) {
        return boolRow(a, title, desc, map, defVal, "system");
    }

    /** 带日志分类的布尔开关行 */
    static LinearLayout boolRow(final Activity a, final String title, String desc,
            final Map<String, Boolean> map, boolean defVal, final String category) {
        return boolRow(a, title, desc, map, defVal, category, false);
    }

    /** 带日志分类的布尔开关行；restartToast=true 时切换后提示"需要重启目标应用后生效" */
    static LinearLayout boolRow(final Activity a, final String title, String desc,
            final Map<String, Boolean> map, boolean defVal, final String category,
            final boolean restartToast) {
        final String app = app();
        boolean on = defVal;
        try {
            Boolean v = map.get(app);
            if (v != null) on = v.booleanValue();
        } catch (Throwable ignored) {
        }
        return sw(a, title, desc, on, new UiComponents.OnChecked() {
            public void onChecked(boolean checked) {
                try {
                    map.put(app, Boolean.valueOf(checked));
                    save(a);
                } catch (Throwable ignored) {
                }
                log(category, title + " -> " + (checked ? "开启" : "关闭"));
                if (restartToast) UiComponents.restartHint(a);
            }
        });
    }

    /** 布尔开关行（二级 Map：按目标应用分组的子项） */
    static LinearLayout detailRow(final Activity a, String title, String desc,
            final Map<String, Map<String, Boolean>> detailMap, final String key, boolean defVal) {
        return detailRow(a, title, desc, detailMap, key, defVal, "system");
    }

    /** 带日志分类的二级 Map 开关行*/
    static LinearLayout detailRow(final Activity a, final String title, String desc,
            final Map<String, Map<String, Boolean>> detailMap, final String key,
            boolean defVal, final String category) {
        final String app = app();
        boolean on = defVal;
        try {
            Map<String, Boolean> m = detailMap.get(app);
            if (m != null) {
                Boolean v = m.get(key);
                if (v != null) on = v.booleanValue();
            }
        } catch (Throwable ignored) {
        }
        return sw(a, title, desc, on, new UiComponents.OnChecked() {
            public void onChecked(boolean checked) {
                try {
                    Map<String, Boolean> m = detailMap.get(app);
                    if (m == null) {
                        m = new HashMap<String, Boolean>();
                        detailMap.put(app, m);
                    }
                    m.put(key, Boolean.valueOf(checked));
                    save(a);
                } catch (Throwable ignored) {
                }
                log(category, title + " -> " + (checked ? "开启" : "关闭"));
            }
        });
    }

    static LinearLayout entryRow(Activity a, String title, String desc,
            View.OnClickListener l) {
        return UiComponents.settingRow(a, title, desc, null, l);
    }

    /** 强调行：标题用主题色加粗（恢复悬浮窗 / 查看日志 / 当前应用等关键项） */
    static LinearLayout entryRowHi(Activity a, String title, String desc,
            View.OnClickListener l) {
        LinearLayout row = UiComponents.settingRow(a, title, desc, null, l);
        try {
            View c0 = row.getChildCount() > 0 ? row.getChildAt(0) : null;
            if (c0 instanceof LinearLayout) {
                LinearLayout col = (LinearLayout) c0;
                View c1 = col.getChildCount() > 0 ? col.getChildAt(0) : null;
                if (c1 instanceof TextView) {
                    TextView t = (TextView) c1;
                    t.setTextColor(UiKit.accent(a));
                    t.setTypeface(null, android.graphics.Typeface.BOLD);
                }
            }
        } catch (Throwable ignored) {
        }
        return row;
    }

    /** 值强调行：副标题（值）用主题色加粗，无点击 */
    static LinearLayout valueRowHi(Activity a, String title, String value) {
        return UiComponents.settingRow(a, title, value, null, null, UiKit.accent(a));
    }

    // ============================================================
    //  导出配置页
    // ============================================================
    /** 拉起系统文件选择器导入：导入页空态与列表入口共用 */
    static void openImportPicker(Activity a, String app, TextView fv) {
        log("config", "导入：走系统文件选择器");
        try {
            com.install.appinstall.xl.util.Portcf.startImportFromPicker(
                    a, app, fv, ReaLog.sHookInit);
        } catch (Throwable t) {
            toast(a, "打开文件选择器失败");
        }
    }

    /** 导入页。external != null 表示文件是从系统文件选择器选进来的（不在私有目录） */
    public static View importPage(final Activity a, final TextView fv, final java.io.File external) {
        LinearLayout content = UiComponents.paddedContent(a);
        final String app = app();

        TextView tip = UiKit.text(a,
                "从备份文件里挑配置项，覆盖当前目标应用的设置", 12, UiKit.textSub(a));
        tip.setPadding(UiKit.dpI(a, 4), 0, UiKit.dpI(a, 4), UiKit.dpI(a, 8));
        content.addView(tip);

        final java.util.List<java.io.File> files;
        if (external != null) {
            files = new ArrayList<java.io.File>();
            files.add(external);
        } else {
            files = com.install.appinstall.xl.util.Portcf.listBackupFiles(a);
        }

        // ---- 空态：私有目录里一个备份都没有 ----
        if (files == null || files.isEmpty()) {
            UiComponents.CardGroup empty = UiComponents.cardGroup(a, "没有找到配置文件", true);
            empty.addRow(entryRow(a, "还没有备份？去导出配置",
            "打开导出页，勾选要导出的项，保存后回到这里就能看到",
            new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.push(new UiPages.ExportPage(fv));
                }
            }));
            empty.addRow(entryRowHi(a, "从文件选择器导入", "选任意目录下的 .json 配置文件",
            new View.OnClickListener() {
                public void onClick(View v) {
                    openImportPicker(a, app, fv);
                }
            }));
            content.addView(empty.root);
            return UiComponents.scroll(a, content, "scroll:import");
        }

        // ---- 1. 选文件（单选；默认上次选的那个，没有就选最新）----
        java.io.File pickedFile = null;
        if (UiPages.ImportPage.sPickedFile != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).getAbsolutePath()
                        .equals(UiPages.ImportPage.sPickedFile.getAbsolutePath())) {
                    pickedFile = files.get(i);
                    break;
                }
            }
        }
        if (pickedFile == null) pickedFile = files.get(0);
        final java.io.File[] chosen = {pickedFile};
        UiComponents.CardGroup fileCard = UiComponents.cardGroup(a,
                "备份文件（" + files.size() + "）", true);
        for (int i = 0; i < files.size(); i++) {
            final java.io.File f = files.get(i);
            final String raw = f.getName();
            final boolean isSel = f.getAbsolutePath().equals(chosen[0].getAbsolutePath());
            String timeStr = "";
            try {
                timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",
                java.util.Locale.getDefault()).format(new java.util.Date(f.lastModified()));
            } catch (Throwable ignored) {
            }
            // 右侧删除：备份文件
            final TextView delBtn = UiComponents.chipButton(a, "删除", null, true, 11);
            final boolean[] delArmed = {false};
            delBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (!delArmed[0]) {
                        delArmed[0] = true;
                        UiKit.haptic(v);
                        delBtn.setText("确认删除");
                        toast(a, "再点一次删除 " + raw);
                        new android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(new Runnable() {
                                    public void run() {
                                        delArmed[0] = false;
                                        delBtn.setText("删除");
                                    }
                                }, UiKit.TWO_STEP_ARM_MS);
                        return;
                    }
                    try {
                        if (f.exists() && f.delete()) {
                            log("config", "删除配置备份 " + raw);
                            if (UiPages.ImportPage.sPickedFile != null
                                    && UiPages.ImportPage.sPickedFile.getAbsolutePath()
                                            .equals(f.getAbsolutePath())) {
                                UiPages.ImportPage.sPickedFile = null;
                            }
                            toast(a, "已删除 " + raw);
                            PageStack s = PageStack.get();
                            if (s != null) s.refreshTopContent();
                        } else {
                            toast(a, "删除失败");
                        }
                    } catch (Throwable tt) {
                        toast(a, "删除失败");
                    }
                }
            });
            final LinearLayout row = UiComponents.settingRow(a,
                    (isSel ? "✓  " : "    ") + raw,
                    timeStr + (i == 0 ? "  ·  最新" : ""), delBtn,
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            if (f.getAbsolutePath().equals(chosen[0].getAbsolutePath())) return;
                            // 换文件后下面的「配置项」必须按新文件重读
                            UiPages.ImportPage.sPickedFile = f;
                            PageStack s = PageStack.get();
                            if (s != null) s.refreshTopContent();
                        }
                    });
            if (isSel) {
                try {
                    View c0 = row.getChildAt(0);
                    if (c0 instanceof LinearLayout) {
                        View c1 = ((LinearLayout) c0).getChildAt(0);
                        if (c1 instanceof TextView) ((TextView) c1).setTextColor(UiKit.accent(a));
                    }
                } catch (Throwable ignored) {
                }
            }
            fileCard.addRow(row);
        }
        content.addView(fileCard.root);
        content.addView(UiKit.gap(a, 6));

        // ---- 2. 选配置项：按这个文件里**真实存在**的字段生成 ----
        final java.util.List<
                String> pkgsInFile = com.install.appinstall.xl.util.Portcf.readPackages(chosen[0]);
        final String srcPkg = (pkgsInFile == null || pkgsInFile.isEmpty())
                ? app : pkgsInFile.get(0);
        final java.util.LinkedHashMap<
                String, String> items = com.install.appinstall.xl.util.Portcf.readItemKeys(chosen[
        0], srcPkg);
        if (!srcPkg.equals(app)) {
            TextView srcTip = UiKit.text(a, "文件来源：" + srcPkg
                    + ((pkgsInFile != null && pkgsInFile.size() > 1)
                            ? "（文件里有 " + pkgsInFile.size() + " 个包名，取第一个）" : ""),
                    11, UiKit.textSub(a));
            srcTip.setPadding(UiKit.dpI(a, 4), 0, UiKit.dpI(a, 4), UiKit.dpI(a, 6));
            content.addView(srcTip);
        }
        final java.util.LinkedHashMap<String, Boolean> sel = new java.util.LinkedHashMap<
                String, Boolean>();
        LinearLayout card = plainCard(a);
        final java.util.List<UiComponents.MiniSwitch> switches = new ArrayList<
                UiComponents.MiniSwitch>();
        LinearLayout importSelBar = UiKit.hbox(a);
        importSelBar.setGravity(Gravity.CENTER_VERTICAL);
        importSelBar.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10),
                UiKit.dpI(a, 4), UiKit.dpI(a, 10));
        final TextView importSelState = UiKit.text(a, "", 12, UiKit.textSub(a));
        LinearLayout.LayoutParams importSelStateLp = new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        importSelStateLp.rightMargin = UiKit.dpI(a, 6);
        importSelBar.addView(importSelState, importSelStateLp);
        final TextView importSelBtn = UiComponents.chipButton(a, "全选", null, false);
        importSelBar.addView(importSelBtn, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
        final Runnable refreshImportSel = new Runnable() {
            public void run() {
                int total = sel.size();
                int picked = 0;
                java.util.Iterator<String> it = sel.keySet().iterator();
                while (it.hasNext()) {
                    if (Boolean.TRUE.equals(sel.get(it.next()))) picked++;
                }
                boolean all = total > 0 && picked >= total;
                importSelBtn.setText(all ? "取消" : "全选");
                importSelState.setText("待导入 " + picked + " 项，待保留 "
                        + (total - picked) + " 项");
            }
        };
        importSelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean toAll = !"取消".equals(importSelBtn.getText().toString());
                java.util.Iterator<String> i = sel.keySet().iterator();
                while (i.hasNext()) sel.put(i.next(), Boolean.valueOf(toAll));
                for (int j = 0; j < switches.size(); j++) {
                    UiComponents.MiniSwitch m = switches.get(j);
                    if (m != null) m.setCheckedSilently(toAll);
                }
                refreshImportSel.run();
            }
        });

        // 先把「文件里的值」一次性读出来（每行只解析一次文件，顺带算出哪些会变化）
        final java.util.LinkedHashMap<String, String> fileVals = new java.util.LinkedHashMap<
                String, String>();
        final java.util.LinkedHashMap<String, Boolean> willChange = new java.util.LinkedHashMap<
                String, Boolean>();
        int changeCount = 0;
        if (items != null && !items.isEmpty()) {
            java.util.Iterator<String> pit = items.keySet().iterator();
            while (pit.hasNext()) {
                String pk = pit.next();
                String fileText = "";
                try {
                    // 和「当前」侧同一套可读格式，两边能直接对照
                    fileText = com.install.appinstall.xl.util.Portcf.fileValueOf(
                            chosen[0], srcPkg, pk);
                } catch (Throwable ignored) {
                }
                fileVals.put(pk, fileText);
                boolean unknown = items.get(pk) != null
                        && items.get(pk).startsWith("未知字段");
                String cv = "";
                try {
                    cv = com.install.appinstall.xl.util.Portcf.configValueOf(app, pk);
                } catch (Throwable ignored) {
                }
                boolean changed = !unknown && fileText.length() > 0
                        && (cv == null || !cv.equals(fileText));
                willChange.put(pk, Boolean.valueOf(changed));
                if (changed) changeCount++;
            }
        }
        if (items != null && !items.isEmpty()) {
            java.util.Iterator<String> it = items.keySet().iterator();
            while (it.hasNext()) {
                final String key = it.next();
                sel.put(key, Boolean.TRUE);
                String cur = "";
                try {
                    cur = com.install.appinstall.xl.util.Portcf.configValueOf(app, key);
                } catch (Throwable ignored) {
                }
                String fileVal = fileVals.get(key);
                if (fileVal == null) fileVal = "";
                boolean changed = Boolean.TRUE.equals(willChange.get(key));
                String desc;
                if (items.get(key) != null && items.get(key).startsWith("未知字段")) {
                    desc = "当前版本无此字段，导入时自动忽略";
                } else if (fileVal.length() == 0) {
                    desc = "文件里没有这一项";
                } else if (!changed) {
                    desc = "文件：" + fileVal + " · 与当前一致";
                } else {
                    desc = "将变化 · 文件：" + fileVal
                            + ((cur != null && cur.length() > 0) ? (" · 当前：" + cur) : "");
                }
                LinearLayout srow = UiComponents.switchRow(a, items.get(key),
                        desc, true,
                        new UiComponents.OnChecked() {
                            public void onChecked(boolean checked) {
                                sel.put(key, Boolean.valueOf(checked));
                                refreshImportSel.run();
                            }
                        }, changed ? UiKit.danger(a) : 0);
                int lastIdx = srow.getChildCount() - 1;
                if (lastIdx >= 0 && srow.getChildAt(lastIdx) instanceof UiComponents.MiniSwitch) {
                    switches.add((UiComponents.MiniSwitch) srow.getChildAt(lastIdx));
                }
                card.addView(UiComponents.rowDivider(a));
                card.addView(srow);
            }
        }
        if (items == null || items.isEmpty()) {
            TextView none = UiKit.text(a, "这个文件里没有可识别的配置项",
                    12, UiKit.textSub(a));
            none.setPadding(0, UiKit.dpI(a, 10), 0, UiKit.dpI(a, 10));
            card.addView(none);
        }
        content.addView(importSelBar, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshImportSel.run();
        content.addView(card, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (items != null && !items.isEmpty()) {
            TextView sum = UiKit.text(a,
                    changeCount == 0 ? "与当前配置完全一致，导入不会产生变化"
                            : ("共 " + changeCount + " 项会发生变化，其余保持不变"),
                    12, UiKit.danger(a));
            sum.setPadding(0, UiKit.dpI(a, 10), 0, UiKit.dpI(a, 2));
            content.addView(sum);
        }

        // ---- 3. 导入 ----
        TextView ok = UiComponents.primaryButton(a, "导入到当前应用", null);
        UiComponents.confirmButton(ok, "再点一次确认导入", new View.OnClickListener() {
            public void onClick(View v) {
                TextView btn = (TextView) v;
                if (UiGuard.isBusy("import") || !UiGuard.tap("import", 600)) return;
                List<String> keys = checkedKeys(sel);
                if (keys.isEmpty()) {
                    toast(a, "至少选一项配置");
                    return;
                }
                UiGuard.begin("import");
                btn.setEnabled(false);
                btn.setText("导入中…");
                String result = null;
                try {
                    result = com.install.appinstall.xl.util.Portcf.importFromFile(
                            a, app, keys, fv, ReaLog.sHookInit, chosen[0]);
                } catch (Throwable t) {
                    toast(a, "导入失败");
                    ReaLog.log("ui", "导入异常: " + t);
                    UiGuard.end("import");
                    btn.setEnabled(true);
                    btn.setText("导入到当前应用");
                    return;
                }
                // 导入结束（无论成败）：解除锁、恢复按钮
                UiGuard.end("import");
                btn.setEnabled(true);
                btn.setText("导入到当前应用");
                log("config", "导入配置 " + keys.size() + " 项，来自 " + chosen[0].getName()
                        + "，结果 " + result);
                if (result != null && result.startsWith("success:")) {
                    if (external != null) {
                        try {
                            external.delete();
                        } catch (Throwable ignored) {
                        }
                    }
                    // 改的是 hook 行为，必须重启目标应用才彻底生效
                    UiComponents.restartHint(a);
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.refreshTopContent();
                }
            }
        });
        LinearLayout.LayoutParams okp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okp.topMargin = UiKit.dpI(a, 10);
        content.addView(ok, okp);

        // 文件选择器入口
        LinearLayout pickRow = UiComponents.settingRow(a, "从文件选择器导入",
                "选别的目录里的 .json 配置文件（会切换到文件里的配置）", null,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        openImportPicker(a, app, fv);
                    }
                });
        LinearLayout.LayoutParams pickp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pickp.topMargin = UiKit.dpI(a, 8);
        content.addView(pickRow, pickp);

        return UiComponents.scroll(a, content, "scroll:import");
    }

    public static View exportPage(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);
        final java.util.LinkedHashMap<
                String, String> items = com.install.appinstall.xl.util.Portcf.getExportableItems();
        final java.util.LinkedHashMap<String, Boolean> sel = new java.util.LinkedHashMap<
                String, Boolean>();

        if (items == null || items.isEmpty()) {
            TextView t = UiKit.text(a, "没有可导出的配置项", 14, UiKit.textSub(a));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UiKit.dpI(a, 24), 0, 0);
            content.addView(t);
            return UiComponents.scroll(a, content, "scroll:export");
        }
        LinearLayout card = plainCard(a);
        final List<UiComponents.MiniSwitch> switches = new ArrayList<UiComponents.MiniSwitch>();
        LinearLayout exportSelBar = UiKit.hbox(a);
        exportSelBar.setGravity(Gravity.CENTER_VERTICAL);
        exportSelBar.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10),
                UiKit.dpI(a, 4), UiKit.dpI(a, 10));
        final TextView exportSelState = UiKit.text(a, "", 12, UiKit.textSub(a));
        LinearLayout.LayoutParams exportSelStateLp = new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        exportSelStateLp.rightMargin = UiKit.dpI(a, 6);
        exportSelBar.addView(exportSelState, exportSelStateLp);
        final TextView exportSelBtn = UiComponents.chipButton(a, "全选", null, false);
        exportSelBar.addView(exportSelBtn, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
        final Runnable refreshExportSel = new Runnable() {
            public void run() {
                int total = sel.size();
                int picked = 0;
                java.util.Iterator<String> it = sel.keySet().iterator();
                while (it.hasNext()) {
                    if (Boolean.TRUE.equals(sel.get(it.next()))) picked++;
                }
                boolean all = total > 0 && picked >= total;
                exportSelBtn.setText(all ? "取消" : "全选");
                exportSelState.setText("待导出 " + picked + " 项，待保留 "
                        + (total - picked) + " 项");
            }
        };
        exportSelBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean toAll = !"取消".equals(exportSelBtn.getText().toString());
                java.util.Iterator<String> i = sel.keySet().iterator();
                while (i.hasNext()) sel.put(i.next(), Boolean.valueOf(toAll));
                for (int j = 0; j < switches.size(); j++) {
                    UiComponents.MiniSwitch m = switches.get(j);
                    if (m != null) m.setCheckedSilently(toAll);
                }
                refreshExportSel.run();
            }
        });

        java.util.Iterator<String> it = items.keySet().iterator();
        while (it.hasNext()) {
            final String key = it.next();
            sel.put(key, Boolean.TRUE);
            String cur = "";
            try {
                cur = com.install.appinstall.xl.util.Portcf.configValueOf(app(), key);
            } catch (Throwable ignored) {
            }
            LinearLayout srow = UiComponents.switchRow(a, items.get(key),
                    (cur == null || cur.length() == 0) ? null : "当前：" + cur, true,
                    new UiComponents.OnChecked() {
                        public void onChecked(boolean checked) {
                            sel.put(key, Boolean.valueOf(checked));
                            refreshExportSel.run();
                        }
                    });
            int lastIdx = srow.getChildCount() - 1;
            if (lastIdx >= 0 && srow.getChildAt(lastIdx) instanceof UiComponents.MiniSwitch) {
                switches.add((UiComponents.MiniSwitch) srow.getChildAt(lastIdx));
            }
            card.addView(UiComponents.rowDivider(a));
            card.addView(srow);
        }

        content.addView(exportSelBar, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshExportSel.run();
        content.addView(card, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView exportPathTip = UiKit.text(a,
                "保存路径(参考)：" + com.install.appinstall.xl.util.Portcf.exportPathFor(a, app()),
                12, UiKit.accent(a));
        content.addView(exportPathTip, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView ok = UiComponents.primaryButton(a, "保存", null);
        UiComponents.confirmButton(ok, "再点一次确认保存导出", new View.OnClickListener() {
            public void onClick(View v) {
                List<String> keys = checkedKeys(sel);
                if (keys.isEmpty()) {
                    toast(a, "请至少选择一项");
                    return;
                }
                try {
                    com.install.appinstall.xl.util.Portcf.exportDirect(a, app(), keys);
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                } catch (Throwable t) {
                    log("config", "导出失败: " + t);
                    toast(a, "导出失败");
                }
            }
        });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.topMargin = UiKit.dpI(a, 8);
        content.addView(ok, op);

        View saveAs = UiComponents.ghostButton(a, "另存为…", new View.OnClickListener() {
            public void onClick(View v) {
                List<String> keys = checkedKeys(sel);
                if (keys.isEmpty()) {
                    toast(a, "请至少选择一项");
                    return;
                }
                try {
                    com.install.appinstall.xl.util.Portcf.exportSaveAs(a, app(), keys);
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                } catch (Throwable t) {
                    log("config", "导出失败: " + t);
                    toast(a, "导出失败");
                }
            }
        });
        LinearLayout.LayoutParams sap = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sap.topMargin = UiKit.dpI(a, 6);
        content.addView(saveAs, sap);

        return UiComponents.scroll(a, content, "scroll:export");
    }

    // ============================================================
    //  权限明细
    // ============================================================
    public static View permissions(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);
        final String app = app();
        HookInit h = ReaLog.sHookInit;

        boolean global = true;
        Boolean g = HookInit.permissionFakeMap.get(app);
        if (g != null) global = g;

        UiComponents.CardGroup head = UiComponents.cardGroup(a, "权限防护", true);
        LinearLayout permStatusRow = UiComponents.settingRow(a, "当前状态", "统计中…", null, null);
        final TextView[] permStatusSub = {new TextView(a)};
        try {
            View sc0 = permStatusRow.getChildAt(0);
            if (sc0 instanceof LinearLayout && ((LinearLayout) sc0).getChildCount() > 1) {
                View sc1 = ((LinearLayout) sc0).getChildAt(1);
                if (sc1 instanceof TextView) permStatusSub[0] = (TextView) sc1;
            }
        } catch (Throwable ignored) {
        }
        head.addRow(permStatusRow);
        final Runnable[] refreshPermStatus = new Runnable[1];
        refreshPermStatus[0] = new Runnable() {
            public void run() {
                int permTotal = 0;
                int permDefault = 0;
                int permChecked = 0;
                int permCustom = 0;
                boolean gNow = true;
                try {
                    Boolean gg = HookInit.permissionFakeMap.get(app);
                    if (gg != null) gNow = gg.booleanValue();
                    Map<String, Boolean> dm = HookInit.permissionFakeDetailMap.get(app);
                    if (dm != null) permCustom = dm.size();
                    HookInit hh = ReaLog.sHookInit;
                    List<String> pl = (hh == null) ? null : hh.getAppDeclaredPermissions();
                    if (pl != null) {
                        permTotal = pl.size();
                        for (int i = 0; i < pl.size(); i++) {
                            String p = pl.get(i);
                            if (p == null) continue;
                            boolean def = isDetectionPerm(p);
                            if (def) permDefault++;
                            boolean on = gNow && def;
                            if (dm != null && dm.containsKey(p)) {
                                Boolean dv = dm.get(p);
                                if (dv != null) on = dv.booleanValue();
                            }
                            if (on) permChecked++;
                        }
                    }
                } catch (Throwable ignored) {
                }
                try {
                    permStatusSub[0].setText((gNow ? "防护中" : "已关闭")
                            + " · 已勾选 " + permChecked + " / " + permTotal + " 项"
                            + " · 默认 " + permDefault + " 项"
                            + " · 自定义调整 " + permCustom + " 项");
                } catch (Throwable ignored) {
                }
            }
        };
        refreshPermStatus[0].run();
        final boolean[] cleanArmed = {false};
        LinearLayout cleanRow = entryRow(a, "清理自定义勾选",
        "清空自定义勾选，恢复默认基础权限",
        new View.OnClickListener() {
            public void onClick(View v) {
                View hit = v.findViewWithTag("row_title");
                final TextView cleanTitle = hit instanceof TextView ? (TextView) hit : null;
                if (!cleanArmed[0]) {
                    cleanArmed[0] = true;
                    UiKit.haptic(v);
                    if (cleanTitle != null) cleanTitle.setText("再点一次确认清理");
                    toast(a, "再点一次确认清理");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    cleanArmed[0] = false;
                                    if (cleanTitle != null) {
                                        cleanTitle.setText("清理自定义勾选");
                                    }
                                }
                            }, UiKit.TWO_STEP_ARM_MS);
                    return;
                }
                cleanArmed[0] = false;
                try {
                    HookInit hi = ReaLog.sHookInit;
                    Map<String, Boolean> cur = HookInit.permissionFakeDetailMap.get(app);
                    HookInit.permissionFakeDetailMap.put(app,
                            new HashMap<String, Boolean>());
                    HookInit.permissionFakeMap.put(app, Boolean.TRUE);
                    log("permission", "清理自定义勾选权限 "
                            + (cur == null ? 0 : cur.size()) + " 项");
                    if (hi != null) {
                        hi.saveConfigToFile();
                        hi.reHookPermissionMethods(a.getClassLoader());
                        hi.clearPermissionCache(a);
                        hi.sendPermissionChangeBroadcast(a);
                    }
                } catch (Throwable ignored) {
                }
                toast(a, "已清理，恢复默认勾选");
                PageStack ps = PageStack.get();
                if (ps != null) ps.refreshTopContent();
            }
        });
        View cleanHit = cleanRow.findViewWithTag("row_title");
        TextView cleanTitle = cleanHit instanceof TextView ? (TextView) cleanHit : null;
        if (cleanTitle != null) {
            cleanTitle.setTextColor(UiKit.danger(a));
            cleanTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        head.addRow(cleanRow);
        content.addView(head.root);
        final List<String> permAll;
        try {
            permAll = h == null ? null : h.getAppDeclaredPermissions();
        } catch (Throwable t) {
            permAll = null;
        }
        final boolean hasPerms = permAll != null && !permAll.isEmpty();
        final LinearLayout[] holder = new LinearLayout[1];
        if (hasPerms) {
            android.widget.EditText search = UiComponents.searchBox(a, "搜索权限（中文 / 英文）", null);
            search.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

                public void onTextChanged(CharSequence s, int st, int b, int c) {}

                public void afterTextChanged(android.text.Editable e) {
                    LinearLayout gcard = holder[0];
                    if (gcard == null) return;
                    String q = e == null ? "" : e.toString().trim().toLowerCase();
                    View firstHit = null;
                    for (int i = 0; i < gcard.getChildCount(); i++) {
                        View child = gcard.getChildAt(i);
                        Object d = child.getContentDescription();
                        if (!(d instanceof String)) {
                            // 分类标题：搜索时一并隐藏
                            if ("group".equals(child.getTag())) {
                                child.setVisibility(q.length() == 0 ? View.VISIBLE : View.GONE);
                            }
                            continue;
                        }
                        boolean hit = q.length() == 0
                                || ((String) d).toLowerCase().contains(q);
                        child.setVisibility(hit ? View.VISIBLE : View.GONE);
                        child.setBackground(null);
                        if (hit && firstHit == null) firstHit = child;
                    }
                    if (firstHit != null && q.length() > 0) {
                        firstHit.setBackground(UiKit.roundRect(a, UiKit.accentSoft(a), 10));
                        final View fh = firstHit;
                        fh.post(new Runnable() {
                            public void run() {
                                try {
                                    fh.requestRectangleOnScreen(new android.graphics.Rect(0, 0,
                                    fh.getWidth(), fh.getHeight()), true);
                                } catch (Throwable ignored) {
                                }
                            }
                        });
                    }
                }
            });
            LinearLayout.LayoutParams spx = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            spx.topMargin = UiKit.dpI(a, 4);
            spx.bottomMargin = UiKit.dpI(a, 8);
            content.addView(search, spx);
        }

        if (hasPerms) {
            try {
                List<String> all = permAll;
                if (all != null && !all.isEmpty()) {
                    Map<String, Boolean> detail = HookInit.permissionFakeDetailMap.get(app);
                    final String permFoldKey = "fold:perm-all";
                    final boolean[] collapsed = {UiState.fold(permFoldKey)};
                    final Runnable[] applyPermFold = new Runnable[1];
                    applyPermFold[0] = new Runnable() {
                        public void run() {}
                    };
                    final java.util.List<View> groupHeads = new ArrayList<View>();
                    final java.util.List<java.util.List<View>> groupBodies = new ArrayList<
                            java.util.List<View>>();
                    // 全局全选/取消全选的数据源（点击时才填充完毕，所以先建空表）
                    final java.util.List<UiComponents.MiniSwitch> allSwitches = new ArrayList<
                            UiComponents.MiniSwitch>();
                    final java.util.List<String> allPermNames = new ArrayList<String>();
                    final TextView[] mergeBtn = new TextView[3];
                    // 「只看默认勾选」
                    final boolean[] onlyDefault = {false};
                    // 分类的圆点和标题刷新器
                    final java.util.List<TextView> gDots = new ArrayList<TextView>();
                    final java.util.List<Runnable> gRefresh = new ArrayList<Runnable>();
                    // 初值按实际数据算：全部为真才是「取消全选」
                    boolean allOnInit = true;
                    for (int ai = 0; ai < all.size(); ai++) {
                        String ap = all.get(ai);
                        if (ap == null || ap.trim().length() == 0) continue;
                        boolean av = global && isDetectionPerm(ap);
                        if (detail != null && detail.containsKey(ap)) {
                            Boolean ad = detail.get(ap);
                            if (ad != null) av = ad.booleanValue();
                        }
                        if (!av) {
                            allOnInit = false;
                            break;
                        }
                    }
                    final boolean[] allOn = {allOnInit};
                    final View.OnClickListener allBtnClick = new View.OnClickListener() {
                        public void onClick(View v) {
                            boolean next = !allOn[0];
                            applyPerms(a, app, allPermNames, allSwitches, next);
                            if (refreshPermStatus[0] != null) refreshPermStatus[0].run();
                            allOn[0] = next;
                            if (mergeBtn[0] != null) {
                                mergeBtn[0].setText(next ? "取消全选" : "全选");
                            }
                            for (int i = 0; i < gDots.size(); i++) {
                                setDot(a, gDots.get(i), next);
                            }
                            for (int i = 0; i < gRefresh.size(); i++) {
                                Runnable r = gRefresh.get(i);
                                if (r != null) r.run();
                            }
                        }
                    };
                    final View.OnClickListener onlyDefaultClick = new View.OnClickListener() {
                        public void onClick(View v) {
                            onlyDefault[0] = !onlyDefault[0];
                            if (mergeBtn[1] != null) {
                                mergeBtn[1].setText(onlyDefault[0] ? "显示全部" : "只看默认");
                            }
                            LinearLayout gcard = holder[0];
                            if (gcard == null) return;
                            int shown = 0;
                            for (int i = 0; i < gcard.getChildCount(); i++) {
                                View child = gcard.getChildAt(i);
                                Object d = child.getContentDescription();
                                if (!(d instanceof String)) continue;
                                String permOfChild = (String) d;
                                int bar = permOfChild.indexOf('|');
                                if (bar > 0) permOfChild = permOfChild.substring(0, bar);
                                boolean show = !onlyDefault[0] || isDetectionPerm(permOfChild);
                                child.setVisibility(show ? View.VISIBLE : View.GONE);
                                if (show) shown++;
                            }
                            toast(a, onlyDefault[0] ? ("只看默认勾选，共 " + shown + " 项")
                                    : "已显示全部权限");
                        }
                    };
                    content.addView(sectionTitleWithActions(a,
                    "目标应用声明的权限（" + all.size() + "）",
                    new String[]{"全选", "只看默认", "收起"},
                    new View.OnClickListener[]{
                            allBtnClick,
                            onlyDefaultClick,
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    collapsed[0] = !collapsed[0];
                                    UiState.setFold(permFoldKey, collapsed[0]);
                                    applyPermFold[0].run();
                                    toast(a, collapsed[0] ? "已收起" : "已全部展开");
                                }
                            }
                    }, mergeBtn));
                    if (mergeBtn[0] != null) {
                        mergeBtn[0].setText(allOnInit ? "取消全选" : "全选");
                    }

                    // 按 PermissionName 的分类分组，组的顺序照旧沿用原分组顺序
                    java.util.LinkedHashMap<
                            String, java.util.List<String>> groups = new java.util.LinkedHashMap<
                            String, java.util.List<String>>();
                    for (int i = 0; i < all.size(); i++) {
                        String p = all.get(i);
                        if (UiKit.isBlank(p)) continue;
                        String grp = "";
                        try {
                            grp = com.install.appinstall.xl.util.PermissionName.groupOf(p);
                        } catch (Throwable ignored) {
                        }
                        // groupOf 认不出的权限要有个归属，否则会出现没有名字的分组标题
                        grp = UiKit.orDefault(grp, "其它权限");
                        java.util.List<String> gl = groups.get(grp);
                        if (gl == null) {
                            gl = new ArrayList<String>();
                            groups.put(grp, gl);
                        }
                        gl.add(p);
                    }

                    LinearLayout list = plainCard(a);
                    holder[0] = list;

                    java.util.Iterator<String> git = groups.keySet().iterator();
                    final java.util.List<java.util.List<View>> allBodies = groupBodies;
                    while (git.hasNext()) {
                        final String groupName = git.next();
                        final java.util.List<String> members = groups.get(groupName);
                        final java.util.List<View> bodyRows = new ArrayList<View>();
                        final java.util.List<UiComponents.MiniSwitch> gsw = new ArrayList<
                                UiComponents.MiniSwitch>();

                        // 标题写成「组名（总数/未启用数）」，未启用数标红；
                        // 全启用时只写总数（不标红），全都没启用时把总数标红。
                        final TextView arrow = UiKit.text(a, "▾", 12, UiKit.accent(a));
                        int offInit = 0;
                        for (int gi = 0; gi < members.size(); gi++) {
                            boolean v = global && isDetectionPerm(members.get(gi));
                            if (detail != null && detail.containsKey(members.get(gi))) {
                                Boolean dv = detail.get(members.get(gi));
                                if (dv != null) v = dv.booleanValue();
                            }
                            if (!v) offInit++;
                        }
                        final TextView gh = UiKit.text(a,
                                permGroupLabel(groupName, members.size(), offInit), 12,
                                UiKit.accent(a));
                        gh.setTypeface(null, android.graphics.Typeface.BOLD);
                        gh.setMaxLines(1);
                        gh.setEllipsize(android.text.TextUtils.TruncateAt.END);

                        LinearLayout ghRow = UiKit.hbox(a);
                        ghRow.setGravity(Gravity.CENTER_VERTICAL);
                        ghRow.setTag("group");
                        int gpad = UiKit.dpI(a, UiKit.PAGE_MARGIN);
                        ghRow.setPadding(gpad, UiKit.dpI(a, 8), UiKit.dpI(a, 4), UiKit.dpI(a, 2));
                        ghRow.addView(arrow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                        ghRow.addView(gh, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                        //分类仍用圆点，但状态要和实际开关一致
                        final boolean[] groupAll = {offInit == 0};
                        final TextView gDot = UiKit.text(a, groupAll[0] ? "●" : "○", 18,
                                groupAll[0] ? UiKit.accent(a) : UiKit.textSub(a));
                        gDot.setGravity(Gravity.CENTER);
                        gDot.setMinWidth(UiKit.dpI(a, 38));
                        gDot.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 2),
                                UiKit.dpI(a, 4), UiKit.dpI(a, 2));
                        gDot.setClickable(true);
                        // 按本组开关的实际状态重算标题与圆点
                        final Runnable refreshGroupTitle = new Runnable() {
                            public void run() {
                                int off = 0;
                                for (int gi = 0; gi < gsw.size(); gi++) {
                                    UiComponents.MiniSwitch ms = gsw.get(gi);
                                    if (ms != null && !ms.isChecked()) off++;
                                }
                                if (gsw.size() < members.size()) {
                                    off += members.size() - gsw.size();
                                }
                                gh.setText(permGroupLabel(groupName, members.size(), off));
                                boolean all = off == 0;
                                groupAll[0] = all;
                                setDot(a, gDot, all);
                            }
                        };
                        gDot.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View v) {
                                boolean next = !groupAll[0];
                                applyPerms(a, app, members, gsw, next);
                                if (refreshPermStatus[0] != null) refreshPermStatus[0].run();
                                refreshGroupTitle.run();
                            }
                        });
                        ghRow.addView(gDot);
                        gDots.add(gDot);
                        gRefresh.add(refreshGroupTitle);
                        final String grpFoldKey = "fold:permgrp:" + groupName;
                        final View.OnClickListener fold = new View.OnClickListener() {
                            public void onClick(View v) {
                                if (bodyRows.isEmpty()) return;
                                boolean vis = bodyRows.get(0).getVisibility() == View.VISIBLE;
                                for (int bi = 0; bi < bodyRows.size(); bi++) {
                                    bodyRows.get(bi).setVisibility(vis ? View.GONE : View.VISIBLE);
                                }
                                arrow.setText(vis ? "▸" : "▾");
                                UiState.setFold(grpFoldKey, !vis);
                            }
                        };
                        arrow.setClickable(true);
                        arrow.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 2),
                                UiKit.dpI(a, 6), UiKit.dpI(a, 2));
                        arrow.setOnClickListener(fold);
                        gh.setClickable(true);
                        gh.setOnClickListener(fold);
                        groupHeads.add(arrow);
                        list.addView(ghRow, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                        for (int j = 0; j < members.size(); j++) {
                            final String perm = members.get(j);
                            boolean on = global && isDetectionPerm(perm);
                            if (detail != null && detail.containsKey(perm)) {
                                Boolean v = detail.get(perm);
                                if (v != null) on = v.booleanValue();
                            }
                            String friendly = "";
                            try {
                                friendly = com.install.appinstall.xl.util.PermissionName
                                        .getFriendlyName(perm);
                            } catch (Throwable ignored) {
                            }
                            if (UiKit.isBlank(friendly)) {
                                friendly = shortPerm(perm);
                            }
                            if (UiKit.isBlank(friendly)) {
                                friendly = "未知权限";
                            }
                            final String copyPerm = perm;
                            LinearLayout prow = sw(a, friendly, perm, on,
                            new UiComponents.OnChecked() {
                                public void onChecked(boolean checked) {
                                    // 只把「与默认不同」的项写进详情表
                                    setPermState(app, perm, checked, allPermNames);
                                    save(a);
                                    if (refreshPermStatus[0] != null) refreshPermStatus[0].run();
                                    log("permission", "权限 " + copyPerm + " -> "
                                            + (checked ? "假装已授予" : "放行"));
                                    refreshGroupTitle.run();
                                    boolean allNow = true;
                                    for (int si = 0; si < allSwitches.size(); si++) {
                                        UiComponents.MiniSwitch xs = allSwitches.get(si);
                                        if (xs != null && !xs.isChecked()) {
                                            allNow = false;
                                            break;
                                        }
                                    }
                                    allOn[0] = allNow;
                                    if (mergeBtn[0] != null) {
                                        mergeBtn[0].setText(allNow ? "取消全选" : "全选");
                                    }
                                }
                            });
                            try {
                                prow.setContentDescription(perm + "|" + friendly);
                            } catch (Throwable ignored) {
                            }
                            prow.setClickable(true);
                            prow.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    copyText(a, copyPerm);
                                }
                            });
                            // 开关对象登记起来，供分类级 / 全局全选直接改状态
                            int lastIdx = prow.getChildCount() - 1;
                            if (lastIdx >= 0
                                    && prow.getChildAt(lastIdx)
                                            instanceof UiComponents.MiniSwitch) {
                                UiComponents.MiniSwitch ms = (UiComponents.MiniSwitch) prow.getChildAt(lastIdx);
                                gsw.add(ms);
                                allSwitches.add(ms);
                            }
                            allPermNames.add(perm);
                            list.addView(prow, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT));
                            bodyRows.add(prow);
                        }
                        // 按记忆恢复本组折叠
                        if (UiState.fold(grpFoldKey)) {
                            for (int bi = 0; bi < bodyRows.size(); bi++) {
                                bodyRows.get(bi).setVisibility(View.GONE);
                            }
                            arrow.setText("▸");
                        }
                        groupBodies.add(bodyRows);
                    }
                    content.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
                    // 全局合并/展开的渲染（幂等）
                    applyPermFold[0] = new Runnable() {
                        public void run() {
                            for (int i = 0; i < groupBodies.size(); i++) {
                                java.util.List<View> body = groupBodies.get(i);
                                for (int j = 0; j < body.size(); j++) {
                                    body.get(j).setVisibility(
                                            collapsed[0] ? View.GONE : View.VISIBLE);
                                }
                            }
                            for (int i = 0; i < groupHeads.size(); i++) {
                                View ar = groupHeads.get(i);
                                if (ar instanceof TextView) {
                                    ((TextView) ar).setText(collapsed[0] ? "▸" : "▾");
                                }
                            }
                            // 文案也算渲染的一部分，避免「折叠了但按钮还写合并」
                            if (mergeBtn[2] != null) {
                                mergeBtn[2].setText(collapsed[0] ? "展开" : "收起");
                            }
                        }
                    };
                    if (collapsed[0]) {
                        applyPermFold[0].run();
                        if (mergeBtn[2] != null) mergeBtn[2].setText("展开");
                    }
                } else {
                    LinearLayout tip = UiKit.vbox(a);
                    tip.setPadding(0, UiKit.dpI(a, 16), 0, 0);
                    TextView t = UiKit.text(a, "无法获取目标应用的权限列表\n需要目标应用真正运行起来后才能读取",
                            13, UiKit.textSub(a));
                    t.setGravity(Gravity.CENTER);
                    tip.addView(t);
                    content.addView(tip);
                }
            } catch (Throwable ignored) {
            }
        }

        return UiComponents.scroll(a, content, "scroll:perm");
    }

    private static String shortPerm(String p) {
        if (p == null) return "";
        int i = p.lastIndexOf('.');
        return i > 0 ? p.substring(i + 1) : p;
    }

    /** 恢复初始状态三级页面用：把列表子项的数据层 key 翻成界面一直用的文案。  */
    public static String resetItemLabel(String entryId, String itemKey) {
        if (itemKey == null) return "";
        if (entryId == null) return itemKey;
        if (entryId.equals("share_fake_detail")) {
            for (int i = 0; i < ShareHook.UI_KEYS.length; i++) {
                if (ShareHook.UI_KEYS[i].equals(itemKey)) return ShareHook.UI_LABELS[i];
            }
            return itemKey;
        }
        if (entryId.equals("vpn_fake_detail")) {
            for (int i = 0; i < VPN_KEYS.length; i++) {
                if (VPN_KEYS[i].equals(itemKey)) return VPN_LABELS[i];
            }
            return itemKey;
        }
        if (entryId.equals("anti_detection_detail")) {
            for (int i = 0; i < ANTI_KEYS.length; i++) {
                if (ANTI_KEYS[i].equals(itemKey)) return ANTI_LABELS[i];
            }
            return itemKey;
        }
        if (entryId.equals("permission_fake_detail")) {
            return shortPerm(itemKey);
        }
        return itemKey;
    }

    /**
     * 批量勾选 / 取消权限
     */
    static void setPermState(String app, String perm, boolean checked,
            java.util.List<String> allPerms) {
        try {
            Map<String, Boolean> detail = HookInit.permissionFakeDetailMap.get(app);
            if (detail == null) {
                detail = new HashMap<String, Boolean>();
                HookInit.permissionFakeDetailMap.put(app, detail);
            }
            boolean def = isDetectionPerm(perm);
            if (checked == def) {
                detail.remove(perm);
            } else {
                detail.put(perm, Boolean.valueOf(checked));
            }
            boolean any = false;
            if (allPerms != null) {
                for (int i = 0; i < allPerms.size(); i++) {
                    String p = allPerms.get(i);
                    if (UiKit.isBlank(p)) continue;
                    boolean v = isDetectionPerm(p);
                    if (detail.containsKey(p)) {
                        Boolean d = detail.get(p);
                        if (d != null) v = d.booleanValue();
                    }
                    if (v) {
                        any = true;
                        break;
                    }
                }
            }
            HookInit.permissionFakeMap.put(app, Boolean.valueOf(any));
        } catch (Throwable ignored) {
        }
    }

    private static void applyPerms(Activity a, String app, java.util.List<String> members,
            java.util.List<UiComponents.MiniSwitch> switches, boolean on) {
        if (members == null || members.isEmpty()) {
            toast(a, "这一组没有可勾选的权限");
            return;
        }
        // 逐项走统一写入：只留与默认不同的项，并按最终状态反推总开关
        java.util.List<String> all = new java.util.ArrayList<String>(members);
        try {
            java.util.List<String> declared = ReaLog.sHookInit == null
                    ? null : ReaLog.sHookInit.getAppDeclaredPermissions();
            if (declared != null && !declared.isEmpty()) all = declared;
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < members.size(); i++) {
            String p = members.get(i);
            if (p != null) setPermState(app, p, on, all);
        }
        if (switches != null) {
            for (int i = 0; i < switches.size(); i++) {
                UiComponents.MiniSwitch ms = switches.get(i);
                if (ms != null) ms.setCheckedSilently(on);
            }
        }
        save(a);
        log("permission", (on ? "全选 " : "取消全选 ") + members.size() + " 项权限");
        toast(a, (on ? "已全选 " : "已取消 ") + members.size() + " 项权限");
    }

    private static boolean isDetectionPerm(String perm) {
        try {
            for (int i = 0; i < HookInit.DETECTION_PERMISSIONS.length; i++) {
                if (HookInit.DETECTION_PERMISSIONS[i].equals(perm)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ============================================================
    //  配置备份
    // ============================================================
    public static View data(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);

        UiComponents.CardGroup card = UiComponents.cardGroup(a, "配置备份", true);
        card.addRow(entryRow(a, "导出配置", "选择要导出的配置项，生成备份文件",
        new View.OnClickListener() {
            public void onClick(View v) {
                log("config", "打开配置导出");
                PageStack s = PageStack.get();
                if (s != null) s.push(new UiPages.ExportPage(fv));
            }
        }));
        card.addRow(entryRow(a, "导入配置", "从备份文件里挑配置项覆盖当前设置",
        new View.OnClickListener() {
            public void onClick(View v) {
                log("config", "打开配置导入");
                PageStack s = PageStack.get();
                if (s != null) {
                    // 可能刚导出过新备份，缓存里的旧文件列表要作废
                    s.invalidatePage("import");
                    s.push(new UiPages.ImportPage(fv));
                }
            }
        }));
        content.addView(card.root);

        LinearLayout tip = UiKit.vbox(a);
        tip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 4), UiKit.dpI(a, 4), 0);
        TextView t1 = UiKit.text(a, "导入完成后会提示重启目标应用", 12, UiKit.textSub(a));
        tip.addView(t1);
        content.addView(tip, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return UiComponents.scroll(a, content, "scroll:data");
    }

    // ============================================================
    //  标签 1：功能
    // ============================================================
    static final String[] VPN_KEYS = {
            "interface", "cmd", "proxy", "proxy_env", "capture", "netfiles",
            "net_detect", "ssl_trust", "ssl_pinning", "ssl_cert", "ssl_cert_hide"
    };
    static final String VPN_RESTART_PREFIX = "ssl_";
    static final String[] VPN_LABELS = {
            "VPN 接口隐藏", "深度接口隐藏", "代理检测隐藏", "代理环境隐藏",
            "抓包应用隐藏", "网络文件隐藏", "网络检测监听", "SSL 信任链绕过",
            "SSL 证书绑定绕过", "SSL 证书信息隐藏", "SSL 证书特征替换"
    };

    /** 功能分类编号（每个功能分类一个标签，不再靠卡片折叠区分） */
    static final int K_LAUNCH = 0;
    static final int K_PERMISSION = 1;
    static final int K_EXIT = 2;
    static final int K_BACK = 3;
    static final int K_SELINUX = 4;
    static final int K_SHARE = 5;
    static final int K_VPN = 6;

    /** 按分类构建功能页 */
    public static boolean openTab(Activity a, TextView fv, int tabIndex) {
        if (a == null) return false;
        try {
            PageStack s = PageStack.get();
            if (s == null) {
                if (!UiPages.open(a, fv)) return false;
                s = PageStack.get();
            }
            if (s == null) return false;
            s.popToRoot();
            if (UiPages.home() == null) return false;
            UiPages.home().jumpToTab(tabIndex);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean fallbackTab(final Activity a, final TextView fv, int tabIndex) {
        if (a == null) return false;
        com.install.appinstall.xl.ui.UiKit.setFallbackMode(true);
        try {
            switch (tabIndex) {
                case 0:
                    if (ReaLog.sHookInit != null) ReaLog.sHookInit.showHideDialog(a, fv);
                    else DialogUi.showMoreFunctionsDialog(a, fv);
                    return true;
                case 1:
                    DialogUi.showLaunchInterceptConfigDialog(a, fv);
                    return true;
                case 2:
                    DialogUi.showCustomPermissionDialog(a, fv);
                    return true;
                case 3:
                    DialogUi.showExitInterceptConfigDialog(a, fv);
                    return true;
                case 4:
                    DialogUi.showForceDefaultBackConfigDialog(a, fv);
                    return true;
                case 5:
                    DialogUi.showSelinuxFakeConfigDialog(a, fv);
                    return true;
                case 6:
                    DialogUi.showShareFakeConfigDialog(a, fv);
                    return true;
                case 7:
                    DialogUi.showVpnFakeConfigDialog(a, fv);
                    return true;
                case 8:
                    DialogUi.showAntiDetectionConfigDialog(a, fv);
                    return true;
                default:
                    // 三级：都对不上就开旧管理菜单
                    DialogUi.showMoreFunctionsDialog(a, fv);
                    return true;
            }
        } catch (Throwable t) {
            try {
                ReaLog.log("system", "降级打开旧对话框失败: " + t);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    /** 页面 key → 旧对话框入口（供 PageStack 在页面构建失败时用）。 明细类半屏没有独立旧入口，统一退到旧管理菜单（三级根）。 */
    static Runnable fallbackAction(final Activity a, final TextView fv, String pageKey) {
        if (a == null) return null;
        com.install.appinstall.xl.ui.UiKit.setFallbackMode(true);
        final String k = pageKey == null ? "" : pageKey;
        // 已知的独立页面 → 各自最贴近的旧入口
        if (k.startsWith("addpkg") || k.startsWith("package-sheet")) {
            return new Runnable() {
                public void run() {
                    try {
                        if (ReaLog.sHookInit != null) ReaLog.sHookInit.showHideDialog(a, fv);
                    } catch (Throwable ignored) {
                    }
                }
            };
        }
        // 其余（设置 / 配置备份 / 明细半屏 / 主题 / 搜索）→ 三级根：旧管理菜单
        return new Runnable() {
            public void run() {
                try {
                    DialogUi.showMoreFunctionsDialog(a, fv);
                } catch (Throwable ignored) {
                }
            }
        };
    }

    public static View featurePage(final Activity a, final TextView fv, int kind) {
        LinearLayout content = UiComponents.paddedContent(a);
        UiComponents.CardGroup card;

        switch (kind) {
            case K_LAUNCH:
                card = UiComponents.cardGroup(a, "启动拦截", true);
                card.addRow(boolRow(a, "拦截第三方应用启动",
                "拦截目标应用发起的应用跳转", HookInit.launchInterceptMap, true,
                "launch_intercept"));
                card.addRow(entryRow(a, "拦截规则与包名", "白名单 / 黑名单与自动拦截规则",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack ps = PageStack.get();
                        if (ps != null) {
                            ps.push(new UiPages.AutoListPage(fv, "拦截处理列表",
                            LIST_LAUNCH));
                        }
                    }
                }));
                break;

            case K_PERMISSION:
                card = UiComponents.cardGroup(a, "权限防护", true);
                card.addRow(boolRow(a, "假装权限已授予",
                "捕获目标请求的权限并返回授权成功", HookInit.permissionFakeMap, true,
                "permission"));
                card.addRow(entryRow(a, "权限明细", "逐个权限单独控制",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack s = PageStack.get();
                        if (s != null) s.push(new UiPages.PermissionPage(fv));
                    }
                }));
                break;

            case K_EXIT:
                card = UiComponents.cardGroup(a, "拦截退出", true);
                // 自动开启是反向存储：userDisabledAutoBlockMap 为 true 表示已关闭
                Boolean autoV = HookInit.userDisabledAutoBlockMap.get(app());
                boolean autoOn = autoV == null || !autoV.booleanValue();
                card.addRow(sw(a, "自动开启",
                "捕获到新包名时，自动为它启用普通拦截", autoOn,
                new UiComponents.OnChecked() {
                    public void onChecked(boolean checked) {
                        HookInit.userDisabledAutoBlockMap.put(app(),
                                Boolean.valueOf(!checked));
                        save(a);
                        log("exit_intercept", "自动开启新包名拦截 -> "
                                + (checked ? "开启" : "关闭"));
                    }
                }));
                // 普通拦截：勾上时联动开启「异常吞噬」
                final UiComponents.MiniSwitch[] crashSwRef = new UiComponents.MiniSwitch[1];
                final Boolean blockV = HookInit.blockExitMap.get(app());
                boolean blockOn = blockV != null && blockV.booleanValue();
                LinearLayout blockRow = sw(a, "普通拦截",
                "拦截应用常用的主流退出方案", blockOn,
                new UiComponents.OnChecked() {
                    public void onChecked(boolean checked) {
                        HookInit.blockExitMap.put(app(), Boolean.valueOf(checked));
                        if (checked) {
                            Boolean cv = HookInit.crashProtectEnabledMap.get(app());
                            boolean crashNow = cv == null || cv.booleanValue();
                            if (!crashNow) {
                                HookInit.crashProtectEnabledMap.put(app(),
                                        Boolean.TRUE);
                                try {
                                    HookInit hi = ReaLog.sHookInit;
                                    if (hi != null) hi.applyCrashProtect(true, a);
                                } catch (Throwable ignored) {
                                }
                                if (crashSwRef[0] != null) {
                                    crashSwRef[0].setCheckedSilently(true);
                                }
                                log("exit_intercept", "普通拦截 -> 开启（联动开启异常吞噬）");
                                toast(a, "已同时开启「异常吞噬」");
                            } else {
                                log("exit_intercept", "普通拦截 -> 开启");
                            }
                        } else {
                            log("exit_intercept", "普通拦截 -> 关闭");
                        }
                        save(a);
                    }
                });
                card.addRow(blockRow);
                final Boolean crashV = HookInit.crashProtectEnabledMap.get(app());
                boolean crashOn = crashV == null || crashV.booleanValue();
                LinearLayout crashRow = sw(a, "异常吞噬",
                "全局捕获 Java 异常，尽量把异常数据吞掉", crashOn,
                new UiComponents.OnChecked() {
                    public void onChecked(boolean checked) {
                        HookInit.crashProtectEnabledMap.put(app(),
                                Boolean.valueOf(checked));
                        save(a);
                        log("exit_intercept", "异常吞噬 -> "
                                + (checked ? "开启" : "关闭"));
                        try {
                            HookInit hi = ReaLog.sHookInit;
                            if (hi != null) hi.applyCrashProtect(checked, a);
                        } catch (Throwable ignored) {
                        }
                    }
                });
                int crashLast = crashRow.getChildCount() - 1;
                if (crashLast >= 0 && crashRow.getChildAt(crashLast)
                                instanceof UiComponents.MiniSwitch) {
                    crashSwRef[0] = (UiComponents.MiniSwitch) crashRow.getChildAt(crashLast);
                }
                card.addRow(crashRow);
                card.addRow(boolRow(a, "超强拦截",
                "在普通拦截之上追加补丁，更激进，可能影响目标正常退出",
                HookInit.superBlockExitMap, false, "exit_intercept"));
                card.addRow(entryRowDanger(a, "处理列表", "拦截退出时识别到的包名模式与判断记录",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack ps = PageStack.get();
                        if (ps != null) {
                            ps.push(new UiPages.AutoListPage(fv, "退出拦截处理列表",
                            LIST_EXIT));
                        }
                    }
                }));
                break;

            case K_BACK:
                card = UiComponents.cardGroup(a, "返回键", true);
                card.addRow(boolRow(a, "替换返回键逻辑",
                "接管目标应用的返回键行为", HookInit.forceDefaultBackMap, false,
                "back_key"));
                break;

            case K_SELINUX:
                {
                    card = UiComponents.cardGroup(a, "伪装强制", true);
                    boolean se = true;
                    Object sev = HookInit.selinuxFakeMap.get(app());
                    if (sev instanceof Boolean) se = ((Boolean) sev).booleanValue();
                    String realSe = "";
                    try {
                        realSe = Selinuxhook.getRealSelinuxMode();
                    } catch (Throwable ignored) {
                    }
                    if (UiKit.isBlank(realSe)) realSe = "读取失败";
                    card.addRow(valueRowHi(a, "本机真实状态", realSe));
                    card.addRow(sw(a, "SELinux 伪装为 Enforcing",
                    "让目标检测到状态为 Enforcing（需重启目标应用）", se,
                    new UiComponents.OnChecked() {
                        public void onChecked(boolean checked) {
                            HookInit.selinuxFakeMap.put(app(), Boolean.valueOf(checked));
                            save(a);
                            log("selinux", "SELinux 伪装 Enforcing -> "
                                    + (checked ? "开启" : "关闭"));
                            // 判定读的是 Selinuxhook 内部的静态 enabledMap，只写 Map 不生效
                            try {
                                Selinuxhook.setEnabled(app(), checked);
                            } catch (Throwable ignored) {
                            }
                            UiComponents.restartHint(a);
                        }
                    }));
                    break;
                }

            case K_SHARE:
                card = UiComponents.cardGroup(a, "假装分享", true);
                for (int i = 0; i < ShareHook.UI_KEYS.length - 1; i++) {
                    card.addRow(detailRow(a, ShareHook.UI_LABELS[i],
                    "该平台分享直接回调成功",
                    HookInit.shareFakeDetailMap, ShareHook.UI_KEYS[i], true,
                    "share_fake"));
                }
                boolean aggOn = true;
                try {
                    Map<String, Boolean> sm = HookInit.shareFakeDetailMap.get(app());
                    if (sm != null && sm.get("aggressive") != null) {
                        aggOn = sm.get("aggressive").booleanValue();
                    }
                } catch (Throwable ignored) {
                }
                final String aggDesc = "对所有应用生效(含系统层分享),可能误拦「用其他应用打开」";
                LinearLayout aggRow = sw(a, ShareHook.UI_LABELS[ShareHook.UI_KEYS.length - 1],
                aggDesc, aggOn,
                new UiComponents.OnChecked() {
                    public void onChecked(boolean checked) {
                        if (checked) {
                            toast(a, "「激进」会连系统层分享一起拦\n可能误拦「用其他应用打开」");
                        }
                        try {
                            Map<String, Boolean> m = HookInit.shareFakeDetailMap.get(app());
                            if (m == null) {
                                m = new HashMap<String, Boolean>();
                                HookInit.shareFakeDetailMap.put(app(), m);
                            }
                            m.put("aggressive", Boolean.valueOf(checked));
                        } catch (Throwable ignored) {
                        }
                        save(a);
                        log("share_fake", "激进模式 -> " + (checked ? "开启" : "关闭"));
                    }
                });
                card.addRow(aggRow);
                card.addRow(entryRowDanger(a, "重置异常熔断计数",
                "回调异常达到上限会临时停用对应平台，这里手动清零",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        java.util.Map<String, Integer> snap = null;
                        try {
                            snap = ShareHook.errorCountSnapshot();
                        } catch (Throwable ignored) {
                        }
                        if (snap == null || snap.isEmpty()) {
                            toast(a, "当前无异常");
                            return;
                        }
                        try {
                            ShareHook.resetAllErrorCount();
                        } catch (Throwable ignored) {
                        }
                        StringBuilder sb = new StringBuilder("已重置");
                        boolean first = true;
                        for (java.util.Map.Entry<String, Integer> en : snap.entrySet()) {
                            if (!first) sb.append("、");
                            first = false;
                            sb.append(ShareHook.displayName(en.getKey()))
                                    .append("平台(").append(en.getValue())
                                    .append("次)");
                        }
                        log("share_fake", "重置分享异常熔断计数 " + sb);
                        toast(a, sb.toString());
                    }
                }));
                break;

            case K_VPN:
                card = UiComponents.cardGroup(a, "网络代理", true);
                final String vpnApp = app();
                for (int i = 0; i < VPN_KEYS.length; i++) {
                    final String k = VPN_KEYS[i];
                    boolean on = true;
                    try {
                        Map<String, Boolean> m = HookInit.vpnFakeDetailMap.get(vpnApp);
                        if (m != null && m.get(k) != null) on = m.get(k).booleanValue();
                    } catch (Throwable ignored) {
                    }
                    final boolean needRestart = k != null && k.startsWith(VPN_RESTART_PREFIX);
                    String vpnLabel = VPN_LABELS[i];
                    if (vpnLabel == null || vpnLabel.trim().length() == 0) vpnLabel = k;
                    card.addRow(sw(a, vpnLabel, null, on,
                    new UiComponents.OnChecked() {
                        public void onChecked(boolean checked) {
                            try {
                                Map<String, Boolean> m = HookInit.vpnFakeDetailMap.get(vpnApp);
                                if (m == null) {
                                    m = new HashMap<String, Boolean>();
                                    HookInit.vpnFakeDetailMap.put(vpnApp, m);
                                }
                                m.put(k, Boolean.valueOf(checked));
                            } catch (Throwable ignored) {
                            }
                            save(a);
                            log("vpn", "网络伪装项 " + k + " -> "
                                    + (checked ? "开启" : "关闭"));
                            if (needRestart && checked) UiComponents.restartHint(a);
                        }
                    }));
                }
                card.addRow(entryRowDanger(a, "处理列表", "查看网络伪装相关的拦截记录",
                new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            ReaLog.showCombinedDialog(a);
                        } catch (Throwable t) {
                            toast(a, "打开日志失败");
                        }
                    }
                }));
                break;

            default:
                card = UiComponents.cardGroup(a, "功能", true);
                break;
        }

        content.addView(card.root);
        return UiComponents.scroll(a, content, "scroll:feat" + kind);
    }

    // ============================================================
    //  标签 2：痕迹
    // ============================================================
    static long sMergeWarn;
    static final String[] ANTI_KEYS = {
            "class", "file", "pm", "proc", "cmd", "stacktrace", "root", "adb", "usb", "dev"
    };
    static final String[] ANTI_LABELS = {
            "框架隐藏", "文件隐藏", "服务隐藏", "信息隐藏", "命令隐藏",
            "堆栈隐藏", "Root 隐藏", "ADB隐藏", "USB 隐藏", "开发者选项隐藏"
    };
    static final String[] ANTI_DESCS = {
            "隐藏 Xposed/LSPosed 的类与加载器特征",
            "隐藏框架相关文件与目录",
            "隐藏模块自身的包名与服务",
            "伪造 Build/设备信息中的框架痕迹",
            "过滤 ps / cat 等命令输出中的关键字",
            "裁剪异常堆栈里的框架调用帧",
            "隐藏 su 与 root 相关痕迹",
            "隐藏 ADB 调试开关与连接状态",
            "隐藏 USB 调试与连接状态",
            "隐藏开发者选项相关设置项"
    };

    /** 危险操作的二次确认按钮：第一次点只把文案改成「再点一次确认」，3 秒内没再点就恢复。*/
    static void markRowDanger(LinearLayout row, Activity a) {
        if (row == null) return;
        View hit = row.findViewWithTag("row_title");
        if (hit instanceof TextView) {
            ((TextView) hit).setTextColor(UiKit.danger(a));
        }
    }

    /** 标红标题的 entryRow*/
    static LinearLayout entryRowDanger(Activity a, String title, String desc,
            View.OnClickListener l) {
        LinearLayout row = entryRow(a, title, desc, l);
        markRowDanger(row, a);
        return row;
    }

    static TextView dangerConfirmButton(Activity a, String label, final Runnable action) {
        return confirmButton(a, label, action, false);
    }

    /** 橙红实底胶囊版的两步确认按钮（批量清空类危险操作） */
    static TextView warnConfirmButton(Activity a, String label, final Runnable action) {
        return confirmButton(a, label, action, true);
    }

    private static TextView confirmButton(Activity a, String label, final Runnable action,
            final boolean warn) {
        final String text = label;
        final TextView btn = warn
                ? UiComponents.warnButton(a, text, null)
                : UiComponents.dangerButton(a, text, null);
        btn.setOnClickListener(new View.OnClickListener() {
            private boolean armed;
            private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

            public void onClick(View v) {
                if (!armed) {
                    armed = true;
                    btn.setText("再点一次确认");
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            if (armed) {
                                armed = false;
                                btn.setText(text);
                            }
                        }
                    }, UiKit.TWO_STEP_ARM_MS);
                    return;
                }
                armed = false;
                btn.setText(text);
                action.run();
            }
        });
        return btn;
    }

    /**
     * 把痕迹隐藏的开关立刻同步到运行时。
     */
    static void applyAntiToRuntime(String key, boolean on) {
        try {
            if ("class".equals(key)) AntiDetection.setClassEnabled(on);
            else if ("file".equals(key)) AntiDetection.setFileEnabled(on);
            else if ("pm".equals(key)) AntiDetection.setPmEnabled(on);
            else if ("proc".equals(key)) AntiDetection.setProcEnabled(on);
            else if ("cmd".equals(key)) AntiDetection.setCmdEnabled(on);
            else if ("stacktrace".equals(key)) AntiDetection.setStacktraceEnabled(on);
            else if ("root".equals(key)) AntiDetection.setRootEnabled(on);
            else if ("adb".equals(key)) AntiDetection.setAdbEnabled(on);
            else if ("dev".equals(key)) AntiDetection.setDevEnabled(on);
            else if ("usb".equals(key)) AntiDetection.setUsbEnabled(on);
            // 清挂载记录：关掉再打开时能让子项重新挂载，不必重启目标应用
            AntiDetection.reset();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 痕迹隐藏的子项 key → ReaLog 里已注册的分类。 
     */
    static String antiCategory(String key) {
        if ("class".equals(key) || "file".equals(key) || "pm".equals(key)
                || "proc".equals(key) || "stacktrace".equals(key) || "root".equals(key)
                || "adb".equals(key) || "dev".equals(key)) {
            return key;
        }
        return "anti_detection";
    }

    public static View anti(final Activity a) {
        LinearLayout content = UiComponents.paddedContent(a);

        UiComponents.CardGroup card = UiComponents.cardGroup(a, "痕迹隐藏", true);
        final String antiApp = app();
        for (int i = 0; i < ANTI_KEYS.length; i++) {
            final String key = ANTI_KEYS[i];
            if (ANTI_LABELS[i] == null || ANTI_LABELS[i].length() == 0) continue;
            boolean on = true;
            try {
                Map<String, Boolean> m = HookInit.antiDetectionDetailMap.get(antiApp);
                if (m != null && m.get(key) != null) on = m.get(key).booleanValue();
            } catch (Throwable ignored) {
            }
            String antiDesc = ANTI_DESCS[i];
            if (antiDesc == null || antiDesc.trim().length() == 0) antiDesc = "该功能项的开关";
            final String antiLabel = ANTI_LABELS[i];
            final String antiCat = antiCategory(key);
            card.addRow(sw(a, antiLabel, antiDesc, on,
            new UiComponents.OnChecked() {
                public void onChecked(boolean checked) {
                    try {
                        Map<String, Boolean> m = HookInit.antiDetectionDetailMap.get(antiApp);
                        if (m == null) {
                            m = new HashMap<String, Boolean>();
                            HookInit.antiDetectionDetailMap.put(antiApp, m);
                        }
                        m.put(key, Boolean.valueOf(checked));
                    } catch (Throwable ignored) {
                    }
                    save(a);
                    log(antiCat, antiLabel + " -> " + (checked ? "开启" : "关闭"));
                    // 立刻同步运行时，否则只是「界面关了」而实际仍生效
                    applyAntiToRuntime(key, checked);
                    UiComponents.restartHint(a);
                }
            }));
        }
        card.addRow(entryRowDanger(a, "处理列表", "查看痕迹隐藏相关的拦截记录",
        new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    ReaLog.showCombinedDialog(a);
                } catch (Throwable t) {
                    toast(a, "打开日志失败");
                }
            }
        }));
        content.addView(card.root);

        LinearLayout tip = UiKit.vbox(a);
        tip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 4), UiKit.dpI(a, 4), 0);
        TextView t1 = UiKit.text(a, "修改后需重启目标应用才能完全生效", 12,
                UiKit.withAlpha(UiKit.textSub(a), 0xE0));
        tip.addView(t1);
        TextView t2 = UiKit.text(a, "若出现异常，模块会自动禁用对应功能项", 12,
                UiKit.withAlpha(UiKit.textSub(a), 0xC0));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = UiKit.dpI(a, 6);
        t2.setLayoutParams(tp);
        tip.addView(t2);
        content.addView(tip, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return UiComponents.scroll(a, content, "scroll:anti");
    }

    // ============================================================
    //  标签：其他功能（屏幕限制解除/对话框控制/震动控制）
    // ============================================================
    public static View other(final Activity a) {
        LinearLayout content = UiComponents.paddedContent(a);

        UiComponents.CardGroup screen = UiComponents.cardGroup(a, "屏幕限制解除", true);
        screen.addRow(boolRow(a, "屏幕限制解除",
        "解除目标应用的截屏和录屏限制", HookInit.hookWindowFlagsMap, true,
        "more", true));
        content.addView(screen.root);

        UiComponents.CardGroup dialog = UiComponents.cardGroup(a, "对话框控制", true);
        dialog.addRow(boolRow(a, "对话框强制取消",
        "允许取消不可取消的对话框", HookInit.hookDialogCancelableMap, true,
        "more", true));
        dialog.addRow(boolRow(a, "对话框强制取消(防界面关闭)",
        "对话框被取消时阻止界面一起关闭", HookInit.hookAntiCancelFinishMap, true,
        "more", true));
        content.addView(dialog.root);

        UiComponents.CardGroup vib = UiComponents.cardGroup(a, "震动控制", true);
        String targetApp = app();
        vib.addRow(boolRow(a, "允许震动",
        "控制宿主应用 " + (targetApp == null || targetApp.length() == 0 ? "未选择" : targetApp) + " 与模块界面的震动",
        HookInit.hookVibratorMap, true,
        "more"));
        content.addView(vib.root);

        LinearLayout tip = UiKit.vbox(a);
        tip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 4), UiKit.dpI(a, 4), 0);
        TextView t1 = UiKit.text(a, "修改后需重启目标应用才能完全生效", 12,
                UiKit.withAlpha(UiKit.textSub(a), 0xE0));
        tip.addView(t1);
        TextView t2 = UiKit.text(a, "关闭后重启目标应用即不再挂载对应功能", 12,
                UiKit.withAlpha(UiKit.textSub(a), 0xC0));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.topMargin = UiKit.dpI(a, 6);
        t2.setLayoutParams(tp);
        tip.addView(t2);
        content.addView(tip, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return UiComponents.scroll(a, content, "scroll:other");
    }

    // ============================================================
    //  标签：包名列表
    // ============================================================
    public static View packages(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);
        final String app = app();
        HookInit h = ReaLog.sHookInit;
        Boolean st = HookInit.installStatusMap.get(app);
        final boolean status = st != null ? st : true;
        UiComponents.CardGroup state = UiComponents.cardGroup(a, "当前状态", true);

        // 状态行：标题与描述跟随开关实时变化，关闭时标红
        final LinearLayout stRow = UiKit.hbox(a);
        stRow.setMinimumHeight(UiKit.dpI(a, 56));
        int pdh = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        stRow.setPadding(pdh, UiKit.dpI(a, 10), pdh, UiKit.dpI(a, 10));
        stRow.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));

        LinearLayout col = UiKit.vbox(a);
        final TextView stTitle = UiKit.text(a,
                status ? "[全局]假装为已安装" : "[全局]假装为未安装", 14,
                status ? UiKit.textMain(a) : 0xFFE53935);
        final TextView stDesc = UiKit.text(a,
                status ? "目标应用会认为该包 已安装" : "目标应用会认为该包 未安装",
                11, status ? UiKit.textSub(a) : 0xFFE53935);
        col.addView(stTitle);
        col.addView(stDesc);
        stRow.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final UiComponents.MiniSwitch sw = new UiComponents.MiniSwitch(a);
        sw.setCheckedSilently(status);
        sw.setOnCheckedListener(new UiComponents.MiniSwitch.OnCheckedListener() {
            public void onCheckedChanged(UiComponents.MiniSwitch v, boolean c) {
                HookInit.installStatusMap.put(app, Boolean.valueOf(c));
                save(a);
                stTitle.setText(c ? "[全局]假装为已安装" : "[全局]假装为未安装");
                stTitle.setTextColor(c ? UiKit.textMain(a) : 0xFFE53935);
                stDesc.setText(c ? "目标应用会认为该包 已安装" : "目标应用会认为该包 未安装");
                stDesc.setTextColor(c ? UiKit.textSub(a) : 0xFFE53935);
                if (UiPages.home() != null) UiPages.home().refreshPackages();
            }
        });
        LinearLayout.LayoutParams swp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        swp.leftMargin = UiKit.dpI(a, 10);
        stRow.addView(sw, swp);
        state.addRow(stRow);

        state.addRow(entryRow(a, "添加包名", "手动输入包名并设定状态",
        new View.OnClickListener() {
            public void onClick(View v) {
                PageStack s = PageStack.get();
                if (s != null) s.push(new UiPages.AddPackagePage(fv));
            }
        }));
        content.addView(state.root);

        // ---------------- 第 4 条：包名搜索 ----------------
        HookInit.DetectedPackages dp = null;
        if (h != null) {
            try {
                dp = h.analyzeDetectedPackages();
            } catch (Throwable ignored) {
            }
        }
        List<String> userList = HookInit.userDefinedPackagesMap.get(app);
        final List<String> showUser = new ArrayList<String>();
        if (userList != null) {
            for (int i = 0; i < userList.size(); i++) {
                String p = userList.get(i);
                if (p != null && p.length() > 0) showUser.add(p);
            }
        }
        int pkgTotalCalc = showUser.size();
        if (dp != null) {
            if (dp.installedPackages != null) pkgTotalCalc += dp.installedPackages.size();
            if (dp.notInstalledPackages != null) pkgTotalCalc += dp.notInstalledPackages.size();
        }
        final int pkgTotal = pkgTotalCalc;
        // 搜索与合并要反复遍历这些集合，先收集好
        final List<View> searchRows = new ArrayList<View>();
        final List<ViewGroup> cardBoxes = new ArrayList<ViewGroup>();
        final List<View> cardTitles = new ArrayList<View>();
        final List<View> allDividers = new ArrayList<View>();
        final android.widget.EditText searchBox;
        if (pkgTotal > 0) {
            searchBox = UiComponents.searchBox(a, "搜索 捕获/添加 的包名", null);
            LinearLayout.LayoutParams spx = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            spx.topMargin = UiKit.dpI(a, 4);
            spx.bottomMargin = UiKit.dpI(a, 8);
            content.addView(searchBox, spx);
        } else {
            searchBox = null;
        }
        final LinearLayout[] foldTip = new LinearLayout[1];
        final String pkgFoldKey = "fold:pkg-detect";
        final boolean[] merged = {UiState.fold(pkgFoldKey)};
        final View.OnClickListener[] mergeClickRef = new View.OnClickListener[1];

        // ---- 捕获到的包名
        final List<String> detInstalled = new ArrayList<String>();
        final List<String> detNotInstalled = new ArrayList<String>();
        if (dp != null) {
            if (dp.installedPackages != null) {
                for (int i = 0; i < dp.installedPackages.size(); i++) {
                    String p = dp.installedPackages.get(i);
                    if (p != null && p.length() > 0 && !showUser.contains(p)) detInstalled.add(p);
                }
            }
            if (dp.notInstalledPackages != null) {
                for (int i = 0; i < dp.notInstalledPackages.size(); i++) {
                    String p = dp.notInstalledPackages.get(i);
                    if (p != null && p.length() > 0 && !showUser.contains(p))
                        detNotInstalled.add(p);
                }
            }
        }
        final int detTotal = detInstalled.size() + detNotInstalled.size();

        if (detTotal > 0) {
            final BatchList bl = new BatchList(a,
            new String[]{"移入手动添加"},
            capturedBatchListener(a, app));
            final TextView[] mergeDetBtn = new TextView[1];
            mergeClickRef[0] = new View.OnClickListener() {
                public void onClick(View v) {
                    merged[0] = !merged[0];
                    UiState.setFold(pkgFoldKey, merged[0]);
                    applyFoldToCard(bl.card, merged[0], detTotal, foldTip[0],
                    mergeDetBtn[0], a);
                }
            };
            final View.OnClickListener refreshClick = new View.OnClickListener() {
                public void onClick(View v) {
                    // 与下拉刷新同一序列
                    if (UiPages.home() != null) UiPages.home().triggerRefresh();
                }
            };
            final View.OnClickListener clearClick = new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.push(new UiPages.ClearConfigSheetPage(fv));
                }
            };
            TextView[] acts = new TextView[4];
            View detTitle = sectionTitleWithActions(a,
            "捕获到的包名（" + detTotal + "）",
            new String[]{"批量", "收起", "刷新", "清理"},
            new View.OnClickListener[]{null, mergeClickRef[0], refreshClick, clearClick},
            acts);
            mergeDetBtn[0] = acts[1];
            bl.bindEntry(acts[0]);
            content.addView(detTitle);
            cardTitles.add(detTitle);

            // 卡内：分段小标题（带 subhead tag，折叠时一起收起）+ 行
            if (!detInstalled.isEmpty()) {
                addCaptureHead(a, bl.card, "检测为已安装（" + detInstalled.size() + "）");
                for (int i = 0; i < detInstalled.size(); i++) {
                    String pkg = detInstalled.get(i);
                    LinearLayout row = capturedPkgRow(a, pkg, "已安装");
                    searchRows.add(row);
                    bl.addRow(pkg, row);
                }
            }
            if (!detNotInstalled.isEmpty()) {
                addCaptureHead(a, bl.card, "检测为未安装（" + detNotInstalled.size() + "）");
                for (int i = 0; i < detNotInstalled.size(); i++) {
                    String pkg = detNotInstalled.get(i);
                    LinearLayout row = capturedPkgRow(a, pkg, "未安装");
                    searchRows.add(row);
                    bl.addRow(pkg, row);
                }
            }
            content.addView(bl.toolbar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(bl.card);
            cardBoxes.add(bl.card);

            // 合并后的尾部提示
            foldTip[0] = UiKit.hbox(a);
            TextView ftText = UiKit.text(a, "", 11, UiKit.textSub(a));
            ftText.setPadding(UiKit.dpI(a, 12), UiKit.dpI(a, 6), UiKit.dpI(a, 12), 0);
            foldTip[0].addView(ftText);
            foldTip[0].setVisibility(View.GONE);
            content.addView(foldTip[0]);
            if (merged[0]) {
                applyFoldToCard(bl.card, true, detTotal, foldTip[0], mergeDetBtn[0], a);
            }
            content.addView(UiKit.gap(a, 6));
        }

        // 手动添加：描述显示各自的固定状态
        if (!showUser.isEmpty()) {
            final BatchList bl = new BatchList(a,
            new String[]{"跟随全局", "固定已安装", "固定未安装"},
            packageBatchListener(a, app));
            View tv4 = sectionTitleWithBatch(a, "手动添加（" + showUser.size() + "）", bl);
            content.addView(tv4);
            cardTitles.add(tv4);
            cardBoxes.add(bl.card);
            for (int i = 0; i < showUser.size(); i++) {
                if (i > 0) {
                    View dv = UiComponents.rowDivider(a);
                    allDividers.add(dv);
                    bl.card.addView(dv);
                }
                String pkg = showUser.get(i);
                LinearLayout row = batchPkgRow(a, app, pkg);
                searchRows.add(row);
                bl.addRow(pkg, row);
            }
            bl.addTo(content);
            content.addView(UiKit.gap(a, 4));
        }

        if (content.getChildCount() <= 1) {
            LinearLayout tip = UiKit.vbox(a);
            tip.setPadding(UiKit.dpI(a, 8), UiKit.dpI(a, 16), UiKit.dpI(a, 8), 0);
            TextView t = UiKit.text(a,
                    "还没有捕获到任何包名\n在目标应用里触发一次包名检测后回来查看",
                    13, UiKit.textSub(a));
            t.setGravity(Gravity.CENTER);
            tip.addView(t);
            content.addView(tip);
        }

        // ---------------- 搜索：过滤 + 高亮----------------
        if (searchBox != null) {
            searchBox.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

                public void onTextChanged(CharSequence s, int st, int b, int c) {}

                public void afterTextChanged(android.text.Editable e) {
                    String q = e == null ? "" : e.toString().trim().toLowerCase();
                    boolean empty = q.length() == 0;
                    // 搜索时先退出合并态，否则命中的行藏在「前 3 行」之外看不到
                    if (!empty && merged[0] && mergeClickRef[0] != null) {
                        mergeClickRef[0].onClick(null);
                    }
                    for (int i = 0; i < allDividers.size(); i++) {
                        View d = allDividers.get(i);
                        if (d != null) d.setVisibility(empty ? View.VISIBLE : View.GONE);
                    }
                    for (int i = 0; i < searchRows.size(); i++) {
                        View row = searchRows.get(i);
                        if (row == null) continue;
                        Object cd = row.getContentDescription();
                        String pkg = cd instanceof String ? (String) cd : "";
                        boolean hit = empty || pkg.toLowerCase().contains(q);
                        row.setVisibility(hit ? View.VISIBLE : View.GONE);
                        // 命中行整行铺淡主题色底，与权限列表的搜索高亮一致
                        row.setBackground(hit && !empty
                                ? UiKit.roundRect(a, UiKit.accentSoft(a), 10)
                                : null);
                    }
                    // 整张卡一行都没命中就把卡和它的标题一起收起来
                    for (int i = 0; i < cardBoxes.size(); i++) {
                        // 卡本身是 ViewGroup（LinearLayout），统计它下面有没有命中的行
                        ViewGroup card = cardBoxes.get(i);
                        if (card == null) continue;
                        boolean any = false;
                        for (int j = 0; j < card.getChildCount(); j++) {
                            View ch = card.getChildAt(j);
                            if (ch != null && ch.getVisibility() == View.VISIBLE
                                    && searchRows.contains(ch)) {
                                any = true;
                                break;
                            }
                        }
                        card.setVisibility(any || empty ? View.VISIBLE : View.GONE);
                        if (i < cardTitles.size()) {
                            View ti = cardTitles.get(i);
                            if (ti != null) {
                                ti.setVisibility(any || empty ? View.VISIBLE : View.GONE);
                            }
                        }
                    }
                }
            });
        }

        return UiComponents.scroll(a, content, "scroll:pkg");
    }

    // ============================================================
    //  添加包名页（第 6 条：输入框 + 状态设置 + 添加按钮）
    // ============================================================
    public static View addPackage(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);
        final String app = app();
        final String[] mode = {"follow"};

        // 类型：添加包名有「[伪造] / [排除]」选择器
        final boolean[] excluded = {false};
        final boolean[] fakeOn = {true};
        final UiComponents.MiniSwitch[] tms = new UiComponents.MiniSwitch[2];
        final View[] modeCardRef = new View[1];
        final View[] inputLabelRef = new View[1];
        final View[] addBtnRef = new View[1];
        final Runnable[] syncInput = new Runnable[1];
        UiComponents.CardGroup tcard = UiComponents.cardGroup(a, "类型", true);
        LinearLayout t0 = UiComponents.switchRow(a, "伪造包名",
                "让目标应用认为这个包已安装（状态见下）", true,
                new UiComponents.OnChecked() {
                    public void onChecked(boolean c) {
                        // 两档互斥，但允许两个都关：都关时整页只读，输入区直接收起来
                        fakeOn[0] = c;
                        if (c) {
                            excluded[0] = false;
                            if (tms[1] != null) tms[1].setCheckedSilently(false);
                        }
                        if (syncInput[0] != null) syncInput[0].run();
                    }
                });
        int t0last = t0.getChildCount() - 1;
        if (t0last >= 0 && t0.getChildAt(t0last) instanceof UiComponents.MiniSwitch) {
            tms[0] = (UiComponents.MiniSwitch) t0.getChildAt(t0last);
        }
        tcard.addRow(t0);
        LinearLayout t1 = UiComponents.switchRow(a, "排除包名",
                "不伪造这个包，目标应用拿到真实检测结果", false,
                new UiComponents.OnChecked() {
                    public void onChecked(boolean c) {
                        excluded[0] = c;
                        if (c) {
                            fakeOn[0] = false;
                            if (tms[0] != null) tms[0].setCheckedSilently(false);
                        }
                        if (syncInput[0] != null) syncInput[0].run();
                    }
                });
        int t1last = t1.getChildCount() - 1;
        if (t1last >= 0 && t1.getChildAt(t1last) instanceof UiComponents.MiniSwitch) {
            tms[1] = (UiComponents.MiniSwitch) t1.getChildAt(t1last);
        }
        tcard.addRow(t1);
        content.addView(tcard.root);

        TextView inputLabel = UiKit.text(a, "输入包名（支持多行 / 逗号批量）", 13, UiKit.textSub(a));
        inputLabelRef[0] = inputLabel;
        content.addView(inputLabel);
        final android.widget.EditText input = new android.widget.EditText(a);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHint("每行一个，或用逗号分隔\n例如 com.example.app");
        input.setHintTextColor(UiKit.textSub(a));
        input.setTextColor(UiKit.textMain(a));
        input.setTextSize(14);
        input.setBackground(UiKit.roundRect(a, UiKit.card(a), 12));
        int ipadH = UiKit.dpI(a, 14);
        int ipadV = UiKit.dpI(a, 10);
        input.setPadding(ipadH, ipadV, ipadH, ipadV);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.topMargin = UiKit.dpI(a, 6);
        ip.bottomMargin = UiKit.dpI(a, 12);
        content.addView(input, ip);

        final String[] keys = {"follow", "installed", "not_installed"};
        final String[] labels = {"跟随全局", "固定已安装", "固定未安装"};
        final String[] descs = {"使用总开关设置的状态", "无论全局如何都视为已安装",
                "无论全局如何都视为未安装"
        };
        final UiComponents.MiniSwitch[] mss = new UiComponents.MiniSwitch[3];

        UiComponents.CardGroup mcard = UiComponents.cardGroup(a, "该包名的状态", true);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout row = UiComponents.switchRow(a, labels[i], descs[i], i == 0,
                    new UiComponents.OnChecked() {
                        public void onChecked(boolean c) {
                            if (!c) {
                                if (mss[idx] != null) mss[idx].setCheckedSilently(true);
                                return;
                            }
                            mode[0] = keys[idx];
                            for (int j = 0; j < 3; j++) {
                                if (j != idx && mss[j] != null) {
                                    mss[j].setCheckedSilently(false);
                                }
                            }
                        }
                    });
            int last = row.getChildCount() - 1;
            if (last >= 0 && row.getChildAt(last) instanceof UiComponents.MiniSwitch) {
                mss[i] = (UiComponents.MiniSwitch) row.getChildAt(last);
            }
            mcard.addRow(row);
        }
        modeCardRef[0] = mcard.root;
        content.addView(mcard.root);

        View add = UiComponents.primaryButton(a, "添加到列表", new View.OnClickListener() {
            public void onClick(View v) {
                String raw = input.getText() == null ? "" : input.getText().toString();
                // 换行 / 中英文逗号 / 分号分隔
                String[] parts = raw.split("[\\s,，;；、]+");
                int ok = 0;
                int bad = 0;
                int same = 0;
                for (int i = 0; i < parts.length; i++) {
                    String pkg = parts[i] == null ? "" : parts[i].trim();
                    if (pkg.length() == 0) continue;
                    if (pkg.length() < 3 || pkg.indexOf('.') < 0) {
                        bad++;
                        continue;
                    }
                    boolean done = false;
                    boolean dup = false;
                    if (excluded[0]) {
                        if (isExcluded(app, pkg)) dup = true;
                        else done = addExcludedPackage(app, pkg);
                    } else {
                        List<String> ul = HookInit.userDefinedPackagesMap.get(app);
                        if (ul != null && ul.contains(pkg)) dup = true;
                        else done = addPackageToConfig(app, pkg, mode[0]);
                    }
                    if (dup) same++;
                    else if (done) ok++;
                    else bad++;
                }
                if (ok == 0) {
                    if (same > 0) {
                        toast(a, "这 " + same + " 个包名已经在列表里了，没有新增");
                        return;
                    }
                    toast(a, bad > 0 ? "请输入完整包名，例如 com.example.app" : "没有可添加的包名");
                    return;
                }
                save(a);
                input.setText("");
                String msg = "已添加 " + ok + " 个" + (excluded[0] ? "排除" : "伪造") + "包名";
                if (same > 0) msg += "，已存在跳过 " + same + " 个";
                if (bad > 0) msg += "，格式不对跳过 " + bad + " 个";
                toast(a, msg);
                UiPages.HomePage home = UiPages.home();
                if (home != null) home.refreshPackages();
                //新添加后重建页面
                PageStack ps = PageStack.get();
                if (ps != null) ps.refreshTopContent();
            }
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = UiKit.dpI(a, 10);
        content.addView(add, ap);
        addBtnRef[0] = add;
        // 类型两个都关掉时：输入区、状态卡、添加按钮一起收起来
        syncInput[0] = new Runnable() {
            public void run() {
                boolean any = fakeOn[0] || excluded[0];
                int vis = any ? View.VISIBLE : View.GONE;
                if (inputLabelRef[0] != null) inputLabelRef[0].setVisibility(vis);
                input.setVisibility(vis);
                if (addBtnRef[0] != null) addBtnRef[0].setVisibility(vis);
                if (modeCardRef[0] != null) {
                    modeCardRef[0].setVisibility((any && !excluded[0]) ? View.VISIBLE : View.GONE);
                }
            }
        };
        syncInput[0].run();

        List<String> user = HookInit.userDefinedPackagesMap.get(app);
        if (user != null && !user.isEmpty()) {
            // 点击切状态、长按或点「批量」多选
            final BatchList bl = new BatchList(a,
            new String[]{"跟随全局", "固定已安装", "固定未安装"},
            packageBatchListener(a, app));
            content.addView(sectionTitleWithBatch(a, "已添加的包名（" + user.size() + "）", bl));
            for (int i = 0; i < user.size(); i++) {
                String pkg = user.get(i);
                if (pkg == null || pkg.length() == 0) continue;
                if (bl.card.getChildCount() > 0) {
                    bl.card.addView(UiComponents.rowDivider(a));
                }
                bl.addRow(pkg, batchPkgRow(a, app, pkg));
            }
            bl.addTo(content);
            content.addView(UiKit.gap(a, 6));
            TextView tip = UiKit.text(a,
                    "点击一行打开半屏：改状态（跟随全局 / 固定已安装 / 固定未安装）、复制、删除\n"
                            + "长按或点「批量」可多选：全选后统一改状态、批量删掉不要的包名\n"
                            + "标着「排除放行」的包名不参与伪造，只按真实检测结果走",
                    11, UiKit.textSub(a));
            int tp = UiKit.dpI(a, 4);
            tip.setPadding(tp, UiKit.dpI(a, 6), tp, 0);
            content.addView(tip);
        }

        return UiComponents.scroll(a, content, "scroll:addpkg");
    }

    /** 写入手动包名 + 状态；已存在则只更新状态 */
    static boolean addPackageToConfig(String app, String pkg, String mode) {
        try {
            List<String> list = HookInit.userDefinedPackagesMap.get(app);
            if (list == null) {
                list = new ArrayList<String>();
                HookInit.userDefinedPackagesMap.put(app, list);
            }
            if (!list.contains(pkg)) list.add(pkg);
            // 从「排除」改回「伪造」：排除标记必须一并清掉，否则仍旧不伪造
            clearExcluded(app, pkg);
            invalidateCaches();
            log("system", "添加手动包名 " + pkg + "（" + modeLabel(mode) + "）");

            List<HookInit.PackageConfig> cfgs = HookInit.packageConfigMap.get(app);
            if (cfgs == null) {
                cfgs = new ArrayList<HookInit.PackageConfig>();
                HookInit.packageConfigMap.put(app, cfgs);
            }
            for (int i = 0; i < cfgs.size(); i++) {
                HookInit.PackageConfig c = cfgs.get(i);
                if (c != null && pkg.equals(c.packageName)) {
                    c.statusMode = mode;
                    return true;
                }
            }
            HookInit.PackageConfig c = new HookInit.PackageConfig(pkg);
            c.statusMode = mode;
            cfgs.add(c);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 统一复制入口 */
    static void copyText(Activity a, String text) {
        if (a == null || text == null || text.length() == 0) return;
        try {
            Object svc = a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (svc instanceof android.content.ClipboardManager) {
                ((android.content.ClipboardManager) svc).setPrimaryClip(
                                android.content.ClipData.newPlainText("text", text));
                toast(a, "已复制 " + text);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 从勾选 map 里收集已勾选的 key（导入/导出按钮点击时都要做一遍） */
    static List<String> checkedKeys(java.util.Map<String, Boolean> sel) {
        List<String> keys = new ArrayList<String>();
        java.util.Iterator<String> i = sel.keySet().iterator();
        while (i.hasNext()) {
            String k = i.next();
            if (Boolean.TRUE.equals(sel.get(k))) keys.add(k);
        }
        return keys;
    }

    /**
     * 长列表容器
     */
    static LinearLayout plainCard(Activity a) {
        LinearLayout c = UiKit.vbox(a);
        c.setBackground(UiKit.strokeRect(a, UiKit.card(a),
                UiKit.withAlpha(UiKit.accent(a), UiKit.isNight(a) ? 0x44 : 0x30),
                UiKit.CARD_R));
        return c;
    }

    static View sectionTitle(Activity a, String title) {
        TextView t = UiKit.text(a, title, 13, UiKit.textSub(a));
        t.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 12), UiKit.dpI(a, 4), UiKit.dpI(a, 6));
        return t;
    }

    /** 带右侧动作文字的小标题*/
    static View sectionTitleWithAction(Activity a, String title, String action,
            View.OnClickListener l) {
        return sectionTitleWithActions(a, title, new String[]{action},
        new View.OnClickListener[]{l}, null);
    }

    /**
     * 权限分类标题文案
     */
    static CharSequence permGroupLabel(String name, int total, int off) {
        String n = String.valueOf(total);
        if (off <= 0) {
            return " " + name + "（" + n + "）";
        }
        if (off >= total) {
            String str = " " + name + "（" + n + "）";
            android.text.SpannableString sp = new android.text.SpannableString(str);
            int st = str.length() - 1 - n.length();
            sp.setSpan(new android.text.style.ForegroundColorSpan(0xFFE53935), st,
                    str.length() - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return sp;
        }
        String offStr = String.valueOf(off);
        String str = " " + name + "（" + n + "/" + offStr + "）";
        android.text.SpannableString sp = new android.text.SpannableString(str);
        int st = str.length() - 1 - offStr.length();
        sp.setSpan(new android.text.style.ForegroundColorSpan(0xFFE53935), st,
                str.length() - 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    /** 圆点指示：实心 ● = 已全选，空心 ○ = 未全选*/
    static void setDot(Activity a, TextView dot, boolean on) {
        if (dot == null) return;
        dot.setText(on ? "●" : "○");
        dot.setTextColor(on ? UiKit.accent(a) : UiKit.textSub(a));
    }

    /** 带「批量」入口的小标题：点一下进入多选模式*/
    static View sectionTitleWithBatch(Activity a, String title, final BatchList bl) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10), UiKit.dpI(a, 4), UiKit.dpI(a, 4));
        TextView t = UiKit.text(a, title, 13, UiKit.textSub(a));
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (bl != null) {
            TextView chip = actionChip(a, "批量", null);
            row.addView(chip);
            bl.bindEntry(chip);
        }
        return row;
    }

    /**
     * 带右侧多个动作胶囊的小标题：一行里同时放「展开/合并」和「清理」）
     */
    static View sectionTitleWithActions(Activity a, String title, String[] labels,
            View.OnClickListener[] listeners, TextView[] outActions) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10), UiKit.dpI(a, 4), UiKit.dpI(a, 4));
        TextView t = UiKit.text(a, title, 13, UiKit.textSub(a));
        // 这一行右侧最多三个胶囊，窄屏时标题只占一行、超出省略，避免被压成几段
        t.setMaxLines(1);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(t, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (labels != null) {
            for (int i = 0; i < labels.length; i++) {
                View.OnClickListener l = (listeners != null && i < listeners.length) ? listeners[
                        i] : null;
                TextView act = actionChip(a, labels[i], l);
                if (outActions != null && i < outActions.length) outActions[i] = act;
                LinearLayout.LayoutParams actp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
                actp.leftMargin = UiKit.dpI(a, 6);
                row.addView(act, actp);
            }
        }
        return row;
    }

    /** 小标题右侧的动作胶囊（主题色淡底） */
    static TextView actionChip(Activity a, String text, View.OnClickListener l) {
        return UiComponents.chipButton(a, text, l, false);
    }

    static View tailTip(Activity a, String text) {
        TextView t = UiKit.text(a, text, 12, UiKit.textSub(a));
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, UiKit.dpI(a, 12), 0, UiKit.dpI(a, 12));
        return t;
    }

    static String currentMode(String app, String pkg) {
        try {
            List<HookInit.PackageConfig> cfgs = HookInit.packageConfigMap.get(app);
            if (cfgs != null) {
                for (int i = 0; i < cfgs.size(); i++) {
                    HookInit.PackageConfig c = cfgs.get(i);
                    if (c != null && pkg.equals(c.packageName)) {
                        return c.statusMode == null ? "follow" : c.statusMode;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "follow";
    }

    static void setPackageMode(String app, String pkg, String mode) {
        try {
            List<HookInit.PackageConfig> cfgs = HookInit.packageConfigMap.get(app);
            if (cfgs == null) {
                cfgs = new ArrayList<HookInit.PackageConfig>();
                HookInit.packageConfigMap.put(app, cfgs);
            }
            for (int i = 0; i < cfgs.size(); i++) {
                HookInit.PackageConfig c = cfgs.get(i);
                if (c != null && pkg.equals(c.packageName)) {
                    // 同一个值重复点就不写了：既不刷日志，也不白清缓存
                    if (mode != null && mode.equals(c.statusMode)) return;
                    c.statusMode = mode;
                    clearStatusCache(pkg);
                    log("system", "包名状态 " + pkg + " -> " + modeLabel(mode));
                    return;
                }
            }
            HookInit.PackageConfig c = new HookInit.PackageConfig(pkg);
            c.statusMode = mode;
            cfgs.add(c);
            clearStatusCache(pkg);
            log("system", "包名状态 " + pkg + " -> " + modeLabel(mode));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 清掉 hook 侧对单个包名的状态缓存
     */
    private static void clearStatusCache(String pkg) {
        try {
            if (ReaLog.sHookInit != null && pkg != null) {
                ReaLog.sHookInit.packageStatusCache.remove(pkg);
            }
        } catch (Throwable ignored) {
        }
    }

    static void removePackage(String app, String pkg) {
        try {
            List<String> list = HookInit.userDefinedPackagesMap.get(app);
            if (list != null) list.remove(pkg);
            try {
                HookInit.globalCapturedPackages.remove(pkg);
            } catch (Throwable ignored) {
            }
            clearExcluded(app, pkg);
            List<HookInit.PackageConfig> cfgs = HookInit.packageConfigMap.get(app);
            if (cfgs != null) {
                for (int i = cfgs.size() - 1; i >= 0; i--) {
                    HookInit.PackageConfig c = cfgs.get(i);
                    if (c != null && pkg.equals(c.packageName)) cfgs.remove(i);
                }
            }
            clearStatusCache(pkg);
            invalidateCaches();
            log("system", "删除手动包名 " + pkg);
        } catch (Throwable ignored) {
        }
    }

    /** 这个包名是不是「排除包名」：排除 = 不伪造它，按真实检测结果走*/
    static boolean isExcluded(String app, String pkg) {
        try {
            List<String> ex = HookInit.excludedPackagesMap.get(app);
            return ex != null && pkg != null && ex.contains(pkg);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** 包名增删后让状态缓存失效，否则列表里显示的还是旧状态 */
    static void invalidateCaches() {
        try {
            HookInit hi = ReaLog.sHookInit;
            if (hi != null) hi.invalidatePackageCaches();
        } catch (Throwable ignored) {
        }
    }

    /** 把包名从「排除」列表里移出来*/
    static void clearExcluded(String app, String pkg) {
        try {
            List<String> ex = HookInit.excludedPackagesMap.get(app);
            if (ex != null) ex.remove(pkg);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 添加「排除包名」：包名进手动列表，但**不进**伪造集合，也不保留包名状态配置
     */
    static boolean addExcludedPackage(String app, String pkg) {
        try {
            List<String> list = HookInit.userDefinedPackagesMap.get(app);
            if (list == null) {
                list = new ArrayList<String>();
                HookInit.userDefinedPackagesMap.put(app, list);
            }
            if (!list.contains(pkg)) list.add(pkg);
            List<String> ex = HookInit.excludedPackagesMap.get(app);
            if (ex == null) {
                ex = new ArrayList<String>();
                HookInit.excludedPackagesMap.put(app, ex);
            }
            if (!ex.contains(pkg)) ex.add(pkg);
            // 排除语义：从伪造集合里摘掉，包名状态配置也一起删掉
            try {
                HookInit.globalCapturedPackages.remove(pkg);
            } catch (Throwable ignored) {
            }
            List<HookInit.PackageConfig> cfgs = HookInit.packageConfigMap.get(app);
            if (cfgs != null) {
                for (int i = cfgs.size() - 1; i >= 0; i--) {
                    HookInit.PackageConfig c = cfgs.get(i);
                    if (c != null && pkg.equals(c.packageName)) cfgs.remove(i);
                }
            }
            clearStatusCache(pkg);
            invalidateCaches();
            log("system", "添加排除包名 " + pkg);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 状态模式的中文名（写日志用） */
    static String modeLabel(String mode) {
        if ("installed".equals(mode)) return "固定已安装";
        if ("not_installed".equals(mode)) return "固定未安装";
        return "跟随全局";
    }

    static int statusModeColor(Activity a, String app, String pkg) {
        if (isExcluded(app, pkg)) return 0xFFFB8C00;
        String m = currentMode(app, pkg);
        if ("installed".equals(m)) return 0xFF4CAF50;
        if ("not_installed".equals(m)) return 0xFFE53935;
        return UiKit.textSub(a);
    }

    // ============================================================
    //  批量选择列表：全选 / 批量改状态 / 批量删除
    // ============================================================
    interface BatchListener {
        /** 非选择模式下点一行（row 上的 tag 由调用方自己放，用来更新状态文字） */
        void onOpen(String key, View row);
        /** 选择模式下点「删除」 */
        void onDelete(java.util.List<String> keys);
        /** 选择模式下点某个状态胶囊；modeLabels 为 null 时不会走到这里 */
        void onSetMode(java.util.List<String> keys, String label);
    }

    /** 一堆行 + 一个操作条：长按任意行或用外部「批量」入口进入选择模式， 勾选后统一改状态 / 删除。既支持单条（选一条再删），也支持全选。 */
    static class BatchList {
        private final Activity a;
        private final String[] modeLabels;
        private final BatchListener listener;
        final LinearLayout card;
        final android.widget.HorizontalScrollView toolbar;
        private final TextView info;
        /** 工具条「全选」胶囊：按当前是否已全选切成「取消全选」 */
        private final TextView allBtn;
        /** 外部入口胶囊：进入选择模式后自己变成「取消」*/
        private TextView entryBtn;
        private final java.util.List<LinearLayout> rows = new ArrayList<LinearLayout>();
        private final java.util.List<TextView> marks = new ArrayList<TextView>();
        private final java.util.LinkedHashSet<String> sel = new java.util.LinkedHashSet<String>();
        private boolean selecting;
        BatchList(Activity a, String[] modeLabels, BatchListener l) {
            this.a = a;
            this.modeLabels = modeLabels;
            this.listener = l;
            this.card = plainCard(a);
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(a);
            hsv.setHorizontalScrollBarEnabled(false);
            hsv.setVisibility(View.GONE);
            LinearLayout bar = UiKit.hbox(a);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            int bp = UiKit.dpI(a, 4);
            bar.setPadding(bp, UiKit.dpI(a, 6), bp, UiKit.dpI(a, 2));
            allBtn = actionChip(a, "全选", new View.OnClickListener() {
                public void onClick(View v) {
                    if (sel.size() == rows.size()) {
                        sel.clear();
                    } else {
                        sel.clear();
                        for (int i = 0; i < rows.size(); i++) {
                            Object k = rows.get(i).getTag(TAG_KEY_ID);
                            if (k instanceof String) sel.add((String) k);
                        }
                    }
                    refreshMarks();
                }
            });
            bar.addView(allBtn);
            info = UiKit.text(a, "", 12, UiKit.textSub(a));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.leftMargin = UiKit.dpI(a, 8);
            ilp.rightMargin = UiKit.dpI(a, 4);
            info.setLayoutParams(ilp);
            bar.addView(info);

            if (modeLabels != null) {
                for (int i = 0; i < modeLabels.length; i++) {
                    final String label = modeLabels[i];
                    bar.addView(actionChip(a, label, new View.OnClickListener() {
                        public void onClick(View v) {
                            doSetMode(label);
                        }
                    }), chipLp());
                }
            }
            bar.addView(actionChip(a, "删除", new View.OnClickListener() {
                public void onClick(View v) {
                    doDelete();
                }
            }), chipLp());
            hsv.addView(bar);
            this.toolbar = hsv;
        }

        private LinearLayout.LayoutParams chipLp() {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = UiKit.dpI(a, 6);
            return lp;
        }

        /** 行 key 存在这个 tag 上（id 用负数，避免和普通 tag 冲突） */
        static final int TAG_KEY_ID = 0x7f0f0001;
        /** 加一行：接管点击（选择模式切换选中）与长按（进入选择模式） */
        void addRow(final String key, final LinearLayout row) {
            row.setTag(TAG_KEY_ID, key);
            TextView mark = UiKit.text(a, "○", 16, UiKit.textSub(a));
            int mp = UiKit.dpI(a, 10);
            mark.setPadding(mp, 0, UiKit.dpI(a, 2), 0);
            mark.setVisibility(View.GONE);
            rows.add(row);
            marks.add(mark);
            // 勾选指示常驻在行首（默认隐藏），进选择模式只切可见性，不动视图结构
            row.addView(mark, 0);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (selecting) {
                        toggle(key);
                    } else if (listener != null) {
                        listener.onOpen(key, row);
                    }
                }
            });
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    UiKit.haptic(v);
                    // 已经在选择模式：再次长按 = 退出多选
                    if (selecting) {
                        exitSelect();
                        return true;
                    }
                    enterSelect();
                    toggle(key);
                    return true;
                }
            });
            card.addView(row);
        }

        /** 外部入口胶囊绑定：点它进入/退出选择模式，文案跟着变「批量」⇄「取消」 */
        void bindEntry(TextView btn) {
            if (btn == null) return;
            entryBtn = btn;
            btn.setText("批量");
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (selecting) {
                        exitSelect();
                    } else {
                        enterSelect();
                    }
                }
            });
        }

        /** 外部「批量」入口 */
        void enterSelect() {
            if (selecting) return;
            selecting = true;
            if (entryBtn != null) entryBtn.setText("取消");
            toolbar.setVisibility(View.VISIBLE);
            for (int i = 0; i < marks.size(); i++) {
                marks.get(i).setVisibility(View.VISIBLE);
            }
            refreshMarks();
        }

        void exitSelect() {
            selecting = false;
            sel.clear();
            if (entryBtn != null) entryBtn.setText("批量");
            toolbar.setVisibility(View.GONE);
            for (int i = 0; i < rows.size(); i++) {
                marks.get(i).setVisibility(View.GONE);
            }
        }

        private void toggle(String key) {
            if (sel.contains(key)) {
                sel.remove(key);
            } else {
                sel.add(key);
            }
            refreshMarks();
        }

        private void refreshMarks() {
            for (int i = 0; i < rows.size(); i++) {
                String key = (String) rows.get(i).getTag(TAG_KEY_ID);
                boolean on = key != null && sel.contains(key);
                TextView mark = marks.get(i);
                mark.setText(on ? "●" : "○");
                mark.setTextColor(on ? UiKit.accent(a) : UiKit.textSub(a));
            }
            info.setText("已选 " + sel.size() + " 项");
            if (allBtn != null) {
                boolean all = !rows.isEmpty() && sel.size() == rows.size();
                allBtn.setText(all ? "取消全选" : "全选");
            }
        }

        private java.util.List<String> selList() {
            return new ArrayList<String>(sel);
        }

        private void doDelete() {
            if (sel.isEmpty()) {
                toast(a, "先勾选要删除的项");
                return;
            }
            if (listener != null) listener.onDelete(selList());
        }

        private void doSetMode(String label) {
            if (sel.isEmpty()) {
                toast(a, "先勾选要设置的项");
                return;
            }
            if (listener != null) listener.onSetMode(selList(), label);
        }

        /** 工具条 + 卡片一起挂进页面 */
        void addTo(LinearLayout content) {
            content.addView(toolbar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            content.addView(card, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** 只能由批量列表接管的包名行：行结构的点击/长按留给 BatchList，不在这里绑 */
    private static LinearLayout batchPkgRow(final Activity a, final String app, final String pkg) {
        LinearLayout row = UiKit.hbox(a);
        row.setMinimumHeight(UiKit.dpI(a, 56));
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));
        row.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));

        LinearLayout col = UiKit.vbox(a);
        TextView name = UiKit.text(a, pkg, 14, UiKit.textMain(a));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final TextView st = UiKit.text(a, statusModeLabel(app, pkg), 11,
                statusModeColor(a, app, pkg));
        col.addView(name);
        col.addView(st);
        row.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        // 搜索过滤靠行上的 contentDescription 匹配包名
        row.setContentDescription(pkg);
        // 状态文字挂在行的默认 tag 上，切换状态后由调用方就地刷新
        row.setTag(st);
        return row;
    }

    /** 手动包名的批量行为：点击循环切状态、多选后统一改状态或删除。  */
    static BatchListener packageBatchListener(final Activity a, final String app) {
        return new BatchListener() {
            // 点行不再循环切状态，改成推半屏（单选 + 复制 + 删除都在里面）
            public void onOpen(String key, View row) {
                openPackageSheet(a, key);
            }

            public void onDelete(java.util.List<String> keys) {
                for (int i = 0; i < keys.size(); i++) {
                    removePackage(app, keys.get(i));
                }
                save(a);
                toast(a, "已删除 " + keys.size() + " 个包名");
                PageStack ps = PageStack.get();
                if (ps != null) ps.refreshTopContent();
                if (UiPages.home() != null) UiPages.home().refreshPackages();
            }

            public void onSetMode(java.util.List<String> keys, String label) {
                String mode = "跟随全局".equals(label) ? "follow"
                        : ("固定已安装".equals(label) ? "installed" : "not_installed");
                int skipped = 0;
                for (int i = 0; i < keys.size(); i++) {
                    if (isExcluded(app, keys.get(i))) {
                        skipped++;
                        continue;
                    }
                    setPackageMode(app, keys.get(i), mode);
                }
                save(a);
                if (skipped > 0) {
                    toast(a, "已设为「" + label + "」，共 " + (keys.size() - skipped)
                            + " 项（排除包跳过 " + skipped + " 个）");
                } else {
                    toast(a, "已设为「" + label + "」，共 " + keys.size() + " 项");
                }
                PageStack ps = PageStack.get();
                if (ps != null) ps.refreshTopContent();
                if (UiPages.home() != null) UiPages.home().refreshPackages();
            }
        };
    }

    // ============================================================
    //  可折叠列表区块：标题 + 批量 + 合并 + 摘要卡
    // ============================================================
    /** 折叠时保留的行数*/
    static final int FOLD_KEEP = 3;
    static class ListItem {
        final String key;
        final String extra;
        ListItem(String key, String extra) {
            this.key = key;
            this.extra = extra;
        }
    }

    interface RowFactory {
        LinearLayout make(Activity a, String key, String extra);
    }

    /**
     * 建一个列表区块：标题右侧带「批量」「合并」两个胶囊。 合并后整张卡收起，只留一张摘要卡（露出前 3 条 + 共 N 条）， 点摘要或「展开」还原——和包名列表的合并是同一套观感 所有行登记进 outRows，供页面顶部搜索框过滤高亮。
     */
    static View listSection(final Activity a, final String title,
            final java.util.List<ListItem> items, final String[] modes,
            final String summaryHead, final BatchListener listener,
            final RowFactory factory, final java.util.List<View> outRows,
            final java.util.List<View> outCards, final java.util.List<View> outTitles) {

        final BatchList bl = new BatchList(a, modes, listener);
        // 实际入卡行数：factory.make 拒绝返 null 的条目不进卡，
        // 但折叠提示「还有 N 条」照 items.size() 算 → 藏的没那么多
        final int[] added = {0};
        for (int i = 0; i < items.size(); i++) {
            ListItem it = items.get(i);
            if (it == null || it.key == null) continue;
            LinearLayout row = factory.make(a, it.key, it.extra);
            if (row == null) continue;
            // contentDescription 兼作搜索文本：key + 副标题，所以搜「黑名单」「拦截」这类词也能命中
            row.setContentDescription(it.extra == null ? it.key
                    : (it.key + " " + it.extra));
            if (bl.card.getChildCount() > 0) {
                View dv = UiComponents.rowDivider(a);
                dv.setTag("divider");
                bl.card.addView(dv);
            }
            if (outRows != null) outRows.add(row);
            bl.addRow(it.key, row);
            added[0]++;
        }

        // 折叠：不换组件、不插摘要卡，只把第 4 行以后藏起来 + 尾部提示一行
        final LinearLayout holder = UiKit.vbox(a);
        holder.addView(bl.card);
        final TextView foldTip = UiKit.text(a, "", 11, UiKit.textSub(a));
        foldTip.setPadding(UiKit.dpI(a, 12), UiKit.dpI(a, 6), UiKit.dpI(a, 12), 0);
        foldTip.setVisibility(View.GONE);
        holder.addView(foldTip, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // 折叠状态跨重建记忆：切标签、开关半屏、退宿主再进来都保持
        final String foldKey = "fold:" + UiState.keyOf(title);
        final boolean[] merged = {UiState.fold(foldKey)};
        final TextView[] mergeBtn = new TextView[1];
        final View.OnClickListener[] self = new View.OnClickListener[1];
        // 应用当前折叠状态：可见性、提示行、按钮文案一次算齐
        final Runnable[] applyFold = new Runnable[1];
        applyFold[0] = new Runnable() {
            public void run() {
                int shown = 0;
                for (int i = 0; i < bl.card.getChildCount(); i++) {
                    View ch = bl.card.getChildAt(i);
                    if (ch == null) continue;
                    if ("divider".equals(ch.getTag())) {
                        ch.setVisibility(merged[0] && (shown == 0 || shown >= FOLD_KEEP)
                                ? View.GONE : View.VISIBLE);
                        continue;
                    }
                    shown++;
                    ch.setVisibility(merged[0] && shown > FOLD_KEEP
                            ? View.GONE : View.VISIBLE);
                }
                int rest = added[0] - FOLD_KEEP;
                if (merged[0] && rest > 0) {
                    foldTip.setText("还有 " + rest + " 条没显示，点「展开」查看全部");
                    foldTip.setVisibility(View.VISIBLE);
                } else {
                    foldTip.setVisibility(View.GONE);
                }
                if (mergeBtn[0] != null) mergeBtn[0].setText(merged[0] ? "展开" : "收起");
            }
        };
        self[0] = new View.OnClickListener() {
            public void onClick(View v) {
                merged[0] = !merged[0];
                UiState.setFold(foldKey, merged[0]);
                if (merged[0]) bl.exitSelect();
                applyFold[0].run();
                if (merged[0] && items.size() - FOLD_KEEP <= 0) {
                    toast(a, "条数不多，已经全部显示了");
                }
            }
        };
        LinearLayout box = UiKit.vbox(a);
        TextView[] acts = new TextView[2];
        View titleView = sectionTitleWithActions(a, title,
        new String[]{"批量", "收起"},
        new View.OnClickListener[]{null, self[0]}, acts);
        box.addView(titleView);
        mergeBtn[0] = acts[1];
        bl.bindEntry(acts[0]);
        // 建完先按记忆的折叠状态渲染一次
        if (merged[0]) applyFold[0].run();
        box.addView(bl.toolbar, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(holder, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // 搜索开始时要把合并态展开，否则命中的行藏着看不到（attachSearch 会拿这个 tag）
        box.setTag(new Runnable() {
            public void run() {
                if (merged[0]) self[0].onClick(null);
            }
        });
        if (outCards != null) outCards.add(bl.card);
        if (outTitles != null) outTitles.add(titleView);
        return box;
    }

    /** 通用搜索过滤：按行上的 contentDescription 模糊匹配， 命中行整行铺淡主题色底；整块都没命中就把卡和标题一起收起。 */
    static void attachSearch(final Activity a, android.widget.EditText box,
            final java.util.List<View> rows, final java.util.List<View> cards,
            final java.util.List<View> titles, final java.util.List<View> dividers,
            final java.util.List<Runnable> expanders) {
        if (box == null || rows == null) return;
        // 行的原始背景兜底
        final java.util.Map<
                View, android.graphics.drawable.Drawable> origBgs = new java.util.WeakHashMap<
                View, android.graphics.drawable.Drawable>();
        box.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}

            public void onTextChanged(CharSequence s, int st, int b, int c) {}

            public void afterTextChanged(android.text.Editable e) {
                String q = e == null ? "" : e.toString().trim().toLowerCase();
                boolean empty = q.length() == 0;
                // 一开始搜就把折叠展开，不然后面的行被藏起来了
                if (!empty && expanders != null) {
                    for (int i = 0; i < expanders.size(); i++) {
                        Runnable r = expanders.get(i);
                        if (r != null) r.run();
                    }
                }
                if (dividers != null) {
                    for (int i = 0; i < dividers.size(); i++) {
                        View d = dividers.get(i);
                        if (d != null) d.setVisibility(empty ? View.VISIBLE : View.GONE);
                    }
                }
                for (int i = 0; i < rows.size(); i++) {
                    View row = rows.get(i);
                    if (row == null) continue;
                    Object cd = row.getContentDescription();
                    String txt = cd instanceof String ? ((String) cd).toLowerCase() : "";
                    boolean hit = empty || txt.indexOf(q) >= 0;
                    row.setVisibility(hit ? View.VISIBLE : View.GONE);
                    // 第一次碰到时记下原始背景；清空搜索时还原，而不是置 null
                    if (!origBgs.containsKey(row)) {
                        origBgs.put(row, row.getBackground());
                    }
                    if (hit && !empty) {
                        row.setBackground(UiKit.roundRect(a, UiKit.accentSoft(a), 10));
                    } else {
                        row.setBackground(origBgs.get(row));
                    }
                }
                if (cards == null) return;
                for (int i = 0; i < cards.size(); i++) {
                    View card = cards.get(i);
                    if (card == null) continue;
                    boolean any = false;
                    if (card instanceof ViewGroup) {
                        ViewGroup vg = (ViewGroup) card;
                        for (int j = 0; j < vg.getChildCount(); j++) {
                            View ch = vg.getChildAt(j);
                            if (ch != null && ch.getVisibility() == View.VISIBLE
                                    && rows.contains(ch)) {
                                any = true;
                                break;
                            }
                        }
                    }
                    card.setVisibility(any || empty ? View.VISIBLE : View.GONE);
                    if (titles != null && i < titles.size()) {
                        View ti = titles.get(i);
                        if (ti != null) {
                            ti.setVisibility(any || empty ? View.VISIBLE : View.GONE);
                        }
                    }
                }
            }
        });
    }

    // ============================================================
    //  自动处理名单页
    // ============================================================
    static void renderSearchResults(final Activity a, LinearLayout box,
            List<UiPages.HomePage.SearchItem> all, String q) {
        if (box == null) return;
        box.removeAllViews();
        if (all == null || all.isEmpty()) {
            box.addView(searchTip(a, "没有可搜索的内容"));
            return;
        }
        final String key = q == null ? "" : q.trim().toLowerCase();
        List<UiPages.HomePage.SearchItem> features = new ArrayList<UiPages.HomePage.SearchItem>();
        List<UiPages.HomePage.SearchItem> others = new ArrayList<UiPages.HomePage.SearchItem>();
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        int featureCap = key.length() == 0 ? 40 : 60;
        int otherCap = key.length() == 0 ? 30 : 60;
        for (int i = 0; i < all.size(); i++) {
            UiPages.HomePage.SearchItem it = all.get(i);
            if (it == null || it.text == null) continue;
            String tabName = it.tabName == null ? "" : it.tabName;
            boolean match = key.length() == 0
                    || it.text.toLowerCase().contains(key)
                    || tabName.toLowerCase().contains(key);
            if (!match) continue;
            if (!seen.add(it.tabIndex + "#" + it.text)) continue;
            if (it.isFeature) {
                if (features.size() < featureCap) features.add(it);
            } else {
                if (others.size() < otherCap) others.add(it);
            }
        }
        if (features.isEmpty() && others.isEmpty()) {
            box.addView(searchTip(a, "没有匹配「" + q + "」的内容"));
            return;
        }
        if (!features.isEmpty()) {
            box.addView(sectionTitle(a, (key.length() == 0 ? "全部功能项" : "功能项")
                    + "（" + features.size() + "）"));
            box.addView(searchCard(a, features));
        }
        if (!others.isEmpty()) {
            box.addView(sectionTitle(a, (key.length() == 0 ? "页面内容" : "页面内容")
                    + "（" + others.size() + "）"));
            box.addView(searchCard(a, others));
        }
    }

    private static LinearLayout searchCard(final Activity a,
            List<UiPages.HomePage.SearchItem> items) {
        LinearLayout card = plainCard(a);
        for (int i = 0; i < items.size(); i++) {
            final UiPages.HomePage.SearchItem it = items.get(i);
            if (i > 0) card.addView(UiComponents.rowDivider(a));
            String where = it.page == 0 ? ("在「" + it.tabName + "」里")
                    : ("在「" + pageName(it.page) + "」里");
            card.addView(UiComponents.settingRow(a, it.text, where,
                    null, new View.OnClickListener() {
                        public void onClick(View v) {
                            PageStack s = PageStack.get();
                            if (s == null) return;
                            s.popToRoot();
                            if (it.page != 0) {
                                // 独立页面：打开那一页并定位到命中项
                                openPageByKind(a, it.page, it.text);
                                return;
                            }
                            // 标签页里的一项：定位到那条文本（首页会在这中间重建一次列表）
                            if (UiPages.home() != null) {
                                UiPages.home().jumpToItem(it.tabIndex, it.text);
                            }
                        }
                    }));
        }
        return card;
    }

    /** 搜索索引里的独立页面编号 → 名字 / 打开动作 */
    static String pageName(int kind) {
        if (kind == 1) return "设置";
        if (kind == 2) return "配置备份";
        if (kind == 3) return "添加包名";
        return "页面";
    }

    static void openPageByKind(final Activity a, int kind, final String text) {
        try {
            PageStack s = PageStack.get();
            if (s == null) return;
            TextView fv = PageStack.sFloatingView;
            // 栈中已有同页就弹回它，不叠一份
            String pk = kind == 1 ? "settings" : (kind == 2 ? "data" : "addpkg");
            if (!s.popTo(pk)) {
                if (kind == 1) s.push(new UiPages.SettingsPage(fv));
                else if (kind == 2) s.push(new UiPages.DataPage(fv));
                else if (kind == 3) s.push(new UiPages.AddPackagePage(fv));
            }
            // 页面刚 push 出来时内容还没测量完，scrollAndHighlight 内部已带 260ms 延迟
            if (text != null && text.length() > 0) {
                UiPages.scrollAndHighlight(s.topView(), text, a);
            }
        } catch (Throwable ignored) {
        }
    }

    private static TextView searchTip(Activity a, String text) {
        TextView t = UiKit.text(a, text, 13, UiKit.textSub(a));
        t.setGravity(Gravity.CENTER);
        int p = UiKit.dpI(a, 20);
        t.setPadding(p, p, p, p);
        return t;
    }

    // ============================================================
    static String shortId(String id) {
        if (id == null) return "";
        String s;
        if (id.startsWith("!")) {
            s = "!：" + id.substring(1);
        } else if (id.contains("*")) {
            s = "*：" + id;
        } else {
            s = id;
        }
        if (s.length() > 90) s = s.substring(0, 87) + "...";
        return s;
    }

    static LinearLayout autoActionRow(final Activity a, final String app, final String id,
            String typeDesc) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));

        LinearLayout col = UiKit.vbox(a);
        TextView name = UiKit.text(a, shortId(id), 13, UiKit.textMain(a));
        name.setMaxLines(3);
        TextView type = UiKit.text(a, typeDesc, 11,
                typeDesc != null && typeDesc.indexOf("黑") >= 0 ? 0xFFE53935 : 0xFF4CAF50);
        col.addView(name);
        col.addView(type);
        row.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView set = UiComponents.chipButton(a, "设置", new View.OnClickListener() {
            public void onClick(View v) {
                openAutoRuleSheet(a, id);
            }
        }, false);
        row.addView(set);

        TextView del = UiComponents.chipButton(a, "删除", new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    HookInit.removeAutoAction(app, id);
                    log("launch_intercept", "移除拦截名单 " + id);
                    HookInit hi = ReaLog.sHookInit;
                    if (hi != null) hi.saveConfigToFile();
                    toast(a, "已删除");
                    PageStack s = PageStack.get();
                    if (s != null) s.rebuildAll();
                } catch (Throwable t) {
                    toast(a, "删除失败");
                }
            }
        }, true);
        row.addView(del);
        return row;
    }

    static LinearLayout autoRecordRow(final Activity a, final String app, final String id,
            java.util.List<String> history) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));

        StringBuilder desc = new StringBuilder();
        if (history != null) {
            for (int i = 0; i < history.size(); i++) {
                String c = history.get(i);
                desc.append("real".equals(c) ? "✅" : ("fake".equals(c) ? "‼️" : "🕸️")).append(" ");
            }
            desc.append("(").append(history.size()).append("次)");
        }

        LinearLayout col = UiKit.vbox(a);
        TextView name = UiKit.text(a, shortId(id), 13, UiKit.textMain(a));
        name.setMaxLines(3);
        col.addView(name);
        col.addView(UiKit.text(a, desc.toString(), 11, UiKit.textSub(a)));
        row.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView del = UiComponents.chipButton(a, "删除记录", new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    HookInit.removeAutoRecord(app, id);
                    HookInit hi = ReaLog.sHookInit;
                    if (hi != null) hi.saveConfigToFile();
                    toast(a, "已删除记录");
                    PageStack s = PageStack.get();
                    if (s != null) s.rebuildAll();
                } catch (Throwable t) {
                    toast(a, "删除失败");
                }
            }
        }, true);
        row.addView(del);
        return row;
    }

    /** 最近一次判断的可读文本*/
    static String lastChoice(java.util.List<String> history) {
        if (history == null || history.isEmpty()) return "无";
        String c = history.get(history.size() - 1);
        if (c == null) return "无";
        if ("real".equals(c)) return "✅ 真实跳转";
        if ("fake".equals(c)) return "‼️ 疑似劫持";
        return "🕸️ " + c;
    }

    /** 处理列表类型*/
    public static final String LIST_LAUNCH = "launch";
    public static final String LIST_EXIT = "exit";
    public static View autoList(final Activity a, final TextView fv, String kind) {
        if (LIST_EXIT.equals(kind)) return exitPatternList(a, fv);
        LinearLayout content = UiComponents.paddedContent(a);
        final String app = app();
        final java.util.Map<String, String> actions = HookInit.getAllAutoActions(app);
        final java.util.Map<
                String, java.util.List<String>> records = HookInit.getAllAutoRecords(app);
        boolean noAction = actions == null || actions.isEmpty();
        boolean noRecord = records == null || records.isEmpty();
        if (noAction && noRecord) {
            LinearLayout tip = UiKit.vbox(a);
            tip.setPadding(0, UiKit.dpI(a, 24), 0, 0);
            TextView t = UiKit.text(a,
                    "当前没有名单或记录\n在目标应用里触发几次跳转后回来查看", 13, UiKit.textSub(a));
            t.setGravity(Gravity.CENTER);
            tip.addView(t);
            content.addView(tip);
            return UiComponents.scroll(a, content, "scroll:auto" + kind);
        }

        //处理列表支持搜索 + 合并摘要
        final java.util.List<View> searchRows = new ArrayList<View>();
        final java.util.List<View> searchCards = new ArrayList<View>();
        final java.util.List<View> searchTitles = new ArrayList<View>();
        final java.util.List<Runnable> searchExpanders = new ArrayList<Runnable>();
        android.widget.EditText searchBox = null;
        if (!noAction || !noRecord) {
            searchBox = UiComponents.searchBox(a, "搜索名单 / 记录", null);
            content.addView(searchBox);
        }

        if (!noAction) {
            java.util.List<ListItem> items = new ArrayList<ListItem>();
            java.util.Iterator<
                    java.util.Map.Entry<String, String>> it = actions.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, String> e = it.next();
                items.add(new ListItem(e.getKey(),
                "black".equals(e.getValue()) ? "⛔ 黑名单" : "✅ 白名单"));
            }
            View sec1 = listSection(a, "黑/白名单（" + actions.size() + "）", items,
            new String[]{"设为黑名单", "设为白名单"}, "⛔ 黑/白名单",
            new BatchListener() {
                public void onOpen(String key, View row) {
                    openAutoRuleSheet(a, key);
                }

                public void onDelete(java.util.List<String> keys) {
                    for (int i = 0; i < keys.size(); i++) {
                        HookInit.removeAutoAction(app, keys.get(i));
                        log("launch_intercept", "移除拦截名单 " + keys.get(i));
                    }
                    afterBatchChange(a, "已删除 " + keys.size() + " 条名单");
                }

                public void onSetMode(java.util.List<String> keys, String label) {
                    String type = label != null && label.indexOf("黑") >= 0
                            ? "black" : "white";
                    for (int i = 0; i < keys.size(); i++) {
                        HookInit.putAutoAction(app, keys.get(i), type);
                        log("launch_intercept", "拦截名单 " + keys.get(i)
                                + " -> " + label);
                    }
                    afterBatchChange(a, "已设为" + label + "，共 " + keys.size() + " 条");
                }
            },
            new RowFactory() {
                public LinearLayout make(Activity aa, String key, String extra) {
                    return autoActionRow(aa, app, key, extra);
                }
            }, searchRows, searchCards, searchTitles);
            content.addView(sec1);
            addExpander(sec1, searchExpanders);
            final int actionTotal = actions.size();
            TextView delAllAction = warnConfirmButton(a,
            "清空全部名单（" + actionTotal + "）", new Runnable() {
                public void run() {
                    int n = 0;
                    try {
                        // 先拷 key：边遍历原 Map 边删会抛 ConcurrentModificationException
                        java.util.List<String> allKeys = new ArrayList<String>(actions.keySet());
                        for (int i = 0; i < allKeys.size(); i++) {
                            HookInit.removeAutoAction(app, allKeys.get(i));
                            n++;
                        }
                    } catch (Throwable ignored) {
                    }
                    log("launch_intercept", "清空全部拦截名单 " + n + " 条");
                    afterBatchChange(a, "已清空全部名单，共 " + n + " 条");
                }
            });
            LinearLayout.LayoutParams dap1 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dap1.topMargin = UiKit.dpI(a, 8);
            content.addView(delAllAction, dap1);
            content.addView(UiKit.gap(a, 6));
        }

        if (!noRecord) {
            java.util.List<ListItem> items2 = new ArrayList<ListItem>();
            java.util.Iterator<
                    java.util.Map.Entry<
                            String, java.util.List<String>>> it2 = records.entrySet().iterator();
            while (it2.hasNext()) {
                java.util.Map.Entry<String, java.util.List<String>> e = it2.next();
                items2.add(new ListItem(e.getKey(), "最近："
                        + lastChoice(e.getValue())));
            }
            View sec2 = listSection(a, "智能判断记录（" + records.size() + "）", items2,
            null, "🧠 智能判断记录",
            new BatchListener() {
                public void onOpen(String key, View row) {
                    openAutoRuleSheet(a, key);
                }

                public void onDelete(java.util.List<String> keys) {
                    for (int i = 0; i < keys.size(); i++) {
                        HookInit.removeAutoRecord(app, keys.get(i));
                        log("auto", "删除智能判断记录 " + keys.get(i));
                    }
                    afterBatchChange(a, "已删除 " + keys.size() + " 条记录");
                }

                public void onSetMode(java.util.List<String> keys, String label) {}
            },
            new RowFactory() {
                public LinearLayout make(Activity aa, String key, String extra) {
                    return autoRecordRow(aa, app, key, records.get(key));
                }
            }, searchRows, searchCards, searchTitles);
            content.addView(sec2);
            addExpander(sec2, searchExpanders);
            final int recordTotal = records.size();
            TextView delAllRecord = warnConfirmButton(a,
            "清空全部记录（" + recordTotal + "）", new Runnable() {
                public void run() {
                    int n = 0;
                    try {
                        java.util.List<String> allKeys = new ArrayList<String>(records.keySet());
                        for (int i = 0; i < allKeys.size(); i++) {
                            HookInit.removeAutoRecord(app, allKeys.get(i));
                            n++;
                        }
                    } catch (Throwable ignored) {
                    }
                    log("auto", "清空全部智能判断记录 " + n + " 条");
                    afterBatchChange(a, "已清空全部记录，共 " + n + " 条");
                }
            });
            LinearLayout.LayoutParams dap2 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dap2.topMargin = UiKit.dpI(a, 8);
            content.addView(delAllRecord, dap2);
            content.addView(UiKit.gap(a, 6));
            TextView tip2 = UiKit.text(a,
                    "同一目标连续 3 次相同判断后会自动写入名单", 11, UiKit.textSub(a));
            tip2.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 4), UiKit.dpI(a, 4), 0);
            content.addView(tip2);
        }

        attachSearch(a, searchBox, searchRows, searchCards, searchTitles, null,
        searchExpanders);

        return UiComponents.scroll(a, content, "scroll:auto" + kind);
    }

    // ============================================================
    //  退出拦截处理列表
    // ============================================================
    private static View exitPatternList(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);
        HookInit hi = ReaLog.sHookInit;
        java.util.List<HookInit.InterceptPattern> patterns;
        try {
            patterns = hi == null
                    ? new java.util.ArrayList<HookInit.InterceptPattern>()
                    : hi.getInterceptPatterns();
        } catch (Throwable t) {
            patterns = new java.util.ArrayList<HookInit.InterceptPattern>();
        }
        if (patterns == null) patterns = new java.util.ArrayList<HookInit.InterceptPattern>();

        if (patterns.isEmpty()) {
            TextView t = UiKit.text(a,
                    "还没有退出拦截记录\n在目标应用里触发几次退出拦截后回来查看", 13, UiKit.textSub(a));
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, UiKit.dpI(a, 24), 0, 0);
            content.addView(t, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return UiComponents.scroll(a, content, "scroll:exitpattern");
        }

        // 第 1 条：搜索 + 合并摘要 + 批量删除（key 用 patternHash）
        final java.util.List<ListItem> items = new ArrayList<ListItem>();
        final java.util.List<View> searchRows = new ArrayList<View>();
        final java.util.List<View> searchCards = new ArrayList<View>();
        final java.util.List<View> searchTitles = new ArrayList<View>();
        for (int i = 0; i < patterns.size(); i++) {
            HookInit.InterceptPattern p = patterns.get(i);
            if (p == null) continue;
            items.add(new ListItem(p.patternHash, exitPatternDesc(p)));
        }
        android.widget.EditText searchBox = UiComponents.searchBox(a, "搜索拦截模式 / 包名", null);
        content.addView(searchBox);

        // 4 档批量
        String[] exitModes = {"⛔ 拦截(清计数)", "✅ 放行(清计数)",
                "⛔ 拦截(留计数)", "✅ 放行(留计数)"
        };
        View sec3 = listSection(a, "拦截模式（" + items.size() + "）", items, exitModes,
        "🚪 拦截模式",
        new BatchListener() {
            public void onOpen(String key, View row) {
                openExitPatternSheet(a, key);
            }

            public void onDelete(java.util.List<String> keys) {
                try {
                    HookInit h = ReaLog.sHookInit;
                    if (h != null) {
                        for (int i = 0; i < keys.size(); i++) {
                            h.removeInterceptPattern(keys.get(i));
                        }
                    }
                } catch (Throwable ignored) {
                }
                afterBatchChange(a, "已删除 " + keys.size() + " 条拦截模式");
            }

            public void onSetMode(java.util.List<String> keys, String label) {
                HookInit h = ReaLog.sHookInit;
                if (h == null) return;
                boolean allow = label != null && label.indexOf("放行") >= 0;
                boolean keep = label != null && label.indexOf("留") >= 0;
                String choice = allow ? "allow" : "intercept";
                int ok = 0;
                for (int i = 0; i < keys.size(); i++) {
                    try {
                        if (keep) {
                            h.setInterceptPatternUserChoiceKeepCount(keys.get(i), choice);
                        } else {
                            h.setInterceptPatternUserChoice(keys.get(i), choice);
                        }
                        ok++;
                    } catch (Throwable ignored) {
                    }
                }
                log("exit_intercept", "批量修改拦截模式 " + ok + " 条 -> " + label);
                afterBatchChange(a, "已设为「" + label + "」，共 " + ok + " 条");
            }
        },
        new RowFactory() {
            public LinearLayout make(Activity aa, String key, String extra) {
                return exitPatternRowById(aa, key);
            }
        }, searchRows, searchCards, searchTitles);
        content.addView(sec3);
        final int exitTotal = items.size();
        TextView delAll = warnConfirmButton(a, "删除全部拦截模式（" + exitTotal + "）",
        new Runnable() {
            public void run() {
                try {
                    HookInit h = ReaLog.sHookInit;
                    if (h != null) h.removeAllInterceptPatterns();
                    log("exit_intercept", "清空全部拦截模式 " + exitTotal + " 条");
                } catch (Throwable ignored) {
                }
                afterBatchChange(a, "已清空全部拦截模式");
            }
        });
        LinearLayout.LayoutParams dap = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dap.topMargin = UiKit.dpI(a, 8);
        content.addView(delAll, dap);
        final java.util.List<Runnable> searchExpanders = new ArrayList<Runnable>();
        addExpander(sec3, searchExpanders);
        attachSearch(a, searchBox, searchRows, searchCards, searchTitles, null,
        searchExpanders);
        content.addView(UiKit.gap(a, 6));
        TextView tip2 = UiKit.text(a,
                "同一模式连续 3 次相同判断后会自动静默处理", 11, UiKit.textSub(a));
        tip2.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 4), UiKit.dpI(a, 4), 0);
        content.addView(tip2);
        return UiComponents.scroll(a, content, "scroll:exitpattern");
    }

    /** 把 listSection 返回容器上的「强制展开」动作收进列表（搜索时逐个调用） */
    static void addExpander(View section, java.util.List<Runnable> out) {
        if (section == null || out == null) return;
        Object tag = section.getTag();
        if (tag instanceof Runnable) out.add((Runnable) tag);
    }

    // ============================================================
    //  捕获到的包名
    // ============================================================
    /** 卡内的分段小标题：带 subhead tag，折叠时会跟着收起 */
    private static void addCaptureHead(Activity a, LinearLayout card, String text) {
        TextView t = UiKit.text(a, text, 12, UiKit.textSub(a));
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        t.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 4));
        t.setTag("subhead");
        card.addView(t);
    }

    /** 捕获区的一行：包名 + 检测到的状态；点击由 BatchList 接管（进半屏） */
    private static LinearLayout capturedPkgRow(Activity a, String pkg, String statusText) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(UiKit.dpI(a, 52));
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));
        row.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));
        TextView name = UiKit.text(a, pkg, 14, UiKit.textMain(a));
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(name, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        int stColor = UiKit.textSub(a);
        if (statusText != null) {
            if (statusText.indexOf("未") >= 0) stColor = 0xFFE53935;
            else if (statusText.indexOf("已") >= 0) stColor = 0xFF4CAF50;
        }
        TextView st = UiKit.text(a, statusText == null ? "" : statusText, 11, stColor);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = UiKit.dpI(a, 8);
        row.addView(st, sp);
        // 搜索锚点：statusText 为 null 时拼出 "pkg null" 会被当成关键字命中（E 组 bug 3）
        row.setContentDescription(pkg + " " + (statusText == null ? "" : statusText));
        return row;
    }

    /**
     * 捕获区的批量动作：迁入手动添加列表。 迁入 = 写入 userDefinedPackagesMap + 从自动捕获集合里移除，
     * 前者会跟着配置导出/导入走，所以迁入结果是持久的。
     */
    static BatchListener capturedBatchListener(final Activity a, final String app) {
        return new BatchListener() {
            public void onOpen(String key, View row) {
                openCaptureSheet(a, key);
            }

            public void onDelete(java.util.List<String> keys) {
                if (keys == null || keys.isEmpty()) return;
                HookInit hi = ReaLog.sHookInit;
                int n = 0;
                for (int i = 0; i < keys.size(); i++) {
                    String pkg = keys.get(i);
                    if (pkg == null) continue;
                    boolean ok = false;
                    try {
                        if (hi != null) ok = hi.removeCapturedPackage(app, pkg);
                    } catch (Throwable ignored) {
                    }
                    if (!ok) {
                        // 兜底：HookInit 还没挂上时直接动静态集合
                        try {
                            ok = HookInit.globalCapturedPackages.remove(pkg);
                        } catch (Throwable ignored) {
                        }
                    }
                    if (ok) {
                        n++;
                        clearStatusCache(pkg);
                    }
                }
                if (n <= 0) {
                    toast(a, "这些包名已不在捕获集合里");
                    return;
                }
                save(a);
                toast(a, "已删除 " + n + " 个捕获记录");
                log("config", "删除捕获记录 " + n + " 个");
                PageStack ps = PageStack.get();
                if (ps != null) ps.refreshTopContent();
            }

            public void onSetMode(java.util.List<String> keys, String label) {
                if (!"移入手动添加".equals(label)) return;
                // 双击确认：批量迁移不可撤销
                long now = System.currentTimeMillis();
                if (now - sMergeWarn > UiKit.TWO_STEP_ARM_MS) {
                    sMergeWarn = now;
                    UiKit.haptic(a);
                    toast(a, "再点一次确认移入");
                    return;
                }
                sMergeWarn = 0L;
                int n = 0;
                for (int i = 0; i < keys.size(); i++) {
                    if (promoteToManual(app, keys.get(i))) n++;
                }
                save(a);
                toast(a, "已迁入 " + n + " 个包名到手动添加");
                PageStack ps = PageStack.get();
                if (ps != null) ps.refreshTopContent();
                if (UiPages.home() != null) UiPages.home().refreshPackages();
            }
        };
    }

    /** 把一个捕获到的包名迁入手动添加列表：这条路只加不删，返回是否新增成功 */
    static boolean promoteToManual(String app, String pkg) {
        if (pkg == null || pkg.length() == 0) return false;
        boolean added = false;
        try {
            List<String> list = HookInit.userDefinedPackagesMap.get(app);
            if (list == null) {
                list = new ArrayList<String>();
                HookInit.userDefinedPackagesMap.put(app, list);
            }
            if (!list.contains(pkg)) {
                list.add(pkg);
                added = true;
                log("system", "捕获包名迁入手动列表 " + pkg);
            }
            try {
                HookInit.detectedCache.clear();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
        return added;
    }

    /** 打开捕获包名的半屏：可迁入手动列表 */
    static void openCaptureSheet(final Activity a, final String pkg) {
        try {
            PageStack s = PageStack.get();
            if (s == null) return;
            s.push(new UiPages.PackageSheetPage(pkg, app(), new Runnable() {
                public void run() {
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.refreshTopContent();
                    if (UiPages.home() != null) UiPages.home().refreshPackages();
                }
            }, true));
        } catch (Throwable t) {
            toast(a, "打开设置失败");
        }
    }

    /** 折叠一张卡*/
    static void applyFoldToCard(LinearLayout card, boolean folded, int total,
            LinearLayout tip, TextView btn, Activity a) {
        if (card == null) return;
        int shown = 0;
        for (int i = 0; i < card.getChildCount(); i++) {
            View ch = card.getChildAt(i);
            if (ch == null) continue;
            if ("divider".equals(ch.getTag())) {
                ch.setVisibility(folded && (shown == 0 || shown >= FOLD_KEEP)
                        ? View.GONE : View.VISIBLE);
                continue;
            }
            if ("subhead".equals(ch.getTag())) {
                ch.setVisibility(folded ? View.GONE : View.VISIBLE);
                continue;
            }
            shown++;
            ch.setVisibility(folded && shown > FOLD_KEEP ? View.GONE : View.VISIBLE);
        }
        if (tip != null && tip.getChildCount() > 0
                && tip.getChildAt(0) instanceof TextView) {
            int rest = total - FOLD_KEEP;
            if (folded && rest > 0) {
                ((TextView) tip.getChildAt(0)).setText(
                                "还有 " + rest + " 条没显示，点「展开」查看全部");
                tip.setVisibility(View.VISIBLE);
            } else {
                tip.setVisibility(View.GONE);
            }
        }
        if (btn != null) btn.setText(folded ? "展开" : "收起");
    }

    /** 打开「包名设置」半屏*/
    static void openPackageSheet(final Activity a, final String pkg) {
        try {
            PageStack s = PageStack.get();
            if (s == null) return;
            s.push(new UiPages.PackageSheetPage(pkg, app(), new Runnable() {
                public void run() {
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.refreshTopContent();
                    if (UiPages.home() != null) UiPages.home().refreshPackages();
                }
            }));
        } catch (Throwable t) {
            toast(a, "打开设置失败");
        }
    }

    /** 打开一条拦截模式的设置半屏 */
    static void openExitPatternSheet(final Activity a, final String hash) {
        try {
            PageStack s = PageStack.get();
            if (s == null) return;
            s.push(new UiPages.ExitPatternSheetPage(hash, new Runnable() {
                public void run() {
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.refreshTopContent();
                }
            }));
        } catch (Throwable t) {
            toast(a, "打开设置失败");
        }
    }

    /** 打开「设置自动处理」半屏*/
    static void openAutoRuleSheet(final Activity a, final String id) {
        try {
            PageStack s = PageStack.get();
            if (s == null) return;
            s.push(new UiPages.AutoRuleSheetPage(id, app(), new Runnable() {
                public void run() {
                    PageStack s = PageStack.get();
                    if (s != null) s.refreshTopContent();
                    if (UiPages.home() != null) UiPages.home().refreshPackages();
                }
            }));
        } catch (Throwable t) {
            toast(a, "打开设置失败");
        }
    }

    /** 批量改完名单/记录/模式后：落盘 + 刷新当前列表页 */
    private static void afterBatchChange(Activity a, String msg) {
        try {
            HookInit hi = ReaLog.sHookInit;
            if (hi != null) hi.saveConfigToFile();
        } catch (Throwable ignored) {
        }
        toast(a, msg);
        PageStack s = PageStack.get();
        if (s != null) s.refreshTopContent();
    }

    /** 一条拦截模式的可读摘要（合并后的摘要卡 / 搜索副标题用） */
    static String exitPatternDesc(HookInit.InterceptPattern p) {
        if (p == null) return "";
        StringBuilder sub = new StringBuilder();
        sub.append("intercept".equals(p.userChoice) ? "⛔ 拦截退出" : "✅ 放行退出");
        if (p.choiceCount > 0) sub.append(" · 命中 ").append(p.choiceCount).append(" 次");
        if (p.silentIntercept) sub.append(" · 静默");
        return sub.toString();
    }

    /** 按 patternHash 重建一条拦截模式行（列表工厂用，避免外面持有对象引用） */
    static LinearLayout exitPatternRowById(Activity a, String hash) {
        try {
            HookInit h = ReaLog.sHookInit;
            if (h == null || hash == null) return null;
            java.util.List<HookInit.InterceptPattern> ps = h.getInterceptPatterns();
            if (ps == null) return null;
            for (int i = 0; i < ps.size(); i++) {
                HookInit.InterceptPattern p = ps.get(i);
                if (p != null && hash.equals(p.patternHash)) return exitPatternRow(a, p);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 一条退出拦截模式记录：包名摘要 + 选择 + 次数 + 静默状态 + 删除 */
    private static LinearLayout exitPatternRow(final Activity a,
            final HookInit.InterceptPattern p) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));
        // 点行进设置半屏：处理方式、静默、清空计数、删除都在里面
        row.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openExitPatternSheet(a, p.patternHash);
            }
        });

        LinearLayout col = UiKit.vbox(a);
        StringBuilder names = new StringBuilder();
        int total = 0;
        java.util.List<String> shown = new java.util.ArrayList<String>();
        if (p.installedPackages != null) {
            total += p.installedPackages.size();
            shown.addAll(p.installedPackages);
        }
        if (p.notInstalledPackages != null) {
            total += p.notInstalledPackages.size();
            shown.addAll(p.notInstalledPackages);
        }
        for (int i = 0; i < shown.size() && i < 3; i++) {
            if (i > 0) names.append(" · ");
            names.append(shown.get(i));
        }
        if (total > 3) names.append(" 等 ").append(total).append(" 个包名");

        TextView name = UiKit.text(a,
                names.length() == 0 ? p.patternHash : names.toString(), 13, UiKit.textMain(a));
        name.setMaxLines(3);
        col.addView(name);

        StringBuilder sub = new StringBuilder();
        sub.append("intercept".equals(p.userChoice) ? "⛔ 拦截退出" : "✅ 放行退出");
        if (p.choiceCount > 0) sub.append(" · 命中 ").append(p.choiceCount).append(" 次");
        if (p.silentIntercept) sub.append(" · 静默");
        TextView desc = UiKit.text(a, sub.toString(), 11, UiKit.textSub(a));
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp1.topMargin = UiKit.dpI(a, 2);
        desc.setLayoutParams(dp1);
        col.addView(desc);
        row.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView del = UiComponents.chipButton(a, "删除", new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    HookInit hi = ReaLog.sHookInit;
                    if (hi != null) hi.removeInterceptPattern(p.patternHash);
                    toast(a, "已删除");
                    PageStack s = PageStack.get();
                    if (s != null) s.rebuildAll();
                } catch (Throwable t) {
                    toast(a, "删除失败");
                }
            }
        }, true);
        row.addView(del);
        return row;
    }

    /** 手动包名的状态文案：跟随全局 / 固定已安装 / 固定未安装 */
    static String statusModeLabel(String app, String pkg) {
        if (isExcluded(app, pkg)) return "排除放行（不伪造此包）";
        try {
            List<HookInit.PackageConfig> cfgs = HookInit.packageConfigMap.get(app);
            if (cfgs != null) {
                for (int i = 0; i < cfgs.size(); i++) {
                    HookInit.PackageConfig c = cfgs.get(i);
                    if (c != null && pkg.equals(c.packageName)) {
                        if ("installed".equals(c.statusMode)) return "固定已安装";
                        if ("not_installed".equals(c.statusMode)) return "固定未安装";
                        return "跟随全局";
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "跟随全局";
    }

    // ============================================================
    //  标签 3：设置
    // ============================================================
    public static View settings(final Activity a, final TextView fv) {
        LinearLayout content = UiComponents.paddedContent(a);

        // ---- 界面 ----
        UiComponents.CardGroup ui = UiComponents.cardGroup(a, "界面", true);
        ui.addRow(UiComponents.switchRow(a, "使用新版界面",
                "关闭后回到旧版对话框样式（下次打开生效）", UiKit.useNewUi(a),
                new UiComponents.OnChecked() {
                    public void onChecked(boolean checked) {
                        // 切换前先刷一次悬浮窗，切换后再刷一次：保证新旧主题色都即时落到悬浮窗上
                        save(a);
                        UiKit.setUseNewUi(a, checked);
                        save(a);
                        log("system", "界面风格 -> " + (checked ? "新版" : "旧版"));
                        if (!checked) {
                            // 延迟关闭：先让开关动画和 Toast 露出来
                            new android.os.Handler(android.os.Looper.getMainLooper())
                                    .postDelayed(new Runnable() {
                                        public void run() {
                                            PageStack s = PageStack.get();
                                            if (s != null) s.close();
                                        }
                                    }, 360);
                        }
                        toast(a, checked ? "已启用新版界面" : "已切回旧版样式");
                    }
                }));
        ui.addRow(UiComponents.settingRow(a, "夜间模式",
                nightText(a), null, new View.OnClickListener() {
                    public void onClick(View v) {
                        int now = UiKit.getNightMode(a);
                        int next = now == UiKit.NIGHT_SYSTEM ? UiKit.NIGHT_DAY
                                : (now == UiKit.NIGHT_DAY ? UiKit.NIGHT_NIGHT
                                        : UiKit.NIGHT_SYSTEM);
                        UiKit.setNightMode(a, next);
                        log("system", "夜间模式 -> " + nightText(a));
                        rebuild(a, "已切换为 " + nightText(a));
                    }
                }));
        ui.addRow(UiComponents.settingRow(a, "主题色",
                "当前：" + UiKit.themeColorLabel(a), null, new View.OnClickListener() {
                    public void onClick(View v) {
                        PageStack s = PageStack.get();
                        if (s != null) s.push(new UiPages.ThemeSheetPage());
                    }
                }, UiKit.accent(a)));
        content.addView(ui.root);

        // ---- 悬浮窗管理 ----
        UiComponents.CardGroup fl = UiComponents.cardGroup(a, "悬浮窗管理", true);
        fl.addRow(entryRowHi(a, "恢复悬浮窗显示", "隐藏后从这里或音量键恢复",
        new View.OnClickListener() {
            public void onClick(View v) {
                HookInit h = ReaLog.sHookInit;
                log("floating", "恢复悬浮窗显示");
                if (h != null) {
                    try {
                        h.uiRestoreFloating(a);
                    } catch (Throwable t) {
                        log("floating", "恢复悬浮窗失败: " + t);
                    }
                }
            }
        }));
        final boolean[] tempArmed = {false};
        LinearLayout tempRow = entryRow(a, "临时隐藏悬浮窗", "双击音量键可恢复显示",
        new View.OnClickListener() {
            public void onClick(View v) {
                View hit = v.findViewWithTag("row_title");
                final TextView tempTitle = hit instanceof TextView ? (TextView) hit : null;
                if (isTempHidden()) {
                    toast(a, "当前已是临时隐藏");
                    return;
                }
                if (!tempArmed[0]) {
                    tempArmed[0] = true;
                    UiKit.haptic(v);
                    if (tempTitle != null) {
                        tempTitle.setTextColor(UiKit.danger(a));
                        tempTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        tempTitle.setText("再点一次确认隐藏");
                    }
                    toast(a, "再点一次确认隐藏");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    tempArmed[0] = false;
                                    if (tempTitle != null) {
                                        tempTitle.setText("临时隐藏悬浮窗");
                                        tempTitle.setTextColor(UiKit.textMain(a));
                                        tempTitle.setTypeface(
                                                android.graphics.Typeface.DEFAULT);
                                    }
                                }
                            }, UiKit.TWO_STEP_ARM_MS);
                    return;
                }
                tempArmed[0] = false;
                HookInit h = ReaLog.sHookInit;
                log("floating", "临时隐藏悬浮窗");
                if (h != null) {
                    try {
                        h.uiTempHideFloating(a, fv);
                    } catch (Throwable t) {
                        log("floating", "临时隐藏失败: " + t);
                    }
                }
            }
        });
        fl.addRow(tempRow);
        final boolean[] permArmed = {false};
        LinearLayout permRow = entryRow(a, "长期隐藏悬浮窗", "三击音量键 +/- 可恢复显示",
        new View.OnClickListener() {
            public void onClick(View v) {
                View hit = v.findViewWithTag("row_title");
                final TextView permTitle = hit instanceof TextView ? (TextView) hit : null;
                if (isPermHidden()) {
                    toast(a, "当前已是长期隐藏");
                    return;
                }
                if (!permArmed[0]) {
                    permArmed[0] = true;
                    UiKit.haptic(v);
                    if (permTitle != null) {
                        permTitle.setTextColor(UiKit.danger(a));
                        permTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        permTitle.setText("再点一次确认长期隐藏");
                    }
                    toast(a, "再点一次确认长期隐藏");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    permArmed[0] = false;
                                    if (permTitle != null) {
                                        permTitle.setText("长期隐藏悬浮窗");
                                        permTitle.setTextColor(UiKit.textMain(a));
                                        permTitle.setTypeface(
                                                android.graphics.Typeface.DEFAULT);
                                    }
                                }
                            }, UiKit.TWO_STEP_ARM_MS);
                    return;
                }
                permArmed[0] = false;
                HookInit h = ReaLog.sHookInit;
                log("floating", "长期隐藏悬浮窗");
                if (h != null) {
                    try {
                        h.uiHideFloating(a, fv);
                    } catch (Throwable t) {
                        log("floating", "长期隐藏失败: " + t);
                    }
                }
            }
        });
        fl.addRow(permRow);
        content.addView(fl.root);

        // ---- 其他 ----
        UiComponents.CardGroup misc = UiComponents.cardGroup(a, "其他", true);
        misc.addRow(entryRowHi(a, "查看日志记录", "Hook 拦截记录日志",
        new View.OnClickListener() {
            public void onClick(View v) {
                log("dialog", "打开日志记录");
                try {
                    ReaLog.showCombinedDialog(a);
                    UiKit.haptic(v);
                } catch (Throwable t) {
                    toast(a, "打开日志失败");
                }
            }
        }));
        misc.addRow(entryRow(a, "清理配置", "清理自动捕获与自定义添加的包名数据",
        new View.OnClickListener() {
            public void onClick(View v) {
                PageStack ps = PageStack.get();
                if (ps != null) ps.push(new UiPages.ClearConfigSheetPage(fv));
            }
        }));
        misc.addRow(entryRowHi(a, "配置备份", "导入 / 导出配置项",
        new View.OnClickListener() {
            public void onClick(View v) {
                PageStack s = PageStack.get();
                if (s != null) s.push(new UiPages.DataPage(fv));
            }
        }));
        LinearLayout resetRow = entryRowHi(a, "恢复初始状态",
        "危险操作：建议先在配置备份里导出一份，再逐项或全部恢复默认",
        new View.OnClickListener() {
            public void onClick(View v) {
                PageStack s = PageStack.get();
                if (s != null) s.push(new UiPages.ResetSheetPage());
            }
        });
        View resetHit = resetRow.findViewWithTag("row_title");
        if (resetHit instanceof TextView) {
            TextView resetTitle = (TextView) resetHit;
            resetTitle.setTextColor(UiKit.danger(a));
            resetTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }
        misc.addRow(resetRow);
        final boolean[] dbgArmed = {false};
        LinearLayout dbgRow = entryRow(a, "调试模式", "按功能逐个控制 Hook 子项",
        new View.OnClickListener() {
            public void onClick(View v) {
                View hit = v.findViewWithTag("row_title");
                final TextView dbgTitle = hit instanceof TextView ? (TextView) hit : null;
                if (!dbgArmed[0]) {
                    dbgArmed[0] = true;
                    UiKit.haptic(v);
                    if (dbgTitle != null) {
                        dbgTitle.setTextColor(UiKit.danger(a));
                        dbgTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                        dbgTitle.setText("再点一次确认打开");
                    }
                    toast(a, "再点一次确认打开");
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .postDelayed(new Runnable() {
                                public void run() {
                                    dbgArmed[0] = false;
                                    if (dbgTitle != null) {
                                        dbgTitle.setText("调试模式");
                                        dbgTitle.setTextColor(UiKit.textMain(a));
                                        dbgTitle.setTypeface(
                                                android.graphics.Typeface.DEFAULT);
                                    }
                                }
                            }, UiKit.TWO_STEP_ARM_MS);
                    return;
                }
                dbgArmed[0] = false;
                log("dialog", "打开调试模式");
                try {
                    com.install.appinstall.xl.util.DebugModeManager.showDebugDialog(a);
                } catch (Throwable t) {
                    toast(a, "打开调试模式失败");
                }
            }
        });
        misc.addRow(dbgRow);
        content.addView(misc.root);

        // ---- 关于 ----
        UiComponents.CardGroup about = UiComponents.cardGroup(a, "关于", true);
        about.addRow(valueRowHi(a, "当前目标应用", app()));
        about.addRow(entryRowHi(a, "检查模块版本更新", "检测 GitHub 是否有新版本",
        new View.OnClickListener() {
            public void onClick(View v) {
                log("update", "手动检查模块版本更新");
                try {
                    Update.checkForUpdate(a);
                } catch (Throwable t) {
                    toast(a, "检查更新失败");
                }
            }
        }));
        about.addRow(entryRow(a, "历史版本", "蓝奏云备用下载通道（密码 ddpa）",
        new View.OnClickListener() {
            public void onClick(View v) {
                UiTabs.openUrlWithPassword(a,
                        "https://yijun01.lanzouu.com/b0pndjjid", "ddpa");
            }
        }));
        // 前往模块主页：宿主页栈内直接 push 模块主页页面，
        // 不跨进程拉起模块 app（用户 m00960 指定）
        about.addRow(entryRow(a, "前往模块主页", "模块功能介绍、状态与说明",
        new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    PageStack s = PageStack.get();
                    if (s != null && s.isOpen()) {
                        s.push(new ModulePages.ModuleHomePage());
                    } else {
                        toast(a, "页面栈未就绪，请重试");
                    }
                } catch (Throwable t) {
                    toast(a, "打开模块主页失败");
                }
            }
        }));
        content.addView(about.root);

        // ---- 链接：复用页脚那三条跳转，按功能列表一行一个 ----
        UiComponents.CardGroup links = UiComponents.cardGroup(a, "链接", true);
        links.addRow(linkRow(a, "作者主页(GitHub)", "作者仓库，看更新与源码",
        "https://github.com/yijun01/com.install.appinstall.xl"));
        links.addRow(linkRow(a, "作者主页(MT论坛)", "反馈问题、交流使用经验",
        "https://bbs.binmt.cc/home.php?mod=space&uid=30974&do=profile"));
        links.addRow(linkRow(a, "模块仓库(GitHub)", "Xposed 模块仓库收录页",
        "https://github.com/Xposed-Modules-Repo/com.install.appinstall.xl"));
        content.addView(links.root);

        // 页脚由 UiComponents.scroll() 统一追加，这里不再单独添加
        return UiComponents.scroll(a, content, "scroll:settings");
    }

    /** 链接行：标题 + 说明 + › ，点开系统浏览器（与页脚同一套跳转） */
    static LinearLayout linkRow(final Activity a, String title, String desc, final String url) {
        return UiComponents.settingRow(a, title, desc, null, new View.OnClickListener() {
            public void onClick(View v) {
                openUrl(a, url);
            }
        });
    }

    static String nightText(Activity a) {
        int m = UiKit.getNightMode(a);
        if (m == UiKit.NIGHT_DAY) return "始终日间";
        if (m == UiKit.NIGHT_NIGHT) return "始终夜间";
        return "跟随宿主（当前" + (UiKit.isNight(a) ? "夜间" : "日间") + "）";
    }

    static void rebuild(Activity a, String msg) {
        PageStack s = PageStack.get();
        if (s != null) s.rebuildAll();
        toast(a, msg);
    }

    /**
     * 页脚：状态 + 作者/声明一行 + 三个跳转链接（第 7/9 条）。 所有页面统一由 UiComponents.scroll() 追加
     */
    static View footer(final Activity a) {
        LinearLayout outer = UiKit.vbox(a);
        outer.setPadding(UiKit.dpI(a, 6), UiKit.dpI(a, 24), UiKit.dpI(a, 6), UiKit.dpI(a, 16));

        LinearLayout box = UiKit.vbox(a);
        int pw = UiKit.dpI(a, 12);
        int pv = UiKit.dpI(a, 10);
        box.setPadding(pw, pv, pw, pv);
        box.setBackground(UiKit.roundRect(a, UiKit.card(a), UiKit.CARD_R));
        TextView state = UiKit.text(a, UiKit.buildLine(a), 11,
                UiKit.withAlpha(UiKit.textSub(a), 0xCC));
        state.setGravity(Gravity.CENTER);
        box.addView(state, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout links = UiKit.hbox(a);
        links.setGravity(Gravity.CENTER);
        links.addView(footerLink(a, "作者主页(GitHub)",
        "https://github.com/yijun01/com.install.appinstall.xl"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        links.addView(footerLink(a, "作者主页(MT论坛)",
        "https://bbs.binmt.cc/home.php?mod=space&uid=30974&do=profile"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        links.addView(footerLink(a, "模块仓库(GitHub)",
        "https://github.com/Xposed-Modules-Repo/com.install.appinstall.xl"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UiKit.dpI(a, 8);
        links.setLayoutParams(lp);
        box.addView(links);

        // 作者 + 声明合成一行，放在链接下面
        TextView author = UiKit.text(a,
                "© 2026 永恒之蓝(小淋) · 仅供个人学习测试 · 禁商用 · 禁引流 · 禁付费盈利",
                10, UiKit.withAlpha(UiKit.textSub(a), 0x99));
        author.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = UiKit.dpI(a, 6);
        author.setLayoutParams(ap);
        box.addView(author);

        outer.addView(box, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return outer;
    }

    /** 页脚链接：主题色小字，点开系统浏览器 */
    private static TextView footerLink(final Activity a, String label, final String url) {
        TextView t = UiKit.text(a, label, 11, UiKit.accent(a));
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        // 窄屏时三个链接各占 1/3 宽，超长就省略，别挤成竖排
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int hp = UiKit.dpI(a, 2);
        int vp = UiKit.dpI(a, 3);
        t.setPadding(hp, vp, hp, vp);
        t.setClickable(true);
        t.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openUrl(a, url);
            }
        });
        UiComponents.pressZoom(t);
        return t;
    }

    /** 打开外部链接：模块跑在宿主进程里，任何一步失败都不能影响宿主，一律吞掉异常 */
    static void openUrl(Activity a, String url) {
        if (url == null || url.length() == 0) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            More.markInternal(intent);
            if (a.getPackageManager().resolveActivity(intent, 0) == null) {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("link", url));
                    }
                } catch (Throwable ignored) {
                }
                toast(a, "系统未提供可打开的目标，链接已复制，可去浏览器粘贴");
                ReaLog.log("open_url", "跳转失败，已复制链接: " + url);
                return;
            }
            a.startActivity(intent);
        } catch (Throwable t) {
            try {
                toast(a, "无法打开链接");
            } catch (Throwable ignored) {
            }
        }
    }

    /** 打开链接并把提取密码复制到剪贴板（蓝奏云这类需要密码的下载页） */
    public static void openUrlWithPassword(Activity a, String url, String pwd) {
        if (url == null || url.length() == 0) return;
        // 复制失败不能静默：用户不知道密码没复制，到下载页手输无门
        boolean copied = false;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
            if (cm != null && pwd != null && pwd.length() > 0) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("password", pwd));
                copied = true;
                toast(a, "密码已复制：" + pwd);
            }
        } catch (Throwable t) {
            copied = false;
        }
        if (!copied && pwd != null && pwd.length() > 0) {
            toast(a, "密码复制失败，请手动记下：" + pwd);
        }
        openUrl(a, url);
    }
}

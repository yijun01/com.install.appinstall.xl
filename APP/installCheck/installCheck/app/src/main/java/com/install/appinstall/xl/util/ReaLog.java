package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.graphics.Typeface;

import com.install.appinstall.xl.HookInit;

public class ReaLog {
    // 分组定义
    private static final Map<String, String> GROUP_DISPLAY_NAMES = new LinkedHashMap<>();
    private static final Map<String, List<String>> GROUP_CATEGORIES = new LinkedHashMap<>();
    private static final Map<String, String> CATEGORY_GROUP_MAP = new ConcurrentHashMap<>();
    private static final String[] GROUP_LAUNCH = {"launch_intercept", "launch"};
    private static final String[] GROUP_EXIT_INTERCEPT = {"exit_intercept"};
    private static final String[] GROUP_ANTI_DETECTION = {"anti_detection", "class", "file", "pm", "proc", "stacktrace", "root", "adb", "dev"};
    private static final String[] GROUP_INSTALL_DETECT = {"install_detect"};
    private static final String[] GROUP_NETWORK_FAKE = {"vpn", "network", "interface", "proxy", "proxy_env", "capture", "netfiles", "ssl_trust", "ssl_cert", "ssl_cert_hide", "net_detect", "ssl_pinning"};
    private static final String[] GROUP_PERMISSION = {"permission"};
    private static final String[] GROUP_QUERY = {"intent_query", "package_query", "binder"};
    private static final String[] GROUP_AUTO = {"auto"};
    private static final String[] GROUP_SYSTEM_LOW = {"file_system", "cmd", "flutter"};
    private static final String[] GROUP_SYSTEM = {"system", "config"};
    private static final String[] GROUP_MISC = {"update", "back_key", "floating", "time_fake", "selinux", "share_fake", "share_detect", "dialog", "Window", "more", "vibrator", "buHook"};
    private static final String[] GROUP_KEYS_ORDER = {
            "query", "install_detect", "anti_detection", "network_fake",
            "system_low", "launch", "auto", "exit_intercept", "binder",
            "permission", "misc", "system"
    };

    static {
        registerGroup("launch", "应用跳转", GROUP_LAUNCH);
        registerGroup("exit_intercept", "调度拦截", GROUP_EXIT_INTERCEPT);
        registerGroup("anti_detection", "痕迹隐藏", GROUP_ANTI_DETECTION);
        registerGroup("install_detect", "应用检测", GROUP_INSTALL_DETECT);
        registerGroup("network_fake", "网络数据", GROUP_NETWORK_FAKE);
        registerGroup("permission", "权限防护", GROUP_PERMISSION);
        registerGroup("query", "应用查询", GROUP_QUERY);
        registerGroup("auto", "智能处理", GROUP_AUTO);
        registerGroup("system_low", "应用响应", GROUP_SYSTEM_LOW);
        registerGroup("system", "模块状态", GROUP_SYSTEM);
        registerGroup("misc", "辅助功能", GROUP_MISC);
    }

    private static void registerGroup(String groupKey, String displayName, String[] categories) {
        GROUP_DISPLAY_NAMES.put(groupKey, displayName);
        GROUP_CATEGORIES.put(groupKey, new ArrayList<>(Arrays.asList(categories)));
        for (String cat : categories) {
            CATEGORY_GROUP_MAP.put(cat, groupKey);
        }
    }

    // ---------- 日志去重控制 ----------
    private static final Map<String, Long> sLastLogTime = new ConcurrentHashMap<>();
    private static final long LOG_DEDUP_INTERVAL_MS = 800; // 0.8秒内相同不重复 0为关闭去重
    private static final int MAX_DEDUP_CACHE_SIZE = 1000; // 去重缓存条目，超过清空去重缓存

    // ============================================================
    // 缓存容量动态计算
    // ============================================================
    private static final int MIN_CACHE_SIZE = 15000; // 最少缓存条目
    private static final int MAX_CACHE_LIMIT = 500000; // 最多缓存条目
    private static final int ESTIMATED_BYTES_PER_LOG = 280; // 计算字节量
    private static final int MIN_PERCENT = 3; // 最少缓存百分比
    private static final int MAX_PERCENT = 10; // 最多缓存百分比
    private static final int MAX_CACHE_SIZE;
    private static final int MAX_DISPLAY_ITEMS = 20000; // 显示列表上限2万条
    static {
        int fallback = 15000, result = fallback; // 安全值15000
        try {
            long maxMem = Runtime.getRuntime().maxMemory();
            if (maxMem < 64 * 1024 * 1024) maxMem = 64 * 1024 * 1024;
            long low = 64 * 1024 * 1024, high = 512 * 1024 * 1024;
            double pct;
            if (maxMem <= low) pct = MIN_PERCENT;
            else if (maxMem >= high) pct = MAX_PERCENT;
            else
                pct = MIN_PERCENT + (double) (maxMem - low) / (high - low) * (MAX_PERCENT - MIN_PERCENT);
            if (pct < MIN_PERCENT) pct = MIN_PERCENT;
            if (pct > MAX_PERCENT) pct = MAX_PERCENT;
            long targetMem = (long) (maxMem * (pct / 100.0)); // 动态计算缓存百分比
            int calc = (int) (targetMem / ESTIMATED_BYTES_PER_LOG);
            if (calc < MIN_CACHE_SIZE) calc = MIN_CACHE_SIZE;
            if (calc > MAX_CACHE_LIMIT) calc = MAX_CACHE_LIMIT;
            result = calc;
        } catch (Throwable t) {
            try {
                Class<?> atClass = Class.forName("android.app.ActivityThread");
                java.lang.reflect.Method m = atClass.getMethod("currentApplication");
                Context ctx = (Context) m.invoke(null);
                if (ctx != null) {
                    android.app.ActivityManager am = (android.app.ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                        am.getMemoryInfo(mi);
                        long totalMem = mi.totalMem;
                        long targetMem = totalMem / 300; // 兜底使用物理内存2%
                        int calc = (int) (targetMem / ESTIMATED_BYTES_PER_LOG);
                        if (calc < MIN_CACHE_SIZE) calc = MIN_CACHE_SIZE;
                        if (calc > MAX_CACHE_LIMIT) calc = MAX_CACHE_LIMIT;
                        result = calc;
                    }
                }
            } catch (Throwable t2) {
                result = fallback; // 全部失败兜底安全值
            }
        }
        MAX_CACHE_SIZE = result;
    }

    // ============================================================
    // 日志核心数据结构
    // ============================================================
    private static final LinkedList<LogItem> sCache = new LinkedList<>();
    private static final List<LogItem> sDisplayList = new ArrayList<>();
    private static final Map<String, Integer> sContentCount = new ConcurrentHashMap<>();
    private static int sTotalCount = 0;

    // ============================================================
    // 暂停状态管理
    // ============================================================
    private static final Map<String, Boolean> sPauseCache = new ConcurrentHashMap<>();
    public static HookInit sHookInit;
    private static Button sPauseBtn;

    // ============================================================
    // UI 控件引用
    // ============================================================
    private static ListView sListView;
    private static LogAdapter sAdapter;
    private static TextView sTitleMainText;
    private static TextView sTitleStatusText;
    private static TextView sSubtitleText;
    private static TextView sBottomTipText;
    private static LinearLayout sCategoryContainer;
    private static Button sClearBtn;
    private static volatile boolean sDialogActive = false;
    private static volatile boolean sMergedMode = false;
    private static volatile boolean sIsAtBottom = true;
    private static String sCurrentGroup = "all";
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());
    private static final Runnable sRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            performRefresh();
        }
    };
    private static final int REFRESH_DELAY = 400; // 节流刷新时间ms

    // ============================================================
    // LogItem
    // ============================================================
    public static class LogItem {
        public String content, category;
        public int count;
        public long time;
        public boolean isSystemTip;

        public LogItem(String category, String content, int count) {
            this(category, content, count, false);
        }

        public LogItem(String category, String content, int count, boolean isSystemTip) {
            this.category = category;
            this.content = content;
            this.count = count;
            this.time = System.currentTimeMillis();
            this.isSystemTip = isSystemTip;
        }

        public String getTimeStr() {
            try {
                return new SimpleDateFormat("HH:mm:ss").format(new Date(time));
            } catch (Throwable e) {
                return "00:00:00";
            }
        }

        public String toHtml(int index) {
            String prefix = isSystemTip ? "⚠️ " : "";
            String color = isSystemTip ? "#FF5722" : "#333333"; // 系统提示用橙色，普通日志用深灰
            return "<font color='#999999'><b>[" + index + "]</b></font> " +
                    "<font color='#66BB6A'><b>[" + getTimeStr() + "]</b></font>  " +
                    "<font color='" + color + "'>" + prefix + content + "</font>" +
                    (isSystemTip ? "" : " <font color='#FF9800'><b>(检测了" + count + "次)</b></font>");
        }

        // 复制追加
        public String toPlainText(int index) {
            String prefix = isSystemTip ? "" : ""; // 无日志复制追加 "[系统]" : "";
            return "[" + index + "] [" + getTimeStr() + "] " + prefix + content +
                    (isSystemTip ? "" : " (检测了" + count + "次)"); // 真实日志
        }
    }

    // ============================================================
    // Adapter日志适配器
    // ============================================================
    private static class LogAdapter extends BaseAdapter {
        private List<LogItem> data;

        LogAdapter(List<LogItem> data) {
            this.data = data;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int pos) {
            return data.get(pos);
        }

        @Override
        public long getItemId(int pos) {
            return pos;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            final Context context = parent.getContext();
            TextView tv;
            LinearLayout ll;
            if (convertView == null) {
                ll = new LinearLayout(context);
                ll.setOrientation(LinearLayout.VERTICAL);
                ll.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView textView = new TextView(context);
                textView.setTextSize(12);
                textView.setTextColor(0xFF333333);
                textView.setLineSpacing(4, 1);
                textView.setPadding(More.spx(context, 20), More.spx(context, 10), More.spx(context, 20), More.spx(context, 10));
                textView.setMaxLines(20);
                textView.setEllipsize(TextUtils.TruncateAt.END);
                textView.setHorizontallyScrolling(false);
                ll.addView(textView);
                View line = new View(context);
                line.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                line.setBackgroundColor(0xFFE0E0E0);
                ll.addView(line);
                ll.setTag(textView);
                convertView = ll;
                tv = textView;
            } else {
                ll = (LinearLayout) convertView;
                tv = (TextView) ll.getTag();
            }

            final LogItem item = data.get(position);
            Spanned spanned;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                spanned = Html.fromHtml(item.toHtml(position + 1), Html.FROM_HTML_MODE_LEGACY);
            } else {
                spanned = Html.fromHtml(item.toHtml(position + 1));
            }
            tv.setText(spanned);

            convertView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    com.install.appinstall.xl.ui.UiKit.haptic(v);
                    try {
                        String plain = item.toPlainText(position + 1);
                        ClipboardManager cb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cb == null) {
                            ToastUtil.showUnique(context, "❌ 复制失败：系统服务不可用");
                            return true;
                        }
                        cb.setPrimaryClip(ClipData.newPlainText("log_entry", plain));
                        String preview = plain.length() > 200 ? plain.substring(0, 197) + "..." : plain;
                        ToastUtil.showUnique(context, "✅ 复制成功:\n " + preview);
                        return true;
                    } catch (Throwable e) {
                        ToastUtil.showUnique(context, "❌ 复制失败：" + e.getMessage());
                        return true;
                    }
                }
            });
            return convertView;
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================
    private static String getGroupKey(String category) {
        String g = CATEGORY_GROUP_MAP.get(category);
        return g != null ? g : category;
    }

    private static String getGroupDisplayName(String groupKey) {
        String name = GROUP_DISPLAY_NAMES.get(groupKey);
        return name != null ? name : groupKey;
    }

    private static String getShortGroupName(String groupKey) {
        String full = getGroupDisplayName(groupKey);
        return full.length() > 2 ? full.substring(0, 2) : full;
    }

    private static List<String> getOrderedGroupKeys() {
        List<String> order = new ArrayList<>();
        order.add("all");
        for (String key : GROUP_KEYS_ORDER) {
            if (GROUP_DISPLAY_NAMES.containsKey(key)) order.add(key);
        }
        boolean hasOther = false;
        for (LogItem item : sCache) {
            if (item.isSystemTip) continue;
            String g = getGroupKey(item.category);
            if (!GROUP_DISPLAY_NAMES.containsKey(g)) {
                hasOther = true;
                break;
            }
        }
        if (hasOther) order.add("other");
        return order;
    }

    private static boolean hasRealLogsForGroup(String groupKey) {
        if (groupKey == null) return false;
        if ("all".equals(groupKey)) {
            for (LogItem item : sCache) {
                if (!item.isSystemTip) return true;
            }
            return false;
        }
        for (LogItem item : sCache) {
            if (item.isSystemTip) continue;
            if ("other".equals(groupKey)) {
                if (!GROUP_DISPLAY_NAMES.containsKey(getGroupKey(item.category))) return true;
            } else {
                if (groupKey.equals(getGroupKey(item.category))) return true;
            }
        }
        return false;
    }

    private static void switchToNextNonEmptyGroup() {
        if (sCache.isEmpty()) {
            sCurrentGroup = "all";
            return;
        }
        List<String> ordered = getOrderedGroupKeys();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LogItem item : sCache) {
            if (item.isSystemTip) continue;
            String g = getGroupKey(item.category);
            Integer cnt = counts.get(g);
            counts.put(g, (cnt == null) ? 1 : cnt + 1);
        }
        if (counts.containsKey(sCurrentGroup) && counts.get(sCurrentGroup) > 0) return;

        int start = ordered.indexOf(sCurrentGroup);
        if (start < 0) start = ordered.size();
        int size = ordered.size();
        for (int i = start + 1; i < size; i++) {
            String g = ordered.get(i);
            if (!"all".equals(g) && counts.containsKey(g) && counts.get(g) > 0) {
                sCurrentGroup = g;
                return;
            }
        }
        for (int i = start - 1; i >= 0; i--) {
            String g = ordered.get(i);
            if (!"all".equals(g) && counts.containsKey(g) && counts.get(g) > 0) {
                sCurrentGroup = g;
                return;
            }
        }
        sCurrentGroup = "all";
    }

    // ============================================================
    // 清理功能
    // ============================================================
    public static void clearCategory(String groupKey) {
        if (groupKey == null) return;
        if (!hasRealLogsForGroup(groupKey)) {
            Context ctx = sTitleMainText != null ? sTitleMainText.getContext() : null;
            if (ctx != null) {
                ToastUtil.showUnique(ctx, "当前分类无日志可清理");
            }
            return;
        }
        if ("all".equals(groupKey)) {
            clearAll();
            return;
        }

        Iterator<LogItem> it = sCache.iterator();
        while (it.hasNext()) {
            LogItem item = it.next();
            if (item.isSystemTip) continue;
            String itemGroup = getGroupKey(item.category);
            boolean match;
            if ("other".equals(groupKey)) {
                match = !GROUP_DISPLAY_NAMES.containsKey(itemGroup);
            } else {
                match = groupKey.equals(itemGroup);
            }
            if (match) {
                sTotalCount -= item.count;
                Integer cnt = sContentCount.get(item.content);
                if (cnt != null) {
                    if (cnt <= item.count) sContentCount.remove(item.content);
                    else sContentCount.put(item.content, cnt - item.count);
                }
                it.remove();
            }
        }

        switchToNextNonEmptyGroup();
        rebuildDisplayList();
        if (sAdapter != null) {
            sAdapter.notifyDataSetChanged();
            updateTitle();
            rebuildCategoryButtons();
            updatePauseButton();
        }
    }

    public static void clearAll() {
        if (!hasRealLogsForGroup("all")) {
            Context ctx = sTitleMainText != null ? sTitleMainText.getContext() : null;
            if (ctx != null) {
                ToastUtil.showUnique(ctx, "当前分类无日志可清理");
            }
            return;
        }
        sMainHandler.removeCallbacks(sRefreshRunnable);
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                sCache.clear();
                sContentCount.clear();
                sTotalCount = 0;
                sCurrentGroup = "all";
                rebuildDisplayList();
                if (sAdapter != null) sAdapter.notifyDataSetChanged();
                updateTitle();
                rebuildCategoryButtons();
                updatePauseButton();
            }
        });
    }

    // ============================================================
    // 列表重建
    // ============================================================
    private static void rebuildDisplayList() {
        sDisplayList.clear();
        List<LogItem> filtered = new ArrayList<>();
        if ("all".equals(sCurrentGroup)) {
            filtered.addAll(sCache);
        } else if ("other".equals(sCurrentGroup)) {
            for (LogItem item : sCache) {
                if (!GROUP_DISPLAY_NAMES.containsKey(getGroupKey(item.category))) {
                    filtered.add(item);
                }
            }
        } else {
            for (LogItem item : sCache) {
                if (sCurrentGroup.equals(getGroupKey(item.category))) {
                    filtered.add(item);
                }
            }
        }

        if (sMergedMode) {
            Map<String, LogItem> merged = new LinkedHashMap<>();
            for (LogItem item : filtered) {
                // 使用 sContentCount 获取最新计数，确保去重后计数准确
                int realCount = sContentCount.getOrDefault(item.content, item.count);
                LogItem exist = merged.get(item.content);
                if (exist == null) {
                    LogItem copy = new LogItem(item.category, item.content, realCount, item.isSystemTip);
                    copy.time = item.time;
                    merged.put(item.content, copy);
                } else {
                    // 更新为最新计数
                    exist.count = realCount;
                }
            }
            sDisplayList.addAll(merged.values());
        } else {
            sDisplayList.addAll(filtered);
        }

        // 限制显示列表长度，避免全量造成内存峰值。保留最新 MAX_DISPLAY_ITEMS 条。
        if (sDisplayList.size() > MAX_DISPLAY_ITEMS) {
            List<LogItem> trimmed = new ArrayList<>(MAX_DISPLAY_ITEMS);
            for (int i = sDisplayList.size() - MAX_DISPLAY_ITEMS; i < sDisplayList.size(); i++) {
                trimmed.add(sDisplayList.get(i));
            }
            sDisplayList.clear();
            sDisplayList.addAll(trimmed);
        }

        // 全部或子分类为空时的提示
        if ("all".equals(sCurrentGroup) && sDisplayList.isEmpty()) {
            sDisplayList.add(new LogItem("system", " 该分类没有新日志...等待中...", 1, true));
        }
        if (!"all".equals(sCurrentGroup) && sDisplayList.isEmpty()) {
            boolean paused = isPaused(sCurrentGroup);
            if (paused) {
                String shortName;
                if ("other".equals(sCurrentGroup)) {
                    shortName = "其他";
                } else {
                    shortName = getShortGroupName(sCurrentGroup);
                }
                String tip = "[" + shortName + "] 该分类已暂停记录，暂无日志";
                sDisplayList.add(new LogItem(sCurrentGroup, tip, 1, true));
            }
        }
    }

    private static int getCurrentGroupTotalCount() {
        int total = 0;
        for (LogItem item : sCache) {
            if (item.isSystemTip) continue;
            if ("all".equals(sCurrentGroup)) total += item.count;
            else if ("other".equals(sCurrentGroup)) {
                if (!GROUP_DISPLAY_NAMES.containsKey(getGroupKey(item.category)))
                    total += item.count;
            } else {
                if (sCurrentGroup.equals(getGroupKey(item.category))) total += item.count;
            }
        }
        return total;
    }

    private static void performRefresh() {
        if (sAdapter == null) return;
        rebuildDisplayList();
        sAdapter.notifyDataSetChanged();
        updateTitle();
        rebuildCategoryButtons();
        updatePauseButton();
        if (sIsAtBottom && sListView != null && sAdapter.getCount() > 0) {
            sListView.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        sListView.setSelection(sAdapter.getCount() - 1);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    private static void scheduleRefresh() {
        sMainHandler.removeCallbacks(sRefreshRunnable);
        sMainHandler.postDelayed(sRefreshRunnable, REFRESH_DELAY);
    }

    // ============================================================
    // 更新标题
    // ============================================================
    private static void updateTitle() {
        if (sTitleMainText != null) {
            sTitleMainText.setText(" 实时日志 - 安装防护模块");
        }
        if (sTitleStatusText != null) {
            boolean paused = isPaused(sCurrentGroup);
            sTitleStatusText.setText(paused ? "● 暂停中" : "● 记录中");
            sTitleStatusText.setTextColor(paused ? 0xFFF44336 : 0xFF4CAF50);
        }
        if (sSubtitleText != null) {
            String groupName = "全部";
            if ("other".equals(sCurrentGroup)) groupName = "其他";
            else if (!"all".equals(sCurrentGroup)) groupName = getGroupDisplayName(sCurrentGroup);
            String mode = sMergedMode ? "合并" : "展开";
            int count = sDisplayList.size();
            int total = getCurrentGroupTotalCount();
            sSubtitleText.setText("分类：" + groupName + "  |  模式：" + mode + "  |  共 " + count + " 条，总检测 " + total + " 次");
        }
        if (sClearBtn != null) {
            String groupName = "全部";
            if ("other".equals(sCurrentGroup)) groupName = "其他";
            else if (!"all".equals(sCurrentGroup)) groupName = getShortGroupName(sCurrentGroup);
            sClearBtn.setText("清理(" + groupName + ")");
        }
    }

    // ============================================================
    // 分类菜单重建
    // ============================================================
    private static void rebuildCategoryButtons() {
        if (sCategoryContainer == null) return;
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sCategoryContainer == null) return;
                Context ctx = sCategoryContainer.getContext();
                if (!(ctx instanceof Activity)) return;
                final Activity act = (Activity) ctx;
                sCategoryContainer.removeAllViews();
                Map<String, Integer> realLogCounts = new LinkedHashMap<>();
                for (LogItem item : sCache) {
                    if (item.isSystemTip) continue;
                    String g = getGroupKey(item.category);
                    Integer cnt = realLogCounts.get(g);
                    realLogCounts.put(g, (cnt == null) ? 1 : cnt + 1);
                }
                addCategoryButton(act, "全部", "all", true);
                boolean isGlobalPaused = sPauseCache.getOrDefault("all", false);
                for (String key : GROUP_KEYS_ORDER) {
                    boolean hasRealLog = realLogCounts.containsKey(key) && realLogCounts.get(key) > 0;
                    boolean isIndividuallyPaused = sPauseCache.getOrDefault(key, false);
                    boolean shouldShow;
                    if (isGlobalPaused) {
                        shouldShow = hasRealLog || isIndividuallyPaused;
                    } else {
                        shouldShow = hasRealLog || isPaused(key);
                    }
                    if (shouldShow) {
                        addCategoryButton(act, getGroupDisplayName(key), key, hasRealLog);
                    }
                }
                boolean hasOtherLog = realLogCounts.containsKey("other") && realLogCounts.get("other") > 0;
                boolean isOtherIndividuallyPaused = sPauseCache.getOrDefault("other", false);
                boolean shouldShowOther;
                if (isGlobalPaused) {
                    shouldShowOther = hasOtherLog || isOtherIndividuallyPaused;
                } else {
                    shouldShowOther = hasOtherLog || isPaused("other");
                }
                if (shouldShowOther) {
                    addCategoryButton(act, "其他", "other", hasOtherLog);
                }
            }
        });
    }

    private static void addCategoryButton(final Activity act, String displayName, final String key, boolean hasRealLog) {
        TextView tv = new TextView(act);
        String text = hasRealLog ? displayName : displayName + "(空)";
        tv.setText(text);
        tv.setTextSize(13);
        tv.setPadding(More.spx(act, 30), More.spx(act, 12), More.spx(act, 30), More.spx(act, 12));
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(50);
        boolean isSelected = key.equals(sCurrentGroup);
        if (isSelected) {
            bg.setColor(0xFF2196F3);
            tv.setTextColor(Color.WHITE);
            bg.setStroke(2, 0xFF1976D2);
        } else if (!hasRealLog) {
            bg.setColor(0xFFE0E0E0);
            tv.setTextColor(0xFF888888);
            bg.setStroke(1, 0xFFCCCCCC);
        } else {
            bg.setColor(0xFFE3F2FD);
            tv.setTextColor(0xFF1976D2);
            bg.setStroke(1, 0xFFBBDEFB);
        }
        tv.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 0, 8, 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sCurrentGroup = key;
                rebuildCategoryButtons();
                updatePauseButton();
                performRefresh();
            }
        });
        sCategoryContainer.addView(tv);
    }

    // ============================================================
    // 添加日志核心
    // ============================================================
    private static void addLog(final String category, final String detail) {
        if (isPaused("all")) return;
        String group = getGroupKey(category);
        if (isPaused(group)) return;
        // ----- 去重检查（不跳过计数，只跳过写入新条目） -----
        String key = category + "||" + detail;
        Long lastTime = sLastLogTime.get(key);
        long now = System.currentTimeMillis();
        if (lastTime != null && (now - lastTime) < LOG_DEDUP_INTERVAL_MS) {
            // 命中去重：只增加计数，不创建新 LogItem
            sTotalCount++;
            Integer cnt = sContentCount.get(detail);
            if (cnt == null) sContentCount.put(detail, 1);
            else sContentCount.put(detail, cnt + 1);
            // 注意：缓存中的已有 LogItem.count 会在下次刷新时从 sContentCount 更新
            return;
        }
        sLastLogTime.put(key, now);
        // 控制缓存大小
        if (sLastLogTime.size() > MAX_DEDUP_CACHE_SIZE) {
            sLastLogTime.clear();
        }

        // ----- 写入逻辑（仅在非去重命中时执行） -----
        sTotalCount++;
        Integer cnt = sContentCount.get(detail);
        if (cnt == null) cnt = 1;
        else cnt += 1;
        sContentCount.put(detail, cnt);
        final int finalCount = cnt;
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                LogItem item = new LogItem(category, detail, finalCount);
                sCache.add(item);
                if (sCache.size() > MAX_CACHE_SIZE) sCache.removeFirst();
                if (sDialogActive) scheduleRefresh();
            }
        });
    }

    // ============================================================
    // 日志接口
    // ============================================================
    public static void log(String category, String detail) {
        if (TextUtils.isEmpty(category) || TextUtils.isEmpty(detail)) return;
        addLog(category, detail);
    }

    public static void antiDetection(String key, String action, String target) {
        String name = sAntiNames.getOrDefault(key, key);
        String detail = name + "：" + action + (TextUtils.isEmpty(target) ? "" : "(" + target + ")");
        log("anti_detection", detail);
    }

    public static void vpn(String key, String action, String target) {
        String name = sVpnNames.getOrDefault(key, key);
        String detail = name + "：" + action + (TextUtils.isEmpty(target) ? "" : "(" + target + ")");
        log("vpn", detail);
    }

    public static void antiDetection(String detail) {
        log("anti_detection", detail);
    }

    public static void vpn(String detail) {
        log("vpn", detail);
    }

    private static volatile Map<String, String> sAntiNames = new LinkedHashMap<>();
    private static volatile Map<String, String> sVpnNames = new LinkedHashMap<>();

    // ============================================================
    // 暂停管理
    // ============================================================
    public static void init(HookInit hookInit) {
        sHookInit = hookInit;
        refreshCache();
    }

    public static void reloadPauseState() {
        if (sHookInit != null) refreshCache();
    }

    private static void refreshCache() {
        if (sHookInit == null) return;
        String pkg = sHookInit.getCurrentTargetApp();
        if (pkg == null) return;
        Map<String, Boolean> map = HookInit.reaLogPauseMap.get(pkg);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            HookInit.reaLogPauseMap.put(pkg, map);
        }
        sPauseCache.clear();
        sPauseCache.putAll(map);
        // 修复：updatePauseButton 涉及 View 操作，若 refreshCache 在非主线程被调用会抛 CalledFromWrongThreadException，冒泡到 handleLoadPackage 的 catch 导致裸createDefaultConfig 重置全部配置严重问题)
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                updatePauseButton();
            } else {
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updatePauseButton();
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isPaused(String groupKey) {
        if (groupKey == null) groupKey = "all";
        if (sPauseCache.getOrDefault("all", false)) {
            return true;
        }
        if ("all".equals(groupKey)) {
            return false;
        }
        Boolean paused = sPauseCache.get(groupKey);
        return paused != null && paused;
    }

    public static void setPaused(String groupKey, boolean paused) {
        if (groupKey == null) groupKey = "all";
        if (sPauseCache.getOrDefault("all", false) && !"all".equals(groupKey)) {
            final Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
            if (ctx != null) {
                sMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.showUnique(ctx, "全局记录暂停中\n请先继续“全部类”记录");
                    }
                });
            }
            return;
        }
        if ("all".equals(groupKey)) {
            sPauseCache.put("all", paused);
        } else {
            sPauseCache.put(groupKey, paused);
        }
        if (sHookInit != null) {
            String pkg = sHookInit.getCurrentTargetApp();
            if (pkg != null) {
                Map<String, Boolean> map = HookInit.reaLogPauseMap.get(pkg);
                if (map == null) {
                    map = new ConcurrentHashMap<>();
                    HookInit.reaLogPauseMap.put(pkg, map);
                }
                map.put(groupKey, paused);
                sHookInit.saveConfigToFile();
            }
        }

        updatePauseButton();
        performRefresh();
        rebuildCategoryButtons();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Boolean> getPauseMap() {
        return (Map<String, Boolean>) new HashMap<>(sPauseCache);
    }

    // ============================================================
    // 暂停按钮更新
    // ============================================================
    private static void updatePauseButton() {
        if (sPauseBtn == null) return;
        boolean effectivePaused = isPaused(sCurrentGroup);
        String prefix;
        if ("all".equals(sCurrentGroup)) {
            prefix = "全部";
        } else if ("other".equals(sCurrentGroup)) {
            prefix = "其他";
        } else {
            String name = getGroupDisplayName(sCurrentGroup);
            prefix = name.length() >= 2 ? name.substring(0, 2) : name;
        }
        sPauseBtn.setText((effectivePaused ? "暂停中" : "记录中") + "(" + prefix + ")");
        if (sPauseCache.getOrDefault("all", false)) {
            setButtonStyle(sPauseBtn, 0xFFF44336, Color.WHITE);
        } else {
            setButtonStyle(sPauseBtn, effectivePaused ? 0xFFF44336 : 0xFF4CAF50, Color.WHITE);
        }
    }

    private static void setButtonStyle(Button btn, int bgColor, int textColor) {
        try {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(bgColor);
            drawable.setCornerRadius(25f);
            btn.setBackground(drawable);
            btn.setTextColor(textColor);
            btn.setPadding(More.spx(btn.getContext(), 20), More.spx(btn.getContext(), 6), More.spx(btn.getContext(), 20), More.spx(btn.getContext(), 6));
            btn.setTextSize(12);
            btn.setMinHeight(0);
        } catch (Throwable t) {
            btn.setBackgroundColor(bgColor);
            btn.setTextColor(textColor);
        }
    }

    // ============================================================
    // 辅助方法：获取当前有效的 Activity（优先从 HookInit 获取）
    // ============================================================
    private static Activity getCurrentValidActivity() {
        if (sHookInit != null) {
            Activity act = sHookInit.getCurrentActivity();
            if (act != null && !act.isFinishing() &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !act.isDestroyed())) {
                return act;
            }
        }
        return null;
    }

    // ============================================================
    // 显示对话框
    // ============================================================
    public static void showCombinedDialog(final Activity activityParam) {
        if (sDialogActive) {
            if (sListView == null || sAdapter == null || sListView.getContext() == null) {
                // 对话框已失效，重置标志
                sDialogActive = false;
            } else {
                // 对话框依然存在，尝试将其带到前台
                Context ctx = sListView.getContext();
                if (ctx instanceof Activity) {
                    Activity act = (Activity) ctx;
                    if (!act.isFinishing() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !act.isDestroyed())) {
                        // 已打开，Toast 提示
                        ToastUtil.showUnique(act, "日志对话框已打开");
                        return;
                    } else {
                        // Activity 已销毁，重置
                        sDialogActive = false;
                    }
                } else {
                    // 上下文无效，重置
                    sDialogActive = false;
                }
            }
        }

        // 获取有效的 Activity（用于构建 Dialog）
        Activity validActivity = activityParam;
        if (validActivity == null || validActivity.isFinishing() ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && validActivity.isDestroyed())) {
            validActivity = getCurrentValidActivity();
        }
        if (validActivity == null) {
            Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
            if (ctx != null) {
                ToastUtil.showUnique(ctx, "当前Activity无效，无法打开日志\n请尝试结束应用恢复");
            }
            return;
        }

        // 重置内部状态
        sDialogActive = true;
        sIsAtBottom = true;
        sMainHandler.removeCallbacks(sRefreshRunnable);
        sClearBtn = null;

        final Activity activity = validActivity;

        try {
            final LinearLayout rootLayout = new LinearLayout(activity);
            rootLayout.setOrientation(LinearLayout.VERTICAL);
            rootLayout.setPadding(More.spx(activityParam, 20), More.spx(activityParam, 0), More.spx(activityParam, 0), More.spx(activityParam, 20)); // 左上右下
            rootLayout.setBackgroundColor(Color.WHITE);
            rootLayout.setFitsSystemWindows(true);
            rootLayout.setTag("rea_log_dialog");

            // ---- 顶部标题 ----
            /*
            bottomLayout.setGravity(Gravity.CENTER_VERTICAL); // 垂直居中
            bottomLayout.setPadding(More.spx(activityParam, 20), More.spx(activityParam, 10), More.spx(activityParam, 20), navBarHeight + More.spx(activityParam, 10)); // 调整上下外边距
            */
            LinearLayout topLayout = new LinearLayout(activity);
            topLayout.setOrientation(LinearLayout.HORIZONTAL);
            topLayout.setGravity(Gravity.CENTER_VERTICAL);
            topLayout.setPadding(More.spx(activityParam, 30), More.spx(activityParam, 10), More.spx(activityParam, 20), More.spx(activityParam, 10));
            TextView titleMain = new TextView(activity);
            titleMain.setText(" 实时日志 - 安装防护模块");
            titleMain.setTextSize(18);
            titleMain.setTextColor(0xFF333333);
            titleMain.setTypeface(titleMain.getTypeface(), android.graphics.Typeface.BOLD);
            titleMain.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            topLayout.addView(titleMain);
            sTitleMainText = titleMain;
            TextView titleStatus = new TextView(activity);
            titleStatus.setText("● 记录中");
            titleStatus.setTextSize(14);
            titleStatus.setTextColor(0xFF4CAF50);
            titleStatus.setPadding(More.spx(activityParam, 30), More.spx(activityParam, 0), More.spx(activityParam, 40), More.spx(activityParam, 0)); // 左上右下
            topLayout.addView(titleStatus);
            sTitleStatusText = titleStatus;
            rootLayout.addView(topLayout);

            // ---- 第二行 ----
            TextView subtitle = new TextView(activity);
            subtitle.setText("分类：全部  |  模式：展开  |  共 0 条，总检测 0 次");
            subtitle.setTextSize(13);
            subtitle.setTextColor(0xFF666666);
            subtitle.setPadding(More.spx(activityParam, 30), More.spx(activityParam, 0), More.spx(activityParam, 20), More.spx(activityParam, 20));
            subtitle.setMaxLines(3); // 最多3行
            subtitle.setEllipsize(TextUtils.TruncateAt.END); // 超出部分显示省略号
            rootLayout.addView(subtitle);
            sSubtitleText = subtitle;

            // ---- 按钮栏 ----
            HorizontalScrollView buttonScroll = new HorizontalScrollView(activity);
            buttonScroll.setHorizontalScrollBarEnabled(false);
            buttonScroll.setPadding(More.spx(activityParam, 30), More.spx(activityParam, 0), More.spx(activityParam, 20), More.spx(activityParam, 10));
            LinearLayout buttonBar = new LinearLayout(activity);
            buttonBar.setOrientation(LinearLayout.HORIZONTAL);
            buttonBar.setGravity(Gravity.CENTER_VERTICAL);
            final Button modeBtn = new Button(activity);
            modeBtn.setText(sMergedMode ? "数据合并" : "数据展开");
            setButtonStyle(modeBtn, sMergedMode ? 0xFF4CAF50 : 0xFF2196F3, Color.WHITE);
            Button topBtn = new Button(activity);
            topBtn.setText("顶部");
            setButtonStyle(topBtn, 0xFF607D8B, Color.WHITE);
            Button bottomBtn = new Button(activity);
            bottomBtn.setText("底部");
            setButtonStyle(bottomBtn, 0xFF607D8B, Color.WHITE);
            final Button pauseBtn = new Button(activity);
            pauseBtn.setText("记录中(全部)");
            setButtonStyle(pauseBtn, 0xFF4CAF50, Color.WHITE);
            sPauseBtn = pauseBtn;
            updatePauseButton();
            final Button clearBtn = new Button(activity);
            clearBtn.setText("清理(全部)");
            setButtonStyle(clearBtn, 0xFFF44336, Color.WHITE);
            sClearBtn = clearBtn;
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.setMargins(30, 0, 12, 0);
            buttonBar.addView(modeBtn, btnParams);
            buttonBar.addView(topBtn, btnParams);
            buttonBar.addView(bottomBtn, btnParams);
            buttonBar.addView(pauseBtn, btnParams);
            LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clearParams.setMargins(20, 0, 12, 0);
            buttonBar.addView(clearBtn, clearParams);
            buttonScroll.addView(buttonBar);
            rootLayout.addView(buttonScroll);
            
            // ---- 分类菜单 ----
            HorizontalScrollView scrollViewCategories = new HorizontalScrollView(activity);
            scrollViewCategories.setHorizontalScrollBarEnabled(false);
            scrollViewCategories.setPadding(More.spx(activityParam, 20), More.spx(activityParam, 10), More.spx(activityParam, 20), More.spx(activityParam, 10));
            LinearLayout categoryContainer = new LinearLayout(activity);
            categoryContainer.setOrientation(LinearLayout.HORIZONTAL);
            categoryContainer.setGravity(Gravity.CENTER_VERTICAL);
            sCategoryContainer = categoryContainer;
            rebuildCategoryButtons();
            scrollViewCategories.addView(categoryContainer);
            rootLayout.addView(scrollViewCategories);
            
            // ---- ListView ----
            final ListView listView = new ListView(activity);
            listView.setScrollbarFadingEnabled(true);
            listView.setVerticalScrollBarEnabled(true);
            listView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
            listView.setFastScrollEnabled(true);
            LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            
            // ---------- 日志列表添加左右边距(含滚动条) ----------
            listParams.leftMargin = More.dpInt(activity, 16);
            listParams.rightMargin = More.dpInt(activity, 16);
            listView.setLayoutParams(listParams);
            listView.setBackgroundColor(0xFFF5F5F5);
            listView.setDivider(null);
            listView.setDividerHeight(0);
            rebuildDisplayList();
            final LogAdapter adapter = new LogAdapter(sDisplayList);
            listView.setAdapter(adapter);
            sAdapter = adapter;
            sListView = listView;
            listView.setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {}

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if (totalItemCount > 0) {
                        int lastVisible = firstVisibleItem + visibleItemCount;
                        sIsAtBottom = (lastVisible >= totalItemCount);
                    } else sIsAtBottom = true;
                }
            });
            rootLayout.addView(listView);
            updateTitle();
            
            // ---- 底部 ----
            LinearLayout bottomLayout = new LinearLayout(activity);
            bottomLayout.setOrientation(LinearLayout.HORIZONTAL);
            bottomLayout.setGravity(Gravity.CENTER_VERTICAL);
            bottomLayout.setPadding(More.spx(activityParam, 80), More.spx(activityParam, 40), More.spx(activityParam, 30), More.spx(activityParam, 12));
            TextView bottomTip = new TextView(activity);
            bottomTip.setText("缓存上限: " + MAX_CACHE_SIZE + " 条，超出自动清理");
            bottomTip.setTextColor(0xFF66BB6A); // 浅绿色 (RGB: 144, 238, 144)
            bottomTip.setTypeface(Typeface.DEFAULT_BOLD); // 加粗
            bottomTip.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            bottomTip.setTextSize(14);
            bottomTip.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            bottomLayout.addView(bottomTip);
            sBottomTipText = bottomTip;
            Button closeBtn = new Button(activity);
            closeBtn.setText("返回应用");
            setButtonStyle(closeBtn, 0xFF2196F3, Color.WHITE);
            closeBtn.setPadding(More.spx(activityParam, 30), More.spx(activityParam, 12), More.spx(activityParam, 30), More.spx(activityParam, 12));
            closeBtn.setTextSize(14);

            final AlertDialog[] dialogRef = new AlertDialog[1];
            closeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (dialogRef[0] != null) dialogRef[0].dismiss();
                }
            });
            bottomLayout.addView(closeBtn);
            int navBarHeight = 0;
            int resId = activity.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (resId > 0) navBarHeight = activity.getResources().getDimensionPixelSize(resId);
            if (navBarHeight == 0)
                navBarHeight = More.dpInt(activity, 50);
            bottomLayout.setPadding(More.spx(activityParam, 20), More.spx(activityParam, 10), More.spx(activityParam, 20), navBarHeight + More.spx(activityParam, 10));
            rootLayout.addView(bottomLayout);
            // ---- 构建全屏Dialog（不使用自定义主题及DialogUi，纯自控制） ----
            AlertDialog.Builder builder = new AlertDialog.Builder(activity, DialogUi.getDialogThemeResId());
            builder.setView(rootLayout);
            final AlertDialog dialog = builder.create();
            dialogRef[0] = dialog;
            // 条件性 overlay 提权
            DialogUi.tryElevateDialogToOverlay(dialog);
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    try {
                        Window window = dialog.getWindow();
                        if (window != null) {
                            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                            WindowManager.LayoutParams params = window.getAttributes();
                            params.width = WindowManager.LayoutParams.MATCH_PARENT;
                            params.height = WindowManager.LayoutParams.MATCH_PARENT;
                            params.gravity = Gravity.TOP;
                            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                            params.dimAmount = 0.4f; // 透明度
                            window.setAttributes(params);
                            window.getDecorView().setSystemUiVisibility(
                                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            );
                            rootLayout.setFitsSystemWindows(true);
                            window.getDecorView().bringToFront();
                            activity.getWindow().getDecorView().bringToFront();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    sDialogActive = false;
                    sListView = null;
                    sAdapter = null;
                    sTitleMainText = null;
                    sTitleStatusText = null;
                    sSubtitleText = null;
                    sBottomTipText = null;
                    sCategoryContainer = null;
                    sClearBtn = null;
                    sPauseBtn = null;
                    sMainHandler.removeCallbacksAndMessages(null);
                }
            });
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
            
            // ---- 按钮点击事件（动态获取 Activity，避免失效） ----
            modeBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity act = getCurrentValidActivity();
                    if (act == null) {
                        Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
                        if (ctx != null) ToastUtil.showUnique(ctx, "当前Activity无效\n请尝试结束应用");
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        return;
                    }
                    sMergedMode = !sMergedMode;
                    modeBtn.setText(sMergedMode ? "数据合并" : "数据展开");
                    setButtonStyle(modeBtn, sMergedMode ? 0xFF4CAF50 : 0xFF2196F3, Color.WHITE);
                    ToastUtil.showUnique(act, sMergedMode ? "已切换为合并模式" : "已切换为展开模式");
                    sMainHandler.removeCallbacks(sRefreshRunnable);
                    performRefresh();
                }
            });
            topBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity act = getCurrentValidActivity();
                    if (act == null) {
                        Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
                        if (ctx != null) ToastUtil.showUnique(ctx, "当前Activity无效\n请尝试结束应用");
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        return;
                    }
                    listView.setSelection(0);
                    sIsAtBottom = false;
                    ToastUtil.showUnique(act, "已滚动到顶部");
                }
            });
            bottomBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity act = getCurrentValidActivity();
                    if (act == null) {
                        Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
                        if (ctx != null) ToastUtil.showUnique(ctx, "当前Activity无效\n请尝试结束应用");
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        return;
                    }
                    if (sAdapter.getCount() > 0) {
                        listView.setSelection(sAdapter.getCount() - 1);
                        sIsAtBottom = true;
                    }
                    ToastUtil.showUnique(act, "已滚动到底部");
                }
            });

            pauseBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity act = getCurrentValidActivity();
                    if (act == null) {
                        Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
                        if (ctx != null) ToastUtil.showUnique(ctx, "当前Activity无效\n请尝试结束应用");
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        return;
                    }
                    if ("all".equals(sCurrentGroup)) {
                        boolean current = isPaused("all");
                        setPaused("all", !current);
                        String state = current ? "已继续" : "已暂停";
                        ToastUtil.showUnique(act, state + "(全部)");
                        performRefresh();
                        return;
                    }
                    if (sPauseCache.getOrDefault("all", false)) {
                        ToastUtil.showUnique(act, "全局记录暂停中\n请先继续“全部类”记录");
                        return;
                    }
                    boolean current = isPaused(sCurrentGroup);
                    setPaused(sCurrentGroup, !current);
                    String state = current ? "已继续" : "已暂停";
                    String groupDisplay = "all".equals(sCurrentGroup) ? "全部" : ("other".equals(sCurrentGroup) ? "其他" : getGroupDisplayName(sCurrentGroup));
                    ToastUtil.showUnique(act, state + "(" + groupDisplay + ")");
                    performRefresh();
                }
            });
            clearBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Activity act = getCurrentValidActivity();
                    if (act == null) {
                        Context ctx = sHookInit != null ? sHookInit.getApplicationContext() : null;
                        if (ctx != null) ToastUtil.showUnique(ctx, "当前Activity无效\n请尝试结束应用");
                        if (dialogRef[0] != null) dialogRef[0].dismiss();
                        return;
                    }
                    String groupDisplay = "all".equals(sCurrentGroup) ? "全部" : ("other".equals(sCurrentGroup) ? "其他" : getGroupDisplayName(sCurrentGroup));
                    // 提前检查是否有真实日志
                    if (!hasRealLogsForGroup(sCurrentGroup)) {
                        ToastUtil.showUnique(act, "[" + groupDisplay + "] 日志清理完成");
                        return;
                    }
                    if ("all".equals(sCurrentGroup)) {
                        clearAll();
                    } else {
                        clearCategory(sCurrentGroup);
                    }
                    ToastUtil.showUnique(act, "清理[" + groupDisplay + "]成功");
                }
            });
        } catch (Throwable t) {
            sDialogActive = false;
            sClearBtn = null;
            sPauseBtn = null;
            ToastUtil.showUnique(activity, "日志加载失败，请重试");
        }
    }
}

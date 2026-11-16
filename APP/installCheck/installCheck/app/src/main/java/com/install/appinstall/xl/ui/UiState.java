package com.install.appinstall.xl.ui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 界面状态的跨重建记忆。
 */
 
final class UiState {

    // 长驻宿主进程，hook 回调线程一旦触及就会并发损坏——直接用并发容器）
    private static final Map<String, Boolean> FOLDS = new ConcurrentHashMap<String, Boolean>();
    private static final Map<String, Integer> SCROLLS = new ConcurrentHashMap<String, Integer>();
    private static int sTabIndex = 0;
    private UiState() {}
    static boolean fold(String key) {
        Boolean b = FOLDS.get(key);
        return b != null && b.booleanValue();
    }

    static void setFold(String key, boolean v) {
        FOLDS.put(key, Boolean.valueOf(v));
    }

    static int scroll(String key) {
        Integer i = SCROLLS.get(key);
        return i == null ? 0 : i.intValue();
    }

    static void setScroll(String key, int v) {
        SCROLLS.put(key, Integer.valueOf(v));
    }

    /** 上次停留的标签页：退出宿主再进来时回到那一页 */
    static int tab() {
        return sTabIndex;
    }

    static void setTab(int i) {
        sTabIndex = i;
    }

    /** 从标题里取稳定的标识：「黑/白名单（12）」→「黑/白名单」 */
    static String keyOf(String title) {
        if (title == null) return "";
        // 全角/半角括号都要截：有些宿主或第三方页面把标题写成「黑名单(12)」，
        // 只认全角的话，数量一变（(11)/(13)）整个标识跟着抖动
        int p = title.indexOf('（');
        int q = title.indexOf('(');
        int cut = p > 0 ? (q > 0 ? Math.min(p, q) : p) : q;
        String k = cut > 0 ? title.substring(0, cut) : title;
        return k.trim();
    }
}

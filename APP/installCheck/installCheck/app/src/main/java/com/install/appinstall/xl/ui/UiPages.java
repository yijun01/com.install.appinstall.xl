package com.install.appinstall.xl.ui;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.ReaLog;
import com.install.appinstall.xl.util.ToastUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * UiPages —— 新 UI 的页面实现
 */
public final class UiPages {

    private UiPages() {}

    /** 打开新 UI 首页；返回 false 表示构建失败，调用方必须回退旧对话框 */
    /** 下次打开主页时定位到指定标签*/
    private static int sPendingTab = -1;

    /**
     * 首页用弱引用持有
     */
    private static java.lang.ref.WeakReference<HomePage> sHomeRef;

    static HomePage home() {
        return sHomeRef != null ? sHomeRef.get() : null;
    }

    public static void requestTab(int index) {
        sPendingTab = index;
    }

    public static boolean open(Activity activity, TextView floatingView) {
        if (activity == null) return false;
        try {
            PageStack stack = PageStack.open(activity, new HomePage(floatingView));
            boolean ok = stack != null && stack.depth() > 0;
            // 能正常打开就说明这次没有降级，清掉标记，旧菜单恢复原有行为
            if (ok) UiKit.setFallbackMode(false);
            return ok;
        } catch (Throwable t) {
            try {
                ReaLog.log("system", "新UI打开失败，回退旧对话框: " + t);
            } catch (Throwable ignored) {
            }
            return false;
        }
    }

    // ============================================================
    //  首页
    // ============================================================
    static View findTextByValue(View root, String text) {
        if (root == null || text == null) return null;
        if (root instanceof TextView) {
            CharSequence cs = ((TextView) root).getText();
            if (cs != null && text.equals(cs.toString().trim())) return root;
            return null;
        }
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View r = findTextByValue(g.getChildAt(i), text);
                if (r != null) return r;
            }
        }
        return null;
    }

    /**
     * 在页面里定位某段文本
     */
    public static void scrollAndHighlight(final View pageView, final String text,
            final Activity a) {
        if (pageView == null || text == null || text.length() == 0) return;
        pageView.postDelayed(new Runnable() {
            public void run() {
                try {
                    View title = findTextByValue(pageView, text);
                    if (title == null) return;
                    // 命中的行可能被「合并」或分类折叠藏起来了，先把内容打开
                    View up = title;
                    for (int d = 0; d < 6; d++) {
                        if (!(up.getParent() instanceof View)) break;
                        up = (View) up.getParent();
                        if (up == pageView) break;
                        if (up.getVisibility() != View.VISIBLE) up.setVisibility(View.VISIBLE);
                    }
                    try {
                        title.requestRectangleOnScreen(new android.graphics.Rect(
                        0, 0, title.getWidth(), title.getHeight()), true);
                    } catch (Throwable ignored) {
                    }
                    // 功能项标题：往上两层就是那一行；普通文本：直接高亮它自己
                    View hl = title;
                    if ("row_title".equals(title.getTag())) {
                        for (int i = 0; i < 2; i++) {
                            if (!(hl.getParent() instanceof View)) break;
                            hl = (View) hl.getParent();
                        }
                    }
                    final View target = hl;
                    final android.graphics.drawable.Drawable old = target.getBackground();
                    if (a != null) {
                        target.setBackground(target instanceof TextView
                                ? UiKit.roundRect(a, UiKit.accentSoft(a), 8)
                                : UiKit.roundRect(a, UiKit.accentSoft(a), 10));
                        UiKit.haptic(title);
                    }
                    target.postDelayed(new Runnable() {
                        public void run() {
                            try {
                                target.setBackground(old);
                            } catch (Throwable ignored) {
                            }
                        }
                    }, 1400);
                } catch (Throwable ignored) {
                }
            }
        }, 260);
    }

    public static class HomePage extends PageStack.Page {

        private static final String[] TABS = {"包名列表", "启动拦截", "权限防护", "拦截退出", "返回键控制",
                "伪装强制", "假装分享", "网络代理", "痕迹隐藏", "其他功能"
        };

        private final TextView floatingView;
        private Activity mA;
        private UiComponents.SwipeTabHost mHost;
        private LinearLayout mTabLine;

        /** 自带 SwipeTabHost 刷新容器*/
        public boolean hasOwnRefresh() {
            return true;
        }

        private android.widget.HorizontalScrollView mTabScroll;
        private final List<View> mTabViews = new ArrayList<View>();
        private int mTabIndex = 0;

        /** 每个标签页的滚动位置：进子页/换标签页回来不归零 */
        private final int[] mTabScrollY = new int[TABS.length];

        /** 哪些标签页构建失败（true = 已降级成说明卡） */
        private final boolean[] mTabFailed = new boolean[TABS.length];

        /**
         * 本轮打开界面内，哪些标签页已经「自动弹过旧对话框」。
         */
        private final java.util.Set<Integer> mAutoFallenBack = new java.util.HashSet<Integer>();

        public HomePage(TextView floatingView) {
            this.floatingView = floatingView;
        }

        public String key() {
            return "home";
        }

        @Override
        public void onShow() {
            refreshCurrentTab();
            // 仍然失败且本轮还没弹过旧对话框，就自动弹一次。
            maybeAutoFallback(mTabIndex);
        }

        /** 重建「当前」标签页。*/
        public void refreshCurrentTab() {
            if (mA == null || mHost == null || mTabViews.isEmpty()) return;
            final int idx = mTabIndex;
            if (idx < 0 || idx >= mTabViews.size()) return;
            View nv = tabViewOrFallback(mA, idx);
            // 重建前先从旧 View 上取走滚动位置，重建后补回去
            saveTabScroll(idx);
            mHost.removeAllTabs();
            mTabViews.set(idx, nv);
            for (int i = 0; i < mTabViews.size(); i++) {
                View v = mTabViews.get(i);
                v.setVisibility(i == idx ? View.VISIBLE : View.GONE);
                mHost.addView(v, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            }
            nv.setTranslationX(0f);
            nv.setAlpha(1f);
            restoreTabScroll(idx, nv);
        }

        /** 重建包名页（TABS[0] 固定是包名） */
        public void triggerRefresh() {
            if (mHost == null) return;
            try {
                if (ReaLog.sHookInit != null) ReaLog.sHookInit.uiRefreshDetectedPackages();
            } catch (Throwable ignored) {
            }
            mHost.startRefresh();
        }

        public void refreshPackages() {
            if (mA == null || mHost == null) return;
            if (mTabIndex != 0 || mTabViews.size() == 0) return;
            View nv = tabViewOrFallback(mA, 0);
            try {
                if (mHost.getChildCount() > 0) {
                    saveTabScroll(0);
                    mHost.removeAllTabs();
                    for (int i = 0; i < mTabViews.size(); i++) {
                        View child = i == 0 ? nv : mTabViews.get(i);
                        mHost.addView(child, new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));
                        child.setVisibility(i == mTabIndex ? View.VISIBLE : View.GONE);
                    }
                    mTabViews.set(0, nv);
                    nv.setTranslationX(0f);
                    nv.setAlpha(1f);
                    restoreTabScroll(0, nv);
                }
            } catch (Throwable ignored) {
            }
        }

        /** 重建全部标签页 */
        public void refreshAllTabs() {
            if (mA == null || mHost == null) return;
            for (int i = 0; i < mTabViews.size(); i++) saveTabScroll(i);
            mHost.removeAllTabs();
            mTabViews.clear();
            for (int i = 0; i < TABS.length; i++) {
                mTabViews.add(tabViewOrFallback(mA, i));
            }
            for (int i = 0; i < mTabViews.size(); i++) {
                View v = mTabViews.get(i);
                v.setVisibility(i == mTabIndex ? View.VISIBLE : View.GONE);
                mHost.addView(v, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
                v.setTranslationX(0f);
                v.setAlpha(1f);
                restoreTabScroll(i, v);
            }
        }

        /** 构建第 i 个标签页的内容*/
        private View buildTabView(Activity a, int i) {
            if (i == TABS.length - 1) return UiTabs.other(a);
            if (i == TABS.length - 2) return UiTabs.anti(a);
            if (i == 0) return UiTabs.packages(a, floatingView);
            return UiTabs.featurePage(a, floatingView, i - 1);
        }

        /**
         * 取第 i 个标签页的 View
         */
        private View tabViewOrFallback(Activity a, final int i) {
            View v = null;
            try {
                v = buildTabView(a, i);
            } catch (Throwable t) {
                v = null;
                try {
                    com.install.appinstall.xl.util.ReaLog.log("system",
                            "标签页「" + TABS[i] + "」构建失败，降级为说明卡: " + t);
                } catch (Throwable ignored) {
                }
            }
            if (v != null) {
                mTabFailed[i] = false;
                return v;
            }
            mTabFailed[i] = true;
            return UiComponents.fallbackView(a, TABS[i],
                    "此分类暂时无法显示，可以先用旧样式修改同样的设置。",
                    "用旧样式打开", new View.OnClickListener() {
                        public void onClick(View vv) {
                            UiTabs.fallbackTab(mA, floatingView, i);
                        }
                    });
        }

        /** 用户主动点进异常标签时：本轮没自动弹过就弹一次，弹过就只留说明卡 */
        private void maybeAutoFallback(int i) {
            if (mA == null) return;
            if (i < 0 || i >= mTabFailed.length) return;
            if (!mTabFailed[i]) return;
            if (!mAutoFallenBack.add(Integer.valueOf(i))) return; // 本轮已弹过
            UiTabs.fallbackTab(mA, floatingView, i);
        }

        /** 记下某个标签页此刻滚到哪了 */
        private void saveTabScroll(int idx) {
            if (idx < 0 || idx >= mTabViews.size() || idx >= mTabScrollY.length) return;
            android.widget.ScrollView sv = UiComponents.findScrollView(mTabViews.get(idx));
            if (sv != null) mTabScrollY[idx] = sv.getScrollY();
        }

        /** 把某个标签页恢复到上次的滚动位置（内容刚建好时要等一帧才能滚） */
        private void restoreTabScroll(int idx, View pageView) {
            if (idx < 0 || idx >= mTabScrollY.length) return;
            final int y = mTabScrollY[idx];
            if (y <= 0 || pageView == null) return;
            final android.widget.ScrollView sv = UiComponents.findScrollView(pageView);
            if (sv == null) return;
            sv.post(new Runnable() {
                public void run() {
                    try {
                        sv.scrollTo(0, y);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }

        public String title() {
            return "功能配置";
        }

        public View build(final Activity a) {
            mA = a;
            sHomeRef = new java.lang.ref.WeakReference<HomePage>(this);
            // 底栏的「设置」入口需要悬浮窗视图来构造设置页
            PageStack.sFloatingView = floatingView;
            if (sPendingTab >= 0) {
                mTabIndex = sPendingTab < TABS.length ? sPendingTab : 0;
                sPendingTab = -1;
            } else if (mTabIndex == 0) {
                // 没有指定就回到上次停留的标签页
                int last = UiState.tab();
                if (last > 0 && last < TABS.length) mTabIndex = last;
            }
            if (mTabIndex >= TABS.length) mTabIndex = 0;
            UiState.setTab(mTabIndex);
            LinearLayout root = UiComponents.pageRoot(a);
            // 标题 + 标签 + 设置按钮压进同一行
            LinearLayout bar = UiKit.hbox(a);
            bar.setBackgroundColor(UiKit.topBarBg(a));
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setClipChildren(false);
            bar.setPadding(UiKit.dpI(a, 12), UiKit.dpI(a, 2), UiKit.dpI(a, 8), UiKit.dpI(a, 2));
            // 标签已说明分类，去掉标题文字，避免和标签 / 按钮抢位置
            android.widget.HorizontalScrollView hsv = new android.widget.HorizontalScrollView(a);
            hsv.setHorizontalScrollBarEnabled(false);
            mTabLine = UiKit.hbox(a);
            mTabLine.setTag("tab_line");
            for (int i = 0; i < TABS.length; i++) {
                boolean sel = i == mTabIndex;
                TextView chip = UiKit.text(a, TABS[i], 13,
                        sel ? 0xFFFFFFFF : UiKit.textSub(a));
                chip.setGravity(Gravity.CENTER);
                int hPad = UiKit.dpI(a, 14);
                int vPad = UiKit.dpI(a, 6);
                chip.setPadding(hPad, vPad, hPad, vPad);
                chip.setBackground(UiKit.roundRect(a,
                        sel ? UiKit.accent(a) : UiKit.rowBg(a), 999));
                if (sel) chip.getPaint().setFakeBoldText(true);
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.rightMargin = UiKit.dpI(a, 7);
                chip.setLayoutParams(clp);
                chip.setTag(Integer.valueOf(i));
                chip.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Object tag = v.getTag();
                        if (tag instanceof Integer) selectTab(((Integer) tag).intValue());
                    }
                });
                // 长按标签 chip 刷新全部标签
                chip.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        UiKit.haptic(v);
                        v.animate().cancel();
                        v.setScaleX(0.92f);
                        v.setScaleY(0.92f);
                        v.animate().scaleX(1f).scaleY(1f)
                                .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                                .setDuration(180).start();
                        refreshAllTabs();
                        toast(a, "已刷新全部标签");
                        return true;
                    }
                });
                mTabLine.addView(chip);
            }
            mTabScroll = hsv;
            hsv.addView(mTabLine, new android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            hsv.setClipToPadding(true);
            hsv.setPadding(0, 0, UiKit.dpI(a, 6), 0);
            bar.addView(hsv, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            bar.addView(UiComponents.chipButton(a, "搜索", new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s == null) return;
                    // 栈里已有搜索页先弹回去，没有再新建
                    if (!s.popTo("search")) s.push(new SearchPage());
                }
            }, false));

            root.addView(bar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiKit.dividerLine(a), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dpI(a, 1))));

            mHost = new UiComponents.SwipeTabHost(a);
            mHost.setOnTabSwipe(new UiComponents.OnTabSwipe() {
                public void onSwipe(int delta) {
                    // 滑过首尾无缝回绕到最后一个/第一个
                    int n = mTabViews.size();
                    if (n <= 0) return;
                    int next = mTabIndex + delta;
                    if (next < 0) next = n - 1;
                    if (next >= n) next = 0;
                    selectTab(next, delta);
                }
            });

            mTabViews.clear();
            // 逐个构建并隔离异常：任何一个分类出问题只降级它自己，其余照常可用
            for (int i = 0; i < TABS.length; i++) {
                mTabViews.add(tabViewOrFallback(a, i));
            }
            for (int i = 0; i < mTabViews.size(); i++) {
                View v = mTabViews.get(i);
                v.setVisibility(i == mTabIndex ? View.VISIBLE : View.GONE);
                mHost.addView(v, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            }
            mHost.setOnPullRefresh(new UiComponents.OnPullRefresh() {
                public void onRefresh() {
                    // 重建是同步的，但转得太快显得没刷新：至少转满 350ms 再收起
                    mHost.postDelayed(new Runnable() {
                        public void run() {
                            if (mHost == null) return;
                            // 先收起刷新区域，收起完毕再换血标签页
                            mHost.finishRefresh(new Runnable() {
                                public void run() {
                                    refreshAllTabs();
                                }
                            });
                        }
                    }, 350);
                }
            });
            root.addView(mHost, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            mHost.onContentReady();
            return root;
        }

        /** 全局搜索 */
        public static class SearchItem {
            public final int tabIndex;
            public final String tabName;
            public final String text;
            /** true = 功能项标题（优先展示），false = 页面上的普通文本 */
            public final boolean isFeature;
            /** null = 标签页里的一项；1=设置 2=配置备份 3=添加包名 */
            public final int page;
            public SearchItem(int tabIndex, String tabName, String text, boolean isFeature) {
                this(tabIndex, tabName, text, isFeature, 0);
            }
            public SearchItem(int tabIndex, String tabName, String text, boolean isFeature,
                    int page) {
                this.tabIndex = tabIndex;
                this.tabName = tabName;
                this.text = text;
                this.isFeature = isFeature;
                this.page = page;
            }
        }

        /**
         * 收集**所有**看得见的文字：不管是功能项标题、分组标题、 说明文字、按钮、还是列表里的包名，一律建索引
         */
        public List<SearchItem> collectSearchItems() {
            List<SearchItem> out = new ArrayList<SearchItem>();
            java.util.HashSet<String> seen = new java.util.HashSet<String>();
            for (int i = 0; i < mTabViews.size() && i < TABS.length; i++) {
                collectTitles(mTabViews.get(i), i, out, seen, 0);
            }
            collectPageItems(out, seen);
            return out;
        }

        /** 把独立页面的文字也建进索引：临时构建一次，取完文本就丢弃 */
        private void collectPageItems(List<SearchItem> out, java.util.HashSet<String> seen) {
            if (mA == null) return;
            addPageItems(out, seen, 1);
            addPageItems(out, seen, 2);
            addPageItems(out, seen, 3);
        }

        private void addPageItems(List<SearchItem> out, java.util.HashSet<String> seen,
                int kind) {
            try {
                View v = null;
                if (kind == 1) v = UiTabs.settings(mA, floatingView);
                else if (kind == 2) v = UiTabs.data(mA, floatingView);
                else if (kind == 3) v = UiTabs.addPackage(mA, floatingView);
                if (v == null) return;
                collectTitles(v, -1, out, seen, kind);
            } catch (Throwable ignored) {
            }
        }

        private void collectTitles(View v, int tab, List<SearchItem> out,
                java.util.HashSet<String> seen, int page) {
            if (v == null) return;
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                CharSequence cs = tv.getText();
                if (cs == null) return;
                String t = cs.toString().trim();
                if (t.length() == 0 || t.length() > 80) return;
                if (!isSearchableText(t)) return;
                boolean feature = "row_title".equals(tv.getTag());
                String key = page + "|" + tab + "|" + t;
                if (seen.contains(key)) return;
                seen.add(key);
                out.add(new SearchItem(tab, tab < 0 ? "页面" : TABS[tab], t, feature, page));
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int c = 0; c < g.getChildCount(); c++) {
                    collectTitles(g.getChildAt(c), tab, out, seen, page);
                }
            }
        }

        /** 过滤掉页脚、纯符号、纯数字这类搜到了也没意义的文本 */
        /*
        private boolean isSearchableText(String t) {
            if (t.length() < 2) return false;
            if (t.indexOf("永恒之蓝") >= 0 || t.indexOf("仅供个人学习测试") >= 0
                    || t.indexOf("禁商用") >= 0 || t.indexOf("GitHub 主页") >= 0
                    || t.indexOf("MT 论坛") >= 0 || t.indexOf("官方仓库") >= 0) {
                return false;
            }*/
            
        private boolean isSearchableText(String t) {
            if (t.length() < 2) return false;
            if (t.indexOf(" ") >= 0 || t.indexOf("0") >= 0
                    || t.indexOf("1") >= 0 || t.indexOf("2") >= 0
                    || t.indexOf("3") >= 0 || t.indexOf("4") >= 0) {
                return false;
            }
            // 至少要有一个字母 / 汉字才收
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (Character.isLetter(c)) return true;
            }
            return false;
        }

        /** 跳到某个文本*/
        public void jumpToItem(final int tabIndex, final String text) {
            if (tabIndex < 0 || tabIndex >= mTabViews.size()) return;
            selectTab(tabIndex);
            final View pageView = mTabViews.get(tabIndex);
            if (pageView == null) return;
            scrollAndHighlight(pageView, text, mA);
        }

        /** 供降级流程跳回某个标签页（UiTabs.openTab） */
        public void jumpToTab(int i) {
            selectTab(i);
        }

        private void selectTab(int i) {
            selectTab(i, 0);
        }

        /** dir=0 时按位置自动判定方向 */
        private void selectTab(int i, int dir) {
            if (i < 0 || i >= mTabViews.size() || i == mTabIndex) return;
            // 离开的那个标签页记住滚到哪，回来时接着看
            saveTabScroll(mTabIndex);
            if (dir == 0) dir = i > mTabIndex ? 1 : -1;
            mTabIndex = i;
            UiState.setTab(i);
            View target = mTabViews.get(i);
            for (int k = 0; k < mTabViews.size(); k++) {
                mTabViews.get(k).setVisibility(k == i ? View.VISIBLE : View.GONE);
            }
            if (mA != null) {
                target.setTranslationX(dir * UiKit.dp(mA, 34));
                target.setAlpha(0.25f);
                target.animate().translationX(0f).alpha(1f).setDuration(200).start();
            }
            restoreTabScroll(i, target);
            maybeAutoFallback(i);
            refreshTabBar();
        }

        private void refreshTabBar() {
            if (mTabLine == null || mA == null) return;
            for (int k = 0; k < mTabLine.getChildCount(); k++) {
                View item = mTabLine.getChildAt(k);
                Object tag = item.getTag();
                if (!(tag instanceof Integer) || !(item instanceof TextView)) continue;
                int idx = ((Integer) tag).intValue();
                boolean sel = idx == mTabIndex;
                TextView t = (TextView) item;
                t.setTextColor(sel ? 0xFFFFFFFF : UiKit.textSub(mA));
                t.getPaint().setFakeBoldText(sel);
                t.setBackground(UiKit.roundRect(mA,
                        sel ? UiKit.accent(mA) : UiKit.rowBg(mA), 999));
                t.invalidate();
                if (sel) scrollTabIntoView(t);
            }
        }

        /** 标签多于一屏时，切过去自动把它滚进可见区*/
        private void scrollTabIntoView(final View item) {
            final android.widget.HorizontalScrollView hsv = mTabScroll;
            if (hsv == null || mA == null) return;
            hsv.post(new Runnable() {
                public void run() {
                    int left = item.getLeft() - UiKit.dpI(mA, 20);
                    if (left < 0) left = 0;
                    hsv.smoothScrollTo(left, 0);
                }
            });
        }
    }

    // ============================================================
    //  权限明细页
    // ============================================================
    public static class PermissionPage extends PageStack.Page {
        private final TextView floatingView;
        public PermissionPage(TextView floatingView) {
            this.floatingView = floatingView;
        }
        public String key() {
            return "permissions";
        }
        public String title() {
            return "权限明细";
        }
        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "权限明细", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiTabs.permissions(a, floatingView), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }
    
       /** 按页面 key 重建一个页面实例*/
    public static PageStack.Page recreatePage(String key) {
        if (key == null || key.length() == 0) return null;
        TextView fv = PageStack.sFloatingView;
        if ("settings".equals(key)) return new SettingsPage(fv);
        if ("data".equals(key)) return new DataPage(fv);
        if ("export".equals(key)) return new ExportPage(fv);
        if ("addpkg".equals(key)) return new AddPackagePage(fv);
        if ("permissions".equals(key)) return new PermissionPage(fv);
        if ("search".equals(key)) return new SearchPage();
        if (key.startsWith("autolist-")) {
            String kind = key.substring("autolist-".length());
            String title = UiTabs.LIST_EXIT.equals(kind) ? "退出拦截处理列表" : "拦截处理列表";
            return new AutoListPage(fv, title, kind);
        }
        return null;
    }

    // ============================================================
    //  全局功能搜索页
    // ============================================================
    public static class SearchPage extends PageStack.Page {
        // key 固定为 "search"：再次进入走 popTo 复用本页。
        public SearchPage() {}
        public String key() {
            return "search";
        }
        public String title() {
            return "搜索功能";
        }
        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "搜索功能", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout content = UiComponents.paddedContent(a);
            final android.widget.EditText input = UiComponents.searchBox(a, "搜索功能项，支持模糊匹配", null);
            content.addView(input);
            final LinearLayout results = UiKit.vbox(a);
            content.addView(results);
            final List<HomePage.SearchItem> all = (home() != null)
                    ? home().collectSearchItems()
                    : new ArrayList<HomePage.SearchItem>();
            UiTabs.renderSearchResults(a, results, all, "");
            input.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
                public void onTextChanged(CharSequence s, int st, int b, int c) {}
                public void afterTextChanged(android.text.Editable e) {
                    UiTabs.renderSearchResults(a, results, all,
                            e == null ? "" : e.toString());
                }
            });
            root.addView(UiComponents.scrollNoFooter(a, content),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            // 进页面就聚焦并弹键盘
            input.postDelayed(new Runnable() {
                public void run() {
                    try {
                        input.requestFocus();
                        Object imm = a.getSystemService(Activity.INPUT_METHOD_SERVICE);
                        if (imm instanceof android.view.inputmethod.InputMethodManager) {
                            ((android.view.inputmethod.InputMethodManager) imm)
                                    .showSoftInput(input, 0);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, 260);
            return root;
        }
    }

    // ============================================================
    //  自动处理名单页
    // ============================================================
    public static class AutoListPage extends PageStack.Page {
        private final TextView floatingView;
        private final String mTitle;
        private final String mListKind;
        /** 首次显示是 push 时的正常构建，不用再重建一次 */
        private boolean mFirstShow = true;
        public AutoListPage(TextView floatingView, String title, String listKind) {
            this.floatingView = floatingView;
            this.mTitle = title;
            this.mListKind = listKind;
        }

        public String key() {
            // 带上类型缓存
            return "autolist-" + mListKind;
        }

        public String title() {
            return mTitle;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, mTitle, true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, UiComponents.chipButton(a, "刷新", new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.triggerTopRefresh();
                }
            }, false), 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiTabs.autoList(a, floatingView, mListKind), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        /** 回到这一页时重读数据 */
        public void onShow() {
            if (mFirstShow) {
                mFirstShow = false;
                return;
            }
            try {
                // 放到下一帧再重建：onShow 里正在切换页面，这一帧别去动视图树
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(new Runnable() {
                            public void run() {
                                try {
                                    PageStack s = PageStack.get();
                                    if (s != null) s.refreshTopContent();
                                } catch (Throwable ignored) {
                                }
                            }
                        });
            } catch (Throwable ignored) {
            }
        }
    }

    public static class AddPackagePage extends PageStack.Page {
        private final TextView floatingView;
        public AddPackagePage(TextView floatingView) {
            this.floatingView = floatingView;
        }

        public String key() {
            return "addpkg";
        }

        public String title() {
            return "添加包名";
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "添加包名", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiTabs.addPackage(a, floatingView), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    public static class ExportPage extends PageStack.Page {
        private final TextView floatingView;
        public ExportPage(TextView floatingView) {
            this.floatingView = floatingView;
        }
        public String key() {
            return "export";
        }
        public String title() {
            return "导出配置";
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "导出配置", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiTabs.exportPage(a, floatingView), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    public static class ImportPage extends PageStack.Page {
        /** 用户挑中的备份文件*/
        public static java.io.File sPickedFile;
        private final TextView floatingView;
        /** 从文件选择器选进来的文件（null = 用私有目录里的备份） */
        private final java.io.File mExternal;
        private boolean mFirstShow = true;
        public ImportPage(TextView floatingView) {
            this(floatingView, null);
        }

        public ImportPage(TextView floatingView, java.io.File external) {
            this.floatingView = floatingView;
            this.mExternal = external;
        }

        public String key() {
            return mExternal == null ? "import" : ("import:" + mExternal.getName());
        }

        public void onShow() {
            if (mFirstShow) {
                mFirstShow = false;
                return;
            }
            // 再进这一页时：文件可能刚被导出/删掉，必须重读目录
            try {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
                            public void run() {
                                try {
                                    PageStack s = PageStack.get();
                                    if (s != null && s.topPage() == ImportPage.this)
                                        s.refreshTopContent();
                                } catch (Throwable ignored) {
                                }
                            }
                        });
            } catch (Throwable ignored) {
            }
        }

        public String title() {
            return "导入配置";
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "导入配置", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiTabs.importPage(a, floatingView, mExternal), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    public static class DataPage extends PageStack.Page {
        private final TextView floatingView;
        public DataPage(TextView floatingView) {
            this.floatingView = floatingView;
        }

        public String key() {
            return "data";
        }

        public String title() {
            return "配置备份";
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "配置备份", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(UiTabs.data(a, floatingView), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    public static class SettingsPage extends PageStack.Page {
        private final TextView floatingView;
        public SettingsPage(TextView floatingView) {
            this.floatingView = floatingView;
        }

        public String key() {
            return "settings";
        }

        public String title() {
            return "设置";
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.pageRoot(a);

            root.addView(UiComponents.topBar(a, "设置", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(UiTabs.settings(a, floatingView),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }
    
    // ============================================================
    //  主题色抽屉
    // ============================================================
    public static class ThemeSheetPage extends PageStack.Page {

        public String key() {
            return "theme";
        }

        public String title() {
            return "主题色";
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "主题色", null));
            LinearLayout tbody = UiKit.vbox(a);
            final int cur = UiKit.getThemeColor(a);
            int cols = 4;
            for (int row = 0; row * cols < UiKit.THEME_PRESETS.length; row++) {
                LinearLayout line = UiKit.hbox(a);
                line.setPadding(UiKit.dpI(a, 8), 0, UiKit.dpI(a, 8), UiKit.dpI(a, 6));
                for (int col = 0; col < cols; col++) {
                    final int idx = row * cols + col;
                    if (idx >= UiKit.THEME_PRESETS.length) break;
                    final int color = UiKit.THEME_PRESETS[idx];
                    boolean sel = color == cur;
                    LinearLayout item = UiKit.vbox(a);
                    item.setGravity(Gravity.CENTER);
                    item.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    item.setPadding(0, UiKit.dpI(a, 6), 0, UiKit.dpI(a, 6));
                    TextView sw = UiKit.text(a, sel ? "✓" : "", 18, 0xFFFFFFFF);
                    sw.setGravity(Gravity.CENTER);
                    sw.setLayoutParams(new LinearLayout.LayoutParams(
                    UiKit.dpI(a, 48), UiKit.dpI(a, 48)));
                    sw.setBackground(sel
                            ? UiKit.strokeRect(a, color, UiKit.textMain(a), 24)
                            : UiKit.roundRect(a, color, 24));
                    item.addView(sw);

                    TextView name = UiKit.text(a, UiKit.THEME_NAMES[idx], 12,
                            sel ? UiKit.accent(a) : UiKit.textSub(a));
                    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    np.topMargin = UiKit.dpI(a, 6);
                    name.setLayoutParams(np);
                    item.addView(name);
                    item.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            UiKit.setThemeColor(a, color);
                            UiTabs.save(a);
                            ReaLog.log("system", "主题色 -> " + UiKit.THEME_NAMES[idx]);
                            PageStack s = PageStack.get();
                            if (s != null) s.rebuildAll();
                            toast(a, "已切换到 " + UiKit.THEME_NAMES[idx]);
                        }
                    });
                    line.addView(item);
                }
                tbody.addView(line, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            // ---- 自定义取色 ----
            TextView wTitle = UiKit.text(a, "自定义取色", 13, UiKit.textSub(a));
            wTitle.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 8),
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 4));
            tbody.addView(wTitle);
            final UiComponents.ColorWheel wheel = new UiComponents.ColorWheel(a);
            wheel.setColor(cur);
            wheel.setOnPick(new UiComponents.ColorWheel.OnPick() {
                public void onPick(int color) {
                    UiKit.setThemeColor(a, color);
                    //限流改用 UiGuard.tap，100ms 一次
                    if (UiGuard.tap("theme_bar", 100)) {
                        PageStack s = PageStack.get();
                        if (s != null) s.refreshBottomBar();
                    }
                }
            });
            wheel.setOnPickEnd(new UiComponents.ColorWheel.OnPickEnd() {
                public void onPickEnd() {
                    PageStack s = PageStack.get();
                    if (s != null) {
                        s.refreshBottomBar();
                        s.invalidateBelow();
                        s.refreshBackdrop();
                    }
                    // 拖动过程每 100ms 就写一次偏好
                    UiTabs.save(a);
                    ReaLog.log("system", "主题色(自定义) -> "
                            + UiKit.colorCode(UiKit.getThemeColor(a)));
                    toast(a, "已应用 " + UiKit.colorCode(UiKit.getThemeColor(a)));
                }
            });
            int wsize = UiKit.dpI(a, 190);
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(wsize, wsize);
            wp.gravity = Gravity.CENTER_HORIZONTAL;
            wp.topMargin = UiKit.dpI(a, 4);
            tbody.addView(wheel, wp);
            tbody.addView(UiKit.gap(a, 20));
            root.addView(UiComponents.scrollNoFooter(a, tbody, "scroll:sheet-theme"), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    // ============================================================
    //  子项详情半屏
    // ============================================================
    public static class DetailSheetPage extends PageStack.Page {
        private final String mTitle;
        private final String mDesc;
        private final boolean mHasSwitch;
        private final boolean mChecked;
        private final UiComponents.OnChecked mListener;
        public DetailSheetPage(String title, String desc, boolean checked,
                UiComponents.OnChecked listener) {
            this.mTitle = title == null ? "" : title;
            this.mDesc = desc == null ? "" : desc;
            this.mHasSwitch = listener != null;
            this.mChecked = checked;
            this.mListener = listener;
        }

        public String key() {
            return "detail:" + mTitle + ":" + Integer.toHexString(mDesc.hashCode());
        }

        public String title() {
            return mTitle;
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, mTitle, null));
            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, UiKit.PAGE_MARGIN));
            if (mHasSwitch) {
                UiComponents.CardGroup card = UiComponents.cardGroup(a, "快速设置", true);
                card.addRow(UiComponents.switchRow(a, mTitle, null, mChecked,
                        new UiComponents.OnChecked() {
                            public void onChecked(boolean c) {
                                if (mListener != null) mListener.onChecked(c);
                                PageStack s = PageStack.get();
                                if (s != null) s.refreshBackdrop();
                            }
                        }));
                body.addView(card.root);
            }
            if (mDesc.length() > 0) {
                TextView desc = UiKit.text(a, mDesc, 13, UiKit.textSub(a));
                desc.setLineSpacing(UiKit.dp(a, 3), 1f);
                desc.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 6),
                        UiKit.dpI(a, 4), 0);
                body.addView(desc);
            }
            root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-detail"), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    // ============================================================
    //  自动处理规则设置
    // ============================================================
    public static class AutoRuleSheetPage extends PageStack.Page {
        private final String mId;
        private final String mApp;
        private final Runnable mOnChanged;
        public AutoRuleSheetPage(String identifier, String appName, Runnable onChanged) {
            mId = identifier == null ? "" : identifier;
            mApp = appName == null ? "" : appName;
            mOnChanged = onChanged;
        }

        public String key() {
            return "autosheet:" + mId;
        }

        public String title() {
            return "设置自动处理";
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "设置自动处理", null));
            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 8));

            // ---- 匹配规则（可编辑）----
            final android.widget.EditText et = new android.widget.EditText(a);
            et.setText(mId);
            et.setHint("支持 * 通配符，! 排除");
            et.setTextSize(14);
            et.setTextColor(UiKit.textMain(a));
            et.setHintTextColor(UiKit.textSub(a));
            et.setBackground(UiKit.strokeRect(a, UiKit.card(a),
                    UiKit.withAlpha(UiKit.accent(a), UiKit.isNight(a) ? 0x44 : 0x30), 10));
            int ep = UiKit.dpI(a, 12);
            et.setPadding(ep, ep, ep, ep);
            et.setMaxLines(4);
            et.setSingleLine(false);
            et.setFilters(new android.text.InputFilter[]{
                    new android.text.InputFilter.LengthFilter(500)
            });
            UiComponents.CardGroup inputCard = UiComponents.cardGroup(a, "匹配规则", true);
            LinearLayout etWrap = UiKit.vbox(a);
            int wp = UiKit.dpI(a, UiKit.PAGE_MARGIN);
            etWrap.setPadding(wp, UiKit.dpI(a, 4), wp, UiKit.dpI(a, 10));
            etWrap.addView(et, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            inputCard.addRow(etWrap);
            body.addView(inputCard.root);

            // ---- 说明 + 规则帮助（原来是一个单独的帮助对话框）----
            TextView desc = UiKit.text(a,
                    "黑名单：始终阻止并拦截启动\n白名单：始终允许并真实启动\n\n"
                            + "智能判断：同一目标连续 3 次相同选择后自动按该选择处理，"
                            + "3 次内选择不一致会重置计数并继续询问。", 12, UiKit.textSub(a));
            desc.setLineSpacing(UiKit.dp(a, 3), 1f);
            desc.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10),
                    UiKit.dpI(a, 4), UiKit.dpI(a, 6));
            body.addView(desc);
            UiComponents.CardGroup help = UiComponents.cardGroup(a, "规则写法", true);
            help.addRow(sheetLine(a, "通配符 *：匹配任意内容，com.example.* 命中该前缀下所有包名"));
            help.addRow(sheetLine(a, "排除符 !：仅用于黑名单，!com.example.safe 表示放行"));
            help.addRow(sheetLine(a, "只写 ! 或 ** 无效，无法保存"));
            help.addRow(sheetLine(a, "精确匹配优先于通配符；多条通配规则以最后添加的为准"));
            body.addView(help.root);
            root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-autorule"), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            // ---- 底部两个动作（原对话框的「加入黑名单 / 加入白名单」）----
            View.OnClickListener act = new View.OnClickListener() {
                public void onClick(View v) {
                    Object tag = v.getTag();
                    final String type = tag instanceof String ? (String) tag : "black";
                    String raw = et.getText() == null ? "" : et.getText().toString().trim();
                    String id = raw.replace('！', '!').replace('＊', '*').trim();
                    if (id.length() == 0 || id.equals("!") || id.indexOf("**") >= 0) {
                        toast(a, "无效的模式，请检查输入");
                        return;
                    }
                    if (id.replace("!", "").replace("*", "").length() == 0) {
                        toast(a, "无效的模式，请检查输入");
                        return;
                    }
                    String cur = com.install.appinstall.xl.HookInit.getAutoAction(mApp, id);
                    if (type.equals(cur)) {
                        toast(a, "当前已是" + ("black".equals(type) ? "黑名单" : "白名单"));
                        return;
                    }
                    com.install.appinstall.xl.HookInit.putAutoAction(mApp, id, type);
                    ReaLog.log("launch_intercept", "拦截名单 " + id + " -> "
                            + ("black".equals(type) ? "黑名单" : "白名单"));
                    UiTabs.save(a);
                    toast(a, "已加入" + ("black".equals(type) ? "黑名单" : "白名单"));
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                    if (mOnChanged != null) mOnChanged.run();
                }
            };
            LinearLayout btns = UiKit.hbox(a);
            btns.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 12));
            View black = UiComponents.dangerButton(a, "加入黑名单", act);
            black.setTag("black");
            View white = UiComponents.primaryButton(a, "加入白名单", act);
            white.setTag("white");
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            b1.rightMargin = UiKit.dpI(a, 8);
            btns.addView(black, b1);
            btns.addView(white, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(btns, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return root;
        }
    }

    /** 半屏里的一行说明文字 */
    private static View sheetLine(Activity a, String text) {
        TextView tv = UiKit.text(a, text, 12, UiKit.textSub(a));
        tv.setLineSpacing(UiKit.dp(a, 2), 1f);
        int p = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        tv.setPadding(p, UiKit.dpI(a, 8), p, UiKit.dpI(a, 8));
        return tv;
    }

    // ============================================================
    //  包名设置
    // ============================================================
    public static class PackageSheetPage extends PageStack.Page {
        private final String mPkg;
        private final String mApp;
        private final Runnable mOnChanged;
        private final boolean mCaptured;
        public PackageSheetPage(String pkg, String appName, Runnable onChanged) {
            this(pkg, appName, onChanged, false);
        }

        public PackageSheetPage(String pkg, String appName, Runnable onChanged,
                boolean captured) {
            mPkg = pkg == null ? "" : pkg;
            mApp = appName;
            mOnChanged = onChanged;
            mCaptured = captured;
        }

        public String key() {
            return mCaptured ? ("pkgsheet:cap:" + mPkg) : ("pkgsheet:" + mPkg);
        }

        public String title() {
            return "包名设置";
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, mPkg, null));
            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 12));

            if (mCaptured) {
                TextView capTip = UiKit.text(a,
                        "这是目标应用在运行中捕获到的包名。移入手动添加列表之后，"
                                + "可以单独设置它的状态、也能被配置导出/导入带着走。",
                        11, UiKit.textSub(a));
                capTip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 2), UiKit.dpI(a, 4),
                        UiKit.dpI(a, 10));
                body.addView(capTip);
                UiComponents.CardGroup capCard = UiComponents.cardGroup(a, "操作", true);
                capCard.addRow(UiTabs.entryRow(a, "移入手动添加", "迁入后可在「手动添加」里设置状态",
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                boolean added = UiTabs.promoteToManual(mApp, mPkg);
                                UiTabs.save(a);
                                toast(a, added ? "已移入手动添加" : "这条已经在手动列表里了");
                                PageStack s = PageStack.get();
                                if (s != null) s.pop();
                                if (mOnChanged != null) mOnChanged.run();
                            }
                        }));
                body.addView(capCard.root);
            } else if (UiTabs.isExcluded(mApp, mPkg)) {
                TextView exTip = UiKit.text(a,
                        "这个包名是「排除包名」：模块不会伪造它，目标应用拿到的是真实检测结果。",
                        11, UiKit.textSub(a));
                exTip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 2), UiKit.dpI(a, 4),
                        UiKit.dpI(a, 10));
                body.addView(exTip);
                UiComponents.CardGroup exCard = UiComponents.cardGroup(a, "操作", true);
                exCard.addRow(UiTabs.entryRow(a, "改为伪造包名",
                        "加入伪造列表，按「跟随全局」生效",
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                UiTabs.addPackageToConfig(mApp, mPkg, "follow");
                                UiTabs.save(a);
                                toast(a, "已改为伪造包名");
                                PageStack s = PageStack.get();
                                if (s != null) s.pop();
                                if (mOnChanged != null) mOnChanged.run();
                            }
                        }));
                body.addView(exCard.root);
            } else {
                final String cur = UiTabs.currentMode(mApp, mPkg);
                UiComponents.CardGroup modes = UiComponents.cardGroup(a, "状态", true);
                modes.addRow(modeRow(a, "跟随全局", "由上方「伪装为已安装」总开关决定",
                "follow".equals(cur), "follow", mPkg, mApp, mOnChanged));
                modes.addRow(modeRow(a, "固定已安装", "始终按已安装处理，不受总开关影响",
                "installed".equals(cur), "installed", mPkg, mApp, mOnChanged));
                modes.addRow(modeRow(a, "固定未安装", "始终按未安装处理，不受总开关影响",
                "not_installed".equals(cur), "not_installed", mPkg, mApp, mOnChanged));
                body.addView(modes.root);
            }

            LinearLayout btns = UiKit.hbox(a);
            btns.setPadding(0, UiKit.dpI(a, 10), 0, 0);
            TextView copy = UiComponents.primaryButton(a, "复制包名", new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        Object svc = a.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                        if (svc instanceof android.content.ClipboardManager) {
                            ((android.content.ClipboardManager) svc).setPrimaryClip(
                                            android.content.ClipData.newPlainText("text", mPkg));
                            toast(a, "已复制 " + mPkg);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            if (mCaptured) {
                btns.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                body.addView(btns);
                root.addView(UiComponents.scrollNoFooter(a, body,
                        "scroll:sheet-pkg"), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                return root;
            }
            TextView del = UiComponents.dangerButton(a, "删除", new View.OnClickListener() {
                public void onClick(View v) {
                    UiTabs.removePackage(mApp, mPkg);
                    UiTabs.save(a);
                    toast(a, "已删除 " + mPkg);
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                    if (mOnChanged != null) mOnChanged.run();
                }
            });
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            b1.rightMargin = UiKit.dpI(a, 8);
            btns.addView(copy, b1);
            btns.addView(del, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            body.addView(btns);

            root.addView(UiComponents.scrollNoFooter(a, body,
                    "scroll:sheet-pkg"), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }
    }

    /** 半屏里的一行状态单选 */
    private static View modeRow(final Activity a, String title, String desc,
            boolean checked, final String mode, final String pkg, final String appName,
            final Runnable onChanged) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));
        row.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));
        LinearLayout col = UiKit.vbox(a);
        col.addView(UiKit.text(a, title, 14, UiKit.textMain(a)));
        col.addView(UiKit.text(a, desc, 11, UiKit.textSub(a)));
        row.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView dot = UiKit.text(a, checked ? "●" : "○", 18,
                checked ? UiKit.accent(a) : UiKit.textSub(a));
        dot.setPadding(UiKit.dpI(a, 6), 0, UiKit.dpI(a, 2), 0);
        row.addView(dot);
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                UiTabs.setPackageMode(appName, pkg, mode);
                UiTabs.save(a);
                toast(a, "已设为 " + UiTabs.statusModeLabel(appName, pkg));
                PageStack s = PageStack.get();
                if (s != null) s.pop();
                if (onChanged != null) onChanged.run();
            }
        });
        return row;
    }

    // ============================================================
    //  拦截退出 · 拦截模式设置
    // ============================================================
    public static class ExitPatternSheetPage extends PageStack.Page {
        private final String mHash;
        private final Runnable mOnChanged;
        public ExitPatternSheetPage(String patternHash, Runnable onChanged) {
            mHash = patternHash == null ? "" : patternHash;
            mOnChanged = onChanged;
        }

        public String key() {
            return "exitsheet:" + mHash;
        }

        public String title() {
            return "拦截模式设置";
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "拦截模式设置", null));
            final LinearLayout body = UiKit.vbox(a);
            int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
            body.setPadding(pd, 0, pd, UiKit.dpI(a, 12));
            HookInit hi = ReaLog.sHookInit;
            HookInit.InterceptPattern pat = null;
            try {
                if (hi != null) pat = hi.findInterceptPattern(mHash);
            } catch (Throwable ignored) {
            }
            if (pat == null) {
                TextView gone = UiKit.text(a, "这条拦截模式已经不存在了（可能刚被清理）", 12,
                        UiKit.textSub(a));
                gone.setPadding(0, UiKit.dpI(a, 12), 0, 0);
                body.addView(gone);
                root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-exitpattern"),
                        new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                return root;
            }

            final HookInit.InterceptPattern p = pat;

            // ---- 关联包名（只读摘要，长的一行一条）----
            UiComponents.CardGroup pkgs = UiComponents.cardGroup(a, "关联包名", true);
            int shownCount = 0;
            if (p.installedPackages != null) {
                for (int i = 0; i < p.installedPackages.size() && shownCount < 8; i++) {
                    pkgs.addRow(sheetLine(a, "✅ " + p.installedPackages.get(i)));
                    shownCount++;
                }
            }
            if (p.notInstalledPackages != null) {
                for (int i = 0; i < p.notInstalledPackages.size() && shownCount < 12; i++) {
                    pkgs.addRow(sheetLine(a, "❌ " + p.notInstalledPackages.get(i)));
                    shownCount++;
                }
            }
            int total = (p.installedPackages == null ? 0 : p.installedPackages.size())
                    + (p.notInstalledPackages == null ? 0 : p.notInstalledPackages.size());
            if (total == 0) {
                pkgs.addRow(sheetLine(a, p.patternHash));
            } else if (total > shownCount) {
                pkgs.addRow(sheetLine(a, "还有 " + (total - shownCount) + " 个包名未显示"));
            }
            body.addView(pkgs.root);
            body.addView(UiKit.gap(a, UiKit.CARD_GAP));

            // ---- 处理方式：拦截/放行 × 是否保留计数----
            final String cur = p.userChoice == null ? "" : p.userChoice;
            String curLabel = p.userChoice == null ? "未设置"
                    : ("allow".equals(cur) ? "✅ 放行退出" : "⛔ 拦截退出");
            String curExtra = p.choiceCount > 0 ? (" · 已命中 " + p.choiceCount + " 次") : "";
            curExtra += p.silentIntercept ? " · 静默中" : " · 每次询问";
            body.addView(sheetLine(a, "当前：" + curLabel + curExtra));
            body.addView(UiKit.gap(a, UiKit.CARD_GAP));
            UiComponents.CardGroup modes = UiComponents.cardGroup(a, "处理方式", true);
            final String[] choiceValues = {"intercept", "allow", "intercept", "allow"};
            final boolean[] keepCount = {false, false, true, true};
            final String[] labels = {
                    "⛔ 拦截退出（清除计数）", "✅ 放行退出（清除计数）",
                    "⛔ 拦截退出（保留计数）", "✅ 放行退出（保留计数）"
            };
            final String[] descs = {
                    "重新开始智能判断：命中次数与静默资格都清零",
                    "重新开始智能判断：命中次数与静默资格都清零",
                    "直接按新状态处理，累计命中次数保留",
                    "直接按新状态处理，累计命中次数保留"
            };
            for (int i = 0; i < choiceValues.length; i++) {
                final int idx = i;
                modes.addRow(exitChoiceRow(a, labels[i], descs[i],
                new View.OnClickListener() {
                    public void onClick(View v) {
                        applyChoice(a, p, choiceValues[idx], keepCount[idx], labels[idx]);
                    }
                }));
            }
            body.addView(modes.root);
            body.addView(UiKit.gap(a, UiKit.CARD_GAP));

            // ---- 静默开关：「拦截模式连续 3 次相同判断」后自动静默，也可以手动开关 ----
            UiComponents.CardGroup misc = UiComponents.cardGroup(a, "其它", true);
            misc.addRow(UiComponents.switchRow(a, "静默处理",
                    (p.choiceCount > 0 ? ("已命中 " + p.choiceCount + " 次 · ") : "")
                            + "拦截时不弹询问框，直接按当前选择处理",
                    p.silentIntercept, new UiComponents.OnChecked() {
                        public void onChecked(boolean checked) {
                            try {
                                HookInit h = ReaLog.sHookInit;
                                if (h != null) h.setInterceptPatternSilent(p.patternHash, checked);
                                ReaLog.log("exit_intercept", "拦截模式 " + p.patternHash
                                        + " 静默 -> " + (checked ? "开启" : "关闭"));
                            } catch (Throwable ignored) {
                            }
                            toast(a, checked ? "已开启静默处理" : "已关闭静默处理");
                            refresh(a);
                        }
                    }));
            misc.addRow(exitChoiceRow(a, "清空累计计数", "命中次数与静默资格归零，重新观察",
            new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        HookInit h = ReaLog.sHookInit;
                        if (h != null) h.resetInterceptPatternCount(p.patternHash);
                        ReaLog.log("exit_intercept", "拦截模式 " + p.patternHash
                                + " 清空累计计数");
                    } catch (Throwable ignored) {
                    }
                    toast(a, "已清空累计计数");
                    refresh(a);
                }
            }));
            body.addView(misc.root);

            // ---- 底部：删除 + 关闭 ----
            LinearLayout btns = UiKit.hbox(a);
            btns.setPadding(0, UiKit.dpI(a, 12), 0, 0);
            View del = UiComponents.dangerButton(a, "删除这条模式", new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        HookInit h = ReaLog.sHookInit;
                        if (h != null) h.removeInterceptPattern(p.patternHash);
                        ReaLog.log("exit_intercept", "删除拦截模式 " + p.patternHash);
                    } catch (Throwable ignored) {
                    }
                    toast(a, "已删除");
                    PageStack s = PageStack.get();
                    if (s != null) s.pop();
                    if (mOnChanged != null) mOnChanged.run();
                }
            });
            View close = UiComponents.ghostButton(a, "关闭", new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack s = PageStack.get();
                    // 先出栈再刷新
                    if (s != null) s.pop();
                    if (mOnChanged != null) mOnChanged.run();
                }
            });
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            b1.rightMargin = UiKit.dpI(a, 8);
            btns.addView(del, b1);
            btns.addView(close, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            body.addView(btns);

            root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-exitpattern"),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        /** 改处理方式：保留计数走 KeepCount，清除计数走会重置的版本 */
        private void applyChoice(Activity a, HookInit.InterceptPattern p, String choice,
                boolean keepCount, String label) {
            try {
                HookInit h = ReaLog.sHookInit;
                if (h == null) return;
                boolean sameChoice = choice != null && choice.equals(p.userChoice);
                boolean nothingToKeep = p.choiceCount <= 0 && !p.silentIntercept;
                if (keepCount && sameChoice && nothingToKeep) {
                    toast(a, "已是「" + label + "」，没有计数可保留");
                    return;
                }
                if (keepCount) {
                    h.setInterceptPatternUserChoiceKeepCount(p.patternHash, choice);
                } else {
                    h.setInterceptPatternUserChoice(p.patternHash, choice);
                }
                ReaLog.log("exit_intercept", "修改拦截模式 " + p.patternHash + " -> "
                        + ("allow".equals(choice) ? "放行退出" : "拦截退出")
                        + (keepCount ? "（保留计数）" : "（清除计数）"));
            } catch (Throwable ignored) {
            }
            toast(a, "已设为「" + label + "」");
            refresh(a);
        }

        /** 改完就地重建半屏 */
        private void refresh(Activity a) {
            PageStack s = PageStack.get();
            if (s != null) {
                s.refreshTopContent();
                s.refreshBackdrop();
            }
        }
    }

    /** 半屏里的可点行 */
    private static LinearLayout exitChoiceRow(final Activity a, String title, String desc,
            final View.OnClickListener click) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 10), pd, UiKit.dpI(a, 10));
        row.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));
        LinearLayout col = UiKit.vbox(a);
        col.addView(UiKit.text(a, title, 14, UiKit.textMain(a)));
        TextView d = UiKit.text(a, desc, 11, UiKit.textSub(a));
        d.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(d);
        row.addView(col, new LinearLayout.LayoutParams(0,
        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.setClickable(true);
        if (click != null) row.setOnClickListener(click);
        return row;
    }

    // ============================================================
    //  清理配置
    // ============================================================
    public static class ClearConfigSheetPage extends PageStack.Page {
        private final TextView mFloatingView;
        public ClearConfigSheetPage(TextView floatingView) {
            mFloatingView = floatingView;
        }

        public String key() {
            return "clearcfg";
        }

        public String title() {
            return "清理配置";
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, "清理配置", null));
            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 12));
            String app = UiTabs.app();
            List<String> user = HookInit.userDefinedPackagesMap.get(app);
            int userCount = user == null ? 0 : user.size();
            int autoCount = 0;
            try {
                HookInit h = ReaLog.sHookInit;
                if (h != null) {
                    HookInit.DetectedPackages dp = h.analyzeDetectedPackages();
                    if (dp != null) {
                        if (dp.installedPackages != null) autoCount += dp.installedPackages.size();
                        if (dp.notInstalledPackages != null) {
                            autoCount += dp.notInstalledPackages.size();
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            final int fUser = userCount;
            final int fAuto = autoCount;
            UiComponents.CardGroup info = UiComponents.cardGroup(a, "当前数据", true);
            info.addRow(valueRow(a, "自动捕获的包名", fAuto + " 个"));
            info.addRow(valueRow(a, "手动添加的包名", fUser + " 个"));
            body.addView(info.root);
            TextView tip = UiKit.text(a,
                    "清理只删除包名记录，不影响其它设置；清理后需要重新捕获。"
                            + "清理会立即生效，无法撤销。",
                    11, UiKit.textSub(a));
            tip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10), UiKit.dpI(a, 4), UiKit.dpI(a, 4));
            body.addView(tip);
            LinearLayout btns = UiKit.vbox(a);
            btns.setPadding(0, UiKit.dpI(a, 8), 0, 0);
            TextView onlyAuto = UiComponents.primaryButton(a,
                    "只清自动捕获（保留手动添加的 " + fUser + " 个）", null);
            UiComponents.confirmButton(onlyAuto, "再点一次确认清理",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            doClear(a, false);
                        }
                    });
            btns.addView(onlyAuto, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            TextView all = UiComponents.primaryButton(a, "全部清空（自动捕获 + 手动添加）", null);
            UiComponents.confirmButton(all, "再点一次确认全部清空",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            doClear(a, true);
                        }
                    });
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            ap.topMargin = UiKit.dpI(a, 8);
            btns.addView(all, ap);
            body.addView(btns);
            root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-clear"),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        private void doClear(final Activity a, final boolean everything) {
            try {
                HookInit h = ReaLog.sHookInit;
                if (h == null) {
                    toast(a, "模块还没就绪，稍后再试");
                    return;
                }
                ReaLog.log("system", "清理包名配置 -> "
                        + (everything ? "全部清空" : "仅清空自动捕获"));
                if (everything) {
                    h.uiClearEverything(a, mFloatingView);
                } else {
                    h.uiClearAutoCaptured(a, mFloatingView);
                }
                toast(a, everything ? "已清空全部包名记录" : "已清空自动捕获的包名");
            } catch (Throwable t) {
                toast(a, "清理失败，请重试");
                ReaLog.log("ui", "包名记录清理失败: " + t);
            }
            try {
                PageStack.bumpDataVersion();
            } catch (Throwable ignored) {
            }
            try {
                HookInit.detectedCache.clear();
            } catch (Throwable ignored) {
            }
            PageStack ps = PageStack.get();
            if (ps != null) {
                ps.pop();
                try {
                    // 栈里其它页面也可能存着旧包名，一并作废重建
                    ps.rebuildAll();
                } catch (Throwable ignored) {
                }
            }
            if (home() != null) home().refreshPackages();
        }
    }

    /**
     * 恢复初始状态
     */
    private static java.util.List<String> groupKeys(
            java.util.List<HookInit.ResetEntry> entries, String func) {
        java.util.List<String> keys = new java.util.ArrayList<String>();
        for (int j = 0; j < entries.size(); j++) {
            HookInit.ResetEntry e2 = entries.get(j);
            if (!e2.func.equals(func)) continue;
            if (e2.defaultMap() != null) {
                java.util.Iterator<String> it = e2.defaultMap().keySet().iterator();
                while (it.hasNext()) keys.add(e2.id + "::" + it.next());
            } else {
                keys.add(e2.id);
            }
        }
        return keys;
    }

    public static class ResetSheetPage extends PageStack.Page {
        private Activity mA;
        /** fillFuncs 统计：当前应用偏离默认值的条目数 */
        private int mChangedCount;
        private LinearLayout mFuncBody;
        private TextView mAllBtn;
        private TextView mDangerTip;
        private View mChipBar;
        private TextView mChipMain;
        private TextView mChipState;
        private TextView mChipCancel;
        /** 一级长按进入的多选模式 */
        private static boolean sMulti;
        /** 跨页面共享的勾选集：二级半屏只勾选，返回后由这里的按钮统一恢复*/
        static final java.util.Map<String, Boolean> sSel = new java.util.HashMap<String, Boolean>();
        private int countPicked() {
            int n = 0;
            java.util.Iterator<String> it = sSel.keySet().iterator();
            while (it.hasNext()) {
                if (Boolean.TRUE.equals(sSel.get(it.next()))) n++;
            }
            return n;
        }

        private java.util.Set<String> pickedIds() {
            java.util.Set<String> ids = new java.util.HashSet<String>();
            java.util.Iterator<String> it = sSel.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (Boolean.TRUE.equals(sSel.get(k))) ids.add(k);
            }
            return ids.isEmpty() ? null : ids;
        }

        /** 底部确认按钮：计数=全量 sSel（含子功能 id::subkey 与列表条目）*/
        private void updateActionLabel() {
            if (mAllBtn == null) return;
            mAllBtn.setText(UiComponents.clearLabel(sSel.size(), countPicked()));
        }

        public String key() {
            return "reset";
        }

        public String title() {
            return "恢复初始状态";
        }

        public View build(final Activity a) {
            mA = a;
            LinearLayout root = UiComponents.pageRoot(a);
            root.addView(UiComponents.topBar(a, "恢复初始状态", true, new View.OnClickListener() {
                public void onClick(View v) {
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.pop();
                }
            }, null, 0), new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 12));

            // 多选时的操作条：二选一小胶囊 + 关闭批量选择；窄屏可横滑
            LinearLayout chipRow = UiKit.hbox(a);
            chipRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            chipRow.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 8),
                    UiKit.dpI(a, 4), UiKit.dpI(a, 8));
            chipRow.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            android.widget.HorizontalScrollView chipScroll = new android.widget.HorizontalScrollView(a);
            chipScroll.setHorizontalScrollBarEnabled(false);
            chipScroll.setVerticalScrollBarEnabled(false);
            chipScroll.addView(chipRow);
            mChipBar = chipScroll;
            mChipMain = UiComponents.chipButton(a, "", new View.OnClickListener() {
                public void onClick(View v) {
                    int total = sSel.size();
                    boolean all = total > 0 && countPicked() >= total;
                    java.util.Iterator<String> it = sSel.keySet().iterator();
                    while (it.hasNext()) {
                        sSel.put(it.next(), Boolean.valueOf(!all));
                    }
                    fillFuncs(a);
                    updateActionLabel();
                    updateChipBar();
                }
            }, false);
            // 排序（左→右）：[批量多选/退出批量][状态小字常驻][全选/取消（仅批量时）]
            mChipCancel = UiComponents.chipButton(a, sMulti ? "退出批量" : "批量多选", new View.OnClickListener() {
                public void onClick(View v) {
                    sMulti = !sMulti;
                    fillFuncs(a);
                    updateActionLabel();
                    updateChipBar();
                }
            }, false);
            chipRow.addView(mChipCancel, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            // 状态小字：批量时「待保留 X 项，待清除 Y 项」；否则「当前应用有 N 项偏离默认值」
            mChipState = UiKit.text(a, "", 12, UiKit.textSub(a));
            LinearLayout.LayoutParams chipStateLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            chipStateLp.leftMargin = UiKit.dpI(a, 6);
            chipStateLp.rightMargin = UiKit.dpI(a, 10);
            chipRow.addView(mChipState, chipStateLp);
            LinearLayout.LayoutParams chipMainLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            chipMainLp.leftMargin = UiKit.dpI(a, 8);
            chipRow.addView(mChipMain, chipMainLp);
            body.addView(mChipBar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

            mFuncBody = UiKit.vbox(a);
            body.addView(mFuncBody);

            // 危险提示常驻
            mDangerTip = UiKit.text(a,
                    "危险操作：将清空所勾选功能的自定义数据，无法撤销；"
                            + "建议先在「配置备份」导出一份",
                    11, UiKit.danger(a));
            mDangerTip.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10),
                    UiKit.dpI(a, 4), UiKit.dpI(a, 4));
            body.addView(mDangerTip, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

            mAllBtn = UiComponents.warnButton(a, "",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            // 两步确认
                            final TextView btn = (TextView) v;
                            final int picked = countPicked();
                            if (picked == 0) {
                                toast(a, "没有勾选任何项，请先勾选要恢复的功能");
                                btn.setTag(null);
                                return;
                            }
                            if (!"confirm".equals(btn.getTag())) {
                                btn.setTag("confirm");
                                UiKit.haptic(v);
                                btn.setText("确认清除？再点一次");
                                toast(a, "再点一次确认清除");
                                btn.postDelayed(new Runnable() {
                                    public void run() {
                                        if ("confirm".equals(btn.getTag())) {
                                            btn.setTag(null);
                                            updateActionLabel();
                                        }
                                    }
                                }, UiKit.TWO_STEP_ARM_MS);
                                return;
                            }
                            btn.setTag(null);
                            HookInit h = ReaLog.sHookInit;
                            if (h == null) {
                                toast(a, "模块还没就绪，稍后再试");
                                return;
                            }
                            final java.util.Set<String> ids = pickedIds();
                            try {
                                h.uiResetToDefault(ids);
                                ReaLog.log("system", ids == null
                                        ? "恢复初始状态：全部"
                                        : ("恢复初始状态：" + ids.size() + " 项"));
                                toast(a, ids == null
                                        ? "已恢复全部初始设置"
                                        : ("已恢复 " + ids.size() + " 项初始设置"));
                                sSel.clear();
                                UiComponents.restartHint(a);
                            } catch (Throwable t) {
                                toast(a, "恢复失败，请重试");
                                ReaLog.log("ui", "恢复默认值失败: " + t);
                            }
                            updateActionLabel();
                            finishReset(a);
                        }
                    });
            body.addView(mAllBtn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(UiComponents.scroll(a, body, "scroll:reset"),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            return root;
        }

        /** 离开这页就退出批量状态 */
        public void onHide() {
            PageStack ps = PageStack.get();
            if (ps == null) {
                sMulti = false;
                return;
            }
            for (int i = 0; i < ps.depth(); i++) {
                PageStack.Page p = ps.pageAt(i);
                if (p instanceof ResetFuncPage || p instanceof ResetItemsPage) return;
            }
            sMulti = false;
            if (!ps.contains(this) || ps.isReplacing()) {
                // 真离开恢复页：清空勾选集，下次进入重新按默认全选渲染
                sSel.clear();
            }
        }

        private void updateChipBar() {
            // 小字常驻：批量时显示待保留/待清除计数，否则显示偏离默认值计数
            if (mChipState != null) {
                mChipState.setText(sMulti
                        ? UiComponents.pickStateLabel(sSel.size(), countPicked())
                        : (mChangedCount > 0
                                ? "当前应用有 " + mChangedCount + " 项偏离默认值"
                                : "当前应用所有配置都在默认值"));
            }
            if (mChipCancel != null) {
                mChipCancel.setText(sMulti ? "退出批量" : "批量多选");
            }
            if (mChipMain == null) return;
            mChipMain.setVisibility(sMulti ? View.VISIBLE : View.GONE);
            if (!sMulti) return;
            int total = sSel.size();
            int picked = countPicked();
            boolean all = total > 0 && picked >= total;
            mChipMain.setText(UiComponents.pickMainLabel(all));
        }

        /** 备份提示每个进程只弹一次 */
        private static boolean sBackupHinted;

        /** 每次回到这页都重算统计 */
        public void onShow() {
            if (mA != null) fillFuncs(mA);
            updateActionLabel();
            updateChipBar();
            if (!sBackupHinted) {
                sBackupHinted = true;
                if (mA != null) {
                    toast(mA, "建议先 配置备份！\n再进行 恢复重置！");
                }
            }
        }

        private void fillFuncs(final Activity a) {
            if (mFuncBody == null) return;
            String app = UiTabs.app();
            final java.util.List<HookInit.ResetEntry> entries = HookInit.RESET_ENTRIES;
            ensureSelection(entries);
            int changed = 0;
            // 分组去重保序，顺手记每组 [总数, 已改默认数]：一次遍历够用
            java.util.List<String> funcs = new java.util.ArrayList<String>();
            java.util.Map<String, int[]> stat = new java.util.HashMap<String, int[]>();
            for (int i = 0; i < entries.size(); i++) {
                HookInit.ResetEntry e = entries.get(i);
                int[] c = stat.get(e.func);
                if (c == null) {
                    c = new int[2];
                    stat.put(e.func, c);
                    funcs.add(e.func);
                }
                c[0]++;
                if (e.isNonDefault(app)) {
                    c[1]++;
                    changed++;
                }
            }
            mChangedCount = changed;
            mFuncBody.removeAllViews();
            UiComponents.CardGroup card = UiComponents.cardGroup(a, "功能列表", true);
            for (int fi = 0; fi < funcs.size(); fi++) {
                final String func = funcs.get(fi);
                int[] c = stat.get(func);
                LinearLayout funcRow = UiTabs.entryRow(a, func,
                        c[0] + " 项设置" + (c[1] > 0 ? " · " + c[1] + " 项已改默认" : " · 均为默认"),
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                if (sMulti) {
                                    toggleGroup(entries, func);
                                    fillFuncs(a);
                                    updateActionLabel();
                                    updateChipBar();
                                    return;
                                }
                                PageStack ps = PageStack.get();
                                if (ps != null) ps.push(new ResetFuncPage(func));
                            }
                        });
                final boolean groupAll = groupAllPicked(entries, func);
                TextView mark = UiKit.text(a, groupAll ? "●" : "○", 16,
                        groupAll ? UiKit.accent(a) : UiKit.textSub(a));
                mark.setPadding(UiKit.dpI(a, 10), 0, UiKit.dpI(a, 2), 0);
                mark.setVisibility(sMulti ? View.VISIBLE : View.GONE);
                funcRow.addView(mark, 0);
                // 长按 = 进入/退出多选
                funcRow.setOnLongClickListener(new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        UiKit.haptic(v);
                        sMulti = !sMulti;
                        fillFuncs(a);
                        updateActionLabel();
                        updateChipBar();
                        toast(a, sMulti ? "已进入多选：再次长按可退出"
                                : "已退出多选");
                        return true;
                    }
                });
                card.addRow(funcRow);
            }
            mFuncBody.addView(card.root);
        }

        private boolean groupAllPicked(java.util.List<HookInit.ResetEntry> entries, String func) {
            java.util.List<String> keys = groupKeys(entries, func);
            if (keys.isEmpty()) return false;
            for (int j = 0; j < keys.size(); j++) {
                if (!Boolean.TRUE.equals(sSel.get(keys.get(j)))) return false;
            }
            return true;
        }

        private void toggleGroup(java.util.List<HookInit.ResetEntry> entries, String func) {
            java.util.List<String> keys = groupKeys(entries, func);
            boolean toPick = !groupAllPicked(entries, func);
            for (int j = 0; j < keys.size(); j++) {
                sSel.put(keys.get(j), Boolean.valueOf(toPick));
            }
        }

        /** 进入页面默认全部勾选（勾选=恢复；用户手动改过的不再覆盖 */
        private void ensureSelection(java.util.List<HookInit.ResetEntry> entries) {
            for (int i = 0; i < entries.size(); i++) {
                HookInit.ResetEntry e = entries.get(i);
                if (e.defaultMap() != null) {
                    java.util.Iterator<String> it = e.defaultMap().keySet().iterator();
                    while (it.hasNext()) {
                        String k = e.id + "::" + it.next();
                        if (!sSel.containsKey(k)) sSel.put(k, Boolean.TRUE);
                    }
                } else {
                    if (!sSel.containsKey(e.id)) sSel.put(e.id, Boolean.TRUE);
                }
            }
        }
    }

    /** 恢复初始状态：二级半屏（某功能下的全部条目，逐项勾选）*/
    public static class ResetFuncPage extends PageStack.Page {
        private final String mFunc;
        private Activity mA;
        public ResetFuncPage(String func) {
            mFunc = func;
        }

        public String key() {
            return "resetfunc:" + mFunc;
        }

        public String title() {
            return mFunc;
        }

        public void onHide() {
            PageStack ps = PageStack.get();
            if (ps == null || (!ps.contains(this) && !ps.isReplacing())) sFuncMulti = false;
        }

        public boolean halfScreen() {
            return true;
        }

        private TextView mToggleBtn;
        private TextView mToggleState;
        private TextView mChipCancel;

        /** 本级自己的多选标记 */
        private static boolean sFuncMulti;

        /** 行首圆点：多选模式显示；多选时点按整行=切换勾选 */
        private void addRowMark(final Activity a, LinearLayout row, final String key,
                boolean picked) {
            final TextView mark = resetRowMark(a, picked, sFuncMulti);
            attachRowMark(row, mark, new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    UiKit.haptic(v);
                    sFuncMulti = !sFuncMulti;
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.replaceTop(new ResetFuncPage(mFunc));
                    return true;
                }
            });
            if (sFuncMulti) {
                // 多选模式：隐藏行尾开关，点按整行切换勾选；退出多选重建页面恢复原行为
                UiComponents.hideRowSwitch(row);
                row.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        boolean now = !Boolean.TRUE.equals(
                                ResetSheetPage.sSel.get(key));
                        ResetSheetPage.sSel.put(key, Boolean.valueOf(now));
                        mark.setText(now ? "●" : "○");
                        mark.setTextColor(now ? UiKit.accent(a) : UiKit.textSub(a));
                        refreshToggle();
                    }
                });
            }
        }

        /** 胶囊按钮：「全选/取消」+ 右侧小字「待保留 X 项，待清除 Y 项」 */
        private void refreshToggle() {
            if (mToggleBtn == null || mA == null) return;
            java.util.List<String> keys = groupKeys(HookInit.RESET_ENTRIES, mFunc);
            int picked = 0;
            for (int i = 0; i < keys.size(); i++) {
                if (Boolean.TRUE.equals(ResetSheetPage.sSel.get(keys.get(i)))) picked++;
            }
            boolean keep = !keys.isEmpty() && picked >= keys.size();
            mToggleBtn.setText(UiComponents.pickMainLabel(keep));
            if (mToggleState != null)
                mToggleState.setText(
                        UiComponents.pickStateLabel(keys.size(), picked));
        }

        public View build(final Activity a) {
            mA = a;
            final String app = UiTabs.app();
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, mFunc, null));

            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 12));

            // 二选一小胶囊靠右；多选时旁边出现「取消」
            LinearLayout bar = UiKit.hbox(a);
            bar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            bar.setPadding(0, UiKit.dpI(a, 10), 0, UiKit.dpI(a, 14));
            bar.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            mToggleBtn = UiComponents.chipButton(a, "", new View.OnClickListener() {
                public void onClick(View v) {
                    java.util.List<String> keys = groupKeys(HookInit.RESET_ENTRIES, mFunc);
                    boolean allPicked = !keys.isEmpty();
                    for (int i = 0; i < keys.size(); i++) {
                        if (!Boolean.TRUE.equals(ResetSheetPage.sSel.get(keys.get(i)))) {
                            allPicked = false;
                            break;
                        }
                    }
                    for (int i = 0; i < keys.size(); i++) {
                        ResetSheetPage.sSel.put(keys.get(i),
                                Boolean.valueOf(!allPicked));
                    }
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.replaceTop(new ResetFuncPage(mFunc));
                }
            }, false);
            // 状态小字：待保留 X 项，待清除 Y 项
            mToggleState = UiKit.text(a, "", 12, UiKit.textSub(a));
            LinearLayout.LayoutParams toggleStateLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            toggleStateLp.leftMargin = UiKit.dpI(a, 6);
            toggleStateLp.rightMargin = UiKit.dpI(a, 10);
            mChipCancel = UiComponents.chipButton(a, sFuncMulti ? "退出批量" : "批量多选",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            sFuncMulti = !sFuncMulti;
                            PageStack ps = PageStack.get();
                            if (ps != null) ps.replaceTop(new ResetFuncPage(mFunc));
                        }
                    }, false);
            LinearLayout.LayoutParams cancelLp2 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelLp2.leftMargin = UiKit.dpI(a, 8);
            bar.addView(mChipCancel, cancelLp2);
            bar.addView(mToggleState, toggleStateLp);
            bar.addView(mToggleBtn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            android.widget.HorizontalScrollView funcScroll = new android.widget.HorizontalScrollView(a);
            funcScroll.setHorizontalScrollBarEnabled(false);
            funcScroll.setVerticalScrollBarEnabled(false);
            funcScroll.addView(bar);
            body.addView(funcScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            UiComponents.CardGroup card = UiComponents.cardGroup(a, mFunc, true);
            java.util.List<HookInit.ResetEntry> entries = HookInit.RESET_ENTRIES;
            for (int i = 0; i < entries.size(); i++) {
                final HookInit.ResetEntry e = entries.get(i);
                if (!e.func.equals(mFunc)) continue;
                if (e.defaultMap() != null) {
                    // 明细类：子功能直接展开到二级（开关粒度）
                    java.util.Map<String, Boolean> live = null;
                    Object cur = e.target instanceof java.util.Map
                            ? ((java.util.Map) e.target).get(app) : null;
                    if (cur instanceof java.util.Map) {
                        try {
                            live = (java.util.Map<String, Boolean>) cur;
                        } catch (Throwable ignored) {
                        }
                    }
                    java.util.Iterator<String> it = e.defaultMap().keySet().iterator();
                    while (it.hasNext()) {
                        final String sub = it.next();
                        String defTxt = Boolean.TRUE.equals(e.defaultMap().get(sub))
                                ? "开" : "关";
                        String curTxt = (live != null && live.containsKey(sub))
                                ? (Boolean.TRUE.equals(live.get(sub)) ? "开" : "关")
                                : defTxt;
                        final String skey = e.id + "::" + sub;
                        if (!ResetSheetPage.sSel.containsKey(skey)) {
                            ResetSheetPage.sSel.put(skey,
                                    Boolean.valueOf(!defTxt.equals(curTxt)));
                        }
                        final boolean pickedSub = Boolean.TRUE.equals(
                                ResetSheetPage.sSel.get(skey));
                        LinearLayout rowSub = UiComponents.switchRow(a,
                                UiTabs.resetItemLabel(e.id, sub),
                                "默认：" + defTxt + " · 当前：" + curTxt,
                                pickedSub,
                                new UiComponents.OnChecked() {
                                    public void onChecked(boolean checked) {
                                        ResetSheetPage.sSel.put(skey, Boolean.valueOf(checked));
                                        refreshToggle();
                                    }
                                });
                        addRowMark(a, rowSub, skey, pickedSub);
                        card.addRow(rowSub);
                    }
                    continue;
                }
                boolean mod = e.isNonDefault(app);
                if (e.isList) {
                    // 列表/规则类：点进去逐项剔除
                    LinearLayout rowList = UiTabs.entryRow(a, e.label,
                            "默认：" + e.defaultDesc + " · 当前：" + e.currentDesc(app)
                                    + " · 点击进入逐项剔除",
                            new View.OnClickListener() {
                                public void onClick(View v) {
                                    PageStack ps = PageStack.get();
                                    if (ps != null) ps.push(new ResetItemsPage(e.id));
                                }
                            });
                    addRowMark(a, rowList, e.id, Boolean.TRUE.equals(
                            ResetSheetPage.sSel.get(e.id)));
                    card.addRow(rowList);
                    continue;
                }
                // 勾选状态写到一级页面共享的选择集
                if (!ResetSheetPage.sSel.containsKey(e.id)) {
                    ResetSheetPage.sSel.put(e.id, Boolean.valueOf(mod));
                }
                final boolean pickedTop = Boolean.TRUE.equals(
                        ResetSheetPage.sSel.get(e.id));
                LinearLayout rowTop = UiComponents.switchRow(a, e.label,
                        "默认：" + e.defaultDesc + " · 当前：" + e.currentDesc(app),
                        pickedTop,
                        new UiComponents.OnChecked() {
                            public void onChecked(boolean checked) {
                                ResetSheetPage.sSel.put(e.id, Boolean.valueOf(checked));
                                refreshToggle();
                            }
                        });
                addRowMark(a, rowTop, e.id, pickedTop);
                card.addRow(rowTop);
            }
            body.addView(card.root);

            TextView hint = UiKit.text(a,
                    "勾选要恢复的项后返回，在上一页统一恢复；长按列表行可批量勾选",
                    11, UiKit.textSub(a));
            hint.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10),
                    UiKit.dpI(a, 4), UiKit.dpI(a, (4)));
            body.addView(hint);

            root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-reset-func"),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            refreshToggle();
            return root;
        }
    }

    /** 恢复初始状态：三级半屏（列表类条目下的逐项剔除）。 */
    public static class ResetItemsPage extends PageStack.Page {

        private final String mId;

        /** 逐项剔除的选择集：key = "条目id::原始item"，跨页面重建共享，真离开时清空 */
        private static final java.util.Map<String, Boolean> mSel = new java.util.HashMap<
                String, Boolean>();

        private Activity mA;
        private TextView mChipMain;
        private TextView mChipState;
        private TextView mChipCancel;
        private TextView mOkBtn;

        /** 本级自己的多选标记 */
        private static boolean sItemsMulti;
        private String kOf(String item) {
            return mId + "::" + item;
        }

        public ResetItemsPage(String id) {
            mId = id;
        }

        public String key() {
            return "resetitems:" + mId;
        }

        public String title() {
            return "逐项剔除";
        }

        public boolean halfScreen() {
            return true;
        }

        public View build(final Activity a) {
            mA = a;
            String app = UiTabs.app();
            HookInit.ResetEntry found = null;
            java.util.List<HookInit.ResetEntry> entries = HookInit.RESET_ENTRIES;
            for (int i = 0; i < entries.size(); i++) {
                HookInit.ResetEntry e = entries.get(i);
                if (e.id.equals(mId)) {
                    found = e;
                    break;
                }
            }
            final HookInit.ResetEntry entry = found;
            String pageTitle = entry != null ? entry.label : "逐项剔除";
            final java.util.List<String> items = entry != null
                    ? entry.currentItems(app)
                    : new java.util.ArrayList<String>();
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, pageTitle, null));
            LinearLayout body = UiKit.vbox(a);
            body.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), 0,
                    UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 12));

            // 多选操作条
            LinearLayout bar = UiKit.hbox(a);
            bar.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            bar.setPadding(0, UiKit.dpI(a, 10), 0, UiKit.dpI(a, 12));
            bar.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            boolean itemsMulti = sItemsMulti;
            android.widget.HorizontalScrollView itemScroll = new android.widget.HorizontalScrollView(a);
            itemScroll.setHorizontalScrollBarEnabled(false);
            itemScroll.setVerticalScrollBarEnabled(false);
            itemScroll.addView(bar);
            mChipMain = UiComponents.chipButton(a, "", new View.OnClickListener() {
                public void onClick(View v) {
                    int total = items.size();
                    int picked = 0;
                    for (int i = 0; i < total; i++) {
                        if (Boolean.TRUE.equals(mSel.get(kOf(items.get(i))))) picked++;
                    }
                    boolean all = total > 0 && picked >= total;
                    for (int i = 0; i < total; i++) {
                        mSel.put(kOf(items.get(i)), Boolean.valueOf(!all));
                    }
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.replaceTop(new ResetItemsPage(mId));
                }
            }, false);
            // 状态小字：待保留 X 项，待清除 Y 项
            mChipState = UiKit.text(a, "", 12, UiKit.textSub(a));
            LinearLayout.LayoutParams chipStateLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            chipStateLp.leftMargin = UiKit.dpI(a, 6);
            chipStateLp.rightMargin = UiKit.dpI(a, 10);
            mChipCancel = UiComponents.chipButton(a, sItemsMulti ? "退出批量" : "批量多选", new View.OnClickListener() {
                public void onClick(View v) {
                    sItemsMulti = !sItemsMulti;
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.replaceTop(new ResetItemsPage(mId));
                }
            }, false);
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
            cancelLp.leftMargin = UiKit.dpI(a, 8);
            bar.addView(mChipCancel, cancelLp);
            bar.addView(mChipState, chipStateLp);
            bar.addView(mChipMain, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            body.addView(itemScroll, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

            UiComponents.CardGroup card = UiComponents.cardGroup(a, "勾选要剔除的数据", true);
            for (int i = 0; i < items.size(); i++) {
                final String it = items.get(i);
                //默认全勾（进入这页的意图通常就是清空列表）；重建不覆盖已改过的
                if (!mSel.containsKey(kOf(it))) {
                    mSel.put(kOf(it), Boolean.TRUE);
                }
                final boolean pickedIt = Boolean.TRUE.equals(mSel.get(kOf(it)));
                final TextView mark = resetRowMark(a, pickedIt, sItemsMulti);
                final LinearLayout rowIt = UiComponents.switchRow(a,
                        UiTabs.resetItemLabel(mId, it),
                        "开启=清除本条数据 关闭=保留本条数据",
                        pickedIt, new UiComponents.OnChecked() {
                            public void onChecked(boolean checked) {
                                mSel.put(kOf(it), Boolean.valueOf(checked));
                                mark.setText(checked ? "●" : "○");
                                mark.setTextColor(checked ? UiKit.accent(a) : UiKit.textSub(a));
                                refreshItemsChip(items.size());
                            }
                        });
                attachRowMark(rowIt, mark, new View.OnLongClickListener() {
                    public boolean onLongClick(View v) {
                        UiKit.haptic(v);
                        sItemsMulti = !sItemsMulti;
                        PageStack ps = PageStack.get();
                        if (ps != null) ps.replaceTop(new ResetItemsPage(mId));
                        return true;
                    }
                });
                if (sItemsMulti) {
                    UiComponents.hideRowSwitch(rowIt);
                    rowIt.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            boolean now = !Boolean.TRUE.equals(mSel.get(kOf(it)));
                            mSel.put(kOf(it), Boolean.valueOf(now));
                            mark.setText(now ? "●" : "○");
                            mark.setTextColor(now ? UiKit.accent(a) : UiKit.textSub(a));
                            refreshItemsChip(items.size());
                        }
                    });
                }
                card.addRow(rowIt);
            }
            body.addView(card.root);
            if (items.isEmpty()) {
                TextView empty = UiKit.text(a, "该项当前没有数据，无需剔除",
                        12, UiKit.textSub(a));
                empty.setPadding(UiKit.dpI(a, 4), UiKit.dpI(a, 10),
                        UiKit.dpI(a, 4), UiKit.dpI(a, 4));
                body.addView(empty);
            }

            mOkBtn = UiComponents.warnButton(a, "",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            final TextView btn = (TextView) v;
                            if (!"confirm".equals(btn.getTag())) {
                                btn.setTag("confirm");
                                UiKit.haptic(v);
                                btn.setText("确认清除？再点一次");
                                toast(a, "再点一次确认清除");
                                btn.postDelayed(new Runnable() {
                                    public void run() {
                                        if ("confirm".equals(btn.getTag())) {
                                            btn.setTag(null);
                                            refreshOkLabel();
                                        }
                                    }
                                }, UiKit.TWO_STEP_ARM_MS);
                                return;
                            }
                            btn.setTag(null);
                            refreshOkLabel();
                            HookInit h = ReaLog.sHookInit;
                            if (h == null) {
                                toast(a, "模块还没就绪，稍后再试");
                                return;
                            }
                            java.util.Set<String> labels = new java.util.HashSet<String>();
                            java.util.Iterator<String> it2 = mSel.keySet().iterator();
                            while (it2.hasNext()) {
                                String k = it2.next();
                                if (!k.startsWith(mId + "::")) continue;
                                if (Boolean.TRUE.equals(mSel.get(k))) {
                                    labels.add(k.substring(mId.length() + 2));
                                }
                            }
                            if (labels.isEmpty()) {
                                // 空选统一处理
                                btn.setTag(null);
                                refreshOkLabel();
                                toast(a, "没有勾选任何项");
                                return;
                            }
                            try {
                                h.uiResetListItems(mId, labels);
                                ReaLog.log("system", "恢复初始状态：剔除 " + labels.size() + " 条子项");
                                toast(a, "已剔除 " + labels.size() + " 条数据");
                                UiComponents.restartHint(a);
                            } catch (Throwable t) {
                                toast(a, "剔除失败，请重试");
                                ReaLog.log("ui", "剔除列表项失败: " + t);
                            }
                            finishReset(a);
                        }
                    });
            body.addView(mOkBtn, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

            root.addView(UiComponents.scrollNoFooter(a, body, "scroll:sheet-reset-items"),
                    new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            refreshItemsChip(items.size());
            return root;
        }

        public void onHide() {
            PageStack ps = PageStack.get();
            if (ps == null || (!ps.contains(this) && !ps.isReplacing())) {
                mSel.clear();
                sItemsMulti = false;
            }
        }

        private void refreshItemsChip(int total) {
            if (mChipMain == null || mA == null) return;
            int picked = 0;
            java.util.Iterator<String> it = mSel.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (k.startsWith(mId + "::")
                        && Boolean.TRUE.equals(mSel.get(k))) picked++;
            }
            mChipMain.setText(UiComponents.pickMainLabel(total > 0 && picked >= total));
            if (mChipState != null) mChipState.setText(UiComponents.pickStateLabel(total, picked));
            refreshOkLabel();
        }

        /** 底部确认按钮文案：计数=本级 mSel（该列表的全部条目） */
        private void refreshOkLabel() {
            if (mOkBtn == null) return;
            int total = 0;
            int picked = 0;
            java.util.Iterator<String> it = mSel.keySet().iterator();
            while (it.hasNext()) {
                String k = it.next();
                if (!k.startsWith(mId + "::")) continue;
                total++;
                if (Boolean.TRUE.equals(mSel.get(k))) picked++;
            }
            mOkBtn.setText(UiComponents.clearLabel(total, picked));
        }

    }

    /** 恢复初始状态收尾：作废快照 + 回一级 + 悬浮窗即时复位 + 刷新全部标签 */
    private static void finishReset(Activity a) {
        try {
            PageStack.bumpDataVersion();
        } catch (Throwable ignored) {
        }
        PageStack ps = PageStack.get();
        if (ps != null) {
            ps.pop();
            try {
                ps.rebuildAll();
            } catch (Throwable ignored) {
            }
        }
        try {
            HookInit h = ReaLog.sHookInit;
            if (h != null) h.uiApplyFloatingConfig(a);
        } catch (Throwable ignored) {
        }
        if (home() != null) {
            try {
                home().refreshAllTabs();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 行首圆点：多选模式才显示；返回圆点本身，调用方在勾选变化时同步更新*/
    private static TextView resetRowMark(Activity a, boolean picked, boolean multi) {
        TextView mark = UiKit.text(a, picked ? "●" : "○", 16,
                picked ? UiKit.accent(a) : UiKit.textSub(a));
        mark.setPadding(UiKit.dpI(a, 10), 0, UiKit.dpI(a, 2), 0);
        mark.setVisibility(multi ? View.VISIBLE : View.GONE);
        return mark;
    }

    /** 把圆点挂到行首 + 绑长按 */
    private static void attachRowMark(LinearLayout row, TextView mark,
            View.OnLongClickListener lc) {
        row.addView(mark, 0);
        row.setOnLongClickListener(lc);
    }

    /** 清理页里的一行只读信息 */
    private static View valueRow(Activity a, String label, String value) {
        LinearLayout row = UiKit.hbox(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        row.setPadding(pd, UiKit.dpI(a, 11), pd, UiKit.dpI(a, 11));
        row.addView(UiKit.text(a, label, 14, UiKit.textMain(a)),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(UiKit.text(a, value, 13, UiKit.textSub(a)));
        return row;
    }

    private static void toast(Activity a, String msg) {
        try {
            ToastUtil.showUnique(a, msg);
        } catch (Throwable ignored) {
        }
    }
}

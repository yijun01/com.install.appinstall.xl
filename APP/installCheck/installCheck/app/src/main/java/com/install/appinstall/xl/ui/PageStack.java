package com.install.appinstall.xl.ui;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PageStack —— 宿主 Activity 上的页面式 UI 容器（View 注入 android.R.id.content）
 */
public class PageStack {
    /** 页面栈深度上限*/
    private static final int MAX_DEPTH = 8;
    /** 连点防抖 */
    private static final long PUSH_GAP_MS = 300;
    private static final int TRANSITION_MS = 240;
    /** 半屏高度占屏比 */
    private static final float HALF_RATIO = 0.62f;
    /** 出场动画时长：整层淡出 */
    private static final int CLOSE_FADE_MS = 160;
    /** 出场后清空内容（子视图/底色）的延迟 */
    private static final int CLOSE_CLEAR_MS = 180;
    /** 出场后把根容器从 content 摘除的延迟（须大于 CLOSE_FADE_MS，等淡出画完） */
    private static final int CLOSE_REMOVE_MS = 560;
    /** mFinishOnClose 时延迟 finish 的间隔（须大于 CLOSE_REMOVE_MS） */
    private static final int CLOSE_FINISH_MS = 620;
    /** 背景层旧页延迟摘除的裕量（叠在转场时长之上，等滑出动画画完） */
    private static final int KEEP_REMOVE_EXTRA_MS = 80;
    /** 转场动画期间不允许新的按下 */
    private static final float SWIPE_CANCEL_DIM_MAX = 0.6f;
    /** pop 时新页从左侧滑回的距离系数（push 从右侧进，方向相反） */
    private static final float POP_SLIDE_FACTOR = 0.55f;
    /** 侧滑关闭判定：位移占屏宽比例 */
    private static final float SWIPE_FINISH_RATIO = 0.28f;
    /** 侧滑起步判定：横向位移须明显大于纵向 */
    private static final float SWIPE_SLOPE = 1.5f;
    /** 侧滑有效移动阈值 */
    private static final int SWIPE_START_DP = 12;
    /** 键盘高度阈值：小于它视为系统栏/测量抖动 */
    private static final int KEYBOARD_MIN_DP = 80;
    /** 二次校正 inset 的补测延迟 */
    private static final int INSET_RECHECK_MS = 120;
    /** 下拉刷新收起后再重建的间隔 */
    private static final int REFRESH_REBUILD_MS = 350;
    /** 半屏侧滑/拖拽的起步阈值 */
    private static final int SHEET_DRAG_SLOP_DP = 3;
    /** 底栏高度（dp） */
    private static final int BOTTOM_BAR_H = 40;
    
    // ============================================================
    //  页面基类
    // ============================================================
    public abstract static class Page {
        private View mView;
        /** build 时的全局数据版本；和当前版本不一致说明数据变了，页面要重建 */
        public int builtVersion = -1;
        /** 唯一标识：栈顶 key 相同则不新建，直接刷新 */
        public abstract String key();
        public abstract String title();
        /** 构建页面 View。允许抛异常，由调用方回退旧 UI */
        public abstract View build(Activity activity);
        /** 每次再现时调用：从 HookInit 静态 Map 重读数据，避免 stale*/
        public void onShow() {}
        /** 离开（被覆盖或出栈）时调用，供释放临时资源 */
        public void onHide() {}
        /** 是否半屏抽屉 */
        public boolean halfScreen() {
            return false;
        }

        /**
         * 自带下拉刷新容器
         */
        public boolean hasOwnRefresh() {
            return false;
        }

        /** 半屏页是否允许下拉关闭 */
        public boolean sheetCancelable() {
            return true;
        }

        public final View view() {
            return mView;
        }

        final void attachView(View v) {
            mView = v;
        }
    }

    // ============================================================
    //  单例
    // ============================================================
    private static PageStack sInstance;

    /** 立即关闭当前新 UI（供悬浮窗安全阀调用，不依赖实例引用） */
    public static void closeAll() {
        PageStack s = sInstance;
        if (s != null) {
            s.close();
        }
    }

    public static PageStack get() {
        return sInstance;
    }

    /** 模块主页打开页面栈时设 true：栈被关闭时直接 finish Activity */
    public void setFinishActivityOnClose(boolean finish) {
        mFinishOnClose = finish;
    }

    public static boolean isOpen() {
        return sInstance != null;
    }

    /** 打开页面栈并 push 首页 */
    public static PageStack open(Activity activity, Page home) {
        if (activity == null || home == null) return null;
        if (sInstance != null) {
            if (sInstance.mActivity == activity && sInstance.mRoot != null
                    && sInstance.mRoot.getParent() != null) {
                // 用户重新打开界面：允许首页再淡入一次（popToRoot 里会消耗它）
                sInstance.mFirstEnter = true;
                sInstance.popToRoot();
                return sInstance;
            }
            sInstance.close();
        }
        PageStack stack = new PageStack(activity);
        if (!stack.attach(home)) {
            stack.close();
            return null;
        }
        sInstance = stack;
        return stack;
    }

    // ============================================================
    //  实例状态
    // ============================================================
    private final Activity mActivity;
    private final List<Page> mStack = new ArrayList<Page>();
    private final Map<String, View> mCache = new HashMap<String, View>();
    private final FrameLayout mRoot;
    private final FrameLayout mHost;
    private final View mDim;

    /** 状态栏区域的色条*/
    private final View mTopStrip;

    /** 底部系统栏（导航栏/手势条）区域的色条 */
    private final View mBottomStrip;

    /** 半屏页面的背景层*/
    private final FrameLayout mBackdropHost;

    /**
     * 半屏高度动画
     */
    private android.animation.ValueAnimator mHeightAnim;

    /** 上次停留的页面 key：退出界面后再打开可以回到同一页 */
    private static String sLastKey;

    /** 全局底部导航栏：返回页面 / 返回宿主 / 设置*/
    private final FrameLayout mBottomBar;

    private int mBottomBarH;
    // 底栏「设置」按钮的选中态
    private TextView mNavIcon;
    private View mNavUnder;

    /** 首页构建时登记的悬浮窗视图 */
    public static android.widget.TextView sFloatingView;

    /** close 后直接 finish 宿主 Activity*/
    private boolean mFinishOnClose;

    private boolean mAnimating;
    private int mTopInset;

    /** 底部导航栏 inset*/
    private int mBottomInset;

    /** 当前是否半屏 */
    private boolean mHalfShowing;

    /** 下一次转场是不是「返回」：决定新页从左边还是右边滑入 */
    private boolean mPopAnim;

    /** 输入法监听 */
    private android.view.ViewTreeObserver.OnGlobalLayoutListener mKeyboardListener;

    /** 背景层旧页的延迟摘除任务*/
    private Runnable mBackdropRemove;

    /**
     * 界面首次打开的整层淡入只允许播一次
     */
    private boolean mFirstEnter = true;

    /** 当前输入法高度（0 = 没有键盘）*/
    private int mKeyboardHeight;

    /** 键盘弹起前的半屏高度*/
    private int mHeightBeforeKeyboard;

    private PageStack(Activity activity) {
        mActivity = activity;
        mRoot = new RootLayout(activity);
        mRoot.setBackgroundColor(UiKit.bg(activity));
        mRoot.setFocusable(true);
        mRoot.setFocusableInTouchMode(true);
        mRoot.setClickable(true);
        mRoot.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                mRoot.setElevation(999999999f);
                mRoot.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRect(0, 0, view.getWidth(), view.getHeight());
                        outline.setAlpha(0f);
                    }
                });
            } catch (Throwable ignored) {
            }
        }

        // 状态栏区域铺顶栏同色
        mTopStrip = new View(activity);
        mTopStrip.setBackgroundColor(UiKit.topBarBg(activity));
        FrameLayout.LayoutParams stripP = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0);
        stripP.gravity = Gravity.TOP;
        mTopStrip.setVisibility(View.GONE);
        mRoot.addView(mTopStrip, stripP);
        mBottomStrip = new View(activity);
        FrameLayout.LayoutParams bstripP = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0);
        bstripP.gravity = Gravity.BOTTOM;
        mBottomStrip.setVisibility(View.GONE);
        mRoot.addView(mBottomStrip, bstripP);
        mBackdropHost = new FrameLayout(activity);
        mBackdropHost.setBackgroundColor(UiKit.bg(activity));
        mRoot.addView(mBackdropHost, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mDim = new View(activity);
        mDim.setBackgroundColor(UiKit.mask(activity));
        mDim.setVisibility(View.GONE);
        mDim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Page top = topPage();
                if (top != null && top.halfScreen() && top.sheetCancelable()) {
                    pop();
                }
            }
        });
        mRoot.addView(mDim, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mHost = new FrameLayout(activity);
        mHost.setClickable(true);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        mHost.setLayoutParams(hp);
        mHost.setBackgroundColor(UiKit.bg(activity));
        mRoot.addView(mHost);
        mBottomBar = buildBottomBar(activity);
        mBottomBarH = UiKit.dpI(activity, BOTTOM_BAR_H);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, mBottomBarH);
        bp.gravity = Gravity.BOTTOM;
        mRoot.addView(mBottomBar, bp);

        // 输入法监听
        mKeyboardListener = new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                int kb = detectKeyboardHeight();
                if (kb == mKeyboardHeight) return;
                int prev = mKeyboardHeight;
                mKeyboardHeight = kb;
                if (!mHalfShowing) return;
                if (prev == 0 && kb > 0) {
                    // 记下弹起前的高度，收起时原样还回去
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
                    mHeightBeforeKeyboard = (lp != null && lp.height > 0) ? lp.height : halfHeight();
                }
                applyKeyboardHeight(kb);
            }
        };
        mRoot.getViewTreeObserver().addOnGlobalLayoutListener(mKeyboardListener);
    }

    /** 摘掉输入法监听*/
    private void removeKeyboardListener() {
        if (mKeyboardListener == null) return;
        try {
            mRoot.getViewTreeObserver().removeOnGlobalLayoutListener(mKeyboardListener);
        } catch (Throwable ignored) {
        }
        mKeyboardListener = null;
    }

    /** 读当前输入法高度：窗口可见区域底边到真实屏幕底边的高度差 */
    private int detectKeyboardHeight() {
        try {
            android.graphics.Rect r = new android.graphics.Rect();
            mRoot.getWindowVisibleDisplayFrame(r);
            int screenH = realScreenHeight(mActivity);
            if (screenH <= 0) return 0;
            int kb = screenH - r.bottom - mBottomInset;
            if (kb < 0) kb = 0;
            // 小于阈值的差值当作系统栏/测量抖动，不算键盘
            if (kb < UiKit.dpI(mActivity, KEYBOARD_MIN_DP)) return 0;
            return kb;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * 按键盘高度调整半屏高度
     */
    private void hideKeyboard() {
        try {
            if (mRoot == null) return;
            View focused = mActivity != null ? mActivity.getCurrentFocus() : null;
            if (focused != null && focused instanceof android.widget.EditText) {
                focused.clearFocus();
            }
            Object imm = mActivity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm instanceof android.view.inputmethod.InputMethodManager) {
                ((android.view.inputmethod.InputMethodManager) imm)
                        .hideSoftInputFromWindow(mRoot.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private void applyKeyboardHeight(int kb) {
        if (!mHalfShowing) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        if (lp == null) return;
        int target;
        if (kb > 0) {
            int usableRoot = mRoot.getHeight() - mTopInset - mBottomInset;
            if (usableRoot <= 0) usableRoot = usableHeight();
            target = Math.min(usableRoot, maxSheetHeight());
            int min = UiKit.dpI(mActivity, 200);
            if (target < min) target = min;
        } else {
            target = mHeightBeforeKeyboard > 0 ? mHeightBeforeKeyboard : halfHeight();
            mHeightBeforeKeyboard = 0;
        }
        if (lp.height == target) return;
        // 高度动画正跑着时先停掉
        cancelHeightAnim();
        lp.height = target;
        lp.gravity = Gravity.BOTTOM;
        mHost.setLayoutParams(lp);
        if (kb > 0) {
            final View focus = mActivity.getCurrentFocus();
            if (focus != null) {
                mHost.post(new Runnable() {
                    public void run() {
                        try {
                            android.graphics.Rect r = new android.graphics.Rect(
                            0, 0, focus.getWidth(), focus.getHeight());
                            focus.requestRectangleOnScreen(r, false);
                        } catch (Throwable ignored) {
                        }
                    }
                });
            }
        }
    }

    /** 底栏：与标题栏同一套表面色 + 顶部细分隔线，三个等宽文字按钮。 */
    private FrameLayout buildBottomBar(final Activity a) {
        FrameLayout wrap = new FrameLayout(a);
        fillBottomBar(wrap, a);
        return wrap;
    }

    /** 底栏内容 */
    private void fillBottomBar(FrameLayout wrap, final Activity a) {
        wrap.removeAllViews();
        wrap.setBackgroundColor(UiKit.topBarBg(a));
        View line = new View(a);
        line.setBackgroundColor(UiKit.divider(a));
        FrameLayout.LayoutParams lineP = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dpI(a, 1)));
        lineP.gravity = Gravity.TOP;
        wrap.addView(line, lineP);
        LinearLayout bar = UiKit.hbox(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams barP = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        barP.topMargin = Math.max(1, UiKit.dpI(a, 1));
        wrap.addView(bar, barP);
        bar.addView(bottomBarButton(a, "←", new View.OnClickListener() {
            public void onClick(View v) {
                // 还有上一页就退一页；已经在首页就当作退出界面
                if (mStack.size() > 1) {
                    pop();
                } else {
                    close();
                }
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(bottomBarButton(a, "⌂", new View.OnClickListener() {
            public void onClick(View v) {
                close();
            }
        }), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout navSettings = bottomBarButton(a, "≡", new View.OnClickListener() {
            public void onClick(View v) {
                Page top = topPage();
                if (top != null && "settings".equals(top.key())) {
                    toast("已经在设置页");
                    return;
                }
                // 栈中已有设置页（在下层）：弹回
                if (popTo("settings")) return;
                push(new UiPages.SettingsPage(sFloatingView));
            }
        });
        mNavIcon = (TextView) navSettings.getChildAt(0);
        mNavUnder = navSettings.getChildAt(1);
        bar.addView(navSettings, new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        refreshNavState();
    }

    /** 底栏按钮：纯图标，按下有轻微缩放与底色*/
    private LinearLayout bottomBarButton(Activity a, String icon, View.OnClickListener l) {
        LinearLayout col = UiKit.vbox(a);
        col.setGravity(Gravity.CENTER);
        col.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.accentSoft(a), 0));
        TextView ic = UiKit.text(a, icon, 15, UiKit.textMain(a));
        ic.setGravity(Gravity.CENTER);
        col.addView(ic);
        // 选中指示条：默认藏起，只有「设置」键进设置页时显示
        View under = new View(a);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(
        UiKit.dpI(a, 18), UiKit.dpI(a, 2));
        up.topMargin = UiKit.dpI(a, 3);
        under.setLayoutParams(up);
        under.setBackgroundColor(UiKit.accent(a));
        under.setVisibility(View.GONE);
        col.addView(under);
        col.setOnClickListener(l);
        UiComponents.pressZoom(col);
        return col;
    }

    /** 底栏选中态刷新：栈顶是设置页时「≡」图标主题色 + 亮起底部横条 */
    private void refreshNavState() {
        if (mNavIcon == null) return;
        boolean inSettings = false;
        Page top = topPage();
        if (top != null && "settings".equals(top.key())) inSettings = true;
        mNavIcon.setTextColor(inSettings ? UiKit.accent(mActivity) : UiKit.textMain(mActivity));
        mNavUnder.setVisibility(inSettings ? View.VISIBLE : View.GONE);
    }

    // ============================================================
    //  挂载 / 卸载
    // ============================================================
    private boolean attach(Page home) {
        ViewGroup content;
        try {
            content = (ViewGroup) mActivity.findViewById(android.R.id.content);
        } catch (Throwable t) {
            return false;
        }
        if (content == null) return false;
        mTopInset = isImmersive(mActivity) ? statusBarHeight(mActivity) : 0;
        mBottomInset = isImmersive(mActivity) ? navBarHeight(mActivity) : 0;
        applyRootInsets();
        View homeView;
        try {
            homeView = home.build(mActivity);
        } catch (Throwable t) {
            return false;
        }
        if (homeView == null) return false;
        mCache.put(home.key(), homeView);
        try {
            content.addView(mRoot, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable t) {
            mCache.remove(home.key());
            return false;
        }
        pushInternal(home);
        restoreLastPage();
        mRoot.post(new Runnable() {
            public void run() {
                if (!syncInsetsWithWindow()) {
                    // 首帧还没量出尺寸时再补一次，避免个别宿主布局偏慢导致漏补
                    mRoot.postDelayed(new Runnable() {
                        public void run() {
                            if (syncInsetsWithWindow()) refreshTopContent();
                        }
                    }, INSET_RECHECK_MS);
                }
            }
        });
        return !mStack.isEmpty();
    }

    private void applyRootInsets() {
        try {
            mRoot.setPadding(0, mTopInset, 0, mBottomInset);
        } catch (Throwable ignored) {
        }
        // 顶部色条：高度 = 状态栏高度，用负 topMargin 顶进 root 的 padding 区
        try {
            if (mTopStrip != null) {
                ViewGroup.LayoutParams lp = mTopStrip.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) lp;
                    fp.height = Math.max(0, mTopInset);
                    fp.topMargin = -mTopInset;
                    fp.gravity = Gravity.TOP;
                    mTopStrip.setLayoutParams(fp);
                }
                mTopStrip.setVisibility(mTopInset > 0 ? View.VISIBLE : View.GONE);
            }
            if (mBottomStrip != null) {
                ViewGroup.LayoutParams lp = mBottomStrip.getLayoutParams();
                if (lp instanceof FrameLayout.LayoutParams) {
                    FrameLayout.LayoutParams fp = (FrameLayout.LayoutParams) lp;
                    fp.height = Math.max(0, mBottomInset);
                    fp.bottomMargin = -mBottomInset;
                    fp.gravity = Gravity.BOTTOM;
                    mBottomStrip.setLayoutParams(fp);
                }
                mBottomStrip.setVisibility(mBottomInset > 0 ? View.VISIBLE : View.GONE);
            }
            refreshTopStripColor(mHalfShowing);
            refreshBottomStripColor(mHalfShowing);
        } catch (Throwable ignored) {
        }
    }

    /** 底部系统栏配色*/
    private void refreshBottomStripColor(boolean half) {
        if (mBottomStrip == null) return;
        try {
            mBottomStrip.setBackgroundColor(half ? UiKit.bg(mActivity) : UiKit.topBarBg(mActivity));
        } catch (Throwable ignored) {
        }
    }

    /**
     * 用 content 的实际屏幕位置实测系统栏占位： - 顶部：content 从 y=0 开始说明内容顶到了状态栏，需要补状态栏高度； - 底部：content同样
     */
    private boolean syncInsetsWithWindow() {
        try {
            ViewGroup content = (ViewGroup) mActivity.findViewById(android.R.id.content);
            if (content == null || content.getHeight() <= 0) return false;
            int[] loc = new int[2];
            content.getLocationOnScreen(loc);
            int contentTop = loc[1];
            int contentBottom = loc[1] + content.getHeight();

            int top = contentTop <= 0 ? statusBarHeight(mActivity) : 0;
            int bottom = 0;
            int realH = realScreenHeight(mActivity);
            if (realH > 0 && contentBottom >= realH - 1) {
                bottom = navBarHeight(mActivity);
            }
            if (top != mTopInset || bottom != mBottomInset) {
                mTopInset = top;
                mBottomInset = bottom;
                applyRootInsets();
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 真实屏幕高度（含导航栏），用于判断内容是否铺到屏幕底部 */
    private static int realScreenHeight(Activity a) {
        try {
            android.graphics.Point p = new android.graphics.Point();
            a.getWindowManager().getDefaultDisplay().getRealSize(p);
            return p.y;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 导航栏高度：取不到时按 48dp 兜底 */
    private static int navBarHeight(Activity a) {
        try {
            int id = a.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) return a.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return UiKit.dpI(a, 48);
    }

    public void close() {
        if (sInstance != this) {
            return;
        }
        try {
            mRoot.clearAnimation();
        } catch (Throwable ignored) {
        }
        cancelHeightAnim();
        Page last = topPage();
        if (last != null && !last.halfScreen()) sLastKey = last.key();
        java.util.List<Page> leaving = new java.util.ArrayList<Page>(mStack);
        mStack.clear();
        for (int i = 0; i < leaving.size(); i++) {
            try {
                leaving.get(i).onHide();
            } catch (Throwable ignored) {
            }
        }
        mCache.clear();
        mAnimating = false;
        mHalfShowing = false;
        removeKeyboardListener();
        try {
            UiComponents.flushRestartHint();
        } catch (Throwable ignored) {
        }
        if (sInstance == this) sInstance = null;
        sFloatingView = null;
        try {
            UiKit.setFallbackMode(false);
        } catch (Throwable ignored) {
        }
        try {
            mRoot.animate().cancel();
            mRoot.setAlpha(1f);
            mRoot.animate().alpha(0f).translationY(UiKit.dp(mActivity, 16))
                    .setDuration(CLOSE_FADE_MS).start();
        } catch (Throwable ignored) {
        }
        final FrameLayout root = mRoot;
        final android.os.Handler closeHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        closeHandler.postDelayed(new Runnable() {
            public void run() {
                try {
                    mHost.removeAllViews();
                    mDim.setVisibility(View.GONE);
                    if (mTopStrip != null) mTopStrip.setVisibility(View.GONE);
                    if (mBottomStrip != null) mBottomStrip.setVisibility(View.GONE);
                    mRoot.setBackgroundColor(0x00000000);
                    mRoot.setAlpha(0f);
                } catch (Throwable ignored) {
                }
            }
        }, CLOSE_CLEAR_MS);
        closeHandler.postDelayed(new Runnable() {
            public void run() {
                try {
                    ViewGroup parent = (ViewGroup) root.getParent();
                    if (parent != null) parent.removeView(root);
                } catch (Throwable ignored) {
                }
            }
        }, CLOSE_REMOVE_MS);
        if (mFinishOnClose) {
            closeHandler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        if (sInstance != null) return;
                        if (mActivity != null && !mActivity.isFinishing()) {
                            mActivity.finish();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }, CLOSE_FINISH_MS);
        }
    }

    /** 宿主是否把内容顶到状态栏下面（沉浸式/全屏）；这类宿主需要补顶部 padding */
    private static boolean isImmersive(Activity a) {
        try {
            android.view.Window w = a.getWindow();
            if (w == null) return false;
            if ((w.getAttributes().flags
                            & android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) {
                return true;
            }
            View decor = w.getDecorView();
            if (decor == null) return false;
            int vis = decor.getSystemUiVisibility();
            return (vis & View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN) != 0
                    || (vis & View.SYSTEM_UI_FLAG_FULLSCREEN) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static int statusBarHeight(Activity a) {
        try {
            int id = a.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return a.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return UiKit.dpI(a, 24);
    }

    /** 顶栏需要额外加的顶部 padding（全屏宿主 statusBar 高度，否则 0） */
    public int topInset() {
        return mTopInset;
    }

    public Activity getActivity() {
        return mActivity;
    }

    // ============================================================
    //  栈操作
    // ============================================================
    /** replaceTop 期间为 true：onHide 据此区分「被同页替换」与「真的离开」 */
    private boolean mReplacing;

    /** replaceTop 期间为 true：重建不播换页转场动画 */
    private boolean mNoAnim;

    /** 页面是否仍在栈中：onHide 时区分「被新页覆盖」与「真的离开」 */
    public boolean contains(Page p) {
        return mStack.indexOf(p) >= 0;
    }

    /** onHide 时调用：true 表示正在被 replaceTop 同页重建，不是真的离开 */
    public boolean isReplacing() {
        return mReplacing;
    }

    public Page topPage() {
        return mStack.isEmpty() ? null : mStack.get(mStack.size() - 1);
    }

    /** 栈中第 i 层页面（0=根页）；越界返 null。 */
    public Page pageAt(int i) {
        return (i < 0 || i >= mStack.size()) ? null : mStack.get(i);
    }

    public int depth() {
        return mStack.size();
    }

    /** 栈顶页面已挂载的 View */
    public View topView() {
        Page p = topPage();
        return p == null ? null : p.view();
    }

    public void push(Page page) {
        if (page == null) return;
        if (!UiGuard.tap("push", PUSH_GAP_MS)) return;

        Page top = topPage();
        if (top != null && top.key().equals(page.key())) {
            refreshTop();
            return;
        }
        if (mStack.size() >= MAX_DEPTH) {
            toast("页面层数已达上限");
            return;
        }
        pushInternal(page);
    }

    /** 某个页面构建失败时的替代视图 */
    private View fallbackViewFor(final Page page) {
        String key = page == null ? "" : page.key();
        String title = page == null ? "" : page.title();
        final Runnable action = UiTabs.fallbackAction(mActivity, sFloatingView, key);
        if (action == null) {
            return UiComponents.fallbackView(mActivity, title,
                    "此页面暂时无法显示，可返回上一页继续使用其他功能。", null, null);
        }
        return UiComponents.fallbackView(mActivity, title,
                "此页面暂时无法显示，可以回退旧样式修改同样的设置。",
                "用旧样式打开", new View.OnClickListener() {
                    public void onClick(View v) {
                        try {
                            action.run();
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    /** 记下「用户此刻停在哪一页」：半屏浮层不覆盖，保留它下面那一页 */
    private void syncLastKey() {
        Page top = topPage();
        if (top == null) return;
        if (top.halfScreen()) return;
        sLastKey = top.key();
    }

    /** 打开界面时直接回到上次停留的页面*/
    private void restoreLastPage() {
        String key = sLastKey;
        if (key == null || key.length() == 0 || "home".equals(key)) return;
        Page p = null;
        try {
            p = UiPages.recreatePage(key);
        } catch (Throwable ignored) {
        }
        if (p == null) return;
        try {
            pushInternal(p);
        } catch (Throwable ignored) {
        }
    }

    private void pushInternal(Page page) {
        hideKeyboard();
        View v = obtainView(page);
        if (v == null) return;

        Page prev = topPage();
        if (prev != null) {
            try {
                prev.onHide();
            } catch (Throwable ignored) {
            }
        }
        mStack.add(page);
        page.attachView(v);
        showPageView(v, page.halfScreen());
        reflowHost(page.halfScreen());
        syncLastKey();

        try {
            page.onShow();
        } catch (Throwable ignored) {
        }
        requestRootFocus();
    }

    public void pop() {
        if (mStack.size() <= 1) {
            close();
            return;
        }
        if (!UiGuard.tap("push", PUSH_GAP_MS)) return;
        hideKeyboard();
        mPopAnim = true;
        Page leaving = mStack.remove(mStack.size() - 1);
        try {
            leaving.onHide();
        } catch (Throwable ignored) {
        }
        Page now = topPage();
        if (now == null) {
            close();
            return;
        }
        View v = obtainView(now);
        if (v == null) {
            v = new View(mActivity);
            v.setBackgroundColor(UiKit.bg(mActivity));
        }
        boolean half = now.halfScreen();
        showPageView(v, half);
        reflowHost(half);
        syncLastKey();
        try {
            now.onShow();
        } catch (Throwable ignored) {
        }
        requestRootFocus();
    }

    /** 用新页面替换栈顶 */
    public void replaceTop(Page page) {
        if (mStack.isEmpty() || page == null) {
            if (page != null) pushInternal(page);
            return;
        }
        Page leaving = mStack.remove(mStack.size() - 1);
        mReplacing = true;
        try {
            leaving.onHide();
        } catch (Throwable ignored) {
        }
        mReplacing = false;
        mCache.remove(leaving.key());
        mNoAnim = true;
        pushInternal(page);
        mNoAnim = false;
    }

    public void popToRoot() {
        hideKeyboard();
        if (mStack.size() <= 1) {
            mFirstEnter = false;
            refreshTop();
            return;
        }
        while (mStack.size() > 1) {
            Page p = mStack.remove(mStack.size() - 1);
            try {
                p.onHide();
            } catch (Throwable ignored) {
            }
        }
        Page root = topPage();
        if (root == null) {
            close();
            return;
        }
        View v = obtainView(root);
        if (v != null) showPageView(v, root.halfScreen());
        reflowHost(root.halfScreen());
        refreshTop();
    }

    /** 弹回到栈中指定 key 的页面（把它上面的页全部出栈）；栈中没有则返回 false 底部导航/搜索结果跳转重入时用（问题 4）：避免同页越堆越多 */
    public boolean popTo(String key) {
        if (key == null || mStack.isEmpty()) return false;
        int idx = -1;
        for (int i = 0; i < mStack.size(); i++) {
            if (key.equals(mStack.get(i).key())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) return false;
        if (idx == mStack.size() - 1) {
            refreshTop();
            return true;
        }
        hideKeyboard();
        mPopAnim = true;
        while (mStack.size() > idx + 1) {
            Page leaving = mStack.remove(mStack.size() - 1);
            try {
                leaving.onHide();
            } catch (Throwable ignored) {
            }
        }
        Page target = topPage();
        if (target == null) return false;
        View v = obtainView(target);
        if (v == null) {
            v = new View(mActivity);
            v.setBackgroundColor(UiKit.bg(mActivity));
        }
        boolean half = target.halfScreen();
        showPageView(v, half);
        reflowHost(half);
        syncLastKey();
        try {
            target.onShow();
        } catch (Throwable ignored) {
        }
        requestRootFocus();
        return true;
    }

    /** 刷新当前页数据（onShow 重读源数据） */
    public void refreshTop() {
        Page p = topPage();
        if (p == null) return;
        try {
            p.onShow();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 只失效「栈顶之下」的页面缓存，栈顶页面保持不动。 
     */
    public void invalidateBelow() {
        Page top = topPage();
        if (top == null) return;
        for (int i = 0; i < mStack.size(); i++) {
            Page p = mStack.get(i);
            if (p == top) continue;
            try {
                p.onHide();
            } catch (Throwable ignored) {
            }
            p.attachView(null);
            mCache.remove(p.key());
        }
    }

    /** 让指定 key 的页面缓存失效*/
    private static int sDataVersion = 0;

    public static void bumpDataVersion() {
        sDataVersion++;
    }

    public void invalidatePage(String key) {
        if (key == null) return;
        mCache.remove(key);
    }

    /** 局部刷新：页面自己拿到 View 后重建内容 */
    public void rebuildAll() {
        mCache.clear();
        if (mStack.isEmpty()) {
            close();
            return;
        }
        for (int i = 0; i < mStack.size(); i++) {
            mStack.get(i).attachView(null);
        }
        try {
            fillBottomBar(mBottomBar, mActivity);
        } catch (Throwable ignored) {
        }
        applyRootInsets();
        Page top = topPage();
        if (top == null) return;
        View v = obtainView(top);
        if (v == null) {
            close();
            return;
        }
        if (mHalfShowing && mStack.size() >= 2) {
            Page back = mStack.get(mStack.size() - 2);
            View bv = obtainView(back);
            if (bv != null) {
                mBackdropHost.removeAllViews();
                mBackdropHost.addView(bv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
                mBackdropHost.setBackgroundColor(0x00000000);
            }
        }
        showPageView(v, top.halfScreen());
        reflowHost(top.halfScreen());
        refreshTop();
    }

    /**
     * 只重建背景层（「打开半屏前的一页」），抽屉本身不动。 
     */
    public void refreshBackdrop() {
        if (!mHalfShowing || mStack.size() < 2) return;
        Page back = mStack.get(mStack.size() - 2);
        if (back == null) return;
        back.attachView(null);
        mCache.remove(back.key());
        View bv = obtainView(back);
        if (bv == null) return;
        mBackdropHost.removeAllViews();
        mBackdropHost.addView(bv, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
        mBackdropHost.setBackgroundColor(0x00000000);
    }

    /** 只重填底栏配色（不重建任何页面）*/
    public void refreshBottomBar() {
        try {
            fillBottomBar(mBottomBar, mActivity);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 标题栏刷新按钮统一入口
     */
    public void triggerTopRefresh() {
        if (mHost != null && mHost.getChildCount() > 0) {
            View top = mHost.getChildAt(0);
            if (top instanceof UiComponents.PullRefreshLayout) {
                ((UiComponents.PullRefreshLayout) top).startRefresh();
                return;
            }
        }
        refreshTopContent();
    }

    /**
     * 只重建栈顶这一页，不播转场动画、也不再回调 onShow
     */
    public void refreshTopContent() {
        Page top = topPage();
        if (top == null) return;
        top.attachView(null);
        mCache.remove(top.key());
        View v = obtainView(top);
        if (v == null) return;
        boolean half = top.halfScreen();
        mHost.removeAllViews();
        if (!half) {
            mBackdropHost.removeAllViews();
            mBackdropHost.setBackgroundColor(UiKit.bg(mActivity));
        }
        mHost.addView(v, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        mHalfShowing = half;
    }

    // ============================================================
    //  视图装配
    // ============================================================
    private View obtainView(Page page) {
        View cached = mCache.get(page.key());
        if (cached != null) {
            boolean stale = page.halfScreen() || page.builtVersion != sDataVersion;
            if (stale) {
                mCache.remove(page.key());
                cached = null;
            }
        }
        if (cached != null) {
            page.attachView(cached);
            return cached;
        }
        View v;
        try {
            v = page.build(mActivity);
        } catch (Throwable t) {
            v = null;
        }
        if (v == null) {
            v = fallbackViewFor(page);
        }
        if (v == null) return null;
        // 全屏页统一包一层下拉刷新
        if (!page.halfScreen() && !page.hasOwnRefresh()
                && !(v instanceof UiComponents.PullRefreshLayout)) {
            UiComponents.PullRefreshLayout wrap = new UiComponents.PullRefreshLayout(mActivity);
            wrap.addView(v, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
            final UiComponents.PullRefreshLayout w = wrap;
            wrap.setOnPullRefresh(new UiComponents.OnPullRefresh() {
                public void onRefresh() {
                    mRoot.postDelayed(new Runnable() {
                        public void run() {
                            // 先让刷新区域走完「成功刷新 → 收起」，收起完毕再重建
                            w.finishRefresh(new Runnable() {
                                public void run() {
                                    try {
                                        rebuildAll();
                                    } catch (Throwable ignored) {
                                    }
                                }
                            });
                        }
                    }, REFRESH_REBUILD_MS);
                }
            });
            v = wrap;
        }
        page.builtVersion = sDataVersion;
        page.attachView(v);
        mCache.put(page.key(), v);
        return v;
    }

    private void showPageView(View v, boolean half) {
        // 换页前先摘掉旧页的延迟移除任务
        if (mBackdropRemove != null) {
            try {
                mBackdropHost.removeCallbacks(mBackdropRemove);
            } catch (Throwable ignored) {
            }
            mBackdropRemove = null;
        }
        FrameLayout.LayoutParams full = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        boolean wasHalf = mHalfShowing;
        View keep = mHost.getChildCount() > 0 ? mHost.getChildAt(0) : null;
        if (half) {
            mHost.removeAllViews();
            if (!wasHalf) {
                // 从全屏打开半屏：keep 
                mBackdropHost.removeAllViews();
                if (keep != null && keep != v) {
                    mBackdropHost.addView(keep, full);
                    mBackdropHost.setBackgroundColor(0x00000000);
                } else {
                    mBackdropHost.setBackgroundColor(UiKit.bg(mActivity));
                }
            } else if (mBackdropHost.getChildCount() == 0) {
                mBackdropHost.setBackgroundColor(UiKit.bg(mActivity));
            }
        } else if (wasHalf) {
            mHost.removeAllViews();
            mBackdropHost.removeAllViews();
            View bg = backdropFor(topPage());
            if (bg != null) {
                mBackdropHost.addView(bg, full);
                mBackdropHost.setBackgroundColor(0x00000000);
            } else {
                mBackdropHost.setBackgroundColor(UiKit.bg(mActivity));
            }
        } else if (keep != null && keep != v) {
            mHost.removeAllViews();
            mBackdropHost.removeAllViews();
            mBackdropHost.addView(keep, full);
            mBackdropHost.setBackgroundColor(0x00000000);
            final View keepRef = keep;
            mBackdropRemove = new Runnable() {
                public void run() {
                    try {
                        mBackdropHost.removeView(keepRef);
                    } catch (Throwable ignored) {
                    }
                    mBackdropRemove = null;
                }
            };
            mBackdropHost.postDelayed(mBackdropRemove, TRANSITION_MS + KEEP_REMOVE_EXTRA_MS);
        } else {
            mHost.removeAllViews();
            mBackdropHost.setBackgroundColor(UiKit.bg(mActivity));
        }
        mHost.addView(v, full);
        mHalfShowing = half;

        if (half) {
            mHost.setBackground(UiKit.roundTop(mActivity, UiKit.bg(mActivity), UiKit.SHEET_R));
            if (Build.VERSION.SDK_INT >= 21) mHost.setElevation(UiKit.dp(mActivity, 16));
        } else {
            mHost.setBackgroundColor(UiKit.bg(mActivity));
            if (Build.VERSION.SDK_INT >= 21) mHost.setElevation(0f);
        }
        if (!mNoAnim) applyTransitions(v, half);
        refreshNavState();
    }

    /** 半屏页在栈中的前驱的视图 */
    private View backdropFor(Page top) {
        if (top == null) return null;
        int idx = mStack.indexOf(top);
        if (idx <= 0) return null;
        View v = mCache.get(mStack.get(idx - 1).key());
        return (v != null && v.getParent() == null) ? v : null;
    }

    /** 高度/位置：全屏铺满；半屏贴底 62% */
    private void reflowHost(boolean half) {
        cancelHeightAnim();
        mHost.setTranslationY(0f);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        if (half) {
            lp.height = halfHeight();
            lp.gravity = Gravity.BOTTOM;
        } else {
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.gravity = Gravity.TOP;
        }
        mHost.setLayoutParams(lp);
        mHost.setTranslationY(0f);
        mHost.setTranslationX(0f);
        if (mBottomBar != null) {
            mBottomBar.setVisibility(half ? View.GONE : View.VISIBLE);
            mBottomBar.setAlpha(1f);
        }
        // 全屏页给底栏让出高度，内容不会被压在底栏下面
        mHost.setPadding(0, 0, 0, half ? 0 : mBottomBarH);
        // 半屏时根部必须透明
        mRoot.setBackgroundColor(half ? 0x00000000 : UiKit.bg(mActivity));
        mDim.setVisibility(half ? View.VISIBLE : View.GONE);
        if (half) {
            mDim.setAlpha(1f);
            mDim.setBackgroundColor(UiKit.mask(mActivity));
        }
        // 顶部色条：半屏时会被遮罩压暗，颜色要跟着算
        refreshTopStripColor(half);
        // 底部系统栏：全屏跟底栏同色，半屏跟抽屉同色
        refreshBottomStripColor(half);
        // 换页可能发生在输入法还开着的时候，高度要按当前键盘状态重算
        applyKeyboardHeight(mKeyboardHeight);
    }

    /** 顶部色条配色：dim=true 时按遮罩浓度压暗，和半屏遮罩下的顶栏一致 */
    private void refreshTopStripColor(boolean dim) {
        if (mTopStrip == null) return;
        try {
            int base = UiKit.topBarBg(mActivity);
            if (dim) {
                int a = (UiKit.mask(mActivity) >>> 24) & 0xFF;
                base = UiKit.blend(base, 0xFF000000, a / 255f);
            }
            mTopStrip.setBackgroundColor(base);
        } catch (Throwable ignored) {
        }
    }

    private void applyTransitions(View v, boolean half) {
        if (mStack.size() <= 1 && !half && mFirstEnter) {
            mFirstEnter = false;
            mRoot.setAlpha(0f);
            mRoot.animate().alpha(1f).setDuration(TRANSITION_MS)
                    .setInterpolator(new AccelerateDecelerateInterpolator()).start();
            return;
        }
        // 用 ViewPropertyAnimator 真实位移代替 TranslateAnimation
        float start = half ? UiKit.screenH(mActivity) : UiKit.screenW(mActivity) * 0.22f;
        if (half) {
            v.setTranslationX(0f);
            v.setTranslationY(start);
        } else {
            v.setTranslationX(mPopAnim ? -start * POP_SLIDE_FACTOR : start);
            v.setTranslationY(0f);
        }
        mPopAnim = false;
        mAnimating = true;
        v.animate().translationX(0f).translationY(0f)
                .setDuration(TRANSITION_MS)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    public void run() {
                        mAnimating = false;
                    }
                })
                .start();
    }

    private void requestRootFocus() {
        try {
            mRoot.requestFocus();
        } catch (Throwable ignored) {
        }
    }

    private void toast(String msg) {
        try {
            com.install.appinstall.xl.util.ToastUtil.showUnique(mActivity, msg);
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  根容器：返回键 + 侧滑返回
    // ============================================================
    private class RootLayout extends FrameLayout {

        private float mDownX;
        private float mDownY;
        private boolean mEdgeCandidate;
        private boolean mSwiping;

        RootLayout(Activity a) {
            super(a);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (mAnimating) return true;
                    Page top = topPage();
                    if (top != null && top.halfScreen()) {
                        if (top.sheetCancelable()) pop();
                    } else if (mStack.size() > 1) {
                        pop();
                    } else if (mFinishOnClose && mActivity != null) {
                        mActivity.onBackPressed();
                    } else {
                        close();
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) return true;
            }
            return super.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            if (mAnimating && ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (ev != null && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (!hasFocus()) {
                    try {
                        requestFocus();
                    } catch (Throwable ignored) {
                    }
                }
            }
            return super.dispatchTouchEvent(ev);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // 半屏不收侧滑
            if (mHalfShowing || mStack.size() <= 1) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = ev.getX();
                    mDownY = ev.getY();
                    mEdgeCandidate = mDownX <= UiKit.dpI(mActivity, UiKit.EDGE_SWIPE_W);
                    mSwiping = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (!mEdgeCandidate) return false;
                    float dx = ev.getX() - mDownX;
                    float dy = ev.getY() - mDownY;
                    if (Math.abs(dx) > Math.abs(dy) * SWIPE_SLOPE
                            && dx > UiKit.dpI(mActivity, SWIPE_START_DP)) {
                        mSwiping = true;
                        return true;
                    }
                    return false;
                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (!mSwiping) return super.onTouchEvent(ev);
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    float dx = ev.getX() - mDownX;
                    if (dx < 0) dx = 0;
                    mHost.setTranslationX(dx);
                    mDim.setVisibility(View.VISIBLE);
                    mDim.setAlpha(Math.min(SWIPE_CANCEL_DIM_MAX,
                            dx / Math.max(1f, UiKit.screenW(mActivity))));
                    mDim.setBackgroundColor(UiKit.mask(mActivity));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float moved = ev.getX() - mDownX;
                    boolean finish = moved > UiKit.screenW(mActivity) * SWIPE_FINISH_RATIO;
                    mDim.setAlpha(1f);
                    if (finish) {
                        mSwiping = false;
                        mHost.setTranslationX(0f);
                        mDim.setVisibility(mHalfShowing ? View.VISIBLE : View.GONE);
                        mPopAnim = true;
                        pop();
                    } else {
                        mHost.animate().translationX(0f).setDuration(CLOSE_FADE_MS).start();
                        mDim.setVisibility(View.GONE);
                        mSwiping = false;
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    // ============================================================
    //  半屏高度与展开状态
    // ============================================================
    /** 可用高度：扣除已补的系统栏，半屏/展开高度都基于它，避免底部被导航栏切掉 */
    private int usableHeight() {
        int h = UiKit.screenH(mActivity) - mTopInset - mBottomInset;
        return h > 0 ? h : UiKit.screenH(mActivity);
    }

    private int halfHeight() {
        int sw = UiKit.screenW(mActivity);
        int sh = usableHeight();
        int h;
        if (sw > sh) {
            h = Math.min((int) (sh * 0.78f), UiKit.dpI(mActivity, 360));
        } else {
            h = Math.min((int) (sh * HALF_RATIO), UiKit.dpI(mActivity, 520));
        }
        return Math.max(h, UiKit.dpI(mActivity, 240));
    }

    /** 半屏满高：留出顶部一小段间隙，不做真正的全屏 */
    private int maxSheetHeight() {
        return usableHeight() - UiKit.dpI(mActivity, 56);
    }

    // ============================================================
    //  半屏高度：自由停靠 + 内容驱动高度
    // ============================================================

    /** 半屏 ⇄ 展开切换 */
    public void setSheetExpanded(boolean expanded) {
        mHost.setTranslationY(0f);
        animateSheetHeight(expanded ? maxSheetHeight() : halfHeight());
    }

    /**
     * 半屏里的内容滚动时，半屏跟着长高：内容越多半屏越高
     */
    public void growSheetToContent(android.widget.ScrollView sv) {
        if (mAnimating || !mHalfShowing || sv == null) return;
        View child = sv.getChildCount() > 0 ? sv.getChildAt(0) : null;
        if (child == null || child.getHeight() <= 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        if (lp == null || lp.height <= 0) return;
        int fixed = mHost.getHeight() - sv.getHeight();
        if (fixed <= 0) fixed = lp.height - sv.getHeight();
        if (fixed <= 0) return;
        int half = halfHeight();
        int max = maxSheetHeight();
        // 内容实际需要多少就封顶多少，但不超过最高值
        int cap = Math.min(max, fixed + child.getHeight());
        int target = half + Math.max(0, sv.getScrollY());
        if (target > cap) target = cap;
        if (target < half) target = half;
        if (target > lp.height) {
            // 只升不降：内容顶上来才长高，往回滚不回落
            applySheetHeight(target);
        }
    }

    /** 高度动画 */
    private void animateSheetHeight(final int target) {
        if (mHost == null) return;
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        if (lp == null) return;
        int start = lp.height;
        final float startTy = mHost.getTranslationY();
        cancelHeightAnim();
        if (start == target && Math.abs(startTy) < 0.5f) return;
        // 升格/回落动画期间冻结交互
        mAnimating = true;
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(start, target);
        va.setDuration(CLOSE_CLEAR_MS);
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator anim) {
                Object v = anim.getAnimatedValue();
                if (!(v instanceof Integer)) return;
                lp.height = ((Integer) v).intValue();
                lp.gravity = Gravity.BOTTOM;
                mHost.setLayoutParams(lp);
                if (Math.abs(startTy) >= 0.5f) {
                    mHost.setTranslationY(startTy * (1f - anim.getAnimatedFraction()));
                }
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            public void onAnimationEnd(android.animation.Animator animation) {
                if (mHeightAnim == animation) mHeightAnim = null;
                mAnimating = false;
            }
        });
        mHeightAnim = va;
        va.start();
    }

    /** 停掉高度动画 */
    private void cancelHeightAnim() {
        android.animation.ValueAnimator va = mHeightAnim;
        mHeightAnim = null;
        if (va != null) {
            try {
                va.cancel();
            } catch (Throwable ignored) {
            }
            mAnimating = false;
        }
    }

    /** 应用半屏高度（gravity 始终 BOTTOM，调用方已算好目标值） */
    private void applySheetHeight(int h) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        if (lp == null || lp.height == h) return;
        lp.height = h;
        lp.gravity = Gravity.BOTTOM;
        mHost.setLayoutParams(lp);
    }

    /** 当前半屏布局高度：优先取 lp.height，动画/拖动中它与 getHeight() 不一致 */
    private int currentSheetHeight() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mHost.getLayoutParams();
        return (lp != null && lp.height > 0) ? lp.height : mHost.getHeight();
    }

    /**
     * 半屏拖拽
     */
    public void bindSheetDrag(View handle) {
        if (handle == null) return;
        final int slop = UiKit.dpI(mActivity, SHEET_DRAG_SLOP_DP);
        final float[] downY = new float[1];
        final boolean[] dragging = new boolean[1];
        final int[] startH = new int[1];
        handle.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                if (!mHalfShowing) return false;
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY[0] = e.getRawY();
                        startH[0] = currentSheetHeight();
                        mHost.setTranslationY(0f);
                        dragging[0] = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        {
                            float dy = e.getRawY() - downY[0];
                            if (!dragging[0] && Math.abs(dy) > slop) dragging[0] = true;
                            if (!dragging[0]) return false;
                            int half = halfHeight();
                            int nh = (int) (startH[0] - dy);
                            if (nh >= half) {
                                // 半屏与顶部之间：改高度，跟手
                                applySheetHeight(Math.min(nh, maxSheetHeight()));
                                mHost.setTranslationY(0f);
                            } else {
                                // 低于半屏：高度锁在半屏，整块跟着手指往下沉。
                                applySheetHeight(half);
                                mHost.setTranslationY(Math.max(0, half - nh));
                            }
                            return true;
                        }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        {
                            int half = halfHeight();
                            int max = maxSheetHeight();
                            boolean handled = false;
                            if (dragging[0]) {
                                // 自由停靠
                                if (mHost.getTranslationY() > half * 0.5f) {
                                    cancelHeightAnim();
                                    mHost.setTranslationY(0f);
                                    pop();
                                } else {
                                    // 停在手指落点：拖到多高就是多高
                                    int target = startH[0] - (int) (e.getRawY() - downY[0]);
                                    if (target > max) target = max;
                                    if (target < half) target = half;
                                    animateSheetHeight(target);
                                }
                                handled = true;
                            }
                            if (!handled) {
                                mHost.animate().translationY(0f).setDuration(160).start();
                            }
                            dragging[0] = false;
                            return true;
                        }
                    default:
                        return false;
                }
            }
        });
    }
}

package com.install.appinstall.xl.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import java.lang.ref.WeakReference;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.DebugModeManager;

//模块悬浮窗文件

public class foat {
    private final HookInit hookInit;
    private TextView currentFloatingView;
    private Handler floatingTopHandler;
    private static long lastVolumeDownTime = 0;
    private static long lastVolumeUpTime = 0;
    private static final int DOUBLE_CLICK_TIMEOUT = 600;
    private static final int DEBOUNCE_TIMEOUT = 150;
    private static final String FLOATING_TAG = "install_fake_floating";
    private long lastVolumeClickTime = 0;
    private int volumeClickCount = 0;
    private static final int TRIPLE_CLICK_TIMEOUT = 600;
    private static long lastKeyDownTime = 0;
    private ViewGroup mCurrentDecorView;
    private View mCurrentFloatingView;
    private boolean mAddViewHookInstalled = false;
    private long lastPermanentHiddenToastTime = 0;
    private static final int PERMANENT_HIDDEN_TOAST_INTERVAL = 2000;

    // ========== 自动收边相关 ==========
    private Handler autoHideHandler = new Handler(Looper.getMainLooper());
    private Runnable autoHideRunnable;
    private boolean isCollapsed = false;
    private boolean isAnimating = false;
    private boolean pendingTextStateRefresh = false;
    private float fullWidth, fullHeight;
    private float fullX, fullY;
    private float collapsedWidthPx;
    private float collapsedX, collapsedY;
    private boolean snapToRight = false;
    private boolean isDragging = false;
    private static final long AUTO_HIDE_DELAY = 8000; // 8秒无操作收边
    public foat(HookInit hookInit) {
        this.hookInit = hookInit;
    }

    // ========== 重置收边状态（供外部调用） ==========
    public void resetCollapsedState() {
        isAnimating = false;
        isCollapsed = false;
        if (currentFloatingView == null) return;
        try {
            ViewGroup.LayoutParams lp = currentFloatingView.getLayoutParams();
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            currentFloatingView.setLayoutParams(lp);
            currentFloatingView.setTextSize(14);
        } catch (Throwable ignored) {
        }
        try {
            applyFloatingTextState(currentFloatingView);
        } catch (Throwable ignored) {
        }
    }

    // ========== 音量键重置 ==========
    public void resetVolumeKeyForHide() {
        long now = System.currentTimeMillis();
        lastVolumeDownTime = now - 1000;
        lastVolumeUpTime = now - 1000;
        lastKeyDownTime = 0;
        volumeClickCount = 0;
        lastVolumeClickTime = 0;
        ReaLog.log("floating", "音量键时间戳/计数已重置, now=" + now);
    }

    // ========== 可见性/安装状态判定 ==========
    private boolean isFloatingVisibleForCurrentApp() {
        String targetApp = hookInit.getCurrentTargetApp();
        Boolean permanent = HookInit.permanentHiddenMap.get(targetApp);
        Boolean shouldShow = HookInit.floatingShownMap.get(targetApp);
        if (permanent != null && permanent) shouldShow = false;
        if (shouldShow == null) shouldShow = true;
        return shouldShow;
    }

    /** 当前目标应用的安装状态，缺省视为已安装。 */
    private boolean isInstalledForCurrentApp() {
        return isInstalled(hookInit.getCurrentTargetApp());
    }

    private boolean isInstalled(String targetApp) {
        Boolean currentStatus = HookInit.installStatusMap.get(targetApp);
        return currentStatus != null ? currentStatus : true;
    }

    // ========== 临时/永久隐藏 ==========
    public void tempHideFloatingView(final Activity activity, final View floatingView) {
        if (blockWhenDebugModeActive(activity, new Runnable() {
            @Override
            public void run() {
                performTempHide(activity, floatingView);
            }
        })) {
            return;
        }
        performTempHide(activity, floatingView);
    }

    public void hideFloatingView(final Activity activity, final View floatingView) {
        if (blockWhenDebugModeActive(activity, new Runnable() {
            @Override
            public void run() {
                performPermanentHide(activity, floatingView);
            }
        })) {
            return;
        }
        performPermanentHide(activity, floatingView);
    }

    /** 调试模式下禁止隐藏悬浮窗：弹出「调试模式冲突」确认框， */
    private boolean blockWhenDebugModeActive(final Activity activity, final Runnable hideAction) {
        if (!DebugModeManager.isDebugModeActive()) return false;
        AlertDialog dialog = DialogUi.createBoundedDialog(
                activity,
                "调试模式冲突",
                "当前处于调试模式，无法隐藏悬浮窗。<br><br>" +
                        "您可以选择关闭调试模式后再隐藏，或取消操作。",
                new String[]{"关闭调试模式并隐藏", "取消"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                DebugModeManager.disableDebugMode();
                                d.dismiss();
                                hideAction.run();
                                DialogUi.showRestartConfirmDialog(activity);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                            }
                        }
                }
        );
        dialog.show();
        return true;
    }

    private void performTempHide(Activity activity, View floatingView) {
        try {
            detachFloatingAndResetState(floatingView, false);
            resetVolumeKeyForHide();
            ReaLog.log("floating", "用户隐藏悬浮窗(临时)，双击音量键恢复");
            ToastUtil.showUnique(activity, "✅ 悬浮窗临时隐藏\n可双击音量键恢复");
        } catch (Throwable t) {
            ReaLog.log("floating", "临时隐藏悬浮窗异常: " + t.getMessage());
            ToastUtil.showUnique(activity, "临时隐藏失败");
        }
    }

    private void performPermanentHide(Activity activity, View floatingView) {
        try {
            detachFloatingAndResetState(floatingView, true);
            hookInit.saveConfigToFile();
            ReaLog.log("floating", "用户隐藏悬浮窗(永久)，三击音量键恢复");
            ToastUtil.showUnique(activity, "✅ 悬浮窗永久隐藏\n三击音量键恢复");
            resetVolumeKeyForHide();
        } catch (Throwable t) {
            ReaLog.log("floating", "永久隐藏悬浮窗异常: " + t.getMessage());
            ToastUtil.showUnique(activity, "永久隐藏失败");
        }
    }

    // 隐藏悬浮窗的前置流程：摘除视图 → 写入隐藏标记 → 清理引用 → 停定时置顶 → 复位收边状态
    private void detachFloatingAndResetState(View floatingView, boolean permanentHidden) {
        View target = floatingView;
        if (target == null || target.getParent() == null) {
            target = currentFloatingView;
        }
        if (target == null || target.getParent() == null) {
            try {
                Activity cur = hookInit.getCurrentResumedActivity();
                if (cur != null && !cur.isFinishing()) {
                    target = findFloatingViewIn(cur);
                }
            } catch (Throwable ignored) {
            }
        }
        if (target != null) {
            try {
                ViewGroup parent = (ViewGroup) target.getParent();
                if (parent != null) parent.removeView(target);
            } catch (Throwable ignored) {
            }
        }
        String targetApp = hookInit.getCurrentTargetApp();
        HookInit.floatingShownMap.put(targetApp, false);
        HookInit.permanentHiddenMap.put(targetApp, permanentHidden);
        currentFloatingView = null;
        mCurrentFloatingView = null;
        mCurrentDecorView = null;
        stop定时置顶();
        isCollapsed = false;
        isAnimating = false;
    }

    /**
     * 恢复悬浮窗显示（新 UI「恢复悬浮窗显示」入口）
     */
    public void restoreFloatingView(Activity activity) {
        if (activity == null) return;
        String targetApp = hookInit.getCurrentTargetApp();
        HookInit.floatingShownMap.put(targetApp, true);
        HookInit.permanentHiddenMap.put(targetApp, false);
        isCollapsed = false;
        isAnimating = false;
        try {
            hookInit.saveConfigToFile();
        } catch (Throwable ignored) {
        }
        resetVolumeKeyForHide();
        Activity act = activity;
        if (act.isFinishing()) {
            try {
                Activity cur = hookInit.getCurrentResumedActivity();
                if (cur != null && !cur.isFinishing()) act = cur;
            } catch (Throwable ignored) {
            }
        }
        if (act == null || act.isFinishing()) {
            ToastUtil.showUnique(activity, "恢复失败：当前没有可用的界面");
            return;
        }
        final Activity finalAct = act;
        try {
            ViewGroup decorView = decorViewOf(finalAct);
            View old = decorView.findViewWithTag(FLOATING_TAG);
            if (old != null) decorView.removeView(old);
        } catch (Throwable ignored) {
        }
        currentFloatingView = null;
        mCurrentFloatingView = null;
        mCurrentDecorView = null;
        finalAct.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showFloatingView(finalAct);
            }
        });
        ToastUtil.showUnique(activity, "悬浮窗已恢复显示");
        ReaLog.log("floating", "用户通过界面恢复悬浮窗显示");
    }

    /**
     * 按当前配置即时校正悬浮窗显隐同步视图。
     */
    public void refreshFloatingState(final Activity activity) {
        if (activity == null) return;
        Activity act = activity;
        if (act.isFinishing()) {
            try {
                Activity cur = hookInit.getCurrentResumedActivity();
                if (cur != null && !cur.isFinishing()) act = cur;
            } catch (Throwable ignored) {
            }
        }
        if (act == null || act.isFinishing()) return;
        final Activity finalAct = act;
        finalAct.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup decorView = decorViewOf(finalAct);
                    View old = decorView.findViewWithTag(FLOATING_TAG);
                    if (old != null) decorView.removeView(old);
                } catch (Throwable ignored) {
                }
                currentFloatingView = null;
                mCurrentFloatingView = null;
                mCurrentDecorView = null;
                if (isFloatingVisibleForCurrentApp()) {
                    showFloatingView(finalAct);
                }
            }
        });
    }

    // ========== 恢复永久隐藏 ==========
    private void restorePermanentHidden(Activity activity) {
        Activity targetActivity = activity;
        if (targetActivity == null || targetActivity.isFinishing()) {
            Activity current = hookInit.getCurrentResumedActivity();
            if (current != null && !current.isFinishing()) {
                targetActivity = current;
            } else {
                ReaLog.log("floating", "恢复永久隐藏失败：没有有效的 Activity");
                return;
            }
        }
        final Activity finalActivity = targetActivity;

        String targetApp = hookInit.getCurrentTargetApp();
        Boolean permanent = HookInit.permanentHiddenMap.get(targetApp);
        if (permanent != null && permanent) {
            HookInit.permanentHiddenMap.put(targetApp, false);
            HookInit.floatingShownMap.put(targetApp, true);
            hookInit.saveConfigToFile();

            finalActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showFloatingView(finalActivity);
                    com.install.appinstall.xl.ui.UiKit.haptic(finalActivity);
                }
            });

            ToastUtil.showUnique(finalActivity, "永久隐藏已恢复\n悬浮窗显示");
            ReaLog.log("floating", "三击音量键恢复永久隐藏");
            resetVolumeKeyForHide();
        } else {
            ToastUtil.showUnique(finalActivity, "当前未永久隐藏");
        }
    }

    private void showPermanentHiddenToast(Activity activity) {
        long now = System.currentTimeMillis();
        if (now - lastPermanentHiddenToastTime > PERMANENT_HIDDEN_TOAST_INTERVAL) {
            ToastUtil.showUnique(activity, "悬浮窗永久隐藏中\n三击音量键恢复");
            lastPermanentHiddenToastTime = now;
        }
    }

    // ========== 音量键双击/三击 ==========
    public void initVolumeKeyDoubleClick(ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(Activity.class, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Activity activity = (Activity) param.thisObject;
                KeyEvent event = (KeyEvent) param.args[0];
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    int keyCode = event.getKeyCode();
                    long currentTime = System.currentTimeMillis();

                    if (currentTime - lastKeyDownTime < DEBOUNCE_TIMEOUT) {
                        ReaLog.log("floating", "防抖忽略重复按键，diff=" + (currentTime - lastKeyDownTime));
                        return;
                    }
                    lastKeyDownTime = currentTime;

                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        if (currentTime - lastVolumeClickTime < TRIPLE_CLICK_TIMEOUT) {
                            volumeClickCount++;
                        } else {
                            volumeClickCount = 1;
                        }
                        lastVolumeClickTime = currentTime;

                        if (volumeClickCount >= 3) {
                            volumeClickCount = 0;
                            Boolean permanent = HookInit.permanentHiddenMap.get(hookInit.getCurrentTargetApp());
                            if (permanent != null && permanent) {
                                restorePermanentHidden(activity);
                            } else {
                                ReaLog.log("floating", "三击音量键，但当前未永久隐藏，忽略");
                            }
                        }
                    }

                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                        long diff = currentTime - lastVolumeDownTime;
                        if (diff < DOUBLE_CLICK_TIMEOUT) {
                            handleDoubleClick(activity);
                        }
                        lastVolumeDownTime = currentTime;
                    } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        long diff = currentTime - lastVolumeUpTime;
                        if (diff < DOUBLE_CLICK_TIMEOUT) {
                            handleDoubleClick(activity);
                        }
                        lastVolumeUpTime = currentTime;
                    }

                    // 调试模式触发（仅当悬浮窗存在时）
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                        if (currentFloatingView != null) {
                            DebugModeManager.onVolumeKeyPressed(keyCode, activity);
                        }
                    }
                }
            }
        });
    }

    private void handleDoubleClick(Activity activity) {
        String targetApp = hookInit.getCurrentTargetApp();
        Boolean permanent = HookInit.permanentHiddenMap.get(targetApp);
        if (permanent != null && permanent) {
            showPermanentHiddenToast(activity);
            ReaLog.log("floating", "双击音量键，但当前为永久隐藏，忽略");
            return;
        }

        Boolean currentlyShown = HookInit.floatingShownMap.get(targetApp);
        if (currentlyShown == null) currentlyShown = true;

        if (!currentlyShown) {
            HookInit.floatingShownMap.put(targetApp, true);
            HookInit.permanentHiddenMap.put(targetApp, false);
            showFloatingView(activity);
            ToastUtil.showUnique(activity, "悬浮窗 已恢复显示(临时)");
            ReaLog.log("floating", "用户通过双击恢复临时隐藏");
            final Activity fa = activity;
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    com.install.appinstall.xl.ui.UiKit.haptic(fa);
                }
            });
        } else {
            ViewGroup decorView = decorViewOf(activity);
            View floatingView = decorView.findViewWithTag(FLOATING_TAG);
            if (floatingView != null) {
                bringFloatingToFront(activity, (TextView) floatingView, decorView);
                start定时置顶(activity, decorView);
                ReaLog.log("floating", "置顶悬浮窗");
            } else {
                showFloatingView(activity);
            }
        }
    }
    
    //悬浮窗层级提权
    private void bringFloatingToFront(Activity activity, TextView floatingView, ViewGroup decorView) {
        if (activity.isFinishing() || floatingView == null || decorView == null) return;
        floatingView.bringToFront();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            floatingView.setElevation(999999999f);
            floatingView.setTranslationZ(999999999f);
        }
    }

    // ========== Activity生命周期Hook ==========
    public void hookActivityLifecycle(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "onCreate", Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                final Activity activity = (Activity) param.thisObject;
                                More.postOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (isFloatingVisibleForCurrentApp()) {
                                                showFloatingView(activity);
                                            }
                                        } catch (Throwable t) {
                                        }
                                    }
                                }, 850);
                            } catch (Throwable t) {
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.thisObject;
                                hookInit.setCurrentResumedActivity(activity);
                                boolean shouldShow = isFloatingVisibleForCurrentApp();
                                ViewGroup decorView = decorViewOf(activity);
                                View floatingView = decorView.findViewWithTag(FLOATING_TAG);
                                if (floatingView != null) {
                                    decorView.removeView(floatingView);
                                }
                                if (shouldShow) {
                                    showFloatingView(activity);
                                }
                            } catch (Throwable t) {
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "onPause",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.thisObject;
                                Activity current = hookInit.getCurrentResumedActivity();
                                if (current == activity) {
                                    hookInit.setCurrentResumedActivity(null);
                                }
                            } catch (Throwable t) {
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "onDestroy",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Activity activity = (Activity) param.thisObject;
                                Activity current = hookInit.getCurrentResumedActivity();
                                if (current == activity) {
                                    hookInit.setCurrentResumedActivity(null);
                                }
                                ViewGroup decorView = decorViewOf(activity);
                                View floatingView = decorView.findViewWithTag(FLOATING_TAG);
                                if (floatingView != null) {
                                    decorView.removeView(floatingView);
                                    ReaLog.log("floating", "Activity销毁时移除悬浮窗");
                                }
                                if (currentFloatingView != null && currentFloatingView.getParent() == null) {
                                    currentFloatingView = null;
                                }
                                if (mCurrentDecorView == decorView) {
                                    mCurrentDecorView = null;
                                    mCurrentFloatingView = null;
                                }
                                if (currentFloatingView != null) {
                                    currentFloatingView.animate().cancel();
                                }
                            } catch (Throwable t) {
                            }
                        }
                    }
            );
        } catch (Throwable t) {
        }
    }

    // ========== 置顶Hook ==========
    private void hookDecorViewAddView() {
        if (mAddViewHookInstalled) return;
        try {
            XposedBridge.hookAllMethods(ViewGroup.class, "addView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mCurrentDecorView == null || mCurrentFloatingView == null) return;
                    ViewGroup target = (ViewGroup) param.thisObject;
                    if (target.getRootView() == mCurrentDecorView) {
                        try {
                            mCurrentFloatingView.bringToFront();
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                mCurrentFloatingView.setElevation(999999999f);
                                mCurrentFloatingView.setTranslationZ(999999999f);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            });
            mAddViewHookInstalled = true;
            ReaLog.log("floating", "悬浮窗置顶已启用");
        } catch (Throwable t) {
            ReaLog.log("floating", "悬浮窗置顶失败: " + t.getMessage());
        }
    }

    // ========== 显示悬浮窗 ==========
    public void showFloatingView(final Activity activity) {
        try {
            if (!isFloatingVisibleForCurrentApp()) {
                ViewGroup decorView = decorViewOf(activity);
                View existingView = decorView.findViewWithTag(FLOATING_TAG);
                if (existingView != null) decorView.removeView(existingView);
                return;
            }

            final ViewGroup decorView = decorViewOf(activity);
            View oldView = decorView.findViewWithTag(FLOATING_TAG);
            if (oldView != null) {
                decorView.removeView(oldView);
                if (currentFloatingView == oldView) currentFloatingView = null;
                if (mCurrentFloatingView == oldView) mCurrentFloatingView = null;
                if (mCurrentDecorView == decorView) mCurrentDecorView = null;
            }

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (activity.isFinishing() || activity.isDestroyed()) return;
                        final TextView floatingView = createFloatingView(activity);
                        if (floatingView == null) return;
                        int initWidth = ViewGroup.LayoutParams.WRAP_CONTENT;
                        if (isCollapsed) {
                            float d0 = activity.getResources().getDisplayMetrics().density;
                            collapsedWidthPx = collapsedWidthOf(d0);
                            initWidth = (int) collapsedWidthPx;
                            try {
                                floatingView.setText("安防");
                                floatingView.setTextSize(12);
                            } catch (Throwable ignored) {
                            }
                        }
                        decorView.addView(floatingView, new ViewGroup.LayoutParams(
                        initWidth, ViewGroup.LayoutParams.WRAP_CONTENT));
                        ReaLog.log("floating", "悬浮窗已创建并显示");
                        mCurrentDecorView = decorView;
                        mCurrentFloatingView = floatingView;
                        currentFloatingView = floatingView;
                        hookDecorViewAddView();
                        floatingView.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    String targetApp = hookInit.getCurrentTargetApp();
                                    Float savedX = HookInit.floatingXMap.get(targetApp);
                                    Float savedY = HookInit.floatingYMap.get(targetApp);
                                    int screenWidth = decorView.getWidth();
                                    int screenHeight = decorView.getHeight();
                                    int viewWidth = floatingView.getWidth();
                                    int viewHeight = floatingView.getHeight();
                                    if (viewWidth == 0) viewWidth = 200;
                                    if (viewHeight == 0) viewHeight = 80;
                                    float x, y;
                                    if (savedX != null && savedY != null && savedX >= 0 && savedY >= 0) {
                                        x = savedX;
                                        y = savedY;
                                    } else {
                                        x = screenWidth - viewWidth - 50;
                                        y = 200;
                                    }
                                    if (screenWidth > 0 && screenHeight > 0) {
                                        x = clampX(x, screenWidth, viewWidth);
                                        y = clampY(y, screenHeight, viewHeight);
                                    }
                                    floatingView.setX(x);
                                    floatingView.setY(y);
                                    saveFloatingPosition(targetApp, x, y);
                                    // 如果是收边状态，立即应用收边
                                    if (isCollapsed) {
                                        floatingView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (currentFloatingView != null && isCollapsed) {
                                                    applyCollapsedStyle(activity);
                                                }
                                            }
                                        });
                                    }
                                } catch (Throwable t) {
                                }
                            }
                        });

                        updateFloatingToTop(activity, floatingView, decorView);
                        start定时置顶(activity, decorView);
                    } catch (Throwable t) {
                        ReaLog.log("floating", "悬浮窗UI异常: " + t.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            ReaLog.log("floating", "悬浮窗View异常: " + t.getMessage());
        }
    }

    // ========== 定时置顶 ==========
    public void start定时置顶(final Activity activity, final ViewGroup decorView) {
        stop定时置顶();
        final WeakReference<Activity> activityRef = new WeakReference<>(activity);

        floatingTopHandler = new Handler(Looper.getMainLooper());
        floatingTopHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity act = activityRef.get();
                if (act == null || act.isFinishing() || currentFloatingView == null) {
                    stop定时置顶();
                    return;
                }
                updateFloatingToTop(act, currentFloatingView, decorView);
                if (isCollapsed && currentFloatingView != null) {
                    animateCollapsedToEdge(act);
                }
                floatingTopHandler.postDelayed(this, 3000);
            }
        }, 3000);

        // 自动收边计时（仅当未收边且悬浮窗可见时）
        scheduleAutoCollapse();
    }

    /** 导入配置后：把悬浮窗立刻挪到新配置里的位置。 */
    private Activity contextActivity() {
        try {
            if (currentFloatingView == null) return null;
            android.content.Context c = currentFloatingView.getContext();
            if (c instanceof Activity) return (Activity) c;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 重排自动收边计时：悬浮窗变化（改文本/状态）后必须重排*/
    private void scheduleAutoCollapse() {
        try {
            if (isCollapsed || currentFloatingView == null || isAnimating) return;
            autoHideHandler.removeCallbacks(autoHideRunnable);
            autoHideRunnable = new Runnable() {
                @Override
                public void run() {
                    Activity act = contextActivity();
                    if (act == null || act.isFinishing() || currentFloatingView == null
                            || isCollapsed || isAnimating) return;
                    collapseFloatingView(act);
                }
            };
            autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY);
        } catch (Throwable ignored) {
        }
    }

    public void stop定时置顶() {
        if (floatingTopHandler != null) {
            floatingTopHandler.removeCallbacksAndMessages(null);
            floatingTopHandler = null;
        }
        autoHideHandler.removeCallbacks(autoHideRunnable);
        if (currentFloatingView != null) {
            currentFloatingView.animate().cancel();
        }
    }

    // ========== 置顶刷新 ==========
    public void updateFloatingToTop(Activity activity, TextView floatingView, ViewGroup decorView) {
        if (activity.isFinishing() || floatingView == null || decorView == null) return;
        floatingView.bringToFront();
        floatingView.requestLayout();
        floatingView.invalidate();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            floatingView.setElevation(999999999f);
            floatingView.setTranslationZ(999999999f);
        }
    }

    // ========== 更新悬浮窗文本 ==========
    public void updateFloatingTextOnly() {
        if (currentFloatingView == null) return;
        currentFloatingView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    applyFloatingTextState(currentFloatingView);
                    clampFloatingPosition(currentFloatingView);
                } catch (Throwable t) {
                }
            }
        });
    }

    /** 把悬浮窗 TextView 刷新为当前状态样式*/
    private void applyFloatingTextState(TextView tv) {
        if (isAnimating) {
            // 动画结束后由 onEnd 补刷一次
            pendingTextStateRefresh = true;
            return;
        }
        pendingTextStateRefresh = false;
        try {
            ViewGroup.LayoutParams lpNow = tv.getLayoutParams();
            if (lpNow != null && collapsedWidthPx > 0 && lpNow.width > 0
                    && Math.abs(lpNow.width - (int) collapsedWidthPx) <= 2) {
                isCollapsed = true;
            }
        } catch (Throwable ignored) {
        }
        boolean status = isInstalledForCurrentApp();
        if (isCollapsed) {
            // 收边：确保宽度为收边窄宽，文本「安防」由收边流程维护
            try {
                ViewGroup.LayoutParams lp = tv.getLayoutParams();
                if (lp.width != (int) collapsedWidthPx) {
                    lp.width = (int) collapsedWidthPx;
                    tv.setLayoutParams(lp);
                }
            } catch (Throwable ignored) {
            }
            applyStatusBackground(tv, status);
            clampFloatingPosition(tv);
            return;
        }
        // 展开状态必须恢复 WRAP_CONTENT 宽度
        try {
            ViewGroup.LayoutParams lp = tv.getLayoutParams();
            if (lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                tv.setLayoutParams(lp);
            }
        } catch (Throwable ignored) {
        }
        // 收边时改过字号和内边距，展开态一并还原
        try {
            tv.setTextSize(14);
            android.content.Context ctx = tv.getContext();
            tv.setPadding(More.spx(ctx, 25), More.spx(ctx, 15),
                    More.spx(ctx, 25), More.spx(ctx, 15));
        } catch (Throwable ignored) {
        }
        tv.setText(getFullText());
        applyStatusBackground(tv, status);
        clampFloatingPosition(tv);
        scheduleAutoCollapse();
    }

    /** 动画结束后补齐动画期间被跳过的文本/背景刷新*/
    private void refreshTextStateAfterAnimation() {
        if (!pendingTextStateRefresh) return;
        pendingTextStateRefresh = false;
        if (currentFloatingView == null) return;
        try {
            applyFloatingTextState(currentFloatingView);
        } catch (Throwable ignored) {
        }
    }

    /** 按安装状态应用悬浮窗背景： 已安装=绿色 0xAA4CAF50（新UI改为主题色）；未安装=红色 0xAAF44336；圆角 25。 */
    private static void applyStatusBackground(TextView view, boolean status) {
        int bgColor;
        if (status && com.install.appinstall.xl.ui.UiKit.useNewUi(view.getContext())) {
            int accent = com.install.appinstall.xl.ui.UiKit.accent(view.getContext());
            bgColor = (accent & 0x00FFFFFF) | 0xAA000000; // 只换色，透明度沿用 0xAA
        } else {
            bgColor = status ? 0xAA4CAF50 : 0xAAF44336;
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(25f);
        view.setBackground(gd);
    }

    /**
     * 把悬浮窗夹回屏幕内（刷新/重建后可能因尺寸变化超出边界）
     */
    private void clampFloatingPosition(final View v) {
        clampFloatingPositionOnce(v);
        try {
            v.post(new Runnable() {
                public void run() {
                    clampFloatingPositionOnce(v);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void clampFloatingPositionOnce(View v) {
        try {
            ViewGroup parent = (ViewGroup) v.getParent();
            if (parent == null) return;
            int pw = parent.getWidth();
            int ph = parent.getHeight();
            if (pw <= 0 || ph <= 0) return;
            ViewGroup.LayoutParams raw = v.getLayoutParams();
            if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            int w = v.getWidth() > 0 ? v.getWidth() : lp.width;
            int h = v.getHeight() > 0 ? v.getHeight() : lp.height;
            try {
                v.measure(android.view.View.MeasureSpec.makeMeasureSpec(
                        0, android.view.View.MeasureSpec.UNSPECIFIED),
                        android.view.View.MeasureSpec.makeMeasureSpec(
                                0, android.view.View.MeasureSpec.UNSPECIFIED));
                if (v.getMeasuredWidth() > w) w = v.getMeasuredWidth();
                if (v.getMeasuredHeight() > h) h = v.getMeasuredHeight();
            } catch (Throwable ignored) {
            }
            if (w <= 0 || h <= 0) return;
            int maxL = Math.max(0, pw - w);
            int maxT = Math.max(0, ph - h);
            boolean changed = false;
            if (lp.leftMargin < 0) {
                lp.leftMargin = 0;
                changed = true;
            }
            if (lp.topMargin < 0) {
                lp.topMargin = 0;
                changed = true;
            }
            if (lp.leftMargin > maxL) {
                lp.leftMargin = maxL;
                changed = true;
            }
            if (lp.topMargin > maxT) {
                lp.topMargin = maxT;
                changed = true;
            }
            if (changed) v.setLayoutParams(lp);
        } catch (Throwable ignored) {
        }
    }

    public void updateFloatingView(final Activity activity) {
        if (activity == null) {
            updateFloatingTextOnly();
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean shouldShow = isFloatingVisibleForCurrentApp();
                    View existingView = findFloatingViewIn(activity);
                    if (!shouldShow) {
                        if (existingView != null) {
                            ((ViewGroup) existingView.getParent()).removeView(existingView);
                            if (currentFloatingView == existingView) currentFloatingView = null;
                            stop定时置顶();
                            isCollapsed = false;
                            isAnimating = false;
                        }
                        return;
                    }
                    if (existingView == null) {
                        showFloatingView(activity);
                        return;
                    }
                    if (existingView instanceof TextView) {
                        applyFloatingTextState((TextView) existingView);
                        clampFloatingPosition(existingView);
                    }
                } catch (Throwable t) {
                    ReaLog.log("floating", "悬浮窗更新异常: " + t.getMessage());
                }
            }
        });
    }

    // ========== 创建悬浮窗View ==========
    private TextView createFloatingView(final Activity activity) {
        try {
            final TextView floatingView = new TextView(activity);
            floatingView.setTag(FLOATING_TAG);
            final boolean status = isInstalledForCurrentApp();
            floatingView.setText(getFullText());
            floatingView.setTextSize(14);
            floatingView.setTextColor(0xFFFFFFFF);
            floatingView.setPadding(More.spx(activity, 25), More.spx(activity, 15), More.spx(activity, 25), More.spx(activity, 15));
            floatingView.setGravity(Gravity.CENTER);
            applyStatusBackground(floatingView, status);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    floatingView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, android.graphics.Outline outline) {
                            outline.setRect(0, 0, view.getWidth(), view.getHeight());
                            outline.setAlpha(0f);
                        }
                    });
                } catch (Throwable ignored) {
                }
            }

            // ========== 触摸事件 ==========
            floatingView.setOnTouchListener(new View.OnTouchListener() {
                private float startX, startY, initialX, initialY;
                private boolean longPressTriggered = false;
                private boolean isClickHandled = false;
                private Handler longPressHandler;
                private long firstClickTime = 0;
                private int clickCount = 0;
                private Handler clickHandler = new Handler();
                private static final int DOUBLE_CLICK_DELAY = 300;
                private boolean isDragging = false;

                @Override
                public boolean onTouch(final View v, MotionEvent event) {
                    try {
                        // ========== 收边状态 ==========
                        if (isCollapsed) {
                            switch (event.getAction()) {
                                case MotionEvent.ACTION_DOWN:
                                    stop定时置顶();
                                    startX = event.getRawX();
                                    startY = event.getRawY();
                                    initialX = v.getX();
                                    initialY = v.getY();
                                    isDragging = false;
                                    // 清理旧任务
                                    clickHandler.removeCallbacksAndMessages(null);
                                    if (longPressHandler != null)
                                        longPressHandler.removeCallbacksAndMessages(null);
                                    return true;

                                case MotionEvent.ACTION_MOVE:
                                    float dx = event.getRawX() - startX;
                                    float dy = event.getRawY() - startY;
                                    if (Math.abs(dx) > 20 || Math.abs(dy) > 20) {
                                        isDragging = true;
                                    }
                                    if (isDragging) {
                                        float newX = initialX + dx;
                                        float newY = initialY + dy;
                                        ViewGroup decorView = decorViewOf(activity);
                                        int screenWidth = decorView.getWidth();
                                        int screenHeight = decorView.getHeight();
                                        float viewWidth = (collapsedWidthPx > 0) ? collapsedWidthPx : More.dp(activity, 60);
                                        float viewHeight = v.getHeight();
                                        newX = clampX(newX, screenWidth, viewWidth);
                                        newY = clampY(newY, screenHeight, viewHeight);
                                        v.setX(newX);
                                        v.setY(newY);
                                        snapToRight = (newX + viewWidth / 2) > screenWidth / 2;
                                        collapsedX = newX;
                                        collapsedY = newY;
                                        saveFloatingPosition(newX, newY);
                                    }
                                    return true;

                                case MotionEvent.ACTION_UP:
                                    if (!isDragging) {
                                        // 单击展开
                                        expandFloatingView(activity);
                                    } else {
                                        // 拖动结束，保存位置并重新计时
                                        ViewGroup decorView = decorViewOf(activity);
                                        start定时置顶(activity, decorView);
                                        hookInit.saveConfigToFile();
                                        isDragging = false;
                                    }
                                    return true;

                                case MotionEvent.ACTION_CANCEL:
                                    isDragging = false;
                                    return true;
                            }
                            return true;
                        }

                        // ========== 展开状态 ==========
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                // 清理旧任务和状态
                                clickHandler.removeCallbacksAndMessages(null);
                                if (longPressHandler != null)
                                    longPressHandler.removeCallbacksAndMessages(null);
                                longPressTriggered = false;
                                isClickHandled = false;
                                stop定时置顶();
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - firstClickTime < DOUBLE_CLICK_DELAY) {
                                    clickCount++;
                                    clickHandler.removeCallbacksAndMessages(null);
                                    isClickHandled = false;
                                    if (clickCount == 2) {
                                        clickCount = 0;
                                        firstClickTime = 0;
                                        ReaLog.log("system", "双击悬浮窗打开添加包名");
                                        com.install.appinstall.xl.ui.UiKit.haptic(activity);
                                        if (com.install.appinstall.xl.ui.UiKit.useNewUi(activity)) {
                                            // 新界面：直达包名页 + 再推入添加包名页
                                            com.install.appinstall.xl.ui.UiPages.requestTab(0);
                                            com.install.appinstall.xl.ui.UiPages.open(activity, floatingView);
                                            final TextView fv0 = floatingView;
                                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                                                            new Runnable() {
                                                                public void run() {
                                                                    try {
                                                                        com.install.appinstall.xl.ui.PageStack s = com.install.appinstall.xl.ui.PageStack.get();
                                                                        if (s != null) {
                                                                            s.push(new com.install.appinstall.xl.ui.UiPages.AddPackagePage(fv0));
                                                                        }
                                                                    } catch (Throwable ignored) {
                                                                    }
                                                                }
                                                            }, 380);
                                            return true;
                                        }
                                        hookInit.showAddPackageDialog(activity);
                                        return true;
                                    }
                                } else {
                                    clickCount = 1;
                                    firstClickTime = currentTime;
                                }
                                clickHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (clickCount == 1 && !longPressTriggered && !isDragging && !isClickHandled) {
                                            isClickHandled = true;
                                            ReaLog.log("system", "单击悬浮窗打开状态切换");
                                            if (com.install.appinstall.xl.ui.UiKit.useNewUi(activity)
                                                    && com.install.appinstall.xl.ui.UiPages.open(activity, floatingView)) {
                                                clickCount = 0;
                                                firstClickTime = 0;
                                                return;
                                            }
                                            hookInit.showStatusSwitchDialog(activity, floatingView);
                                            clickCount = 0;
                                            firstClickTime = 0;
                                        }
                                    }
                                }, DOUBLE_CLICK_DELAY);

                                longPressHandler = new Handler();
                                longPressHandler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        clickHandler.removeCallbacksAndMessages(null);
                                        longPressTriggered = true;
                                        isClickHandled = true;
                                        clickCount = 0;
                                        firstClickTime = 0;
                                        if (hookInit.statusSwitchDialog != null && hookInit.statusSwitchDialog.isShowing()) {
                                            hookInit.statusSwitchDialog.dismiss();
                                            hookInit.statusSwitchDialog = null;
                                        }
                                        // 按当前界面风格分派
                                        if (com.install.appinstall.xl.ui.UiKit.useNewUi(activity)) {
                                            com.install.appinstall.xl.ui.UiKit.setUseNewUi(activity, false);
                                            com.install.appinstall.xl.ui.PageStack.closeAll();
                                            ReaLog.log("system", "长按切回旧版界面");
                                            ToastUtil.showUnique(activity,"使用旧版，重新点击悬浮窗打开");
                                            com.install.appinstall.xl.ui.UiTabs.save(activity);
                                        } else {
                                            ReaLog.log("system", "长按悬浮窗打开管理设置");
                                            hookInit.showHideDialog(activity, v);
                                        }
                                    }
                                }, 300);

                                startX = event.getRawX();
                                startY = event.getRawY();
                                initialX = v.getX();
                                initialY = v.getY();
                                isDragging = false;
                                return true;

                            case MotionEvent.ACTION_MOVE:
                                float deltaX = Math.abs(event.getRawX() - startX);
                                float deltaY = Math.abs(event.getRawY() - startY);
                                if (!isDragging && (deltaX > 80 || deltaY > 80)) { // 提高阈值，减少误触
                                    isDragging = true;
                                    clickHandler.removeCallbacksAndMessages(null);
                                    if (longPressHandler != null)
                                        longPressHandler.removeCallbacksAndMessages(null);
                                    isClickHandled = true;
                                    clickCount = 0;
                                    firstClickTime = 0;
                                }
                                if (isDragging) {
                                    float newX = initialX + (event.getRawX() - startX);
                                    float newY = initialY + (event.getRawY() - startY);
                                    ViewGroup decorView = decorViewOf(activity);
                                    int screenWidth = decorView.getWidth();
                                    int screenHeight = decorView.getHeight();
                                    int viewWidth = v.getWidth();
                                    int viewHeight = v.getHeight();
                                    if (viewWidth == 0) viewWidth = 200;
                                    if (viewHeight == 0) viewHeight = 80;
                                    newX = clampX(newX, screenWidth, viewWidth);
                                    newY = clampY(newY, screenHeight, viewHeight);
                                    v.setX(newX);
                                    v.setY(newY);
                                    saveFloatingPosition(newX, newY);
                                }
                                return true;

                            case MotionEvent.ACTION_UP:
                                if (longPressHandler != null)
                                    longPressHandler.removeCallbacksAndMessages(null);
                                if (longPressTriggered) {
                                    clickHandler.removeCallbacksAndMessages(null);
                                    isClickHandled = true;
                                }
                                if (isDragging) {
                                    saveFloatingPosition(v.getX(), v.getY());
                                    hookInit.saveConfigToFile();
                                    ViewGroup decorView = decorViewOf(activity);
                                    start定时置顶(activity, decorView);
                                } else {
                                    ViewGroup decorView = decorViewOf(activity);
                                    start定时置顶(activity, decorView);
                                }
                                isDragging = false;
                                return true;

                            case MotionEvent.ACTION_CANCEL:
                                clickHandler.removeCallbacksAndMessages(null);
                                if (longPressHandler != null)
                                    longPressHandler.removeCallbacksAndMessages(null);
                                isDragging = false;
                                longPressTriggered = false;
                                isClickHandled = true;
                                clickCount = 0;
                                firstClickTime = 0;
                                ViewGroup decorView = decorViewOf(activity);
                                start定时置顶(activity, decorView);
                                return true;
                        }
                    } catch (Throwable t) {
                        ReaLog.log("floating", "触摸事件异常: " + t.getMessage());
                    }
                    return true;
                }
            });

            currentFloatingView = floatingView;
            return floatingView;
        } catch (Throwable t) {
            ReaLog.log("floating", "悬浮窗创建异常: " + t.getMessage());
            return null;
        }
    }

    // 按「完整文本」测量悬浮窗尺寸
    private float[] measureFullTextSize(TextView tv) {
        tv.setText(getFullText());
        tv.setTextSize(14);
        tv.setPadding(More.spx(tv.getContext(), 25), More.spx(tv.getContext(), 15), More.spx(tv.getContext(), 25), More.spx(tv.getContext(), 15));
        tv.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return new float[]{tv.getMeasuredWidth(), tv.getMeasuredHeight()};
    }

    // 统一的悬浮窗过渡动画：300ms
    private void runSlideAnimation(final View view,
            final boolean animateWidth, final float fromWidth, final float toWidth,
            final float fromX, final float toX,
            final float fromY, final float toY,
            final Runnable onEnd) {
        ValueAnimator anim = ValueAnimator.ofFloat(0, 1);
        anim.setDuration(300);
        anim.setInterpolator(new DecelerateInterpolator());

        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = animation.getAnimatedFraction();
                if (animateWidth) {
                    ViewGroup.LayoutParams lp = view.getLayoutParams();
                    lp.width = (int) (fromWidth + (toWidth - fromWidth) * fraction);
                    view.setLayoutParams(lp);
                }
                view.setX(fromX + (toX - fromX) * fraction);
                view.setY(fromY + (toY - fromY) * fraction);
            }
        });
        if (onEnd != null) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    onEnd.run();
                }
            });
        }
        anim.start();
    }

    /** 取 Activity 的 DecorView */
    private static ViewGroup decorViewOf(Activity activity) {
        return (ViewGroup) activity.getWindow().getDecorView();
    }

    /** 在 Activity 的 DecorView 上查找当前悬浮窗。 */
    private static View findFloatingViewIn(Activity activity) {
        return decorViewOf(activity).findViewWithTag(FLOATING_TAG);
    }

    /** 收边窄宽 = 60dp */
    private static float collapsedWidthOf(float density) {
        return 60 * density;
    }

    /** 确保悬浮窗宽度等于目标值 */
    private void ensureCollapsedWidth(int targetWidth) {
        ViewGroup.LayoutParams lp = currentFloatingView.getLayoutParams();
        if (lp.width != targetWidth) {
            lp.width = targetWidth;
            currentFloatingView.setLayoutParams(lp);
        }
    }

    /** 保存当前目标应用的悬浮窗坐标 */
    private void saveFloatingPosition(float x, float y) {
        saveFloatingPosition(hookInit.getCurrentTargetApp(), x, y);
    }

    /** 用调用方给定的目标应用保存坐标 */
    private void saveFloatingPosition(String targetApp, float x, float y) {
        HookInit.floatingXMap.put(targetApp, x);
        HookInit.floatingYMap.put(targetApp, y);
    }

    /** 悬浮窗 X 边界夹取：左右各留 10px */
    private static float clampX(float x, int screenWidth, float viewWidth) {
        return Math.max(10, Math.min(x, screenWidth - viewWidth - 10));
    }

    /** 悬浮窗 Y 边界夹取：上留 150、下留 250 */
    private static float clampY(float y, int screenHeight, float viewHeight) {
        return Math.max(150, Math.min(y, screenHeight - viewHeight - 250));
    }

    /** 收边状态下平滑移动到边缘（用于定时器循环） */
    private void animateCollapsedToEdge(Activity activity) {
        if (currentFloatingView == null || activity.isFinishing() || !isCollapsed || isAnimating)
            return;

        ViewGroup decorView = decorViewOf(activity);
        int screenWidth = decorView.getWidth();
        int screenHeight = decorView.getHeight();
        float density = activity.getResources().getDisplayMetrics().density;
        float margin = 8 * density;
        int targetWidth = (int) collapsedWidthOf(density);

        // 确保宽度正确
        ensureCollapsedWidth(targetWidth);

        float currentX = currentFloatingView.getX();
        float currentY = currentFloatingView.getY();
        // 根据当前中心点判断吸附方向
        float centerX = currentX + targetWidth / 2;
        snapToRight = centerX > screenWidth / 2;

        float targetX = snapToRight ? screenWidth - targetWidth - margin : margin;
        float targetY = clampY(currentY, screenHeight, currentFloatingView.getHeight());

        // 如果已经非常接近边缘（误差 < 5px），则直接设置并跳过动画
        if (Math.abs(currentX - targetX) < 5 && Math.abs(currentY - targetY) < 5) {
            return;
        }

        // 执行滑动动画
        final float endX = targetX;
        final float endY = targetY;
        runSlideAnimation(
        currentFloatingView,
        false,
        0f,
        0f,
        currentX,
        endX,
        currentY,
        endY,
        new Runnable() {
            @Override
            public void run() {
                // 更新存储位置
                saveFloatingPosition(endX, endY);
                collapsedX = endX;
                collapsedY = endY;
            }
        }
        );
    }

    // ========== 收边与展开核心 ==========
    // 获取悬浮窗完整文本（展开态显示内容），供测量尺寸/恢复文本共用
    private String getFullText() {
        String targetApp = hookInit.getCurrentTargetApp();
        boolean status = isInstalled(targetApp);
        String statusText = status ? "已安装" : "未安装";
        boolean blockExit = HookInit.blockExitMap.getOrDefault(targetApp, false);
        boolean superBlock = HookInit.superBlockExitMap.getOrDefault(targetApp, false);
        String blockText = (blockExit || superBlock) ? "[拦截]" : "";
        return "安装防护(" + statusText + ")" + blockText;
    }

    private void collapseFloatingView(final Activity activity) {
        if (currentFloatingView == null || activity.isFinishing() || isCollapsed || isAnimating)
            return;
        isAnimating = true;

        // 收边时用「完整文本测量宽度」记录展开尺寸，不用 getWidth()/getHeight()——
        // 悬浮窗在异常状态（展开失败/文本换行）下 getWidth 会被污染，
        // 污染值会被当成下次展开/收边的目标，导致永久错乱。
        try {
            TextView tv = currentFloatingView;
            float[] measured = measureFullTextSize(tv);
            fullWidth = measured[0];
            fullHeight = measured[1];
            tv.setText("安防");
            tv.setTextSize(12);
        } catch (Throwable t) {
            fullWidth = currentFloatingView.getWidth();
            fullHeight = currentFloatingView.getHeight();
        }
        fullX = currentFloatingView.getX();
        fullY = currentFloatingView.getY();

        float density = activity.getResources().getDisplayMetrics().density;
        collapsedWidthPx = collapsedWidthOf(density);

        ViewGroup decorView = decorViewOf(activity);
        int screenWidth = decorView.getWidth();
        int screenHeight = decorView.getHeight();

        float centerX = fullX + fullWidth / 2;
        snapToRight = centerX > screenWidth / 2;

        float margin = 8 * density;
        final float targetX = snapToRight ? screenWidth - collapsedWidthPx - margin : margin;
        final float targetY = clampY(fullY, screenHeight, fullHeight);

        collapsedX = targetX;
        collapsedY = targetY;

        runSlideAnimation(
        currentFloatingView,
        true,
        fullWidth,
        collapsedWidthPx,
        fullX,
        targetX,
        targetY,
        targetY,
        new Runnable() {
            @Override
            public void run() {
                try {
                    ViewGroup.LayoutParams lp = currentFloatingView.getLayoutParams();
                    lp.width = (int) collapsedWidthPx;
                    currentFloatingView.setLayoutParams(lp);
                    currentFloatingView.requestLayout();
                } catch (Throwable ignored) {
                }
                currentFloatingView.setText("安防");
                currentFloatingView.setTextSize(12);
                isCollapsed = true;
                isAnimating = false;
                saveFloatingPosition(targetX, targetY);
                hookInit.saveConfigToFile();
                start定时置顶(activity, decorViewOf(activity));
                ReaLog.log("floating", "悬浮窗：自动吸附");
                refreshTextStateAfterAnimation();
            }
        }
        );
    }

    private void expandFloatingView(final Activity activity) {
        if (currentFloatingView == null || activity.isFinishing() || !isCollapsed) return;
        // 若上一个动画未结束（isAnimating 卡 true，主线程繁忙时动画帧被跳过可能触发）
        if (isAnimating) {
            More.postOnMainThread(new Runnable() {
                @Override
                public void run() {
                    if (currentFloatingView == null || activity.isFinishing() || !isCollapsed)
                        return;
                    // 兜底：正常动画 300ms 内必已结束；若 300ms 后 isAnimating 仍为
                    // true，说明动画对象已异常卡死，强制复位后继续展开，确保点击永远可用
                    if (isAnimating) {
                        isAnimating = false;
                        ReaLog.log("floating", "悬浮窗：动画状态异常，强制复位后展开");
                    }
                    expandFloatingView(activity);
                }
            }, 300);
            return;
        }
        isAnimating = true;

        final ViewGroup decorView = decorViewOf(activity);
        int screenWidth = decorView.getWidth();
        int screenHeight = decorView.getHeight();
        float density = activity.getResources().getDisplayMetrics().density;
        float margin = 8 * density;

        // 使用当前视图位置作为基准
        final float currentX = currentFloatingView.getX();
        final float currentY = currentFloatingView.getY();
        final float currentWidth = currentFloatingView.getWidth();

        // 根据当前中心点判断吸附方向
        snapToRight = (currentX + currentWidth / 2) > screenWidth / 2;

        // 展开目标宽度/高度用实时测量「完整文本」的实际尺寸，不依赖 fullWidth/fullHeight（异常状态下会被污染，导致永久错乱）。
        final String targetApp = hookInit.getCurrentTargetApp();
        final boolean status = HookInit.installStatusMap.getOrDefault(targetApp, true);
        final String fullText = getFullText();
        TextView tv = currentFloatingView;
        float[] measured = measureFullTextSize(tv);
        final float measuredW = measured[0];
        final float measuredH = measured[1];
        final float targetW = (measuredW > 0) ? measuredW : (200 * density);
        final float targetH = (measuredH > 0) ? measuredH : (80 * density);
        tv.setText("安防");
        tv.setTextSize(14);

        // 计算展开目标 X/Y（用测量出的目标宽度，确保悬浮窗完整落在屏内）
        final float targetX;
        if (snapToRight) {
            targetX = screenWidth - targetW - margin;
        } else {
            targetX = margin;
        }
        final float targetY = clampY(currentY, screenHeight, targetH);

        // 展开动画：宽度与位置一起过渡
        runSlideAnimation(
        currentFloatingView,
        true,
        (currentWidth > 0 ? currentWidth : collapsedWidthPx),
        targetW,
        currentX,
        targetX,
        targetY,
        targetY,
        new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        ViewGroup.LayoutParams lp = currentFloatingView.getLayoutParams();
                        lp.width = (int) targetW;
                        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        currentFloatingView.setLayoutParams(lp);
                        currentFloatingView.requestLayout();
                    } catch (Throwable ignored) {
                    }
                    // 恢复文本和样式
                    currentFloatingView.setText(fullText);
                    currentFloatingView.setTextSize(14);
                    currentFloatingView.setPadding(More.spx(currentFloatingView.getContext(), 25), More.spx(currentFloatingView.getContext(), 15), More.spx(currentFloatingView.getContext(), 25), More.spx(currentFloatingView.getContext(), 15));
                    currentFloatingView.setGravity(Gravity.CENTER);
                    applyStatusBackground(currentFloatingView, status);

                    // 更新保存的位置（展开位置）
                    saveFloatingPosition(targetApp, targetX, targetY);
                    hookInit.saveConfigToFile();

                    ReaLog.log("floating", "悬浮窗：恢复展开");
                } catch (Throwable t) {
                    ReaLog.log("floating", "悬浮窗展开结束异常: " + t.getMessage());
                } finally {
                    isCollapsed = false;
                    isAnimating = false;
                    try {
                        start定时置顶(activity, decorView);
                    } catch (Throwable ignored) {
                    }
                    refreshTextStateAfterAnimation();
                }
            }
        }
        );
    }

    // ========== 调整收边位置（横竖屏适配） ==========
    private void adjustCollapsedPosition(Activity activity) {
        if (currentFloatingView == null || activity.isFinishing() || !isCollapsed) return;
        ViewGroup decorView = decorViewOf(activity);
        int screenWidth = decorView.getWidth();
        int screenHeight = decorView.getHeight();
        float density = activity.getResources().getDisplayMetrics().density;

        // 确保宽度正确
        int targetWidth = (int) collapsedWidthOf(density);
        ensureCollapsedWidth(targetWidth);
        float margin = 8 * density;
        // 坐标来源优先级：配置(Map) > 内部字段
        String app = (hookInit != null) ? hookInit.getCurrentTargetApp() : null;
        Float savedX = (app != null) ? HookInit.floatingXMap.get(app) : null;
        Float savedY = (app != null) ? HookInit.floatingYMap.get(app) : null;
        float baseX = (savedX != null && savedX.floatValue() >= 0)
                ? savedX.floatValue() : collapsedX;
        float baseY = (savedY != null && savedY.floatValue() >= 0)
                ? savedY.floatValue() : collapsedY;
        // 贴哪一边由配置里的 X 决定，否则导入右边坐标也会被贴回左边
        if (screenWidth > 0) {
            snapToRight = baseX > screenWidth / 2f;
        }
        float targetX = snapToRight ? screenWidth - targetWidth - margin : margin;
        float targetY = clampY(baseY, screenHeight, currentFloatingView.getHeight());

        currentFloatingView.setX(targetX);
        currentFloatingView.setY(targetY);
        collapsedX = targetX;
        collapsedY = targetY;
        saveFloatingPosition(targetX, targetY);
    }

    // ========== 强制应用收边样式 ==========
    private void applyCollapsedStyle(Activity activity) {
        if (currentFloatingView == null || activity.isFinishing()) return;
        if (isCollapsed) {
            float density = activity.getResources().getDisplayMetrics().density;
            collapsedWidthPx = collapsedWidthOf(density);
            ViewGroup.LayoutParams lp = currentFloatingView.getLayoutParams();
            lp.width = (int) collapsedWidthPx;
            currentFloatingView.setLayoutParams(lp);
            currentFloatingView.setText("安防");
            currentFloatingView.setTextSize(12);
            adjustCollapsedPosition(activity);
        } else {
            collapseFloatingView(activity);
        }
    }

    // ========== 获取当前悬浮窗 ==========
    public TextView getCurrentFloatingView() {
        return currentFloatingView;
    }
}

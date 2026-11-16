package com.install.appinstall.xl.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.install.appinstall.xl.HookInit;

/**
 * 小功能集合体
 */
public class More {

    /**
     * 向 BuHook 仓库注册本组件提供的挂载功能（注册幂等，重复调用安全）。
     * 对话框取消/屏幕限制解除本为同步段挂载项，现统一走仓库调度。
     */
    public static void registerHooks() {
        BuHook.register("hook_dialog_cancelable", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    String pkg = ctx != null ? ctx.getPackageName() : "";
                    if (!HookInit.hookDialogCancelableMap.getOrDefault(pkg, true)) {
                        ReaLog.log("more", "对话框强制取消已关闭，跳过挂载: " + pkg);
                        return;
                    }
                    hookDialogCancelableMethods(cl);
                }
            });
        BuHook.register("hook_window_flags", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    String pkg = ctx != null ? ctx.getPackageName() : "";
                    if (!HookInit.hookWindowFlagsMap.getOrDefault(pkg, true)) {
                        ReaLog.log("more", "屏幕限制解除已关闭，跳过挂载: " + pkg);
                        return;
                    }
                    hookWindowFlags(cl);
                }
            });
        BuHook.register("hook_anti_cancel_finish", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    String pkg = ctx != null ? ctx.getPackageName() : "";
                    if (!HookInit.hookAntiCancelFinishMap.getOrDefault(pkg, true)) {
                        ReaLog.log("more", "对话框强制取消(防界面关闭)已关闭，跳过挂载: " + pkg);
                        return;
                    }
                    hookAntiCancelFinish(cl);
                }
            });
        // 震动控制：hook 始终挂载，门控在回调内即时读 Map（开关即时生效，不必重启）。
        BuHook.register("hook_vibrator", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookVibrator(cl);
                }
            });
    }

    // 窗口 flags 位掩码常量
    private static final int FLAG_SECURE = WindowManager.LayoutParams.FLAG_SECURE;
    private static final int FLAG_NOT_TOUCHABLE = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    private static final int FLAG_NOT_FOCUSABLE = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;

    // 正在显示且可取消的 Dialog 跟踪表
    private static final java.util.List<Object> sShowingCancelableDialogs = new java.util.ArrayList<Object>();

    // 对话框强制取消(防界面关闭)：判定"finish 是否发生在 Dialog.cancel 调用链内部"
    private static final ThreadLocal<Boolean> sInDialogCancel = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return Boolean.FALSE;
        }
    };
    // 兜底窗口：cancel 的 after 未执行的极端情况下，避免标记残留永久卡死 finish
    private static final long ANTI_FINISH_FALLBACK_MS = 5000;
    private static volatile long sDialogCancelStartedAt = 0;
    // 兜底的记账表：ViewRootImpl → 抹除 FLAG_NOT_TOUCHABLE|FLAG_NOT_FOCUSABLE 前的原始 flags
    private static final java.util.WeakHashMap<Object, Integer> sWindowFlagRestoreMap =
    new java.util.WeakHashMap<Object, Integer>();

    /** 还原兜底抹掉的窗口 flags（弹窗全部消失时调用；还原后清空账本） */
    private static void restoreWindowFlags() {
        synchronized (sWindowFlagRestoreMap) {
            if (sWindowFlagRestoreMap.isEmpty()) return;
            java.util.Iterator<java.util.Map.Entry<Object, Integer>> it =
                sWindowFlagRestoreMap.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<Object, Integer> e = it.next();
                try {
                    Object attrs = getViewRootWindowAttrs(e.getKey());
                    if (attrs != null && sWindowAttrsFlagsField != null) {
                        sWindowAttrsFlagsField.setInt(attrs, e.getValue());
                    }
                } catch (Throwable ignored) {
                }
                it.remove();
            }
        }
    }

    // 对话框跳过标记：过滤模块自身对话框被方法覆盖
    private static final String SKIP_DIALOG_TAG = "__hook_skip_dialog";
    /** 模块内部发起启动的跳过标记，新增功能调 markInternal 即可 */
    public static final String SKIP_LAUNCH_TAG = "__hook_skip_flag";
    /** 给模块内部发起的启动打跳过标记：启动拦截 hook 见标记直接放行
     *  （View tag）是两套机制，各自管各的 hook */
    public static void markInternal(android.content.Intent intent) {
        if (intent != null) intent.putExtra(SKIP_LAUNCH_TAG, true);
    }

    // 返回键 hook 幂等标志（防重复挂导致 handler 叠加）
    private static boolean sBackHookInstalled = false;

    // ViewRootImpl.requestLayout 热路径反射元数据缓存
    private static volatile java.lang.reflect.Field sViewRootWindowField;
    private static volatile java.lang.reflect.Method sWindowGetAttributesMethod;
    private static volatile java.lang.reflect.Field sWindowAttrsFlagsField;

    // ===== 屏幕限制解除：扩展挂载点 =====
    // 各扩展项挂载幂等标志（BuHook 已有 key 级幂等，这层防重复调度导致 hook 叠加）
    private static volatile boolean sWindowGlobalHookInstalled = false;
    private static volatile boolean sSurfaceViewHookInstalled = false;
    private static volatile boolean sTxnBlockHookInstalled = false;
    private static volatile boolean sAntiSecureHooksInstalled = false;
    // 反射元数据初始化锁（双重检查）
    private static final Object sSurfaceLock = new Object();
    // ViewRootImpl 持有的 SurfaceControl 字段（mSurfaceControl/mSurface/mLeash/mSurfaceControlLocked 多变体）
    private static volatile java.lang.reflect.Field sVriSurfaceField;
    private static volatile Class<?> sScClass;
    private static volatile java.lang.reflect.Method sScIsValid;
    private static volatile java.lang.reflect.Constructor<?> sTxnCtor;
    private static volatile java.lang.reflect.Method sTxnApply;
    private static volatile java.lang.reflect.Method sTxnClose;
    private static volatile java.lang.reflect.Method sTxnSetSecure;
    private static volatile java.lang.reflect.Method sTxnSetSkipScreenshot;
    // 已下发解除事务的窗口记账（ViewRootImpl → SurfaceControl）：重布局时不重复下发，WeakHashMap 防泄漏
    private static final java.util.WeakHashMap<Object, Object> sAntiSecureApplied =
    new java.util.WeakHashMap<Object, Object>();

    /** 取 ViewRootImpl 所在窗口的 LayoutParams；反射元数据懒缓存，失败返回 null */
    private static Object getViewRootWindowAttrs(Object viewRoot) {
        try {
            if (sViewRootWindowField == null) {
                java.lang.reflect.Field mf = viewRoot.getClass().getDeclaredField("mWindow");
                mf.setAccessible(true);
                sViewRootWindowField = mf;
            }
            Object win = sViewRootWindowField.get(viewRoot);
            if (win == null) return null;
            if (sWindowGetAttributesMethod == null) {
                sWindowGetAttributesMethod = win.getClass().getMethod("getAttributes");
            }
            Object attrs = sWindowGetAttributesMethod.invoke(win);
            if (attrs != null && sWindowAttrsFlagsField == null) {
                java.lang.reflect.Field ff = attrs.getClass().getDeclaredField("flags");
                ff.setAccessible(true);
                sWindowAttrsFlagsField = ff;
            }
            return attrs;
        } catch (Throwable t) {
            return null;
        }
    }


    // ============================================================
    //  功能区一：屏幕限制解除
    // ============================================================
    public static void hookWindowFlags(ClassLoader classLoader) {

        // 1) Window.setFlags / addFlags
        try {
            Class<?> window = XposedHelpers.findClass("android.view.Window", classLoader);

            XposedHelpers.findAndHookMethod(window, "setFlags", int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[0];
                            if ((flags & FLAG_SECURE) != 0) {
                                param.args[0] = flags & ~FLAG_SECURE;
                                ReaLog.log("Window", "屏幕限制解除：生效(Window.setFlags)");
                            }
                        } catch (Throwable t) {
                        }
                    }
                });

            XposedHelpers.findAndHookMethod(window, "addFlags", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[0];
                            int cleaned = flags;
                            if ((cleaned & FLAG_SECURE) != 0) {
                                cleaned &= ~FLAG_SECURE;
                                ReaLog.log("Window", "屏幕限制解除：生效(Window.addFlags)");
                            }
                            if (cleaned != flags) param.args[0] = cleaned;
                        } catch (Throwable t) {
                        }
                    }
                });
        } catch (Throwable ignored) {
        }

        // 2) WindowManager.LayoutParams 层
        try {
            Class<?> lp = XposedHelpers.findClass("android.view.WindowManager$LayoutParams", classLoader);
            XposedHelpers.findAndHookMethod(lp, "setFlags", int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[0];
                            if ((flags & FLAG_SECURE) != 0) {
                                param.args[0] = flags & ~FLAG_SECURE;
                                ReaLog.log("Window", "屏幕限制解除：生效(LayoutParams.setFlags)");
                            }
                        } catch (Throwable t) {
                        }
                    }
                });
        } catch (Throwable ignored) {
        }

        // 3) Activity.setContentView 后通过 window 之外路径设置的
        try {
            final Class<?> viewRoot = XposedHelpers.findClassIfExists("android.view.ViewRootImpl", classLoader);
            if (viewRoot != null) {
                XposedHelpers.findAndHookMethod(viewRoot, "requestLayout",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object attrs = getViewRootWindowAttrs(param.thisObject);
                                if (attrs == null || sWindowAttrsFlagsField == null) return;
                                int flags = sWindowAttrsFlagsField.getInt(attrs);
                                int cleaned = flags;
                                if ((cleaned & FLAG_SECURE) != 0) {
                                    cleaned &= ~FLAG_SECURE;
                                    ReaLog.log("Window", "屏幕限制解除：生效(ViewRootImpl.requestLayout)");
                                }
                                if (cleaned != flags) sWindowAttrsFlagsField.setInt(attrs, cleaned);
                            } catch (Throwable t) {
                            }
                        }
                    });
            }
        } catch (Throwable ignored) {
        }

        // 4) Surface 截图层：强制允许捕获安全层（S~T / SDK 31~33）
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT <= 33) {
            // 4a) nativeCaptureDisplay / nativeCaptureLayers：native 方法可能有重载，按方法名挂全部
            try {
                Class<?> surfaceControl = XposedHelpers.findClassIfExists("android.view.SurfaceControl", classLoader);
                Class<?> captureArgs = XposedHelpers.findClassIfExists("android.view.SurfaceControl$CaptureArgs", classLoader);
                if (surfaceControl != null && captureArgs != null) {
                    java.lang.reflect.Field secureField = null;
                    try {
                        secureField = captureArgs.getDeclaredField("mCaptureSecureLayers");
                        secureField.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    if (secureField != null) {
                        final java.lang.reflect.Field fSecure = secureField;
                        final Class<?> argsType = captureArgs;
                        for (java.lang.reflect.Method m : surfaceControl.getDeclaredMethods()) {
                            final String name = m.getName();
                            if (!"nativeCaptureDisplay".equals(name) && !"nativeCaptureLayers".equals(name)) {
                                continue;
                            }
                            try {
                                m.setAccessible(true);
                                XposedBridge.hookMethod(m, new XC_MethodHook() {
                                        @Override
                                        protected void beforeHookedMethod(MethodHookParam param) {
                                            try {
                                                for (Object arg : param.args) {
                                                    if (argsType.isInstance(arg)) {
                                                        if (!Boolean.TRUE.equals(fSecure.get(arg))) {
                                                            fSecure.set(arg, true);
                                                            ReaLog.log("Window", "屏幕限制解除：生效(SurfaceControl." + name + ")");
                                                        }
                                                        break;
                                                    }
                                                }
                                            } catch (Throwable t) {
                                            }
                                        }
                                    });
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }

            // 4b) ScreenshotHardwareBuffer.containsSecureLayers：直接判定无安全层
            try {
                Class<?> shb = XposedHelpers.findClassIfExists("android.view.SurfaceControl$ScreenshotHardwareBuffer", classLoader);
                if (shb != null) {
                    for (java.lang.reflect.Method m : shb.getDeclaredMethods()) {
                        if (!"containsSecureLayers".equals(m.getName())) continue;
                        try {
                            m.setAccessible(true);
                            XposedBridge.hookMethod(m, XC_MethodReplacement.returnConstant(false));
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 5) 全部窗口的最后一站覆盖
        if (!sWindowGlobalHookInstalled) {
            try {
                Class<?> wmg = XposedHelpers.findClassIfExists("android.view.WindowManagerGlobal", classLoader);
                if (wmg != null) {
                    XC_MethodHook stripSecureLp = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            stripSecureFromArgs(param.args);
                        }
                    };
                    try {
                        XposedBridge.hookAllMethods(wmg, "addView", stripSecureLp);
                    } catch (Throwable ignored) {
                    }
                    try {
                        XposedBridge.hookAllMethods(wmg, "updateViewLayout", stripSecureLp);
                    } catch (Throwable ignored) {
                    }
                    sWindowGlobalHookInstalled = true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 6) SurfaceView.setSecure(true)：视频/直播/DRM 播放器给 SurfaceView 单打安全标记
        if (!sSurfaceViewHookInstalled) {
            try {
                Class<?> surfaceView = XposedHelpers.findClassIfExists("android.view.SurfaceView", classLoader);
                if (surfaceView != null) {
                    XposedHelpers.findAndHookMethod(surfaceView, "setSecure", boolean.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    if (Boolean.TRUE.equals(param.args[0])) {
                                        param.args[0] = Boolean.FALSE;
                                        ReaLog.log("Window", "屏幕限制解除：生效(SurfaceView.setSecure)");
                                    }
                                } catch (Throwable t) {
                                }
                            }
                        });
                    sSurfaceViewHookInstalled = true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 7) 源头阻断
        if (!sTxnBlockHookInstalled) {
            try {
                Class<?> txnClass = XposedHelpers.findClassIfExists(
                    "android.view.SurfaceControl$Transaction", classLoader);
                if (txnClass != null) {
                    XC_MethodHook blockSecureTrue = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                Object[] args = param.args;
                                if (args == null || args.length == 0) return;
                                Object last = args[args.length - 1];
                                if (!(last instanceof Boolean)) return;
                                if (!((Boolean) last).booleanValue()) return;
                                param.setResult(param.thisObject);
                                ReaLog.log("Window", "屏幕限制解除：生效(安全标记事务已拦截)");
                            } catch (Throwable t) {
                            }
                        }
                    };
                    try {
                        XposedBridge.hookAllMethods(txnClass, "setSecure", blockSecureTrue);
                    } catch (Throwable ignored) {
                    }
                    try {
                        XposedBridge.hookAllMethods(txnClass, "setSkipScreenshot", blockSecureTrue);
                    } catch (Throwable ignored) {
                    }
                    sTxnBlockHookInstalled = true;
                }
            } catch (Throwable ignored) {
            }
        }

        // 8) 全版本兜底
        hookSurfaceAntiSecure(classLoader);
    }

    // ============================================================
    //  屏幕限制解除 - 辅助方法
    // ============================================================
    /** 扫描被 hook 方法的实参，把窗口 LayoutParams 的 FLAG_SECURE 位清掉（WindowManagerGlobal 路径） */
    private static void stripSecureFromArgs(Object[] args) {
        if (args == null) return;
        try {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (!(arg instanceof WindowManager.LayoutParams)) continue;
                WindowManager.LayoutParams lp = (WindowManager.LayoutParams) arg;
                if ((lp.flags & FLAG_SECURE) == 0) continue;
                lp.flags &= ~FLAG_SECURE;
                ReaLog.log("Window", "屏幕限制解除：生效(WindowManagerGlobal)");
            }
        } catch (Throwable ignored) {
        }
    }

    /** 挂 ViewRootImpl.setView/relayoutWindow 后置解除；幂等，防重复调度导致事务叠加 */
    private static void hookSurfaceAntiSecure(ClassLoader classLoader) {
        if (sAntiSecureHooksInstalled) return;
        try {
            Class<?> viewRoot = XposedHelpers.findClassIfExists("android.view.ViewRootImpl", classLoader);
            if (viewRoot == null) return;
            if (!ensureSurfaceReflect(classLoader)) return;
            XC_MethodHook afterApply = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    applyAntiSecure(param.thisObject);
                }
            };
            try {
                XposedBridge.hookAllMethods(viewRoot, "setView", afterApply);
            } catch (Throwable ignored) {
            }
            try {
                XposedBridge.hookAllMethods(viewRoot, "relayoutWindow", afterApply);
            } catch (Throwable ignored) {
            }
            sAntiSecureHooksInstalled = true;
        } catch (Throwable ignored) {
        }
    }

    /** 反射元数据一次性初始化（懒加载 + 双重检查，失败下次调用可重试） */
    private static boolean ensureSurfaceReflect(ClassLoader classLoader) {
        if (sTxnCtor != null && sVriSurfaceField != null) return true;
        synchronized (sSurfaceLock) {
            if (sTxnCtor != null && sVriSurfaceField != null) return true;
            try {
                Class<?> viewRoot = XposedHelpers.findClassIfExists("android.view.ViewRootImpl", classLoader);
                if (viewRoot == null) return false;
                // VRI 持有 SurfaceControl 的字段名各版本不一，按兼容性从高到低逐个试
                String[] names = {"mSurfaceControl", "mSurface", "mLeash", "mSurfaceControlLocked"};
                for (int i = 0; i < names.length && sVriSurfaceField == null; i++) {
                    try {
                        java.lang.reflect.Field f = viewRoot.getDeclaredField(names[i]);
                        f.setAccessible(true);
                        sVriSurfaceField = f;
                    } catch (Throwable ignored) {
                    }
                }
                sScClass = XposedHelpers.findClassIfExists("android.view.SurfaceControl", classLoader);
                if (sScClass != null) {
                    try {
                        java.lang.reflect.Method m = sScClass.getDeclaredMethod("isValid");
                        m.setAccessible(true);
                        sScIsValid = m;
                    } catch (Throwable ignored) {
                    }
                }
                Class<?> txnClass = XposedHelpers.findClassIfExists(
                    "android.view.SurfaceControl$Transaction", classLoader);
                if (txnClass != null) {
                    try {
                        java.lang.reflect.Constructor<?> c = txnClass.getDeclaredConstructor();
                        c.setAccessible(true);
                        sTxnCtor = c;
                    } catch (Throwable ignored) {
                    }
                    sTxnApply = findMethodQuiet(txnClass, "apply");
                    sTxnClose = findMethodQuiet(txnClass, "close");
                    sTxnSetSecure = findBooleanTailMethodQuiet(txnClass, "setSecure", sScClass);
                    sTxnSetSkipScreenshot = findBooleanTailMethodQuiet(txnClass, "setSkipScreenshot", sScClass);
                }
            } catch (Throwable ignored) {
            }
            return sTxnCtor != null && sVriSurfaceField != null;
        }
    }

    private static java.lang.reflect.Method findMethodQuiet(Class<?> cls, String name) {
        try {
            java.lang.reflect.Method m = cls.getDeclaredMethod(name);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 取 (首参 firstParam, 末位 boolean) 的重载；取不到再退化为 (boolean) 单参重载（老版本/厂商变体） */
    private static java.lang.reflect.Method findBooleanTailMethodQuiet(Class<?> cls, String name,
                                                                       Class<?> firstParam) {
        if (firstParam != null) {
            try {
                java.lang.reflect.Method m = cls.getDeclaredMethod(name, firstParam, boolean.class);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
            }
        }
        try {
            java.lang.reflect.Method m = cls.getDeclaredMethod(name, boolean.class);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 类型与有效性校验：只放行真正的 SurfaceControl 且 isValid()，避免对无效 surface 下发事务 */
    private static Object getValidSurface(Object viewRoot) {
        try {
            if (sVriSurfaceField == null) return null;
            Object sc = sVriSurfaceField.get(viewRoot);
            if (sc == null) return null;
            if (sScClass != null && !sScClass.isInstance(sc)) return null;
            if (sScIsValid != null) {
                Object valid = sScIsValid.invoke(sc);
                if (!Boolean.TRUE.equals(valid)) return null;
            }
            return sc;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 把窗口自身 SurfaceControl 的 secure / skipScreenshot 清成 false；同一 surface 只下发一次 */
    private static void applyAntiSecure(Object viewRoot) {
        try {
            if (viewRoot == null || sTxnCtor == null || sVriSurfaceField == null) return;
            Object raw;
            try {
                raw = sVriSurfaceField.get(viewRoot);
            } catch (Throwable t) {
                return;
            }
            if (raw == null) return;
            synchronized (sAntiSecureApplied) {
                if (sAntiSecureApplied.get(viewRoot) == raw) return;
            }
            Object sc = getValidSurface(viewRoot);
            if (sc == null) return;
            Object txn = null;
            boolean applied = false;
            try {
                txn = sTxnCtor.newInstance();
                if (sTxnSetSecure != null) {
                    try {
                        sTxnSetSecure.invoke(txn, sc, Boolean.FALSE);
                        applied = true;
                    } catch (Throwable ignored) {
                    }
                }
                if (sTxnSetSkipScreenshot != null) {
                    try {
                        sTxnSetSkipScreenshot.invoke(txn, sc, Boolean.FALSE);
                        applied = true;
                    } catch (Throwable ignored) {
                    }
                }
                if (applied && sTxnApply != null) {
                    sTxnApply.invoke(txn);
                    synchronized (sAntiSecureApplied) {
                        sAntiSecureApplied.put(viewRoot, sc);
                    }
                }
            } finally {
                if (txn != null && sTxnClose != null) {
                    try {
                        sTxnClose.invoke(txn);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ============================================================
    //  功能区二：对话框强制取消
    // ============================================================
    /** 给我们自己弹出的对话框打跳过标记（在 show 之前调用） */
    public static void markOurs(android.app.Dialog dialog) {
        try {
            if (dialog != null && dialog.getWindow() != null && dialog.getWindow().getDecorView() != null) {
                dialog.getWindow().getDecorView().setTag(SKIP_DIALOG_TAG);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 判断对话框是否为我们自己弹出（各强制 hook 在此为 true 时原样放行） */
    public static boolean isOurs(Object dialog) {
        if (!(dialog instanceof android.app.Dialog)) return false;
        try {
            Object tag = ((android.app.Dialog) dialog).getWindow().getDecorView().getTag();
            return SKIP_DIALOG_TAG.equals(tag);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void hookDialogCancelableMethods(ClassLoader classLoader) {
        // 0) Window.setFlags / addFlags
        try {
            Class<?> window = XposedHelpers.findClass("android.view.Window", classLoader);

            XposedHelpers.findAndHookMethod(window, "setFlags", int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[0];
                            int mask = (int) param.args[1];
                            int target = FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE;
                            if ((flags & mask & target) != 0) {
                                param.args[1] = mask & ~target;
                            }
                        } catch (Throwable t) {
                        }
                    }
                });

            XposedHelpers.findAndHookMethod(window, "addFlags", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[0];
                            int cleaned = flags;
                            if ((cleaned & (FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE)) != 0) {
                                cleaned &= ~(FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE);
                            }
                            if (cleaned != flags) param.args[0] = cleaned;
                        } catch (Throwable t) {
                        }
                    }
                });
        } catch (Throwable ignored) {
        }

        // 0b) WindowManager.LayoutParams 层
        try {
            Class<?> lp = XposedHelpers.findClass("android.view.WindowManager$LayoutParams", classLoader);
            XposedHelpers.findAndHookMethod(lp, "setFlags", int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[0];
                            int mask = (int) param.args[1];
                            int target = FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE;
                            // 仅当应用要把目标位置 1 时才拦；为 0 时放行
                            if ((flags & mask & target) != 0) {
                                param.args[1] = mask & ~target;
                            }
                        } catch (Throwable t) {
                        }
                    }
                });
            XposedHelpers.findAndHookMethod(lp, "update", int.class, int.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            int flags = (int) param.args[1];
                            int cleaned = flags;
                            if ((cleaned & (FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE)) != 0) {
                                cleaned &= ~(FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE);
                            }
                            if (cleaned != flags) param.args[1] = cleaned;
                        } catch (Throwable t) {
                        }
                    }
                });
        } catch (Throwable ignored) {
        }

        // ViewRootImpl.requestLayout
        try {
            final Class<?> viewRoot = XposedHelpers.findClassIfExists("android.view.ViewRootImpl", classLoader);
            if (viewRoot != null) {
                XposedHelpers.findAndHookMethod(viewRoot, "requestLayout",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (findShowingCancelableDialog() == null) {
                                    restoreWindowFlags();
                                    return;
                                }
                                Object attrs = getViewRootWindowAttrs(param.thisObject);
                                if (attrs == null || sWindowAttrsFlagsField == null) return;
                                int flags = sWindowAttrsFlagsField.getInt(attrs);
                                int target = FLAG_NOT_TOUCHABLE | FLAG_NOT_FOCUSABLE;
                                if ((flags & target) == 0) return;   // 本来就没有，不动
                                // 记账：只记第一次（同窗口多次 requestLayout 不覆盖原值）
                                synchronized (sWindowFlagRestoreMap) {
                                    if (!sWindowFlagRestoreMap.containsKey(param.thisObject)) {
                                        sWindowFlagRestoreMap.put(param.thisObject, flags);
                                    }
                                }
                                sWindowAttrsFlagsField.setInt(attrs, flags & ~target);
                            } catch (Throwable t) {
                            }
                        }
                    });
            }
        } catch (Throwable ignored) {
        }

        // Dialog dismiss 时主动触发一次还原
        try {
            XposedHelpers.findAndHookMethod("android.app.Dialog", classLoader, "dismiss", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (isOurs(param.thisObject)) return;
                            if (findShowingCancelableDialog() == null) {
                                restoreWindowFlags();
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });
        } catch (Throwable ignored) {
        }

        try {
            XposedHelpers.findAndHookMethod("android.app.Dialog", classLoader, "setCancelable", boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isOurs(param.thisObject)) return;
                        param.args[0] = true;
                    }
                });

            XposedHelpers.findAndHookMethod("android.app.Dialog", classLoader, "setCanceledOnTouchOutside", boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isOurs(param.thisObject)) return;
                        param.args[0] = true;
                    }
                });

            XposedHelpers.findAndHookMethod("android.app.Dialog", classLoader, "show", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isOurs(param.thisObject)) return;
                        try {
                            Object dialog = param.thisObject;
                            // 先读 mCancelable 原值；遍历父类链以兼容多层继承
                            boolean wasNotCancelable = false;
                            Class<?> cls = dialog.getClass();
                            while (cls != null && cls != Object.class) {
                                try {
                                    java.lang.reflect.Field f = cls.getDeclaredField("mCancelable");
                                    f.setAccessible(true);
                                    Object cur = f.get(dialog);
                                    wasNotCancelable = cur != null && !(Boolean) cur;
                                    break;
                                } catch (NoSuchFieldException ignored) {
                                    cls = cls.getSuperclass();
                                }
                            }
                            XposedHelpers.callMethod(dialog, "setCancelable", true);
                            XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                            synchronized (sShowingCancelableDialogs) {
                                sShowingCancelableDialogs.add(dialog);
                            }
                            if (wasNotCancelable) {
                                ReaLog.log("dialog", "对话框强制取消：生效" + dialog.getClass().getName());
                            }
                            try {
                                java.lang.reflect.Field fCancel = dialog.getClass().getSuperclass().getDeclaredField("mCancelable");
                                fCancel.setAccessible(true);
                                fCancel.set(dialog, true);
                                java.lang.reflect.Field fTouch = dialog.getClass().getSuperclass().getDeclaredField("mCanceledOnTouchOutside");
                                fTouch.setAccessible(true);
                                fTouch.set(dialog, true);
                            } catch (Throwable ignored) {
                            }
                        } catch (Throwable t) {
                            ReaLog.log("dialog", "强制对话框设置异常: " + t.getMessage());
                        }
                    }
                });

            // dismiss 时从跟踪列表移除，避免强引用已销毁 Dialog
            XposedHelpers.findAndHookMethod("android.app.Dialog", classLoader, "dismiss", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object dialog = param.thisObject;
                            synchronized (sShowingCancelableDialogs) {
                                sShowingCancelableDialogs.remove(dialog);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                });

            XposedHelpers.findAndHookMethod("android.app.AlertDialog$Builder", classLoader, "create", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object dialog = param.getResult();
                            if (dialog != null) {
                                XposedHelpers.callMethod(dialog, "setCancelable", true);
                                XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                            }
                        } catch (Throwable t) {
                            ReaLog.log("dialog", "对话框方法create异常: " + t.getMessage());
                        }
                    }
                });

            try {
                XposedHelpers.findAndHookMethod("android.app.ProgressDialog", classLoader, "setCancelable", boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param)
                        throws Throwable {
                            if (isOurs(param.thisObject)) return;
                            param.args[0] = true;
                        }
                    });

                //hook 兜底
                XposedHelpers.findAndHookMethod("android.app.ProgressDialog", classLoader, "show",
                    android.content.Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object pd = param.getResult();
                                if (pd != null) {
                                    XposedHelpers.callMethod(pd, "setCancelable", true);
                                    XposedHelpers.callMethod(pd, "setCanceledOnTouchOutside", true);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            } catch (Throwable ignored) {
            }

            // ===== 扩展类型 A 层：override 基类或独立 Builder 路径的弹窗 =====
            try {
                Class<?> bsFragClass = XposedHelpers.findClassIfExists(
                    "com.google.android.material.bottomsheet.BottomSheetDialogFragment", classLoader);
                if (bsFragClass != null) {
                    XposedHelpers.findAndHookMethod(bsFragClass, "onCreateDialog",
                        android.os.Bundle.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    Object dialog = param.getResult();
                                    if (dialog != null) {
                                        XposedHelpers.callMethod(dialog, "setCancelable", true);
                                        XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                                    }
                                } catch (Throwable t) {
                                    ReaLog.log("dialog", "对话框方法异常: " + t.getMessage());
                                }
                            }
                        });
                }
            } catch (Throwable ignored) {
            }

            // androidx AlertDialog$Builder（独立于 android.app.AlertDialog$Builder 的构造路径）
            try {
                XposedHelpers.findAndHookMethod("androidx.appcompat.app.AlertDialog$Builder",
                    classLoader, "create", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object dialog = param.getResult();
                                if (dialog != null) {
                                    XposedHelpers.callMethod(dialog, "setCancelable", true);
                                    XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                                }
                            } catch (Throwable t) {
                                ReaLog.log("dialog", "AppCompat对话框方法异常: " + t.getMessage());
                            }
                        }
                    });
            } catch (Throwable ignored) {
            }

            // MaterialAlertDialogBuilder（Material Components 的构造路径）
            try {
                XposedHelpers.findAndHookMethod("com.google.android.material.dialog.MaterialAlertDialogBuilder",
                    classLoader, "create", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object dialog = param.getResult();
                                if (dialog != null) {
                                    XposedHelpers.callMethod(dialog, "setCancelable", true);
                                    XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                                }
                            } catch (Throwable t) {
                                ReaLog.log("dialog", "Material对话框方法异常: " + t.getMessage());
                            }
                        }
                    });
            } catch (Throwable ignored) {
            }

            // ===== 扩展类型 B 层：非 Dialog 的弹层 =====
            try {
                Class<?> pwClass = XposedHelpers.findClassIfExists("android.widget.PopupWindow", classLoader);
                if (pwClass != null) {
                    XC_MethodHook forceOutside = new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                XposedHelpers.callMethod(param.thisObject, "setOutsideTouchable", true);
                            } catch (Throwable ignored) {
                            }
                        }
                    };
                    XposedHelpers.findAndHookMethod(pwClass, "showAsDropDown",
                                                    android.view.View.class, forceOutside);
                    XposedHelpers.findAndHookMethod(pwClass, "showAsDropDown",
                                                    android.view.View.class, int.class, int.class, forceOutside);
                    try {
                        XposedHelpers.findAndHookMethod(pwClass, "showAsDropDown",
                                                        android.view.View.class, int.class, int.class, int.class, forceOutside);
                    } catch (NoSuchMethodError ignored) {
                    }
                    XposedHelpers.findAndHookMethod(pwClass, "showAtLocation",
                                                    android.view.View.class, int.class, int.class, int.class, forceOutside);
                }
            } catch (Throwable ignored) {
            }

            // ListPopupWindow：常配合 setModal(true) 使用导致点外部不消失；
            try {
                Class<?> lpwClass = XposedHelpers.findClassIfExists("android.widget.ListPopupWindow", classLoader);
                if (lpwClass != null) {
                    XposedHelpers.findAndHookMethod(lpwClass, "show", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                try {
                                    XposedHelpers.callMethod(param.thisObject, "setModal", false);
                                } catch (Throwable ignored) {
                                }
                            }
                        });
                }
            } catch (Throwable ignored) {
            }

            // 对话框「点外部取消」
            try {
                Class<?> viewCls = XposedHelpers.findClass("android.view.View", classLoader);
                XposedHelpers.findAndHookMethod(viewCls, "dispatchTouchEvent", android.view.MotionEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                android.view.MotionEvent event = (android.view.MotionEvent) param.args[0];
                                if (event.getAction() != android.view.MotionEvent.ACTION_DOWN) return;
                                Object dialog = findShowingCancelableDialog();
                                if (dialog == null) return;
                                Object decorView = XposedHelpers.callMethod(dialog, "getWindow") != null
                                    ? XposedHelpers.callMethod(XposedHelpers.callMethod(dialog, "getWindow"), "getDecorView")
                                    : null;
                                if (decorView == null || decorView != param.thisObject) return;
                                int[] loc = new int[2];
                                decorView.getClass().getMethod("getLocationOnScreen", int[].class)
                                    .invoke(decorView, new Object[]{loc});
                                int w = (int) decorView.getClass().getMethod("getWidth").invoke(decorView);
                                int h = (int) decorView.getClass().getMethod("getHeight").invoke(decorView);
                                if (w <= 0 || h <= 0) return;
                                boolean inside = event.getRawX() >= loc[0]
                                    && event.getRawX() <= loc[0] + w
                                    && event.getRawY() >= loc[1]
                                    && event.getRawY() <= loc[1] + h;
                                if (!inside) {
                                    XposedHelpers.callMethod(dialog, "cancel");
                                    ReaLog.log("dialog", "对话框强制取消：生效(View.dispatchTouchEvent)");
                                    param.setResult(true);
                                }
                            } catch (Throwable t) {
                            }
                        }
                    });
            } catch (Throwable ignored) {
            }

            hookDialogFragmentMethods(classLoader);
            hookAndroidXDialogFragmentMethods(classLoader);
            ReaLog.log("dialog", "Hook对话框完成强制可取消");

        } catch (Throwable t) {
            ReaLog.log("system", "hook对话框强制取消异常: " + t.getMessage());
        }
    }

    // 取"当前正在显示且可取消的 Dialog"
    private static Object findShowingCancelableDialog() {
        synchronized (sShowingCancelableDialogs) {
            java.util.Iterator<Object> it = sShowingCancelableDialogs.iterator();
            while (it.hasNext()) {
                Object d = it.next();
                try {
                    if ((boolean) XposedHelpers.callMethod(d, "isShowing")
                        && (boolean) XposedHelpers.callMethod(d, "isCancelable")) {
                        return d;
                    }
                    it.remove(); // 已不显示/不可取消的顺带清理
                } catch (Throwable ignored) {
                    it.remove();
                }
            }
        }
        return null;
    }

    private static void hookDialogFragmentMethods(ClassLoader classLoader) {
        try {
            Class<
                ?> dialogFragmentClass = Class.forName("android.app.DialogFragment", false, classLoader);
            XposedHelpers.findAndHookMethod(dialogFragmentClass, "onCreateDialog", Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object dialog = param.getResult();
                            if (dialog != null) {
                                XposedHelpers.callMethod(dialog, "setCancelable", true);
                                XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                                Object dialogFragment = param.thisObject;
                                XposedHelpers.callMethod(dialogFragment, "setCancelable", true);
                            }
                        } catch (Throwable t) {
                            ReaLog.log("dialog", "Dialog异常: " + t.getMessage());
                        }
                    }
                });
            XposedHelpers.findAndHookMethod(dialogFragmentClass, "setCancelable", boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isOurs(param.thisObject)) return;
                        param.args[0] = true;
                    }
                });
            ReaLog.log("dialog", "原生Dialog完成");
        } catch (ClassNotFoundException e) {
            ReaLog.log("dialog", "原生Dialog类不存在，跳过");
        } catch (Throwable t) {
            ReaLog.log("dialog", "原生Dialog异常: " + t.getMessage());
        }
    }

    private static void hookAndroidXDialogFragmentMethods(ClassLoader classLoader) {
        try {
            Class<?> dialogFragmentClass = Class.forName(
                "androidx.fragment.app.DialogFragment",
                false,
                classLoader
            );
            XposedHelpers.findAndHookMethod(
                dialogFragmentClass,
                "onCreateDialog",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object dialog = param.getResult();
                            if (dialog != null) {
                                XposedHelpers.callMethod(dialog, "setCancelable", true);
                                XposedHelpers.callMethod(dialog, "setCanceledOnTouchOutside", true);
                                Object dialogFragment = param.thisObject;
                                XposedHelpers.callMethod(dialogFragment, "setCancelable", true);
                            }
                        } catch (Throwable t) {
                            ReaLog.log("dialog", "AndroidX Dialog异常: " + t.getMessage());
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                dialogFragmentClass,
                "setCancelable",
                boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[0] = true;
                    }
                }
            );
        } catch (ClassNotFoundException e) {
        } catch (Throwable t) {
            ReaLog.log("dialog", "AndroidXDialogFrag异常: " + t.getMessage());
        }
    }

    // ============================================================
    //  功能区二附录：对话框强制取消(防界面关闭)
    // ============================================================
    public static void hookAntiCancelFinish(ClassLoader classLoader) {
        // 1) 标记"当前正处在 Dialog.cancel 方法体内部"调用链判定标记已复位，从而被正确放行。
        try {
            XposedHelpers.findAndHookMethod("android.app.Dialog", classLoader, "cancel", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isOurs(param.thisObject)) return; // 我们的对话框取消不进入拦截上下文
                        sInDialogCancel.set(Boolean.TRUE);
                        sDialogCancelStartedAt = android.os.SystemClock.uptimeMillis();
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        sInDialogCancel.set(Boolean.FALSE);
                    }
                });
        } catch (Throwable ignored) {
        }

        // 2) 仅拦截 cancel 调用链内部连带的页面关闭请求
        final XC_MethodHook antiFinish = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    if (!Boolean.TRUE.equals(sInDialogCancel.get())) return;
                    if (android.os.SystemClock.uptimeMillis() - sDialogCancelStartedAt
                        > ANTI_FINISH_FALLBACK_MS) {
                        sInDialogCancel.set(Boolean.FALSE);
                        return;
                    }
                    ReaLog.log("dialog", "对话框取消(防界面关闭)：强制可取消生效");
                    param.setResult(null);
                } catch (Throwable ignored) {
                }
            }
        };
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "finish", antiFinish);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "finishAffinity", antiFinish);
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod("android.app.Activity", classLoader, "finishAndRemoveTask", antiFinish);
        } catch (Throwable ignored) {
        }
    }


    // ============================================================
    //  功能区三：返回键控制
    // ============================================================
    //targetPackage 为当前目标包名
    public static void hookActivityDefaultBack(final ClassLoader classLoader, final String targetPackage) {
        if (sBackHookInstalled) return;  // 幂等：防重复挂导致 onBackPressed handler 叠加
        sBackHookInstalled = true;
        Class<?> fragmentActivityClass = null;
        try {
            fragmentActivityClass = Class.forName("androidx.fragment.app.FragmentActivity");
        } catch (Throwable ignored) {
        }
        final Class<?> cachedFragmentActivityClass = fragmentActivityClass;

        class BackPressHandler {
            private long lastPressTime = 0;
            private static final long DEBOUNCE_MS = 200;

            boolean handleBefore(Activity activity, MethodHookParam param) {
                if (!HookInit.forceDefaultBackMap.getOrDefault(targetPackage, false)) {
                    return false;
                }

                long now = System.currentTimeMillis();
                if (now - lastPressTime < DEBOUNCE_MS) {
                    param.setResult(true);
                    return true;
                }
                lastPressTime = now;

                if (activity == null || activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    param.setResult(true);
                    return true;
                }

                if (cachedFragmentActivityClass != null && cachedFragmentActivityClass.isInstance(activity)) {
                    try {
                        Object fm = XposedHelpers.callMethod(activity, "getSupportFragmentManager");
                        int count = (int) XposedHelpers.callMethod(fm, "getBackStackEntryCount");
                        if (count > 0) {
                            XposedHelpers.callMethod(fm, "popBackStackImmediate");
                            param.setResult(true);
                            ReaLog.log("back_key", "返回键消费回退栈");
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }

                try {
                    Object nativeFm = XposedHelpers.callMethod(activity, "getFragmentManager");
                    if (nativeFm != null) {
                        int count = (int) XposedHelpers.callMethod(nativeFm, "getBackStackEntryCount");
                        if (count > 0) {
                            XposedHelpers.callMethod(nativeFm, "popBackStackImmediate");
                            param.setResult(true);
                            ReaLog.log("back_key", "返回键消费原生回退栈");
                            return true;
                        }
                    }
                } catch (Throwable ignored) {
                }
                return false;
            }

            void handleAfter(Activity activity) {
                if (!HookInit.forceDefaultBackMap.getOrDefault(targetPackage, false)) {
                    return;
                }

                if (activity == null || activity.isFinishing()
                    || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
                    return;
                }
                // 打标记：超级退出拦截（hookExitMethods）凭此放行返回键触发的 finish
                HookInit.markBackPressFinish(activity);
                activity.finish();
                ReaLog.log("back_key", "返回键触发 Activity.finish()");
            }
        }

        final BackPressHandler handler = new BackPressHandler();

        try {
            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                classLoader,
                "onBackPressed",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        handler.handleBefore(activity, param);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        handler.handleAfter(activity);
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "android.app.Activity",
                classLoader,
                "dispatchKeyEvent",
                android.view.KeyEvent.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        android.view.KeyEvent event = (android.view.KeyEvent) param.args[0];
                        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                            && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                            && !event.isLongPress()) {
                            Activity activity = (Activity) param.thisObject;
                            handler.handleBefore(activity, param);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        android.view.KeyEvent event = (android.view.KeyEvent) param.args[0];
                        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BACK
                            && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                            Activity activity = (Activity) param.thisObject;
                            handler.handleAfter(activity);
                        }
                    }
                }
            );

            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onKeyDown",
                    int.class,
                    android.view.KeyEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param)
                        throws Throwable {
                            int keyCode = (int) param.args[0];
                            android.view.KeyEvent event = (android.view.KeyEvent) param.args[1];
                            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                                && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                                && !event.isLongPress()) {
                                Activity activity = (Activity) param.thisObject;
                                handler.handleBefore(activity, param);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param)
                        throws Throwable {
                            int keyCode = (int) param.args[0];
                            android.view.KeyEvent event = (android.view.KeyEvent) param.args[1];
                            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                                && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                                Activity activity = (Activity) param.thisObject;
                                handler.handleAfter(activity);
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {
            }

            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onKeyUp",
                    int.class,
                    android.view.KeyEvent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param)
                        throws Throwable {
                            int keyCode = (int) param.args[0];
                            android.view.KeyEvent event = (android.view.KeyEvent) param.args[1];
                            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                                && event.getAction() == android.view.KeyEvent.ACTION_UP
                                && !event.isLongPress()) {
                                Activity activity = (Activity) param.thisObject;
                                handler.handleBefore(activity, param);
                            }
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param)
                        throws Throwable {
                            int keyCode = (int) param.args[0];
                            android.view.KeyEvent event = (android.view.KeyEvent) param.args[1];
                            if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                                && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                                Activity activity = (Activity) param.thisObject;
                                handler.handleAfter(activity);
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {
            }

            ReaLog.log("misc", "返回键控制已安装");
        } catch (Throwable t) {
            String msg = "返回键控制失败: " + t.getMessage();
            if (ReaLog.sHookInit != null) {
                ReaLog.sHookInit.log(msg);
            } else {
                try {
                    XposedBridge.log("[" + HookInit.MODULE_TAG + "] [" + targetPackage + "] " + msg);
                } catch (Throwable ignored) {
                }
            }
            ReaLog.log("misc", "返回键控制异常: " + t.getMessage());
        }
    }

    //  通用工具：密度换算与主线程切换
    public static float dp(Context context, float dp) {
        if (context == null) return dp;
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static int dpInt(Context context, float dp) {
        return (int) dp(context, dp);
    }

    // spx：按 xxhdpi(3x) 基准设计的旧 px 数值，自动适应当前屏幕密度
    public static int spx(Context context, float px) {
        if (context == null) return (int) px;
        return (int) (px / 3f * context.getResources().getDisplayMetrics().density);
    }

    //切到主线程执行；已在主线程则同步执行
    public static void runOnMainThread(Runnable r) {
        if (r == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }

    //主线程延时执行（不涉及 removeCallbacks 配对的单次延时任务用此）
    public static void postOnMainThread(Runnable r, long delayMs) {
        if (r == null) return;
        new Handler(Looper.getMainLooper()).postDelayed(r, delayMs);
    }

    // ==================== 震动控制 ====================
    // 门控在回调内实时读 Map：宿主与模块自身两套独立开关，默认均关闭（不改变现状）。
    // 模块调用按调用栈识别（类名以 com.install.appinstall.xl. 开头）。

    /** 拦截本进程内的震动调用：Vibrator 各重载 / VibratorManager（API31+）/ View.performHapticFeedback */
    public static void hookVibrator(ClassLoader classLoader) {
        XC_MethodHook swallow = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (shouldBlockVibrate()) param.setResult(null);
            }
        };
        // 1) Vibrator.vibrate 的四个重载
        try {
            Class<?> vib = XposedHelpers.findClassIfExists("android.os.Vibrator", classLoader);
            if (vib != null) {
                XposedHelpers.findAndHookMethod(vib, "vibrate", long.class, swallow);
                XposedHelpers.findAndHookMethod(vib, "vibrate", long[].class, int.class, swallow);
                Class<?> effect = XposedHelpers.findClassIfExists("android.os.VibrationEffect", classLoader);
                if (effect != null) {
                    XposedHelpers.findAndHookMethod(vib, "vibrate", effect, swallow);
                    Class<?> attrs = XposedHelpers.findClassIfExists("android.media.AudioAttributes", classLoader);
                    if (attrs != null) {
                        XposedHelpers.findAndHookMethod(vib, "vibrate", effect, attrs, swallow);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 2) VibratorManager（API31+）
        try {
            Class<?> vm = XposedHelpers.findClassIfExists("android.os.VibratorManager", classLoader);
            if (vm != null) {
                Class<?> effect = XposedHelpers.findClassIfExists("android.os.VibrationEffect", classLoader);
                if (effect != null) {
                    XposedHelpers.findAndHookMethod(vm, "vibrate", int.class, effect, swallow);
                    Class<?> vattrs = XposedHelpers.findClassIfExists("android.os.VibrationAttributes", classLoader);
                    if (vattrs != null) {
                        XposedHelpers.findAndHookMethod(vm, "vibrate", int.class, effect, vattrs, swallow);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 2b) SystemVibrator（API31+ 自己 override 了 vibrate 全系，绕过 Vibrator 父类的 hook）
        try {
            Class<?> sv = XposedHelpers.findClassIfExists("android.os.SystemVibrator", classLoader);
            if (sv != null) {
                XposedHelpers.findAndHookMethod(sv, "vibrate", long.class, swallow);
                XposedHelpers.findAndHookMethod(sv, "vibrate", long[].class, int.class, swallow);
                Class<?> effect2 = XposedHelpers.findClassIfExists("android.os.VibrationEffect", classLoader);
                if (effect2 != null) {
                    XposedHelpers.findAndHookMethod(sv, "vibrate", effect2, swallow);
                    Class<?> attrs2 = XposedHelpers.findClassIfExists("android.media.AudioAttributes", classLoader);
                    if (attrs2 != null) {
                        XposedHelpers.findAndHookMethod(sv, "vibrate", effect2, attrs2, swallow);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 2c) SystemVibratorManager（API33+ 同理）
        try {
            Class<?> svm = XposedHelpers.findClassIfExists("android.os.SystemVibratorManager", classLoader);
            if (svm != null) {
                Class<?> effect3 = XposedHelpers.findClassIfExists("android.os.VibrationEffect", classLoader);
                if (effect3 != null) {
                    XposedHelpers.findAndHookMethod(svm, "vibrate", int.class, effect3, swallow);
                    Class<?> vattrs2 = XposedHelpers.findClassIfExists("android.os.VibrationAttributes", classLoader);
                    if (vattrs2 != null) {
                        XposedHelpers.findAndHookMethod(svm, "vibrate", int.class, effect3, vattrs2, swallow);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // 3) View.performHapticFeedback（拿不到 Vibrator 时的回退路径）
        try {
            Class<?> view = XposedHelpers.findClassIfExists("android.view.View", classLoader);
            if (view != null) {
                XposedHelpers.findAndHookMethod(view, "performHapticFeedback", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (shouldBlockVibrate()) param.setResult(Boolean.FALSE);
                        }
                    });
                XposedHelpers.findAndHookMethod(view, "performHapticFeedback", int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (shouldBlockVibrate()) param.setResult(Boolean.FALSE);
                        }
                    });
            }
        } catch (Throwable ignored) {
        }
        ReaLog.log("vibrator", "震动控制已hook");
    }

    /** 是否拦截本次震动：单总开关。开=放行一切震动，关=拦截宿主与模块的全部震动；默认开保持现状 */
    private static boolean shouldBlockVibrate() {
        try {
            if (HookInit.hookVibratorMap.getOrDefault(currentPkg(), true)) return false;
            ReaLog.log("vibrator", "震动被拦截");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String currentPkg() {
        try {
            HookInit h = ReaLog.sHookInit;
            String p = h != null ? h.getCurrentTargetApp() : "";
            return p == null ? "" : p;
        } catch (Throwable t) {
            return "";
        }
    }
}

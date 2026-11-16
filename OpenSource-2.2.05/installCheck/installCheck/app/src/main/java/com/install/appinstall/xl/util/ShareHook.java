package com.install.appinstall.xl.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.install.appinstall.xl.HookInit;

public final class ShareHook {

    // 与 HookInit 的 shareFakeDetailMap 键一致
    public static final String TARGET_XQ = "XQ";
    public static final String TARGET_XW = "XW";
    public static final String TARGET_XB = "XB";
    public static final String TARGET_XD = "XD";
    /** 识别标识，whichShare 返回值。开关由 aggressive 统一控制 */
    public static final String TARGET_OTHER = "other";
    /** 激进模式开关 key */
    public static final String TARGET_AGGRESSIVE = "aggressive";

    /** UI 显示的开关 key/label*/
    public static final String[] UI_KEYS = {
        "XW", "XQ", "XD", "XB", "aggressive"
    };
    public static final String[] UI_LABELS = {
        "小微", "小Q", "小钉", "小搏", "激进"
    };

    // ============================================================
    //  静态开关字段 —— 改开关立即生效，无需重装 Hook
    // ============================================================
    public static volatile boolean sEnableXW     = false;
    public static volatile boolean sEnableXQ         = false;
    public static volatile boolean sEnableXB      = false;
    public static volatile boolean sEnableXD   = false;
    public static volatile boolean sEnableAggressive = false;

    public static void setEnabled(String target, boolean enabled) {
        if (TARGET_XQ.equals(target))                 sEnableXQ = enabled;
        else if (TARGET_XW.equals(target))        sEnableXW = enabled;
        else if (TARGET_XB.equals(target))         sEnableXB = enabled;
        else if (TARGET_XD.equals(target))      sEnableXD = enabled;
        else if (TARGET_OTHER.equals(target) || TARGET_AGGRESSIVE.equals(target)) {
            sEnableAggressive = enabled;
        }
    }

    public static void setAllEnabled(Map<String, Boolean> map) {
        if (map == null) return;
        sEnableXW     = Boolean.TRUE.equals(map.get(TARGET_XW));
        sEnableXQ         = Boolean.TRUE.equals(map.get(TARGET_XQ));
        sEnableXB      = Boolean.TRUE.equals(map.get(TARGET_XB));
        sEnableXD   = Boolean.TRUE.equals(map.get(TARGET_XD));
        sEnableAggressive = Boolean.TRUE.equals(map.get(TARGET_AGGRESSIVE));
    }

    public static boolean isEnabled(String target) {
        // 调试模式门控：假装分享被彻底禁用时，任意 target 均视为关闭
        if (!DebugModeManager.isFeatureEnabled("share_fake")) return false;

        // 每次判定均从 HookInit.shareFakeDetailMap 实时读取当前目标 App 的配置
        try {
            Map<String, Boolean> detailMap = null;
            if (ReaLog.sHookInit != null) {
                String currentApp = ReaLog.sHookInit.getCurrentTargetApp();
                if (currentApp != null && !currentApp.isEmpty()) {
                    detailMap = HookInit.shareFakeDetailMap.get(currentApp);
                }
            }
            if (detailMap != null && detailMap.containsKey(target)) {
                return detailMap.get(target);
            }
        } catch (Throwable ignored) {}

        // 未设置细项，回退到静态字段
        if (TARGET_XQ.equals(target))            return sEnableXQ;
        if (TARGET_XW.equals(target))        return sEnableXW;
        if (TARGET_XB.equals(target))         return sEnableXB;
        if (TARGET_XD.equals(target))      return sEnableXD;
        if (TARGET_OTHER.equals(target))         return sEnableAggressive;
        if (TARGET_AGGRESSIVE.equals(target))    return sEnableAggressive;
        return false;
    }

    // ============================================================
    //  错误计数 + 自动禁用
    // ============================================================
    // 错误计数委托 BuHook 统一熔断器（key 前缀 share:），请与 SHARE_TARGETS 保持同步
    private static final String[] SHARE_TARGETS = {
            TARGET_XQ, TARGET_XW, TARGET_XB, TARGET_XD, TARGET_OTHER, TARGET_AGGRESSIVE
    };
    private static final int ERROR_THRESHOLD = 3;
    private static volatile WeakReference<Object> sHookInstanceRef =
    new WeakReference<Object>(null);

    public interface BooleanProvider {
        boolean get();
    }

    private ShareHook() {
    }

    public static void setHookInstance(Object instance) {
        sHookInstanceRef = new WeakReference<Object>(instance);
    }

    private static void recordError(String target, Throwable t) {
        try {
            if (target == null) return;
            if (t == null) t = new Throwable("unknown");
            int count = BuHook.recordError("share:" + target, t, ERROR_THRESHOLD, 0L, false);
            if (count <= 0) return;
            ReaLog.log("share_fake", "假装分享异常[" + displayName(target) + "]: "
                       + t.getClass().getSimpleName() + ": " + t.getMessage()
                       + " (计数=" + count + "/" + ERROR_THRESHOLD + ")");
            if (count >= ERROR_THRESHOLD) {
                disableTarget(target);
                ReaLog.log("share_fake", "【错误】" + displayName(target)
                           + " 连续异常 " + count + " 次，已禁用该分享平台");
            }
        } catch (Throwable ignored) {
        }
    }

    private static void disableTarget(String target) {
        try {
            WeakReference<Object> ref = sHookInstanceRef;
            Object instance = (ref == null) ? null : ref.get();
            if (instance == null) {
                setEnabled(target, false);
                return;
            }

            Class<?> clazz = instance.getClass();

            Method getApp = clazz.getMethod("getCurrentTargetApp");
            String app = (String) getApp.invoke(instance);
            if (app == null || app.isEmpty()) {
                setEnabled(target, false);
                return;
            }

            Field field = clazz.getField("shareFakeDetailMap");
            Object rawMap = field.get(null);
            if (rawMap instanceof Map) {
                @SuppressWarnings("unchecked")
                    Map<String, Map<String, Boolean>> map =
                    (Map<String, Map<String, Boolean>>) rawMap;
                Map<String, Boolean> detail = map.get(app);
                if (detail == null) {
                    detail = new HashMap<String, Boolean>();
                    map.put(app, detail);
                }
                if (TARGET_OTHER.equals(target) || TARGET_AGGRESSIVE.equals(target)) {
                    detail.put(TARGET_AGGRESSIVE, false);
                } else {
                    detail.put(target, false);
                }
            }

            setEnabled(target, false);

            Method save = clazz.getMethod("saveConfigToFile");
            save.invoke(instance);
        } catch (Throwable ignored) {
        }
    }

    public static void resetAllErrorCount() {
        for (String t : SHARE_TARGETS) BuHook.clearErrorCount("share:" + t);
    }

    /** 当前各平台异常计数快照（界面读取展示用，反馈 m03459） */
    public static java.util.Map<String, Integer> errorCountSnapshot() {
        java.util.Map<String, Integer> m = new java.util.TreeMap<String, Integer>();
        for (String t : SHARE_TARGETS) {
            int c = BuHook.errorCount("share:" + t);
            if (c > 0) m.put(t, Integer.valueOf(c));
        }
        return m;
    }

    /** 显示名辅助 */
    public static String displayName(String target) {
        if (TARGET_XQ.equals(target))         return "小Q";
        if (TARGET_XW.equals(target))     return "小微";
        if (TARGET_XB.equals(target))      return "小搏";
        if (TARGET_XD.equals(target))   return "小钉";
        if (TARGET_OTHER.equals(target))      return "通用";
        if (TARGET_AGGRESSIVE.equals(target)) return "激进";
        return target == null ? "unknown" : target;
    }

    // ============================================================
    //  对外 Hook 入口（每平台独立幂等守卫：防重复挂载导致 handler 叠加）
    // ============================================================
    private static volatile boolean sWechatHooked = false;
    private static volatile boolean sXQHooked = false;
    private static volatile boolean sDingTalkHooked = false;
    private static volatile boolean sWeiboHooked = false;

    public static void hookWeChatShareOnly(ClassLoader cl, String targetPackage, BooleanProvider enabled) {
        if (!sWechatHooked) { XW.hook(cl, targetPackage, enabled); sWechatHooked = true; }
    }

    public static void hookXQShareOnly(ClassLoader cl, BooleanProvider enabled) {
        if (!sXQHooked) { XQ.hook(cl, enabled); sXQHooked = true; }
    }

    public static void hookDingTalkShareOnly(ClassLoader cl, BooleanProvider enabled) {
        if (!sDingTalkHooked) { XD.hook(cl, enabled); sDingTalkHooked = true; }
    }

    public static void hookWeiboShareOnly(ClassLoader cl, BooleanProvider enabled) {
        if (!sWeiboHooked) { XB.hook(cl, enabled); sWeiboHooked = true; }
    }

    // ============================================================
    //  对外识别
    // ============================================================
    public static boolean isXqShare(Intent it)      { return Detector.isXqShare(it); }
    public static boolean isXwShare(Intent it)  { return Detector.isXwShare(it); }
    public static boolean isXbShare(Intent it)   { return Detector.isXbShare(it); }
    public static boolean isXdShare(Intent it) { return Detector.isXdShare(it); }
    public static String  whichShare(Intent it)     { return Detector.whichShare(it); }

    // ============================================================
    //  主入口
    // ============================================================
    @Deprecated
    public static boolean handleShareIfNeeded(Activity activity, Intent realIntent, String callerPkg,
                                              Map<String, Boolean> shareDetailMap, ClassLoader classLoader) {
        return handleShareIfNeeded(activity, realIntent, callerPkg, -1, shareDetailMap, classLoader);
    }

    public static boolean handleShareIfNeeded(Activity activity, Intent realIntent, String callerPkg,
                                              int requestCode, Map<String, Boolean> shareDetailMap,
                                              ClassLoader classLoader) {
        if (activity == null || realIntent == null) return false;
        if (activity.isFinishing()) return false;

        String target = Detector.whichShare(realIntent);
        if (target == null) return false;

        if (!isEnabled(target)) return false;

        if (TARGET_XW.equals(target) && !XW.isShareCmd(realIntent)) {
            ReaLog.log("share_fake", "非小微分享参数，跳过处理");
            return false;
        }

        if (Util.isDuplicate(callerPkg, realIntent)) {
            ReaLog.log("share_fake", "重复分享已去重: " + callerPkg
                       + " target=" + displayName(target));
            if (requestCode > 0) {
                safeCallOnActivityResult(activity, requestCode, Activity.RESULT_OK, null);
            }
            return true;
        }

        boolean ok = false;

        if (TARGET_XQ.equals(target)) {
            ok = XQ.fakeByIntent(activity, realIntent, callerPkg);
            if (!ok) { ReaLog.log("share_fake", "小Q 回调Intent未成功(回退直调): " + (callerPkg == null ? "" : callerPkg)); XQ.fakeDirect(activity, classLoader); ok = true; }
        } else if (TARGET_XW.equals(target)) {
            ok = XW.fakeByIntent(activity, realIntent, callerPkg);
            if (!ok) { ReaLog.log("share_fake", "小微 回调Intent未成功(回退直调): " + (callerPkg == null ? "" : callerPkg)); XW.fakeDirect(activity, classLoader, callerPkg); ok = true; }
        } else if (TARGET_XB.equals(target)) {
            ok = XB.fakeByIntent(activity, realIntent, callerPkg);
            if (!ok) { ReaLog.log("share_fake", "小搏 回调Intent未成功(回退直调): " + (callerPkg == null ? "" : callerPkg)); XB.fakeDirect(activity, classLoader); ok = true; }
        } else if (TARGET_XD.equals(target)) {
            ReaLog.log("share_fake", "小钉(直调): " + (callerPkg == null ? "" : callerPkg));
            XD.fakeDirect(activity, classLoader);
            ok = true;
        } else if (TARGET_OTHER.equals(target)) {
            ReaLog.log("share_fake", "激进分享已处理: " + (callerPkg == null ? "" : callerPkg));
            Generic.pretend(activity, realIntent, callerPkg);
            ok = true;
        }
        if (ok && requestCode > 0) {
            safeCallOnActivityResult(activity, requestCode, Activity.RESULT_OK, null);
        }

        if (ok) {
            final Activity act = activity;
            More.postOnMainThread(new Runnable() {
                    @Override public void run() {
                        try {
                            if (act != null && !act.isFinishing()) {
                                ToastUtil.showUnique(act, "[激进]假装分享成功");
                            }
                        } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常: " + (ignored == null ? "" : ignored.getMessage())); }
                    }
                }, 300);
        }
        ReaLog.log("share_fake", "假装分享：渠道=" + displayName(target)
                   + " 调用方=" + callerPkg + " 回调=" + ok
                   + (requestCode > 0 ? " (结果链 req=" + requestCode + ")" : ""));
        return ok;
    }

    private static void safeCallOnActivityResult(final Activity activity,
                                                 final int requestCode,
                                                 final int resultCode,
                                                 final Intent data) {
        if (activity == null || requestCode <= 0) return;
        boolean success = false;
        try {
            XposedHelpers.callMethod(activity, "onActivityResult",
                                     requestCode, resultCode, data);
            success = true;
        } catch (Throwable ignored) {
            try {
                Method method = Activity.class.getDeclaredMethod("onActivityResult",
                                                                 int.class, int.class, Intent.class);
                method.setAccessible(true);
                method.invoke(activity, requestCode, resultCode, data);
                success = true;
            } catch (Throwable ignored2) {
            }
        }
        if (!success) {
            More.postOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Method method = Activity.class.getDeclaredMethod("onActivityResult",
                                                                             int.class, int.class, Intent.class);
                            method.setAccessible(true);
                            method.invoke(activity, requestCode, resultCode, data);
                        } catch (Throwable ignored) {
                        }
                    }
                }, 200);
        }
        ReaLog.log("share_fake", "手动触发 onActivityResult req=" + requestCode
                   + " result=" + resultCode + (success ? " (直调)" : " (延迟重试)"));
    }

    // ============================================================
    //  公共工具
    // ============================================================
    static final class Util {

        private static final Map<String, Long> sRecentShare = new ConcurrentHashMap<String, Long>();
        private static final long DEDUP_MS = 400;

        static boolean isDuplicate(String callerPkg, Intent it) {
            if (it == null) return false;
            String action = it.getAction() != null ? it.getAction() : "";
            String data = it.getData() != null ? it.getData().toString() : "";
            String comp = it.getComponent() != null ? it.getComponent().flattenToShortString() : "";
            String key = (callerPkg == null ? "" : callerPkg) + "|" + action + "|" + data + "|" + comp;

            long now = System.currentTimeMillis();
            Long last = sRecentShare.get(key);
            if (last != null && now - last < DEDUP_MS) return true;
            sRecentShare.put(key, now);

            if (sRecentShare.size() > 300) {
                sRecentShare.clear();
                sRecentShare.put(key, now);
            }
            return false;
        }

        static boolean safeSendCallback(Activity activity, Intent back,
                                        String callerPkg, String standardClass) {
            if (activity == null || activity.isFinishing()) return false;
            if (callerPkg == null || callerPkg.isEmpty()) return false;

            PackageManager pm = activity.getPackageManager();

            if (standardClass != null && !standardClass.isEmpty()) {
                ComponentName std = new ComponentName(callerPkg, standardClass);
                boolean exists = false;
                try { pm.getActivityInfo(std, 0); exists = true; } catch (PackageManager.NameNotFoundException e) { exists = false; } catch (Throwable ignored) { exists = false; }
                if (exists) {
                    try {
                        back.setComponent(std);
                        activity.startActivity(back);
                        ReaLog.log("share_fake", "假装分享：回调成功(标准类): " + std.flattenToShortString());
                        return true;
                    } catch (Throwable t) {
                        ReaLog.log("share_fake", "假装分享：标准类发送失败: " + t.getMessage());
                    }
                } else {
                    ReaLog.log("share_fake", "假装分享：标准类不存在，尝试查询: " + standardClass);
                }
            }

            try {
                Intent probe = new Intent(back);
                probe.setComponent(null);
                probe.setPackage(callerPkg);
                List<ResolveInfo> ris = pm.queryIntentActivities(probe, 0);
                if (ris != null && !ris.isEmpty()) {
                    for (int i = 0; i < ris.size(); i++) {
                        ResolveInfo ri = ris.get(i);
                        if (ri == null || ri.activityInfo == null) continue;
                        if (!ri.activityInfo.exported) continue;
                        ComponentName cn = new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name);
                        try {
                            back.setComponent(cn);
                            activity.startActivity(back);
                            ReaLog.log("share_fake", "假装分享：回调成功(查询命中): " + cn.flattenToShortString());
                            return true;
                        } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
                    }
                }
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }

            try {
                back.setComponent(null);
                back.setPackage(callerPkg);
                activity.startActivity(back);
                ReaLog.log("share_fake", "假装分享：回调成功(包名降级): " + callerPkg);
                return true;
            } catch (Throwable t) {
                ReaLog.log("share_fake", "假装分享：包名降级失败: " + t.getMessage());
            }

            try {
                back.setComponent(null);
                back.setPackage(null);
                activity.startActivity(back);
                ReaLog.log("share_fake", "假装分享：回调成功(裸发兜底)");
                return true;
            } catch (Throwable t) {
                ReaLog.log("share_fake", "假装分享：所有方式均失败: " + t.getMessage());
                return false;
            }
        }

        static List<Activity> getAllActivities() {
            List<Activity> result = new ArrayList<Activity>();
            try {
                Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
                Object activityThread = XposedHelpers.callStaticMethod(activityThreadClass, "currentActivityThread");
                Object activitiesMap = XposedHelpers.getObjectField(activityThread, "mActivities");
                if (activitiesMap == null) return result;
                Collection<?> values = ((Map<?, ?>) activitiesMap).values();
                for (Object record : values) {
                    if (record == null) continue;
                    Activity activity = (Activity) XposedHelpers.getObjectField(record, "activity");
                    if (activity != null && !activity.isFinishing()) result.add(activity);
                }
            } catch (Throwable t) {
            }
            return result;
        }

        static Context getCurrentActivityContext() {
            List<Activity> list = getAllActivities();
            return list.isEmpty() ? null : list.get(0);
        }

        static void showToast(final String message) {
            Context ctx = getCurrentActivityContext();
            if (ctx != null) ToastUtil.show(ctx, message);
        }

        static String genTransaction() {
            long now = System.currentTimeMillis();
            int a = (int) (Math.random() * 10000);
            int b = (int) (Math.random() * 9999999);
            return now + "#sep#" + a + "#sep#" + b + "#sep#" + now;
        }

        static boolean isPkgIn(String p, String[] arr) {
            if (p == null) return false;
            for (int i = 0; i < arr.length; i++) {
                if (arr[i].equals(p)) return true;
            }
            return false;
        }

        static int safeIntExtra(Intent it, String key, int def) {
            try { return it.getIntExtra(key, def); } catch (Throwable t) { return def; }
        }
    }

    // ============================================================
    //  分享识别器
    // ============================================================
    static final class Detector {

        static boolean isXqShare(Intent it)      { return TARGET_XQ.equals(whichShare(it)); }
        static boolean isXwShare(Intent it)  { return TARGET_XW.equals(whichShare(it)); }
        static boolean isXbShare(Intent it)   { return TARGET_XB.equals(whichShare(it)); }
        static boolean isXdShare(Intent it) { return TARGET_XD.equals(whichShare(it)); }

        static String whichShare(Intent it) {
            if (it == null) return null;

            String scheme = it.getScheme();
            Uri data = it.getData();
            if (scheme != null) {
                if ("mqqapi".equals(scheme) || "mqq".equals(scheme)) {
                    String host = data != null ? data.getHost() : null;
                    String path = data != null ? data.getPath() : null;
                    if (host != null && (host.contains("share") || host.contains("to_fri")
                        || host.contains("to_qzone"))) return TARGET_XQ;
                    if (path != null && (path.contains("share") || path.contains("to_fri")
                        || path.contains("to_qzone"))) return TARGET_XQ;
                }
                if ("weixin".equals(scheme)) {
                    int cmd = Util.safeIntExtra(it, "_wxapi_command_type", -1);
                    if (cmd == 2) return TARGET_XW;
                }
                if ("sinaweibo".equals(scheme)) {
                    String host = data != null ? data.getHost() : null;
                    String path = data != null ? data.getPath() : null;
                    boolean isShare = (host != null && (host.contains("sendweibo") || host.contains("share")))
                        || (path != null && (path.contains("sendweibo") || path.contains("share")));
                    if (isShare) return TARGET_XB;
                }
                if ("dingtalk".equals(scheme)) {
                    String host = data != null ? data.getHost() : null;
                    String path = data != null ? data.getPath() : null;
                    boolean isSharePath = (host != null && (host.contains("send") || host.contains("share")))
                        || (path != null && (path.contains("send") || path.contains("share")));
                    if (isSharePath) return TARGET_XD;
                }
                if ("ddshare".equals(scheme)) return TARGET_XD;
            }

            ComponentName cn = it.getComponent();
            if (cn != null && cn.getPackageName() != null) {
                String p = cn.getPackageName();
                String c = cn.getClassName();
                if (XW.PKG_WX.equals(p)) {
                    if (XW.CLS_WX_ENTRY.equals(c)) {
                        int cmd = Util.safeIntExtra(it, "_wxapi_command_type", -1);
                        if (cmd == 2) return TARGET_XW;
                    }
                    return null;
                }
                if (Util.isPkgIn(p, XQ.PKG_XQ_ARR)) {
                    if (c != null && (c.contains("AuthActivity") || c.contains("Share") || c.contains("share"))) {
                        return TARGET_XQ;
                    }
                    return null;
                }
                if (XB.PKG_WB.equals(p)) {
                    if (c != null && c.contains("WbShareTransActivity")) {
                        return TARGET_XB;
                    }
                    return null;
                }
                if (XD.PKG_DD.equals(p)) {
                    if (c != null && c.contains("DDShareActivity")) {
                        return TARGET_XD;
                    }
                    return null;
                }
            }

            String action = it.getAction();
            if (Intent.ACTION_CHOOSER.equals(action)) {
                // 系统分享面板(Intent.createChooser)实际是 ACTION_CHOOSER，需解包内部 ACTION_SEND
                try {
                    Intent inner = it.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (inner != null) {
                        String r = whichShare(inner);
                        if (r != null) return r;
                    }
                } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常: " + (ignored == null ? "" : ignored.getMessage())); }
            }
            if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                String pkg = it.getPackage();
                if (Util.isPkgIn(pkg, XQ.PKG_XQ_ARR)) return TARGET_XQ;
                if (XW.PKG_WX.equals(pkg)) return TARGET_XW;
                if (XB.PKG_WB.equals(pkg)) return TARGET_XB;
                if (XD.PKG_DD.equals(pkg)) return TARGET_XD;
                if (isNewPlatformPkg(pkg) && sEnableAggressive) return TARGET_OTHER;
            }
            if (detectNewPlatform(it) && sEnableAggressive) return TARGET_OTHER;
            if (sEnableAggressive && isGenericShare(it)) return TARGET_OTHER;

            return null;
        }

        static boolean detectNewPlatform(Intent it) {
            if (it == null) return false;

            String scheme = it.getScheme();
            Uri data = it.getData();
            String host = data != null ? data.getHost() : null;
            String path = data != null ? data.getPath() : null;

            if (scheme != null) {
                String s = scheme.toLowerCase();
                if ("snssdk1128".equals(s) || "sslocal".equals(s)) {
                    if (isSharePath(host, path) || hasAnyExtra(it,
                                                               "client_key", "callerLocalEntry", "mState")) return true;
                }
                if ("kwai".equals(s) || "kwaiapi".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("xhsdiscover".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("zhihu".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("jianshu".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("csdnapp".equals(s) || "csdn".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("taobao".equals(s) || "tbopen".equals(s) || "tmall".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("openapp.jdmobile".equalsIgnoreCase(s) || "openjd".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
                if ("pinduoduo".equals(s) || "pddopen".equals(s)) {
                    if (isSharePath(host, path)) return true;
                }
            }

            ComponentName cn = it.getComponent();
            if (cn != null && cn.getPackageName() != null) {
                if (isNewPlatformPkg(cn.getPackageName())) {
                    String c = cn.getClassName();
                    if (c != null && c.toLowerCase().contains("share")) return true;
                }
            }

            return false;
        }

        /** 激进模式判定：ACTION_SEND + 携带数据 = 视为分享 */
        static boolean isGenericShare(Intent it) {
            if (it == null) return false;
            String action = it.getAction();
            if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                try {
                    Bundle b = it.getExtras();
                    if (b != null) {
                        if (b.get(Intent.EXTRA_TEXT) != null) return true;
                        if (b.get(Intent.EXTRA_STREAM) != null) return true;
                        if (b.get(Intent.EXTRA_TITLE) != null) return true;
                    }
                    if (it.getData() != null) return true;
                    if (it.getType() != null) return true;
                } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常: " + (ignored == null ? "" : ignored.getMessage())); }
            }
            return false;
        }

        private static boolean isSharePath(String host, String path) {
            if (host != null) {
                String h = host.toLowerCase();
                if (h.contains("share") || h.contains("send")) return true;
            }
            if (path != null) {
                String p = path.toLowerCase();
                if (p.contains("share") || p.contains("send")) return true;
            }
            return false;
        }

        static boolean isNewPlatformPkg(String p) {
            if (p == null) return false;
            if (p.equals("com.ss.android.ugc.aweme")
                || p.equals("com.ss.android.ugc.aweme.lite")
                || p.equals("com.ss.android.ugc.aweme.minikatv")
                || p.equals("com.ss.android.ugc.live")) return true;
            if (p.equals("com.smile.gifmaker")
                || p.equals("com.kuaishou.nebula")) return true;
            if (p.equals("com.xingin.xhs")) return true;
            if (p.equals("com.zhihu.android")
                || p.equals("com.zhihu.android.lite")) return true;
            if (p.equals("com.jianshu.haruki")) return true;
            if (p.equals("net.csdn.csdnplus")) return true;
            if (p.equals("com.taobao.taobao")
                || p.equals("com.tmall.wireless")
                || p.equals("com.taobao.litetao")) return true;
            if (p.equals("com.jingdong.app.mall")
                || p.equals("com.jd.jdlite")) return true;
            if (p.equals("com.xunmeng.pinduoduo")) return true;
            return false;
        }

        static boolean hasAnyExtra(Intent it, String... keys) {
            if (it == null || keys == null) return false;
            for (int i = 0; i < keys.length; i++) {
                try {
                    Bundle b = it.getExtras();
                    if (b != null && b.get(keys[i]) != null) return true;
                } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常: " + (ignored == null ? "" : ignored.getMessage())); }
            }
            return false;
        }
    }

    // ============================================================
    //  小微模块
    // ============================================================
    static final class XW {

        static final String PKG_WX = "com.tencent.mm";
        static final String CLS_WX_ENTRY = "com.tencent.mm.plugin.base.stub.WXEntryActivity";
        static final String WX_ENTRY_SUFFIX = ".wxapi.WXEntryActivity";
        private static final String CLS_BASE_WX_API_V10 = "com.tencent.mm.opensdk.openapi.BaseWXApiImplV10";
        private static final String CLS_WX_API_V10 = "com.tencent.mm.opensdk.openapi.WXApiImplV10";
        private static final String CLS_BASE_REQ = "com.tencent.mm.opensdk.modelbase.BaseReq";
        private static final String CLS_BASE_RESP = "com.tencent.mm.opensdk.modelbase.BaseResp";
        private static final String CLS_SEND_MSG_TO_WX_RESP = "com.tencent.mm.opensdk.modelmsg.SendMessageToWX$Resp";
        private static final String CLS_WX_API_EVENT_HANDLER = "com.tencent.mm.opensdk.openapi.IWXAPIEventHandler";
        private static final int DEFAULT_SDK_VERSION = 587333634;
        private static final String DEFAULT_CONTENT = "辣鸡腾讯坑我钱财";
        private static final String CHECKSUM_SALT = "mMcShCsTr";
        private static final ThreadLocal<Boolean> sReentrant = new ThreadLocal<Boolean>() {
            @Override protected Boolean initialValue() { return Boolean.FALSE; }
        };
        private static volatile byte[] sCachedChecksum = null;
        private static volatile int sCachedChecksumSdkVersion = -1;
        private static volatile String sCachedChecksumAppPkg = null;
        static void hook(ClassLoader cl, String targetPackage, BooleanProvider enabled) {
            Class<?> wxApiImplClass = findWxApiImplClass(cl);
            if (wxApiImplClass != null) {
                hookSendReq(wxApiImplClass, cl, targetPackage, enabled);
            }
        }

        private static Class<?> findWxApiImplClass(ClassLoader cl) {
            Class<?> c = XposedHelpers.findClassIfExists(CLS_BASE_WX_API_V10, cl);
            if (c == null) c = XposedHelpers.findClassIfExists(CLS_WX_API_V10, cl);
            return c;
        }

        private static void hookSendReq(final Class<?> wxApiImplClass, final ClassLoader cl,
                                        final String targetPackage, final BooleanProvider enabled) {
            try {
                XposedBridge.hookAllMethods(wxApiImplClass, "sendReq", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            handleSendReq(param, cl, targetPackage, enabled);
                        }
                    });
            } catch (Throwable ignored) {
                ReaLog.log("share_fake", "假装分享：小微请求安装失败: " + (ignored == null ? "" : ignored.getMessage()));
            }
        }

        private static void handleSendReq(XC_MethodHook.MethodHookParam param,
                                          ClassLoader cl, String targetPackage,
                                          BooleanProvider enabled) throws Throwable {
            if (!isEnabled(TARGET_XW)) return;
            if (Boolean.TRUE.equals(sReentrant.get())) return;
            Object req = param.args.length > 0 ? param.args[0] : null;
            if (req == null) return;
            String reqCls = req.getClass().getName();
            if (!(reqCls.contains("SendMessageToWX$Req") || reqCls.contains("SendMultiMessageToWX$Req"))) {
                ReaLog.log("share_fake", "假装分享：小微请求非分享类请求，放行: " + reqCls);
                return;
            }
            try {
                sReentrant.set(Boolean.TRUE);
                Util.showToast("[分享]小微成功");
                ReaLog.log("share_fake", "假装分享：小微分享成功");
                Object resp = buildSuccessResp(cl);
                if (resp != null) {
                    Object handler = findEventHandler(cl);
                    if (handler != null) {
                        XposedHelpers.callMethod(handler, "onResp", resp);
                    } else {
                        callViaTempActivity(cl, resp, targetPackage);
                    }
                }
                param.setResult(true);
            } catch (Throwable t) {
                recordError(TARGET_XW, t);
            } finally {
                sReentrant.set(Boolean.FALSE);
            }
        }

        private static Object buildSuccessResp(ClassLoader cl) {
            try {
                Class<?> respClass = XposedHelpers.findClass(CLS_SEND_MSG_TO_WX_RESP, cl);
                Object resp = XposedHelpers.newInstance(respClass);
                XposedHelpers.setIntField(resp, "errCode", 0);
                return resp;
            } catch (Throwable e) {
                ReaLog.log("share_fake", "小微对象回调构造失败: " + (e == null ? "" : e.getMessage()));
                return null;
            }
        }

        private static Object findEventHandler(ClassLoader cl) {
            try {
                Class<?> handlerInterface = XposedHelpers.findClass(CLS_WX_API_EVENT_HANDLER, cl);
                List<Activity> activities = Util.getAllActivities();
                for (int i = 0; i < activities.size(); i++) {
                    Activity act = activities.get(i);
                    if (handlerInterface.isInstance(act)) return act;
                }
            } catch (Throwable t) {}
            return null;
        }

        private static void callViaTempActivity(ClassLoader cl, Object resp, String targetPackage) {
            String entryName = targetPackage + ".wxapi.WXEntryActivity";
            Object tempActivity = null;
            try {
                Class<?> entryClass = XposedHelpers.findClass(entryName, cl);
                tempActivity = entryClass.newInstance();
                Method onRespMethod = XposedHelpers.findMethodExact(entryClass, "onResp",
                                                                    XposedHelpers.findClass(CLS_BASE_RESP, cl));
                onRespMethod.invoke(tempActivity, resp);
            } catch (Throwable t) {
            } finally {
                if (tempActivity != null) {
                    try { XposedHelpers.callMethod(tempActivity, "finish"); } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
                }
            }
        }

        static boolean fakeByIntent(Activity activity, Intent realIntent, String callerPkg) {
            if (callerPkg == null || callerPkg.isEmpty()) return false;
            if (!validateCmd(realIntent)) return false;

            try {
                Intent back = new Intent();

                int sdkVersion = DEFAULT_SDK_VERSION;
                if (realIntent != null && realIntent.hasExtra("_mmessage_sdkVersion")) {
                    sdkVersion = realIntent.getIntExtra("_mmessage_sdkVersion", sdkVersion);
                }

                String appPkg = callerPkg;
                back.putExtra("wx_token_key", "com.tencent.mm.openapi.token");
                back.putExtra("_mmessage_content", DEFAULT_CONTENT);
                back.putExtra("_mmessage_sdkVersion", sdkVersion);
                back.putExtra("_mmessage_appPackage", appPkg);
                back.putExtra("_mmessage_checksum", checksum(DEFAULT_CONTENT, sdkVersion, appPkg));

                int cmd = realIntent != null ? realIntent.getIntExtra("_wxapi_command_type", 0) : 0;
                back.putExtra("_wxapi_command_type", cmd);
                back.putExtra("_wxapi_baseresp_errcode", 0);

                String tx = realIntent != null
                    ? realIntent.getStringExtra("_wxapi_baseresp_transaction") : null;
                if (tx == null || tx.isEmpty()) tx = Util.genTransaction();
                back.putExtra("_wxapi_baseresp_transaction", tx);

                back.addFlags(272629760);
                return Util.safeSendCallback(activity, back, callerPkg, callerPkg + WX_ENTRY_SUFFIX);
            } catch (Throwable t) {
                ReaLog.log("share_fake", "假装分享：小微(Intent)异常: " + t.getMessage());
                return false;
            }
        }

        static void fakeDirect(Activity activity, ClassLoader cl, String targetPackage) {
            try {
                Object resp = buildSuccessResp(cl);
                if (resp == null) return;
                Object handler = findEventHandler(cl);
                if (handler != null) {
                    XposedHelpers.callMethod(handler, "onResp", resp);
                } else {
                    callViaTempActivity(cl, resp, targetPackage);
                }
                Util.showToast("[分享]小微成功");
                ReaLog.log("share_fake", "假装分享：小微(主动调用)");
            } catch (Throwable t) {
                recordError(TARGET_XW, t);
            }
        }

        static boolean isShareCmd(Intent realIntent) {
            if (realIntent == null) return true;
            if (!realIntent.hasExtra("_wxapi_command_type")) return true;
            int cmd = realIntent.getIntExtra("_wxapi_command_type", -1);
            return cmd == 2;
        }

        private static boolean validateCmd(Intent realIntent) {
            return isShareCmd(realIntent);
        }

        private static byte[] checksum(String content, int sdkVersion, String appPackage) {
            if (sCachedChecksum != null
                && sCachedChecksumSdkVersion == sdkVersion
                && appPackage != null
                && appPackage.equals(sCachedChecksumAppPkg)) {
                return sCachedChecksum;
            }
            StringBuilder sb = new StringBuilder();
            if (content != null) sb.append(content);
            sb.append(sdkVersion).append(appPackage).append(CHECKSUM_SALT);
            String s = sb.toString();
            String sub = s.length() >= 9 ? s.substring(1, 9) : s;
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] d = md.digest(sub.getBytes());
                StringBuilder out = new StringBuilder();
                for (byte b : d) out.append(String.format("%02x", b));
                byte[] result = out.toString().getBytes();
                sCachedChecksum = result;
                sCachedChecksumSdkVersion = sdkVersion;
                sCachedChecksumAppPkg = appPackage;
                return result;
            } catch (Throwable t) {
                return new byte[0];
            }
        }
    }

    // ============================================================
    //  小Q 模块
    // ============================================================
    static final class XQ {

        static final String[] PKG_XQ_ARR = {
            "com.tencent.mobileqq", "com.tencent.qqlite", "com.tencent.tim"
        };
        static final String CLS_XQ_AUTH = "com.tencent.tauth.AuthActivity";
        private static final String CLS_XQ_SHARE = "com.tencent.connect.share.QQShare";
        private static final String CLS_QZONE_SHARE = "com.tencent.connect.share.QzoneShare";
        private static final String CLS_BASE_API = "com.tencent.connect.common.BaseApi";
        private static final String CLS_TENCENT = "com.tencent.connect.Tencent";
        private static final String CLS_IUI_LISTENER = "com.tencent.tauth.IUiListener";
        private static final String CLS_UI_ERROR = "com.tencent.tauth.UiError";
        private static final class ShareSpec {
            final String className;
            final String methodName;
            final boolean isQzone;
            final String toastText;
            final String logTag;
            ShareSpec(String className, String methodName, boolean isQzone,
                      String toastText, String logTag) {
                this.className = className;
                this.methodName = methodName;
                this.isQzone = isQzone;
                this.toastText = toastText;
                this.logTag = logTag;
            }
        }

        private static final ShareSpec[] SHARE_SPECS = {
            new ShareSpec(CLS_XQ_SHARE, "shareToQQ", false,
                          "[分享]小Q好友", "假装分享：小Q好友分享成功"),
            new ShareSpec(CLS_QZONE_SHARE, "shareToQzone", true,
                          "[分享]小Q空间", "假装分享：小Q空间分享成功"),
            new ShareSpec(CLS_TENCENT, "shareToQQ", false,
                          "[分享]小Q好友", "假装分享：小Q好友分享成功(旧API)"),
            new ShareSpec(CLS_TENCENT, "shareToQzone", true,
                          "[分享]小Q空间", "假装分享：小Q空间分享成功(旧API)"),
        };

        static void hook(ClassLoader cl, BooleanProvider enabled) {
            for (int i = 0; i < SHARE_SPECS.length; i++) {
                hookShareMethod(cl, SHARE_SPECS[i], enabled);
            }
            hookBaseApi(cl, enabled);
        }

        private static void hookShareMethod(final ClassLoader cl, final ShareSpec spec,
                                            final BooleanProvider enabled) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(spec.className, cl);
                if (cls == null) return;
                Class<?> listenerClass = XposedHelpers.findClassIfExists(CLS_IUI_LISTENER, cl);
                if (listenerClass == null) return;

                XposedHelpers.findAndHookMethod(cls, spec.methodName,
                    Activity.class, Bundle.class, listenerClass,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            handleQqShare(param, cl, enabled, 2, spec.isQzone,
                                          spec.toastText, spec.logTag);
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static void handleQqShare(XC_MethodHook.MethodHookParam param, ClassLoader cl,
                                          BooleanProvider enabled, int listenerIdx,
                                          boolean isQzone, String toastText, String logTag) {
            if (!isEnabled(TARGET_XQ)) return;
            try {
                Util.showToast(toastText);
                ReaLog.log("share_fake", logTag);
                Object listener = param.args[listenerIdx];
                if (listener != null) {
                    Object result = createSuccessJson(cl, isQzone);
                    if (result != null) {
                        XposedHelpers.callMethod(listener, "onComplete", result);
                    }
                }
                param.setResult(null);
            } catch (Throwable t) {
                recordError(TARGET_XQ, t);
            }
        }

        private static void hookBaseApi(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> baseApiClass = XposedHelpers.findClassIfExists(CLS_BASE_API, cl);
                if (baseApiClass == null) return;
                Class<?> listenerClass = XposedHelpers.findClassIfExists(CLS_IUI_LISTENER, cl);
                if (listenerClass == null) return;

                XposedBridge.hookAllMethods(baseApiClass, "request", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XQ)) return;
                            try {
                                Object arg1 = param.args.length > 1 ? param.args[1] : null;
                                if (arg1 instanceof String && ((String) arg1).contains("share")) {
                                    Util.showToast("[分享]小Q成功");
                                    ReaLog.log("share_fake", "假装分享：小Q分享成功(通用)");
                                    if (param.args.length > 4 && param.args[4] != null) {
                                        JSONObject result = new JSONObject();
                                        result.put("ret", 0);
                                        result.put("error_message", "");
                                        result.put("msg", "");
                                        XposedHelpers.callMethod(param.args[4], "onComplete", result);
                                    }
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                recordError(TARGET_XQ, t);
                            }
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }


        private static Object createSuccessJson(ClassLoader cl, boolean withPostId) {
            try {
                Class<?> jsonClass = XposedHelpers.findClass("org.json.JSONObject", cl);
                Object json = XposedHelpers.newInstance(jsonClass);
                XposedHelpers.callMethod(json, "put", "ret", 0);
                XposedHelpers.callMethod(json, "put", "error_message", "");
                XposedHelpers.callMethod(json, "put", "msg", "");
                XposedHelpers.callMethod(json, "put", "share_id",
                                         "share_" + System.currentTimeMillis());
                if (withPostId) {
                    XposedHelpers.callMethod(json, "put", "post_id",
                                             "fake_post_" + System.currentTimeMillis());
                }
                return json;
            } catch (Throwable t) {
                return null;
            }
        }

        // ============================================================
        //  小Q appid 解析
        // ============================================================
        private static String resolveQqAppId(Activity activity, Intent realIntent, String callerPkg) {
            // 1) 取真实URI appid参数
            try {
                Uri u = realIntent != null ? realIntent.getData() : null;
                if (u != null) {
                    String q = u.getQueryParameter("appid");
                    if (q == null) q = u.getQueryParameter("app_id");
                    if (q == null) q = u.getQueryParameter("appId");
                    if (isValidAppId(q)) return "tencent" + q;
                }
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }

            // 2) PackageManager 扫 callerPkg 的 AuthActivity intent-filter
            if (activity != null && callerPkg != null && !callerPkg.isEmpty()) {
                String hit = scanViaPackageManager(activity, callerPkg);
                if (hit != null) return hit;
            }

            // 3) 兜底 demo appid
            return "tencent222222";
        }

        private static String scanViaPackageManager(Activity activity, String callerPkg) {
            try {
                PackageManager pm = activity.getPackageManager();
                PackageInfo pi = pm.getPackageInfo(callerPkg,
                                                   PackageManager.GET_ACTIVITIES | PackageManager.GET_INTENT_FILTERS);
                if (pi == null || pi.activities == null) return null;
                // 优先只看 AuthActivity 的 filter
                for (ActivityInfo ai : pi.activities) {
                    if (ai == null || ai.name == null) continue;
                    if (!ai.name.equals(CLS_XQ_AUTH)) continue;
                    String hit = scanFilters(filtersOf(ai));
                    if (hit != null) return hit;
                }
                // 退一步：扫描全部 activity 的 filter
                for (ActivityInfo ai : pi.activities) {
                    if (ai == null) continue;
                    String hit = scanFilters(filtersOf(ai));
                    if (hit != null) return hit;
                }
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
            return null;
        }
        
         // 反射取 ActivityInfo.intentFilters（隐藏字段，公开 SDK 无此字段）
        private static List<IntentFilter> filtersOf(ActivityInfo ai) {
            List<IntentFilter> out = new ArrayList<IntentFilter>();
            if (ai == null) return out;
            try {
                Field f = null;
                for (Class<?> c = ai.getClass(); c != null && f == null; c = c.getSuperclass()) {
                    try {
                        f = c.getDeclaredField("intentFilters");
                        f.setAccessible(true);
                    } catch (NoSuchFieldException ignored) {
                        // 沿父类继续找
                    }
                }
                if (f == null) return out;
                Object v = f.get(ai);
                if (v instanceof IntentFilter[]) {
                    for (IntentFilter fl : (IntentFilter[]) v) if (fl != null) out.add(fl);
                } else if (v instanceof List) {
                    for (Object o : (List<?>) v) if (o instanceof IntentFilter) out.add((IntentFilter) o);
                }
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
            return out;
        }

        private static String scanFilters(List<IntentFilter> list) {
            if (list == null) return null;
            for (IntentFilter f : list) {
                if (f == null) continue;
                int n = f.countDataSchemes();
                for (int i = 0; i < n; i++) {
                    String sch = f.getDataScheme(i);
                    if (sch != null && sch.matches("tencent\\d+")) return sch;
                }
            }
            return null;
        }

        private static boolean isValidAppId(String s) {
            if (s == null || s.isEmpty()) return false;
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isDigit(s.charAt(i))) return false;
            }
            return true;
        }

        static boolean fakeByIntent(Activity activity, Intent realIntent, String callerPkg) {
            if (callerPkg == null || callerPkg.isEmpty()) return false;
            try {
                Bundle extras = realIntent != null ? realIntent.getExtras() : null;
                Intent back = new Intent();
                String qqScheme = resolveQqAppId(activity, realIntent, callerPkg);
                back.setData(Uri.parse(qqScheme + "://tauth.qq.com/?#action=shareToQQ&result=complete&response={\"ret\":0}"));
                back.addFlags(272629760);
                back.putExtra("fling_action_key", 2);
                back.putExtra("preAct", "LiteActivity");
                back.putExtra("leftViewText", "分享成功");
                back.putExtra("fling_code_key", 32253408);
                back.putExtra("preAct_time", 1493381615748L);
                String pkgName = null;
                if (extras != null && extras.containsKey("pkg_name")) {
                    pkgName = extras.getString("pkg_name");
                }
                if (pkgName == null || pkgName.isEmpty()) pkgName = callerPkg;
                back.putExtra("pkg_name", pkgName);

                return Util.safeSendCallback(activity, back, callerPkg, CLS_XQ_AUTH);
            } catch (Throwable t) {
                ReaLog.log("share_fake", "假装分享：小Q分享(Intent)异常: " + t.getMessage());
                return false;
            }
        }

        static void fakeDirect(Activity activity, ClassLoader cl) {
            try {
                Object json = createSuccessJson(cl, false);
                if (json == null) return;
                Object listener = findListener(activity, cl);
                if (listener != null) {
                    XposedHelpers.callMethod(listener, "onComplete", json);
                }
                Util.showToast("[分享]小Q成功");
                ReaLog.log("share_fake", "假装分享：小Q分享(主动调用)");
            } catch (Throwable t) {
                recordError(TARGET_XQ, t);
            }
        }

        private static Object findListener(Activity activity, ClassLoader cl) {
            try {
                Class<?> listenerClass = XposedHelpers.findClass(CLS_IUI_LISTENER, cl);
                if (listenerClass.isInstance(activity)) return activity;
            } catch (Throwable ignore) {}
            return null;
        }
    }

    // ============================================================
    //  小搏模块
    // ============================================================
    static final class XB {

        static final String PKG_WB = "com.sina.weibo";
        static final String CLS_WB_TRANS = "com.sina.weibo.sdk.share.WbShareTransActivity";
        private static final String CLS_XB_SHARE_API = "com.sina.weibo.sdk.api.share.IWeiboShareAPI";
        private static final String CLS_XB_API = "com.sina.weibo.sdk.api.WeiboAPI";
        private static final String CLS_WEIBO_MULTI_MSG = "com.sina.weibo.sdk.api.WeiboMultiMessage";
        private static final String CLS_WEIBO_HANDLER_RESP = "com.sina.weibo.sdk.api.share.IWeiboHandler$Response";
        private static final String CLS_BASE_RESPONSE = "com.sina.weibo.sdk.api.share.BaseResponse";
        private static final String CLS_SEND_MSG_TO_WEIBO_RESP = "com.sina.weibo.sdk.api.share.SendMessageToWeiboResponse";
        private static final String CLS_XB_API_RESP = "com.sina.weibo.sdk.api.WeiboAPI$Response";
        private static final String CLS_WB_SHARE_CALLBACK = "com.sina.weibo.sdk.api.share.WbShareCallback";
        private static final ThreadLocal<Boolean> sWeiboReentrant = new ThreadLocal<Boolean>() {
            @Override protected Boolean initialValue() { return Boolean.FALSE; }
        };

        static void hook(ClassLoader cl, BooleanProvider enabled) {
            hookSendRequest(cl, enabled);
            hookDoResultIntent(cl, enabled);
            hookWeiboApi(cl, enabled);
            hookCallbacks(cl, enabled);
        }

        private static void hookSendRequest(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> weiboShareApiClass = XposedHelpers.findClassIfExists(CLS_XB_SHARE_API, cl);
                if (weiboShareApiClass == null) return;
                XposedBridge.hookAllMethods(weiboShareApiClass, "sendRequest", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XB)) return;
                            if (Boolean.TRUE.equals(sWeiboReentrant.get())) return;
                            try {
                                sWeiboReentrant.set(Boolean.TRUE);
                                Util.showToast("[分享]小搏成功");
                                ReaLog.log("share_fake", "假装分享：小搏分享成功");
                                Object response = buildSuccessResponse(cl);
                                Object callback = findCallback(param);
                                if (callback != null) {
                                    XposedHelpers.callMethod(callback, "onSuccess", response);
                                }
                                param.setResult(true);
                            } catch (Throwable t) {
                                recordError(TARGET_XB, t);
                            } finally {
                                sWeiboReentrant.set(Boolean.FALSE);
                            }
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static void hookDoResultIntent(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> weiboShareApiClass = XposedHelpers.findClassIfExists(CLS_XB_SHARE_API, cl);
                if (weiboShareApiClass == null) return;
                Class<?> responseHandler = XposedHelpers.findClassIfExists(CLS_WEIBO_HANDLER_RESP, cl);
                if (responseHandler == null) return;

                XposedHelpers.findAndHookMethod(weiboShareApiClass, "doResultIntent",
                    Intent.class, responseHandler,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XB)) return;
                            try {
                                Object handler = param.args[1];
                                if (handler != null) {
                                    Object response = buildSuccessResponse(cl);
                                    XposedHelpers.callMethod(handler, "onResponse", response);
                                }
                                ReaLog.log("share_fake", "假装分享：小搏分享成功(doResultIntent)");
                            } catch (Throwable t) {
                                recordError(TARGET_XB, t);
                            }
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static void hookWeiboApi(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> weiboAPIClass = XposedHelpers.findClassIfExists(CLS_XB_API, cl);
                if (weiboAPIClass == null) return;

                XposedHelpers.findAndHookMethod(weiboAPIClass, "share", Activity.class,
                    XposedHelpers.findClass(CLS_WEIBO_MULTI_MSG, cl),
                    XposedHelpers.findClass(CLS_WEIBO_HANDLER_RESP, cl),
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XB)) return;
                            try {
                                Util.showToast("[分享]小搏成功");
                                ReaLog.log("share_fake", "假装分享：小搏分享(旧API)成功");
                                Object callback = param.args[2];
                                if (callback != null) {
                                    Object response = buildSuccessResponse(cl);
                                    XposedHelpers.callMethod(callback, "onResponse", response);
                                }
                                param.setResult(true);
                            } catch (Throwable t) {
                                recordError(TARGET_XB, t);
                            }
                        }
                    });

                Class<?> baseResponseClass = XposedHelpers.findClassIfExists(CLS_BASE_RESPONSE, cl);
                if (baseResponseClass != null) {
                    XposedHelpers.findAndHookMethod(baseResponseClass, "getErrCode", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                if (!isEnabled(TARGET_XB)) return;
                                Object self = param.thisObject;
                                if (self == null) return;
                                String cls = self.getClass().getName();
                                if (cls.contains("SendMessageToWeibo") || cls.contains("SendMultiMessageToWeibo")) {
                                    param.setResult(0);
                                    ReaLog.log("share_fake", "假装分享：小搏虚假信息创建成功");
                                }
                            }
                        });
                }
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static void hookCallbacks(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> weiboResponseClass = XposedHelpers.findClassIfExists(CLS_XB_API_RESP, cl);
                if (weiboResponseClass != null) {
                    XposedHelpers.findAndHookMethod(weiboResponseClass, "onComplete", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                if (!isEnabled(TARGET_XB)) return;
                                param.setResult(null);
                                XposedHelpers.callMethod(param.thisObject, "onSuccess");
                            }
                        });
                }
                Class<?> wbShareCallbackClass = XposedHelpers.findClassIfExists(CLS_WB_SHARE_CALLBACK, cl);
                if (wbShareCallbackClass != null) {
                    XposedHelpers.findAndHookMethod(wbShareCallbackClass, "onWbShareSuccess", new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                                if (!isEnabled(TARGET_XB)) return;
                                param.setResult(null);
                            }
                        });
                }
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static Object buildSuccessResponse(ClassLoader cl) {
            try {
                Class<?> responseClass = XposedHelpers.findClass(CLS_SEND_MSG_TO_WEIBO_RESP, cl);
                Object response = XposedHelpers.newInstance(responseClass);
                XposedHelpers.setIntField(response, "errCode", 0);
                XposedHelpers.setObjectField(response, "errMsg", "success");
                try { XposedHelpers.setIntField(response, "reqType", 0); } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
                try {
                    XposedHelpers.setObjectField(response, "transaction",
                                                 "tx_" + System.currentTimeMillis());
                } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
                return response;
            } catch (Throwable e) {
                try {
                    Class<?> baseRespClass = XposedHelpers.findClass(CLS_BASE_RESPONSE, cl);
                    Object response = XposedHelpers.newInstance(baseRespClass);
                    XposedHelpers.setIntField(response, "errCode", 0);
                    try { XposedHelpers.setObjectField(response, "errMsg", "success"); } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
                    return response;
                } catch (Throwable t) {
                    return null;
                }
            }
        }

        private static Object findCallback(XC_MethodHook.MethodHookParam param) {
            for (Object arg : param.args) {
                if (arg != null && (arg.getClass().getName().contains("IWeiboHandler$Response")
                    || arg.getClass().getName().contains("WbShareCallback"))) {
                    return arg;
                }
            }
            return null;
        }

        static boolean fakeByIntent(Activity activity, Intent realIntent, String callerPkg) {
            if (callerPkg == null || callerPkg.isEmpty()) return false;
            try {
                Intent back = new Intent();
                back.setAction("com.sina.weibo.sdk.action.ACTION_SDK_RESP_ACTIVITY");
                back.putExtra("_weibo_resp_errcode", 0);
                back.putExtra("_weibo_resp_errmsg", "");

                String tx = realIntent != null ? realIntent.getStringExtra("_weibo_transaction") : null;
                if (tx == null || tx.isEmpty()) tx = String.valueOf(System.currentTimeMillis());
                back.putExtra("_weibo_transaction", tx);

                back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return Util.safeSendCallback(activity, back, callerPkg, CLS_WB_TRANS);
            } catch (Throwable t) {
                ReaLog.log("share_fake", "假装分享：小搏(Intent)异常: " + t.getMessage());
                return false;
            }
        }

        static void fakeDirect(Activity activity, ClassLoader cl) {
            try {
                Object response = buildSuccessResponse(cl);
                if (response == null) return;
                Object callback = findListener(activity, cl);
                if (callback != null) {
                    XposedHelpers.callMethod(callback, "onSuccess", response);
                }
                Util.showToast("[分享]小搏成功");
                ReaLog.log("share_fake", "假装分享：小搏(主动调用)");
            } catch (Throwable t) {
                recordError(TARGET_XB, t);
            }
        }

        private static Object findListener(Activity activity, ClassLoader cl) {
            try {
                Class<?> responseClass = XposedHelpers.findClass(CLS_WEIBO_HANDLER_RESP, cl);
                if (responseClass.isInstance(activity)) return activity;
            } catch (Throwable ignore) {}
            return null;
        }
    }

    // ============================================================
    //  小钉模块
    // ============================================================
    static final class XD {
        static final String PKG_DD = "com.alibaba.android.rimet";
        private static final String CLS_DD_SHARE_API = "com.alibaba.android.ddsharesdk.share.IDDShareApi";
        private static final String CLS_DD_BASE_API = "com.alibaba.android.ddsharesdk.share.BaseApi";
        private static final String CLS_DD_BASE_REQ = "com.alibaba.android.ddsharesdk.model.base.BaseReq";
        private static final String CLS_DD_BASE_RESP = "com.alibaba.android.ddsharesdk.model.base.BaseResp";
        private static final String CLS_DD_SEND_MSG_RESP = "com.alibaba.android.ddsharesdk.model.share.SendMessageToDD$Resp";
        private static final String CLS_DD_EVENT_HANDLER = "com.alibaba.android.ddsharesdk.model.callback.IDDAPIEventHandler";
        private static final String CLS_DD_SHARE_ACT = "com.alibaba.android.ddsharesdk.share.DDShareActivity";
        private static final ThreadLocal<Boolean> sReentrant = new ThreadLocal<Boolean>() {
            @Override protected Boolean initialValue() { return Boolean.FALSE; }
        };

        static void hook(ClassLoader cl, BooleanProvider enabled) {
            hookShareApi(cl, enabled);
            hookBaseApi(cl, enabled);
            hookCallbacks(cl, enabled);
        }

        private static void hookShareApi(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> ddShareApiClass = XposedHelpers.findClassIfExists(CLS_DD_SHARE_API, cl);
                if (ddShareApiClass == null) return;
                XposedBridge.hookAllMethods(ddShareApiClass, "sendReq", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XD)) return;
                            if (Boolean.TRUE.equals(sReentrant.get())) return;
                            Object req = param.args.length > 0 ? param.args[0] : null;
                            if (req == null) return;
                            String reqCls = req.getClass().getName();
                            if (!reqCls.contains("SendMessageToDD")) {
                                ReaLog.log("share_fake", "小钉 sendReq 非分享类请求，放行: " + reqCls);
                                return;
                            }
                            try {
                                sReentrant.set(Boolean.TRUE);
                                Util.showToast("[分享]小钉成功");
                                ReaLog.log("share_fake", "假装分享：小钉分享成功");
                                Object resp = buildSuccessResp(cl);
                                if (resp != null) {
                                    Object handler = findEventHandler(cl);
                                    if (handler != null) {
                                        XposedHelpers.callMethod(handler, "onResp", resp);
                                    } else {
                                        callViaTempActivity(cl, resp);
                                    }
                                }
                                param.setResult(true);
                            } catch (Throwable t) {
                                recordError(TARGET_XD, t);
                            } finally {
                                sReentrant.set(Boolean.FALSE);
                            }
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static void hookBaseApi(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> baseApiClass = XposedHelpers.findClassIfExists(CLS_DD_BASE_API, cl);
                if (baseApiClass == null) return;
                Class<?> baseReqClass = XposedHelpers.findClassIfExists(CLS_DD_BASE_REQ, cl);
                if (baseReqClass == null) return;

                XposedHelpers.findAndHookMethod(baseApiClass, "sendReq", baseReqClass,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XD)) return;
                            if (Boolean.TRUE.equals(sReentrant.get())) return;
                            // 与小微同款判定：BaseApi.sendReq 是全请求总入口，只伪造分享类 req
                            Object req = param.args.length > 0 ? param.args[0] : null;
                            if (req == null) return;
                            String reqCls = req.getClass().getName();
                            if (!reqCls.contains("SendMessageToDD")) {
                                ReaLog.log("share_fake", "小钉 BaseApi.sendReq 非分享类请求，放行: " + reqCls);
                                return;
                            }
                            try {
                                sReentrant.set(Boolean.TRUE);
                                Util.showToast("[分享]小钉成功");
                                ReaLog.log("share_fake", "假装分享：小钉分享成功");
                                Object resp = buildSuccessResp(cl);
                                // BaseApi.sendReq(BaseReq) 是单参方法，回调对象不在参数里；
                                // 与 hookShareApi 同路径：找实现 IDDAPIEventHandler 的 Activity 发回调，
                                // 找不到则反射小钉分享入口 Activity 的 onResp
                                if (resp != null) {
                                    Object handler = findEventHandler(cl);
                                    if (handler != null) {
                                        XposedHelpers.callMethod(handler, "onResp", resp);
                                    } else {
                                        callViaTempActivity(cl, resp);
                                    }
                                }
                                param.setResult(true);
                            } catch (Throwable t) {
                                recordError(TARGET_XD, t);
                            } finally {
                                sReentrant.set(Boolean.FALSE);
                            }
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static void hookCallbacks(final ClassLoader cl, final BooleanProvider enabled) {
            try {
                Class<?> handlerClass = XposedHelpers.findClassIfExists(CLS_DD_EVENT_HANDLER, cl);
                if (handlerClass == null) return;
                Class<?> baseRespClass = XposedHelpers.findClassIfExists(CLS_DD_BASE_RESP, cl);
                if (baseRespClass == null) return;

                XposedHelpers.findAndHookMethod(handlerClass, "onResp", baseRespClass,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                            if (!isEnabled(TARGET_XD)) return;
                            try {
                                Object resp = param.args[0];
                                if (resp == null) return;
                                String cls = resp.getClass().getName();
                                if (cls.contains("SendMessageToDD")) {
                                    XposedHelpers.setIntField(resp, "errCode", 0);
                                    XposedHelpers.setIntField(resp, "errType", 0);
                                }
                            } catch (Throwable t) {
                                recordError(TARGET_XD, t);
                            }
                        }
                    });
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        private static Object buildSuccessResp(ClassLoader cl) {
            try {
                Class<?> respClass = XposedHelpers.findClass(CLS_DD_SEND_MSG_RESP, cl);
                Object resp = XposedHelpers.newInstance(respClass);
                XposedHelpers.setIntField(resp, "errCode", 0);
                XposedHelpers.setIntField(resp, "errType", 0);
                return resp;
            } catch (Throwable e) {
                try {
                    Class<?> baseRespClass = XposedHelpers.findClass(CLS_DD_BASE_RESP, cl);
                    Object resp = XposedHelpers.newInstance(baseRespClass);
                    XposedHelpers.setIntField(resp, "errCode", 0);
                    return resp;
                } catch (Throwable t) {
                    return null;
                }
            }
        }

        private static Object findEventHandler(ClassLoader cl) {
            try {
                Class<?> handlerInterface = XposedHelpers.findClass(CLS_DD_EVENT_HANDLER, cl);
                List<Activity> activities = Util.getAllActivities();
                for (int i = 0; i < activities.size(); i++) {
                    Activity act = activities.get(i);
                    if (handlerInterface.isInstance(act)) return act;
                }
            } catch (Throwable t) {}
            return null;
        }

        private static void callViaTempActivity(ClassLoader cl, Object resp) {
            try {
                Class<?> entryClass = XposedHelpers.findClass(CLS_DD_SHARE_ACT, cl);
                Object tempActivity = entryClass.newInstance();
                Method onRespMethod = XposedHelpers.findMethodExact(entryClass, "onResp",
                                                                    XposedHelpers.findClass(CLS_DD_BASE_RESP, cl));
                onRespMethod.invoke(tempActivity, resp);
                XposedHelpers.callMethod(tempActivity, "finish");
            } catch (Throwable ignored) { ReaLog.log("share_fake", "假装分享异常" + (ignored == null ? "" : ignored.getMessage())); }
        }

        static void fakeDirect(Activity activity, ClassLoader cl) {
            try {
                Object resp = buildSuccessResp(cl);
                if (resp == null) return;
                Object handler = findEventHandler(cl);
                if (handler != null) {
                    XposedHelpers.callMethod(handler, "onResp", resp);
                } else {
                    callViaTempActivity(cl, resp);
                }
                Util.showToast("[分享]小钉成功");
                ReaLog.log("share_fake", "假装分享：小钉(主动调用)");
            } catch (Throwable t) {
                recordError(TARGET_XD, t);
            }
        }
    }

    // ============================================================
    //  激进通用平台模块（TARGET_OTHER 的执行体；开关由 aggressive 控制）
    // ============================================================
    static final class Generic {

        static boolean pretend(Activity activity, Intent realIntent, String callerPkg) {
            if (activity == null || activity.isFinishing()) return false;
            // 不再调用 activity.setResult() —— 那是"给 A 的调用者回传"，
            // 无法触发 A 自己的 onActivityResult。
            // 改由 handleShareIfNeeded 在 requestCode > 0 时手动调用 onActivityResult。
            ReaLog.log("share_fake", "假装分享：通用平台已处理 (caller=" + callerPkg + ")");
            return true;
        }
    }
}

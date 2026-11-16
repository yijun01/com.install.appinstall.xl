package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.DebugModeManager;


/**
 * 权限相关 Hook 功能集合
 * 包含智能权限请求拆分处理
 */
public class Prsprn {
    private final HookInit mHookInit; //引用类
    /**
     * 向 BuHook 仓库注册本组件提供的全部挂载功能（注册幂等，重复调用安全）。
     */
    public void registerHooks() {
        // === 极速组：权限配置核心 8 项 ===
        BuHook.register("hook_check_permission", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookCheckPermission(cl);
                }
            });
        BuHook.register("hook_permission_request", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookPermissionRequest(cl);
                }
            });
        BuHook.register("hook_query_all_packages", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookQueryAllPackagesPermission(cl);
                }
            });
        BuHook.register("hook_package_usage_stats", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookPackageUsageStatsPermission(cl);
                }
            });
        BuHook.register("hook_app_ops", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookAppOpsForAllPermissions(cl);
                }
            });
        BuHook.register("hook_overlay_app_ops", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookOverlayAppOps(cl);
                }
            });
        BuHook.register("hook_write_settings", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookWriteSettingsPermission(cl);
                }
            });
        BuHook.register("hook_special_system_apis", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookSpecialSystemApis(cl);
                }
            });

        // === 普通组：权限配置补充 7 项 ===
        BuHook.register("hook_account_manager", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookAccountManager(cl);
                }
            });
        BuHook.register("hook_Content_Resolver_Query", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookContentResolverQuery(cl);
                }
            });
        BuHook.register("hook_Telephony_Getters", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookTelephonyGetters(cl);
                }
            });
        BuHook.register("hook_accessibility", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookAccessibilityManager(cl);
                }
            });
        BuHook.register("hook_app_widget", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookAppWidgetManager(cl);
                }
            });
        BuHook.register("hook_shortcut_widget", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookShortcutAndWidgetFake(cl);
                }
            });
        BuHook.register("hook_settings_provider", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
                @Override
                public void install(ClassLoader cl, Context ctx) throws Throwable {
                    hookSettingsProvider(cl);
                }
            });
    }

    // ---------- 常量 ----------
    private static final String[] DETECTION_PERMISSIONS = {
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.GET_PACKAGE_SIZE",
        "com.android.permission.GET_INSTALLED_APPS",
        "android.permission.PACKAGE_USAGE_STATS",
        "android.permission.PACKAGE_VERIFICATION_AGENT",
        "android.permission.READ_DEFAULT_APPLICATIONS",
        "android.permission.ACCESS_EPHEMERAL_APPS",
        "android.permission.SET_PREFERRED_APPLICATIONS"
    };
    private static final Set<String> DETECTION_PERMISSIONS_SET =
    new HashSet<>(Arrays.asList(DETECTION_PERMISSIONS));

    // 系统设置页 Intent -> 权限字符串映射
    private static final Map<String, String> PERMISSION_INTENT_MAP = new HashMap<>();
    static {
        PERMISSION_INTENT_MAP.put("android.settings.MANAGE_OVERLAY_PERMISSION",
                                  "android.permission.SYSTEM_ALERT_WINDOW");
        PERMISSION_INTENT_MAP.put("android.settings.MANAGE_UNKNOWN_APP_SOURCES",
                                  "android.permission.REQUEST_INSTALL_PACKAGES");
        PERMISSION_INTENT_MAP.put("android.settings.NOTIFICATION_LISTENER_SETTINGS",
                                  "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE");
        PERMISSION_INTENT_MAP.put("android.settings.ACCESSIBILITY_SETTINGS",
                                  "android.permission.BIND_ACCESSIBILITY_SERVICE");
        PERMISSION_INTENT_MAP.put("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                                  "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        PERMISSION_INTENT_MAP.put("android.settings.USAGE_ACCESS_SETTINGS",
                                  "android.permission.PACKAGE_USAGE_STATS");
        PERMISSION_INTENT_MAP.put("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION",
                                  "android.permission.MANAGE_EXTERNAL_STORAGE");
        PERMISSION_INTENT_MAP.put("android.settings.PICTURE_IN_PICTURE_SETTINGS",
                                  "android.permission.PICTURE_IN_PICTURE");
        PERMISSION_INTENT_MAP.put("android.settings.DEVICE_ADMIN_SETTINGS",
                                  "android.permission.BIND_DEVICE_ADMIN");
        PERMISSION_INTENT_MAP.put("android.settings.EXTERNAL_STORAGE_ACCESS_SETTINGS",
                                  "android.permission.ACCESS_EXTERNAL_STORAGE");
    }

    // 权限组缓存
    private static final Map<String, String> sPermissionGroupCache = Collections.synchronizedMap(
        new LinkedHashMap<String, String>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > 500;
            }
        }
    );

    // ---------- 隐私数据源枚举 ----------
    private enum DataSourceGroup {
        CONTACTS(new String[]{"contacts", "com.android.contacts"},
                 "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS"),
        SMS(new String[]{"sms", "mms", "mms-sms"},
            "android.permission.READ_SMS"),
        CALL_LOG(new String[]{"call_log"},
                 "android.permission.READ_CALL_LOG"),
        CALENDAR(new String[]{"calendar"},
                 "android.permission.READ_CALENDAR");

        final String[] authorities;
        final String[] permissions;

        DataSourceGroup(String[] authorities, String... permissions) {
            this.authorities = authorities;
            this.permissions = permissions;
        }

        static DataSourceGroup match(Uri uri) {
            if (uri == null) return null;
            String auth = uri.getAuthority();
            if (auth == null) return null;
            String lowerAuth = auth.toLowerCase();
            for (DataSourceGroup group : values()) {
                for (String a : group.authorities) {
                    if (lowerAuth.contains(a) || lowerAuth.equals(a)) {
                        return group;
                    }
                }
            }
            return null;
        }
    }

    // ---------- 日志去重工具 ----------
    private static void logOnce(String tag, String message) {
        if (HookInit.sLoggedPackageSet.add(tag + "||" + message)) {
            ReaLog.log(tag, message);
        }
    }

    public Prsprn(HookInit hookInit) {
        mHookInit = hookInit;
        // 初始化时 Hook onRequestPermissionsResult 用于合并回调
        hookOnRequestPermissionsResult();
    }

    // ========== 重新挂载所有权限 Hook（供外部调用） ==========
    public void reHookPermissionMethods(ClassLoader classLoader) {
        hookQueryAllPackagesPermission(classLoader);
        hookPackageUsageStatsPermission(classLoader);
        hookBasicPermissionChecks(classLoader);
        hookCheckPermission(classLoader);
        hookAppOpsForAllPermissions(classLoader);
        hookOverlayAppOps(classLoader);
        hookSpecialSystemApis(classLoader);
        hookWriteSettingsPermission(classLoader);
        hookShortcutAndWidgetFake(classLoader);
        hookSettingsProvider(classLoader);
        hookAccountManager(classLoader);
        hookAppWidgetManager(classLoader);
        hookAccessibilityManager(classLoader);
        hookTelephonyGetters(classLoader);
        hookContentResolverQuery(classLoader);
        // 权限请求拆分也需要重装
        // hookPermissionRequest(classLoader);
        // 清空权限组缓存
        sPermissionGroupCache.clear();
        //ReaLog.log("permission", "所有权限Hook已重新安装，缓存已清空");
    }

    //hook检查权限-新版
    public void hookCheckPermission(ClassLoader classLoader) {
        try {
            // 1. PackageManager.checkPermission
            try {
                Class<?> appPmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
                XposedHelpers.findAndHookMethod(appPmClass, "checkPermission",
                    String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String permName = (String) param.args[0];
                            String packageName = (String) param.args[1];
                            if (packageName == null || !packageName.equals(mHookInit.getCurrentTargetApp())) {
                                return;
                            }
                            if (shouldFakePermission(permName)) {
                                param.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    }
                );
            } catch (Throwable t) { /* 静默 */ }

            // 2. ContextWrapper.checkSelfPermission
            try {
                XposedHelpers.findAndHookMethod(
                    "android.content.ContextWrapper",
                    classLoader,
                    "checkSelfPermission",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String permName = (String) param.args[0];
                            if (shouldFakePermission(permName)) {
                                param.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    }
                );
            } catch (Throwable t) { /* 静默 */ }

            // 3. ContextImpl.checkSelfPermission（兜底）
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.ContextImpl",
                    classLoader,
                    "checkSelfPermission",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String permName = (String) param.args[0];
                            if (shouldFakePermission(permName)) {
                                param.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    }
                );
            } catch (Throwable t) { /* 静默 */ }

            // 4. ContextImpl.checkPermission (三参数)  ← 新版新增
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.ContextImpl",
                    classLoader,
                    "checkPermission",
                    String.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String permName = (String) param.args[0];
                            if (shouldFakePermission(permName)) {
                                param.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    }
                );
            } catch (Throwable t) { /* 静默 */ }

            // 5. Activity.checkSelfPermission  ← 新版新增（API 23+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    XposedHelpers.findAndHookMethod(
                        "android.app.Activity",
                        classLoader,
                        "checkSelfPermission",
                        String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                String permName = (String) param.args[0];
                                if (shouldFakePermission(permName)) {
                                    param.setResult(PackageManager.PERMISSION_GRANTED);
                                }
                            }
                        }
                    );
                } catch (Throwable t) { /* 静默 */ }
            }

            // 6. AndroidX PermissionChecker
            try {
                Class<?> permissionCheckerClass = XposedHelpers.findClassIfExists(
                    "androidx.core.content.PermissionChecker", classLoader);
                if (permissionCheckerClass != null) {
                    XposedBridge.hookAllMethods(
                        permissionCheckerClass,
                        "checkSelfPermission",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                String targetPerm = null;
                                for (Object arg : param.args) {
                                    if (arg instanceof String && DETECTION_PERMISSIONS_SET.contains(arg)) {
                                        targetPerm = (String) arg;
                                        break;
                                    }
                                }
                                if (targetPerm != null && shouldFakePermission(targetPerm)) {
                                    param.setResult(PackageManager.PERMISSION_GRANTED);
                                }
                            }
                        }
                    );
                }
            } catch (Throwable t) { /* 静默 */ }

            // 7. AndroidX PackageManagerCompat
            try {
                Class<?> packageManagerCompatClass = XposedHelpers.findClassIfExists(
                    "androidx.core.content.PackageManagerCompat", classLoader);
                if (packageManagerCompatClass != null) {
                    XposedBridge.hookAllMethods(
                        packageManagerCompatClass,
                        "checkPermission",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                String permission = null;
                                for (Object arg : param.args) {
                                    if (arg instanceof String && DETECTION_PERMISSIONS_SET.contains(arg)) {
                                        permission = (String) arg;
                                        break;
                                    }
                                }
                                if (permission != null && shouldFakePermission(permission)) {
                                    param.setResult(PackageManager.PERMISSION_GRANTED);
                                }
                            }
                        }
                    );
                }
            } catch (Throwable t) { /* 忽略无AndroidX的情况 */ }

            logOnce("permission", "全部权限检查已启用");
        } catch (Throwable t) {
            // 顶层异常静默
        }
    }

    // ========== 智能权限请求拆分处理 ==========
    public void hookPermissionRequest(ClassLoader classLoader) {
        // --- Activity.requestPermissions ---
        XposedHelpers.findAndHookMethod(
            Activity.class,
            "requestPermissions",
            String[].class, int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    String[] permissions = (String[]) param.args[0];
                    int requestCode = (int) param.args[1];
                    Activity activity = (Activity) param.thisObject;
                    handlePermissionRequest(activity, null, permissions, requestCode, param);
                }
            }
        );

        // --- android.app.Fragment ---
        try {
            final Class<?> fragmentClass = Class.forName("android.app.Fragment");
            XposedHelpers.findAndHookMethod(
                fragmentClass,
                "requestPermissions",
                String[].class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        String[] permissions = (String[]) param.args[0];
                        int requestCode = (int) param.args[1];
                        Object fragment = param.thisObject;
                        Activity activity = (Activity) XposedHelpers.callMethod(fragment, "getActivity");
                        if (activity == null) return;
                        handlePermissionRequest(activity, fragment, permissions, requestCode, param);
                    }
                }
            );
        } catch (Throwable ignored) {}

        // --- androidx.fragment.app.Fragment ---
        try {
            final Class<?> fragmentCompatClass = Class.forName("androidx.fragment.app.Fragment");
            XposedHelpers.findAndHookMethod(
                fragmentCompatClass,
                "requestPermissions",
                String[].class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        String[] permissions = (String[]) param.args[0];
                        int requestCode = (int) param.args[1];
                        Object fragment = param.thisObject;
                        Activity activity = (Activity) XposedHelpers.callMethod(fragment, "requireActivity");
                        if (activity == null) return;
                        handlePermissionRequest(activity, fragment, permissions, requestCode, param);
                    }
                }
            );
        } catch (Throwable ignored) {}

        // --- android.support.v4.app.Fragment (旧版兼容) ---
        try {
            final Class<?> supportV4FragmentClass = Class.forName("android.support.v4.app.Fragment");
            XposedHelpers.findAndHookMethod(
                supportV4FragmentClass,
                "requestPermissions",
                String[].class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        String[] permissions = (String[]) param.args[0];
                        int requestCode = (int) param.args[1];
                        Object fragment = param.thisObject;
                        Activity activity = (Activity) XposedHelpers.callMethod(fragment, "getActivity");
                        if (activity == null) return;
                        handlePermissionRequest(activity, fragment, permissions, requestCode, param);
                    }
                }
            );
            ReaLog.log("permission", "support.v4.app.Fragment 权限请求已Hook");
        } catch (ClassNotFoundException e) {
            // 忽略
        } catch (Throwable t) {
            ReaLog.log("permission", "support.v4.app.Fragment Hook失败: " + t.getMessage());
        }

        //  logOnce("permission", "已启用智能权限请求拆分");
    }

    // 核心处理方法
    private void handlePermissionRequest(Activity activity, Object fragment, String[] permissions, int requestCode, XC_MethodHook.MethodHookParam param) {
        try {
            // 如果所有权限都未勾选（全部真实），直接放行
            boolean allReal = true;
            for (String perm : permissions) {
                if (shouldFakePermission(perm)) {
                    allReal = false;
                    break;
                }
            }
            if (allReal) {
                logOnce("permission", "权限防护：是未防护权限，放行:  " + Arrays.toString(permissions));
                return;
            }

            // 检查是否全部勾选（全部伪造）
            boolean allFake = true;
            for (String perm : permissions) {
                if (!shouldFakePermission(perm)) {
                    allFake = false;
                    break;
                }
            }
            if (allFake) {
                int[] grantResults = new int[permissions.length];
                Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
                deliverPermissionResult(activity, fragment, requestCode, permissions, grantResults);
                param.setResult(null);
                //   logOnce("permission", "【伪造】本次请求所有权限均已勾选（全部伪造），已静默授予权限，无弹窗: " + Arrays.toString(permissions));
                return;
            }

            // 混合情况：按权限组拆分（LinkedHashMap 保持按原始请求顺序入组
            PackageManager pm = activity.getPackageManager();
            Map<String, List<String>> groupMap = new LinkedHashMap<String, List<String>>();
            for (String perm : permissions) {
                String group = getPermissionGroup(pm, perm);
                if (group == null) {
                    group = perm; // 独立权限，组名即自身
                }
                if (!groupMap.containsKey(group)) {
                    groupMap.put(group, new ArrayList<>());
                }
                groupMap.get(group).add(perm);
            }

            // 分类：真实组和伪造组
            List<String> realPerms = new ArrayList<>();
            List<String> fakePerms = new ArrayList<>();

            for (Map.Entry<String, List<String>> entry : groupMap.entrySet()) {
                boolean hasReal = false;
                for (String perm : entry.getValue()) {
                    if (!shouldFakePermission(perm)) {
                        hasReal = true;
                        break;
                    }
                }
                if (hasReal) {
                    realPerms.addAll(entry.getValue());
                } else {
                    fakePerms.addAll(entry.getValue());
                }
            }

            // 如果真实组为空
            if (realPerms.isEmpty()) {
                int[] grantResults = new int[permissions.length];
                Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
                deliverPermissionResult(activity, fragment, requestCode, permissions, grantResults);
                param.setResult(null);
                //  logOnce("permission", "【伪造(兜底)】本次请求权限已全部伪造授权: " + Arrays.toString(permissions));
                return;
            }

            // 如果伪造组为空，但 allReal 已处理过
            if (fakePerms.isEmpty()) {
                logOnce("permission", "权限防护：是未防护权限，放行: " + Arrays.toString(permissions));
                return;
            }

            // === 混合处理：先伪造 fakePerms，再发起真实请求 realPerms ===
            // 1. 伪造 fakePerms
            int[] fakeResults = new int[fakePerms.size()];
            Arrays.fill(fakeResults, PackageManager.PERMISSION_GRANTED);
            PermissionSplitHelper.storeFakeResult(requestCode, fakePerms, fakeResults);

            // 2. 记录原始请求
            PermissionSplitHelper.storeOriginalRequest(requestCode, permissions);

            // 3. 发起真实请求（仅包含 realPerms）
            if (fragment != null) {
                // Fragment 请求
                XposedHelpers.callMethod(fragment, "requestPermissions", realPerms.toArray(new String[0]), requestCode);
            } else {
                activity.requestPermissions(realPerms.toArray(new String[0]), requestCode);
            }
            // 阻止原始请求执行
            param.setResult(null);
            //logOnce("permission", "【拆分】本次请求存在混合状态：已勾选权限（伪造）: " + fakePerms + "，未勾选权限（真实授权，将弹窗）: " + realPerms);
            logOnce("permission", "权限防护-混合：防护中: " + fakePerms + "，未防护: " + realPerms);

        } catch (Throwable t) {
            // 异常时放行，避免影响应用
        }
    }

    // 权限组查询（带缓存）
    private String getPermissionGroup(PackageManager pm, String permission) {
        String cached = sPermissionGroupCache.get(permission);
        if (cached != null || sPermissionGroupCache.containsKey(permission)) {
            return cached;
        }
        try {
            PermissionInfo pi = pm.getPermissionInfo(permission, 0);
            String group = pi.group;
            sPermissionGroupCache.put(permission, group);
            return group;
        } catch (PackageManager.NameNotFoundException e) {
            sPermissionGroupCache.put(permission, null);
            return null;
        } catch (Throwable t) {
            sPermissionGroupCache.put(permission, null);
            return null;
        }
    }

    // 传递权限结果
    private void deliverPermissionResult(Activity activity, Object fragment, int requestCode, String[] permissions, int[] grantResults) {
        try {
            if (fragment != null) {
                Method method = fragment.getClass().getMethod("onRequestPermissionsResult",
                                                              int.class, String[].class, int[].class);
                method.setAccessible(true);
                method.invoke(fragment, requestCode, permissions, grantResults);
            } else {
                Method method = Activity.class.getDeclaredMethod("onRequestPermissionsResult",
                                                                 int.class, String[].class, int[].class);
                method.setAccessible(true);
                method.invoke(activity, requestCode, permissions, grantResults);
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    // 合并 onRequestPermissionsResult 回调
    private void hookOnRequestPermissionsResult() {
        try {
            XposedHelpers.findAndHookMethod(
                Activity.class,
                "onRequestPermissionsResult",
                int.class, String[].class, int[].class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        int requestCode = (int) param.args[0];
                        if (PermissionSplitHelper.isSplitRequest(requestCode)) {
                            String[] realPerms = (String[]) param.args[1];
                            int[] realResults = (int[]) param.args[2];
                            int[] merged = PermissionSplitHelper.mergeResults(requestCode, realPerms, realResults);
                            if (merged != null) {
                                param.args[1] = PermissionSplitHelper.getOriginalPermissions(requestCode).toArray(new String[0]);
                                param.args[2] = merged;
                                PermissionSplitHelper.clear(requestCode);
                                logOnce("permission", "权限防护：模拟回调requestCode=" + requestCode);
                            }
                        }
                    }
                }
            );

            // 同样 Hook Fragment 和 AndroidX Fragment 的 onRequestPermissionsResult
            try {
                Class<?> fragmentClass = Class.forName("android.app.Fragment");
                XposedHelpers.findAndHookMethod(
                    fragmentClass,
                    "onRequestPermissionsResult",
                    int.class, String[].class, int[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            int requestCode = (int) param.args[0];
                            if (PermissionSplitHelper.isSplitRequest(requestCode)) {
                                String[] realPerms = (String[]) param.args[1];
                                int[] realResults = (int[]) param.args[2];
                                int[] merged = PermissionSplitHelper.mergeResults(requestCode, realPerms, realResults);
                                if (merged != null) {
                                    param.args[1] = PermissionSplitHelper.getOriginalPermissions(requestCode).toArray(new String[0]);
                                    param.args[2] = merged;
                                    PermissionSplitHelper.clear(requestCode);
                                }
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}

            try {
                Class<?> fragmentCompatClass = Class.forName("androidx.fragment.app.Fragment");
                XposedHelpers.findAndHookMethod(
                    fragmentCompatClass,
                    "onRequestPermissionsResult",
                    int.class, String[].class, int[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            int requestCode = (int) param.args[0];
                            if (PermissionSplitHelper.isSplitRequest(requestCode)) {
                                String[] realPerms = (String[]) param.args[1];
                                int[] realResults = (int[]) param.args[2];
                                int[] merged = PermissionSplitHelper.mergeResults(requestCode, realPerms, realResults);
                                if (merged != null) {
                                    param.args[1] = PermissionSplitHelper.getOriginalPermissions(requestCode).toArray(new String[0]);
                                    param.args[2] = merged;
                                    PermissionSplitHelper.clear(requestCode);
                                }
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}

        } catch (Throwable t) {
            // 静默
        }
    }

    // 内部辅助类：存储拆分状态
    private static class PermissionSplitHelper {
        private static final Map<Integer, List<String>> sOriginalPerms = new ConcurrentHashMap<>();
        private static final Map<Integer, List<String>> sFakePerms = new ConcurrentHashMap<>();
        private static final Map<Integer, int[]> sFakeResults = new ConcurrentHashMap<>();
        private static final long CLEANUP_INTERVAL = 60000;
        private static long lastCleanupTime = 0;

        static void storeOriginalRequest(int requestCode, String[] perms) {
            sOriginalPerms.put(requestCode, Arrays.asList(perms));
            maybeCleanup();
        }

        static void storeFakeResult(int requestCode, List<String> fakePerms, int[] fakeResults) {
            sFakePerms.put(requestCode, fakePerms);
            sFakeResults.put(requestCode, fakeResults);
            maybeCleanup();
        }

        static void clear(int requestCode) {
            sOriginalPerms.remove(requestCode);
            sFakePerms.remove(requestCode);
            sFakeResults.remove(requestCode);
        }

        static boolean isSplitRequest(int requestCode) {
            return sOriginalPerms.containsKey(requestCode);
        }

        static List<String> getOriginalPermissions(int requestCode) {
            return sOriginalPerms.get(requestCode);
        }

        static int[] mergeResults(int requestCode, String[] realPerms, int[] realResults) {
            List<String> original = sOriginalPerms.get(requestCode);
            List<String> fakePerms = sFakePerms.get(requestCode);
            int[] fakeResults = sFakeResults.get(requestCode);
            if (original == null || fakePerms == null || fakeResults == null) {
                return null;
            }

            int[] merged = new int[original.size()];
            for (int i = 0; i < original.size(); i++) {
                String perm = original.get(i);
                // 按权限在 fake/real 列表中的实际位置取结果，不再依赖递增下标
                int fakePos = fakePerms.indexOf(perm);
                if (fakePos >= 0 && fakePos < fakeResults.length) {
                    merged[i] = fakeResults[fakePos];
                } else {
                    int realPos = indexOf(realPerms, perm);
                    if (realPos >= 0 && realPos < realResults.length) {
                        merged[i] = realResults[realPos];
                    } else {
                        // 未知权限（理论上 original = fake ∪ real）：保守拒绝
                        merged[i] = PackageManager.PERMISSION_DENIED;
                    }
                }
            }
            return merged;
        }

        private static int indexOf(String[] arr, String value) {
            if (arr == null || value == null) return -1;
            for (int i = 0; i < arr.length; i++) {
                if (value.equals(arr[i])) return i;
            }
            return -1;
        }

        private static void maybeCleanup() {
            long now = System.currentTimeMillis();
            if (now - lastCleanupTime < CLEANUP_INTERVAL) return;
            lastCleanupTime = now;
            if (sOriginalPerms.size() > 100) {
                sOriginalPerms.clear();
                sFakePerms.clear();
                sFakeResults.clear();
                ReaLog.log("permission", "权限拆分缓存已自动清理（超过100条）");
            }
        }
    }

    // ========== 其他权限 Hook 方法 ==========
    public void hookQueryAllPackagesPermission(ClassLoader classLoader) {
        try {
            // Android 13+ 预检测权限（静默处理）
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "checkPermissionForPreflight",
                    String.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String permission = (String) param.args[0];
                            String targetPackage = (String) param.args[1];
                            if (targetPackage == null || !targetPackage.equals(mHookInit.getCurrentTargetApp()))
                                return;
                            if (shouldFakePermission(permission)) {
                                param.setResult(PackageManager.PERMISSION_GRANTED);
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}

            // 拦截 PermissionInfo 查询
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPermissionInfo",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String permissionName = (String) param.args[0];
                            if (!shouldFakePermission(permissionName)) return;
                            try {
                                Class<?> permissionInfoClass = Class.forName("android.content.pm.PermissionInfo");
                                Object fakePermissionInfo = permissionInfoClass.newInstance();
                                XposedHelpers.setObjectField(fakePermissionInfo, "name", permissionName);
                                XposedHelpers.setObjectField(fakePermissionInfo, "packageName", mHookInit.getCurrentTargetApp());
                                XposedHelpers.setIntField(
                                    fakePermissionInfo,
                                    "protectionLevel",
                                    XposedHelpers.getStaticIntField(
                                        Class.forName("android.content.pm.PermissionInfo"),
                                        "PROTECTION_NORMAL"
                                    )
                                );
                                XposedHelpers.setIntField(fakePermissionInfo, "flags", 0);
                                param.setResult(fakePermissionInfo);
                            } catch (Throwable e) {
                                // 静默
                            }
                        }
                    }
                );
            } catch (Throwable t) {
                // 静默
            }
            // 兜底：基础权限检查
            hookBasicPermissionChecks(classLoader);
            logOnce("permission", "查询权限特有Hook完成");
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookTelephonyGetters(ClassLoader classLoader) {
        String[] methods = {
            "getDeviceId", "getImei", "getMeid",
            "getSubscriberId", "getSimSerialNumber",
            "getLine1Number", "getVoiceMailNumber",
            "getNai"
        };
        for (final String methodName : methods) {
            try {
                XposedHelpers.findAndHookMethod(
                    TelephonyManager.class,
                    methodName,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (!HookInit.permissionFakeMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) return;
                            Map<String, Boolean> detailMap = HookInit.permissionFakeDetailMap.get(mHookInit.getCurrentTargetApp());
                            if (detailMap != null && detailMap.getOrDefault("android.permission.READ_PHONE_STATE", false)) {
                                param.setResult("");
                                logOnce("permission", "空白通行证：成功拦截 " + methodName + "()");
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(
                    TelephonyManager.class,
                    methodName,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (!HookInit.permissionFakeMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) return;
                            Map<String, Boolean> detailMap = HookInit.permissionFakeDetailMap.get(mHookInit.getCurrentTargetApp());
                            if (detailMap != null && detailMap.getOrDefault("android.permission.READ_PHONE_STATE", false)) {
                                param.setResult("");
                                logOnce("permission", "空白通行证：成功拦截 " + methodName + "(int)");
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}
        }
        logOnce("permission", "空白通行证-电话权限 已安装");
    }

    public void hookContentResolverQuery(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                ContentResolver.class,
                "query",
                Uri.class, String[].class, String.class, String[].class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        if (!HookInit.permissionFakeMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                            return;
                        }
                        Uri uri = (Uri) param.args[0];
                        if (uri == null) return;
                        DataSourceGroup group = DataSourceGroup.match(uri);
                        if (group == null) return;
                        Map<String, Boolean> detailMap = HookInit.permissionFakeDetailMap.get(mHookInit.getCurrentTargetApp());
                        if (detailMap == null) detailMap = new HashMap<>();
                        boolean anyChecked = false;
                        for (String perm : group.permissions) {
                            if (detailMap.getOrDefault(perm, false)) {
                                anyChecked = true;
                                logOnce("permission", "空白通行证：防护 " + perm + " 生效，拦截 ContentProvider: " + uri);
                            }
                        }
                        if (anyChecked) {
                            String[] projection = (String[]) param.args[1];
                            if (projection == null || projection.length == 0) {
                                projection = getDefaultColumnsForUri(uri);
                            }
                            param.setResult(new MatrixCursor(projection));
                        }
                    }
                }
            );
            logOnce("permission", "空白通行证-通讯录等 已安装");
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookSpecialSystemApis(ClassLoader classLoader) {
        try {
            // 1. 拦截 startActivity 跳转设置页
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "startActivity",
                    Intent.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Intent intent = (Intent) param.args[0];
                            if (intent == null) return;
                            String action = intent.getAction();
                            if (action == null) return;
                            String permission = PERMISSION_INTENT_MAP.get(action);
                            if (permission != null && shouldFakePermission(permission)) {
                                Activity activity = (Activity) param.thisObject;
                                if (activity != null && !activity.isFinishing()) {
                                    simulatePermissionGranted(activity, intent);
                                    param.setResult(null);
                                    logOnce("permission", "模拟授权成功: " + action + " (" + permission + ")");
                                }
                            }
                        }
                    }
                );
            } catch (Throwable t) {
                // 静默
            }

            // 2. 特殊 API 伪造
            try {
                // 悬浮窗 (API 23+)
                XposedHelpers.findAndHookMethod(
                    "android.provider.Settings",
                    classLoader,
                    "canDrawOverlays",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (shouldFakePermission("android.permission.SYSTEM_ALERT_WINDOW")) {
                                param.setResult(true);
                                logOnce("permission", "悬浮窗权限 ->true");
                            }
                        }
                    }
                );

                // 安装未知应用 (API 26+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        Class<?> pmClass = XposedHelpers.findClassIfExists("android.app.ApplicationPackageManager", classLoader);
                        if (pmClass != null) {
                            XposedHelpers.findAndHookMethod(
                                pmClass,
                                "canRequestPackageInstalls",
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                                        if (shouldFakePermission("android.permission.REQUEST_INSTALL_PACKAGES")) {
                                            param.setResult(true);
                                            logOnce("permission", "安装未知应用权限 ->true");
                                        }
                                    }
                                }
                            );
                        }
                    } catch (Throwable t) {
                        // 静默
                    }
                }

                // 电池优化 (API 23+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        Class<?> powerManagerClass = XposedHelpers.findClassIfExists("android.os.PowerManager", classLoader);
                        if (powerManagerClass != null) {
                            XposedHelpers.findAndHookMethod(
                                powerManagerClass,
                                "isIgnoringBatteryOptimizations",
                                String.class,
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                                        String pkg = (String) param.args[0];
                                        if (pkg != null && pkg.equals(mHookInit.getCurrentTargetApp())) {
                                            if (shouldFakePermission("android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) {
                                                param.setResult(true);
                                                logOnce("permission", "电池优化权限 ->true");
                                            }
                                        }
                                    }
                                }
                            );
                        }
                    } catch (Throwable t) {
                        // 静默
                    }
                }

                // 所有文件管理 (API 30+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        XposedHelpers.findAndHookMethod(
                            "android.os.Environment",
                            classLoader,
                            "isExternalStorageManager",
                            new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                                    if (shouldFakePermission("android.permission.MANAGE_EXTERNAL_STORAGE")) {
                                        param.setResult(true);
                                        logOnce("permission", "所有文件管理权限 ->true");
                                    }
                                }
                            }
                        );
                    } catch (Throwable t) {
                        // 静默
                    }
                }
            } catch (Throwable t) {
                // 静默
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    //拦截查询第三方账号
    public void hookAccountManager(ClassLoader classLoader) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists("android.accounts.AccountManager", classLoader);
            if (clazz == null) return;

            XposedHelpers.findAndHookMethod(
                clazz,
                "getAccountsByType",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        String accountType = (String) param.args[0];
                        if (accountType != null && accountType.contains(mHookInit.getCurrentTargetApp())) {
                            return;
                        }
                        if (mHookInit.isSystemCorePackage(mHookInit.getCurrentTargetApp())
                            && mHookInit.isRealSystemPackage(mHookInit.getCurrentTargetApp())) {
                            return;
                        }
                        param.setResult(new android.accounts.Account[0]);
                        logOnce("permission", "账户管理: 拦截查询第三方账号 -> accountType=" + accountType);
                    }
                }
            );

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getAccounts",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (mHookInit.isSystemCorePackage(mHookInit.getCurrentTargetApp())
                                && mHookInit.isRealSystemPackage(mHookInit.getCurrentTargetApp())) {
                                return;
                            }
                            param.setResult(new android.accounts.Account[0]);
                            logOnce("permission", "账户管理(Accounts): 拦截查询第三方账号");
                        }
                    }
                );
            } catch (Throwable ignored) {}

            try {
                XposedHelpers.findAndHookMethod(
                    clazz,
                    "getAccountsByTypeAndFeatures",
                    String.class, String[].class, android.accounts.AccountManagerCallback.class, android.os.Handler.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String accountType = (String) param.args[0];
                            if (accountType != null && accountType.contains(mHookInit.getCurrentTargetApp())) {
                                return;
                            }
                            if (mHookInit.isSystemCorePackage(mHookInit.getCurrentTargetApp())
                                && mHookInit.isRealSystemPackage(mHookInit.getCurrentTargetApp())) {
                                return;
                            }
                            param.setResult(null);
                            logOnce("permission", "账户管理(带特征): 拦截");
                        }
                    }
                );
            } catch (Throwable ignored) {}

            logOnce("permission", "账户管理 Hook 完成");
        } catch (Throwable t) {
            // 静默
        }
    }

    //设置写入权限
    public void hookWriteSettingsPermission(ClassLoader classLoader) {
        try {
            Class<?> settingsClass = Class.forName("android.provider.Settings$System");
            XposedHelpers.findAndHookMethod(
                settingsClass,
                "canWrite",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        if (shouldFakePermission("android.permission.WRITE_SETTINGS")) {
                            param.setResult(true);
                            logOnce("permission", "设置写入授权返回 ->true");
                        }
                    }
                }
            );
            logOnce("permission", "已Hook底层权限检测");
        } catch (Throwable t) {
            // 静默
        }
    }

    //AppOpsManager
    public void hookAppOpsForAllPermissions(ClassLoader classLoader) {
        try {
            final Class<?> appOpsClass = Class.forName("android.app.AppOpsManager");
            final Method permissionToOpMethod;
            try {
                permissionToOpMethod = appOpsClass.getMethod("permissionToOp", String.class);
            } catch (NoSuchMethodException e) {
                return;
            }

            XC_MethodHook dynamicHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    Object opObj = null;
                    for (Object arg : param.args) {
                        if (arg instanceof Integer || arg instanceof String) {
                            opObj = arg;
                            break;
                        }
                    }
                    if (opObj == null) return;

                    Set<String> permsToFake = new HashSet<>();
                    Map<String, Boolean> detailMap = HookInit.permissionFakeDetailMap.get(mHookInit.getCurrentTargetApp());
                    if (detailMap != null) {
                        for (Map.Entry<String, Boolean> entry : detailMap.entrySet()) {
                            if (entry.getValue()) permsToFake.add(entry.getKey());
                        }
                    }
                    Boolean globalFake = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                    if (globalFake == null || globalFake) {
                        permsToFake.addAll(DETECTION_PERMISSIONS_SET);
                    }

                    for (String perm : permsToFake) {
                        try {
                            int expectedOp = (int) permissionToOpMethod.invoke(null, perm);
                            if (expectedOp == -1) continue;
                            if (opObj instanceof Integer && (int) opObj == expectedOp) {
                                param.setResult(AppOpsManager.MODE_ALLOWED);
                                logOnce("permission", "AppOps 授权: " + perm + " (op=" + expectedOp + ")");
                                return;
                            }
                            if (opObj instanceof String) {
                                String expectedOpStr = getOpStringFromCode(appOpsClass, expectedOp);
                                if (expectedOpStr != null && expectedOpStr.equals(opObj)) {
                                    param.setResult(AppOpsManager.MODE_ALLOWED);
                                    logOnce("permission", "AppOps 授权(字符串): " + perm + " (op=" + expectedOpStr + ")");
                                    return;
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            };

            String[] methodNames = {"checkOp", "checkOpNoThrow", "noteOp", "noteOpNoThrow"};
            for (String methodName : methodNames) {
                try {
                    XposedHelpers.findAndHookMethod(appOpsClass, methodName, int.class, int.class, String.class, dynamicHook);
                } catch (Throwable ignored) {}
                try {
                    XposedHelpers.findAndHookMethod(appOpsClass, methodName, String.class, int.class, String.class, dynamicHook);
                } catch (Throwable ignored) {}
            }
            logOnce("permission", "AppOps Hook 完成");
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookOverlayAppOps(ClassLoader classLoader) {
        try {
            Class<?> appOpsClass = Class.forName("android.app.AppOpsManager");
            final int OP_SYSTEM_ALERT_WINDOW = 24;

            XposedHelpers.findAndHookMethod(
                appOpsClass,
                "checkOp",
                int.class, int.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        int op = (int) param.args[0];
                        if (op == OP_SYSTEM_ALERT_WINDOW && shouldFakePermission("android.permission.SYSTEM_ALERT_WINDOW")) {
                            param.setResult(AppOpsManager.MODE_ALLOWED);
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                appOpsClass,
                "checkOpNoThrow",
                int.class, int.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        int op = (int) param.args[0];
                        if (op == OP_SYSTEM_ALERT_WINDOW && shouldFakePermission("android.permission.SYSTEM_ALERT_WINDOW")) {
                            param.setResult(AppOpsManager.MODE_ALLOWED);
                        }
                    }
                }
            );
            logOnce("permission", "AppOps安装完成");
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookPackageUsageStatsPermission(ClassLoader classLoader) {
        try {
            Boolean shouldFakePermission = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
            boolean fakeEnabled = shouldFakePermission != null ? shouldFakePermission : true;
            if (!fakeEnabled) {
                return;
            }

            // queryUsageStats (API 21+)
            try {
                XposedHelpers.findAndHookMethod(
                    "android.app.usage.UsageStatsManager",
                    classLoader,
                    "queryUsageStats",
                    int.class, long.class, long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            try {
                                Boolean shouldFake = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                                if (shouldFake != null && !shouldFake) return;
                                List<Object> fakeList = new ArrayList<>();
                                long offset = HookInit.sFakeTimeOffset != 0 ? HookInit.sFakeTimeOffset : 0;
                                for (String pkg : HookInit.globalCapturedPackages) {
                                    Object stats = createFakeUsageStats(pkg, offset);
                                    if (stats != null) fakeList.add(stats);
                                }
                                param.setResult(fakeList);
                            } catch (Throwable t) {
                                // 静默
                            }
                        }
                    }
                );
            } catch (Throwable t) {
                // 静默
            }

            // getUsageStatsForPackage (API 23+) — 静默处理
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    XposedHelpers.findAndHookMethod(
                        "android.app.usage.UsageStatsManager",
                        classLoader,
                        "getUsageStatsForPackage",
                        String.class, long.class, long.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                Boolean shouldFake = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                                if (shouldFake != null && !shouldFake) return;
                                String pkg = (String) param.args[0];
                                List<Object> fakeList = new ArrayList<>();
                                if (HookInit.globalCapturedPackages.contains(pkg)) {
                                    Object stats = createFakeUsageStats(pkg, HookInit.sFakeTimeOffset);
                                    if (stats != null) fakeList.add(stats);
                                }
                                param.setResult(fakeList);
                            }
                        }
                    );
                } catch (Throwable t) {
                    // 完全静默
                }
            }
        } catch (Throwable t) {
            // 顶层静默
        }
    }

    // ========== 其他权限方法 ==========
    public void hookShortcutAndWidgetFake(ClassLoader classLoader) {
        try {
            Class<?> shortcutManagerClass = XposedHelpers.findClassIfExists(
                "android.content.pm.ShortcutManager", classLoader);
            if (shortcutManagerClass != null) {
                XposedHelpers.findAndHookMethod(
                    shortcutManagerClass,
                    "isRequestPinShortcutSupported",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Boolean fakeEnabled = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                            if (fakeEnabled != null && fakeEnabled) {
                                param.setResult(true);
                                logOnce("permission", "请求快捷方式授权 -> true");
                            }
                        }
                    }
                );
                try {
                    XposedHelpers.findAndHookMethod(
                        shortcutManagerClass,
                        "isRateLimitingActive",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                                Boolean fakeEnabled = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                                if (fakeEnabled != null && fakeEnabled) {
                                    param.setResult(false);
                                    logOnce("permission", "快捷方式是否被系统禁用 -> false");
                                }
                            }
                        }
                    );
                } catch (Throwable ignored) {}
            }

            Class<?> appWidgetManagerClass = XposedHelpers.findClassIfExists(
                "android.appwidget.AppWidgetManager", classLoader);
            if (appWidgetManagerClass != null) {
                XposedHelpers.findAndHookMethod(
                    appWidgetManagerClass,
                    "isRequestPinAppWidgetSupported",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Boolean fakeEnabled = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                            if (fakeEnabled != null && fakeEnabled) {
                                param.setResult(true);
                                logOnce("permission", "桌面组件授权 -> true");
                            }
                        }
                    }
                );
            }
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookAppWidgetManager(ClassLoader classLoader) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists("android.appwidget.AppWidgetManager", classLoader);
            if (clazz == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(
                clazz,
                "getInstalledProviders",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        List<?> providers = (List<?>) param.getResult();
                        if (providers == null || providers.isEmpty()) {
                            return;
                        }
                        String targetPkg = mHookInit.getCurrentTargetApp();
                        if (targetPkg == null || targetPkg.isEmpty()) return;
                        List<Object> filtered = new ArrayList<>();
                        for (Object info : providers) {
                            String pkg = null;
                            try {
                                Object providerField = XposedHelpers.getObjectField(info, "provider");
                                if (providerField instanceof android.content.ComponentName) {
                                    pkg = ((android.content.ComponentName) providerField).getPackageName();
                                } else if (providerField instanceof String) {
                                    pkg = (String) providerField;
                                }
                                if (pkg == null) {
                                    pkg = (String) XposedHelpers.getObjectField(info, "packageName");
                                }
                            } catch (Throwable ignored) {}
                            if (pkg != null && !pkg.equals(targetPkg) && !mHookInit.isSystemCorePackage(pkg)) {
                                filtered.add(info);
                            }
                        }
                        param.setResult(filtered);
                        logOnce("permission", "过滤小部件: 原始 " + providers.size() + " 个，过滤后 " + filtered.size() + " 个");
                    }
                }
            );
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookAccessibilityManager(ClassLoader classLoader) {
        try {
            Class<?> amClass = XposedHelpers.findClassIfExists(
                "android.view.accessibility.AccessibilityManager", classLoader);
            if (amClass == null) return;

            // 1. getInstalledAccessibilityServiceList
            XposedHelpers.findAndHookMethod(
                amClass,
                "getInstalledAccessibilityServiceList",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        List<AccessibilityServiceInfo> list =
                            (List<AccessibilityServiceInfo>) param.getResult();
                        if (list == null) return;
                        List<AccessibilityServiceInfo> filtered = new ArrayList<>();
                        String selfPkg = mHookInit.getCurrentTargetApp();
                        for (AccessibilityServiceInfo info : list) {
                            String pkg = null;
                            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                                pkg = info.getResolveInfo().serviceInfo.packageName;
                            }
                            if (pkg != null && (mHookInit.isSystemCorePackage(pkg) || pkg.equals(selfPkg))) {
                                filtered.add(info);
                            }
                        }
                        param.setResult(filtered);
                        // 构建列表字符串
                        StringBuilder sb = new StringBuilder();
                        for (AccessibilityServiceInfo info : filtered) {
                            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                                String pkg = info.getResolveInfo().serviceInfo.packageName;
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(pkg);
                            }
                        }
                        String listStr = sb.toString();
                        logOnce("permission", "无障碍防护: 过滤后 " + filtered.size() + " 个服务: [" + listStr + "]");
                    }
                }
            );

            // 2. getEnabledAccessibilityServiceList(int)
            XposedHelpers.findAndHookMethod(
                amClass,
                "getEnabledAccessibilityServiceList",
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        List<AccessibilityServiceInfo> list =
                            (List<AccessibilityServiceInfo>) param.getResult();
                        if (list == null) return;
                        List<AccessibilityServiceInfo> filtered = new ArrayList<>();
                        String selfPkg = mHookInit.getCurrentTargetApp();
                        for (AccessibilityServiceInfo info : list) {
                            String pkg = null;
                            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                                pkg = info.getResolveInfo().serviceInfo.packageName;
                            }
                            if (pkg != null && (mHookInit.isSystemCorePackage(pkg) || pkg.equals(selfPkg))) {
                                filtered.add(info);
                            }
                        }
                        param.setResult(filtered);
                        StringBuilder sb = new StringBuilder();
                        for (AccessibilityServiceInfo info : filtered) {
                            if (info.getResolveInfo() != null && info.getResolveInfo().serviceInfo != null) {
                                String pkg = info.getResolveInfo().serviceInfo.packageName;
                                if (sb.length() > 0) sb.append(", ");
                                sb.append(pkg);
                            }
                        }
                        String listStr = sb.toString();
                        logOnce("permission", "无障碍防护: 过滤后 " + filtered.size() + " 个服务: [" + listStr + "]");
                    }
                }
            );
        } catch (Throwable t) {
            // 静默
        }
    }

    public void hookSettingsProvider(ClassLoader classLoader) {
        final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
                                                             "enabled_accessibility_services",
                                                             "enabled_notification_listeners",
                                                             "enabled_input_methods",
                                                             "enabled_vr_listeners",
                                                             "enabled_autofill_services"
                                                         ));
        hookSettingsGetString("android.provider.Settings$Secure", classLoader, SENSITIVE_KEYS);
        hookSettingsGetString("android.provider.Settings$Global", classLoader, SENSITIVE_KEYS);
        hookSettingsGetString("android.provider.Settings$System", classLoader, SENSITIVE_KEYS);
        // logOnce("permission", "已Hook 辅助数据查询");
    }

    // ========== 辅助方法 ==========
    //hook基本权限检查
    private void hookBasicPermissionChecks(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.ContextImpl",
                classLoader,
                "checkPermission",
                String.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                        Boolean shouldFakePermission = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                        boolean fakePermissionEnabled = shouldFakePermission != null ? shouldFakePermission : true;
                        if (!fakePermissionEnabled) {
                            logOnce("permission", "权限防护已关闭，跳过检查");
                            return;
                        }
                        String permission = (String) param.args[0];
                        if (DETECTION_PERMISSIONS_SET.contains(permission)) {
                            param.setResult(PackageManager.PERMISSION_GRANTED);
                            logOnce("permission", "权限检查授权: " + permission);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // 静默
        }
    }

    //创建虚假的使用统计
    private Object createFakeUsageStats(String packageName, long fakeOffset) {
        try {
            Class<?> statsClass = Class.forName("android.app.usage.UsageStats");
            Object stats = statsClass.newInstance();
            long now = System.currentTimeMillis() - fakeOffset;
            XposedHelpers.setObjectField(stats, "mPackageName", packageName);
            XposedHelpers.setLongField(stats, "mTotalTimeInForeground", fakeOffset + 1000);
            XposedHelpers.setLongField(stats, "mLastTimeUsed", now);
            XposedHelpers.setLongField(stats, "mLastTimeForeground", now);
            return stats;
        } catch (Throwable t) {
            return null;
        }
    }

    //模拟授权回调
    private void simulatePermissionGranted(Activity activity, Intent originalIntent) {
        try {
            int requestCode = 0;
            if (originalIntent != null) {
                requestCode = originalIntent.getIntExtra("request_code", 0);
            }
            if (requestCode == 0) {
                try {
                    Object field = XposedHelpers.getObjectField(activity, "mRequestCode");
                    if (field instanceof Integer) requestCode = (Integer) field;
                } catch (Throwable ignored) {}
            }
            Method method = Activity.class.getDeclaredMethod("onActivityResult",
                                                             int.class, int.class, Intent.class);
            method.setAccessible(true);
            Intent resultIntent = new Intent();
            method.invoke(activity, requestCode, Activity.RESULT_OK, resultIntent);
            logOnce("permission", "模拟授权回调成功, requestCode=" + requestCode);
        } catch (Throwable t) {
            // 静默
        }
    }

    //获取操作字符串
    private String getOpStringFromCode(Class<?> appOpsClass, int code) {
        try {
            Field[] fields = appOpsClass.getDeclaredFields();
            for (Field f : fields) {
                if (f.getName().startsWith("OPSTR_") && f.getType() == String.class) {
                    String opStr = (String) f.get(null);
                    String constName = f.getName().substring(6);
                    Field codeField = appOpsClass.getDeclaredField("OP_" + constName);
                    if (codeField.getType() == int.class && codeField.getInt(null) == code) {
                        return opStr;
                    }
                }
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    //获取Uri的默认列
    private String[] getDefaultColumnsForUri(Uri uri) {
        if (uri == null) return new String[]{"_id"};
        String auth = uri.getAuthority();
        if (auth == null) return new String[]{"_id"};
        String lowerAuth = auth.toLowerCase();
        if (lowerAuth.contains("contacts") || lowerAuth.equals("com.android.contacts")) {
            return new String[]{"_id", "display_name", "lookup", "photo_uri"};
        }
        if (lowerAuth.equals("sms") || lowerAuth.equals("mms") || lowerAuth.equals("mms-sms")) {
            return new String[]{"_id", "address", "body", "date", "type"};
        }
        if (lowerAuth.equals("call_log") || lowerAuth.contains("call_log")) {
            return new String[]{"_id", "number", "date", "duration", "type", "name"};
        }
        if (lowerAuth.contains("calendar")) {
            return new String[]{"_id", "title", "dtstart", "dtend", "eventLocation"};
        }
        return new String[]{"_id"};
    }

    //系统申请询问
    private boolean shouldFakePermission(String permission) {
        if (permission == null) return false;
        // 调试模式总门控：权限防护被彻底禁用时，全部（check/request/query/app_ops…）一律不伪造。
        if (!DebugModeManager.isFeatureEnabled("perm_guard_gate")) return false;
        // 1. 优先检查用户勾选的细粒度配置（最高优先级）
        Map<String, Boolean> detailMap = HookInit.permissionFakeDetailMap.get(mHookInit.getCurrentTargetApp());
        if (detailMap != null && detailMap.containsKey(permission)) {
            return detailMap.get(permission); // 用户明确勾选 → true，取消 → false
        }
        // 2. 用户未明确设置，则判断是否在检测权限集合中
        if (DETECTION_PERMISSIONS_SET.contains(permission)) {
            Boolean globalFake = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
            return (globalFake == null || globalFake); // 全局开关决定
        }
        // 3. 非检测权限且用户未设置 → 放行
        return false;
    }

    //获取字符串
    private void hookSettingsGetString(final String className, ClassLoader classLoader,
                                       final Set<String> sensitiveKeys) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, classLoader);
            if (clazz == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(
                clazz,
                "getString",
                ContentResolver.class, String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        String key = (String) param.args[1];
                        if (!sensitiveKeys.contains(key)) return;
                        String value = (String) param.getResult();
                        if (value == null || value.isEmpty()) return;
                        String targetPkg = mHookInit.getCurrentTargetApp();
                        if (targetPkg == null || targetPkg.isEmpty()) return;
                        String[] parts = value.split(":");
                        StringBuilder filtered = new StringBuilder();
                        boolean modified = false;
                        for (String part : parts) {
                            if (part.contains(targetPkg)) {
                                modified = true;
                                continue;
                            }
                            if (filtered.length() > 0) filtered.append(":");
                            filtered.append(part);
                        }
                        if (modified) {
                            param.setResult(filtered.toString());
                            logOnce("permission", "Settings." + className + ".getString 过滤包名: " + key);
                        }
                    }
                }
            );
        } catch (Throwable t) {
            // 静默
        }
    }
}

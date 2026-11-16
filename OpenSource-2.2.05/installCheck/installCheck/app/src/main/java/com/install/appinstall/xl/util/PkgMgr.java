package com.install.appinstall.xl.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import android.content.IntentFilter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.ActivityNotFoundException;
import java.lang.reflect.InvocationHandler;
import android.os.IBinder;
import java.lang.reflect.Proxy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.DebugModeManager;

/** 包管理核心功能模块 包含：包名捕获、安装状态伪造、智能数据生成、包管理 Hook、命令行检测等 */
public class PkgMgr {

    private final HookInit mHookInit;

    /** 向 BuHook 仓库注册本组件提供的全部挂载功能（注册幂等，重复调用安全）。 挂载时机由仓库统一调度（门控/幂等/熔断/热重载补挂）。 */
    public void registerHooks() {
        // === 极速组前置：包管理缓存预加载（无需门控） ===
        BuHook.registerSystem("pkg_preload_cache", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                preloadPackageInfoCache();
            }
        });

        // === 极速组：包管理配置 ===
        BuHook.register("hook_get_package_info", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetPackageInfo(cl);
            }
        });
        BuHook.register("hook_get_application_info", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetApplicationInfo(cl);
            }
        });
        BuHook.register("hook_get_installed_packages", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetInstalledPackages(cl);
            }
        });
        BuHook.register("hook_get_installed_applications", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetInstalledApplications(cl);
            }
        });
        BuHook.register("hook_get_package_info_as_user", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetPackageInfoAsUser(cl);
            }
        });
        BuHook.register("hook_installed_packages_as_user", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetInstalledPackagesAsUser(cl);
            }
        });
        BuHook.register("hook_installed_applications_as_user", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetInstalledApplicationsAsUser(cl);
            }
        });
        BuHook.register("hook_package_installer", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookPackageInstaller(cl);
                installExtraCaptureHooks(cl);
            }
        });
        BuHook.register("hook_is_application_enabled", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookIsApplicationEnabled(cl);
            }
        });
        BuHook.register("hook_get_activity_info", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetActivityInfo(cl);
            }
        });
        BuHook.register("hook_get_installer_package_name", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGetInstallerPackageName(cl);
            }
        });

        // === 普通组：检测拦截类 ===
        BuHook.register("hook_reflect_invoke", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookReflectInstallCheck(cl);
            }
        });
        BuHook.register("hook_okhttp", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookOkHttp(cl);
            }
        });
        BuHook.register("hook_runtime_exec", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookRuntimeExecMethods(cl);
            }
        });
        BuHook.register("hook_process_builder", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookProcessBuilder(cl);
            }
        });
        BuHook.register("hook_file_exists", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookFileSystemInstallCheck(cl);
            }
        });
        BuHook.register("hook_data_app_listing", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookDataAppDirectoryListing(cl);
            }
        });
        BuHook.register("hook_lib_directory", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookLibDirectoryChecks(cl);
            }
        });
        BuHook.register("hook_os_stat", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookOsLibcoreStat(cl);
            }
        });
        BuHook.register("hook_os_readdir", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookOsLibcoreReaddir(cl);
            }
        });
        BuHook.register("hook_file_length", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookFileLength(cl);
            }
        });
        BuHook.register("hook_proc_files", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookProcFileSystem(cl);
            }
        });
        BuHook.register("hook_process_memory", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookProcessMemoryInfo(cl);
            }
        });
        BuHook.register("hook_random_access_file", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookRandomAccessFile(cl);
            }
        });
    }

    // ---------- 基础系统包常量 ----------
    private static final String[] BASE_SYSTEM_PACKAGES = {
            "root", "system", "android", "com.android.", "de.robv.android.",
            "com.google.", "com.google.android", "com.google.android.gms",
            "com.google.android.webview", "org.lsposed.", "com.lsposed.",
            "com.topjohnwu.", "io.va.exposed", "org.meowcat.edxposed."
    };
    // ---------- 强制包前缀/过滤列表常量 ----------
    private static final String[] VENDOR_PACKAGE_PREFIXES = {
            "com.qualcomm", "com.samsung", "com.huawei", "com.hihonor",
            "com.miui", "com.xiaomi", "com.mi", "com.poco", "com.redmi",
            "com.oppo", "com.oplus", "com.oneplus", "com.realme", "com.heytap",
            "com.coloros", "com.oppo.nearme", "com.oppo.global", "com.realme.global",
            "com.vivo", "com.vivo.global", "com.vivo.browser",
            "com.meizu", "com.flyme", "com.lenovo", "com.zuk", "com.motorola",
            "com.asus", "com.nokia", "com.sony", "com.lg", "com.sharp",
            "com.infinix", "com.tecno", "com.itel", "com.blackshark",
            "cn.nubia", "com.zte", "com.acer", "com.bbk", "com.osp",
            "com.sonyericsson", "com.yulong", "com.transsion"
    };

    // ========== 社交应用伪造版本表(用于假装分享)==========
    private static final Map<String, String[]> FAKE_VERSION_TABLE;

    static {
        Map<String, String[]> m = new HashMap<>();
        // 小Q 系列
        m.put("com.tencent.mobileqq", new String[]{"9.2.60", "9260", "33", "21"});
        m.put("com.tencent.qqlite", new String[]{"9.2.60", "9260", "33", "21"});
        m.put("com.tencent.tim", new String[]{"4.1.0", "410", "33", "21"});
        // 小微系列
        m.put("com.tencent.mm", new String[]{"8.0.78", "8078", "33", "21"});
        m.put("com.tencent.wework", new String[]{"5.0.11", "5011", "33", "21"});
        // 小搏
        m.put("com.sina.weibo", new String[]{"16.9.2", "16902", "33", "21"});
        // 小钉
        m.put("com.alibaba.android.rimet", new String[]{"9.0.2", "902", "33", "21"});
        FAKE_VERSION_TABLE = Collections.unmodifiableMap(m);
    }

/** 查表；命中返回 {versionName, versionCode, targetSdk, minSdk}，未命中返回 null */
    private static String[] lookupFakeVersion(String pkg) {
        if (pkg == null) return null;
        String[] v = FAKE_VERSION_TABLE.get(pkg);
        if (v != null) return v;
        if (pkg.startsWith("com.tencent.")) {
            return new String[]{"9.2.60", "9260", "33", "21"};
        }
        return null;
    }

    private static int parseIntSafe(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return def;
        }
    }

/** API 28+ 的 longVersionCode 安全设置 */
    private void setLongVersionCodeSafe(PackageInfo pi, long code) {
        if (pi == null) return;
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            XposedHelpers.setLongField(pi, "longVersionCode", code);
        } catch (Throwable ignored) {
        }
    }

    //
    // ========== 社交分享 SDK 自检放行（分层：SDK 自检拿真实结果，检测调用仍走伪造）==========
    // 分享平台包清单（与 ShareHook 的 PKG_WX/PKG_QQ_ARR/PKG_WB/PKG_DD 保持同步）：
    // 小微 com.tencent.mm / QQ系 com.tencent.mobileqq,qqlite,tim / 小搏 com.sina.weibo / 小钉
    // com.alibaba.android.rimet
    private static final String[] SHARE_PLATFORM_PACKAGES = {
            "com.tencent.mm",
            "com.tencent.mobileqq", "com.tencent.qqlite", "com.tencent.tim",
            "com.sina.weibo",
            "com.alibaba.android.rimet"
    };
    private static final String[] SHARE_SDK_STACK_PREFIXES = {
            "com.tencent.mm.opensdk.", // 小微 SDK
            "com.tencent.tauth.", // 小Q 互联
            "com.tencent.connect.", // 小Q 互联（旧版）
            "com.sina.weibo.sdk.", // 小搏 SDK
            "com.sina.weibo.share.", // 小搏 SDK
            "com.alibaba.android.rimet.share.", // 小钉
            "com.android.dingtalk.share." // 小钉2
    };
    private static final java.util.Set<
            String> sSdkPassLogged = Collections.synchronizedSet(new HashSet<String>());

    /** 是否为社交分享平台包 */
    private static boolean isSharePlatformPackage(String packageName) {
        if (packageName == null) return false;
        for (String pkg : SHARE_PLATFORM_PACKAGES) {
            if (pkg.equals(packageName)) return true;
        }
        return false;
    }

    /**
     * 调用栈命中社交 SDK 特征类：识别本次查询是否出自分享 SDK 内部自检（拉起小微/小Q/小搏/小钉前的 包可用性校验）。限前 12 层栈帧——SDK 自检栈很浅，业务代码不会误命中
     * SDK 类前缀。
     */
    private static boolean calledFromSharePlatformSdk() {
        try {
            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            int limit = Math.min(frames.length, 12);
            for (int i = 0; i < limit; i++) {
                String cls = frames[i].getClassName();
                if (cls == null) continue;
                for (String prefix : SHARE_SDK_STACK_PREFIXES) {
                    if (cls.startsWith(prefix)) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * 分层判定：平台包 + 应报"已安装"（未装/固定未装的语义保留不受影响）+ SDK 自检调用 → 返回 true 由调用方据此 return 放行原方法，SDK 拿到真实
     * PackageInfo/ActivityInfo（签名/组件/版本皆真）， 分享自检得以通过、正常跳转；其余场景（含检测代码查询）一律走原伪造逻辑。
     */
    private boolean shouldPassShareSdkQuery(String packageName) {
        if (!isSharePlatformPackage(packageName)) return false;
        if (!shouldReturnInstalledForPackage(packageName)) return false;
        if (!calledFromSharePlatformSdk()) return false;
        if (sSdkPassLogged.add(packageName)) {
            ReaLog.log("package_query", "社交SDK自检放行(真实查询): " + packageName);
        }
        return true;
    }

// ====================  Flutter常量通道检测名单====================
    private static final Set<String> FLUTTER_WHITELIST_CHANNELS;

    static {
        Set<String> set = new HashSet<String>();
        set.add("plugins");
        set.add("detection");
        set.add("package");
        set.add("check");
        FLUTTER_WHITELIST_CHANNELS = Collections.unmodifiableSet(set);
    }

    private static final String FALLBACK_PACKAGE = "com.小淋.虚假APP";

    private static final Pattern DATA_APP_PATTERN = Pattern.compile("/data/app/([a-zA-Z0-9._]+)-\\d+");
    private static final Pattern DATA_DATA_PATTERN = Pattern.compile("/data/data/([a-zA-Z0-9._]+)");
    private static final Pattern DATA_USER_PATTERN = Pattern.compile("/data/user/\\d+/([a-zA-Z0-9._]+)");
    private static final Pattern BASE_APK_PATTERN = Pattern.compile("/data/app/~~[^/]*~~/([a-zA-Z0-9._]+)/base\\.apk");
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");

    // 空数组常量（性能优化）
    private static final ActivityInfo[] EMPTY_ACTIVITY_INFO = new ActivityInfo[0];
    private static final ServiceInfo[] EMPTY_SERVICE_INFO = new ServiceInfo[0];
    private static final ActivityInfo[] EMPTY_RECEIVER_INFO = new ActivityInfo[0];
    private static final ProviderInfo[] EMPTY_PROVIDER_INFO = new ProviderInfo[0];
    private static final PermissionInfo[] EMPTY_PERMISSION_INFO = new PermissionInfo[0];
    private static final Signature[] FAKE_SIGNATURES = new Signature[]{new Signature("fake".getBytes())};

    // 智能伪造缓存
    private static final int MAX_CACHE_SIZE = 4096;
    private final Map<
            String, PackageInfo> sPackageInfoCache = Collections.synchronizedMap(new LinkedHashMap<
            String, PackageInfo>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PackageInfo> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    });
    private final Map<
            String, ApplicationInfo> sAppInfoCache = Collections.synchronizedMap(new LinkedHashMap<
            String, ApplicationInfo>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ApplicationInfo> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    });
    // 智能数据缓存：hook 回调来自宿主任意线程，使用并发集合避免并发修改异常
    private final Map<String, String> versionCache = new ConcurrentHashMap<String, String>();
    private final Map<String, Integer> versionCodeCache = new ConcurrentHashMap<String, Integer>();
    private final Map<String, Long> installTimeCache = new ConcurrentHashMap<String, Long>();
    private final Map<String, String> installerCache = new ConcurrentHashMap<String, String>();
    private final Map<String, String> appNameCache = new ConcurrentHashMap<String, String>();
    private final List<QueryPattern> queryPatterns = Collections.synchronizedList(new ArrayList<
            QueryPattern>());
    private final Random random = new Random();

    // 内部查询模式记录
    private static class QueryPattern {
        String targetApp;
        String queriedPackage;
        int flags;
        long timestamp;

        QueryPattern(String targetApp, String queriedPackage, int flags) {
            this.targetApp = targetApp;
            this.queriedPackage = queriedPackage;
            this.flags = flags;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // ---------- 构造 ----------
    public PkgMgr(HookInit hookInit) {
        mHookInit = hookInit;
    }

    // 安装所有 Flutter 相关 Hook
    public void installFlutterHooks(ClassLoader classLoader) {
        // 如果 Flutter 功能被全局禁用，直接返回
        if (!DebugModeManager.isFeatureEnabled("hook_flutter_method_channel") &&
                !DebugModeManager.isFeatureEnabled("hook_flutter_package_info") &&
                !DebugModeManager.isFeatureEnabled("hook_flutter_app_installed")) {
            ReaLog.log("flutter", "调试：所有Flutter方法被禁用，跳过安装");
            return;
        }

        // 分别根据开关安装
        if (DebugModeManager.isFeatureEnabled("hook_flutter_package_info")) {
            hookFlutterPackageInfoPlus(classLoader);
        }
        if (DebugModeManager.isFeatureEnabled("hook_flutter_app_installed")) {
            hookFlutterAppInstalledChecker(classLoader);
        }
        if (DebugModeManager.isFeatureEnabled("hook_flutter_method_channel")) {
            hookFlutterMethodChannelCheck(classLoader);
        }
    }

    // ---------- 核心判断逻辑 ----------

    public boolean isSystemPackage(String packageName) {
        if (packageName == null) {
            ReaLog.log("system", "过滤: 包名为空");
            return false;
        }
        if (mHookInit.mIsSystemPackageCache != null && packageName.equals(mHookInit.getCurrentTargetApp())) {
            return mHookInit.mIsSystemPackageCache;
        }
        if (isRealSystemPackage(packageName)) {
            if (packageName.equals(mHookInit.getCurrentTargetApp())) {
                mHookInit.mIsSystemPackageCache = true;
            }
            ReaLog.log("system", "过滤: 系统应用 " + packageName + " -> true");
            return true;
        }
        if (isBaseSystemPackage(packageName)) {
            ReaLog.log("system", "过滤: 基础系统包 " + packageName);
            if (packageName.equals(mHookInit.getCurrentTargetApp())) {
                mHookInit.mIsSystemPackageCache = true;
            }
            return true;
        }
        if (isVendorPackage(packageName)) {
            if (!isRealSystemPackage(packageName)) {
                return false;
            }
            Boolean choice = HookInit.vendorChoiceMap.get(packageName);
            if (choice == null) {
                HookInit.vendorChoiceMap.put(packageName, false);
                // needVendorDialog 由 HookInit 管理，这里只记录日志
                ReaLog.log("system", "过滤: 强制包询问" + packageName);
            } else if (choice) {
                ReaLog.log("system", "过滤: 强制包(启用) " + packageName);
            } else {
                ReaLog.log("system", "过滤: 强制包(禁用) " + packageName);
            }
            if (packageName.equals(mHookInit.getCurrentTargetApp())) {
                mHookInit.mIsSystemPackageCache = true;
            }
            return true;
        }
        // 兜底：通过 ApplicationInfo 标志位验证
        try {
            Context context = mHookInit.getApplicationContext();
            if (context == null) {
                mHookInit.mIsSystemPackageCache = false;
                return false;
            }
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            boolean result = ((appInfo.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0);
            if (packageName.equals(mHookInit.getCurrentTargetApp())) {
                mHookInit.mIsSystemPackageCache = result;
            }
            return result;
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isBaseSystemPackage(String packageName) {
        if (packageName == null) return false;
        for (String basePkg : BASE_SYSTEM_PACKAGES) {
            if (packageName.startsWith(basePkg)) {
                return true;
            }
        }
        return false;
    }

    public boolean isVendorPackage(String packageName) {
        if (packageName == null) return false;
        for (String vendorPrefix : VENDOR_PACKAGE_PREFIXES) {
            if (packageName.startsWith(vendorPrefix)) {
                mHookInit.mSystemCoreCache.put(packageName, true);
                return true;
            }
        }
        return false;
    }
    
    public boolean isRealSystemPackage(String packageName) {
        return mHookInit.isRealSystemPackage(packageName);
    }

    public boolean isSystemCorePackage(String packageName) {
        if (packageName == null) return false;
        List<
                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
        if (excluded.contains(packageName)) {
            mHookInit.mSystemCoreCache.put(packageName, true);
            return true;
        }
        if (isBaseSystemPackage(packageName)) {
            mHookInit.mSystemCoreCache.put(packageName, true);
            return true;
        }
        Boolean cached = mHookInit.mSystemCoreCache.get(packageName);
        if (cached != null && cached) {
            return true;
        }
        if (isVendorPackage(packageName)) {
            return true;
        }
        if (packageName.endsWith(".account")) {
            mHookInit.mSystemCoreCache.put(packageName, true);
            return true;
        }
        boolean isCoreLibRelated = packageName.contains("webview") ||
                packageName.contains("jiagu") ||
                packageName.contains("c++_shared") ||
                packageName.contains("breakpad") ||
                packageName.contains("monochrome") ||
                packageName.contains("vendor") ||
                packageName.contains("chipset") ||
                packageName.contains("modem") ||
                packageName.contains("radio") ||
                packageName.contains("firmware");
        if (packageName.startsWith("io.va.exposed") ||
                packageName.startsWith("com.excean.dualaid") ||
                packageName.startsWith("com.qihoo.magic") ||
                packageName.startsWith("info.red.virtual") ||
                packageName.startsWith("com.bly.dkplat") ||
                packageName.startsWith("dkplugin.") ||
                packageName.startsWith("com.pengyou.cloneapp") ||
                packageName.startsWith("com.jy.x.separation.manager") ||
                packageName.startsWith("com.dong.multirun") ||
                packageName.startsWith("com.excelliance.dualaid") ||
                packageName.startsWith("com.lbe.parallel") ||
                packageName.startsWith("com.parallel.space") ||
                packageName.startsWith("com.chaozhijian.multiopen")) {
            return false;
        }
        boolean result = isCoreLibRelated;
        if (result) {
            mHookInit.mSystemCoreCache.put(packageName, true);
        }
        return result;
    }

    public int getPackageStatus(String packageName) {
        Integer cached = mHookInit.packageStatusCache.get(packageName);
        if (cached != null) return cached;
        List<
                HookInit.PackageConfig> configs = HookInit.packageConfigMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
        int status = -1;
        for (HookInit.PackageConfig config : configs) {
            if (config.packageName.equals(packageName)) {
                switch (config.statusMode) {
                    case "installed":
                        status = 1;
                        break;
                    case "not_installed":
                        status = 0;
                        break;
                    default:
                        status = -1;
                }
                break;
            }
        }
        mHookInit.packageStatusCache.put(packageName, status);
        return status;
    }

    public boolean shouldReturnInstalledForPackage(String packageName) {
        if (packageName == null) return false;
        int status = getPackageStatus(packageName);
        if (status == 0) return false;
        if (status == 1) return true;
        return HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
    }

    public boolean isExcludedPackage(String targetPkg) {
        if (targetPkg == null || targetPkg.isEmpty()) return false;
        List<
                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
        return excluded.contains(targetPkg);
    }

    // ---------- 包名分析 ----------
    public HookInit.DetectedPackages analyzeDetectedPackages() {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(mHookInit.getCurrentTargetApp()).append("_");
        keyBuilder.append(HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)).append("_");
        List<String> sortedPkgs = new ArrayList<>(HookInit.globalCapturedPackages);
        Collections.sort(sortedPkgs);
        for (String pkg : sortedPkgs) {
            keyBuilder.append(pkg).append(",");
        }
        List<
                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
        Collections.sort(excluded);
        for (String pkg : excluded) {
            keyBuilder.append("ex:").append(pkg).append(",");
        }
        String cacheKey = keyBuilder.toString();

        HookInit.DetectedPackages cached = HookInit.detectedCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        HookInit.DetectedPackages detected = new HookInit.DetectedPackages();
        try {
            boolean isInstalledMode = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
            Set<String> uniqueInstalled = new HashSet<>();
            Set<String> uniqueNotInstalled = new HashSet<>();
            List<
                    String> excludedPackages = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());

            for (String pkg : HookInit.globalCapturedPackages) {
                if (pkg == null || pkg.trim().isEmpty()) continue;
                if (isBaseSystemPackage(pkg)) continue;
                if (isVendorPackage(pkg)) continue;
                if (isSystemCorePackage(pkg)) {
                    Boolean cachedIsSystem = mHookInit.mRealSystemCache.get(pkg);
                    if (cachedIsSystem != null) {
                        if (cachedIsSystem) continue;
                    } else {
                        if (isRealSystemPackage(pkg)) {
                            mHookInit.mRealSystemCache.put(pkg, true);
                            continue;
                        } else {
                            mHookInit.mRealSystemCache.put(pkg, false);
                        }
                    }
                }
                if (excludedPackages.contains(pkg)) continue;
                int pkgStatus = getPackageStatus(pkg);
                if (pkgStatus == 0) {
                    uniqueNotInstalled.add(pkg);
                } else if (pkgStatus == 1) {
                    uniqueInstalled.add(pkg);
                } else {
                    if (isInstalledMode) uniqueInstalled.add(pkg);
                    else uniqueNotInstalled.add(pkg);
                }
            }
            detected.installedPackages = new ArrayList<>(uniqueInstalled);
            detected.notInstalledPackages = new ArrayList<>(uniqueNotInstalled);
            List<String> allPackages = new ArrayList<>();
            allPackages.addAll(uniqueInstalled);
            allPackages.addAll(uniqueNotInstalled);
            detected.patternHash = generatePatternHash(allPackages);
        } catch (Throwable t) {
            mHookInit.log("分析检测包名异常: " + t.getMessage());
            ReaLog.log("system", "分析检测包名异常: " + t.getMessage());
        }
        HookInit.detectedCache.put(cacheKey, detected);
        return detected;
    }

    public String generatePatternHash(List<String> allPackages) {
        try {
            List<String> sorted = new ArrayList<>(allPackages);
            Collections.sort(sorted);
            MessageDigest md = MessageDigest.getInstance("MD5");
            for (String pkg : sorted) {
                md.update(pkg.getBytes());
            }
            byte[] digest = md.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Throwable t) {
            mHookInit.log("生成模式哈希异常: " + t.getMessage());
            return "default_hash";
        }
    }

    public boolean isDetectionUrl(String url) {
        return (url.contains("checkInstall") ||
                url.contains("appDetect") ||
                url.contains("packageCheck") ||
                url.contains("umeng/app/check") ||
                url.contains("jiguang/detect") ||
                url.contains("appInstalled") ||
                url.contains("verifyApp"));
    }

    public void fakeOkHttpResponse(Object callback, String url, boolean isInstalled) {
        try {
            ClassLoader cl = callback.getClass().getClassLoader();
            Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", cl);
            Class<?> responseBuilderClass = XposedHelpers.findClass("okhttp3.Response$Builder", cl);
            Class<?> mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", cl);
            Class<?> requestBodyClass = XposedHelpers.findClass("okhttp3.RequestBody", cl);
            Object jsonMediaType = XposedHelpers.callStaticMethod(mediaTypeClass, "parse", "application/json; charset=utf-8");
            String fakeJson;
            if (isInstalled) {
                fakeJson = "{\"code\":200,\"msg\":\"success\",\"isInstalled\":true,\"data\":{\"packageList\":" +
                        HookInit.globalCapturedPackages.toString() + "}}";
            } else {
                fakeJson = "{\"code\":404,\"msg\":\"app not installed\",\"isInstalled\":false}";
            }
            Object fakeBody = XposedHelpers.callStaticMethod(requestBodyClass, "create", jsonMediaType, fakeJson);
            Object fakeResponse = XposedHelpers.newInstance(responseBuilderClass);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "code", isInstalled ? 200 : 404);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "body", fakeBody);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "build");
            Class<?> callClass = XposedHelpers.findClass("okhttp3.Call", cl);
            Class<?> callbackClass = XposedHelpers.findClass("okhttp3.Callback", cl);
            java.lang.reflect.Method onResponse = callbackClass.getMethod("onResponse", callClass, responseClass);
            onResponse.invoke(callback, null, fakeResponse);
        } catch (Exception e) {
        }
    }

    public Object createOkHttpFakeResponse(boolean isInstalled, ClassLoader classLoader) {
        try {
            ClassLoader cl = classLoader != null ? classLoader : ClassLoader.getSystemClassLoader();
            Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", cl);
            Class<?> responseBuilderClass = XposedHelpers.findClass("okhttp3.Response$Builder", cl);
            Class<?> mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", cl);
            Class<?> requestBodyClass = XposedHelpers.findClass("okhttp3.RequestBody", cl);
            Object jsonMediaType = XposedHelpers.callStaticMethod(mediaTypeClass, "parse", "application/json; charset=utf-8");
            String fakeJson;
            if (isInstalled) {
                fakeJson = "{\"code\":200,\"msg\":\"检测通过\",\"isInstalled\":true}";
            } else {
                fakeJson = "{\"code\":404,\"msg\":\"应用未安装\",\"isInstalled\":false}";
            }
            Object fakeBody = XposedHelpers.callStaticMethod(requestBodyClass, "create", jsonMediaType, fakeJson);
            Object fakeResponse = XposedHelpers.newInstance(responseBuilderClass);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "code", isInstalled ? 200 : 404);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "body", fakeBody);
            return XposedHelpers.callMethod(fakeResponse, "build");
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- 智能伪造工厂 ----------
    public PackageInfo createSmartFakePackageInfo(String packageName, int flags) {
        if (packageName == null) return null;
        String cacheKey = packageName + "|" + flags;
        PackageInfo cached = sPackageInfoCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        PackageInfo pi = internalCreateSmartFakePackageInfo(packageName, flags);
        if (pi != null) {
            sPackageInfoCache.put(cacheKey, pi);
        }
        return pi;
    }

    private PackageInfo internalCreateSmartFakePackageInfo(String packageName, int flags) {
        ReaLog.log("auto", "智能填充数据(快速): " + packageName + ", flags=" + flags);
        try {
            recordQueryPattern(packageName, flags);
            PackageInfo pi = new PackageInfo();
            pi.packageName = packageName;

            int hash = Math.abs(packageName.hashCode());

            // ---- 版本号：优先查表，未命中再用 hash 兜底 ----
            String[] preset = lookupFakeVersion(packageName);
            String vName;
            int vCode;
            if (preset != null) {
                vName = preset[0];
                vCode = parseIntSafe(preset[1], 100);
            } else {
                vCode = (hash % 9000) + 100;
                int major = 1 + (hash % 8);
                int minor = (hash / 7) % 20;
                int patch = (hash / 29) % 100;
                vName = major + "." + minor + "." + patch;
            }
            pi.versionName = vName;
            pi.versionCode = vCode;
            setLongVersionCodeSafe(pi, (long) vCode);

            // ---- 时间戳：30~365 天前安装，更新在安装后 7~67 天 ----
            long day = 86400000L;
            long now = System.currentTimeMillis();
            long daysAgo = 30 + (hash % 335);
            pi.firstInstallTime = now - daysAgo * day;
            long updateDays = 7 + (hash % 60);
            pi.lastUpdateTime = pi.firstInstallTime + updateDays * day;
            if (pi.lastUpdateTime > now) {
                pi.lastUpdateTime = now - day * 5;
            }

            setInstallerPackageNameSafe(pi, generateSmartInstaller(packageName));
            pi.applicationInfo = createSmartApplicationInfo(packageName, flags);

            pi.activities = EMPTY_ACTIVITY_INFO;
            pi.services = EMPTY_SERVICE_INFO;
            pi.receivers = EMPTY_RECEIVER_INFO;
            pi.providers = EMPTY_PROVIDER_INFO;
            pi.permissions = EMPTY_PERMISSION_INFO;
            pi.signatures = FAKE_SIGNATURES;

            try {
                if ((flags & PackageManager.GET_CONFIGURATIONS) != 0) {
                    pi.configPreferences = new ConfigurationInfo[0];
                }
            } catch (NoSuchFieldError e) {
            }
            if ((flags & PackageManager.GET_GIDS) != 0 && pi.gids == null) {
                pi.gids = new int[0];
            }
            ReaLog.log("auto", "智能数据快速完成: " + packageName);
            return pi;
        } catch (Throwable e) {
            mHookInit.log("智能填充快速失败: " + packageName + " - " + e.getMessage());
            ReaLog.log("auto", "智能数据异常: " + e.getMessage());
            return createSimpleFakePackageInfo(packageName);
        }
    }
    
    public PackageInfo createSimpleFakePackageInfo(String packageName) {
        try {
            PackageInfo pi = new PackageInfo();
            pi.packageName = packageName != null ? packageName : "fake.package.default";

            // ---- 版本号：查表优先 ----
            String[] preset = lookupFakeVersion(pi.packageName);
            String vName = "1.0.0";
            int vCode = 100;
            int targetSdk = 33;
            int minSdk = 21;
            if (preset != null) {
                vName = preset[0];
                vCode = parseIntSafe(preset[1], 100);
                targetSdk = parseIntSafe(preset[2], 33);
                minSdk = parseIntSafe(preset[3], 21);
            }
            pi.versionName = vName;
            pi.versionCode = vCode;
            setLongVersionCodeSafe(pi, (long) vCode);

            // ---- 时间戳：30 天前安装，5 天前更新 ----
            long day = 86400000L;
            long now = System.currentTimeMillis();
            pi.firstInstallTime = now - 30 * day;
            pi.lastUpdateTime = now - 5 * day;

            setInstallerPackageNameSafe(pi, "com.android.vending");

            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = pi.packageName;
            ai.flags = ApplicationInfo.FLAG_INSTALLED;
            ai.enabled = true;
            ai.sourceDir = "/data/app/" + pi.packageName.replace('.', '-') + "-1/base.apk";
            ai.publicSourceDir = ai.sourceDir;
            ai.dataDir = "/data/data/" + pi.packageName;
            ai.nativeLibraryDir = ai.dataDir + "/lib";
            ai.uid = 10000 + (Math.abs(pi.packageName.hashCode()) % 50000);
            ai.targetSdkVersion = targetSdk;
            ai.minSdkVersion = minSdk;
            pi.applicationInfo = ai;
            ReaLog.log("auto", "智能最小化填充: " + pi.packageName);
            return pi;
        } catch (Throwable e) {
            mHookInit.log("智能最小化填充异常: " + e.getMessage());
            ReaLog.log("auto", "智能最小化填充异常: " + e.getMessage());
            return null;
        }
    }
    
    public ApplicationInfo createSmartApplicationInfo(String packageName, int flags) {
        if (packageName == null) return null;
        String cacheKey = packageName + "|" + flags;
        ApplicationInfo cached = sAppInfoCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ApplicationInfo ai = internalCreateSmartApplicationInfo(packageName, flags);
        if (ai != null) {
            sAppInfoCache.put(cacheKey, ai);
        }
        return ai;
    }

    private ApplicationInfo internalCreateSmartApplicationInfo(String packageName, int flags) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;

        String rawName = packageName;
        int lastDot = packageName.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < packageName.length() - 1) {
            rawName = packageName.substring(lastDot + 1);
        }
        if (rawName.isEmpty()) {
            rawName = packageName;
        }
        StringBuilder formatted = new StringBuilder();
        if (rawName.length() > 0) {
            formatted.append(Character.toUpperCase(rawName.charAt(0)));
            for (int i = 1; i < rawName.length(); i++) {
                char c = rawName.charAt(i);
                char prev = rawName.charAt(i - 1);
                if (Character.isUpperCase(c) && !Character.isUpperCase(prev)) {
                    formatted.append(' ');
                }
                formatted.append(c);
            }
        }
        ai.name = formatted.toString();

        ai.flags = ApplicationInfo.FLAG_INSTALLED;
        ai.enabled = true;

        // ---- targetSdkVersion / minSdkVersion：查表优先 ----
        String[] preset = lookupFakeVersion(packageName);
        ai.targetSdkVersion = preset != null ? parseIntSafe(preset[2], 33) : 33;
        ai.minSdkVersion = preset != null ? parseIntSafe(preset[3], 21) : 21;

        int hash = Math.abs(packageName.hashCode());
        int suffix = (hash % 5) + 1;
        String basePath = "/data/app/" + packageName.replace('.', '-') + "-" + suffix;
        ai.sourceDir = basePath + "/base.apk";
        ai.publicSourceDir = ai.sourceDir;
        ai.dataDir = "/data/data/" + packageName;
        ai.nativeLibraryDir = ai.dataDir + "/lib";
        ai.uid = 10000 + (hash % 50000);

        try {
            if ((flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                ai.flags |= ApplicationInfo.FLAG_SYSTEM;
            }
            if ((flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                ai.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
            }
        } catch (Throwable t) {
        }
        ReaLog.log("auto", "创建应用信息: " + packageName);
        return ai;
    }
    
    public ApplicationInfo createSimpleFakeApplicationInfo(String packageName) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName != null ? packageName : "fake.package.default";
        ai.flags = 0;
        ai.enabled = false;
        ai.sourceDir = "/system/fake";
        ai.publicSourceDir = ai.sourceDir;
        ai.dataDir = "/data/data/" + packageName;
        ai.nativeLibraryDir = ai.dataDir + "/lib";
        ai.uid = -1;
        ai.processName = packageName;
        ai.className = packageName + ".FakeApplication";
        ai.taskAffinity = packageName;
        ai.targetSdkVersion = 0;
        ai.minSdkVersion = 0;
        ai.metaData = null;
        return ai;
    }

    public ApplicationInfo createFakeApplicationInfo(String packageName) {
        return createSmartApplicationInfo(packageName, 0);
    }

    public PackageInfo createFakePackageInfo(String packageName) {
        return createSmartFakePackageInfo(packageName, PackageManager.GET_META_DATA);
    }

    private void setInstallerPackageNameSafe(PackageInfo pi, String installer) {
        try {
            XposedHelpers.setObjectField(pi, "installerPackageName", installer);
        } catch (Throwable t) {
            try {
                if (pi.applicationInfo != null && installer != null) {
                    if (pi.applicationInfo.metaData == null) {
                        pi.applicationInfo.metaData = new Bundle();
                    }
                    pi.applicationInfo.metaData.putString("installer_source", installer);
                }
            } catch (Throwable t2) {
            }
        }
    }

    // ---------- 智能生成器 ----------
    private String generateSmartVersion(String packageName) {
        if (versionCache.containsKey(packageName)) {
            return versionCache.get(packageName);
        }
        if (packageName == null || packageName.isEmpty()) {
            versionCache.put("", "1.0.0");
            return "1.0.0";
        }
        int hash = Math.abs(packageName.hashCode());
        String version;
        int nameLength = packageName.length();
        if (nameLength < 15) {
            int major = (hash % 5) + 1;
            int minor = (hash / 7) % 20;
            version = String.format("%d.%d", major, minor);
        } else if (nameLength < 25) {
            int major = (hash % 8) + 1;
            int minor = (hash / 13) % 30;
            int patch = (hash / 29) % 100;
            version = String.format("%d.%d.%d", major, minor, patch);
        } else {
            int major = (hash % 4) + 1;
            int minor = (hash / 17) % 10;
            int patch = (hash / 31) % 50;
            int build = (hash / 53) % 10;
            version = String.format("%d.%d.%d.%d", major, minor, patch, build);
        }
        versionCache.put(packageName, version);
        return version;
    }

    private int generateSmartVersionCode(String packageName, String versionName) {
        if (versionCodeCache.containsKey(packageName)) {
            return versionCodeCache.get(packageName);
        }
        int versionCode;
        int hash = Math.abs(packageName.hashCode());
        try {
            String cleanVersion = versionName.replaceAll("[^0-9.]", "");
            String[] parts = cleanVersion.split("\\.");
            if (parts.length >= 2) {
                int code = 0;
                for (int i = 0; i < Math.min(parts.length, 3); i++) {
                    try {
                        int num = Integer.parseInt(parts[i]) % 100;
                        code = code * 100 + num;
                    } catch (NumberFormatException e) {
                        code = code * 100 + ((hash >> (i * 8)) % 100);
                    }
                }
                versionCode = Math.max(Math.abs(code), 1);
            } else {
                versionCode = 1 + (hash % 999);
            }
        } catch (Exception e) {
            versionCode = 1 + random.nextInt(999);
        }
        versionCodeCache.put(packageName, versionCode);
        return versionCode;
    }

    private long generateSmartInstallTime(String packageName) {
        if (installTimeCache.containsKey(packageName)) {
            return installTimeCache.get(packageName);
        }
        int hash = Math.abs(packageName.hashCode());
        long twoYearsAgo = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 36 * 2);
        long threeMonthsAgo = System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 3 * 3);
        long timeRange = twoYearsAgo - threeMonthsAgo;
        long timeOffset = hash % timeRange;
        long installTime = twoYearsAgo - timeOffset;
        installTimeCache.put(packageName, installTime);
        return installTime;
    }

    private String generateSmartInstaller(String packageName) {
        if (installerCache.containsKey(packageName)) {
            String cached = installerCache.get(packageName);
            // 空串为占位值：表示"已缓存但无安装器"，转回 null 保持与原 HashMap 实现一致
            return (cached != null && cached.isEmpty()) ? null : cached;
        }
        if (packageName == null) return null;
        int hash = Math.abs(packageName.hashCode());
        String installer;
        switch (hash % 11) {
            case 0:
                installer = "com.android.vending";
                break;
            case 1:
                installer = "com.tencent.android.qqdownloader";
                break;
            case 2:
                installer = "com.xiaomi.market";
                break;
            case 3:
                installer = "com.huawei.appmarket";
                break;
            case 4:
                installer = "com.oppo.market";
                break;
            case 5:
                installer = "com.vivo.appstore";
                break;
            case 6:
                installer = "com.baidu.appsearch";
                break;
            case 7:
                installer = "com.wandoujia.phoenix2";
                break;
            case 8:
                installer = "com.meizu.mstore";
                break;
            case 9:
                installer = "com.samsung.android.app.smartswitch";
                break;
            default:
                installer = null;
        }
        // ConcurrentHashMap 不允许 null 值，用空串占位表示"已缓存但无安装器"
        installerCache.put(packageName, installer == null ? "" : installer);
        return installer;
    }

    private String generateSmartAppName(String packageName) {
        if (packageName == null || packageName.length() < 2) {
            return "虚假APP";
        }
        if (appNameCache.containsKey(packageName)) {
            return appNameCache.get(packageName);
        }
        String appName;
        String[] parts = packageName.split("\\.");
        String candidate = "";
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].length() > 2 &&
                    !parts[i].matches("com|org|net|io|co|app|android|mobile|plus")) {
                candidate = parts[i];
                break;
            }
        }
        if (candidate.isEmpty() && parts.length > 0) {
            candidate = parts[parts.length - 1];
        }
        if (candidate.length() > 0) {
            StringBuilder nameBuilder = new StringBuilder();
            nameBuilder.append(Character.toUpperCase(candidate.charAt(0)));
            for (int i = 1; i < candidate.length(); i++) {
                char c = candidate.charAt(i);
                char prev = candidate.charAt(i - 1);
                if (Character.isDigit(c) && !Character.isDigit(prev) && prev != ' ' && i > 1) {
                    nameBuilder.append(" ").append(c);
                } else if (Character.isUpperCase(c) && !Character.isUpperCase(prev) && prev != ' ' && i > 1) {
                    nameBuilder.append(" ").append(c);
                } else if (c == '_' || c == '-') {
                    nameBuilder.append(" ");
                } else {
                    nameBuilder.append(c);
                }
            }
            appName = nameBuilder.toString();
        } else {
            appName = "Application";
        }
        appName = SPACE_PATTERN.matcher(appName.trim()).replaceAll(" ");
        if (appName.isEmpty()) {
            appName = "虚假APP";
        }
        appNameCache.put(packageName, appName);
        return appName;
    }

    private void recordQueryPattern(String packageName, int flags) {
        try {
            queryPatterns.add(new QueryPattern(mHookInit.getCurrentTargetApp(), packageName, flags));
            if (queryPatterns.size() > 500) {
                queryPatterns.remove(0);
            }
        } catch (Throwable t) {
        }
    }

    // ---------- 预加载和缓存 ----------
    public void preloadPackageInfoCache() {
        boolean pkgInfoEnabled = DebugModeManager.isFeatureEnabled("hook_get_package_info");
        boolean appInfoEnabled = DebugModeManager.isFeatureEnabled("hook_get_application_info");
        if (!pkgInfoEnabled && !appInfoEnabled) {
            ReaLog.log("package_query", "预热跳过：包管理功能已全部禁用");
            return;
        }
        if (mHookInit.getCurrentTargetApp() == null || HookInit.globalCapturedPackages.isEmpty()) {
            return;
        }
        long startTime = System.currentTimeMillis();
        int count = 0;
        final List<String> packagesToWarm = new ArrayList<>();
        for (String pkg : HookInit.globalCapturedPackages) {
            try {
                if (shouldReturnInstalledForPackage(pkg)) {
                    createSmartFakePackageInfo(pkg, 0);
                    count++;
                    packagesToWarm.add(pkg);
                }
            } catch (Throwable ignored) {
            }
        }
        long cost = System.currentTimeMillis() - startTime;
        if (count > 0) {
            ReaLog.log("package_query", "同步预热完成: " + count + " 个包, 耗时 " + cost + "ms");
        }
        if (!packagesToWarm.isEmpty()) {
            More.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    long extraStart = System.currentTimeMillis();
                    int extraCount = 0;
                    int[] extraFlags = {PackageManager.GET_META_DATA, PackageManager.GET_SIGNATURES};
                    for (String pkg : packagesToWarm) {
                        try {
                            for (int flag : extraFlags) {
                                createSmartFakePackageInfo(pkg, flag);
                                extraCount++;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    long extraCost = System.currentTimeMillis() - extraStart;
                    if (extraCount > 0) {
                        ReaLog.log("package_query", "异步预热完成: " + extraCount + " 个包变体, 耗时 " + extraCost + "ms");
                    }
                }
            });
        }
    }

    public void invalidateAppDirCache() {
        synchronized (mHookInit.sCacheLock) {
            mHookInit.sCacheVersion++;
            mHookInit.sCachedAppDirNames = null;
            mHookInit.sCachedAppDirFiles = null;
            synchronized (sPackageInfoCache) {
                sPackageInfoCache.clear();
            }
            synchronized (sAppInfoCache) {
                sAppInfoCache.clear();
            }
            HookInit.sInstalledPackagesCache.clear();
            HookInit.sInstalledApplicationsCache.clear();
            mHookInit.packageStatusCache.clear();
            mHookInit.mSystemCoreCache.clear();
        }
    }

    public void clearSmartFakeCache() {
        try {
            versionCache.clear();
            versionCodeCache.clear();
            installTimeCache.clear();
            installerCache.clear();
            appNameCache.clear();
            queryPatterns.clear();
            sPackageInfoCache.clear();
            sAppInfoCache.clear();
            HookInit.detectedCache.clear();
            ReaLog.log("auto", "智能数据缓存清理完成");
        } catch (Throwable t) {
            mHookInit.log("清理智能数据缓存异常: " + t.getMessage());
            ReaLog.log("auto", "清理智能数据缓存异常: " + t.getMessage());
        }
    }

    public void clearAutoCapturedCache(List<String> userPackages) {
        try {
            Iterator<Map.Entry<String, String>> versionIt = versionCache.entrySet().iterator();
            while (versionIt.hasNext()) {
                if (!userPackages.contains(versionIt.next().getKey())) {
                    versionIt.remove();
                }
            }
            Iterator<
                    Map.Entry<
                            String,
                            Integer>> versionCodeIt = versionCodeCache.entrySet().iterator();
            while (versionCodeIt.hasNext()) {
                if (!userPackages.contains(versionCodeIt.next().getKey())) {
                    versionCodeIt.remove();
                }
            }
            Iterator<
                    Map.Entry<String, Long>> installTimeIt = installTimeCache.entrySet().iterator();
            while (installTimeIt.hasNext()) {
                if (!userPackages.contains(installTimeIt.next().getKey())) {
                    installTimeIt.remove();
                }
            }
            Iterator<Map.Entry<String, String>> installerIt = installerCache.entrySet().iterator();
            while (installerIt.hasNext()) {
                if (!userPackages.contains(installerIt.next().getKey())) {
                    installerIt.remove();
                }
            }
            Iterator<Map.Entry<String, String>> appNameIt = appNameCache.entrySet().iterator();
            while (appNameIt.hasNext()) {
                if (!userPackages.contains(appNameIt.next().getKey())) {
                    appNameIt.remove();
                }
            }
            queryPatterns.clear();
            ReaLog.log("misc", "清理捕获缓存完成，保留手动包: " + userPackages.size());
        } catch (Throwable t) {
            mHookInit.log("清理自动捕获缓存异常: " + t.getMessage());
            ReaLog.log("system", "清理自动捕获缓存异常: " + t.getMessage());
        }
    }

    // ---------- 包名工具 ----------

    public String extractPackageName(Object[] args) {
        if (args == null || args.length == 0) return null;
        for (Object arg : args) {
            if (arg instanceof String) {
                String str = (String) arg;
                if (isValidPackageName(str)) return str;
            }
        }
        for (Object arg : args) {
            if (arg instanceof Intent) {
                Intent intent = (Intent) arg;
                String pkg = intent.getPackage();
                if (isValidPackageName(pkg)) return pkg;
                ComponentName cn = intent.getComponent();
                if (cn != null) {
                    pkg = cn.getPackageName();
                    if (isValidPackageName(pkg)) return pkg;
                }
            }
        }
        for (Object arg : args) {
            if (arg instanceof ComponentName) {
                ComponentName cn = (ComponentName) arg;
                String pkg = cn.getPackageName();
                if (isValidPackageName(pkg)) return pkg;
            }
        }
        return null;
    }

    public String extractPackageFromIntent(Intent intent) {
        if (intent == null) return null;
        if (intent.getComponent() != null) {
            return intent.getComponent().getPackageName();
        }
        if (!TextUtils.isEmpty(intent.getPackage())) {
            return intent.getPackage();
        }
        android.net.Uri data = intent.getData();
        if (data != null && "package".equals(data.getScheme())) {
            return data.getSchemeSpecificPart();
        }
        return null;
    }

    /** 查询包是否真实安装（自 HookInit 迁入）。GET_ACTIVITIES 精度，与系统包管理器口径一致 */
    public boolean isPackageReallyInstalled(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        try {
            Context context = (Context) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", null),
                            "currentApplication"
                    );
            if (context == null) return false;
            PackageManager pm = context.getPackageManager();
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public String extractPackageFromCommand(String command) {
        Pattern pattern = Pattern.compile("(?:pm\\s+path|dumpsys\\s+package)\\s+([a-zA-Z0-9._]+)");
        Matcher matcher = pattern.matcher(command);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile("\"([a-zA-Z0-9._]+)\"");
        matcher = pattern.matcher(command);
        if (matcher.find()) {
            return matcher.group(1);
        }
        pattern = Pattern.compile("'([a-zA-Z0-9._]+)'");
        matcher = pattern.matcher(command);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public String extractPackageFromPath(String path) {
        if (path == null) return null;
        String cached = mHookInit.pathPackageCache.get(path);
        if (cached != null) return cached;
        String pkg = null;
        try {
            if (path.startsWith("/data/app/")) {
                if (path.contains("/base.apk")) {
                    Matcher m = BASE_APK_PATTERN.matcher(path);
                    if (m.find()) pkg = m.group(1);
                } else {
                    Matcher m = DATA_APP_PATTERN.matcher(path);
                    if (m.find()) pkg = m.group(1).replace('-', '.');
                }
            } else if (path.startsWith("/data/data/")) {
                Matcher m = DATA_DATA_PATTERN.matcher(path);
                if (m.find()) pkg = m.group(1);
            } else if (path.startsWith("/data/user/")) {
                Matcher m = DATA_USER_PATTERN.matcher(path);
                if (m.find()) pkg = m.group(1);
            }
        } catch (Throwable ignored) {
        }
        if (pkg != null) {
            mHookInit.pathPackageCache.put(path, pkg);
        }
        return pkg;
    }

    private String extractPackageFromFlutterArgs(Object arguments) {
        if (arguments instanceof String) {
            return (String) arguments;
        } else if (arguments instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) arguments;
            Object pkg = map.get("packageName");
            if (pkg != null) return pkg.toString();
            pkg = map.get("pkg");
            if (pkg != null) return pkg.toString();
        }
        return null;
    }

    public boolean isValidPackageName(String str) {
        if (str == null || str.length() < 3 || !str.contains(".")) {
            return false;
        }
        return !str.matches(".*[\\\\/:*?\"<>|].*");
    }

    public void captureValidPackage(String pkg) {
        if (pkg == null || pkg.trim().isEmpty()) return;
        if (pkg.contains("/") || pkg.contains("\\") || pkg.endsWith(".dex") || pkg.endsWith(".so") ||
                pkg.endsWith(".apk") || pkg.contains(":")) return;
        if (isValidPackageName(pkg) && !isSystemCorePackage(pkg) &&
                !HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>()).contains(pkg) &&
                !pkg.equals(mHookInit.getCurrentTargetApp())) {
            if (HookInit.globalCapturedPackages.add(pkg)) {
                invalidateAppDirCache();
                mHookInit.maybeEnableBlockExit();
                mHookInit.log("捕获有效包名：" + pkg);
                ReaLog.log("file_system", "捕获有效包名" + pkg);
            }
        }
    }

    // ---------- 包名捕获辅助 ----------
/** 捕获 getPackageUid(String, int) */
    public void hookGetPackageUid(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageUid",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (pkg != null) {
                                captureValidPackage(pkg);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包Uid失败: " + t.getMessage());
        }
    }

/** 捕获 getPackageGids(String, int) */
    public void hookGetPackageGids(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageGids",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (pkg != null) {
                                captureValidPackage(pkg);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包Gid失败: " + t.getMessage());
        }
    }

// ========== 跨平台通用 API 捕获 ==========
/** 安装所有额外的包管理捕获 所有捕获均通过 captureValidPackage 统一过滤（系统包、自身包、排除包） */
    public void installExtraCaptureHooks(ClassLoader classLoader) {
        // ---------- 1. getPackageUid(String, int) ----------
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageUid",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (pkg != null) captureValidPackage(pkg);
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包Uid 失败: " + t.getMessage());
        }

        // ---------- 2. getPackageGids(String, int) ----------
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageGids",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (pkg != null) captureValidPackage(pkg);
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包Gid 失败: " + t.getMessage());
        }

        // ---------- 3. getPackageInfo(VersionedPackage, int) [Android 8.0+] ----------
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageInfo",
                    "android.content.pm.VersionedPackage", int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object vp = param.args[0];
                            int flags = (int) param.args[1];
                            if (vp == null) return;
                            String packageName;
                            try {
                                packageName = (String) XposedHelpers.callMethod(vp, "getPackageName");
                            } catch (Throwable t) {
                                return;
                            }
                            if (packageName == null) return;

                            // 与 getPackageInfo(String,int) 保持一致的分流逻辑
                            if (isBaseSystemPackage(packageName)) return;
                            if (isVendorPackage(packageName)) return;
                            if (isSystemCorePackage(packageName) && isRealSystemPackage(packageName))
                                return;
                            if (packageName.equals(mHookInit.getCurrentTargetApp())) return;
                            if (isExcludedPackage(packageName)) return;

                            // 社交分享 SDK 自检放行（小微/小Q/小搏/小钉 SDK 拉起前的包校验）：拿真实结果
                            if (shouldPassShareSdkQuery(packageName)) return;

                            int pkgStatus = getPackageStatus(packageName);
                            if (pkgStatus == 0) {
                                param.setThrowable(new PackageManager.NameNotFoundException());
                                mHookInit.log("【固定未安装】: " + packageName);
                                ReaLog.log("package_query", "【固定未安装】" + packageName);
                                return;
                            }
                            if (pkgStatus == 1) {
                                param.setResult(createSmartFakePackageInfo(packageName, flags));
                                if (HookInit.sLoggedPackageSet.add(packageName)) {
                                    mHookInit.log("【固定已安装】: " + packageName);
                                    ReaLog.log("package_query", "【固定已安装】: " + packageName);
                                }
                                return;
                            }

                            boolean isNewlyAdded = false;
                            if (!HookInit.globalCapturedPackages.contains(packageName)) {
                                HookInit.globalCapturedPackages.add(packageName);
                                isNewlyAdded = true;
                                mHookInit.saveConfigToFile();
                                mHookInit.maybeEnableBlockExit();
                            }

                            if (!shouldReturnInstalledForPackage(packageName)) {
                                param.setThrowable(new PackageManager.NameNotFoundException());
                                mHookInit.log("【全局未安装】: " + packageName);
                                ReaLog.log("package_query", "【全局未安装】" + packageName);
                                return;
                            }

                            if (isNewlyAdded) {
                                param.setResult(createSimpleFakePackageInfo(packageName));
                                mHookInit.log("【首查-已安装】最小化数据: " + packageName);
                                ReaLog.log("package_query", "【首查】最小化数据: " + packageName);
                            } else {
                                param.setResult(createSmartFakePackageInfo(packageName, flags));
                                if (HookInit.sLoggedPackageSet.add(packageName)) {
                                    mHookInit.log("【续查-已安装】完整数据: " + packageName);
                                    ReaLog.log("package_query", "【续查】完整数据: " + packageName);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包版本信息 失败: " + t.getMessage());
        }

        // ---------- 4. resolveService(Intent, int) ----------
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "resolveService",
                    Intent.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Intent intent = (Intent) param.args[0];
                            if (intent != null) {
                                String pkg = extractPackageFromIntent(intent);
                                if (pkg != null) captureValidPackage(pkg);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包服务 失败: " + t.getMessage());
        }

        // ---------- 5. getPackagesHoldingPermissions(String[], int) ----------
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackagesHoldingPermissions",
                    String[].class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (result instanceof List) {
                                List<?> list = (List<?>) result;
                                for (Object obj : list) {
                                    if (obj instanceof PackageInfo) {
                                        PackageInfo pi = (PackageInfo) obj;
                                        if (pi.packageName != null) {
                                            captureValidPackage(pi.packageName);
                                        }
                                    }
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包权限信息 失败: " + t.getMessage());
        }

        // ---------- 6. canPackageQuery(String, String[]) [Android 14+] ----------
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "canPackageQuery",
                        String.class, String[].class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String pkg = (String) param.args[0];
                                if (pkg != null) captureValidPackage(pkg);
                            }
                        }
                );
            } catch (Throwable t) {
                mHookInit.log("Hook 获取包查询 失败: " + t.getMessage());
            }
        }

// ---------- 7. 拦截 APK 文件头解析 (getPackageArchiveInfo) ----------
// 先尝试 Hook 父类 (PackageManager) —— 兼容所有 Android 版本
        try {
            XposedHelpers.findAndHookMethod(
                    android.content.pm.PackageManager.class, // 改为父类
                    "getPackageArchiveInfo",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handlePackageArchiveInfo(param);
                        }
                    }
            );
            ReaLog.log("file_system", "文件头解析：父类PackageManager拦截成功");
        } catch (Throwable t) {
            ReaLog.log("file_system", "文件头解析： 父类拦截失败:" + t.getMessage());

            // 降级：尝试 Hook 子类 (ApplicationPackageManager)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "getPackageArchiveInfo",
                        String.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                handlePackageArchiveInfo(param);
                            }
                        }
                );
                ReaLog.log("file_system", "文件头解析：辅类ApplicationPackageManager拦截成功");
            } catch (Throwable t2) {
                mHookInit.log("❌ 文件头解析 Hook 失败: " + t2.getMessage());
                ReaLog.log("file_system", "❌ 文件头解析 Hook 失败: " + t2.getMessage());
            }
        }
    }

    /** 处理 getPackageArchiveInfo 的核心逻辑 */
    private void handlePackageArchiveInfo(MethodHookParam param) {
        String path = (String) param.args[0];
        String currentPkg = mHookInit.getCurrentTargetApp();
        // ---------- 1. 优先放行自身私有目录下的任何文件（插件、缓存等） ----------
        if (path != null && currentPkg != null) {
            if (path.contains("/data/data/" + currentPkg) ||
                    path.contains("/data/user/0/" + currentPkg) ||
                    path.contains("/storage/emulated/0/Android/data/" + currentPkg)) {
                // 这是应用自身私有目录，直接放行，不拦截
                ReaLog.log("file_system", "文件头解析：自身放行: " + path);
                return;
            }
        }
        // ---------- 2. 提取包名 ----------
        String pkg = extractPackageFromPath(path);
        // ---------- 3. 记录所有调用（方便调试） ----------
        if (pkg != null) {
            ReaLog.log("file_system", "文件头解析触发: " + path + " -> " + pkg);
        }
        // ---------- 4. 拦截条件：非系统、非自身包 ----------
        if (pkg != null && !isSystemCorePackage(pkg) && !pkg.equals(currentPkg)) {
            if (!shouldReturnInstalledForPackage(pkg)) {
                param.setResult(null);
                ReaLog.log("file_system", "[未安装]文件头解析返回: " + path + " -> " + pkg);
            } else {
                param.setResult(createSmartFakePackageInfo(pkg, (int) param.args[1]));
                ReaLog.log("file_system", "[已安装]文件头解析返回: " + path + " -> " + pkg);
            }
        }
        // 其他情况（系统包、自身包）不做任何处理，放行
    }

    // ---------- 命令行检测 ----------

    public void hookRuntimeExecMethods(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.Runtime", classLoader, "exec", String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String command = (String) param.args[0];
                            handleCommandLineDetection(param, command, "exec(String)");
                        }
                    });
            XposedHelpers.findAndHookMethod("java.lang.Runtime", classLoader, "exec", String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String[] commands = (String[]) param.args[0];
                            if (commands != null && commands.length > 0) {
                                StringBuilder cmdBuilder = new StringBuilder();
                                for (String cmd : commands) {
                                    cmdBuilder.append(cmd).append(" ");
                                }
                                String command = cmdBuilder.toString().trim();
                                handleCommandLineDetection(param, command, "exec(String[])");
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod("java.lang.Runtime", classLoader, "exec", String.class, String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String command = (String) param.args[0];
                            handleCommandLineDetection(param, command, "exec(String, String[])");
                        }
                    });
            XposedHelpers.findAndHookMethod("java.lang.Runtime", classLoader, "exec", String[].class, String[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String[] commands = (String[]) param.args[0];
                            if (commands != null && commands.length > 0) {
                                StringBuilder cmdBuilder = new StringBuilder();
                                for (String cmd : commands) {
                                    cmdBuilder.append(cmd).append(" ");
                                }
                                String command = cmdBuilder.toString().trim();
                                handleCommandLineDetection(param, command, "exec(String[], String[])");
                            }
                        }
                    });
            ReaLog.log("cmd", "已Hook Runtime.exec所有重载方法");
        } catch (Throwable t) {
            mHookInit.log("Hook Runtime.exec失败: " + t.getMessage());
            ReaLog.log("cmd", "Hook Runtime.exec异常: " + t.getMessage());
        }
    }

    public void hookProcessBuilder(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("java.lang.ProcessBuilder", classLoader, "start",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            ProcessBuilder pb = (ProcessBuilder) param.thisObject;
                            List<String> cmd = pb.command();
                            if (cmd == null || cmd.isEmpty()) {
                                ReaLog.log("cmd", "ProcessBuilder: 命令为空");
                                return;
                            }
                            String full = TextUtils.join(" ", cmd);
                            if (isPackageDetectionCommand(full)) {
                                boolean installed = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                Object fake = createFakeProcess(full, installed);
                                if (fake != null) {
                                    param.setResult(fake);
                                    ReaLog.log("cmd", "ProcessBuilder 拦截: " + full + ", 安装状态: " + (installed ? "已安装" : "未安装"));
                                    return;
                                }
                            }
                            ReaLog.log("cmd", "ProcessBuilder 放行: " + full);
                        }
                    });
            ReaLog.log("cmd", "已启用 ProcessBuilder");
        } catch (Throwable t) {
            mHookInit.log("Hook ProcessBuilder失败: " + t.getMessage());
            ReaLog.log("cmd", "Hook ProcessBuilder异常: " + t.getMessage());
        }
    }

    private void handleCommandLineDetection(XC_MethodHook.MethodHookParam param, String command, String methodName) {
        ReaLog.log("cmd", "处理命令行检测: " + methodName + " -> " + command);
        if (command == null) return;
        String lowerCommand = command.toLowerCase();
        if (isPackageDetectionCommand(lowerCommand)) {
            boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
            Object fakeProcess = createFakeProcess(command, shouldReturnInstalled);
            if (lowerCommand.contains("pm list packages") || lowerCommand.contains("dumpsys package") ||
                    lowerCommand.contains("ip link") || lowerCommand.contains("netstat -r") || lowerCommand.contains("ifconfig")) {
                try {
                    String commandOutput = generateFakeCommandOutput(command, shouldReturnInstalled);
                    if (commandOutput != null && !commandOutput.isEmpty()) {
                        String[] outputLines = commandOutput.split("\\n");
                        for (String line : outputLines) {
                            line = line.trim();
                            if (line.startsWith("package:")) {
                                String pkg = line.replaceFirst("^package:", "").trim();
                                captureValidPackage(pkg);
                            } else if (line.startsWith("Package [") && line.endsWith("]")) {
                                String pkg = line.replace("Package [", "").replace("]", "").trim();
                                captureValidPackage(pkg);
                            }
                        }
                    }
                } catch (Throwable e) {
                    ReaLog.log("cmd", "命令行检测输出异常: " + e.getMessage());
                }
            }
            if (fakeProcess != null) {
                param.setResult(fakeProcess);
                ReaLog.log("cmd", "命令行检测: 返回虚假进程, 命令: " + command);
            }
        }
    }

    private boolean isPackageDetectionCommand(String command) {
        if (command == null) return false;
        String lowerCommand = command.toLowerCase();
        if (command.contains("pm ") || command.startsWith("pm ")) {
            return (command.contains("list packages") ||
                    command.contains("path ") ||
                    command.contains("dump ") ||
                    command.contains("clear ") ||
                    command.contains("install ") ||
                    command.contains("uninstall ") ||
                    command.contains("enable ") ||
                    command.contains("disable "));
        }
        if (command.contains("dumpsys ")) {
            return (command.contains("dumpsys package") ||
                    command.contains("dumpsys activity") ||
                    command.contains("dumpsys meminfo") ||
                    command.contains("dumpsys package --check") ||
                    command.contains("dumpsys package --verify") ||
                    command.contains("dumpsys package --brief"));
        }
        if (command.contains("cmd package ") || command.startsWith("cmd package ")) {
            return (command.contains("list") ||
                    command.contains("path") ||
                    command.contains("dump") ||
                    command.contains("--check") ||
                    command.contains("--verify"));
        }
        if (command.contains("ls ") || command.contains("find ") || command.contains("cat ")) {
            return (command.contains("/data/app/") ||
                    command.contains("/system/app/") ||
                    command.contains("/system/priv-app/") ||
                    command.contains("/vendor/app/") ||
                    command.contains("/product/app/") ||
                    command.contains("/data/data/") ||
                    command.contains("/proc/") ||
                    command.contains("packages.xml") ||
                    command.contains("packages.list") ||
                    command.contains("packages_cache.xml"));
        }
        if (command.contains("getprop")) {
            return (command.contains("package") || command.contains("app") || command.contains("install"));
        }
        if (command.contains("ps ") || command.startsWith("ps")) {
            return (command.contains("| grep ") || command.contains("com.") || command.contains("-A") || command.contains("-a"));
        }
        if (command.contains("which ") || command.contains("whereis ")) {
            return (command.contains("pm") || command.contains("dumpsys") || command.contains("getprop"));
        }
        return false;
    }

    private Object createFakeProcess(final String command, final boolean shouldReturnInstalled) {
        ReaLog.log("cmd", "创建虚假进程: " + command + ", 安装状态: " + (shouldReturnInstalled ? "已安装" : "未安装"));
        try {
            final ClassLoader classLoader = getClass().getClassLoader();
            final Class<?> processClass = Class.forName("java.lang.Process");
            final String fakeOutput = generateFakeCommandOutput(command, shouldReturnInstalled);
            return java.lang.reflect.Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[]{processClass},
                    new java.lang.reflect.InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args)
                                throws Throwable {
                            String methodName = method.getName();
                            switch (methodName) {
                                case "getInputStream":
                                    return new java.io.ByteArrayInputStream(fakeOutput.getBytes("UTF-8"));
                                case "getErrorStream":
                                    return new java.io.ByteArrayInputStream(new byte[0]);
                                case "getOutputStream":
                                    return new java.io.OutputStream() {
                                        @Override
                                        public void write(int b) {}
                                    };
                                case "waitFor":
                                    return 0;
                                case "exitValue":
                                    return 0;
                                case "destroy":
                                    return null;
                                case "toString":
                                    return "FakeProcess[cmd=" + command + "]";
                                default:
                                    if (method.getReturnType() == boolean.class) return false;
                                    else if (method.getReturnType() == int.class) return 0;
                                    else if (method.getReturnType() == long.class) return 0L;
                                    return null;
                            }
                        }
                    }
            );
        } catch (Throwable e) {
            return null;
        }
    }

    private String generateFakeCommandOutput(String command, boolean shouldReturnInstalled) {
        if (!shouldReturnInstalled) {
            if (command.contains("pm list packages")) return "";
            else if (command.contains("pm path ")) return "Error: package not found";
            else if (command.contains("dumpsys package ")) return "No package found";
            else if (command.contains("ip link")) return "";
            else if (command.contains("netstat -r")) return "";
            else if (command.contains("ifconfig")) return "";
            return "";
        }
        String lowerCommand = command.toLowerCase();
        if (lowerCommand.contains("pm list packages")) {
            StringBuilder output = new StringBuilder();
            Set<String> allPackages = new HashSet<>(HookInit.globalCapturedPackages);
            allPackages.addAll(HookInit.userDefinedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>()));
            allPackages.removeAll(HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>()));
            for (String pkg : allPackages) {
                if (getPackageStatus(pkg) == 0) continue;
                output.append("package:").append(pkg).append("\n");
            }
            if (output.length() == 0) {
                output.append("package:com.android.chrome\n");
            }
            return output.toString();
        } else if (lowerCommand.contains("pm path ")) {
            String targetPackage = extractPackageFromCommand(command);
            if (targetPackage != null) {
                int status = getPackageStatus(targetPackage);
                if (status == 0) return "Error: package not found";
                else if (status == 1)
                    return "package:/data/app/" + targetPackage.replace('.', '-') + "-1/base.apk";
                if (!shouldReturnInstalled) return "Error: package not found";
                List<
                        String> userPkgs = HookInit.userDefinedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
                if (userPkgs.contains(targetPackage)) {
                    return "package:/data/app/" + targetPackage.replace('.', '-') + "-1/base.apk";
                }
                if (HookInit.globalCapturedPackages.contains(targetPackage)) {
                    return "package:/data/app/" + targetPackage.replace('.', '-') + "-1/base.apk";
                }
            }
            return "Error: package not found";
        } else if (lowerCommand.contains("dumpsys package ")) {
            String targetPackage = extractPackageFromCommand(command);
            if (targetPackage != null) {
                int status = getPackageStatus(targetPackage);
                if (status == 0) return "No package found for: " + targetPackage;
                if (HookInit.globalCapturedPackages.contains(targetPackage) ||
                        HookInit.userDefinedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>()).contains(targetPackage)) {
                    return generateFakeDumpsysOutput(targetPackage);
                }
            }
            return "No package found for: " + targetPackage;
        } else if (lowerCommand.contains("ls ") && lowerCommand.contains("/data/app/")) {
            StringBuilder output = new StringBuilder();
            Set<String> allPackages = new HashSet<>(HookInit.globalCapturedPackages);
            allPackages.addAll(HookInit.userDefinedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>()));
            allPackages.removeAll(HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>()));
            for (String pkg : allPackages) {
                if (getPackageStatus(pkg) == 0) continue;
                output.append(pkg.replace('.', '-') + "-1\n");
            }
            return output.toString();
        } else if (lowerCommand.contains("cat ") && lowerCommand.contains("packages.xml")) {
            return generateFakePackagesXml();
        } else if (lowerCommand.contains("ps ") || command.startsWith("ps")) {
            return generateFakePsOutput();
        } else if (lowerCommand.contains("getprop")) {
            return "[ro.build.version.sdk]: [28]\n[ro.product.brand]: [google]\n[ro.product.model]: [Pixel 3]\n";
        } else if (lowerCommand.contains("ip link") || lowerCommand.contains("ip addr")) {
            return "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN mode DEFAULT group default qlen 1000\n    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00\n2: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP mode DORMANT group default qlen 1000\n    link/ether 00:11:22:33:44:55 brd ff:ff:ff:ff:ff:ff\n";
        } else if (lowerCommand.contains("netstat -r") || lowerCommand.contains("route")) {
            return "Kernel IP routing table\nDestination     Gateway         Genmask         Flags   MSS Window  irtt Iface\n0.0.0.0         192.168.1.1     0.0.0.0         UG        0 0          0 wlan0\n192.168.1.0     0.0.0.0         255.255.255.0   U         0 0          0 wlan0\n";
        } else if (lowerCommand.contains("ifconfig")) {
            return "lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536\n        inet 127.0.0.1  netmask 255.0.0.0\n        inet6 ::1  prefixlen 128  scopeid 0x10<host>\n        loop  txqueuelen 1000  (Local Loopback)\n        RX packets 1234  bytes 123456 (123.4 KB)\n        TX packets 1234  bytes 123456 (123.4 KB)\n\nwlan0: flags=4163<BROADCAST,RUNNING,MULTICAST>  mtu 1500\n        inet 192.168.1.100  netmask 255.255.255.0  broadcast 192.168.1.255\n        inet6 fe80::1234:5678:90ab:cdef  prefixlen 64  scopeid 0x20<link>\n        ether 00:11:22:33:44:55  txqueuelen 1000  (Ethernet)\n        RX packets 5678  bytes 567890 (567.8 KB)\n        TX packets 4321  bytes 432109 (432.1 KB)\n";
        }
        return "";
    }

    private String generateFakeDumpsysOutput(String packageName) {
        long now = System.currentTimeMillis();
        return "Packages:\n" +
                "  Package [" + packageName + "] (aaaaaaaa):\n" +
                "    userId=10000\n" +
                "    pkg=Package{" + packageName + "}\n" +
                "    codePath=/data/app/" + packageName.replace('.', '-') + "-1\n" +
                "    resourcePath=/data/app/" + packageName.replace('.', '-') + "-1\n" +
                "    legacyNativeLibraryDir=/data/app/" + packageName.replace('.', '-') + "-1/lib\n" +
                "    primaryCpuAbi=null\n" +
                "    secondaryCpuAbi=null\n" +
                "    versionCode=1 minSdk=21 targetSdk=28\n" +
                "    versionName=1.0.0\n" +
                "    splits=[base]\n" +
                "    apkSigningVersion=2\n" +
                "    applicationInfo=ApplicationInfo{" + packageName + "}\n" +
                "    flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]\n" +
                "    privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE ]\n" +
                "    dataDir=/data/data/" + packageName + "\n" +
                "    supportsScreens=[small, medium, large, xlarge, resizeable, anyDensity]\n" +
                "    timeStamp=" + (now - 86400000) + "\n" +
                "    firstInstallTime=" + (now - 86400000) + "\n" +
                "    lastUpdateTime=" + now + "\n" +
                "    installerPackageName=com.android.vending\n" +
                "    signatures=PackageSignatures{aaaaaaaa version:1, signatures:[aaaaaaaa], past signatures:[]}\n" +
                "    permissionsFixed=true\n" +
                "    pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]\n";
    }

    private String generateFakePackagesXml() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n");
        xml.append("<packages>\n");
        int userId = 10000;
        for (String pkg : HookInit.globalCapturedPackages) {
            int status = getPackageStatus(pkg);
            if (status == 0) continue;
            xml.append("  <package name=\"").append(pkg)
                    .append("\" codePath=\"/data/app/").append(pkg.replace('.', '-')).append("-1\" userId=\"")
                    .append(userId++).append("\" version=\"1\">\n");
            xml.append("    <sigs count=\"1\"><cert index=\"0\" key=\"fake\" /></sigs>\n");
            xml.append("  </package>\n");
        }
        List<
                String> userPkgs = HookInit.userDefinedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
        for (String pkg : userPkgs) {
            int status = getPackageStatus(pkg);
            if (status == 0) continue;
            if (!HookInit.globalCapturedPackages.contains(pkg)) {
                xml.append("  <package name=\"").append(pkg)
                        .append("\" codePath=\"/data/app/").append(pkg.replace('.', '-')).append("-1\" userId=\"")
                        .append(userId++).append("\" version=\"1\">\n");
                xml.append("    <sigs count=\"1\"><cert index=\"0\" key=\"fake\" /></sigs>\n");
                xml.append("  </package>\n");
            }
        }
        xml.append("</packages>\n");
        return xml.toString();
    }

    private String generateFakePsOutput() {
        StringBuilder output = new StringBuilder();
        output.append("USER      PID   PPID  VSIZE  RSS   WCHAN            PC  NAME\n");
        output.append("root      1     0     1234   567   SyS_epoll_ 00000000 S /init\n");
        output.append("system    100   1     2345   678   SyS_epoll_ 00000000 S system_server\n");
        int pid = 2000;
        for (String pkg : HookInit.globalCapturedPackages) {
            if (getPackageStatus(pkg) == 0) continue;
            output.append(String.format("u0_a100  %-6d 100   34567  4567  SyS_epoll_ 00000000 S %s\n", pid++, pkg));
        }
        return output.toString();
    }

    // ---------- 包管理 Hook 安装 ----------
    public void hookGetPackageInfo(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageInfo",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            final String packageName = (String) param.args[0];
                            int flags = (int) param.args[1];
                            if (packageName == null) return;

                            if (isBaseSystemPackage(packageName)) return;
                            if (isVendorPackage(packageName)) return;
                            if (isSystemCorePackage(packageName) && isRealSystemPackage(packageName))
                                return;
                            if (packageName.equals(mHookInit.getCurrentTargetApp())) return;
                            if (isExcludedPackage(packageName)) return;

                            // 社交分享 SDK 自检放行
                            if (shouldPassShareSdkQuery(packageName)) return;

                            int pkgStatus = getPackageStatus(packageName);
                            if (pkgStatus == 0) {
                                param.setThrowable(new PackageManager.NameNotFoundException());
                                mHookInit.log("【固定未安装】: " + packageName);
                                ReaLog.log("package_query", "【固定未安装】" + packageName);
                                return;
                            }
                            if (pkgStatus == 1) {
                                param.setResult(createSmartFakePackageInfo(packageName, flags));
                                if (HookInit.sLoggedPackageSet.add(packageName)) {
                                    mHookInit.log("【固定已安装】: " + packageName);
                                    ReaLog.log("package_query", "【固定已安装】: " + packageName);
                                }
                                return;
                            }

                            boolean isNewlyAdded = false;
                            if (!HookInit.globalCapturedPackages.contains(packageName)) {
                                HookInit.globalCapturedPackages.add(packageName);
                                isNewlyAdded = true;
                                mHookInit.saveConfigToFile();
                                mHookInit.maybeEnableBlockExit();
                                if (!HookInit.sGlobalListLogged) {
                                    mHookInit.log("全局捕获列表(" + HookInit.globalCapturedPackages.size() + "): " + HookInit.globalCapturedPackages.toString());
                                    ReaLog.log("package_query", "全局捕获列表(" + HookInit.globalCapturedPackages.size() + "): " + HookInit.globalCapturedPackages.toString());
                                    HookInit.sGlobalListLogged = true;
                                }
                            }

                            if (!shouldReturnInstalledForPackage(packageName)) {
                                param.setThrowable(new PackageManager.NameNotFoundException());
                                mHookInit.log("【全局未安装】: " + packageName);
                                ReaLog.log("package_query", "【全局未安装】" + packageName);
                                return;
                            }

                            if (isNewlyAdded) {
                                param.setResult(createSimpleFakePackageInfo(packageName));
                                mHookInit.log("【首查-已安装】最小化数据: " + packageName);
                                ReaLog.log("package_query", "【首查】最小化数据: " + packageName);
                                More.runOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            int[] extraFlags = {PackageManager.GET_META_DATA, PackageManager.GET_SIGNATURES};
                                            for (int flag : extraFlags) {
                                                createSmartFakePackageInfo(packageName, flag);
                                            }
                                            createSmartApplicationInfo(packageName, PackageManager.GET_META_DATA);
                                            createSmartApplicationInfo(packageName, PackageManager.GET_SIGNATURES);
                                        } catch (Throwable ignored) {
                                        }
                                    }
                                });
                            } else {
                                param.setResult(createSmartFakePackageInfo(packageName, flags));
                                if (HookInit.sLoggedPackageSet.add(packageName)) {
                                    mHookInit.log("【续查-已安装】完整数据: " + packageName);
                                    ReaLog.log("package_query", "【续查】完整数据: " + packageName);
                                }
                            }
                        }
                    }
            );
            ReaLog.log("package_query", "启用包名捕获");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook 包名捕获 失败: " + t.getMessage());
            ReaLog.log("package_query", "Hook 包名捕获 异常: " + t.getMessage());
        }
    }

    public void hookGetApplicationInfo(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getApplicationInfo",
                    String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            int flags = (int) param.args[1];
                            if (packageName == null) return;

                            if (packageName.equals(mHookInit.getCurrentTargetApp())) return;
                            if (isSystemCorePackage(packageName)) return;

                            boolean isNewlyAdded = false;
                            if (!HookInit.globalCapturedPackages.contains(packageName)) {
                                HookInit.globalCapturedPackages.add(packageName);
                                isNewlyAdded = true;
                                mHookInit.saveConfigToFile();
                                mHookInit.maybeEnableBlockExit();
                                ReaLog.log("install_detect", "捕获新包名2: " + packageName);
                            }

                            int pkgStatus = getPackageStatus(packageName);
                            if (pkgStatus == 0) {
                                param.setThrowable(new PackageManager.NameNotFoundException());
                                mHookInit.log("【固定未安装】: " + packageName);
                                ReaLog.log("package_query", "【固定未安装】" + packageName);
                                return;
                            }
                            if (pkgStatus == 1) {
                                param.setResult(createSmartApplicationInfo(packageName, flags));
                                if (HookInit.sLoggedPackageSet.add(packageName)) {
                                    mHookInit.log("【固定已安装】: " + packageName);
                                    ReaLog.log("package_query", "【固定已安装】: " + packageName);
                                }
                                return;
                            }

                            if (!shouldReturnInstalledForPackage(packageName)) {
                                ApplicationInfo fakeInfo = createSimpleFakeApplicationInfo(packageName);
                                if (fakeInfo != null) {
                                    fakeInfo.enabled = false;
                                    fakeInfo.flags = 0;
                                    fakeInfo.sourceDir = "/system/fake";
                                    fakeInfo.publicSourceDir = fakeInfo.sourceDir;
                                    fakeInfo.dataDir = "/data/data/" + packageName;
                                    fakeInfo.nativeLibraryDir = fakeInfo.dataDir + "/lib";
                                    fakeInfo.packageName = packageName;
                                    fakeInfo.uid = -1;
                                    fakeInfo.processName = packageName;
                                    fakeInfo.className = packageName + ".FakeApplication";
                                    fakeInfo.taskAffinity = packageName;
                                    fakeInfo.targetSdkVersion = 0;
                                    fakeInfo.minSdkVersion = 0;
                                    fakeInfo.metaData = null;
                                    param.setResult(fakeInfo);
                                    ReaLog.log("package_query", "返回未安装(ApplicationInfo): " + packageName);
                                    mHookInit.log("返回未安装(ApplicationInfo): " + packageName);
                                } else {
                                    param.setThrowable(new PackageManager.NameNotFoundException());
                                    ReaLog.log("package_query", "返回未安装(降级异常): " + packageName);
                                }
                                return;
                            }

                            if (isNewlyAdded) {
                                PackageInfo minimalPkg = createSimpleFakePackageInfo(packageName);
                                if (minimalPkg != null && minimalPkg.applicationInfo != null) {
                                    param.setResult(minimalPkg.applicationInfo);
                                    mHookInit.log("【首查-已安装】最小化数据 ApplicationInfo: " + packageName);
                                    ReaLog.log("package_query", "首查 -> 返回最小化 ApplicationInfo: " + packageName);
                                } else {
                                    param.setResult(createFakeApplicationInfo(packageName));
                                    mHookInit.log("【首查-已安装】最小化数据失败，使用兜底: " + packageName);
                                    ReaLog.log("package_query", "首查查询 -> 兜底 ApplicationInfo: " + packageName);
                                }
                            } else {
                                ApplicationInfo full = createSmartApplicationInfo(packageName, flags);
                                if (full != null) {
                                    param.setResult(full);
                                    mHookInit.log("【续查-已安装】完整数据 ApplicationInfo: " + packageName);
                                    ReaLog.log("package_query", "【续查-已安装】完整数据 ApplicationInfo: " + packageName);
                                } else {
                                    param.setResult(createFakeApplicationInfo(packageName));
                                    mHookInit.log("【续查-已安装】完整数据失败，使用兜底: " + packageName);
                                    ReaLog.log("package_query", "【续查-已安装】完整数据失败，使用兜底: " + packageName);
                                }
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("❌ Hook 应用程序信息 失败: " + t.getMessage());
            ReaLog.log("package_query", "应用程序信息 Hook异常: " + t.getMessage());
        }
    }

    public void hookGetInstalledPackages(ClassLoader classLoader) {
        try {
            Class<
                    ?> pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
            XposedBridge.hookAllMethods(pmClass, "getInstalledPackages", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    /*
                    param.setResult(new ArrayList<>());
                    return; //测试 强制返回空列表
                    */
                    Boolean shouldFakePermission = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                    boolean fakeEnabled = shouldFakePermission != null ? shouldFakePermission : true;
                    if (!fakeEnabled) {
                        ReaLog.log("misc", "权限防护已关闭，获取应用列表权限放行1");
                        return;
                    }

                    int flags = extractFlagsFromArgs(param.args);
                    String cacheKey = flags + "|" + mHookInit.sCacheVersion;
                    List<PackageInfo> cachedList = HookInit.sInstalledPackagesCache.get(cacheKey);
                    if (cachedList != null) {
                        param.setResult(new ArrayList<>(cachedList));
                        ReaLog.log("package_query", "返回已安装包列表(缓存): " + cachedList.size() + " 个包");
                        return;
                    }

                    List<PackageInfo> fakeList = new ArrayList<>();
                    for (String pkg : HookInit.globalCapturedPackages) {
                        if (isSystemCorePackage(pkg) || isRealSystemPackage(pkg)) continue;
                        if (shouldReturnInstalledForPackage(pkg)) {
                            fakeList.add(createSmartFakePackageInfo(pkg, flags));
                        }
                    }

                    if (fakeList.isEmpty() && !HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                        List<
                                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
                        if (!excluded.contains(FALLBACK_PACKAGE)) {
                            PackageInfo fallbackPi = createSimpleFakePackageInfo(FALLBACK_PACKAGE);
                            if (fallbackPi != null) {
                                try {
                                    fallbackPi.applicationInfo.flags = flags;
                                } catch (Throwable ignored) {
                                }
                                fakeList.add(fallbackPi);
                            }
                        }
                    }

                    HookInit.sInstalledPackagesCache.put(cacheKey, Collections.unmodifiableList(fakeList));
                    param.setResult(fakeList);
                    ReaLog.log("package_query", "返回已安装包列表(新生成): " + fakeList.size() + " 个包");
                }
            });

            // ---------- 底层 Binder 加固 ----------
            hookIPackageManagerGetInstalledPackages(classLoader);
        } catch (Throwable t) {
            mHookInit.log("Hook getInstalledPackages失败: " + t.getMessage());
            ReaLog.log("package_query", "拦截获取已安装的应用包Hook异常: " + t.getMessage());
        }
    }

    public void hookGetInstalledApplications(ClassLoader classLoader) {
        try {
            Class<
                    ?> pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
            XposedBridge.hookAllMethods(pmClass, "getInstalledApplications", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Boolean shouldFakePermission = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                    boolean fakeEnabled = shouldFakePermission != null ? shouldFakePermission : true;
                    if (!fakeEnabled) {
                        ReaLog.log("misc", "权限防护已关闭，获取应用列表权限放行2");
                        return;
                    }

                    int flags = extractFlagsFromArgs(param.args);
                    String cacheKey = flags + "|" + mHookInit.sCacheVersion;
                    List<
                            ApplicationInfo> cachedList = HookInit.sInstalledApplicationsCache.get(cacheKey);
                    if (cachedList != null) {
                        param.setResult(new ArrayList<>(cachedList));
                        ReaLog.log("package_query", "返回已安装应用列表(缓存): " + cachedList.size() + " 个应用");
                        return;
                    }

                    List<ApplicationInfo> fakeList = new ArrayList<>();
                    for (String pkg : HookInit.globalCapturedPackages) {
                        if (isSystemCorePackage(pkg) || isRealSystemPackage(pkg)) continue;
                        if (shouldReturnInstalledForPackage(pkg)) {
                            fakeList.add(createSmartApplicationInfo(pkg, flags));
                        }
                    }

                    if (fakeList.isEmpty() && !HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                        List<
                                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
                        if (!excluded.contains(FALLBACK_PACKAGE)) {
                            ApplicationInfo fallbackAi = createFakeApplicationInfo(FALLBACK_PACKAGE);
                            if (fallbackAi != null) {
                                try {
                                    fallbackAi.flags = flags;
                                } catch (Throwable ignored) {
                                }
                                fakeList.add(fallbackAi);
                            }
                        }
                    }

                    HookInit.sInstalledApplicationsCache.put(cacheKey, Collections.unmodifiableList(fakeList));
                    param.setResult(fakeList);
                    ReaLog.log("package_query", "返回已安装应用列表(新生成): " + fakeList.size() + " 个应用");
                }
            });

            // 底层加固
            hookIPackageManagerGetInstalledApplications(classLoader);
        } catch (Throwable t) {
            mHookInit.log("Hook getInstalledApplications失败: " + t.getMessage());
            ReaLog.log("package_query", "拦截获取已安装的应用列表Hook异常: " + t.getMessage());
        }
    }

    public void hookGetPackageInfoAsUser(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getPackageInfoAsUser",
                    String.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            int flags = (int) param.args[1];
                            if (pkg == null) return;
                            if (pkg.equals(mHookInit.getCurrentTargetApp()) || isSystemCorePackage(pkg)) {
                                ReaLog.log("package_query", "获取包信息(User) 放行: " + pkg);
                                return;
                            }
                            boolean isNewlyAdded = false;
                            if (!HookInit.globalCapturedPackages.contains(pkg)) {
                                HookInit.globalCapturedPackages.add(pkg);
                                isNewlyAdded = true;
                                mHookInit.saveConfigToFile();
                                mHookInit.maybeEnableBlockExit();
                                ReaLog.log("install_detect", "获取包信息(User) 捕获新包: " + pkg);
                            }
                            if (!shouldReturnInstalledForPackage(pkg)) {
                                param.setThrowable(new PackageManager.NameNotFoundException());
                                ReaLog.log("package_query", "获取包信息(User) 返回未安装: " + pkg);
                                return;
                            }
                            if (isNewlyAdded) {
                                param.setResult(createSimpleFakePackageInfo(pkg));
                                mHookInit.log("【首查-已安装】最小化 PackageInfo (AsUser): " + pkg);
                                ReaLog.log("package_query", "获取包信息(User) 首查 -> 最小化: " + pkg);
                            } else {
                                param.setResult(createSmartFakePackageInfo(pkg, flags));
                                mHookInit.log("【续查-已安装】完整 PackageInfo (AsUser): " + pkg);
                                ReaLog.log("package_query", "获取包信息(User) 续查 -> 完整: " + pkg);
                            }
                        }
                    }
            );
            ReaLog.log("package_query", "已启用 获取包信息(User)");
        } catch (Throwable t) {
            mHookInit.log("Hook 获取包信息(User)失败: " + t.getMessage());
            ReaLog.log("package_query", "获取包信息(User) Hook异常: " + t.getMessage());
        }
    }

    public void hookGetInstalledPackagesAsUser(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Class<
                    ?> pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
            XposedBridge.hookAllMethods(pmClass, "getInstalledPackagesAsUser", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Boolean fake = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                    if (fake != null && !fake) {
                        ReaLog.log("package_query", "已安装的软件包(User): 已关闭，不拦截");
                        return;
                    }

                    int flags = extractFlagsFromArgs(param.args); // 第一个参数是 flags
                    List<PackageInfo> fakeList = new ArrayList<>();
                    for (String pkg : HookInit.globalCapturedPackages) {
                        if (shouldReturnInstalledForPackage(pkg)) {
                            fakeList.add(createSmartFakePackageInfo(pkg, flags));
                        }
                    }

                    if (fakeList.isEmpty() && !HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                        List<
                                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
                        if (!excluded.contains(FALLBACK_PACKAGE)) {
                            PackageInfo fallbackPi = createSimpleFakePackageInfo(FALLBACK_PACKAGE);
                            if (fallbackPi != null) {
                                try {
                                    fallbackPi.applicationInfo.flags = flags;
                                } catch (Throwable ignored) {
                                }
                                fakeList.add(fallbackPi);
                            }
                        }
                    }

                    param.setResult(fakeList);
                    ReaLog.log("package_query", "已安装的软件包(User): 返回 " + fakeList.size() + " 个包");
                }
            });
        } catch (Throwable t) {
            mHookInit.log("Hook 已安装的软件包(User)失败: " + t.getMessage());
            ReaLog.log("package_query", "Hook 已安装的软件包(User)异常: " + t.getMessage());
        }
    }

    public void hookGetInstalledApplicationsAsUser(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        try {
            Class<
                    ?> pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
            XposedBridge.hookAllMethods(pmClass, "getInstalledApplicationsAsUser", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Boolean fake = HookInit.permissionFakeMap.get(mHookInit.getCurrentTargetApp());
                    if (fake != null && !fake) {
                        ReaLog.log("package_query", "安装的软件包(User): 已关闭，不拦截");
                        return;
                    }

                    int flags = extractFlagsFromArgs(param.args);
                    List<ApplicationInfo> fakeList = new ArrayList<>();
                    for (String pkg : HookInit.globalCapturedPackages) {
                        if (shouldReturnInstalledForPackage(pkg)) {
                            fakeList.add(createSmartApplicationInfo(pkg, flags));
                        }
                    }

                    if (fakeList.isEmpty() && !HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                        List<
                                String> excluded = HookInit.excludedPackagesMap.getOrDefault(mHookInit.getCurrentTargetApp(), new ArrayList<>());
                        if (!excluded.contains(FALLBACK_PACKAGE)) {
                            ApplicationInfo fallbackAi = createFakeApplicationInfo(FALLBACK_PACKAGE);
                            if (fallbackAi != null) {
                                try {
                                    fallbackAi.flags = flags;
                                } catch (Throwable ignored) {
                                }
                                fakeList.add(fallbackAi);
                            }
                        }
                    }

                    param.setResult(fakeList);
                    ReaLog.log("package_query", "安装的软件包(User): 返回 " + fakeList.size() + " 个应用");
                }
            });
        } catch (Throwable t) {
            mHookInit.log("Hook 安装的软件包(User)失败: " + t.getMessage());
            ReaLog.log("package_query", "Hook 安装的软件包(User)异常: " + t.getMessage());
        }
    }

    public void hookPackageInstaller(ClassLoader classLoader) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        try {
            Class<
                    ?> clazz = XposedHelpers.findClassIfExists("android.content.pm.PackageInstaller", classLoader);
            if (clazz == null) {
                ReaLog.log("package_query", "包安装程序类不存在，跳过Hook");
                return;
            }
            XposedBridge.hookAllMethods(clazz, "getAllPackages", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    List<String> fake = new ArrayList<>();
                    for (String pkg : HookInit.globalCapturedPackages) {
                        if (shouldReturnInstalledForPackage(pkg)) {
                            fake.add(pkg);
                        }
                    }
                    param.setResult(fake);
                    ReaLog.log("package_query", "获取包安装程序 返回 " + fake.size() + " 个包");
                }
            });
            ReaLog.log("package_query", "启用获取包安装程序");
        } catch (Throwable t) {
            mHookInit.log("Hook PackageInstaller失败: " + t.getMessage());
            ReaLog.log("package_query", "Hook 获取包安装程序异常: " + t.getMessage());
        }
    }

    public void hookIsApplicationEnabled(ClassLoader classLoader) {
        try {
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader),
                    "isApplicationEnabled",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (param.args.length == 1 && param.args[0] instanceof String) {
                                String packageName = (String) param.args[0];
                                if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp())) {
                                    boolean result = shouldReturnInstalledForPackage(packageName);
                                    param.setResult(result);
                                    ReaLog.log("package_query", "包名捕获: " + packageName + " -> " + (result ? "启用" : "禁用"));
                                }
                            }
                        }
                    }
            );
            ReaLog.log("misc", "已启用程序包名捕获");
        } catch (Throwable t) {
            mHookInit.log("Hook isApplicationEnabled失败: " + t.getMessage());
            ReaLog.log("misc", "Hook 程序包名捕获异常: " + t.getMessage());
        }
    }

    public void hookGetActivityInfo(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getActivityInfo",
                    ComponentName.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            ComponentName component = (ComponentName) param.args[0];
                            if (component == null) {
                                ReaLog.log("package_query", "获取活动信息: Component为空");
                                return;
                            }
                            String packageName = component.getPackageName();
                            if (packageName == null) {
                                ReaLog.log("package_query", "获取活动信息: 包名为空");
                                return;
                            }
                            if (packageName.equals(mHookInit.getCurrentTargetApp())) return;
                            if (isSystemCorePackage(packageName)) {
                                ReaLog.log("package_query", "获取活动信息: " + packageName + " 系统包，放行");
                                return;
                            }
                            // 社交分享 SDK 自检放行：返回真实 ActivityInfo，分享自检才能通过
                            if (shouldPassShareSdkQuery(packageName)) return;

                            if (!shouldReturnInstalledForPackage(packageName)) {
                                param.setThrowable(new PackageManager.NameNotFoundException("Activity not found"));
                                ReaLog.log("package_query", "获取活动信息: " + packageName + " 设置未安装");
                                return;
                            }
                            Object fake = createFakeActivityInfo(packageName, component.getClassName());
                            if (fake != null) {
                                param.setResult(fake);
                                ReaLog.log("package_query", "获取活动信息: " + packageName + " 返回虚假ActivityInfo");
                            }
                        }
                    }
            );
            ReaLog.log("package_query", "已启用活动信息检查");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook 活动信息检查失败: " + t.getMessage());
            ReaLog.log("package_query", "活动信息检查异常: " + t.getMessage());
        }
    }

/** 底层 Binder 加固：拦截 IPackageManager.getInstalledPackages 防止应用通过反射直接调用 Binder 代理绕过上层 Hook */
    private void hookIPackageManagerGetInstalledPackages(ClassLoader classLoader) {
        try {
            Object iPM = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", classLoader),
                    "getPackageManager"
            );
            if (iPM != null) {
                XposedBridge.hookAllMethods(iPM.getClass(), "getInstalledPackages", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Class<
                                    ?> sliceClass = Class.forName("android.content.pm.ParceledListSlice");
                            Method emptyMethod = sliceClass.getMethod("emptyList");
                            Object emptySlice = emptyMethod.invoke(null);
                            param.setResult(emptySlice);
                            ReaLog.log("package_query", "底层Binder: 返回空列表");
                        } catch (Throwable e) {
                            param.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private void hookIPackageManagerGetInstalledApplications(ClassLoader classLoader) {
        try {
            Object iPM = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", classLoader),
                    "getPackageManager"
            );
            if (iPM != null) {
                XposedBridge.hookAllMethods(iPM.getClass(), "getInstalledApplications", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Class<
                                    ?> sliceClass = Class.forName("android.content.pm.ParceledListSlice");
                            Method emptyMethod = sliceClass.getMethod("emptyList");
                            Object emptySlice = emptyMethod.invoke(null);
                            param.setResult(emptySlice);
                            ReaLog.log("package_query", "底层Binder: 返回空列表");
                        } catch (Throwable e) {
                            param.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private Object createFakeActivityInfo(String packageName, String className) {
        try {
            Class<?> activityInfoClass = Class.forName("android.content.pm.ActivityInfo");
            Object activityInfo = activityInfoClass.newInstance();
            XposedHelpers.setObjectField(activityInfo, "packageName", packageName);
            XposedHelpers.setObjectField(activityInfo, "name", className);
            XposedHelpers.setObjectField(activityInfo, "enabled", true);
            XposedHelpers.setObjectField(activityInfo, "exported", true);
            XposedHelpers.setIntField(activityInfo, "flags", 0);
            XposedHelpers.setIntField(activityInfo, "theme", 0);
            XposedHelpers.setIntField(activityInfo, "uiOptions", 0);
            ApplicationInfo appInfo = createFakeApplicationInfo(packageName);
            XposedHelpers.setObjectField(activityInfo, "applicationInfo", appInfo);
            return activityInfo;
        } catch (Throwable e) {
            return null;
        }
    }

    public void hookGetInstallerPackageName(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getInstallerPackageName",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String pkg = (String) param.args[0];
                            if (pkg != null) {
                                captureValidPackage(pkg);
                            }
                            if (pkg == null) {
                                ReaLog.log("package_query", "获取安装包名: 包名为空");
                                return;
                            }
                            if (pkg.equals(mHookInit.getCurrentTargetApp())) return;
                            if (isSystemCorePackage(pkg)) {
                                ReaLog.log("package_query", "获取安装包名: " + pkg + " 系统包，放行");
                                return;
                            }
                            if (!shouldReturnInstalledForPackage(pkg)) {
                                param.setResult(null);
                                ReaLog.log("package_query", "获取安装包名: " + pkg + " 设置未安装，返回null");
                                return;
                            }
                            param.setResult("com.android.vending");
                            ReaLog.log("package_query", "获取安装包名: " + pkg + " 返回虚假来源: com.android.vending");
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("Hook 获取安装包名 失败: " + t.getMessage());
        }
    }

// 与启动拦截hookStartActivity冲突-未启用
    public void hookGetLaunchIntentForPackage(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "getLaunchIntentForPackage",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String packageName = (String) param.args[0];
                            if (packageName != null) {
                                captureValidPackage(packageName); // 新增
                            }
                            handleLaunchIntentQuery(param, "getLaunchIntentForPackage");
                        }
                    }
            );
            // 同样处理 AsUser 版本
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "getLaunchIntentForPackageAsUser",
                        String.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                String packageName = (String) param.args[0];
                                if (packageName != null) {
                                    captureValidPackage(packageName); // 新增
                                }
                                handleLaunchIntentQuery(param, "getLaunchIntentForPackageAsUser");
                            }
                        }
                );
            } catch (NoSuchMethodError e) {
            }
        } catch (Throwable t) {
            mHookInit.log("❌ Hook Hook 启动意图查询 失败: " + t.getMessage());
            ReaLog.log("intent_query", "Hook 启动意图查询异常: " + t.getMessage());
        }
    }

    private void handleLaunchIntentQuery(XC_MethodHook.MethodHookParam param, String methodName) {
        try {
            String packageName = (String) param.args[0];
            if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp())) {
                if (!shouldReturnInstalledForPackage(packageName)) {
                    param.setResult(null);
                    ReaLog.log("launch_intercept", methodName + ": " + packageName + " 设置了未安装，返回null");
                    return;
                }
                boolean isCaptured = HookInit.globalCapturedPackages.contains(packageName);
                if (isCaptured) {
                    Intent fakeIntent = new Intent(Intent.ACTION_MAIN);
                    fakeIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    fakeIntent.setPackage(packageName);
                    fakeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    param.setResult(fakeIntent);
                    mHookInit.log("启动拦截: " + methodName + " -> 返回Intent: " + packageName);
                    ReaLog.log("launch_intercept", methodName + ": " + packageName + " 返回意图Intent");
                } else {
                    ReaLog.log("launch_intercept", "启动拦截: " + methodName + ": " + packageName + " 未捕获启动意图，放行");
                }
            }
        } catch (Throwable t) {
            ReaLog.log("intent_query", "启动意图异常: " + t.getMessage());
        }
    }

    public void hookCanStartActivity(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ContextImpl",
                    classLoader,
                    "startActivity",
                    Intent.class, Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            Intent intent = (Intent) param.args[0];
                            if (intent == null) return;
                            String targetPackage = extractPackageFromIntent(intent);
                            if (targetPackage != null && !targetPackage.equals(mHookInit.getCurrentTargetApp())) {
                                boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                if (!shouldReturnInstalled) {
                                    param.setThrowable(new ActivityNotFoundException("No Activity found to handle " + intent));
                                    ReaLog.log("launch_intercept", "启动拦截: " + targetPackage + " 设置了未安装，返回null");
                                    return;
                                } else {
                                    boolean isCaptured = HookInit.globalCapturedPackages.contains(targetPackage);
                                    if (isCaptured) {
                                        ReaLog.log("launch_intercept", "允许启动应用: " + targetPackage);
                                        mHookInit.log("启动拦截: 允许虚假启动应用: " + targetPackage);
                                    }
                                }
                            }
                        }
                    }
            );
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ContextImpl",
                        classLoader,
                        "startActivity",
                        Intent.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                Intent intent = (Intent) param.args[0];
                                if (intent == null) return;
                                String targetPackage = extractPackageFromIntent(intent);
                                if (targetPackage != null && !targetPackage.equals(mHookInit.getCurrentTargetApp())) {
                                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                    if (!shouldReturnInstalled) {
                                        param.setThrowable(new ActivityNotFoundException("No Activity found to handle " + intent));
                                        ReaLog.log("launch_intercept", "启动拦截(单参数): " + targetPackage + " 设置了未安装");
                                        return;
                                    } else {
                                        boolean isCaptured = HookInit.globalCapturedPackages.contains(targetPackage);
                                        if (isCaptured) {
                                            ReaLog.log("launch_intercept", "启动拦截(单参数): 允许启动应用: " + targetPackage);
                                            mHookInit.log("启动拦截(Intent) -> 允许启动: " + targetPackage);
                                        }
                                    }
                                }
                            }
                        }
                );
            } catch (Throwable t) {
            }
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.Activity",
                        classLoader,
                        "startActivityForResult",
                        Intent.class, int.class, Bundle.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                Intent intent = (Intent) param.args[0];
                                if (intent == null) return;
                                String targetPackage = extractPackageFromIntent(intent);
                                if (targetPackage != null && !targetPackage.equals(mHookInit.getCurrentTargetApp())) {
                                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                    if (!shouldReturnInstalled) {
                                        final Activity activity = (Activity) param.thisObject;
                                        final int requestCode = (int) param.args[1];
                                        More.postOnMainThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    XposedHelpers.callMethod(activity, "onActivityResult",
                                                            requestCode, Activity.RESULT_CANCELED, (Intent) null);
                                                } catch (Throwable e) {
                                                    ReaLog.log("launch_intercept", "启动拦截(结果): 延迟回调异常: " + e.getMessage());
                                                }
                                            }
                                        }, 300);
                                        param.setResult(null);
                                        ReaLog.log("launch_intercept", "启动拦截(结果): " + targetPackage + " 设置了未安装，返回null");
                                        return;
                                    } else {
                                        boolean isCaptured = HookInit.globalCapturedPackages.contains(targetPackage);
                                        if (isCaptured) {
                                            ReaLog.log("launch_intercept", "启动拦截(结果): 允许启动应用: " + targetPackage);
                                            mHookInit.log("启动拦截(结果) -> 允许启动: " + targetPackage);
                                        }
                                    }
                                }
                            }
                        }
                );
            } catch (Throwable t) {
            }
            ReaLog.log("launch_intercept", "已启用启动意图相关方法");
        } catch (Throwable t) {
            ReaLog.log("intent_query", "启动意图异常: " + t.getMessage());
        }
    }

    public void hookReflectInstallCheck(ClassLoader classLoader) {
        try {
            Class<?> methodClass = Class.forName("java.lang.reflect.Method");
            XposedBridge.hookAllMethods(methodClass, "invoke", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    try {
                        Method method = (Method) param.thisObject;
                        if (method == null) return;
                        String methodName = method.getName();
                        if (!"getPackageInfo".equals(methodName) && !"getApplicationInfo".equals(methodName)) {
                            return;
                        }
                        Class<?> declaringClass = method.getDeclaringClass();
                        if (declaringClass == null) return;
                        String className = declaringClass.getName();
                        if (!className.contains("PackageManager")) {
                            return;
                        }
                        String targetPkg = extractPackageFromReflectArgs(param.args);
                        if (targetPkg == null || isSystemCorePackage(targetPkg) || isReflectRetryInvocation()) {
                            return;
                        }
                        if (!shouldReturnInstalledForPackage(targetPkg)) {
                            param.setResult(null);
                            ReaLog.log("package_query", "反射返回未安装: " + targetPkg);
                            return;
                        }
                        int flags = extractFlagsFromReflectArgs(method, param.args);
                        Object fakeResult = methodName.equals("getPackageInfo") ? createSmartFakePackageInfo(targetPkg, flags) : createSmartApplicationInfo(targetPkg, flags);
                        param.setResult(fakeResult);
                        ReaLog.log("package_query", "反射返回虚假信息: " + targetPkg);
                    } catch (Throwable t) {
                    }
                }
            });
            ReaLog.log("package_query", "反射调用检测已启用");
        } catch (Throwable t) {
        }
    }

    private boolean isReflectRetryInvocation() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        int pkgManagerCallCount = 0;
        for (StackTraceElement element : stackTrace) {
            if ((element.getClassName().equals("android.content.pm.PackageManager") ||
                            element.getClassName().equals("android.app.ApplicationPackageManager")) &&
                    (element.getMethodName().equals("getPackageInfo") ||
                            element.getMethodName().equals("getApplicationInfo"))) {
                pkgManagerCallCount++;
                if (pkgManagerCallCount >= 2) return true;
            }
        }
        return false;
    }

    private String extractPackageFromReflectArgs(Object[] args) {
        if (args == null || args.length < 2) return null;
        Object[] methodArgs = (Object[]) args[1];
        for (Object arg : methodArgs) {
            if (arg instanceof String && isValidPackageName((String) arg)) {
                return (String) arg;
            }
        }
        return null;
    }

    private int extractFlagsFromReflectArgs(Method method, Object[] args) {
        if (args == null || args.length < 2) return 0;
        Object[] methodArgs = (Object[]) args[1];
        if (method.getParameterTypes().length == 2 && method.getParameterTypes()[1] == int.class) {
            for (Object arg : methodArgs) {
                if (arg instanceof Integer) {
                    return (int) arg;
                }
            }
        }
        return 0;
    }

/**
 * 安全地从 getInstalledPackages / getInstalledApplications 的参数中提取 flags 兼容 Android 13+ 的
 * PackageInfoFlags 对象和旧版 int
 */
    private int extractFlagsFromArgs(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return 0;
        }
        Object first = args[0];
        if (first instanceof Integer) {
            return (int) first;
        }
        // Android 13+ (API 33+) 使用 PackageInfoFlags 对象
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Method getValue = first.getClass().getMethod("getValue");
                return (int) (long) getValue.invoke(first);
            } catch (Throwable ignored) {
                // 若反射失败则返回 0，不影响后续逻辑
            }
        }
        return 0;
    }

    // ---------- 文件系统及底层 Hook ----------

    public void hookFileSystemInstallCheck(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "exists",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            File file = (File) param.thisObject;
                            String path = file.getAbsolutePath();
                            if (!path.startsWith("/data/app/") && !path.startsWith("/data/data/") && !path.startsWith("/data/user/")) {
                                return;
                            }
                            if (isAppInstallPath(path)) {
                                String targetPkg = extractPackageFromPath(path);
                                if (targetPkg != null && !targetPkg.equals(mHookInit.getCurrentTargetApp()) && !isSystemCorePackage(targetPkg)) {
                                    boolean result = shouldReturnInstalledForPackage(targetPkg);
                                    param.setResult(result);
                                    ReaLog.log("file_system", "路径检查: " + path + " -> " + (result ? "存在" : "不存在"));
                                }
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "isDirectory",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            File file = (File) param.thisObject;
                            String path = file.getAbsolutePath();
                            if (!path.startsWith("/data/app/") && !path.startsWith("/data/data/") && !path.startsWith("/data/user/")) {
                                return;
                            }
                            if (isAppInstallPath(path)) {
                                String targetPkg = extractPackageFromPath(path);
                                if (targetPkg != null && !targetPkg.equals(mHookInit.getCurrentTargetApp()) && !isSystemCorePackage(targetPkg)) {
                                    boolean result = shouldReturnInstalledForPackage(targetPkg);
                                    param.setResult(result);
                                    ReaLog.log("file_system", "检查目录: " + path + " -> " + (result ? "目录存在" : "目录不存在"));
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            mHookInit.log("Hook文件路径检测失败: " + t.getMessage());
            ReaLog.log("file_system", "Hook安装路径检查异常: " + t.getMessage());
        }
    }

    private boolean isAppInstallPath(String path) {
        return (path.startsWith("/data/data/") ||
                path.startsWith("/data/app/") ||
                path.startsWith("/data/user/") ||
                path.startsWith("/data/user_de/") ||
                path.contains("/base.apk") ||
                path.endsWith("/lib") ||
                path.endsWith("/lib64") ||
                path.contains("/system/app/") ||
                path.contains("/system/priv-app/") ||
                (path.startsWith("/data/app/~~") && path.contains("/base.apk")));
    }

    public void hookDataAppDirectoryListing(ClassLoader classLoader) {
        try {
            final Runnable ensureCache = new Runnable() {
                @Override
                public void run() {
                    int currentVersion = mHookInit.sCacheVersion;
                    if (mHookInit.sCachedAppDirNames != null && mHookInit.sCachedAppDirFiles != null) {
                        return;
                    }
                    synchronized (mHookInit.sCacheLock) {
                        if (mHookInit.sCachedAppDirNames != null && mHookInit.sCachedAppDirFiles != null && mHookInit.sCacheVersion == currentVersion) {
                            return;
                        }
                        List<String> newNames = new ArrayList<>();
                        List<File> newFiles = new ArrayList<>();
                        File parent = new File("/data/app");
                        for (String pkg : HookInit.globalCapturedPackages) {
                            if (shouldReturnInstalledForPackage(pkg)) {
                                String dirName = pkg.replace('.', '-') + "-" + (Math.abs(pkg.hashCode()) % 10 + 1);
                                newNames.add(dirName);
                                newFiles.add(new File(parent, dirName));
                            }
                        }
                        mHookInit.sCachedAppDirNames = Collections.unmodifiableList(newNames);
                        mHookInit.sCachedAppDirFiles = newFiles.toArray(new File[0]);
                        ReaLog.log("file_system", "更新 /data/app 缓存: " + newNames.size() + " 个目录");
                    }
                }
            };

            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "list",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            File f = (File) param.thisObject;
                            String path = f.getAbsolutePath();
                            if (path != null && (path.equals("/data/app") || path.equals("/data/app/"))) {
                                ensureCache.run();
                                List<String> names = mHookInit.sCachedAppDirNames;
                                if (names != null) {
                                    param.setResult(names.toArray(new String[0]));
                                    ReaLog.log("file_system", "File.list /data/app 返回 " + names.size() + " 个条目");
                                } else {
                                    param.setResult(new String[0]);
                                }
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "listFiles",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            File f = (File) param.thisObject;
                            String path = f.getAbsolutePath();
                            if (path != null && (path.equals("/data/app") || path.equals("/data/app/"))) {
                                ensureCache.run();
                                File[] files = mHookInit.sCachedAppDirFiles;
                                if (files != null) {
                                    param.setResult(files.clone());
                                    ReaLog.log("file_system", "File.listFiles /data/app 返回 " + files.length + " 个文件");
                                } else {
                                    param.setResult(new File[0]);
                                }
                            }
                        }
                    });
            ReaLog.log("file_system", "已Hook /data/app 目录枚举");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook /data/app 目录枚举失败: " + t.getMessage());
            ReaLog.log("file_system", "Hook /data/app 目录枚举异常: " + t.getMessage());
        }
    }

    public void hookLibDirectoryChecks(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "exists",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            checkAndFakeFileExistence(param, "exists");
                            checkAndFakeLibExistence(param, "exists");
                        }
                    });
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "isDirectory",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            checkAndFakeFileExistence(param, "isDirectory");
                            checkAndFakeLibExistence(param, "isDirectory");
                        }
                    });
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "list",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            checkAndFakeFileListing(param, "list");
                        }
                    });
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "listFiles",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            checkAndFakeFileListing(param, "listFiles");
                        }
                    });
        } catch (Throwable t) {
            mHookInit.log("Hook Lib目录检测失败: " + t.getMessage());
            ReaLog.log("file_system", "Lib目录检查异常: " + t.getMessage());
        }
    }

    private void checkAndFakeFileExistence(XC_MethodHook.MethodHookParam param, String methodName) {
        try {
            File file = (File) param.thisObject;
            String path = file.getAbsolutePath();
            if (path.contains("/" + mHookInit.getCurrentTargetApp() + "/") || path.endsWith("/" + mHookInit.getCurrentTargetApp())) {
                return;
            }
            if (path.contains("packages.xml") || path.contains("packages.list")) {
                BufferedReader reader = null;
                try {
                    reader = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(path));
                    String line;
                    Pattern xmlPattern = Pattern.compile("package name=\"([a-zA-Z0-9._]+)\"");
                    Pattern listPattern = Pattern.compile("package:([a-zA-Z0-9._]+)");
                    while ((line = reader.readLine()) != null) {
                        Matcher xmlMatcher = xmlPattern.matcher(line);
                        if (xmlMatcher.find()) {
                            captureValidPackage(xmlMatcher.group(1));
                            continue;
                        }
                        Matcher listMatcher = listPattern.matcher(line);
                        if (listMatcher.find()) {
                            captureValidPackage(listMatcher.group(1));
                        }
                    }
                } catch (IOException ignored) {
                } finally {
                    if (reader != null) {
                        try {
                            reader.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
                return;
            }
            if (isAppInstallPath(path)) {
                String packageName = extractPackageFromPath(path);
                if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp()) && !isSystemCorePackage(packageName)) {
                    int pkgStatus = getPackageStatus(packageName);
                    if (pkgStatus == 0) {
                        if (methodName.equals("exists") || methodName.equals("isDirectory") ||
                                methodName.equals("isFile") || methodName.equals("canRead")) {
                            param.setResult(false);
                            ReaLog.log("file_system", "文件存在性检查: " + path + " -> false (固定未安装)");
                        }
                        return;
                    }
                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                    if (!shouldReturnInstalled) {
                        param.setResult(false);
                        ReaLog.log("file_system", "文件存在性检查: " + path + " -> false (全局未安装)");
                        return;
                    }
                    if (HookInit.globalCapturedPackages.contains(packageName)) {
                        if (methodName.equals("exists") || methodName.equals("canRead")) {
                            param.setResult(true);
                            ReaLog.log("file_system", "文件存在性检查: " + path + " -> true (已捕获)");
                        } else if (methodName.equals("isDirectory")) {
                            param.setResult(path.contains("/data/data/") || path.endsWith("/lib"));
                        } else if (methodName.equals("isFile")) {
                            param.setResult(path.contains("/base.apk") || path.endsWith(".apk"));
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    private void checkAndFakeLibExistence(XC_MethodHook.MethodHookParam param, String methodName) {
        try {
            File file = (File) param.thisObject;
            String path = file.getAbsolutePath();
            if (isLibDirectoryPath(path)) {
                String packageName = extractPackageNameFromLibPath(path);
                if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp())) {
                    int pkgStatus = getPackageStatus(packageName);
                    if (pkgStatus == 0) {
                        if (methodName.equals("exists") || methodName.equals("isDirectory")) {
                            param.setResult(false);
                            ReaLog.log("file_system", "Lib目录检查: " + path + " -> false (固定未安装)");
                        }
                        return;
                    }
                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                    if (!shouldReturnInstalled) {
                        if (methodName.equals("exists") || methodName.equals("isDirectory")) {
                            param.setResult(false);
                            ReaLog.log("file_system", "Lib目录检查: " + path + " -> false (全局未安装)");
                        }
                        return;
                    }
                    if (HookInit.globalCapturedPackages.contains(packageName)) {
                        if (methodName.equals("exists")) {
                            param.setResult(true);
                            ReaLog.log("file_system", "Lib目录检查: " + path + " -> true (已捕获)");
                        } else if (methodName.equals("isDirectory")) {
                            param.setResult(true);
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    private void checkAndFakeFileListing(XC_MethodHook.MethodHookParam param, String methodName) {
        try {
            File file = (File) param.thisObject;
            String path = file.getAbsolutePath();
            if (isLibDirectoryPath(path)) {
                String packageName = extractPackageNameFromLibPath(path);
                if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp())) {
                    int pkgStatus = getPackageStatus(packageName);
                    if (pkgStatus == 0) {
                        if (methodName.equals("list")) {
                            param.setResult(new String[0]);
                        } else if (methodName.equals("listFiles")) {
                            param.setResult(new File[0]);
                        }
                        ReaLog.log("file_system", "文件列表检查: " + path + " -> 空数组 (固定未安装)");
                        return;
                    }
                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                    if (!shouldReturnInstalled) {
                        if (methodName.equals("list")) {
                            param.setResult(new String[0]);
                        } else if (methodName.equals("listFiles")) {
                            param.setResult(new File[0]);
                        }
                        ReaLog.log("file_system", "文件列表检查: " + path + " -> 空数组 (全局未安装)");
                        return;
                    }
                    if (HookInit.globalCapturedPackages.contains(packageName)) {
                        if (methodName.equals("list")) {
                            String[] fakeLibs = createFakeLibList(path);
                            param.setResult(fakeLibs);
                            ReaLog.log("file_system", "文件列表检查: " + path + " -> 返回 " + fakeLibs.length + " 个Lib文件");
                        } else if (methodName.equals("listFiles")) {
                            File[] fakeFiles = createFakeLibFiles(path);
                            param.setResult(fakeFiles);
                            ReaLog.log("file_system", "文件列表检查: " + path + " -> 返回 " + fakeFiles.length + " 个Lib文件");
                        }
                    }
                }
            }
        } catch (Throwable t) {
        }
    }

    private boolean isLibDirectoryPath(String path) {
        if (path == null) return false;
        return (path.contains("/lib/") ||
                path.contains("/lib64/") ||
                path.contains("/lib/arm") ||
                path.contains("/lib/arm64") ||
                path.contains("/lib/x86") ||
                path.endsWith("/lib") ||
                path.contains("app-lib/"));
    }

    private String extractPackageNameFromLibPath(String path) {
        try {
            Pattern pattern = Pattern.compile("/data/app/([a-zA-Z0-9._]+)-\\d+/lib/");
            Matcher matcher = pattern.matcher(path);
            if (matcher.find()) {
                return matcher.group(1).replace('-', '.');
            }
            pattern = Pattern.compile("/data/data/([a-zA-Z0-9._]+)/lib/");
            matcher = pattern.matcher(path);
            if (matcher.find()) {
                return matcher.group(1);
            }
            pattern = Pattern.compile("/data/app-lib/([a-zA-Z0-9._]+)");
            matcher = pattern.matcher(path);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Throwable e) {
        }
        return null;
    }

    private String[] createFakeLibList(String libPath) {
        List<String> libs = new ArrayList<>();
        libs.add("libapp.so");
        libs.add("libnative.so");
        libs.add("libcore.so");
        libs.add("libutils.so");
        libs.add("libsecurity.so");
        libs.add("libcrypto.so");
        libs.add("libssl.so");
        libs.add("libz.so");
        if (libPath.contains("arm64")) {
            libs.add("libarm64.so");
        } else if (libPath.contains("arm")) {
            libs.add("libarm.so");
        } else if (libPath.contains("x86")) {
            libs.add("libx86.so");
        }
        return libs.toArray(new String[0]);
    }

    private File[] createFakeLibFiles(String libPath) {
        String[] libNames = createFakeLibList(libPath);
        File[] files = new File[libNames.length];
        for (int i = 0; i < libNames.length; i++) {
            files[i] = new File(libPath, libNames[i]);
        }
        return files;
    }

    public void hookOsLibcoreStat(ClassLoader classLoader) {
        try {
            Class<?> osClass = XposedHelpers.findClassIfExists("android.system.Os", classLoader);
            if (osClass == null)
                osClass = XposedHelpers.findClassIfExists("libcore.io.Os", classLoader);
            if (osClass == null) {
                ReaLog.log("file_system", "Os类不存在，跳过Os.stat Hook");
                return;
            }
            for (String method : new String[]{"stat", "lstat"}) {
                try {
                    XposedHelpers.findAndHookMethod(osClass, method, String.class,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                    String path = (String) param.args[0];
                                    if (path == null) return;
                                    String pkg = extractPackageFromPath(path);
                                    if (pkg != null && !pkg.equals(mHookInit.getCurrentTargetApp()) && !isSystemCorePackage(pkg)) {
                                        if (!shouldReturnInstalledForPackage(pkg)) {
                                            try {
                                                Class<
                                                        ?> errnoClass = Class.forName("android.system.ErrnoException");
                                                Constructor<
                                                        ?> ctor = errnoClass.getConstructor(String.class, int.class);
                                                Throwable errnoEx = (Throwable) ctor.newInstance("stat", 2);
                                                param.setThrowable(errnoEx);
                                                ReaLog.log("file_system", "Os.stat 拦截: " + path + " -> 返回未安装");
                                            } catch (Exception e) {
                                                param.setResult(null);
                                                ReaLog.log("file_system", "Os.stat 拦截(降级): " + path);
                                            }
                                        } else {
                                            ReaLog.log("file_system", "Os.stat 放行: " + path);
                                        }
                                    }
                                }
                            }
                    );
                } catch (Throwable ignored) {
                }
            }
            ReaLog.log("file_system", "已Hook Os.stat/lstat");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook Os.stat失败: " + t.getMessage());
            ReaLog.log("file_system", "HookOsLibcoreStat异常: " + t.getMessage());
        }
    }

    public void hookOsLibcoreReaddir(ClassLoader classLoader) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                ReaLog.log("file_system", "Android版本过低，跳过Os.readdir Hook");
                return;
            }
            Class<?> osClass = XposedHelpers.findClassIfExists("android.system.Os", classLoader);
            if (osClass == null)
                osClass = XposedHelpers.findClassIfExists("libcore.io.Os", classLoader);
            if (osClass == null) {
                ReaLog.log("file_system", "Os类不存在，跳过readdir Hook");
                return;
            }
            XposedBridge.hookAllMethods(osClass, "readdir", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    if (param.args == null || param.args.length < 1) return;
                    String path = null;
                    for (Object arg : param.args) {
                        if (arg instanceof String) {
                            path = (String) arg;
                            break;
                        }
                    }
                    if (path != null && (path.startsWith("/data/app") || path.startsWith("/data/user"))) {
                        param.setResult(new String[0]);
                        ReaLog.log("file_system", "Os.readdir 拦截: " + path + " -> 返回空列表");
                    } else {
                        ReaLog.log("file_system", "Os.readdir 放行: " + path);
                    }
                }
            });
            ReaLog.log("file_system", "已Hook Os.readdir");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook Os.readdir失败: " + t.getMessage());
            ReaLog.log("file_system", "HookOsLibcoreReaddir异常: " + t.getMessage());
        }
    }

    public void hookFileLength(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod("java.io.File", classLoader, "length",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            File f = (File) param.thisObject;
                            String path = f.getAbsolutePath();
                            if (isAppInstallPath(path)) {
                                String pkg = extractPackageFromPath(path);
                                if (pkg != null && !pkg.equals(mHookInit.getCurrentTargetApp()) && !isSystemCorePackage(pkg)) {
                                    if (!HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                                        param.setResult(0L);
                                        ReaLog.log("file_system", "File.length 拦截: " + path + " -> 返回0");
                                    } else {
                                        param.setResult(5 * 1024 * 1024L);
                                        ReaLog.log("file_system", "File.length 拦截: " + path + " -> 返回5MB");
                                    }
                                }
                            }
                        }
                    }
            );
            ReaLog.log("file_system", "已Hook File.length");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook File.length失败: " + t.getMessage());
            ReaLog.log("file_system", "HookFileLength异常: " + t.getMessage());
        }
    }

    public void hookRandomAccessFile(ClassLoader classLoader) {
        try {
            Class<
                    ?> rafClass = XposedHelpers.findClassIfExists("java.io.RandomAccessFile", classLoader);
            if (rafClass == null) {
                ReaLog.log("file_system", "随机存取文件类不存在，跳过");
                return;
            }
            XposedBridge.hookAllConstructors(rafClass, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    String path = null;
                    for (Object arg : param.args) {
                        if (arg instanceof String) {
                            path = (String) arg;
                            break;
                        } else if (arg instanceof File) {
                            path = ((File) arg).getAbsolutePath();
                            break;
                        }
                    }
                    if (path == null) {
                        return;
                    }
                    if (isAppInstallPath(path)) {
                        String pkg = extractPackageFromPath(path);
                        if (pkg != null && !pkg.equals(mHookInit.getCurrentTargetApp()) && !isSystemCorePackage(pkg)) {
                            int s = getPackageStatus(pkg);
                            if (s == 0 || !HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                                param.setThrowable(new java.io.FileNotFoundException(path));
                                ReaLog.log("file_system", "随机存取文件 拦截: " + path + " -> 返回未安装");
                                return;
                            }
                            ReaLog.log("file_system", "随机存取文件 放行: " + path);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            mHookInit.log("❌ Hook 随机存取文件 失败: " + t.getMessage());
            ReaLog.log("file_system", "随机存取文件异常: " + t.getMessage());
        }
    }

    public void hookProcessMemoryInfo(ClassLoader classLoader) {
        try {
            Class<
                    ?> clazz = XposedHelpers.findClassIfExists("android.app.ActivityManager", classLoader);
            if (clazz == null) {
                ReaLog.log("intent_query", "内存信息反推类不存在，跳过");
                return;
            }
            XposedHelpers.findAndHookMethod(clazz, "getProcessMemoryInfo", int[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            if (!HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true)) {
                                int[] pids = (int[]) param.args[0];
                                if (pids != null && pids.length > 0) {
                                    param.setResult(new android.os.Debug.MemoryInfo[0]);
                                    ReaLog.log("intent_query", "内存信息反推: 设置未安装，返回空数组");
                                    return;
                                }
                            }
                            ReaLog.log("intent_query", "内存信息反推: 设置已安装，返回空数组");
                        }
                    }
            );
        } catch (Throwable t) {
            mHookInit.log("❌ Hook 内存信息反推 失败: " + t.getMessage());
            ReaLog.log("anti_detection", "内存信息反推异常: " + t.getMessage());
        }
    }

    public void hookProcFileSystem(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(File.class, "exists", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    File f = (File) param.thisObject;
                    String path = f.getAbsolutePath();
                    if (path != null && (path.startsWith("/proc/net/") || path.startsWith("/sys/class/net/") || path.equals("/proc/net"))) {
                        param.setResult(false);
                        ReaLog.log("file_system", "File.exists 拦截 /proc/net: " + path + " -> false");
                    }
                }
            });
            XposedHelpers.findAndHookMethod(File.class, "isDirectory", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    File f = (File) param.thisObject;
                    String path = f.getAbsolutePath();
                    if (path != null && (path.startsWith("/proc/net/") || path.startsWith("/sys/class/net/") || path.equals("/proc/net"))) {
                        param.setResult(false);
                        ReaLog.log("file_system", "File.isDirectory 拦截 /proc/net: " + path + " -> false");
                    }
                }
            });
            XposedHelpers.findAndHookMethod(File.class, "list", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    File f = (File) param.thisObject;
                    String path = f.getAbsolutePath();
                    if (path != null && (path.startsWith("/proc/net/") || path.startsWith("/sys/class/net/"))) {
                        param.setResult(new String[0]);
                        ReaLog.log("file_system", "File.list 拦截 /proc/net: " + path + " -> 空数组");
                    }
                }
            });
            XposedHelpers.findAndHookMethod(File.class, "list", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                    File f = (File) param.thisObject;
                    String path = f.getAbsolutePath();
                    if (path != null && path.equals("/proc")) {
                        param.setResult(new String[0]);
                        ReaLog.log("file_system", "File.list 拦截 /proc -> 空数组");
                    }
                }
            });
            ReaLog.log("file_system", "已Hook /proc/net 目录拦截");
        } catch (Throwable t) {
            mHookInit.log("❌ Hook /proc/net 失败: " + t.getMessage());
            ReaLog.log("file_system", "HookProcFileSystem异常: " + t.getMessage());
        }
    }

    // ---------- OkHttp 相关（检测拦截） ----------
    public void hookOkHttp(ClassLoader classLoader) {
        final ClassLoader cl = classLoader;
        try {
            Class<?> realCallClass = XposedHelpers.findClassIfExists("okhttp3.RealCall", cl);
            if (realCallClass == null) {
                return;
            }
            XposedHelpers.findAndHookMethod(realCallClass, "enqueue", "okhttp3.Callback",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            try {
                                Object requestObj = XposedHelpers.getObjectField(param.thisObject, "originalRequest");
                                if (requestObj == null) return;
                                String url = XposedHelpers.callMethod(requestObj, "url").toString();
                                if (isDetectionUrl(url)) {
                                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                    ReaLog.log("network", "拦截OkHttp检测请求: " + url + ", 安装状态: " + (shouldReturnInstalled ? "已安装" : "未安装"));
                                    Object callback = param.args[0];
                                    fakeOkHttpResponse(callback, url, shouldReturnInstalled);
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(realCallClass, "execute",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                            try {
                                Object requestObj = XposedHelpers.getObjectField(param.thisObject, "originalRequest");
                                if (requestObj == null) return;
                                String url = XposedHelpers.callMethod(requestObj, "url").toString();
                                if (isDetectionUrl(url)) {
                                    boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                    ReaLog.log("network", "拦截OkHttp同步检测请求: " + url + ", 安装状态: " + (shouldReturnInstalled ? "已安装" : "未安装"));
                                    Object fakeResponse = createOkHttpFakeResponse(shouldReturnInstalled, cl);
                                    param.setResult(fakeResponse);
                                }
                            } catch (Throwable t) {
                            }
                        }
                    });
            ReaLog.log("network", "已启用 OkHttp检测方法");
        } catch (Throwable t) {
            mHookInit.log("Hook OkHttp失败: " + t.getMessage());
            ReaLog.log("network", "Hook OkHttp异常: " + t.getMessage());
        }
    }

    // ---------- 其他公共辅助 ----------
    // 固定配置伪造数据
    public Object createFakeResolveInfo(String packageName) {
        try {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.priority = 1;
            resolveInfo.isDefault = false;
            resolveInfo.match = 0;
            ActivityInfo activityInfo = new ActivityInfo();
            activityInfo.packageName = packageName;
            activityInfo.name = packageName + ".FakeInterceptActivity";
            activityInfo.enabled = true;
            activityInfo.exported = false;
            resolveInfo.activityInfo = activityInfo;
            return resolveInfo;
        } catch (Throwable e) {
            mHookInit.log("创建FakeResolveInfo异常: " + e.getMessage());
            return null;
        }
    }

    /*
        public Object createFakeResolveInfo(String packageName) {
        try {
            ResolveInfo resolveInfo = new ResolveInfo();
            resolveInfo.priority = 1;
            resolveInfo.isDefault = false;
            resolveInfo.match = 0;

            ActivityInfo activityInfo = new ActivityInfo();
            activityInfo.packageName = packageName;
            activityInfo.name = packageName + ".MainActivity";  // 任意占位类名
            activityInfo.enabled = true;
            activityInfo.exported = true;         // 关键：允许外部启动

            ApplicationInfo appInfo = new ApplicationInfo();
            appInfo.packageName = packageName;
            appInfo.flags = ApplicationInfo.FLAG_INSTALLED;
            appInfo.enabled = true;
            activityInfo.applicationInfo = appInfo;
            resolveInfo.activityInfo = activityInfo;

            // 添加启动意图过滤器
            IntentFilter filter = new IntentFilter(Intent.ACTION_MAIN);
            filter.addCategory(Intent.CATEGORY_LAUNCHER);
            resolveInfo.filter = filter;

            return resolveInfo;
        } catch (Throwable e) {
            return null;
        }
    }*/

    public Object createEmptyResolveInfo() {
        try {
            Class<?> resolveInfoClass = Class.forName("android.content.pm.ResolveInfo");
            Object empty = resolveInfoClass.newInstance();
            XposedHelpers.setIntField(empty, "priority", -1);
            XposedHelpers.setIntField(empty, "match", 0);
            XposedHelpers.setBooleanField(empty, "isDefault", false);
            return empty;
        } catch (Throwable e) {
            return null;
        }
    }

    // ---------- 重载方法（兜底） ----------
    public void hookOverloadMethods(ClassLoader classLoader) {
        try {
            // getPackageInfo(String, int, int)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "getPackageInfo",
                        String.class, int.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                String packageName = extractPackageName(param.args);
                                if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp())) {
                                    if (!shouldReturnInstalledForPackage(packageName)) {
                                        param.setResult(null);
                                        ReaLog.log("package_query", "封装信息重载: " + packageName + " -> null (设置未安装)");
                                        return;
                                    }
                                    if (!HookInit.globalCapturedPackages.contains(packageName)) {
                                        HookInit.globalCapturedPackages.add(packageName);
                                        ReaLog.log("install_detect", "封装重载捕获新包名: " + packageName);
                                    }
                                    Object fakeResult = createFakePackageInfo(packageName);
                                    if (fakeResult != null) {
                                        param.setResult(fakeResult);
                                        ReaLog.log("package_query", "封装重载: " + packageName + " -> 返回虚假信息");
                                    }
                                }
                            }
                        }
                );
            } catch (Throwable t) {
            }

            // getApplicationInfo(String, int, int)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "getApplicationInfo",
                        String.class, int.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                String packageName = extractPackageName(param.args);
                                if (packageName != null && !packageName.equals(mHookInit.getCurrentTargetApp())) {
                                    if (!shouldReturnInstalledForPackage(packageName)) {
                                        param.setResult(null);
                                        ReaLog.log("package_query", "申请重载: " + packageName + " -> null (设置未安装)");
                                        return;
                                    }
                                    if (!HookInit.globalCapturedPackages.contains(packageName)) {
                                        HookInit.globalCapturedPackages.add(packageName);
                                        ReaLog.log("install_detect", "申请重载捕获新包名: " + packageName);
                                    }
                                    Object fakeResult = createFakeApplicationInfo(packageName);
                                    if (fakeResult != null) {
                                        param.setResult(fakeResult);
                                        ReaLog.log("package_query", "申请重载: " + packageName + " -> 返回虚假信息");
                                    }
                                }
                            }
                        }
                );
            } catch (Throwable t) {
            }

            // getInstalledPackages(int, int)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "getInstalledPackages",
                        int.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                if (!shouldReturnInstalled) {
                                    param.setResult(new ArrayList<PackageInfo>());
                                    ReaLog.log("package_query", "已安装重载: 返回空列表 (全局未安装)");
                                    return;
                                }
                                List<PackageInfo> fakeList = new ArrayList<>();
                                for (String pkg : HookInit.globalCapturedPackages) {
                                    if (shouldReturnInstalledForPackage(pkg)) {
                                        fakeList.add(createFakePackageInfo(pkg));
                                    }
                                }
                                param.setResult(fakeList);
                                ReaLog.log("package_query", "已安装重载: 返回 " + fakeList.size() + " 个包");
                            }
                        }
                );
            } catch (Throwable t) {
            }

            // getInstalledApplications(int, int)
            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "getInstalledApplications",
                        int.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) {
                                boolean shouldReturnInstalled = HookInit.installStatusMap.getOrDefault(mHookInit.getCurrentTargetApp(), true);
                                if (!shouldReturnInstalled) {
                                    param.setResult(new ArrayList<ApplicationInfo>());
                                    ReaLog.log("package_query", "已安装申请重载: 返回空列表 (全局未安装)");
                                    return;
                                }
                                List<ApplicationInfo> fakeList = new ArrayList<>();
                                for (String pkg : HookInit.globalCapturedPackages) {
                                    if (shouldReturnInstalledForPackage(pkg)) {
                                        fakeList.add(createFakeApplicationInfo(pkg));
                                    }
                                }
                                param.setResult(fakeList);
                                ReaLog.log("package_query", "已安装申请重载: 返回 " + fakeList.size() + " 个应用");
                            }
                        }
                );
            } catch (Throwable t) {
            }
            ReaLog.log("package_query", "重载方法Hook完成");
        } catch (Throwable t) {
            mHookInit.log("Hook安装重载异常: " + t.getMessage());
            ReaLog.log("package_query", "Hook安装重载异常: " + t.getMessage());
        }
    }

    // ========== Flutter 辅助方法 ==========
    private List<String> getFakePackageList() {
        String currentApp = mHookInit.getCurrentTargetApp();
        List<String> fakeList = new ArrayList<String>(mHookInit.globalCapturedPackages);
        List<
                String> excluded = mHookInit.excludedPackagesMap.getOrDefault(currentApp, new ArrayList<
                String>());
        fakeList.removeAll(excluded);
        Iterator<String> it = fakeList.iterator();
        while (it.hasNext()) {
            String pkg = it.next();
            if (isSystemCorePackage(pkg) || isRealSystemPackage(pkg)) {
                it.remove();
            }
        }
        boolean isInstalledMode = mHookInit.installStatusMap.getOrDefault(currentApp, true);
        if (!isInstalledMode) {
            List<String> finalList = new ArrayList<String>();
            for (String pkg : fakeList) {
                if (getPackageStatus(pkg) == 1) {
                    finalList.add(pkg);
                }
            }
            return finalList;
        }
        return fakeList;
    }

    private void hookSharedPreferences(final ClassLoader classLoader) {
        try {
            Class<?> editorImplClass = XposedHelpers.findClassIfExists(
                    "android.app.SharedPreferencesImpl$EditorImpl", classLoader);
            if (editorImplClass == null) {
                editorImplClass = XposedHelpers.findClassIfExists(
                        "android.app.SharedPreferencesImpl$EditorImpl",
                        ClassLoader.getSystemClassLoader());
            }
            if (editorImplClass == null) {
                ReaLog.log("flutter", "flutterEditor实现类未找到，跳过");
                return;
            }

            XposedBridge.hookAllMethods(editorImplClass, "putString", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    if (key != null && (key.contains("violation") || key.contains("tamper") ||
                                    key.contains("root") || key.contains("fake_env") || key.contains("detected"))) {
                        param.args[1] = "false";
                        ReaLog.log("flutter", "flutter拦截 putString: " + key + " -> false");
                    }
                }
            });
            XposedBridge.hookAllMethods(editorImplClass, "putBoolean", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String key = (String) param.args[0];
                    if (key != null && (key.contains("violation") || key.contains("tamper") ||
                                    key.contains("root") || key.contains("fake_env") || key.contains("detected"))) {
                        param.args[1] = false;
                        ReaLog.log("flutter", "flutter拦截 putBoolean: " + key + " -> false");
                    }
                }
            });
            ReaLog.log("flutter", "flutter拦截Hook成功");
        } catch (Throwable t) {
            ReaLog.log("flutter", "SharedPreferences Hook 失败: " + t.getMessage());
        }
    }

    private void handleFlutterBinaryMessage(final MethodHookParam param, final ClassLoader flutterLoader) {
        try {
            String channel = (String) param.args[0];
            if (channel != null) {
                for (String keyword : FLUTTER_WHITELIST_CHANNELS) {
                    if (channel.contains(keyword)) {
                        ReaLog.log("flutter", "flutterBinary 命中白名单: " + channel);
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object handleFlutterMethodCall(String methodName, Object arguments, MethodHookParam param) {
        String currentApp = mHookInit.getCurrentTargetApp();
        try {
            // 设备指纹伪造
            if (methodName.contains("deviceInfo") || methodName.equals("getAndroidDeviceInfo")) {
                Map<String, Object> fakeInfo = new HashMap<>();
                fakeInfo.put("id", "00000000-0000-0000-0000-000000000000");
                fakeInfo.put("model", "Pixel 6");
                fakeInfo.put("manufacturer", "Google");
                fakeInfo.put("board", "redfin");
                fakeInfo.put("brand", "google");
                fakeInfo.put("device", "redfin");
                fakeInfo.put("display", "RQ3A.211001.001");
                fakeInfo.put("fingerprint", "google/redfin/redfin:11/RQ3A.211001.001/123456:user/release-keys");
                fakeInfo.put("isPhysicalDevice", false);
                Map<String, Object> version = new HashMap<>();
                version.put("sdkInt", 30);
                version.put("release", "11");
                fakeInfo.put("version", version);
                return fakeInfo;
            }

            // ---------- 单个包检测（支持更多方法名） ----------
            if (methodName.contains("isAppInstalled") || methodName.contains("checkInstalled") ||
                    "PKG".equals(methodName) || "packageCheck".equals(methodName) ||
                    methodName.equals("isInstalled") || methodName.equals("checkPackage")) {
                String targetPkg = extractPackageFromFlutterArgs(arguments);
                if (targetPkg != null) {
                    boolean result = shouldReturnInstalledForPackage(targetPkg);
                    ReaLog.log("flutter", methodName + " -> " + targetPkg + " = " + result);
                    return result;
                }
                return false;
            }

            // ---------- 获取单个包信息 ----------
            if (methodName.contains("getPackageInfo") || methodName.equals("packageInfo")) {
                String targetPkg = extractPackageFromFlutterArgs(arguments);
                if (targetPkg != null && shouldReturnInstalledForPackage(targetPkg)) {
                    PackageInfo fakePi = createSmartFakePackageInfo(targetPkg, PackageManager.GET_META_DATA);
                    if (fakePi != null) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("packageName", targetPkg);
                        result.put("versionName", fakePi.versionName);
                        result.put("versionCode", fakePi.versionCode);
                        result.put("installTime", fakePi.firstInstallTime);
                        ReaLog.log("flutter", "返回虚假 PackageInfo: " + targetPkg);
                        return result;
                    }
                }
                return null;
            }

            // ---------- 获取已安装包列表 ----------
            if (methodName.contains("queryInstalledPackages") || methodName.contains("getInstalledPackages") ||
                    methodName.contains("getAllPackageInfo") || methodName.equals("getPackages")) {
                List<String> fakeList = getFakePackageList();
                ReaLog.log("flutter", "返回 " + fakeList.size() + " 个应用包");
                return fakeList;
            }

            // 其他方法放行
            return null;

        } catch (Throwable t) {
            ReaLog.log("flutter", "handleFlutterMethodCall 异常: " + t.getMessage());
            return null;
        }
    }

    // ==========  Flutter Hook 方法 ==========
    private void hookFlutterMethodChannelCheck(final ClassLoader classLoader) {
        try {
            Class<?> dartMessengerClass = XposedHelpers.findClassIfExists(
                    "io.flutter.embedding.engine.dart.DartMessenger", classLoader);
            if (dartMessengerClass == null) {
                dartMessengerClass = XposedHelpers.findClassIfExists(
                        "io.flutter.view.FlutterNativeView", classLoader);
                if (dartMessengerClass == null) {
                    return;
                }
            }
            final ClassLoader flutterLoader = dartMessengerClass.getClassLoader();
            ReaLog.log("flutter", "检测到Flutter，启用Hook");

            // 引擎层入口拦截（仅记录）
            try {
                Class<?> flutterJNIClass = XposedHelpers.findClassIfExists(
                        "io.flutter.embedding.engine.FlutterJNI", classLoader);
                if (flutterJNIClass != null) {
                    XposedHelpers.findAndHookMethod(flutterJNIClass, "dispatchPlatformMessage",
                            String.class, java.nio.ByteBuffer.class, long.class, int.class,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    try {
                                        String channel = (String) param.args[0];
                                        if (channel == null) return;
                                        boolean shouldIntercept = false;
                                        for (String keyword : FLUTTER_WHITELIST_CHANNELS) {
                                            if (channel.contains(keyword)) {
                                                shouldIntercept = true;
                                                break;
                                            }
                                        }
                                        if (!shouldIntercept) return;
                                        ReaLog.log("flutter", "flutter引擎层拦截通道: " + channel);
                                        handleFlutterBinaryMessage(param, flutterLoader);
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                    );
                    ReaLog.log("flutter", "Flutter引擎入口 Hook 成功");
                }
            } catch (Throwable t) {
                // ReaLog.log("flutter", "FlutterJNI Hook 失败（低版本跳过）: " + t.getMessage());
            }

            // MethodChannel 应用层拦截
            try {
                Class<?> methodChannelClass = XposedHelpers.findClassIfExists(
                        "io.flutter.plugin.common.MethodChannel", classLoader);
                if (methodChannelClass == null) {
                    ReaLog.log("flutter", "flutterMethodChannel 类不存在，跳过");
                    return;
                }

                // invokeMethod 拦截
                XposedHelpers.findAndHookMethod(
                        methodChannelClass,
                        "invokeMethod",
                        String.class,
                        Object.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    String methodName = (String) param.args[0];
                                    Object arguments = param.args[1];
                                    Object fakeResult = handleFlutterMethodCall(methodName, arguments, param);
                                    if (fakeResult != null) {
                                        param.setResult(null);
                                    }
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                );

                // setMethodCallHandler 拦截（Result 代理）
                XposedHelpers.findAndHookMethod(
                        methodChannelClass,
                        "setMethodCallHandler",
                        "io.flutter.plugin.common.MethodChannel$MethodCallHandler",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(final MethodHookParam param) {
                                try {
                                    final Object handler = param.args[0];
                                    if (handler == null) return;

                                    final Class<?> methodCallClass = Class.forName(
                                            "io.flutter.plugin.common.MethodCall", false, classLoader);
                                    final Class<?> resultClass = Class.forName(
                                            "io.flutter.plugin.common.Result", false, classLoader);

                                    XposedHelpers.findAndHookMethod(
                                            handler.getClass(),
                                            "onMethodCall",
                                            methodCallClass,
                                            resultClass,
                                            new XC_MethodHook() {
                                                @Override
                                                protected void beforeHookedMethod(final MethodHookParam hookParam) {
                                                    try {
                                                        final Object result = hookParam.args[1];
                                                        Object proxyResult = java.lang.reflect.Proxy.newProxyInstance(
                                                                classLoader,
                                                                new Class<?>[]{resultClass},
                                                                new java.lang.reflect.InvocationHandler() {
                                                                    @Override
                                                                    public Object invoke(Object proxy, Method method, Object[] args)
                                                                            throws Throwable {
                                                                        if ("success".equals(method.getName()) && args.length > 0) {
                                                                            Object data = args[0];
                                                                            if (data
                                                                                    instanceof
                                                                                    List) {
                                                                                List<
                                                                                        ?> list = (List<
                                                                                                ?>) data;
                                                                                if (!list.isEmpty() && list.get(0)
                                                                                                instanceof
                                                                                                String) {
                                                                                    List<
                                                                                            String> fakeList = getFakePackageList();
                                                                                    args[
                                                                                    0] = fakeList;
                                                                                    ReaLog.log("flutter", "flutterResult 回调篡改: " +
                                                                                            list.size() + " -> " + fakeList.size() + " 个包");
                                                                                }
                                                                            }
                                                                        }
                                                                        return method.invoke(result, args);
                                                                    }
                                                                }
                                                        );
                                                        XposedHelpers.setObjectField(hookParam.thisObject, "result", proxyResult);
                                                    } catch (Throwable ignored) {
                                                    }
                                                }
                                            }
                                    );
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                );
                ReaLog.log("flutter", "flutterMethodChannel Hook 完成");
            } catch (Throwable t) {
                ReaLog.log("flutter", "flutterMethodChannel Hook 失败: " + t.getMessage());
            }

            // SharedPreferences 拦截
            try {
                hookSharedPreferences(classLoader);
            } catch (Throwable ignored) {
            }

            // ReaLog.log("flutter", "Flutter 全套增强 Hook 安装完成");
        } catch (Throwable t) {
            ReaLog.log("flutter", "Flutter Hook 整体初始化失败: " + t.getMessage());
        }
    }

    private void hookFlutterPackageInfoPlus(final ClassLoader classLoader) {
        String[] targetClasses = {
                "dev.fluttercommunity.plus.packageinfo.PackageInfoPlugin",
                "io.flutter.plugins.packageinfo.PackageInfoPlugin",
        };
        for (final String className : targetClasses) {
            try {
                Class<?> pluginClass = XposedHelpers.findClassIfExists(className, classLoader);
                if (pluginClass != null) {
                    XposedHelpers.findAndHookMethod(
                            pluginClass,
                            "handleMethodCall",
                            "io.flutter.plugin.common.MethodCall",
                            "io.flutter.plugin.common.Result",
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    try {
                                        Object methodCall = param.args[0];
                                        Object result = param.args[1];
                                        if (methodCall == null || result == null) return;

                                        String methodName = XposedHelpers.callMethod(methodCall, "method").toString();
                                        Object arguments = XposedHelpers.callMethod(methodCall, "arguments");

                                        Object fakeResult = handleFlutterMethodCall(methodName, arguments, param);
                                        if (fakeResult != null) {
                                            XposedHelpers.callMethod(result, "success", fakeResult);
                                            param.setResult(null);
                                        }
                                    } catch (Throwable t) {
                                        ReaLog.log("flutter", "flutter package_info_plus 处理异常: " + t.getMessage());
                                    }
                                }
                            }
                    );
                    ReaLog.log("flutter", "flutter package_info_plus 插件: " + className);
                    return;
                }
            } catch (Throwable t) {
                ReaLog.log("flutter", "flutterHook " + className + " 异常: " + t.getMessage());
            }
        }
        ReaLog.log("flutter", "未找到flutter package_info_plus 插件类");
    }

    private void hookFlutterAppInstalledChecker(final ClassLoader classLoader) {
        String[] targetClasses = {
                "com.javih.addtoapp.AppInstalledCheckerPlugin",
                "com.example.appinstalledchecker.AppInstalledCheckerPlugin",
                "app.installed.checker.AppInstalledCheckerPlugin",
        };
        for (final String className : targetClasses) {
            try {
                Class<?> pluginClass = XposedHelpers.findClassIfExists(className, classLoader);
                if (pluginClass != null) {
                    XposedHelpers.findAndHookMethod(
                            pluginClass,
                            "onMethodCall",
                            "io.flutter.plugin.common.MethodCall",
                            "io.flutter.plugin.common.Result",
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    try {
                                        Object methodCall = param.args[0];
                                        Object result = param.args[1];
                                        if (methodCall == null || result == null) return;

                                        String methodName = XposedHelpers.callMethod(methodCall, "method").toString();
                                        Object arguments = XposedHelpers.callMethod(methodCall, "arguments");

                                        Object fakeResult = handleFlutterMethodCall(methodName, arguments, param);
                                        if (fakeResult != null) {
                                            XposedHelpers.callMethod(result, "success", fakeResult);
                                            param.setResult(null);
                                        }
                                    } catch (Throwable t) {
                                        ReaLog.log("flutter", "flutterChecker 处理异常: " + t.getMessage());
                                    }
                                }
                            }
                    );
                    ReaLog.log("flutter", "已 Hook flutterChecker 插件: " + className);
                    return;
                }
            } catch (Throwable t) {
                ReaLog.log("flutter", "flutterHook " + className + " 异常: " + t.getMessage());
            }
        }
        ReaLog.log("flutter", "未找到 flutterChecker 插件类");
    }

// ========== Binder 层代理（防 Native JNI 绕过） ==========
/**
 * 安装 ServiceManager 级别的 Binder 代理 Hook 拦截 ServiceManager.getService("package") 返回代理 IPackageManager
 */
    public void installServiceManagerHook(final ClassLoader classLoader) {
        try {
            Class<?> smClass = XposedHelpers.findClass("android.os.ServiceManager", classLoader);
            XposedHelpers.findAndHookMethod(smClass, "getService", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String name = (String) param.args[0];
                    if (!"package".equals(name)) return;

                    IBinder original = (IBinder) param.getResult();
                    if (original == null) return;

                    Class<?> ipmInterface = Class.forName("android.content.pm.IPackageManager");
                    Object proxy = Proxy.newProxyInstance(
                            classLoader,
                            new Class<?>[]{ipmInterface},
                            new PmBinderProxy(original, classLoader)
                    );
                    param.setResult(proxy);
                    ReaLog.log("binder", "binder服务 已代理");
                }
            });
            ReaLog.log("binder", "binder服务 Hook 安装成功");
        } catch (Throwable t) {
            ReaLog.log("binder", "binder服务 失败: " + t.getMessage());
            mHookInit.log("binder服务 异常: " + t.getMessage());
        }
    }

// ---------- 内部代理处理器 ----------
    private class PmBinderProxy implements InvocationHandler {
        private final Object realService; // IPackageManager 实例
        private final ClassLoader classLoader;

        public PmBinderProxy(IBinder binder, ClassLoader loader) throws Exception {
            this.classLoader = loader;
            Class<?> iPMClass = Class.forName("android.content.pm.IPackageManager");
            Method asInterface = iPMClass.getMethod("asInterface", IBinder.class);
            this.realService = asInterface.invoke(null, binder);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();

            // 只拦截单包查询：args[0] 为包名，args[1] 为 flags（Android 11+ 起为 long，不能直接强转 int）
            if (args != null && args.length >= 2 && args[0] instanceof String) {
                String pkg = (String) args[0];
                int flags = args[1] instanceof Number ? ((Number) args[1]).intValue() : 0;
                if ("getPackageInfo".equals(methodName)) {
                    return handleGetPackageInfo(pkg, flags, method, args);
                }
                if ("getApplicationInfo".equals(methodName)) {
                    return handleGetApplicationInfo(pkg, flags, method, args);
                }
            }

            // 所有其他方法（包括 getInstalledPackages、getInstalledApplications）直接透传
            if (realService != null) {
                try {
                    return method.invoke(realService, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            return null;
        }

        private Object handleGetPackageInfo(String pkg, int flags, Method method, Object[] args)
                throws Throwable {
            if (pkg == null) return null;
            // 系统包或自身应用放行：method 与 args 来自同一 Binder 接口调用，
            // 签名（含各版本的 long flags / userId 形参）天然匹配，不假设固定签名
            if (isSystemCorePackage(pkg) || isRealSystemPackage(pkg) || pkg.equals(mHookInit.getCurrentTargetApp())) {
                try {
                    return method.invoke(realService, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            captureValidPackage(pkg);
            if (!shouldReturnInstalledForPackage(pkg)) {
                throw new PackageManager.NameNotFoundException();
            }
            return createSmartFakePackageInfo(pkg, flags);
        }

        private Object handleGetApplicationInfo(String pkg, int flags, Method method, Object[] args)
                throws Throwable {
            if (pkg == null) return null;
            if (isSystemCorePackage(pkg) || isRealSystemPackage(pkg) || pkg.equals(mHookInit.getCurrentTargetApp())) {
                try {
                    return method.invoke(realService, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            captureValidPackage(pkg);
            if (!shouldReturnInstalledForPackage(pkg)) {
                throw new PackageManager.NameNotFoundException();
            }
            return createSmartApplicationInfo(pkg, flags);
        }
    }

}

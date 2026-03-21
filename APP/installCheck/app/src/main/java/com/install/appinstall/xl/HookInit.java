package com.install.appinstall.xl;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ConfigurationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.Signature;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.text.Html;
import android.webkit.WebView;
import android.webkit.JsResult;

public class HookInit implements IXposedHookLoadPackage {

  private String currentTargetApp = "";
  private static final String MODULE_TAG = "InstallHook";
  private static final ArrayList<String> globalCapturedPackages =
    new ArrayList<>();
  private final ArrayList<String> appCapturedPackages = new ArrayList<>();

private Activity currentResumedActivity = null; //悬浮窗界面
  private Handler floatingTopHandler; // 定时置顶处理器
  private TextView currentFloatingView; // 当前悬浮窗视图
  // 配置存储
  private static final Map<String, Boolean> installStatusMap = new HashMap<>();
  private static final Map<String, Boolean> floatingShownMap = new HashMap<>();
  private static final Map<String, Float> floatingXMap = new HashMap<>();
  private static final Map<String, Float> floatingYMap = new HashMap<>();

  // 拦截退出功能配置
  private static final Map<String, Boolean> blockExitMap = new HashMap<>();
  private static final Map<
    String,
    List<InterceptPattern>
  > interceptPatternsMap = new HashMap<>();
  // 类顶部新增：退出控制变量
  private boolean isExitPending = false; // 是否有等待处理的退出请求
  private String pendingExitMethod = ""; // 等待处理的退出方式（System.exit()/Process.killProcess()）
  private Object pendingExitParam = null; // 等待处理的退出参数
  private int isCurrentlyBlocking;
  private String blockText;
  // 双击判定阈值（可调整，默认500毫秒）
  private static final int DOUBLE_CLICK_THRESHOLD = 500;
  private long lastClickTime = 0; // 上一次点击时间
  private AlertDialog statusSwitchDialog; // 单击菜单弹窗引用

// 用户自定义包名列表（独立存储，避免冲突）
 private static final Map<String, List<String>> userDefinedPackagesMap = new HashMap<>();
 // 新增：用户自定义排除包名列表（独立存储，与伪造包名区分）
 private static final Map<String, List<String>> excludedPackagesMap = new HashMap<>();
 
  // ========== 完整的检测权限常量列表 ==========
  private static final String[] DETECTION_PERMISSIONS = {
    // 直接读取应用列表权限
    "android.permission.QUERY_ALL_PACKAGES", // Android 11+ 读取所有包
    "android.permission.GET_PACKAGE_SIZE", // 低版本读取应用列表
    // 使用统计权限
    "android.permission.PACKAGE_USAGE_STATS", // 应用使用统计
    // 系统级权限（通常用于设备管理应用）
    "android.permission.PACKAGE_VERIFICATION_AGENT", // 包验证代理
    /*
    // 存储权限
    "android.permission.READ_EXTERNAL_STORAGE", // 读取外部存储
    "android.permission.WRITE_EXTERNAL_STORAGE", // 写入外部存储

    // 其他可能的检测权限
    "android.permission.ACCESS_WIFI_STATE",     // WiFi状态
    "android.permission.BLUETOOTH",             // 蓝牙
    "android.permission.INTERNET",              // 网络（可能用于云端验证）

    // 权限组相关
    "android.permission-group.STORAGE",         // 存储权限组
    "android.permission-group.PHONE"            // 电话权限组（某些应用会检测）
    */
  };

  // ========== 智能伪造相关变量 ==========
  private static final List<QueryPattern> queryPatterns = new ArrayList<>();
  private static final Random random = new Random();

  // 查询模式记录类
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

  // 智能伪造缓存（确保同一包多次查询返回相同信息）
  private static final Map<String, String> versionCache = new HashMap<>();
  private static final Map<String, Integer> versionCodeCache = new HashMap<>();
  private static final Map<String, Long> installTimeCache = new HashMap<>();
  private static final Map<String, String> installerCache = new HashMap<>();
  private static final Map<String, String> appNameCache = new HashMap<>();
  // 权限伪造map
  private static final Map<String, Boolean> permissionFakeMap = new HashMap<>();

  // 拦截模式类
  private static class InterceptPattern {

    String patternHash;
    List<String> installedPackages;
    List<String> notInstalledPackages;
    String userChoice; // "intercept" 或 "allow"
    int choiceCount;
    long lastDetectedTime;
    boolean silentIntercept;

    InterceptPattern(
      String patternHash,
      List<String> installedPackages,
      List<String> notInstalledPackages
    ) {
      this.patternHash = patternHash;
      this.installedPackages = installedPackages;
      this.notInstalledPackages = notInstalledPackages;
      this.choiceCount = 0;
      this.lastDetectedTime = System.currentTimeMillis();
      this.silentIntercept = false;
    }
  }

  // 检测到的包名数据结构
  private static class DetectedPackages {

    List<String> installedPackages;
    List<String> notInstalledPackages;
    String patternHash;

    DetectedPackages() {
      installedPackages = new ArrayList<>();
      notInstalledPackages = new ArrayList<>();
    }
  }
// 顶部加这一句
//private Context mAppContext = null;
//private boolean configLoaded 
/*

@Override
public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
    
    // ========== 🚀【分身专用】立即修复 so 路径（不等 attach）==========
    try {
        Context context = (Context) XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null),
            "currentApplication"
        );
        if (context != null) {
            ApplicationInfo ai = context.getApplicationInfo();
            if (ai != null) {
                // 强制重置 nativeLibraryDir，触发系统重新计算正确路径
                XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                //XposedBridge.log("[InstallHook] ✅ 分身主动修复 .so 路径: " + ai.nativeLibraryDir);
            }
        }
    } catch (Throwable e) {
        //XposedBridge.log("[InstallHook] ❌ 分身主动修复失败: " + e.getMessage());
    }

    // 跳过分身系统类/核心类
    if (
        lpparam.packageName.contains("webview") ||
        lpparam.packageName.contains("chromium") ||
        lpparam.packageName.contains("android.") ||
        lpparam.packageName.contains("com.android.") ||
        lpparam.packageName.contains("system") ||
        lpparam.packageName.contains("root")
    ) {
        return;
    }
    
    // Hook模块自身激活状态
    if ("com.install.appinstall.xl".equals(lpparam.packageName)) {
        XposedHelpers.findAndHookMethod(
            MainActivity.class.getName(),
            lpparam.classLoader,
            "isModuleActivated",
            XC_MethodReplacement.returnConstant(true)
        );
        return;
    }

    currentTargetApp = lpparam.packageName;
    log("=========================================");
    log("开始Hook应用: " + currentTargetApp);

    if (isSystemPackage(currentTargetApp)) {
        log("跳过系统应用: " + currentTargetApp);
        return;
    }

    try {
        // 仍然保留原来的 so 路径 Hook 作为兜底（不影响已修复的分身）
        hookSoPathInApp(lpparam.classLoader);
        
        // 加载配置文件
        loadConfigFromFile();

        // 初始化配置
        if (!installStatusMap.containsKey(currentTargetApp)) {
            installStatusMap.put(currentTargetApp, true);
        }
        if (!floatingShownMap.containsKey(currentTargetApp)) {
            floatingShownMap.put(currentTargetApp, true);
        }
        if (!blockExitMap.containsKey(currentTargetApp)) {
            blockExitMap.put(currentTargetApp, false);
        }

        // 检查当前状态
        Boolean currentStatus = installStatusMap.get(currentTargetApp);
        boolean status = currentStatus != null ? currentStatus : true;

        // 第1级：基础环境准备（防止崩溃）
        hookBundleGetString(lpparam.classLoader);
        hookBundleEmptyInstance(lpparam.classLoader);

        // 第2级：权限伪造（关键防御）
        hookPackageUsageStatsPermission(lpparam.classLoader);
        hookQueryAllPackagesPermission(lpparam.classLoader);

        // 第3级：系统底层Hook
        hookSystemFileRead();
        hookPackageManagerReflect();

        // 第4级：退出拦截（用户体验保障）
        hookExitMethods(lpparam.classLoader);
        hookIndirectExitMethods(lpparam.classLoader);
        hookGlobalExitSources(lpparam.classLoader);
        hookRunnableSystemExit(lpparam.classLoader);

        // 第5级：包管理核心伪造（数据层）
        hookKeyMethods(lpparam.classLoader);
        hookOverloadMethods(lpparam.classLoader);
        hookIsApplicationEnabled(lpparam.classLoader);
        hookCheckPermission(lpparam.classLoader);
        hookGetActivityInfo(lpparam.classLoader);
        
        // 第6级：Intent相关伪造（应用层）
        hookQueryIntentActivities(lpparam.classLoader);
        hookResolveActivity(lpparam.classLoader);
        hookQueryIntentServices(lpparam.classLoader);
        
        // 第7级：启动拦截
         // 第7级：启动和访问控制
         /*
      hookGetLaunchIntentForPackage(lpparam.classLoader);
      hookCanStartActivity(lpparam.classLoader);
      */
      //  hookStartActivity(lpparam.classLoader);

      /*
      // 第8级：文件系统和命令行检测（底层检测）
      hookRuntimeExecMethods(lpparam.classLoader);
      hookFileSystemChecks(lpparam.classLoader);
      hookLibDirectoryChecks(lpparam.classLoader);
      */
      /*
        // 第9级：ok网络和跨平台框架
        hookOkHttp(lpparam.classLoader);
        hookFlutterPackageInfoPlus(lpparam.classLoader);
        hookFlutterAppInstalledChecker(lpparam.classLoader);
        hookFileSystemInstallCheck(lpparam.classLoader);
        hookReflectInstallCheck(lpparam.classLoader);
        hookFlutterMethodChannelCheck(lpparam.classLoader);

        // 第10级：UI和用户体验
        hookDialogCancelableMethods(lpparam.classLoader);
        hookActivityLifecycle(lpparam.classLoader);
    } catch (Throwable t) {
        log("Hook失败: " + t);
    }
}
*/
/*
*/

@Override
public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {


// ========== 🚨【WebView子线程崩溃专杀】==========
try {
    // Hook WebView 的所有子线程创建，直接吞掉空指针
    XposedHelpers.findAndHookMethod(
        "android.webkit.WebView",
        null,
        "onDraw",
        android.graphics.Canvas.class,
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // 什么都不做，只是防止崩溃
            }
        }
    );
} catch (Throwable ignored) {}

try {
    // Hook Chromium 内核的崩溃点
    XposedHelpers.findAndHookMethod(
        "org.chromium.android_webview.AwContents",
        null,
        "destroy",
        new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                return null; // 防止销毁时崩溃
            }
        }
    );
} catch (Throwable ignored) {}

// ========== 🚨【终极暴力修复】Hook所有Bundle方法（可编译版）==========
try {
    Class<?> bundleClass = Class.forName("android.os.Bundle");
    
    // 使用 findAndHookMethod 逐个 Hook 关键方法，而不是 hookAllMethods
    String[] bundleMethods = {
        "getString", "getInt", "getBoolean", "getLong", 
        "getDouble", "getFloat", "getBundle", "getSerializable",
        "getParcelable", "containsKey"
    };
    
    for (final String methodName : bundleMethods) {
        try {
            // Hook 单参数版本
            XposedHelpers.findAndHookMethod(
                bundleClass,
                methodName,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject == null) {
                            setDefaultReturnValue(param);
                            return;
                        }
                        // 检测Bundle是否可用
                        try {
                            ((Bundle) param.thisObject).hashCode();
                        } catch (Throwable e) {
                            setDefaultReturnValue(param);
                        }
                    }
                    
                    private void setDefaultReturnValue(MethodHookParam param) {
                        if (methodName.equals("getString")) param.setResult("");
                        else if (methodName.equals("getInt")) param.setResult(0);
                        else if (methodName.equals("getBoolean")) param.setResult(false);
                        else if (methodName.equals("getLong")) param.setResult(0L);
                        else if (methodName.equals("getDouble")) param.setResult(0.0);
                        else if (methodName.equals("getFloat")) param.setResult(0.0f);
                        else if (methodName.equals("getBundle")) param.setResult(new Bundle());
                        else param.setResult(null);
                    }
                }
            );
        } catch (Throwable ignored) {}
        
        try {
            // Hook 双参数版本（带默认值）
            XposedHelpers.findAndHookMethod(
                bundleClass,
                methodName,
                String.class,
                Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject == null) {
                            param.setResult(param.args[1]);
                            return;
                        }
                        try {
                            Bundle b = (Bundle) param.thisObject;
                            if (!b.containsKey((String) param.args[0])) {
                                param.setResult(param.args[1]);
                            }
                        } catch (Throwable e) {
                            param.setResult(param.args[1]);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }
    
    // 特别处理 getString(String, String) 确保万无一失
    try {
        XposedHelpers.findAndHookMethod(
            bundleClass,
            "getString",
            String.class,
            String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject == null) {
                        param.setResult(param.args[1]);
                        return;
                    }
                    try {
                        Bundle b = (Bundle) param.thisObject;
                        if (!b.containsKey((String) param.args[0])) {
                            param.setResult(param.args[1]);
                        }
                    } catch (Throwable e) {
                        param.setResult(param.args[1]);
                    }
                }
            }
        );
    } catch (Throwable ignored) {}
    
 //   //XposedBridge.log("[InstallHook] ✅ Bundle暴力修复完成（可编译版）");
    
} catch (Throwable e) {
    //XposedBridge.log("[InstallHook] ❌ Bundle暴力修复失败: " + e.getMessage());
}

// ========== 🚨【强制全局异常捕获】==========
try {
    // 先设置我们自己的Handler
    final Thread.UncaughtExceptionHandler ourHandler = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (e instanceof NullPointerException) {
                StackTraceElement[] stack = e.getStackTrace();
                if (stack.length > 0) {
                    String className = stack[0].getClassName();
                    if (className.contains("Bundle") || 
                        className.contains("PackageManager") ||
                        className.contains("ContextImpl") ||
                        className.contains("ApplicationPackageManager")) {
                        //XposedBridge.log("[InstallHook] 💪 拦截崩溃: " + className + "." + stack[0].getMethodName());
                        return;
                    }
                }
            }
            // 交给系统默认Handler
            Thread.UncaughtExceptionHandler defaultHandler = 
                Thread.getDefaultUncaughtExceptionHandler();
            if (defaultHandler != null && defaultHandler != this) {
                defaultHandler.uncaughtException(t, e);
            }
        }
    };
    
    // 设置我们的Handler
    Thread.setDefaultUncaughtExceptionHandler(ourHandler);
    
    // Hook setDefaultUncaughtExceptionHandler 防止被覆盖
    XposedHelpers.findAndHookMethod(
        "java.lang.Thread",
        null,
        "setDefaultUncaughtExceptionHandler",
        Thread.UncaughtExceptionHandler.class,
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // 不允许覆盖我们的Handler
                //XposedBridge.log("[InstallHook] ⚠️ 阻止应用设置自己的异常处理器");
                param.setResult(null);
            }
        }
    );
    
    //XposedBridge.log("[InstallHook] ✅ 强制全局异常捕获已启用");
    
} catch (Throwable e) {
    //XposedBridge.log("[InstallHook] ❌ 强制异常捕获失败: " + e.getMessage());
}

    // ========== 🚨【终极兜底】任何线程的任何空指针都不崩溃 ==========
try {
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            // 只处理空指针
            if (e instanceof NullPointerException) {
                StackTraceElement[] stack = e.getStackTrace();
                if (stack.length > 0) {
                    String className = stack[0].getClassName();
                    // 只要是我们Hook的包，全部吞掉
                    if (className.startsWith("android.os.Bundle") ||
                        className.startsWith("android.content.pm") ||
                        className.startsWith("android.app") ||
                        className.contains("PackageManager")) {
                        
                        //XposedBridge.log("[InstallHook] 🛡️ 拦截子线程空指针: " +   className + "." + stack[0].getMethodName());
                        return; // 不崩溃
                    }
                }
            }
            
            // 其他崩溃交给系统
            Thread.UncaughtExceptionHandler defaultHandler = 
                Thread.getDefaultUncaughtExceptionHandler();
            if (defaultHandler != null && defaultHandler != this) {
                defaultHandler.uncaughtException(t, e);
            }
        }
    });
} catch (Throwable ignored) {}
    
    // ========== 🚨【分身核心修复1】获取真实Context和ClassLoader（必须最先执行）==========
    Context appContext = null;
    ClassLoader realClassLoader = null;
    String realPackageName = lpparam.packageName; // 默认使用传入包名
    
    try {
        // 通过ActivityThread获取当前应用的真实Context
        appContext = (Context) XposedHelpers.callStaticMethod(
            XposedHelpers.findClass("android.app.ActivityThread", null),
            "currentApplication"
        );
        if (appContext != null) {
            // 获取真实的ClassLoader（分身的classLoader是代理，必须用这个）
            realClassLoader = appContext.getClassLoader();
            // 获取真实的包名（分身传入的是分身包名，这里是宿主编译）
            realPackageName = appContext.getPackageName();
            
            //XposedBridge.log("[InstallHook] ✅ 获取真实Context: " + realPackageName);
        }
    } catch (Throwable e) {
        //XposedBridge.log("[InstallHook] ❌ 获取真实Context失败: " + e.getMessage());
    }
    
    // 降级方案：如果获取失败，回退使用Xposed传入的参数
    if (realClassLoader == null) {
        realClassLoader = lpparam.classLoader;
    }
    if (appContext == null) {
        try {
            // 尝试创建代理Context
          //  appContext = createProxyContext(realPackageName);
        } catch (Throwable ignored) {}
    }
    
    // ========== 🚨【分身核心修复2】主动修复so路径（不等Application.attach）==========
    if (appContext != null) {
        try {
            ApplicationInfo ai = appContext.getApplicationInfo();
            if (ai != null) {
                // 强制重置nativeLibraryDir，触发系统重新计算正确路径
                // 分身环境下这个字段指向错误路径，重新set同一值会触发重新解析
                XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                //XposedBridge.log("[InstallHook] ✅ 分身主动修复.so路径: " + ai.nativeLibraryDir);
            }
        } catch (Throwable e) {
            //XposedBridge.log("[InstallHook] ❌ 分身主动修复.so路径失败: " + e.getMessage());
        }
    }
    
    // ========== 🚨【分身核心修复3】主动加载配置（不等任何回调）==========
    // 必须在这里加载，因为分身的Application.attach永远不会触发
    currentTargetApp = realPackageName;
    try {
        loadConfigFromFile();
        //XposedBridge.log("[InstallHook] ✅ 主动加载配置完成: " + currentTargetApp);
    } catch (Throwable e) {
        //XposedBridge.log("[InstallHook] ❌ 主动加载配置失败: " + e.getMessage());
        createDefaultConfig();
    }
    
    // ========== 跳过分身系统类/核心类 ==========
    if (lpparam.packageName.contains("webview") ||
        lpparam.packageName.contains("chromium") ||
        lpparam.packageName.contains("android.") ||
        lpparam.packageName.contains("com.android.") ||
        lpparam.packageName.contains("system") ||
        lpparam.packageName.contains("root")) {
        return;
    }
    
    // ========== Hook模块自身激活状态 ==========
    if ("com.install.appinstall.xl".equals(lpparam.packageName)) {
        try {
            XposedHelpers.findAndHookMethod(
                MainActivity.class.getName(),
                lpparam.classLoader,
                "isModuleActivated",
                XC_MethodReplacement.returnConstant(true)
            );
        } catch (Throwable ignored) {}
        return;
    }

    // 更新当前目标应用（可能已被真实包名覆盖）
    if (!currentTargetApp.equals(lpparam.packageName)) {
        //XposedBridge.log("[InstallHook] 📌 包名修正: " + lpparam.packageName + " → " + currentTargetApp);
    }

    // 跳过系统应用
    if (isSystemPackage(currentTargetApp)) {
        //XposedBridge.log("[InstallHook] 跳过系统应用: " + currentTargetApp);
        return;
    }

    //XposedBridge.log("[InstallHook] =========================================");
    //XposedBridge.log("[InstallHook] 🚀 开始Hook应用: " + currentTargetApp);
    //XposedBridge.log("[InstallHook] 📦 使用ClassLoader: " + realClassLoader.getClass().getName());
    //XposedBridge.log("[InstallHook] =========================================");

    try {
        // ========== 初始化配置状态（确保默认值）==========
        if (!installStatusMap.containsKey(currentTargetApp)) {
            installStatusMap.put(currentTargetApp, true);
        }
        if (!floatingShownMap.containsKey(currentTargetApp)) {
            floatingShownMap.put(currentTargetApp, true);
        }
        if (!blockExitMap.containsKey(currentTargetApp)) {
            blockExitMap.put(currentTargetApp, false);
        }
        if (!permissionFakeMap.containsKey(currentTargetApp)) {
            permissionFakeMap.put(currentTargetApp, true);
        }
        if (!userDefinedPackagesMap.containsKey(currentTargetApp)) {
            userDefinedPackagesMap.put(currentTargetApp, new ArrayList<>());
        }
        if (!excludedPackagesMap.containsKey(currentTargetApp)) {
            excludedPackagesMap.put(currentTargetApp, new ArrayList<>());
        }

        // ========== 🚨【分身核心修复4】所有Hook方法必须使用真实ClassLoader ==========
        // 第1级：基础环境准备（防止崩溃）
        hookBundleGetString(realClassLoader);
        hookBundleEmptyInstance(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ 基础环境Hook完成");

        // 第2级：权限伪造（关键防御）
        hookPackageUsageStatsPermission(realClassLoader);
        hookQueryAllPackagesPermission(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ 权限伪造Hook完成");

        // 第3级：系统底层Hook
        hookSystemFileRead();
        hookPackageManagerReflect();
        //XposedBridge.log("[InstallHook] ✅ 系统底层Hook完成");

        // 第4级：退出拦截（用户体验保障）
        hookExitMethods(realClassLoader);
        hookIndirectExitMethods(realClassLoader);
        hookGlobalExitSources(realClassLoader);
        hookRunnableSystemExit(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ 退出拦截Hook完成");

        // 第5级：包管理核心伪造（数据层）
        hookKeyMethods(realClassLoader);
        hookOverloadMethods(realClassLoader);
        hookIsApplicationEnabled(realClassLoader);
        hookCheckPermission(realClassLoader);
        hookGetActivityInfo(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ 包管理核心Hook完成");

        // 第6级：Intent相关伪造（应用层）
        hookQueryIntentActivities(realClassLoader);
        hookResolveActivity(realClassLoader);
        hookQueryIntentServices(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ Intent相关Hook完成");

        // 第7级：启动拦截
          /*
      hookGetLaunchIntentForPackage(lpparam.classLoader);
      hookCanStartActivity(lpparam.classLoader);
      */
        hookStartActivity(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ 启动拦截Hook完成");

        // 第8级：OkHttp网络和跨平台框架
        hookOkHttp(realClassLoader);
        hookFlutterPackageInfoPlus(realClassLoader);
        hookFlutterAppInstalledChecker(realClassLoader);
        hookFileSystemInstallCheck(realClassLoader);
        hookReflectInstallCheck(realClassLoader);
        hookFlutterMethodChannelCheck(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ 网络/跨平台Hook完成");

      
      // 第9级：文件系统和命令行检测（底层检测）
      hookRuntimeExecMethods(lpparam.classLoader);
      hookFileSystemChecks(lpparam.classLoader);
      hookLibDirectoryChecks(lpparam.classLoader);
      
        // 第10级：UI和用户体验
        hookDialogCancelableMethods(realClassLoader);
        hookActivityLifecycle(realClassLoader);
        //XposedBridge.log("[InstallHook] ✅ UI相关Hook完成");

        //XposedBridge.log("[InstallHook] 🎉 所有Hook执行成功: " + currentTargetApp);

    } catch (Throwable t) {
        //XposedBridge.log("[InstallHook] ❌ Hook失败: " + t.getMessage());
        t.printStackTrace();
    }
}




  // 新增：判断是否为 Flutter 应用的辅助方法
  private boolean isFlutterApp(ClassLoader classLoader) {
    try {
      // 检查 Flutter 核心类是否存在
      Class<?> flutterClass = Class.forName(
        "io.flutter.embedding.engine.FlutterJNI",
        false,
        classLoader
      );
      return flutterClass != null;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  // 过滤系统应用
  private boolean isSystemPackage(String packageName) {
    if (packageName == null) {
      return false;
    }

    //过滤主流系统/厂商包名前缀
    String[] systemPackages = {
      // 基础系统包
      "android",
      "com.android",
      "com.google.android",
      // 原有核心厂商
      "com.qualcomm",
      "com.samsung",
      "com.huawei",
      "com.miui",
      "com.oneplus",
      "com.oppo",
      "com.vivo",
      "com.realme",
      "com.xiaomi",
      "com.meizu",
      "com.google.",
      "system",
      "root",
      "com.google.android.webview",
      "com.google.android.gms",
      // 厂商/子品牌/国际版
      "com.asus",
      "com.lenovo",
      "com.zuk",
      "com.motorola",
      "com.nokia",
      "com.honor",
      "com.xiaomi.global",
      "com.oppo.global",
      "com.vivo.global",
      "com.realme.global",
      "com.infinix",
      "com.tecno",
      "com.itel",
      "com.sharp",
      "com.sony",
      "com.lg",
      "com.poco",
      "com.redmi",
      "com.huawei.hwid",
      "com.oppo.nearme",
      "com.vivo.browser",
      "com.heytap",
      "com.coloros",
      "com.flyme",
      "com.mi",
      "com.xiaomi.account",
    };

    // 前缀匹配判断
    for (String systemPkg : systemPackages) {
      if (packageName.startsWith(systemPkg)) {
        return true;
      }
    }

    // 兜底逻辑：通过 ApplicationInfo 标志位验证
    try {
    
      Context context = (Context) XposedHelpers.callStaticMethod(
        XposedHelpers.findClass("android.app.ActivityThread", null),
        "currentApplication"
      );
      PackageManager pm = context.getPackageManager();
      ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
      return (
        (appInfo.flags &
          (ApplicationInfo.FLAG_SYSTEM |
            ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) !=
        0
      );
      
    } catch (Throwable t) {
      return false;
    }
  }

  private void log(String message) {
    try {
      // Xposed 框架日志
      XposedBridge.log(
        "[" + MODULE_TAG + "] [" + currentTargetApp + "] " + message
      );
    } catch (Throwable e) {
      // 添加系统 Log
      android.util.Log.d(MODULE_TAG, "[" + currentTargetApp + "] " + message);
    }
  }

  // ========== 配置管理方法 ==========
  private List<String> jsonArrayToStringList(JSONArray array) {
    List<String> list = new ArrayList<>();
    if (array != null) {
      for (int i = 0; i < array.length(); i++) {
        try {
          String item = array.getString(i);
          if (item != null && !item.isEmpty()) {
            list.add(item);
          }
        } catch (Exception e) {
          try {
            Object obj = array.get(i);
            if (obj != null) {
              list.add(obj.toString());
            }
          } catch (Exception ex) {}
        }
      }
    }
    return list;
  }

  // 辅助方法
  private JSONArray stringListToJsonArray(List<String> list) {
    JSONArray array = new JSONArray();
    if (list != null) {
      for (String item : list) {
        if (item != null && !item.isEmpty()) {
          array.put(item);
        }
      }
    }
    return array;
  }

 // 从文件路径加载配置
private void loadConfigFromFile() {
  try {
  
    // 配置文件路径 - 按优先级查找
    String currentPkg = currentTargetApp;  // 适配分身的配置路径
     String[] loadPaths = {
       "/data/data/" + currentPkg + "/files/install_fake_config.json",
       "/data/user/0/" + currentPkg + "/files/install_fake_config.json",
       "/storage/emulated/0/Android/data/" + currentPkg + "/files/install_fake_config.json",
     };
    File configFile = null;
    String foundPath = null;
    // 按优先级查找存在的配置文件
    for (String filePath : loadPaths) {
      File tempFile = new File(filePath);
      if (tempFile.exists() && tempFile.length() > 0) {
        configFile = tempFile;
        foundPath = filePath;
        break; // 找到就停止
      }
    }
    if (configFile != null && foundPath != null) {
      try {
        FileInputStream fis = new FileInputStream(configFile);
        BufferedReader reader = new BufferedReader(
          new InputStreamReader(fis)
        );
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          jsonBuilder.append(line);
        }
        reader.close();
        fis.close();
        String jsonStr = jsonBuilder.toString();
        JSONObject configJson = new JSONObject(jsonStr);
        if (configJson.has(currentTargetApp)) {
          JSONObject appConfig = configJson.getJSONObject(currentTargetApp);
          // 加载安装状态
          if (appConfig.has("install_status")) {
            boolean status = appConfig.getBoolean("install_status");
            installStatusMap.put(currentTargetApp, status);
          } else {
            installStatusMap.put(currentTargetApp, true);
          }
          // 加载悬浮窗显示状态
          if (appConfig.has("floating_shown")) {
            boolean shown = appConfig.getBoolean("floating_shown");
            floatingShownMap.put(currentTargetApp, shown);
          } else {
            floatingShownMap.put(currentTargetApp, true);
          }
          // 加载悬浮窗位置
          if (appConfig.has("floating_x")) {
            String xStr = appConfig.getString("floating_x");
            if (!xStr.equals("null") && !xStr.isEmpty()) {
              try {
                floatingXMap.put(currentTargetApp, Float.parseFloat(xStr));
              } catch (NumberFormatException e) {
                floatingXMap.put(currentTargetApp, null);
              }
            }
          }
          if (appConfig.has("floating_y")) {
            String yStr = appConfig.getString("floating_y");
            if (!yStr.equals("null") && !yStr.isEmpty()) {
              try {
                floatingYMap.put(currentTargetApp, Float.parseFloat(yStr));
              } catch (NumberFormatException e) {
                floatingYMap.put(currentTargetApp, null);
              }
            }
          }
          // 加载拦截退出状态
          if (appConfig.has("block_exit")) {
            boolean blockExit = appConfig.getBoolean("block_exit");
            blockExitMap.put(currentTargetApp, blockExit);
          } else {
            blockExitMap.put(currentTargetApp, false);
          }
          // 加载权限伪造状态
          if (appConfig.has("permission_fake")) {
            boolean permissionFake = appConfig.getBoolean("permission_fake");
            permissionFakeMap.put(currentTargetApp, permissionFake);
          } else {
            permissionFakeMap.put(currentTargetApp, true); // 默认开启
          }
          // 加载拦截模式历史
          if (appConfig.has("intercept_patterns")) {
            JSONArray patternsArray = appConfig.getJSONArray(
              "intercept_patterns"
            );
            List<InterceptPattern> patterns = new ArrayList<>();
            for (int i = 0; i < patternsArray.length(); i++) {
              JSONObject patternObj = patternsArray.getJSONObject(i);
              InterceptPattern pattern = new InterceptPattern(
                patternObj.getString("pattern_hash"),
                jsonArrayToStringList(
                  patternObj.getJSONArray("installed_packages")
                ),
                jsonArrayToStringList(
                  patternObj.getJSONArray("not_installed_packages")
                )
              );
              pattern.userChoice = patternObj.optString("user_choice", "");
              pattern.choiceCount = patternObj.optInt("choice_count", 0);
              pattern.lastDetectedTime = patternObj.optLong(
                "last_detected_time",
                System.currentTimeMillis()
              );
              pattern.silentIntercept = patternObj.optBoolean(
                "silent_intercept",
                false
              );
              patterns.add(pattern);
            }
            interceptPatternsMap.put(currentTargetApp, patterns);
          }
          // 加载全局捕获包名
          if (appConfig.has("global_captured_packages")) {
            JSONArray packagesArray = appConfig.getJSONArray(
              "global_captured_packages"
            );
            synchronized (globalCapturedPackages) {
              globalCapturedPackages.clear();
              for (int i = 0; i < packagesArray.length(); i++) {
                String pkg = packagesArray.getString(i);
                if (!globalCapturedPackages.contains(pkg)) {
                  globalCapturedPackages.add(pkg);
                }
              }
            }
          }
          // 加载用户自定义包名
          if (appConfig.has("user_defined_packages")) {
            JSONArray userPackagesArray = appConfig.getJSONArray(
              "user_defined_packages"
            );
            List<String> userPackages = jsonArrayToStringList(
              userPackagesArray
            );
            userDefinedPackagesMap.put(currentTargetApp, userPackages);
            // 同步到全局捕获包（确保参与伪造）
            synchronized (globalCapturedPackages) {
              globalCapturedPackages.addAll(userPackages);
            }
            log("加载用户自定义伪造包名: " + userPackages.size() + " 个");
          } else {
            // 无用户自定义包名时初始化空列表
            userDefinedPackagesMap.put(currentTargetApp, new ArrayList<>());
          }
          // 新增：加载用户自定义排除包名
          if (appConfig.has("excluded_packages")) {
            JSONArray excludedArray = appConfig.getJSONArray("excluded_packages");
            List<String> excludedPackages = jsonArrayToStringList(excludedArray);
            excludedPackagesMap.put(currentTargetApp, excludedPackages);
            log("加载用户自定义排除包名: " + excludedPackages.size() + " 个");
          } else {
            excludedPackagesMap.put(currentTargetApp, new ArrayList<>());
          }
          // 清理全局捕获包名的重复数据
          synchronized (globalCapturedPackages) {
            globalCapturedPackages.addAll(
              new HashSet<>(
                userDefinedPackagesMap.getOrDefault(
                  currentTargetApp,
                  new ArrayList<>()
                )
              )
            );
          }
          log("✅ 配置加载完成 " + foundPath);
        } else {
        //  log("⚠️ 配置文件中没有当前应用的数据，创建默认配置");
          createDefaultConfig();
        }
      } catch (Throwable e) {
        log("❌ 读取配置文件异常: " + e.getMessage());
        createDefaultConfig();
      }
    } else {
    //  log("⚠️ 未找到配置文件,创建默认配置");
      createDefaultConfig();
    }
  } catch (Throwable t) {
    log("❌ 加载配置异常: " + t.getMessage());
    createDefaultConfig();
  }
}


  // 保存配置文件
private void saveConfigToFile() {
  if (currentTargetApp == null || currentTargetApp.isEmpty()) {
    log("❌ 配置保存失败：当前目标应用为空");
    return;
  }
  if (isSystemPackage(currentTargetApp)) {
    log("跳过系统配置: " + currentTargetApp);
    return;
  }
  Boolean status = installStatusMap.get(currentTargetApp);
  if (status == null) {
    log("❌ 配置保存失败：未获取到应用安装状态");
    return;
  }
  // 动态使用当前运行包名（分身时自动为分身包名）
   String currentPkg = currentTargetApp;
  // 按优先级尝试多个保存路径
  String[] savePaths = {
    "/data/data/" + currentTargetApp + "/files/install_fake_config.json",
    "/data/user/0/" + currentTargetApp + "/files/install_fake_config.json",
    "/storage/emulated/0/Android/data/" +
    currentTargetApp +
    "/files/install_fake_config.json",
  };
  boolean saveSuccess = false;
  String savedPath = null;
  Throwable lastError = null;
  for (String filePath : savePaths) {
    try {
      File configFile = new File(filePath);
      File parentDir = configFile.getParentFile();
      // 确保父目录存在
      if (parentDir != null && !parentDir.exists()) {
        boolean mkdirSuccess = parentDir.mkdirs();
        if (!mkdirSuccess) {
          lastError = new Exception("无法创建目录 " + parentDir.getPath());
          log("❌ 配置保存失败：无法创建目录 " + parentDir.getPath());
          continue; // 尝试下一个路径
        }
      }
      // 读取现有配置（无则创建新JSON）
      JSONObject configJson = new JSONObject();
      if (configFile.exists() && configFile.length() > 0) {
        FileInputStream fis = new FileInputStream(configFile);
        BufferedReader reader = new BufferedReader(
          new InputStreamReader(fis)
        );
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
          jsonBuilder.append(line);
        }
        reader.close();
        fis.close();
        String existingJson = jsonBuilder.toString();
        if (!existingJson.trim().isEmpty()) {
          configJson = new JSONObject(existingJson);
        }
      }
      // 组装应用配置数据
      Float x = floatingXMap.get(currentTargetApp);
      Float y = floatingYMap.get(currentTargetApp);
      Boolean blockExit = blockExitMap.get(currentTargetApp);
      Boolean permissionFake = permissionFakeMap.get(currentTargetApp);
      JSONObject appConfig = new JSONObject();
      appConfig.put("install_status", status);
      appConfig.put(
        "floating_shown",
        floatingShownMap.getOrDefault(currentTargetApp, true)
      );
      appConfig.put("floating_x", x != null ? String.valueOf(x) : "null");
      appConfig.put("floating_y", y != null ? String.valueOf(y) : "null");
      appConfig.put("block_exit", blockExit != null ? blockExit : false);
      appConfig.put(
        "permission_fake",
        permissionFake != null ? permissionFake : true
      );
      appConfig.put("last_save_time", System.currentTimeMillis());
      appConfig.put("last_save_path", filePath); // 记录保存的路径
      // 保存用户自定义包名
      List<String> userPackages = userDefinedPackagesMap.getOrDefault(
        currentTargetApp,
        new ArrayList<>()
      );
      appConfig.put(
        "user_defined_packages",
        stringListToJsonArray(userPackages)
      );
      // 新增：保存用户自定义排除包名
      List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
      appConfig.put("excluded_packages", stringListToJsonArray(excludedPackages));
      // 保存拦截模式历史
      List<InterceptPattern> patterns = interceptPatternsMap.get(
        currentTargetApp
      );
      if (patterns != null && !patterns.isEmpty()) {
        JSONArray patternsArray = new JSONArray();
        for (InterceptPattern pattern : patterns) {
          JSONObject patternObj = new JSONObject();
          patternObj.put("pattern_hash", pattern.patternHash);
          patternObj.put(
            "installed_packages",
            stringListToJsonArray(pattern.installedPackages)
          );
          patternObj.put(
            "not_installed_packages",
            stringListToJsonArray(pattern.notInstalledPackages)
          );
          patternObj.put(
            "user_choice",
            pattern.userChoice != null ? pattern.userChoice : ""
          );
          patternObj.put("choice_count", pattern.choiceCount);
          patternObj.put("last_detected_time", pattern.lastDetectedTime);
          patternObj.put("silent_intercept", pattern.silentIntercept);
          patternsArray.put(patternObj);
        }
        appConfig.put("intercept_patterns", patternsArray);
      }
      // 保存全局捕获包名
      synchronized (globalCapturedPackages) {
        appConfig.put(
          "global_captured_packages",
          stringListToJsonArray(globalCapturedPackages)
        );
      }
      // 写入配置文件
      configJson.put(currentTargetApp, appConfig);
      String finalJson = configJson.toString(2); // 格式化JSON
      FileOutputStream fos = new FileOutputStream(configFile);
      OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
      writer.write(finalJson);
      writer.flush();
      writer.close();
      fos.close();
      saveSuccess = true;
      savedPath = filePath;
   //   log("✅ 配置保存成功: " + filePath);
      break; // 保存成功，不再尝试其他路径
    } catch (Throwable t) {
      // 当前路径保存失败，记录日志并继续尝试下一个路径
      lastError = t;
      log("❌ 配置保存异常: " + t.getMessage() + " | 路径: " + filePath);
      continue; // 继续尝试下一个路径
    }
  }
  if (!saveSuccess) {
    if (lastError != null) {
      //    log("❌ 最后错误: " + lastError.getMessage());
    }
  }
}

/*

private void hookGetAppContext() {
    try {
        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            null,
            "onCreate",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    mAppContext = (Context) param.thisObject;
                  //  log("✅ 成功获取应用 Context");

                    // 👇 在这里加载配置！！！
                    loadConfigFromFile();
                }
            }
        );
    } catch (Throwable e) {
    }
}



// 从文件路径加载配置
private void loadConfigFromFile() {
  try {
    Context context = mAppContext;
    if (context == null) {
      log("❌ Context 未就绪，跳过加载");
      return;
    }

    File configFile = new File(context.getFilesDir(), "install_fake_config.json");
    if (!configFile.exists() || configFile.length() == 0) {
      log("⚠️ 配置文件不存在，使用默认配置");
      configLoaded = true; // 🔥 即使是空，也算加载完成
      return;
    }

    FileInputStream fis = new FileInputStream(configFile);
    BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
    StringBuilder jsonBuilder = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      jsonBuilder.append(line);
    }
    reader.close();
    fis.close();

    String json = jsonBuilder.toString();
    JSONObject configJson = new JSONObject(json);

    // 这里保留你原来的解析逻辑
    if (configJson.has(currentTargetApp)) {
      JSONObject appConfig = configJson.getJSONObject(currentTargetApp);

      // 示例：你原来怎么解析就怎么写
      // 我这里按你之前逻辑写
      if (appConfig.has("install_status")) {
        boolean status = appConfig.getBoolean("install_status");
        installStatusMap.put(currentTargetApp, status);
      }
      if (appConfig.has("floating_shown")) {
        boolean shown = appConfig.getBoolean("floating_shown");
        floatingShownMap.put(currentTargetApp, shown);
      }
      if (appConfig.has("block_exit")) {
        boolean block = appConfig.getBoolean("block_exit");
        blockExitMap.put(currentTargetApp, block);
      }
      if (appConfig.has("permission_fake")) {
        boolean fake = appConfig.getBoolean("permission_fake");
        permissionFakeMap.put(currentTargetApp, fake);
      }
    }

    log("✅ 配置加载成功");

  } catch (Throwable e) {
    log("❌ 配置加载失败: " + e.getMessage());
  } finally {
    configLoaded = true; // 🔥 不管成功失败，都标记加载完成
  }
}




private void saveConfigToFile() {
  // ==============================
  // 🔥 🔥 🔥 防丢失核心
  // ==============================
  if (mAppContext == null) {
    log("⏳ Context 未就绪，跳过保存");
    return;
  }
  if (!configLoaded) {
    log("⏳ 配置未加载完成，禁止保存（防止覆盖丢失）");
    return;
  }

  if (currentTargetApp == null || currentTargetApp.isEmpty()) {
    log("❌ 无当前包名，不保存");
    return;
  }
  if (isSystemPackage(currentTargetApp)) {
    return;
  }
  Boolean status = installStatusMap.get(currentTargetApp);
  if (status == null) {
    log("❌ 无安装状态，不保存");
    return;
  }

  try {
    Context context = mAppContext;
    File dir = context.getFilesDir();
    dir.mkdirs(); // 兼容所有分身

    File configFile = new File(dir, "install_fake_config.json");

    JSONObject configJson = new JSONObject();
    if (configFile.exists() && configFile.length() > 0) {
      FileInputStream fis = new FileInputStream(configFile);
      BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) sb.append(line);
      reader.close();
      fis.close();

      String old = sb.toString().trim();
      if (!old.isEmpty()) {
        configJson = new JSONObject(old);
      }
    }

    Float x = floatingXMap.get(currentTargetApp);
    Float y = floatingYMap.get(currentTargetApp);
    Boolean blockExit = blockExitMap.get(currentTargetApp);
    Boolean permissionFake = permissionFakeMap.get(currentTargetApp);

    JSONObject appConfig = new JSONObject();
    appConfig.put("install_status", status);
    appConfig.put("floating_shown", floatingShownMap.getOrDefault(currentTargetApp, true));
    appConfig.put("floating_x", x != null ? String.valueOf(x) : "null");
    appConfig.put("floating_y", y != null ? String.valueOf(y) : "null");
    appConfig.put("block_exit", blockExit != null ? blockExit : false);
    appConfig.put("permission_fake", permissionFake != null ? permissionFake : true);
    appConfig.put("last_save_time", System.currentTimeMillis());

    // 你原来的其他配置项原样保留
    List<String> userPackages = userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
    appConfig.put("user_defined_packages", stringListToJsonArray(userPackages));
    List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
    appConfig.put("excluded_packages", stringListToJsonArray(excludedPackages));

    List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
    if (patterns != null && !patterns.isEmpty()) {
      JSONArray arr = new JSONArray();
      for (InterceptPattern p : patterns) {
        JSONObject o = new JSONObject();
        o.put("pattern_hash", p.patternHash);
        o.put("installed_packages", stringListToJsonArray(p.installedPackages));
        o.put("not_installed_packages", stringListToJsonArray(p.notInstalledPackages));
        o.put("user_choice", p.userChoice);
        o.put("choice_count", p.choiceCount);
        o.put("last_detected_time", p.lastDetectedTime);
        o.put("silent_intercept", p.silentIntercept);
        arr.put(o);
      }
      appConfig.put("intercept_patterns", arr);
    }

    synchronized (globalCapturedPackages) {
      appConfig.put("global_captured_packages", stringListToJsonArray(globalCapturedPackages));
    }

    configJson.put(currentTargetApp, appConfig);

    // 写入
    FileOutputStream fos = new FileOutputStream(configFile);
    OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
    writer.write(configJson.toString(2));
    writer.flush();
    writer.close();
    fos.close();

    log("✅ 配置保存成功：" + configFile.getAbsolutePath());

  } catch (Throwable e) {
    log("❌ 配置保存失败: " + e.getMessage());
  }
}

*/




// 创建默认配置
private void createDefaultConfig() {
  try {
    installStatusMap.put(currentTargetApp, true);
    floatingShownMap.put(currentTargetApp, true);
    blockExitMap.put(currentTargetApp, false);
    permissionFakeMap.put(currentTargetApp, true);
    interceptPatternsMap.put(
      currentTargetApp,
      new ArrayList<InterceptPattern>()
    );
    // 初始化用户自定义包名列表
    userDefinedPackagesMap.put(currentTargetApp, new ArrayList<>());
    // 初始化用户自定义排除包名列表
    excludedPackagesMap.put(currentTargetApp, new ArrayList<>());
    // log("✅ 创建默认配置完成");
  } catch (Throwable t) {
    log("❌ 创建默认配置异常: " + t.getMessage());
  }
}


  // ========== 拦截退出功能核心方法 ==========
  private void hookExitMethods(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "java.lang.System",
        classLoader,
        "exit",
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            param.setResult(null); // 阻断返回
            param.setThrowable(null);
            log("✅ 拦截exit退出");
            // 调用自定义处理逻辑（先拦截后弹窗）
            handleAppExit("System.exit()", param);
          }

          @Override
          protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
            // 禁止执行原方法后的逻辑，彻底阻断
          }
        }
      );

      // Process.killProcess() 同样彻底阻断
      XposedHelpers.findAndHookMethod(
        "android.os.Process",
        classLoader,
        "killProcess",
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            param.setResult(null);
            param.setThrowable(null);
            log("✅ 拦截退出killProcess");
            handleAppExit("Process.killProcess()", param);
          }
        }
      );

      // Activity.finish() 拦截逻辑不变（仅主Activity）
      XposedHelpers.findAndHookMethod(
        "android.app.Activity",
        classLoader,
        "finish",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Activity activity = (Activity) param.thisObject;
            if (isMainActivity(activity)) {
              handleActivityFinish(activity, param);
            }
          }
        }
      );

      log("✅ 拦截finish退出");
    } catch (Throwable t) {
      log("Hook退出方法失败: " + t.getMessage());
    }
  }

  // 判断是否为主Activity
  private boolean isMainActivity(Activity activity) {
    try {
      Intent intent = activity.getIntent();
      if (intent != null) {
        String action = intent.getAction();
        if (Intent.ACTION_MAIN.equals(action)) {
          Set<String> categories = intent.getCategories();
          if (
            categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)
          ) {
            return true;
          }
        }
      }
    } catch (Throwable t) {
      log("判断主Activity异常: " + t.getMessage());
    }
    return false;
  }

  // 处理应用退出
  private void handleAppExit(
    final String exitMethod,
    final MethodHookParam param
  ) {
    try {
      Boolean blockExit = blockExitMap.get(currentTargetApp);
      boolean forceIntercept = (blockExit == null);
      if (!forceIntercept && !blockExit) {
        return;
      }

      DetectedPackages detected = analyzeDetectedPackages();
      if (
        detected.installedPackages.isEmpty() &&
        detected.notInstalledPackages.isEmpty()
      ) {
        return;
      }

      // 兜底：仅做应用层恢复，不阻断 System.exit()
      new Handler(Looper.getMainLooper()).post(
        new Runnable() {
          @Override
          public void run() {
            Activity activity = getCurrentActivity();
            if (activity != null && !activity.isFinishing()) {
              // 强制恢复应用前台
              ActivityManager am = (ActivityManager) activity.getSystemService(
                Context.ACTIVITY_SERVICE
              );
              if (am != null) {
                am.moveTaskToFront(
                  activity.getTaskId(),
                  ActivityManager.MOVE_TASK_NO_USER_ACTION
                );
              }
              showSilentToast(activity, "已拦截应用退出");
            }
          }
        }
      );

      // 保存配置
      if (forceIntercept) {
        blockExitMap.put(currentTargetApp, true);
        saveConfigToFile();
      }
    } catch (Throwable t) {
      log("处理退出拦截异常: " + t.getMessage());
    }
  }

  // 处理Activity结束
  private void handleActivityFinish(
    final Activity activity,
    final MethodHookParam param
  ) {
    try {
      Boolean blockExit = blockExitMap.get(currentTargetApp);
      if (blockExit == null || !blockExit) {
        return;
      }
      DetectedPackages detected = analyzeDetectedPackages();
      if (
        detected.installedPackages.isEmpty() &&
        detected.notInstalledPackages.isEmpty()
      ) {
        return;
      }
      boolean shouldSilentIntercept = checkSilentIntercept(detected);
      if (shouldSilentIntercept) {
        param.setResult(null);
        showSilentInterceptToast(detected);
        log("静默拦截Activity退出");
        return;
      }

      // 创建局部final变量，供匿名内部类引用
      final DetectedPackages finalDetected = detected;
      final Activity finalActivity = activity;
      final MethodHookParam finalParam = param;

      // 强制显示弹窗
      new Handler(Looper.getMainLooper()).post(
        new Runnable() {
          @Override
          public void run() {
            try {
              if (finalActivity.isFinishing() || finalActivity.isDestroyed()) {
                return;
              }

              // 构建弹窗消息
              StringBuilder message = new StringBuilder();
              if (
                !finalDetected.installedPackages.isEmpty() &&
                finalDetected.notInstalledPackages.isEmpty()
              ) {
                message.append("当前应用检测到你已安装：\n");
                for (String pkg : finalDetected.installedPackages) {
                  message.append("• ").append(pkg).append("\n");
                }
              } else if (
                finalDetected.installedPackages.isEmpty() &&
                !finalDetected.notInstalledPackages.isEmpty()
              ) {
                message.append("当前应用检测到你未安装：\n");
                for (String pkg : finalDetected.notInstalledPackages) {
                  message.append("• ").append(pkg).append("\n");
                }
              } else {
                message.append("当前应用检测到你：\n\n");
                if (!finalDetected.installedPackages.isEmpty()) {
                  message.append("【已安装】：\n");
                  for (String pkg : finalDetected.installedPackages) {
                    message.append("• ").append(pkg).append("\n");
                  }
                  message.append("\n");
                }
                if (!finalDetected.notInstalledPackages.isEmpty()) {
                  message.append("【未安装】：\n");
                  for (String pkg : finalDetected.notInstalledPackages) {
                    message.append("• ").append(pkg).append("\n");
                  }
                }
              }
              message.append("\n即将结束退出应用\n请选择是否退出？");

              // 创建弹窗
              AlertDialog dialog = createBoundedDialog(
                finalActivity,
                "拦截提醒",
                message.toString(),
                new String[] { "拦截退出", "不拦截" },
                new DialogInterface.OnClickListener[] {
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      handleInterceptChoice(finalDetected, "intercept", true);
                      dialog.dismiss();
                      Toast.makeText(
                        finalActivity,
                        "已拦截退出，应用继续运行",
                        Toast.LENGTH_SHORT
                      ).show();
                    }
                  },
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      handleInterceptChoice(finalDetected, "allow", false);
                      dialog.dismiss();
                      blockExitMap.put(currentTargetApp, false);
                      saveConfigToFile();
                      try {
                        finalParam.setResult(null);
                        finalActivity.finish();
                      } catch (Throwable t) {
                        log("重新结束Activity异常: " + t.getMessage());
                      }
                      Toast.makeText(
                        finalActivity,
                        "已允许退出，拦截功能已关闭",
                        Toast.LENGTH_SHORT
                      ).show();
                    }
                  },
                }
              );

              // 仅保留基础配置
              dialog.setCanceledOnTouchOutside(false);
              dialog.setCancelable(false); // 禁止关闭
              dialog.show();

              // 基础置顶
              Window window = dialog.getWindow();
              if (window != null) {
                finalActivity.getWindow().getDecorView().bringToFront();
                window.getDecorView().bringToFront();
              }
            } catch (Throwable t) {
              log("显示Activity退出拦截对话框异常: " + t.getMessage());
            }
          }
        }
      );

      // 阻塞Activity退出
      param.setResult(null);
    } catch (Throwable t) {
      log("处理Activity结束异常: " + t.getMessage());
    }
  }

 // 分析检测到的包名（过滤排除包，不纳入伪造列表）
private DetectedPackages analyzeDetectedPackages() {
  DetectedPackages detected = new DetectedPackages();
  try {
    Boolean currentStatus = installStatusMap.get(currentTargetApp);
    boolean isInstalledMode = currentStatus != null ? currentStatus : true;
    // 用 Set 临时存储，自动去重
    Set<String> uniqueInstalled = new HashSet<>();
    Set<String> uniqueNotInstalled = new HashSet<>();
    // 获取用户手动排除的包名列表（核心过滤逻辑）
    List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
    
    synchronized (globalCapturedPackages) {
      for (String pkg : globalCapturedPackages) {
        if (isSystemPackage(pkg)) continue; // 过滤系统包
        if (excludedPackages.contains(pkg)) continue; // 过滤排除包（不纳入伪造）
        if (isInstalledMode) {
          uniqueInstalled.add(pkg); // 自动去重
        } else {
          uniqueNotInstalled.add(pkg); // 自动去重
        }
      }
    }
    // 转存回 List，保持原有逻辑
    detected.installedPackages = new ArrayList<>(uniqueInstalled);
    detected.notInstalledPackages = new ArrayList<>(uniqueNotInstalled);
    // 生成模式哈希
    detected.patternHash = generatePatternHash(detected);
  } catch (Throwable t) {
    log("分析检测包名异常: " + t.getMessage());
  }
  return detected;
}


  // 生成模式哈希
  private String generatePatternHash(DetectedPackages detected) {
    try {
      List<String> allPackages = new ArrayList<>();
      allPackages.addAll(detected.installedPackages);
      allPackages.add("|");
      allPackages.addAll(detected.notInstalledPackages);
      Collections.sort(allPackages);

      MessageDigest md = MessageDigest.getInstance("MD5");
      for (String pkg : allPackages) {
        md.update(pkg.getBytes());
      }
      byte[] digest = md.digest();

      StringBuilder hexString = new StringBuilder();
      for (byte b : digest) {
        hexString.append(String.format("%02x", b));
      }

      return hexString.toString();
    } catch (Throwable t) {
      log("生成模式哈希异常: " + t.getMessage());
      return "default_hash";
    }
  }

  // 检查静默拦截
  private boolean checkSilentIntercept(DetectedPackages detected) {
    try {
      List<InterceptPattern> patterns = interceptPatternsMap.get(
        currentTargetApp
      );
      if (patterns == null) {
        return false;
      }

      for (InterceptPattern pattern : patterns) {
        if (
          pattern.patternHash.equals(detected.patternHash) &&
          pattern.silentIntercept &&
          pattern.userChoice.equals("intercept")
        ) {
          pattern.lastDetectedTime = System.currentTimeMillis();
          return true;
        }
      }
    } catch (Throwable t) {
      log("检查静默拦截异常: " + t.getMessage());
    }
    return false;
  }

  // 显示静默拦截Toast
  private void showSilentInterceptToast(DetectedPackages detected) {
    try {
      Activity activity = getCurrentActivity();
      if (activity == null || activity.isFinishing()) {
        return;
      }

      int totalCount =
        detected.installedPackages.size() +
        detected.notInstalledPackages.size();
      String packageNames = "";
      if (totalCount <= 3) {
        List<String> allPackages = new ArrayList<>();
        allPackages.addAll(detected.installedPackages);
        allPackages.addAll(detected.notInstalledPackages);
        packageNames = String.join(", ", allPackages);
      } else {
        packageNames =
          detected.installedPackages.get(0) + " 等" + totalCount + "个应用";
      }

      String message =
        "已自动拦截退出（基于历史选择）\n检测到：" + packageNames;
      Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    } catch (Throwable t) {
      log("显示静默拦截Toast异常: " + t.getMessage());
    }
  }

  // ========== 显示退出拦截对话框 ==========
  private void showExitInterceptDialog(
    final String exitMethod,
    final DetectedPackages detected,
    final MethodHookParam param
  ) {
    try {
      final Activity activity = getCurrentActivity();
      if (activity == null || activity.isFinishing()) {
        return;
      }

      // 构建弹窗消息
      StringBuilder message = new StringBuilder();

      if (
        !detected.installedPackages.isEmpty() &&
        detected.notInstalledPackages.isEmpty()
      ) {
        message.append("当前应用检测到你已安装：\n");
        for (String pkg : detected.installedPackages) {
          message.append("• ").append(pkg).append("\n");
        }
      } else if (
        detected.installedPackages.isEmpty() &&
        !detected.notInstalledPackages.isEmpty()
      ) {
        message.append("当前应用检测到你未安装：\n");
        for (String pkg : detected.notInstalledPackages) {
          message.append("• ").append(pkg).append("\n");
        }
      } else {
        message.append("当前应用检测到你：\n\n");
        if (!detected.installedPackages.isEmpty()) {
          message.append("【已安装】：\n");
          for (String pkg : detected.installedPackages) {
            message.append("• ").append(pkg).append("\n");
          }
          message.append("\n");
        }
        if (!detected.notInstalledPackages.isEmpty()) {
          message.append("【未安装】：\n");
          for (String pkg : detected.notInstalledPackages) {
            message.append("• ").append(pkg).append("\n");
          }
        }
      }

      message.append("\n即将结束退出应用\n请选择是否退出？");

      AlertDialog dialog = createBoundedDialog(
        activity,
        "拦截提醒",
        message.toString(),
        new String[] { "拦截退出", "不拦截" },
        new DialogInterface.OnClickListener[] {
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              // 拦截退出
              handleInterceptChoice(detected, "intercept", true);
              dialog.dismiss();
              Toast.makeText(
                activity,
                "已拦截退出，应用继续运行",
                Toast.LENGTH_SHORT
              ).show();
            }
          },
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              // 不拦截
              handleInterceptChoice(detected, "allow", false);
              dialog.dismiss();
              blockExitMap.put(currentTargetApp, false);
              saveConfigToFile();

              try {
                param.setResult(null);
                if (exitMethod.equals("System.exit()")) {
                  System.exit((int) param.args[0]);
                } else if (exitMethod.equals("Process.killProcess()")) {
                  android.os.Process.killProcess((int) param.args[0]);
                }
              } catch (Throwable t) {
                log("重新退出异常: " + t.getMessage());
              }

              Toast.makeText(
                activity,
                "已允许退出，拦截功能已关闭",
                Toast.LENGTH_SHORT
              ).show();
            }
          },
        }
      );

      dialog.show();
    } catch (Throwable t) {
      log("显示退出拦截对话框异常: " + t.getMessage());
    }
  }


  // ========== 显示Activity退出拦截对话框 ==========
  private void showActivityExitInterceptDialog(
    final Activity activity,
    final DetectedPackages detected,
    final MethodHookParam param
  ) {
    try {
      // 构建弹窗消息
      StringBuilder message = new StringBuilder();

      if (
        !detected.installedPackages.isEmpty() &&
        detected.notInstalledPackages.isEmpty()
      ) {
        message.append("当前应用检测到你已安装：\n");
        for (String pkg : detected.installedPackages) {
          message.append("• ").append(pkg).append("\n");
        }
      } else if (
        detected.installedPackages.isEmpty() &&
        !detected.notInstalledPackages.isEmpty()
      ) {
        message.append("当前应用检测到你未安装：\n");
        for (String pkg : detected.notInstalledPackages) {
          message.append("• ").append(pkg).append("\n");
        }
      } else {
        message.append("当前应用检测到你：\n\n");
        if (!detected.installedPackages.isEmpty()) {
          message.append("【已安装】：\n");
          for (String pkg : detected.installedPackages) {
            message.append("• ").append(pkg).append("\n");
          }
          message.append("\n");
        }
        if (!detected.notInstalledPackages.isEmpty()) {
          message.append("【未安装】：\n");
          for (String pkg : detected.notInstalledPackages) {
            message.append("• ").append(pkg).append("\n");
          }
        }
      }

      message.append("\n即将结束退出应用\n请选择是否退出？");

      AlertDialog dialog = createBoundedDialog(
        activity,
        "拦截提醒",
        message.toString(),
        new String[] { "拦截退出", "不拦截" },
        new DialogInterface.OnClickListener[] {
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              // 拦截退出
              handleInterceptChoice(detected, "intercept", true);
              dialog.dismiss();
              Toast.makeText(
                activity,
                "已拦截退出，应用继续运行",
                Toast.LENGTH_SHORT
              ).show();
            }
          },
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              // 不拦截
              handleInterceptChoice(detected, "allow", false);
              dialog.dismiss();
              blockExitMap.put(currentTargetApp, false);
              saveConfigToFile();

              try {
                param.setResult(null);
                activity.finish();
              } catch (Throwable t) {
                log("重新结束Activity异常: " + t.getMessage());
              }

              Toast.makeText(
                activity,
                "已允许退出，拦截功能已关闭",
                Toast.LENGTH_SHORT
              ).show();
            }
          },
        }
      );

      dialog.show();
    } catch (Throwable t) {
      log("显示Activity退出拦截对话框异常: " + t.getMessage());
    }
  }

  // ========== 处理拦截选择 ==========
  private void handleInterceptChoice(
    DetectedPackages detected,
    String choice,
    boolean interceptSuccess
  ) {
    try {
      List<InterceptPattern> patterns = interceptPatternsMap.get(
        currentTargetApp
      );
      if (patterns == null) {
        patterns = new ArrayList<>();
        interceptPatternsMap.put(currentTargetApp, patterns);
      }

      InterceptPattern existingPattern = null;
      for (InterceptPattern pattern : patterns) {
        if (pattern.patternHash.equals(detected.patternHash)) {
          existingPattern = pattern;
          break;
        }
      }

      if (existingPattern == null) {
        existingPattern = new InterceptPattern(
          detected.patternHash,
          new ArrayList<>(detected.installedPackages),
          new ArrayList<>(detected.notInstalledPackages)
        );
        patterns.add(existingPattern);
      }

      existingPattern.userChoice = choice;
      existingPattern.choiceCount++;
      existingPattern.lastDetectedTime = System.currentTimeMillis();
      // 超过2次后你再询问是否拦截
      if (choice.equals("intercept") && existingPattern.choiceCount >= 2) {
        existingPattern.silentIntercept = true;
        log("启用静默拦截，模式: " + detected.patternHash);
      } else if (choice.equals("allow")) {
        existingPattern.silentIntercept = false;
      }

      saveConfigToFile();

      log(
        "拦截选择记录 - 模式: " +
        detected.patternHash +
        ", 选择: " +
        choice +
        ", 计数: " +
        existingPattern.choiceCount +
        ", 静默: " +
        existingPattern.silentIntercept
      );
    } catch (Throwable t) {
      log("处理拦截选择异常: " + t.getMessage());
    }
  }

  // ========== 悬浮窗功能 ==========
private void hookActivityLifecycle(ClassLoader classLoader) {
    try {
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            classLoader,
            "onCreate",
            Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        final Activity activity = (Activity) param.thisObject;
                        final String activityName = activity.getClass().getName();

                        if (activityName.contains("com.android") ||
                            activityName.contains("android.support") ||
                            activityName.contains("androidx.")) {
                            return;
                        }

                        // 加载悬浮窗调用
                        floatingShownMap.put(currentTargetApp, true);
                        
                        new Handler(Looper.getMainLooper()).postDelayed(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        showFloatingView(activity);
                                    } catch (Throwable t) {
                                        log("显示悬浮窗异常: " + t.getMessage());
                                    }
                                }
                            },
                            850
                        );
                    } catch (Throwable t) {
                        log("Activity生命周期Hook异常: " + t.getMessage());
                    }
                }
            }
        );
        
        // 直接通过 Hook onResume 和 onPause 来追踪当前Activity
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            classLoader,
            "onResume",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    currentResumedActivity = (Activity) param.thisObject;
                    log("当前Activity: " + currentResumedActivity.getClass().getName());
                }
            }
        );
        
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            classLoader,
            "onPause",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (currentResumedActivity == param.thisObject) {
                        currentResumedActivity = null;
                    }
                }
            }
        );
        
        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            classLoader,
            "onDestroy",
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (currentResumedActivity == param.thisObject) {
                        currentResumedActivity = null;
                    }
                }
            }
        );
        
        //log("✅ Activity追踪Hook完成");
        
    } catch (Throwable t) {
        log("Hook Activity生命周期异常: " + t.getMessage());
    }
}

// 完善 getCurrentActivity 方法
private Activity getCurrentActivity() {
    // 返回当前追踪的Activity
    if (currentResumedActivity != null && 
        !currentResumedActivity.isFinishing() && 
        !currentResumedActivity.isDestroyed()) {
        return currentResumedActivity;
    }
    return null;
}

  // 完整的 showFloatingView 方法
  private void showFloatingView(final Activity activity) {
    try {
      Boolean shouldShow = floatingShownMap.get(currentTargetApp);
      if (shouldShow == null || !shouldShow) {
        return;
      }
      activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            try {
              View existingView = activity
                .getWindow()
                .getDecorView()
                .findViewWithTag("install_fake_floating");
              if (existingView != null) {
                return;
              }
              if (activity.isFinishing() || activity.isDestroyed()) {
                return;
              }
              final TextView floatingView = createFloatingView(activity);
              if (floatingView == null) {
                return;
              }
              final ViewGroup decorView = (ViewGroup) activity
                .getWindow()
                .getDecorView();
              if (decorView == null) {
                return;
              }
              ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
              );
              decorView.addView(floatingView, params);
              floatingView.post(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      Float savedX = floatingXMap.get(currentTargetApp);
                      Float savedY = floatingYMap.get(currentTargetApp);
                      int screenWidth = decorView.getWidth();
                      int screenHeight = decorView.getHeight();
                      int viewWidth = floatingView.getWidth();
                      int viewHeight = floatingView.getHeight();
                      if (viewWidth == 0) viewWidth = 200;
                      if (viewHeight == 0) viewHeight = 80;
                      float x, y;
                      if (
                        savedX != null &&
                        savedY != null &&
                        savedX >= 0 &&
                        savedY >= 0
                      ) {
                        x = savedX;
                        y = savedY;
                      } else {
                        x = screenWidth - viewWidth - 50;
                        y = 200;
                      }
                      if (screenWidth > 0 && screenHeight > 0) {
                        x = Math.max(
                          10,
                          Math.min(x, screenWidth - viewWidth - 10)
                        );
                        y = Math.max(
                          50,
                          Math.min(y, screenHeight - viewHeight - 100)
                        );
                      }
                      floatingView.setX(x);
                      floatingView.setY(y);
                      floatingXMap.put(currentTargetApp, x);
                      floatingYMap.put(currentTargetApp, y);
                    } catch (Throwable t) {
                      log("设置位置异常: " + t.getMessage());
                    }
                  }
                }
              );
              // 置顶逻辑-顶层显示）
              updateFloatingToTop(activity, floatingView, decorView);
              start定时置顶(activity, decorView);
              hook弹窗相关方法(activity, decorView);
            } catch (Throwable t) {
              log("添加悬浮窗异常: " + t.getMessage());
            }
          }
        }
      );
    } catch (Throwable t) {
      log("显示悬浮窗异常: " + t.getMessage());
    }
  }

  //更新悬浮窗置顶
  private void updateFloatingToTop(
    Activity activity,
    TextView floatingView,
    ViewGroup decorView
  ) {
    if (activity.isFinishing() || floatingView == null || decorView == null) {
      return;
    }
    // 强制置顶（Android所有版本兼容）
    floatingView.bringToFront();
    decorView.updateViewLayout(floatingView, floatingView.getLayoutParams());
    // 提升层级（兼容部分弹窗）
    floatingView.setElevation(9999f);
  }

  //定时置顶
  private void start定时置顶(
    final Activity activity,
    final ViewGroup decorView
  ) {
    // 停止原有定时任务
    stop定时置顶();

    floatingTopHandler = new Handler(Looper.getMainLooper());
    // 每300ms置顶一次
    floatingTopHandler.postDelayed(
      new Runnable() {
        @Override
        public void run() {
          try {
            if (activity.isFinishing() || currentFloatingView == null) {
              stop定时置顶();
              return;
            }
            // 执行置顶
            updateFloatingToTop(activity, currentFloatingView, decorView);
            // 循环执行
            floatingTopHandler.postDelayed(this, 300);
          } catch (Throwable t) {
            log("定时置顶异常：" + t.getMessage());
          }
        }
      },
      300
    );
  }

  //停止定时置顶
  private void stop定时置顶() {
    if (floatingTopHandler != null) {
      floatingTopHandler.removeCallbacksAndMessages(null);
      floatingTopHandler = null;
    }
  }

  // Hook弹窗相关方法
  private void hook弹窗相关方法(
    final Activity activity,
    final ViewGroup decorView
  ) {
    try {
      // Hook AlertDialog.show()
      Class<?> alertDialogClass = Class.forName("android.app.AlertDialog");
      XposedHelpers.findAndHookMethod(
        alertDialogClass,
        "show",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
            // 弹窗显示后，悬浮窗置顶
            activity.runOnUiThread(
              new Runnable() {
                @Override
                public void run() {
                  if (currentFloatingView != null) {
                    updateFloatingToTop(
                      activity,
                      currentFloatingView,
                      decorView
                    );
                  }
                }
              }
            );
          }
        }
      );

      // Hook DialogFragment.show()
      hookDialogFragmentShow(
        "androidx.fragment.app.DialogFragment",
        activity,
        decorView
      );
      hookDialogFragmentShow("android.app.DialogFragment", activity, decorView);
    } catch (ClassNotFoundException e) {
      //  log("Hook弹窗方法失败：未找到对应类");
    } catch (Throwable t) {
      //    log("Hook弹窗方法异常：" + t.getMessage());
    }
  }

  // Hook DialogFragment.show
  private void hookDialogFragmentShow(
    String dialogFragmentClassName,
    final Activity activity,
    final ViewGroup decorView
  ) {
    try {
      Class<?> dialogFragmentClass = Class.forName(dialogFragmentClassName);
      // Hook show方法
      try {
        XposedHelpers.findAndHookMethod(
          dialogFragmentClass,
          "show",
          Class.forName("androidx.fragment.app.FragmentManager"),
          String.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
              activity.runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    if (currentFloatingView != null) {
                      updateFloatingToTop(
                        activity,
                        currentFloatingView,
                        decorView
                      );
                    }
                  }
                }
              );
            }
          }
        );
      } catch (NoSuchMethodError e) {
        // 兼容原生FragmentManager
        XposedHelpers.findAndHookMethod(
          dialogFragmentClass,
          "show",
          Class.forName("android.app.FragmentManager"),
          String.class,
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
              activity.runOnUiThread(
                new Runnable() {
                  @Override
                  public void run() {
                    if (currentFloatingView != null) {
                      updateFloatingToTop(
                        activity,
                        currentFloatingView,
                        decorView
                      );
                    }
                  }
                }
              );
            }
          }
        );
      }
    } catch (ClassNotFoundException | NoSuchMethodError e) {
      // 忽略无对应类/方法的情况
    } catch (Throwable t) {
      log("Hook DialogFragment失败：" + t.getMessage());
    }
  }

// 弹出添加自定义包名对话框（最终版：修复提示不显示+批量输入+全兼容）
private void showAddPackageDialog(final Activity activity) {
  try {
    // 所有匿名类引用变量声明为final，解决非final报错
    final String targetApp = currentTargetApp;
    if (!userDefinedPackagesMap.containsKey(targetApp)) {
      userDefinedPackagesMap.put(targetApp, new ArrayList<>());
    }
    final List<String> userPackages = userDefinedPackagesMap.get(targetApp);
    final DisplayMetrics metrics = new DisplayMetrics();
    activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
    final LinearLayout[] packagesLayoutArr = new LinearLayout[1];
    final EditText[] inputEtArr = new EditText[1];
    
    // 1. 顶部：输入框 + 下拉选择 + 添加按钮 横向布局（保持原有逻辑不变）
    LinearLayout topLayout = new LinearLayout(activity);
    topLayout.setOrientation(LinearLayout.HORIZONTAL);
    topLayout.setPadding(0, 0, 0, 20); // 增加底部内边距，与下方列表分隔
    
    // ========== 输入框优化（保持原有逻辑不变） ==========
    final EditText inputEt = new EditText(activity);
    inputEt.setHint("输入包名\n(如com.a.b.c)");
    inputEt.setPadding(30, 20, 30, 20);
    inputEt.setTextColor(0xFF000000);
    inputEt.setBackgroundColor(0xFFFFFFFF);
    inputEt.setHintTextColor(0xFF999999);
    inputEt.setBackgroundTintMode(null);
    inputEt.setOutlineProvider(null);
    try {
        Class<?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
        Object etDrawable = gradientDrawableClass.newInstance();
        Method setColorMethod = gradientDrawableClass.getDeclaredMethod("setColor", int.class);
        Method setCornerRadiusMethod = gradientDrawableClass.getDeclaredMethod("setCornerRadius", float.class);
        Method setStrokeMethod = gradientDrawableClass.getDeclaredMethod("setStroke", int.class, int.class);
        
        setColorMethod.invoke(etDrawable, 0xFFFFFFFF);
        setCornerRadiusMethod.invoke(etDrawable, 25f);
        setStrokeMethod.invoke(etDrawable, 2, 0xFFE0E0E0); 
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            inputEt.setBackground((android.graphics.drawable.Drawable) etDrawable);
        } else {
            inputEt.setBackgroundDrawable((android.graphics.drawable.Drawable) etDrawable);
        }
    } catch (Exception e) {
        android.graphics.drawable.ShapeDrawable etShape = new android.graphics.drawable.ShapeDrawable();
        etShape.setShape(new android.graphics.drawable.shapes.RoundRectShape(
            new float[]{25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f},
            null, null
        ));
        etShape.getPaint().setColor(0xFFFFFFFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            inputEt.setBackground(etShape);
        } else {
            inputEt.setBackgroundDrawable(etShape);
        }
    }
    LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1
    );
    topLayout.addView(inputEt, etParams);
    inputEtArr[0] = inputEt;
    
    // ========== Spinner优化（保持原有逻辑不变） ==========
    final Spinner typeSpinner = new Spinner(activity);
    List<String> spinnerItems = new ArrayList<>();
    spinnerItems.add("◀ 添加伪造");
    spinnerItems.add("◀ 排除伪造");
    ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, spinnerItems) {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            TextView tv = (TextView) view;
            tv.setTextColor(0xFF000000);
            tv.setTextSize(14);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(40, 20, 40, 20);
            return view;
        }
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = super.getDropDownView(position, convertView, parent);
            TextView tv = (TextView) view;
            tv.setTextColor(0xFF000000);
            tv.setTextSize(18);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setPadding(20, 30, 20, 30);
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            return view;
        }
    };
    spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    typeSpinner.setAdapter(spinnerAdapter);
    typeSpinner.setSelection(0);
    try {
        Class<?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
        Object spinnerDrawable = gradientDrawableClass.newInstance();
        Method setColorMethod = gradientDrawableClass.getDeclaredMethod("setColor", int.class);
        Method setCornerRadiusMethod = gradientDrawableClass.getDeclaredMethod("setCornerRadius", float.class);
        Method setStrokeMethod = gradientDrawableClass.getDeclaredMethod("setStroke", int.class, int.class);
        
        setColorMethod.invoke(spinnerDrawable, 0xFFF5F5F5);
        setCornerRadiusMethod.invoke(spinnerDrawable, 25f);
        setStrokeMethod.invoke(spinnerDrawable, 2, 0xFFE0E0E0);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            typeSpinner.setBackground((android.graphics.drawable.Drawable) spinnerDrawable);
        } else {
            typeSpinner.setBackgroundDrawable((android.graphics.drawable.Drawable) spinnerDrawable);
        }
    } catch (Exception e) {
        android.graphics.drawable.ShapeDrawable shapeDrawable = new android.graphics.drawable.ShapeDrawable();
        shapeDrawable.setShape(new android.graphics.drawable.shapes.RoundRectShape(
            new float[]{25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f},
            null, null
        ));
        shapeDrawable.getPaint().setColor(0xFFF5F5F5);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            typeSpinner.setBackground(shapeDrawable);
        } else {
            typeSpinner.setBackgroundDrawable(shapeDrawable);
        }
    }
    typeSpinner.setPadding(40, 20, 20, 20);
    LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    );
    spinnerParams.leftMargin = 10;
    spinnerParams.width = (int) (activity.getResources().getDisplayMetrics().density * 150);
    topLayout.addView(typeSpinner, spinnerParams);
    
    // ========== 添加按钮（保持原有逻辑不变） ==========
    Button addBtn = new Button(activity);
    addBtn.setText("添加");
    addBtn.setPadding(40, 20, 40, 20);
    addBtn.setTextColor(0xFFFFFFFF);
    addBtn.setBackgroundTintMode(null);
    addBtn.setOutlineProvider(null);
    try {
      Class<?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
      Object addBtnDrawable = gradientDrawableClass.newInstance();
      Method setColorMethod = gradientDrawableClass.getDeclaredMethod("setColor", int.class);
      Method setCornerRadiusMethod = gradientDrawableClass.getDeclaredMethod("setCornerRadius", float.class);
      setColorMethod.invoke(addBtnDrawable, 0xAA4CAF50);
      setCornerRadiusMethod.invoke(addBtnDrawable, 25f);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        addBtn.setBackground((android.graphics.drawable.Drawable) addBtnDrawable);
      } else {
        addBtn.setBackgroundDrawable((android.graphics.drawable.Drawable) addBtnDrawable);
      }
    } catch (Exception e) {
      android.graphics.drawable.ShapeDrawable shapeDrawable = new android.graphics.drawable.ShapeDrawable();
      shapeDrawable.setShape(new android.graphics.drawable.shapes.RoundRectShape(
        new float[] { 25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f },
        null,
        null
      ));
      shapeDrawable.getPaint().setColor(0xAA4CAF50);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        addBtn.setBackground(shapeDrawable);
      } else {
        addBtn.setBackgroundDrawable(shapeDrawable);
      }
    }
    LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    );
    btnParams.leftMargin = 15;
    topLayout.addView(addBtn, btnParams);
    
    // 2. 中间：包名列表（重点修复提示显示问题）
    final int maxScrollHeight = (int) (metrics.heightPixels * 0.5);
    ScrollView adaptiveScrollView = new ScrollView(activity) {
      @Override
      protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(
          maxScrollHeight,
          MeasureSpec.AT_MOST
        );
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
      }
    };
    adaptiveScrollView.setPadding(0, 0, 0, 0);
    // 包名列表容器（关键修复：设置背景色+强制垂直排列）
    final LinearLayout packagesLayout = new LinearLayout(activity);
    packagesLayout.setOrientation(LinearLayout.VERTICAL);
    packagesLayout.setBackgroundColor(0xFFFFFFFF); // 白色背景，避免透明覆盖
    packagesLayout.setPadding(10, 10, 10, 10); // 整体内边距
    adaptiveScrollView.addView(packagesLayout);
    packagesLayoutArr[0] = packagesLayout;

    // 原有提示文本（强化样式）
    TextView tipTv = new TextView(activity);
    tipTv.setText("请准确输入需要伪造的包名,否则伪造失效\n批量输入:一行一个换行或中英文(逗号分号)");
    tipTv.setPadding(30, 20, 30, 10); // 增大上下内边距
    tipTv.setTextSize(12); // 放大文字
    tipTv.setTextColor(0xFFFF5722); // 橙色，醒目
    packagesLayout.addView(tipTv);
    packagesLayout.requestLayout(); // 强制刷新
/*
    // 批量输入提示文本（强化样式）
    TextView batchTipTv = new TextView(activity);
    batchTipTv.setText("批量输入支持：换行、英文逗号(,)、中文逗号（，）、英文分号(;)、中文分号（；）");
    batchTipTv.setPadding(30, 10, 30, 20); // 增大上下内边距
    batchTipTv.setTextSize(11); // 放大文字
    batchTipTv.setTextColor(0xFF2196F3); // 蓝色，辨识度高
    batchTipTv.setGravity(Gravity.START);
    packagesLayout.addView(batchTipTv);
    packagesLayout.requestLayout(); // 强制刷新
*/
    // 延迟刷新包名列表，确保提示先显示
    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
      @Override
      public void run() {
        refreshPackagesLayout(packagesLayout, userPackages, activity);
        packagesLayout.requestLayout(); // 最终刷新
      }
    }, 100);
    
    // 3. 对话框总布局（保持原有逻辑，增加整体内边距）
    LinearLayout totalLayout = new LinearLayout(activity);
    totalLayout.setOrientation(LinearLayout.VERTICAL);
    totalLayout.setPadding(20, 20, 20, 20); // 增大整体内边距
    totalLayout.setBackgroundColor(0xFFFFFFFF); // 总布局背景色
    totalLayout.addView(topLayout);
    totalLayout.addView(
      adaptiveScrollView,
      new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
    );
    
    // 4. 构建对话框（保持原有逻辑不变）
    final AlertDialog dialog = createBoundedDialog(
      activity,
      "添加自定义包名",
      "",
      new String[] { "一键清空", "保存", "取消" },
      new DialogInterface.OnClickListener[] {
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int which) {
            if (userPackages == null || userPackages.isEmpty()) {
              Toast.makeText(activity, "当前无已添加包名，无需清空", Toast.LENGTH_SHORT).show();
              return;
            }
            AlertDialog confirmDialog = createBoundedDialog(
              activity,
              "确认清空",
              "确定要清空所有手动添加的包名吗？<br><br><font color='#F44336'>此操作不可恢复！</font>",
              new String[] { "确认清空", "取消" },
              new DialogInterface.OnClickListener[] {
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface d, int which) {
                    userPackages.clear();
                    excludedPackagesMap.put(targetApp, new ArrayList<>());
                    synchronized (globalCapturedPackages) {
                      globalCapturedPackages.removeAll(userDefinedPackagesMap.get(targetApp));
                    }
                    refreshPackagesLayout(packagesLayoutArr[0], userPackages, activity);
                    Toast.makeText(activity, "✅ 已清空所有手动添加的包名", Toast.LENGTH_SHORT).show();
                    d.dismiss();
                  }
                },
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                  }
                },
              }
            );
            confirmDialog.show();
          }
        },
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int which) {
            if (userPackages == null || userPackages.isEmpty()) {
              Toast.makeText(activity, "当前无已添加包名，无需保存", Toast.LENGTH_SHORT).show();
              return;
            }
            saveConfigToFile();
            Toast.makeText(activity, "配置已保存", Toast.LENGTH_SHORT).show();
            dialogInterface.dismiss();
          }
        },
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int which) {
            dialogInterface.dismiss();
          }
        },
      },
      totalLayout
    );
    
    // 对话框显示优化（强制设置布局参数）
    dialog.setOnShowListener(new DialogInterface.OnShowListener() {
      @Override
      public void onShow(DialogInterface dialogInterface) {
        final Window window = dialog.getWindow();
        if (window != null) {
          WindowManager.LayoutParams params = window.getAttributes();
          params.height = WindowManager.LayoutParams.WRAP_CONTENT;
          params.width = WindowManager.LayoutParams.MATCH_PARENT;
          params.gravity = Gravity.CENTER;
          window.setAttributes(params);
          // 强制刷新对话框布局
          window.getDecorView().requestLayout();
        }
      }
    });
    
    // 5. 添加按钮点击事件（保持批量输入逻辑不变）
    addBtn.setOnClickListener(
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          final String input = inputEt.getText().toString().trim();
          if (input.isEmpty()) {
            Toast.makeText(activity, "请输入有效包名", Toast.LENGTH_SHORT).show();
            return;
          }

          Set<String> pkgSet = new HashSet<>();
          String[] splits = input.split("[\\n,，;；]+");
          for (String pkg : splits) {
            String trimmedPkg = pkg.trim();
            if (!trimmedPkg.isEmpty() && isValidPackageName(trimmedPkg)) {
              pkgSet.add(trimmedPkg);
            }
          }

          final List<String> pkgList = new ArrayList<>(pkgSet);
          if (pkgList.isEmpty()) {
            Toast.makeText(activity, "未识别到有效包名", Toast.LENGTH_SHORT).show();
            return;
          }

          if (input.contains(" ")) {
            AlertDialog spaceDialog = createBoundedDialog(
              activity,
              "检测到空格",
              "",
              new String[] { "去除空格", "强制添加" },
              
              new DialogInterface.OnClickListener[] {
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface d, int which) {
                    List<String> processedList = new ArrayList<>();
                    for (String pkg : pkgList) {
                      processedList.add(pkg.replaceAll(" ", ""));
                    }
                    Set<String> processedSet = new HashSet<>(processedList);
                    processedList = new ArrayList<>(processedSet);
                    int selectedType = typeSpinner.getSelectedItemPosition();
                    batchHandlePackageAdd(processedList, userPackages, packagesLayout, activity, selectedType);
                    inputEt.setText("");
                  }
                },
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface d, int which) {
                    int selectedType = typeSpinner.getSelectedItemPosition();
                    batchHandlePackageAdd(pkgList, userPackages, packagesLayout, activity, selectedType);
                    inputEt.setText("");
                  }
                },
              }
            );
            
            spaceDialog.setMessage(Html.fromHtml(
        "包名包含空格可能导致伪造失效，是否去除所有空格？<br><br>" +
        "将添加 <font color='#FF5722'><b>" + pkgList.size() + "</b></font> 个包名。"
    ));
            spaceDialog.show();
            return;
          }

          int selectedType = typeSpinner.getSelectedItemPosition();
          batchHandlePackageAdd(pkgList, userPackages, packagesLayout, activity, selectedType);
          inputEt.setText("");
        }
      }
    );
    
    dialog.show();
  } catch (Throwable t) {
    log("显示添加包名对话框异常: " + t.getMessage());
  }
}

// 批量处理包名添加（保持原有逻辑不变）
private void batchHandlePackageAdd(List<String> pkgList, List<String> userPackages, LinearLayout packagesLayout, Activity activity, int selectedType) {
  int successCount = 0;
  synchronized (globalCapturedPackages) {
    List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
    for (String pkg : pkgList) {
      if (pkg == null || pkg.isEmpty() || !isValidPackageName(pkg)) {
        continue;
      }
      if (userPackages.contains(pkg)) {
        continue;
      }

      if (selectedType == 0) {
        if (excludedPackages.contains(pkg)) {
          excludedPackages.remove(pkg);
        }
        userPackages.add(pkg);
        if (!globalCapturedPackages.contains(pkg)) {
          globalCapturedPackages.add(pkg);
        }
        successCount++;
      } else {
        if (globalCapturedPackages.contains(pkg)) {
          globalCapturedPackages.remove(pkg);
        }
        userPackages.add(pkg);
        excludedPackages.add(pkg);
        excludedPackagesMap.put(currentTargetApp, excludedPackages);
        successCount++;
      }
    }
  }

  refreshPackagesLayout(packagesLayout, userPackages, activity);
  Toast.makeText(
    activity,
    "处理完成：成功添加 " + successCount + "/" + pkgList.size() + " 个包名",
    Toast.LENGTH_LONG
  ).show();
}




// 辅助方法：处理包名添加（区分伪造/排除）
private void handlePackageAdd(String pkg, List<String> userPackages, LinearLayout packagesLayout, Activity activity, int selectedType) {
  // 1. 校验包名格式
  if (pkg == null || pkg.isEmpty() || !isValidPackageName(pkg)) {
    Toast.makeText(activity, "包名格式无效", Toast.LENGTH_SHORT).show();
    return;
  }
  // 2. 同步锁覆盖全流程（避免并发冲突）
  synchronized (globalCapturedPackages) {
    // 3. 锁内双重去重校验（用户列表 + 全局列表）
    if (userPackages.contains(pkg)) {
      Toast.makeText(activity, "包名已存在，无需重复添加", Toast.LENGTH_SHORT).show();
      return;
    }
    // 4. 根据选择类型处理
    List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
    if (selectedType == 0) { // 添加伪造
      if (excludedPackages.contains(pkg)) {
        excludedPackages.remove(pkg); // 从排除列表移除，避免冲突
      }
      userPackages.add(pkg);
      if (!globalCapturedPackages.contains(pkg)) {
        globalCapturedPackages.add(pkg);
      }
      Toast.makeText(activity, "已添加伪造包名：" + pkg, Toast.LENGTH_SHORT).show();
    } else { // 排除伪造
      if (globalCapturedPackages.contains(pkg)) {
        globalCapturedPackages.remove(pkg); // 从全局捕获列表移除
      }
      userPackages.add(pkg);
      excludedPackages.add(pkg);
      excludedPackagesMap.put(currentTargetApp, excludedPackages);
      Toast.makeText(activity, "已添加排除包名：" + pkg, Toast.LENGTH_SHORT).show();
    }
  }
  // 5. 刷新包名列表显示
  refreshPackagesLayout(packagesLayout, userPackages, activity);
}

// 刷新包名列表（紧凑布局，区分伪造/排除）
private void refreshPackagesLayout(
  final LinearLayout packagesLayout,
  final List<String> userPackages,
  final Activity activity
) {
  // 1. 移除原有所有子视图（保留提示文本）
  for (int i = packagesLayout.getChildCount() - 1; i >= 1; i--) {
    packagesLayout.removeViewAt(i);
  }

  // 2. 分离伪造包名和排除包名
  List<String> fakePackages = new ArrayList<>(); // 伪造包名列表
  List<String> excludePackages = new ArrayList<>(); // 排除包名列表
  List<String> excludedGlobal = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
  
  for (String pkg : userPackages) {
    if (excludedGlobal.contains(pkg)) {
      excludePackages.add(pkg); // 属于排除包，加入排除列表
    } else {
      fakePackages.add(pkg); // 属于伪造包，加入伪造列表
    }
  }

  // 3. 显示伪造包名（上方区域）
  if (!fakePackages.isEmpty()) {
    // 伪造包标题
    TextView fakeTitle = new TextView(activity);
    fakeTitle.setText("📌 伪造包名（" + fakePackages.size() + "个）");
    fakeTitle.setPadding(30, 15, 30, 10);
    fakeTitle.setTextSize(13);
    fakeTitle.setTextColor(0xFF4CAF50);
    packagesLayout.addView(fakeTitle);

    // 渲染伪造包列表
    for (int i = 0; i < fakePackages.size(); i++) {
      final int pos = userPackages.indexOf(fakePackages.get(i)); // 原始索引（用于删除）
      final String pkg = fakePackages.get(i);
      // 包名项布局
      LinearLayout itemLayout = new LinearLayout(activity);
      itemLayout.setOrientation(LinearLayout.HORIZONTAL);
      itemLayout.setPadding(30, 8, 30, 8);
      itemLayout.setGravity(Gravity.CENTER_VERTICAL);

      TextView pkgTv = new TextView(activity);
      pkgTv.setText((i + 1) + ". [伪造] " + pkg); // 伪造标签
      pkgTv.setPadding(0, 5, 0, 5);
      pkgTv.setTextSize(13);
      pkgTv.setSingleLine(false);
      pkgTv.setMaxLines(2);
      pkgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams pkgParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      itemLayout.addView(pkgTv, pkgParams);

      // 删除按钮
      Button delBtn = new Button(activity);
      delBtn.setText("删除");
      delBtn.setTextSize(11);
      delBtn.setPadding(20, 5, 20, 5);
      try {
        Class<?> gradientDrawableClass = Class.forName(
          "android.graphics.drawable.GradientDrawable"
        );
        Object delBtnDrawable = gradientDrawableClass.newInstance();
        Method setColorMethod = gradientDrawableClass.getMethod(
          "setColor",
          int.class
        );
        Method setCornerRadiusMethod = gradientDrawableClass.getMethod(
          "setCornerRadius",
          float.class
        );
        setColorMethod.invoke(delBtnDrawable, 0xAAF44336);
        setCornerRadiusMethod.invoke(delBtnDrawable, 25f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          delBtn.setBackground(
            (android.graphics.drawable.Drawable) delBtnDrawable
          );
        } else {
          delBtn.setBackgroundDrawable(
            (android.graphics.drawable.Drawable) delBtnDrawable
          );
        }
        delBtn.setTextColor(0xFFFFFFFF);
        delBtn.setBackgroundTintMode(null);
        delBtn.setOutlineProvider(null);
      } catch (Throwable e) {
        android.graphics.drawable.ShapeDrawable shapeDrawable =
          new android.graphics.drawable.ShapeDrawable();
        shapeDrawable.setShape(
          new android.graphics.drawable.shapes.RoundRectShape(
            new float[] { 25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f },
            null,
            null
          )
        );
        shapeDrawable.getPaint().setColor(0xAAF44336);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          delBtn.setBackground(shapeDrawable);
        } else {
          delBtn.setBackgroundDrawable(shapeDrawable);
        }
      }
      delBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          userPackages.remove(pos);
          List<String> excludedGlobal = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
          excludedGlobal.remove(pkg);
          excludedPackagesMap.put(currentTargetApp, excludedGlobal);
          synchronized (globalCapturedPackages) {
            globalCapturedPackages.remove(pkg);
          }
          refreshPackagesLayout(packagesLayout, userPackages, activity);
          Toast.makeText(activity, "已删除伪造包名：" + pkg, Toast.LENGTH_SHORT).show();
        }
      });
      itemLayout.addView(delBtn);

      packagesLayout.addView(itemLayout);
      // 分割线（伪造包之间）
      if (i != fakePackages.size() - 1) {
        View divider = new View(activity);
        divider.setBackgroundColor(0xFFE0E0E0);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = 8;
        packagesLayout.addView(divider, dividerParams);
      }
    }
  }

  // 4. 添加分割线（区分伪造和排除包）
  if (!fakePackages.isEmpty() && !excludePackages.isEmpty()) {
    View mainDivider = new View(activity);
    mainDivider.setBackgroundColor(0xFF999999);
    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
    dividerParams.topMargin = 15;
    dividerParams.bottomMargin = 15;
    packagesLayout.addView(mainDivider, dividerParams);
  }

  // 5. 显示排除包名（下方区域）
  if (!excludePackages.isEmpty()) {
    // 排除包标题
    TextView excludeTitle = new TextView(activity);
    excludeTitle.setText("❌ 排除包名（" + excludePackages.size() + "个）");
    excludeTitle.setPadding(30, 15, 30, 10);
    excludeTitle.setTextSize(13);
    excludeTitle.setTextColor(0xFFF44336);
    packagesLayout.addView(excludeTitle);

    // 渲染排除包列表
    for (int i = 0; i < excludePackages.size(); i++) {
      final int pos = userPackages.indexOf(excludePackages.get(i)); // 原始索引（用于删除）
      final String pkg = excludePackages.get(i);
      // 包名项布局
      LinearLayout itemLayout = new LinearLayout(activity);
      itemLayout.setOrientation(LinearLayout.HORIZONTAL);
      itemLayout.setPadding(30, 8, 30, 8);
      itemLayout.setGravity(Gravity.CENTER_VERTICAL);

      TextView pkgTv = new TextView(activity);
      pkgTv.setText((i + 1) + ". [排除] " + pkg); // 排除标签
      pkgTv.setPadding(0, 5, 0, 5);
      pkgTv.setTextSize(13);
      pkgTv.setSingleLine(false);
      pkgTv.setMaxLines(2);
      pkgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
      LinearLayout.LayoutParams pkgParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
      itemLayout.addView(pkgTv, pkgParams);

      // 删除按钮
      Button delBtn = new Button(activity);
      delBtn.setText("删除");
      delBtn.setTextSize(11);
      delBtn.setPadding(20, 5, 20, 5);
      try {
        Class<?> gradientDrawableClass = Class.forName(
          "android.graphics.drawable.GradientDrawable"
        );
        Object delBtnDrawable = gradientDrawableClass.newInstance();
        Method setColorMethod = gradientDrawableClass.getMethod(
          "setColor",
          int.class
        );
        Method setCornerRadiusMethod = gradientDrawableClass.getMethod(
          "setCornerRadius",
          float.class
        );
        setColorMethod.invoke(delBtnDrawable, 0xAAF44336);
        setCornerRadiusMethod.invoke(delBtnDrawable, 25f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          delBtn.setBackground(
            (android.graphics.drawable.Drawable) delBtnDrawable
          );
        } else {
          delBtn.setBackgroundDrawable(
            (android.graphics.drawable.Drawable) delBtnDrawable
          );
        }
        delBtn.setTextColor(0xFFFFFFFF);
        delBtn.setBackgroundTintMode(null);
        delBtn.setOutlineProvider(null);
      } catch (Throwable e) {
        android.graphics.drawable.ShapeDrawable shapeDrawable =
          new android.graphics.drawable.ShapeDrawable();
        shapeDrawable.setShape(
          new android.graphics.drawable.shapes.RoundRectShape(
            new float[] { 25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f },
            null,
            null
          )
        );
        shapeDrawable.getPaint().setColor(0xAAF44336);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
          delBtn.setBackground(shapeDrawable);
        } else {
          delBtn.setBackgroundDrawable(shapeDrawable);
        }
      }
      delBtn.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          userPackages.remove(pos);
          List<String> excludedGlobal = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
          excludedGlobal.remove(pkg);
          excludedPackagesMap.put(currentTargetApp, excludedGlobal);
          synchronized (globalCapturedPackages) {
            globalCapturedPackages.remove(pkg);
          }
          refreshPackagesLayout(packagesLayout, userPackages, activity);
          Toast.makeText(activity, "已删除排除包名：" + pkg, Toast.LENGTH_SHORT).show();
        }
      });
      itemLayout.addView(delBtn);

      packagesLayout.addView(itemLayout);
      // 分割线（排除包之间）
      if (i != excludePackages.size() - 1) {
        View divider = new View(activity);
        divider.setBackgroundColor(0xFFE0E0E0);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = 8;
        packagesLayout.addView(divider, dividerParams);
      }
    }
  }

  // 6. 空列表提示（无伪造+无排除包时）
  if (fakePackages.isEmpty() && excludePackages.isEmpty()) {
    TextView emptyTv = new TextView(activity);
    emptyTv.setText("暂无已添加包名");
    emptyTv.setPadding(30, 15, 30, 15);
    emptyTv.setTextSize(12);
    packagesLayout.addView(emptyTv);
  }
}


  // 包名添加方法
  private void addPackageToList(
    String pkg,
    List<String> userPackages,
    LinearLayout packagesLayout,
    Activity activity
  ) {
    // 1. 复用已有方法校验包名格式（无需新增，直接调用）
    if (pkg == null || pkg.isEmpty() || !isValidPackageName(pkg)) {
      Toast.makeText(activity, "包名格式无效", Toast.LENGTH_SHORT).show();
      return;
    }
    // 2. 同步锁覆盖全流程（避免并发冲突，核心修复）
    synchronized (globalCapturedPackages) {
      // 3. 锁内双重去重校验（用户列表 + 全局列表）
      if (userPackages.contains(pkg) || globalCapturedPackages.contains(pkg)) {
        Toast.makeText(
          activity,
          "包名已存在，无需重复添加",
          Toast.LENGTH_SHORT
        ).show();
        return;
      }
      // 4. 无重复则同步添加（确保用户列表和全局列表一致）
      userPackages.add(pkg);
      globalCapturedPackages.add(pkg);
    }
    // 5. 刷新包名列表显示
    refreshPackagesLayout(packagesLayout, userPackages, activity);
    // 6. 仅添加成功后提示（避免矛盾）
    Toast.makeText(activity, "已添加：" + pkg, Toast.LENGTH_SHORT).show();
  }

  // 带包名的清理询问弹窗（支持选择清理范围）
  private void showClearConfirmDialog(
  final Activity activity,
  final View floatingView
) {
  List<String> userPackages = userDefinedPackagesMap.getOrDefault(
    currentTargetApp,
    new ArrayList<>()
  );
  StringBuilder pkgText = new StringBuilder();
  pkgText.append("检测到 <font color='#FF5722'><b>" + userPackages.size() + "</b></font> 个手动添加的包名<br>"); // 标题后换行
  for (int i = 0; i < userPackages.size(); i++) {
    pkgText
      .append((i + 1))
      .append(". ")
      .append(userPackages.get(i))
      .append("<br>"); // 每个包名后换行
  }
  pkgText.append(
    "<br>==============<br>" // 分隔线+换行
    + "是否需要同步清空？<br><br>" // 换行
    + "同步清空：清理自动捕获包名+手动添加包名 <font color='#FF5722'><b>" + userPackages.size() + "</b></font> 个<br>" // 换行
    + "单独留下：仅清空自动捕获的包名"
  );
 
    AlertDialog dialog = createBoundedDialog(
      activity,
      "清理包名",
      pkgText.toString(),
      new String[] { "同步清空", "单独留下(" + userPackages.size() + ")", "取消" },
      new DialogInterface.OnClickListener[] {
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            clearAllPackageLists(activity, floatingView);
            dialog.dismiss();
          }
        },
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            clearAutoCapturedPackagesOnly(activity, floatingView);
            dialog.dismiss();
          }
        },
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
        },
      }
    );

    dialog.show();
  }

  // 仅清理自动捕获的包，保留手动添加的包
  private void clearAutoCapturedPackagesOnly(
    final Activity activity,
    final View floatingView
  ) {
    try {
      List<String> userPackages = userDefinedPackagesMap.getOrDefault(
        currentTargetApp,
        new ArrayList<>()
      );
      // 1. 清空全局捕获包列表中“非手动添加”的包（保留手动添加的）
      synchronized (globalCapturedPackages) {
        Iterator<String> iterator = globalCapturedPackages.iterator();
        while (iterator.hasNext()) {
          String pkg = iterator.next();
          if (!userPackages.contains(pkg)) {
            iterator.remove(); // 仅移除自动捕获的包
          }
        }
      }
      // 2. 清空当前应用的自动捕获包列表
      appCapturedPackages.clear();
      // 3. 清空拦截模式中“非手动添加”的包
      List<InterceptPattern> patterns = interceptPatternsMap.get(
        currentTargetApp
      );
      if (patterns != null) {
        for (InterceptPattern pattern : patterns) {
          Iterator<String> installedIt = pattern.installedPackages.iterator();
          while (installedIt.hasNext()) {
            if (!userPackages.contains(installedIt.next())) {
              installedIt.remove();
            }
          }
          Iterator<String> notInstalledIt =
            pattern.notInstalledPackages.iterator();
          while (notInstalledIt.hasNext()) {
            if (!userPackages.contains(notInstalledIt.next())) {
              notInstalledIt.remove();
            }
          }
        }
      }
      // 4. 重置智能伪造缓存（仅清空自动捕获包的缓存）
      clearAutoCapturedCache(userPackages);
      // 5. 保存配置
      saveConfigToFile();
      showRefreshConfirmDialog(activity, null); //刷新应用
      // 6. 显示提示
      Toast.makeText(
        activity,
        "✅ 仅清理自动捕获的包，保留手动添加的包",
        Toast.LENGTH_LONG
      ).show();
    } catch (Throwable t) {
      log("仅清理自动捕获包异常: " + t.getMessage());
      Toast.makeText(activity, "清理失败", Toast.LENGTH_SHORT).show();
    }
  }

  // 辅助方法：仅清空自动捕获包的伪造缓存（保留手动添加包的缓存）
  private void clearAutoCapturedCache(List<String> userPackages) {
    try {
      // 遍历缓存，仅移除非手动添加的包的缓存
      Iterator<Map.Entry<String, String>> versionIt = versionCache
        .entrySet()
        .iterator();
      while (versionIt.hasNext()) {
        if (!userPackages.contains(versionIt.next().getKey())) {
          versionIt.remove();
        }
      }
      Iterator<Map.Entry<String, Integer>> versionCodeIt = versionCodeCache
        .entrySet()
        .iterator();
      while (versionCodeIt.hasNext()) {
        if (!userPackages.contains(versionCodeIt.next().getKey())) {
          versionCodeIt.remove();
        }
      }
      Iterator<Map.Entry<String, Long>> installTimeIt = installTimeCache
        .entrySet()
        .iterator();
      while (installTimeIt.hasNext()) {
        if (!userPackages.contains(installTimeIt.next().getKey())) {
          installTimeIt.remove();
        }
      }
      Iterator<Map.Entry<String, String>> installerIt = installerCache
        .entrySet()
        .iterator();
      while (installerIt.hasNext()) {
        if (!userPackages.contains(installerIt.next().getKey())) {
          installerIt.remove();
        }
      }
      Iterator<Map.Entry<String, String>> appNameIt = appNameCache
        .entrySet()
        .iterator();
      while (appNameIt.hasNext()) {
        if (!userPackages.contains(appNameIt.next().getKey())) {
          appNameIt.remove();
        }
      }
      // 清空查询模式记录
      queryPatterns.clear();
    } catch (Throwable t) {
      log("清理自动捕获缓存异常: " + t.getMessage());
    }
  }

  // 创建标识
  private TextView createFloatingView(final Activity activity) {
    try {
      final TextView floatingView = new TextView(activity);
      floatingView.setTag("install_fake_floating");
      Boolean currentStatus = installStatusMap.get(currentTargetApp);
      final boolean status = currentStatus != null ? currentStatus : true;
      String statusText = status ? "已安装" : "未安装";
      // 获取拦截状态标识
      boolean isBlockingExit = blockExitMap.getOrDefault(
        currentTargetApp,
        false
      );
      String blockText = isBlockingExit ? "[拦截]" : "";
      floatingView.setText("伪造安装(" + statusText + ")" + blockText);
      floatingView.setTextSize(14);
      floatingView.setTextColor(0xFFFFFFFF);
      if (status) {
        floatingView.setBackgroundColor(0xAA4CAF50);
      } else {
        floatingView.setBackgroundColor(0xAAF44336);
      }
      floatingView.setPadding(25, 15, 25, 15);
      floatingView.setGravity(Gravity.CENTER);
      try {
        Class<?> gradientDrawableClass = Class.forName(
          "android.graphics.drawable.GradientDrawable"
        );
        Object gradientDrawable = gradientDrawableClass.newInstance();
        Method setColorMethod = gradientDrawableClass.getMethod(
          "setColor",
          int.class
        );
        Method setCornerRadiusMethod = gradientDrawableClass.getMethod(
          "setCornerRadius",
          float.class
        );
        setColorMethod.invoke(
          gradientDrawable,
          status ? 0xAA4CAF50 : 0xAAF44336
        );
        setCornerRadiusMethod.invoke(gradientDrawable, 25f);
        floatingView.setBackground(
          (android.graphics.drawable.Drawable) gradientDrawable
        );
      } catch (Throwable e) {
        log("设置圆角背景失败: " + e.getMessage());
      }
      floatingView.setOnTouchListener(
        new View.OnTouchListener() {
          private float startX, startY;
          private float initialX, initialY;
          private boolean isDragging = false;
          private boolean longPressTriggered = false;
          private final int DRAG_THRESHOLD = 50; //拖动抖动
          private final long LONG_PRESS_TIME = 300; //长按触发时间
          private Handler longPressHandler;

          // 双击检测相关变量
          private long firstClickTime = 0;
          private int clickCount = 0;
          private Handler clickHandler = new Handler();
          private static final int DOUBLE_CLICK_DELAY = 300; //匹配单击触发时间

          // 新增：标记当前是否正在处理单击
          private boolean isClickHandled = false;

          @Override
          public boolean onTouch(final View v, MotionEvent event) {
            try {
              switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                  startX = event.getRawX();
                  startY = event.getRawY();
                  initialX = v.getX();
                  initialY = v.getY();
                  isDragging = false;
                  longPressTriggered = false;
                  isClickHandled = false; // 重置标记

                  // 双击检测逻辑
                  long currentTime = System.currentTimeMillis();
                  if (currentTime - firstClickTime < DOUBLE_CLICK_THRESHOLD) {
                    clickCount++;
                    // 移除之前的单击任务
                    clickHandler.removeCallbacksAndMessages(null);
                    isClickHandled = false;

                    if (clickCount == 2) {
                      // 双击触发
                      clickCount = 0;
                      firstClickTime = 0;
                      // 弹出添加包名对话框
                      showAddPackageDialog(activity);
                      return true;
                    }
                  } else {
                    clickCount = 1;
                    firstClickTime = currentTime;
                  }

                  // 延迟执行单击（给双击留时间）
                  clickHandler.postDelayed(
                    new Runnable() {
                      @Override
                      public void run() {
                        // 关键：只有不是长按、拖拽，且单击未被处理时才执行
                        if (
                          clickCount == 1 &&
                          !longPressTriggered &&
                          !isDragging &&
                          !isClickHandled
                        ) {
                          isClickHandled = true;
                          showStatusSwitchDialog(activity, floatingView);
                          clickCount = 0;
                          firstClickTime = 0;
                        }
                      }
                    },
                    DOUBLE_CLICK_DELAY
                  );

                  // 长按检测
                  longPressHandler = new Handler();
                  longPressHandler.postDelayed(
                    new Runnable() {
                      @Override
                      public void run() {
                        // 关键：长按触发时，取消单击任务并标记
                        clickHandler.removeCallbacksAndMessages(null);
                        longPressTriggered = true;
                        isClickHandled = true; // 标记单击已被处理
                        clickCount = 0;
                        firstClickTime = 0;
                        // 假设单击菜单弹窗是通过 showStatusSwitchDialog 显示的，需持有其引用
                        if (
                          statusSwitchDialog != null &&
                          statusSwitchDialog.isShowing()
                        ) {
                          statusSwitchDialog.dismiss();
                          statusSwitchDialog = null; // 重置引用
                        }
                        showHideDialog(activity, v);
                      }
                    },
                    LONG_PRESS_TIME
                  );
                  return true;
                case MotionEvent.ACTION_MOVE:
                  float deltaX = Math.abs(event.getRawX() - startX);
                  float deltaY = Math.abs(event.getRawY() - startY);

                  if (
                    !isDragging &&
                    (deltaX > DRAG_THRESHOLD || deltaY > DRAG_THRESHOLD)
                  ) {
                    isDragging = true;
                    // 拖拽时取消所有检测并标记
                    clickHandler.removeCallbacksAndMessages(null);
                    if (longPressHandler != null) {
                      longPressHandler.removeCallbacksAndMessages(null);
                    }
                    isClickHandled = true; // 标记单击已被处理
                    clickCount = 0;
                    firstClickTime = 0;
                  }

                  if (isDragging) {
                    float newX = initialX + (event.getRawX() - startX);
                    float newY = initialY + (event.getRawY() - startY);
                    ViewGroup decorView = (ViewGroup) activity
                      .getWindow()
                      .getDecorView();
                    int screenWidth = decorView.getWidth();
                    int screenHeight = decorView.getHeight();
                    int viewWidth = v.getWidth();
                    int viewHeight = v.getHeight();
                    newX = Math.max(
                      10,
                      Math.min(newX, screenWidth - viewWidth - 10)
                    );
                    newY = Math.max(
                      50,
                      Math.min(newY, screenHeight - viewHeight - 100)
                    );
                    v.setX(newX);
                    v.setY(newY);
                    floatingXMap.put(currentTargetApp, newX);
                    floatingYMap.put(currentTargetApp, newY);
                  }
                  return true;
                case MotionEvent.ACTION_UP:
                  // 清理长按处理器
                  if (longPressHandler != null) {
                    longPressHandler.removeCallbacksAndMessages(null);
                  }

                  // 如果长按已触发，确保单击不会执行
                  if (longPressTriggered) {
                    clickHandler.removeCallbacksAndMessages(null);
                    isClickHandled = true;
                  }

                  floatingXMap.put(currentTargetApp, v.getX());
                  floatingYMap.put(currentTargetApp, v.getY());
                  saveConfigToFile();

                  // 重置状态（保留isClickHandled直到下一次ACTION_DOWN）
                  isDragging = false;
                  return true;
                case MotionEvent.ACTION_CANCEL:
                  // 清理所有处理器和状态
                  clickHandler.removeCallbacksAndMessages(null);
                  if (longPressHandler != null) {
                    longPressHandler.removeCallbacksAndMessages(null);
                  }
                  isDragging = false;
                  longPressTriggered = false;
                  isClickHandled = true; // 标记为已处理，防止后续执行
                  clickCount = 0;
                  firstClickTime = 0;
                  return true;
              }
            } catch (Throwable t) {
              log("触摸处理异常: " + t.getMessage());
            }
            return true;
          }

          // 状态切换对话框（细分自定义包名区域+颜色区分）
private void showStatusSwitchDialog(
    final Activity activity,
    final TextView floatingView
) {
  try {
    activity.runOnUiThread(
      new Runnable() {
        @Override
        public void run() {
          try {
            Boolean currentStatus = installStatusMap.get(
              currentTargetApp
            );
            final boolean status = currentStatus != null
              ? currentStatus
              : true;
            // 获取检测到的包信息（已过滤排除包）
            DetectedPackages detected = analyzeDetectedPackages();
            // 计算总项数（仅伪造包，不含排除包）
            int totalPackages =
              detected.installedPackages.size() +
              detected.notInstalledPackages.size();
            // 构建详细消息，包含检测到的包列表（支持HTML颜色）
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder
              .append("当前状态: ")
              .append(status ? "<font color='#4CAF50'>【已安装】</font>" : "<font color='#F44336'>【未安装】</font>")
              .append("<br>"); // 新增换行
            if (totalPackages > 0) {
              messageBuilder
                .append("捕获应用总累计：<font color='#FF5722'><b>")
                .append(totalPackages)
                .append("</b></font><br><br>"); // 新增换行
              if (!detected.installedPackages.isEmpty()) {
                messageBuilder
                  .append("✅ 伪造已安装的包(")
                  .append(detected.installedPackages.size())
                  .append("项):<br>");
                for (String pkg : detected.installedPackages) {
                  messageBuilder
                    .append("+ ")
                    .append(pkg)
                    .append("<br>"); // 每项后换行
                }
                messageBuilder.append("<br>"); // 列表结束后换行
              }
              if (!detected.notInstalledPackages.isEmpty()) {
                messageBuilder
                  .append("❌ 伪造未安装的包(")
                  .append(detected.notInstalledPackages.size())
                  .append("项):<br>");
                for (String pkg : detected.notInstalledPackages) {
                  messageBuilder
                    .append("- ")
                    .append(pkg)
                    .append("<br>"); // 每项后换行
                }
                messageBuilder.append("<br>"); // 列表结束后换行
              }
            } else {
              messageBuilder.append(
                "📊 当前应用 未检测 到任何包<br><br>"
              ); // 新增换行
            }
            // 拆分为2个独立区域显示用户自定义包名（带颜色区分）
            List<String> userPackages = userDefinedPackagesMap.getOrDefault(
              currentTargetApp,
              new ArrayList<>()
            );
            // 关键：补充变量定义（之前缺失导致报错）
            List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
            List<String> userFakePackages = new ArrayList<>();
            for (String pkg : userPackages) {
              if (!excludedPackages.contains(pkg)) {
                userFakePackages.add(pkg);
              }
            }
            // 1. 手动添加的伪造包名（过滤掉已排除的包）- 绿色标题
            if (!userFakePackages.isEmpty()) {
              // 绿色标题（HTML格式）
              messageBuilder
                .append("<font color='#4CAF50'>❣️ 手动添加的伪造包名(")
                .append(userFakePackages.size())
                .append("项):</font><br>");
              for (String pkg : userFakePackages) {
                messageBuilder.append("☆ ").append(pkg).append("<br>");
              }
              messageBuilder.append("<br>");
            }
            // 2. 手动添加的排除包名 - 红色标题
            if (!excludedPackages.isEmpty()) {
              // 红色标题（HTML格式）
              messageBuilder
                .append("<font color='#F44336'>❌ 手动添加的排除包名(")
                .append(excludedPackages.size())
                .append("项):</font><br>");
              for (String pkg : excludedPackages) {
                messageBuilder.append("☆ ").append(pkg).append("<br>");
              }
              messageBuilder.append("<br>");
            }
            messageBuilder.append("请选择要切换的状态:");
            // 创建边界安全的对话框
            AlertDialog dialog = createBoundedDialog(
              activity,
              "配置切换安装状态-(小淋)",
              messageBuilder.toString(),
              new String[] {
                "切换为已安装",
                "切换为未安装",
                "配置更多功能",
              },
              new DialogInterface.OnClickListener[] {
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(
                    DialogInterface dialogInterface,
                    int which
                  ) {
                    if (!status) {
                      handleStatusSwitch(
                        activity,
                        floatingView,
                        true
                      );
                      dialogInterface.dismiss();
                    } else {
                      Toast.makeText(
                        activity,
                        "当前已是已安装状态",
                        Toast.LENGTH_SHORT
                      ).show();
                    }
                  }
                },
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(
                    DialogInterface dialogInterface,
                    int which
                  ) {
                    if (status) {
                      handleStatusSwitch(
                        activity,
                        floatingView,
                        false
                      );
                      dialogInterface.dismiss();
                    } else {
                      Toast.makeText(
                        activity,
                        "当前已是未安装状态",
                        Toast.LENGTH_SHORT
                      ).show();
                    }
                  }
                },
                new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(
                    DialogInterface dialogInterface,
                    int which
                  ) {
                    dialogInterface.dismiss();
                    //显示更多功能配置弹窗
                    showMoreFunctionsDialog(activity, floatingView);
                  }
                },
              }
            );
            statusSwitchDialog = dialog; //长按隐藏弹窗
            dialog.show();
          } catch (Throwable t) {
            log("显示状态切换对话框异常: " + t.getMessage());
            // 备用对话框
            showFallbackDialog(activity, floatingView, status);
          }
        }
      }
    );
  } catch (Throwable t) {
    log("显示状态切换对话框异常: " + t.getMessage());
  }
}



          // ========== 显示更多功能配置对话框 ==========
          private void showMoreFunctionsDialog(
  final Activity activity,
  final TextView floatingView
) {
  try {
    // 获取当前状态
    final boolean isBlockingExit = blockExitMap.getOrDefault(
      currentTargetApp,
      false
    );
    final Boolean currentPermissionFake = permissionFakeMap.get(
      currentTargetApp
    );
    final boolean permissionFakeEnabled = currentPermissionFake !=
      null
      ? currentPermissionFake
      : true;
    // 构建消息内容（添加换行分隔）
    StringBuilder messageBuilder = new StringBuilder();
    messageBuilder.append("当前功能状态：<br><br>"); // 标题后换行
    messageBuilder.append("1. 退出拦截：");
    messageBuilder.append(isBlockingExit ? "✅ 开启" : "❌ 关闭");
    messageBuilder.append("<br>   - 拦截应用因检测到伪造包而退出<br><br>"); // 功能说明换行+空行
    messageBuilder.append("2. 权限伪造：");
    messageBuilder.append(
      permissionFakeEnabled ? "✅ 开启" : "❌ 关闭"
    );
    messageBuilder.append("<br>   - 伪造应用检测权限为已授权<br>"); // 换行
    messageBuilder.append("   - 如读取应用列表、使用统计权限<br><br>"); // 换行+空行
    messageBuilder.append("请选择要配置的功能：");

              // 创建对话框
              AlertDialog dialog = createBoundedDialog(
                activity,
                "更多功能配置",
                messageBuilder.toString(),
                new String[] {
                  isBlockingExit ? "关闭退出拦截" : "开启退出拦截",
                  permissionFakeEnabled ? "关闭权限伪造" : "开启权限伪造",
                  "取消",
                },
                new DialogInterface.OnClickListener[] {
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      // 切换退出拦截功能
                      toggleBlockExit(activity, floatingView, !isBlockingExit);
                      dialog.dismiss();
                    }
                  },
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      // 切换权限伪造功能
                      togglePermissionFake(
                        activity,
                        floatingView,
                        !permissionFakeEnabled
                      );
                      dialog.dismiss();
                    }
                  },
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      dialog.dismiss();
                    }
                  },
                }
              );

              dialog.show();
            } catch (Throwable t) {
              log("显示更多功能对话框异常: " + t.getMessage());
              Toast.makeText(
                activity,
                "显示功能配置失败",
                Toast.LENGTH_SHORT
              ).show();
            }
          }

          // ========== 切换权限伪造功能 ==========
          private void togglePermissionFake(
            final Activity activity,
            final TextView floatingView,
            final boolean enable
          ) {
            try {
              permissionFakeMap.put(currentTargetApp, enable);
              saveConfigToFile();
              //强制重新执行权限Hook逻辑（立即更新拦截规则）
              reHookPermissionMethods(activity.getClassLoader());
              //清空目标应用的权限缓存（兼容系统和应用层缓存）
              clearPermissionCache(activity);
              //发送广播通知应用权限变化（触发部分应用重查询）
              sendPermissionChangeBroadcast(activity);

              String message = enable
                ? "✅ 权限伪造功能已开启\n无需重启，刷新后立即生效！"
                : "❌ 权限伪造功能已关闭\n无需重启，刷新后立即生效！";
              Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
              // log("权限伪造功能: " + (enable ? "开启" : "关闭") + "（已强制刷新）");

              showPermissionFakeRefreshPrompt(activity, enable);
            } catch (Throwable t) {
              log("切换权限伪造异常: " + t.getMessage());
              Toast.makeText(
                activity,
                "切换失败（需重启应用生效）",
                Toast.LENGTH_SHORT
              ).show();
            }
          }

          // 重新执行权限Hook，立即应用新的伪造状态
          private void reHookPermissionMethods(ClassLoader classLoader) {
            try {
              // 重新Hook所有权限相关方法
              hookQueryAllPackagesPermission(classLoader);
              hookPackageUsageStatsPermission(classLoader);
              hookBasicPermissionChecks(classLoader);

              // Hook ContextWrapper的checkSelfPermission（应用自查权限的核心方法）
              try {
                XposedHelpers.findAndHookMethod(
                  "android.content.ContextWrapper",
                  classLoader,
                  "checkSelfPermission",
                  String.class,
                  new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param)
                      throws Throwable {
                      Boolean shouldFakePermission = permissionFakeMap.get(
                        currentTargetApp
                      );
                      boolean fakeEnabled = shouldFakePermission != null
                        ? shouldFakePermission
                        : true;
                      if (!fakeEnabled) return;

                      String permission = (String) param.args[0];
                      if (
                        Arrays.asList(DETECTION_PERMISSIONS).contains(
                          permission
                        )
                      ) {
                        param.setResult(PackageManager.PERMISSION_GRANTED);
                      }
                    }
                  }
                );
              } catch (Throwable t) {
                //    log("重新Hook ContextWrapper权限失败: " + t.getMessage());
              }
              //   log("✅ 权限Hook逻辑已重新加载，立即生效");
            } catch (Throwable t) {
              log("重新Hook权限方法失败: " + t.getMessage());
            }
          }

          // 清空系统+应用层的权限查询缓存
          private void clearPermissionCache(Context context) {
            try {
              // 1. 清空PackageManager系统缓存
              PackageManager pm = context.getPackageManager();
              try {
                // 清空首选活动缓存（影响权限相关组件查询）
                Method clearPreferredMethod =
                  PackageManager.class.getDeclaredMethod(
                      "clearPackagePreferredActivities",
                      String.class
                    );
                clearPreferredMethod.setAccessible(true);
                clearPreferredMethod.invoke(pm, currentTargetApp);
              } catch (Throwable t) {
                log("清空PackageManager缓存失败: " + t.getMessage());
              }

              // 清空应用自身的内存缓存
              try {
                Class<?> appClass = Class.forName(
                  context.getPackageName() + ".App"
                );
                Field cacheField = appClass.getDeclaredField(
                  "sPermissionCache"
                );
                cacheField.setAccessible(true);
                Object cache = cacheField.get(null);
                if (cache instanceof Map) {
                  ((Map<?, ?>) cache).clear();
                  //    log("✅ 清空应用权限内存缓存");
                }
              } catch (Throwable t) {
                // 忽略无缓存类的情况（大部分应用无此缓存）
              }

              // 清空AndroidX权限缓存
              try {
                Class<?> permissionCacheClass = Class.forName(
                  "androidx.core.content.PermissionChecker"
                );
                Field sPermissionCacheField =
                  permissionCacheClass.getDeclaredField("sPermissionCache");
                sPermissionCacheField.setAccessible(true);
                Object cache = sPermissionCacheField.get(null);
                if (cache instanceof Map) {
                  ((Map<?, ?>) cache).clear();
                  //    log("✅ 清空AndroidX权限缓存");
                }
              } catch (Throwable t) {
                // 忽略无AndroidX的情况
              }
            } catch (Throwable t) {
              log("清空权限缓存异常: " + t.getMessage());
            }
          }

          // 发送系统广播，通知应用权限状态变化
          private void sendPermissionChangeBroadcast(Context context) {
            try {
              // 发送系统权限变化广播
              Intent broadcastIntent = new Intent();
              broadcastIntent.setAction(
                "android.intent.action.PACKAGE_PERMISSION_CHANGED"
              );
              broadcastIntent.setData(Uri.parse("package:" + currentTargetApp));
              broadcastIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
              context.sendBroadcast(broadcastIntent);

              // 额外发送自定义广播
              Intent customIntent = new Intent();
              customIntent.setAction(
                "com.install.appinstall.xl.PERMISSION_CHANGED"
              );
              customIntent.putExtra("packageName", currentTargetApp);
              context.sendBroadcast(customIntent);
              //   log("✅ 发送权限变化广播，触发应用重查询");
            } catch (Throwable t) {
              log("发送权限广播异常: " + t.getMessage());
            }
          }

          // ========== 显示权限伪造刷新提示 ==========
          private void showPermissionFakeRefreshPrompt(
  final Activity activity,
  final boolean enabled
) {
  try {
    String title = enabled ? "权限伪造已开启" : "权限伪造已关闭";
    String message = enabled
      ? "✅ <font color='#4CAF50'>权限伪造功能已开启</font><br><br>" +
        "功能效果：<br>" +
        "• 读取应用列表权限时返回虚假已授权<br>" +
        "• 部分应用可能需要重启或无效<br><br>" +
        "是否立即刷新应用使设置生效？"
      : "❌ <font color='#F44336'>权限伪造功能已关闭</font><br><br>" +
        "功能效果：<br>" +
        "• 读取应用列表权限时返回真实状态<br>" +
        "• (比如当前应用没有“授权”,应用能检测到)<br><br>" +
        "• 部分应用可能因此拒绝运行或无效<br>" +
        "是否立即刷新应用使设置生效？";


              AlertDialog dialog = createBoundedDialog(
                activity,
                title,
                message,
                new String[] { "立即刷新", "稍后" },
                new DialogInterface.OnClickListener[] {
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      refreshApplication(activity);
                    }
                  },
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      dialog.dismiss();
                    }
                  },
                }
              );

              dialog.show();
            } catch (Throwable t) {
              log("显示权限伪造刷新提示异常: " + t.getMessage());
            }
          }

          // 备用对话框
          private void showFallbackDialog(
            final Activity activity,
            final TextView floatingView,
            final boolean status
          ) {
            try {
              String[] items = {
                "切换为已安装" + (status ? " (当前)" : ""),
                "切换为未安装" + (!status ? " (当前)" : ""),
                "配置拦截退出",
              };
              new AlertDialog.Builder(activity)
                .setTitle("切换安装状态")
                .setItems(
                  items,
                  new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      switch (which) {
                        case 0:
                          if (!status) {
                            handleStatusSwitch(activity, floatingView, true);
                          }
                          break;
                        case 1:
                          if (status) {
                            handleStatusSwitch(activity, floatingView, false);
                          }
                          break;
                        case 2:
                          showRefreshConfirmDialog(activity, null);
                          break;
                      }
                      dialog.dismiss();
                    }
                  }
                )
                .setNegativeButton("取消", null)
                .show();
            } catch (Throwable t) {
              log("显示备用对话框异常: " + t.getMessage());
              // 最终兜底：直接切换状态（无对话框）
              handleStatusSwitch(activity, floatingView, !status);
            }
          }
        }
      );
      return floatingView;
    } catch (Throwable t) {
      log("创建悬浮窗视图异常: " + t.getMessage());
      return null;
    }
  }



  // ========== 点击菜单 ==========
  private void showClickMenu(
    final Activity activity,
    final TextView floatingView
  ) {
    try {
      Boolean currentStatus = installStatusMap.get(currentTargetApp);
      final boolean status = currentStatus != null ? currentStatus : true;

      Boolean blockExit = blockExitMap.get(currentTargetApp);
      final boolean isBlockingExit = blockExit != null ? blockExit : false;

      // 构建按钮文本（去掉刷新按钮）
      final String btn1Text = "切换为已安装" + (status ? " (当前)" : "");
      final String btn2Text = "切换为未安装" + (!status ? " (当前)" : "");
      final String btn3Text = isBlockingExit
        ? "关闭拦截退出 (已开启)"
        : "开启拦截退出 (已关闭)";

      // 创建带有三个按钮的对话框
      AlertDialog dialog = new AlertDialog.Builder(activity)
        .setTitle("悬浮窗菜单")
        .setMessage("当前应用: " + currentTargetApp)
        .setPositiveButton(
          btn1Text,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              if (!status) {
                handleStatusSwitch(activity, floatingView, true);
              } else {
                Toast.makeText(
                  activity,
                  "当前已是已安装状态",
                  Toast.LENGTH_SHORT
                ).show();
              }
            }
          }
        )
        .setNegativeButton(
          btn2Text,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              if (status) {
                handleStatusSwitch(activity, floatingView, false);
              } else {
                Toast.makeText(
                  activity,
                  "当前已是未安装状态",
                  Toast.LENGTH_SHORT
                ).show();
              }
            }
          }
        )
        .setNeutralButton(
          btn3Text,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              toggleBlockExit(activity, floatingView, !isBlockingExit);
            }
          }
        )
        .create();

      // 显示对话框
      dialog.show();

      // 设置对话框窗口属性
      Window window = dialog.getWindow();
      if (window != null) {
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setAttributes(params);
      }
    } catch (Throwable t) {
      log("显示点击菜单异常: " + t.getMessage());

      // 简单回退方案
      try {
        Toast.makeText(
          activity,
          "显示菜单失败，请重试",
          Toast.LENGTH_SHORT
        ).show();
      } catch (Throwable e) {
        log("显示Toast也失败: " + e.getMessage());
      }
    }
  }

  // ========== 切换拦截退出功能 ==========
  private void toggleBlockExit(
    final Activity activity,
    final TextView floatingView,
    final boolean enable
  ) {
    try {
      blockExitMap.put(currentTargetApp, enable);
      saveConfigToFile();

      if (floatingView != null) {
        Boolean currentStatus = installStatusMap.get(currentTargetApp);
        boolean status = currentStatus != null ? currentStatus : true;
        String statusText = status ? "已安装" : "未安装";
        String blockText = enable ? "[拦截]" : "";
        floatingView.setText("伪造安装(" + statusText + ")" + blockText);
      }

      String message = enable
        ? "✅ 拦截退出功能已开启\n应用因检测到伪造包退出时会被拦截"
        : "❌ 拦截退出功能已关闭\n应用可正常退出";
      Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
      log("拦截退出功能: " + (enable ? "开启" : "关闭"));

      // 显示刷新提示
      showBlockExitRefreshPrompt(activity, enable);
    } catch (Throwable t) {
      log("切换拦截退出异常: " + t.getMessage());
      Toast.makeText(activity, "切换失败", Toast.LENGTH_SHORT).show();
    }
  }

  // ========== 显示退出拦截刷新提示 ==========
  private void showBlockExitRefreshPrompt(
  final Activity activity,
  final boolean enabled
) {
  try {
    String title = enabled ? "退出拦截已开启" : "退出拦截已关闭";
    String message = enabled
      ? "✅ <font color='#4CAF50'>退出拦截功能已开启</font><br><br>" +
        "功能效果：<br>" +
        "• 应用因检测到伪造包而退出时会被拦截<br>" +
        "• 会弹出提示询问是否允许退出<br><br>" +
        "• 可设置静默拦截（不再询问）<br>" +
        "不再询问：当应用没有新的检测包时，你选择相同功能2次后自动拦截<br>" +
        "(比如你选择了2次拦截功能，应用没有更新包名就会拦截退出)<br>" +
        "每当有更新时将作废,直到没有更新。部分应用退出拦截可能失效<br><br>" +
        "是否立即刷新应用使设置生效？"
      : "❌ <font color='#F44336'>退出拦截功能已关闭</font><br><br>" +
        "功能效果：<br>" +
        "• 应用可正常退出<br>" +
        "• 本次不再拦截任何退出行为<br><br>" +
        "静默选择：当应用没有新的检测包时，你选择相同功能2次后自动放行<br>" +
        "(比如你选择了2次不拦截功能，应用没有更新包名就会放行退出)<br>" +
        "每当有更新时将作废,直到没有更新。部分应用退出拦截可能失效<br><br>" +
        "是否立即刷新应用使设置生效？";
        
      AlertDialog dialog = createBoundedDialog(
        activity,
        title,
        message,
        new String[] { "立即刷新", "稍后" },
        new DialogInterface.OnClickListener[] {
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              refreshApplication(activity);
            }
          },
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              dialog.dismiss();
            }
          },
        }
      );

      dialog.show();
    } catch (Throwable t) {
      log("显示退出拦截刷新提示异常: " + t.getMessage());
    }
  }

// ========== 隐藏对话框 ==========
private void showHideDialog(
    final Activity activity,
    final View floatingView
) {
    try {
        String message = "【隐藏】隐藏本次应用内的悬浮窗显示功能<br>" +
                        "<font color='#F44336'>隐藏后将在应用重启时恢复显示</font><br><br>" +
                        "——————————————<br><br>" +
                        "【清理包列表】" +
                        "•清理自动捕获/添加的包名数据<br>" +
                        "<font color='#F44336'>清理后将重新获取自动捕获的新数据</font>";
        AlertDialog dialog = createBoundedDialog(
            activity,
            "悬浮窗设置",
            message,  // 使用HTML格式的消息
            new String[] { "隐藏", "清理包列表", "取消" },
            new DialogInterface.OnClickListener[] {
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            if (floatingView != null) {
                                ViewGroup parent = (ViewGroup) floatingView.getParent();
                                if (parent != null) {
                                    parent.removeView(floatingView);
                                }
                            }
                            floatingShownMap.put(currentTargetApp, false);
                            currentFloatingView = null;
                            stop定时置顶();
                          //  saveConfigToFile();  // ⚠️ 保存后重启不恢复！
                            
                            // ========== 显示提示 ==========
                            Toast.makeText(
                                activity,
                                "✅ 悬浮窗已隐藏\n重启应用后重新显示",
                                Toast.LENGTH_LONG
                            ).show();
                        } catch (Throwable t) {
                            log("❌ 隐藏悬浮窗异常: " + t.getMessage());
                            Toast.makeText(activity, "隐藏失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        List<String> userPackages = userDefinedPackagesMap.getOrDefault(
                            currentTargetApp,
                            new ArrayList<>()
                        );
                        if (userPackages.isEmpty()) {
                            clearAllPackageLists(activity, floatingView);
                        } else {
                            showClearConfirmDialog(activity, floatingView);
                        }
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();  // 取消
                    }
                },
            }
        );
        
        dialog.show();
        
    } catch (Throwable t) {
        log("❌ 显示隐藏对话框异常: " + t.getMessage());
    }
}


  // ========== 清理所有包列表 ==========
// 清理所有包列表（自动捕获+手动添加+排除包）
private void clearAllPackageLists(
  final Activity activity,
  final View floatingView
) {
  try {
    // 1. 清空全局捕获包列表
    synchronized (globalCapturedPackages) {
      globalCapturedPackages.clear();
    }
    // 2. 清空当前应用的捕获包列表
    appCapturedPackages.clear();
    // 3. 清空拦截模式中的包列表
    List<InterceptPattern> patterns = interceptPatternsMap.get(
      currentTargetApp
    );
    if (patterns != null) {
      for (InterceptPattern pattern : patterns) {
        pattern.installedPackages.clear();
        pattern.notInstalledPackages.clear();
      }
    }
    // 4. 清空用户手动添加的包列表（伪造包）
    userDefinedPackagesMap.put(currentTargetApp, new ArrayList<>());
    // 新增：5. 显式清空手动添加的排除包列表（关键修改）
    excludedPackagesMap.put(currentTargetApp, new ArrayList<>());
    // 6. 重置智能伪造缓存
    clearSmartFakeCache();
    // 7. 保存配置到文件（确保清空状态持久化）
    saveConfigToFile();
    // 8. 显示清理成功的提示
    activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String message = "<b>✅ 清理完成</b><br>" +
                                   "• 已清空全局包列表<br>" +
                                   "• 已清空当前应用包列表<br>" +
                                   "• 已清空用户自定义包名<br>" +
                                   "• 已重置伪造缓存<br>" +
                                   "• 配置已保存";
                    
                    Toast toast = Toast.makeText(activity, "", Toast.LENGTH_LONG);
                    TextView tv = new TextView(activity);
                    tv.setText(Html.fromHtml(message));
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(40, 30, 40, 30);
                    tv.setTextSize(16);
                    tv.setTextColor(0xFF000000);
                    tv.setBackgroundColor(0xAAFFFFFF);
                    toast.setView(tv);
                    toast.setGravity(Gravity.CENTER, 0, 400);
                    toast.show();
                    if (floatingView != null) {
                        showRefreshConfirmDialog(activity, null); //显示弹窗
                    }
                } catch (Throwable t) {
                    log("显示清理成功提示异常: " + t.getMessage());
                    // 兜底Toast
                    Toast.makeText(activity, "✅ 清理完成", Toast.LENGTH_SHORT).show();
                }
            }
        });
  } catch (Throwable t) {
    log("清理包列表异常: " + t.getMessage());
    activity.runOnUiThread(
      new Runnable() {
        @Override
        public void run() {
          Toast.makeText(activity, "清理失败", Toast.LENGTH_SHORT).show();
        }
      }
    );
  }
}



  // ========== 清理智能伪造缓存的方法 ==========
  private void clearSmartFakeCache() {
    try {
      versionCache.clear();
      versionCodeCache.clear();
      installTimeCache.clear();
      installerCache.clear();
      appNameCache.clear();
      queryPatterns.clear();
      // log("✅ 已清理智能伪造缓存");
    } catch (Throwable t) {
      log("清理智能伪造缓存异常: " + t.getMessage());
    }
  }

// 边界安全的对话框创建方法（支持自定义视图+HTML消息颜色）
private AlertDialog createBoundedDialog(
  final Activity activity,
  String title,
  String message,
  String[] buttonTexts,
  DialogInterface.OnClickListener[] listeners
) {
  return createBoundedDialog(
    activity,
    title,
    message,
    buttonTexts,
    listeners,
    null
  );
}

// 重载版本：支持自定义视图+HTML消息颜色
private AlertDialog createBoundedDialog(
  final Activity activity,
  String title,
  String message,
  String[] buttonTexts,
  DialogInterface.OnClickListener[] listeners,
  final View customView
) {
  AlertDialog.Builder builder = new AlertDialog.Builder(
    activity,
    AlertDialog.THEME_DEVICE_DEFAULT_LIGHT
  ).setTitle(title);
  // 处理消息或自定义视图（支持HTML颜色）
  if (customView != null) {
    // 使用自定义视图
    builder.setView(customView);
  } else if (message != null && !message.isEmpty()) {
    // 支持HTML格式消息（用于颜色显示）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      builder.setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
    } else {
      builder.setMessage(Html.fromHtml(message));
    }
  }
  // 按钮配置
  if (buttonTexts.length >= 1 && listeners.length >= 1) {
    builder.setPositiveButton(buttonTexts[0], listeners[0]);
  }
  if (buttonTexts.length >= 2 && listeners.length >= 2) {
    builder.setNegativeButton(buttonTexts[1], listeners[1]);
  }
  if (buttonTexts.length >= 3 && listeners.length >= 3) {
    builder.setNeutralButton(buttonTexts[2], listeners[2]);
  }
  final AlertDialog dialog = builder.create();
  // 设置对话框显示时的属性
  dialog.setOnShowListener(
    new DialogInterface.OnShowListener() {
      @Override
      public void onShow(DialogInterface dialogInterface) {
        try {
          Window window = dialog.getWindow();
          if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            // 基础层级提升
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            // 位置和大小
            final DisplayMetrics metrics = new DisplayMetrics();
            activity
              .getWindowManager()
              .getDefaultDisplay()
              .getMetrics(metrics);
            // 根据是否有自定义视图调整大小
            if (customView != null) {
              // 自定义视图时使用包裹内容
              params.width = WindowManager.LayoutParams.WRAP_CONTENT;
              params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            } else {
              // 文本消息时使用MATCH_PARENT
              params.width = WindowManager.LayoutParams.MATCH_PARENT;
              params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            }
            params.gravity = Gravity.CENTER;
            params.horizontalMargin = 0.25f; // 25%边距
            // 弱暗化
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            params.dimAmount = 0.6f; // 60%暗化，确保弹窗可见
            // 安全置顶
            window.setAttributes(params);
            window.getDecorView().bringToFront();
            activity.getWindow().getDecorView().bringToFront();
            // 对于自定义视图，可能需要额外处理
            if (customView != null) {
              // 设置最大高度（避免超出屏幕）
              final View contentView = window.findViewById(
                android.R.id.content
              );
              if (contentView != null) {
                contentView.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      int maxHeight = (metrics.heightPixels * 70) / 100; // 屏幕高度的70%
                      if (contentView.getHeight() > maxHeight) {
                        LinearLayout.LayoutParams lp =
                          new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            maxHeight
                          );
                        contentView.setLayoutParams(lp);
                      }
                    }
                  }
                );
              }
            }
          }
        } catch (Throwable t) {
          log("设置对话框异常: " + t.getMessage());
          // 兜底：强制显示弹窗
          dialog.show();
        }
      }
    }
  );
  // 允许外部点击关闭（但保留默认行为）
  dialog.setCanceledOnTouchOutside(true);
  dialog.setCancelable(true);
  return dialog;
}


  // ========== 状态切换处理方法 ==========
  private void handleStatusSwitch(
    Activity activity,
    TextView floatingView,
    boolean newStatus
  ) {
    try {
      // 1. 更新内存状态
      installStatusMap.put(currentTargetApp, newStatus);

      // 2.同步获取拦截状态，拼接完整文本
      String statusText = newStatus ? "已安装" : "未安装";
      boolean isBlockingExit = blockExitMap.getOrDefault(
        currentTargetApp,
        false
      ); // 获取当前拦截状态
      String blockText = isBlockingExit ? "[拦截]" : ""; // 拦截标识

      // 3. 更新悬浮窗显示
      if (floatingView != null) {
        floatingView.setText("伪造安装(" + statusText + ")" + blockText); // 安装状态+拦截标识
        floatingView.setBackgroundColor(newStatus ? 0xAA4CAF50 : 0xAAF44336);
      }

      // 4. 保存到文件
      saveConfigToFile();

      // 5. 显示提示
      String message = "已切换为" + statusText + "状态";
      Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();

      // 6. 显示刷新提示
      showRefreshPrompt(activity, message);
    } catch (Throwable t) {
      log("处理状态切换异常: " + t.getMessage());
      Toast.makeText(activity, "切换失败", Toast.LENGTH_SHORT).show();
    }
  }

  // ========== 刷新功能 ==========
  private void showRefreshPrompt(
  final Activity activity,
  final String message
) {
  try {
    AlertDialog dialog = createBoundedDialog(
      activity,
      "状态切换成功",
      message + "<br><br>是否立即刷新应用使状态生效？", // 新增空行
      new String[] { "立即刷新", "稍后" },
        new DialogInterface.OnClickListener[] {
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              refreshApplication(activity);
            }
          },
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              dialog.dismiss();
            }
          },
        }
      );

      dialog.show();
    } catch (Throwable t) {
      log("显示刷新提示异常: " + t.getMessage());
    }
  }

//刷新弹窗
  private void showRefreshConfirmDialog(final Activity activity, final String customMessage) {
    if (activity == null || activity.isFinishing()) return;
    
    try {
        String message;
        if (customMessage != null && !customMessage.isEmpty()) {
            message = customMessage + "<br><br>是否立即刷新应用使配置生效？";
        } else {
            message = "这将重新加载配置文件并更新显示，<br>" +
                     "使状态切换立即生效。<br><br>" +
                     "确定要刷新吗？";
        }
        
        AlertDialog dialog = createBoundedDialog(
            activity,
            "刷新应用",
            message,
            new String[]{"立即刷新", "取消"},
            new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        refreshApplication(activity);
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }
            }
        );
        
        dialog.show();
        
    } catch (Throwable t) {
        log("显示刷新对话框异常: " + t.getMessage());
    }
}

  private void refreshApplication(final Activity activity) {
    try {
     // appCapturedPackages.clear();
      installStatusMap.remove(currentTargetApp);
      floatingShownMap.remove(currentTargetApp);
      loadConfigFromFile();
      updateFloatingView(activity);

      activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Boolean currentStatus = installStatusMap.get(currentTargetApp);
                    String statusText = "未知";
                    if (currentStatus != null) {
                        statusText = currentStatus ? "已安装" : "未安装";
                    }
                    String toastMessage = "✅ 刷新完成<br>当前状态: " + statusText;
                    
                    // 使用自定义Toast显示HTML
                    Toast toast = Toast.makeText(activity, "", Toast.LENGTH_SHORT);
                    TextView tv = new TextView(activity);
                    tv.setText(Html.fromHtml(toastMessage));
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(30, 20, 30, 20);
                    tv.setTextSize(16);
                    tv.setTextColor(0xFF000000);
                    tv.setBackgroundColor(0xAAFFFFFF);
                    toast.setView(tv);
                    toast.setGravity(Gravity.CENTER, 0, 400);
                    toast.show();
                  //  log("刷新成功，当前状态: " + statusText);
                } catch (Throwable e) {
                    // 兜底方案
                    Boolean currentStatus = installStatusMap.get(currentTargetApp);
                    String statusText = currentStatus != null ? 
                        (currentStatus ? "已安装" : "未安装") : "未知";
                    Toast.makeText(activity, "刷新完成\n当前状态: " + statusText, 
                        Toast.LENGTH_SHORT).show();
                }
            }
        });
    } catch (Throwable t) {
      log("刷新应用异常: " + t.getMessage());
      activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            Toast.makeText(activity, "刷新失败", Toast.LENGTH_SHORT).show();
          }
        }
      );
    }
  }

  // 更新悬浮窗显示
  private void updateFloatingView(final Activity activity) {
    try {
      activity.runOnUiThread(
        new Runnable() {
          @Override
          public void run() {
            try {
              View existingView = activity
                .getWindow()
                .getDecorView()
                .findViewWithTag("install_fake_floating");
              if (existingView instanceof TextView) {
                TextView floatingView = (TextView) existingView;
                // 1. 获取安装状态和拦截状态
                Boolean currentStatus = installStatusMap.get(currentTargetApp);
                boolean status = currentStatus != null ? currentStatus : true;
                String statusText = status ? "已安装" : "未安装";
                boolean isBlockingExit = blockExitMap.getOrDefault(
                  currentTargetApp,
                  false
                );
                String blockText = isBlockingExit ? "[拦截]" : "";

                // 2. 更新文本和背景色
                floatingView.setText(
                  "伪造安装(" + statusText + ")" + blockText
                );
                int bgColor = status ? 0xAA4CAF50 : 0xAAF44336;
                floatingView.setBackgroundColor(bgColor);

                // 3. 核心修复：同步恢复圆角背景
                try {
                  Class<?> gradientDrawableClass = Class.forName(
                    "android.graphics.drawable.GradientDrawable"
                  );
                  Object gradientDrawable = gradientDrawableClass.newInstance();
                  // 设置背景色（与上面一致）
                  Method setColorMethod = gradientDrawableClass.getMethod(
                    "setColor",
                    int.class
                  );
                  setColorMethod.invoke(gradientDrawable, bgColor);
                  // 设置圆角半径（25f与创建时一致）
                  Method setCornerRadiusMethod =
                    gradientDrawableClass.getMethod(
                      "setCornerRadius",
                      float.class
                    );
                  setCornerRadiusMethod.invoke(gradientDrawable, 25f);
                  // 应用背景
                  floatingView.setBackground(
                    (android.graphics.drawable.Drawable) gradientDrawable
                  );
                } catch (Throwable e) {
                  log("更新圆角背景失败: " + e.getMessage());
                  // 兜底：即使圆角设置失败，也不影响核心功能
                  floatingView.setBackgroundColor(bgColor);
                }
              }
            } catch (Throwable t) {
              log("更新悬浮窗异常: " + t.getMessage());
            }
          }
        }
      );
    } catch (Throwable t) {
      log("更新悬浮窗显示异常: " + t.getMessage());
    }
  }

  // 全局阻断所有间接调用 System.exit() 的场景
  private void hookIndirectExitMethods(ClassLoader classLoader) {
    try {
      // 1. Hook 反射调用 System.exit()
      XposedHelpers.findAndHookMethod(
        "java.lang.reflect.Method",
        classLoader,
        "invoke",
        Object.class,
        Object[].class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Method method = (Method) param.thisObject;
            if (
              method.getDeclaringClass().getName().equals("java.lang.System") &&
              method.getName().equals("exit")
            ) {
              param.setResult(null);
              param.setThrowable(null);
              log("✅ 拦截反射exit退出");
              handleAppExit("反射调用 System.exit()", param);
            }
          }
        }
      );

      // 2. Hook Runtime.exit()
      XposedHelpers.findAndHookMethod(
        "java.lang.Runtime",
        classLoader,
        "exit",
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            param.setResult(null);
            param.setThrowable(null);
            //    log("✅ 阻断 Runtime.exit()");
            handleAppExit("Runtime.exit()", param);
          }
        }
      );

      // 3. Hook 可能触发退出的JNI相关方法
      XposedHelpers.findAndHookMethod(
        "java.lang.System",
        classLoader,
        "loadLibrary",
        String.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
            String libName = (String) param.args[0];
            if (
              libName.contains("native") ||
              libName.contains("exit") ||
              libName.contains("kill")
            ) {
              //         log("⚠️  检测到可能触发退出的Native库加载: " + libName);
            }
          }
        }
      );
      //   log("✅ 成功Hook所有间接退出方法");
    } catch (Throwable t) {
      log("Hook间接退出方法失败: " + t.getMessage());
    }
  }

  // 通用退出源头拦截
  private void hookGlobalExitSources(ClassLoader classLoader) {
    try {
      // 拦截退出按钮点击
      XposedHelpers.findAndHookMethod(
        "android.view.View",
        classLoader,
        "performClick",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(
            XC_MethodHook.MethodHookParam param
          ) throws Throwable {
            View view = (View) param.thisObject;
            Context context = view.getContext();
            if (context instanceof Activity) {
              Activity activity = (Activity) context;
              DetectedPackages detected = analyzeDetectedPackages();
              if (
                !detected.installedPackages.isEmpty() ||
                !detected.notInstalledPackages.isEmpty()
              ) {
                String viewText = "";
                if (view instanceof TextView) {
                  CharSequence charSeq = ((TextView) view).getText();
                  viewText = charSeq != null
                    ? charSeq.toString().toLowerCase()
                    : "";
                }
                if (
                  viewText.contains("退出") ||
                  viewText.contains("关闭") ||
                  viewText.contains("exit") ||
                  viewText.contains("quit")
                ) {
                  param.setResult(false);
                  //         log("✅ 通用拦截：退出按钮点击");
                  showSilentToast(activity, "已拦截退出");
                }
              }
            }
          }
        }
      );

      // 场景2：拦截带退出关键词的 Handler.post
      XposedHelpers.findAndHookMethod(
        "android.os.Handler",
        classLoader,
        "post",
        Runnable.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(
            XC_MethodHook.MethodHookParam param
          ) throws Throwable {
            Runnable runnable = (Runnable) param.args[0];
            String runnableStr = runnable.toString().toLowerCase();
            if (
              runnableStr.contains("exit") ||
              runnableStr.contains("kill") ||
              runnableStr.contains("finish")
            ) {
              DetectedPackages detected = analyzeDetectedPackages();
              if (
                !detected.installedPackages.isEmpty() ||
                !detected.notInstalledPackages.isEmpty()
              ) {
                param.setResult(false);
                //              log("✅ 通用拦截：退出逻辑 Runnable");
              }
            }
          }
        }
      );

      // 场景3：拦截Activity启动后直接退出
      XposedHelpers.findAndHookMethod(
        "android.app.Activity",
        classLoader,
        "onResume",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(
            XC_MethodHook.MethodHookParam param
          ) throws Throwable {
            Activity activity = (Activity) param.thisObject;
            ActivityManager am = (ActivityManager) activity.getSystemService(
              Context.ACTIVITY_SERVICE
            );
            if (am != null) {
              List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(
                1
              );
              if (
                !tasks.isEmpty() &&
                tasks
                  .get(0)
                  .topActivity.getPackageName()
                  .equals(currentTargetApp)
              ) {
                DetectedPackages detected = analyzeDetectedPackages();
                if (
                  !detected.installedPackages.isEmpty() ||
                  !detected.notInstalledPackages.isEmpty()
                ) {
                  //               log("✅ 通用拦截：Activity 启动后直接退出");
                }
              }
            }
          }
        }
      );

      // 场景4：拦截可疑检测线程
      XposedHelpers.findAndHookMethod(
        "java.lang.Thread",
        classLoader,
        "run",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(
            XC_MethodHook.MethodHookParam param
          ) throws Throwable {
            Thread thread = (Thread) param.thisObject;
            String threadName = thread.getName().toLowerCase();
            if (
              threadName.contains("check") ||
              threadName.contains("detect") ||
              threadName.contains("exit")
            ) {
              DetectedPackages detected = analyzeDetectedPackages();
              if (
                !detected.installedPackages.isEmpty() ||
                !detected.notInstalledPackages.isEmpty()
              ) {
                //            log("⚠️  检测到可疑检测线程：" + threadName);
              }
            }
          }
        }
      );

      // 场景5：拦截Runnable内部的 System.exit()
      hookRunnableSystemExit(classLoader);
      //   log("✅ 通用退出源头拦截初始化完成");
    } catch (Throwable t) {
      log("退出拦截初始化失败: " + t.getMessage());
    }
  }

  // 拦截所有 Runnable 内部调用的 System.exit()
  private void hookRunnableSystemExit(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "java.lang.Runnable",
        classLoader,
        "run",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(
            final XC_MethodHook.MethodHookParam param
          ) throws Throwable {
            // 检测当前应用是否有伪造包（有则开启拦截）
            DetectedPackages detected = analyzeDetectedPackages();
            if (
              detected.installedPackages.isEmpty() &&
              detected.notInstalledPackages.isEmpty()
            ) {
              return; // 无伪造包，放行
            }

            // 核心：临时Hook System.exit()，阻断Runnable内部调用
            XC_MethodHook exitHook = new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(
                XC_MethodHook.MethodHookParam exitParam
              ) throws Throwable {
                // 彻底阻断 System.exit()
                exitParam.setResult(null);
                exitParam.setThrowable(null);
                //     log("✅ 拦截异步Runnable内部的 System.exit()");
                // 显示提示
                new Handler(Looper.getMainLooper()).post(
                  new Runnable() {
                    @Override
                    public void run() {
                      Activity activity = getCurrentActivity();
                      if (activity != null) {
                        showSilentToast(activity, "已拦截异步退出");
                      }
                    }
                  }
                );
              }
            };

            // 临时Hook System.exit() 方法
            Method exitMethod = Class.forName("java.lang.System").getMethod(
              "exit",
              int.class
            );
            XposedBridge.hookMethod(exitMethod, exitHook);

            // 核心兼容：用反射执行原Runnable的 run() 方法，避开Xposed API
            try {
              Runnable originalRunnable = (Runnable) param.thisObject;
              originalRunnable.run(); // 直接执行原逻辑，不依赖Xposed API
            } catch (Throwable t) {
              log("执行原Runnable异常: " + t.getMessage());
            } finally {
              // 卸载临时Hook，避免内存泄漏
              XposedBridge.unhookMethod(exitMethod, exitHook);
            }

            // 阻断原方法后续执行，避免重复调用
            param.setResult(null);
          }
        }
      );
      //      log("✅ 异步Runnable内部退出拦截初始化完成");
    } catch (Throwable t) {
      // log("Hook 内部exit失败: " + t.getMessage());
    }
  }

  // 静默提示（不依赖弹窗，兼容系统强制退出场景）
  private void showSilentToast(Activity activity, String message) {
    try {
      if (activity == null || activity.isFinishing()) {
        return;
      }
      // 使用系统 Toast，避免被应用内弹窗覆盖
      Toast toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT);
      toast.setGravity(Gravity.CENTER, 0, 0);
      toast.show();
    } catch (Throwable t) {
      // 兜底：使用系统日志提示
      log("⚠️  提示失败：" + message);
    }
  }

  // ========== 权限伪造核心方法 ==========
  private void hookQueryAllPackagesPermission(ClassLoader classLoader) {
    try {
      // 1. 拦截普通权限检查
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "checkPermission",
        String.class, // 权限名
        String.class, // 应用包名
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Boolean shouldFakePermission = permissionFakeMap.get(
              currentTargetApp
            );
            boolean fakeEnabled = shouldFakePermission != null
              ? shouldFakePermission
              : true;
            if (!fakeEnabled) return;

            String permission = (String) param.args[0];
            String targetPackage = (String) param.args[1];
            if (
              targetPackage != null &&
              targetPackage.equals(currentTargetApp) &&
              Arrays.asList(DETECTION_PERMISSIONS).contains(permission)
            ) {
              param.setResult(PackageManager.PERMISSION_GRANTED);
              return;
            }
          }
        }
      );

      // 2. 拦截Android 13+ 预检测权限（checkPermissionForPreflight）
      try {
        XposedHelpers.findAndHookMethod(
          "android.app.ApplicationPackageManager",
          classLoader,
          "checkPermissionForPreflight",
          String.class,
          String.class,
          int.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (!fakeEnabled) return;

              String permission = (String) param.args[0];
              String targetPackage = (String) param.args[1];
              if (
                targetPackage != null &&
                targetPackage.equals(currentTargetApp) &&
                Arrays.asList(DETECTION_PERMISSIONS).contains(permission)
              ) {
                param.setResult(PackageManager.PERMISSION_GRANTED);
              }
            }
          }
        );
      } catch (NoSuchMethodError e) {
        // Android 13以下无此方法，忽略
      }

      // 3. 拦截 ContextWrapper.checkSelfPermission（应用自查权限）
      try {
        XposedHelpers.findAndHookMethod(
          "android.content.ContextWrapper",
          classLoader,
          "checkSelfPermission",
          String.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (!fakeEnabled) return;

              String permission = (String) param.args[0];
              if (Arrays.asList(DETECTION_PERMISSIONS).contains(permission)) {
                param.setResult(PackageManager.PERMISSION_GRANTED);
              }
            }
          }
        );
      } catch (Throwable t) {
        // 兜底：Hook ContextImpl.checkSelfPermission
        try {
          XposedHelpers.findAndHookMethod(
            "android.app.ContextImpl",
            classLoader,
            "checkSelfPermission",
            String.class,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
                Boolean shouldFakePermission = permissionFakeMap.get(
                  currentTargetApp
                );
                boolean fakeEnabled = shouldFakePermission != null
                  ? shouldFakePermission
                  : true;
                if (!fakeEnabled) return;

                String permission = (String) param.args[0];
                if (Arrays.asList(DETECTION_PERMISSIONS).contains(permission)) {
                  param.setResult(PackageManager.PERMISSION_GRANTED);
                }
              }
            }
          );
        } catch (Throwable e) {
          log("Hook ContextImpl.checkSelfPermission失败: " + e.getMessage());
        }
      }

      // 4. Hook PackageManager.checkPermission 所有重载版本（兜底）
      try {
        Class<?> packageManagerClass = XposedHelpers.findClass(
          "android.app.ApplicationPackageManager",
          classLoader
        );
        XposedBridge.hookAllMethods(
          packageManagerClass,
          "checkPermission",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (!fakeEnabled) return;

              String permission = null;
              String targetPackage = null;
              for (Object arg : param.args) {
                if (arg instanceof String) {
                  if (
                    permission == null &&
                    Arrays.asList(DETECTION_PERMISSIONS).contains(arg)
                  ) {
                    permission = (String) arg;
                  } else if (
                    targetPackage == null &&
                    !Arrays.asList(DETECTION_PERMISSIONS).contains(arg)
                  ) {
                    targetPackage = (String) arg;
                  }
                }
              }
              if (
                permission != null &&
                targetPackage != null &&
                targetPackage.equals(currentTargetApp)
              ) {
                param.setResult(PackageManager.PERMISSION_GRANTED);
              }
            }
          }
        );
      } catch (Throwable t) {
        log("Hook PackageManager.checkPermission重载失败: " + t.getMessage());
      }

      // 5. 拦截 AndroidX PackageManagerCompat（兼容库）
      try {
        Class<?> packageManagerCompatClass = XposedHelpers.findClassIfExists(
          "androidx.core.content.PackageManagerCompat",
          classLoader
        );
        if (packageManagerCompatClass != null) {
          XposedBridge.hookAllMethods(
            packageManagerCompatClass,
            "checkPermission",
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
                Boolean shouldFakePermission = permissionFakeMap.get(
                  currentTargetApp
                );
                boolean fakeEnabled = shouldFakePermission != null
                  ? shouldFakePermission
                  : true;
                if (!fakeEnabled) return;

                for (Object arg : param.args) {
                  if (
                    arg instanceof String &&
                    Arrays.asList(DETECTION_PERMISSIONS).contains(arg)
                  ) {
                    param.setResult(PackageManager.PERMISSION_GRANTED);
                    break;
                  }
                }
              }
            }
          );
        }
      } catch (Throwable t) {
        // 忽略无AndroidX的情况
      }

      // 6. 拦截 ActivityManager.checkPermission（系统服务级查询）
      try {
        Class<?> amClass = XposedHelpers.findClass(
          "android.app.ActivityManager",
          classLoader
        );
        // 先判断方法是否存在
        Method checkPermMethod = null;
        try {
          checkPermMethod = amClass.getDeclaredMethod(
            "checkPermission",
            String.class,
            int.class,
            int.class
          );
        } catch (NoSuchMethodException e) {
          //    log("ActivityManager无checkPermission方法，跳过Hook");
          return;
        }
        // 方法存在时才Hook
        XposedBridge.hookMethod(
          checkPermMethod,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (!fakeEnabled) return;

              String permission = (String) param.args[0];
              if (Arrays.asList(DETECTION_PERMISSIONS).contains(permission)) {
                param.setResult(PackageManager.PERMISSION_GRANTED);
              }
            }
          }
        );
      } catch (Throwable t) {
        log("Hook ActivityManager.checkPermission失败: " + t.getMessage());
      }

      // 7. 拦截 PackageManager.getPermissionInfo（权限信息查询）
      try {
        XposedHelpers.findAndHookMethod(
          "android.app.ApplicationPackageManager",
          classLoader,
          "getPermissionInfo",
          String.class,
          int.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (!fakeEnabled) return;

              String permissionName = (String) param.args[0];
              if (
                Arrays.asList(DETECTION_PERMISSIONS).contains(permissionName)
              ) {
                try {
                  Class<?> permissionInfoClass = Class.forName(
                    "android.content.pm.PermissionInfo"
                  );
                  Object fakePermissionInfo = permissionInfoClass.newInstance();
                  XposedHelpers.setObjectField(
                    fakePermissionInfo,
                    "name",
                    permissionName
                  );
                  XposedHelpers.setObjectField(
                    fakePermissionInfo,
                    "packageName",
                    currentTargetApp
                  );
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
                  log("伪造PermissionInfo失败: " + e.getMessage());
                }
              }
            }
          }
        );
      } catch (Throwable t) {
        log("Hook getPermissionInfo失败: " + t.getMessage());
      }
      //   log("✅ 所有检测权限Hook初始化完成（支持即时生效）");
    } catch (Throwable t) {
      log("❌ Hook检测权限失败: " + t.getMessage());
      // 基础兜底Hook
      try {
        hookBasicPermissionChecks(classLoader);
      } catch (Throwable t2) {
        log("基础权限兜底Hook失败: " + t2.getMessage());
      }
    }
  }

  // ========== 基础权限检查Hook ==========
  private void hookBasicPermissionChecks(ClassLoader classLoader) {
    try {
      // 最基本的权限检查Hook
      XposedHelpers.findAndHookMethod(
        "android.app.ContextImpl",
        classLoader,
        "checkPermission",
        String.class,
        int.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            // 检查权限伪造配置
            Boolean shouldFakePermission = permissionFakeMap.get(
              currentTargetApp
            );
            boolean fakePermissionEnabled = shouldFakePermission != null
              ? shouldFakePermission
              : true;

            if (!fakePermissionEnabled) {
              return;
            }

            String permission = (String) param.args[0];
            if (Arrays.asList(DETECTION_PERMISSIONS).contains(permission)) {
              param.setResult(PackageManager.PERMISSION_GRANTED);
              // log("【兜底权限伪造】ContextImpl.checkPermission -> 授予权限: " +
              // permission);
            }
          }
        }
      );
      // log("✅ 基础权限Hook成功（根据配置）");
    } catch (Throwable t) {
      // 忽略错误
    }
  }

  // ==========  PACKAGE_USAGE_STATS 权限伪造 ==========
  private void hookPackageUsageStatsPermission(ClassLoader classLoader) {
    try {
      // 检查权限伪造配置
      Boolean shouldFakePermission = permissionFakeMap.get(currentTargetApp);
      boolean fakePermissionEnabled = shouldFakePermission != null
        ? shouldFakePermission
        : true;

      if (!fakePermissionEnabled) {
        // log("【权限伪造】PACKAGE_USAGE_STATS 权限伪造已关闭，跳过Hook");
        return;
      }

      // 1. 拦截queryUsageStats方法
      XposedHelpers.findAndHookMethod(
        "android.app.usage.UsageStatsManager",
        classLoader,
        "queryUsageStats",
        int.class,
        long.class,
        long.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            // 再次检查权限伪造状态
            Boolean shouldFake = permissionFakeMap.get(currentTargetApp);
            if (shouldFake != null && !shouldFake) {
              return; // 如果已关闭，不伪造
            }

            List<Object> fakeList = new ArrayList<>();
            synchronized (globalCapturedPackages) {
              for (String pkg : globalCapturedPackages) {
                try {
                  Class<?> usageStatsClass = Class.forName(
                    "android.app.usage.UsageStats"
                  );
                  Object stats = usageStatsClass.newInstance();
                  XposedHelpers.setObjectField(stats, "mPackageName", pkg);
                  XposedHelpers.setLongField(
                    stats,
                    "mTotalTimeInForeground",
                    3600000L
                  );
                  fakeList.add(stats);
                } catch (Exception e) {
                  // 忽略创建失败的包
                }
              }
            }
            param.setResult(fakeList);
          }
        }
      );

      // 2. 拦截getUsageStatsForPackage方法
      XposedHelpers.findAndHookMethod(
        "android.app.usage.UsageStatsManager",
        classLoader,
        "getUsageStatsForPackage",
        String.class,
        long.class,
        long.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            // 再次检查权限伪造状态
            Boolean shouldFake = permissionFakeMap.get(currentTargetApp);
            if (shouldFake != null && !shouldFake) {
              return; // 如果已关闭，不伪造
            }

            String pkg = (String) param.args[0];
            List<Object> fakeList = new ArrayList<>();

            synchronized (globalCapturedPackages) {
              if (globalCapturedPackages.contains(pkg)) {
                try {
                  Class<?> usageStatsClass = Class.forName(
                    "android.app.usage.UsageStats"
                  );
                  Object stats = usageStatsClass.newInstance();
                  XposedHelpers.setObjectField(stats, "mPackageName", pkg);
                  XposedHelpers.setLongField(
                    stats,
                    "mTotalTimeInForeground",
                    3600000L
                  );
                  fakeList.add(stats);
                } catch (Exception e) {
                  // 忽略创建失败
                }
              }
            }
            param.setResult(fakeList);
          }
        }
      );
      // log("✅ PACKAGE_USAGE_STATS权限Hook完成");
    } catch (Throwable t) {
      // 忽略错误
    }
  }

  // ========== 弹窗取消Hook方法 ==========
  private void hookDialogCancelableMethods(ClassLoader classLoader) {
    try {
      // 1. Hook Dialog.setCancelable() 方法
      XposedHelpers.findAndHookMethod(
        "android.app.Dialog",
        classLoader,
        "setCancelable",
        boolean.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              boolean originalCancelable = param.args[0];

              // 获取当前Activity上下文
              Context context = null;
              try {
                context = (Context) XposedHelpers.getObjectField(
                  param.thisObject,
                  "mContext"
                );
              } catch (Throwable e) {
                try {
                  context = (Context) XposedHelpers.getObjectField(
                    param.thisObject,
                    "context"
                  );
                } catch (Throwable e2) {
                  // 尝试其他字段名
                  try {
                    context = (Context) XposedHelpers.callMethod(
                      param.thisObject,
                      "getContext"
                    );
                  } catch (Throwable e3) {
                    log("无法获取Dialog上下文");
                  }
                }
              }

              // 检查是否为当前目标应用
              if (context != null) {
                String packageName = context.getPackageName();
                if (packageName.equals(currentTargetApp)) {
                  // 强制设置为可取消
                  param.args[0] = true;
                  //   log("✅ 强制设置Dialog可取消，原状态: " + originalCancelable);

                  // 可选：记录哪个Activity或类调用了此方法
                  String dialogClassName = param.thisObject
                    .getClass()
                    .getName();
                  String callerStackTrace = getSimpleStackTrace(10);
                  //    log("Dialog类: " + dialogClassName + "，调用栈: " +
                  // callerStackTrace);
                }
              }
            } catch (Throwable t) {
              log("Hook Dialog异常: " + t.getMessage());
            }
          }
        }
      );

      // 2. Hook Dialog.setCanceledOnTouchOutside() 方法
      XposedHelpers.findAndHookMethod(
        "android.app.Dialog",
        classLoader,
        "setCanceledOnTouchOutside",
        boolean.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              boolean originalCanceledOnTouchOutside = (boolean) param.args[0];

              // 强制允许外部点击取消
              param.args[0] = true;
              //   log("✅ 强制设置Dialog可外部点击取消，原状态: " +
              // originalCanceledOnTouchOutside);
            } catch (Throwable t) {
              log("Hook Dialog异常: " + t.getMessage());
            }
          }
        }
      );

      // 3. Hook AlertDialog.Builder.setCancelable() 方法
      XposedHelpers.findAndHookMethod(
        "android.app.AlertDialog$Builder",
        classLoader,
        "setCancelable",
        boolean.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              boolean originalCancelable = (boolean) param.args[0];

              // 强制设置为可取消
              param.args[0] = true;
              //   log("✅ 强制设置AlertDialog.Builder可取消，原状态: " + originalCancelable);
            } catch (Throwable t) {
              log("Hook AlertDialog异常: " + t.getMessage());
            }
          }
        }
      );

      // 4. Hook Dialog的show()方法
      XposedHelpers.findAndHookMethod(
        "android.app.Dialog",
        classLoader,
        "show",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Object dialog = param.thisObject;
              // 设置可取消
              XposedHelpers.callMethod(dialog, "setCancelable", true);
              // 设置可外部点击取消
              XposedHelpers.callMethod(
                dialog,
                "setCanceledOnTouchOutside",
                true
              );
              //  log("✅ 确保Dialog显示时可取消");
            } catch (Throwable t) {
              log("Hook Dialog.show异常: " + t.getMessage());
            }
          }
        }
      );

      // 5. Hook AlertDialog的create()方法
      XposedHelpers.findAndHookMethod(
        "android.app.AlertDialog$Builder",
        classLoader,
        "create",
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Object dialog = param.getResult();
              if (dialog != null) {
                // 确保创建的Dialog可取消
                XposedHelpers.callMethod(dialog, "setCancelable", true);
                XposedHelpers.callMethod(
                  dialog,
                  "setCanceledOnTouchOutside",
                  true
                );
                //    log("✅ 确保AlertDialog.create()创建的Dialog可取消");
              }
            } catch (Throwable t) {
              log("Hook AlertDialog异常: " + t.getMessage());
            }
          }
        }
      );

      // 6. Hook ProgressDialog
      try {
        XposedHelpers.findAndHookMethod(
          "android.app.ProgressDialog",
          classLoader,
          "setCancelable",
          boolean.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              param.args[0] = true;
              //   log("✅ 强制设置ProgressDialog可取消");
            }
          }
        );
      } catch (Throwable t) {
        // ProgressDialog可能在较高API版本中不存在
      }

      // 7. Hook DialogFragment
      hookDialogFragmentMethods(classLoader);
      // 8. Hook AndroidX DialogFragment
      hookAndroidXDialogFragmentMethods(classLoader);
      //  log("✅ 弹窗取消Hook完成");

    } catch (Throwable t) {
      log("❌ Hook弹窗取消方法失败: " + t.getMessage());
    }
  }

  // ========== DialogFragment Hook方法 ==========
  private void hookDialogFragmentMethods(ClassLoader classLoader) {
    try {
      Class<?> dialogFragmentClass = Class.forName(
        "android.app.DialogFragment",
        false,
        classLoader
      );

      // Hook DialogFragment的onCreateDialog方法
      XposedHelpers.findAndHookMethod(
        dialogFragmentClass,
        "onCreateDialog",
        Bundle.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Object dialog = param.getResult();
              if (dialog != null) {
                // 设置Dialog可取消
                XposedHelpers.callMethod(dialog, "setCancelable", true);
                XposedHelpers.callMethod(
                  dialog,
                  "setCanceledOnTouchOutside",
                  true
                );

                // 设置DialogFragment本身可取消
                Object dialogFragment = param.thisObject;
                XposedHelpers.callMethod(dialogFragment, "setCancelable", true);
                //    log("✅ 设置DialogFragment创建的Dialog可取消");
              }
            } catch (Throwable t) {
              //    log("Hook DialogFragment.onCreateDialog异常: " +
              // t.getMessage());
            }
          }
        }
      );

      // Hook DialogFragment的setCancelable方法
      XposedHelpers.findAndHookMethod(
        dialogFragmentClass,
        "setCancelable",
        boolean.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            // 强制设置为可取消
            param.args[0] = true;
            //     log("✅ 强制设置DialogFragment可取消");
          }
        }
      );
    } catch (ClassNotFoundException e) {
      // DialogFragment类不存在，跳过
    } catch (Throwable t) {
      //    log("Hook DialogFragment方法失败: " + t.getMessage());
    }
  }

  // ========== AndroidX DialogFragment Hook方法 ==========
  private void hookAndroidXDialogFragmentMethods(ClassLoader classLoader) {
    try {
      Class<?> dialogFragmentClass = Class.forName(
        "androidx.fragment.app.DialogFragment",
        false,
        classLoader
      );

      // Hook AndroidX DialogFragment的onCreateDialog方法
      XposedHelpers.findAndHookMethod(
        dialogFragmentClass,
        "onCreateDialog",
        Bundle.class,
        new XC_MethodHook() {
          @Override
          protected void afterHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Object dialog = param.getResult();
              if (dialog != null) {
                // 设置Dialog可取消
                XposedHelpers.callMethod(dialog, "setCancelable", true);
                XposedHelpers.callMethod(
                  dialog,
                  "setCanceledOnTouchOutside",
                  true
                );

                // 设置DialogFragment本身可取消
                Object dialogFragment = param.thisObject;
                XposedHelpers.callMethod(dialogFragment, "setCancelable", true);
                //    log("✅ 设置AndroidX DialogFragment创建的Dialog可取消");
              }
            } catch (Throwable t) {
              log("Hook AndroidX 异常: " + t.getMessage());
            }
          }
        }
      );

      // Hook AndroidX DialogFragment的setCancelable方法
      XposedHelpers.findAndHookMethod(
        dialogFragmentClass,
        "setCancelable",
        boolean.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            // 强制设置为可取消
            param.args[0] = true;
            // log("✅ 强制设置AndroidX DialogFragment可取消");
          }
        }
      );
    } catch (ClassNotFoundException e) {
      // AndroidX DialogFragment类不存在，跳过
    } catch (Throwable t) {
      log("Hook AndroidX Dialog方法失败: " + t.getMessage());
    }
  }

  // ========== 获取调用栈方法 ==========
  private String getSimpleStackTrace(int maxDepth) {
    try {
      StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
      StringBuilder sb = new StringBuilder();

      int startIndex = Math.min(3, stackTrace.length); // 跳过前几个内部方法
      int endIndex = Math.min(startIndex + maxDepth, stackTrace.length);

      for (int i = startIndex; i < endIndex; i++) {
        StackTraceElement element = stackTrace[i];
        String className = element.getClassName();
        String methodName = element.getMethodName();
        int lineNumber = element.getLineNumber();

        // 简化类名
        if (className.contains(".")) {
          className = className.substring(className.lastIndexOf('.') + 1);
        }

        sb
          .append(className)
          .append(".")
          .append(methodName)
          .append(":")
          .append(lineNumber);

        if (i < endIndex - 1) {
          sb.append(" ← ");
        }
      }

      return sb.toString();
    } catch (Throwable t) {
      return "无法获取调用栈";
    }
  }

  // ========== 核心Hook方法 ==========
  private void hookKeyMethods(ClassLoader classLoader) {
    try {
    
     hookGetPackageInfo(classLoader);
      hookGetApplicationInfo(classLoader);
      hookGetInstalledPackages(classLoader);
      hookGetInstalledApplications(classLoader);
      
    } catch (Throwable t) {
      log("Hook关键方法失败: " + t);
    }
  }

  // ========== 智能伪造核心方法==========
  // 1. 记录查询模式
  private void recordQueryPattern(String packageName, int flags) {
    try {
      queryPatterns.add(new QueryPattern(currentTargetApp, packageName, flags));
      // 保持最近500条记录
      if (queryPatterns.size() > 500) {
        queryPatterns.remove(0);
      }
    } catch (Throwable t) {
      // 静默失败
    }
  }

  // 2. 安全设置installerPackageName（兼容不同Android版本）
  private void setInstallerPackageNameSafe(PackageInfo pi, String installer) {
    try {
      // 只使用XposedHelpers
      XposedHelpers.setObjectField(pi, "installerPackageName", installer);
    } catch (Throwable t) {
      // 如果失败，尝试在ApplicationInfo.metaData中存储
      try {
        if (pi.applicationInfo != null && installer != null) {
          if (pi.applicationInfo.metaData == null) {
            pi.applicationInfo.metaData = new Bundle();
          }
          pi.applicationInfo.metaData.putString("installer_source", installer);
        }
      } catch (Throwable t2) {
        // 完全失败，记录日志
        // log("⚠️ 无法设置installerPackageName，Android版本可能过低: " + Build.VERSION.SDK_INT);
      }
    }
  }

  // 3. 智能版本号生成器
  private String generateSmartVersion(String packageName) {
    if (versionCache.containsKey(packageName)) {
      return versionCache.get(packageName);
    }

    if (packageName == null || packageName.isEmpty()) {
      String defaultVersion = "1.0.0";
      versionCache.put("", defaultVersion);
      return defaultVersion;
    }

    // 基于包名hash生成确定性版本
    int hash = Math.abs(packageName.hashCode());
    String version;

    // 根据包名长度决定版本格式
    int nameLength = packageName.length();

    if (nameLength < 15) {
      // 短包名：简单版本 x.x
      int major = (hash % 5) + 1; // 1-5
      int minor = (hash / 7) % 20; // 0-19
      version = String.format("%d.%d", major, minor);
    } else if (nameLength < 25) {
      // 中等包名：标准版本 x.x.x
      int major = (hash % 8) + 1; // 1-8
      int minor = (hash / 13) % 30; // 0-29
      int patch = (hash / 29) % 100; // 0-99
      version = String.format("%d.%d.%d", major, minor, patch);
    } else {
      // 长包名：详细版本 x.x.x.x
      int major = (hash % 4) + 1; // 1-4
      int minor = (hash / 17) % 10; // 0-9
      int patch = (hash / 31) % 50; // 0-49
      int build = (hash / 53) % 10; // 0-9
      version = String.format("%d.%d.%d.%d", major, minor, patch, build);
    }

    versionCache.put(packageName, version);
    return version;
  }

  // 4. 智能版本码生成器
  private int generateSmartVersionCode(String packageName, String versionName) {
    if (versionCodeCache.containsKey(packageName)) {
      return versionCodeCache.get(packageName);
    }

    int versionCode;
    int hash = Math.abs(packageName.hashCode());

    try {
      // 尝试从版本号提取数字
      String cleanVersion = versionName.replaceAll("[^0-9.]", "");
      String[] parts = cleanVersion.split("\\.");

      if (parts.length >= 2) {
        // 从版本号生成：例如 2.1.3 -> 2013
        int code = 0;
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
          try {
            int num = Integer.parseInt(parts[i]) % 100;
            code = code * 100 + num;
          } catch (NumberFormatException e) {
            code = code * 100 + ((hash >> (i * 8)) % 100);
          }
        }
        // 初始内部版本号1000起。改为1起
        versionCode = Math.max(Math.abs(code), 1);
      } else {
        // 生成随机版本码 1000-9999 改为1-9000
        versionCode = 1 + (hash % 9000);
      }
    } catch (Exception e) {
      versionCode = 1 + random.nextInt(9000);
    }

    versionCodeCache.put(packageName, versionCode);
    return versionCode;
  }

  // 5. 智能安装时间生成器
  private long generateSmartInstallTime(String packageName) {
    if (installTimeCache.containsKey(packageName)) {
      return installTimeCache.get(packageName);
    }

    int hash = Math.abs(packageName.hashCode());

    // 安装时间：3个月到2年前改为9天-72天
    long twoYearsAgo =
      System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 36 * 2);
    long threeMonthsAgo =
      System.currentTimeMillis() - (1000L * 60 * 60 * 24 * 3 * 3);

    // 使用包名hash确定时间
    long timeRange = twoYearsAgo - threeMonthsAgo;
    long timeOffset = hash % timeRange;
    long installTime = twoYearsAgo - timeOffset;

    installTimeCache.put(packageName, installTime);
    return installTime;
  }

  // 6. 智能更新时间生成器
  private long generateSmartUpdateTime(String packageName, long installTime) {
    int hash = Math.abs(packageName.hashCode());

    // 更新时间在安装后7-180天
    long daysAfter = 7 + (hash % 174); // 7-180天
    return installTime + (1000L * 60 * 60 * 24 * daysAfter);
  }

  // 7. 智能安装来源生成器
  private String generateSmartInstaller(String packageName) {
    if (installerCache.containsKey(packageName)) {
      return installerCache.get(packageName);
    }

    if (packageName == null) {
      return null;
    }

    int hash = Math.abs(packageName.hashCode());
    String installer;

    switch (hash % 11) { // 0-10共11种可能
      case 0:
        installer = "com.android.vending";
        break; // Google Play
      case 1:
        installer = "com.tencent.android.qqdownloader";
        break; // 应用宝
      case 2:
        installer = "com.xiaomi.market";
        break; // 小米商店
      case 3:
        installer = "com.huawei.appmarket";
        break; // 华为商店
      case 4:
        installer = "com.oppo.market";
        break; // OPPO商店
      case 5:
        installer = "com.vivo.appstore";
        break; // VIVO商店
      case 6:
        installer = "com.baidu.appsearch";
        break; // 百度
      case 7:
        installer = "com.wandoujia.phoenix2";
        break; // 豌豆荚
      case 8:
        installer = "com.meizu.mstore";
        break; // 魅族
      case 9:
        installer = "com.samsung.android.app.smartswitch";
        break; // 三星
      case 10:
        installer = null;
        break; // 浏览器/侧载
      default:
        installer = null;
      //无来源
    }

    installerCache.put(packageName, installer);
    return installer;
  }

  // 8. 智能应用名称生成器
  private String generateSmartAppName(String packageName) {
    if (packageName == null || packageName.length() < 2) {
      return "伪造App";
    }

    // 缓存检查
    if (appNameCache.containsKey(packageName)) {
      return appNameCache.get(packageName);
    }

    // 算法：从包名提取有意义的名称
    String appName;

    // 1. 尝试提取最后一个有意义的部分
    String[] parts = packageName.split("\\.");
    String candidate = "";

    for (int i = parts.length - 1; i >= 0; i--) {
      if (
        parts[i].length() > 2 &&
        !parts[i].matches("com|org|net|io|co|app|android|mobile|plus")
      ) {
        candidate = parts[i];
        break;
      }
    }

    // 2. 如果没找到有意义的部分，用最后一个
    if (candidate.isEmpty() && parts.length > 0) {
      candidate = parts[parts.length - 1];
    }

    // 3. 转换格式
    if (candidate.length() > 0) {
      StringBuilder nameBuilder = new StringBuilder();
      // 首字母大写
      nameBuilder.append(Character.toUpperCase(candidate.charAt(0)));

      // 处理后续字符
      for (int i = 1; i < candidate.length(); i++) {
        char c = candidate.charAt(i);
        char prev = candidate.charAt(i - 1);

        // 在数字前、大写字母前、下划线后添加空格
        if (
          Character.isDigit(c) &&
          !Character.isDigit(prev) &&
          prev != ' ' &&
          i > 1
        ) {
          nameBuilder.append(" ").append(c);
        }
        // 大写字母前添加空格（驼峰转换）
        else if (
          Character.isUpperCase(c) &&
          !Character.isUpperCase(prev) &&
          prev != ' ' &&
          i > 1
        ) {
          nameBuilder.append(" ").append(c);
        }
        // 下划线或连字符转换为空格
        else if (c == '_' || c == '-') {
          nameBuilder.append(" ");
        } else {
          nameBuilder.append(c);
        }
      }

      appName = nameBuilder.toString();
    } else {
      appName = "Application";
    }

    // 4. 清理多余空格
    appName = appName.trim().replaceAll("\\s+", " ");

    // 5. 确保不为空
    if (appName.isEmpty()) {
      appName = "伪造App";
    }

    appNameCache.put(packageName, appName);
    return appName;
  }

  // 9. 智能ApplicationInfo生成器
  private ApplicationInfo createSmartApplicationInfo(
    String packageName,
    int flags
  ) {
    ApplicationInfo ai = new ApplicationInfo();
    ai.packageName = packageName;

    // 应用名称
    ai.name = generateSmartAppName(packageName);

    // 基本标志
    ai.flags = ApplicationInfo.FLAG_INSTALLED;
    ai.enabled = true;
    ai.targetSdkVersion = Build.VERSION.SDK_INT;

    // 智能路径
    int suffix = (Math.abs(packageName.hashCode()) % 5) + 1;
    ai.sourceDir =
      "/data/app/" + packageName.replace('.', '-') + "-" + suffix + "/base.apk";
    ai.publicSourceDir = ai.sourceDir;
    ai.dataDir = "/data/data/" + packageName;
    ai.nativeLibraryDir = ai.dataDir + "/lib";

    // 智能UID（基于包名hash）
    ai.uid = 10000 + (Math.abs(packageName.hashCode()) % 50000);

    // 可选标志（根据flags）
    try {
      if ((flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
        ai.flags |= ApplicationInfo.FLAG_SYSTEM;
      }
      if ((flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
        ai.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
      }
    } catch (Throwable t) {
      // 忽略标志设置错误
    }

    return ai;
  }

  // 10. 解码flags（用于日志）
  private String decodeFlags(int flags) {
    List<String> needs = new ArrayList<>();

    if ((flags & PackageManager.GET_META_DATA) != 0) needs.add("版本");
    if ((flags & PackageManager.GET_ACTIVITIES) != 0) needs.add("Activities");
    if ((flags & PackageManager.GET_SERVICES) != 0) needs.add("Services");
    if ((flags & PackageManager.GET_RECEIVERS) != 0) needs.add("Receivers");
    if ((flags & PackageManager.GET_PROVIDERS) != 0) needs.add("Providers");
    if ((flags & PackageManager.GET_PERMISSIONS) != 0) needs.add("权限");
    if ((flags & PackageManager.GET_SIGNATURES) != 0) needs.add("签名");

    return needs.isEmpty() ? "基本信息" : String.join("+", needs);
  }

  // 11. 主方法：智能伪造PackageInfo
  private PackageInfo createSmartFakePackageInfo(
    String packageName,
    int flags
  ) {
    try {
      // 记录这次查询
      recordQueryPattern(packageName, flags);

      PackageInfo pi = new PackageInfo();
      pi.packageName = packageName;

      // ========== 按 flags 提供信息 ==========
      // 1. 版本信息
      pi.versionName = generateSmartVersion(packageName);
      pi.versionCode = generateSmartVersionCode(packageName, pi.versionName);

      // 2. 时间信息
      pi.firstInstallTime = generateSmartInstallTime(packageName);
      pi.lastUpdateTime = generateSmartUpdateTime(
        packageName,
        pi.firstInstallTime
      );

      // 3. 安装来源（安全设置）
      setInstallerPackageNameSafe(pi, generateSmartInstaller(packageName));

      // 4. ApplicationInfo
      pi.applicationInfo = createSmartApplicationInfo(packageName, flags);

      // 5. 其他按需字段
      if ((flags & PackageManager.GET_ACTIVITIES) != 0) {
        pi.activities = createFakeActivities(packageName);
      }

      if ((flags & PackageManager.GET_SERVICES) != 0) {
        pi.services = createFakeServices(packageName);
      }

      if ((flags & PackageManager.GET_RECEIVERS) != 0) {
        pi.receivers = createFakeReceivers(packageName);
      }

      if ((flags & PackageManager.GET_PROVIDERS) != 0) {
        pi.providers = createFakeProviders(packageName);
      }

      if ((flags & PackageManager.GET_PERMISSIONS) != 0) {
        pi.permissions = createFakePermissions(packageName);
      }

      if ((flags & PackageManager.GET_SIGNATURES) != 0) {
        pi.signatures = createFakeSignatures(packageName);
      }

      // 6. 空字段安全处理
      try {
        if (
          (flags & PackageManager.GET_CONFIGURATIONS) != 0 &&
          pi.configPreferences == null
        ) {
          pi.configPreferences = new ConfigurationInfo[0];
        }
      } catch (NoSuchFieldError e) {
        // 忽略不存在的字段
      }

      if ((flags & PackageManager.GET_GIDS) != 0 && pi.gids == null) {
        pi.gids = new int[0];
      }
      /*
            // 日志记录
            log("🔧 智能伪造: " + packageName +
                " v" + pi.versionName +
                " (" + pi.versionCode + ")" +
                " 需要字段: " + decodeFlags(flags));
            */
      return pi;
    } catch (Throwable e) {
      log("智能伪造失败: " + packageName + " - " + e.getMessage());
      // 兜底：返回简单伪造
      return createSimpleFakePackageInfo(packageName);
    }
  }

  // ========== 辅助伪造方法 ==========
  // 1. 创建伪造Activities
  private ActivityInfo[] createFakeActivities(String packageName) {
    try {
      ActivityInfo[] activities = new ActivityInfo[1];
      Object activityInfo = createFakeActivityInfo(
        packageName,
        packageName + ".MainActivity"
      );
      activities[0] = (ActivityInfo) activityInfo;
      return activities;
    } catch (Throwable e) {
      return new ActivityInfo[0];
    }
  }

  // 2. 创建伪造Services
  private android.content.pm.ServiceInfo[] createFakeServices(
    String packageName
  ) {
    try {
      Class<?> serviceInfoClass = Class.forName(
        "android.content.pm.ServiceInfo"
      );
      Object serviceInfo = serviceInfoClass.newInstance();

      XposedHelpers.setObjectField(serviceInfo, "packageName", packageName);
      XposedHelpers.setObjectField(
        serviceInfo,
        "name",
        packageName + ".MyService"
      );
      XposedHelpers.setBooleanField(serviceInfo, "enabled", true);
      XposedHelpers.setBooleanField(serviceInfo, "exported", false);

      android.content.pm.ServiceInfo[] services =
        new android.content.pm.ServiceInfo[1];
      services[0] = (android.content.pm.ServiceInfo) serviceInfo;
      return services;
    } catch (Throwable e) {
      return new android.content.pm.ServiceInfo[0];
    }
  }

  // 3. 创建伪造Receivers
  private ActivityInfo[] createFakeReceivers(String packageName) {
    return new ActivityInfo[0];
  }

  // 4. 创建伪造Providers
  private ProviderInfo[] createFakeProviders(String packageName) {
    return new ProviderInfo[0];
  }

  // 5. 创建伪造Permissions
  private PermissionInfo[] createFakePermissions(String packageName) {
    return new PermissionInfo[0];
  }

  // 6. 创建伪造Signatures
  private Signature[] createFakeSignatures(String packageName) {
    try {
      // 生成伪随机签名（基于包名）
      byte[] sigBytes = new byte[256];
      byte[] pkgBytes = packageName.getBytes("UTF-8");

      for (int i = 0; i < sigBytes.length; i++) {
        if (i < pkgBytes.length) {
          sigBytes[i] = (byte) (pkgBytes[i] ^ (i % 256));
        } else {
          sigBytes[i] = (byte) (packageName.hashCode() >> ((i % 4) * 8));
        }
      }

      Signature[] signatures = new Signature[1];
      signatures[0] = new Signature(sigBytes);
      return signatures;
    } catch (Throwable e) {
      return new Signature[0];
    }
  }

  // 7. 兜底方法：简单伪造（保持原有）
  private PackageInfo createSimpleFakePackageInfo(String packageName) {
    try {
      PackageInfo pi = new PackageInfo();
      pi.packageName = packageName != null
        ? packageName
        : "fake.package.default";

      pi.versionName = "1.0.0";
      pi.versionCode = 1;
      pi.firstInstallTime = System.currentTimeMillis() - 86400000L;
      pi.lastUpdateTime = System.currentTimeMillis();

      try {
        setInstallerPackageNameSafe(pi, "com.android.vending");
      } catch (Throwable t) {
        // 忽略
      }

      try {
        pi.applicationInfo = createFakeApplicationInfo(packageName);
      } catch (Throwable e) {
        ApplicationInfo ai = new ApplicationInfo();
        ai.packageName = packageName;
        ai.flags = ApplicationInfo.FLAG_INSTALLED;
        ai.enabled = true;
        pi.applicationInfo = ai;
      }

      return pi;
    } catch (Throwable e) {
      log("简单伪造失败: " + e.getMessage());
      return null;
    }
  }

/*
  private void hookGetPackageInfo(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getPackageInfo",
        String.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String packageName = (String) param.args[0];
            int flags = (int) param.args[1];
            /*
             if (packageName == null) return;
                    // ========== 🟢 1. 唯一放行条件：自己查自己 ==========
                    if (packageName.equals(currentTargetApp)) {
                        return;
                    }下面已有↓
                    */
                    /*
            if (packageName != null && !packageName.equals(currentTargetApp)) {
              // 系统包直接放行，走系统真实查询逻辑
              if (
                isSystemCorePackage(packageName) ||
                packageName.contains("webview") ||
                packageName.contains("chromium")
              ) {
                log("✅ 跳过系统包查询: " + packageName);
                return;
              }
              
              
              // Android 11+ PACKAGE_QUERY_FLAGS 权限伪造
              boolean isAndroid11Plus =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
              int packageQueryFlags = 0;
              if (isAndroid11Plus) {
                try {
                  // 反射获取 PACKAGE_QUERY_FLAGS
                  Field flagsField =
                    PackageManager.class.getDeclaredField(
                        "PACKAGE_QUERY_FLAGS"
                      );
                  flagsField.setAccessible(true);
                  packageQueryFlags = flagsField.getInt(null);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                  // 极端情况：字段不存在，跳过该逻辑
                  //      log("【权限伪造】Android 11+ 反射获取 PACKAGE_QUERY_FLAGS 失败: " + e.getMessage());
                }
              }

              // 仅 Android 11+ 且获取到字段时，才处理权限伪造
              if (
                isAndroid11Plus &&
                packageQueryFlags != 0 &&
                (flags & packageQueryFlags) != 0
              ) {
                Boolean shouldFakePermission = permissionFakeMap.get(
                  currentTargetApp
                );
                boolean fakeEnabled = shouldFakePermission != null
                  ? shouldFakePermission
                  : true;

                if (fakeEnabled) {
                  // 伪造授权：返回空安全PackageInfo，避免系统返回权限不足
                  param.setResult(createEmptyPackageInfo(packageName));
                  //   log("【权限伪造】Android 11+ PACKAGE_QUERY_FLAGS -> 授予权限（伪造状态：开启）");
                  return;
                } else {
                  // log("【权限伪造】Android 11+ PACKAGE_QUERY_FLAGS -> 返回真实状态（伪造状态：关闭）");
                }
              }

              // 安装状态
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;

              if (!shouldReturnInstalled) {
                try {
                
                  // 抛出系统原生异常
                  param.setThrowable(
                    new PackageManager.NameNotFoundException(
                      "Package " + packageName + " not found"
                    )
                  );
                  
                } catch (Throwable e) {
                  // 兜底：返回无有效信息的空实例
                  PackageInfo emptyPkg = new PackageInfo();
                  emptyPkg.packageName = packageName != null
                    ? packageName
                    : "xl.null.package.yhzl";
                  emptyPkg.versionName = null;
                  emptyPkg.versionCode = -1;
                  emptyPkg.applicationInfo = null;
                  emptyPkg.firstInstallTime = 0;
                  emptyPkg.lastUpdateTime = 0;
                  param.setResult(emptyPkg);
                  log("【未安装】兜底返回空实例: " + packageName);
                }
                return;
              }
              
              // 已安装：启用智能伪造
              synchronized (globalCapturedPackages) {
                if (!globalCapturedPackages.contains(packageName)) {
                  globalCapturedPackages.add(packageName);
                  saveConfigToFile();
                }
              }
              
              if (!appCapturedPackages.contains(packageName)) {
                appCapturedPackages.add(packageName);
              }
              log(
                "全局捕获列表(" +
                globalCapturedPackages.size() +
                "): " +
                globalCapturedPackages.toString()
              );
              Object fakeResult = createSmartFakePackageInfo(
                packageName,
                flags
              );
              if (fakeResult != null) {
                param.setResult(fakeResult);
                log("【已安装】智能伪造成功: " + packageName);
                if (!blockExitMap.getOrDefault(currentTargetApp, false)) {
                 // blockExitMap.put(currentTargetApp, true); //可选自动开启拦截退出
                //  log("⚠️ 检测到伪造包，自动开启拦截退出");
                }
              }
            }
            
          }
        }
      );
    } catch (Throwable t) {
      log("Hook getPackageInfo失败: " + t.getMessage());
    }
  }
*/

private void hookGetPackageInfo(ClassLoader classLoader) {
    try {
        XposedHelpers.findAndHookMethod(
            "android.app.ApplicationPackageManager",
            classLoader,
            "getPackageInfo",
            String.class,
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable {
                    String packageName = (String) param.args[0];
                    int flags = (int) param.args[1];
                    
                    if (packageName == null) return;
                    
                    // ========== 1. 自己查自己，直接放行 ==========
                    if (packageName.equals(currentTargetApp)) {
                        return;
                    }
                    
                    // ========== 2. 系统包直接放行 ==========
                    if (isSystemCorePackage(packageName)) {
                        return;
                    }
                    
                    // ========== 3. 🟢 核心修复：无条件捕获包名（同步保存） ==========
                    boolean isNewCapture = false;
                    synchronized (globalCapturedPackages) {
                        if (!globalCapturedPackages.contains(packageName)) {
                            globalCapturedPackages.add(packageName);
                            isNewCapture = true;
                            // ✅ 直接同步保存，最稳定！
                            saveConfigToFile();
                        }
                    }
                    if (!appCapturedPackages.contains(packageName)) {
                        appCapturedPackages.add(packageName);
                    }
                    
                    if (isNewCapture) {
                      //  log("【包名捕获】" + packageName + " (当前状态: " + (installStatusMap.getOrDefault(currentTargetApp, true) ? "已安装" : "未安装") + ")");
                    }
                    
                    // ========== 4. 根据安装状态处理返回值 ==========
                    Boolean status = installStatusMap.get(currentTargetApp);
                    boolean shouldReturnInstalled = status != null ? status : true;
                    
                    if (!shouldReturnInstalled) {
                        // 🟢 未安装状态：返回包不存在（但包名已捕获并保存）
                        try {
                            param.setThrowable(
                                new PackageManager.NameNotFoundException(
                                    "Package " + packageName + " not found"
                                )
                            );
                        } catch (Throwable e) {
                            PackageInfo emptyPkg = new PackageInfo();
                            emptyPkg.packageName = packageName;
                            emptyPkg.versionName = null;
                            emptyPkg.versionCode = -1;
                            emptyPkg.applicationInfo = null;
                            emptyPkg.firstInstallTime = 0;
                            emptyPkg.lastUpdateTime = 0;
                            param.setResult(emptyPkg);
                        }
                        return;
                    }
                    log("全局捕获列表(" + globalCapturedPackages.size() +"): " + globalCapturedPackages.toString());
                    // ========== 5. 已安装状态：返回伪造信息 ==========
                    Object fakeResult = createSmartFakePackageInfo(packageName, flags);
                    if (fakeResult != null) {
                        param.setResult(fakeResult);
                        log("【已安装】智能伪造: " + packageName);
                        if (!blockExitMap.getOrDefault(currentTargetApp, false)) {
                      // blockExitMap.put(currentTargetApp, true); //可选自动开启拦截退出
                       //  log("⚠️ 检测到伪造包，自动开启拦截退出");
                       }
                    }
                }
            }
        );
       // log("✅ Hook getPackageInfo成功（同步保存版）");
    } catch (Throwable t) {
        log("❌ Hook getPackageInfo失败: " + t.getMessage());
    }
}

  // 创建空安全PackageInfo（用于Android 11+权限伪造）
  private PackageInfo createEmptyPackageInfo(String packageName) {
    PackageInfo emptyPkg = new PackageInfo();
    emptyPkg.packageName = packageName != null ? packageName : "fake.package";
    emptyPkg.versionName = "";
    emptyPkg.versionCode = 0;
    emptyPkg.applicationInfo = new ApplicationInfo();
    emptyPkg.applicationInfo.packageName = emptyPkg.packageName;
    return emptyPkg;
  }



 // 过滤系统核心包+厂商核心包+用户排除包
private boolean isSystemCorePackage(String packageName) {
  // 先处理精确匹配的核心包（android、root、system）
  if (
    packageName.equals("android") ||
    packageName.equals("root") ||
    packageName.equals("system")
  ) {
    return true;
  }
  // 系统核心包 + 主流厂商核心包 + 厂商/子品牌
  boolean isSystemOrManufacturer =
    packageName.startsWith("com.android.") ||
    packageName.startsWith("com.google.android.") ||
    packageName.startsWith("android.") ||
    packageName.equals("com.google.android.webview") ||
    packageName.equals("com.google.android.gms") ||
    packageName.equals("com.heytap.openid") ||
    packageName.equals("com.google.android.packageinstaller") ||
    // 核心厂商
    packageName.startsWith("com.qualcomm") ||
    packageName.startsWith("com.samsung") ||
    packageName.startsWith("com.huawei") ||
    packageName.startsWith("com.miui") ||
    packageName.startsWith("com.oneplus") ||
    packageName.startsWith("com.oppo") ||
    packageName.startsWith("com.vivo") ||
    packageName.startsWith("com.realme") ||
    packageName.startsWith("com.xiaomi") ||
    packageName.startsWith("com.meizu") ||
    // 厂商/子品牌生态
    packageName.startsWith("com.asus") || // 华硕
    packageName.startsWith("com.lenovo") || // 联想
    packageName.startsWith("com.zuk") || // zuk（联想子品牌）
    packageName.startsWith("com.motorola") || // 摩托罗拉
    packageName.startsWith("com.nokia") || // 诺基亚
    packageName.startsWith("com.honor") || // 荣耀（原华为子品牌，现已独立）
    packageName.startsWith("com.xiaomi.global") || // 小米国际版
    packageName.startsWith("com.oppo.global") || // OPPO国际版
    packageName.startsWith("com.vivo.global") || // VIVO国际版
    packageName.startsWith("com.realme.global") || // realme国际版
    packageName.startsWith("com.infinix") || // 传音Infinix
    packageName.startsWith("com.tecno") || // 传音Tecno
    packageName.startsWith("com.itel") || // 传音Itel
    packageName.startsWith("com.sharp") || // 夏普
    packageName.startsWith("com.sony") || // 索尼
    packageName.startsWith("com.lg") || // LG
    packageName.startsWith("com.poco") || // 小米POCO（子品牌）
    packageName.startsWith("com.redmi") || // 小米Redmi（子品牌）
    packageName.startsWith("com.huawei.hwid") || // 华为账号核心包
    packageName.startsWith("com.oppo.nearme") || // OPPO应用商店核心包
    packageName.startsWith("com.vivo.browser") || // VIVO浏览器核心包
    packageName.startsWith("com.heytap") || // 一加/OPPO 欢太生态
    packageName.startsWith("com.coloros") || // OPPO ColorOS核心
    packageName.startsWith("com.flyme") || // 魅族Flyme核心
    packageName.startsWith("com.mi") || // 小米生态核心（部分包前缀）
    packageName.startsWith("com.xiaomi.account"); // 小米账号核心
  // 核心库相关包
  boolean isCoreLibRelated =
    packageName.contains("webview") ||
    packageName.contains("jiagu") ||
    packageName.contains("c++_shared") ||
    packageName.contains("breakpad") ||
    packageName.contains("monochrome") ||
    packageName.contains("vendor") || // 厂商底层库
    packageName.contains("chipset") || // 芯片相关库
    packageName.contains("modem") || // 调制解调器相关
    packageName.contains("radio") || // 射频相关
    packageName.contains("firmware"); // 固件相关
  /*
  List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
  if (excludedPackages.contains(packageName)) {
    return true; 
  }
*/
  // 新增：检查是否为用户手动排除的包名
List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
if (excludedPackages.contains(packageName) || 
    (packageName != null && (
        packageName.startsWith("io.va.exposed") ||          // VAExposed
        packageName.startsWith("com.excean.dualaid") ||      // 微双开分身
        packageName.startsWith("com.qihoo.magic") ||         // 分身大师(360)
        packageName.startsWith("info.red.virtual") ||        // 悟空分身
        packageName.startsWith("com.bly.dkplat") ||          // 小X分身
        packageName.startsWith("com.pengyou.cloneapp") ||   // 小X分身国际版/Clone App
        packageName.startsWith("com.jy.x.separation.manager") || // 团团分身
        packageName.startsWith("com.dong.multirun") ||       // Multi Run
        packageName.startsWith("com.excelliance.dualaid") || // 双开助手
        packageName.startsWith("com.lbe.parallel") ||        // 平行空间
        packageName.startsWith("com.parallel.space") ||      // 平行空间国际版
        packageName.startsWith("com.chaozhijian.multiopen")  // 多开分身
    ))) {
    return true; // 纳入排除范围，跳过所有伪造/Hook
}


  return isSystemOrManufacturer || isCoreLibRelated;
}

/*
  private void hookGetApplicationInfo(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getApplicationInfo",
        String.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String packageName = (String) param.args[0];
            int flags = (int) param.args[1];
            /*
             if (packageName == null) return;
                    // ========== 🟢 1. 唯一放行条件：自己查自己 ==========
                    if (packageName.equals(currentTargetApp)) {
                        return;
                    }下面已有↓
                    */
                    /*
            if (packageName != null && !packageName.equals(currentTargetApp)) {
              // 系统包直接放行，返回真实系统应用信息
              if (
                isSystemCorePackage(packageName) ||
                packageName.contains("webview") ||
                packageName.contains("chromium")
              ) {
              //  log("✅ 放行系统包信息查询: " + packageName);
                return;
              }
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;

              if (!shouldReturnInstalled) {
                try {
                  // 抛出系统原生“包未找到”异常（目标应用直接识别为未安装）
                  param.setThrowable(
                    new PackageManager.NameNotFoundException(
                      "Package " + packageName + " not found"
                    )
                  );
                  log(
                    "【未安装状态】返回未安装: " +
                    packageName +
                    "（智能伪造已关闭）"
                  );
                } catch (Throwable e) {
                  // 兜底：若抛出异常失败，返回无有效信息的空实例
                  ApplicationInfo emptyAppInfo = new ApplicationInfo();
                  emptyAppInfo.packageName = packageName != null
                    ? packageName
                    : "xl.null.package.yhzl2";
                  // 所有核心字段置空/无效，彻底模拟未安装
                  emptyAppInfo.name = null;
                  emptyAppInfo.flags = 0;
                  emptyAppInfo.enabled = false;
                  emptyAppInfo.sourceDir = null;
                  emptyAppInfo.dataDir = null;
                  emptyAppInfo.nativeLibraryDir = null;
                  emptyAppInfo.uid = -1;
                  emptyAppInfo.targetSdkVersion = -1;
                  // 版本字段无效化
                  try {
                    XposedHelpers.setIntField(emptyAppInfo, "versionCode", -1);
                    XposedHelpers.setObjectField(
                      emptyAppInfo,
                      "versionName",
                      null
                    );
                  } catch (NoSuchFieldError err) {}
                  param.setResult(emptyAppInfo);
                  log(
                    "【未安装状态】返回未安装: " + packageName + "（兜底逻辑）"
                  );
                }
                return;
              }

              // 已安装：启用智能伪造
              synchronized (globalCapturedPackages) {
                if (!globalCapturedPackages.contains(packageName)) {
                  globalCapturedPackages.add(packageName);
                  saveConfigToFile();
                }
              }
              if (!appCapturedPackages.contains(packageName)) {
                appCapturedPackages.add(packageName);
              }
              Object fakeResult = createSmartApplicationInfo(
                packageName,
                flags
              );
              if (fakeResult != null) {
                param.setResult(fakeResult);
              }
            }
          }
        }
      );
    } catch (Throwable t) {
      log("Hook getApplicationInfo失败: " + t.getMessage());
    }
  }
*/

private void hookGetApplicationInfo(ClassLoader classLoader) {
    try {
        XposedHelpers.findAndHookMethod(
            "android.app.ApplicationPackageManager",
            classLoader,
            "getApplicationInfo",
            String.class,
            int.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable {
                    String packageName = (String) param.args[0];
                    int flags = (int) param.args[1];
                    
                    if (packageName == null) return;
                    
                    // ========== 1. 自己查自己，直接放行 ==========
                    if (packageName.equals(currentTargetApp)) {
                        return;
                    }
                    
                    // ========== 2. 系统包直接放行 ==========
                    if (isSystemCorePackage(packageName)) {
                        return;
                    }
                    
                    // ========== 3. 🟢 核心修复：无条件捕获包名（同步保存） ==========
                    boolean isNewCapture = false;
                    synchronized (globalCapturedPackages) {
                        if (!globalCapturedPackages.contains(packageName)) {
                            globalCapturedPackages.add(packageName);
                            isNewCapture = true;
                            // ✅ 直接同步保存，最稳定！
                            saveConfigToFile();
                        }
                    }
                    if (!appCapturedPackages.contains(packageName)) {
                        appCapturedPackages.add(packageName);
                    }
                    
                    if (isNewCapture) {
                      //  log("【应用信息捕获】" + packageName + " (当前状态: " +   (installStatusMap.getOrDefault(currentTargetApp, true) ? "已安装" : "未安装") + ")");
                    }
                    
                    // ========== 4. 根据安装状态处理返回值 ==========
                    Boolean status = installStatusMap.get(currentTargetApp);
                    boolean shouldReturnInstalled = status != null ? status : true;
                    
                    if (!shouldReturnInstalled) {
                        // 🟢 未安装状态：返回包不存在
                        try {
                            param.setThrowable(
                                new PackageManager.NameNotFoundException(
                                    "Package " + packageName + " not found"
                                )
                            );
                            log(
                    "【未安装】返回未安装: " + packageName +"(关闭智能伪造)"
                  );
                        } catch (Throwable e) {
                            ApplicationInfo emptyAppInfo = new ApplicationInfo();
                            emptyAppInfo.packageName = packageName;
                            emptyAppInfo.name = null;
                            emptyAppInfo.flags = 0;
                            emptyAppInfo.enabled = false;
                            emptyAppInfo.sourceDir = null;
                            emptyAppInfo.dataDir = null;
                            emptyAppInfo.nativeLibraryDir = null;
                            emptyAppInfo.uid = -1;
                            emptyAppInfo.targetSdkVersion = -1;
                            param.setResult(emptyAppInfo);
                        }
                        return;
                    }
                  // log("全局捕获列表(" + globalCapturedPackages.size() +"): " + globalCapturedPackages.toString());
                    // ========== 5. 已安装状态：返回伪造信息 ==========
                    Object fakeResult = createSmartApplicationInfo(packageName, flags);
                    if (fakeResult != null) {
                        param.setResult(fakeResult);
                       // log("【已安装状态】智能伪造应用信息: " + packageName);
                    } else {
                        param.setResult(createFakeApplicationInfo(packageName));
                    }
                }
            }
        );
      //  log("✅ Hook getApplicationInfo成功（同步保存版）");
    } catch (Throwable t) {
        log("❌ Hook getApplicationInfo失败: " + t.getMessage());
    }
}

  private void hookGetInstalledPackages(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getInstalledPackages",
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Boolean shouldFakePermission = permissionFakeMap.get(
              currentTargetApp
            );
            boolean fakeEnabled = shouldFakePermission != null
              ? shouldFakePermission
              : true;
            if (!fakeEnabled) return;

            // Android 11+：伪造QUERY_ALL_PACKAGES权限，返回完整列表
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
              List<PackageInfo> fakeList = new ArrayList<>();
              synchronized (globalCapturedPackages) {
                for (String pkg : globalCapturedPackages) {
                  fakeList.add(createSmartFakePackageInfo(pkg, param.args[0]));
                }
              }
              param.setResult(fakeList);
              //    log("【权限伪造】getInstalledPackages -> 返回伪造列表（Android 11+）");
              return;
            }

            // 低版本
            Boolean status = installStatusMap.get(currentTargetApp);
            boolean shouldReturnInstalled = status != null ? status : true;
            if (!shouldReturnInstalled) {
              param.setResult(new ArrayList<PackageInfo>());
              return;
            }
            List<PackageInfo> fakeList = new ArrayList<>();
            synchronized (globalCapturedPackages) {
              for (String pkg : globalCapturedPackages) {
                fakeList.add(createSmartFakePackageInfo(pkg, param.args[0]));
              }
            }
            param.setResult(fakeList);
          }
        }
      );
    } catch (Throwable t) {
      log("Hook getInstalledPackages失败: " + t.getMessage());
    }
  }

  private void hookGetInstalledApplications(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getInstalledApplications",
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Boolean status = installStatusMap.get(currentTargetApp);
            boolean shouldReturnInstalled = status != null ? status : true;
            if (!shouldReturnInstalled) {
              // 未安装：关闭智能伪造，返回系统真实系统应用列表
              return;
            }
            // 已安装：启用智能伪造，返回伪造应用列表
            List<ApplicationInfo> fakeList = new ArrayList<>();
            synchronized (globalCapturedPackages) {
              for (String pkg : globalCapturedPackages) {
                ApplicationInfo ai = createSmartApplicationInfo(
                  pkg,
                  param.args[0]
                );
                if (ai != null) {
                  fakeList.add(ai);
                }
              }
            }
            param.setResult(fakeList);
          }
        }
      );
    } catch (Throwable t) {
      log("Hook getInstalledApplications失败: " + t.getMessage());
    }
  }

  // 兜底方法
  private PackageInfo createFakePackageInfo(String packageName) {
    // 现在调用智能伪造，使用默认flags
    return createSmartFakePackageInfo(
      packageName,
      PackageManager.GET_META_DATA
    );
  }

  // 兜底方法）
  private ApplicationInfo createFakeApplicationInfo(String packageName) {
    return createSmartApplicationInfo(packageName, 0);
  }

  // 优化createFakeIntent：确保ComponentName非空
  private Intent createFakeIntent(String packageName) {
    Intent fakeIntent = new Intent(Intent.ACTION_MAIN);
    fakeIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    fakeIntent.setPackage(
      packageName != null ? packageName : "fake.package.default"
    );
    // 确保ComponentName非空，避免目标应用获取时崩溃
    String className = packageName != null
      ? packageName + ".MainActivity"
      : "fake.package.default.MainActivity";
    fakeIntent.setComponent(
      new ComponentName(fakeIntent.getPackage(), className)
    );
    fakeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return fakeIntent;
  }

  private String extractPackageName(Object[] args) {
    if (args == null || args.length == 0) {
      return null;
    }

    for (Object arg : args) {
      if (arg instanceof String) {
        String str = (String) arg;
        if (isValidPackageName(str)) {
          return str;
        }
      }
    }

    for (Object arg : args) {
      if (arg instanceof Intent) {
        Intent intent = (Intent) arg;
        String pkg = intent.getPackage();
        if (isValidPackageName(pkg)) {
          return pkg;
        }
        ComponentName cn = intent.getComponent();
        if (cn != null) {
          pkg = cn.getPackageName();
          if (isValidPackageName(pkg)) {
            return pkg;
          }
        }
      }
    }

    for (Object arg : args) {
      if (arg instanceof ComponentName) {
        ComponentName cn = (ComponentName) arg;
        String pkg = cn.getPackageName();
        if (isValidPackageName(pkg)) {
          return pkg;
        }
      }
    }

    return null;
  }

  private boolean isValidPackageName(String str) {
    if (str == null || str.length() < 3 || !str.contains(".")) {
      return false;
    }
    return !str.startsWith("/") && !str.contains("://");
  }

//Hook Bundle.getString
private void hookBundleGetString(ClassLoader classLoader) {
    try {
        Class<?> bundleClass = Class.forName("android.os.Bundle", false, classLoader);
        
        XposedBridge.hookAllMethods(
            bundleClass,
            "getString",
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    // ========== 🚨【核心修复1】thisObject为空 ==========
                    if (param.thisObject == null) {
                        // 返回默认值
                        if (param.args.length >= 2) {
                            param.setResult(param.args[1]);
                        } else {
                            param.setResult("");
                        }
                        return;
                    }
                    
                    Bundle bundle = (Bundle) param.thisObject;
                    
                    // ========== 🚨【核心修复2】Bundle已被GC或非法状态 ==========
                    try {
                        // 唯一安全的检测：hashCode() 会抛异常如果对象已销毁
                        int hash = bundle.hashCode();
                    } catch (Throwable e) {
                        // Bundle已不可用，返回默认值
                        if (param.args.length >= 2) {
                            param.setResult(param.args[1]);
                        } else {
                            param.setResult("");
                        }
                        return;
                    }
                    
                    // ========== 🚨【核心修复3】key不存在 ==========
                    if (param.args.length >= 1) {
                        String key = (String) param.args[0];
                        if (!bundle.containsKey(key)) {
                            if (param.args.length >= 2) {
                                param.setResult(param.args[1]);
                            } else {
                                param.setResult("");
                            }
                            return;
                        }
                    }
                }
                
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    // ========== 🚨【核心修复4】返回值为空 ==========
                    if (param.getResult() == null) {
                        if (param.args.length >= 2) {
                            param.setResult(param.args[1]);
                        } else {
                            param.setResult("");
                        }
                    }
                }
            }
        );
        
        // ========== 🚨【核心修复5】额外修复 getString(String key, String defaultValue) ==========
        try {
            XposedHelpers.findAndHookMethod(
                bundleClass,
                "getString",
                String.class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject == null) {
                            param.setResult(param.args[1]); // 返回默认值
                            return;
                        }
                        
                        Bundle bundle = (Bundle) param.thisObject;
                        try {
                            bundle.hashCode(); // 检测是否可用
                        } catch (Throwable e) {
                            param.setResult(param.args[1]);
                            return;
                        }
                        
                        String key = (String) param.args[0];
                        if (!bundle.containsKey(key)) {
                            param.setResult(param.args[1]);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
        
        //XposedBridge.log("[InstallHook] ✅ Bundle.getString Hook完成（空指针防护）");
        
    } catch (Throwable t) {
        //XposedBridge.log("[InstallHook] ❌ Hook Bundle.getString失败: " + t.getMessage());
    }
}

  // 底层Hook Bundle类，拦截所有空实例的方法调用
  private void hookBundleEmptyInstance(ClassLoader classLoader) {
    try {
        Class<?> bundleClass = Class.forName("android.os.Bundle", false, classLoader);
        
        // Hook构造函数
        XposedHelpers.findAndHookConstructor(
            bundleClass,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // 构造出来的Bundle不可能为null，但防止反射异常
                    if (param.getResult() == null) return;
                    
                    Bundle bundle = (Bundle) param.getResult();
                    try {
                        // 加个标记字段，但不依赖它
                        bundle.putBoolean("__hook_protected", true);
                    } catch (Throwable ignored) {}
                }
            }
        );
        
        // Hook所有可能崩溃的方法
        String[] methods = {"getString", "getInt", "getBoolean", "getLong", 
                           "getDouble", "getFloat", "getBundle", "getSerializable",
                           "getParcelable", "getParcelableArrayList", "getStringArrayList",
                           "getIntegerArrayList", "getBooleanArray", "containsKey"};
        
        for (String methodName : methods) {
            try {
                // 先Hook单参数版本
                XposedHelpers.findAndHookMethod(
                    bundleClass,
                    methodName,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // ========== 🚨 统一空指针保护 ==========
                            if (param.thisObject == null) {
                                setDefaultReturnValue(param);
                                return;
                            }
                            
                            // 检测Bundle是否可用
                            Bundle bundle = (Bundle) param.thisObject;
                            try {
                                bundle.hashCode();
                            } catch (Throwable e) {
                                setDefaultReturnValue(param);
                            }
                        }
                        
                        private void setDefaultReturnValue(MethodHookParam param) {
                            if (param.method instanceof Method) {
                                Class<?> returnType = ((Method) param.method).getReturnType();
                                if (returnType == String.class) param.setResult("");
                                else if (returnType == int.class) param.setResult(0);
                                else if (returnType == long.class) param.setResult(0L);
                                else if (returnType == boolean.class) param.setResult(false);
                                else if (returnType == double.class) param.setResult(0.0);
                                else if (returnType == float.class) param.setResult(0.0f);
                                else param.setResult(null);
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}
            
            try {
                // Hook双参数版本（带默认值）
                XposedHelpers.findAndHookMethod(
                    bundleClass,
                    methodName,
                    String.class,
                    Object.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.thisObject == null) {
                                param.setResult(param.args[1]); // 返回默认值
                                return;
                            }
                            
                            Bundle bundle = (Bundle) param.thisObject;
                            try {
                                bundle.hashCode();
                            } catch (Throwable e) {
                                param.setResult(param.args[1]);
                                return;
                            }
                            
                            String key = (String) param.args[0];
                            if (!bundle.containsKey(key)) {
                                param.setResult(param.args[1]);
                            }
                        }
                    }
                );
            } catch (Throwable ignored) {}
        }
        
        //XposedBridge.log("[InstallHook] ✅ Bundle空实例防护完成");
        
    } catch (Throwable t) {
        //XposedBridge.log("[InstallHook] ❌ Hook Bundle失败: " + t.getMessage());
    }
}

  // ========== 重载方法Hook ==========
  private void hookOverloadMethods(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getPackageInfo",
        String.class,
        int.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String packageName = extractPackageName(param.args);
            if (packageName != null && !packageName.equals(currentTargetApp)) {
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;

              if (!shouldReturnInstalled) {
                param.setResult(null);
                return;
              }

              synchronized (globalCapturedPackages) {
                if (!globalCapturedPackages.contains(packageName)) {
                  globalCapturedPackages.add(packageName);
                }
              }

              Object fakeResult = createFakePackageInfo(packageName);
              if (fakeResult != null) {
                param.setResult(fakeResult);
              }
            }
          }
        }
      );

      // Hook getApplicationInfo重载版
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getApplicationInfo",
        String.class,
        int.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String packageName = extractPackageName(param.args);
            if (packageName != null && !packageName.equals(currentTargetApp)) {
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;

              if (!shouldReturnInstalled) {
                param.setResult(null);
                return;
              }

              synchronized (globalCapturedPackages) {
                if (!globalCapturedPackages.contains(packageName)) {
                  globalCapturedPackages.add(packageName);
                }
              }

              Object fakeResult = createFakeApplicationInfo(packageName);
              if (fakeResult != null) {
                param.setResult(fakeResult);
              }
            }
          }
        }
      );

      // Hook getInstalledPackages重载版
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getInstalledPackages",
        int.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Boolean status = installStatusMap.get(currentTargetApp);
            boolean shouldReturnInstalled = status != null ? status : true;

            if (!shouldReturnInstalled) {
              param.setResult(new ArrayList<PackageInfo>());
              return;
            }

            List<PackageInfo> fakeList = new ArrayList<>();
            synchronized (globalCapturedPackages) {
              for (String pkg : globalCapturedPackages) {
                PackageInfo pi = createFakePackageInfo(pkg);
                if (pi != null) {
                  fakeList.add(pi);
                }
              }
            }

            param.setResult(fakeList);
          }
        }
      );

      // Hook getInstalledApplications重载版
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getInstalledApplications",
        int.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Boolean status = installStatusMap.get(currentTargetApp);
            boolean shouldReturnInstalled = status != null ? status : true;

            if (!shouldReturnInstalled) {
              param.setResult(new ArrayList<ApplicationInfo>());
              return;
            }

            List<ApplicationInfo> fakeList = new ArrayList<>();
            synchronized (globalCapturedPackages) {
              for (String pkg : globalCapturedPackages) {
                ApplicationInfo ai = createFakeApplicationInfo(pkg);
                if (ai != null) {
                  fakeList.add(ai);
                }
              }
            }

            param.setResult(fakeList);
          }
        }
      );
    } catch (Throwable t) {
      //    log("Hook重载方法失败: " + t.getMessage());
    }
  }

  private void hookSystemFileRead() {
    try {
      XposedHelpers.findAndHookMethod(
        "java.io.FileInputStream",
        null,
        "FileInputStream",
        String.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String filePath = (String) param.args[0];
            if (
              filePath.contains("/data/system/packages.xml") ||
              filePath.contains("/data/system/packages.list") ||
              filePath.contains("com.android.settings/databases/apps.db")
            ) {
              param.setThrowable(new SecurityException("权限不足，无法读取"));
            }
          }
        }
      );
    } catch (Throwable t) {
      //    log("Hook文件读取失败: " + t.getMessage());
    }
  }
  
  
// 让分身里的应用能正确加载 .so 文件（兜底方案）
private void hookSoPathInApp(ClassLoader classLoader) {
    try {
        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            classLoader,
            "attach",
            Context.class,
            new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context context = (Context) param.args[0];
                        ApplicationInfo ai = context.getApplicationInfo();
                        // 已经修复过的就不重复log了
                        XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                    } catch (Throwable ignored) {}
                }
            }
        );
    } catch (Throwable ignored) {}
}




  private void hookPackageManagerReflect() {
    try {
        XposedHelpers.findAndHookMethod(
            "java.lang.Class",
            null,
            "getMethod",
            String.class,
            Class[].class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        if (!(param.thisObject instanceof Class)) {
                            return;
                        }

                        Class<?> targetClass = (Class<?>) param.thisObject;
                        String methodName = (String) param.args[0];

                        // 仅过滤 PackageManager 相关类的敏感方法（不区分分身/普通应用）
                        boolean isPackageManagerRelated =
                                targetClass.getName().equals("android.content.pm.PackageManager") ||
                                targetClass.getName().equals("android.app.ApplicationPackageManager") ||
                                targetClass.getName().contains("PackageManager");

                        boolean isSensitiveMethod =
                                methodName.contains("getPackageInfoAsUser") ||
                                methodName.contains("getApplicationInfoAsUser") ||
                                methodName.contains("getInstalledPackagesAsUser") ||
                                methodName.contains("getInstalledApplicationsAsUser") ||
                                methodName.contains("hidden") ||
                                methodName.contains("internal") ||
                                methodName.contains("AsUser");

                        if (isPackageManagerRelated && isSensitiveMethod) {
                            // 核心逻辑：不判断是否为分身，直接尝试阻断，异常则放行
                            try {
                                // 尝试获取当前应用上下文（仅作兼容校验，失败不影响）
                                Context context = (Context) XposedHelpers.callStaticMethod(
                                        XposedHelpers.findClass("android.app.ActivityThread", null),
                                        "currentApplication"
                                );
                                // 无异常则阻断敏感方法（普通应用场景）
                                param.setResult(null);
                              //  log("✅ 拦截 PackageManager 敏感反射方法: " + methodName);
                            } catch (Throwable e) {
                                // 任何异常（分身权限不足、上下文获取失败等），直接放行
                             //   log("⚠️ 执行拦截失败（可能是分身环境），跳过 Hook: " + e.getMessage());
                            }
                        }
                    } catch (Throwable t) {
                        // 外层兜底捕获，绝对避免影响应用运行
                        log("反射 Hook 全局异常: " + t.getMessage());
                    }
                }
            }
        );
    //    log("✅ 成功Hook PackageManager 反射监控（兼容任意分身/包名/UID）");
    } catch (Throwable t) {
        log("Hook反射监控初始化失败: " + t.getMessage());
    }
}



  // ========== PackageManager扩展方法 ==========
  private void hookIsApplicationEnabled(ClassLoader classLoader) {
    try {
      // 用 hookAllMethods 兼容方法签名差异
      XposedBridge.hookAllMethods(
        XposedHelpers.findClass(
          "android.app.ApplicationPackageManager",
          classLoader
        ),
        "isApplicationEnabled",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            // 只处理1个参数的情况（String packageName）
            if (param.args.length == 1 && param.args[0] instanceof String) {
              String packageName = (String) param.args[0];
              if (
                packageName != null && !packageName.equals(currentTargetApp)
              ) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;
                if (!shouldReturnInstalled) {
                  param.setResult(false);
                  return;
                }
                boolean isCaptured = false;
                synchronized (globalCapturedPackages) {
                  isCaptured = globalCapturedPackages.contains(packageName);
                }
                if (isCaptured) {
                  param.setResult(true);
                }
              }
            }
          }
        }
      );
      //  log("✅ 成功Hook isApplicationEnabled（兼容所有版本）");
    } catch (Throwable t) {
      log("Hook isApplicationEnabled失败: " + t.getMessage());
    }
  }

  private void hookCheckPermission(ClassLoader classLoader) {
    try {
      // 1. Hook PackageManager.checkPermission（系统级权限查询）
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "checkPermission",
        String.class,
        String.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String permName = (String) param.args[0];
            String packageName = (String) param.args[1];
            if (packageName != null && !packageName.equals(currentTargetApp)) {
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (
                fakeEnabled &&
                Arrays.asList(DETECTION_PERMISSIONS).contains(permName)
              ) {
                param.setResult(PackageManager.PERMISSION_GRANTED);
                return;
              }
            }
          }
        }
      );

      // 2. Hook ContextWrapper.checkSelfPermission（应用自查核心路径）
      try {
        XposedHelpers.findAndHookMethod(
          "android.content.ContextWrapper",
          classLoader,
          "checkSelfPermission",
          String.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              String permName = (String) param.args[0];
              Boolean shouldFakePermission = permissionFakeMap.get(
                currentTargetApp
              );
              boolean fakeEnabled = shouldFakePermission != null
                ? shouldFakePermission
                : true;
              if (
                fakeEnabled &&
                Arrays.asList(DETECTION_PERMISSIONS).contains(permName)
              ) {
                param.setResult(PackageManager.PERMISSION_GRANTED);
                //  log("【权限伪造】ContextWrapper.checkSelfPermission -> 授予权限: " + permName);
                return;
              }
            }
          }
        );
      } catch (Throwable t) {
        // 兜底：Hook ContextImpl.checkSelfPermission
        try {
          XposedHelpers.findAndHookMethod(
            "android.app.ContextImpl",
            classLoader,
            "checkSelfPermission",
            String.class,
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
                String permName = (String) param.args[0];
                Boolean shouldFakePermission = permissionFakeMap.get(
                  currentTargetApp
                );
                boolean fakeEnabled = shouldFakePermission != null
                  ? shouldFakePermission
                  : true;
                if (
                  fakeEnabled &&
                  Arrays.asList(DETECTION_PERMISSIONS).contains(permName)
                ) {
                  param.setResult(PackageManager.PERMISSION_GRANTED);
                  //  log("【权限伪造】ContextImpl.checkSelfPermission -> 授予权限: " + permName);
                  return;
                }
              }
            }
          );
        } catch (Throwable e) {
          log("Hook ContextImpl.checkSelfPermission失败: " + e.getMessage());
        }
      }

      // 3. Hook AndroidX PermissionChecker
      try {
        Class<?> permissionCheckerClass = XposedHelpers.findClassIfExists(
          "androidx.core.content.PermissionChecker",
          classLoader
        );
        if (permissionCheckerClass != null) {
          XposedBridge.hookAllMethods(
            permissionCheckerClass,
            "checkSelfPermission",
            new XC_MethodHook() {
              @Override
              protected void beforeHookedMethod(MethodHookParam param)
                throws Throwable {
                String targetPerm = null;
                // 遍历参数找到权限名（适配不同参数顺序）
                for (Object arg : param.args) {
                  if (
                    arg instanceof String &&
                    Arrays.asList(DETECTION_PERMISSIONS).contains(arg)
                  ) {
                    targetPerm = (String) arg;
                    break;
                  }
                }
                if (targetPerm != null) {
                  Boolean shouldFakePermission = permissionFakeMap.get(
                    currentTargetApp
                  );
                  boolean fakeEnabled = shouldFakePermission != null
                    ? shouldFakePermission
                    : true;
                  if (fakeEnabled) {
                    param.setResult(PackageManager.PERMISSION_GRANTED);
                    //    log("【权限伪造】AndroidX.PermissionChecker -> 授予权限: " + targetPerm);
                    return;
                  }
                }
              }
            }
          );
        }
      } catch (Throwable t) {
        log("Hook AndroidX PermissionChecker失败: " + t.getMessage());
      }
      //     log("✅ 权限检查Hook初始化完成（覆盖系统+应用+AndroidX）");
    } catch (Throwable t) {
      log("Hook checkPermission失败: " + t.getMessage());
    }
  }

  private void hookGetActivityInfo(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getActivityInfo",
        ComponentName.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            ComponentName component = (ComponentName) param.args[0];
            if (component != null) {
              String packageName = component.getPackageName();

              if (
                packageName != null && !packageName.equals(currentTargetApp)
              ) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;

                if (!shouldReturnInstalled) {
                  param.setResult(null);
                  return;
                }

                Object fakeActivityInfo = createFakeActivityInfo(
                  packageName,
                  component.getClassName()
                );
                if (fakeActivityInfo != null) {
                  param.setResult(fakeActivityInfo);
                }
              }
            }
          }
        }
      );
    } catch (Throwable t) {
      log("Hook getActivityInfo失败: " + t.getMessage());
    }
  }

  private Object createFakeActivityInfo(String packageName, String className) {
    try {
      Class<?> activityInfoClass = Class.forName(
        "android.content.pm.ActivityInfo"
      );
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

  // ========== Intent相关方法 ==========
  private void hookQueryIntentActivities(ClassLoader classLoader) {
    try {
      XposedBridge.hookAllMethods(
        XposedHelpers.findClass(
          "android.app.ApplicationPackageManager",
          classLoader
        ),
        "queryIntentActivities",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              // 提取Intent参数（兼容参数位置变化，遍历找到Intent类型）
              Intent intent = null;
              for (Object arg : param.args) {
                if (arg instanceof Intent) {
                  intent = (Intent) arg;
                  break;
                }
              }

              if (intent == null) {
                //          log("【queryIntentActivities】未找到Intent参数，跳过Hook");
                return;
              }
              // 从Intent中提取目标包名
              String targetPackage = extractPackageFromIntent(intent);
              if (
                targetPackage != null && !targetPackage.equals(currentTargetApp)
              ) {
                // 统一处理Intent查询
                handleIntentQueryForIntentHook(
                  param,
                  targetPackage,
                  "queryIntentActivities（兼容版）"
                );
              }
            } catch (Throwable t) {
              //         log("【queryIntentActivities Hook异常】" + t.getMessage());
            }
          }
        }
      );
      // 2. 额外Hook Android 11+ 新增的 queryIntentActivitiesAsUser 方法
      try {
        XposedBridge.hookAllMethods(
          XposedHelpers.findClass(
            "android.app.ApplicationPackageManager",
            classLoader
          ),
          "queryIntentActivitiesAsUser",
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              try {
                // 提取Intent参数（兼容参数位置变化）
                Intent intent = null;
                for (Object arg : param.args) {
                  if (arg instanceof Intent) {
                    intent = (Intent) arg;
                    break;
                  }
                }
                if (intent == null) {
                  //
                  // log("【queryIntentActivitiesAsUser】未找到Intent参数，跳过Hook");
                  return;
                }
                String targetPackage = extractPackageFromIntent(intent);
                if (
                  targetPackage != null &&
                  !targetPackage.equals(currentTargetApp)
                ) {
                  handleIntentQueryForIntentHook(
                    param,
                    targetPackage,
                    "queryIntentActivitiesAsUser（兼容版）"
                  );
                }
              } catch (Throwable t) {
                log(
                  "【queryIntentActivitiesAsUser Hook异常】" + t.getMessage()
                );
              }
            }
          }
        );
        //     log("✅ 成功Hook queryIntentActivitiesAsUser（Android 11+适配）");
      } catch (Throwable e) {
        // 低版本Android无此方法，忽略（不影响核心功能）
        //       log("⚠️  设备不支持queryIntentActivitiesAsUser，跳过Hook");
      }
      //    log("✅ 成功Hook queryIntentActivities（兼容所有重载版本+Android 11+）");
    } catch (Throwable t) {
      log("Hook queryIntentActivities失败: " + t.getMessage());
    }
  }

  private void hookResolveActivity(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "resolveActivity",
        Intent.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Intent intent = (Intent) param.args[0];
            if (intent == null) {
              //   log("⚠️  空Intent，跳过ResolveActivity Hook");
              return;
            }
            String targetPackage = extractPackageFromIntent(intent);
            if (
              targetPackage != null && !targetPackage.equals(currentTargetApp)
            ) {
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;
              if (!shouldReturnInstalled) {
                // 未安装：返回空安全ResolveInfo，避免跳转崩溃
                Class<?> resolveInfoClass = Class.forName(
                  "android.content.pm.ResolveInfo"
                );
                Object emptyResolveInfo = resolveInfoClass.newInstance();
                // 设置无效标记，让目标应用识别为“无匹配Activity”
                XposedHelpers.setIntField(emptyResolveInfo, "priority", -1);
                XposedHelpers.setIntField(emptyResolveInfo, "match", 0);
                XposedHelpers.setBooleanField(
                  emptyResolveInfo,
                  "isDefault",
                  false
                );

                param.setResult(emptyResolveInfo);
                //    log("【未安装状态】返回安全空ResolveInfo: " + targetPackage);
                return;
              }
              // 已安装：原有逻辑不变
              boolean isCaptured = false;
              synchronized (globalCapturedPackages) {
                isCaptured = globalCapturedPackages.contains(targetPackage);
              }
              if (isCaptured) {
                Object fakeResolveInfo = createFakeResolveInfo(targetPackage);
                if (fakeResolveInfo != null) {
                  param.setResult(fakeResolveInfo);
                }
              }
            }
          }
        }
      );
    } catch (Throwable t) {
      log("Hook resolveActivity失败: " + t.getMessage());
    }
  }

  private void hookQueryIntentServices(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "queryIntentServices",
        Intent.class,
        int.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Intent intent = (Intent) param.args[0];
            if (intent == null) {
              //          log("⚠️  空Intent，跳过ResolveActivity Hook");
              return;
            }

            String targetPackage = extractPackageFromIntent(intent);
            if (
              targetPackage != null && !targetPackage.equals(currentTargetApp)
            ) {
              handleIntentQueryForIntentHook(
                param,
                targetPackage,
                "queryIntentServices"
              );
            }
          }
        }
      );
    } catch (Throwable t) {
      log("Hook queryIntentServices失败: " + t.getMessage());
    }
  }

  // 统一的Intent查询处理方法
  private void handleIntentQueryForIntentHook(
    MethodHookParam param,
    String targetPackage,
    String methodName
  ) {
    try {
      Boolean status = installStatusMap.get(currentTargetApp);
      boolean shouldReturnInstalled = status != null ? status : true;

      if (!shouldReturnInstalled) {
        param.setResult(new ArrayList<>());
        return;
      }

      boolean isCaptured = false;
      synchronized (globalCapturedPackages) {
        isCaptured = globalCapturedPackages.contains(targetPackage);
      }

      if (isCaptured) {
        List<Object> fakeList = new ArrayList<>();
        Object fakeResolveInfo = createFakeResolveInfo(targetPackage);
        if (fakeResolveInfo != null) {
          fakeList.add(fakeResolveInfo);
          param.setResult(fakeList);
        }
      }
    } catch (Throwable t) {}
  }

  // 从Intent中提取包名
  private String extractPackageFromIntent(Intent intent) {
    if (intent == null) {
      //        log("⚠️  空Intent，跳过ResolveActivity Hook");
      return null;
    }

    String packageName = intent.getPackage();
    if (packageName != null && isValidPackageName(packageName)) {
      return packageName;
    }

    ComponentName component = intent.getComponent();
    if (component != null) {
      packageName = component.getPackageName();
      if (isValidPackageName(packageName)) {
        return packageName;
      }
    }

    Uri data = intent.getData();
    if (data != null) {
      String scheme = data.getScheme();
      if ("package".equals(scheme)) {
        String schemeSpecificPart = data.getSchemeSpecificPart();
        if (isValidPackageName(schemeSpecificPart)) {
          return schemeSpecificPart;
        }
      }
    }

    return null;
  }

  // 创建伪造的ResolveInfo
  private Object createFakeResolveInfo(String packageName) {
    try {
      Class<?> resolveInfoClass = Class.forName(
        "android.content.pm.ResolveInfo"
      );
      Object resolveInfo = resolveInfoClass.newInstance();

      XposedHelpers.setIntField(resolveInfo, "priority", 0);
      XposedHelpers.setIntField(resolveInfo, "preferredOrder", 0);
      XposedHelpers.setIntField(resolveInfo, "match", 0x00000000);
      XposedHelpers.setBooleanField(resolveInfo, "isDefault", false);

      Object activityInfo = createFakeActivityInfo(
        packageName,
        packageName + ".MainActivity"
      );
      if (activityInfo != null) {
        XposedHelpers.setObjectField(resolveInfo, "activityInfo", activityInfo);
      }

      try {
        XposedHelpers.setObjectField(
          resolveInfo,
          "nonLocalizedLabel",
          "App: " + packageName
        );
      } catch (Throwable e) {}

      return resolveInfo;
    } catch (Throwable e) {
      return null;
    }
  }

  // ========== 命令行检测拦截 ==========
  private void hookRuntimeExecMethods(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "java.lang.Runtime",
        classLoader,
        "exec",
        String.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String command = (String) param.args[0];
            handleCommandLineDetection(param, command, "exec(String)");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.lang.Runtime",
        classLoader,
        "exec",
        String[].class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
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
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.lang.Runtime",
        classLoader,
        "exec",
        String.class,
        String[].class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String command = (String) param.args[0];
            handleCommandLineDetection(
              param,
              command,
              "exec(String, String[])"
            );
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.lang.Runtime",
        classLoader,
        "exec",
        String[].class,
        String[].class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            String[] commands = (String[]) param.args[0];
            if (commands != null && commands.length > 0) {
              StringBuilder cmdBuilder = new StringBuilder();
              for (String cmd : commands) {
                cmdBuilder.append(cmd).append(" ");
              }
              String command = cmdBuilder.toString().trim();
              handleCommandLineDetection(
                param,
                command,
                "exec(String[], String[])"
              );
            }
          }
        }
      );
    } catch (Throwable t) {
      log("Hook Runtime.exec失败: " + t.getMessage());
    }
  }

  // 处理命令行检测
  private void handleCommandLineDetection(
    MethodHookParam param,
    String command,
    String methodName
  ) {
    if (command == null) return;

    String lowerCommand = command.toLowerCase();

    if (isPackageDetectionCommand(lowerCommand)) {
      Boolean status = installStatusMap.get(currentTargetApp);
      boolean shouldReturnInstalled = status != null ? status : true;

      Object fakeProcess = createFakeProcess(command, shouldReturnInstalled);
      if (fakeProcess != null) {
        param.setResult(fakeProcess);
      }
    }
  }

  // 判断是否为包检测命令
  private boolean isPackageDetectionCommand(String command) {
    if (command == null) return false;
    String lowerCommand = command.toLowerCase();
    if (command.contains("pm ") || command.startsWith("pm ")) {
      return (
        command.contains("list packages") ||
        command.contains("path ") ||
        command.contains("dump ") ||
        command.contains("clear ") ||
        command.contains("install ") ||
        command.contains("uninstall ") ||
        command.contains("enable ") ||
        command.contains("disable ")
      );
    }

    // 2. 扩展dumpsys命令拦截
    if (command.contains("dumpsys ")) {
      return (
        command.contains("dumpsys package") ||
        command.contains("dumpsys activity") ||
        command.contains("dumpsys meminfo") ||
        command.contains("dumpsys package --check") ||
        command.contains("dumpsys package --verify") ||
        command.contains("dumpsys package --brief")
      );
    }

    // 3. 扩展cmd命令拦截（Android 11+）
    if (
      command.contains("cmd package ") || command.startsWith("cmd package ")
    ) {
      return (
        command.contains("list") ||
        command.contains("path") ||
        command.contains("dump") ||
        command.contains("--check") ||
        command.contains("--verify")
      );
    }

    if (
      command.contains("ls ") ||
      command.contains("find ") ||
      command.contains("cat ")
    ) {
      return (
        command.contains("/data/app/") ||
        command.contains("/system/app/") ||
        command.contains("/system/priv-app/") ||
        command.contains("/vendor/app/") ||
        command.contains("/product/app/") ||
        command.contains("/data/data/") ||
        command.contains("/proc/") ||
        command.contains("packages.xml") ||
        command.contains("packages.list") ||
        command.contains("packages_cache.xml")
      );
    }

    if (command.contains("getprop")) {
      return (
        command.contains("package") ||
        command.contains("app") ||
        command.contains("install")
      );
    }

    if (command.contains("ps ") || command.startsWith("ps")) {
      return (
        command.contains("| grep ") ||
        command.contains("com.") ||
        command.contains("-A") ||
        command.contains("-a")
      );
    }

    if (command.contains("which ") || command.contains("whereis ")) {
      return (
        command.contains("pm") ||
        command.contains("dumpsys") ||
        command.contains("getprop")
      );
    }

    return false;
  }

  // 创建伪造的Process对象
  private Object createFakeProcess(
    final String command,
    final boolean shouldReturnInstalled
  ) {
    try {
      final ClassLoader classLoader = getClass().getClassLoader();
      final Class<?> processClass = Class.forName("java.lang.Process");

      final String fakeOutput = generateFakeCommandOutput(
        command,
        shouldReturnInstalled
      );

      return java.lang.reflect.Proxy.newProxyInstance(
        classLoader,
        new Class<?>[] { processClass },
        new java.lang.reflect.InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            String methodName = method.getName();

            switch (methodName) {
              case "getInputStream":
                return new java.io.ByteArrayInputStream(
                  fakeOutput.getBytes("UTF-8")
                );
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
                if (method.getReturnType() == boolean.class) {
                  return false;
                } else if (method.getReturnType() == int.class) {
                  return 0;
                } else if (method.getReturnType() == long.class) {
                  return 0L;
                }
                return null;
            }
          }
        }
      );
    } catch (Throwable e) {
      return null;
    }
  }

  // 生成伪造的命令行输出
  private String generateFakeCommandOutput(
    String command,
    boolean shouldReturnInstalled
  ) {
    if (!shouldReturnInstalled) {
      if (command.contains("pm list packages")) {
        return "";
      } else if (command.contains("pm path ")) {
        return "Error: package not found";
      } else if (command.contains("dumpsys package ")) {
        return "No package found";
      }
      return "";
    }

    String lowerCommand = command.toLowerCase();

    if (lowerCommand.contains("pm list packages")) {
      StringBuilder output = new StringBuilder();
      synchronized (globalCapturedPackages) {
        for (String pkg : globalCapturedPackages) {
          output.append("package:").append(pkg).append("\n");
        }
      }
      return output.toString();
    } else if (lowerCommand.contains("pm path ")) {
      String targetPackage = extractPackageFromCommand(command);
      if (targetPackage != null) {
        synchronized (globalCapturedPackages) {
          if (globalCapturedPackages.contains(targetPackage)) {
            return (
              "package:/data/app/" +
              targetPackage.replace('.', '-') +
              "-1/base.apk\n"
            );
          }
        }
      }
      return "Error: package not found";
    } else if (lowerCommand.contains("dumpsys package ")) {
      String targetPackage = extractPackageFromCommand(command);
      if (targetPackage != null) {
        synchronized (globalCapturedPackages) {
          if (globalCapturedPackages.contains(targetPackage)) {
            return generateFakeDumpsysOutput(targetPackage);
          }
        }
      }
      return "No package found for: " + targetPackage;
    } else if (
      lowerCommand.contains("ls ") && lowerCommand.contains("/data/app/")
    ) {
      StringBuilder output = new StringBuilder();
      synchronized (globalCapturedPackages) {
        for (String pkg : globalCapturedPackages) {
          String dirName = pkg.replace('.', '-') + "-1";
          output.append(dirName).append("\n");
        }
      }
      return output.toString();
    } else if (
      lowerCommand.contains("cat ") && lowerCommand.contains("packages.xml")
    ) {
      return generateFakePackagesXml();
    } else if (lowerCommand.contains("ps ") || command.startsWith("ps")) {
      return generateFakePsOutput();
    } else if (lowerCommand.contains("getprop")) {
      return (
        "[ro.build.version.sdk]: [28]\n" +
        "[ro.product.brand]: [google]\n" +
        "[ro.product.model]: [Pixel 3]\n"
      );
    }

    return "";
  }

  // 从命令中提取包名
  private String extractPackageFromCommand(String command) {
    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
      "(?:pm\\s+path|dumpsys\\s+package)\\s+([a-zA-Z0-9._]+)"
    );
    java.util.regex.Matcher matcher = pattern.matcher(command);
    if (matcher.find()) {
      return matcher.group(1);
    }

    pattern = java.util.regex.Pattern.compile("\"([a-zA-Z0-9._]+)\"");
    matcher = pattern.matcher(command);
    if (matcher.find()) {
      return matcher.group(1);
    }

    pattern = java.util.regex.Pattern.compile("'([a-zA-Z0-9._]+)'");
    matcher = pattern.matcher(command);
    if (matcher.find()) {
      return matcher.group(1);
    }

    return null;
  }

  // 生成伪造的dumpsys输出
  private String generateFakeDumpsysOutput(String packageName) {
    long now = System.currentTimeMillis();
    return (
      "Packages:\n" +
      "  Package [" +
      packageName +
      "] (aaaaaaaa):\n" +
      "    userId=10000\n" +
      "    pkg=Package{" +
      packageName +
      "}\n" +
      "    codePath=/data/app/" +
      packageName.replace('.', '-') +
      "-1\n" +
      "    resourcePath=/data/app/" +
      packageName.replace('.', '-') +
      "-1\n" +
      "    legacyNativeLibraryDir=/data/app/" +
      packageName.replace('.', '-') +
      "-1/lib\n" +
      "    primaryCpuAbi=null\n" +
      "    secondaryCpuAbi=null\n" +
      "    versionCode=1 minSdk=21 targetSdk=28\n" +
      "    versionName=1.0.0\n" +
      "    splits=[base]\n" +
      "    apkSigningVersion=2\n" +
      "    applicationInfo=ApplicationInfo{" +
      packageName +
      "}\n" +
      "    flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]\n" +
      "    privateFlags=[ PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_RESIZEABLE ]\n" +
      "    dataDir=/data/data/" +
      packageName +
      "\n" +
      "    supportsScreens=[small, medium, large, xlarge, resizeable, anyDensity]\n" +
      "    timeStamp=" +
      (now - 86400000) +
      "\n" +
      "    firstInstallTime=" +
      (now - 86400000) +
      "\n" +
      "    lastUpdateTime=" +
      now +
      "\n" +
      "    installerPackageName=com.android.vending\n" +
      "    signatures=PackageSignatures{aaaaaaaa version:1, signatures:[aaaaaaaa], past signatures:[]}\n" +
      "    permissionsFixed=true\n" +
      "    pkgFlags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ALLOW_BACKUP ]\n"
    );
  }

  // 生成伪造的packages.xml内容
  private String generateFakePackagesXml() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n");
    xml.append("<packages>\n");

    synchronized (globalCapturedPackages) {
      int userId = 10000;
      for (String pkg : globalCapturedPackages) {
        xml
          .append("  <package name=\"")
          .append(pkg)
          .append("\" codePath=\"/data/app/")
          .append(pkg.replace('.', '-'))
          .append("-1\" nativeLibraryPath=\"/data/app/")
          .append(pkg.replace('.', '-'))
          .append("-1/lib\" primaryCpuAbi=\"arm64-v8a\" ")
          .append(
            "publicFlags=\"940834305\" privateFlags=\"0\" ft=\"16b4c\" it=\"16b4c\" "
          )
          .append("ut=\"16b4c\" version=\"1\" userId=\"")
          .append(userId++)
          .append("\">\n");
        xml.append("    <sigs count=\"1\">\n");
        xml.append("      <cert index=\"0\" key=\"fake_signature_key\" />\n");
        xml.append("    </sigs>\n");
        xml.append("    <perms />\n");
        xml.append("  </package>\n");
      }
    }

    xml.append("</packages>\n");
    return xml.toString();
  }

  // 生成伪造的进程列表
  private String generateFakePsOutput() {
    StringBuilder output = new StringBuilder();
    output.append(
      "USER      PID   PPID  VSIZE  RSS   WCHAN            PC  NAME\n"
    );
    output.append(
      "root      1     0     1234   567   SyS_epoll_ 00000000 S /init\n"
    );
    output.append(
      "system    100   1     2345   678   SyS_epoll_ 00000000 S system_server\n"
    );

    synchronized (globalCapturedPackages) {
      int pid = 2000;
      for (String pkg : globalCapturedPackages) {
        output.append(
          String.format(
            "u0_a100  %-6d 100   34567  4567  SyS_epoll_ 00000000 S %s\n",
            pid++,
            pkg
          )
        );
      }
    }

    return output.toString();
  }

  // ========== 数据目录检测补充 ==========
  private void hookFileSystemChecks(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "exists",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileExistence(param, "exists");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "isDirectory",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileExistence(param, "isDirectory");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "isFile",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileExistence(param, "isFile");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "canRead",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileExistence(param, "canRead");
          }
        }
      );
    } catch (Throwable t) {
      log("Hook文件系统检查失败: " + t.getMessage());
    }
  }

  // 统一的文件存在性检查
  private void checkAndFakeFileExistence(
    MethodHookParam param,
    String methodName
  ) {
    try {
      File file = (File) param.thisObject;
      String path = file.getAbsolutePath();

      if (
        path.startsWith("/data/data/") ||
        path.startsWith("/data/app/") ||
        path.startsWith("/data/user/") ||
        path.startsWith("/data/user_de/") ||
        path.contains("/base.apk")
      ) {
        String packageName = extractPackageNameFromPath(path);
        if (packageName != null && !packageName.equals(currentTargetApp)) {
          Boolean status = installStatusMap.get(currentTargetApp);
          boolean shouldReturnInstalled = status != null ? status : true;

          if (!shouldReturnInstalled) {
            if (
              methodName.equals("exists") ||
              methodName.equals("isDirectory") ||
              methodName.equals("isFile")
            ) {
              param.setResult(false);
            } else if (methodName.equals("canRead")) {
              param.setResult(false);
            }
            return;
          }

          boolean isCaptured = false;
          synchronized (globalCapturedPackages) {
            isCaptured = globalCapturedPackages.contains(packageName);
          }

          if (isCaptured) {
            if (path.contains("/data/data/") && !path.contains("/base.apk")) {
              if (methodName.equals("exists")) {
                param.setResult(true);
              } else if (methodName.equals("isDirectory")) {
                param.setResult(true);
              } else if (methodName.equals("isFile")) {
                param.setResult(false);
              } else if (methodName.equals("canRead")) {
                param.setResult(true);
              }
            } else if (path.contains("/base.apk")) {
              if (methodName.equals("exists")) {
                param.setResult(true);
              } else if (methodName.equals("isDirectory")) {
                param.setResult(false);
              } else if (methodName.equals("isFile")) {
                param.setResult(true);
              } else if (methodName.equals("canRead")) {
                param.setResult(true);
              }
            }
          }
        }
      }
    } catch (Throwable t) {}
  }

  // 从路径中提取包名
  private String extractPackageNameFromPath(String path) {
    try {
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "/data/data/([a-zA-Z0-9._]+)"
      );
      java.util.regex.Matcher matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(1);
      }

      pattern = java.util.regex.Pattern.compile(
        "/data/app/([a-zA-Z0-9._]+)-\\d+"
      );
      matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(1).replace('-', '.');
      }

      pattern = java.util.regex.Pattern.compile(
        "/data/user/\\d+/([a-zA-Z0-9._]+)"
      );
      matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(1);
      }
    } catch (Throwable e) {}
    return null;
  }

  // ========== 可启动检测补充 ==========
  private void hookGetLaunchIntentForPackage(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ApplicationPackageManager",
        classLoader,
        "getLaunchIntentForPackage",
        String.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            handleLaunchIntentQuery(param, "getLaunchIntentForPackage");
          }
        }
      );

      try {
        XposedHelpers.findAndHookMethod(
          "android.app.ApplicationPackageManager",
          classLoader,
          "getLaunchIntentForPackageAsUser",
          String.class,
          int.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              handleLaunchIntentQuery(param, "getLaunchIntentForPackageAsUser");
            }
          }
        );
      } catch (NoSuchMethodError e) {}
    } catch (Throwable t) {
      log("Hook启动Intent查询失败: " + t.getMessage());
    }
  }

  // 统一的启动Intent处理
  private void handleLaunchIntentQuery(
    MethodHookParam param,
    String methodName
  ) {
    try {
      String packageName = (String) param.args[0];
      if (packageName != null && !packageName.equals(currentTargetApp)) {
        Boolean status = installStatusMap.get(currentTargetApp);
        boolean shouldReturnInstalled = status != null ? status : true;

        if (!shouldReturnInstalled) {
          param.setResult(null);
          return;
        }

        boolean isCaptured = false;
        synchronized (globalCapturedPackages) {
          isCaptured = globalCapturedPackages.contains(packageName);
        }

        if (isCaptured) {
          Intent fakeIntent = new Intent(Intent.ACTION_MAIN);
          fakeIntent.addCategory(Intent.CATEGORY_LAUNCHER);
          fakeIntent.setPackage(packageName);
          fakeIntent.setComponent(
            new ComponentName(packageName, packageName + ".MainActivity")
          );
          fakeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

          param.setResult(fakeIntent);
          log(
            "【伪造启动】" + methodName + " -> 返回伪造Intent: " + packageName
          );
        }
      }
    } catch (Throwable t) {}
  }

  // ========== 启动可访问性检查 ==========
  private void hookCanStartActivity(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.ContextImpl",
        classLoader,
        "startActivity",
        Intent.class,
        Bundle.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Intent intent = (Intent) param.args[0];
            if (intent == null) {
              //          log("⚠️  空Intent，跳过ResolveActivity Hook");
              return;
            }

            String targetPackage = extractPackageFromIntent(intent);
            if (
              targetPackage != null && !targetPackage.equals(currentTargetApp)
            ) {
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;

              if (!shouldReturnInstalled) {
                param.setThrowable(
                  new android.content.ActivityNotFoundException(
                    "No Activity found to handle " + intent
                  )
                );
              } else {
                boolean isCaptured = false;
                synchronized (globalCapturedPackages) {
                  isCaptured = globalCapturedPackages.contains(targetPackage);
                }

                if (isCaptured) {
                  log("【启动伪装】允许启动伪造应用: " + targetPackage);
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
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Intent intent = (Intent) param.args[0];
              if (intent == null) {
                //       log("⚠️  空Intent，跳过ResolveActivity Hook");
                return;
              }

              String targetPackage = extractPackageFromIntent(intent);
              if (
                targetPackage != null && !targetPackage.equals(currentTargetApp)
              ) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;

                if (!shouldReturnInstalled) {
                  param.setThrowable(
                    new android.content.ActivityNotFoundException(
                      "No Activity found to handle " + intent
                    )
                  );
                } else {
                  boolean isCaptured = false;
                  synchronized (globalCapturedPackages) {
                    isCaptured = globalCapturedPackages.contains(targetPackage);
                  }

                  if (isCaptured) {
                    log(
                      "【启动伪装】startActivity(Intent) -> 允许启动: " +
                      targetPackage
                    );
                  }
                }
              }
            }
          }
        );
      } catch (Throwable t) {}

      try {
        XposedHelpers.findAndHookMethod(
          "android.app.Activity",
          classLoader,
          "startActivityForResult",
          Intent.class,
          int.class,
          Bundle.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              Intent intent = (Intent) param.args[0];
              if (intent == null) {
                //   log("⚠️  空Intent，跳过ResolveActivity Hook");
                return;
              }

              String targetPackage = extractPackageFromIntent(intent);
              if (
                targetPackage != null && !targetPackage.equals(currentTargetApp)
              ) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;

                if (!shouldReturnInstalled) {
                  final Activity activity = (Activity) param.thisObject;
                  final int requestCode = (int) param.args[1];

                  new Handler(Looper.getMainLooper()).postDelayed(
                    new Runnable() {
                      @Override
                      public void run() {
                        try {
                          XposedHelpers.callMethod(
                            activity,
                            "onActivityResult",
                            requestCode,
                            Activity.RESULT_CANCELED,
                            (Intent) null
                          );
                        } catch (Throwable e) {}
                      }
                    },
                    300
                  );

                  param.setResult(null);
                } else {
                  boolean isCaptured = false;
                  synchronized (globalCapturedPackages) {
                    isCaptured = globalCapturedPackages.contains(targetPackage);
                  }

                  if (isCaptured) {
                    log(
                      "【启动伪装】startActivityForResult -> 允许启动: " +
                      targetPackage
                    );
                  }
                }
              }
            }
          }
        );
      } catch (Throwable t) {}
    } catch (Throwable t) {
      log("Hook启动可访问性检查失败: " + t.getMessage());
    }
  }

  // ========== Lib目录检测补充 ==========
  private void hookLibDirectoryChecks(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "exists",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileExistence(param, "exists");
            checkAndFakeLibExistence(param, "exists");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "isDirectory",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileExistence(param, "isDirectory");
            checkAndFakeLibExistence(param, "isDirectory");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "list",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileListing(param, "list");
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "listFiles",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            checkAndFakeFileListing(param, "listFiles");
          }
        }
      );
    } catch (Throwable t) {
      log("Hook Lib目录检测失败: " + t.getMessage());
    }
  }

  // ========== Lib目录检测专用方法 ==========
  private void checkAndFakeLibExistence(
    MethodHookParam param,
    String methodName
  ) {
    try {
      File file = (File) param.thisObject;
      String path = file.getAbsolutePath();

      if (isLibDirectoryPath(path)) {
        String packageName = extractPackageNameFromLibPath(path);
        if (packageName != null && !packageName.equals(currentTargetApp)) {
          Boolean status = installStatusMap.get(currentTargetApp);
          boolean shouldReturnInstalled = status != null ? status : true;

          if (!shouldReturnInstalled) {
            if (
              methodName.equals("exists") || methodName.equals("isDirectory")
            ) {
              param.setResult(false);
            }
            return;
          }

          boolean isCaptured = false;
          synchronized (globalCapturedPackages) {
            isCaptured = globalCapturedPackages.contains(packageName);
          }

          if (isCaptured) {
            if (methodName.equals("exists")) {
              param.setResult(true);
            } else if (methodName.equals("isDirectory")) {
              param.setResult(true);
            }
          }
        }
      }
    } catch (Throwable t) {}
  }

  // Lib目录列表检测
  private void checkAndFakeFileListing(
    MethodHookParam param,
    String methodName
  ) {
    try {
      File file = (File) param.thisObject;
      String path = file.getAbsolutePath();

      if (isLibDirectoryPath(path)) {
        String packageName = extractPackageNameFromLibPath(path);
        if (packageName != null && !packageName.equals(currentTargetApp)) {
          Boolean status = installStatusMap.get(currentTargetApp);
          boolean shouldReturnInstalled = status != null ? status : true;

          if (!shouldReturnInstalled) {
            if (methodName.equals("list")) {
              param.setResult(new String[0]);
            } else if (methodName.equals("listFiles")) {
              param.setResult(new File[0]);
            }
            return;
          }

          boolean isCaptured = false;
          synchronized (globalCapturedPackages) {
            isCaptured = globalCapturedPackages.contains(packageName);
          }

          if (isCaptured) {
            if (methodName.equals("list")) {
              String[] fakeLibs = createFakeLibList(path);
              param.setResult(fakeLibs);
            } else if (methodName.equals("listFiles")) {
              File[] fakeFiles = createFakeLibFiles(path);
              param.setResult(fakeFiles);
            }
          }
        }
      }
    } catch (Throwable t) {}
  }

  // 判断是否是lib目录路径
  private boolean isLibDirectoryPath(String path) {
    if (path == null) return false;

    return (
      path.contains("/lib/") ||
      path.contains("/lib64/") ||
      path.contains("/lib/arm") ||
      path.contains("/lib/arm64") ||
      path.contains("/lib/x86") ||
      path.endsWith("/lib") ||
      path.contains("app-lib/")
    );
  }

  // 从lib路径中提取包名
  private String extractPackageNameFromLibPath(String path) {
    try {
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "/data/app/([a-zA-Z0-9._]+)-\\d+/lib/"
      );
      java.util.regex.Matcher matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(1).replace('-', '.');
      }

      pattern = java.util.regex.Pattern.compile(
        "/data/data/([a-zA-Z0-9._]+)/lib/"
      );
      matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(1);
      }

      pattern = java.util.regex.Pattern.compile(
        "/data/app-lib/([a-zA-Z0-9._]+)"
      );
      matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(1);
      }
    } catch (Throwable e) {}
    return null;
  }

  // 创建伪造的lib文件列表
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

  // 创建伪造的lib文件对象
  private File[] createFakeLibFiles(String libPath) {
    String[] libNames = createFakeLibList(libPath);
    File[] files = new File[libNames.length];

    for (int i = 0; i < libNames.length; i++) {
      files[i] = new File(libPath, libNames[i]);
    }

    return files;
  }

  // ========== Flutter相关适配 ==========
  private void hookFlutterPackageInfoPlus(ClassLoader classLoader) {
    try {
      String[] targetClasses = {
        "dev.fluttercommunity.plus.packageinfo.PackageInfoPlugin",
        "io.flutter.plugins.packageinfo.PackageInfoPlugin",
      };

      for (String className : targetClasses) {
        try {
          Class<?> pluginClass = XposedHelpers.findClassIfExists(
            className,
            classLoader
          );
          if (pluginClass != null) {
            XposedHelpers.findAndHookMethod(
              pluginClass,
              "handleMethodCall",
              "io.flutter.plugin.common.MethodCall",
              "io.flutter.plugin.common.Result",
              new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                  throws Throwable {
                  try {
                    Object methodCall = param.args[0];
                    Object result = param.args[1];
                    if (methodCall == null || result == null) return;

                    String methodName = XposedHelpers.callMethod(
                      methodCall,
                      "method"
                    ).toString();

                    if (
                      "getPackageInfo".equals(methodName) ||
                      "getAllPackageInfo".equals(methodName)
                    ) {
                      String targetPackage = null;
                      Object arguments = XposedHelpers.callMethod(
                        methodCall,
                        "arguments"
                      );

                      if (arguments instanceof String) {
                        targetPackage = (String) arguments;
                      } else if (arguments instanceof Map) {
                        targetPackage = (String) ((Map) arguments).get(
                            "packageName"
                          );
                      }

                      Boolean status = installStatusMap.get(currentTargetApp);
                      boolean shouldReturnInstalled = status != null
                        ? status
                        : true;

                      if (!shouldReturnInstalled) {
                        Map<String, Object> errorMap = new HashMap<>();
                        errorMap.put("error", "Package not found");
                        XposedHelpers.callMethod(
                          result,
                          "error",
                          "NOT_FOUND",
                          "Package not found",
                          errorMap
                        );
                        param.setResult(null);
                        return;
                      }

                      Object fakeResult = createFakePackageInfoPlusResult(
                        targetPackage
                      );
                      if (fakeResult != null) {
                        XposedHelpers.callMethod(result, "success", fakeResult);
                        param.setResult(null);
                      }
                    }
                  } catch (Throwable t) {}
                }
              }
            );

            return;
          }
        } catch (Throwable t) {}
      }
    } catch (Throwable t) {
      log("适配 package_info_plus 插件失败: " + t.getMessage());
    }
  }

  private void hookFlutterAppInstalledChecker(ClassLoader classLoader) {
    try {
      String[] targetClasses = {
        "com.javih.addtoapp.AppInstalledCheckerPlugin",
        "com.example.appinstalledchecker.AppInstalledCheckerPlugin",
        "app.installed.checker.AppInstalledCheckerPlugin",
      };

      for (String className : targetClasses) {
        try {
          Class<?> pluginClass = XposedHelpers.findClassIfExists(
            className,
            classLoader
          );
          if (pluginClass != null) {
            XposedHelpers.findAndHookMethod(
              pluginClass,
              "onMethodCall",
              "io.flutter.plugin.common.MethodCall",
              "io.flutter.plugin.common.Result",
              new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param)
                  throws Throwable {
                  try {
                    Object methodCall = param.args[0];
                    Object result = param.args[1];
                    if (methodCall == null || result == null) return;

                    String methodName = XposedHelpers.callMethod(
                      methodCall,
                      "method"
                    ).toString();

                    if (
                      "isAppInstalled".equals(methodName) ||
                      "areAppsInstalled".equals(methodName)
                    ) {
                      Boolean status = installStatusMap.get(currentTargetApp);
                      boolean shouldReturnInstalled = status != null
                        ? status
                        : true;

                      if (!shouldReturnInstalled) {
                        if ("isAppInstalled".equals(methodName)) {
                          XposedHelpers.callMethod(result, "success", false);
                        } else if ("areAppsInstalled".equals(methodName)) {
                          Map<String, Boolean> fakeResultMap = new HashMap<>();
                          Object arguments = XposedHelpers.callMethod(
                            methodCall,
                            "arguments"
                          );
                          if (arguments instanceof ArrayList) {
                            ArrayList<String> targetPackages = (ArrayList<
                                String
                              >) arguments;
                            for (String pkg : targetPackages) {
                              fakeResultMap.put(pkg, false);
                            }
                          }
                          XposedHelpers.callMethod(
                            result,
                            "success",
                            fakeResultMap
                          );
                        }
                        param.setResult(null);
                        return;
                      }

                      Object arguments = XposedHelpers.callMethod(
                        methodCall,
                        "arguments"
                      );
                      if (
                        "isAppInstalled".equals(methodName) &&
                        arguments instanceof String
                      ) {
                        String targetPackage = (String) arguments;
                        boolean fakeInstalled = globalCapturedPackages.contains(
                          targetPackage
                        );
                        XposedHelpers.callMethod(
                          result,
                          "success",
                          fakeInstalled
                        );
                        param.setResult(null);
                      } else if (
                        "areAppsInstalled".equals(methodName) &&
                        arguments instanceof ArrayList
                      ) {
                        ArrayList<String> targetPackages = (ArrayList<
                            String
                          >) arguments;
                        Map<String, Boolean> fakeResultMap = new HashMap<>();
                        for (String pkg : targetPackages) {
                          fakeResultMap.put(
                            pkg,
                            globalCapturedPackages.contains(pkg)
                          );
                        }
                        XposedHelpers.callMethod(
                          result,
                          "success",
                          fakeResultMap
                        );
                        param.setResult(null);
                      }
                    }
                  } catch (Throwable t) {}
                }
              }
            );

            return;
          }
        } catch (Throwable t) {}
      }
    } catch (Throwable t) {
      log("适配 app_installed_checker 插件失败: " + t.getMessage());
    }
  }

  // ========== Flutter MethodChannel 检测拦截 ==========
  private void hookFlutterMethodChannelCheck(final ClassLoader classLoader) {
    try {
    // 先判断 Flutter 核心类是否存在，不存在则跳过
     Class<?> flutterClass = XposedHelpers.findClassIfExists("io.flutter.embedding.engine.dart.DartMessenger", classLoader);
      // 先判断是否为Flutter应用（避免无效Hook）
      if (!isFlutterApp(classLoader)) {
        //   log("⚠️ 非Flutter应用，跳过MethodChannel Hook");
        return;
      }

      // 1. Hook Flutter 3.10+ 底层通信：DartMessenger.send
      Class<?> dartMessengerClass = XposedHelpers.findClassIfExists(
        "io.flutter.embedding.engine.dart.DartMessenger",
        classLoader
      );
      if (dartMessengerClass != null) {
        XposedHelpers.findAndHookMethod(
          dartMessengerClass,
          "send",
          int.class, // messageId
          "io.flutter.plugin.common.BinaryMessenger$BinaryMessage", // message
          "io.flutter.plugin.common.BinaryMessenger$BinaryReply", // reply
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              handleFlutterBinaryMessage(param, classLoader);
            }
          }
        );
      }

      // 2. Hook 旧版 MethodChannel.invokeMethod（兼容Flutter 3.10以下）
      Class<?> methodChannelClass = XposedHelpers.findClassIfExists(
        "io.flutter.plugin.common.MethodChannel",
        classLoader
      );
      if (methodChannelClass != null) {
        // Hook invokeMethod（同步调用）
        XposedHelpers.findAndHookMethod(
          methodChannelClass,
          "invokeMethod",
          String.class,
          Object.class,
          new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param)
              throws Throwable {
              handleFlutterMethodCall(
                (String) param.args[0],
                param.args[1],
                param
              );
            }
          }
        );

        // Hook setMethodCallHandler（异步调用）
        XposedHelpers.findAndHookMethod(
          methodChannelClass,
          "setMethodCallHandler",
          "io.flutter.plugin.common.MethodChannel$MethodCallHandler",
          new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param)
              throws Throwable {
              Object handler = param.args[0];
              if (handler == null) return;
              // 拦截handler的onMethodCall
              XposedHelpers.findAndHookMethod(
                handler.getClass(),
                "onMethodCall",
                "io.flutter.plugin.common.MethodCall",
                "io.flutter.plugin.common.Result",
                new XC_MethodHook() {
                  @Override
                  protected void beforeHookedMethod(
                    MethodHookParam handlerParam
                  ) throws Throwable {
                    Object methodCall = handlerParam.args[0];
                    Object result = handlerParam.args[1];
                    String methodName = XposedHelpers.callMethod(
                      methodCall,
                      "method"
                    ).toString();
                    Object arguments = XposedHelpers.callMethod(
                      methodCall,
                      "arguments"
                    );
                    // 处理检测调用并返回结果
                    Object fakeResult = handleFlutterMethodCall(
                      methodName,
                      arguments,
                      null
                    );
                    if (fakeResult != null) {
                      XposedHelpers.callMethod(result, "success", fakeResult);
                      handlerParam.setResult(null); // 阻断
                    }
                  }
                }
              );
            }
          }
        );
      }
      // log("✅ 已Hook Flutter MethodChannel检测");
    } catch (Throwable t) {
      log("Hook FlutterMeth失败: " + t.getMessage());
    }
  }

  // 处理Flutter BinaryMessage（3.10+）
  private void handleFlutterBinaryMessage(
    MethodHookParam param,
    ClassLoader classLoader
  ) {
    try {
      Object message = param.args[1];
      if (message == null) return;
      // 解析BinaryMessage中的MethodCall数据
      byte[] messageData = (byte[]) XposedHelpers.callMethod(
        message,
        "getData"
      );
      if (messageData == null || messageData.length == 0) return;

      // 反序列化MethodCall
      Class<?> methodCallClass = XposedHelpers.findClass(
        "io.flutter.plugin.common.MethodCall",
        classLoader
      );
      Object methodCall = XposedHelpers.newInstance(methodCallClass);
      XposedHelpers.callStaticMethod(
        methodCallClass,
        "decode",
        messageData,
        methodCall
      );

      String methodName = (String) XposedHelpers.getObjectField(
        methodCall,
        "method"
      );
      Object arguments = XposedHelpers.getObjectField(methodCall, "arguments");

      // 处理检测调用
      Object fakeResult = handleFlutterMethodCall(methodName, arguments, param);
      if (fakeResult != null) {
        // 发送伪造响应
        sendFlutterBinaryReply(param.args[2], fakeResult, classLoader);
        param.setResult(null); // 阻断
      }
    } catch (Throwable t) {
      log("处理FlutterBina失败: " + t.getMessage());
    }
  }

  // 处理Flutter MethodCall
  private Object handleFlutterMethodCall(
    String methodName,
    Object arguments,
    MethodHookParam param
  ) {
    // 匹配常见的Flutter包检测方法名
    if (
      methodName.contains("isAppInstalled") ||
      methodName.contains("checkAppInstalled") ||
      methodName.contains("getPackageInfo") ||
      methodName.contains("queryInstalledPackages")
    ) {
      Boolean status = installStatusMap.get(currentTargetApp);
      boolean shouldReturnInstalled = status != null ? status : true;
      String targetPkg = extractPackageFromFlutterArgs(arguments);

      if (methodName.contains("isAppInstalled")) {
        // 单个包检测：未安装返回false，已安装返回true
        boolean result =
          shouldReturnInstalled &&
          targetPkg != null &&
          globalCapturedPackages.contains(targetPkg);
        //   log("【Flutter检测拦截】" + methodName + "(" + targetPkg + ") -> " + result);
        return result;
      } else if (methodName.contains("getPackageInfo")) {
        // 包信息查询：未安装返回null，已安装返回伪造信息
        Object fakeInfo = shouldReturnInstalled && targetPkg != null
          ? createFakeFlutterPackageInfo(targetPkg)
          : null;
        //   log("【Flutter检测拦截】" + methodName + "(" + targetPkg + ") -> " + (fakeInfo !=
        // null ? "伪造信息" : "null"));
        return fakeInfo;
      } else if (methodName.contains("queryInstalledPackages")) {
        // 应用列表查询：未安装返回空列表，已安装返回捕获包列表
        List<Map<String, Object>> fakeList = new ArrayList<>();
        if (shouldReturnInstalled) {
          synchronized (globalCapturedPackages) {
            for (String pkg : globalCapturedPackages) {
              fakeList.add(createFakeFlutterPackageInfo(pkg));
            }
          }
        }
        //  log("【Flutter检测拦截】" + methodName + " -> " + fakeList.size() + "个包");
        return fakeList;
      }
    }
    return null; // 非检测方法，不拦截
  }

  // 辅助方法：从Flutter参数中提取包名
  private String extractPackageFromFlutterArgs(Object arguments) {
    if (arguments instanceof String) {
      return (String) arguments;
    } else if (arguments instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) arguments;
      return map.get("packageName") != null
        ? map.get("packageName").toString()
        : null;
    }
    return null;
  }

  // 辅助方法：创建伪造的Flutter包信息
  private Map<String, Object> createFakeFlutterPackageInfo(String packageName) {
    Map<String, Object> fakeInfo = new HashMap<>();
    fakeInfo.put("packageName", packageName);
    fakeInfo.put("appName", generateSmartAppName(packageName)); // 复用原有智能名称生成
    fakeInfo.put("versionName", generateSmartVersion(packageName)); // 复用智能版本生成
    fakeInfo.put(
      "versionCode",
      generateSmartVersionCode(
        packageName,
        fakeInfo.get("versionName").toString()
      )
    );
    fakeInfo.put("installTime", generateSmartInstallTime(packageName));
    fakeInfo.put("isInstalled", true);
    return fakeInfo;
  }

  // 辅助方法：给Flutter BinaryReply发送伪造响应
  private void sendFlutterBinaryReply(
    Object reply,
    Object fakeResult,
    ClassLoader classLoader
  ) {
    try {
      if (reply == null) return;
      // 序列化伪造结果为Flutter可识别的Binary数据
      Class<?> resultClass = XposedHelpers.findClass(
        "io.flutter.plugin.common.Result",
        classLoader
      );
      Class<?> binaryCodecClass = XposedHelpers.findClass(
        "io.flutter.plugin.common.BinaryCodec",
        classLoader
      );
      Object binaryCodec = XposedHelpers.newInstance(binaryCodecClass);
      Object successResult = XposedHelpers.callStaticMethod(
        resultClass,
        "success",
        fakeResult
      );
      byte[] resultData = (byte[]) XposedHelpers.callMethod(
        binaryCodec,
        "encode",
        successResult
      );
      // 发送响应
      XposedHelpers.callMethod(reply, "reply", resultData);
    } catch (Throwable t) {
      log("发送Flutter伪造响应失败: " + t.getMessage());
    }
  }

  // ========== 反射调用PackageManager检测拦截 ==========
  private void hookReflectInstallCheck(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "java.lang.reflect.Method",
        classLoader,
        "invoke",
        Object.class,
        Object[].class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            Method method = (Method) param.thisObject;
            String methodName = method.getName();
            Class<?> declaringClass = method.getDeclaringClass();

            // 1. 过滤处理PackageManager的包查询方法
            boolean isPackageManagerClass =
              declaringClass
                .getName()
                .equals("android.content.pm.PackageManager") ||
              declaringClass
                .getName()
                .equals("android.app.ApplicationPackageManager");
            boolean isPackageQueryMethod =
              methodName.equals("getPackageInfo") ||
              methodName.equals("getApplicationInfo");

            // 非目标调用，直接放行
            if (!isPackageManagerClass || !isPackageQueryMethod) {
              return;
            }

            // 2. 提取包名，跳过重试包、系统包、空包名
            String targetPkg = extractPackageFromReflectArgs(param.args);
            if (
              targetPkg == null ||
              isSystemCorePackage(targetPkg) ||
              isReflectRetryInvocation()
            ) {
              //    log("【反射拦截】放行包: " + (targetPkg == null ? "null" : targetPkg));
              return;
            }

            // 3. 按安装状态返回结果
            Boolean status = installStatusMap.get(currentTargetApp);
            boolean shouldReturnInstalled = status != null ? status : true;
            if (!shouldReturnInstalled) {
              param.setResult(null);
              //   log("【反射调用拦截】未安装 -> 返回null: " + methodName + "(" + targetPkg + ")");
            } else {
              int flags = extractFlagsFromReflectArgs(method, param.args);
              Object fakeResult = methodName.equals("getPackageInfo")
                ? createSmartFakePackageInfo(targetPkg, flags)
                : createSmartApplicationInfo(targetPkg, flags);
              param.setResult(fakeResult);
              // log("【反射调用拦截】已安装 -> 伪造信息: " + methodName + "(" + targetPkg + ")");
            }
          }
        }
      );
      //log("✅ 已Hook反射调用PackageManager检测（终极无冲突版）");
    } catch (Throwable t) {
      log("Hook反射检测失败: " + t.getMessage());
    }
  }

  // 保留所有原有辅助方法
  private boolean isReflectRetryInvocation() {
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    int pkgManagerCallCount = 0;
    for (StackTraceElement element : stackTrace) {
      if (
        (element.getClassName().equals("android.content.pm.PackageManager") ||
          element
            .getClassName()
            .equals("android.app.ApplicationPackageManager")) &&
        (element.getMethodName().equals("getPackageInfo") ||
          element.getMethodName().equals("getApplicationInfo"))
      ) {
        pkgManagerCallCount++;
        if (pkgManagerCallCount >= 2) {
          return true;
        }
      }
    }
    return false;
  }

  private Method createFakePackageManagerMethod(
    Class<?> clazz,
    String methodName
  ) {
    try {
      if (methodName.equals("getPackageInfo")) {
        return clazz.getMethod("getPackageInfo", String.class, int.class);
      } else if (methodName.equals("getApplicationInfo")) {
        return clazz.getMethod("getApplicationInfo", String.class, int.class);
      } else if (methodName.equals("getInstalledPackages")) {
        return clazz.getMethod("getInstalledPackages", int.class);
      } else {
        return clazz.getMethod(methodName);
      }
    } catch (NoSuchMethodException e) {
      return null;
    }
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
    if (
      method.getParameterTypes().length == 2 &&
      method.getParameterTypes()[1] == int.class
    ) {
      for (Object arg : methodArgs) {
        if (arg instanceof Integer) {
          return (int) arg;
        }
      }
    }
    return 0;
  }

  // ========== 文件路径验证检测拦截 ==========
  private void hookFileSystemInstallCheck(ClassLoader classLoader) {
  
    try {
    
      // Hook File.exists() - 拦截安装路径存在性检测
      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "exists",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            File file = (File) param.thisObject;
            String path = file.getAbsolutePath();
            // 仅拦截应用安装相关路径
            if (isAppInstallPath(path)) {
              String targetPkg = extractPackageFromPath(path);
              if (
                targetPkg != null &&
                !targetPkg.equals(currentTargetApp) &&
                !isSystemCorePackage(targetPkg)
              ) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;
                // 未安装状态返回false（路径不存在），已安装返回true
                param.setResult(shouldReturnInstalled);
                //  log("【文件检测拦截】路径: " + path + " -> 返回" +
                // shouldReturnInstalled);
              }
            }
          }
        }
      );


      // Hook File.isDirectory() - 拦截目录验证
      XposedHelpers.findAndHookMethod(
        "java.io.File",
        classLoader,
        "isDirectory",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            File file = (File) param.thisObject;
            String path = file.getAbsolutePath();
            if (isAppInstallPath(path)) {
              String targetPkg = extractPackageFromPath(path);
              if (
                targetPkg != null &&
                !targetPkg.equals(currentTargetApp) &&
                !isSystemCorePackage(targetPkg)
              ) {
                Boolean status = installStatusMap.get(currentTargetApp);
                param.setResult(status != null ? status : true);
              }
            }
          }
        }
      );
      
      //    log("✅ 已Hook文件路径验证检测（exists/isDirectory）");
    } catch (Throwable t) {
      log("Hook文件路径检测失败: " + t.getMessage());
    }
  }

  // 辅助方法：判断是否为应用安装路径
  private boolean isAppInstallPath(String path) {
    return (
      path.startsWith("/data/data/") ||
      path.startsWith("/data/app/") ||
      path.startsWith("/data/user/") ||
      path.startsWith("/data/user_de/") ||
      path.contains("/base.apk") ||
      path.endsWith("/lib") ||
      path.endsWith("/lib64") ||
      path.contains("/system/app/") ||
      path.contains("/system/priv-app/")
    );
  }

  // 辅助方法：从路径中提取包名
  private String extractPackageFromPath(String path) {
    try {
    /*
      // 匹配 /data/data/包名、/data/app/包名-1、/data/user/0/包名 等格式
      java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
        "/data/(data|app|user/\\d+|user_de/\\d+)/([a-zA-Z0-9._]+)"
      );
      
      java.util.regex.Matcher matcher = pattern.matcher(path);
      if (matcher.find()) {
        return matcher.group(2);
      }
      */
              java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "/data/app/([a-zA-Z0-9._]+)-\\d+"
        );
        java.util.regex.Matcher matcher = pattern.matcher(path);
        if (matcher.find()) {
            return matcher.group(1).replace('-', '.');
        }
    } catch (Throwable t) {}
    return null;
  }
  

  // ========== OkHttp网络Hook ==========
  private void hookOkHttp(ClassLoader classLoader) {
    try {
      Class<?> realCallClass = XposedHelpers.findClassIfExists(
        "okhttp3.RealCall",
        classLoader
      );
      if (realCallClass == null) {
        return;
      }

      XposedHelpers.findAndHookMethod(
        realCallClass,
        "enqueue",
        "okhttp3.Callback",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Object requestObj = XposedHelpers.getObjectField(
                param.thisObject,
                "originalRequest"
              );
              if (requestObj == null) return;

              String url = XposedHelpers.callMethod(
                requestObj,
                "url"
              ).toString();

              if (isDetectionUrl(url)) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;

                if (!shouldReturnInstalled) {
                  Object callback = param.args[0];
                  fakeOkHttpResponse(callback, url, false);
                  param.setResult(null);
                  return;
                }

                Object callback = param.args[0];
                fakeOkHttpResponse(callback, url, true);
                param.setResult(null);
              }
            } catch (Throwable t) {}
          }
        }
      );

      XposedHelpers.findAndHookMethod(
        realCallClass,
        "execute",
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Object requestObj = XposedHelpers.getObjectField(
                param.thisObject,
                "originalRequest"
              );
              if (requestObj == null) return;

              String url = XposedHelpers.callMethod(
                requestObj,
                "url"
              ).toString();

              if (isDetectionUrl(url)) {
                Boolean status = installStatusMap.get(currentTargetApp);
                boolean shouldReturnInstalled = status != null ? status : true;

                if (!shouldReturnInstalled) {
                  Object fakeResponse = createOkHttpFakeResponse(false);
                  param.setResult(fakeResponse);
                  return;
                }

                Object fakeResponse = createOkHttpFakeResponse(true);
                param.setResult(fakeResponse);
              }
            } catch (Throwable t) {}
          }
        }
      );
    } catch (Throwable t) {
      log("Hook OkHttp失败: " + t.getMessage());
    }
  }

  // 辅助方法1：校验包名是否真实安装（系统原生查询，不受模块伪造影响）
  private boolean isPackageReallyInstalled(String packageName) {
    try {
      Context context = (Context) XposedHelpers.callStaticMethod(
        XposedHelpers.findClass("android.app.ActivityThread", null),
        "currentApplication"
      );
      PackageManager pm = context.getPackageManager();
      pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    } catch (Throwable t) {
      return false;
    }
  }

  // 辅助方法2：判断包名是否在捕获列表中（手动+自动添加）
  private boolean isPackageInCapturedList(String packageName) {
    synchronized (globalCapturedPackages) {
      return globalCapturedPackages.contains(packageName);
    }
  }

  // 应用启动拦截
  private void hookStartActivity(ClassLoader classLoader) {
    try {
        XposedHelpers.findAndHookMethod(
            "android.app.Instrumentation",
            classLoader,
            "execStartActivity",
            Context.class,
            IBinder.class,
            IBinder.class,
            Activity.class,
            Intent.class,
            int.class,
            Bundle.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    final Intent intent = (Intent) param.args[4];
                    if (intent == null) return;

                    // ========== 1. 常用App登录授权过滤（放行） ==========
                    String[] authAppPackages = {
                        // 国内常用
                        "com.tencent.mm", // 微信
                        "com.qq.mobileqq", // QQ
                        "com.alipay.mobile.securitycommon", // 支付宝
                        "com.sina.weibo", // 微博
                        "com.baidu.account", // 百度账号
                        "com.tencent.wework", // 企业微信
                        "com.dingtalk", // 钉钉
                        "com.bytedance.ss.android.ugc.aweme", // 抖音
                        "com.xiaomi.account", // 小米账号
                        "com.huawei.hwid", // 华为账号
                        "com.oppo.account", // OPPO账号
                        "com.vivo.account", // VIVO账号
                        "com.meizu.account", // 魅族账号
                        "com.lenovo.account", // 联想账号
                        "com.samsung.android.mobileservice", // 三星账号
                        // 国外常用
                        "com.google.android.gms", // Google 登录
                        "com.facebook.katana", // Facebook
                        "com.twitter.android", // Twitter
                        "com.instagram.android", // Instagram
                        "com.linkedin.android", // LinkedIn
                        "com.pinterest", // Pinterest
                        "com.tumblr", // Tumblr
                        "com.microsoft.office.officehubrow", // Microsoft
                        "com.apple.android.music", // Apple 登录
                        // 其他授权类App
                        "com.auth0.lock", // Auth0
                        "com.okta.mobile", // Okta
                        "com.paypal.android.p2pmobile", // PayPal
                    };

                    // ========== 2. 登录授权相关类名关键词过滤（放行） ==========
                    String[] authClassKeywords = {
                        "Login", "Auth", "Authorization", "OAuth", "OAuth2",
                        "LoginActivity", "AuthActivity", "AuthorizeActivity",
                        "Wechat", "QQAuth", "WeiboAuth", "AlipayAuth",
                        "GoogleAuth", "FacebookAuth", "TwitterAuth",
                        "AccountLogin", "UserLogin", "SignIn", "SignUp",
                        "Sso", "SingleSignOn", "OauthCallback"
                    };

                    // ========== 3. 网页授权平台过滤（放行） ==========
                    String[] webAuthSchemes = {
                        "http", "https", "oauth", "oauth2", "openid",
                        "github", "git", "google", "facebook", "twitter",
                        "linkedin", "instagram", "pinterest", "tumblr",
                        "microsoft", "apple", "wechat", "qq", "weibo", "alipay"
                    };

                    String[] webAuthHosts = {
                        // GitHub 相关
                        "github.com", "github.io", "oauth.github.com", "accounts.github.com",
                        // Google 相关
                        "google.com", "accounts.google.com", "oauth2.googleapis.com",
                        // Facebook 相关
                        "facebook.com", "fb.com", "accounts.facebook.com",
                        // Twitter 相关
                        "twitter.com", "api.twitter.com", "auth.twitter.com",
                        // 其他国外平台
                        "linkedin.com", "www.linkedin.com", "api.linkedin.com",
                        "instagram.com", "www.instagram.com",
                        "pinterest.com", "www.pinterest.com",
                        "tumblr.com", "www.tumblr.com",
                        "microsoft.com", "login.live.com", "account.live.com",
                        "apple.com", "appleid.apple.com",
                        "auth0.com", "okta.com",
                        // 国内平台
                        "weixin110.qq.com", "open.weixin.qq.com", // 微信开放平台
                        "graph.qq.com", "open.mobile.qq.com", // QQ开放平台
                        "api.weibo.com", "open.weibo.com", // 微博开放平台
                        "open.alipay.com", "auth.alipay.com", // 支付宝开放平台
                        "openapi.baidu.com", "passport.baidu.com", // 百度开放平台
                        "developers.dingtalk.com", "oapi.dingtalk.com", // 钉钉开放平台
                        "qyapi.weixin.qq.com", // 企业微信开放平台
                        "openapi.bytedance.com", // 字节跳动开放平台
                    };

                    // ========== 4. 过滤逻辑执行 ==========
                    String targetPkg = null;
                    ComponentName cn = intent.getComponent();

                    // 4.1 组件名过滤（类名包含授权关键词 → 放行）
                    if (cn != null) {
                        targetPkg = cn.getPackageName();
                        String className = cn.getClassName();
                        for (String keyword : authClassKeywords) {
                            if (className.contains(keyword)) {
                                return;
                            }
                        }
                    } else {
                        targetPkg = intent.getPackage();
                    }

                    // 4.2 包名过滤（属于授权App → 放行）
                    if (targetPkg != null) {
                        for (String authPkg : authAppPackages) {
                            if (targetPkg.equals(authPkg) || targetPkg.startsWith(authPkg)) {
                                return;
                            }
                        }
                    }

                    // 4.3 网页授权过滤（Scheme/Host 匹配 → 放行）
                    Uri data = intent.getData();
                    if (data != null) {
                        String scheme = data.getScheme();
                        String host = data.getHost();

                        // Scheme 匹配网页/授权协议
                        if (scheme != null) {
                            for (String authScheme : webAuthSchemes) {
                                if (authScheme.equalsIgnoreCase(scheme)) {
                                    return;
                                }
                            }
                        }

                        // Host 匹配授权域名
                        if (host != null) {
                            for (String authHost : webAuthHosts) {
                                if (host.contains(authHost)) {
                                    return;
                                }
                            }
                        }
                    }

                    // ========== 5. 原有伪造启动逻辑 ==========
                    if (intent.hasExtra("__hook_skip_flag")) {
                        return;
                    }

                    boolean isInternal = false;
                    boolean isSpecificActivity = (cn != null);
                    if (cn != null) {
                        Context selfContext = (Context) param.args[0];
                        if (targetPkg != null && targetPkg.equals(selfContext.getPackageName())) {
                            isInternal = true;
                        }
                    }

                    final String pkg = targetPkg;
                    final boolean finalIsInternal = isInternal;
                    final boolean finalIsSpecificAct = isSpecificActivity;
                    if (pkg == null) return;

                    // 仅对捕获列表中的非授权App执行伪造逻辑
                    if (!isPackageInCapturedList(pkg) || !isPackageReallyInstalled(pkg)) {
                        return;
                    }

                    param.setResult(null);
                    final Intent oriIntent = new Intent(intent);
                    final Activity activity = (Activity) param.args[3];
                    final int reqCode = (int) param.args[5];

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            String[] btnArray;
                            String msg = "当前应用想要打开应用\n" + pkg + "\n\n请问是否需要伪造启动？";
                            if (finalIsInternal || finalIsSpecificAct) {
                                btnArray = new String[]{"虚假启动", "取消"};
                            } else {
                                btnArray = new String[]{"虚假启动", "真实打开", "取消"};
                            }

                            AlertDialog dialog = createBoundedDialog(
                                activity,
                                "启动确认",
                                msg,
                                btnArray,
                                new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            try {
                                                if (reqCode != 0 && activity != null && !activity.isFinishing()) {
                                                    java.lang.reflect.Method m = Activity.class.getDeclaredMethod(
                                                        "onActivityResult",
                                                        int.class, int.class, Intent.class
                                                    );
                                                    m.setAccessible(true);
                                                    m.invoke(activity, reqCode, Activity.RESULT_OK, null);
                                                }
                                            } catch (Throwable ignored) {}
                                            Toast.makeText(activity, "已伪造启动：" + pkg, Toast.LENGTH_SHORT).show();
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            if (finalIsInternal || finalIsSpecificAct) {
                                                try {
                                                    if (reqCode != 0 && activity != null && !activity.isFinishing()) {
                                                        java.lang.reflect.Method m = Activity.class.getDeclaredMethod(
                                                            "onActivityResult",
                                                            int.class, int.class, Intent.class
                                                        );
                                                        m.setAccessible(true);
                                                        m.invoke(activity, reqCode, Activity.RESULT_CANCELED, null);
                                                    }
                                                } catch (Throwable ignored) {}
                                                Toast.makeText(activity, "已取消启动：" + pkg, Toast.LENGTH_SHORT).show();
                                            } else {
                                                try {
                                                    Intent newIntent = new Intent(oriIntent);
                                                    newIntent.putExtra("__hook_skip_flag", true);
                                                    activity.startActivity(newIntent);
                                                    Toast.makeText(activity, "已真实打开：" + pkg, Toast.LENGTH_SHORT).show();
                                                } catch (Throwable e) {
                                                    log("真实启动异常: " + e.getMessage());
                                                    Toast.makeText(activity, "启动失败（应用限制）", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                            try {
                                                if (reqCode != 0 && activity != null && !activity.isFinishing()) {
                                                    java.lang.reflect.Method m = Activity.class.getDeclaredMethod(
                                                        "onActivityResult",
                                                        int.class, int.class, Intent.class
                                                    );
                                                    m.setAccessible(true);
                                                    m.invoke(activity, reqCode, Activity.RESULT_CANCELED, null);
                                                }
                                            } catch (Throwable ignored) {}
                                            Toast.makeText(activity, "已取消启动：" + pkg, Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }
                            );
                            dialog.show();
                        }
                    });
                }
            }
        );
    } catch (Throwable e) {
        log("hookStartActivity 异常: " + e.getMessage());
    }
}









  /*
  // ========== 应用启动拦截 ==========
  private void hookStartActivity(ClassLoader classLoader) {
    try {
      XposedHelpers.findAndHookMethod(
        "android.app.Instrumentation",
        classLoader,
        "execStartActivity",
        Context.class,
        IBinder.class,
        IBinder.class,
        Activity.class,
        Intent.class,
        int.class,
        Bundle.class,
        new XC_MethodHook() {
          @Override
          protected void beforeHookedMethod(MethodHookParam param)
            throws Throwable {
            try {
              Intent intent = (Intent) param.args[4];

              Bundle bundle = (Bundle) param.args[6];

              // 通用空值校验：避免传递空Intent或空Bundle
              if (intent == null) {
                //         log("⚠️  拦截到空Intent，跳过Hook处理");
                return;
              }
              if (bundle == null) {
                // 若Bundle为空，创建默认空Bundle避免目标应用崩溃
                param.args[6] = new Bundle();
                //   log("⚠️  补全空Bundle，避免目标应用空指针");
              }
              if (intent == null) {
                //  log("⚠️  空Intent，跳过ResolveActivity Hook");
                return;
              }

              String targetPackageName = null;

              ComponentName component = intent.getComponent();
              if (component != null) {
                targetPackageName = component.getPackageName();
              } else if (intent.getPackage() != null) {
                targetPackageName = intent.getPackage();
              }

              if (
                targetPackageName != null &&
                !targetPackageName.equals(currentTargetApp)
              ) {
                boolean isCapturedApp = false;
                synchronized (globalCapturedPackages) {
                  isCapturedApp = globalCapturedPackages.contains(
                    targetPackageName
                  );
                }

                if (isCapturedApp) {
                  log(
                    "【启动拦截】目标应用尝试打开伪造应用: " + targetPackageName
                  );

                  Boolean statusConfig = installStatusMap.get(currentTargetApp);
                  boolean shouldReturnInstalled = statusConfig != null
                    ? statusConfig
                    : true;

                  if (shouldReturnInstalled) {
                    log("【启动拦截】当前配置为[已安装]，伪装启动成功");

                    final int requestCode = (int) param.args[5];
                    final Activity targetActivity = (Activity) param.args[3];

                    if (requestCode != 0 && targetActivity != null) {
                      new Handler(Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                          @Override
                          public void run() {
                            try {
                              XposedHelpers.callMethod(
                                targetActivity,
                                "onActivityResult",
                                requestCode,
                                Activity.RESULT_OK,
                                (Intent) null
                              );
                            } catch (Throwable e) {
                              log(
                                "【启动拦截】发送回调失败: " + e.getMessage()
                              );
                            }
                          }
                        },
                        300
                      );
                    }

                    param.setResult(null);
                    return;
                  }
                }
              }
            } catch (Throwable t) {}
          }
        }
      );
    } catch (Throwable t) {
      log("Hook startActivity失败: " + t.getMessage());
    }
  }
  */

  // ========== 辅助工具方法 ==========
  private boolean isDetectionUrl(String url) {
    return (
      url.contains("checkInstall") ||
      url.contains("appDetect") ||
      url.contains("packageCheck") ||
      url.contains("umeng/app/check") ||
      url.contains("jiguang/detect") ||
      url.contains("appInstalled") ||
      url.contains("verifyApp")
    );
  }

  private void fakeOkHttpResponse(
    Object callback,
    String url,
    boolean isInstalled
  ) {
    try {
      ClassLoader cl = callback.getClass().getClassLoader();
      Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", cl);
      Class<?> responseBuilderClass = XposedHelpers.findClass(
        "okhttp3.Response$Builder",
        cl
      );
      Class<?> mediaTypeClass = XposedHelpers.findClass(
        "okhttp3.MediaType",
        cl
      );
      Class<?> requestBodyClass = XposedHelpers.findClass(
        "okhttp3.RequestBody",
        cl
      );

      Object jsonMediaType = XposedHelpers.callStaticMethod(
        mediaTypeClass,
        "parse",
        "application/json; charset=utf-8"
      );
      String fakeJson;

      if (isInstalled) {
        fakeJson =
          "{\"code\":200,\"msg\":\"success\",\"isInstalled\":true,\"data\":{\"packageList\":" +
          globalCapturedPackages.toString() +
          "}}";
      } else {
        fakeJson =
          "{\"code\":404,\"msg\":\"app not installed\",\"isInstalled\":false}";
      }

      Object fakeBody = XposedHelpers.callStaticMethod(
        requestBodyClass,
        "create",
        jsonMediaType,
        fakeJson
      );

      Object fakeResponse = XposedHelpers.newInstance(responseBuilderClass);
      fakeResponse = XposedHelpers.callMethod(
        fakeResponse,
        "code",
        isInstalled ? 200 : 404
      );
      fakeResponse = XposedHelpers.callMethod(fakeResponse, "body", fakeBody);
      fakeResponse = XposedHelpers.callMethod(fakeResponse, "build");

      XposedHelpers.callMethod(callback, "onResponse", null, fakeResponse);
    } catch (Exception e) {}
  }

  private Object createOkHttpFakeResponse(boolean isInstalled) {
    try {
      ClassLoader cl = ClassLoader.getSystemClassLoader();
      Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", cl);
      Class<?> responseBuilderClass = XposedHelpers.findClass(
        "okhttp3.Response$Builder",
        cl
      );
      Class<?> mediaTypeClass = XposedHelpers.findClass(
        "okhttp3.MediaType",
        cl
      );
      Class<?> requestBodyClass = XposedHelpers.findClass(
        "okhttp3.RequestBody",
        cl
      );

      Object jsonMediaType = XposedHelpers.callStaticMethod(
        mediaTypeClass,
        "parse",
        "application/json; charset=utf-8"
      );
      String fakeJson;

      if (isInstalled) {
        fakeJson = "{\"code\":200,\"msg\":\"检测通过\",\"isInstalled\":true}";
      } else {
        fakeJson =
          "{\"code\":404,\"msg\":\"应用未安装\",\"isInstalled\":false}";
      }

      Object fakeBody = XposedHelpers.callStaticMethod(
        requestBodyClass,
        "create",
        jsonMediaType,
        fakeJson
      );
      Object fakeResponse = XposedHelpers.newInstance(responseBuilderClass);
      fakeResponse = XposedHelpers.callMethod(
        fakeResponse,
        "code",
        isInstalled ? 200 : 404
      );
      fakeResponse = XposedHelpers.callMethod(fakeResponse, "body", fakeBody);
      return XposedHelpers.callMethod(fakeResponse, "build");
    } catch (Exception e) {
      return null;
    }
  }

  private String extractPackageFromChannelArgs(Object arguments) {
    if (arguments instanceof String) {
      return (String) arguments;
    } else if (arguments instanceof Map) {
      Map<?, ?> map = (Map<?, ?>) arguments;
      if (map.get("packageName") != null) {
        return (String) map.get("packageName");
      } else if (map.get("pkg") != null) {
        return (String) map.get("pkg");
      }
    }
    return null;
  }

  private Object buildChannelFakeResult(
    String methodName,
    String targetPackage,
    Object arguments
  ) {
    if (methodName.contains("isInstalled")) {
      if (arguments instanceof ArrayList) {
        Map<String, Boolean> resultMap = new HashMap<>();
        for (String pkg : (ArrayList<String>) arguments) {
          resultMap.put(pkg, globalCapturedPackages.contains(pkg));
        }
        return resultMap;
      } else {
        return globalCapturedPackages.contains(targetPackage);
      }
    } else if (methodName.contains("getPackage")) {
      return createFakePackageInfoPlusResult(targetPackage);
    }
    return null;
  }

  private Object createFakePackageInfoPlusResult(String targetPackage) {
    try {
      Map<String, Object> fakeMap = new HashMap<>();
      if (targetPackage == null) {
        ArrayList<Map<String, Object>> allPackages = new ArrayList<>();
        for (String pkg : globalCapturedPackages) {
          allPackages.add(buildSinglePackageInfo(pkg));
        }
        fakeMap.put("packages", allPackages);
      } else {
        fakeMap.putAll(buildSinglePackageInfo(targetPackage));
      }
      fakeMap.put(
        "appName",
        targetPackage == null ? "All Apps" : ("App: " + targetPackage)
      );
      fakeMap.put(
        "packageName",
        targetPackage == null ? "multiple" : targetPackage
      );
      fakeMap.put("version", "1.0.0");
      fakeMap.put("buildNumber", "1");
      fakeMap.put("buildSignature", "fake_signature");
      fakeMap.put("installerPackageName", "com.android.vending");
      return fakeMap;
    } catch (Exception e) {
      return null;
    }
  }

  private Map<String, Object> buildSinglePackageInfo(String packageName) {
    Map<String, Object> pkgMap = new HashMap<>();
    pkgMap.put("packageName", packageName);
    pkgMap.put("appName", "App: " + packageName);
    pkgMap.put("version", "1.0.0");
    pkgMap.put("buildNumber", "1");
    pkgMap.put("installTime", System.currentTimeMillis() - 86400000);
    pkgMap.put("updateTime", System.currentTimeMillis());
    return pkgMap;
  }

// ========== 内嵌模式专用方法（完美兼容分身 + 空指针防护） ==========
public void initForEmbed(
    ClassLoader classLoader,
    String targetPackageName,
    Context context
) {

// ========== 🚨【WebView子线程崩溃专杀】==========
try {
    // Hook WebView 的所有子线程创建，直接吞掉空指针
    XposedHelpers.findAndHookMethod(
        "android.webkit.WebView",
        null,
        "onDraw",
        android.graphics.Canvas.class,
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // 什么都不做，只是防止崩溃
            }
        }
    );
} catch (Throwable ignored) {}

try {
    // Hook Chromium 内核的崩溃点
    XposedHelpers.findAndHookMethod(
        "org.chromium.android_webview.AwContents",
        null,
        "destroy",
        new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                return null; // 防止销毁时崩溃
            }
        }
    );
} catch (Throwable ignored) {}
// ========== 🚨【终极暴力修复】Hook所有Bundle方法（可编译版）==========
try {
    Class<?> bundleClass = Class.forName("android.os.Bundle");
    
    // 使用 findAndHookMethod 逐个 Hook 关键方法，而不是 hookAllMethods
    String[] bundleMethods = {
        "getString", "getInt", "getBoolean", "getLong", 
        "getDouble", "getFloat", "getBundle", "getSerializable",
        "getParcelable", "containsKey"
    };
    
    for (final String methodName : bundleMethods) {
        try {
            // Hook 单参数版本
            XposedHelpers.findAndHookMethod(
                bundleClass,
                methodName,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject == null) {
                            setDefaultReturnValue(param);
                            return;
                        }
                        // 检测Bundle是否可用
                        try {
                            ((Bundle) param.thisObject).hashCode();
                        } catch (Throwable e) {
                            setDefaultReturnValue(param);
                        }
                    }
                    
                    private void setDefaultReturnValue(MethodHookParam param) {
                        if (methodName.equals("getString")) param.setResult("");
                        else if (methodName.equals("getInt")) param.setResult(0);
                        else if (methodName.equals("getBoolean")) param.setResult(false);
                        else if (methodName.equals("getLong")) param.setResult(0L);
                        else if (methodName.equals("getDouble")) param.setResult(0.0);
                        else if (methodName.equals("getFloat")) param.setResult(0.0f);
                        else if (methodName.equals("getBundle")) param.setResult(new Bundle());
                        else param.setResult(null);
                    }
                }
            );
        } catch (Throwable ignored) {}
        
        try {
            // Hook 双参数版本（带默认值）
            XposedHelpers.findAndHookMethod(
                bundleClass,
                methodName,
                String.class,
                Object.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.thisObject == null) {
                            param.setResult(param.args[1]);
                            return;
                        }
                        try {
                            Bundle b = (Bundle) param.thisObject;
                            if (!b.containsKey((String) param.args[0])) {
                                param.setResult(param.args[1]);
                            }
                        } catch (Throwable e) {
                            param.setResult(param.args[1]);
                        }
                    }
                }
            );
        } catch (Throwable ignored) {}
    }
    
    // 特别处理 getString(String, String) 确保万无一失
    try {
        XposedHelpers.findAndHookMethod(
            bundleClass,
            "getString",
            String.class,
            String.class,
            new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject == null) {
                        param.setResult(param.args[1]);
                        return;
                    }
                    try {
                        Bundle b = (Bundle) param.thisObject;
                        if (!b.containsKey((String) param.args[0])) {
                            param.setResult(param.args[1]);
                        }
                    } catch (Throwable e) {
                        param.setResult(param.args[1]);
                    }
                }
            }
        );
    } catch (Throwable ignored) {}
    
    //XposedBridge.log("[InstallHook] ✅ Bundle暴力修复完成（可编译版）");
    
} catch (Throwable e) {
    //XposedBridge.log("[InstallHook] ❌ Bundle暴力修复失败: " + e.getMessage());
}
    
    // ========== 🚨【强制全局异常捕获】==========
try {
    // 先设置我们自己的Handler
    final Thread.UncaughtExceptionHandler ourHandler = new Thread.UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            if (e instanceof NullPointerException) {
                StackTraceElement[] stack = e.getStackTrace();
                if (stack.length > 0) {
                    String className = stack[0].getClassName();
                    if (className.contains("Bundle") || 
                        className.contains("PackageManager") ||
                        className.contains("ContextImpl") ||
                        className.contains("ApplicationPackageManager")) {
                        //XposedBridge.log("[InstallHook] 💪 拦截崩溃: " +    className + "." + stack[0].getMethodName());
                        return;
                    }
                }
            }
            // 交给系统默认Handler
            Thread.UncaughtExceptionHandler defaultHandler = 
                Thread.getDefaultUncaughtExceptionHandler();
            if (defaultHandler != null && defaultHandler != this) {
                defaultHandler.uncaughtException(t, e);
            }
        }
    };
    
    // 设置我们的Handler
    Thread.setDefaultUncaughtExceptionHandler(ourHandler);
    
    // Hook setDefaultUncaughtExceptionHandler 防止被覆盖
    XposedHelpers.findAndHookMethod(
        "java.lang.Thread",
        null,
        "setDefaultUncaughtExceptionHandler",
        Thread.UncaughtExceptionHandler.class,
        new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // 不允许覆盖我们的Handler
                //XposedBridge.log("[InstallHook] ⚠️ 阻止应用设置自己的异常处理器");
                param.setResult(null);
            }
        }
    );
    
    //XposedBridge.log("[InstallHook] ✅ 强制全局异常捕获已启用");
    
} catch (Throwable e) {
    //XposedBridge.log("[InstallHook] ❌ 强制异常捕获失败: " + e.getMessage());
}
    
    // ========== 🚨【内嵌核心修复1】全局空指针保护（加到最前面）==========
    try {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                // 只处理空指针
                if (e instanceof NullPointerException) {
                    StackTraceElement[] stack = e.getStackTrace();
                    if (stack.length > 0) {
                        String className = stack[0].getClassName();
                        String methodName = stack[0].getMethodName();
                        
                        // 只要是我们Hook的包，全部吞掉
                        if (className.contains("android.os.Bundle") ||
                            className.contains("android.content.pm.PackageManager") ||
                            className.contains("android.app.ApplicationPackageManager") ||
                            className.contains("android.app.Activity") ||
                            className.contains("android.content.Context") ||
                            className.contains("PackageManager") ||
                            className.contains("Bundle")) {
                            
                          //  log("🛡️【内嵌模式】拦截子线程空指针: " +  className + "." + methodName);
                            return; // 不崩溃
                        }
                    }
                }
                
                // 其他崩溃交给系统
                Thread.UncaughtExceptionHandler defaultHandler = 
                    Thread.getDefaultUncaughtExceptionHandler();
                if (defaultHandler != null && defaultHandler != this) {
                    defaultHandler.uncaughtException(t, e);
                }
            }
        });
        log("✅【内嵌模式】全局空指针保护已启用");
    } catch (Throwable ignored) {}
    
    // ========== 🚨【内嵌核心修复2】获取真实Context和ClassLoader ==========
    Context realContext = context;
    ClassLoader realClassLoader = classLoader;
    String realPackageName = targetPackageName;
    
    // 2.1 优先使用传入的Context，但尝试获取更真实的实例
    if (realContext == null) {
        try {
            realContext = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentApplication"
            );
        } catch (Throwable e) {
            log("【内嵌模式】获取ActivityThread Context失败: " + e.getMessage());
        }
    }
    
    // 2.2 如果还是null，尝试通过反射获取
    if (realContext == null) {
        try {
            realContext = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.AppGlobals", null),
                "getInitialApplication"
            );
        } catch (Throwable e) {
            log("【内嵌模式】获取AppGlobals失败: " + e.getMessage());
        }
    }
    
    // 2.3 获取真实包名和ClassLoader
    if (realContext != null) {
        try {
            realPackageName = realContext.getPackageName();
            realClassLoader = realContext.getClassLoader();
            log("✅【内嵌模式】获取真实信息: " + realPackageName);
        } catch (Throwable e) {
            log("❌【内嵌模式】获取真实信息失败: " + e.getMessage());
        }
    }
    
    // 2.4 ClassLoader降级方案
    if (realClassLoader == null) {
        if (classLoader != null) {
            realClassLoader = classLoader;
            log("✅【内嵌模式】使用传入ClassLoader");
        } else if (realContext != null) {
            realClassLoader = realContext.getClassLoader();
            log("✅【内嵌模式】从Context获取ClassLoader");
        } else {
            realClassLoader = getClass().getClassLoader();
            log("✅【内嵌模式】使用自身ClassLoader作为最后回退");
        }
    }
    
    // 2.5 包名降级方案
    if (realPackageName == null || realPackageName.isEmpty()) {
        realPackageName = targetPackageName != null ? targetPackageName : "unknown";
        log("✅【内嵌模式】使用传入包名: " + realPackageName);
    }
    
    // ========== 🚨【内嵌核心修复3】主动修复so路径（不等任何回调）==========
    if (realContext != null) {
        try {
            ApplicationInfo ai = realContext.getApplicationInfo();
            if (ai != null) {
                // 强制重置nativeLibraryDir，触发系统重新计算正确路径
                XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                log("✅【内嵌模式】主动修复.so路径: " + ai.nativeLibraryDir);
            }
        } catch (Throwable e) {
            log("❌【内嵌模式】主动修复.so路径失败: " + e.getMessage());
            
            // 兜底方案：通过PackageManager再试一次
            try {
                PackageManager pm = realContext.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(realPackageName, 0);
                if (ai != null) {
                    XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                    log("✅【内嵌模式】兜底修复.so路径: " + ai.nativeLibraryDir);
                }
            } catch (Throwable t) {
                log("❌【内嵌模式】兜底修复失败: " + t.getMessage());
            }
        }
    }
    
    // ========== 🚨【内嵌核心修复4】主动加载配置（不等任何回调）==========
    currentTargetApp = realPackageName;
    try {
        loadConfigFromFile();
        log("✅【内嵌模式】主动加载配置完成: " + currentTargetApp);
    } catch (Throwable e) {
        log("❌【内嵌模式】主动加载配置失败: " + e.getMessage());
        createDefaultConfig();
    }
    
    // ========== 初始化日志 ==========
    log("==========================================");
    log("【内嵌模式】目标包名: " + realPackageName);
    log("【内嵌模式】Context: " + (realContext != null ? "有效" : "无效"));
    log("【内嵌模式】ClassLoader: " + (realClassLoader != null ? "有效" : "无效"));
    log("==========================================");
    
    // ========== 系统包过滤 ==========
    if (isSystemPackage(currentTargetApp) || 
        currentTargetApp.equals("com.install.appinstall.xl")) {
        log("✅【内嵌模式】跳过系统包或自身包: " + currentTargetApp);
        return;
    }
    
    // ========== 初始化配置状态（确保默认值）==========
    try {
        if (!installStatusMap.containsKey(currentTargetApp)) {
            installStatusMap.put(currentTargetApp, true);
            log("✅【内嵌模式】初始化安装状态为: 已安装");
        }
        if (!floatingShownMap.containsKey(currentTargetApp)) {
            floatingShownMap.put(currentTargetApp, true);
            log("✅【内嵌模式】初始化悬浮窗状态为: 显示");
        }
        if (!blockExitMap.containsKey(currentTargetApp)) {
            blockExitMap.put(currentTargetApp, false);
            log("✅【内嵌模式】初始化退出拦截状态为: 关闭");
        }
        if (!permissionFakeMap.containsKey(currentTargetApp)) {
            permissionFakeMap.put(currentTargetApp, true);
            log("✅【内嵌模式】初始化权限伪造状态为: 开启");
        }
        if (!userDefinedPackagesMap.containsKey(currentTargetApp)) {
            userDefinedPackagesMap.put(currentTargetApp, new ArrayList<>());
        }
        if (!excludedPackagesMap.containsKey(currentTargetApp)) {
            excludedPackagesMap.put(currentTargetApp, new ArrayList<>());
        }
        
        saveConfigToFile();
        log("✅【内嵌模式】初始配置已保存");
    } catch (Throwable t) {
        log("❌【内嵌模式】配置初始化失败: " + t.getMessage());
    }
    
    // ========== 🚨【内嵌核心修复5】先执行Bundle防护（防止刚Hook就崩溃）==========
    try {
        // 先修复Bundle空指针问题
        hookBundleGetString(realClassLoader);
        hookBundleEmptyInstance(realClassLoader);
        log("✅【内嵌模式】Bundle空指针防护完成");
    } catch (Throwable t) {
        log("❌【内嵌模式】Bundle防护失败: " + t.getMessage());
    }
    
    // ========== 🚨【内嵌核心修复6】所有Hook方法必须使用真实ClassLoader ==========
    try {
        log("【内嵌模式】开始执行Hook方法");
        
        // 第1级：基础环境准备（Bundle已经Hook过了）
        log("✅【内嵌模式】基础环境Hook完成");
        
        // 第2级：权限伪造（关键防御）
        try {
            hookQueryAllPackagesPermission(realClassLoader);
            hookPackageUsageStatsPermission(realClassLoader);
            log("✅【内嵌模式】权限伪造Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】权限伪造Hook失败: " + t.getMessage());
        }
        
        // 第3级：退出拦截（用户体验保障）
        try {
            hookExitMethods(realClassLoader);
            hookIndirectExitMethods(realClassLoader);
            hookGlobalExitSources(realClassLoader);
            hookRunnableSystemExit(realClassLoader);
            log("✅【内嵌模式】退出拦截Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】退出拦截Hook失败: " + t.getMessage());
        }
        
        // 第4级：包管理核心伪造（数据层）
        try {
            hookKeyMethods(realClassLoader);
            hookOverloadMethods(realClassLoader);
            hookIsApplicationEnabled(realClassLoader);
            hookCheckPermission(realClassLoader);
            hookGetActivityInfo(realClassLoader);
            log("✅【内嵌模式】包管理核心Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】包管理核心Hook失败: " + t.getMessage());
        }
        
        // 第5级：Intent相关伪造（应用层）
        try {
            hookQueryIntentActivities(realClassLoader);
            hookResolveActivity(realClassLoader);
            hookQueryIntentServices(realClassLoader);
            log("✅【内嵌模式】Intent相关Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】Intent相关Hook失败: " + t.getMessage());
        }
        
        // 第6级：启动拦截
        try {
            hookStartActivity(realClassLoader);
            log("✅【内嵌模式】启动拦截Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】启动拦截Hook失败: " + t.getMessage());
        }
        
        // 第7级：OkHttp网络和跨平台框架
        try {
            hookOkHttp(realClassLoader);
            hookFlutterPackageInfoPlus(realClassLoader);
            hookFlutterAppInstalledChecker(realClassLoader);
            hookFileSystemInstallCheck(realClassLoader);
            hookReflectInstallCheck(realClassLoader);
            hookFlutterMethodChannelCheck(realClassLoader);
            log("✅【内嵌模式】网络/跨平台Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】网络/跨平台Hook失败: " + t.getMessage());
        }
        
        // 第8级：UI和用户体验
        try {
            hookDialogCancelableMethods(realClassLoader);
            hookActivityLifecycle(realClassLoader);
            log("✅【内嵌模式】UI相关Hook完成");
        } catch (Throwable t) {
            log("❌【内嵌模式】UI相关Hook失败: " + t.getMessage());
        }
        
        log("🎉【内嵌模式】所有Hook执行成功: " + currentTargetApp);
        
        // 显示初始化完成提示
        showInitSuccessToast(realContext);
        
    } catch (Throwable t) {
        log("❌【内嵌模式】Hook执行失败: " + t.getMessage());
        t.printStackTrace();
        showInitFailToast(realContext, t.getMessage());
    }
}


/*
  // ========== 辅助方法：创建代理Context ==========
  private Context createProxyContext(final String packageName) {
    try {
      // 使用动态代理创建Context
      Class<?> contextClass = Class.forName("android.content.Context");
      Context proxyContext = (Context) java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class<?>[] { contextClass },
        new java.lang.reflect.InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            String methodName = method.getName();

            // 处理关键方法
            switch (methodName) {
              case "getPackageName":
                return packageName;
              case "getClassLoader":
                return getClass().getClassLoader();
              case "getApplicationContext":
                return proxy; // 返回自身
              case "getPackageManager":
                // 创建代理PackageManager
                return createProxyPackageManager(packageName);
              case "getSystemService":
                if (args.length > 0 && args[0] instanceof String) {
                  String serviceName = (String) args[0];
                  if (Context.ACTIVITY_SERVICE.equals(serviceName)) {
                    return null;
                  }
                }
                return null;
              default:
                // 对于其他方法，返回默认值
                if (method.getReturnType() == boolean.class) {
                  return false;
                } else if (method.getReturnType() == int.class) {
                  return 0;
                } else if (method.getReturnType() == String.class) {
                  return "";
                }
                return null;
            }
          }
        }
      );

      return proxyContext;
    } catch (Throwable t) {
      log("❌ 创建代理Context失败: " + t.getMessage());
      return null;
    }
  }
  */

  // ========== 辅助方法：创建代理PackageManager ==========
  private Object createProxyPackageManager(String packageName) {
    try {
      Class<?> packageManagerClass = Class.forName(
        "android.content.pm.PackageManager"
      );
      return java.lang.reflect.Proxy.newProxyInstance(
        getClass().getClassLoader(),
        new Class<?>[] { packageManagerClass },
        new java.lang.reflect.InvocationHandler() {
          @Override
          public Object invoke(Object proxy, Method method, Object[] args)
            throws Throwable {
            String methodName = method.getName();

            // 处理包查询相关方法
            if (
              methodName.startsWith("getPackage") ||
              methodName.startsWith("getApplication") ||
              methodName.contains("Installed")
            ) {
              // 记录查询请求
              log("【代理PackageManager】拦截方法: " + methodName);

              // 根据安装状态返回结果
              Boolean status = installStatusMap.get(currentTargetApp);
              boolean shouldReturnInstalled = status != null ? status : true;

              if (!shouldReturnInstalled) {
                return createEmptyResult(method);
              }

              // 返回伪造结果
              return createFakeResultForMethod(method, args);
            }

            // 其他方法返回默认值
            return createDefaultReturnValue(method);
          }
        }
      );
    } catch (Throwable t) {
      log("❌ 创建代理PackageManager失败: " + t.getMessage());
      return null;
    }
  }

  // ========== 辅助方法：按优先级执行Hook ==========
  private void executePriorityHooks(ClassLoader classLoader) {
    try {
      // 第1优先级：基础环境准备
      hookBundleGetString(classLoader);
      hookBundleEmptyInstance(classLoader);
      log("✅【内嵌模式】基础环境Hook完成");

      // 第2优先级：权限伪造
      hookQueryAllPackagesPermission(classLoader);
      hookPackageUsageStatsPermission(classLoader);
      log("✅【内嵌模式】权限伪造Hook完成");

      // 第3优先级：退出拦截
      hookExitMethods(classLoader);
      hookIndirectExitMethods(classLoader);
      hookGlobalExitSources(classLoader);
      hookRunnableSystemExit(classLoader);
      log("✅【内嵌模式】退出拦截Hook完成");

      // 第4优先级：包管理核心
      hookKeyMethods(classLoader);
      hookOverloadMethods(classLoader);
      log("✅【内嵌模式】包管理Hook完成");

      // 第5优先级：Intent相关
      hookQueryIntentActivities(classLoader);
      hookResolveActivity(classLoader);
      hookQueryIntentServices(classLoader);
      log("✅【内嵌模式】Intent相关Hook完成");
/*
      // 第6优先级：启动相关
      hookGetLaunchIntentForPackage(classLoader);
      hookCanStartActivity(classLoader);
      */
      hookStartActivity(classLoader);
      log("✅【内嵌模式】启动相关Hook完成");
/*
      // 第7优先级：文件系统
      hookFileSystemChecks(classLoader);
      hookLibDirectoryChecks(classLoader);
      hookRuntimeExecMethods(classLoader);
      log("✅【内嵌模式】文件系统Hook完成");
*/
      // 第8优先级：网络相关
      hookOkHttp(classLoader);
      log("✅【内嵌模式】网络Hook完成");
    } catch (Throwable t) {
      log("❌【内嵌模式】优先级Hook执行失败: " + t.getMessage());
    }
  }

  // ========== 辅助方法：执行Flutter相关Hook ==========
  private void executeFlutterHooks(ClassLoader classLoader) {
    try {
      hookFlutterPackageInfoPlus(classLoader);
      hookFlutterAppInstalledChecker(classLoader);
      hookFileSystemInstallCheck(classLoader); // 文件路径验证
      hookReflectInstallCheck(classLoader); // 反射PackageManager
      hookFlutterMethodChannelCheck(classLoader); // Flutter MethodChannel
      log("✅【内嵌模式】Flutter适配Hook完成");
    } catch (Throwable t) {
      log("❌【内嵌模式】Flutter Hook执行失败: " + t.getMessage());
    }
  }

  // ========== 辅助方法：执行UI相关Hook ==========
  private void executeUIHooks(ClassLoader classLoader) {
    try {
      hookDialogCancelableMethods(classLoader);
      hookActivityLifecycle(classLoader);
      log("✅【内嵌模式】UI相关Hook完成");
    } catch (Throwable t) {
      log("❌【内嵌模式】UI Hook执行失败: " + t.getMessage());
    }
  }

  // ========== 辅助方法：为方法创建空结果 ==========
  private Object createEmptyResult(Method method) {
    Class<?> returnType = method.getReturnType();

    if (returnType == List.class) {
      return Collections.emptyList();
    } else if (
      returnType == PackageInfo.class ||
      returnType == ApplicationInfo.class ||
      returnType == ActivityInfo.class
    ) {
      return null;
    } else if (returnType == boolean.class) {
      return false;
    } else if (returnType == int.class) {
      return 0;
    } else if (returnType == String.class) {
      return "";
    }

    return null;
  }

  // ========== 辅助方法：为方法创建伪造结果 ==========
  private Object createFakeResultForMethod(Method method, Object[] args) {
    String methodName = method.getName();

    if (
      methodName.contains("getPackageInfo") &&
      args.length > 0 &&
      args[0] instanceof String
    ) {
      String packageName = (String) args[0];
      int flags = args.length > 1 && args[1] instanceof Integer
        ? (int) args[1]
        : 0;
      return createSmartFakePackageInfo(packageName, flags);
    } else if (
      methodName.contains("getApplicationInfo") &&
      args.length > 0 &&
      args[0] instanceof String
    ) {
      String packageName = (String) args[0];
      return createFakeApplicationInfo(packageName);
    } else if (methodName.contains("getInstalledPackages")) {
      List<PackageInfo> fakeList = new ArrayList<>();
      synchronized (globalCapturedPackages) {
        for (String pkg : globalCapturedPackages) {
          fakeList.add(createFakePackageInfo(pkg));
        }
      }
      return fakeList;
    }

    return createDefaultReturnValue(method);
  }

  // ========== 辅助方法：创建默认返回值 ==========
  private Object createDefaultReturnValue(Method method) {
    Class<?> returnType = method.getReturnType();

    if (returnType == boolean.class) {
      return true;
    } else if (returnType == int.class) {
      return 0;
    } else if (returnType == long.class) {
      return 0L;
    } else if (returnType == String.class) {
      return "";
    } else if (returnType == List.class) {
      return Collections.emptyList();
    } else if (returnType == PackageManager.class) {
      // 返回代理PackageManager
      return createProxyPackageManager(currentTargetApp);
    }

    return null;
  }

  // ========== 辅助方法：显示初始化成功Toast ==========
  private void showInitSuccessToast(final Context context) {
    if (context == null) return;

    try {
      new Handler(Looper.getMainLooper()).post(
        new Runnable() {
          @Override
          public void run() {
            try {
              Toast.makeText(
                context,
                "✅ 安装伪造模块已激活\n目标应用: " + currentTargetApp,
                Toast.LENGTH_LONG
              ).show();
            } catch (Throwable t) {
              log("❌ 显示Toast失败: " + t.getMessage());
            }
          }
        }
      );
    } catch (Throwable t) {
      log("❌ 发送Toast消息失败: " + t.getMessage());
    }
  }

  // ========== 辅助方法：显示初始化失败Toast ==========
  private void showInitFailToast(final Context context, final String errorMsg) {
    if (context == null) return;

    try {
      new Handler(Looper.getMainLooper()).post(
        new Runnable() {
          @Override
          public void run() {
            try {
              Toast.makeText(
                context,
                "❌ 模块初始化失败\n" +
                (errorMsg != null ? errorMsg : "未知错误"),
                Toast.LENGTH_LONG
              ).show();
            } catch (Throwable t) {
              log("❌ 显示失败Toast失败: " + t.getMessage());
            }
          }
        }
      );
    } catch (Throwable t) {
      log("❌ 发送失败Toast消息失败: " + t.getMessage());
    }
  }

  // ========== 内嵌模式ContentProvider ==========
  public static class HookProvider extends ContentProvider {

    private HookInit hookInstance;

    @Override
    public boolean onCreate() {
      // log("【内嵌ContentProvider】开始初始化");

      try {
        // 获取当前应用的包名和Context
        String targetPackage = getContext() != null
          ? getContext().getPackageName()
          : "unknown";
        ClassLoader appClassLoader = getContext() != null
          ? getContext().getClassLoader()
          : null;
        /*
                log("【内嵌ContentProvider】目标包名: " + targetPackage);
                log("【内嵌ContentProvider】ClassLoader: " + (appClassLoader != null ? "有效" : "无效"));
                log("【内嵌ContentProvider】Context: " + (getContext() != null ? "有效" : "无效"));
                */
        // 创建Hook实例并初始化
        hookInstance = new HookInit();
        hookInstance.initForEmbed(appClassLoader, targetPackage, getContext());

        //  log("✅【内嵌ContentProvider】初始化完成");
        return true;
      } catch (Throwable t) {
        //   log("❌【内嵌ContentProvider】初始化失败: " + t.getMessage());
        return false;
      }
    }

    @Override
    public Cursor query(
      Uri uri,
      String[] projection,
      String selection,
      String[] selectionArgs,
      String sortOrder
    ) {
      // 提供模块状态查询接口
      if (
        hookInstance != null &&
        uri != null &&
        uri.toString().contains("module_status")
      ) {
        MatrixCursor cursor = new MatrixCursor(
          new String[] { "module_active", "target_app", "install_status" }
        );
        cursor.addRow(
          new Object[] {
            true,
            hookInstance.currentTargetApp,
            hookInstance.installStatusMap.get(hookInstance.currentTargetApp),
          }
        );
        return cursor;
      }
      return null;
    }

    @Override
    public String getType(Uri uri) {
      return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
      // 提供配置更新接口
      if (
        hookInstance != null &&
        uri != null &&
        uri.toString().contains("update_config")
      ) {
        if (values != null) {
          try {
            String key = values.getAsString("key");
            String value = values.getAsString("value");

            if ("install_status".equals(key)) {
              boolean newStatus = "true".equals(value);
              hookInstance.installStatusMap.put(
                hookInstance.currentTargetApp,
                newStatus
              );
              hookInstance.saveConfigToFile();
              // log("✅【内嵌ContentProvider】更新配置: " + key + " = " + value);
            }
          } catch (Throwable t) {
            //  log("❌【内嵌ContentProvider】更新配置失败: " + t.getMessage());
          }
        }
      }
      return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
      return 0;
    }

    @Override
    public int update(
      Uri uri,
      ContentValues values,
      String selection,
      String[] selectionArgs
    ) {
      return 0;
    }
  }
}

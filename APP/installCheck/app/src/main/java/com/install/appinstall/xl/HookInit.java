package com.install.appinstall.xl;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import android.content.pm.PackageInfo;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import de.robv.android.xposed.XC_MethodReplacement;

// 核心Hook类（保留原有所有代码+注解，仅新增内嵌初始化调用）
public class HookInit implements IXposedHookLoadPackage {
    private String currentTargetApp = "";
    private static final String MODULE_TAG = "InstallHook";
    private static final ArrayList<String> globalCapturedPackages = new ArrayList<>();
    private final ArrayList<String> appCapturedPackages = new ArrayList<>();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		// 修改第 40 行左右的代码：
		if ("com.install.appinstall.xl".equals(lpparam.packageName)) {
			XposedHelpers.findAndHookMethod(
				MainActivity.class.getName(),
				lpparam.classLoader,
				"isModuleActivated",
				XC_MethodReplacement.returnConstant(true));
		}
        currentTargetApp = lpparam.packageName;
        log("=========================================");
        log("开始Hook应用: " + currentTargetApp);
        if (isSystemPackage(currentTargetApp) || 
            currentTargetApp.equals("com.install.appinstall.xl")) {
            log("跳过系统应用或模块自身: " + currentTargetApp);
            return;
        }
        try {
            hookKeyMethods(lpparam.classLoader);
            // 原有补充防护方法
            hookOverloadMethods(lpparam.classLoader);
            hookSystemFileRead();
            hookPackageManagerReflect();
            // Flutter相关适配（2个插件 + MethodChannel全局拦截）
            hookFlutterPackageInfoPlus(lpparam.classLoader);
            hookFlutterAppInstalledChecker(lpparam.classLoader);
            hookFlutterMethodChannel(lpparam.classLoader); // 新增：MethodChannel全局拦截
            // 新增：OkHttp网络Hook（不影响正常网络）
            hookOkHttp(lpparam.classLoader);
            //  log("Hook完成，开始监听包查询...");
			log("当前应用已捕获包名: " + appCapturedPackages.toString());
        } catch (Throwable t) {
            log("Hook失败: " + t);
        }
        //    log("=========================================");
    }

    // ========== 内嵌模式专用：初始化入口（新增，不影响原有逻辑） ==========
    /**
     * 内嵌模式初始化核心方法（供HookProvider调用）
     * 保留所有原有Hook逻辑，仅适配ClassLoader来源
     */
    public void initForEmbed(ClassLoader classLoader, String targetPackageName) {
        currentTargetApp = targetPackageName;
        log("=========================================");
        log("【内嵌模式】开始初始化Hook: " + currentTargetApp);
        if (isSystemPackage(currentTargetApp) || 
            currentTargetApp.equals("com.install.appinstall.xl")) {
            log("【内嵌模式】跳过系统应用或模块自身: " + currentTargetApp);
            return;
        }
        try {
            // 复用所有原有Hook方法，仅传入内嵌环境的ClassLoader
            hookKeyMethods(classLoader);
            hookOverloadMethods(classLoader);
            hookSystemFileRead();
            hookPackageManagerReflect();
            hookFlutterPackageInfoPlus(classLoader);
            hookFlutterAppInstalledChecker(classLoader);
            hookFlutterMethodChannel(classLoader);
            hookOkHttp(classLoader);
            log("【内嵌模式】Hook初始化完成，当前应用已捕获包名: " + appCapturedPackages.toString());
        } catch (Throwable t) {
            log("【内嵌模式】Hook初始化失败: " + t);
        }
        log("=========================================");
    }

    // ========== 原有核心Hook方法（未修改，保留所有注解） ==========
    private void hookKeyMethods(ClassLoader classLoader) {
        try {
            hookGetPackageInfo(classLoader);
            hookGetApplicationInfo(classLoader);
            hookGetInstalledPackages(classLoader);
            hookGetInstalledApplications(classLoader);
            //  log("4个关键方法Hook完成");
        } catch (Throwable t) {
            log("Hook关键方法失败: " + t);
        }
    }

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
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getPackageInfo", param);
                    }
                });
            //   log("成功Hook: getPackageInfo(String, int)");
        } catch (Throwable t) {
            log("Hook getPackageInfo失败: " + t.getMessage());
        }
    }

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
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getApplicationInfo", param);
                    }
                });
            //  log("成功Hook: getApplicationInfo(String, int)");
        } catch (Throwable t) {
            log("Hook getApplicationInfo失败: " + t.getMessage());
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
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getInstalledPackages", param);
                    }
                });
            //     log("成功Hook: getInstalledPackages(int)");
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
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getInstalledApplications", param);
                    }
                });
            //  log("成功Hook: getInstalledApplications(int)");
        } catch (Throwable t) {
            log("Hook getInstalledApplications失败: " + t.getMessage());
        }
    }

    // ========== 原有补充防护方法（3个，保留所有注解） ==========
    /**
     * 补充1：Hook系统方法重载版（Android 12+适配）
     */
    private void hookOverloadMethods(ClassLoader classLoader) {
        try {
            // 1. Hook getPackageInfo重载版（String, int, int）
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                classLoader,
                "getPackageInfo",
                String.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getPackageInfo(重载)", param);
                    }
                });
            // log("成功Hook: getPackageInfo(String, int, int)");
            // 2. Hook getApplicationInfo重载版（String, int, int）
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                classLoader,
                "getApplicationInfo",
                String.class, int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getApplicationInfo(重载)", param);
                    }
                });
            // log("成功Hook: getApplicationInfo(String, int, int)");
            // 3. Hook getInstalledPackages重载版（int, int）
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                classLoader,
                "getInstalledPackages",
                int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getInstalledPackages(重载)", param);
                    }
                });
            // log("成功Hook: getInstalledPackages(int, int)");
            // 4. Hook getInstalledApplications重载版（int, int）
            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager",
                classLoader,
                "getInstalledApplications",
                int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        handlePackageQuery("getInstalledApplications(重载)", param);
                    }
                });
            // log("成功Hook: getInstalledApplications(int, int)");
        } catch (Throwable t) {
            log("Hook重载方法失败: " + t.getMessage());
        }
    }

    /**
     * 补充2：拦截系统包信息文件读取
     */
    private void hookSystemFileRead() {
        try {
            // Hook FileInputStream构造方法，拦截关键文件
            XposedHelpers.findAndHookMethod(
                "java.io.FileInputStream",
                null, // 系统类无需classLoader
                "FileInputStream",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String filePath = (String) param.args[0];
                        // 拦截核心包信息文件
                        if (filePath.contains("/data/system/packages.xml") ||
                            filePath.contains("/data/system/packages.list") ||
                            filePath.contains("com.android.settings/databases/apps.db")) {
                            param.setThrowable(new SecurityException("权限不足，无法读取"));
                            //      log("【拦截文件】阻止读取系统包信息文件: " + filePath);
                        }
                    }
                });
            // log("成功Hook文件读取拦截");
        } catch (Throwable t) {
            log("Hook文件读取失败: " + t.getMessage());
        }
    }

    /**
     * 补充3：监控PackageManager反射调用
     */
    private void hookPackageManagerReflect() {
        try {
            // Hook Class.getMethod（拦截反射获取隐藏方法）
            XposedHelpers.findAndHookMethod(
                "java.lang.Class",
                null,
                "getMethod",
                String.class, Class[].class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        // 修复：先判断类型，再安全转换（消除强制转换警告）
                        if (!(param.thisObject instanceof Class)) {
                            return; // 非Class对象，直接跳过
                        }
                        Class<?> targetClass = (Class<?>) param.thisObject; // 此时转换安全，无警告
                        String methodName = (String) param.args[0];
                        // 只监控PackageManager相关类的反射
                        if (targetClass.getName().equals("android.app.ApplicationPackageManager") ||
                            targetClass.getName().equals("android.content.pm.PackageManager")) {
                            // 拦截常见隐藏方法（可根据需求扩展）
                            if (methodName.contains("getPackageInfoAsUser") ||
                                methodName.contains("getApplicationInfoAsUser") ||
                                methodName.contains("hidden") ||
                                methodName.contains("internal")) {
                                //	log("【拦截反射】阻止调用PackageManager隐藏方法: " + methodName);
                                param.setResult(null); // 返回空，阻止反射成功
                            }
                        }
                    }
                });
            // log("成功Hook反射监控");
        } catch (Throwable t) {
            log("Hook反射监控失败: " + t.getMessage());
        }
    }

    // ========== Flutter相关适配（3个，保留所有注解） ==========
    /**
     * 补充4：适配 Flutter 插件 package_info_plus
     */
    private void hookFlutterPackageInfoPlus(ClassLoader classLoader) {
        try {
            String[] targetClasses = {
                "dev.fluttercommunity.plus.packageinfo.PackageInfoPlugin",
                "io.flutter.plugins.packageinfo.PackageInfoPlugin"
            };
            for (String className : targetClasses) {
                try {
                    Class<?> pluginClass = XposedHelpers.findClass(className, classLoader);
                    XposedHelpers.findAndHookMethod(
                        pluginClass,
                        "handleMethodCall",
                        "io.flutter.plugin.common.MethodCall",
                        "io.flutter.plugin.common.Result",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object methodCall = param.args[0];
                                Object result = param.args[1];
                                if (methodCall == null || result == null) return;
                                String methodName = XposedHelpers.callMethod(methodCall, "method").toString();
                                // log("【Flutter插件】package_info_plus 调用: " + methodName);
                                if ("getPackageInfo".equals(methodName) || "getAllPackageInfo".equals(methodName)) {
                                    String targetPackage = null;
                                    Object arguments = XposedHelpers.callMethod(methodCall, "arguments");
                                    if (arguments instanceof String) {
                                        targetPackage = (String) arguments;
                                    } else if (arguments instanceof Map) {
                                        targetPackage = (String) ((Map<?, ?>) arguments).get("packageName");
                                    }
                                    // log("【Flutter插件】查询包名: " + (targetPackage == null ? "所有应用" : targetPackage));
                                    Object fakeResult = createFakePackageInfoPlusResult(targetPackage);
                                    if (fakeResult != null) {
                                        XposedHelpers.callMethod(result, "success", fakeResult);
                                        param.setResult(null);
                                        // log("【Flutter插件】伪造安装结果: " + targetPackage);
                                    }
                                }
                            }
                        });
                    // log("成功Hook Flutter package_info_plus 插件");
                    break;
                } catch (Throwable t) {
                    // log("Hook插件类 " + className + " 失败: " + t.getMessage());
                    continue;
                }
            }
        } catch (Throwable t) {
            log("适配 package_info_plus 插件失败: " + t.getMessage());
        }
    }

    /**
     * 补充5：适配 Flutter 插件 app_installed_checker
     */
    private void hookFlutterAppInstalledChecker(ClassLoader classLoader) {
        try {
            String[] targetClasses = {
                "com.javih.addtoapp.AppInstalledCheckerPlugin",
                "com.example.appinstalledchecker.AppInstalledCheckerPlugin",
                "app.installed.checker.AppInstalledCheckerPlugin"
            };
            for (String className : targetClasses) {
                try {
                    Class<?> pluginClass = XposedHelpers.findClass(className, classLoader);
                    XposedHelpers.findAndHookMethod(
                        pluginClass,
                        "onMethodCall",
                        "io.flutter.plugin.common.MethodCall",
                        "io.flutter.plugin.common.Result",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                Object methodCall = param.args[0];
                                Object result = param.args[1];
                                if (methodCall == null || result == null) return;
                                String methodName = XposedHelpers.callMethod(methodCall, "method").toString();
                                // log("【Flutter插件】app_installed_checker 调用: " + methodName);
                                if ("isAppInstalled".equals(methodName) || "areAppsInstalled".equals(methodName)) {
                                    Object arguments = XposedHelpers.callMethod(methodCall, "arguments");
                                    if ("isAppInstalled".equals(methodName) && arguments instanceof String) {
                                        String targetPackage = (String) arguments;
                                        boolean fakeInstalled = globalCapturedPackages.contains(targetPackage);
                                        XposedHelpers.callMethod(result, "success", fakeInstalled);
                                        param.setResult(null);
                                        // log("【Flutter插件】伪造单个应用安装状态: " + targetPackage + " -> " + fakeInstalled);
                                    } else if ("areAppsInstalled".equals(methodName) && arguments instanceof ArrayList) {
                                        ArrayList<String> targetPackages = (ArrayList<String>) arguments;
                                        Map<String, Boolean> fakeResultMap = new HashMap<>();
                                        for (String pkg : targetPackages) {
                                            fakeResultMap.put(pkg, globalCapturedPackages.contains(pkg));
                                        }
                                        XposedHelpers.callMethod(result, "success", fakeResultMap);
                                        param.setResult(null);
                                        // log("【Flutter插件】伪造多个应用安装状态: " + targetPackages);
                                    }
                                }
                            }
                        });
                    // log("成功Hook Flutter app_installed_checker 插件");
                    break;
                } catch (Throwable t) {
                    // log("Hook插件类 " + className + " 失败: " + t.getMessage());
                    continue;
                }
            }
        } catch (Throwable t) {
            log("适配 app_installed_checker 插件失败: " + t.getMessage());
        }
    }

    /**
     * 补充6：Flutter MethodChannel全局拦截（覆盖所有Dart插件通信）
     */
    private void hookFlutterMethodChannel(ClassLoader classLoader) {
        try {
            // Hook MethodChannel的invokeMethod方法（Dart->原生的通用通信入口）
            Class<?> methodChannelClass = XposedHelpers.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
            XposedHelpers.findAndHookMethod(
                methodChannelClass,
                "invokeMethod",
                String.class, // 方法名
                Object.class, // 参数
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String methodName = (String) param.args[0];
                        Object arguments = param.args[1];
                        // log("【MethodChannel全局拦截】方法名: " + methodName + ", 参数: " + arguments);
                        // 拦截所有与"应用安装检测"相关的调用（关键词匹配）
                        if (methodName.contains("getPackage") || 
                            methodName.contains("isInstalled") || 
                            methodName.contains("checkApp") || 
                            methodName.contains("appDetect")) {
                            // 解析包名参数
                            String targetPackage = extractPackageFromChannelArgs(arguments);
                            if (targetPackage != null || arguments instanceof ArrayList) {
                                // 伪造结果（已安装）
                                Object fakeResult = buildChannelFakeResult(methodName, targetPackage, arguments);
                                param.setResult(fakeResult);
                                // log("【MethodChannel全局拦截】伪造结果: " + fakeResult);
                            }
                        }
                    }
                });
            // 同时Hook setMethodCallHandler（原生->Dart的回调入口）
            XposedHelpers.findAndHookMethod(
                methodChannelClass,
                "setMethodCallHandler",
                "io.flutter.plugin.common.MethodChannel$MethodCallHandler",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object handler = param.args[0];
                        if (handler == null) return;
                        // Hook Handler的onMethodCall方法
                        XposedHelpers.findAndHookMethod(
                            handler.getClass(),
                            "onMethodCall",
                            "io.flutter.plugin.common.MethodCall",
                            "io.flutter.plugin.common.Result",
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam handlerParam) throws Throwable {
                                    Object methodCall = handlerParam.args[0];
                                    Object result = handlerParam.args[1];
                                    String methodName = XposedHelpers.callMethod(methodCall, "method").toString();
                                    Object arguments = XposedHelpers.callMethod(methodCall, "arguments");
                                    // 拦截检测相关调用
                                    if (methodName.contains("getPackage") || methodName.contains("isInstalled")) {
                                        String targetPackage = extractPackageFromChannelArgs(arguments);
                                        Object fakeResult = buildChannelFakeResult(methodName, targetPackage, arguments);
                                        XposedHelpers.callMethod(result, "success", fakeResult);
                                        handlerParam.setResult(null);
                                        // log("【MethodChannel回调拦截】伪造结果: " + fakeResult);
                                    }
                                }
                            });
                    }
                });
            // log("成功Hook Flutter MethodChannel全局拦截");
        } catch (Throwable t) {
            log("Hook Flutter MethodChannel失败: " + t.getMessage());
        }
    }

    // ========== OkHttp网络Hook（补充7：不影响正常网络） ==========
    /**
     * 补充7：Hook OkHttp网络请求（仅拦截检测相关请求，不影响正常业务）
     */
    private void hookOkHttp(ClassLoader classLoader) {
        try {
            // 1. Hook OkHttp异步请求（RealCall#enqueue）
            Class<?> realCallClass = XposedHelpers.findClass("okhttp3.RealCall", classLoader);
            XposedHelpers.findAndHookMethod(
                realCallClass,
                "enqueue",
                "okhttp3.Callback",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object requestObj = XposedHelpers.getObjectField(param.thisObject, "originalRequest");
                        if (requestObj == null) return;
                        String url = XposedHelpers.callMethod(requestObj, "url").toString();
                        String method = XposedHelpers.callMethod(requestObj, "method").toString();
                        // log("【OkHttp拦截】异步请求: " + method + " " + url);
                        // 仅拦截检测相关请求（关键词匹配，避免影响正常网络）
                        if (isDetectionUrl(url)) {
                            // 方案：直接返回伪造响应，阻断真实请求
                            Object callback = param.args[0];
                            fakeOkHttpResponse(callback, url);
                            param.setResult(null); // 取消原有请求
                            // log("【OkHttp拦截】伪造检测响应: " + url);
                        }
                    }
                });
            // 2. Hook OkHttp同步请求（RealCall#execute）
            XposedHelpers.findAndHookMethod(
                realCallClass,
                "execute",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object requestObj = XposedHelpers.getObjectField(param.thisObject, "originalRequest");
                        if (requestObj == null) return;
                        String url = XposedHelpers.callMethod(requestObj, "url").toString();
                        // log("【OkHttp拦截】同步请求: " + url);
                        if (isDetectionUrl(url)) {
                            Object fakeResponse = createOkHttpFakeResponse();
                            param.setResult(fakeResponse);
                            // log("【OkHttp拦截】伪造同步检测响应: " + url);
                        }
                    }
                });
            // log("成功Hook OkHttp网络请求");
        } catch (Throwable t) {
            // 应用未使用OkHttp时Hook失败，不影响核心功能
            log("Hook OkHttp失败（应用可能未使用OkHttp）: " + t.getMessage());
        }
    }

    // ========== 辅助方法（新增，不影响原有逻辑） ==========
    /**
     * 辅助：判断是否是检测相关URL（精准匹配，避免误拦截）
     */
    private boolean isDetectionUrl(String url) {
        // 仅拦截包含检测关键词的URL，正常业务URL不触碰
        return url.contains("checkInstall") ||
			url.contains("appDetect") ||
			url.contains("packageCheck") ||
			url.contains("umeng/app/check") ||
			url.contains("jiguang/detect") ||
			url.contains("appInstalled") ||
			url.contains("verifyApp");
    }

    /**
     * 辅助：伪造OkHttp异步响应
     */
    private void fakeOkHttpResponse(Object callback, String url) {
        try {
            ClassLoader cl = callback.getClass().getClassLoader();
            Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", cl);
            Class<?> responseBuilderClass = XposedHelpers.findClass("okhttp3.Response$Builder", cl);
            Class<?> mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", cl);
            Class<?> requestBodyClass = XposedHelpers.findClass("okhttp3.RequestBody", cl);
            // 伪造JSON响应（模拟检测通过）
            Object jsonMediaType = XposedHelpers.callStaticMethod(mediaTypeClass, "parse", "application/json; charset=utf-8");
            String fakeJson = "{\"code\":200,\"msg\":\"success\",\"isInstalled\":true,\"data\":{\"packageList\":" + globalCapturedPackages.toString() + "}}";
            Object fakeBody = XposedHelpers.callStaticMethod(requestBodyClass, "create", jsonMediaType, fakeJson);
            // 构建响应对象
            Object fakeResponse = XposedHelpers.newInstance(responseBuilderClass);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "code", 200);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "body", fakeBody);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "build");
            // 回调成功响应
            XposedHelpers.callMethod(callback, "onResponse", null, fakeResponse);
        } catch (Exception e) {
            log("伪造OkHttp异步响应失败: " + e.getMessage());
        }
    }

    /**
     * 辅助：创建OkHttp同步伪造响应
     */
    private Object createOkHttpFakeResponse() {
        try {
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Class<?> responseClass = XposedHelpers.findClass("okhttp3.Response", cl);
            Class<?> responseBuilderClass = XposedHelpers.findClass("okhttp3.Response$Builder", cl);
            Class<?> mediaTypeClass = XposedHelpers.findClass("okhttp3.MediaType", cl);
            Class<?> requestBodyClass = XposedHelpers.findClass("okhttp3.RequestBody", cl);
            Object jsonMediaType = XposedHelpers.callStaticMethod(mediaTypeClass, "parse", "application/json; charset=utf-8");
            String fakeJson = "{\"code\":200,\"msg\":\"检测通过\",\"isInstalled\":true}";
            Object fakeBody = XposedHelpers.callStaticMethod(requestBodyClass, "create", jsonMediaType, fakeJson);
            Object fakeResponse = XposedHelpers.newInstance(responseBuilderClass);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "code", 200);
            fakeResponse = XposedHelpers.callMethod(fakeResponse, "body", fakeBody);
            return XposedHelpers.callMethod(fakeResponse, "build");
        } catch (Exception e) {
            log("创建OkHttp同步伪造响应失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 辅助：从MethodChannel参数中提取包名
     */
    private String extractPackageFromChannelArgs(Object arguments) {
        if (arguments instanceof String) {
            return (String) arguments;
        } else if (arguments instanceof Map) {
            return (String) ((Map<?, ?>) arguments).get("packageName") != null ?
				(String) ((Map<?, ?>) arguments).get("packageName") :
				(String) ((Map<?, ?>) arguments).get("pkg");
        }
        return null;
    }

    /**
     * 辅助：构建MethodChannel伪造结果
     */
    private Object buildChannelFakeResult(String methodName, String targetPackage, Object arguments) {
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

    /**
     * 辅助：为package_info_plus生成伪造结果
     */
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
            fakeMap.put("appName", targetPackage == null ? "All Apps" : ("App: " + targetPackage));
            fakeMap.put("packageName", targetPackage == null ? "multiple" : targetPackage);
            fakeMap.put("version", "1.0.0");
            fakeMap.put("buildNumber", "1");
            fakeMap.put("buildSignature", "fake_signature");
            fakeMap.put("installerPackageName", "com.android.vending");
            return fakeMap;
        } catch (Exception e) {
            log("创建 package_info_plus 伪造结果失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 辅助：构建单个应用的伪造信息
     */
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

    // ========== 原有核心逻辑（未修改，保留所有注解） ==========
    private void handlePackageQuery(String methodName, XC_MethodHook.MethodHookParam param) {
        String packageName = extractPackageName(param.args);
        if (packageName == null) {
            return;
        }
        //  log("【查询检测】" + methodName + " -> 查询包名: " + packageName);
        if (packageName.equals(currentTargetApp)) {
            //     log("【跳过】查询自身应用: " + packageName);
            return;
        }
        if (isSystemPackage(packageName)) {
            //   log("【跳过】查询系统应用: " + packageName);
            return;
        }
        boolean alreadyCaptured = appCapturedPackages.contains(packageName);
        boolean alreadyGlobal = globalCapturedPackages.contains(packageName);
        if (!alreadyCaptured) {
            if (appCapturedPackages.contains(packageName)) {
                appCapturedPackages.remove(packageName);
            }
            appCapturedPackages.add(packageName);
            log("【新捕获】添加到当前应用列表: " + packageName);
        }
        if (!alreadyGlobal) {
            synchronized (globalCapturedPackages) {
                if (globalCapturedPackages.contains(packageName)) {
                    globalCapturedPackages.remove(packageName);
                }
                globalCapturedPackages.add(packageName);
                //   log("【全局记录】添加到全局列表: " + packageName);
            }
        }
        log("当前应用捕获列表(" + appCapturedPackages.size() + "): " + appCapturedPackages.toString());
        //   log("全局捕获列表(" + globalCapturedPackages.size() + "): " + globalCapturedPackages.toString());
        log("=========================================");
        Object fakeResult = createFakeResult(methodName, packageName);
        if (fakeResult != null) {
            param.setResult(fakeResult);
			log("【成功伪装】" + methodName + " -> 返回假结果: " + packageName);
        } else {
            log("【未处理】" + methodName + " -> 无法创建假结果: " + packageName);
        }
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
        if (str == null || str.length() < 4 || !str.contains(".")) {
            return false;
        }
        return !str.startsWith("/") && !str.contains("://");
    }

    private boolean isSystemPackage(String packageName) {
        return packageName.startsWith("android.") ||
            packageName.startsWith("com.android.") ||
            packageName.startsWith("com.google.android.") ||
            packageName.equals("android") ||
            packageName.equals("system") ||
            packageName.equals("root");
    }

    private Object createFakeResult(String methodName, String packageName) {
        try {
            if (methodName.contains("PackageInfo")) {
                return createFakePackageInfo(packageName);
            } else if (methodName.contains("ApplicationInfo")) {
                return createFakeApplicationInfo(packageName);
            } else if (methodName.contains("InstalledPackages") || 
                       methodName.contains("InstalledApplications")) {
                return createFakeInstalledList(packageName, methodName);
            }
        } catch (Exception e) {
            log("创建假结果失败 " + methodName + ": " + e.getMessage());
        }
        return null;
    }

    private PackageInfo createFakePackageInfo(String packageName) {
        try {
            PackageInfo pi = new PackageInfo();
            pi.packageName = packageName;
            pi.versionName = "1.0.0";
            pi.versionCode = 1;
            pi.firstInstallTime = System.currentTimeMillis();
            pi.lastUpdateTime = System.currentTimeMillis();
            pi.applicationInfo = createFakeApplicationInfo(packageName);
            return pi;
        } catch (Exception e) {
            return null;
        }
    }

    private ApplicationInfo createFakeApplicationInfo(String packageName) {
        try {
            ApplicationInfo ai = new ApplicationInfo();
            ai.packageName = packageName;
            ai.name = "App: " + packageName;
            ai.flags = ApplicationInfo.FLAG_INSTALLED;
            ai.enabled = true;
            ai.uid = 10000 + Math.abs(packageName.hashCode() % 10000);
            ai.targetSdkVersion = 30;
            ai.sourceDir = "/data/app/" + packageName.replace('.', '-') + "-1/base.apk";
            ai.publicSourceDir = ai.sourceDir;
            return ai;
        } catch (Exception e) {
            return null;
        }
    }

    private Object createFakeInstalledList(String packageName, String methodName) {
        synchronized (globalCapturedPackages) {
            if (methodName.contains("Application")) {
                ArrayList<ApplicationInfo> list = new ArrayList<>();
                for (String pkgName : globalCapturedPackages) {
                    list.add(createFakeApplicationInfo(pkgName));
                }
                log("创建已安装列表（ApplicationInfo），包含 " + list.size() + " 个应用");
                return list;
            } else {
                ArrayList<PackageInfo> list = new ArrayList<>();
                for (String pkgName : globalCapturedPackages) {
                    list.add(createFakePackageInfo(pkgName));
                }
                log("创建已安装列表（PackageInfo），包含 " + list.size() + " 个应用");
                return list;
            }
        }
    }

    public static ArrayList<String> getGlobalCapturedPackages() {
        synchronized (globalCapturedPackages) {
            return new ArrayList<String>(globalCapturedPackages);
        }
    }

    private void log(String message) {
        try {
            XposedBridge.log("[" + MODULE_TAG + "] [" + currentTargetApp + "] " + message);
        } catch (Throwable e) {
            android.util.Log.d(MODULE_TAG, "[" + currentTargetApp + "] " + message);
        }
    }

    // ========== 内嵌模式专用：ContentProvider初始化组件（新增） ==========
    public static class HookProvider extends ContentProvider {
        @Override
        public boolean onCreate() {
            if (getContext() == null) return false;
            // 获取目标应用的包名和ClassLoader（内嵌环境专用）
            String targetPackage = getContext().getPackageName();
            ClassLoader appClassLoader = getContext().getClassLoader();
            // 初始化Hook核心逻辑（复用原有所有功能）
            HookInit hookInit = new HookInit();
            hookInit.initForEmbed(appClassLoader, targetPackage);
            return false;
        }

        // 以下为ContentProvider必填空实现（不影响功能）
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            return null;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }
}

package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import android.os.Build;
import android.text.Html;
import java.util.concurrent.ConcurrentHashMap;

import com.install.appinstall.xl.HookInit;

//调试模式

public class DebugModeManager {
    private static final String DEBUG_CONFIG_FILE = "debug_config.json";
    private static final long KEY_SEQUENCE_TIMEOUT = 2000;
    private static HookInit sHookInit;
    private static final Map<String, Boolean> sDebugFeatures = new ConcurrentHashMap<>();
    private static boolean sDebugModeActive = false;
    private static final Map<String, String> FEATURE_DISPLAY_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> FEATURE_CATEGORIES = new LinkedHashMap<>();
    static {
        // ---- 系统底层 ----
        addFeature("hook_bundle_get_string", "Bundle防护", "系统底层");
        addFeature("hook_bundle_empty", "Bundle空实例防护", "系统底层");
        addFeature("hook_so_path_fix", "SO 路径修复", "系统底层");
        addFeature("hook_system_properties", "系统属性检测", "系统底层");
        addFeature("hook_base_activity_lifecycle", "系统应用检测", "系统底层");
        addFeature("hook_process_memory", "进程反推包名拦截", "系统底层");

        // ---- 捕获包相关 ----
        addFeature("hook_get_activity_info", "获取应用Activity信息", "捕获包相关");
        addFeature("hook_get_package_info", "获取应用包信息", "捕获包相关");
        addFeature("hook_get_application_info", "获取应用信息", "捕获包相关");
        addFeature("hook_get_installed_packages", "获取已安装包列表", "捕获包相关");
        addFeature("hook_get_installed_applications", "获取已安装应用列表", "捕获包相关");
        addFeature("hook_get_package_info_as_user", "多用户包信息", "捕获包相关");
        addFeature("hook_installed_packages_as_user", "多用户已安装包", "捕获包相关");
        addFeature("hook_installed_applications_as_user", "多用户已安装应用", "捕获包相关");
        addFeature("hook_package_installer", "应用包安装捕获", "捕获包相关");
        addFeature("hook_service_manager", "Binder层应用捕获", "捕获包相关");
        addFeature("hook_is_application_enabled", "检查应用启用状态", "捕获包相关");
        addFeature("hook_get_installer_package_name", "获取安装来源", "捕获包相关");
        addFeature("hook_Desktop_App_Query", "拦截穿透桌面应用", "捕获包相关");
        addFeature("hook_flutter_package_info", "Flutter包信息插件", "捕获包相关");
        addFeature("hook_flutter_app_installed", "Flutter安装检测", "捕获包相关");
        addFeature("hook_flutter_method_channel", "Flutter检测方法l", "捕获包相关");

        // ---- 权限相关 ----
        addFeature("perm_guard_gate", "权限防护总控 (禁用权限列表)", "权限相关");
        addFeature("hook_query_all_packages", "解析应用声明权限 (QUERY_ALL_PACKAGES等)", "权限相关");
        addFeature("hook_Telephony_Getters", "空白通行证数据组（READ_PHONE_STATE等）", "权限相关");
        addFeature("hook_Content_Resolver_Query", "空白通行证数据(通讯录/通话记录/短信/日历)", "权限相关");
        addFeature("hook_special_system_apis", "特殊权限 (文件管理/安装未知应用等)", "权限相关");
        addFeature("hook_account_manager", "账户管理拦截 (检测账户)", "权限相关");
        addFeature("hook_write_settings", "写入系统设置权限", "权限相关");
        addFeature("hook_app_ops", "AppOps 权限检查", "权限相关");
        addFeature("hook_overlay_app_ops", "AppOps 权限拦截", "权限相关");
        addFeature("hook_package_usage_stats", "情况统计权限", "权限相关");
        addFeature("hook_check_permission", "check权限检测", "权限相关");
        addFeature("hook_permission_request", "权限请求拦截", "权限相关");
        addFeature("hook_settings_provider", "系统Settings查询", "权限相关");
        addFeature("hook_shortcut_widget", "快捷方式/桌面组件", "权限相关");
        addFeature("hook_app_widget", "桌面小部件列表", "权限相关");
        addFeature("hook_accessibility", "无障碍服务列表", "权限相关");

        // ---- 启动相关 ----
        addFeature("hook_resolve_activity", "解析 Activity", "启动相关");
        addFeature("hook_time_fake", "Activity前后台", "启动相关");
        addFeature("hook_get_launch_intent", "获取启动 Intent", "启动相关");
        addFeature("hook_can_start_activity", "检查可启动性", "启动相关");
        addFeature("hook_query_intent_activities", "查询 Intent 意图", "启动相关");
        addFeature("hook_query_intent_services", "查询 Intent 服务", "启动相关");
        addFeature("hook_query_broadcast_receivers", "查询广播接收器", "启动相关");
        addFeature("hook_start_activity", "启动跳转拦截", "启动相关");

        // ---- 文件系统相关 ----
        addFeature("hook_file_exists", "exists应用安装路径", "文件系统相关");
        addFeature("hook_file_length", "length路径分析", "文件系统相关");
        addFeature("hook_lib_directory", "Lib 目录存在性检查", "文件系统相关");
        addFeature("hook_data_app_listing", "/data/app 目录枚举", "文件系统相关");
        addFeature("hook_os_readdir", "Os.readdir 拦截", "文件系统相关");
        addFeature("hook_os_stat", "Os.stat/lstat 文件状态", "文件系统相关");
        addFeature("hook_proc_files", "/proc 文件系统拦截", "文件系统相关");
        addFeature("hook_system_file_read", "系统文件读取", "文件系统相关");
        addFeature("hook_random_access_file", "随机读取 APK", "文件系统相关");

        // ---- 拦截相关 ----
        addFeature("hook_exit_methods", "退出拦截 (System.exit 等)", "拦截相关");
        addFeature("hook_indirect_exit", "间接退出 (反射/Process)", "拦截相关");
        addFeature("hook_global_exit_sources", "按钮退出源 (按钮/Handler)", "拦截相关");
        addFeature("crash_protect", "异常防护 (Java 崩溃吞噬)", "拦截相关");

        // ---- 命令行相关 ----
        addFeature("hook_runtime_exec", "Runtime拦截", "命令行相关");
        addFeature("hook_process_builder", "ProcessBuilder拦截", "命令行相关");
        // ---- 网络相关 ----
        addFeature("hook_okhttp", "OkHttp 网络拦截", "网络相关");
        // ---- 反射相关 ----
        addFeature("hook_reflect_invoke", "反射方法调用", "反射相关");
        addFeature("hook_package_manager_reflect", "包反射监控", "反射相关");

        // ---- 痕迹相关 ----
        addFeature("anti_detection", "痕迹检测 (总开关)", "痕迹相关");
        addFeature("class_hide", "类加载隐藏", "痕迹相关");
        addFeature("file_hide", "文件隐藏", "痕迹相关");
        addFeature("pm_hide", "服务隐藏", "痕迹相关");
        addFeature("proc_hide", "信息隐藏", "痕迹相关");
        addFeature("cmd_hide", "命令隐藏", "痕迹相关");
        addFeature("stacktrace_hide", "堆栈隐藏", "痕迹相关");
        addFeature("root_hide", "Root隐藏", "痕迹相关");
        addFeature("adb_hide", "ADB隐藏", "痕迹相关");
        addFeature("dev_hide", "开发者隐藏", "痕迹相关");
        addFeature("usb_hide", "USB隐藏", "痕迹相关");

        // ---- 代理相关 ----
        addFeature("vpn_fake", "网络代理 (总开关)", "代理相关");
        addFeature("vpn_interface", "VPN 接口隐藏", "代理相关");
        addFeature("vpn_proxy", "代理检测隐藏", "代理相关");
        addFeature("vpn_proxy_env", "代理环境变量隐藏", "代理相关");
        addFeature("vpn_capture", "抓包应用隐藏", "代理相关");
        addFeature("vpn_netfiles", "网络文件隐藏", "代理相关");
        addFeature("vpn_net_detect", "网络检测监听", "代理相关");
        addFeature("vpn_ssl_trust", "SSL 信任链绕过", "代理相关");
        addFeature("vpn_ssl_pinning", "SSL 证书固定绕过", "代理相关");
        addFeature("vpn_ssl_cert", "SSL 证书信息隐藏", "代理相关");
        addFeature("vpn_ssl_cert_hide", "SSL 证书特征替换", "代理相关");
        addFeature("vpn_cmd", "命令拦截 (ip/netstat等)", "代理相关");

        // ---- 其他 ----
        addFeature("hook_window_flags", "截屏/录屏限制解除", "其他");
        addFeature("hook_dialog_cancelable", "对话框强制取消", "其他");
        addFeature("hook_anti_cancel_finish", "对话框强制取消(防界面关闭)", "其他");
        addFeature("selinux_fake", "SELinux伪装", "其他");
        addFeature("force_default_back", "返回键控制", "其他");
        addFeature("hook_vibrator", "震动控制", "其他");
        addFeature("share_fake", "假装分享", "其他");
    }

    private static void addFeature(String key, String displayName, String category) {
        FEATURE_DISPLAY_NAMES.put(key, displayName);
        FEATURE_CATEGORIES.put(key, category);
    }

    private static List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        for (String key : FEATURE_DISPLAY_NAMES.keySet()) {
            String cat = FEATURE_CATEGORIES.get(key);
            if (!categories.contains(cat)) categories.add(cat);
        }
        return categories;
    }

    private static List<String> getFeaturesForCategory(String category) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, String> entry : FEATURE_CATEGORIES.entrySet()) {
            if (entry.getValue().equals(category)) keys.add(entry.getKey());
        }
        return keys;
    }

    // ==================== 初始化 ====================
    public static void init(HookInit hookInit) {
        sHookInit = hookInit;
        loadDebugConfig();
        if (sDebugModeActive) {
            // 增强 Context 获取：优先 Activity，其次 Application
            final Context ctx = getBestContext();
            if (ctx != null) {
                More.postOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        ToastUtil.show(ctx, "目前处于调试模式！\n请尽快恢复！\n操作音量键：上下上下");
                    }
                }, 3500);
            } else {
                // 延迟重试（防止 Context 尚未初始化）
                More.postOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        Context retryCtx = getBestContext();
                        if (retryCtx != null) {
                            ToastUtil.show(retryCtx, "目前处于调试模式！\n请尽快恢复！\n操作音量键：上下上下");
                        }
                    }
                }, 500);
            }
        }
    }

// 辅助方法：获取最佳可用 Context
    private static Context getBestContext() {
        if (sHookInit != null) {
            Activity act = sHookInit.getCurrentActivity();
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                return act;
            }
            Context appCtx = sHookInit.getApplicationContext();
            if (appCtx != null) {
                return appCtx;
            }
        }
        return null;
    }

    public static boolean isDebugModeActive() {
        return sDebugModeActive;
    }

    public static boolean isFeatureEnabled(String key) {
        if (!sDebugModeActive) return true;
        Boolean enabled = sDebugFeatures.get(key);
        return enabled == null || enabled;
    }

    private static void loadDebugConfig() {
        if (sHookInit == null) return;
        String targetApp = sHookInit.getCurrentTargetApp();
        if (TextUtils.isEmpty(targetApp)) return;

        File configFile = getDebugConfigFile(targetApp);
        if (!configFile.exists()) {
            sDebugModeActive = false;
            sDebugFeatures.clear();
            return;
        }

        try {
            FileInputStream fis = new FileInputStream(configFile);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            isr.close();
            fis.close();

            JSONObject json = new JSONObject(sb.toString());
            for (String key : FEATURE_DISPLAY_NAMES.keySet()) {
                boolean enabled = json.optBoolean(key, true);
                sDebugFeatures.put(key, enabled);
            }

            boolean allEnabled = true;
            for (String key : FEATURE_DISPLAY_NAMES.keySet()) {
                if (!sDebugFeatures.getOrDefault(key, true)) {
                    allEnabled = false;
                    break;
                }
            }
            if (allEnabled) {
                if (configFile.delete()) {
                    ReaLog.log("system", "调试配置已关闭");
                }
                sDebugModeActive = false;
                sDebugFeatures.clear();
            } else {
                sDebugModeActive = true;
                ReaLog.log("system", "调试模式已激活，加载配置 " + sDebugFeatures.size() + " 项");
            }
        } catch (Exception e) {
            if (configFile.exists()) configFile.delete();
            sDebugModeActive = false;
            sDebugFeatures.clear();
            ReaLog.log("system", "调试配置加载失败，已删除损坏文件");
        }
    }

    public static void saveDebugConfig(Map<String, Boolean> config) {
        if (sHookInit == null) return;
        String targetApp = sHookInit.getCurrentTargetApp();
        if (TextUtils.isEmpty(targetApp)) return;

        File configFile = getDebugConfigFile(targetApp);

        boolean allEnabled = true;
        for (String key : FEATURE_DISPLAY_NAMES.keySet()) {
            if (!config.getOrDefault(key, true)) {
                allEnabled = false;
                break;
            }
        }

        if (allEnabled) {
            if (configFile.exists() && configFile.delete()) {
                ReaLog.log("system", "调试配置已关闭");
            }
            sDebugModeActive = false;
            sDebugFeatures.clear();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            for (Map.Entry<String, Boolean> entry : config.entrySet()) {
                json.put(entry.getKey(), entry.getValue());
            }
            FileOutputStream fos = new FileOutputStream(configFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            writer.write(json.toString(2));
            writer.close();
            fos.close();

            sDebugFeatures.clear();
            sDebugFeatures.putAll(config);
            sDebugModeActive = true;
            ReaLog.log("system", "调试配置已保存，共 " + config.size() + " 项");
        } catch (Exception e) {
            ReaLog.log("system", "保存调试配置失败: " + e.getMessage());
        }
    }

    public static void disableDebugMode() {
        if (sHookInit == null) return;
        String targetApp = sHookInit.getCurrentTargetApp();
        if (TextUtils.isEmpty(targetApp)) return;
        File configFile = getDebugConfigFile(targetApp);
        if (configFile.exists() && configFile.delete()) {
            ReaLog.log("system", "调试模式已手动关闭");
        }
        sDebugModeActive = false;
        sDebugFeatures.clear();
    }

    private static File getDebugConfigFile(String targetApp) {
        File dir = new File("/data/user/0/" + targetApp + "/files/");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, DEBUG_CONFIG_FILE);
    }

    public static void showDebugDialog(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        // 震动反馈
        com.install.appinstall.xl.ui.UiKit.haptic(activity);
        final Map<String, Boolean> originalState = new LinkedHashMap<>();
        for (String key : FEATURE_DISPLAY_NAMES.keySet()) {
            originalState.put(key, sDebugFeatures.getOrDefault(key, true));
        }
        final Map<String, Boolean> currentState = new LinkedHashMap<>(originalState);
        LinearLayout rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(More.spx(activity, 60), More.spx(activity, 30), More.spx(activity, 60), More.spx(activity, 30));
        rootLayout.setBackgroundColor(0xFFFFFFFF);
        TextView title = new TextView(activity);
        title.setText("[管理员] 安装防护模块-调试模式");
        title.setTextSize(18);
        title.setTextColor(0xFF333333);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        rootLayout.addView(title);
        TextView hint = new TextView(activity);
        hint.setText("⚠️ 修改后需重启应用才能生效，调试完毕请尽快恢复默认。\n⚠️ 未勾选即表示完全禁止该功能运行，相关配置设置将完全失效！");
        hint.setTextSize(12);
        hint.setTextColor(0xFFFF0000);
        hint.setTypeface(hint.getTypeface(), android.graphics.Typeface.BOLD);
        hint.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        rootLayout.addView(hint);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        final LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(container);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        final Map<CheckBox, String> childCheckBoxMap = new LinkedHashMap<>();
        final Map<CheckBox, List<CheckBox>> categoryCheckBoxMap = new LinkedHashMap<>();
        List<String> categories = getCategories();
        for (String category : categories) {
            LinearLayout categoryHeader = new LinearLayout(activity);
            categoryHeader.setOrientation(LinearLayout.HORIZONTAL);
            categoryHeader.setGravity(Gravity.CENTER_VERTICAL);
            categoryHeader.setPadding(More.spx(activity, 0), More.spx(activity, 20), More.spx(activity, 0), More.spx(activity, 10));
            final CheckBox catCheckBox = new CheckBox(activity);
            catCheckBox.setText(category);
            catCheckBox.setTextSize(16);
            catCheckBox.setTextColor(0xFF2196F3);
            catCheckBox.setTypeface(catCheckBox.getTypeface(), android.graphics.Typeface.BOLD);
            catCheckBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            categoryHeader.addView(catCheckBox);
            container.addView(categoryHeader);
            List<String> keys = getFeaturesForCategory(category);
            final List<CheckBox> childCheckBoxes = new ArrayList<>();
            for (String key : keys) {
                CheckBox cb = new CheckBox(activity);
                String display = FEATURE_DISPLAY_NAMES.get(key);
                boolean checked = currentState.get(key);
                // 设置初始文本（根据 checked 状态）
                updateCheckBoxText(cb, display, checked);
                cb.setChecked(checked);
                cb.setPadding(More.spx(activity, 60), More.spx(activity, 8), More.spx(activity, 20), More.spx(activity, 8));
                cb.setTag(key);
                container.addView(cb);
                childCheckBoxes.add(cb);
                childCheckBoxMap.put(cb, key);
                // 监听勾选变化，实时更新文本样式
                cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        String key = (String) buttonView.getTag();
                        String display = FEATURE_DISPLAY_NAMES.get(key);
                        updateCheckBoxText((CheckBox) buttonView, display, isChecked);
                        // 更新分类全选状态
                        boolean allChildChecked = true;
                        for (CheckBox child : childCheckBoxes) {
                            if (!child.isChecked()) {
                                allChildChecked = false;
                                break;
                            }
                        }
                        catCheckBox.setChecked(allChildChecked);
                    }
                });
            }
            categoryCheckBoxMap.put(catCheckBox, childCheckBoxes);
            boolean allChecked = true;
            for (CheckBox cb : childCheckBoxes) {
                if (!cb.isChecked()) {
                    allChecked = false;
                    break;
                }
            }
            catCheckBox.setChecked(allChecked);
            catCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    for (CheckBox cb : childCheckBoxes) {
                        cb.setChecked(isChecked);
                    }
                }
            });
            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            container.addView(divider);
        }
        LinearLayout buttonBar = new LinearLayout(activity);
        buttonBar.setOrientation(LinearLayout.HORIZONTAL);
        buttonBar.setGravity(Gravity.CENTER);
        buttonBar.setPadding(More.spx(activity, 20), More.spx(activity, 30), More.spx(activity, 20), More.spx(activity, 30));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(More.dpInt(activity, 8), 0, More.dpInt(activity, 8), 0);
        Button saveBtn = new Button(activity);
        saveBtn.setText("保存设置");
        saveBtn.setPadding(More.spx(activity, 30), More.spx(activity, 15), More.spx(activity, 30), More.spx(activity, 15));
        saveBtn.setTextColor(0xFFFFFFFF);
        saveBtn.setTextSize(14);
        saveBtn.setMinHeight(0);
        saveBtn.setMinWidth(0);
        saveBtn.setBackground(DialogUi.getRoundButtonDrawable(0xFFFF9800));
        saveBtn.setLayoutParams(btnParams);
        buttonBar.addView(saveBtn);
        Button resetBtn = new Button(activity);
        resetBtn.setText("恢复默认");
        resetBtn.setPadding(More.spx(activity, 30), More.spx(activity, 15), More.spx(activity, 30), More.spx(activity, 15));
        resetBtn.setTextColor(0xFFFFFFFF);
        resetBtn.setTextSize(14);
        resetBtn.setMinHeight(0);
        resetBtn.setMinWidth(0);
        resetBtn.setBackground(DialogUi.getRoundButtonDrawable(0xFF4CAF50));
        resetBtn.setLayoutParams(btnParams);
        buttonBar.addView(resetBtn);
        Button closeBtn = new Button(activity);
        closeBtn.setText("返回应用");
        closeBtn.setPadding(More.spx(activity, 30), More.spx(activity, 15), More.spx(activity, 30), More.spx(activity, 15));
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setTextSize(14);
        closeBtn.setMinHeight(0);
        closeBtn.setMinWidth(0);
        closeBtn.setBackground(DialogUi.getRoundButtonDrawable(0xFF2196F3));
        closeBtn.setLayoutParams(btnParams);
        buttonBar.addView(closeBtn);
        rootLayout.addView(buttonBar);
        final AlertDialog dialog = new AlertDialog.Builder(activity, DialogUi.getDialogThemeResId())
                .setView(rootLayout)
                .create();
        DialogUi.tryElevateDialogToOverlay(dialog);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawableResource(android.R.color.white);
                    WindowManager.LayoutParams params = window.getAttributes();
                    params.width = WindowManager.LayoutParams.MATCH_PARENT;
                    params.height = WindowManager.LayoutParams.MATCH_PARENT;
                    params.gravity = Gravity.TOP;
                    window.setAttributes(params);
                }
            }
        });

        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Map<String, Boolean> newState = new LinkedHashMap<>();
                for (CheckBox cb : childCheckBoxMap.keySet()) {
                    String key = (String) cb.getTag();
                    newState.put(key, cb.isChecked());
                }

                boolean hasChanged = false;
                for (Map.Entry<String, Boolean> entry : originalState.entrySet()) {
                    String key = entry.getKey();
                    boolean originalVal = entry.getValue();
                    boolean currentVal = newState.getOrDefault(key, originalVal);
                    if (originalVal != currentVal) {
                        hasChanged = true;
                        break;
                    }
                }

                if (!hasChanged) {
                    ToastUtil.showUnique(activity, "未修改任何设置\n无需保存");
                    return;
                }

                saveDebugConfig(newState);
                dialog.dismiss();
                if (sHookInit != null) {
                    DialogUi.showRestartConfirmDialog(activity);
                } else {
                    ToastUtil.showUnique(activity, "配置已保存\n请重启应用生效");
                }
            }
        });

        resetBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean allChecked = true;
                for (CheckBox cb : childCheckBoxMap.keySet()) {
                    if (!cb.isChecked()) {
                        allChecked = false;
                        break;
                    }
                }
                if (allChecked) {
                    ToastUtil.showUnique(activity, "已是默认状态\n无需恢复");
                    return;
                }

                for (CheckBox cb : childCheckBoxMap.keySet()) {
                    cb.setChecked(true);
                }
                for (Map.Entry<CheckBox, List<CheckBox>> entry : categoryCheckBoxMap.entrySet()) {
                    entry.getKey().setChecked(true);
                }

                Map<String, Boolean> defaultState = new LinkedHashMap<>();
                for (String key : FEATURE_DISPLAY_NAMES.keySet()) {
                    defaultState.put(key, true);
                }
                saveDebugConfig(defaultState);
                dialog.dismiss();
                if (sHookInit != null) {
                    DialogUi.showRestartConfirmDialog(activity);
                } else {
                    ToastUtil.showUnique(activity, "已恢复默认\n请重启应用生效");
                }
            }
        });

        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }

// ===== 更新 CheckBox 文本样式  =====
    private static void updateCheckBoxText(CheckBox cb, String displayName, boolean checked) {
        String text;
        if (checked) {
            // 强制黑色，不加粗
            text = "<font color='#000000'>" + displayName + "</font>";
        } else {
            // 红色加粗，"(已禁用)" 小号
            text = "<b><font color='#F44336'>" + displayName + " <small>(已禁用)</small></font></b>";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cb.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
        } else {
            cb.setText(Html.fromHtml(text));
        }
    }

    private static final List<Integer> sKeySequence = new ArrayList<>();
    private static long sLastKeyTime = 0;
    public static void onVolumeKeyPressed(int keyCode, Activity activity) {
        if (activity == null) return;
        long now = System.currentTimeMillis();
        if (now - sLastKeyTime > KEY_SEQUENCE_TIMEOUT) {
            sKeySequence.clear();
        }
        sLastKeyTime = now;
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            sKeySequence.add(1);
        } else if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            sKeySequence.add(0);
        } else {
            return;
        }
        if (sKeySequence.size() >= 4) {
            int size = sKeySequence.size();
            if (sKeySequence.get(size - 4) == 1 &&
                    sKeySequence.get(size - 3) == 0 &&
                    sKeySequence.get(size - 2) == 1 &&
                    sKeySequence.get(size - 1) == 0) {
                ReaLog.log("system", "音量键 (上下上下) 触发调试模式");
                sKeySequence.clear();
                showDebugDialog(activity);
                return;
            }
        }
        if (sKeySequence.size() > 8) {
            sKeySequence.clear();
        }
    }
}

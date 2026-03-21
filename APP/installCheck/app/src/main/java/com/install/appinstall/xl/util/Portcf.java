package com.install.appinstall.xl.util;

import android.app.Activity;
import android.widget.TextView;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.HookInit.PackageConfig; // 注意内部类的引用方式

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class Portcf {

    // 导出配置，返回文件路径
    public static String exportConfig(Activity activity, String currentTargetApp) {
        try {
            File externalDir = activity.getExternalFilesDir(null);
            if (externalDir == null) {
                ToastUtil.show(activity, "无法访问存储");
                return null;
            }
            File configFile = new File(externalDir, "install_fake_config.json");

            JSONObject root = new JSONObject();
            JSONObject appConfig = new JSONObject();

            appConfig.put("install_status", HookInit.installStatusMap.getOrDefault(currentTargetApp, true));
            appConfig.put("floating_shown", HookInit.floatingShownMap.getOrDefault(currentTargetApp, true));
            appConfig.put("block_exit", HookInit.blockExitMap.getOrDefault(currentTargetApp, false));
            appConfig.put("permission_fake", HookInit.permissionFakeMap.getOrDefault(currentTargetApp, true));
            appConfig.put("launch_intercept", HookInit.launchInterceptMap.getOrDefault(currentTargetApp, true));

            List<String> userPackages = HookInit.userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
            appConfig.put("user_defined_packages", stringListToJsonArray(userPackages));

            List<String> excludedPackages = HookInit.excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
            appConfig.put("excluded_packages", stringListToJsonArray(excludedPackages));

            List<PackageConfig> pkgConfigs = HookInit.packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<>());
            JSONArray pkgConfigArray = new JSONArray();
            for (PackageConfig config : pkgConfigs) {
                JSONObject obj = new JSONObject();
                obj.put("packageName", config.packageName);
                obj.put("statusMode", config.statusMode);
                pkgConfigArray.put(obj);
            }
            appConfig.put("package_configs", pkgConfigArray);

            root.put(currentTargetApp, appConfig);

            FileOutputStream fos = new FileOutputStream(configFile);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
            writer.write(root.toString(2));
            writer.close();
            fos.close();

            return configFile.getAbsolutePath();
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    // 导入配置，返回状态字符串
    public static String importConfig(Activity activity, String currentTargetApp, TextView floatingView, HookInit hookInit) {
        try {
            File externalDir = activity.getExternalFilesDir(null);
            if (externalDir == null) {
                ToastUtil.show(activity, "无法访问存储");
                return "error";
            }
            File configFile = new File(externalDir, "install_fake_config.json");
            if (!configFile.exists()) {
                return "not_found";
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

            JSONObject root = new JSONObject(jsonBuilder.toString());

            if (!root.has(currentTargetApp)) {
                ToastUtil.show(activity, "配置文件中不包含当前应用的配置");
                return "error";
            }

            JSONObject appConfig = root.getJSONObject(currentTargetApp);

            HookInit.installStatusMap.put(currentTargetApp, appConfig.optBoolean("install_status", true));
            HookInit.floatingShownMap.put(currentTargetApp, appConfig.optBoolean("floating_shown", true));
            HookInit.blockExitMap.put(currentTargetApp, appConfig.optBoolean("block_exit", false));
            HookInit.permissionFakeMap.put(currentTargetApp, appConfig.optBoolean("permission_fake", true));
            HookInit.launchInterceptMap.put(currentTargetApp, appConfig.optBoolean("launch_intercept", true));

            JSONArray userArray = appConfig.optJSONArray("user_defined_packages");
            if (userArray != null) {
                List<String> userPackages = jsonArrayToStringList(userArray);
                HookInit.userDefinedPackagesMap.put(currentTargetApp, userPackages);
            }

            JSONArray excludedArray = appConfig.optJSONArray("excluded_packages");
            if (excludedArray != null) {
                List<String> excludedPackages = jsonArrayToStringList(excludedArray);
                HookInit.excludedPackagesMap.put(currentTargetApp, excludedPackages);
            }

            JSONArray pkgConfigArray = appConfig.optJSONArray("package_configs");
            if (pkgConfigArray != null) {
                List<PackageConfig> configs = new ArrayList<>();
                for (int i = 0; i < pkgConfigArray.length(); i++) {
                    JSONObject obj = pkgConfigArray.getJSONObject(i);
                    PackageConfig config = new PackageConfig(obj.getString("packageName"));
                    config.statusMode = obj.optString("statusMode", "follow");
                    configs.add(config);
                }
                HookInit.packageConfigMap.put(currentTargetApp, configs);
            }

            List<String> userPackages = HookInit.userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
            synchronized (HookInit.globalCapturedPackages) {
                HookInit.globalCapturedPackages.addAll(userPackages);
            }

            hookInit.saveConfigToFile();

            if (floatingView != null) {
                hookInit.updateFloatingView(activity);
            }

            ToastUtil.show(activity, "配置导入成功");
            return "success";
        } catch (Throwable t) {
            t.printStackTrace();
            return "error";
        }
    }

    // 获取配置文件摘要（用于导入前预览），返回字符串
    public static String getConfigSummary(Activity activity, String currentTargetApp) {
    try {
        File externalDir = activity.getExternalFilesDir(null);
        if (externalDir == null) return null;
        File configFile = new File(externalDir, "install_fake_config.json");
        if (!configFile.exists()) return null;

        FileInputStream fis = new FileInputStream(configFile);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        StringBuilder jsonBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonBuilder.append(line);
        }
        reader.close();
        fis.close();

        JSONObject root = new JSONObject(jsonBuilder.toString());
        if (!root.has(currentTargetApp)) {
            return "⚠️ <font color='#F44336'><b>配置文件中不包含当前应用的配置</b></font>";
        }

        JSONObject appConfig = root.getJSONObject(currentTargetApp);

        StringBuilder summary = new StringBuilder();
        summary.append("即将覆盖以下配置：<br><br>");
        summary.append("• 安装状态：").append(appConfig.optBoolean("install_status", true) ? "<font color='#4CAF50'>已安装</font>" : "<font color='#F44336'>未安装</font>").append("<br>");
        summary.append("• 拦截退出：").append(appConfig.optBoolean("block_exit", false) ? "<font color='#4CAF50'>开启</font>" : "<font color='#F44336'>关闭</font>").append("<br>");
        summary.append("• 权限伪造：").append(appConfig.optBoolean("permission_fake", true) ? "<font color='#4CAF50'>开启</font>" : "<font color='#F44336'>关闭</font>").append("<br>");
        summary.append("• 启动拦截：").append(appConfig.optBoolean("launch_intercept", true) ? "<font color='#4CAF50'>开启</font>" : "<font color='#F44336'>关闭</font>").append("<br>");

        JSONArray userArray = appConfig.optJSONArray("user_defined_packages");
        int userCount = userArray != null ? userArray.length() : 0;
        summary.append("• 伪造包名：<font color='#FF5722'><b>").append(userCount).append("</b></font> 个<br>");

        JSONArray excludedArray = appConfig.optJSONArray("excluded_packages");
        int excludedCount = excludedArray != null ? excludedArray.length() : 0;
        summary.append("• 排除包名：<font color='#FF5722'><b>").append(excludedCount).append("</b></font> 个<br>");

        JSONArray pkgArray = appConfig.optJSONArray("package_configs");
        int pkgCount = pkgArray != null ? pkgArray.length() : 0;
        summary.append("• 独立包配置：<font color='#FF5722'><b>").append(pkgCount).append("</b></font> 个<br>");

        return summary.toString();
    } catch (Throwable t) {
        t.printStackTrace();
        return "读取配置文件失败：" + t.getMessage();
    }
}

// 删除配置文件，返回是否成功
public static boolean deleteConfig(Activity activity) {
    try {
        File externalDir = activity.getExternalFilesDir(null);
        if (externalDir == null) return false;
        File configFile = new File(externalDir, "install_fake_config.json");
        if (configFile.exists()) {
            return configFile.delete();
        }
        return false;
    } catch (Throwable t) {
        t.printStackTrace();
        return false;
    }
}

    // 辅助方法：JSONArray 转 List<String>
    private static List<String> jsonArrayToStringList(JSONArray array) throws Exception {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String item = array.getString(i);
            if (item != null && !item.isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    // 辅助方法：List<String> 转 JSONArray
    private static JSONArray stringListToJsonArray(List<String> list) {
        JSONArray array = new JSONArray();
        for (String item : list) {
            if (item != null && !item.isEmpty()) {
                array.put(item);
            }
        }
        return array;
    }
}
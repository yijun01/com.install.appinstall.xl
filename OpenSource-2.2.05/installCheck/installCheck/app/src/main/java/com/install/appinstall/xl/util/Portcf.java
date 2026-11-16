package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.util.Collection;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.HookInit.PackageConfig;
import com.install.appinstall.xl.HookInit.InterceptPattern;

// 配置文件导入导出

public class Portcf {

    /** 配置文件格式版本标识，读取端不依赖它 */
    public static final int SCHEMA_VERSION = 2;

    /**
     * 导出时的字段顺序：按「状态 → 包名 → 开关 → 明细」四组排
     */
    private static final String[] GROUP_ORDER = {
            // 状态
            "install_status", "floating_shown", "permanent_hidden", "floating_x", "floating_y",
            // 包名
            "user_defined_packages", "excluded_packages", "package_configs",
            // 开关
            "user_disabled_auto_block", "block_exit", "super_block_exit",
            "permission_fake", "launch_intercept", "selinux_fake", "vendor_enabled",
            "force_default_back", "crash_protect_enabled",
            "hook_dialog_cancelable", "hook_window_flags", "hook_anti_cancel_finish",
            "hook_vibrator",
            // 明细 / 记录
            "permission_fake_detail", "anti_detection_detail", "vpn_fake_detail",
            "share_fake_detail", "auto_actions", "auto_records",
            "intercept_patterns", "rea_log_pause"
    };

    /** 按分组顺序把 key 排一遍，不在表里的追加在最后*/
    private static List<String> orderedKeys(List<String> keys) {
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < GROUP_ORDER.length; i++) {
            if (keys.contains(GROUP_ORDER[i])) out.add(GROUP_ORDER[i]);
        }
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            if (!out.contains(k)) out.add(k);
        }
        return out;
    }

    // ============================================================
    //  配置项注册表
    //  一处 reg(key, 标签, 读写器) = 导出写入 + 导入读取
    //  加新字段只改这里，读写器内部一律「用到时才取 HookInit 的 Map」，不在注册时引用，避免 Portcf / HookInit 两个类初始化互相触发。
    // ============================================================
    /** 一项配置的读写器 */
    private interface ItemCodec {
        void write(JSONObject target, String app) throws Exception;
        void read(String app, JSONObject src) throws Exception;
    }

    private interface BoolMapRef {
        Map<String, Boolean> get();
    }

    private interface FloatMapRef {
        Map<String, Float> get();
    }

    private interface BoolDetailMapRef {
        Map<String, Map<String, Boolean>> get();
    }

    /** 合法字段集（惰性取值，避免静态注册期触发 HookInit 类初始化） */
    private interface DetailKeysRef {
        java.util.Set<String> get();
    }

    private interface StringListMapRef {
        Map<String, List<String>> get();
    }

    private interface StringMapRef {
        Map<String, Map<String, String>> get();
    }

    private static final LinkedHashMap<String, ItemCodec> CODECS =
            new LinkedHashMap<String, ItemCodec>();

    private static final LinkedHashMap<String, String> CONFIG_ITEMS =
            new LinkedHashMap<String, String>();

    /** 注册一项配置：标签直接进界面清单，注册顺序就是界面顺序 */
    private static void reg(String key, String label, ItemCodec codec) {
        CODECS.put(key, codec);
        CONFIG_ITEMS.put(key, label);
    }

    /** 单一布尔开关 */
    private static ItemCodec boolCodec(final String key, final BoolMapRef ref,
            final boolean def) {
        return new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, Boolean> m = ref.get();
                t.put(key, m == null ? Boolean.valueOf(def) : m.getOrDefault(app, Boolean.valueOf(def)));
            }

            public void read(String app, JSONObject src) throws Exception {
                Map<String, Boolean> m = ref.get();
                if (m != null) m.put(app, Boolean.valueOf(src.optBoolean(key, def)));
            }
        };
    }

    /** 悬浮窗坐标：文件里存字符串，允许 "null" 表示没记录过 */
    private static ItemCodec floatCodec(final String key, final FloatMapRef ref) {
        return new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, Float> m = ref.get();
                Float v = (m == null) ? null : m.get(app);
                t.put(key, v != null ? String.valueOf(v) : "null");
            }

            public void read(String app, JSONObject src) throws Exception {
                Map<String, Float> m = ref.get();
                if (m == null) return;
                String val = src.optString(key, "null");
                if (!"null".equals(val) && !val.isEmpty()) {
                    try {
                        m.put(app, Float.valueOf(Float.parseFloat(val)));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    m.remove(app);
                }
            }
        };
    }

    /** 子项布尔表；useDefaultShare 时缺省取默认表 */
    private static ItemCodec boolMapCodec(final String key, final BoolDetailMapRef ref,
            final boolean useDefaultShare, final DetailKeysRef keysRef) {
        return new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, Map<String, Boolean>> store = ref.get();
                Map<String, Boolean> m = (store == null) ? null : store.get(app);
                if (m == null) {
                    m = useDefaultShare ? HookInit.getDefaultShareDetailMap()
                            : new HashMap<String, Boolean>();
                }
                // TreeMap 排序。
                t.put(key, new JSONObject(new java.util.TreeMap<String, Boolean>(m)));
            }

            public void read(String app, JSONObject src) throws Exception {
                Map<String, Map<String, Boolean>> store = ref.get();
                if (store == null) return;
                JSONObject detail = src.optJSONObject(key);
                Map<String, Boolean> m = new HashMap<String, Boolean>();
                java.util.Set<String> valid = (keysRef == null) ? null : keysRef.get();
                if (detail != null) {
                    Iterator<String> it = detail.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (valid != null && !valid.contains(k)) continue;
                        m.put(k, Boolean.valueOf(detail.getBoolean(k)));
                    }
                }
                store.put(app, m);
            }
        };
    }

    /** 包名列表：导入时并入现有列表，不覆盖数据*/
    private static ItemCodec stringListCodec(final String key, final StringListMapRef ref) {
        return new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, List<String>> store = ref.get();
                List<String> list = (store == null) ? null : store.get(app);
                t.put(key, stringListToJsonArray(list != null ? list : new ArrayList<String>()));
            }

            public void read(String app, JSONObject src) throws Exception {
                Map<String, List<String>> store = ref.get();
                if (store == null) return;
                JSONArray arr = src.optJSONArray(key);
                List<String> imported = arr != null ? jsonArrayToStringList(arr)
                        : new ArrayList<String>();
                List<String> cur = store.get(app);
                if (cur == null) {
                    cur = new ArrayList<String>();
                    store.put(app, cur);
                }
                for (String pkg : imported) {
                    if (!cur.contains(pkg)) cur.add(pkg);
                }
            }
        };
    }

    /** 通用字符串 Map（只导出非空项） */
    private static ItemCodec stringMapCodec(final String key, final StringMapRef ref) {
        return new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, Map<String, String>> store = ref.get();
                if (store == null) return;
                Map<String, String> m = store.get(app);
                if (m != null && !m.isEmpty()) {
                    t.put(key, new JSONObject(new java.util.TreeMap<String, String>(m)));
                }
            }

            public void read(String app, JSONObject src) throws Exception {
                JSONObject obj = src.optJSONObject(key);
                if (obj == null) return;
                Map<String, Map<String, String>> store = ref.get();
                if (store == null) return;
                Map<String, String> cur = store.get(app);
                if (cur == null) {
                    cur = new HashMap<String, String>();
                    store.put(app, cur);
                }
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    cur.put(k, obj.getString(k));
                }
            }
        };
    }

    static {
        // ---- 状态 ----
        reg("install_status", "安装状态", boolCodec("install_status", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.installStatusMap;
            }
        }, true));
        reg("floating_shown", "悬浮窗显示", boolCodec("floating_shown", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.floatingShownMap;
            }
        }, true));
        reg("permanent_hidden", "永久隐藏", boolCodec("permanent_hidden", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.permanentHiddenMap;
            }
        }, false));
        reg("floating_x", "悬浮窗X坐标", floatCodec("floating_x", new FloatMapRef() {
            public Map<String, Float> get() {
                return HookInit.floatingXMap;
            }
        }));
        reg("floating_y", "悬浮窗Y坐标", floatCodec("floating_y", new FloatMapRef() {
            public Map<String, Float> get() {
                return HookInit.floatingYMap;
            }
        }));

        // ---- 开关 ----
        reg("user_disabled_auto_block", "自动拦截",
                boolCodec("user_disabled_auto_block", new BoolMapRef() {
                    public Map<String, Boolean> get() {
                        return HookInit.userDisabledAutoBlockMap;
                    }
                }, false));
        reg("block_exit", "普通拦截", boolCodec("block_exit", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.blockExitMap;
            }
        }, false));
        reg("super_block_exit", "超强拦截", boolCodec("super_block_exit", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.superBlockExitMap;
            }
        }, false));
        reg("permission_fake", "权限防护", boolCodec("permission_fake", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.permissionFakeMap;
            }
        }, true));
        reg("launch_intercept", "启动拦截", boolCodec("launch_intercept", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.launchInterceptMap;
            }
        }, true));
        reg("selinux_fake", "SELinux伪装", boolCodec("selinux_fake", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.selinuxFakeMap;
            }
        }, true));
        reg("vendor_enabled", "强制包启用", boolCodec("vendor_enabled", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.vendorChoiceMap;
            }
        }, false));
        reg("force_default_back", "返回键控制", boolCodec("force_default_back", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.forceDefaultBackMap;
            }
        }, false));
        reg("hook_dialog_cancelable", "对话框强制取消", boolCodec("hook_dialog_cancelable", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.hookDialogCancelableMap;
            }
        }, true));
        reg("hook_window_flags", "屏幕限制解除", boolCodec("hook_window_flags", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.hookWindowFlagsMap;
            }
        }, true));
        reg("hook_anti_cancel_finish", "对话框强制取消(防界面关闭)", boolCodec("hook_anti_cancel_finish", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.hookAntiCancelFinishMap;
            }
        }, true));
        reg("hook_vibrator", "震动控制", boolCodec("hook_vibrator", new BoolMapRef() {
            public Map<String, Boolean> get() {
                return HookInit.hookVibratorMap;
            }
        }, true));
        reg("crash_protect_enabled", "闪退防护",
                boolCodec("crash_protect_enabled", new BoolMapRef() {
                    public Map<String, Boolean> get() {
                        return HookInit.crashProtectEnabledMap;
                    }
                }, true));

        // ---- 明细（子项表） ----
        reg("anti_detection_detail", "痕迹检测",
                boolMapCodec("anti_detection_detail", new BoolDetailMapRef() {
                    public Map<String, Map<String, Boolean>> get() {
                        return HookInit.antiDetectionDetailMap;
                    }
                }, false, new DetailKeysRef() {
                    public java.util.Set<String> get() {
                        return HookInit.getDefaultAntiDetectionMap().keySet();
                    }
                }));
        reg("vpn_fake_detail", "网络代理", boolMapCodec("vpn_fake_detail", new BoolDetailMapRef() {
            public Map<String, Map<String, Boolean>> get() {
                return HookInit.vpnFakeDetailMap;
            }
        }, false, new DetailKeysRef() {
            public java.util.Set<String> get() {
                return HookInit.getDefaultVpnDetailMap().keySet();
            }
        }));
        reg("permission_fake_detail", "权限防护",
                boolMapCodec("permission_fake_detail", new BoolDetailMapRef() {
                    public Map<String, Map<String, Boolean>> get() {
                        return HookInit.permissionFakeDetailMap;
                    }
                }, false, null));
        reg("share_fake_detail", "假装分享", boolMapCodec("share_fake_detail", new BoolDetailMapRef() {
            public Map<String, Map<String, Boolean>> get() {
                return HookInit.shareFakeDetailMap;
            }
        }, true, new DetailKeysRef() {
            public java.util.Set<String> get() {
                return HookInit.getDefaultShareDetailMap().keySet();
            }
        }));

        // ---- 包名列表 ----
        reg("user_defined_packages", "自定义包名",
                stringListCodec("user_defined_packages", new StringListMapRef() {
                    public Map<String, List<String>> get() {
                        return HookInit.userDefinedPackagesMap;
                    }
                }));
        reg("excluded_packages", "已排除包名",
                stringListCodec("excluded_packages", new StringListMapRef() {
                    public Map<String, List<String>> get() {
                        return HookInit.excludedPackagesMap;
                    }
                }));
        reg("package_configs", "独立包名配置", new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                List<PackageConfig> configs = HookInit.packageConfigMap.get(app);
                JSONArray arr = new JSONArray();
                if (configs != null) {
                    for (PackageConfig cfg : configs) {
                        JSONObject obj = new JSONObject();
                        obj.put("packageName", cfg.packageName);
                        obj.put("statusMode", cfg.statusMode);
                        arr.put(obj);
                    }
                }
                t.put("package_configs", arr);
            }

            public void read(String app, JSONObject src) throws Exception {
                JSONArray arr = src.optJSONArray("package_configs");
                List<PackageConfig> importConfigs = new ArrayList<PackageConfig>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        PackageConfig cfg = new PackageConfig(obj.getString("packageName"));
                        cfg.statusMode = obj.optString("statusMode", "follow");
                        importConfigs.add(cfg);
                    }
                }
                List<PackageConfig> currentConfigs = HookInit.packageConfigMap.get(app);
                Map<String, PackageConfig> configMap = new HashMap<String, PackageConfig>();
                if (currentConfigs != null) {
                    for (PackageConfig cfg : currentConfigs) {
                        configMap.put(cfg.packageName, cfg);
                    }
                }
                for (PackageConfig cfg : importConfigs) {
                    configMap.put(cfg.packageName, cfg);
                }
                HookInit.packageConfigMap.put(app, new ArrayList<PackageConfig>(configMap.values()));
            }
        });

        // ---- 记录 ----
        reg("auto_actions", "黑白名单",
                stringMapCodec("auto_actions", new StringMapRef() {
                    public Map<String, Map<String, String>> get() {
                        return HookInit.autoActionMap;
                    }
                }));
        reg("auto_records", "智能记录", new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, List<String>> records = HookInit.autoChoiceRecordsMap.get(app);
                if (records == null || records.isEmpty()) return;
                JSONObject recJson = new JSONObject();
                for (Map.Entry<String, List<String>> entry : records.entrySet()) {
                    JSONArray arr = new JSONArray();
                    List<String> v = entry.getValue();
                    if (v != null) {
                        for (String s : v) arr.put(s);
                    }
                    recJson.put(entry.getKey(), arr);
                }
                t.put("auto_records", recJson);
            }

            public void read(String app, JSONObject src) throws Exception {
                JSONObject obj = src.optJSONObject("auto_records");
                if (obj == null) return;
                Map<String, List<String>> cur = HookInit.autoChoiceRecordsMap.get(app);
                if (cur == null) {
                    cur = new HashMap<String, List<String>>();
                    HookInit.autoChoiceRecordsMap.put(app, cur);
                }
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONArray arr = obj.getJSONArray(k);
                    List<String> list = new ArrayList<String>();
                    for (int i = 0; i < arr.length(); i++) list.add(arr.getString(i));
                    cur.put(k, list);
                }
            }
        });
        reg("intercept_patterns", "拦截模式", new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                List<InterceptPattern> patterns = HookInit.interceptPatternsMap.get(app);
                if (patterns == null || patterns.isEmpty()) return;
                JSONArray arr = new JSONArray();
                for (InterceptPattern p : patterns) {
                    JSONObject obj = new JSONObject();
                    obj.put("pattern_hash", p.patternHash);
                    obj.put("installed_packages", stringListToJsonArray(p.installedPackages));
                    obj.put("not_installed_packages", stringListToJsonArray(p.notInstalledPackages));
                    obj.put("user_choice", p.userChoice != null ? p.userChoice : "");
                    obj.put("choice_count", p.choiceCount);
                    obj.put("last_detected_time", p.lastDetectedTime);
                    obj.put("silent_intercept", p.silentIntercept);
                    arr.put(obj);
                }
                t.put("intercept_patterns", arr);
            }

            public void read(String app, JSONObject src) throws Exception {
                JSONArray arr = src.optJSONArray("intercept_patterns");
                if (arr == null) return;
                Map<String, InterceptPattern> patternMap = new HashMap<String, InterceptPattern>();
                List<InterceptPattern> currentPatterns = HookInit.interceptPatternsMap.get(app);
                if (currentPatterns != null) {
                    for (InterceptPattern p : currentPatterns) {
                        patternMap.put(p.patternHash, p);
                    }
                }
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    InterceptPattern p = new InterceptPattern(
                            obj.getString("pattern_hash"),
                            jsonArrayToStringList(obj.getJSONArray("installed_packages")),
                            jsonArrayToStringList(obj.getJSONArray("not_installed_packages")));
                    p.userChoice = obj.optString("user_choice", "");
                    p.choiceCount = obj.optInt("choice_count", 0);
                    p.lastDetectedTime = obj.optLong("last_detected_time", System.currentTimeMillis());
                    p.silentIntercept = obj.optBoolean("silent_intercept", false);
                    patternMap.put(p.patternHash, p);
                }
                HookInit.interceptPatternsMap.put(app,
                        new ArrayList<InterceptPattern>(patternMap.values()));
            }
        });
        reg("rea_log_pause", "日志运行状态", new ItemCodec() {
            public void write(JSONObject t, String app) throws Exception {
                Map<String, Boolean> pauseMap = HookInit.reaLogPauseMap.get(app);
                if (pauseMap != null && !pauseMap.isEmpty()) {
                    t.put("rea_log_pause",
                            new JSONObject(new java.util.TreeMap<String, Boolean>(pauseMap)));
                }
            }

            public void read(String app, JSONObject src) throws Exception {
                JSONObject obj = src.optJSONObject("rea_log_pause");
                if (obj == null) return;
                Map<String, Boolean> cur = HookInit.reaLogPauseMap.get(app);
                if (cur == null) {
                    cur = new ConcurrentHashMap<String, Boolean>();
                    HookInit.reaLogPauseMap.put(app, cur);
                }
                Iterator<String> it = obj.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    cur.put(k, Boolean.valueOf(obj.getBoolean(k)));
                }
            }
        });
    }

    // ========== 空监听器工具方法 ==========
    private static DialogInterface.OnClickListener emptyListener() {
        return new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                d.dismiss();
            }
        };
    }

    public static String exportConfig(Activity activity, String targetApp, List<String> keysToExport) {
        ReaLog.log("config", "开始导出配置: " + targetApp + ", 选定项数: " + (keysToExport == null ? "全部" : keysToExport.size()));
        try {
            File dir = activity.getExternalFilesDir(null);
            if (dir == null) {
                ReaLog.log("config", "导出失败: 无法获取外部存储目录");
                return "storage_error";
            }
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "installcf_" + targetApp + "_" + timeStamp + ".json";
            File configFile = new File(dir, fileName);
            JSONObject appConfig = buildConfigJson(targetApp, keysToExport);
            if (appConfig == null || appConfig.length() == 0) {
                ReaLog.log("config", "导出失败: 没有有效配置项可导出");
                return "no_items";
            }
            JSONObject root = new JSONObject();
            root.put(targetApp, appConfig);
            writeJsonToFile(configFile, root);
            int total = (keysToExport == null || keysToExport.isEmpty()) ? CONFIG_ITEMS.size() : keysToExport.size();
            ReaLog.log("config", "导出成功: " + total + " 项, 路径: " + configFile.getAbsolutePath());
            return "success:" + total + ":" + configFile.getAbsolutePath();
        } catch (Throwable t) {
            ReaLog.log("config", "导出异常: " + t.getMessage());
            return "error:" + t.getMessage();
        }
    }

    private static JSONObject buildConfigJson(String targetApp, List<String> keysToExport)
            throws Exception {
        JSONObject appConfig = new JSONObject();
        boolean exportAll = (keysToExport == null || keysToExport.isEmpty());
        if (exportAll) keysToExport = new ArrayList<>(CONFIG_ITEMS.keySet());
        // permanent_hidden 无论是否勾选都要导出（否则取消勾选一次就把隐藏状态弄丢）
        List<String> exportKeys = new ArrayList<String>(keysToExport);
        if (!exportKeys.contains("permanent_hidden")
                && HookInit.permanentHiddenMap.get(targetApp) != null) {
            exportKeys.add("permanent_hidden");
        }
        keysToExport = orderedKeys(exportKeys);
        appConfig.put("_schema", SCHEMA_VERSION);
        for (String key : keysToExport) {
            try {
                ItemCodec codec = CODECS.get(key);
                if (codec != null) codec.write(appConfig, targetApp);
            } catch (Throwable t) {
                ReaLog.log("config", "构建配置项 " + key + " 异常: " + t.getMessage());
            }
        }
        return appConfig.length() > 0 ? appConfig : null;
    }

    public static String importConfig(Activity activity, String targetApp, List<
                    String> keysToImport,
            TextView floatingView, HookInit hookInit, File importFile) {
        ReaLog.log("config", "开始导入配置: " + targetApp + ", 选定项数: " + (keysToImport == null ? "全部" : keysToImport.size()));
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) {
            ReaLog.log("config", "导入失败: 无法访问存储");
            return "storage_error";
        }
        File fileToUse = importFile;
        if (fileToUse == null) {
            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File d, String name) {
                    return (name.startsWith("installcf_") && name.endsWith(".json")) ||
                            "install_fake_config.json".equals(name);
                }
            });
            if (files == null || files.length == 0) {
                ReaLog.log("config", "未找到配置文件");
                return "no_files";
            }
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });
            fileToUse = files[0];
            ReaLog.log("config", "自动选择最新配置文件: " + fileToUse.getName());
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(fileToUse), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            JSONObject root = new JSONObject(sb.toString());
            List<String> packages = getTopLevelPackages(root);
            if (packages.isEmpty()) {
                ReaLog.log("config", "配置文件中没有顶层包名");
                return "no_package";
            }
            String sourcePackage = packages.get(0);
            ReaLog.log("config", "读取到来源包名: " + sourcePackage);
            JSONObject appConfig = root.getJSONObject(sourcePackage);
            boolean importAll = (keysToImport == null || keysToImport.isEmpty());
            if (importAll) keysToImport = new ArrayList<>(CONFIG_ITEMS.keySet());
            int totalKeys = keysToImport.size();
            int successCount = 0;
            int skippedCount = 0;
            List<String> importedKeys = new ArrayList<>();
            for (String key : keysToImport) {
                if (!appConfig.has(key)) {
                    skippedCount++;
                    ReaLog.log("config", "配置项 " + key + " 在文件中不存在，跳过");
                    continue;
                }
                importedKeys.add(key);
                try {
                    applyConfigItem(targetApp, key, appConfig);
                    successCount++;
                } catch (Throwable t) {
                    skippedCount++;
                    ReaLog.log("config", "配置项 " + key + " 数据损坏，已跳过: " + t.getMessage());
                }
            }

            hookInit.saveConfigImmediate(); // 立即保存
            // 全功能配置免重启即时生效
            hookInit.applyAllConfigChanges(activity);
            ReaLog.log("config", "导入完成，成功 " + successCount + " 项，跳过 " + skippedCount + " 项");

            // 刷新 ReaLog 暂停状态缓存
            ReaLog.reloadPauseState();

            if (floatingView != null) {
                String statusText = HookInit.installStatusMap.getOrDefault(targetApp, true) ? "已安装" : "未安装";
                String blockText = (HookInit.blockExitMap.getOrDefault(targetApp, false) ||
                                HookInit.superBlockExitMap.getOrDefault(targetApp, false)) ? "[拦截]" : "";
                floatingView.setText("安装防护(" + statusText + ")" + blockText);
                floatingView.invalidate();
                floatingView.requestLayout();
            }
            hookInit.updateFloatingView(activity);

            ReaLog.log("config", "导入成功: " + successCount + " 项, 来源: " + sourcePackage + ", 跳过: " + skippedCount);
            // 强制刷新悬浮窗位置
            if (activity != null && !activity.isFinishing()) {
                try {
                    ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
                    View oldView = decorView.findViewWithTag("install_fake_floating");
                    if (oldView != null) {
                        decorView.removeView(oldView);
                    }
                    hookInit.updateFloatingView(activity);
                } catch (Throwable ignored) {
                }
            }

            return "success:" + totalKeys + ":" + successCount + ":" + skippedCount;
        } catch (Throwable t) {
            ReaLog.log("config", "导入异常: " + t.getMessage());
            return "error:" + t.getMessage();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void applyConfigItem(String targetApp, String key, JSONObject appConfig)
            throws Exception {
        if (key == null || key.startsWith("_")) return; // _schema 这类元字段不参与导入
        if (!CONFIG_ITEMS.containsKey(key)) {
            // 文件里存在当前版本不认识的字段：忽略，写入一条日志
            ReaLog.log("config", "导入时忽略未知字段: " + key);
            return;
        }
        ItemCodec codec = CODECS.get(key);
        if (codec != null) {
            codec.read(targetApp, appConfig);
        }
    }

    // ========== 所有对话框统一调用 DialogUi.createBoundedDialog ==========
    private static void showConfirmDialog(Activity activity, String title, String message,
            DialogInterface.OnClickListener onConfirm,
            DialogInterface.OnClickListener onCancel, HookInit hookInit) {
        AlertDialog dialog = DialogUi.createBoundedDialog(activity, title, message,
                new String[]{"下一步", "返回上一步"},
                new DialogInterface.OnClickListener[]{onConfirm, onCancel});
        if (dialog == null) {
            ReaLog.log("config", "显示确认对话框: 对话框创建失败，已跳过");
            return;
        }
        try {
            TextView msgView = dialog.findViewById(android.R.id.message);
            if (msgView != null) msgView.setTextIsSelectable(true);
        } catch (Throwable ignored) {
        }
        dialog.show();
    }

    /** 新界面用：某项配置当前值的文字描述（给勾选列表做副标题） */
    public static String configValueOf(String targetApp, String key) {
        try {
            String v = getConfigValueDisplay(targetApp, key);
            if (v == null || "未知".equals(v) || "读取失败".equals(v)) {
                // 不认识的字段：直接返回「沿用当前值」
                return "沿用当前值";
            }
            return v;
        } catch (Throwable t) {
            return "沿用当前值";
        }
    }

    /** 新界面用：本次导出的目标路径 */
    public static String exportPathFor(Activity activity, String targetApp) {
        return buildExportFilePath(activity, targetApp);
    }

    /** 新界面用：直接保存到应用目录 */
    public static void exportDirect(Activity activity, String targetApp,
            java.util.List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        doDirectExport(activity, targetApp, keys, buildExportFilePath(activity, targetApp));
    }

    /** 新界面用：另存为（系统文件选择器） */
    public static void exportSaveAs(Activity activity, String targetApp,
            java.util.List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        startSafExport(activity, targetApp, keys);
    }

    /** 新界面用：可导出的配置项表（key → 显示名），已排除 permanent_hidden */
    public static java.util.LinkedHashMap<String, String> getExportableItems() {
        java.util.LinkedHashMap<String, String> m = new java.util.LinkedHashMap<String, String>();
        for (String key : CONFIG_ITEMS.keySet()) {
            if (!"permanent_hidden".equals(key)) m.put(key, CONFIG_ITEMS.get(key));
        }
        return m;
    }

    /** 新界面用：读配置文件里实际存在的顶层包名 */
    public static java.util.List<String> readPackages(File file) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        try {
            if (file == null || !file.exists()) return out;
            JSONObject root = readJsonFromFile(file);
            out.addAll(getTopLevelPackages(root));
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * 新界面用：列出这个文件里**实际存在**的配置字段（不是代码里的白名单）。
     */
    public static java.util.LinkedHashMap<String, String> readItemKeys(File file, String pkg) {
        java.util.LinkedHashMap<String, String> out =
                new java.util.LinkedHashMap<String, String>();
        try {
            if (file == null || !file.exists()) return out;
            JSONObject root = readJsonFromFile(file);
            JSONObject appConfig = root.optJSONObject(pkg);
            if (appConfig == null) return out;
            // 先按注册表顺序列出文件里存在的字段（显示中文名）
            java.util.Iterator<String> kit = CONFIG_ITEMS.keySet().iterator();
            while (kit.hasNext()) {
                String k = kit.next();
                if (appConfig.has(k)) out.put(k, CONFIG_ITEMS.get(k));
            }
            // 文件/代码里没登记的字段追加在后面，显式标未知
            java.util.Iterator<String> it = appConfig.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k == null || k.startsWith("_")) continue; // _schema 这类元字段不列出来
                if (!out.containsKey(k)) out.put(k, "未知字段（" + k + "）");
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * 新界面用：读文件里某一项的**可读描述**
     */
    public static String fileValueOf(File file, String pkg, String key) {
        try {
            if (file == null || !file.exists() || key == null) return "";
            JSONObject root = readJsonFromFile(file);
            JSONObject appConfig = root.optJSONObject(pkg);
            if (appConfig == null || !appConfig.has(key)) return "";
            String v = getConfigValueDisplay(pkg, key, appConfig);
            if (v == null || "未知".equals(v)) return "";
            // 子项布尔表按「开 N / 总数」显示
            if (v.matches("\\d+ 项")) {
                JSONObject detail = appConfig.optJSONObject(key);
                if (detail != null) {
                    int on = 0, total = detail.length();
                    java.util.Iterator<String> it = detail.keys();
                    while (it.hasNext()) {
                        if (detail.optBoolean(it.next(), false)) on++;
                    }
                    v = on + "/" + total + " 项";
                }
            }
            return v;
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** 新界面用：可导入的配置项（与导出同一份清单，含「永久隐藏」） */
    public static java.util.LinkedHashMap<String, String> getImportableItems() {
        return new java.util.LinkedHashMap<String, String>(CONFIG_ITEMS);
    }

    /**
     * 新界面用：列出私有目录里的配置备份（倒序-新的排前面）
     */
    public static java.util.List<File> listBackupFiles(Activity activity) {
        java.util.List<File> out = new java.util.ArrayList<File>();
        try {
            File dir = activity.getExternalFilesDir(null);
            if (dir == null) return out;
            File[] files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File d, String name) {
                    return (name.startsWith("installcf_") && name.endsWith(".json")) ||
                            "install_fake_config.json".equals(name);
                }
            });
            if (files == null) return out;
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return Long.compare(f2.lastModified(), f1.lastModified());
                }
            });
            out.addAll(Arrays.asList(files));
        } catch (Throwable ignored) {
        }
        return out;
    }

    /**
     * 文件选择器成功收到配置文件：新界面=开 推入新UI / 新界面=关 使用DialogUi
     */
    public static void importPickedFile(final Activity activity, final String targetApp,
            final File file, final TextView floatingView, final HookInit hookInit) {
        try {
            com.install.appinstall.xl.ui.PageStack stack =
                    com.install.appinstall.xl.ui.PageStack.get();
            if (stack != null && activity != null && !activity.isFinishing()) {
                com.install.appinstall.xl.ui.UiPages.ImportPage page =
                        new com.install.appinstall.xl.ui.UiPages.ImportPage(floatingView, file);
                com.install.appinstall.xl.ui.PageStack.Page top = stack.topPage();
                // 新界面：已经在导入页里再选一次文件：替换栈顶
                if (top != null && top.key() != null && top.key().startsWith("import")) {
                    stack.replaceTop(page);
                } else {
                    stack.push(page);
                }
                return;
            }
        } catch (Throwable ignored) {
        }
        doImportFile(activity, targetApp, file, floatingView, hookInit);
    }

    /** 新界面用：调起系统文件选择器导入*/
    public static void startImportFromPicker(Activity activity, String targetApp,
            TextView floatingView, HookInit hookInit) {
        startSafImport(activity, targetApp, floatingView, hookInit);
    }

    /**
     * 新界面用：按指定的文件 + 配置项执行导入，并统一处理结果提示。
     */
    public static String importFromFile(Activity activity, String targetApp,
            java.util.List<String> keys, TextView floatingView, final HookInit hookInit,
            final File file) {
        if (file == null) return "no_files";
        String result = importConfig(activity, targetApp, keys, floatingView, hookInit, file);
        try {
            if (result != null && result.startsWith("success:")) {
                String[] parts = result.split(":");
                int total = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                int success = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                int skipped = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                String msg = "总选择配置 " + total + " 项\n已成功覆盖 " + success + " 项";
                msg += skipped > 0 ? ("\n已跳过损坏 " + skipped + " 项") : "\n全部导入成功！";
                ToastUtil.show(activity, msg);
                ReaLog.log("config", "导入结果: 成功 " + success + ", 跳过 " + skipped);
                if (floatingView != null) {
                    String statusText = HookInit.installStatusMap.getOrDefault(targetApp, true)
                            ? "已安装" : "未安装";
                    String blockText = (HookInit.blockExitMap.getOrDefault(targetApp, false)
                            || HookInit.superBlockExitMap.getOrDefault(targetApp, false))
                            ? "[拦截]" : "";
                    floatingView.setText("安装防护(" + statusText + ")" + blockText);
                    floatingView.invalidate();
                    floatingView.requestLayout();
                }
                if (hookInit != null) {
                    hookInit.updateFloatingView(activity);
                    try {
                        hookInit.applyImportedRuntime(activity, targetApp);
                    } catch (Throwable ignored) {
                    }
                }
                if ("install_fake_config.json".equals(file.getName())) {
                    More.postOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            if (file.exists() && file.delete()) {
                                ReaLog.log("config", "已删除旧格式配置文件: "
                                        + file.getAbsolutePath());
                            }
                        }
                    }, 3000);
                }
            } else {
                ToastUtil.show(activity, importFailText(result));
                ReaLog.log("config", "导入失败: " + result);
            }
        } catch (Throwable t) {
            ReaLog.log("config", "导入结果处理异常: " + t.getMessage());
        }
        return result;
    }

    /** 导入失败码 - 转中文 */
    private static String importFailText(String result) {
        if (result == null) return "导入失败：未知错误";
        if (result.startsWith("error:")) return "导入失败：" + result.substring(6);
        if ("storage_error".equals(result)) return "导入失败：无法访问存储，请检查文件权限";
        if ("no_package".equals(result)) return "导入失败：配置里没有当前应用的数据";
        if ("no_files".equals(result)) return "导入失败：没有找到可导入的配置文件";
        return "导入失败：" + result;
    }
    
    // 旧UI Dialog
    public static void showExportSelectionDialog(final Activity activity, final String targetApp, final HookInit hookInit) {
        ReaLog.log("config", "显示导出选择对话框");
        // ===== 过滤掉 permanent_hidden =====
        final Map<String, Boolean> selectedMap = new LinkedHashMap<String, Boolean>();
        LinkedHashMap<String, String> displayMap = new LinkedHashMap<>();
        for (String key : CONFIG_ITEMS.keySet()) {
            if (!"permanent_hidden".equals(key)) {
                selectedMap.put(key, true);
                displayMap.put(key, CONFIG_ITEMS.get(key));
            }
        }

        Button cleanBtn = new Button(activity);
        cleanBtn.setText("清理配置");
        cleanBtn.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        cleanBtn.setTextColor(0xFFFFFFFF);
        cleanBtn.setBackground(DialogUi.getRoundButtonDrawable(0xAAFF6347));
        cleanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDeleteConfigDialog(activity, targetApp, hookInit);
            }
        });

        LinearLayout selectionLayout = createSelectionLayout(activity, selectedMap, displayMap, targetApp, true, cleanBtn, null);
        LinearLayout titleLayout = createTitleWithTip(activity, " 选择要导出的配置项", "<b>注：仅导出已勾选的配置！</b>");

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, null, null,
                new String[]{"下一步", "取消导出"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                List<String> selectedKeys = getSelectedKeys(selectedMap);
                                if (selectedKeys.isEmpty()) {
                                    ToastUtil.show(activity, "请至少选择一项");
                                    ReaLog.log("config", "导出取消: 未选择任何项");
                                    d.dismiss();
                                    showExportSelectionDialog(activity, targetApp, hookInit);
                                    return;
                                }
                                String filePath = buildExportFilePath(activity, targetApp);
                                ReaLog.log("config", "用户确定导出，选定 " + selectedKeys.size() + " 项");
                                showExportConfirmDialog(activity, targetApp, selectedKeys, filePath, hookInit);
                                d.dismiss();
                            }
                        },
                        emptyListener()
                },
                selectionLayout
        );
        dialog.setCustomTitle(titleLayout);
        dialog.show();
    }

    private static void showExportConfirmDialog(final Activity activity, final String targetApp,
            final List<String> selectedKeys, final String filePath,
            final HookInit hookInit) {
        ReaLog.log("config", "显示导出确认对话框，路径: " + filePath);
        String msg = buildConfirmMessage(selectedKeys, targetApp);
        msg += "<br><br><b>导出路径：</b><font color='#2196F3'>" + filePath + "</font>";

        int total = CONFIG_ITEMS.size();
        int selected = selectedKeys.size();
        int skipped = total - selected;
        msg +=
                "<br><br>即将导出 <b><font color='#FF5722'>" + selected + "</font></b> 项，未勾选/忽略 <b><font color='#FF5722'>" + skipped + "</font></b> 项";
        msg += "<br>确定要 <b><font color='#FF5722'>继续并执行</font></b> 吗？";

        showConfirmDialog(activity, "确认导出以下配置项:", msg,
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                ReaLog.log("config", "用户确认导出，选择保存方式");
                d.dismiss();
                showExportWayDialog(activity, targetApp, selectedKeys, filePath, hookInit);
            }
        },
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                ReaLog.log("config", "用户取消导出");
                d.dismiss();
                showExportSelectionDialog(activity, targetApp, hookInit);
            }
        },
        hookInit
        );
    }

    /** 询问导出保存方式：直接保存（应用私有目录）或 另存为（系统文件选择器自选目录） */
    private static void showExportWayDialog(final Activity activity, final String targetApp,
            final List<String> selectedKeys, final String filePath,
            final HookInit hookInit) {
        ReaLog.log("config", "显示导出方式选择对话框");
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));

        TextView tip = new TextView(activity);
        tip.setText("请选择保存方式：");
        tip.setTextSize(14);
        tip.setTextColor(0xFF333333);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        layout.addView(tip);

        final String[] options = {"直接保存（应用私有目录）", "另存为（自选储存路径）"};
        final int checked = 0; // 默认直接保存
        final LinearLayout radioContainer = new LinearLayout(activity);
        radioContainer.setOrientation(LinearLayout.VERTICAL);

        final android.widget.RadioButton[] radioButtons = new android.widget.RadioButton[options.length];
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            android.widget.RadioButton rb = new android.widget.RadioButton(activity);
            rb.setText(options[i]);
            rb.setTextSize(14);
            rb.setTextColor(0xFF333333);
            rb.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
            rb.setChecked(i == checked);
            rb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < radioButtons.length; j++) {
                        radioButtons[j].setChecked(j == index);
                    }
                }
            });
            radioContainer.addView(rb);
            radioButtons[i] = rb;
            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            radioContainer.addView(divider);
        }
        layout.addView(radioContainer);

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "导出到", null,
                new String[]{"确定执行", "返回上一页", "取消导出"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                int pos = 0;
                                for (int i = 0; i < radioButtons.length; i++) {
                                    if (radioButtons[i].isChecked()) {
                                        pos = i;
                                        break;
                                    }
                                }
                                d.dismiss();
                                if (pos == 1) {
                                    ReaLog.log("config", "用户选择另存为（SAF）");
                                    startSafExport(activity, targetApp, selectedKeys);
                                } else {
                                    ReaLog.log("config", "用户选择直接保存到私有目录");
                                    doDirectExport(activity, targetApp, selectedKeys, filePath);
                                }
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ReaLog.log("config", "导出方式返回上一页");
                                d.dismiss();
                                showExportConfirmDialog(activity, targetApp, selectedKeys, filePath, hookInit);
                            }
                        },
                        emptyListener()
                },
                layout
        );
        dialog.show();
    }

    /** 直接保存到应用私有目录 */
    private static void doDirectExport(final Activity activity, final String targetApp,
            final List<String> selectedKeys, final String filePath) {
        String result = exportConfig(activity, targetApp, selectedKeys);
        if (result != null && result.startsWith("success:")) {
            String[] parts = result.split(":");
            int total = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            String path = parts.length > 2 ? parts[2] : filePath;
            try {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("config_path", path);
                clipboard.setPrimaryClip(clip);
            } catch (Throwable ignored) {
            }
            ToastUtil.show(activity, "✅ 已导出 " + total + " 项配置\n\n成功复制路径:\n" + path);
            ReaLog.log("config", "导出成功，共 " + total + " 项");
        } else {
            ToastUtil.show(activity, "导出失败：" + (result != null ? result : "未知错误"));
            ReaLog.log("config", "导出失败: " + result);
        }
    }

    /** 另存为：调起系统文件选择器让用户选保存位置（默认名带包名+时间戳） */
    private static void startSafExport(final Activity activity, final String targetApp,
            final List<String> selectedKeys) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String suggestedName = "installcf_" + targetApp + "_" + timeStamp + ".json";
        SafPicker.pickExportLocation(activity, suggestedName, new SafPicker.UriCallback() {
            @Override
            public void onResult(Uri uri) {
                if (uri == null) {
                    ReaLog.log("config", "SAF 导出已取消");
                    return; // 用户取消：静默返回
                }
                exportToUri(activity, targetApp, selectedKeys, uri);
            }
        });
    }

    /** 导出到 SAF 所选位置 */
    private static void exportToUri(Activity activity, String targetApp, List<
                    String> keysToExport, Uri uri) {
        OutputStream os = null;
        OutputStreamWriter writer = null;
        try {
            JSONObject appConfig = buildConfigJson(targetApp, keysToExport);
            if (appConfig == null || appConfig.length() == 0) {
                ToastUtil.show(activity, "没有有效配置项可导出");
                ReaLog.log("config", "SAF 导出失败: 没有有效配置项");
                return;
            }
            JSONObject root = new JSONObject();
            root.put(targetApp, appConfig);
            os = activity.getContentResolver().openOutputStream(uri);
            if (os == null) throw new IOException("openOutputStream 返回 null");
            writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            writer.write(root.toString(2));
            writer.flush();
            int total = (keysToExport == null || keysToExport.isEmpty()) ? CONFIG_ITEMS.size() : keysToExport.size();
            String displayName = uri.getLastPathSegment();
            ReaLog.log("config", "SAF 导出成功: " + total + " 项, 位置: " + displayName);
            ToastUtil.show(activity, "✅ 已导出 " + total + " 项配置\n已保存到所选位置:\n" + displayName);
        } catch (Throwable t) {
            ReaLog.log("config", "SAF 导出失败: " + t.getMessage());
            ToastUtil.show(activity, "导出失败：" + t.getMessage());
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            } else if (os != null) {
                try {
                    os.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static void showImportSelectionDialog(final Activity activity, final String targetApp,
            final TextView floatingView, final HookInit hookInit) {
        ReaLog.log("config", "显示导入选择对话框");
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) {
            ToastUtil.show(activity, "无法访问存储");
            return;
        }
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return (name.startsWith("installcf_") && name.endsWith(".json")) ||
                        "install_fake_config.json".equals(name);
            }
        });
        if (files == null || files.length == 0) {
            // 私有目录没有配置文件：直接调起系统文件选择器
            ReaLog.log("config", "未找到配置文件，直接打开文件选择器");
            startSafImport(activity, targetApp, floatingView, hookInit);
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });
        final List<File> fileList = new ArrayList<File>(Arrays.asList(files));
        ReaLog.log("config", "找到 " + fileList.size() + " 个配置文件");
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));
        TextView tipText = new TextView(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tipText.setText(Html.fromHtml("<b>请选择要<font color='#FF5722'><b>导入</b></font>的配置文件</b>", Html.FROM_HTML_MODE_LEGACY));
        } else {
            tipText.setText(Html.fromHtml("<b>请选择要<font color='#FF5722'><b>导入</b></font>的配置文件</b>"));
        }
        tipText.setTextColor(0xFFFF5722);
        tipText.setTextSize(12);
        tipText.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 15));

        // 标题行：左侧提示 + 右侧「选择文件」按钮调起系统文件选择器
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 8));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleRow.addView(tipText, titleLp);

        Button safPickBtn = new Button(activity);
        safPickBtn.setText("导入新文件");
        safPickBtn.setTextSize(12);
        safPickBtn.setTextColor(0xFFFFFFFF);
        safPickBtn.setBackground(DialogUi.getRoundButtonDrawable(0xAA2196F3));
        safPickBtn.setPadding(More.spx(activity, 20), More.spx(activity, 8), More.spx(activity, 20), More.spx(activity, 8));
        safPickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ReaLog.log("config", "用户选择从文件选择器导入");
                startSafImport(activity, targetApp, floatingView, hookInit);
            }
        });
        titleRow.addView(safPickBtn);
        mainLayout.addView(titleRow);
        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(listContainer);
        mainLayout.addView(scrollView);
        final int[] selectedIndex = new int[]{0};
        for (int i = 0; i < fileList.size(); i++) {
            final int index = i;
            File f = fileList.get(i);
            String fileName = f.getName();
            boolean isOldFormat = "install_fake_config.json".equals(fileName);
            String displayName = fileName + (isOldFormat ? " [旧格式]" : "");
            String timeStr = "";
            if (!isOldFormat) {
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{8}_\\d{6})").matcher(fileName);
                    if (m.find()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                        Date date = sdf.parse(m.group(1));
                        timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
                    }
                } catch (Exception e) {
                    timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(f.lastModified());
                }
            } else {
                timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(f.lastModified());
            }
            final LinearLayout itemLayout = new LinearLayout(activity);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(More.spx(activity, 30), More.spx(activity, 10), More.spx(activity, 30), More.spx(activity, 10));
            itemLayout.setBackgroundColor(0x00000000);
            final TextView nameView = new TextView(activity);
            nameView.setText(displayName);
            nameView.setTextColor(0xFF333333);
            nameView.setTextSize(14);
            itemLayout.addView(nameView);
            final TextView timeView = new TextView(activity);
            timeView.setText(timeStr);
            timeView.setTextColor(0xFF888888);
            timeView.setTextSize(11);
            timeView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            itemLayout.addView(timeView);
            itemLayout.setClickable(true);
            itemLayout.setFocusable(true);
            itemLayout.setTag(i);
            if (i == 0) {
                itemLayout.setBackgroundColor(0xAA2196F3);
                nameView.setTextColor(0xFFFFFFFF);
                timeView.setTextColor(0xFFDDDDDD);
            }
            itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < listContainer.getChildCount(); j++) {
                        View child = listContainer.getChildAt(j);
                        if (child instanceof LinearLayout) {
                            LinearLayout layout = (LinearLayout) child;
                            layout.setBackgroundColor(0x00000000);
                            TextView nv = (TextView) layout.getChildAt(0);
                            TextView tv = (TextView) layout.getChildAt(1);
                            nv.setTextColor(0xFF333333);
                            tv.setTextColor(0xFF888888);
                        }
                    }
                    itemLayout.setBackgroundColor(0xAA2196F3);
                    nameView.setTextColor(0xFFFFFFFF);
                    timeView.setTextColor(0xFFDDDDDD);
                    selectedIndex[0] = index;
                }
            });
            listContainer.addView(itemLayout);
            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            listContainer.addView(divider);
        }

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "选择配置文件", null,
                new String[]{"确认选择", "取消导入", "清理配置"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                File selectedFile = fileList.get(selectedIndex[0]);
                                ReaLog.log("config", "用户选择导入文件: " + selectedFile.getName());
                                d.dismiss();
                                doImportFile(activity, targetApp, selectedFile, floatingView, hookInit);
                            }
                        },
                        emptyListener(),
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                showDeleteConfigDialog(activity, targetApp, hookInit);
                            }
                        }
                },
                mainLayout
        );
        dialog.show();
    }

    // 从系统文件选择器（SAF）导入：读出所选 Uri 内容，落到应用缓存目录
    private static void startSafImport(final Activity activity, final String targetApp,
            final TextView floatingView, final HookInit hookInit) {
        SafPicker.pickImportFile(activity, new SafPicker.UriCallback() {
            @Override
            public void onResult(Uri uri) {
                if (uri == null) {
                    ReaLog.log("config", "SAF 导入已取消");
                    return; // 用户取消/未选择：静默返回
                }
                InputStream is = null;
                OutputStream os = null;
                File tmp = null;
                try {
                    is = activity.getContentResolver().openInputStream(uri);
                    if (is == null) throw new IOException("openInputStream 返回 null");
                    tmp = new File(activity.getCacheDir(), "saf_import_" + System.currentTimeMillis() + ".json");
                    os = new FileOutputStream(tmp);
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
                    os.flush();
                    String displayName = uri.getLastPathSegment();
                    ReaLog.log("config", "SAF 选择文件: " + displayName + "，写入缓存后走导入流程");
                    importPickedFile(activity, targetApp, tmp, floatingView, hookInit);
                } catch (Throwable t) {
                    ReaLog.log("config", "SAF 读取文件失败: " + t.getMessage());
                    ToastUtil.show(activity, "读取所选文件失败\n" + t.getMessage());
                    if (tmp != null) {
                        try {
                            tmp.delete();
                        } catch (Throwable ignored) {
                        }
                    }
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (Throwable ignored) {
                        }
                    }
                    if (os != null) {
                        try {
                            os.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        });
    }

    private static void doImportFile(final Activity activity, final String targetApp, final File file,
            final TextView floatingView, final HookInit hookInit) {
        ReaLog.log("config", "开始导入文件: " + file.getName());
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);

            final JSONObject root = new JSONObject(sb.toString());
            final List<String> packages = getTopLevelPackages(root);
            if (packages.isEmpty()) {
                ToastUtil.show(activity, "配置文件中没有可用包名");
                ReaLog.log("config", "文件中无顶层包名");
                return;
            }

            final String sourcePackage;
            if (packages.size() == 1) {
                sourcePackage = packages.get(0);
                ReaLog.log("config", "自动选择唯一包名: " + sourcePackage);
            } else {
                LinearLayout pkgLayout = new LinearLayout(activity);
                pkgLayout.setOrientation(LinearLayout.VERTICAL);
                pkgLayout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));
                TextView pkgMsg = new TextView(activity);
                pkgMsg.setText("请选择要导入的来源包名：");
                pkgMsg.setTextSize(14);
                pkgMsg.setTextColor(0xFF333333);
                pkgLayout.addView(pkgMsg);
                final String[] pkgArray = packages.toArray(new String[0]);
                final LinearLayout pkgListLayout = new LinearLayout(activity);
                pkgListLayout.setOrientation(LinearLayout.VERTICAL);
                pkgListLayout.setPadding(More.spx(activity, 0), More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 10));
                final int[] selectedPkgIndex = new int[]{0};
                for (int i = 0; i < pkgArray.length; i++) {
                    final int idx = i;
                    final String pkg = pkgArray[i];
                    final LinearLayout item = new LinearLayout(activity);
                    item.setOrientation(LinearLayout.HORIZONTAL);
                    item.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
                    final TextView pkgTv = new TextView(activity);
                    pkgTv.setText(pkg);
                    pkgTv.setTextSize(14);
                    pkgTv.setTextColor(0xFF333333);
                    item.addView(pkgTv);
                    if (i == 0) item.setBackgroundColor(0xAA2196F3);
                    item.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            for (int j = 0; j < pkgListLayout.getChildCount(); j++) {
                                View child = pkgListLayout.getChildAt(j);
                                child.setBackgroundColor(0x00000000);
                                TextView tv = (TextView) ((LinearLayout) child).getChildAt(0);
                                tv.setTextColor(0xFF333333);
                            }
                            item.setBackgroundColor(0xAA2196F3);
                            pkgTv.setTextColor(0xFFFFFFFF);
                            selectedPkgIndex[0] = idx;
                        }
                    });
                    pkgListLayout.addView(item);
                    View divider = new View(activity);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0xFFE0E0E0);
                    pkgListLayout.addView(divider);
                }
                pkgLayout.addView(pkgListLayout);
                AlertDialog pkgDialog = DialogUi.createBoundedDialog(activity, "选择来源包名", null,
                        new String[]{"确认", "取消"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        String srcPkg = pkgArray[selectedPkgIndex[0]];
                                        ReaLog.log("config", "用户选择来源包名: " + srcPkg);
                                        try {
                                            JSONObject appConfig = root.getJSONObject(srcPkg);
                                            showImportItemsDialog(activity, targetApp, srcPkg, appConfig, floatingView, hookInit, file);
                                        } catch (Throwable t) {
                                            ToastUtil.show(activity, "读取包配置失败");
                                            ReaLog.log("config", "读取包配置异常: " + t.getMessage());
                                        }
                                        d.dismiss();
                                    }
                                },
                                emptyListener()
                        },
                        pkgLayout
                );
                pkgDialog.show();
                return;
            }
            JSONObject appConfig = root.getJSONObject(sourcePackage);
            showImportItemsDialog(activity, targetApp, sourcePackage, appConfig, floatingView, hookInit, file);
        } catch (Throwable t) {
            ReaLog.log("config", "读取文件异常: " + t.getMessage());
            ToastUtil.show(activity, "读取文件失败：" + t.getMessage());
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void showImportItemsDialog(final Activity activity, final String targetApp,
            final String sourcePackage, final JSONObject appConfig,
            final TextView floatingView, final HookInit hookInit,
            final File file) {
        ReaLog.log("config", "显示导入项选择对话框，来源包: " + sourcePackage);
        try {
            final List<String> availableKeys = new ArrayList<String>();
            for (String key : CONFIG_ITEMS.keySet()) {
                if (appConfig.has(key)) availableKeys.add(key);
            }
            if (availableKeys.isEmpty()) {
                ToastUtil.show(activity, "该包名下没有可导入的配置项");
                ReaLog.log("config", "来源包无可用配置项");
                return;
            }
            // ===== 过滤掉 permanent_hidden =====
            final Map<String, Boolean> selectedMap = new LinkedHashMap<String, Boolean>();
            LinkedHashMap<String, String> displayMap = new LinkedHashMap<String, String>();
            for (String key : availableKeys) {
                if (!"permanent_hidden".equals(key)) {
                    selectedMap.put(key, true);
                    displayMap.put(key, CONFIG_ITEMS.get(key));
                }
            }
            LinearLayout selectionLayout = createSelectionLayout(activity, selectedMap, displayMap,
            null, true, null, appConfig);
            LinearLayout titleLayout = createTitleWithTip(activity, " 选择要导入的配置项",
            "<b>来源: " + sourcePackage + " | 仅导入存在且已勾选的配置！</b>");
            AlertDialog dialog = DialogUi.createBoundedDialog(activity, null, null,
                    new String[]{"下一步", "返回上一步"},
                    new DialogInterface.OnClickListener[]{
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    List<String> selectedKeys = getSelectedKeys(selectedMap);
                                    if (selectedKeys.isEmpty()) {
                                        ToastUtil.show(activity, "请至少选择一项");
                                        ReaLog.log("config", "导入取消: 未选择任何项");
                                        d.dismiss();
                                        showImportItemsDialog(activity, targetApp, sourcePackage, appConfig, floatingView, hookInit, file);
                                        return;
                                    }
                                    ReaLog.log("config", "用户选择导入 " + selectedKeys.size() + " 项");
                                    showImportConfirmDialog(activity, targetApp, selectedKeys, sourcePackage, floatingView, hookInit, file, appConfig);
                                    d.dismiss();
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    ReaLog.log("config", "用户取消导入选择");
                                    d.dismiss();
                                    showImportSelectionDialog(activity, targetApp, floatingView, hookInit);
                                }
                            }
                    },
                    selectionLayout
            );
            dialog.setCustomTitle(titleLayout);
            dialog.show();
        } catch (Throwable t) {
            ReaLog.log("config", "配置导入异常: " + t.getMessage());
            ToastUtil.show(activity, "配置导入失败");
        }
    }

    private static void showImportConfirmDialog(final Activity activity, final String targetApp,
            final List<String> selectedKeys, final String sourcePackage,
            final TextView floatingView, final HookInit hookInit,
            final File file, final JSONObject fileAppConfig) {
        String filePath = file.getAbsolutePath();
        ReaLog.log("config", "显示导入确认对话框，来源: " + sourcePackage + ", 导入项: " + selectedKeys.size());
        String configList = buildConfirmMessage(selectedKeys, targetApp, fileAppConfig);
        StringBuilder msg = new StringBuilder();
        msg.append("即将从 <font color='#FF5722'><b>").append(sourcePackage).append("</b></font> 导入以下配置项：<br><br>");
        msg.append(configList);
        int existsCount = 0;
        for (String key : selectedKeys) {
            if (fileAppConfig.has(key)) existsCount++;
        }
        int totalAll = CONFIG_ITEMS.size();
        int notSelected = totalAll - selectedKeys.size();
        msg.append("<br><b>导入路径：</b><font color='#2196F3'>").append(filePath).append("</font>");
        msg.append("<br><br>即将导入 <b><font color='#FF5722'>").append(existsCount).append("</b></font> 项");
        msg.append("，未勾选/不存在 <b><font color='#FF5722'>").append(notSelected).append("</b></font> 项");
        msg.append("<br>确定要 <b><font color='#FF5722'>继续并执行</font></b> 吗？");
        showConfirmDialog(activity, "确认导入", msg.toString(),
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                ReaLog.log("config", "用户确认导入");
                String result = importConfig(activity, targetApp, selectedKeys, floatingView, hookInit, file);
                if (result != null && result.startsWith("success:")) {
                    String[] parts = result.split(":");
                    int total = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                    int success = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                    int skipped = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;
                    String toastMsg = "总选择配置 " + total + " 项\n已成功覆盖 " + success + " 项";
                    if (skipped > 0) toastMsg += "\n已跳过损坏 " + skipped + " 项";
                    else toastMsg += "\n全部导入成功！";
                    ToastUtil.show(activity, toastMsg);
                    ReaLog.log("config", "导入结果: 成功 " + success + ", 跳过 " + skipped);
                    if (ReaLog.sHookInit != null) {
                        // 提示重启
                        DialogUi.showRestartConfirmDialog(activity);
                    }

                    if (floatingView != null) {
                        String statusText = HookInit.installStatusMap.getOrDefault(targetApp, true) ? "已安装" : "未安装";
                        String blockText = (HookInit.blockExitMap.getOrDefault(targetApp, false) ||
                                        HookInit.superBlockExitMap.getOrDefault(targetApp, false)) ? "[拦截]" : "";
                        floatingView.setText("安装防护(" + statusText + ")" + blockText);
                        floatingView.invalidate();
                        floatingView.requestLayout();
                    }
                    hookInit.updateFloatingView(activity);

                    if (result.startsWith("success:") && file.getName().equals("install_fake_config.json")) {
                        More.postOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                if (file.exists() && file.delete()) {
                                    ReaLog.log("config", "已删除旧格式配置文件: " + file.getAbsolutePath());
                                } else {
                                    ReaLog.log("config", "旧格式配置文件删除失败或已不存在: " + file.getAbsolutePath());
                                }
                            }
                        }, 3000);
                    }
                } else {
                    ToastUtil.show(activity, "导入失败：" + result);
                    ReaLog.log("config", "导入失败: " + result);
                }
                d.dismiss();
            }
        },
        new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int which) {
                ReaLog.log("config", "用户取消导入");
                d.dismiss();
                showImportSelectionDialog(activity, targetApp, floatingView, hookInit);
            }
        },
        hookInit
        );
    }

// 构建确认消息（从内存读取）
    private static String buildConfirmMessage(List<String> keys, String targetApp) {
        return buildConfirmMessage(keys, targetApp, null);
    }

// 重载：从 JSONObject 读取（用于导入预览）
    private static String buildConfirmMessage(List<
                    String> keys, String targetApp, JSONObject fileAppConfig) {
        StringBuilder msg = new StringBuilder();
        for (String key : keys) {
            String display = CONFIG_ITEMS.get(key);
            if (display == null) display = key;
            // 根据是否有 fileAppConfig 决定数据来源
            String value = (fileAppConfig != null)
                    ? getConfigValueDisplay(targetApp, key, fileAppConfig)
                    : getConfigValueDisplay(targetApp, key);
            if (value == null) value = "未知";
            String color = value.contains("关闭") || value.contains("未安装") || value.contains("禁用") ? "#F44336" : "#4CAF50";
            String coloredValue = colorizeNumericValues(value);
            msg.append("• ").append(display).append("：<b><font color='").append(color).append("'>").append(coloredValue).append("</font></b><br>");
        }
        return msg.toString();
    }

    private static String colorizeNumericValues(String value) {
        if (TextUtils.isEmpty(value)) return value;
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?(?:/\\d+(?:\\.\\d+)?)?)");
        java.util.regex.Matcher matcher = pattern.matcher(value);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<font color='#FF5722'><b>" + matcher.group(1) + "</b></font>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static LinearLayout createTitleWithTip(Activity activity, String title, String tip) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 10));
        TextView titleView = new TextView(activity);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTextColor(0xFF333333);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        layout.addView(titleView);
        if (tip != null && !tip.isEmpty()) {
            TextView tipView = new TextView(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                tipView.setText(Html.fromHtml(tip, Html.FROM_HTML_MODE_LEGACY));
            } else {
                tipView.setText(Html.fromHtml(tip));
            }
            tipView.setTextColor(0xFFFF5722);
            tipView.setTextSize(12);
            tipView.setPadding(More.spx(activity, 0), More.spx(activity, 5), More.spx(activity, 0), More.spx(activity, 0));
            layout.addView(tipView);
        }
        return layout;
    }

    public static void showDeleteConfigDialog(final Activity activity, final String targetApp, final HookInit hookInit) {
        ReaLog.log("config", "显示删除配置对话框");
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) {
            ToastUtil.show(activity, "无法访问存储");
            return;
        }
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File d, String name) {
                return (name.startsWith("installcf_") && name.endsWith(".json")) ||
                        "install_fake_config.json".equals(name);
            }
        });
        if (files == null || files.length == 0) {
            ToastUtil.show(activity, "没有可删除的配置文件");
            ReaLog.log("config", "没有可删除的配置文件");
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                return Long.compare(f2.lastModified(), f1.lastModified());
            }
        });

        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));
        TextView tip = new TextView(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tip.setText(Html.fromHtml("<b>请选择要<font color='#FF5722'><b>删除</b></font>的配置文件：</b>", Html.FROM_HTML_MODE_LEGACY));
        } else {
            tip.setText(Html.fromHtml("<b>请选择要<font color='#FF5722'><b>删除</b></font>的配置文件：</b>"));
        }
        tip.setTextColor(0xFF333333);
        tip.setTextSize(14);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 15));
        mainLayout.addView(tip);
        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(listContainer);
        mainLayout.addView(scrollView);
        final List<File> fileList = Arrays.asList(files);
        final Map<Integer, Boolean> selectedMap = new HashMap<Integer, Boolean>();
        for (int i = 0; i < fileList.size(); i++) selectedMap.put(i, false);
        for (int i = 0; i < fileList.size(); i++) {
            final int index = i;
            File f = fileList.get(i);
            String fileName = f.getName();
            boolean isOldFormat = "install_fake_config.json".equals(fileName);
            String displayName = fileName + (isOldFormat ? " [旧格式]" : "");
            String timeStr = "";
            if (!isOldFormat) {
                try {
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{8}_\\d{6})").matcher(fileName);
                    if (m.find()) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
                        Date date = sdf.parse(m.group(1));
                        timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date);
                    }
                } catch (Exception e) {
                    timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(f.lastModified());
                }
            } else {
                timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(f.lastModified());
            }

            LinearLayout itemLayout = new LinearLayout(activity);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setPadding(More.spx(activity, 10), More.spx(activity, 5), More.spx(activity, 10), More.spx(activity, 5));
            itemLayout.setBackgroundColor(0x00000000);
            final CheckBox cb = new CheckBox(activity);
            cb.setText(displayName);
            cb.setTextSize(14);
            cb.setTextColor(0xFF333333);
            itemLayout.addView(cb);
            TextView timeView = new TextView(activity);
            timeView.setText(timeStr);
            timeView.setTextColor(0xFF888888);
            timeView.setTextSize(11);
            timeView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            timeView.setPadding(More.spx(activity, 40), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
            itemLayout.addView(timeView);
            cb.setTag(index);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    selectedMap.put(index, isChecked);
                }
            });
            itemLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    cb.setChecked(!cb.isChecked());
                }
            });
            listContainer.addView(itemLayout);
            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            listContainer.addView(divider);
        }

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "删除已导出配置", null,
                new String[]{"确认删除", "取消删除"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                List<File> toDelete = new ArrayList<File>();
                                for (Map.Entry<Integer, Boolean> entry : selectedMap.entrySet()) {
                                    if (entry.getValue())
                                        toDelete.add(fileList.get(entry.getKey()));
                                }
                                if (toDelete.isEmpty()) {
                                    ToastUtil.show(activity, "请至少选择一个文件");
                                    d.dismiss();
                                    showDeleteConfigDialog(activity, targetApp, hookInit);
                                    return;
                                }
                                int success = 0;
                                for (File f : toDelete) {
                                    if (f.delete()) success++;
                                    ReaLog.log("config", "删除文件: " + f.getName());
                                }
                                ToastUtil.show(activity, "成功删除 " + success + "/" + toDelete.size() + " 个配置文件");
                                ReaLog.log("config", "删除完成，成功 " + success + " 个");
                                d.dismiss();
                            }
                        },
                        emptyListener()
                },
                mainLayout
        );
        dialog.show();
    }

    private static String buildExportFilePath(Activity activity, String targetApp) {
        File dir = activity.getExternalFilesDir(null);
        if (dir == null) return null;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return new File(dir, "installcf_" + targetApp + "_" + timeStamp + ".json").getAbsolutePath();
    }

    private static List<String> getTopLevelPackages(JSONObject root) throws Exception {
        List<String> packages = new ArrayList<String>();
        Iterator<String> keys = root.keys();
        while (keys.hasNext()) packages.add(keys.next());
        return packages;
    }

    private static List<String> jsonArrayToStringList(JSONArray array) throws Exception {
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < array.length(); i++) {
            String item = array.getString(i);
            if (item != null && !item.isEmpty()) list.add(item);
        }
        return list;
    }

    private static JSONArray stringListToJsonArray(List<String> list) {
        JSONArray array = new JSONArray();
        for (String item : list) {
            if (item != null && !item.isEmpty()) array.put(item);
        }
        return array;
    }

    private static LinearLayout createSelectionLayout(Activity activity, final Map<
                    String, Boolean> selectedMap,
            LinkedHashMap<String, String> displayMap,
            final String targetApp, final boolean showStatus, View extraButton,
            final JSONObject fileAppConfig) {
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        final Button toggleAllBtn = new Button(activity);
        toggleAllBtn.setText("全选");
        toggleAllBtn.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        toggleAllBtn.setTextColor(0xFFFFFFFF);
        toggleAllBtn.setBackground(DialogUi.getRoundButtonDrawable(0xFF2196F3));
        LinearLayout.LayoutParams toggleParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        toggleParams.rightMargin = 20;
        toggleAllBtn.setLayoutParams(toggleParams);
        topBar.addView(toggleAllBtn);
        if (extraButton != null) topBar.addView(extraButton);
        mainLayout.addView(topBar);
        final ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        final List<CheckBox> checkBoxList = new ArrayList<CheckBox>();
        final Map<CheckBox, String> baseTextMap = new HashMap<CheckBox, String>();
        final Runnable updateSelectAllButtonState = new Runnable() {
            @Override
            public void run() {
                boolean allChecked = true;
                for (CheckBox cb : checkBoxList) {
                    if (!cb.isChecked()) {
                        allChecked = false;
                        break;
                    }
                }
                if (allChecked) {
                    toggleAllBtn.setText("取消全选");
                    toggleAllBtn.setBackground(DialogUi.getRoundButtonDrawable(0xAAFF6347));
                } else {
                    toggleAllBtn.setText("全选");
                    toggleAllBtn.setBackground(DialogUi.getRoundButtonDrawable(0xFF2196F3));
                }
            }
        };

        for (final Map.Entry<String, String> entry : displayMap.entrySet()) {
            final String key = entry.getKey();
            final String displayName = entry.getValue();
            String baseText = displayName;
            String state = "";
            if (showStatus) {
                state = getConfigValueDisplay(targetApp, key, fileAppConfig);
                if (state != null && !state.isEmpty()) baseText = displayName + "：";
            }
            final String finalBaseText = baseText;
            final String finalState = state;
            final CheckBox cb = new CheckBox(activity);
            boolean checked = selectedMap.getOrDefault(key, true);
            updateCheckBoxText(cb, finalBaseText, finalState, checked, showStatus);
            cb.setChecked(checked);
            cb.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
            cb.setTextSize(14);
            cb.setTag(key);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    selectedMap.put(key, isChecked);
                    String newState = getConfigValueDisplay(targetApp, key, fileAppConfig);
                    updateCheckBoxText(cb, finalBaseText, newState, isChecked, showStatus);
                    updateSelectAllButtonState.run();
                }
            });
            listContainer.addView(cb);
            checkBoxList.add(cb);
            baseTextMap.put(cb, finalBaseText);
            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            listContainer.addView(divider);
        }

        toggleAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean allChecked = true;
                for (CheckBox cb : checkBoxList) {
                    if (!cb.isChecked()) {
                        allChecked = false;
                        break;
                    }
                }
                boolean target = !allChecked;
                for (CheckBox cb : checkBoxList) {
                    cb.setChecked(target);
                    String key = (String) cb.getTag();
                    selectedMap.put(key, target);
                    String state = getConfigValueDisplay(targetApp, key, fileAppConfig);
                    updateCheckBoxText(cb, baseTextMap.get(cb), state, target, showStatus);
                }
                updateSelectAllButtonState.run();
            }
        });
        updateSelectAllButtonState.run();
        return mainLayout;
    }

    private static void updateCheckBoxText(CheckBox cb, String baseText, String state, boolean checked, boolean showStatus) {
        String text;
        if (checked) {
            // 选中状态：baseText 强制黑色，state 保持原有颜色（绿/红）
            if (showStatus && state != null && !state.isEmpty()) {
                String color = state.contains("关闭") || state.contains("未安装") || state.contains("禁用") ? "#F44336" : "#4CAF50";
                text = "<font color='#000000'>" + baseText + "</font><b><font color='" + color + "'>" + state + "</font></b>";
            } else {
                text = "<font color='#000000'>" + baseText + "</font>";
            }
        } else {
            // 未选中状态：整体红色加粗 + (已禁用) 小字
            String content = baseText + (showStatus && state != null ? state : "");
            text = "<b><font color='#F44336'>" + content + " </font><font color='#FF5722'><small>(已禁用)</small></font></b>";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cb.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY));
        } else {
            cb.setText(Html.fromHtml(text));
        }
    }

    private static List<String> getSelectedKeys(Map<String, Boolean> selectedMap) {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String, Boolean> entry : selectedMap.entrySet()) {
            if (entry.getValue()) list.add(entry.getKey());
        }
        return list;
    }

    private static String getConfigValueDisplay(String targetApp, String key, JSONObject fileAppConfig) {
        if (fileAppConfig != null) {
            try {
                switch (key) {
                    case "install_status":
                        return fileAppConfig.optBoolean("install_status", true) ? "已安装" : "未安装";
                    case "floating_shown":
                        return fileAppConfig.optBoolean("floating_shown", true) ? "显示" : "隐藏";
                    case "permanent_hidden":
                        // 导出时无论是否勾选都会带上它，所以文件里基本总有这一项：永久隐藏
                        return fileAppConfig.optBoolean("permanent_hidden", false)
                                ? "已隐藏" : "未隐藏";
                    case "floating_x":
                        {
                            String val = fileAppConfig.optString("floating_x", "null");
                            return "null".equals(val) ? "未设置" : val;
                        }
                    case "floating_y":
                        {
                            String val = fileAppConfig.optString("floating_y", "null");
                            return "null".equals(val) ? "未设置" : val;
                        }
                    case "user_disabled_auto_block":
                        return fileAppConfig.optBoolean("user_disabled_auto_block", false) ? "已禁用" : "启用";
                    case "block_exit":
                        return fileAppConfig.optBoolean("block_exit", false) ? "开启" : "关闭";
                    case "super_block_exit":
                        return fileAppConfig.optBoolean("super_block_exit", false) ? "开启" : "关闭";
                    case "permission_fake":
                        return fileAppConfig.optBoolean("permission_fake", true) ? "开启" : "关闭";
                    case "launch_intercept":
                        return fileAppConfig.optBoolean("launch_intercept", true) ? "开启" : "关闭";
                    case "selinux_fake":
                        return fileAppConfig.optBoolean("selinux_fake", true) ? "开启" : "关闭";
                    case "vendor_enabled":
                        return fileAppConfig.optBoolean("vendor_enabled", false) ? "开启" : "关闭";
                    case "force_default_back":
                        return fileAppConfig.optBoolean("force_default_back", false) ? "模块级" : "自定义";
                    case "hook_dialog_cancelable":
                        return fileAppConfig.optBoolean("hook_dialog_cancelable", true) ? "开启" : "关闭";
                    case "hook_window_flags":
                        return fileAppConfig.optBoolean("hook_window_flags", true) ? "开启" : "关闭";
                    case "hook_anti_cancel_finish":
                        return fileAppConfig.optBoolean("hook_anti_cancel_finish", true) ? "开启" : "关闭";
                    case "hook_vibrator":
                        return fileAppConfig.optBoolean("hook_vibrator", true) ? "开启" : "关闭";
                    case "crash_protect_enabled":
                        return fileAppConfig.optBoolean("crash_protect_enabled", true) ? "开启" : "关闭";
                    case "anti_detection_detail":
                        {
                            JSONObject detail = fileAppConfig.optJSONObject("anti_detection_detail");
                            int total = detail != null ? detail.length() : 0;
                            int count = 0;
                            if (detail != null) {
                                Iterator<String> it = detail.keys();
                                while (it.hasNext()) {
                                    if (detail.optBoolean(it.next(), false)) count++;
                                }
                            }
                            return count + "/" + total + " 项";
                        }
                    case "vpn_fake_detail":
                        {
                            JSONObject detail = fileAppConfig.optJSONObject("vpn_fake_detail");
                            int total = detail != null ? detail.length() : 0;
                            int count = 0;
                            if (detail != null) {
                                Iterator<String> it = detail.keys();
                                while (it.hasNext()) {
                                    if (detail.optBoolean(it.next(), false)) count++;
                                }
                            }
                            return count + "/" + total + " 项";
                        }
                    case "permission_fake_detail":
                        {
                            JSONObject detail = fileAppConfig.optJSONObject("permission_fake_detail");
                            int pTotal = detail != null ? detail.length() : 0;
                            int pOn = 0;
                            if (detail != null) {
                                Iterator<String> pit = detail.keys();
                                while (pit.hasNext()) {
                                    if (detail.optBoolean(pit.next(), false)) pOn++;
                                }
                            }
                            return pOn + "/" + pTotal + " 项";
                        }
                    case "share_fake_detail":
                        {
                            JSONObject detail = fileAppConfig.optJSONObject("share_fake_detail");
                            int total = detail != null ? detail.length() : 4;
                            int count = 0;
                            if (detail != null) {
                                Iterator<String> it = detail.keys();
                                while (it.hasNext()) {
                                    if (detail.optBoolean(it.next(), false)) count++;
                                }
                            }
                            return count + "/" + total + " 项";
                        }
                    case "user_defined_packages":
                        {
                            JSONArray arr = fileAppConfig.optJSONArray("user_defined_packages");
                            return (arr != null ? arr.length() : 0) + " 个包";
                        }
                    case "excluded_packages":
                        {
                            JSONArray arr = fileAppConfig.optJSONArray("excluded_packages");
                            return (arr != null ? arr.length() : 0) + " 个包";
                        }
                    case "package_configs":
                        {
                            JSONArray arr = fileAppConfig.optJSONArray("package_configs");
                            return (arr != null ? arr.length() : 0) + " 个配置";
                        }
                    case "auto_actions":
                        {
                            JSONObject obj = fileAppConfig.optJSONObject("auto_actions");
                            return (obj != null ? obj.length() : 0) + " 条记录";
                        }
                    case "auto_records":
                        {
                            JSONObject obj = fileAppConfig.optJSONObject("auto_records");
                            return (obj != null ? obj.length() : 0) + " 条记录";
                        }
                    case "intercept_patterns":
                        {
                            JSONArray arr = fileAppConfig.optJSONArray("intercept_patterns");
                            return (arr != null ? arr.length() : 0) + " 条记录";
                        }
                    case "rea_log_pause":
                        {
                            JSONObject pauseObj = fileAppConfig.optJSONObject("rea_log_pause");
                            if (pauseObj != null) {
                                int total = pauseObj.length();
                                int paused = 0;
                                Iterator<String> keys = pauseObj.keys();
                                while (keys.hasNext()) {
                                    String k = keys.next();
                                    if (pauseObj.optBoolean(k, false)) paused++;
                                }
                                int recording = total - paused;
                                return "记录中:" + recording + "项, 已暂停:" + paused + "项";
                            } else {
                                return "未设置";
                            }
                        }
                    default:
                        return "未知";
                }
            } catch (Throwable t) {
                return "读取失败";
            }
        } else {
            try {
                switch (key) {
                    case "install_status":
                        return HookInit.installStatusMap.getOrDefault(targetApp, true) ? "已安装" : "未安装";
                    case "floating_shown":
                        return HookInit.floatingShownMap.getOrDefault(targetApp, true) ? "显示" : "隐藏";
                    case "permanent_hidden":
                        return HookInit.permanentHiddenMap.getOrDefault(targetApp, false)
                                ? "已隐藏" : "未隐藏";
                    case "floating_x":
                        {
                            Float f = HookInit.floatingXMap.get(targetApp);
                            return f != null ? String.valueOf(f) : "未设置";
                        }
                    case "floating_y":
                        {
                            Float f = HookInit.floatingYMap.get(targetApp);
                            return f != null ? String.valueOf(f) : "未设置";
                        }
                    case "user_disabled_auto_block":
                        return HookInit.userDisabledAutoBlockMap.getOrDefault(targetApp, false) ? "已禁用" : "启用";
                    case "block_exit":
                        return HookInit.blockExitMap.getOrDefault(targetApp, false) ? "开启" : "关闭";
                    case "super_block_exit":
                        return HookInit.superBlockExitMap.getOrDefault(targetApp, false) ? "开启" : "关闭";
                    case "permission_fake":
                        return HookInit.permissionFakeMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "launch_intercept":
                        return HookInit.launchInterceptMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "selinux_fake":
                        return HookInit.selinuxFakeMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "vendor_enabled":
                        {
                            Boolean v = HookInit.vendorChoiceMap.get(targetApp);
                            return v != null && v ? "开启" : "关闭";
                        }
                    case "force_default_back":
                        return HookInit.forceDefaultBackMap.getOrDefault(targetApp, false) ? "模块级" : "自定义";
                    case "hook_dialog_cancelable":
                        return HookInit.hookDialogCancelableMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "hook_window_flags":
                        return HookInit.hookWindowFlagsMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "hook_anti_cancel_finish":
                        return HookInit.hookAntiCancelFinishMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "hook_vibrator":
                        return HookInit.hookVibratorMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "crash_protect_enabled":
                        return HookInit.crashProtectEnabledMap.getOrDefault(targetApp, true) ? "开启" : "关闭";
                    case "anti_detection_detail":
                        {
                        Map<String,Boolean> map = HookInit.antiDetectionDetailMap.getOrDefault(targetApp, new HashMap<String, Boolean>());
                            int count = 0;
                            for (Boolean b : map.values()) if (b) count++;
                            return count + "/" + map.size() + " 项";
                        }
                    case "vpn_fake_detail":
                        {
                        Map<String,Boolean> map = HookInit.vpnFakeDetailMap.getOrDefault(targetApp, new HashMap<String, Boolean>());
                            int count = 0;
                            for (Boolean b : map.values()) if (b) count++;
                            return count + "/" + map.size() + " 项";
                        }
                    case "permission_fake_detail":
                        {
                        Map<String,Boolean> map = HookInit.permissionFakeDetailMap.getOrDefault(targetApp, new HashMap<String, Boolean>());
                            int count = 0;
                            for (Boolean b : map.values()) if (b) count++;
                            return count + "/" + map.size() + " 项";
                        }
                    case "share_fake_detail":
                        {
                        Map<String,Boolean> map = HookInit.shareFakeDetailMap.getOrDefault(targetApp, HookInit.getDefaultShareDetailMap());
                            int count = 0;
                            for (Boolean b : map.values()) if (b) count++;
                            return count + "/" + map.size() + " 项";
                        }
                    case "user_defined_packages":
                        {List<String> list = HookInit.userDefinedPackagesMap.getOrDefault(targetApp, new ArrayList<String>());
                            return list.size() + " 个包";
                        }
                    case "excluded_packages":
                        {
                        List<String> list = HookInit.excludedPackagesMap.getOrDefault(targetApp, new ArrayList<String>());
                            return list.size() + " 个包";
                        }
                    case "package_configs":
                        {
                        List<PackageConfig> list = HookInit.packageConfigMap.getOrDefault(targetApp, new ArrayList<PackageConfig>());
                            return list.size() + " 个配置";
                        }
                    case "auto_actions":
                        {
                        Map<String,String> map = HookInit.autoActionMap.getOrDefault(targetApp, new HashMap<String, String>());
                            return map.size() + " 条记录";
                        }
                    case "auto_records":
                        {
                        Map<String,List<String>> map = HookInit.autoChoiceRecordsMap.getOrDefault(targetApp, new HashMap<String, List<String>>());
                            return map.size() + " 条记录";
                        }
                    case "intercept_patterns":
                        {
                        List<InterceptPattern> list = HookInit.interceptPatternsMap.getOrDefault(targetApp, new ArrayList<InterceptPattern>());
                            return list.size() + " 条记录";
                        }
                    case "rea_log_pause":
                        {
                            if (targetApp == null) return "未设置";
                            Map<String, Boolean> map = HookInit.reaLogPauseMap.get(targetApp);
                            if (map == null || map.isEmpty()) {
                                return "未设置";
                            }
                            int total = map.size();
                            int paused = 0;
                            for (Boolean v : map.values()) if (v) paused++;
                            int recording = total - paused;
                            return "记录中:" + recording + "项, 已暂停:" + paused + "项";
                        }
                    default:
                        return "未知";
                }
            } catch (Throwable t) {
                return "读取失败";
            }
        }
    }

    // 显示配置值
    private static String getConfigValueDisplay(String targetApp, String key) {
        return getConfigValueDisplay(targetApp, key, null);
    }

    // ===== 通用 JSON 文件工具 =====
    public static JSONArray stringListToJsonArray(Collection<String> collection) {
        JSONArray array = new JSONArray();
        if (collection != null) {
            for (String item : collection) {
                if (item != null && !item.isEmpty()) {
                    array.put(item);
                }
            }
        }
        return array;
    }

    /** 配置文件解析缓存：所有调用方只读不改返回的 JSONObject */
    private static final java.util.Map<String, JSONObject> PARSE_CACHE =
            new java.util.HashMap<String, JSONObject>();
    private static final java.util.Map<String, Long> PARSE_CACHE_MTIME =
            new java.util.HashMap<String, Long>();

    public static JSONObject readJsonFromFile(File file) throws Exception {
        String path = file.getAbsolutePath();
        long mtime = file.lastModified();
        JSONObject cached = PARSE_CACHE.get(path);
        if (cached != null) {
            Long cachedMtime = PARSE_CACHE_MTIME.get(path);
            if (cachedMtime != null && cachedMtime.longValue() == mtime) return cached;
        }
        JSONObject root = readJsonFromDisk(file);
        PARSE_CACHE.put(path, root);
        PARSE_CACHE_MTIME.put(path, Long.valueOf(mtime));
        return root;
    }

    private static JSONObject readJsonFromDisk(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        fis.close();
        return new JSONObject(sb.toString());
    }

    public static void writeJsonToFile(File file, JSONObject root) throws Exception {
        OutputStream os = null;
        OutputStreamWriter writer = null;
        try {
            os = new FileOutputStream(file);
            writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            writer.write(root.toString(2));
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            } else if (os != null) {
                try {
                    os.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

}

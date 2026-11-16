package com.install.appinstall.xl;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import java.io.FileReader;
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
import android.content.ClipboardManager;
import java.util.concurrent.ConcurrentHashMap;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.Process;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import com.install.appinstall.xl.util.PermissionName;
import com.install.appinstall.xl.util.DialogUi;
import android.app.AppOpsManager;
import android.widget.Switch;
import java.util.LinkedHashMap;
import org.json.JSONException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.lang.reflect.Constructor;
import java.io.OutputStream;
import java.io.FilenameFilter;
import java.util.Comparator;
import java.util.Collection;
import java.io.IOException;
import android.view.ViewParent;
import android.content.ContentResolver;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.pm.ServiceInfo;
import android.telephony.TelephonyManager;

import com.install.appinstall.xl.util.Portcf;
import com.install.appinstall.xl.util.ToastUtil;
import com.install.appinstall.xl.util.instar;
import com.install.appinstall.xl.util.Update;
import com.install.appinstall.xl.util.Spkill;
import com.install.appinstall.xl.util.foat;
import com.install.appinstall.xl.util.Selinuxhook;
import com.install.appinstall.xl.util.ShareHook;
import com.install.appinstall.xl.util.AntiDetection;
import com.install.appinstall.xl.util.VpnStatusFaker;
import com.install.appinstall.xl.util.ReaLog;
import com.install.appinstall.xl.util.DebugModeManager;
import com.install.appinstall.xl.util.Prsprn;
import com.install.appinstall.xl.util.PkgMgr;
import com.install.appinstall.xl.util.BuHook;
import com.install.appinstall.xl.util.More;

public class HookInit implements IXposedHookLoadPackage {
    private foat foatInstance;
    private Prsprn mPrsprn;
    private PkgMgr mPkgMgr;
    public static final Map<String, Boolean> installStatusMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> floatingShownMap = new ConcurrentHashMap<>();
    public static final Map<String, Float> floatingXMap = new ConcurrentHashMap<>();
    public static final Map<String, Float> floatingYMap = new ConcurrentHashMap<>();
    public static Map<String, Boolean> launchInterceptMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> blockExitMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> permissionFakeMap = new ConcurrentHashMap<>();
    public static final Map<
            String, List<String>> userDefinedPackagesMap = new ConcurrentHashMap<>();
    public static final Map<String, List<String>> excludedPackagesMap = new ConcurrentHashMap<>();
    public static final Map<
            String, List<PackageConfig>> packageConfigMap = new ConcurrentHashMap<>();
    public static final Set<String> globalCapturedPackages = ConcurrentHashMap.newKeySet();
    public static final Map<String, Map<String, String>> autoActionMap = new ConcurrentHashMap<>();
    public static final Map<
            String, Map<String, List<String>>> autoChoiceRecordsMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> superBlockExitMap = new ConcurrentHashMap<>();
    public static final Map<
            String, List<InterceptPattern>> interceptPatternsMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> selinuxFakeMap = new ConcurrentHashMap<>();
    public static final Map<
            String, Map<String, Boolean>> shareFakeDetailMap = new ConcurrentHashMap<>();
    public static final Map<
            String, Map<String, Boolean>> antiDetectionDetailMap = new ConcurrentHashMap<>();
    public static final Map<
            String, Map<String, Boolean>> vpnFakeDetailMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> forceDefaultBackMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> hookDialogCancelableMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> hookWindowFlagsMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> hookAntiCancelFinishMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> hookVibratorMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> crashProtectEnabledMap = new ConcurrentHashMap<>();
    public static final Map<
            String, Map<String, Boolean>> permissionFakeDetailMap = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> userDisabledAutoBlockMap = new ConcurrentHashMap<>();
    private final Set<Activity> pendingFinishSet = Collections.newSetFromMap(new WeakHashMap<>());
    public final Map<String, String> pathPackageCache = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> permanentHiddenMap = new ConcurrentHashMap<>();
    public final Map<String, Integer> packageStatusCache = new ConcurrentHashMap<>();
    public static final Map<String, DetectedPackages> detectedCache = new ConcurrentHashMap<>();
    public static final Map<
            String, Map<String, Boolean>> reaLogPauseMap = new ConcurrentHashMap<>();
    private static final Set<String> sReflectSkipCache = ConcurrentHashMap.newKeySet();
    public final Map<String, Boolean> mSystemCoreCache = new ConcurrentHashMap<>();
    public final Map<String, Boolean> mRealSystemCache = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService sSaveExecutor = Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> sSaveFuture = null;
    private static final java.util.concurrent.atomic.AtomicLong sSaveSeq = new java.util.concurrent.atomic.AtomicLong(0);
    private static final int SAVE_DELAY_MS = 300;
    public static volatile List<String> sCachedAppDirNames = null;
    public static volatile File[] sCachedAppDirFiles = null;
    public static volatile int sCacheVersion = 0;
    public static final Object sCacheLock = new Object();
    public Boolean mIsSystemPackageCache = null;
    public static final Map<
            String, List<PackageInfo>> sInstalledPackagesCache = new ConcurrentHashMap<>();
    public static final Map<
            String, List<ApplicationInfo>> sInstalledApplicationsCache = new ConcurrentHashMap<>();
    private static final ActivityInfo[] EMPTY_ACTIVITY_INFO = new ActivityInfo[0];
    private static final ServiceInfo[] EMPTY_SERVICE_INFO = new ServiceInfo[0];
    private static final ActivityInfo[] EMPTY_RECEIVER_INFO = new ActivityInfo[0];
    private static final ProviderInfo[] EMPTY_PROVIDER_INFO = new ProviderInfo[0];
    private static final PermissionInfo[] EMPTY_PERMISSION_INFO = new PermissionInfo[0];
    private static final Signature[] FAKE_SIGNATURES = new Signature[]{new Signature("fake".getBytes())};
    private static final Pattern SPACE_PATTERN = Pattern.compile("\\s+");
    private boolean autoBlockTriggeredThisSession = false;
    private String currentTargetApp = "";
    public static final String MODULE_TAG = "InstallHook";
    private static final String MODULE_PACKAGE = "com.install.appinstall.xl";
    private final ArrayList<String> appCapturedPackages = new ArrayList<>();
    private Activity currentResumedActivity = null;
    private static boolean sConfigLoadedLogged = false;
    public static final Set<
            String> sLoggedPackageSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
    public static boolean sGlobalListLogged = false;
    private static String sCachedProcessName = null;
    private static final Map<String, Long> sLastLogTime = new ConcurrentHashMap<>();
    private static final long LOG_DEDUP_INTERVAL_MS = 5000;
    private static final int MAX_LOG_CACHE_SIZE = 1000;
    private static final Set<String> sDefaultConfigLogged = new HashSet<>();
    public boolean isExitPending = false;
    public String pendingExitMethod = "";
    public Object pendingExitParam = null;
    public int isCurrentlyBlocking;
    public String blockText;
    public static int sCrashRecoverCount = 0;
    public static final String SP_CRASH_NAME = "crash_protect_sp";
    public static final String SP_KEY_COUNT = "crash_count";
    public static final String SP_KEY_LAST_TIME = "crash_last_time";
    private static volatile boolean isHandlingCrash = false;
    private static volatile int sCrashCount = 0;
    private static volatile long sLastCrashTime = 0;
    private static volatile Context sCachedAppContext = null;
    private static volatile Intent sCachedLaunchIntent = null;
    public static final int DOUBLE_CLICK_THRESHOLD = 500;
    public long lastClickTime = 0;
    public AlertDialog statusSwitchDialog;
    private static final String FALLBACK_PACKAGE = "com.小淋.虚假APP";
    public static final String[] DETECTION_PERMISSIONS = {
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.GET_PACKAGE_SIZE",
            "com.android.permission.GET_INSTALLED_APPS",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.PACKAGE_VERIFICATION_AGENT",
            "android.permission.READ_DEFAULT_APPLICATIONS",
            "android.permission.SET_PREFERRED_APPLICATIONS",
    };
    private static final Set<
            String> DETECTION_PERMISSIONS_SET = new HashSet<>(Arrays.asList(DETECTION_PERMISSIONS));
    private static final Map<String, String> PERMISSION_INTENT_MAP = new HashMap<>();
    static {
        PERMISSION_INTENT_MAP.put("android.settings.MANAGE_OVERLAY_PERMISSION", "android.permission.SYSTEM_ALERT_WINDOW");
        PERMISSION_INTENT_MAP.put("android.settings.MANAGE_UNKNOWN_APP_SOURCES", "android.permission.REQUEST_INSTALL_PACKAGES");
        PERMISSION_INTENT_MAP.put("android.settings.NOTIFICATION_LISTENER_SETTINGS", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE");
        PERMISSION_INTENT_MAP.put("android.settings.ACCESSIBILITY_SETTINGS", "android.permission.BIND_ACCESSIBILITY_SERVICE");
        PERMISSION_INTENT_MAP.put("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS", "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        PERMISSION_INTENT_MAP.put("android.settings.USAGE_ACCESS_SETTINGS", "android.permission.PACKAGE_USAGE_STATS");
        PERMISSION_INTENT_MAP.put("android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION", "android.permission.MANAGE_EXTERNAL_STORAGE");
        PERMISSION_INTENT_MAP.put("android.settings.PICTURE_IN_PICTURE_SETTINGS", "android.permission.PICTURE_IN_PICTURE");
        PERMISSION_INTENT_MAP.put("android.settings.DEVICE_ADMIN_SETTINGS", "android.permission.BIND_DEVICE_ADMIN");
        PERMISSION_INTENT_MAP.put("android.settings.EXTERNAL_STORAGE_ACCESS_SETTINGS", "android.permission.ACCESS_EXTERNAL_STORAGE");
    }

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
    
    private static final String[] ALL_ANTI_KEYS = {
            "class", "file", "pm", "proc", "cmd", "stacktrace", "root", "adb", "usb", "dev"
    };

    private final Map<Activity, Long> mPauseTimeMap = new WeakHashMap<>();
    private static volatile long sTimeOffset = 0;

    private static final List<QueryPattern> queryPatterns = new ArrayList<>();
    private static final Random random = new Random();

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

    private static final int MAX_CACHE_SIZE = 4096;
    private static final Map<
            String, PackageInfo> sPackageInfoCache = Collections.synchronizedMap(new LinkedHashMap<
            String, PackageInfo>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PackageInfo> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    });
    private static final Map<
            String, ApplicationInfo> sAppInfoCache = Collections.synchronizedMap(new LinkedHashMap<
            String, ApplicationInfo>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ApplicationInfo> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    });
    private static final Map<String, String> versionCache = new HashMap<>();
    private static final Map<String, Integer> versionCodeCache = new HashMap<>();
    private static final Map<String, Long> installTimeCache = new HashMap<>();
    private static final Map<String, String> installerCache = new HashMap<>();
    private static final Map<String, String> appNameCache = new HashMap<>();

    public static class PackageConfig {
        public String packageName;
        public String statusMode;

        public PackageConfig(String packageName) {
            this.packageName = packageName;
            this.statusMode = "follow";
        }
    }

    public static class InterceptPattern {
        public String patternHash;
        public List<String> installedPackages;
        public List<String> notInstalledPackages;
        public String userChoice;
        public int choiceCount;
        public long lastDetectedTime;
        public boolean silentIntercept;
        public ArrayList<String> recentChoices;

        public InterceptPattern(String patternHash, List<String> installedPackages, List<
                        String> notInstalledPackages) {
            this.patternHash = patternHash;
            this.installedPackages = new ArrayList<>(installedPackages);
            this.notInstalledPackages = new ArrayList<>(notInstalledPackages);
            this.choiceCount = 0;
            this.lastDetectedTime = System.currentTimeMillis();
            this.silentIntercept = false;
            this.recentChoices = new ArrayList<>();
        }
    }

    public static class DetectedPackages {
        public List<String> installedPackages;
        public List<String> notInstalledPackages;
        public String patternHash;

        public DetectedPackages() {
            installedPackages = new ArrayList<>();
            notInstalledPackages = new ArrayList<>();
        }
    }

    private static boolean matchesPattern(String identifier, String pattern) {
        if (identifier == null || pattern == null) return false;
        identifier = identifier.toLowerCase();
        pattern = pattern.toLowerCase();

        boolean isExclude = pattern.startsWith("!");
        String actualPattern = isExclude ? pattern.substring(1) : pattern;

        if (!actualPattern.contains("*")) {
            return isExclude ? !identifier.equals(actualPattern) : identifier.equals(actualPattern);
        }

        String regex = actualPattern.replace(".", "\\.").replace("*", ".*");
        try {
            boolean matched = identifier.matches(regex);
            return isExclude ? !matched : matched;
        } catch (java.util.regex.PatternSyntaxException e) {
            return false;
        }
    }

    public static String getAutoAction(String app, String identifier) {
        Map<String, String> actions = autoActionMap.get(app);
        if (actions == null) return null;

        // 精确匹配
        String exact = actions.get(identifier);
        if (exact != null) return exact;

        // 通配符匹配：按模式长度降序（最长优先）
        String matched = null;
        int maxLen = -1;
        for (Map.Entry<String, String> entry : actions.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.contains("*") && matchesPattern(identifier, pattern)) {
                int len = pattern.length();
                if (len > maxLen) {
                    maxLen = len;
                    matched = entry.getValue();
                }
            }
        }
        return matched;
    }

    public static String checkAutoChoice(String app, String identifier) {
        Map<String, List<String>> records = autoChoiceRecordsMap.get(app);
        if (records == null) return null;

        // 精确匹配
        List<String> history = records.get(identifier);
        if (history != null && history.size() >= 3) {
            String first = history.get(0);
            for (int i = 1; i < history.size(); i++) {
                if (!first.equals(history.get(i))) return null;
            }
            return first;
        }

        // 通配符匹配：按模式长度降序
        String matched = null;
        int maxLen = -1;
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            String pattern = entry.getKey();
            if (pattern.contains("*") && matchesPattern(identifier, pattern)) {
                List<String> rec = entry.getValue();
                if (rec != null && rec.size() >= 3) {
                    String first = rec.get(0);
                    boolean allSame = true;
                    for (int i = 1; i < rec.size(); i++) {
                        if (!first.equals(rec.get(i))) {
                            allSame = false;
                            break;
                        }
                    }
                    if (allSame) {
                        int len = pattern.length();
                        if (len > maxLen) {
                            maxLen = len;
                            matched = first;
                        }
                    }
                }
            }
        }
        return matched;
    }

    public static void putAutoAction(String app, String finalidentifier, String action) {
        Map<String, String> actions = autoActionMap.get(app);
        if (actions == null) {
            actions = new HashMap<>();
            autoActionMap.put(app, actions);
        }
        actions.put(finalidentifier, action);
    }

    public static void removeAutoAction(String app, String finalidentifier) {
        Map<String, String> actions = autoActionMap.get(app);
        if (actions != null) {
            actions.remove(finalidentifier);
        }
    }

    public static void addAutoRecord(String app, String identifier, String choice) {
        Map<String, List<String>> records = autoChoiceRecordsMap.get(app);
        if (records == null) {
            records = new HashMap<>();
            autoChoiceRecordsMap.put(app, records);
        }
        List<String> history = records.get(identifier);
        if (history == null) {
            history = new ArrayList<>();
            records.put(identifier, history);
        }
        history.add(choice);
        if (history.size() > 3) {
            history.remove(0);
        }
    }

    public static Map<String, String> getAllAutoActions(String app) {
        return autoActionMap.getOrDefault(app, new HashMap<String, String>());
    }

    public static Map<String, List<String>> getAllAutoRecords(String app) {
        return autoChoiceRecordsMap.getOrDefault(app, new HashMap<String, List<String>>());
    }

    public static void removeAutoRecord(String app, String identifier) {
        Map<String, List<String>> records = autoChoiceRecordsMap.get(app);
        if (records != null) {
            records.remove(identifier);
        }
    }

    public void setCurrentResumedActivity(Activity activity) {
        this.currentResumedActivity = activity;
    }

    public Activity getCurrentResumedActivity() {
        return this.currentResumedActivity;
    }

    private static final ConcurrentHashMap<
            Activity, Boolean> backPressFinishFlag = new ConcurrentHashMap<>();

    public boolean isBackPressFinish(Activity activity) {
        return backPressFinishFlag.getOrDefault(activity, false);
    }

    public static void markBackPressFinish(final Activity activity) {
        if (activity != null) {
            backPressFinishFlag.put(activity, true);
            More.postOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            backPressFinishFlag.remove(activity);
                        }
                    }, 500);
        }
    }

    private static class DebounceEntry {
        WeakReference<Activity> activityRef;
        Intent originalIntent;
        String targetPkg;
        int requestCode;
        boolean reallyInstalled;
        Runnable pendingRunnable;

        DebounceEntry(Activity activity, Intent intent, String pkg, int code, boolean installed) {
            this.activityRef = new WeakReference<>(activity);
            this.originalIntent = intent;
            this.targetPkg = pkg;
            this.requestCode = code;
            this.reallyInstalled = installed;
        }
    }

    public static volatile long sFakeTimeOffset = 0;
    private Runnable mClearFakeTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (sFakeTimeOffset != 0) {
                ReaLog.log("time_fake", "清除虚假启动偏移 (原偏移 " + sFakeTimeOffset + "ms)");
            }
            sFakeTimeOffset = 0;
        }
    };

    private final Map<String, DebounceEntry> debounceMap = new ConcurrentHashMap<>();
    private Handler debounceHandler;

    private String generateDebounceKey(Intent intent, String targetPkg) {
        if (!TextUtils.isEmpty(targetPkg)) {
            return targetPkg;
        }
        Uri uri = intent.getData();
        if (uri != null) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            if (scheme != null && host != null) {
                return scheme + "://" + host + (path != null ? path : "");
            }
            return uri.toString();
        }
        return intent.toUri(0);
    }

    private void debounceAndShowDialog(Activity activity, Intent originalIntent, String targetPkg,
            int requestCode, boolean reallyInstalled) {
        final String key = generateDebounceKey(originalIntent, targetPkg);
        if (key == null) {
            showLaunchConfirmDialog(activity, originalIntent, targetPkg, requestCode, reallyInstalled);
            return;
        }
        if (debounceHandler == null) {
            debounceHandler = new Handler(Looper.getMainLooper());
        }
        synchronized (debounceMap) {
            DebounceEntry existing = debounceMap.get(key);
            if (existing != null && existing.pendingRunnable != null) {
                debounceHandler.removeCallbacks(existing.pendingRunnable);
            }
            final DebounceEntry newEntry = new DebounceEntry(activity, originalIntent, targetPkg,
            requestCode, reallyInstalled);
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    DebounceEntry removed = debounceMap.remove(key);
                    if (removed == newEntry) {
                        Activity act = newEntry.activityRef.get();
                        if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                            showLaunchConfirmDialog(act, newEntry.originalIntent,
                            newEntry.targetPkg, newEntry.requestCode, newEntry.reallyInstalled);
                        }
                    }
                }
            };
            newEntry.pendingRunnable = task;
            debounceMap.put(key, newEntry);
            debounceHandler.postDelayed(task, 300);
        }
    }

    public JSONObject recordsToJson(Map<String, List<String>> records) {
        JSONObject json = new JSONObject();
        if (records == null) return json;
        for (Map.Entry<String, List<String>> entry : records.entrySet()) {
            JSONArray arr = new JSONArray();
            for (String s : entry.getValue()) {
                arr.put(s);
            }
            try {
                json.put(entry.getKey(), arr);
            } catch (Exception e) {
            }
        }
        return json;
    }

    public Map<String, List<String>> jsonToRecords(JSONObject json) {
        Map<String, List<String>> records = new HashMap<>();
        if (json == null) return records;
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONArray arr = json.getJSONArray(key);
                List<String> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    list.add(arr.getString(i));
                }
                records.put(key, list);
            } catch (Exception e) {
            }
        }
        return records;
    }

    private static volatile boolean bypassExitHook = false;
    public AlertDialog currentDialog;

    public static void setBypassExitHook(boolean bypass) {
        bypassExitHook = bypass;
    }

    private Map<String, String> sessionChoiceMap = new ConcurrentHashMap<>();

    public List<InterceptPattern> getInterceptPatterns() {
        return interceptPatternsMap.getOrDefault(currentTargetApp, new ArrayList<>());
    }

    public boolean removeInterceptPattern(String patternHash) {
        List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
        if (patterns != null) {
            Iterator<InterceptPattern> it = patterns.iterator();
            while (it.hasNext()) {
                if (it.next().patternHash.equals(patternHash)) {
                    it.remove();
                    saveConfigToFile();
                    return true;
                }
            }
        }
        return false;
    }

    /** 按 hash 找一条拦截模式（新 UI 的半屏设置页读当前值用） */
    public InterceptPattern findInterceptPattern(String patternHash) {
        if (patternHash == null) return null;
        List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
        if (patterns != null) {
            for (InterceptPattern pattern : patterns) {
                if (pattern != null && patternHash.equals(pattern.patternHash)) return pattern;
            }
        }
        return null;
    }

    /** 设置一条拦截模式是否静默处理（半屏里的开关用） */
    public boolean setInterceptPatternSilent(String patternHash, boolean silent) {
        InterceptPattern pattern = findInterceptPattern(patternHash);
        if (pattern == null) return false;
        pattern.silentIntercept = silent;
        saveConfigToFile();
        return true;
    }

    /** 清空一条拦截模式的累计计数与静默资格，回到「重新观察」状态 */
    public boolean resetInterceptPatternCount(String patternHash) {
        InterceptPattern pattern = findInterceptPattern(patternHash);
        if (pattern == null) return false;
        pattern.choiceCount = 0;
        pattern.silentIntercept = false;
        if (pattern.recentChoices != null) pattern.recentChoices.clear();
        sessionChoiceMap.remove(patternHash);
        saveConfigToFile();
        return true;
    }

    public void removeAllInterceptPatterns() {
        interceptPatternsMap.put(currentTargetApp, new ArrayList<>());
        saveConfigToFile();
    }

    public void setInterceptPatternUserChoice(String patternHash, String newChoice) {
        List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
        if (patterns != null) {
            for (InterceptPattern pattern : patterns) {
                if (pattern.patternHash.equals(patternHash)) {
                    pattern.userChoice = newChoice;
                    pattern.silentIntercept = false;
                    pattern.recentChoices.clear();
                    pattern.choiceCount = 0;
                    sessionChoiceMap.remove(patternHash);
                    saveConfigToFile();
                    break;
                }
            }
        }
    }

    
     // 退出拦截的拦截/放行状态修改但保留累计计数与静默资格
    public void setInterceptPatternUserChoiceKeepCount(String patternHash, String newChoice) {
        List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
        if (patterns != null) {
            for (InterceptPattern pattern : patterns) {
                if (pattern.patternHash.equals(patternHash)) {
                    pattern.userChoice = newChoice;
                    saveConfigToFile();
                    break;
                }
            }
        }
    }

    public void addPendingFinishActivity(Activity activity) {
        if (activity != null) {
            pendingFinishSet.add(activity);
        }
    }

    private static final String[] BASE_SYSTEM_PACKAGES = {
            "root", "system", "android", "com.android", "de.robv.android.",
            "com.google.", "com.google.android", "com.google.android.gms",
            "com.google.android.webview", "org.lsposed.", "com.lsposed.",
            "com.topjohnwu.", "io.va.exposed.", "org.meowcat.edxposed."
    };

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
            "com.nubia", "com.zte", "com.sonyericsson", "com.yulong", "com.transsion"
    };

    public static final Map<String, Boolean> vendorChoiceMap = new ConcurrentHashMap<>();
    private boolean needVendorDialog = false;

    private boolean isSystemPackage(String packageName) {
        if (mPkgMgr != null) {
            return mPkgMgr.isSystemPackage(packageName);
        }
        return false;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
        ReaLog.log("system", "初始化入口: " + lpparam.packageName);
        try {
            XposedHelpers.findAndHookMethod("android.webkit.WebView", null, "onDraw", android.graphics.Canvas.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {}
                    });
        } catch (Throwable ignored) {
        }
        try {
            XposedHelpers.findAndHookMethod("org.chromium.android_webview.AwContents", null, "destroy",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return null;
                        }
                    });
        } catch (Throwable ignored) {
        }

        try {
            Class<?> bundleClass = Class.forName("android.os.Bundle");
            String[] bundleMethods = {"getString", "getInt", "getBoolean", "getLong", "getDouble", "getFloat", "getBundle", "getSerializable", "getParcelable", "containsKey"};
            for (final String methodName : bundleMethods) {
                try {
                    XposedHelpers.findAndHookMethod(bundleClass, methodName, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject == null) {
                                setDefaultReturnValue(param);
                                return;
                            }
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
                    });
                } catch (Throwable ignored) {
                }
                try {
                    XposedHelpers.findAndHookMethod(bundleClass, methodName, String.class, Object.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (param.thisObject == null) {
                                param.setResult(param.args[1]);
                                return;
                            }
                            try {
                                Bundle b = (Bundle) param.thisObject;
                                if (!b.containsKey((String) param.args[0]))
                                    param.setResult(param.args[1]);
                            } catch (Throwable e) {
                                param.setResult(param.args[1]);
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
            XposedHelpers.findAndHookMethod(bundleClass, "getString", String.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.thisObject == null) {
                        param.setResult(param.args[1]);
                        return;
                    }
                    try {
                        Bundle b = (Bundle) param.thisObject;
                        if (!b.containsKey((String) param.args[0])) param.setResult(param.args[1]);
                    } catch (Throwable e) {
                        param.setResult(param.args[1]);
                    }
                }
            });
        } catch (Throwable ignored) {
        }

        Context appContext = getApplicationContext();

        if (appContext == null) {
            try {
                appContext = (Context) XposedHelpers.getObjectField(lpparam, "appContext");
                if (appContext != null) {
                    ReaLog.log("system", "获取Context: 通过 LoadPackageParam 成功");
                }
            } catch (Throwable ignored) {
            }
        }

        if (appContext != null) {
            try {
                ApplicationInfo ai = appContext.getApplicationInfo();
                if (ai != null) {
                    XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                }
            } catch (Throwable ignored) {
            }
        }

        ClassLoader realClassLoader = (appContext != null) ? appContext.getClassLoader() : lpparam.classLoader;
        String realPackageName = (appContext != null) ? appContext.getPackageName() : lpparam.packageName;

        if ("com.install.appinstall.xl".equals(lpparam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(
                        MainActivity.class.getName(),
                        lpparam.classLoader,
                        "isModuleActivated",
                        XC_MethodReplacement.returnConstant(true)
                );
                ReaLog.log("system", "模块已激活");
            } catch (Throwable t) {
                ReaLog.log("system", "模块激活失败: " + t.getMessage());
            }
            return;
        }

        // 初始化 PkgMgr 和 Binder 代理
        if (mPkgMgr == null) {
            mPkgMgr = new PkgMgr(this);
        }

        // 初始化 DebugModeManager
        DebugModeManager.init(this);

        // Binder层拦截
        if (DebugModeManager.isFeatureEnabled("hook_service_manager")) {
            mPkgMgr.installServiceManagerHook(lpparam.classLoader);
        }

        currentTargetApp = realPackageName;

        // 加载本地配置
        try {
            loadConfigFromFile();
            ReaLog.init(this);
        } catch (Throwable e) {
            // 运行中二次 handleLoadPackage / ReaLog.init 异常时，若当前 App 内存已有配置则保留，避免裸 createDefaultConfig 把已加载好的配置重置为默认值
            if (!hasInMemoryConfig(realPackageName)) {
                createDefaultConfig();
            } else {
                log("二次加载配置异常，保留内存中的现有配置: " + e.getMessage());
                ReaLog.log("config", "二次加载配置异常，保留内存中的现有配置: " + e.getMessage());
            }
        }

        doHook(realPackageName, realClassLoader, appContext);
    }

    // ============================================================
    //  门控功能挂载清单（单一数据源）：doHook 启动挂载 与 导入补挂(applyDebugFeatureChanges) 共用。
    //  全部功能经 BuHook 仓库统一调度（门控/幂等/熔断/热重载补挂）：
    //    - 各组件功能注册见其 registerHooks()（PkgMgr / Prsprn / More）
    //    - 本类方法与复合项注册见 registerAllHooks()
    //  doHook 同步段 4 项（对话框取消/截图限制/foat/Activity 生命周期）同样注册在仓库 PHASE_NOW，
    //  模块禁用路径经单项 mount 挂载这 4 项后 return（保持原"仅 Hook 弹窗/截屏"语义）。
    // ============================================================
    private void mountFeatureHooks(final ClassLoader classLoader, final String packageName, Context appContext, final boolean isFlutterApp) {
        if (mPrsprn == null) mPrsprn = new Prsprn(this);
        if (mPkgMgr == null) mPkgMgr = new PkgMgr(this);

        registerAllHooks(packageName);
        registerFlutterHook(isFlutterApp, packageName);
        BuHook.mountAll(classLoader, appContext);
    }

    // 功能注册幂等标志（与 BuHook 的 static 注册表一致：同进程只注册一次，重复调用安全）
    private static boolean sHooksRegistered = false;
    private static boolean sFlutterHookRegistered = false;

    /**
     * 向 BuHook 注册本类全部挂载功能与复合项（注册幂等，重复调用安全）。
     * 注册顺序决定各阶段组内的执行顺序；PHASE_NOW 组含原 doHook 同步段 4 项。
     */
    private void registerAllHooks(final String packageName) {
        if (sHooksRegistered) return;
        sHooksRegistered = true;
        if (mPrsprn == null) mPrsprn = new Prsprn(this);
        if (mPkgMgr == null) mPkgMgr = new PkgMgr(this);
        if (foatInstance == null) foatInstance = new foat(this);

        // --- 组件委托注册 ---
        mPkgMgr.registerHooks();
        mPrsprn.registerHooks();
        More.registerHooks();

        // --- 同步段（原 doHook 禁用路径也挂的 4 项） ---
        BuHook.registerSystem("foat_volume_key_lifecycle", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                if (foatInstance == null) foatInstance = new foat(HookInit.this);
                foatInstance.initVolumeKeyDoubleClick(cl);
                foatInstance.hookActivityLifecycle(cl);
            }
        });
        BuHook.register("hook_base_activity_lifecycle", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookBaseActivityLifecycle(cl);
            }
        });

        // --- 立即段（原 mountFeatureHooks 同步部分） ---
        BuHook.registerRefresh("crash_protect", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                applyCrashProtect(crashProtectEnabledMap.getOrDefault(packageName, true), ctx);
            }
        });
        BuHook.register("hook_exit_methods", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookExitMethods(cl);
            }
        });
        BuHook.register("hook_indirect_exit", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookIndirectExitMethods(cl);
            }
        });
        BuHook.register("hook_global_exit_sources", BuHook.PHASE_NOW, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookGlobalExitSources(cl);
            }
        });

        // --- 极速段（本类方法 6 项） ---
        BuHook.register("hook_bundle_get_string", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookBundleGetString(cl);
            }
        });
        BuHook.register("hook_bundle_empty", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookBundleEmptyInstance(cl);
            }
        });
        BuHook.register("hook_so_path_fix", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookSoPathInApp(cl);
            }
        });
        BuHook.register("hook_system_file_read", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookSystemFileRead();
            }
        });
        BuHook.register("hook_package_manager_reflect", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookPackageManagerReflect();
            }
        });
        BuHook.register("hook_Desktop_App_Query", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookDesktopAppQuery(cl);
            }
        });
        // 极速段末尾：痕迹检测/网络代理（内部各自管理 9/11 项子项明细与幂等）
        BuHook.registerRefresh("anti_detection", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                AntiDetection.initForEmbed(cl, packageName, HookInit.this);
            }
        });
        BuHook.registerRefresh("vpn_fake", BuHook.PHASE_FRONT, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                VpnStatusFaker.installForEmbed(cl, HookInit.this, packageName);
            }
        });

        // --- 普通段（本类方法 6 项 + SELinux 复合项） ---
        BuHook.register("hook_system_properties", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookSystemProperties(cl);
            }
        });
        BuHook.register("hook_start_activity", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookStartActivity(cl);
            }
        });
        BuHook.register("hook_query_intent_activities", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookQueryIntentActivities(cl);
            }
        });
        BuHook.register("hook_resolve_activity", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookResolveActivity(cl);
            }
        });
        BuHook.register("hook_query_intent_services", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookQueryIntentServices(cl);
            }
        });
        BuHook.register("hook_query_broadcast_receivers", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                hookQueryBroadcastReceivers(cl);
            }
        });
        BuHook.registerRefresh("selinux_fake", BuHook.PHASE_NORMAL, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                Selinuxhook.setEnabled(packageName, selinuxFakeMap.getOrDefault(packageName, true));
                Selinuxhook.installHooks(cl);
            }
        });

        // --- 延迟 1s（分享复合 / 时间 / 返回键） ---
        BuHook.registerRefresh("share_fake", BuHook.PHASE_DELAY_1S, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                ShareHook.setHookInstance(HookInit.this);
                Map<String, Boolean> initShareMap = shareFakeDetailMap.getOrDefault(
                        packageName, getDefaultShareDetailMap());
                ShareHook.setAllEnabled(initShareMap);

                try {
                    ShareHook.hookWeChatShareOnly(cl, packageName, new ShareHook.BooleanProvider() {
                        @Override
                        public boolean get() {
                            return ShareHook.sEnableXW;
                        }
                    });
                } catch (Throwable t) {
                }
                try {
                    ShareHook.hookXQShareOnly(cl, new ShareHook.BooleanProvider() {
                        @Override
                        public boolean get() {
                            return ShareHook.sEnableXQ;
                        }
                    });
                } catch (Throwable t) {
                }
                try {
                    ShareHook.hookDingTalkShareOnly(cl, new ShareHook.BooleanProvider() {
                        @Override
                        public boolean get() {
                            return ShareHook.sEnableXD;
                        }
                    });
                } catch (Throwable t) {
                }
                try {
                    ShareHook.hookWeiboShareOnly(cl, new ShareHook.BooleanProvider() {
                        @Override
                        public boolean get() {
                            return ShareHook.sEnableXB;
                        }
                    });
                } catch (Throwable t) {
                }
            }
        });
        BuHook.register("hook_time_fake", BuHook.PHASE_DELAY_1S, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                installTimeFakeHooks(cl);
            }
        });
        BuHook.register("force_default_back", BuHook.PHASE_DELAY_1S, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                More.hookActivityDefaultBack(cl, packageName);
            }
        });

        // --- 延迟 2s（退出拦截兜底，无门控） ---
        BuHook.registerSystem("maybe_block_exit", BuHook.PHASE_DELAY_2S, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                maybeEnableBlockExit();
            }
        });
    }

    /**
     * 注册 Flutter 应用专属挂载项（条件 = isFlutterApp；单项注册幂等）。
     * 与 registerAllHooks 分离的原因：doHook 同步段执行时 isFlutterApp 尚未计算。
     */
    private void registerFlutterHook(final boolean isFlutterApp, final String packageName) {
        if (sFlutterHookRegistered) return;
        sFlutterHookRegistered = true;
        if (mPkgMgr == null) mPkgMgr = new PkgMgr(this);
        BuHook.register("flutter_app_hooks", BuHook.PHASE_DELAY_1S, new BuHook.HookCondition() {
            @Override
            public boolean ok() {
                return isFlutterApp;
            }
        }, new BuHook.HookInstaller() {
            @Override
            public void install(ClassLoader cl, Context ctx) throws Throwable {
                mPkgMgr.installFlutterHooks(cl);
            }
        });
    }


    private void doHook(final String packageName, final ClassLoader classLoader, Context appContext) {

        if (mPrsprn == null) {
            mPrsprn = new Prsprn(this);
        }
        if (mPkgMgr == null) {
            mPkgMgr = new PkgMgr(this);
        }

        DebugModeManager.init(this);
        if (DebugModeManager.isDebugModeActive()) {
            log("调试模式激活状态：" + DebugModeManager.isDebugModeActive());
        }

        boolean moduleEnabled = true;
        boolean isVendor = false;
        for (String vendorPrefix : VENDOR_PACKAGE_PREFIXES) {
            if (packageName.startsWith(vendorPrefix)) {
                isVendor = true;
                break;
            }
        }
        if (isVendor) {
            Boolean choice = vendorChoiceMap.get(packageName);
            if (choice == null) {
                if (mPkgMgr.isRealSystemPackage(packageName)) {
                    vendorChoiceMap.put(packageName, false);
                    needVendorDialog = true;
                    choice = false;
                } else {
                    vendorChoiceMap.put(packageName, true);
                    needVendorDialog = false;
                    choice = true;
                }
            }
            moduleEnabled = choice;
            ReaLog.log("system", "强制包: " + packageName + ", 模块启用: " + moduleEnabled);
        }

        // 同步段 4 项（对话框取消/截图限制/foat/Activity 生命周期）：所有包都挂（含禁用包/系统包）。
        // 经 BuHook 单项 mount 同步执行；正式挂载由 mountFeatureHooks 统一调度。
        registerAllHooks(packageName);
        BuHook.mount("hook_dialog_cancelable", classLoader, appContext);
        BuHook.mount("hook_window_flags", classLoader, appContext);
        BuHook.mount("foat_volume_key_lifecycle", classLoader, appContext);
        BuHook.mount("hook_base_activity_lifecycle", classLoader, appContext);

        if (!moduleEnabled) {
            ReaLog.log("system", "模块已禁用，仅Hook弹窗/截屏");
            log("模块已禁用，仅Hook弹窗/截屏");
            return;
        }

        ReaLog.log("system", "正式开始Hook: " + packageName);
        currentTargetApp = packageName;
        final boolean isFlutterApp = isFlutterApp(classLoader);

        for (String basePkg : BASE_SYSTEM_PACKAGES) {
            if (packageName.startsWith(basePkg)) {
                ReaLog.log("system", "基础系统包，跳过: " + packageName);
                return;
            }
        }

        try {
            if (!installStatusMap.containsKey(packageName)) installStatusMap.put(packageName, true);
            if (!floatingShownMap.containsKey(packageName)) floatingShownMap.put(packageName, true);
            if (!blockExitMap.containsKey(packageName)) blockExitMap.put(packageName, false);
            if (!superBlockExitMap.containsKey(packageName))
                superBlockExitMap.put(packageName, false);
            if (!permissionFakeMap.containsKey(packageName))
                permissionFakeMap.put(packageName, true);
            if (!userDefinedPackagesMap.containsKey(packageName))
                userDefinedPackagesMap.put(packageName, new ArrayList<String>());
            if (!excludedPackagesMap.containsKey(packageName))
                excludedPackagesMap.put(packageName, new ArrayList<String>());
        } catch (Throwable t) {
            log("初始化配置状态异常: " + t.getMessage());
            ReaLog.log("system", "初始化配置状态异常: " + t.getMessage());
        }

        // 门控功能挂载（单一数据源）：清单见 mountFeatureHooks，doHook 与导入补挂共用
        mountFeatureHooks(classLoader, packageName, appContext, isFlutterApp);
    }

    private boolean isFlutterApp(ClassLoader classLoader) {
        try {
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

    private void hookBaseActivityLifecycle(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onWindowFocusChanged",
                    boolean.class,
                    new XC_MethodHook() {
                        private boolean shown = false;

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            boolean hasFocus = (boolean) param.args[0];
                            if (!hasFocus) return;
                            if (shown) return;
                            if (!needVendorDialog) return;

                            final Activity activity = (Activity) param.thisObject;
                            if (activity == null || activity.isFinishing()) return;

                            final String pkg = currentTargetApp;
                            More.postOnMainThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (!needVendorDialog) return;
                                            if (activity.isFinishing() || activity.isDestroyed())
                                                return;
                                            ReaLog.log("system", "显示强制包询问对话框: " + pkg);
                                            showVendorDialog(activity, pkg);
                                        }
                                    }, 1300);

                            shown = true;
                        }
                    }
            );
        } catch (Throwable t) {
            log("强制包询问对话框失败: " + t.getMessage());
        }
    }

    private void showVendorDialog(final Activity activity, final String packageName) {
        ReaLog.log("system", "显示不推荐包询问弹窗: " + packageName);
        if (activity == null || activity.isFinishing()) {
            ReaLog.log("system", "showVendorDialog: Activity无效");
            return;
        }
        if (!needVendorDialog) {
            ReaLog.log("system", "不推荐包已为false，退出");
            return;
        }
        needVendorDialog = false;
        String message = "<font color='#FF5722'><b>检测到当前应用</b></font><br>" +
                "可能是系统/不推荐启用的应用：<br><b><font color='#FF5722'>" + packageName + "</font></b><br><br>" +
                "是否强制启用\"安装防护模块\"？<br>" +
                "启用后可能导致当前应用出现异常问题，请谨慎选择！<br><br>";

        AlertDialog dialog = DialogUi.createBoundedDialog(
        activity,
        "系统提示",
        message,
        new String[]{"强制开启", "不启用"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("system", "不推荐包选择: 强制开启 " + packageName);
                        vendorChoiceMap.put(packageName, true);
                        mIsSystemPackageCache = null;
                        saveConfigImmediate();
                        ToastUtil.showUnique(activity, "✅ 已强制启用,应用即将停止...\n请重新启动应用！");
                        More.postOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        restartApplication(activity);
                                    }
                                }, 1500);
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("system", "不推荐包选择: 不启用 " + packageName);
                        vendorChoiceMap.put(packageName, false);
                        mIsSystemPackageCache = null;
                        saveConfigImmediate();
                        ToastUtil.showUnique(activity, "❌ 已禁用模块");
                    }
                }
        }
        );
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                ReaLog.log("system", "不推荐包选择: 取消弹窗，默认不启用 " + packageName);
                vendorChoiceMap.put(packageName, false);
                mIsSystemPackageCache = null;
                saveConfigImmediate();
            }
        });
        dialog.show();
        ReaLog.log("system", "启用包询问弹窗已显示");
    }

    private String getCurrentProcessName() {
        if (sCachedProcessName != null) {
            return sCachedProcessName;
        }
        try {
            sCachedProcessName = (String) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", null),
                            "currentProcessName"
                    );
            if (sCachedProcessName != null && !sCachedProcessName.isEmpty()) {
                ReaLog.log("system", "获取进程名: " + sCachedProcessName);
                return sCachedProcessName;
            }
        } catch (Throwable e) {
        }
        try {
            Context ctx = (Context) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", null),
                            "currentApplication"
                    );
            if (ctx != null) {
                sCachedProcessName = ctx.getPackageName();
                ReaLog.log("system", "通过Context获取进程名: " + sCachedProcessName);
                return sCachedProcessName;
            }
        } catch (Throwable e) {
        }
        sCachedProcessName = "";
        return sCachedProcessName;
    }

    public void restartApplication(Context context) {
        ReaLog.log("system", "开始重启应用");
        saveConfigToFile();

        try {
            Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launchIntent == null) {
                ToastUtil.showUnique(context, "无法自动重启，请手动结束应用");
                ReaLog.log("system", "重启失败: 无法获取启动Intent");
                Process.killProcess(Process.myPid());
                System.exit(0);
                return;
            }

            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, pendingFlags);

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            long triggerTime = System.currentTimeMillis() + 200;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }

            More.postOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            Process.killProcess(Process.myPid());
                            System.exit(0);
                            ReaLog.log("system", "进程已终止");
                        }
                    }, 150);

            ReaLog.log("system", "重启任务已提交");
        } catch (Throwable e) {
            log("重启应用失败: " + e.getMessage());
            ReaLog.log("system", "重启应用失败: " + e.getMessage());
            ToastUtil.showUnique(context, "自动重启失败，请手动结束应用");
            Process.killProcess(Process.myPid());
            System.exit(0);
        }
    }

    public void showRestartConfirmDialog(final Activity activity) {
        DialogUi.showRestartConfirmDialog(activity);
    }

    public void log(String message) {
        String currentProcess = sCachedProcessName;
        if (currentProcess == null) {
            currentProcess = getCurrentProcessName();
        }
        boolean isMain = currentTargetApp != null && currentTargetApp.equals(currentProcess);

        if (message.contains("❌") || message.contains("错误:")) {
            try {
                ReaLog.log("other", "XP日志：[" + currentTargetApp + "]" + message);
                XposedBridge.log("[" + MODULE_TAG + "] [" + currentTargetApp + "] " + message);
            } catch (Throwable e) {
                android.util.Log.d(MODULE_TAG, "[" + currentTargetApp + "] " + message);
            }
            return;
        }

        if (!isMain) {
            return;
        }

        Long lastTime = sLastLogTime.get(message);
        long now = System.currentTimeMillis();
        if (lastTime != null && (now - lastTime) < LOG_DEDUP_INTERVAL_MS) {
            return;
        }
        sLastLogTime.put(message, now);

        if (sLastLogTime.size() > MAX_LOG_CACHE_SIZE) {
            sLastLogTime.clear();
        }

        try {
            ReaLog.log("other", "XP日志：[" + currentTargetApp + "]" + message);
            XposedBridge.log("[" + MODULE_TAG + "] [" + currentTargetApp + "] " + message);
        } catch (Throwable e) {
            android.util.Log.d(MODULE_TAG, "[" + currentTargetApp + "] " + message);
        }
    }

    private boolean isViewInReaLogDialog(View view) {
        if (view == null) return false;
        if ("rea_log_dialog".equals(view.getTag())) {
            return true;
        }
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof View) {
                Object tag = ((View) parent).getTag();
                if ("rea_log_dialog".equals(tag)) {
                    return true;
                }
            }
            parent = parent.getParent();
        }
        return false;
    }

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
                    } catch (Exception ex) {
                    }
                }
            }
        }
        return list;
    }

    // 判断是否有内存配置（任一核心配置 key 存在即视为已有）
    private boolean hasInMemoryConfig(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return installStatusMap.containsKey(pkg)
                || blockExitMap.containsKey(pkg)
                || permissionFakeMap.containsKey(pkg)
                || shareFakeDetailMap.containsKey(pkg)
                || vpnFakeDetailMap.containsKey(pkg)
                || antiDetectionDetailMap.containsKey(pkg)
                || launchInterceptMap.containsKey(pkg);
    }
    
    //创建默认配置
    private void createDefaultConfig() {
        try {
            permissionFakeDetailMap.put(currentTargetApp, new HashMap<String, Boolean>());
            installStatusMap.put(currentTargetApp, true);
            floatingShownMap.put(currentTargetApp, true);
            permanentHiddenMap.put(currentTargetApp, false);
            blockExitMap.put(currentTargetApp, false);
            superBlockExitMap.put(currentTargetApp, false);
            permissionFakeMap.put(currentTargetApp, true);
            launchInterceptMap.put(currentTargetApp, true);
            selinuxFakeMap.put(currentTargetApp, true);
            shareFakeDetailMap.put(currentTargetApp, getDefaultShareDetailMap());
            forceDefaultBackMap.put(currentTargetApp, false);
            hookDialogCancelableMap.put(currentTargetApp, true);
            hookWindowFlagsMap.put(currentTargetApp, true);
            hookAntiCancelFinishMap.put(currentTargetApp, true);
            // 震动控制默认关闭：新功能不应改变老用户的震动体验
            hookVibratorMap.put(currentTargetApp, true);
            crashProtectEnabledMap.put(currentTargetApp, true);
            interceptPatternsMap.put(currentTargetApp, new ArrayList<InterceptPattern>());
            userDefinedPackagesMap.put(currentTargetApp, new ArrayList<String>());
            excludedPackagesMap.put(currentTargetApp, new ArrayList<String>());
            if (!packageConfigMap.containsKey(currentTargetApp)) {
                packageConfigMap.put(currentTargetApp, new ArrayList<PackageConfig>());
            }
            vpnFakeDetailMap.put(currentTargetApp, getDefaultVpnDetailMap());
            antiDetectionDetailMap.put(currentTargetApp, getDefaultAntiDetectionMap());
            reaLogPauseMap.put(currentTargetApp, new ConcurrentHashMap<>());

            ReaLog.log("config", "默认配置创建完成: " + currentTargetApp);
            log("✅ 创建默认配置完成");
        } catch (Throwable t) {
            log("❌ 创建默认配置异常: " + t.getMessage());
            ReaLog.log("config", "创建默认配置异常: " + t.getMessage());
        }
    }
    
    //迁移旧配置
    private File findConfigFile(String[] dirPaths, String currentPkg) {
        for (String dirPath : dirPaths) {
            File dir = new File(dirPath);
            if (!dir.exists() || !dir.isDirectory()) continue;

            File newFile = new File(dir, "installcf_" + currentPkg + ".json");
            if (newFile.exists() && newFile.length() > 0) {
                return newFile;
            }

            File oldFile = new File(dir, "install_fake_config.json");
            if (oldFile.exists() && oldFile.length() > 0) {
                try {
                    JSONObject root = Portcf.readJsonFromFile(oldFile);
                    if (root.has(currentPkg)) {
                        Portcf.writeJsonToFile(newFile, root);
                        final File f = oldFile;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(3000);
                                    f.delete();
                                } catch (Exception ignored) {
                                }
                            }
                        }).start();
                        return newFile;
                    }
                } catch (Exception e) {
                    log("迁移旧配置失败: " + e.getMessage());
                    ReaLog.log("config", "迁移旧配置失败: " + e.getMessage());
                }
            }
        }
        return null;
    }
    
    //加载配置
    public void loadConfigFromFile() {
        try {
            String currentPkg = currentTargetApp;
            File configFile = null;
            String foundPath = null;

            String[] privatePaths = {
                    "/data/data/" + currentPkg + "/files/",
                    "/data/user/0/" + currentPkg + "/files/"
            };
            configFile = findConfigFile(privatePaths, currentPkg);

            if (configFile == null) {
                String[] externalPaths = {
                        "/storage/emulated/0/Android/data/" + currentPkg + "/files/"
                };
                configFile = findConfigFile(externalPaths, currentPkg);
            }

            if (configFile == null) {
                String[] allPaths = {
                        "/data/data/" + currentPkg + "/files/",
                        "/data/user/0/" + currentPkg + "/files/",
                        "/storage/emulated/0/Android/data/" + currentPkg + "/files/"
                };
                for (String dirPath : allPaths) {
                    File dir = new File(dirPath);
                    if (!dir.exists() || !dir.isDirectory()) continue;
                    File[] files = dir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File d, String name) {
                            return name.startsWith("installcf_") && name.endsWith(".json");
                        }
                    });
                    if (files != null && files.length > 0) {
                        Arrays.sort(files, new Comparator<File>() {
                            @Override
                            public int compare(File f1, File f2) {
                                return Long.compare(f2.lastModified(), f1.lastModified());
                            }
                        });
                        configFile = files[0];
                        foundPath = configFile.getAbsolutePath();
                        log("使用备份配置文件: " + foundPath);
                        ReaLog.log("config", "使用备份配置文件: " + foundPath);
                        break;
                    }
                }
            }

            if (configFile != null && configFile.exists()) {
                foundPath = configFile.getAbsolutePath();
                try {
                    JSONObject configJson = Portcf.readJsonFromFile(configFile);
                    if (configJson.has(currentTargetApp)) {
                        JSONObject appConfig = configJson.getJSONObject(currentTargetApp);

                        installStatusMap.put(currentTargetApp, appConfig.optBoolean("install_status", true));
                        floatingShownMap.put(currentTargetApp, appConfig.optBoolean("floating_shown", true));
                        permanentHiddenMap.put(currentTargetApp, appConfig.optBoolean("permanent_hidden", false));
                        String xStr = appConfig.optString("floating_x", "null");
                        if (!"null".equals(xStr) && !xStr.isEmpty()) {
                            try {
                                floatingXMap.put(currentTargetApp, Float.parseFloat(xStr));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        String yStr = appConfig.optString("floating_y", "null");
                        if (!"null".equals(yStr) && !yStr.isEmpty()) {
                            try {
                                floatingYMap.put(currentTargetApp, Float.parseFloat(yStr));
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        blockExitMap.put(currentTargetApp, appConfig.optBoolean("block_exit", false));
                        superBlockExitMap.put(currentTargetApp, appConfig.optBoolean("super_block_exit", false));
                        userDisabledAutoBlockMap.put(currentTargetApp, appConfig.optBoolean("user_disabled_auto_block", false));
                        permissionFakeMap.put(currentTargetApp, appConfig.optBoolean("permission_fake", true));
                        launchInterceptMap.put(currentTargetApp, appConfig.optBoolean("launch_intercept", true));
                        if (appConfig.has("vendor_enabled")) {
                            boolean vendorEnabled = appConfig.getBoolean("vendor_enabled");
                            vendorChoiceMap.put(currentTargetApp, vendorEnabled);
                        }
                        selinuxFakeMap.put(currentTargetApp, appConfig.optBoolean("selinux_fake", true));
                        forceDefaultBackMap.put(currentTargetApp, appConfig.optBoolean("force_default_back", false));
                        hookDialogCancelableMap.put(currentTargetApp, appConfig.optBoolean("hook_dialog_cancelable", true));
                        hookWindowFlagsMap.put(currentTargetApp, appConfig.optBoolean("hook_window_flags", true));
                        hookAntiCancelFinishMap.put(currentTargetApp, appConfig.optBoolean("hook_anti_cancel_finish", true));
                        hookVibratorMap.put(currentTargetApp, appConfig.optBoolean("hook_vibrator", true));
                        crashProtectEnabledMap.put(currentTargetApp, appConfig.optBoolean("crash_protect_enabled", true));

                        JSONArray userPackagesArray = appConfig.optJSONArray("user_defined_packages");
                        List<
                                String> userPackages = userPackagesArray != null ? jsonArrayToStringList(userPackagesArray) : new ArrayList<
                                        String>();
                        userDefinedPackagesMap.put(currentTargetApp, userPackages);

                        JSONArray excludedArray = appConfig.optJSONArray("excluded_packages");
                        List<
                                String> excludedPackages = excludedArray != null ? jsonArrayToStringList(excludedArray) : new ArrayList<
                                        String>();
                        excludedPackagesMap.put(currentTargetApp, excludedPackages);

                        JSONArray pkgConfigArray = appConfig.optJSONArray("package_configs");
                        List<PackageConfig> configs = new ArrayList<PackageConfig>();
                        if (pkgConfigArray != null) {
                            for (int i = 0; i < pkgConfigArray.length(); i++) {
                                JSONObject obj = pkgConfigArray.getJSONObject(i);
                                PackageConfig cfg = new PackageConfig(obj.getString("packageName"));
                                cfg.statusMode = obj.optString("statusMode", "follow");
                                configs.add(cfg);
                            }
                        }
                        packageConfigMap.put(currentTargetApp, configs);

                        JSONArray patternsArray = appConfig.optJSONArray("intercept_patterns");
                        List<InterceptPattern> patterns = new ArrayList<InterceptPattern>();
                        if (patternsArray != null) {
                            for (int i = 0; i < patternsArray.length(); i++) {
                                JSONObject obj = patternsArray.getJSONObject(i);
                                InterceptPattern pattern = new InterceptPattern(
                                obj.getString("pattern_hash"),
                                jsonArrayToStringList(obj.getJSONArray("installed_packages")),
                                jsonArrayToStringList(obj.getJSONArray("not_installed_packages"))
                                );
                                pattern.userChoice = obj.optString("user_choice", "");
                                pattern.choiceCount = obj.optInt("choice_count", 0);
                                pattern.lastDetectedTime = obj.optLong("last_detected_time", System.currentTimeMillis());
                                pattern.silentIntercept = obj.optBoolean("silent_intercept", false);
                                // 兼容旧配置：recent_choices 缺失时为空列表（不影响新数据）
                                JSONArray recentArr = obj.optJSONArray("recent_choices");
                                if (recentArr != null) {
                                    pattern.recentChoices = new ArrayList<String>(jsonArrayToStringList(recentArr));
                                }
                                patterns.add(pattern);
                            }
                        }
                        interceptPatternsMap.put(currentTargetApp, patterns);

                        JSONArray globalArray = appConfig.optJSONArray("global_captured_packages");
                        globalCapturedPackages.clear();
                        if (globalArray != null) {
                            for (int i = 0; i < globalArray.length(); i++) {
                                String pkg = globalArray.getString(i);
                                globalCapturedPackages.add(pkg);
                            }
                        }

                        JSONObject actionsJson = appConfig.optJSONObject("auto_actions");
                        Map<String, String> actions = new HashMap<String, String>();
                        if (actionsJson != null) {
                            Iterator<String> keys = actionsJson.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                actions.put(key, actionsJson.getString(key));
                            }
                        }
                        autoActionMap.put(currentTargetApp, actions);

                        JSONObject recordsJson = appConfig.optJSONObject("auto_records");
                        Map<String, List<String>> records = new HashMap<String, List<String>>();
                        if (recordsJson != null) {
                            Iterator<String> keys = recordsJson.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                JSONArray arr = recordsJson.getJSONArray(key);
                                List<String> list = new ArrayList<String>();
                                for (int i = 0; i < arr.length(); i++) {
                                    list.add(arr.getString(i));
                                }
                                records.put(key, list);
                            }
                        }
                        autoChoiceRecordsMap.put(currentTargetApp, records);

                        JSONObject permDetailJson = appConfig.optJSONObject("permission_fake_detail");
                        Map<String, Boolean> permDetail = new HashMap<String, Boolean>();
                        if (permDetailJson != null) {
                            Iterator<String> keys = permDetailJson.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                permDetail.put(key, permDetailJson.getBoolean(key));
                            }
                        }
                        permissionFakeDetailMap.put(currentTargetApp, permDetail);

                        JSONObject antiDetailJson = appConfig.optJSONObject("anti_detection_detail");
                        Map<String, Boolean> antiDetail = new HashMap<String, Boolean>();
                        if (antiDetailJson != null) {
                            for (String k : getDefaultAntiDetectionMap().keySet()) {
                                if (antiDetailJson.has(k)) {
                                    antiDetail.put(k, Boolean.valueOf(antiDetailJson.getBoolean(k)));
                                }
                            }
                        }
                        antiDetectionDetailMap.put(currentTargetApp, antiDetail);

                        JSONObject vpnDetailJson = appConfig.optJSONObject("vpn_fake_detail");
                        Map<String, Boolean> vpnDetail = new HashMap<String, Boolean>();
                        if (vpnDetailJson != null) {
                            for (String k : getDefaultVpnDetailMap().keySet()) {
                                if (vpnDetailJson.has(k)) {
                                    vpnDetail.put(k, Boolean.valueOf(vpnDetailJson.getBoolean(k)));
                                }
                            }
                        }
                        vpnFakeDetailMap.put(currentTargetApp, vpnDetail);

                        JSONObject shareDetailJson = appConfig.optJSONObject("share_fake_detail");
                        Map<String, Boolean> shareDetail = new HashMap<String, Boolean>();
                        if (shareDetailJson != null) {
                            for (String k : getDefaultShareDetailMap().keySet()) {
                                if (shareDetailJson.has(k)) {
                                    shareDetail.put(k, Boolean.valueOf(shareDetailJson.getBoolean(k)));
                                }
                            }
                        }
                        shareFakeDetailMap.put(currentTargetApp, shareDetail);
                        ShareHook.setAllEnabled(shareDetail);

                        JSONObject pauseJson = appConfig.optJSONObject("rea_log_pause");
                        Map<String, Boolean> pauseMap = new ConcurrentHashMap<>();
                        if (pauseJson != null) {
                            Iterator<String> keys = pauseJson.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                pauseMap.put(key, pauseJson.getBoolean(key));
                            }
                        }
                        reaLogPauseMap.put(currentTargetApp, pauseMap);

                        globalCapturedPackages.addAll(userPackages);
                        if (mPkgMgr != null) {
                            mPkgMgr.invalidateAppDirCache();
                        }

                        if (!sConfigLoadedLogged) {
                            log("配置加载完成 " + foundPath);
                            ReaLog.log("config", "配置加载完成 " + foundPath);
                            sConfigLoadedLogged = true;
                        }
                    } else {
                        log("配置文件中无当前应用数据");
                        ReaLog.log("config", "配置文件中无当前应用数据");
                        if (!hasInMemoryConfig(currentTargetApp)) {
                            log("且内存无配置，创建默认配置");
                            ReaLog.log("config", "且内存无配置，创建默认配置");
                            createDefaultConfig();
                        }
                    }
                } catch (Throwable e) {
                    log("读取配置文件异常: " + e.getMessage());
                    ReaLog.log("config", "读取配置文件异常: " + e.getMessage());
                    if (hasInMemoryConfig(currentTargetApp)) {
                        log("配置读取失败，保留内存中的现有配置");
                        ReaLog.log("config", "配置读取失败，保留内存中的现有配置");
                    } else {
                        createDefaultConfig();
                    }
                }
            } else {
                log("未找到配置文件");
                ReaLog.log("config", "未找到配置文件");
                if (!hasInMemoryConfig(currentTargetApp)) {
                    log("且内存无配置，创建默认配置");
                    ReaLog.log("config", "且内存无配置，创建默认配置");
                    createDefaultConfig();
                }
            }
        } catch (Throwable t) {
            log("加载配置异常: " + t.getMessage());
            ReaLog.log("config", "加载配置异常: " + t.getMessage());
            if (!hasInMemoryConfig(currentTargetApp)) {
                createDefaultConfig();
            }
        }
        ReaLog.reloadPauseState();

        int removedCount = 0;
        synchronized (globalCapturedPackages) {
            Iterator<String> it = globalCapturedPackages.iterator();
            while (it.hasNext()) {
                String pkg = it.next();
                if (mPkgMgr.isBaseSystemPackage(pkg) || mPkgMgr.isVendorPackage(pkg) ||
                        (isSystemCorePackage(pkg) && isRealSystemPackage(pkg))) {
                    it.remove();
                    removedCount++;
                    ReaLog.log("config", "清理残留系统包: " + pkg);
                }
            }
        }
        if (removedCount > 0) {
            doSaveConfigToFile();
            ReaLog.log("config", "已清理 " + removedCount + " 个残留系统包并保存");
        }

// ========== 强制重新刷新调试模式状态 ==========
        DebugModeManager.init(this);
// ========== 统一处理悬浮窗隐藏与调试模式 ==========
        boolean isPermanentHidden = permanentHiddenMap.getOrDefault(currentTargetApp, false);
        boolean isTemporaryHidden = !floatingShownMap.getOrDefault(currentTargetApp, true);
        boolean isHidden = isPermanentHidden || isTemporaryHidden;
        // 1. 若调试模式激活且悬浮窗隐藏 → 强制关闭调试模式
        if (DebugModeManager.isDebugModeActive() && isHidden) {
            DebugModeManager.disableDebugMode();
            String msg = isPermanentHidden ? "检测到永久隐藏，调试模式强制关闭" : "检测到临时隐藏，调试模式强制关闭";
            DialogUi.showToastDelayed(msg);
        }
        // 2. 自动恢复临时隐藏（永久隐藏不恢复）
        if (!isPermanentHidden && isTemporaryHidden) {
            floatingShownMap.put(currentTargetApp, true);
            saveConfigToFile(); // 持久化

            if (DebugModeManager.isDebugModeActive()) {
                DebugModeManager.disableDebugMode();
                DialogUi.showToastDelayed("临时隐藏已恢复显示并关闭调试模式");
            } else {
                DialogUi.showToastDelayed("临时隐藏已自动恢复显示");
            }
        }
        mSystemCoreCache.clear();
        packageStatusCache.clear();
        needVendorDialog = false;
        // 重置悬浮窗收边状态（导入/重启后展开）
        if (foatInstance != null) {
            foatInstance.resetCollapsedState();
        }
    }
    
    //保存配置-立即同步
    public void saveConfigImmediate() {
        try {
            doSaveConfigToFile();
        } catch (Throwable t) {
            log("立即保存异常: " + (t == null ? "" : t.getMessage()));
            ReaLog.log("config", "立即保存异常: " + (t == null ? "" : t.getMessage()));
        }
    }
    
    //保存配置-延迟防抖300ms
    public void saveConfigToFile() {
        synchronized (sSaveExecutor) {
            if (sSaveFuture != null) {
                sSaveFuture.cancel(false);
            }
            //记录本次保存的序号，任务执行时若序号已过期（期间又有新保存）则丢弃本次快照
            final long seq = sSaveSeq.incrementAndGet();
            sSaveFuture = sSaveExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    if (seq != sSaveSeq.get()) {
                        log("配置快照过期(seq=" + seq + "，最新=" + sSaveSeq.get() + ")，丢弃本次写盘");
                        ReaLog.log("config", "配置快照过期丢弃: " + seq + "/" + sSaveSeq.get());
                        return;
                    }
                    try {
                        doSaveConfigToFile();
                    } catch (Throwable t) {
                        log("异步保存异常: " + t.getMessage());
                        ReaLog.log("config", "异步保存异常: " + t.getMessage());
                    }
                }
            }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void doSaveConfigToFile() {
        if (currentTargetApp == null || currentTargetApp.isEmpty()) {
            log("配置保存失败：当前目标应用为空");
            return;
        }

        Boolean vendorChoice = vendorChoiceMap.get(currentTargetApp);
        if (vendorChoice != null && !vendorChoice && isSystemPackage(currentTargetApp)) {
            log("用户禁用模块，跳过保存: " + currentTargetApp);
            ReaLog.log("config", "用户禁用模块: " + currentTargetApp);
            return;
        }

        Boolean status = installStatusMap.get(currentTargetApp);
        if (status == null) {
            log("配置保存失败：未获取到应用安装状态");
            return;
        }

        String fileName = "installcf_" + currentTargetApp + ".json";
        String[] savePaths = {
                "/data/data/" + currentTargetApp + "/files/" + fileName,
                "/data/user/0/" + currentTargetApp + "/files/" + fileName,
                "/storage/emulated/0/Android/data/" + currentTargetApp + "/files/" + fileName,
        };
        boolean saveSuccess = false;
        String savedPath = null;
        Throwable lastError = null;

        for (String filePath : savePaths) {
            try {
                File configFile = new File(filePath);
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    if (!parentDir.mkdirs()) {
                        lastError = new Exception("无法创建目录 " + parentDir.getPath());
                        log("无法创建目录: " + parentDir.getPath());
                        continue;
                    }
                }

                JSONObject configJson = new JSONObject();
                if (configFile.exists() && configFile.length() > 0) {
                    try {
                        configJson = Portcf.readJsonFromFile(configFile);
                    } catch (Exception e) {
                        log("读取旧配置失败，覆盖创建: " + e.getMessage());
                        configJson = new JSONObject();
                    }
                }

                JSONObject appConfig = new JSONObject();
                appConfig.put("install_status", status);
                appConfig.put("floating_shown", floatingShownMap.getOrDefault(currentTargetApp, true));
                appConfig.put("permanent_hidden", permanentHiddenMap.getOrDefault(currentTargetApp, false));
                Float x = floatingXMap.get(currentTargetApp);
                Float y = floatingYMap.get(currentTargetApp);
                appConfig.put("floating_x", x != null ? String.valueOf(x) : "null");
                appConfig.put("floating_y", y != null ? String.valueOf(y) : "null");
                appConfig.put("block_exit", blockExitMap.getOrDefault(currentTargetApp, false));
                appConfig.put("super_block_exit", superBlockExitMap.getOrDefault(currentTargetApp, false));
                appConfig.put("user_disabled_auto_block", userDisabledAutoBlockMap.getOrDefault(currentTargetApp, false));
                appConfig.put("permission_fake", permissionFakeMap.getOrDefault(currentTargetApp, true));
                appConfig.put("launch_intercept", launchInterceptMap.getOrDefault(currentTargetApp, true));
                appConfig.put("vendor_enabled", vendorChoiceMap.getOrDefault(currentTargetApp, false));
                appConfig.put("last_save_time", System.currentTimeMillis());
                appConfig.put("last_save_path", filePath);
                appConfig.put("selinux_fake", selinuxFakeMap.getOrDefault(currentTargetApp, true));
                appConfig.put("force_default_back", forceDefaultBackMap.getOrDefault(currentTargetApp, false));
                appConfig.put("hook_dialog_cancelable", hookDialogCancelableMap.getOrDefault(currentTargetApp, true));
                appConfig.put("hook_window_flags", hookWindowFlagsMap.getOrDefault(currentTargetApp, true));
                appConfig.put("hook_anti_cancel_finish", hookAntiCancelFinishMap.getOrDefault(currentTargetApp, true));
                appConfig.put("hook_vibrator", hookVibratorMap.getOrDefault(currentTargetApp, true));
                appConfig.put("crash_protect_enabled", crashProtectEnabledMap.getOrDefault(currentTargetApp, true));
                List<String> userPackages = userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
                appConfig.put("user_defined_packages", Portcf.stringListToJsonArray(userPackages));
                List<String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
                appConfig.put("excluded_packages", Portcf.stringListToJsonArray(excludedPackages));
                List<PackageConfig> pkgConfigs = packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<>());
                JSONArray pkgConfigArray = new JSONArray();
                for (PackageConfig config : pkgConfigs) {
                    JSONObject configObj = new JSONObject();
                    configObj.put("packageName", config.packageName);
                    configObj.put("statusMode", config.statusMode);
                    pkgConfigArray.put(configObj);
                }
                appConfig.put("package_configs", pkgConfigArray);
                List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
                if (patterns != null && !patterns.isEmpty()) {
                    JSONArray patternsArray = new JSONArray();
                    for (InterceptPattern pattern : patterns) {
                        JSONObject patternObj = new JSONObject();
                        patternObj.put("pattern_hash", pattern.patternHash);
                        patternObj.put("installed_packages", Portcf.stringListToJsonArray(pattern.installedPackages));
                        patternObj.put("not_installed_packages", Portcf.stringListToJsonArray(pattern.notInstalledPackages));
                        patternObj.put("user_choice", pattern.userChoice != null ? pattern.userChoice : "");
                        patternObj.put("choice_count", pattern.choiceCount);
                        patternObj.put("last_detected_time", pattern.lastDetectedTime);
                        patternObj.put("silent_intercept", pattern.silentIntercept);
                        patternObj.put("recent_choices", Portcf.stringListToJsonArray(pattern.recentChoices));
                        patternsArray.put(patternObj);
                    }
                    appConfig.put("intercept_patterns", patternsArray);
                }
                appConfig.put("global_captured_packages", Portcf.stringListToJsonArray(globalCapturedPackages));
                Map<String, String> actions = autoActionMap.get(currentTargetApp);
                if (actions != null && !actions.isEmpty()) {
                    appConfig.put("auto_actions", new JSONObject(actions));
                }
                Map<String, List<String>> records = autoChoiceRecordsMap.get(currentTargetApp);
                if (records != null && !records.isEmpty()) {
                    JSONObject recordsJson = new JSONObject();
                    for (Map.Entry<String, List<String>> entry : records.entrySet()) {
                        JSONArray arr = new JSONArray();
                        for (String s : entry.getValue()) arr.put(s);
                        recordsJson.put(entry.getKey(), arr);
                    }
                    appConfig.put("auto_records", recordsJson);
                }
                Map<String, Boolean> detailMap = permissionFakeDetailMap.get(currentTargetApp);
                if (detailMap != null && !detailMap.isEmpty()) {
                    appConfig.put("permission_fake_detail", new JSONObject(detailMap));
                }
                Map<String,Boolean> antiDetailMap = antiDetectionDetailMap.getOrDefault(currentTargetApp, new HashMap<>());
                appConfig.put("anti_detection_detail", new JSONObject(antiDetailMap));
                Map<String,Boolean> vpnDetail = vpnFakeDetailMap.getOrDefault(currentTargetApp, new HashMap<>());
                appConfig.put("vpn_fake_detail", new JSONObject(vpnDetail));
                Map<String,Boolean> shareDetail = shareFakeDetailMap.getOrDefault(currentTargetApp, getDefaultShareDetailMap());
                appConfig.put("share_fake_detail", new JSONObject(shareDetail));
                Map<String, Boolean> pauseMap = reaLogPauseMap.get(currentTargetApp);
                if (pauseMap != null && !pauseMap.isEmpty()) {
                    appConfig.put("rea_log_pause", new JSONObject(pauseMap));
                }
                configJson.put(currentTargetApp, appConfig);
                String finalJson = configJson.toString(2);

                // 原子写入：先写同目录临时文件，落盘成功后 rename 覆盖正式文件，避免出现半截损坏 JSON。
                File tmpFile = new File(filePath + ".tmp");
                FileOutputStream fos = new FileOutputStream(tmpFile);
                OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");
                writer.write(finalJson);
                writer.flush();
                writer.close();
                fos.close();
                if (!tmpFile.renameTo(configFile)) {
                    FileOutputStream fos2 = new FileOutputStream(configFile);
                    OutputStreamWriter writer2 = new OutputStreamWriter(fos2, "UTF-8");
                    writer2.write(finalJson);
                    writer2.flush();
                    writer2.close();
                    fos2.close();
                }

                saveSuccess = true;
                savedPath = filePath;
                packageStatusCache.clear();
                pathPackageCache.clear();
                mSystemCoreCache.clear();
                if (mPkgMgr != null) {
                    mPkgMgr.clearSmartFakeCache();
                }
                log("配置保存成功: " + savedPath);
                ReaLog.log("config", "配置保存成功: " + savedPath);
                break;
            } catch (Throwable t) {
                lastError = t;
                log("配置保存异常: " + t.getMessage() + " | 路径: " + filePath);
                ReaLog.log("config", "配置保存错误: " + t.getMessage() + " | 路径: " + filePath);
            }
        }

        if (!saveSuccess) {
            if (lastError != null) {
                log("配置保存最终失败(用缓存): " + lastError.getMessage());
                ReaLog.log("config", "配置错误回退仅缓存: " + lastError.getMessage());
                try {
                    DialogUi.showToastDelayed("⚠️ 配置保存失败，仅保存在内存中\n退出应用后将丢失当前配置");
                } catch (Throwable ignored) {
                }
            }
        }
    }
    
    public static Map<String, Boolean> getDefaultAntiDetectionMap() {
        Map<String, Boolean> defaultMap = new LinkedHashMap<String, Boolean>();
        for (String key : ALL_ANTI_KEYS) {
            defaultMap.put(key, true);
        }
        if (sDefaultConfigLogged.add("anti_detection")) {
            ReaLog.log("misc", "获取痕迹检测配置: " + defaultMap.size() + " 项");
        }
        return defaultMap;
    }

    public static Map<String, Boolean> getDefaultVpnDetailMap() {
        Map<String, Boolean> defaultMap = new LinkedHashMap<String, Boolean>();
        defaultMap.put("interface", true);
        defaultMap.put("cmd", true);
        defaultMap.put("capture", true);
        defaultMap.put("netfiles", true);
        defaultMap.put("net_detect", true);
        defaultMap.put("proxy", true);
        defaultMap.put("proxy_env", true);
        defaultMap.put("ssl_trust", false);
        defaultMap.put("ssl_pinning", false);
        defaultMap.put("ssl_cert", false);
        defaultMap.put("ssl_cert_hide", false);
        if (sDefaultConfigLogged.add("vpn_detail")) {
            ReaLog.log("misc", "获取网络代理配置: " + defaultMap.size() + " 项");
        }
        return defaultMap;
    }

    public static Map<String, Boolean> getDefaultShareDetailMap() {
        Map<String, Boolean> defaultMap = new LinkedHashMap<String, Boolean>();
        defaultMap.put("XW", false);
        defaultMap.put("XQ", false);
        defaultMap.put("XD", false);
        defaultMap.put("XB", false);
        defaultMap.put("aggressive", false);
        if (sDefaultConfigLogged.add("share_detail")) {
            ReaLog.log("misc", "获取虚假分享配置: " + defaultMap.size() + " 项");
        }
        return defaultMap;
    }

    public int getChoiceCountForDetected(DetectedPackages detected) {
        Set<String> currentPkgSet = new HashSet<>();
        currentPkgSet.addAll(detected.installedPackages);
        currentPkgSet.addAll(detected.notInstalledPackages);
        List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
        if (patterns != null) {
            for (InterceptPattern pattern : patterns) {
                Set<String> patternPkgSet = new HashSet<>();
                patternPkgSet.addAll(pattern.installedPackages);
                patternPkgSet.addAll(pattern.notInstalledPackages);
                if (patternPkgSet.equals(currentPkgSet)) {
                    return pattern.choiceCount;
                }
            }
        }
        return 0;
    }

    private void hookExitMethods(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.System",
                    classLoader,
                    "exit",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (bypassExitHook) {
                                return;
                            }
                            Spkill.handleAppExit(HookInit.this, "System.exit()", param);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.os.Process",
                    classLoader,
                    "killProcess",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (bypassExitHook) {
                                return;
                            }
                            Spkill.handleAppExit(HookInit.this, "Process.killProcess()", param);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "finish",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (bypassExitHook) {
                                return;
                            }
                            Activity activity = (Activity) param.thisObject;
                            if (pendingFinishSet.contains(activity)) {
                                pendingFinishSet.remove(activity);
                                return;
                            }
                            boolean isSuperBlockEnabled = superBlockExitMap.getOrDefault(currentTargetApp, false);
                            if (isSuperBlockEnabled) {
                                Boolean isBackPressTrigger = backPressFinishFlag.getOrDefault(activity, false);
                                if (isBackPressTrigger) {
                                    backPressFinishFlag.remove(activity);
                                    return;
                                }
                                Spkill.handleActivityFinish(HookInit.this, activity, param);
                            } else {
                                Spkill.handleActivityFinish(HookInit.this, activity, param);
                            }
                        }
                    }
            );

            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.Activity",
                        classLoader,
                        "finishAffinity",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                if (bypassExitHook) {
                                    return;
                                }
                                Activity activity = (Activity) param.thisObject;
                                boolean isSuperBlockEnabled = superBlockExitMap.getOrDefault(currentTargetApp, false);

                                if (isSuperBlockEnabled) {
                                    Boolean isBackPressTrigger = backPressFinishFlag.getOrDefault(activity, false);
                                    if (isBackPressTrigger) {
                                        backPressFinishFlag.remove(activity);
                                        return;
                                    }
                                    Spkill.handleActivityFinish(HookInit.this, activity, param);
                                } else {
                                    Spkill.handleActivityFinish(HookInit.this, activity, param);
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
                        "finishAndRemoveTask",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                if (bypassExitHook) {
                                    return;
                                }
                                Activity activity = (Activity) param.thisObject;
                                boolean isSuperBlockEnabled = superBlockExitMap.getOrDefault(currentTargetApp, false);

                                if (isSuperBlockEnabled) {
                                    Boolean isBackPressTrigger = backPressFinishFlag.getOrDefault(activity, false);
                                    if (isBackPressTrigger) {
                                        backPressFinishFlag.remove(activity);
                                        return;
                                    }
                                    Spkill.handleActivityFinish(HookInit.this, activity, param);
                                } else {
                                    Spkill.handleActivityFinish(HookInit.this, activity, param);
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
                        "finishAfterTransition",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                if (bypassExitHook) {
                                    return;
                                }
                                Activity activity = (Activity) param.thisObject;
                                boolean isSuperBlockEnabled = superBlockExitMap.getOrDefault(currentTargetApp, false);

                                if (isSuperBlockEnabled) {
                                    Boolean isBackPressTrigger = backPressFinishFlag.getOrDefault(activity, false);
                                    if (isBackPressTrigger) {
                                        backPressFinishFlag.remove(activity);
                                        return;
                                    }
                                    Spkill.handleActivityFinish(HookInit.this, activity, param);
                                } else {
                                    Spkill.handleActivityFinish(HookInit.this, activity, param);
                                }
                            }
                        }
                );
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            log("Hook退出方法失败: " + t.getMessage());
        }
    }

    public void maybeEnableBlockExit() {
        if (userDisabledAutoBlockMap.getOrDefault(currentTargetApp, false)) {
            ReaLog.log("exit_intercept", "用户已禁用自动开启，跳过");
            return;
        }
        if (autoBlockTriggeredThisSession) {
            return;
        }

        DetectedPackages detected = analyzeDetectedPackages();
        if (!detected.installedPackages.isEmpty() || !detected.notInstalledPackages.isEmpty()) {
            Boolean current = blockExitMap.get(currentTargetApp);
            if (current == null || !current) {
                blockExitMap.put(currentTargetApp, true);
                ReaLog.log("exit_intercept", "自动开启普通拦截");

                if (!crashProtectEnabledMap.getOrDefault(currentTargetApp, true)) {
                    crashProtectEnabledMap.put(currentTargetApp, true);
                    applyCrashProtect(true, getApplicationContext());
                    ReaLog.log("exit_intercept", "自动开启Java异常");
                }

                saveConfigToFile();
                autoBlockTriggeredThisSession = true;

                final Context ctx = getApplicationContext();
                if (ctx != null) {
                    More.runOnMainThread(new Runnable() {
                                @Override
                                public void run() {
                                    ToastUtil.show(ctx, "检测到新包名，自动开启退出拦截");
                                    log("检测到新包名，自动开启退出拦截");
                                    ReaLog.log("exit_intercept", "检测到新包名，自动开启退出拦截" + ctx);
                                }
                            });
                }
                if (foatInstance != null) {
                    foatInstance.updateFloatingTextOnly();
                }
            }
        }
    }

    public Context getApplicationContext() {
        if (sCachedAppContext != null) {
            return sCachedAppContext;
        }

        try {
            Context ctx = (Context) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", null),
                            "currentApplication"
                    );
            if (ctx != null) {
                ReaLog.log("system", "获取Context: 通过 currentApplication() 成功");
                sCachedAppContext = ctx;
                return ctx;
            }
        } catch (Throwable ignored) {
        }

        try {
            Context ctx = (Context) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.AppGlobals", null),
                            "getInitialApplication"
                    );
            if (ctx != null) {
                ReaLog.log("system", "获取Context: 通过 getInitialApplication() 成功");
                sCachedAppContext = ctx;
                return ctx;
            }
        } catch (Throwable ignored) {
        }

        try {
            Object thread = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentActivityThread"
            );
            if (thread != null) {
                Context ctx = (Context) XposedHelpers.callMethod(thread, "getApplication");
                if (ctx != null) {
                    ReaLog.log("system", "获取Context: 通过 getApplication() 成功");
                    sCachedAppContext = ctx;
                    return ctx;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> threadClass = XposedHelpers.findClass("android.app.ActivityThread", null);
            Field field = threadClass.getDeclaredField("mInitialApplication");
            field.setAccessible(true);
            Object thread = XposedHelpers.callStaticMethod(threadClass, "currentActivityThread");
            if (thread != null) {
                Context ctx = (Context) field.get(thread);
                if (ctx != null) {
                    ReaLog.log("system", "获取Context: 通过 mInitialApplication 成功");
                    sCachedAppContext = ctx;
                    return ctx;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Context ctx = (Context) Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null);
            if (ctx != null) {
                ReaLog.log("system", "获取Context: 通过反射 currentApplication() 成功");
                sCachedAppContext = ctx;
                return ctx;
            }
        } catch (Throwable ignored) {
        }

        ReaLog.log("system", "获取Context: 所有方法均失败，返回 null");
        return null;
    }

    public String getCurrentTargetApp() {
        return currentTargetApp;
    }

    public boolean isMainActivity(Activity activity) {
        if (activity == null) {
            ReaLog.log("system", "主界面: Activity为空");
            return false;
        }
        try {
            Intent intent = activity.getIntent();
            if (intent != null) {
                String action = intent.getAction();
                if (Intent.ACTION_MAIN.equals(action)) {
                    Set<String> categories = intent.getCategories();
                    if (categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)) {
                        ReaLog.log("system", "主界面: true");
                        return true;
                    }
                }
            }
        } catch (Throwable t) {
            log("判断主Activity异常: " + t.getMessage());
            ReaLog.log("system", "主界面异常: " + t.getMessage());
        }
        ReaLog.log("system", "主界面: false");
        return false;
    }

    public DetectedPackages analyzeDetectedPackages() {
        if (mPkgMgr != null) {
            return mPkgMgr.analyzeDetectedPackages();
        }
        return new DetectedPackages();
    }

    private String generatePatternHash(List<String> allPackages) {
        if (mPkgMgr != null) {
            return mPkgMgr.generatePatternHash(allPackages);
        }
        return "default_hash";
    }

    public boolean checkSilentIntercept(DetectedPackages detected) {
        try {
            List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
            if (patterns == null) {
                return false;
            }
            for (InterceptPattern pattern : patterns) {
                if (pattern.patternHash.equals(detected.patternHash) &&
                        pattern.silentIntercept && pattern.userChoice.equals("intercept")) {
                    pattern.lastDetectedTime = System.currentTimeMillis();
                    return true;
                }
            }
        } catch (Throwable t) {
            log("检查静默拦截异常: " + t.getMessage());
        }
        return false;
    }
    
    public void showSilentInterceptToast(DetectedPackages detected) {
        try {
            // 1. 获取有效的Context
            Context ctx = getCurrentActivity(); // 优先当前Activity
            if (ctx == null) {
                ctx = getApplicationContext();
            }
            if (ctx == null) {
                ReaLog.log("exit_intercept", "自动拦截: Context为空，无法显示Toast");
                return;
            }

            // 2. 计算历史选择次数
            int choiceCount = 0;
            List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
            if (patterns != null) {
                Set<String> currentPkgSet = new HashSet<>();
                currentPkgSet.addAll(detected.installedPackages);
                currentPkgSet.addAll(detected.notInstalledPackages);

                for (InterceptPattern pattern : patterns) {
                    Set<String> patternPkgSet = new HashSet<>();
                    patternPkgSet.addAll(pattern.installedPackages);
                    patternPkgSet.addAll(pattern.notInstalledPackages);
                    if (patternPkgSet.equals(currentPkgSet)) {
                        choiceCount = pattern.choiceCount;
                        break;
                    }
                }
            }

            // 3. 构建提示消息
            int totalCount = detected.installedPackages.size() + detected.notInstalledPackages.size();
            String packageNames;
            if (totalCount <= 3) {
                List<String> allPackages = new ArrayList<>();
                allPackages.addAll(detected.installedPackages);
                allPackages.addAll(detected.notInstalledPackages);
                packageNames = String.join(", ", allPackages);
            } else if (!detected.installedPackages.isEmpty()) {
                packageNames = detected.installedPackages.get(0) + " 等" + totalCount + "个应用";
            } else if (!detected.notInstalledPackages.isEmpty()) {
                packageNames = detected.notInstalledPackages.get(0) + " 等" + totalCount + "个应用";
            } else {
                packageNames = "未知应用";
            }

            final String message = "已自动拦截退出（基于 " + choiceCount + " 次历史选择）\n检测到：" + packageNames;
            ReaLog.log("exit_intercept", message);

            // 4. 在主线程显示Toast
            final Context finalCtx = ctx;
            More.runOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            ToastUtil.showUnique(finalCtx, message);
                        }
                    });
        } catch (Throwable t) {
            log("显示静默拦截Toast异常: " + t.getMessage());
            ReaLog.log("exit_intercept", "显示静默拦截Toast异常: " + t.getMessage());
        }
    }

    public void handleInterceptChoice(DetectedPackages detected, String choice, boolean interceptSuccess) {
        try {
            List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
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

            existingPattern.recentChoices.add(choice);
            if (existingPattern.recentChoices.size() > 3) {
                existingPattern.recentChoices.remove(0);
            }

            boolean threeSame = false;
            if (existingPattern.recentChoices.size() == 3) {
                String first = existingPattern.recentChoices.get(0);
                if (first.equals(existingPattern.recentChoices.get(1)) &&
                        first.equals(existingPattern.recentChoices.get(2))) {
                    threeSame = true;
                }
            }

            if (threeSame) {
                existingPattern.silentIntercept = true;
                log("启用静默，模式: " + detected.patternHash + "，选择: " + choice);
                ReaLog.log("exit_intercept", "启用静默，模式: " + detected.patternHash + "，选择: " + choice);
            } else {
                existingPattern.silentIntercept = false;
            }

            saveConfigToFile();
            log("拦截选择记录 - 模式: " + detected.patternHash +
                    ", 选择: " + choice +
                    ", 计数: " + existingPattern.choiceCount +
                    ", 静默: " + existingPattern.silentIntercept +
                    ", 最近选择: " + existingPattern.recentChoices);

            ReaLog.log("exit_intercept", "拦截选择记录 - 模式: " + detected.patternHash +
                    ", 选择: " + choice +
                    ", 计数: " + existingPattern.choiceCount +
                    ", 静默: " + existingPattern.silentIntercept +
                    ", 最近选择: " + existingPattern.recentChoices);
        } catch (Throwable t) {
            log("处理拦截选择异常: " + t.getMessage());
        }
    }

    public String getSilentAction(DetectedPackages detected) {
        List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
        if (patterns == null) return null;

        for (InterceptPattern pattern : patterns) {
            if (pattern.patternHash.equals(detected.patternHash) && pattern.silentIntercept) {
                return pattern.userChoice;
            }
        }

        for (InterceptPattern pattern : patterns) {
            if (pattern.silentIntercept && "intercept".equals(pattern.userChoice)) {
                return "intercept";
            }
        }
        return null;
    }

    public Activity getCurrentActivity() {
        if (currentResumedActivity != null &&
                !currentResumedActivity.isFinishing() &&
                !currentResumedActivity.isDestroyed()) {
            return currentResumedActivity;
        }
        return null;
    }

    public void showAddPackageDialog(final Activity activity) {
        try {
            ReaLog.log("system", "打开添加包名对话框");
            final String targetApp = currentTargetApp;
            if (!userDefinedPackagesMap.containsKey(targetApp)) {
                userDefinedPackagesMap.put(targetApp, new ArrayList<String>());
            }
            final List<String> userPackages = userDefinedPackagesMap.get(targetApp);
            final DisplayMetrics metrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
            final LinearLayout[] packagesLayoutArr = new LinearLayout[1];
            final EditText[] inputEtArr = new EditText[1];
            LinearLayout topLayout = new LinearLayout(activity);
            topLayout.setOrientation(LinearLayout.HORIZONTAL);
            topLayout.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
            final EditText inputEt = new EditText(activity);
            inputEt.setHint("请输入包名\n(如com.a.b.c)");
            inputEt.setPadding(More.spx(activity, 30), More.spx(activity, 20), More.spx(activity, 30), More.spx(activity, 20));
            inputEt.setTextColor(0xFF000000);
            inputEt.setBackgroundColor(0xFFFFFFFF);
            inputEt.setHintTextColor(0xFF999999);
            inputEt.setBackgroundTintMode(null);
            inputEt.setOutlineProvider(null);
            try {
                Class<
                        ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
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
            final Spinner typeSpinner = new Spinner(activity);
            typeSpinner.setWillNotDraw(false);
            typeSpinner.setPopupBackgroundResource(android.R.color.transparent);
            List<String> spinnerItems = new ArrayList<String>();
            spinnerItems.add("◀ [伪造]包名");
            spinnerItems.add("◀ [排除]包名");
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<
                    String>(activity, android.R.layout.simple_spinner_item, spinnerItems) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = super.getView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    tv.setTextColor(0xFF000000);
                    tv.setTextSize(14);
                    tv.setGravity(Gravity.CENTER);
                    tv.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));
                    tv.setBackgroundColor(0xFFF5F5F5);
                    tv.setBackgroundResource(android.R.drawable.list_selector_background);
                    return view;
                }

                @Override
                public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    View view = super.getDropDownView(position, convertView, parent);
                    TextView tv = (TextView) view;
                    tv.setTextColor(0xFF000000);
                    tv.setTextSize(18);
                    tv.setGravity(Gravity.CENTER_VERTICAL);
                    tv.setPadding(More.spx(activity, 20), More.spx(activity, 30), More.spx(activity, 20), More.spx(activity, 30));
                    tv.setBackgroundColor(0xFFF5F5F5);
                    tv.setBackgroundResource(android.R.drawable.list_selector_background);
                    return view;
                }
            };
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            typeSpinner.setAdapter(spinnerAdapter);
            typeSpinner.setSelection(0);
            typeSpinner.setBackgroundColor(0xFFFDFDFD);
            typeSpinner.setPopupBackgroundResource(android.R.color.white);
            try {
                Class<
                        ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
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
            typeSpinner.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 20), More.spx(activity, 20));
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
            );
            spinnerParams.leftMargin = 10;
            spinnerParams.width = More.dpInt(activity, 150);
            topLayout.addView(typeSpinner, spinnerParams);
            Button addBtn = new Button(activity);
            addBtn.setText("确定");
            addBtn.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));
            addBtn.setTextColor(0xFFFFFFFF);
            addBtn.setBackgroundTintMode(null);
            addBtn.setOutlineProvider(null);
            try {
                Class<
                        ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
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
                new float[]{25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f},
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
            adaptiveScrollView.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
            final LinearLayout packagesLayout = new LinearLayout(activity);
            packagesLayout.setOrientation(LinearLayout.VERTICAL);
            packagesLayout.setBackgroundColor(0xFFFFFFFF);
            packagesLayout.setPadding(More.spx(activity, 10), More.spx(activity, 10), More.spx(activity, 10), More.spx(activity, 10));
            adaptiveScrollView.addView(packagesLayout);
            packagesLayoutArr[0] = packagesLayout;
            TextView tipTv = new TextView(activity);
            tipTv.setText("请准确输入包名,否则数据设置失效\n批量输入:一行一个换行或中英文(逗号分号)");
            tipTv.setPadding(More.spx(activity, 30), More.spx(activity, 20), More.spx(activity, 30), More.spx(activity, 10));
            tipTv.setTextSize(12);
            tipTv.setTextColor(0xFFFF5722);
            tipTv.setTextIsSelectable(true);
            packagesLayout.addView(tipTv);
            packagesLayout.requestLayout();
            More.postOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            refreshPackagesLayout(packagesLayout, userPackages, activity);
                            packagesLayout.requestLayout();
                        }
                    }, 100);
            LinearLayout totalLayout = new LinearLayout(activity);
            totalLayout.setOrientation(LinearLayout.VERTICAL);
            totalLayout.setPadding(More.spx(activity, 20), More.spx(activity, 20), More.spx(activity, 20), More.spx(activity, 20));
            totalLayout.setBackgroundColor(0xFFFFFFFF);
            totalLayout.addView(topLayout);
            totalLayout.addView(
                    adaptiveScrollView,
                    new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                    )
            );
            final AlertDialog dialog = DialogUi.createBoundedDialog(
            activity,
            "添加自定义包名",
            "",
            new String[]{"一键清空", "保存", "取消"},
            new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            if (userPackages == null || userPackages.isEmpty()) {
                                ToastUtil.showUnique(activity, "当前无已添加包名，无需清空");
                                ReaLog.log("system", "清空包名: 当前无包名");
                                return;
                            }
                            AlertDialog confirmDialog = DialogUi.createBoundedDialog(
                            activity,
                            "确认清空",
                            "确定要清空所有手动添加的包名吗？<br><br><font color='#F44336'>此操作不可恢复！</font>",
                            new String[]{"确认清空", "取消"},
                            new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            List<String> packagesToRemove = new ArrayList<
                                                    String>(userPackages);
                                            userPackages.clear();
                                            excludedPackagesMap.put(targetApp, new ArrayList<
                                                    String>());
                                            synchronized (globalCapturedPackages) {
                                                globalCapturedPackages.removeAll(packagesToRemove);
                                            }
                                            List<
                                                    PackageConfig> configs = packageConfigMap.get(targetApp);
                                            if (configs != null) {
                                                Iterator<PackageConfig> it = configs.iterator();
                                                while (it.hasNext()) {
                                                    if (packagesToRemove.contains(it.next().packageName)) {
                                                        it.remove();
                                                    }
                                                }
                                                packageConfigMap.put(targetApp, configs);
                                            }
                                            saveConfigToFile();
                                            refreshPackagesLayout(packagesLayoutArr[
                                            0], userPackages, activity);
                                            ToastUtil.showUnique(activity, "✅ 已清空所有手动添加的包名");
                                            ReaLog.log("misc", "一键清空包名: " + packagesToRemove.size() + " 个");
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
                                ToastUtil.showUnique(activity, "当前无已添加包名，无需保存");
                                ReaLog.log("misc", "保存包名: 无包名");
                                return;
                            }
                            saveConfigToFile();
                            ToastUtil.showUnique(activity, "配置已保存");
                            ReaLog.log("misc", "保存包名列表: " + userPackages.size() + " 个");
                            dialogInterface.dismiss();
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int which) {
                            ReaLog.log("misc", "取消添加包名");
                            dialogInterface.dismiss();
                        }
                    },
            },
            totalLayout
            );
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
                        window.getDecorView().requestLayout();
                    }
                }
            });
            addBtn.setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            final String input = inputEt.getText().toString().trim();
                            if (input.isEmpty()) {
                                ToastUtil.showUnique(activity, "请输入有效包名");
                                ReaLog.log("system", "添加包名: 输入为空");
                                return;
                            }
                            Set<String> pkgSet = new HashSet<String>();
                            String[] splits = input.split("[\\n,，;；]+");
                            for (String pkg : splits) {
                                String trimmedPkg = pkg.trim();
                                if (!trimmedPkg.isEmpty() && mPkgMgr.isValidPackageName(trimmedPkg)) {
                                    pkgSet.add(trimmedPkg);
                                }
                            }
                            final List<String> pkgList = new ArrayList<String>(pkgSet);
                            if (pkgList.isEmpty()) {
                                ToastUtil.showUnique(activity, "未识别到有效包名");
                                ReaLog.log("system", "添加包名: 无有效包名");
                                return;
                            }
                            if (input.contains(" ")) {
                                AlertDialog spaceDialog = DialogUi.createBoundedDialog(
                                activity,
                                "检测到空格",
                                "",
                                new String[]{"去除空格", "强制添加"},
                                new DialogInterface.OnClickListener[]{
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int which) {
                                                List<String> processedList = new ArrayList<
                                                        String>();
                                                for (String pkg : pkgList) {
                                                    processedList.add(pkg.replaceAll(" ", ""));
                                                }
                                                Set<String> processedSet = new HashSet<
                                                        String>(processedList);
                                                processedList = new ArrayList<String>(processedSet);
                                                int selectedType = typeSpinner.getSelectedItemPosition();
                                                batchHandlePackageAdd(processedList, userPackages, packagesLayout, activity, selectedType);
                                                inputEt.setText("");
                                                ReaLog.log("system", "添加包名(去除空格): " + processedList.size() + " 个");
                                            }
                                        },
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int which) {
                                                int selectedType = typeSpinner.getSelectedItemPosition();
                                                batchHandlePackageAdd(pkgList, userPackages, packagesLayout, activity, selectedType);
                                                inputEt.setText("");
                                                ReaLog.log("misc", "添加包名(强制): " + pkgList.size() + " 个");
                                            }
                                        },
                                }
                                );
                                spaceDialog.setMessage(Html.fromHtml(
                                        "包名包含空格可能导致数据失效，是否去除所有空格？<br><br>" +
                                                "将添加 <font color='#FF5722'><b>" + pkgList.size() + "</b></font> 个包名。"
                                ));
                                spaceDialog.show();
                                return;
                            }
                            int selectedType = typeSpinner.getSelectedItemPosition();
                            batchHandlePackageAdd(pkgList, userPackages, packagesLayout, activity, selectedType);
                            ReaLog.log("misc", "添加包名: " + pkgList.size() + " 个");
                            ToastUtil.show(activity, "成功添加: " + pkgList.size() + " 个");
                            inputEt.setText("");
                        }
                    }
            );
            dialog.show();
        } catch (Throwable t) {
            log("显示添加包名对话框异常: " + t.getMessage());
        }
    }

    private void showPackageConfigDialog(final Activity activity, final String packageName) {
        if (activity == null || TextUtils.isEmpty(packageName) || packageConfigMap == null) {
            ToastUtil.showUnique(activity, "操作异常");
            ReaLog.log("misc", "独立配置: 参数无效");
            return;
        }
        if (!packageConfigMap.containsKey(currentTargetApp)) {
            ToastUtil.showUnique(activity, "无当前应用配置");
            ReaLog.log("misc", "独立配置: 无当前应用配置");
            return;
        }
        PackageConfig tempConfig = null;
        List<PackageConfig> configs = packageConfigMap.get(currentTargetApp);
        for (PackageConfig config : configs) {
            if (packageName.equals(config.packageName)) {
                tempConfig = config;
                break;
            }
        }
        if (tempConfig == null) {
            ToastUtil.showUnique(activity, "未找到该包名配置");
            ReaLog.log("misc", "未找到独立包名配置: " + packageName);
            return;
        }
        final PackageConfig targetConfig = tempConfig;
        ReaLog.log("system", "打开包名配置对话框: " + packageName + ", 当前模式: " + targetConfig.statusMode);

        String btn1 = "固定为已安装";
        String btn2 = "固定为未安装";
        String btn3 = "跟随全局配置";
        if ("installed".equals(targetConfig.statusMode)) {
            btn1 += "（当前）";
        } else if ("not_installed".equals(targetConfig.statusMode)) {
            btn2 += "（当前）";
        } else {
            btn3 += "（当前）";
        }
        StringBuilder msg = new StringBuilder();
        msg.append("包名：<font color='#FF5722'><b>").append(packageName).append("</b></font><br><br>");
        msg.append("当前状态：");
        switch (targetConfig.statusMode) {
            case "installed":
                msg.append("<font color='#4CAF50'>固定为已安装</font>");
                break;
            case "not_installed":
                msg.append("<font color='#F44336'>固定为未安装</font>");
                break;
            default:
                msg.append("<font color='#9E9E9E'>跟随全局配置</font>");
                break;
        }
        AlertDialog dialog = DialogUi.createBoundedDialog(
        activity,
        "配置包名状态",
        msg.toString(),
        new String[]{btn1, btn2, btn3},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        targetConfig.statusMode = "installed";
                        saveConfigToFile();
                        ToastUtil.showUnique(activity, "已设置为固定为已安装");
                        ReaLog.log("misc", "修改包名配置: " + packageName + " -> 固定已安装");
                        dialog.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        targetConfig.statusMode = "not_installed";
                        saveConfigToFile();
                        ToastUtil.showUnique(activity, "已设置为固定为未安装");
                        ReaLog.log("misc", "修改包名配置: " + packageName + " -> 固定未安装");
                        dialog.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        targetConfig.statusMode = "follow";
                        saveConfigToFile();
                        ToastUtil.showUnique(activity, "已设置为跟随全局配置");
                        ReaLog.log("misc", "修改包名配置: " + packageName + " -> 跟随全局");
                        dialog.dismiss();
                    }
                }
        }
        );
        if (!activity.isFinishing() && !dialog.isShowing()) {
            dialog.show();
            ReaLog.log("system", "包名配置对话框已显示: " + packageName);
        }
    }

    private void batchHandlePackageAdd(List<String> pkgList, List<
                    String> userPackages, LinearLayout packagesLayout, Activity activity, int selectedType) {
        int successCount = 0;
        List<
                String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                String>());
        List<PackageConfig> configs = packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<
                PackageConfig>());

        ReaLog.log("misc", "批量添加包名，类型: " + (selectedType == 0 ? "伪造" : "排除") + ", 数量: " + pkgList.size());

        for (String pkg : pkgList) {
            if (pkg == null || pkg.isEmpty() || !mPkgMgr.isValidPackageName(pkg)) {
                continue;
            }
            if (userPackages.contains(pkg)) {
                ReaLog.log("misc", "包名已存在，跳过: " + pkg);
                continue;
            }
            if (selectedType == 0) {
                if (excludedPackages.contains(pkg)) {
                    excludedPackages.remove(pkg);
                }
                userPackages.add(pkg);
                synchronized (globalCapturedPackages) {
                    if (!globalCapturedPackages.contains(pkg)) {
                        globalCapturedPackages.add(pkg);
                        if (mPkgMgr != null) {
                            mPkgMgr.invalidateAppDirCache();
                        }
                        packageStatusCache.clear();
                        mSystemCoreCache.clear();
                    }
                }
                boolean configExists = false;
                for (PackageConfig c : configs) {
                    if (c.packageName.equals(pkg)) {
                        configExists = true;
                        break;
                    }
                }
                if (!configExists) {
                    configs.add(new PackageConfig(pkg));
                }
                successCount++;
                ReaLog.log("misc", "添加伪造包名: " + pkg);
            } else {
                if (globalCapturedPackages.contains(pkg)) {
                    globalCapturedPackages.remove(pkg);
                    if (mPkgMgr != null) {
                        mPkgMgr.invalidateAppDirCache();
                    }
                    packageStatusCache.clear();
                    mSystemCoreCache.clear();
                }
                userPackages.add(pkg);
                if (!excludedPackages.contains(pkg)) {
                    excludedPackages.add(pkg);
                }
                Iterator<PackageConfig> it = configs.iterator();
                while (it.hasNext()) {
                    if (it.next().packageName.equals(pkg)) {
                        it.remove();
                        break;
                    }
                }
                successCount++;
                ReaLog.log("misc", "添加排除包名: " + pkg);
            }
        }

        excludedPackagesMap.put(currentTargetApp, excludedPackages);
        packageConfigMap.put(currentTargetApp, configs);

        refreshPackagesLayout(packagesLayout, userPackages, activity);
        if (mPkgMgr != null) {
            mPkgMgr.invalidateAppDirCache();
        }
        packageStatusCache.clear();
        mSystemCoreCache.clear();
        saveConfigToFile();
        ToastUtil.showUnique(activity, "处理完成\n批量添加 " + successCount + "/" + pkgList.size() + " 个包名");
        ReaLog.log("misc", "批量添加成功: " + successCount + "/" + pkgList.size() + " 个包名");
    }

    private void handlePackageAdd(String pkg, List<
                    String> userPackages, LinearLayout packagesLayout, Activity activity, int selectedType) {
        if (pkg == null || pkg.isEmpty() || !mPkgMgr.isValidPackageName(pkg)) {
            ToastUtil.showUnique(activity, "包名格式无效");
            ReaLog.log("misc", "包名格式无效: " + pkg);
            return;
        }

        if (userPackages.contains(pkg) || globalCapturedPackages.contains(pkg)) {
            ToastUtil.showUnique(activity, "重复:包名已存在\n" + pkg);
            ReaLog.log("misc", "包名重复添加: " + pkg);
            return;
        }
        List<
                String> excludedPackages = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>());
        if (selectedType == 0) {
            if (excludedPackages.contains(pkg)) {
                excludedPackages.remove(pkg);
            }
            userPackages.add(pkg);
            globalCapturedPackages.add(pkg);
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }
            packageStatusCache.clear();
            mSystemCoreCache.clear();
            List<
                    PackageConfig> configs = packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<>());
            configs.add(new PackageConfig(pkg));
            packageConfigMap.put(currentTargetApp, configs);

            ToastUtil.showUnique(activity, "已添加伪造包名：" + pkg);
            ReaLog.log("misc", "已添加伪造包名: " + pkg);
        } else {
            if (globalCapturedPackages.contains(pkg)) {
                globalCapturedPackages.remove(pkg);
                if (mPkgMgr != null) {
                    mPkgMgr.invalidateAppDirCache();
                }
                packageStatusCache.clear();
                mSystemCoreCache.clear();
            }
            userPackages.add(pkg);
            excludedPackages.add(pkg);
            excludedPackagesMap.put(currentTargetApp, excludedPackages);
            ToastUtil.showUnique(activity, "已添加排除包名：" + pkg);
            ReaLog.log("misc", "已添加排除包名: " + pkg);
        }

        refreshPackagesLayout(packagesLayout, userPackages, activity);
    }

    private void refreshPackagesLayout(final LinearLayout packagesLayout, final List<
                    String> userPackages, final Activity activity) {
        for (int i = packagesLayout.getChildCount() - 1; i >= 1; i--) {
            packagesLayout.removeViewAt(i);
        }

        List<String> fakePackages = new ArrayList<String>();
        List<String> excludePackages = new ArrayList<String>();
        List<
                String> excludedGlobal = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                String>());
        for (String pkg : userPackages) {
            if (excludedGlobal.contains(pkg)) {
                excludePackages.add(pkg);
            } else {
                fakePackages.add(pkg);
            }
        }

        if (!fakePackages.isEmpty()) {
            TextView fakeTitle = new TextView(activity);
            fakeTitle.setText("📌 伪造包名（" + fakePackages.size() + "个）");
            fakeTitle.setPadding(More.spx(activity, 30), More.spx(activity, 15), More.spx(activity, 30), More.spx(activity, 10));
            fakeTitle.setTextSize(13);
            fakeTitle.setTextColor(0xFF4CAF50);
            fakeTitle.setTextIsSelectable(true);
            packagesLayout.addView(fakeTitle);

            for (int i = 0; i < fakePackages.size(); i++) {
                final int pos = userPackages.indexOf(fakePackages.get(i));
                final String pkg = fakePackages.get(i);
                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(More.spx(activity, 30), More.spx(activity, 8), More.spx(activity, 30), More.spx(activity, 8));
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);

                TextView pkgTv = new TextView(activity);
                pkgTv.setText((i + 1) + ". [伪造] " + pkg);
                pkgTv.setPadding(More.spx(activity, 0), More.spx(activity, 5), More.spx(activity, 0), More.spx(activity, 5));
                pkgTv.setTextSize(13);
                pkgTv.setTextColor(0xFF9E9E9E);
                pkgTv.setIncludeFontPadding(false);
                pkgTv.setWillNotDraw(false);
                pkgTv.setSingleLine(false);
                pkgTv.setMaxLines(2);
                pkgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                pkgTv.setTextIsSelectable(true);
                LinearLayout.LayoutParams pkgParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                itemLayout.addView(pkgTv, pkgParams);

                Button configBtn = new Button(activity);
                configBtn.setText("配置");
                configBtn.setTextSize(11);
                configBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
                try {
                    Class<
                            ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
                    Object delBtnDrawable = gradientDrawableClass.newInstance();
                    Method setColorMethod = gradientDrawableClass.getMethod("setColor", int.class);
                    Method setCornerRadiusMethod = gradientDrawableClass.getMethod("setCornerRadius", float.class);
                    setColorMethod.invoke(delBtnDrawable, 0xAA2196F3);
                    setCornerRadiusMethod.invoke(delBtnDrawable, 25f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        configBtn.setBackground((android.graphics.drawable.Drawable) delBtnDrawable);
                    } else {
                        configBtn.setBackgroundDrawable((android.graphics.drawable.Drawable) delBtnDrawable);
                    }
                    configBtn.setTextColor(0xFFFFFFFF);
                    configBtn.setBackgroundTintMode(null);
                    configBtn.setOutlineProvider(null);
                } catch (Throwable e) {
                    android.graphics.drawable.ShapeDrawable shapeDrawable = new android.graphics.drawable.ShapeDrawable();
                    shapeDrawable.setShape(new android.graphics.drawable.shapes.RoundRectShape(
                    new float[]{25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f}, null, null));
                    shapeDrawable.getPaint().setColor(0xAA2196F3);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        configBtn.setBackground(shapeDrawable);
                    } else {
                        configBtn.setBackgroundDrawable(shapeDrawable);
                    }
                }
                configBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ReaLog.log("system", "打开包名配置: " + pkg);
                        showPackageConfigDialog(activity, pkg);
                    }
                });
                itemLayout.addView(configBtn);

                Button delBtn = new Button(activity);
                delBtn.setText("删除");
                delBtn.setTextSize(11);
                delBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
                try {
                    Class<
                            ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
                    Object delBtnDrawable = gradientDrawableClass.newInstance();
                    Method setColorMethod = gradientDrawableClass.getMethod("setColor", int.class);
                    Method setCornerRadiusMethod = gradientDrawableClass.getMethod("setCornerRadius", float.class);
                    setColorMethod.invoke(delBtnDrawable, 0xAAF44336);
                    setCornerRadiusMethod.invoke(delBtnDrawable, 25f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        delBtn.setBackground((android.graphics.drawable.Drawable) delBtnDrawable);
                    } else {
                        delBtn.setBackgroundDrawable((android.graphics.drawable.Drawable) delBtnDrawable);
                    }
                    delBtn.setTextColor(0xFFFFFFFF);
                    delBtn.setBackgroundTintMode(null);
                    delBtn.setOutlineProvider(null);
                } catch (Throwable e) {
                    android.graphics.drawable.ShapeDrawable shapeDrawable = new android.graphics.drawable.ShapeDrawable();
                    shapeDrawable.setShape(new android.graphics.drawable.shapes.RoundRectShape(
                    new float[]{25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f}, null, null));
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
                        List<
                                String> excludedGlobal = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                                String>());
                        excludedGlobal.remove(pkg);
                        excludedPackagesMap.put(currentTargetApp, excludedGlobal);
                        synchronized (globalCapturedPackages) {
                            globalCapturedPackages.remove(pkg);
                        }
                        List<
                                PackageConfig> configs = packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<
                                PackageConfig>());
                        Iterator<PackageConfig> configIt = configs.iterator();
                        while (configIt.hasNext()) {
                            if (configIt.next().packageName.equals(pkg)) {
                                configIt.remove();
                                break;
                            }
                        }
                        packageConfigMap.put(currentTargetApp, configs);
                        saveConfigToFile();
                        refreshPackagesLayout(packagesLayout, userPackages, activity);
                        ToastUtil.showUnique(activity, "已删除伪造包名：" + pkg);
                        ReaLog.log("misc", "已删除伪造包名: " + pkg);
                    }
                });
                LinearLayout.LayoutParams delParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                delParams.leftMargin = 10;
                itemLayout.addView(delBtn, delParams);
                packagesLayout.addView(itemLayout);

                if (i != fakePackages.size() - 1) {
                    View divider = new View(activity);
                    divider.setBackgroundColor(0xFFE0E0E0);
                    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                    dividerParams.topMargin = 8;
                    packagesLayout.addView(divider, dividerParams);
                }
            }
        }

        if (!fakePackages.isEmpty() && !excludePackages.isEmpty()) {
            View mainDivider = new View(activity);
            mainDivider.setBackgroundColor(0xFF999999);
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2);
            dividerParams.topMargin = 15;
            dividerParams.bottomMargin = 15;
            packagesLayout.addView(mainDivider, dividerParams);
        }

        if (!excludePackages.isEmpty()) {
            TextView excludeTitle = new TextView(activity);
            excludeTitle.setText("❌ 排除包名（" + excludePackages.size() + "个）");
            excludeTitle.setPadding(More.spx(activity, 30), More.spx(activity, 15), More.spx(activity, 30), More.spx(activity, 10));
            excludeTitle.setTextSize(13);
            excludeTitle.setTextColor(0xFFF44336);
            excludeTitle.setTextIsSelectable(true);
            packagesLayout.addView(excludeTitle);

            for (int i = 0; i < excludePackages.size(); i++) {
                final int pos = userPackages.indexOf(excludePackages.get(i));
                final String pkg = excludePackages.get(i);
                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(More.spx(activity, 30), More.spx(activity, 8), More.spx(activity, 30), More.spx(activity, 8));
                itemLayout.setGravity(Gravity.CENTER_VERTICAL);

                TextView pkgTv = new TextView(activity);
                pkgTv.setText((i + 1) + ". [排除] " + pkg);
                pkgTv.setPadding(More.spx(activity, 0), More.spx(activity, 5), More.spx(activity, 0), More.spx(activity, 5));
                pkgTv.setTextSize(13);
                pkgTv.setTextColor(0xFF9E9E9E);
                pkgTv.setIncludeFontPadding(false);
                pkgTv.setWillNotDraw(false);
                pkgTv.setSingleLine(false);
                pkgTv.setMaxLines(2);
                pkgTv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                pkgTv.setTextIsSelectable(true);
                LinearLayout.LayoutParams pkgParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                itemLayout.addView(pkgTv, pkgParams);

                Button delBtn = new Button(activity);
                delBtn.setText("删除");
                delBtn.setTextSize(11);
                delBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
                try {
                    Class<
                            ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
                    Object delBtnDrawable = gradientDrawableClass.newInstance();
                    Method setColorMethod = gradientDrawableClass.getMethod("setColor", int.class);
                    Method setCornerRadiusMethod = gradientDrawableClass.getMethod("setCornerRadius", float.class);
                    setColorMethod.invoke(delBtnDrawable, 0xAAF44336);
                    setCornerRadiusMethod.invoke(delBtnDrawable, 25f);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        delBtn.setBackground((android.graphics.drawable.Drawable) delBtnDrawable);
                    } else {
                        delBtn.setBackgroundDrawable((android.graphics.drawable.Drawable) delBtnDrawable);
                    }
                    delBtn.setTextColor(0xFFFFFFFF);
                    delBtn.setBackgroundTintMode(null);
                    delBtn.setOutlineProvider(null);
                } catch (Throwable e) {
                    android.graphics.drawable.ShapeDrawable shapeDrawable = new android.graphics.drawable.ShapeDrawable();
                    shapeDrawable.setShape(new android.graphics.drawable.shapes.RoundRectShape(
                    new float[]{25f, 25f, 25f, 25f, 25f, 25f, 25f, 25f}, null, null));
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
                        List<
                                String> excludedGlobal = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                                String>());
                        excludedGlobal.remove(pkg);
                        excludedPackagesMap.put(currentTargetApp, excludedGlobal);
                        synchronized (globalCapturedPackages) {
                            globalCapturedPackages.remove(pkg);
                        }
                        saveConfigToFile();
                        refreshPackagesLayout(packagesLayout, userPackages, activity);
                        ToastUtil.showUnique(activity, "已删除排除包名：" + pkg);
                        ReaLog.log("misc", "已删除排除包: " + pkg);
                    }
                });
                itemLayout.addView(delBtn);
                packagesLayout.addView(itemLayout);

                if (i != excludePackages.size() - 1) {
                    View divider = new View(activity);
                    divider.setBackgroundColor(0xFFE0E0E0);
                    LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1);
                    dividerParams.topMargin = 8;
                    packagesLayout.addView(divider, dividerParams);
                }
            }
        }

        if (fakePackages.isEmpty() && excludePackages.isEmpty()) {
            TextView emptyTv = new TextView(activity);
            emptyTv.setText("暂无已添加包名");
            emptyTv.setPadding(More.spx(activity, 30), More.spx(activity, 15), More.spx(activity, 30), More.spx(activity, 15));
            emptyTv.setTextColor(0xFF9E9E9E);
            emptyTv.setTextSize(12);
            emptyTv.setTextIsSelectable(true);
            packagesLayout.addView(emptyTv);
        }
        ReaLog.log("system", "包名列表刷新完成, 伪造: " + fakePackages.size() + ", 排除: " + excludePackages.size());
    }

    // ============================================================
    //  给新界面（半屏页面）用的入口：只是把原有清理动作公开出来，
    //  不改任何清理逻辑（第 2 条：清理配置不再弹老式对话框）。
    // ============================================================
    public void uiClearEverything(final Activity a, final View fv) {
        clearAllPackageLists(a, fv);
    }

    public void uiClearAutoCaptured(final Activity a, final View fv) {
        clearAutoCapturedPackagesOnly(a, fv);
    }

    private void showClearConfirmDialog(final Activity activity, final View floatingView) {
        ReaLog.log("system", "显示清理确认弹窗");
        final List<
                String> userPackages = userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                String>());
        StringBuilder pkgText = new StringBuilder();
        pkgText.append("检测到 <font color='#FF5722'><b>" + userPackages.size() + "</b></font> 个手动添加的包名<br>");
        for (int i = 0; i < userPackages.size(); i++) {
            pkgText.append((i + 1)).append(". ").append(userPackages.get(i)).append("<br>");
        }
        pkgText.append("<br>==============<br>")
                .append("同步清空：删除自动捕获包名+删除手动添加包名 <font color='#FF5722'><b>" + userPackages.size() + "</b></font> 个<br>")
                .append("单独留下：删除自动捕获包名,<font color='#FF5722'><b>保留</b></font>手动添加包名<br>")
                .append("<font color='#9E9E9E'><small><b>请选择操作：</b></small></font>");
        AlertDialog dialog = DialogUi.createBoundedDialog(
        activity,
        "清理包名",
        pkgText.toString(),
        new String[]{"同步清空", "单独留下(" + userPackages.size() + ")", "取消"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("misc", "用户选择同步清空");
                        clearAllPackageLists(activity, floatingView);
                        dialog.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("misc", "用户选择单独留下(" + userPackages.size() + ")个");
                        clearAutoCapturedPackagesOnly(activity, floatingView);
                        dialog.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("misc", "用户取消清理");
                        dialog.dismiss();
                    }
                },
        }
        );
        dialog.show();
    }

    private void clearAutoCapturedPackagesOnly(final Activity activity, final View floatingView) {
        try {
            List<
                    String> userPackages = userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                    String>());
            synchronized (globalCapturedPackages) {
                Iterator<String> iterator = globalCapturedPackages.iterator();
                while (iterator.hasNext()) {
                    String pkg = iterator.next();
                    if (!userPackages.contains(pkg)) {
                        iterator.remove();
                        ReaLog.log("misc", "移除自动捕获包: " + pkg);
                    }
                }
            }
            appCapturedPackages.clear();
            List<
                    PackageConfig> configs = packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<
                    PackageConfig>());
            Iterator<PackageConfig> configIterator = configs.iterator();
            while (configIterator.hasNext()) {
                PackageConfig config = configIterator.next();
                if (!userPackages.contains(config.packageName)) {
                    configIterator.remove();
                    ReaLog.log("misc", "移除自动捕获包配置: " + config.packageName);
                }
            }
            packageConfigMap.put(currentTargetApp, configs);
            List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
            if (patterns != null) {
                for (InterceptPattern pattern : patterns) {
                    Iterator<String> installedIt = pattern.installedPackages.iterator();
                    while (installedIt.hasNext()) {
                        String pkg = installedIt.next();
                        if (!userPackages.contains(pkg)) {
                            installedIt.remove();
                        }
                    }
                    Iterator<String> notInstalledIt = pattern.notInstalledPackages.iterator();
                    while (notInstalledIt.hasNext()) {
                        String pkg = notInstalledIt.next();
                        if (!userPackages.contains(pkg)) {
                            notInstalledIt.remove();
                        }
                    }
                }
            }
            clearAutoCapturedCache(userPackages);
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }
            packageStatusCache.clear();
            mSystemCoreCache.clear();
            saveConfigToFile();
            showRefreshConfirmDialog(activity, null);
            ToastUtil.showUnique(activity, "✅ 仅清理自动捕获的包，保留手动添加的包");
            ReaLog.log("misc", "仅清理自动捕获包完成");
        } catch (Throwable t) {
            log("仅清理自动捕获包异常: " + t.getMessage());
            ToastUtil.showUnique(activity, "清理失败");
        }
    }

    private void clearAutoCapturedCache(List<String> userPackages) {
        if (mPkgMgr != null) {
            mPkgMgr.clearAutoCapturedCache(userPackages);
        }
    }
    
    
    public void showStatusSwitchDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开悬浮窗状态切换对话框");
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Boolean currentStatus = installStatusMap.get(currentTargetApp);
                    final boolean status = currentStatus != null ? currentStatus : true;
                    DetectedPackages detected = analyzeDetectedPackages();
                    // 已经迁入「手动添加」的包名不再在捕获区重复列一遍：
                    // 新界面就是这么渲染的，旧界面不过滤会导致「同一批包名两边显示不一样」（第 3 条）。
                    // 注意只是界面去重，globalCapturedPackages 一条都没动，伪造链不受影响。
                    List<String> userAll = userDefinedPackagesMap.getOrDefault(
                            currentTargetApp, new ArrayList<String>());
                    List<String> detInstalled = new ArrayList<String>();
                    List<String> detNotInstalled = new ArrayList<String>();
                    for (String pkg : detected.installedPackages) {
                        if (pkg != null && !userAll.contains(pkg)) detInstalled.add(pkg);
                    }
                    for (String pkg : detected.notInstalledPackages) {
                        if (pkg != null && !userAll.contains(pkg)) detNotInstalled.add(pkg);
                    }
                    int totalPackages = detInstalled.size() + detNotInstalled.size();

                    StringBuilder msg = new StringBuilder();
                    msg.append("当前状态: ")
                            .append(status ? "<font color='#4CAF50'>【已安装】</font>" : "<font color='#F44336'>【未安装】</font>")
                            .append("<br>");
                    if (totalPackages > 0) {
                        msg.append("捕获应用总累计：<font color='#FF5722'><b>").append(totalPackages).append("</b></font><br><br>");
                        if (!detInstalled.isEmpty()) {
                            msg.append("✅ 设置已安装的包(").append(detInstalled.size()).append("项):<br>");
                            for (String pkg : detInstalled)
                                msg.append("+ ").append(pkg).append("<br>");
                            msg.append("<br>");
                        }
                        if (!detNotInstalled.isEmpty()) {
                            msg.append("❌ 设置未安装的包(").append(detNotInstalled.size()).append("项):<br>");
                            for (String pkg : detNotInstalled)
                                msg.append("- ").append(pkg).append("<br>");
                            msg.append("<br>");
                        }
                    } else {
                        // 全部迁入手动添加之后原始捕获集合里还有东西，只是界面上不重复列，
                        // 这时不能笼统写「未检测到任何包」，否则看着像数据丢了（第 3 条）
                        int rawTotal = detected.installedPackages.size()
                                + detected.notInstalledPackages.size();
                        if (rawTotal > 0) {
                            msg.append("📊 捕获到的 ").append(rawTotal)
                                    .append(" 个包名已全部移入「手动添加」列表<br><br>");
                        } else {
                            msg.append("📊 当前应用 未检测 到任何包<br><br>");
                        }
                    }

                    List<
                            String> userPackages = userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                            String>());
                    List<
                            String> excluded = excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                            String>());
                    List<String> fakeList = new ArrayList<String>();
                    for (String pkg : userPackages) if (!excluded.contains(pkg)) fakeList.add(pkg);
                    if (!fakeList.isEmpty()) {
                        msg.append("<font color='#4CAF50'>❣️ 手动添加的包名(").append(fakeList.size()).append("项):</font><br>");
                        for (String pkg : fakeList) {
                            String desc = "";
                            for (PackageConfig cfg : packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<
                                    PackageConfig>())) {
                                if (cfg.packageName.equals(pkg)) {
                                    if ("installed".equals(cfg.statusMode)) desc = "（固定已安装）";
                                    else if ("not_installed".equals(cfg.statusMode))
                                        desc = "（固定未安装）";
                                    break;
                                }
                            }
                            msg.append("☆ ").append(pkg).append("<font color='#9E9E9E' size='2'>").append(desc).append("</font><br>");
                        }
                        msg.append("<br>");
                    }
                    if (!excluded.isEmpty()) {
                        msg.append("<font color='#F44336'>❌ 手动添加的排除包名(").append(excluded.size()).append("项):</font><br>");
                        for (String pkg : excluded) msg.append("☆ ").append(pkg).append("<br>");
                        msg.append("<br>");
                    }
                    msg.append("<font color='#9E9E9E'><small><b>请选择切换/进入设置：</b></small></font>");

                    AlertDialog dialog = DialogUi.createBoundedDialog(activity, "包名列表", msg.toString(),
                    new String[]{"切换为已安装", "切换为未安装", "更多配置设置"},
                    new DialogInterface.OnClickListener[]{
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    if (!status) {
                                        ReaLog.log("misc", "用户切换为已安装");
                                        handleStatusSwitch(activity, floatingView, true);
                                    } else {
                                        ToastUtil.showUnique(activity, "当前已是已安装状态");
                                        ReaLog.log("misc", "已是已安装状态，无需切换");
                                    }
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    if (status) {
                                        ReaLog.log("misc", "用户切换为未安装");
                                        handleStatusSwitch(activity, floatingView, false);
                                    } else {
                                        ToastUtil.showUnique(activity, "当前已是未安装状态");
                                        ReaLog.log("misc", "已是未安装状态，无需切换");
                                    }
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    d.dismiss();
                                    ReaLog.log("system", "打开更多配置设置");
                                    if (!com.install.appinstall.xl.ui.UiKit.useNewUi(activity)
                                            || !com.install.appinstall.xl.ui.UiPages.open(activity, floatingView)) {
                                        DialogUi.showMoreFunctionsDialog(activity, floatingView);
                                    }
                                }
                            }
                    });
                    statusSwitchDialog = dialog;
                    dialog.show();
                } catch (Throwable t) {
                    log("显示状态切换对话框异常: " + t.getMessage());
                    showFallbackDialog(activity, floatingView, true);
                }
            }
        });
    }

    private void showFallbackDialog(final Activity activity, final TextView floatingView, final boolean status) {
        Activity validActivity = activity;
        if (validActivity == null || validActivity.isFinishing() ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && validActivity.isDestroyed())) {
            if (ReaLog.sHookInit != null) {
                Activity current = ReaLog.sHookInit.getCurrentActivity();
                if (current != null && !current.isFinishing() &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !current.isDestroyed())) {
                    validActivity = current;
                }
            }
            if (validActivity == null) {
                Context ctx = ReaLog.sHookInit != null ? ReaLog.sHookInit.getApplicationContext() : null;
                if (ctx != null) ToastUtil.showUnique(ctx, "Activity 已失效，无法显示备用对话框");
                return;
            }
        }
        final Activity finalActivity = validActivity;

        if (finalActivity == null || finalActivity.isFinishing()) return;
        AlertDialog dlg = new AlertDialog.Builder(finalActivity, DialogUi.getDialogThemeResId())
                .setTitle("切换状态")
                .setItems(new String[]{"切换为已安装", "切换为未安装", "刷新配置"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        boolean current = installStatusMap.getOrDefault(currentTargetApp, true);
                        switch (which) {
                            case 0:
                                if (!current) {
                                    ReaLog.log("system", "备用对话框: 切换为已安装");
                                    handleStatusSwitch(finalActivity, floatingView, true);
                                } else {
                                    ReaLog.log("system", "备用对话框: 当前已是已安装，无需切换");
                                }
                                break;
                            case 1:
                                if (current) {
                                    ReaLog.log("system", "备用对话框: 切换为未安装");
                                    handleStatusSwitch(finalActivity, floatingView, false);
                                } else {
                                    ReaLog.log("system", "备用对话框: 当前已是未安装，无需切换");
                                }
                                break;
                            case 2:
                                ReaLog.log("system", "备用对话框: 刷新配置");
                                refreshApplication(finalActivity);
                                break;
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("system", "备用对话框: 取消");
                        dialog.dismiss();
                    }
                })
                .create();
        More.markOurs(dlg);
        dlg.show();
        ReaLog.log("system", "备用对话框已显示");
    }
    
    
    public void reHookPermissionMethods(ClassLoader classLoader) {
        if (mPrsprn != null) {
            mPrsprn.reHookPermissionMethods(classLoader);
        }
        Context context = getApplicationContext();
        if (context != null) {
            clearPermissionCache(context);
            sendPermissionChangeBroadcast(context);
        }
        ReaLog.log("permission", "权限防护：重载权限方法完成");
    }

    // 公开入口：配置导入等场景复用，免重启即时生效
    public void applyPermissionConfigChanges(Activity activity) {
        if (activity == null) return;
        reHookPermissionMethods(activity.getClassLoader());
        clearPermissionCache(activity);
        sendPermissionChangeBroadcast(activity);
    }

    // 全功能配置导入变更统一生效入口
    public void applyAllConfigChanges(Activity activity) {
        if (activity == null) return;
        applyPermissionConfigChanges(activity);
        applyDebugFeatureChanges(activity);
        ReaLog.log("config", "全功能配置变更已应用（免重启）");
    }

    // 补挂：与 doHook 共用同一份门控挂载清单（BuHook 仓库，单一数据源）。
    // mountAll 可重入：已挂载项幂等跳过，未挂载且条件成立项执行挂载。
    private void applyDebugFeatureChanges(Activity activity) {
        if (activity == null) return;
        if (mPrsprn == null) mPrsprn = new Prsprn(this);
        if (mPkgMgr == null) mPkgMgr = new PkgMgr(this);
        final ClassLoader cl = activity.getClassLoader();
        mountFeatureHooks(cl, currentTargetApp, activity, isFlutterApp(cl));
        ReaLog.log("config", "免重启：重载Hook完成");
    }

    public void clearPermissionCache(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            try {
                Method clearPreferredMethod = PackageManager.class.getDeclaredMethod("clearPackagePreferredActivities", String.class);
                clearPreferredMethod.setAccessible(true);
                clearPreferredMethod.invoke(pm, currentTargetApp);
            } catch (Throwable t) {
                // 忽略
            }
            try {
                Class<?> appClass = Class.forName(context.getPackageName() + ".App");
                Field cacheField = appClass.getDeclaredField("sPermissionCache");
                cacheField.setAccessible(true);
                Object cache = cacheField.get(null);
                if (cache instanceof Map) {
                    ((Map<?, ?>) cache).clear();
                }
            } catch (Throwable t) {
                // 忽略
            }
            try {
                Class<
                        ?> permissionCheckerClass = Class.forName("androidx.core.content.PermissionChecker");
                Field sPermissionCacheField = permissionCheckerClass.getDeclaredField("sPermissionCache");
                sPermissionCacheField.setAccessible(true);
                Object cache = sPermissionCacheField.get(null);
                if (cache instanceof Map) {
                    ((Map<?, ?>) cache).clear();
                }
            } catch (Throwable t) {
                // 忽略
            }
            // 清空 Prsprn 中的权限组缓存（去除 modifiers 反射）
            try {
                Class<?> prsprnClass = Class.forName("com.install.appinstall.xl.util.Prsprn");
                Field cacheField = prsprnClass.getDeclaredField("sPermissionGroupCache");
                cacheField.setAccessible(true);
                Object cache = cacheField.get(null);
                if (cache instanceof Map) {
                    ((Map<?, ?>) cache).clear();
                    ReaLog.log("permission", "权限防护：已清空权限组缓存");
                }
            } catch (Throwable t) {
                ReaLog.log("permission", "权限防护：清空权限组缓存失败: " + t.getMessage());
            }
        } catch (Throwable t) {
            ReaLog.log("permission", "权限防护：清空权限缓存异常: " + t.getMessage());
        }
    }

    public void sendPermissionChangeBroadcast(Context context) {
        try {
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("android.intent.action.PACKAGE_PERMISSION_CHANGED");
            broadcastIntent.setData(Uri.parse("package:" + currentTargetApp));
            broadcastIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
            context.sendBroadcast(broadcastIntent);
            Intent customIntent = new Intent();
            customIntent.setAction("com.install.appinstall.xl.PERMISSION_CHANGED");
            customIntent.putExtra("packageName", currentTargetApp);
            context.sendBroadcast(customIntent);
        } catch (Throwable t) {
            log("发送权限广播异常: " + t.getMessage());
        }
    }

    public void showPermissionFakeRefreshPrompt(final Activity activity, final boolean enabled) {
        ReaLog.log("system", "权限防护: " + (enabled ? "已开启" : "已关闭"));
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            String title = enabled ? "权限防护已开启" : "权限防护已关闭";
            String message = enabled
                    ? "✅ <font color='#4CAF50'>权限防护功能已开启</font><br><br>" +
                            "功能效果：<br>" +
                            "• 勾选的权限时返回虚假已授权<br>" +
                            "• 部分应用可能需要重启或无效<br><br>" +
                            "是否立即刷新应用使设置生效？"
                    : "❌ <font color='#F44336'>权限防护功能已关闭</font><br><br>" +
                            "功能效果：<br>" +
                            "• 取消勾选应用权限时返回真实状态<br>" +
                            "• (比如当前应用没有授权,应用能检测到)<br><br>" +
                            "• 部分应用可能因此拒绝运行或无效<br>" +
                            "是否立即刷新应用使设置生效？";
            AlertDialog dialog = DialogUi.createBoundedDialog(
            activity,
            title,
            message,
            new String[]{"立即刷新", "稍后"},
            new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReaLog.log("misc", "用户选择立即刷新(权限)");
                            refreshApplication(activity);
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReaLog.log("misc", "用户选择稍后刷新(权限)");
                            dialog.dismiss();
                        }
                    }
            }
            );
            dialog.show();
        } catch (Throwable t) {
            log("显示权限防护刷新提示异常: " + t.getMessage());
        }
    }
    
    // 权限对话框列表
    private List<String> getScopePermissions(List<String> allPermissions, boolean defaultOnly) {
        if (!defaultOnly) return allPermissions;
        List<String> scope = new ArrayList<String>();
        for (String p : allPermissions) {
            if (Arrays.asList(DETECTION_PERMISSIONS).contains(p)) scope.add(p);
        }
        return scope;
    }

    public List<String> getAppDeclaredPermissions() {
        List<String> permList = new ArrayList<>();
        try {
            Context context = (Context) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", null),
                            "currentApplication"
                    );
            if (context == null) return permList;
            PackageManager pm = context.getPackageManager();
            PackageInfo pkgInfo = pm.getPackageInfo(currentTargetApp, PackageManager.GET_PERMISSIONS);
            if (pkgInfo.requestedPermissions != null) {
                permList.addAll(Arrays.asList(pkgInfo.requestedPermissions));
            }
        } catch (Throwable t) {
            log("获取声明列表失败: " + t.getMessage());
            ReaLog.log("system", "获取声明列表失败: " + t.getMessage());
        }
        return permList;
    }
    
    //长按打开管理配置对话框
    // ============================================================
    //  新版 UI 桥接（供 com.install.appinstall.xl.ui 调用，避免暴露 private 字段）
    // ============================================================

    /** 临时隐藏悬浮窗（双击音量键恢复） */
    public void uiTempHideFloating(Activity a, View fv) {
        if (foatInstance != null) {
            foatInstance.tempHideFloatingView(a, fv);
        } else {
            ToastUtil.showUnique(a, "悬浮窗模块未初始化");
        }
    }

    /** 长期隐藏悬浮窗（三击音量键恢复） */
    public void uiHideFloating(Activity a, View fv) {
        if (foatInstance != null) {
            foatInstance.hideFloatingView(a, fv);
        } else {
            ToastUtil.showUnique(a, "悬浮窗模块未初始化");
        }
    }

    /** 刷新悬浮窗显示（功能配置变化后调用，避免悬浮窗状态显示滞后） */
    public void uiRefreshFloating(Activity a) {
        if (foatInstance != null) {
            try {
                foatInstance.updateFloatingView(a);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 恢复悬浮窗显示（隐藏后的动态恢复入口） */
    public void uiRestoreFloating(Activity a) {
        if (foatInstance == null) {
            ToastUtil.showUnique(a, "悬浮窗模块未初始化");
            return;
        }
        try {
            // 不能直接调 showFloatingView：它一进来就检查 isFloatingVisibleForCurrentApp()，
            // 而隐藏时 floatingShownMap=false / permanentHiddenMap=true 恰好会让它直接 return，
            // 这就是「恢复悬浮窗显示」点了没反应的原因（第 3 条）。改为走会先复位标记的恢复入口。
            foatInstance.restoreFloatingView(a);
        } catch (Throwable t) {
            ToastUtil.showUnique(a, "恢复悬浮窗失败");
            log("❌ 恢复悬浮窗异常: " + t.getMessage());
        }
    }

    /** 清空包名检测相关缓存（新 UI 包名列表的「刷新 / 清理」按钮用） */
    public void uiRefreshDetectedPackages() {
        try {
            HookInit.detectedCache.clear();
            packageStatusCache.clear();
            mRealSystemCache.clear();
            mSystemCoreCache.clear();
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }
        } catch (Throwable t) {
            log("清理包名检测缓存异常: " + t.getMessage());
        }
    }

    /**
     * 包名增删后让状态缓存失效（旧界面加/删包名后都会做这一步）。
     * 不清的话新加的包名拿到的还是缓存里的旧状态，删掉的包名也会被缓存顶住。
     * 这里不动 detectedCache：那是「捕获到的包名」列表的快照，删了会让列表闪一下。
     */
    /**
     * 导入配置后把「运行期才生效」的开关同步一遍。
     * 这些功能判定读的是静态字段 / 已装好的 hook：只改 Map 会出现
     * 「界面显示新值、实际还是旧行为」，所以导入完必须重挂一次。
     */
    public void applyImportedRuntime(Activity a, String app) {
        try {
            Selinuxhook.setEnabled(app, selinuxFakeMap.getOrDefault(app, true));
        } catch (Throwable ignored) {
        }
        try {
            Map<String, Boolean> m = antiDetectionDetailMap.get(app);
            AntiDetection.setClassEnabled(antiBool(m, "class"));
            AntiDetection.setFileEnabled(antiBool(m, "file"));
            AntiDetection.setPmEnabled(antiBool(m, "pm"));
            AntiDetection.setProcEnabled(antiBool(m, "proc"));
            AntiDetection.setCmdEnabled(antiBool(m, "cmd"));
            AntiDetection.setStacktraceEnabled(antiBool(m, "stacktrace"));
            AntiDetection.setRootEnabled(antiBool(m, "root"));
            AntiDetection.setAdbEnabled(antiBool(m, "adb"));
            AntiDetection.setDevEnabled(antiBool(m, "dev"));
            AntiDetection.setUsbEnabled(antiBool(m, "usb"));
            AntiDetection.reset();
        } catch (Throwable ignored) {
        }
        try {
            applyCrashProtect(crashProtectEnabledMap.getOrDefault(app, true), a);
        } catch (Throwable ignored) {
        }
        try {
            if (a != null) {
                reHookPermissionMethods(a.getClassLoader());
                clearPermissionCache(a);
                sendPermissionChangeBroadcast(a);
            }
        } catch (Throwable ignored) {
        }
        try {
            ReaLog.log("config", "导入后已同步运行时开关: " + app);
        } catch (Throwable ignored) {
        }
    }

    /** 痕迹隐藏子项取值：Map 里没有 = 默认开启（与界面默认一致） */
    private static boolean antiBool(Map<String, Boolean> m, String key) {
        if (m == null) return true;
        Boolean b = m.get(key);
        return b == null || b.booleanValue();
    }

    /**
     * 真正删除一条「捕获到的包名」：从静态捕获集合里移掉。
     * 界面上的捕获区就是用 globalCapturedPackages 分析出来的，所以只能删它，
     * 走「清理配置」是另一回事（那个是整片清空）。
     */
    public boolean removeCapturedPackage(String app, String pkg) {
        boolean removed = false;
        if (pkg == null) return false;
        try {
            if (globalCapturedPackages.remove(pkg)) removed = true;
        } catch (Throwable ignored) {
        }
        try {
            if (appCapturedPackages.remove(pkg)) removed = true;
        } catch (Throwable ignored) {
        }
        if (removed) {
            try {
                invalidatePackageCaches();
            } catch (Throwable ignored) {
            }
            try {
                ReaLog.log("config", "删除捕获记录: " + pkg);
            } catch (Throwable ignored) {
            }
        }
        return removed;
    }

    public void invalidatePackageCaches() {
        try {
            packageStatusCache.clear();
            mSystemCoreCache.clear();
            mRealSystemCache.clear();
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }
        } catch (Throwable t) {
            log("清包名状态缓存异常: " + t.getMessage());
        }
    }

    // ============================================================
    //  恢复初始状态（第 12 轮）：注册表 + 遍历执行
    // ============================================================

    /** 单条恢复动作：app 为当前目标应用（静态注册表不能引用 currentTargetApp） */
    public interface ResetAction {
        void perform(String app);
    }

    /** 一项可恢复的配置：target 是 per-app 表（Map）或全局集合（Set），null 表示只靠 extra */
    public static class ResetEntry {
        public final String id;
        public final String func;          // 功能分组名
        public final String label;         // 行标题
        public final boolean isList;       // true=列表/明细类，false=开关类
        public final boolean defaultOn;    // 开关类默认值（列表类忽略）
        public final Object target;        // per-app Map 或全局 Set
        public final ResetAction extra;    // map.remove 之外的兜底动作
        public final String defaultDesc;   // 默认值文案

        public ResetEntry(String id, String func, String label, boolean isList,
                boolean defaultOn, Object target, ResetAction extra, String defaultDesc) {
            this.id = id;
            this.func = func;
            this.label = label;
            this.isList = isList;
            this.defaultOn = defaultOn;
            this.target = target;
            this.extra = extra;
            this.defaultDesc = defaultDesc;
        }

        /**
         * 明细类的出厂默认（按 id 派发到工厂方法，懒加载）：
         * 不能在 RESET_ENTRIES 类初始化期间直接调用工厂（会提前触发日志组件初始化）。
         */
        public java.util.Map<String, Boolean> defaultMap() {
            if ("vpn_fake_detail".equals(id)) return getDefaultVpnDetailMap();
            if ("anti_detection_detail".equals(id)) return getDefaultAntiDetectionMap();
            if ("share_fake_detail".equals(id)) return getDefaultShareDetailMap();
            return null;
        }

        /** 该项当前是否偏离默认（用于统计「N 项偏离默认值」与默认勾选） */
        public boolean isNonDefault(String app) {
            if (target instanceof Set) {
                return !((Set) target).isEmpty();
            }
            Object cur = null;
            if (target instanceof Map) {
                cur = ((Map) target).get(app);
            }
            // 明细类：与出厂默认逐项比较，而不是简单判空
            java.util.Map<String, Boolean> dm = defaultMap();
            if (dm != null) {
                if (!(cur instanceof Map)) return false;
                return detailDiff((Map) cur, dm) != 0;
            }
            if (isList) {
                if (cur instanceof List) return !((List) cur).isEmpty();
                if (cur instanceof Map) return !((Map) cur).isEmpty();
                return false;
            }
            if (cur == null) return false;
            if (cur instanceof Boolean) {
                return ((Boolean) cur).booleanValue() != defaultOn;
            }
            // 开关类出现的非布尔记录（如悬浮窗坐标）也视为已修改
            return !isList;
        }

        /** 当前值文案（列表类给条数，开关类给开/关） */
        public String currentDesc(String app) {
            Object cur = null;
            if (target instanceof Map) {
                cur = ((Map) target).get(app);
            }
            java.util.Map<String, Boolean> dm2 = defaultMap();
            if (dm2 != null) {
                if (cur == null || !(cur instanceof Map)) return "默认配置";
                int diff = detailDiff((Map) cur, dm2);
                return diff == 0 ? "默认配置" : (diff + " 项已改");
            }
            if (target instanceof Set) {
                int n = ((Set) target).size();
                return n > 0 ? (n + " 项") : "空";
            }
            if (isList) {
                int n = 0;
                if (cur instanceof List) n = ((List) cur).size();
                else if (cur instanceof Map) n = ((Map) cur).size();
                return n > 0 ? (n + " 项") : "空";
            }
            if (cur instanceof Boolean) {
                return ((Boolean) cur).booleanValue() ? "开" : "关";
            }
            return isList ? "非空" : "已设置";
        }

        /**
         * 该项下的子项清单（仅列表类有意义）：供三级页面逐项剔除。
         * 列表 -> 每个元素；明细 Map -> 每个 key；全局 Set -> 每个元素。
         */
        public java.util.List<String> currentItems(String app) {
            java.util.List<String> out = new java.util.ArrayList<String>();
            if (target == null) return out;
            Object base;
            if (target instanceof java.util.Map) {
                base = ((java.util.Map) target).get(app);
            } else {
                base = target;
            }
            if (base instanceof java.util.List) {
                java.util.List lst = (java.util.List) base;
                for (int i = 0; i < lst.size(); i++) {
                    out.add(readableItem(lst.get(i)));
                }
            } else if (base instanceof java.util.Map) {
                java.util.Iterator it = ((java.util.Map) base).keySet().iterator();
                while (it.hasNext()) out.add(String.valueOf(it.next()));
            } else if (base instanceof java.util.Set) {
                java.util.Iterator it = ((java.util.Set) base).iterator();
                while (it.hasNext()) out.add(readableItem(it.next()));
            }
            return out;
        }

        /** 与出厂默认明细的差异数（cur 缺/多/不同的 key 都算） */
        private static int detailDiff(Map cur, Map def) {
            int diff = 0;
            Iterator it = def.keySet().iterator();
            while (it.hasNext()) {
                Object k = it.next();
                Object d = def.get(k);
                Object v = cur.get(k);
                if (v == null ? d != null : !v.equals(d)) diff++;
            }
            Iterator it2 = cur.keySet().iterator();
            while (it2.hasNext()) {
                Object k = it2.next();
                if (!def.containsKey(k)) diff++;
            }
            return diff;
        }

        /** 列表元素转可读文案：已知结构体取名字字段，其余直接 toString */
        private String readableItem(Object item) {
            if (item instanceof InterceptPattern) {
                String h = ((InterceptPattern) item).patternHash;
                return (h != null && h.length() > 0) ? h : "(未命名方案)";
            }
            if (item instanceof PackageConfig) {
                return ((PackageConfig) item).packageName;
            }
            return String.valueOf(item);
        }
    }

    /**
     * 全部可恢复项，按功能分组（顺序 = 两级界面的分组顺序）。
     * target 只放 public static 的 per-app 表；运行时陷阱交给 extra 兜底。
     */
    public static final List<ResetEntry> RESET_ENTRIES = new ArrayList<ResetEntry>() {{
        // ---- 包名列表（5） ----
        add(new ResetEntry("install_status", "包名列表", "安装状态", false, true,
                installStatusMap, new ResetAction() {
                    public void perform(String app) {
                        // 保存流程在 installStatusMap.get(app)==null 时会拒绝整次保存
                        installStatusMap.put(app, Boolean.TRUE);
                    }
                }, "已安装"));
        add(new ResetEntry("user_defined_packages", "包名列表", "自定义包名", true, false,
                userDefinedPackagesMap, null, "空"));
        add(new ResetEntry("excluded_packages", "包名列表", "排除包名", true, false,
                excludedPackagesMap, null, "空"));
        add(new ResetEntry("package_configs", "包名列表", "包名独立配置", true, false,
                packageConfigMap, null, "空"));
        add(new ResetEntry("captured_packages", "包名列表", "已捕获包名记录", true, false,
                globalCapturedPackages, null, "空"));
        // ---- 启动拦截（3） ----
        add(new ResetEntry("launch_intercept", "启动拦截", "启动拦截", false, true,
                launchInterceptMap, null, "开"));
        add(new ResetEntry("auto_action", "启动拦截", "自动动作", true, false,
                autoActionMap, null, "空"));
        add(new ResetEntry("auto_choice_records", "启动拦截", "自动动作选择记录", true, false,
                autoChoiceRecordsMap, null, "空"));
        // ---- 权限防护（2） ----
        add(new ResetEntry("permission_fake", "权限防护", "权限伪装", false, true,
                permissionFakeMap, null, "开"));
        add(new ResetEntry("permission_fake_detail", "权限防护", "权限伪装条目", true, false,
                permissionFakeDetailMap, null, "空"));
        // ---- 拦截退出（5） ----
        add(new ResetEntry("block_exit", "拦截退出", "拦截退出", false, false,
                blockExitMap, null, "关"));
        add(new ResetEntry("super_block_exit", "拦截退出", "超级拦截退出", false, false,
                superBlockExitMap, null, "关"));
        add(new ResetEntry("user_disabled_auto_block", "拦截退出", "禁用自动拦截", false, false,
                userDisabledAutoBlockMap, null, "关"));
        add(new ResetEntry("crash_protect", "拦截退出", "崩溃保护", false, true,
                crashProtectEnabledMap, null, "开"));
        add(new ResetEntry("exit_intercept_patterns", "拦截退出", "退出拦截规则", true, false,
                interceptPatternsMap, null, "空"));
        // ---- 返回键控制（1） ----
        add(new ResetEntry("force_default_back", "返回键控制", "强制默认返回键", false, false,
                forceDefaultBackMap, null, "关"));
        // ---- 其他功能（5） ----
        add(new ResetEntry("hook_dialog_cancelable", "其他功能", "对话框强制取消", false, true,
                hookDialogCancelableMap, null, "开"));
        add(new ResetEntry("hook_window_flags", "其他功能", "屏幕限制解除", false, true,
                hookWindowFlagsMap, null, "开"));
        add(new ResetEntry("hook_anti_cancel_finish", "其他功能", "对话框强制取消(防界面关闭)", false, true,
                hookAntiCancelFinishMap, null, "开"));
        add(new ResetEntry("hook_vibrator", "其他功能", "震动控制", false, true,
                hookVibratorMap, null, "开"));
        // ---- 伪装强制（1） ----
        add(new ResetEntry("selinux_fake", "伪装强制", "SELinux 伪装", false, true,
                selinuxFakeMap, new ResetAction() {
                    public void perform(String app) {
                        // 运行时开关也要一起复位（:1150 读取处）
                        Selinuxhook.setEnabled(app, true);
                    }
                }, "开"));
        // ---- 假装分享（2） ----
        add(new ResetEntry("share_fake_detail", "假装分享", "分享伪装条目", true, false,
                shareFakeDetailMap, new ResetAction() {
                    public void perform(String app) {
                        // 与 vpn/anti 同理：只 remove 会写成 {}，恢复时填回出厂默认
                        shareFakeDetailMap.put(app, getDefaultShareDetailMap());
                    }
                }, "全部关闭"));
        add(new ResetEntry("share_error_count", "假装分享", "分享失败计数", true, false,
                null, new ResetAction() {
                    public void perform(String app) {
                        ShareHook.resetAllErrorCount();
                    }
                }, "空"));
        // ---- 网络代理（1） ----
        add(new ResetEntry("vpn_fake_detail", "网络代理", "代理伪装条目", true, false,
                vpnFakeDetailMap, new ResetAction() {
                    public void perform(String app) {
                        // 恢复 = 填回出厂默认明细；只 remove 会写成 {}，功能会失灵
                        vpnFakeDetailMap.put(app, getDefaultVpnDetailMap());
                    }
                }, "7 项开启"));
        // ---- 痕迹隐藏（1） ----
        add(new ResetEntry("anti_detection_detail", "痕迹隐藏", "痕迹隐藏条目", true, false,
                antiDetectionDetailMap, new ResetAction() {
                    public void perform(String app) {
                        antiDetectionDetailMap.put(app, getDefaultAntiDetectionMap());
                    }
                }, "全部开启"));
        // ---- 界面与状态（6） ----
        add(new ResetEntry("floating_shown", "界面与状态", "悬浮窗显示", false, true,
                floatingShownMap, null, "显示"));
        add(new ResetEntry("permanent_hidden", "界面与状态", "永久隐藏悬浮窗", false, false,
                permanentHiddenMap, null, "关"));
        add(new ResetEntry("floating_pos", "界面与状态", "悬浮窗位置", false, false,
                floatingXMap, new ResetAction() {
                    public void perform(String app) {
                        floatingXMap.remove(app);
                        floatingYMap.remove(app);
                    }
                }, "默认位置"));
        add(new ResetEntry("vendor_enabled", "界面与状态", "厂商伪装", false, false,
                vendorChoiceMap, null, "关"));
        add(new ResetEntry("rea_log_pause", "界面与状态", "日志暂停设置", true, false,
                reaLogPauseMap, null, "空"));
    }};

    /**
     * 恢复初始状态（第 12 轮第 0 条）：按 id 清回默认。空集合 = 全部。幂等。
     * 列表/明细类清空 per-app 记录，开关类移除 key（读取处 getOrDefault 回默认）。
     */
    public void uiResetToDefault(Set<String> ids) {
        boolean all = ids == null || ids.isEmpty();
        String app = currentTargetApp;
        for (int i = 0; i < RESET_ENTRIES.size(); i++) {
            ResetEntry e = RESET_ENTRIES.get(i);
            boolean sel = all || (ids != null && ids.contains(e.id));
            // 收集 "id::subkey" 形式的子项级勾选
            java.util.List<String> subs = null;
            java.util.Map<String, Boolean> dmap = e.defaultMap();
            if (!all && dmap != null && ids != null) {
                String prefix = e.id + "::";
                Iterator<String> it = ids.iterator();
                while (it.hasNext()) {
                    String id = it.next();
                    if (id.startsWith(prefix)) {
                        if (subs == null) subs = new ArrayList<String>();
                        subs.add(id.substring(prefix.length()));
                    }
                }
            }
            if (!sel && subs == null) continue;
            if (subs != null && !sel) {
                // 只恢复勾选的子项：把它们的值写回出厂默认
                try {
                    Object cur = e.target instanceof Map
                            ? ((Map) e.target).get(app) : null;
                    Map detail;
                    if (cur instanceof Map) {
                        detail = (Map) cur;
                    } else {
                        // 该应用从没记录过：从默认复制一份再改
                        detail = new java.util.LinkedHashMap<String, Boolean>(dmap);
                        ((Map) e.target).put(app, detail);
                    }
                    Iterator<String> it2 = subs.iterator();
                    while (it2.hasNext()) {
                        String sub = it2.next();
                        Object def = dmap.get(sub);
                        if (def != null) detail.put(sub, def);
                    }
                } catch (Throwable ignored) {
                }
                continue;
            }
            if (e.target instanceof Map) {
                try {
                    ((Map) e.target).remove(app);
                } catch (Throwable ignored) {
                }
            } else if (e.target instanceof Set) {
                try {
                    ((Set) e.target).clear();
                } catch (Throwable ignored) {
                }
            }
            if (e.extra != null) {
                try {
                    e.extra.perform(app);
                } catch (Throwable ignored) {
                }
            }
        }
        saveConfigToFile();
    }

    /**
     * 精细剔除（第 12 轮追加）：不整条回默认，只把列表/明细里勾选的子项移掉。
     * items 是 currentItems 给出的可读标签（作为剔除依据）。
     */
    public void uiResetListItems(String id, Set<String> items) {
        if (id == null || items == null || items.isEmpty()) return;
        String app = currentTargetApp;
        for (int i = 0; i < RESET_ENTRIES.size(); i++) {
            ResetEntry e = RESET_ENTRIES.get(i);
            if (!e.id.equals(id)) continue;
            Object base = null;
            if (e.target instanceof Map) {
                base = ((Map) e.target).get(app);
            } else if (e.target instanceof Set) {
                base = e.target;
            }
            // 明细类还没记录过：从出厂默认复制一份再剔除，剔完写回
            boolean copiedDefault = false;
            java.util.Map<String, Boolean> dmap2 = e.defaultMap();
            if (base == null && dmap2 != null) {
                base = new java.util.LinkedHashMap(dmap2);
                copiedDefault = true;
            }
            try {
                if (base instanceof List) {
                    List lst = (List) base;
                    for (int j = lst.size() - 1; j >= 0; j--) {
                        Object item = lst.get(j);
                        String label = itemLabel(item);
                        if (items.contains(label)) lst.remove(j);
                    }
                } else if (base instanceof Map) {
                    Map m = (Map) base;
                    Iterator kit = m.keySet().iterator();
                    while (kit.hasNext()) {
                        if (items.contains(String.valueOf(kit.next()))) kit.remove();
                    }
                } else if (base instanceof Set) {
                    Set set = (Set) base;
                    Iterator sit = set.iterator();
                    while (sit.hasNext()) {
                        if (items.contains(itemLabel(sit.next()))) sit.remove();
                    }
                }
            } catch (Throwable ignored) {
            }
            if (copiedDefault && e.target instanceof Map) {
                try {
                    ((Map) e.target).put(app, base);
                } catch (Throwable ignored) {
                }
            }
            // 有默认明细的条目：extra 会整体填回默认，与逐项剔除冲突，跳过
            if (e.extra != null && dmap2 == null) {
                try {
                    e.extra.perform(app);
                } catch (Throwable ignored) {
                }
            }
        }
        saveConfigToFile();
    }

    /** 子项的可读标签：必须与 ResetEntry.readableItem 完全一致，剔除才对得上 */
    private static String itemLabel(Object item) {
        if (item instanceof InterceptPattern) {
            String h = ((InterceptPattern) item).patternHash;
            return (h != null && h.length() > 0) ? h : "(未命名方案)";
        }
        if (item instanceof PackageConfig) {
            return ((PackageConfig) item).packageName;
        }
        return String.valueOf(item);
    }

    /** 恢复初始状态后：悬浮窗即时复位（第 12 轮第 1 条） */
    public void uiApplyFloatingConfig(Activity a) {
        if (foatInstance != null && a != null) {
            try {
                foatInstance.refreshFloatingState(a);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 清理配置（有自定义包名时先确认） */
    public void uiClearConfig(final Activity a, final View fv) {
        try {
            List<String> userPackages = userDefinedPackagesMap
                    .getOrDefault(currentTargetApp, new ArrayList<String>());
            if (userPackages.isEmpty()) {
                clearAllPackageLists(a, fv);
            } else {
                showClearConfirmDialog(a, fv);
            }
        } catch (Throwable t) {
            ToastUtil.showUnique(a, "清理配置失败");
            log("❌ 清理配置异常: " + t.getMessage());
        }
    }

    public void showHideDialog(final Activity activity, final View floatingView) {
        ReaLog.log("system", "打开管理配置对话框");
        try {
            LinearLayout titleLayout = new LinearLayout(activity);
            titleLayout.setOrientation(LinearLayout.HORIZONTAL);
            titleLayout.setGravity(Gravity.CENTER_VERTICAL);
            titleLayout.setPadding(More.spx(activity, 80), More.spx(activity, 20), More.spx(activity, 20), More.spx(activity, 20));

            TextView titleText = new TextView(activity);
            titleText.setText("管理配置/悬浮窗设置");
            titleText.setTextSize(18);
            titleText.setTextColor(0xFF333333);
            titleText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            titleText.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            Button permHideBtn = new Button(activity);
            permHideBtn.setText("隐藏悬浮窗(长期)");
            permHideBtn.setTextSize(12);
            permHideBtn.setPadding(More.spx(activity, 20), More.spx(activity, 20), More.spx(activity, 20), More.spx(activity, 20));
            permHideBtn.setTextColor(0xFFFFFFFF);
            try {
                Class<
                        ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
                Object gd = gradientDrawableClass.newInstance();
                Method setColor = gradientDrawableClass.getMethod("setColor", int.class);
                Method setCornerRadius = gradientDrawableClass.getMethod("setCornerRadius", float.class);
                setColor.invoke(gd, 0xAAF44336);
                setCornerRadius.invoke(gd, 25f);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    permHideBtn.setBackground((android.graphics.drawable.Drawable) gd);
                } else {
                    permHideBtn.setBackgroundDrawable((android.graphics.drawable.Drawable) gd);
                }
            } catch (Throwable e) {
                permHideBtn.setBackgroundColor(0xAAF44336);
            }
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
            int marginPx = More.dpInt(activity, 8);
            btnParams.setMargins(marginPx, marginPx, marginPx, marginPx);
            permHideBtn.setLayoutParams(btnParams);
            titleLayout.addView(titleText);
            titleLayout.addView(permHideBtn);

            String message = "【隐藏】隐藏应用内的悬浮窗显示功能<br>" +
                    "<font color='#9E9E9E'><b>· [临时] 隐藏后后通过双击音量键恢复显示</b></font><br>" +
                    "<font color='#9E9E9E'><b>· [长期] 隐藏后通过三击音量键+/-恢复显示</b></font><br>" +
                    "<font color='#F44336'><small>—————————————————</small></font><br>" +
                    "【清理配置】清理自动捕获+自定义添加的数据<br>" +
                    "<font color='#9E9E9E'><b>清理后会自动捕获新数据/自定义包名需重新添加</b></font><br><br>" +
                    "<font color='#F44336'><small><b>请选择操作：</b></small></font>";

            final AlertDialog[] dialogHolder = new AlertDialog[1];

            AlertDialog dialog = DialogUi.createBoundedDialog(
            activity,
            null,
            message,
            new String[]{"临时隐藏", "清理配置", "取消"},
            new DialogInterface.OnClickListener[]{new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            ReaLog.log("misc", "用户选择临时隐藏悬浮窗");
                            if (foatInstance != null) {
                                foatInstance.tempHideFloatingView(activity, floatingView);
                            } else {
                                ToastUtil.showUnique(activity, "悬浮窗模块未初始化");
                            }
                            d.dismiss();
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            d.dismiss();
                            ReaLog.log("misc", "用户选择清理配置");
                            List<
                                    String> userPackages = userDefinedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<
                                    String>());
                            if (userPackages.isEmpty()) {
                                clearAllPackageLists(activity, floatingView);
                            } else {
                                showClearConfirmDialog(activity, floatingView);
                            }
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            ReaLog.log("system", "关闭管理配置对话框");
                            d.dismiss();
                        }
                    }
            }
            );
            dialogHolder[0] = dialog;

            permHideBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ReaLog.log("floating", "用户选择长期隐藏悬浮窗");
                    foatInstance.hideFloatingView(activity, floatingView);
                    if (dialogHolder[0] != null && dialogHolder[0].isShowing()) {
                        dialogHolder[0].dismiss();
                    }
                }
            });

            dialog.setCustomTitle(titleLayout);
            dialog.show();
        } catch (Throwable t) {
            log("❌ 显示隐藏对话框异常: " + t.getMessage());
        }
    }
    
    //清理缓存
    private void clearAllPackageLists(final Activity activity, final View floatingView) {
        try {
            ReaLog.log("system", "打开清理所有包名列表");

            synchronized (globalCapturedPackages) {
                globalCapturedPackages.clear();
            }

            appCapturedPackages.clear();

            List<InterceptPattern> patterns = interceptPatternsMap.get(currentTargetApp);
            if (patterns != null) {
                for (InterceptPattern pattern : patterns) {
                    pattern.installedPackages.clear();
                    pattern.notInstalledPackages.clear();
                    pattern.choiceCount = 0;
                    pattern.silentIntercept = false;
                    pattern.recentChoices.clear();
                }
            }

            userDefinedPackagesMap.put(currentTargetApp, new ArrayList<String>());
            packageConfigMap.put(currentTargetApp, new ArrayList<PackageConfig>());
            excludedPackagesMap.put(currentTargetApp, new ArrayList<String>());
            clearSmartFakeCache();
            sessionChoiceMap.clear();
            pathPackageCache.clear();
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }
            packageStatusCache.clear();
            mSystemCoreCache.clear();
            detectedCache.clear();
            mRealSystemCache.clear();

            saveConfigToFile();

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String message = "<b>✅ 清理完成</b><br>" +
                                "<small>• 已清空全局包列表</small>" +
                                "<small>• 已清空当前应用包列表<br></small>" +
                                "<small>• 已清空用户自定义包名</small>" +
                                "<small>• 已清空包名独立配置<br></small>" +
                                "<small>• 已重置包名数据缓存</small>" +
                                "<small>• 拦截模式计数已重置<br></small>" +
                                "<b>• 配置已保存</b>";
                        ToastUtil.showUnique(activity, message, true);
                        if (floatingView != null) {
                            showRefreshConfirmDialog(activity, null);
                        }
                        ReaLog.log("misc", "数据缓存清理完成");
                    } catch (Throwable t) {
                        ToastUtil.showUnique(activity, "✅ 清理完成");
                    }
                }
            });

            log("✅ 清理所有包列表完成");
        } catch (Throwable t) {
            log("清理包列表异常: " + t.getMessage());
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ToastUtil.showUnique(activity, "❌清理失败");
                }
            });
        }
    }

    private void clearSmartFakeCache() {
        if (mPkgMgr != null) {
            mPkgMgr.clearSmartFakeCache();
        }
    }
    
    //状态切换开关
    private void handleStatusSwitch(Activity activity, TextView floatingView, boolean newStatus) {
        try {
            installStatusMap.put(currentTargetApp, newStatus);

            detectedCache.clear();
            packageStatusCache.clear();
            mSystemCoreCache.clear();
            mRealSystemCache.clear();
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }
            saveConfigImmediate();

            if (floatingView != null) {
                String statusText = newStatus ? "已安装" : "未安装";
                boolean isBlockingExit = blockExitMap.getOrDefault(currentTargetApp, false);
                String blockText = isBlockingExit ? "[拦截]" : "";
                floatingView.setText("安装防护(" + statusText + ")" + blockText);
                floatingView.setBackgroundColor(newStatus ? 0xAA4CAF50 : 0xAAF44336);
            }

            ToastUtil.showUnique(activity, "切换为" + (newStatus ? "已安装" : "未安装") + "状态");
            showRefreshPrompt(activity, "切换成功");
            log("状态切换: " + (newStatus ? "已安装" : "未安装"));
            ReaLog.log("system", "状态切换: " + (newStatus ? "已安装" : "未安装"));
        } catch (Throwable t) {
            log("处理状态切换异常: " + t.getMessage());
            ToastUtil.showUnique(activity, "切换失败");
        }
    }
    
    //刷新提示
    private void showRefreshPrompt(final Activity activity, final String message) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        try {
            AlertDialog dialog = DialogUi.createBoundedDialog(activity, "状态切换成功",
            message + "<br><br>是否立即刷新应用使状态生效？",
            new String[]{"立即刷新", "稍后"},
            new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReaLog.log("system", "用户选择立即刷新(状态切换)");
                            refreshApplication(activity);
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReaLog.log("system", "用户选择稍后刷新(状态切换)");
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

    private void showRefreshConfirmDialog(final Activity activity, final String customMessage) {
        if (activity == null || activity.isFinishing()) {
            ReaLog.log("system", "刷新对话框: Activity无效");
            return;
        }
        try {
            String message;
            if (customMessage != null && !customMessage.isEmpty()) {
                message = customMessage + "<br><br>是否立即刷新应用使配置生效？";
            } else {
                message = "这将重新加载配置文件并更新显示，<br>" +
                        "使状态切换立即生效。<br><br>" +
                        "确定要刷新吗？";
            }
            AlertDialog dialog = DialogUi.createBoundedDialog(activity, "刷新应用", message,
            new String[]{"立即刷新", "取消"},
            new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReaLog.log("system", "用户选择立即刷新");
                            refreshApplication(activity);
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ReaLog.log("system", "用户取消刷新");
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
            detectedCache.clear();
            packageStatusCache.clear();
            mSystemCoreCache.clear();
            mRealSystemCache.clear();
            if (mPkgMgr != null) {
                mPkgMgr.invalidateAppDirCache();
            }

            installStatusMap.remove(currentTargetApp);
            floatingShownMap.remove(currentTargetApp);
            loadConfigFromFile();

            if (foatInstance != null) {
                foatInstance.updateFloatingView(activity);
            }

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
                        ToastUtil.showUnique(activity, toastMessage, true);
                        ReaLog.log("system", "刷新成功,当前状态：" + statusText);
                    } catch (Throwable e) {
                        Boolean currentStatus = installStatusMap.get(currentTargetApp);
                        String statusText = currentStatus != null ? (currentStatus ? "已安装" : "未安装") : "未知";
                        ToastUtil.showUnique(activity, "刷新完成\n当前状态: " + statusText);
                    }
                }
            });
        } catch (Throwable t) {
            log("刷新应用异常: " + t.getMessage());
            ReaLog.log("system", "刷新应用异常: " + t.getMessage());
            activity.runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            ToastUtil.showUnique(activity, "刷新失败");
                        }
                    }
            );
        }
    }

    public void updateFloatingView(final Activity activity) {
        if (foatInstance != null) {
            foatInstance.updateFloatingView(activity);
        }
    }

    public void applyCrashProtect(boolean enable, Context context) {
        ReaLog.log("misc", "应用Java异常: " + (enable ? "启用" : "禁用"));
        if (!enable) {
            Thread.setDefaultUncaughtExceptionHandler(null);
            return;
        }

        Context appCtx = context != null ? context.getApplicationContext() : getApplicationContext();
        if (appCtx == null && sCachedAppContext != null) appCtx = sCachedAppContext;
        if (appCtx != null) sCachedAppContext = appCtx;

        Intent launchIntent = null;
        if (appCtx != null) {
            try {
                launchIntent = appCtx.getPackageManager().getLaunchIntentForPackage(appCtx.getPackageName());
                if (launchIntent != null) sCachedLaunchIntent = launchIntent;
            } catch (Throwable ignored) {
            }
        }
        if (launchIntent == null && sCachedLaunchIntent != null) {
            launchIntent = sCachedLaunchIntent;
        }

        final Context finalCtx = appCtx;
        final Intent finalLaunchIntent = launchIntent;
        final boolean canRestart = (finalCtx != null && finalLaunchIntent != null);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                if (isHandlingCrash) return;
                isHandlingCrash = true;

                try {
                    String msg = "捕获崩溃: " + e.getClass().getName() + " - " + e.getMessage();
                    ReaLog.log("exit_intercept", msg);
                } catch (Throwable ignored) {
                }

                try {
                    android.util.Log.e("CrashProtect", "崩溃: " + e.getClass().getName());
                    if (e.getMessage() != null) {
                        android.util.Log.e("CrashProtect", "原因: " + e.getMessage());
                    }
                } catch (Throwable ignored) {
                }

                long now = System.currentTimeMillis();
                if (now - sLastCrashTime > 60_000) {
                    sCrashCount = 0;
                }
                sCrashCount++;
                sLastCrashTime = now;

                if (sCrashCount >= 3) {
                    android.util.Log.e("CrashProtect", "熔断触发（连续3次），交给系统处理");
                    try {
                        doSaveConfigToFile();
                    } catch (Throwable ignored) {
                    }
                    Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
                    if (defaultHandler != null && defaultHandler != this) {
                        defaultHandler.uncaughtException(t, e);
                    } else {
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }
                    return;
                }

                try {
                    doSaveConfigToFile();
                } catch (Throwable ignored) {
                }

                if (canRestart && finalCtx != null && finalLaunchIntent != null) {
                    try {
                        Thread.sleep(300);
                        finalLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        finalCtx.startActivity(finalLaunchIntent);
                    } catch (Throwable ignored) {
                        android.util.Log.e("CrashProtect", "重启失败");
                    } finally {
                        Process.killProcess(Process.myPid());
                        System.exit(0);
                    }
                } else {
                    android.util.Log.e("CrashProtect", "无法重启，静默退出");
                    Process.killProcess(Process.myPid());
                    System.exit(0);
                }
            }
        });
    }

    private void hookIndirectExitMethods(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.reflect.Method",
                    classLoader,
                    "invoke",
                    Object.class,
                    Object[].class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Method method = (Method) param.thisObject;
                            if (method.getDeclaringClass().getName().equals("java.lang.System") &&
                                    method.getName().equals("exit")) {
                                param.setResult(null);
                                param.setThrowable(null);
                                Spkill.handleAppExit(HookInit.this, "反射调用 System.exit()", param);
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.lang.Runtime",
                    classLoader,
                    "exit",
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(null);
                            param.setThrowable(null);
                            Spkill.handleAppExit(HookInit.this, "Runtime.exit()", param);
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.lang.System",
                    classLoader,
                    "loadLibrary",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String libName = (String) param.args[0];
                            if (libName.contains("native") ||
                                    libName.contains("exit") ||
                                    libName.contains("kill")) {}
                        }
                    }
            );
        } catch (Throwable t) {
        }
    }

    private void hookGlobalExitSources(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.Handler",
                    classLoader,
                    "post",
                    Runnable.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            boolean blockExit = blockExitMap.getOrDefault(currentTargetApp, false);
                            boolean superBlock = superBlockExitMap.getOrDefault(currentTargetApp, false);
                            if (!blockExit && !superBlock) {
                                return;
                            }
                            Runnable runnable = (Runnable) param.args[0];
                            String runnableStr = runnable.toString().toLowerCase();
                            if (runnableStr.contains("exit") || runnableStr.contains("kill") || runnableStr.contains("finish")) {
                                DetectedPackages detected = analyzeDetectedPackages();
                                if (!detected.installedPackages.isEmpty() || !detected.notInstalledPackages.isEmpty()) {
                                    param.setResult(false);
                                    ReaLog.log("exit_intercept", "拦截退出任务: " + runnableStr);
                                }
                            }
                        }
                    }
            );

            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.Activity",
                        classLoader,
                        "onResume",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                Activity activity = (Activity) param.thisObject;
                                ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
                                if (am != null) {
                                    try {
                                        List<
                                                ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                                        if (!tasks.isEmpty() && tasks.get(0).topActivity.getPackageName().equals(currentTargetApp)) {
                                            ReaLog.log("system", "检测到目标应用: " + tasks.get(0).topActivity.getPackageName()
                                                    + ", 当前页面: " + activity.getClass().getName());
                                        }
                                    } catch (SecurityException e) {
                                        ReaLog.log("system", "记录获取任务列表无权限");
                                    }
                                }
                            }
                        }
                );
            } catch (Throwable t) {
            }

            try {
                XposedHelpers.findAndHookMethod(
                        "java.lang.Thread",
                        classLoader,
                        "run",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                Thread thread = (Thread) param.thisObject;
                                String threadName = thread.getName().toLowerCase();
                                if (threadName.contains("check") ||
                                        threadName.contains("detect") ||
                                        threadName.contains("exit")) {
                                    ReaLog.log("exit_intercept", "检测到可疑线程: " + threadName);
                                }
                            }
                        }
                );
            } catch (Throwable t) {
            }

        } catch (Throwable t) {
            log("退出拦截初始化失败: " + t.getMessage());
            ReaLog.log("exit_intercept", "退出拦截初始化失败: " + t.getMessage());
        }
    }

    private void hookRunnableSystemExit(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.Runnable",
                    classLoader,
                    "run",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(
                                final XC_MethodHook.MethodHookParam param)
                                throws Throwable {
                            boolean blockExit = blockExitMap.getOrDefault(currentTargetApp, false);
                            boolean superBlock = superBlockExitMap.getOrDefault(currentTargetApp, false);
                            if (!blockExit && !superBlock) {
                                return;
                            }

                            DetectedPackages detected = analyzeDetectedPackages();
                            if (detected.installedPackages.isEmpty() &&
                                    detected.notInstalledPackages.isEmpty()) {
                                return;
                            }

                            ReaLog.log("exit_intercept", "检测到 System.exit 调用，尝试拦截");

                            XC_MethodHook exitHook = new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(
                                        XC_MethodHook.MethodHookParam exitParam)
                                        throws Throwable {
                                    exitParam.setResult(null);
                                    exitParam.setThrowable(null);
                                    ReaLog.log("exit_intercept", "调用 System.exit() 已拦截");
                                    More.runOnMainThread(
                                                    new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            Activity activity = getCurrentActivity();
                                                            if (activity != null) {
                                                                ToastUtil.showUnique(activity, "已拦截异步退出");
                                                            }
                                                        }
                                                    }
                                            );
                                }
                            };

                            Method exitMethod = Class.forName("java.lang.System").getMethod(
                                    "exit",
                                    int.class
                            );
                            XposedBridge.hookMethod(exitMethod, exitHook);
                            try {
                                Runnable originalRunnable = (Runnable) param.thisObject;
                                originalRunnable.run();
                                ReaLog.log("exit_intercept", "拦截exit 执行完成");
                            } catch (Throwable t) {
                            } finally {
                                XposedBridge.unhookMethod(exitMethod, exitHook);
                            }
                            param.setResult(null);
                        }
                    }
            );
        } catch (Throwable t) {
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject == null) return;
                                String pkg = null;
                                try {
                                    pkg = (String) XposedHelpers.callMethod(param.thisObject, "getPackageName");
                                } catch (Throwable ignored) {
                                }
                                if (MODULE_PACKAGE.equals(pkg)) {
                                    return;
                                }
                                Intent intent = (Intent) param.args[0];
                                if (intent == null) return;
                                String targetPackage = mPkgMgr.extractPackageFromIntent(intent);
                                if (targetPackage != null) {
                                    if (targetPackage.equals(currentTargetApp)) {
                                        return;
                                    }
                                    handleIntentQueryForIntentHook(param, targetPackage, "queryIntentServices");
                                }
                            } catch (Throwable t) {
                            }
                        }
                    }
            );

            try {
                XposedHelpers.findAndHookMethod(
                        "android.app.ApplicationPackageManager",
                        classLoader,
                        "queryIntentServicesAsUser",
                        Intent.class, int.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                Intent intent = (Intent) param.args[0];
                                if (intent != null) {
                                    String pkg = mPkgMgr.extractPackageFromIntent(intent);
                                    if (pkg != null) {
                                        if (mPkgMgr != null) {
                                            mPkgMgr.captureValidPackage(pkg);
                                        }
                                    }
                                }
                            }
                        }
                );
            } catch (Throwable ignored) {
            }

            ReaLog.log("intent_query", "查询意图服务 Hook成功");
        } catch (Throwable t) {
            ReaLog.log("intent_query", "查询意图服务 Hook失败: " + t.getMessage());
        }
    }

    private void handleIntentQueryForIntentHook(MethodHookParam param, String targetPackage, String methodName) {
        try {
            if (!installStatusMap.getOrDefault(currentTargetApp, true)) {
                param.setResult(new ArrayList<Object>());
                ReaLog.log("package_query", methodName + "查询拦截: 未安装，返回空列表");
                return;
            }

            if (mPkgMgr.isExcludedPackage(targetPackage) || isSystemCorePackage(targetPackage)) {
                ReaLog.log("intent_query", methodName + ": " + targetPackage + " 排除包或系统包，放行");
                return;
            }
            if (!mPkgMgr.shouldReturnInstalledForPackage(targetPackage)) {
                param.setResult(new ArrayList<Object>());
                ReaLog.log("intent_query", methodName + ": " + targetPackage + " 设置未安装，返回空列表");
                return;
            }
            List<Object> fakeList = new ArrayList<Object>();
            for (String fakePkg : globalCapturedPackages) {
                if (mPkgMgr.shouldReturnInstalledForPackage(fakePkg)) {
                    Object fakeResolveInfo = createFakeResolveInfo(fakePkg);
                    if (fakeResolveInfo != null) {
                        fakeList.add(fakeResolveInfo);
                    }
                }
            }
            param.setResult(fakeList);
            ReaLog.log("intent_query", methodName + ": " + targetPackage + " 返回 " + fakeList.size() + " 条虚假数据");
        } catch (Throwable t) {
            param.setResult(new ArrayList<Object>());
            ReaLog.log("intent_query", "处理 Intent 查询异常: " + t.getMessage());
        }
    }

    public ResolveInfo createFakeResolveInfo(String packageName) {
        if (mPkgMgr != null) {
            return (ResolveInfo) mPkgMgr.createFakeResolveInfo(packageName);
        }
        return null;
    }

    private Object createEmptyResolveInfo() {
        if (mPkgMgr != null) {
            return mPkgMgr.createEmptyResolveInfo();
        }
        return null;
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
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (param.thisObject == null) return;
                                String pkg = null;
                                try {
                                    pkg = (String) XposedHelpers.callMethod(param.thisObject, "getPackageName");
                                } catch (Throwable ignored) {
                                }
                                if (MODULE_PACKAGE.equals(pkg)) {
                                    return;
                                }
                                Intent intent = (Intent) param.args[0];
                                if (intent == null) return;
                                String targetPackage = mPkgMgr.extractPackageFromIntent(intent);
                                if (targetPackage == null) {
                                    return;
                                }

                                if (targetPackage.equals(currentTargetApp)) {
                                    return;
                                }

                                if (mPkgMgr.isExcludedPackage(targetPackage) || isSystemCorePackage(targetPackage)) {
                                    ReaLog.log("intent_query", "解析活动: 排除包或系统包 " + targetPackage + "，放行");
                                    return;
                                }
                                if (!mPkgMgr.shouldReturnInstalledForPackage(targetPackage)) {
                                    param.setResult(createEmptyResolveInfo());
                                    ReaLog.log("intent_query", "解析活动: " + targetPackage + " 设置未安装");
                                    return;
                                }
                                ReaLog.log("intent_query", "解析活动: " + targetPackage + " 放行");
                            } catch (Throwable t) {
                            }
                        }
                    }
            );
            ReaLog.log("intent_query", "解析活动before Hook成功");
        } catch (Throwable t) {
            ReaLog.log("intent_query", "解析活动before Hook失败: " + t.getMessage());
        }
    }

    private void hookQueryBroadcastReceivers(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "queryBroadcastReceivers",
                    Intent.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Intent intent = (Intent) param.args[0];
                            if (intent == null) {
                                return;
                            }
                            String pkg = mPkgMgr.extractPackageFromIntent(intent);
                            if (pkg == null || pkg.isEmpty()) {
                                return;
                            }

                            if (pkg.equals(currentTargetApp)) {
                                return;
                            }

                            if (pkg.equals(currentTargetApp) || isSystemCorePackage(pkg)) {
                                ReaLog.log("intent_query", "查询广播: " + pkg + " 自身或系统包，放行");
                                return;
                            }

                            if (!mPkgMgr.shouldReturnInstalledForPackage(pkg)) {
                                param.setResult(new ArrayList<ResolveInfo>());
                                ReaLog.log("intent_query", "查询广播: " + pkg + " 设置未安装，返回空列表");
                                return;
                            }
                            ReaLog.log("intent_query", "查询广播: " + pkg + " 放行");
                        }
                    }
            );
        } catch (Throwable t) {
            log("❌ Hook 查询广播接收 失败: " + t.getMessage());
            ReaLog.log("intent_query", "Hook 查询广播接收异常: " + t.getMessage());
        }
    }

    private void hookStartActivity(ClassLoader classLoader) {
        try {
            Class<
                    ?> instrumentationClass = XposedHelpers.findClass("android.app.Instrumentation", classLoader);
            XposedBridge.hookAllMethods(instrumentationClass, "execStartActivity", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ReaLog.log("launch_intercept", "启动拦截：执行启动活动 拦截入口");
                    handleExecStartActivity(param);
                }
            });
        } catch (Throwable t) {
            ReaLog.log("launch_intercept", "执行启动活动异常: " + t.getMessage());
        }
    }

    private void handleExecStartActivity(XC_MethodHook.MethodHookParam param) {
        try {
            // ============================================================
            //  【分享分流】先判断是否为分享 Intent
            // ============================================================
            Intent shareIntent = null;
            Activity shareAct = null;
            int shareRequestCode = -1;
            for (Object arg : param.args) {
                if (arg instanceof Intent && shareIntent == null) {
                    shareIntent = (Intent) arg;
                } else if (arg instanceof Activity && shareAct == null) {
                    shareAct = (Activity) arg;
                } else if (arg instanceof Integer && shareRequestCode == -1) {
                    shareRequestCode = (Integer) arg;
                }
            }
            try {
                if (shareIntent != null) {
                    if (shareAct == null || shareAct.isFinishing()) {
                        shareAct = getCurrentActivity();
                    }
                    if (shareAct != null && !shareAct.isFinishing()) {
                        String shareTarget = ShareHook.whichShare(shareIntent);
                        if (shareTarget != null) {
                            if (ShareHook.isEnabled(shareTarget)) {
                                boolean handled = ShareHook.handleShareIfNeeded(
                                        shareAct, shareIntent, currentTargetApp,
                                        shareRequestCode, null,
                                        shareAct.getClassLoader());
                                if (handled) {
                                    param.setResult(null);
                                    return;
                                }
                            } else {
                                ReaLog.log("share_fake", "假装分享：" + ShareHook.displayName(shareTarget)
                                        + "已禁用，放行 ← " + currentTargetApp);
                                return;
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                ReaLog.log("share_fake", "假装分享异常: " + t.getMessage());
            }

            // ============================================================
            //  【启动拦截】
            // ============================================================
            boolean isLaunchIntercept = launchInterceptMap.getOrDefault(currentTargetApp, true);
            if (!isLaunchIntercept) {
                ReaLog.log("launch_intercept", "启动拦截已关闭");
                return;
            }

            Intent intent = null;
            Activity activity = null;
            int requestCode = -1;
            for (Object arg : param.args) {
                if (arg instanceof Intent) intent = (Intent) arg;
                else if (arg instanceof Activity) activity = (Activity) arg;
                else if (arg instanceof Integer && requestCode == -1) requestCode = (int) arg;
            }
            if (intent == null) {
                ReaLog.log("launch_intercept", "启动拦截：Intent为空，无法拦截");
                return;
            }
            if (intent.hasExtra(More.SKIP_LAUNCH_TAG)) {
                ReaLog.log("launch_intercept", "启动拦截：内部启动，跳过拦截");
                return;
            }

            String currentPkg = currentTargetApp;
            String targetPkg = mPkgMgr.extractPackageFromIntent(intent);
            if (targetPkg == null && intent.getData() != null) {
                Uri data = intent.getData();
                String scheme = data.getScheme();
                if ("market".equals(scheme) || "appmarket".equals(scheme)) {
                    targetPkg = data.getQueryParameter("id");
                    ReaLog.log("launch_intercept", "从market链接提取包名: " + targetPkg);
                }
            }

            String[] systemPermissionPackages = {
                    "com.android.permissioncontroller",
                    "com.android.packageinstaller",
                    "com.google.android.packageinstaller",
                    "com.android.settings",
                    "com.google.android.permissioncontroller"
            };
            if (targetPkg != null) {
                for (String sysPkg : systemPermissionPackages) {
                    if (sysPkg.equals(targetPkg)) {
                        ReaLog.log("launch_intercept", "系统权限包，放行: " + targetPkg);
                        return;
                    }
                }
            }
            String action = intent.getAction();
            if (action != null && (action.startsWith("android.settings.") ||
                            "android.intent.action.REQUEST_PERMISSIONS".equals(action))) {
                ReaLog.log("launch_intercept", "系统设置/权限请求，放行: " + action);
                return;
            }

            if (targetPkg != null && (targetPkg.equals(currentPkg) || mPkgMgr.isExcludedPackage(targetPkg))) {
                ReaLog.log("launch_intercept", "自身包或排除包，放行: " + targetPkg);
                return;
            }
            boolean isImplicit = (targetPkg == null && intent.getComponent() == null
                    && intent.getPackage() == null);
            if (!isImplicit && targetPkg == null) {
                ReaLog.log("launch_intercept", "无法确定目标包，放行");
                return;
            }

            final String finalTargetPkg = (targetPkg != null) ? targetPkg : "";
            param.setResult(null);

            final Activity finalActivity = (activity != null) ? activity : getCurrentActivity();
            if (finalActivity == null || finalActivity.isFinishing()) {
                ReaLog.log("launch_intercept", "启动拦截：当前Activity无效，无法显示弹窗");
                return;
            }
            final int finalRequestCode = requestCode;
            final Intent originalIntent = new Intent(intent);
            final boolean reallyInstalled = !TextUtils.isEmpty(targetPkg)
                    && mPkgMgr.isPackageReallyInstalled(targetPkg);

            ReaLog.log("launch_intercept", "准备显示启动确认弹窗，目标: "
                    + (TextUtils.isEmpty(targetPkg) ? "隐式Intent" : targetPkg));
            debounceAndShowDialog(finalActivity, originalIntent, finalTargetPkg,
            finalRequestCode, reallyInstalled);
        } catch (Throwable t) {
            log("handleExecStartActivity 异常: " + t.getMessage());
            ReaLog.log("launch_intercept", "处理执行启动活动异常: " + t.getMessage());
        }
    }

    private void showLaunchConfirmDialog(final Activity activity, final Intent originalIntent,
            final String targetPkg, final int requestCode,
            final boolean reallyInstalled) {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) {
                ReaLog.log("launch_intercept", "启动拦截：Activity已销毁，无法显示启动确认弹窗");
                return;
            }

            Uri uri = originalIntent.getData();
            final String finalIdentifier;
            final String finalDisplayName;
            if (!TextUtils.isEmpty(targetPkg)) {
                finalIdentifier = targetPkg;
                finalDisplayName = targetPkg;
            } else if (uri != null) {
                finalIdentifier = uri.toString();
                String uriStr = uri.toString();
                finalDisplayName = uriStr.length() > 120 ? uriStr.substring(0, 117) + "..." : uriStr;
            } else {
                String intentUri = originalIntent.toUri(0);
                if (TextUtils.isEmpty(intentUri)) {
                    String action = originalIntent.getAction();
                    intentUri = (action != null) ? action : "unknown_intent";
                }
                finalIdentifier = intentUri;
                String shortDesc;
                if (originalIntent.getAction() != null) {
                    shortDesc = originalIntent.getAction();
                } else if (originalIntent.getCategories() != null && !originalIntent.getCategories().isEmpty()) {
                    shortDesc = "category:" + originalIntent.getCategories().iterator().next();
                } else {
                    shortDesc = intentUri;
                }
                if (shortDesc.length() > 120) {
                    shortDesc = shortDesc.substring(0, 117) + "...";
                }
                finalDisplayName = "外部链接: " + shortDesc;
            }
            ReaLog.log("launch_intercept", "启动确认弹窗，标识: " + finalIdentifier);
            if (finalIdentifier != null) {
                // 自动处理（黑/白名单）
                String action = getAutoAction(currentTargetApp, finalIdentifier);
                if (action != null) {
                    if ("black".equals(action)) {
                        if (requestCode > 0) {
                            callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, originalIntent);
                        }
                        ToastUtil.showUnique(activity, "⛔ 黑名单已自动拦截\n" + finalIdentifier);
                        ReaLog.log("auto", "⛔ 黑名单自动拦截: " + finalIdentifier);
                        return;
                    } else if ("white".equals(action)) {
                        performRealLaunch(activity, originalIntent, targetPkg, finalDisplayName, requestCode);
                        ReaLog.log("auto", "💚 白名单自动放行: " + finalIdentifier);
                        return;
                    }
                }
                // 智能判断
                String autoChoice = checkAutoChoice(currentTargetApp, finalIdentifier);
                if (autoChoice != null) {
                    if ("real".equals(autoChoice)) {
                        performRealLaunch(activity, originalIntent, targetPkg, finalDisplayName, requestCode);
                        ReaLog.log("auto", "智能判断-真实启动: " + finalIdentifier);
                    } else if ("fake".equals(autoChoice)) {
                        performFakeLaunch(activity, finalDisplayName, requestCode, originalIntent);
                        ReaLog.log("auto", "智能判断-虚假启动: " + finalIdentifier);
                    } else if ("cancel".equals(autoChoice)) {
                        if (requestCode > 0) {
                            callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, originalIntent);
                        }
                        ToastUtil.showUnique(activity, "🕸️ 已自动判断取消\n" + finalIdentifier);
                        ReaLog.log("auto", "智能判断-取消启动: " + finalIdentifier);
                    }
                    return;
                }
            }

            String pkgTip = reallyInstalled ? finalDisplayName : finalDisplayName + (TextUtils.isEmpty(targetPkg) ? "" : "（未安装）");
            boolean isBrowser = isBrowserFromIntent(originalIntent, activity.getPackageManager());
            String browserTip = isBrowser ? "<br><b>(<font color='#FF5722'>检测到跳转浏览器</font>，<font color='#1E90FF'>可选择虚假启动屏蔽</font>)</b>" : "";

            String msg = "当前应用想要打开<br><font color='#FF5722'><b>" + pkgTip + "</b></font>" + browserTip + "<br><br>" +
                    "—————————————————<br>" +
                    "【虚假启动】不需要启动跳转到应用，返回假装已启动<br>" +
                    "【真实启动】真正的打开想要的应用，按宿主要求执行<br>" +
                    "【取消启动】阻止跳转到第三方应用，可能会重复提醒<br>" +
                    "—————————————————<br>";
            if (originalIntent.getData() != null || (originalIntent.getAction() != null &&
                            (originalIntent.getAction().equals(Intent.ACTION_OPEN_DOCUMENT) ||
                                    originalIntent.getAction().equals(Intent.ACTION_SEND)))) {
                msg +=
                        "<font color='#1E90FF'>检测到启动的应用中包含参数跳转请求</font><br>" +
                                "<font color='#FF5722'>虚假:</font><font color='#1E90FF'>假装参数返回</font>/<font color='#FF5722'>真实:</font><font color='#1E90FF'>附带参数跳转</font><br>" +
                                "<font color='#D3D3D3'><b>部分应用虚假启动需切换至后台再回来生效</b></font><br>";
            }
            boolean canResolve = (originalIntent.getData() != null) || (originalIntent.getAction() != null);

            LinearLayout contentLayout = new LinearLayout(activity);
            contentLayout.setOrientation(LinearLayout.VERTICAL);
            contentLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
            TextView msgView = new TextView(activity);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                msgView.setText(Html.fromHtml(msg, Html.FROM_HTML_MODE_LEGACY));
            } else {
                msgView.setText(Html.fromHtml(msg));
            }
            msgView.setTextIsSelectable(true);
            msgView.setTextSize(14);
            msgView.setTextColor(0xFF333333);
            contentLayout.addView(msgView);

            if (finalIdentifier != null) {
                View divider = new View(activity);
                divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(0xFFE0E0E0);
                contentLayout.addView(divider);
                Button autoBtn = new Button(activity);
                autoBtn.setText("⚙ 设置自动处理");
                autoBtn.setTextSize(14);
                autoBtn.setPadding(More.spx(activity, 40), More.spx(activity, 15), More.spx(activity, 40), More.spx(activity, 15));
                autoBtn.setBackgroundColor(0xAAFF6347);
                autoBtn.setTextColor(0xFFFFFFFF);
                autoBtn.setGravity(Gravity.CENTER);
                autoBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ReaLog.log("system", "打开自动处理设置: " + finalIdentifier);
                        instar.showAutoSettingDialog(activity, finalIdentifier, finalDisplayName, null, HookInit.this, currentTargetApp);
                    }
                });
                contentLayout.addView(autoBtn);
            }

            if (canResolve) {
                AlertDialog dialog = DialogUi.createBoundedDialog(activity, "启动确认", null,
                new String[]{"真实启动", "虚假启动", "取消启动"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ReaLog.log("launch_intercept", "用户选择真实启动: " + finalDisplayName);
                                performRealLaunch(activity, originalIntent, targetPkg, finalDisplayName, requestCode);
                                if (finalIdentifier != null) {
                                    addAutoRecord(currentTargetApp, finalIdentifier, "real");
                                    HookInit.this.saveConfigToFile();
                                }
                                d.dismiss();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ReaLog.log("launch_intercept", "用户选择虚假启动: " + finalDisplayName);
                                performFakeLaunch(activity, finalDisplayName, requestCode, originalIntent);
                                if (finalIdentifier != null) {
                                    addAutoRecord(currentTargetApp, finalIdentifier, "fake");
                                    HookInit.this.saveConfigToFile();
                                }
                                d.dismiss();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (requestCode > 0) {
                                    callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, originalIntent);
                                }
                                ToastUtil.showUnique(activity, "已取消启动" + finalDisplayName);
                                ReaLog.log("launch_intercept", "用户选择取消启动: " + finalDisplayName);
                                if (finalIdentifier != null) {
                                    addAutoRecord(currentTargetApp, finalIdentifier, "cancel");
                                    HookInit.this.saveConfigToFile();
                                }
                                d.dismiss();
                            }
                        }
                },
                contentLayout
                );
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        if (requestCode > 0) {
                            callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, originalIntent);
                        }
                        ReaLog.log("system", "启动确认弹窗取消");
                    }
                });
                dialog.show();
            } else {
                AlertDialog dialog = DialogUi.createBoundedDialog(activity, "启动确认", null,
                new String[]{"尝试强启", "虚假启动", "取消启动"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ReaLog.log("launch_intercept", "用户选择尝试启动2: " + finalDisplayName);
                                performRealLaunch(activity, originalIntent, targetPkg, finalDisplayName, 0);
                                if (finalIdentifier != null) {
                                    addAutoRecord(currentTargetApp, finalIdentifier, "real");
                                    saveConfigToFile();
                                }
                                d.dismiss();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ReaLog.log("launch_intercept", "用户选择虚假启动2: " + finalDisplayName);
                                performFakeLaunch(activity, finalDisplayName, requestCode, originalIntent);
                                if (finalIdentifier != null) {
                                    addAutoRecord(currentTargetApp, finalIdentifier, "fake");
                                    saveConfigToFile();
                                }
                                d.dismiss();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                if (requestCode > 0) {
                                    callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, originalIntent);
                                }
                                ToastUtil.showUnique(activity, "已取消启动" + finalDisplayName);
                                ReaLog.log("launch_intercept", "用户选择取消启动2: " + finalDisplayName);
                                if (finalIdentifier != null) {
                                    addAutoRecord(currentTargetApp, finalIdentifier, "cancel");
                                    saveConfigToFile();
                                }
                                d.dismiss();
                            }
                        }
                },
                contentLayout
                );
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface d) {
                        if (requestCode > 0) {
                            callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, originalIntent);
                        }
                        ReaLog.log("launch_intercept", "启动确认弹窗取消2");
                    }
                });
                dialog.show();
            }
        } catch (Throwable t) {
            log("显示启动确认异常: " + t.getMessage());
            ReaLog.log("system", "显示启动确认对话框异常: " + t.getMessage());
        }
    }

    private void performRealLaunch(Activity activity, Intent originalIntent, String targetPkg, String finalDisplayName, int requestCode) {
        try {
            Intent launchIntent;
            if (TextUtils.isEmpty(targetPkg)) {
                launchIntent = new Intent(originalIntent);
                More.markInternal(launchIntent);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (originalIntent.getData() != null) {
                    launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                ReaLog.log("launch_intercept", "真实启动: " + finalDisplayName);
            } else {
                launchIntent = buildLaunchIntent(activity, originalIntent, targetPkg, true);
                ReaLog.log("launch_intercept", "真实启动: " + targetPkg);
            }
            // 真实启动必须把结果交还给宿主：requestCode>0
            if (requestCode > 0) {
                // FLAG_ACTIVITY_NEW_TASK 会阻断的结果回传，必须清除
                launchIntent.setFlags(launchIntent.getFlags() & ~Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivityForResult(launchIntent, requestCode);
                ReaLog.log("launch_intercept", "真实启动（带结果回调）: " + finalDisplayName);
            } else {
                activity.startActivity(launchIntent);
                ReaLog.log("launch_intercept", "真实启动（无结果回调）: " + finalDisplayName);
            }
            ToastUtil.showUnique(activity, "✅ 已启动：" + finalDisplayName);
            ReaLog.log("launch_intercept", "启动成功: " + finalDisplayName);
            refreshAppToForeground(activity);
        } catch (ActivityNotFoundException e) {
            if (!TextUtils.isEmpty(targetPkg)) {
                try {
                    Intent launchIntent = activity.getPackageManager().getLaunchIntentForPackage(targetPkg);
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(launchIntent);
                        ToastUtil.showUnique(activity, "✅ 启动主页：" + targetPkg);
                        ReaLog.log("launch_intercept", "启动主页: " + targetPkg);
                        refreshAppToForeground(activity);
                        return;
                    }
                } catch (Exception fallbackE) {
                    ReaLog.log("launch_intercept", "启动失败2: " + fallbackE.getMessage());
                }
            }
            ToastUtil.showUnique(activity, "启动失败：无法处理该跳转\n" + finalDisplayName);
            ReaLog.log("launch_intercept", "启动失败: 无法处理该跳转 - " + finalDisplayName);
        } catch (Exception e) {
            ToastUtil.showUnique(activity, "启动异常：" + e.getMessage());
            ReaLog.log("launch_intercept", "启动异常: " + e.getMessage());
        }
    }

    private void performFakeLaunch(Activity activity, String finalDisplayName, int requestCode, Intent originalIntent) {
        // ===== 文件选择器/分享 CHOOSER 过滤 =====
        if (originalIntent != null) {
            String action = originalIntent.getAction();
            if (isFilePickerAction(action) || isChooserWithFilePicker(originalIntent) || isChooserWithShareIntent(originalIntent)) {
                if (isFilePickerAction(action) || isChooserWithFilePicker(originalIntent)) {
                    Intent resultIntent = new Intent();
                    Uri fakeUri = Uri.parse("content://com.xiaolin.fake.yhzl/fake_file_" + System.currentTimeMillis());
                    resultIntent.setData(fakeUri);
                    if (requestCode > 0) {
                        callOnActivityResult(activity, requestCode, Activity.RESULT_OK, resultIntent);
                    }
                    ReaLog.log("launch_intercept", "【虚假启动】文件选择器返回虚假 Uri: " + fakeUri);
                    ToastUtil.showUnique(activity, "✅ 已返回虚假 Uri: \n" + fakeUri);
                    return;
                }
                if (isChooserWithShareIntent(originalIntent)) {
                    Intent targetIntent = originalIntent.getParcelableExtra(Intent.EXTRA_INTENT);
                    String targetAction = targetIntent != null ? targetIntent.getAction() : "unknown_share";
                    ReaLog.log("launch_intercept", "【虚假启动】系统分享,阻断启动: " + targetAction);
                    ToastUtil.showUnique(activity, "✅ 已阻断系统分享: \n" + targetAction);
                    if (requestCode > 0) {
                        callOnActivityResult(activity, requestCode, Activity.RESULT_CANCELED, null);
                    }
                    return;
                }
            }
        }

        try {
            // ===== 分享伪造：统一走 ShareHook 入口 =====
            Map<String, Boolean> shareDetail = shareFakeDetailMap.getOrDefault(
                    currentTargetApp, getDefaultShareDetailMap());
            boolean shareHandled = false;
            if (originalIntent != null) {
                // 传 requestCode，ShareHook 内部在 requestCode > 0 时已触发 onActivityResult
                shareHandled = ShareHook.handleShareIfNeeded(
                        activity, originalIntent, currentTargetApp, requestCode, shareDetail,
                        activity.getClassLoader());
                if (shareHandled) {
                    ReaLog.log("share_fake", "虚假启动：假装分享已处理");
                }
            }

            // ===== 设置时间偏移 =====
            long minDuration = 3 * 60 * 1000;
            long maxDuration = 6 * 60 * 1000;
            long fakeDuration = minDuration + (long) (Math.random() * (maxDuration - minDuration));
            sFakeTimeOffset = fakeDuration;
            sTimeOffset = 0;
            ReaLog.log("time_fake", "设置虚假启动偏移: " + fakeDuration + "ms (" + (fakeDuration / 1000) + "秒)");
            new Handler(Looper.getMainLooper()).removeCallbacks(mClearFakeTimeRunnable);

            // ===== 返回结果（ShareHook 已处理时不重复触发） =====
            if (requestCode > 0 && !shareHandled) {
                Intent resultIntent = new Intent();
                if (originalIntent != null) {
                    Uri data = originalIntent.getData();
                    if (data != null) resultIntent.setData(data);
                    resultIntent.setAction(originalIntent.getAction());
                    Set<String> categories = originalIntent.getCategories();
                    if (categories != null) {
                        for (String cat : categories) resultIntent.addCategory(cat);
                    }
                    Bundle extras = originalIntent.getExtras();
                    if (extras != null) resultIntent.putExtras(extras);
                    resultIntent.setFlags(originalIntent.getFlags());
                }
                callOnActivityResult(activity, requestCode, Activity.RESULT_OK, resultIntent);
                ReaLog.log("launch_intercept", "虚假启动成功(有回调): " + finalDisplayName);
            } else if (shareHandled) {
                ReaLog.log("launch_intercept", "虚假启动成功(ShareHook 已触发结果链): " + finalDisplayName);
            } else {
                ReaLog.log("launch_intercept", "虚假启动成功(无结果链): " + finalDisplayName);
            }

            ToastUtil.showUnique(activity, "✅ 已虚假启动: " + finalDisplayName + "\n时间偏移" + (fakeDuration / 1000) + "秒");
            ReaLog.log("launch_intercept", "虚假启动成功: " + finalDisplayName);
            refreshAppToForeground(activity);
            More.postOnMainThread(mClearFakeTimeRunnable, 5000);

        } catch (Throwable e) {
            ToastUtil.showUnique(activity, "虚假启动失败");
            ReaLog.log("launch_intercept", "虚假启动异常: " + e.getMessage());
            e.printStackTrace();
            sFakeTimeOffset = 0;
            sTimeOffset = 0;
            new Handler(Looper.getMainLooper()).removeCallbacks(mClearFakeTimeRunnable);
        }
    }

/** 检查是否为文件选择器 Action */
    private boolean isFilePickerAction(String action) {
        if (action == null) {
            return false;
        }

        return Intent.ACTION_GET_CONTENT.equals(action) ||
                Intent.ACTION_OPEN_DOCUMENT.equals(action) ||
                Intent.ACTION_PICK.equals(action) ||
                "android.intent.action.OPEN_DOCUMENT_TREE".equals(action);
    }

/** 检查 CHOOSER 内部是否为文件选择 */
    private boolean isChooserWithFilePicker(Intent originalIntent) {
        if (originalIntent == null || !Intent.ACTION_CHOOSER.equals(originalIntent.getAction())) {
            return false;
        }

        Intent targetIntent = originalIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (targetIntent != null) {
            return isFilePickerAction(targetIntent.getAction());
        }

        return false;
    }

/** 检查 CHOOSER 内部是否为分享 */
    private boolean isChooserWithShareIntent(Intent originalIntent) {
        if (originalIntent == null || !Intent.ACTION_CHOOSER.equals(originalIntent.getAction())) {
            return false;
        }

        Intent targetIntent = originalIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        if (targetIntent != null) {
            String targetAction = targetIntent.getAction();
            return Intent.ACTION_SEND.equals(targetAction) ||
                    Intent.ACTION_SEND_MULTIPLE.equals(targetAction);
        }

        return false;
    }

    private void hookActivityLifecycleForFakeBackground(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onPause",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity activity = (Activity) param.thisObject;
                            if (!activity.getPackageName().equals(currentTargetApp)) return;
                            mPauseTimeMap.put(activity, System.currentTimeMillis());
                            ReaLog.log("time_fake", "记录时间: " + System.currentTimeMillis());
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "android.app.Activity",
                    classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            Activity activity = (Activity) param.thisObject;
                            if (!activity.getPackageName().equals(currentTargetApp)) return;

                            Long pauseTime = mPauseTimeMap.get(activity);
                            if (pauseTime == null) {
                                return;
                            }

                            long realBackgroundDuration = System.currentTimeMillis() - pauseTime;

                            if (realBackgroundDuration < 3000) {
                                long minDuration = 65 * 1000;
                                long maxDuration = 5 * 60 * 1000;
                                long fakeDuration = minDuration + (long) (Math.random() * (maxDuration - minDuration));
                                sTimeOffset = fakeDuration;
                                ReaLog.log("time_fake", "设置前后台切换偏移: " + fakeDuration + "ms (" + (fakeDuration / 1000) + "秒)");
                                String durationStr = String.format("%d分%d秒",
                                        fakeDuration / 60000,
                                        (fakeDuration % 60000) / 1000);
                                ReaLog.log("time_fake", "Activity前后台切换: " + durationStr);
                            } else {
                                ReaLog.log("time_fake", "是真实后台[" + realBackgroundDuration + "ms]，跳过");
                            }

                            mPauseTimeMap.remove(activity);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (sTimeOffset != 0) {
                                ReaLog.log("time_fake", "清除前后台切换偏移 (原偏移 " + sTimeOffset + "ms)");
                                sTimeOffset = 0;
                            }
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "java.lang.System",
                    null,
                    "currentTimeMillis",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (sTimeOffset != 0) {
                                long realTime = (long) param.getResult();
                                param.setResult(realTime - sTimeOffset);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            log("❌ Hook Activity前后台失败: " + t.getMessage());
            ReaLog.log("time_fake", "Activity前后台切换异常: " + t.getMessage());
        }
    }

    private void simulateBackgroundSwitch(final Activity activity) {
        if (activity == null || activity.isFinishing()) {
            ReaLog.log("time_fake", "前后Activity无效");
            return;
        }
        try {
            final ActivityManager am = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                ReaLog.log("time_fake", "Activity活动为空");
                return;
            }
            final int taskId = activity.getTaskId();

            More.postOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                activity.moveTaskToBack(true);
                                ReaLog.log("time_fake", "模拟移到后台");
                                More.postOnMainThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                                        am.moveTaskToFront(taskId, 0);
                                                        ReaLog.log("time_fake", "模拟拉回前台(高版本)");
                                                    } else {
                                                        Intent intent = activity.getPackageManager()
                                                                .getLaunchIntentForPackage(activity.getPackageName());
                                                        if (intent != null) {
                                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                                                            activity.startActivity(intent);
                                                            ReaLog.log("time_fake", "模拟拉回前台(低版本)");
                                                        }
                                                    }
                                                } catch (Throwable e) {
                                                    log("拉回前台失败: " + e.getMessage());
                                                    ReaLog.log("time_fake", "拉回前台异常: " + e.getMessage());
                                                }
                                            }
                                        }, 30);
                            } catch (Throwable e) {
                                log("移到后台失败: " + e.getMessage());
                                ReaLog.log("time_fake", "移到后台异常: " + e.getMessage());
                            }
                        }
                    }, 10);
        } catch (Throwable e) {
            log("后台切换失败: " + e.getMessage());
        }
    }

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

    private void installTimeFakeHooks(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "java.lang.System",
                    classLoader,
                    "currentTimeMillis",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long totalOffset = sFakeTimeOffset != 0 ? sFakeTimeOffset : sTimeOffset;
                            if (totalOffset != 0) {
                                long realTime = (long) param.getResult();
                                param.setResult(realTime - totalOffset);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.SystemClock",
                    classLoader,
                    "elapsedRealtime",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long totalOffset = sFakeTimeOffset != 0 ? sFakeTimeOffset : sTimeOffset;
                            if (totalOffset != 0) {
                                long realTime = (long) param.getResult();
                                param.setResult(realTime - totalOffset);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            ReaLog.log("time_fake", "时间偏移异常1: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.SystemClock",
                    classLoader,
                    "uptimeMillis",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long totalOffset = sFakeTimeOffset != 0 ? sFakeTimeOffset : sTimeOffset;
                            if (totalOffset != 0) {
                                long realTime = (long) param.getResult();
                                param.setResult(realTime - totalOffset);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            ReaLog.log("time_fake", "时间偏移异常2: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.os.SystemClock",
                    classLoader,
                    "elapsedRealtimeNanos",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            long totalOffset = sFakeTimeOffset != 0 ? sFakeTimeOffset : sTimeOffset;
                            if (totalOffset != 0) {
                                long realTime = (long) param.getResult();
                                param.setResult(realTime - (totalOffset * 1_000_000L));
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            ReaLog.log("time_fake", "时间偏移异常3: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.usage.UsageStatsManager",
                    classLoader,
                    "queryUsageStats",
                    int.class,
                    long.class,
                    long.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (sFakeTimeOffset != 0) {
                                List<Object> fakeStats = new ArrayList<Object>();
                                synchronized (globalCapturedPackages) {
                                    for (String pkg : globalCapturedPackages) {
                                        Object stats = createFakeUsageStats(pkg, sFakeTimeOffset);
                                        if (stats != null) fakeStats.add(stats);
                                    }
                                }
                                param.setResult(fakeStats);
                            }
                        }
                    }
            );
        } catch (Throwable t) {
            ReaLog.log("time_fake", "时间偏移异常4: " + t.getMessage());
        }
    }

    private Intent buildLaunchIntent(Activity activity, Intent oriIntent, String pkg, boolean needParams) {
        if (needParams && oriIntent != null) {
            Intent launchIntent = new Intent(oriIntent);
            More.markInternal(launchIntent);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (oriIntent.getData() != null) {
                String scheme = oriIntent.getData().getScheme();
                if ("content".equals(scheme) || "file".equals(scheme)) {
                    launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    launchIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
            }
            ReaLog.log("launch_intercept", "启动拦截：带参启动 " + pkg);
            return launchIntent;
        } else {
            Intent launchIntent = new Intent(Intent.ACTION_MAIN);
            launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            launchIntent.setPackage(pkg);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            More.markInternal(launchIntent);
            ReaLog.log("launch_intercept", "启动拦截：无参启动 " + pkg);
            return launchIntent;
        }
    }
    
    
    private void callOnActivityResult(final Activity activity, final int requestCode, final int resultCode, final Intent data) {
        ReaLog.log("launch_intercept", "虚假启动: 请求码=" + requestCode + ",回调码=" + resultCode);
        // 先尝试同步调用
        boolean success = false;
        try {
            XposedHelpers.callMethod(activity, "onActivityResult", requestCode, resultCode, data);
            success = true;
        } catch (Throwable t) {

            try {
                Method method = Activity.class.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
                method.setAccessible(true);
                method.invoke(activity, requestCode, resultCode, data);
                success = true;
            } catch (Throwable e2) {
            }
        }
        // 如果都失败了，延迟在主线程再试一次
        if (!success) {
            More.postOnMainThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Method method = Activity.class.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
                                method.setAccessible(true);
                                method.invoke(activity, requestCode, resultCode, data);
                            } catch (Throwable e) {
                            }
                        }
                    }, 200);
        }
    }


    private void refreshAppToForeground(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        try {
            Window window = activity.getWindow();
            if (window != null) {
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                window.getDecorView().invalidate();
                window.getDecorView().requestLayout();
            }
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.onWindowFocusChanged(true);
                }
            });
        } catch (Throwable e) {
        }
    }

    private boolean isDetectionUrl(String url) {
        if (mPkgMgr != null) {
            return mPkgMgr.isDetectionUrl(url);
        }
        return false;
    }

    private void fakeOkHttpResponse(Object callback, String url, boolean isInstalled) {
        if (mPkgMgr != null) {
            mPkgMgr.fakeOkHttpResponse(callback, url, isInstalled);
        }
    }

    private Object createOkHttpFakeResponse(boolean isInstalled) {
        if (mPkgMgr != null) {
            return mPkgMgr.createOkHttpFakeResponse(isInstalled, null);
        }
        return null;
    }

    private boolean isBrowserFromIntent(Intent intent, PackageManager pm) {
        if (intent == null || pm == null) {
            return false;
        }
        String pkg = mPkgMgr.extractPackageFromIntent(intent);
        if (pkg == null) {
            return false;
        }
        String lowerPkg = pkg.toLowerCase();
        String[] browserKeywords = {
                "browser", "chrome", "quark", "firefox", "edge", "opera", "safari",
                "maxthon", "uc", "via", "yandex", "vivaldi", "brave", "duckduckgo",
                "huawei.browser", "honor.browser", "miui.browser", "xiaomi.browser",
                "oppo.browser", "realme.browser", "vivo.browser", "iqoo.browser",
                "oneplus.browser", "meizu.browser", "nubia.browser", "zte.browser",
                "lenovo.browser", "moto.browser", "heytap.browser", "samsung.browser"
        };
        for (String key : browserKeywords) {
            if (lowerPkg.contains(key)) {
                ReaLog.log("launch_intercept", "跳转浏览器: true -> " + pkg);
                return true;
            }
        }
        return false;
    }


    public boolean isRealSystemPackage(String packageName) {
        if (packageName == null) return false;

        // 使用 mRealSystemCache 缓存
        Boolean cached = mRealSystemCache.get(packageName);
        if (cached != null && cached) {
            return true;
        }

        try {
            Context ctx = getApplicationContext();
            if (ctx == null) {
                return false;
            }
            PackageManager pm = ctx.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(packageName, 0);
            if (ai == null) {
                return false;
            }

            boolean isSystem = false;

            // 1. 路径匹配（扩展系统分区）
            if (ai.sourceDir != null) {
                isSystem = ai.sourceDir.startsWith("/system/") ||
                        ai.sourceDir.startsWith("/product/") ||
                        ai.sourceDir.startsWith("/vendor/") ||
                        ai.sourceDir.startsWith("/odm/");
            }

            // 2. 标志位兜底
            if (!isSystem) {
                isSystem = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            }

            if (isSystem) {
                mRealSystemCache.put(packageName, true);
            }
            return isSystem;
        } catch (PackageManager.NameNotFoundException e) {
            mRealSystemCache.put(packageName, false);
            return false;
        } catch (Throwable t) {
            ReaLog.log("system", "过滤系统应用异常: " + t.getMessage());
            return false;
        }
    }

    public boolean isSystemCorePackage(String packageName) {
        if (mPkgMgr != null) {
            return mPkgMgr.isSystemCorePackage(packageName);
        }
        return false;
    }

    public int getPackageStatus(String packageName) {
        if (mPkgMgr != null) {
            return mPkgMgr.getPackageStatus(packageName);
        }
        return -1;
    }




    private void hookQueryIntentActivities(ClassLoader classLoader) {
        try {
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader),
                    "queryIntentActivities",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handleQuery(param);
                        }
                    }
            );
            try {
                XposedBridge.hookAllMethods(
                        XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader),
                        "queryIntentActivitiesAsUser",
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                handleQuery(param);
                            }
                        }
                );
            } catch (Throwable e) {
            }
        } catch (Throwable e) {
            log("Hook 查询意图活动失败: " + e.getMessage());
        }
    }

    private void handleQuery(MethodHookParam param) {
        try {
            if (!installStatusMap.getOrDefault(currentTargetApp, true)) {
                param.setResult(new ArrayList<ResolveInfo>());
                ReaLog.log("package_query", "查询意图活动: 未安装，返回空列表");
                return;
            }

            Intent intent = null;
            for (Object o : param.args) {
                if (o instanceof Intent) {
                    intent = (Intent) o;
                    break;
                }
            }
            if (intent == null) {
                param.setResult(new ArrayList<ResolveInfo>());
                return;
            }

            if (MODULE_PACKAGE.equals(currentTargetApp)) {
                return;
            }

            String pkg = mPkgMgr.extractPackageFromIntent(intent);
            if (pkg != null && (pkg.equals(currentTargetApp) || isSystemCorePackage(pkg) || mPkgMgr.isExcludedPackage(pkg))) {
                return;
            }

            boolean isExplicit = (intent.getComponent() != null || intent.getPackage() != null);
            if (isExplicit) {
                return;
            }

            List<ResolveInfo> fakeList = new ArrayList<>();
            synchronized (globalCapturedPackages) {
                for (String fakePkg : globalCapturedPackages) {
                    if (mPkgMgr.shouldReturnInstalledForPackage(fakePkg)) {
                        ResolveInfo fake = createFakeResolveInfo(fakePkg);
                        if (fake != null) {
                            fakeList.add(fake);
                        }
                    }
                }
            }
            param.setResult(fakeList);
            ReaLog.log("launch", "查询意图活动 返回 " + fakeList.size() + " 个虚假隐式活动");
        } catch (Exception e) {
            log("handleQuery异常: " + e.getMessage());
            param.setResult(new ArrayList<ResolveInfo>());
        }
    }
    
    private void hookDesktopAppQuery(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager",
                    classLoader,
                    "queryIntentActivities",
                    Intent.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            handleDesktopAppQuery(param);
                        }
                    }
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    XposedHelpers.findAndHookMethod(
                            "android.app.ApplicationPackageManager",
                            classLoader,
                            "queryIntentActivitiesAsUser",
                            Intent.class,
                            int.class,
                            int.class,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) {
                                    handleDesktopAppQuery(param);
                                }
                            }
                    );
                } catch (Throwable ignored) {
                }
            }

            ReaLog.log("intent_query", "桌面应用列表穿透已安装");
        } catch (Throwable t) {
            ReaLog.log("intent_query", "桌面应用列表穿透失败: " + t.getMessage());
        }
    }

    private void handleDesktopAppQuery(MethodHookParam param) {
        try {
            Intent intent = null;
            for (Object arg : param.args) {
                if (arg instanceof Intent) {
                    intent = (Intent) arg;
                    break;
                }
            }
            if (intent == null) return;
            boolean isLauncherQuery = Intent.ACTION_MAIN.equals(intent.getAction()) &&
                    intent.hasCategory(Intent.CATEGORY_LAUNCHER);
            if (!isLauncherQuery) return;

            Set<String> allPackages = new HashSet<>();

            allPackages.addAll(HookInit.globalCapturedPackages);

            List<
                    PackageConfig> configs = packageConfigMap.getOrDefault(currentTargetApp, new ArrayList<>());
            for (PackageConfig config : configs) {
                if ("installed".equals(config.statusMode)) {
                    allPackages.add(config.packageName);
                }
            }

            allPackages.removeAll(HookInit.excludedPackagesMap.getOrDefault(currentTargetApp, new ArrayList<>()));

            List<ResolveInfo> fakeList = new ArrayList<>();
            for (String pkg : allPackages) {
                if (isSystemCorePackage(pkg)) continue;
                if (mPkgMgr.isExcludedPackage(pkg)) continue;
                if (!mPkgMgr.shouldReturnInstalledForPackage(pkg)) continue;
                ResolveInfo fake = createFakeResolveInfo(pkg);
                if (fake != null) {
                    fakeList.add(fake);
                }
            }

            if (fakeList.isEmpty()) {
                ResolveInfo selfFake = createFakeResolveInfo(currentTargetApp);
                if (selfFake != null) {
                    fakeList.add(selfFake);
                } else {
                    ResolveInfo fallback = new ResolveInfo();
                    ActivityInfo ai = new ActivityInfo();
                    ai.packageName = currentTargetApp;
                    ai.name = currentTargetApp + ".MainActivity";
                    ai.enabled = true;
                    ai.exported = true;
                    fallback.activityInfo = ai;
                    fallback.priority = 1;
                    fakeList.add(fallback);
                }
                ReaLog.log("intent_query", "桌面穿透查询，兜底返回自身包");
            }

            param.setResult(fakeList);
            ReaLog.log("intent_query", "桌面穿透查询返回 " + fakeList.size() + " 个虚假应用");

        } catch (Throwable t) {
            ReaLog.log("intent_query", "桌面穿透查询异常: " + t.getMessage());
        }
    }

    private void hookBundleGetString(ClassLoader classLoader) {
        try {
            Class<?> bundleClass = Class.forName("android.os.Bundle", false, classLoader);
            XposedBridge.hookAllMethods(bundleClass, "getString", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.thisObject == null) {
                            if (param.args.length >= 2) {
                                param.setResult(param.args[1]);
                            } else {
                                param.setResult("");
                            }
                            return;
                        }
                        Bundle bundle = (Bundle) param.thisObject;
                        try {
                            bundle.hashCode();
                        } catch (Throwable e) {
                            if (param.args.length >= 2) {
                                param.setResult(param.args[1]);
                            } else {
                                param.setResult("");
                            }
                            return;
                        }
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
                    } catch (Throwable t) {
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        // 只在 thisObject 为空时兜底，不动“key 存在但值为 null”的合法情况
                        if (param.thisObject == null && param.getResult() == null) {
                            if (param.args.length >= 2) {
                                param.setResult(param.args[1]);
                            } else {
                                param.setResult("");
                            }
                        }
                    } catch (Throwable t) {
                    }
                }
            });

            XposedBridge.hookAllMethods(bundleClass, "getString", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args.length == 2 && param.args[0] instanceof String) {
                            if (param.thisObject == null) {
                                param.setResult(param.args[1]);
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
                    } catch (Throwable t) {
                    }
                }
            });
        } catch (Throwable t) {
            ReaLog.log("system", "Bundle失败: " + t.getMessage());
        }
    }

    private void hookBundleEmptyInstance(ClassLoader classLoader) {
        try {
            Class<?> bundleClass = Class.forName("android.os.Bundle", false, classLoader);
            XposedHelpers.findAndHookConstructor(bundleClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getResult() == null) return;
                    Bundle bundle = (Bundle) param.getResult();
                    try {
                        bundle.putBoolean("__hook_protected", true);
                    } catch (Throwable ignored) {
                    }
                }
            });

            String[] methods = {"getString", "getInt", "getBoolean", "getLong",
                    "getDouble", "getFloat", "getBundle", "getSerializable",
                    "getParcelable", "getParcelableArrayList", "getStringArrayList",
                    "getIntegerArrayList", "getBooleanArray", "containsKey"
            };
            for (final String methodName : methods) {
                try {
                    XposedHelpers.findAndHookMethod(bundleClass, methodName, String.class,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param)
                                        throws Throwable {
                                    if (param.thisObject == null) {
                                        setDefaultReturnValue(param);
                                        return;
                                    }
                                    Bundle bundle = (Bundle) param.thisObject;
                                    try {
                                        bundle.hashCode();
                                    } catch (Throwable e) {
                                        setDefaultReturnValue(param);
                                    }
                                }

                                private void setDefaultReturnValue(MethodHookParam param) {
                                    if (param.method instanceof Method) {
                                        Class<
                                                ?> returnType = ((Method) param.method).getReturnType();
                                        if (returnType == String.class) param.setResult("");
                                        else if (returnType == int.class) param.setResult(0);
                                        else if (returnType == long.class) param.setResult(0L);
                                        else if (returnType == boolean.class)
                                            param.setResult(false);
                                        else if (returnType == double.class) param.setResult(0.0);
                                        else if (returnType == float.class) param.setResult(0.0f);
                                        else param.setResult(null);
                                    }
                                }
                            });
                } catch (Throwable ignored) {
                }
                try {
                    XposedHelpers.findAndHookMethod(bundleClass, methodName, String.class, Object.class,
                            new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param)
                                        throws Throwable {
                                    if (param.thisObject == null) {
                                        param.setResult(param.args[1]);
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
                            });
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            ReaLog.log("system", "HookBundle失败: " + t.getMessage());
        }
    }

    private void hookSoPathInApp(ClassLoader classLoader) {
        try {
            Context ctx = getApplicationContext();
            if (ctx == null) {
                ReaLog.log("system", "so路径修复: 无法获取 Context");
                return;
            }
            ApplicationInfo ai = ctx.getApplicationInfo();
            if (ai == null) {
                ReaLog.log("system", "so路径修复: ApplicationInfo 为空");
                return;
            }

            String currentLibDir = ai.nativeLibraryDir;

            if (currentLibDir != null &&
                    currentLibDir.matches(".*/lib/(arm64|arm|x86|x86_64|armeabi|armeabi-v7a|mips|mips64)$")) {
                if (new File(currentLibDir).isDirectory()) {
                    ReaLog.log("system", "so路径修复: 无需修复: " + currentLibDir);
                    return;
                }
            }

            File sourceFile = new File(ai.sourceDir);
            File parent = sourceFile.getParentFile();
            if (parent == null) {
                ReaLog.log("system", "so路径修复: 无法获取 parent 目录");
                return;
            }

            String abi = null;
            try {
                abi = (String) XposedHelpers.getObjectField(ai, "primaryCpuAbi");
            } catch (Throwable t) {
                ReaLog.log("system", "so路径修复: 获取 primaryCpuAbi 失败: " + t.getMessage());
            }
            if (abi == null || abi.isEmpty()) {
                if (Build.SUPPORTED_ABIS != null && Build.SUPPORTED_ABIS.length > 0) {
                    abi = Build.SUPPORTED_ABIS[0];
                }
            }

            File libRoot = new File(parent, "lib");
            File targetDir = null;

            if (abi != null) {
                File abiDir = new File(libRoot, abi);
                if (abiDir.isDirectory() && abiDir.listFiles() != null && abiDir.listFiles().length > 0) {
                    targetDir = abiDir;
                }
            }

            if (targetDir == null && libRoot.isDirectory()) {
                File[] subs = libRoot.listFiles();
                if (subs != null) {
                    for (File sub : subs) {
                        if (sub.isDirectory() && sub.listFiles() != null && sub.listFiles().length > 0) {
                            targetDir = sub;
                            break;
                        }
                    }
                }
            }

            if (targetDir == null) {
                ReaLog.log("system", "so路径修复: 未找到有效 lib 目录，跳过。当前=" + currentLibDir);
                return;
            }

            String correctLibDir = targetDir.getAbsolutePath();
            if (!correctLibDir.equals(currentLibDir)) {
                XposedHelpers.setObjectField(ai, "nativeLibraryDir", correctLibDir);
                ReaLog.log("system", "so路径已修复: " + currentLibDir + " -> " + correctLibDir);
            } else {
                ReaLog.log("system", "so路径修复: 计算结果与原值一致，无需修改: " + correctLibDir);
            }
        } catch (Throwable t) {
            ReaLog.log("system", "so路径修复异常: " + t.getMessage());
        }
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
                                if (!(param.thisObject instanceof Class)) return;
                                String methodName = (String) param.args[0];

                                if (methodName == null) return;
                                if (!methodName.contains("getPackageInfo") &&
                                        !methodName.contains("getApplicationInfo") &&
                                        !methodName.contains("getInstalledPackages") &&
                                        !methodName.contains("getInstalledApplications") &&
                                        !methodName.contains("AsUser") &&
                                        !methodName.contains("hidden") &&
                                        !methodName.contains("internal")) {
                                    return;
                                }

                                Class<?> targetClass = (Class<?>) param.thisObject;
                                String className = targetClass.getName();
                                boolean isPackageManagerRelated = className.equals("android.content.pm.PackageManager") ||
                                        className.equals("android.app.ApplicationPackageManager") ||
                                        className.contains("PackageManager");

                                if (isPackageManagerRelated) {
                                    ReaLog.log("system_low", "反射监控：拦截: " + className + "#" + methodName);
                                    param.setThrowable(new NoSuchMethodException(methodName));
                                }
                            } catch (Throwable t) {
                                ReaLog.log("system_low", "反射监控异常: " + t.getMessage());
                            }
                        }
                    }
            );
            ReaLog.log("system_low", "反射监控Hook初始化成功");
        } catch (Throwable t) {
            ReaLog.log("system_low", "Hook反射监控初始化失败: " + t.getMessage());
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
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String filePath = (String) param.args[0];
                            if (filePath.contains("/data/system/packages.xml") ||
                                    filePath.contains("/data/system/packages.list") ||
                                    filePath.contains("com.android.settings/databases/apps.db")) {
                                param.setThrowable(new SecurityException("权限不足，无法读取"));
                            }
                        }
                    }
            );
        } catch (Throwable t) {
        }
    }

    private void hookSystemProperties(ClassLoader classLoader) {
        try {
            Class<
                    ?> clazz = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader);
            if (clazz == null) {
                return;
            }

            final String targetPkg = currentTargetApp;
            if (targetPkg == null || targetPkg.isEmpty()) {
                ReaLog.log("intent_query", "系统属性：当前目标包名为空，跳过");
                return;
            }

            try {
                XposedHelpers.findAndHookMethod(clazz, "get", String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                String key = (String) param.args[0];
                                String value = (String) param.getResult();
                                if (value != null && !value.isEmpty() && value.contains(targetPkg)) {
                                    String filtered = filterPackageFromProperty(value, targetPkg);
                                    param.setResult(filtered);
                                    ReaLog.log("intent_query", "系统属性: " + key + " 过滤包名");
                                }
                            }
                        }
                );
            } catch (Throwable ignored) {
                ReaLog.log("intent_query", "系统属性(单参数) Hook异常: " + ignored.getMessage());
            }

            try {
                XposedHelpers.findAndHookMethod(clazz, "get", String.class, String.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                String key = (String) param.args[0];
                                String value = (String) param.getResult();
                                if (value != null && !value.isEmpty() && value.contains(targetPkg)) {
                                    String filtered = filterPackageFromProperty(value, targetPkg);
                                    param.setResult(filtered);
                                    ReaLog.log("intent_query", "系统属性(双参数): " + key + " 过滤包名");
                                }
                            }
                        }
                );
            } catch (Throwable ignored) {
                ReaLog.log("intent_query", "系统属性(双参数) Hook异常: " + ignored.getMessage());
            }

            try {
                XposedHelpers.findAndHookMethod(clazz, "getInt", String.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param)
                                    throws Throwable {
                                String key = (String) param.args[0];
                                if (key != null && key.toLowerCase().contains("package")) {
                                    param.setResult(param.args[1]);
                                    ReaLog.log("intent_query", "系统属性: " + key + " 返回默认值");
                                }
                            }
                        }
                );
            } catch (Throwable ignored) {
                ReaLog.log("intent_query", "系统属性 Hook异常: " + ignored.getMessage());
            }
        } catch (Throwable t) {
            log("❌ Hook 系统属性失败: " + t.getMessage());
            ReaLog.log("intent_query", "系统属性异常: " + t.getMessage());
        }
    }

    private String filterPackageFromProperty(String value, String targetPkg) {
        ReaLog.log("intent_query", "过滤属性中的包名: target=" + targetPkg);
        if (value == null || value.isEmpty() || targetPkg == null || targetPkg.isEmpty()) {
            ReaLog.log("intent_query", "属性过滤: 值或目标包名为空，返回原值");
            return value;
        }
        String[] delimiters = {":", ",", ";", " "};
        for (String delim : delimiters) {
            if (value.contains(delim)) {
                String[] parts = value.split(delim);
                StringBuilder filtered = new StringBuilder();
                for (String part : parts) {
                    String trimmed = part.trim();
                    if (!trimmed.contains(targetPkg)) {
                        if (filtered.length() > 0) filtered.append(delim);
                        filtered.append(trimmed);
                    } else {
                        ReaLog.log("intent_query", "属性过滤: 移除包名 " + trimmed);
                    }
                }
                return filtered.toString();
            }
        }
        if (value.contains(targetPkg)) {
            ReaLog.log("intent_query", "属性过滤: 移除单一包名");
            return "";
        }
        return value;
    }

    public void initForEmbed(ClassLoader classLoader, String targetPackageName, Context context) {
        ReaLog.log("system", "内嵌模式初始化开始: " + targetPackageName);
        currentTargetApp = targetPackageName;

        try {
            loadConfigFromFile();
            ReaLog.log("system", "内嵌模式配置加载完成: " + targetPackageName);
        } catch (Throwable e) {
            createDefaultConfig();
            ReaLog.log("system", "内嵌模式创建默认配置: " + targetPackageName);
        }

        Context appContext = context;
        if (appContext == null) {
            try {
                appContext = (Context) XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass("android.app.ActivityThread", null),
                                "currentApplication");
            } catch (Throwable e) {
            }
        }
        if (appContext == null) {
            try {
                appContext = (Context) XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass("android.app.AppGlobals", null),
                                "getInitialApplication");
            } catch (Throwable e) {
            }
        }
        ClassLoader realClassLoader = classLoader;
        if (realClassLoader == null && appContext != null) {
            realClassLoader = appContext.getClassLoader();
        }
        if (realClassLoader == null) {
            realClassLoader = getClass().getClassLoader();
        }
        String realPackageName = targetPackageName;
        if (realPackageName == null || realPackageName.isEmpty()) {
            if (appContext != null) {
                realPackageName = appContext.getPackageName();
            } else {
                realPackageName = "unknown";
            }
        }

        if (appContext != null) {
            try {
                ApplicationInfo ai = appContext.getApplicationInfo();
                if (ai != null) {
                    XposedHelpers.setObjectField(ai, "nativeLibraryDir", ai.nativeLibraryDir);
                }
            } catch (Throwable ignored) {
            }
        }

        // 痕迹/代理/返回键/崩溃保护等挂载统一由 doHook → mountFeatureHooks 清单完成（带幂等守卫）
        doHook(realPackageName, realClassLoader, appContext);
        ReaLog.log("system", "内嵌模式初始化完成: " + realPackageName);
    }

    public static class HookProvider extends ContentProvider {
        private HookInit hookInstance;

        @Override
        public boolean onCreate() {
            try {
                String targetPackage = getContext() != null
                        ? getContext().getPackageName()
                        : "unknown";
                ClassLoader appClassLoader = getContext() != null
                        ? getContext().getClassLoader()
                        : null;
                hookInstance = new HookInit();
                hookInstance.initForEmbed(appClassLoader, targetPackage, getContext());
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public Cursor query(
                Uri uri,
                String[] projection,
                String selection,
                String[] selectionArgs,
                String sortOrder) {
            if (hookInstance != null &&
                    uri != null &&
                    uri.toString().contains("module_status")) {
                MatrixCursor cursor = new MatrixCursor(
                new String[]{"module_active", "target_app", "install_status"}
                );
                cursor.addRow(
                        new Object[]{
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
            if (hookInstance != null &&
                    uri != null &&
                    uri.toString().contains("update_config")) {
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
                        }
                    } catch (Throwable t) {
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
                String[] selectionArgs) {
            return 0;
        }
    }
}

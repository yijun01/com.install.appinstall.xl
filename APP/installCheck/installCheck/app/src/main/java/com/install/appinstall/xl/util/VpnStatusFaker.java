package com.install.appinstall.xl.util;

import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Proxy;
import android.util.Log;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.net.NetworkInterface;
import java.net.ProxySelector;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.DebugModeManager;

//网络代理hook

public class VpnStatusFaker {

    private static boolean sHookInstalled = false;
    private static final Map<String, Boolean> sEnabledMap = new ConcurrentHashMap<String, Boolean>();
    private static String sCurrentPackage = null;
    private static HookInit sHookInstance = null;
    public static final String KEY_INTERFACE = "interface";
    public static final String KEY_PROXY = "proxy";
    public static final String KEY_CAPTURE = "capture";
    public static final String KEY_NETFILES = "netfiles";
    public static final String KEY_SSL_TRUST = "ssl_trust";
    public static final String KEY_SSL_CERT = "ssl_cert";
    public static final String KEY_PROXY_ENV = "proxy_env";
    public static final String KEY_NET_DETECT = "net_detect";
    public static final String KEY_SSL_PINNING = "ssl_pinning";
    public static final String KEY_SSL_CERT_HIDE = "ssl_cert_hide";
    public static final String KEY_CMD = "cmd";
    private static final int TYPE_VPN = 17;
    private static final int TYPE_WIFI = 1;
    private static final int TRANSPORT_VPN = 4;
    private static final int NET_CAPABILITY_NOT_VPN = 15;
    // ========== 错误计数（内存，每次启动重置） ==========
    // 错误计数委托 BuHook 统一熔断器（key 前缀 vpn:），请与 VPN_KEYS 保持同步
    private static final String[] VPN_KEYS = {
            KEY_INTERFACE, KEY_PROXY, KEY_PROXY_ENV, KEY_CAPTURE, KEY_NETFILES,
            KEY_SSL_TRUST, KEY_SSL_CERT, KEY_SSL_CERT_HIDE, KEY_SSL_PINNING,
            KEY_NET_DETECT, KEY_CMD
    };
    private static final int ERROR_THRESHOLD = 2;
    private static final Set<String> VPN_KEYWORDS = new HashSet<String>(Arrays.asList(
    "tun", "tap", "ppp", "pptp", "wg", "ipsec", "l2tp","ss", "v2ray", "clash", "singbox", "shadowsocks","vpn", "ovpn"));

    private static final Set<String> CAPTURE_APPS = new HashSet<String>(Arrays.asList(
    "com.reqable.android","com.guoshi.httpcanary","com.minhui.networkcapture","com.emanuelef.remote_capture","org.proxydroid"));

    private static final Set<String> NETWORK_PATH_KEYWORDS = new HashSet<String>(Arrays.asList(
    "/proc/net", "/proc/self/net", "/sys/class/net","/net/tun", "/net/tap", "/net/ppp", "/net/vpn"));

    private static final Set<String> NET_CMD_KEYWORDS = new HashSet<String>(Arrays.asList(
    "ip link", "ifconfig", "netstat -r", "route", "ip addr","pm path", "pm list packages", "dumpsys package"));
    // ========== 日志 ==========
    private static void log(String msg) {
        if (sHookInstance != null) {
            sHookInstance.log(msg);
        } else {
            XposedBridge.log(msg);
        }
    }

    // ========== 子功能状态 ==========
    public static void setEnabled(String packageName, boolean enabled) {
        if (packageName != null) {
            sEnabledMap.put(packageName, enabled);
        }
    }

    public static boolean isEnabled(String packageName) {
        // 运行期总开关：与 BuHook 注册侧 vpn_fake 门控保持一致。
        // 非调试模式下 isFeatureEnabled 恒 true（全开）
        return DebugModeManager.isFeatureEnabled("vpn_fake");
    }

    private static boolean isSubFeatureEnabled(String key) {
        // ===== 调试模式优先 =====
        if (DebugModeManager.isDebugModeActive()) {
            String debugKey = null;
            if (KEY_INTERFACE.equals(key)) debugKey = "vpn_interface";
            else if (KEY_PROXY.equals(key)) debugKey = "vpn_proxy";
            else if (KEY_PROXY_ENV.equals(key)) debugKey = "vpn_proxy_env";
            else if (KEY_CAPTURE.equals(key)) debugKey = "vpn_capture";
            else if (KEY_NETFILES.equals(key)) debugKey = "vpn_netfiles";
            else if (KEY_NET_DETECT.equals(key)) debugKey = "vpn_net_detect";
            else if (KEY_SSL_TRUST.equals(key)) debugKey = "vpn_ssl_trust";
            else if (KEY_SSL_PINNING.equals(key)) debugKey = "vpn_ssl_pinning";
            else if (KEY_SSL_CERT.equals(key)) debugKey = "vpn_ssl_cert";
            else if (KEY_SSL_CERT_HIDE.equals(key)) debugKey = "vpn_ssl_cert_hide";
            else if (KEY_CMD.equals(key)) debugKey = "vpn_cmd";
            if (debugKey != null) {
                return DebugModeManager.isFeatureEnabled(debugKey);
            }
            return true; // 未映射的默认开启
        }
        
        if (sHookInstance == null || sCurrentPackage == null) return false;
        Map<String, Boolean> detail = HookInit.vpnFakeDetailMap.get(sCurrentPackage);
        if (detail != null && detail.containsKey(key)) {
            return detail.get(key);
        }
        Map<String, Boolean> defaultMap = HookInit.getDefaultVpnDetailMap();
        return defaultMap.getOrDefault(key, false);
    }

    private static void disableSubFeature(String key) {
        if (sHookInstance == null || sCurrentPackage == null) return;
        try {
            Map<String, Boolean> detail = HookInit.vpnFakeDetailMap.get(sCurrentPackage);
            if (detail == null) {
                detail = new HashMap<String, Boolean>();
                HookInit.vpnFakeDetailMap.put(sCurrentPackage, detail);
            }
            detail.put(key, false);
            sHookInstance.saveConfigToFile();
            ReaLog.vpn("error", "网络代理子功能自动禁用", key);
        } catch (Throwable t) {
            log("禁用网络代理子功能保存失败: " + t.getMessage());
        }
    }

    // ========== 错误计数与自动禁用 ==========
    private static boolean checkAndDisableOnStartup(String featureKey, boolean enabled) {
        if (!enabled) return true;
        int count = BuHook.errorCount("vpn:" + featureKey);
        if (count >= ERROR_THRESHOLD) {
            disableSubFeature(featureKey);
            ReaLog.vpn("auto_disable", "启动时自动禁用 " + featureKey, "错误计数=" + count);
            return true;
        }
        return false;
    }

    private static boolean recordErrorAndMaybeDisable(String featureKey, Throwable t) {
        int n = BuHook.recordError("vpn:" + featureKey, t, ERROR_THRESHOLD, 0L, true);
        if (n == -1) {
            ReaLog.vpn("warn", featureKey + " 可忽略异常", t.getClass().getName());
            return false;
        }
        if (n >= ERROR_THRESHOLD) {
            disableSubFeature(featureKey);
            ReaLog.vpn("error", featureKey + " 达到阈值，自动禁用", "计数=" + n);
            return true;
        }
        ReaLog.vpn("warn", featureKey + " 发生异常，计数=" + n, t.getMessage());
        return false;
    }

    private static void installFeature(String featureKey, boolean enabled, Runnable installAction) {
        if (!enabled) return;
        if (checkAndDisableOnStartup(featureKey, enabled)) {
            return;
        }
        try {
            installAction.run();
            // ReaLog.vpn("hook", featureKey + " 安装成功", "");
        } catch (Throwable t) {
            boolean disabled = recordErrorAndMaybeDisable(featureKey, t);
            if (!disabled) {
                ReaLog.vpn("warn", featureKey + " 安装失败", t.getMessage());
            }
        }
    }

    // ========== 安装入口 ==========
    public static void installForEmbed(final ClassLoader classLoader, HookInit hookInstance, String packageName) {
        if (sHookInstalled) return;
        sCurrentPackage = packageName;
        sHookInstance = hookInstance;
        for (String k : VPN_KEYS) BuHook.clearErrorCount("vpn:" + k);

        ReaLog.vpn("status", "网络代理功能状态: interface=" + isSubFeatureEnabled(KEY_INTERFACE)
                   + ", proxy=" + isSubFeatureEnabled(KEY_PROXY)
                   + ", proxy_env=" + isSubFeatureEnabled(KEY_PROXY_ENV)
                   + ", capture=" + isSubFeatureEnabled(KEY_CAPTURE)
                   + ", netfiles=" + isSubFeatureEnabled(KEY_NETFILES)
                   + ", ssl_trust=" + isSubFeatureEnabled(KEY_SSL_TRUST)
                   + ", ssl_cert=" + isSubFeatureEnabled(KEY_SSL_CERT)
                   + ", ssl_cert_hide=" + isSubFeatureEnabled(KEY_SSL_CERT_HIDE)
                   + ", ssl_pinning=" + isSubFeatureEnabled(KEY_SSL_PINNING)
                   + ", net_detect=" + isSubFeatureEnabled(KEY_NET_DETECT)
                   + ", cmd=" + isSubFeatureEnabled(KEY_CMD), "");

        installFeature(KEY_INTERFACE, isSubFeatureEnabled(KEY_INTERFACE), new Runnable() {
                public void run() {
                    try { hookNetworkInterfaceLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                    try { hookNetworkCapabilitiesLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                    try { hookLinkPropertiesLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                    try { hookNetworkInfoLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                    try { hookVpnEnhance(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                }
            });

        installFeature(KEY_PROXY, isSubFeatureEnabled(KEY_PROXY), new Runnable() {
                public void run() { try { hookProxyLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_PROXY_ENV, isSubFeatureEnabled(KEY_PROXY_ENV), new Runnable() {
                public void run() { try { hookProxyEnhance(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_CAPTURE, isSubFeatureEnabled(KEY_CAPTURE), new Runnable() {
                public void run() { try { hookCaptureAppsLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_NETFILES, isSubFeatureEnabled(KEY_NETFILES), new Runnable() {
                public void run() { try { hookNetworkFilesLayer(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_SSL_TRUST, isSubFeatureEnabled(KEY_SSL_TRUST), new Runnable() {
                public void run() { try { hookSSLTrustManager(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_SSL_CERT, isSubFeatureEnabled(KEY_SSL_CERT), new Runnable() {
                public void run() { try { hookSSLCertificate(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_SSL_CERT_HIDE, isSubFeatureEnabled(KEY_SSL_CERT_HIDE), new Runnable() {
                public void run() { try { hookCertTextHide(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_SSL_PINNING, isSubFeatureEnabled(KEY_SSL_PINNING), new Runnable() {
                public void run() {
                    try { hookMessageDigestBypass(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                    try { hookObfuscatedPinners(classLoader); } catch (Throwable t) { throw new RuntimeException(t); }
                }
            });

        installFeature(KEY_NET_DETECT, isSubFeatureEnabled(KEY_NET_DETECT), new Runnable() {
                public void run() { try { hookNetworkDetectMonitor(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        installFeature(KEY_CMD, isSubFeatureEnabled(KEY_CMD), new Runnable() {
                public void run() { try { hookCommandLine(classLoader); } catch (Throwable t) { throw new RuntimeException(t); } }
            });

        sHookInstalled = true;
        ReaLog.vpn("status", "网络代理总安装完成", "");
    }

    public static void install(XC_LoadPackage.LoadPackageParam lpparam,
                               ClassLoader classLoader,
                               HookInit hookInstance) {
        installForEmbed(classLoader, hookInstance, lpparam.packageName);
    }

    // ============================================================
    // 以下为所有 Hook 方法的完整实现
    // ============================================================
    private static boolean isVpnInterface(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        for (String kw : VPN_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private static boolean isNetworkDetectionCommand(String cmd) {
        if (cmd == null) return false;
        String lower = cmd.toLowerCase();
        return lower.contains("pm ") || lower.contains("dumpsys") ||
            lower.contains("ip ") || lower.contains("ifconfig") ||
            lower.contains("netstat") || lower.contains("route") ||
            lower.contains("cmd package") || lower.contains("pm path") ||
            lower.contains("getprop") || lower.contains("ps ") ||
            lower.contains("ls /data/app") || lower.contains("cat /data/system/packages");
    }

    // ---- 命令拦截 ----
    private static void hookCommandLine(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CMD)) return;
        ReaLog.vpn("hook", "安装 CommandLine Hook", "");

        XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class,
            new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CMD)) return;
                    try {
                        String cmd = (String) param.args[0];
                        if (isNetworkDetectionCommand(cmd)) {
                            ReaLog.vpn("cmd", "Runtime.exec 拦截命令", cmd);
                            param.setResult(createFakeProcess(cmd));
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_CMD, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(Runtime.class, "exec", String[].class,
            new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CMD)) return;
                    try {
                        String[] cmds = (String[]) param.args[0];
                        String full = String.join(" ", cmds);
                        if (isNetworkDetectionCommand(full)) {
                            ReaLog.vpn("cmd", "Runtime.exec(String[]) 拦截命令", full);
                            param.setResult(createFakeProcess(full));
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_CMD, t);
                    }
                }
            });

        try {
            XposedHelpers.findAndHookMethod(Runtime.class, "exec", String.class, String[].class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CMD)) return;
                        try {
                            String cmd = (String) param.args[0];
                            if (isNetworkDetectionCommand(cmd)) {
                                ReaLog.vpn("cmd", "Runtime.exec(String,String[]) 拦截命令", cmd);
                                param.setResult(createFakeProcess(cmd));
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_CMD, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(Runtime.class, "exec", String[].class, String[].class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CMD)) return;
                        try {
                            String[] cmds = (String[]) param.args[0];
                            String full = String.join(" ", cmds);
                            if (isNetworkDetectionCommand(full)) {
                                ReaLog.vpn("cmd", "Runtime.exec(String[],String[]) 拦截命令", full);
                                param.setResult(createFakeProcess(full));
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_CMD, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(ProcessBuilder.class, "start",
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CMD)) return;
                        try {
                            ProcessBuilder pb = (ProcessBuilder) param.thisObject;
                            List<String> cmdList = pb.command();
                            if (cmdList != null && !cmdList.isEmpty()) {
                                String full = String.join(" ", cmdList);
                                if (isNetworkDetectionCommand(full)) {
                                    ReaLog.vpn("cmd", "ProcessBuilder.start 拦截命令", full);
                                    param.setResult(createFakeProcess(full));
                                }
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_CMD, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }

    private static Object createFakeProcess(final String command) {
        ReaLog.vpn("cmd", "创建虚假 Process", command);
        try {
            return new Process() {
                private final ByteArrayInputStream inputStream;
                private final ByteArrayInputStream errorStream;
                {
                    String fakeOutput = generateFakeCommandOutput(command);
                    this.inputStream = new ByteArrayInputStream(fakeOutput.getBytes("UTF-8"));
                    this.errorStream = new ByteArrayInputStream(new byte[0]);
                }
                public OutputStream getOutputStream() {
                    return new OutputStream() {
                        public void write(int b) throws IOException {}
                    };
                }
                public InputStream getInputStream() {
                    return inputStream;
                }
                public InputStream getErrorStream() {
                    return errorStream;
                }
                public int waitFor() throws InterruptedException {
                    return 0;
                }
                public int exitValue() {
                    return 0;
                }
                public void destroy() {}
                public String toString() {
                    return "FakeProcess[cmd=" + command + "]";
                }
            };
        } catch (Throwable e) {
            ReaLog.vpn("error", "创建虚假 Process 失败", e.getMessage());
            return new Process() {
                public OutputStream getOutputStream() { return new OutputStream() { public void write(int b) {} }; }
                public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
                public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
                public int waitFor() { return 0; }
                public int exitValue() { return 0; }
                public void destroy() {}
            };
        }
    }

    private static String generateFakeCommandOutput(String command) {
        if (command == null) return "";
        String lower = command.toLowerCase();

        if (lower.contains("pm list packages")) {
            StringBuilder sb = new StringBuilder();
            if (sHookInstance != null) {
                Set<String> allPackages = new HashSet<String>(HookInit.globalCapturedPackages);
                List<String> userPkgs = HookInit.userDefinedPackagesMap.getOrDefault(sCurrentPackage, new ArrayList<String>());
                allPackages.addAll(userPkgs);
                List<String> excluded = HookInit.excludedPackagesMap.getOrDefault(sCurrentPackage, new ArrayList<String>());
                allPackages.removeAll(excluded);
                for (String pkg : allPackages) {
                    int status = sHookInstance.getPackageStatus(pkg);
                    if (status == 0) continue;
                    sb.append("package:").append(pkg).append("\n");
                }
            }
            if (sb.length() == 0) sb.append("package:com.android.chrome\n");
            ReaLog.vpn("cmd", "pm list packages 返回", sb.length() + " 字节");
            return sb.toString();
        }

        if (lower.contains("pm path ")) {
            String pkg = extractPackageFromCommand(command);
            if (pkg != null && sHookInstance != null) {
                int status = sHookInstance.getPackageStatus(pkg);
                if (status == 0) {
                    ReaLog.vpn("cmd", "pm path " + pkg + " -> 固定未安装", "");
                    return "Error: package not found";
                }
                if (status == 1) {
                    ReaLog.vpn("cmd", "pm path " + pkg + " -> 固定已安装", "");
                    return "package:/data/app/" + pkg.replace('.', '-') + "-1/base.apk";
                }
                Boolean global = HookInit.installStatusMap.get(sCurrentPackage);
                if (global != null && !global) {
                    ReaLog.vpn("cmd", "pm path " + pkg + " -> 全局未安装", "");
                    return "Error: package not found";
                }
                List<String> userPkgs = HookInit.userDefinedPackagesMap.getOrDefault(sCurrentPackage, new ArrayList<String>());
                if (userPkgs.contains(pkg) || HookInit.globalCapturedPackages.contains(pkg)) {
                    ReaLog.vpn("cmd", "pm path " + pkg + " -> 虚假路径", "");
                    return "package:/data/app/" + pkg.replace('.', '-') + "-1/base.apk";
                }
            }
            ReaLog.vpn("cmd", "pm path 未匹配，返回未找到", "");
            return "Error: package not found";
        }

        if (lower.contains("dumpsys package")) {
            ReaLog.vpn("cmd", "dumpsys package 返回 No package found", "");
            return "No package found";
        }

        if (lower.contains("ip link") || lower.contains("ip addr") ||
            lower.contains("ip route") || lower.contains("ip rule")) {
            ReaLog.vpn("cmd", "ip 命令返回虚假网络配置", "");
            return "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN mode DEFAULT\n" +
                "    inet 127.0.0.1/8 scope host lo\n" +
                "2: wlan0: <BROADCAST,MULTICAST,UP,LOWER_UP> mtu 1500 qdisc mq state UP mode DORMANT\n" +
                "    inet 192.168.1.100/24 brd 192.168.1.255 scope global wlan0\n" +
                "3: dummy0: <BROADCAST,NOARP> mtu 1500 qdisc noop state DOWN mode DEFAULT\n";
        }

        if (lower.contains("ifconfig")) {
            ReaLog.vpn("cmd", "ifconfig 返回虚假网络配置", "");
            return "lo: flags=73<UP,LOOPBACK,RUNNING>  mtu 65536\n" +
                "        inet 127.0.0.1  netmask 255.0.0.0\n" +
                "wlan0: flags=4163<UP,BROADCAST,RUNNING,MULTICAST>  mtu 1500\n" +
                "        inet 192.168.1.100  netmask 255.255.255.0  broadcast 192.168.1.255\n";
        }

        if (lower.contains("netstat -r") || lower.contains("route")) {
            ReaLog.vpn("cmd", "netstat -r 返回虚假路由表", "");
            return "Kernel IP routing table\n" +
                "Destination     Gateway         Genmask         Flags   MSS Window  irtt Iface\n" +
                "0.0.0.0         192.168.1.1     0.0.0.0         UG        0 0          0 wlan0\n";
        }

        if (lower.contains("getprop")) {
            ReaLog.vpn("cmd", "getprop 返回虚假属性", "");
            return "[ro.build.version.sdk]: [30]\n[ro.product.manufacturer]: [Xiaomi]\n";
        }

        if (lower.contains("ls ") && lower.contains("/data/app")) {
            StringBuilder sb = new StringBuilder();
            Set<String> allPkgs = new HashSet<String>(HookInit.globalCapturedPackages);
            allPkgs.addAll(HookInit.userDefinedPackagesMap.getOrDefault(sCurrentPackage, new ArrayList<String>()));
            for (String pkg : allPkgs) {
                if (sHookInstance != null && sHookInstance.getPackageStatus(pkg) == 0) continue;
                sb.append(pkg.replace('.', '-')).append("-1\n");
            }
            if (sb.length() == 0) sb.append("com.android.chrome-1\n");
            ReaLog.vpn("cmd", "ls /data/app 返回", sb.length() + " 字节");
            return sb.toString();
        }

        if (lower.contains("cat") && lower.contains("packages.xml")) {
            ReaLog.vpn("cmd", "cat packages.xml 返回空内容", "");
            return "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<packages>\n</packages>";
        }

        if (lower.contains("ps")) {
            ReaLog.vpn("cmd", "ps 返回虚假进程列表", "");
            return "USER      PID   PPID  VSIZE  RSS   WCHAN            PC  NAME\n" +
                "system    100   1     2345   678   SyS_epoll_ 00000000 S system_server\n";
        }

        ReaLog.vpn("cmd", "未识别的命令", command + "，返回空");
        return "";
    }

    private static String extractPackageFromCommand(String command) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:pm\\s+path|dumpsys\\s+package)\\s+([a-zA-Z0-9._]+)");
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

    // ---- NetworkInterface 层 ----
    private static void hookNetworkInterfaceLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
        ReaLog.vpn("hook", "安装 NetworkInterface 层", "");
        Class<?> niClass = NetworkInterface.class;

        XposedHelpers.findAndHookMethod(niClass, "getNetworkInterfaces",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        @SuppressWarnings("unchecked")
                            Enumeration<NetworkInterface> original = (Enumeration<NetworkInterface>) param.getResult();
                        if (original == null) return;
                        List<NetworkInterface> filtered = new ArrayList<NetworkInterface>();
                        while (original.hasMoreElements()) {
                            NetworkInterface ni = original.nextElement();
                            if (!isVpnInterface(ni.getName())) {
                                filtered.add(ni);
                            } else {
                                ReaLog.vpn("interface", "过滤VPN接口", ni.getName());
                            }
                        }
                        param.setResult(Collections.enumeration(filtered));
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "getName",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        String name = (String) param.getResult();
                        if (isVpnInterface(name)) {
                            ReaLog.vpn("interface", "NetworkInterface.getName 替换", name + " -> wlan0");
                            param.setResult("wlan0");
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "isUp",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        NetworkInterface ni = (NetworkInterface) param.thisObject;
                        if (isVpnInterface(ni.getName())) {
                            ReaLog.vpn("interface", "NetworkInterface.isUp 替换", ni.getName() + " -> false");
                            param.setResult(false);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "isVirtual",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        NetworkInterface ni = (NetworkInterface) param.thisObject;
                        if (isVpnInterface(ni.getName())) {
                            ReaLog.vpn("interface", "NetworkInterface.isVirtual 替换", ni.getName() + " -> false");
                            param.setResult(false);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "isPointToPoint",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        NetworkInterface ni = (NetworkInterface) param.thisObject;
                        if (isVpnInterface(ni.getName())) {
                            ReaLog.vpn("interface", "NetworkInterface.isPointToPoint 替换", ni.getName() + " -> false");
                            param.setResult(false);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "getByName", String.class,
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        String name = (String) param.args[0];
                        if (isVpnInterface(name)) {
                            ReaLog.vpn("interface", "NetworkInterface.getByName 替换", name + " -> null");
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });
    }

    // ---- NetworkCapabilities ----
    private static void hookNetworkCapabilitiesLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
        ReaLog.vpn("hook", "安装 NetworkCapabilities 层", "");
        Class<?> ncClass = Class.forName("android.net.NetworkCapabilities");

        XposedHelpers.findAndHookMethod(ncClass, "hasTransport", int.class,
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        int transport = (Integer) param.args[0];
                        if (transport == TRANSPORT_VPN) {
                            ReaLog.vpn("interface", "NetworkCapabilities.hasTransport(TRANSPORT_VPN) -> false", "");
                            param.setResult(false);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(ncClass, "hasCapability", int.class,
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        int cap = (Integer) param.args[0];
                        if (cap == NET_CAPABILITY_NOT_VPN) {
                            ReaLog.vpn("interface", "NetworkCapabilities.hasCapability(NET_CAPABILITY_NOT_VPN) -> true", "");
                            param.setResult(true);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });
    }

    // ---- LinkProperties ----
    private static void hookLinkPropertiesLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
        ReaLog.vpn("hook", "安装 LinkProperties 层", "");
        Class<?> lpClass = Class.forName("android.net.LinkProperties");

        XposedHelpers.findAndHookMethod(lpClass, "getRoutes",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        @SuppressWarnings("unchecked")
                            List<Object> routes = (List<Object>) param.getResult();
                        if (routes == null || routes.isEmpty()) return;
                        List<Object> filtered = new ArrayList<Object>();
                        for (Object route : routes) {
                            try {
                                String iface = (String) XposedHelpers.getObjectField(route, "mInterface");
                                if (iface != null && isVpnInterface(iface)) {
                                    ReaLog.vpn("interface", "LinkProperties.getRoutes 过滤VPN路由", iface);
                                } else {
                                    filtered.add(route);
                                }
                            } catch (Throwable e) {
                                filtered.add(route);
                            }
                        }
                        if (!filtered.isEmpty()) param.setResult(filtered);
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(lpClass, "getInterfaceName",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        String name = (String) param.getResult();
                        if (isVpnInterface(name)) {
                            ReaLog.vpn("interface", "LinkProperties.getInterfaceName 替换", name + " -> wlan0");
                            param.setResult("wlan0");
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });
    }

    // ---- NetworkInfo ----
    private static void hookNetworkInfoLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
        ReaLog.vpn("hook", "安装 NetworkInfo 层", "");
        Class<?> niClass = Class.forName("android.net.NetworkInfo");

        XposedHelpers.findAndHookMethod(niClass, "getType",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        int type = (Integer) param.getResult();
                        if (type == TYPE_VPN) {
                            ReaLog.vpn("interface", "NetworkInfo.getType VPN -> WIFI", "");
                            param.setResult(TYPE_WIFI);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "getSubtype",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        int subtype = (Integer) param.getResult();
                        if (subtype == TYPE_VPN) {
                            ReaLog.vpn("interface", "NetworkInfo.getSubtype VPN -> WIFI", "");
                            param.setResult(TYPE_WIFI);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "getTypeName",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        String name = (String) param.getResult();
                        if ("VPN".equals(name)) {
                            ReaLog.vpn("interface", "NetworkInfo.getTypeName VPN -> WIFI", "");
                            param.setResult("WIFI");
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "getSubtypeName",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        String name = (String) param.getResult();
                        if ("VPN".equals(name)) {
                            ReaLog.vpn("interface", "NetworkInfo.getSubtypeName VPN -> WIFI", "");
                            param.setResult("WIFI");
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "isConnected",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        NetworkInfo ni = (NetworkInfo) param.thisObject;
                        if (ni.getType() == TYPE_VPN) {
                            ReaLog.vpn("interface", "NetworkInfo.isConnected VPN -> false", "");
                            param.setResult(false);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "isAvailable",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        NetworkInfo ni = (NetworkInfo) param.thisObject;
                        if (ni.getType() == TYPE_VPN) {
                            ReaLog.vpn("interface", "NetworkInfo.isAvailable VPN -> false", "");
                            param.setResult(false);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });

        XposedHelpers.findAndHookMethod(niClass, "getExtraInfo",
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                    try {
                        String extra = (String) param.getResult();
                        if (isVpnInterface(extra)) {
                            ReaLog.vpn("interface", "NetworkInfo.getExtraInfo 替换", extra + " -> null");
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                    }
                }
            });
    }

    // ---- 代理隐藏 ----
    private static void hookProxyLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY)) return;
        ReaLog.vpn("hook", "安装 Proxy 层", "");

        try {
            XposedHelpers.findAndHookMethod(Proxy.class, "getHost", Context.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY)) return;
                        try {
                            ReaLog.vpn("proxy", "Proxy.getHost -> null", "");
                            param.setResult(null);
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_PROXY, t);
                        }
                    }
                });
            XposedHelpers.findAndHookMethod(Proxy.class, "getPort", Context.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY)) return;
                        try {
                            ReaLog.vpn("proxy", "Proxy.getPort -> -1", "");
                            param.setResult(-1);
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_PROXY, t);
                        }
                    }
                });
        } catch (Throwable t) { /* 忽略 */ }

        try {
            Class<?> cmClass = Class.forName("android.net.ConnectivityManager");
            XposedHelpers.findAndHookMethod(cmClass, "getDefaultProxy",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY)) return;
                        try {
                            ReaLog.vpn("proxy", "ConnectivityManager.getDefaultProxy -> null", "");
                            param.setResult(null);
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_PROXY, t);
                        }
                    }
                });
        } catch (Throwable t) { /* 忽略 */ }
    }

    // ---- 代理环境变量 ----
    private static void hookProxyEnhance(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY_ENV)) return;
        ReaLog.vpn("hook", "安装 ProxyEnhance 层", "");

        try {
            XposedHelpers.findAndHookMethod(System.class, "getProperty", String.class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY_ENV)) return;
                        try {
                            String key = (String) param.args[0];
                            if ("http.proxyHost".equals(key) || "https.proxyHost".equals(key) ||
                                "ftp.proxyHost".equals(key) || "http.proxyPort".equals(key) ||
                                "https.proxyPort".equals(key) || "ftp.proxyPort".equals(key)) {
                                ReaLog.vpn("proxy_env", "System.getProperty 拦截", key + " -> null");
                                param.setResult(null);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_PROXY_ENV, t);
                        }
                    }
                });
        } catch (Throwable t) { /* ignore */ }

        try {
            XposedHelpers.findAndHookMethod(System.class, "getenv", String.class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY_ENV)) return;
                        try {
                            String key = (String) param.args[0];
                            if (key != null && (key.equalsIgnoreCase("http_proxy") ||
                                key.equalsIgnoreCase("https_proxy") ||
                                key.equalsIgnoreCase("ftp_proxy") ||
                                key.equalsIgnoreCase("all_proxy"))) {
                                ReaLog.vpn("proxy_env", "System.getenv 拦截", key + " -> null");
                                param.setResult(null);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_PROXY_ENV, t);
                        }
                    }
                });
        } catch (Throwable t) { /* ignore */ }

        try {
            XposedHelpers.findAndHookMethod(ProxySelector.class, "select", URI.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_PROXY_ENV)) return;
                        try {
                            List<java.net.Proxy> list = (List<java.net.Proxy>) param.getResult();
                            if (list != null) {
                                for (java.net.Proxy p : list) {
                                    if (p != java.net.Proxy.NO_PROXY) {
                                        ReaLog.vpn("proxy_env", "ProxySelector.select 拦截代理", p.toString() + " -> NO_PROXY");
                                        param.setResult(Collections.singletonList(java.net.Proxy.NO_PROXY));
                                        break;
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_PROXY_ENV, t);
                        }
                    }
                });
        } catch (Throwable t) { /* ignore */ }
    }

    // ---- 抓包应用隐藏 ----
    private static void hookCaptureAppsLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CAPTURE)) return;
        ReaLog.vpn("hook", "安装 CaptureApps 层", "");
        try {
            Class<?> pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
            XposedHelpers.findAndHookMethod(pmClass, "getPackageInfo", String.class, int.class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_CAPTURE)) return;
                        try {
                            String pkg = (String) param.args[0];
                            if (CAPTURE_APPS.contains(pkg)) {
                                ReaLog.vpn("capture", "getPackageInfo 隐藏抓包应用", pkg);
                                param.setResult(null);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_CAPTURE, t);
                        }
                    }
                });
        } catch (Throwable t) {
            log("应用隐藏 Hook 失败: " + t.getMessage());
        }
    }

    // ---- 网络文件隐藏 ----
    private static void hookNetworkFilesLayer(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NETFILES)) return;
        ReaLog.vpn("hook", "安装 NetworkFiles 层", "");
        try {
            Class<?> fileClass = XposedHelpers.findClass("java.io.File", classLoader);
            XposedHelpers.findAndHookMethod(fileClass, "exists",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NETFILES)) return;
                        try {
                            File f = (File) param.thisObject;
                            String path = f.getAbsolutePath().toLowerCase();
                            for (String keyword : NETWORK_PATH_KEYWORDS) {
                                if (path.contains(keyword)) {
                                    ReaLog.vpn("netfiles", "File.exists 隐藏网络文件", path + " -> false");
                                    param.setResult(false);
                                    return;
                                }
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_NETFILES, t);
                        }
                    }
                });
        } catch (Throwable t) {
            log("文件隐藏 Hook 失败: " + t.getMessage());
        }
    }

    // ---- SSL 信任链绕过 ----
    private static void hookSSLTrustManager(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_TRUST)) return;
        ReaLog.vpn("hook", "安装 SSLTrust 层", "");

        try {
            Class<?> sslClass = XposedHelpers.findClass("javax.net.ssl.SSLContext", classLoader);
            XposedHelpers.findAndHookMethod(sslClass, "init", KeyManager[].class, TrustManager[].class, SecureRandom.class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_TRUST)) return;
                        try {
                            ReaLog.vpn("ssl_trust", "SSLContext.init 替换 TrustManager 为全信任", "");
                            TrustManager[] newTMs = new TrustManager[]{
                                new X509TrustManager() {
                                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                                }
                            };
                            p.args[1] = newTMs;
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_SSL_TRUST, t);
                        }
                    }
                });
        } catch (Throwable t) {
            throw new RuntimeException("SSL Hook 失败", t);
        }

        try {
            Class<?> cpClass = XposedHelpers.findClassIfExists("okhttp3.CertificatePinner", classLoader);
            if (cpClass != null) {
                XposedHelpers.findAndHookMethod(cpClass, "check", String.class, List.class,
                    new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_TRUST)) return;
                            try {
                                ReaLog.vpn("ssl_trust", "CertificatePinner.check 绕过", "");
                                p.setResult(null);
                            } catch (Throwable t) {
                                recordErrorAndMaybeDisable(KEY_SSL_TRUST, t);
                            }
                        }
                    });
            }
        } catch (Throwable t) {
            log("CertificatePinner失败跳过: " + t.getMessage());
        }

        try {
            Class<?> okHostVerifier = XposedHelpers.findClassIfExists("okhttp3.internal.tls.OkHostnameVerifier", classLoader);
            if (okHostVerifier == null) {
                okHostVerifier = XposedHelpers.findClassIfExists("com.android.okhttp.internal.tls.OkHostnameVerifier", classLoader);
            }
            if (okHostVerifier != null) {
                XposedHelpers.findAndHookMethod(okHostVerifier, "verify", String.class, SSLSession.class,
                    new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) {
                            if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_TRUST)) return;
                            try {
                                ReaLog.vpn("ssl_trust", "OkHostnameVerifier.verify 绕过", "");
                                p.setResult(true);
                            } catch (Throwable t) {
                                recordErrorAndMaybeDisable(KEY_SSL_TRUST, t);
                            }
                        }
                    });
            }
        } catch (Throwable t) {
            log("HostnameVerifier失败跳过: " + t.getMessage());
        }

        try {
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String hostname, SSLSession session) { return true; }
                });
        } catch (Throwable t) {
            log("设置默认HostnameVerifier失败: " + t.getMessage());
        }
    }

    // ---- SSL 证书信息隐藏 ----
    private static void hookSSLCertificate(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_CERT)) return;
        ReaLog.vpn("hook", "安装 SSLCert 层", "");

        String[] implCandidates = {
            "com.android.org.conscrypt.OpenSSLX509Certificate",
            "org.apache.harmony.security.x509.X509CertificateImpl",
            "com.android.org.bouncycastle.jce.provider.X509CertificateObject",
            "sun.security.x509.X509CertImpl"
        };
        Class<?> certImpl = null;
        for (String name : implCandidates) {
            try {
                certImpl = XposedHelpers.findClassIfExists(name, classLoader);
                if (certImpl != null) break;
            } catch (Throwable ignored) {}
        }

        if (certImpl == null) return;

        try {
            XposedHelpers.findAndHookMethod(certImpl, "getIssuerDN",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_CERT)) return;
                        try {
                            String s = p.getResult() != null ? p.getResult().toString() : "";
                            if (s.contains("reqable") || s.contains("httpcanary") ||
                                s.contains("charles") || s.contains("mitmproxy")) {
                                ReaLog.vpn("ssl_cert", "X509Certificate.getIssuerDN 隐藏", s);
                                p.setResult(null);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_SSL_CERT, t);
                        }
                    }
                });
        } catch (Throwable t) {
            log("getIssuerDN Hook 失败: " + t.getMessage());
        }

        try {
            XposedHelpers.findAndHookMethod(certImpl, "getSubjectDN",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_CERT)) return;
                        try {
                            String s = p.getResult() != null ? p.getResult().toString() : "";
                            if (s.contains("reqable") || s.contains("httpcanary") ||
                                s.contains("charles") || s.contains("mitmproxy")) {
                                ReaLog.vpn("ssl_cert", "X509Certificate.getSubjectDN 隐藏", s);
                                p.setResult(null);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_SSL_CERT, t);
                        }
                    }
                });
        } catch (Throwable t) {
            log("getSubjectDN Hook 失败: " + t.getMessage());
        }
    }

    // ---- 证书特征文本隐藏 ----
    private static void hookCertTextHide(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_CERT_HIDE)) return;
        ReaLog.vpn("hook", "安装 SSLCertHide 层", "");

        String[] implCandidates = {
            "com.android.org.conscrypt.OpenSSLX509Certificate",
            "org.apache.harmony.security.x509.X509CertificateImpl",
            "com.android.org.bouncycastle.jce.provider.X509CertificateObject",
            "sun.security.x509.X509CertImpl"
        };
        for (String name : implCandidates) {
            try {
                Class<?> certImpl = XposedHelpers.findClassIfExists(name, classLoader);
                if (certImpl == null) continue;

                XposedHelpers.findAndHookMethod(certImpl, "toString",
                    new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam p) {
                            if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_CERT_HIDE)) return;
                            try {
                                String s = (String) p.getResult();
                                if (s != null) {
                                    ReaLog.vpn("ssl_cert_hide", "X509Certificate.toString 替换证书特征", "");
                                    s = s.replaceAll("(?i)reqable", "DigiCert")
                                        .replaceAll("(?i)httpcanary", "DigiCert")
                                        .replaceAll("(?i)charles", "DigiCert")
                                        .replaceAll("(?i)mitmproxy", "DigiCert")
                                        .replaceAll("(?i)packet capture", "DigiCert");
                                    p.setResult(s);
                                }
                            } catch (Throwable t) {
                                recordErrorAndMaybeDisable(KEY_SSL_CERT_HIDE, t);
                            }
                        }
                    });
            } catch (Throwable ignored) {}
        }
    }

    // ---- VPN 增强 ----
    private static void hookVpnEnhance(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
        ReaLog.vpn("hook", "安装 VpnEnhance 层", "");

        try {
            XposedHelpers.findAndHookMethod(NetworkInterface.class, "toString",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                        try {
                            String str = (String) param.getResult();
                            if (str != null) {
                                NetworkInterface ni = (NetworkInterface) param.thisObject;
                                try {
                                    String name = ni.getName();
                                    if (isVpnInterface(name)) {
                                        ReaLog.vpn("interface", "NetworkInterface.toString 替换VPN名称", name);
                                        param.setResult(str.replaceAll("(?i)tun[0-9]*|tap[0-9]*|ppp[0-9]*|vpn", "wlan0"));
                                    }
                                } catch (Throwable ignored) {}
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}

        try {
            Class<?> ncClass = XposedHelpers.findClassIfExists("android.net.NetworkCapabilities", classLoader);
            if (ncClass != null) {
                XposedHelpers.findAndHookMethod(ncClass, "toString",
                    new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_INTERFACE)) return;
                            try {
                                String s = (String) param.getResult();
                                if (s != null) {
                                    ReaLog.vpn("interface", "NetworkCapabilities.toString 替换VPN特征", "");
                                    s = s.replaceAll("(?i)TRANSPORT_VPN", "TRANSPORT_WIFI")
                                        .replaceAll("(?i)\\|VPN\\|", "|WIFI|")
                                        .replaceAll("(?i)VpnTransportInfo\\{[^}]*\\}", "");
                                    param.setResult(s);
                                }
                            } catch (Throwable t) {
                                recordErrorAndMaybeDisable(KEY_INTERFACE, t);
                            }
                        }
                    });
            }
        } catch (Throwable ignored) {}
    }

    // ---- 网络检测监听 ----
    private static void hookNetworkDetectMonitor(ClassLoader classLoader) throws Throwable {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NET_DETECT)) return;
        ReaLog.vpn("hook", "安装 NetworkDetectMonitor 层", "");

        try {
            Class<?> cmClass = XposedHelpers.findClass("android.net.ConnectivityManager", classLoader);
            XposedHelpers.findAndHookMethod(cmClass, "getActiveNetwork",
                                            new DetectHook("getActiveNetwork"));
            XposedHelpers.findAndHookMethod(cmClass, "getAllNetworks",
                                            new DetectHook("getAllNetworks"));
            XposedHelpers.findAndHookMethod(cmClass, "getNetworkCapabilities", Network.class,
                                            new DetectHook("getNetworkCapabilities"));
        } catch (Throwable ignored) {}

        try {
            Class<?> ncClass = XposedHelpers.findClass("android.net.NetworkCapabilities", classLoader);
            XposedHelpers.findAndHookMethod(ncClass, "hasTransport", int.class,
                                            new DetectHook("hasTransport"));
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(NetworkInterface.class, "getNetworkInterfaces",
                                            new DetectHook("getNetworkInterfaces"));
            XposedHelpers.findAndHookMethod(NetworkInterface.class, "getByName", String.class,
                                            new DetectHook("getByName"));
        } catch (Throwable ignored) {}

        try {
            Class<?> fileClass = XposedHelpers.findClass("java.io.File", classLoader);
            XposedHelpers.findAndHookMethod(fileClass, "exists",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NET_DETECT)) return;
                        try {
                            File f = (File) param.thisObject;
                            String path = f.getAbsolutePath().toLowerCase();
                            if (path.contains("/proc/net") || path.contains("/proc/self/net") ||
                                path.contains("/sys/class/net")) {
                                ReaLog.vpn("net_detect", "File.exists 检测网络文件", path);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_NET_DETECT, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}

        try {
            Class<?> globalClass = XposedHelpers.findClass("android.provider.Settings$Global", classLoader);
            Class<?> secureClass = XposedHelpers.findClass("android.provider.Settings$Secure", classLoader);
            hookSettingsDetect(globalClass);
            hookSettingsDetect(secureClass);
        } catch (Throwable ignored) {}

        try {
            Class<?> pmClass = XposedHelpers.findClass("android.app.ApplicationPackageManager", classLoader);
            XposedHelpers.findAndHookMethod(pmClass, "getPackageInfo", String.class, int.class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NET_DETECT)) return;
                        try {
                            String pkg = (String) param.args[0];
                            if (CAPTURE_APPS.contains(pkg)) {
                                ReaLog.vpn("net_detect", "PackageManager.getPackageInfo 检测抓包应用", pkg);
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_NET_DETECT, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }

    private static class DetectHook extends XC_MethodHook {
        private final String point;
        DetectHook(String point) { this.point = point; }
        protected void afterHookedMethod(MethodHookParam param) {
            if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NET_DETECT)) return;
            try {
                String value = (param.getResult() != null) ? param.getResult().toString() : "null";
                ReaLog.vpn("net_detect", point, value);
            } catch (Throwable t) {
                recordErrorAndMaybeDisable(KEY_NET_DETECT, t);
            }
        }
    }

    private static void hookSettingsDetect(final Class<?> cls) {
        if (cls == null) return;
        try {
            XposedHelpers.findAndHookMethod(cls, "getString", ContentResolver.class, String.class,
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_NET_DETECT)) return;
                        try {
                            String key = (String) param.args[1];
                            if (key != null && (key.toLowerCase().contains("proxy") || key.toLowerCase().contains("vpn"))) {
                                ReaLog.vpn("net_detect", "Settings." + cls.getSimpleName() + ".getString 检测", key + "=" + param.getResult());
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_NET_DETECT, t);
                        }
                    }
                });
        } catch (Throwable ignored) {}
    }

    // ---- SSL Pinning 绕过 ----
    private static void hookMessageDigestBypass(ClassLoader classLoader) {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_PINNING)) return;
        ReaLog.vpn("hook", "安装 MessageDigest 绕过", "");
        try {
            XposedHelpers.findAndHookMethod(java.security.MessageDigest.class, "isEqual", byte[].class, byte[].class,
                new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_PINNING)) return;
                        try {
                            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                            for (StackTraceElement e : stack) {
                                if (e.getClassName().contains("CertificatePinner") ||
                                    e.getClassName().contains("OkHostnameVerifier")) {
                                    ReaLog.vpn("ssl_pinning", "MessageDigest.isEqual 绕过证书固定", "");
                                    param.setResult(true);
                                    break;
                                }
                            }
                        } catch (Throwable t) {
                            recordErrorAndMaybeDisable(KEY_SSL_PINNING, t);
                        }
                    }
                });
        } catch (Throwable t) {
            disableSubFeature(KEY_SSL_PINNING);
            log("MessageDigest.isEqual Hook 失败: " + t.getMessage());
        }
    }

    private static void hookObfuscatedPinners(ClassLoader classLoader) {
        if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_PINNING)) return;
        ReaLog.vpn("hook", "安装混淆 Pinner 绕过", "");
        String[] candidates = {"o8", "okhttp3.CertificatePinner"};
        for (String name : candidates) {
            try {
                Class<?> cls = XposedHelpers.findClassIfExists(name, classLoader);
                if (cls != null) {
                    XposedBridge.hookAllMethods(cls, "check",
                        new XC_MethodReplacement() {
                            protected Object replaceHookedMethod(MethodHookParam param) {
                                if (!isEnabled(sCurrentPackage) || !isSubFeatureEnabled(KEY_SSL_PINNING)) return null;
                                try {
                                    ReaLog.vpn("ssl_pinning", "混淆 CertificatePinner.check 绕过", "");
                                } catch (Throwable t) {
                                    recordErrorAndMaybeDisable(KEY_SSL_PINNING, t);
                                }
                                return null;
                            }
                        });
                    break;
                }
            } catch (Throwable ignored) {}
        }
    }


}

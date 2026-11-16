package com.install.appinstall.xl.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import de.robv.android.xposed.XposedHelpers;

/**
 * BuHook —— 全局 hook 挂载仓库（注册表 + 调度器）。
 *
 * 定位：所有功能（无论已有还是未来新增）统一在此注册，由仓库负责 门控（DebugModeManager 开关）、幂等（防重复挂载）、熔断（错误计数自动禁用）、
 * 时序调度（立即/极速/普通/延迟1s/延迟2s 五档）与热重载补挂（mountAll 可重入， 已挂跳过、未挂补挂）。
 *
 * 用法： 组件初始化时注册（注册幂等，重复调用安全）： BuHook.register("hook_xxx", BuHook.PHASE_FRONT, new
 * BuHook.HookInstaller() { public void install(ClassLoader cl, Context ctx) throws Throwable {
 * mXxx.hookXxx(cl); } }); 无门控的系统项： BuHook.registerSystem("preload", BuHook.PHASE_FRONT, installer);
 * 自定义条件（如 isFlutterApp）：BuHook.register(key, phase, condition, installer); 批量挂载/补挂：
 * BuHook.mountAll(classLoader, appContext); 单项热挂载： BuHook.mount("hook_xxx", classLoader,
 * appContext); 状态查询： BuHook.isMounted(key) / isEnabled(key) / errorCount(key) ... 给某功能记错（熔断）：
 * BuHook.recordError(key, t) 记错并返回本次计数（-1=可忽略异常，-2=窗口内重复），由调用方判定是否熔断。
 */
public class BuHook {

    // ==================== 阶段定义 ====================
    /** 同步立即执行（调用线程内） */
    public static final int PHASE_NOW = 0;
    /** postAtFrontOfQueue 极速执行（主线程队列最前） */
    public static final int PHASE_FRONT = 1;
    /** post 普通执行（主线程队列尾部） */
    public static final int PHASE_NORMAL = 2;
    /** postDelayed 1000ms 延迟执行 */
    public static final int PHASE_DELAY_1S = 3;
    /** postDelayed 2000ms 延迟执行 */
    public static final int PHASE_DELAY_2S = 4;
    /** 无门控条件（系统/基础项使用） */
    private static final HookCondition ALWAYS = new HookCondition() {
        @Override
        public boolean ok() {
            return true;
        }
    };

    // ==================== 接口 ====================
    /** 挂载动作：完成一个功能的所有 hook 安装。抛出的异常由仓库统一记录，不影响其他项。 */
    public interface HookInstaller {
        void install(ClassLoader cl, Context ctx) throws Throwable;
    }

    /** 挂载前置条件（每次挂载批次重新评估，支撑热重载时开关变化即时生效）。 */
    public interface HookCondition {
        boolean ok();
    }

    // ==================== 注册表 ====================
    private static final class Entry {
        final String key;
        final int phase;
        final HookCondition cond;
        final HookInstaller installer;

        /** 刷新型：mountAll 每次重入均执行（条目自身负责挂载幂等），用于需随配置重载刷新状态的复合管理器。 */
        final boolean refresh;

        Entry(String key, int phase, HookCondition cond, HookInstaller installer, boolean refresh) {
            this.key = key;
            this.phase = phase;
            this.cond = cond;
            this.installer = installer;
            this.refresh = refresh;
        }
    }

    /** 注册表：按注册顺序保存（保证各阶段组内的挂载顺序与注册顺序一致）。 */
    private static final Map<String, Entry> sRegistry = new LinkedHashMap<String, Entry>();

    private static final Set<String> sRegistered = new HashSet<String>();
    private static final Object sRegistryLock = new Object();

    // ==================== 幂等 / 熔断 ====================
    /** 已挂载功能集合（子项级幂等：无论成功/部分成功/失败都置位，防双重挂载）。 */
    private static final Set<String> sMounted = Collections.synchronizedSet(new HashSet<String>());
    /** 错误计数（进程内，熔断用；各功能域按带前缀 key 共用这一份）。 */
    private static final Map<String, Integer> sErrorCount = new ConcurrentHashMap<String, Integer>();
    /** 最近一次报错时间（按 key：同 key 在去重窗口内重复异常只计一次，防双挂回调翻倍计数）。 */
    private static final Map<String, Long> sLastErrorTime = new ConcurrentHashMap<String, Long>();
    /** 可忽略异常：说明「目标环境缺方法/缺类」，不代表功能本身故障，计数无意义。 */
    private static final Set<String> IGNORABLE_EXCEPTIONS = new HashSet<String>(Arrays.asList("java.lang.NoSuchMethodError", "java.lang.NoClassDefFoundError", "java.lang.ClassNotFoundException"));
    /** 已熔断集合：加入后 mountAll/mount 永久跳过（除非 reset）。 */
    private static final Set<String> sDisabled = Collections.synchronizedSet(new HashSet<String>());
    /** 挂载互斥锁：保证"检查条件→挂载→置位"原子。 */
    private static final Object sInstallLock = new Object();
    /** 错误熔断阈值（与 AntiDetection 全局惯例一致）。 */
    private static final int ERROR_THRESHOLD = 2;
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    // ==================== 注册 API ====================
    /** 注册功能（默认门控 = DebugModeManager.isFeatureEnabled(key)）。 注册幂等：同 key 只生效首次。 */
    public static void register(final String key, int phase, HookInstaller installer) {
        registerInternal(key, phase, null, installer, false);
    }

    /** 注册功能（自定义前置条件）。 */
    public static void register(String key, int phase, HookCondition cond, HookInstaller installer) {
        registerInternal(key, phase, cond, installer, false);
    }

    /** 注册无门控的系统/基础项（如数据预加载、必要兜底挂载）。 */
    public static void registerSystem(String key, int phase, HookInstaller installer) {
        registerInternal(key, phase, ALWAYS, installer, false);
    }

    /**
     * 注册"刷新型"功能：mountAll 每次重入均执行（不做 mounted 幂等跳过，条目自身负责挂载幂等）。
     * 用于需随配置重载刷新内部状态的复合管理器（如痕迹检测：每次重入刷新子项开关快照）。 错误计数/熔断仍生效。
     */
    public static void registerRefresh(String key, int phase, HookInstaller installer) {
        registerInternal(key, phase, null, installer, true);
    }

    private static void registerInternal(String key, int phase, HookCondition cond,
                                         HookInstaller installer, boolean refresh) {
        if (key == null || installer == null) return;
        synchronized (sRegistryLock) {
            if (sRegistered.contains(key)) return;
            sRegistered.add(key);
            if (cond == null) {
                final String fkey = key;
                cond = new HookCondition() {
                    @Override
                    public boolean ok() {
                        return DebugModeManager.isFeatureEnabled(fkey);
                    }
                };
            }
            sRegistry.put(key, new Entry(key, phase, cond, installer, refresh));
        }
    }

    // ==================== 调度 API ====================
    /** 全量挂载 / 热重载补挂（可重入）： 已挂载项跳过，未挂载且条件成立且未熔断项执行挂载。 PHASE_NOW 同步执行，其余按阶段 post 到主线程。 */
    public static void mountAll(final ClassLoader cl, final Context ctx) {
        runPhase(PHASE_NOW, cl, ctx);
        sHandler.postAtFrontOfQueue(new Runnable() {
                @Override
                public void run() {
                    runPhase(PHASE_FRONT, cl, ctx);
                }
            });
        sHandler.post(new Runnable() {
                @Override
                public void run() {
                    runPhase(PHASE_NORMAL, cl, ctx);
                }
            });
        sHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runPhase(PHASE_DELAY_1S, cl, ctx);
                }
            }, 1000L);
        sHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    runPhase(PHASE_DELAY_2S, cl, ctx);
                }
            }, 2000L);
    }

    /**
     * 单项挂载（补挂 / 热开启某功能）。
     * @return true=本次完成挂载；false=未注册/条件不成立/已挂载/已熔断
     */
    public static boolean mount(String key, ClassLoader cl, Context ctx) {
        if (key == null) return false;
        Entry e;
        synchronized (sRegistryLock) {
            e = sRegistry.get(key);
        }
        if (e == null) return false;
        return mountEntry(e, cl, ctx);
    }

    private static void runPhase(int phase, ClassLoader cl, Context ctx) {
        List<Entry> snapshot = new ArrayList<Entry>();
        synchronized (sRegistryLock) {
            for (Entry e : sRegistry.values()) {
                if (e.phase == phase) snapshot.add(e);
            }
        }
        for (Entry e : snapshot) {
            mountEntry(e, cl, ctx);
        }
    }

    private static boolean mountEntry(Entry e, ClassLoader cl, Context ctx) {
        if (!e.cond.ok()) return false;
        synchronized (sInstallLock) {
            if (sDisabled.contains(e.key)) {
                ReaLog.log("buHook", "跳过已熔断: " + e.key);
                return false;
            }
            if (e.refresh) {
                // 刷新型：每次重入均执行（条目自身负责挂载幂等），错误计数/熔断仍生效
                try {
                    e.installer.install(cl, ctx);
                } catch (Throwable t) {
                    recordErrorAndTrip(e.key, t);
                    if (!sDisabled.contains(e.key)) {
                        ReaLog.log("buHook", "刷新执行失败 " + e.key + ": " + t.getMessage());
                    }
                }
                sMounted.add(e.key);
                return true;
            }
            if (sMounted.contains(e.key)) return false; // 防双重挂载
            try {
                e.installer.install(cl, ctx);
            } catch (Throwable t) {
                recordErrorAndTrip(e.key, t);
                if (!sDisabled.contains(e.key)) {
                    ReaLog.log("buHook", "挂载失败 " + e.key + ": " + t.getMessage());
                }
            }
            // 无论成功/部分成功/失败都置位：
            // 已挂方法真实生效，重试只会双挂（回调内若含错误计数逻辑对双挂敏感）。
            sMounted.add(e.key);
            return true;
        }
    }

    // ==================== 熔断 / 管理 API ====================
    /**
     * 记录一次功能错误，返回本次计数值（>=1）；-1=可忽略异常、-2=窗口内重复，均未计数。
     * 是否熔断由调用方按返回值自行判定并执行禁用动作（本方法不主动禁用）。
     */
    public static int recordError(String key, Throwable t) {
        return recordError(key, t, ERROR_THRESHOLD, 0L, false);
    }

    /**
     * 完整重载：threshold=视为熔断点的计数；dedupMs>0 时同 key 窗口内重复只计一次；
     * ignorable=true 时跳过「环境缺方法/缺类」类异常（该类异常不代表功能故障）。
     */
    public static int recordError(String key, Throwable t, int threshold, long dedupMs, boolean ignorable) {
        if (key == null || t == null) return 0;
        if (ignorable && IGNORABLE_EXCEPTIONS.contains(t.getClass().getName())) return -1;
        if (dedupMs > 0L) {
            Long last = sLastErrorTime.get(key);
            long now = System.currentTimeMillis();
            if (last != null && now - last < dedupMs) return -2;
            sLastErrorTime.put(key, now);
        }
        Integer cur = sErrorCount.get(key);
        int n = (cur == null ? 0 : cur) + 1;
        sErrorCount.put(key, n);
        return n;
    }

    /** 挂载失败专用：计数并达阈熔断（加入 sDisabled，之后 mountAll/mount 跳过该项）。 */
    private static int recordErrorAndTrip(String key, Throwable t) {
        synchronized (sInstallLock) {
            int n = recordError(key, t);
            if (n >= ERROR_THRESHOLD && !sDisabled.contains(key)) {
                sDisabled.add(key);
                ReaLog.log("buHook", "自动熔断 " + key + "（错误计数 " + n + "）");
            }
            return n;
        }
    }

    /** 清除某项错误计数与去重时间（功能重新初始化/界面重置时调用）。 */
    public static void clearErrorCount(String key) {
        if (key == null) return;
        sErrorCount.remove(key);
        sLastErrorTime.remove(key);
    }

    // ==================== 查询 API ====================
    /** 该项是否已挂载。 */
    public static boolean isMounted(String key) {
        if (key == null) return false;
        synchronized (sInstallLock) {
            return sMounted.contains(key);
        }
    }

    /** 该项当前条件是否成立（默认 = 调试开关开启或未注册）。 */
    public static boolean isEnabled(String key) {
        if (key == null) return false;
        Entry e;
        synchronized (sRegistryLock) {
            e = sRegistry.get(key);
        }
        return e != null && e.cond.ok();
    }

    /** 该项错误计数（未出错返回 0）。 */
    public static int errorCount(String key) {
        if (key == null) return 0;
        synchronized (sInstallLock) {
            Integer n = sErrorCount.get(key);
            return n == null ? 0 : n;
        }
    }

    // ==================== 通用挂载辅助（未来功能可直接使用） ====================
    /**
     * 挂载普通方法（封装 findAndHookMethod，失败不抛只记错误）。 参数格式与 XposedHelpers.findAndHookMethod 相同：形参类型列表最后接
     * XC_MethodHook。
     *
     * @return 是否挂载成功
     */
    public static boolean hook(String className, ClassLoader cl, String methodName,
                               Object... paramsAndCallback) {
        try {
            XposedHelpers.findAndHookMethod(className, cl, methodName, paramsAndCallback);
            return true;
        } catch (Throwable t) {
            ReaLog.log("buHook", "通用补挂失败 " + className + "." + methodName + ": " + t.getMessage());
            return false;
        }
    }

    private static Class<?>[] toClassArray(java.lang.reflect.Method m) {
        Class<?>[] types = m.getParameterTypes();
        Class<?>[] out = new Class<?>[types.length];
        System.arraycopy(types, 0, out, 0, types.length);
        return out;
    }
}

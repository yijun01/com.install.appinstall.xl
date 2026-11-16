package com.install.appinstall.xl.ui;

import android.os.SystemClock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UiGuard —— 全局防重
 */
 
public final class UiGuard {

    private UiGuard() {}
    private static final Map<String, Long> TAP = new ConcurrentHashMap<String, Long>();
    private static final Map<String, Long> BUSY = new ConcurrentHashMap<String, Long>();
    /** 进行中锁的兜底时长：超过它视作上一次 begin 没有正常收尾 */
    private static final long BUSY_TIMEOUT_MS = 30000L;
    /**
     * 时间窗口防重：距离上次同名 tap 不足 ms 毫秒就拒绝。
     */
    public static boolean tap(String key, long ms) {
        if (key == null) return true;
        long now = SystemClock.elapsedRealtime();
        Long last = TAP.putIfAbsent(key, Long.valueOf(now));
        if (last == null) return true;
        if (now - last.longValue() < ms) return false;
        return TAP.replace(key, last, Long.valueOf(now));
    }

    /** 进行中锁：同名 key 已 begin 且未 end 就拒绝。  */
    public static boolean begin(String key) {
        if (key == null) return true;
        long now = SystemClock.elapsedRealtime();
        // 与 tap 相同的原子化：并发首次进入不会双双入门
        Long since = BUSY.putIfAbsent(key, Long.valueOf(now));
        if (since == null) return true;
        if (now - since.longValue() < BUSY_TIMEOUT_MS) return false;
        return BUSY.replace(key, since, Long.valueOf(now));
    }

    /** 解除进行中锁，必须与 begin 成对（放在 finally 里） */
    public static void end(String key) {
        if (key == null) return;
        BUSY.remove(key);
    }

    /** 该 key 是否处于进行中状态 */
    public static boolean isBusy(String key) {
        if (key == null) return false;
        Long since = BUSY.get(key);
        if (since == null) return false;
        // 僵死的锁不算 busy，否则白拦
        return SystemClock.elapsedRealtime() - since.longValue() < BUSY_TIMEOUT_MS;
    }
}

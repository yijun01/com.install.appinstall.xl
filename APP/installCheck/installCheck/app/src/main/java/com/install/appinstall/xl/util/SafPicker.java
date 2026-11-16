package com.install.appinstall.xl.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 系统文件选择器（SAF，Storage Access Framework）桥接。
 */
public class SafPicker {

    /** 导入请求码（高位中段，避开宿主常用小值；小于 0x8000 防止个别 ROM 按 short 解析成负数） */
    private static final int REQUEST_IMPORT = 0x5A01;
    /** 导出请求码 */
    private static final int REQUEST_EXPORT = 0x5A02;
    private static final Map<Integer, UriCallback> sPending = new ConcurrentHashMap<Integer, UriCallback>();
    private static final Set<Class<?>> sHookedClasses = new HashSet<Class<?>>();
    /** 选择结果回调；uri == null 表示用户取消或未选择 */
    public interface UriCallback {
        void onResult(Uri uri);
    }

    /** 打开系统文件选择器，选择单个 json 配置文件 */
    public static void pickImportFile(Activity activity, UriCallback cb) {
        ensureHook(activity);
        sPending.put(REQUEST_IMPORT, cb);
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        // 模块内部发起的启动：加跳过标记
        More.markInternal(intent);
        try {
            com.install.appinstall.xl.ui.UiKit.haptic(activity); // 震动反馈
            activity.startActivityForResult(intent, REQUEST_IMPORT);
        } catch (Throwable t) {
            sPending.remove(REQUEST_IMPORT);
            ReaLog.log("config", "打开文件选择器失败: " + t.getMessage());
            ToastUtil.show(activity, "无法打开文件选择器");
        }
    }

    /** 打开系统「新建文件」选择器，让用户选保存位置*/
    public static void pickExportLocation(Activity activity, String suggestedName, UriCallback cb) {
        ensureHook(activity);
        sPending.put(REQUEST_EXPORT, cb);
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        if (suggestedName != null) intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        More.markInternal(intent); // 跳过自身启动拦截
        try {
            com.install.appinstall.xl.ui.UiKit.haptic(activity); // 震动反馈
            activity.startActivityForResult(intent, REQUEST_EXPORT);
        } catch (Throwable t) {
            sPending.remove(REQUEST_EXPORT);
            ReaLog.log("config", "打开保存位置选择器失败: " + t.getMessage());
            ToastUtil.show(activity, "无法打开保存位置选择器");
        }
    }

    /** 安装 onActivityResult 拦截 按类去重*/
    private static void ensureHook(Activity activity) {
        if (activity == null) return;
        Class<?> cls = activity.getClass();
        synchronized (SafPicker.class) {
            // 1) 从具体类向上遍历，hook 每一层 override 的 onActivityResult，必须挂到覆盖层）
            Class<?> c = cls;
            while (c != null && c != Activity.class) {
                hookOnActivityResultIfNew(c, activity);
                c = c.getSuperclass();
            }
            // 2) Activity 基类本身
            hookOnActivityResultIfNew(Activity.class, activity);
        }
    }

    private static void hookOnActivityResultIfNew(Class<?> cls, Activity act) {
        if (sHookedClasses.contains(cls)) return;
        Method m;
        try {
            m = cls.getDeclaredMethod("onActivityResult", int.class, int.class, Intent.class);
        } catch (Throwable t) {
            sHookedClasses.add(cls); // 该层没有此方法，标记避免重复查找
            return;
        }
        try {
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        int requestCode = (Integer) param.args[0];
                        UriCallback cb = sPending.remove(requestCode);
                        if (cb == null) return; // 非我们的请求：放行，宿主逻辑照常
                        int resultCode = (Integer) param.args[1];
                        Intent data = (Intent) param.args[2];
                        Uri uri = (resultCode == Activity.RESULT_OK && data != null) ? data.getData() : null;
                        cb.onResult(uri);
                    } catch (Throwable ignored) {
                        // 选择器回调内的任何异常都不应影响宿主原方法执行
                    }
                }
            });
            sHookedClasses.add(cls);
        } catch (Throwable t) {
            ReaLog.log("config", "数据回调失败: " + cls.getName() + " " + t.getMessage());
            if (act != null) {
                ToastUtil.show(act, "数据回调失败: " + cls.getName() + " " + t.getMessage());
            }
        }
    }
}

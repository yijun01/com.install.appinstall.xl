package com.install.appinstall.xl.util;

import android.app.Activity;
import android.view.KeyEvent;
import com.install.appinstall.xl.HookInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
//导入文件
import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.ToastUtil;

public class foat {
    private static long lastVolumeDownTime = 0;
    private static long lastVolumeUpTime = 0;
    private static final int DOUBLE_CLICK_TIMEOUT = 500;

    public static void initVolumeKeyDoubleClick(final HookInit hookInit, ClassLoader classLoader) {
        XposedHelpers.findAndHookMethod(Activity.class, "dispatchKeyEvent", KeyEvent.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Activity activity = (Activity) param.thisObject;
                    KeyEvent event = (KeyEvent) param.args[0];
                    if (event.getAction() == KeyEvent.ACTION_DOWN) {
                        int keyCode = event.getKeyCode();
                        long currentTime = System.currentTimeMillis();
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            if (currentTime - lastVolumeDownTime < DOUBLE_CLICK_TIMEOUT) {
                                handleDoubleClick(activity, hookInit);
                            }
                            lastVolumeDownTime = currentTime;
                        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            if (currentTime - lastVolumeUpTime < DOUBLE_CLICK_TIMEOUT) {
                                handleDoubleClick(activity, hookInit);
                            }
                            lastVolumeUpTime = currentTime;
                        }
                    }
                }
            });
    }

    private static void handleDoubleClick(Activity activity, HookInit hookInit) {
        String targetApp = hookInit.getCurrentTargetApp();
        Boolean currentlyShown = HookInit.floatingShownMap.get(targetApp);
        if (currentlyShown == null) currentlyShown = true;

        if (currentlyShown) {
            ToastUtil.show(activity, "悬浮窗已存在\n无需重复创建");
        } else {
            HookInit.floatingShownMap.put(targetApp, true);
            hookInit.saveConfigToFile();
            hookInit.showFloatingView(activity);
            ToastUtil.show(activity, "悬浮窗 已恢复显示");
        }
    }
}

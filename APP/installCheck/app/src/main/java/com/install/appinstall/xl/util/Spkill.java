package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import com.install.appinstall.xl.HookInit;
import com.install.appinstall.xl.util.ToastUtil;
import de.robv.android.xposed.XC_MethodHook;

public class Spkill {
    public static void handleAppExit(final HookInit hookInit,
                                     final String exitMethod,
                                     final XC_MethodHook.MethodHookParam param) {
        try {
            final String targetApp = hookInit.getCurrentTargetApp();
            boolean blockExit = hookInit.blockExitMap.getOrDefault(targetApp, false);
            boolean superBlock = hookInit.superBlockExitMap.getOrDefault(targetApp, false);
            if (!blockExit && !superBlock) return;
            final HookInit.DetectedPackages detected = hookInit.analyzeDetectedPackages();
            if (detected.installedPackages.isEmpty() && detected.notInstalledPackages.isEmpty()) return;
            if (hookInit.checkSilentIntercept(detected)) {
                hookInit.showSilentInterceptToast(detected);
                param.setResult(null);
                param.setThrowable(null);
                return;
            }
            final Activity activity = hookInit.getCurrentActivity();
            if (activity == null || activity.isFinishing()) return;
            StringBuilder message = buildDetectedMessage(detected);
            message.append("<br><font color='#FF5722'><b>想要结束退出应用/页面</b></font><br>请选择是否需要退出？");
            AlertDialog dialog = hookInit.createBoundedDialog(
                activity,
                "拦截提醒",
                message.toString(),
                new String[]{"阻止退出", "不拦截"},
                new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            hookInit.handleInterceptChoice(detected, "intercept", true);
                            d.dismiss();
                            ToastUtil.show(activity, "已拦截退出，应用继续运行");
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            hookInit.handleInterceptChoice(detected, "allow", false);
                            d.dismiss();
                            hookInit.blockExitMap.put(targetApp, false);
                            // 新增：同时关闭超强拦截
                            hookInit.superBlockExitMap.put(targetApp, false);
                            //hookInit.saveConfigToFile();
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (exitMethod.contains("System.exit")) {
                                                System.exit((int) param.args[0]);
                                            } else if (exitMethod.contains("Process.killProcess")) {
                                                android.os.Process.killProcess((int) param.args[0]);
                                            } else if (exitMethod.contains("Runtime.exit")) {
                                                Runtime.getRuntime().exit((int) param.args[0]);
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }, 100);
                            ToastUtil.show(activity, "已允许退出，拦截功能已关闭");
                        }
                    }
                }
            );
            dialog.show();
            param.setResult(null);
            param.setThrowable(null);
        } catch (Throwable t) {
            // 出错时放行
        }
    }


    public static void handleActivityFinish(final HookInit hookInit,
                                            final Activity activity,
                                            final XC_MethodHook.MethodHookParam param) {
        try {
            // 检查是否为返回键双击放行
            if (hookInit.isBackPressFinish(activity)) {
                // 直接放行，让原 finish 继续执行
                return;
            }
            final String targetApp = hookInit.getCurrentTargetApp();
            boolean blockExit = hookInit.blockExitMap.getOrDefault(targetApp, false);
            boolean superBlock = hookInit.superBlockExitMap.getOrDefault(targetApp, false);
            // 1. 拦截开关全部关闭：直接放行（不拦截任何 finish）
            if (!blockExit && !superBlock) return;
            // 2. 如果只有普通拦截开启且不是主 Activity，则放行
            if (blockExit && !superBlock && !hookInit.isMainActivity(activity)) return;
            final HookInit.DetectedPackages detected = hookInit.analyzeDetectedPackages();
            if (detected.installedPackages.isEmpty() && detected.notInstalledPackages.isEmpty()) return;
            if (hookInit.checkSilentIntercept(detected)) {
                param.setResult(null);
                hookInit.showSilentInterceptToast(detected);
                return;
            }
            StringBuilder message = buildDetectedMessage(detected);
            message.append("<br><font color='#FF5722'><b>想要结束退出应用/页面</b></font><br>请选择是否需要退出？");
            AlertDialog dialog = hookInit.createBoundedDialog(
                activity,
                "拦截提醒",
                message.toString(),
                new String[]{"阻止退出", "不拦截"},
                new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            hookInit.handleInterceptChoice(detected, "intercept", true);
                            d.dismiss();
                            ToastUtil.show(activity, "已拦截退出，应用继续运行");
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface d, int which) {
                            hookInit.handleInterceptChoice(detected, "allow", false);
                            d.dismiss();
                            hookInit.blockExitMap.put(targetApp, false);
                            // 新增：同时关闭超强拦截
                            hookInit.superBlockExitMap.put(targetApp, false);
                            //hookInit.saveConfigToFile();
                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            activity.finish();
                                        } catch (Throwable ignored) {}
                                    }
                                });
                            ToastUtil.show(activity, "已允许退出，拦截功能已关闭");
                        }
                    }
                }
            );
            dialog.show();
            param.setResult(null);
        } catch (Throwable t) {
            // 出错时放行
        }
    }


    private static StringBuilder buildDetectedMessage(HookInit.DetectedPackages detected) {
        StringBuilder message = new StringBuilder();
        if (!detected.installedPackages.isEmpty() && detected.notInstalledPackages.isEmpty()) {
            message.append("当前应用检测到你<font color='#4CAF50'>已安装:</font><br>");
            for (String pkg : detected.installedPackages) {
                message.append("• ").append(pkg).append("<br>");
            }
        } else if (detected.installedPackages.isEmpty() && !detected.notInstalledPackages.isEmpty()) {
            message.append("当前应用检测到你<font color='#F44336'>未安装:</font><br>");
            for (String pkg : detected.notInstalledPackages) {
                message.append("• ").append(pkg).append("<br>");
            }
        } else {
            message.append("当前应用检测到你：<br><br>");
            if (!detected.installedPackages.isEmpty()) {
                message.append("<font color='#4CAF50'>【已安装】</font><br>");
                for (String pkg : detected.installedPackages) {
                    message.append("• ").append(pkg).append("<br>");
                }
                message.append("<br>");
            }
            if (!detected.notInstalledPackages.isEmpty()) {
                message.append("<font color='#F44336'>【未安装】</font><br>");
                for (String pkg : detected.notInstalledPackages) {
                    message.append("• ").append(pkg).append("<br>");
                }
            }
        }
        return message;
    }

/*
    // ========== 点击菜单 ==========
    private void showClickMenu(
        final Activity activity,
        final TextView floatingView) {
        try {
            Boolean currentStatus = installStatusMap.get(currentTargetApp);
            final boolean status = currentStatus != null ? currentStatus : true;
            Boolean blockExit = blockExitMap.get(currentTargetApp);
            final boolean isBlockingExit = blockExit != null ? blockExit : false;
            // 构建按钮文本
            final String btn1Text = "切换为已安装" + (status ? " (当前)" : "");
            final String btn2Text = "切换为未安装" + (!status ? " (当前)" : "");
            final String btn3Text = isBlockingExit
                ? "关闭拦截退出 (已开启)"
                : "开启拦截退出 (已关闭)";
            // 创建带有三个按钮的对话框
            AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("悬浮窗菜单")
                .setMessage("当前应用: " + currentTargetApp)
                .setPositiveButton(
                btn1Text,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!status) {
                            handleStatusSwitch(activity, floatingView, true);
                        } else {
                            ToastUtil.show(
                                activity,
                                "当前已是已安装状态");
                        }
                    }
                }
            )
                .setNegativeButton(
                btn2Text,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (status) {
                            handleStatusSwitch(activity, floatingView, false);
                        } else {
                            ToastUtil.show(
                                activity,
                                "当前已是未安装状态");
                        }
                    }
                }
            )
                .setNeutralButton(
                btn3Text,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        toggleBlockExit(activity, floatingView, !isBlockingExit);
                    }
                }
            )
                .create();
            // 显示对话框
            dialog.show();
            // 设置对话框窗口属性
            Window window = dialog.getWindow();
            if (window != null) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        } catch (Throwable t) {
            log("显示点击菜单异常: " + t.getMessage());
            // 简单回退方案
            try {
                ToastUtil.show(activity, "显示菜单失败\n请重新启动应用");
            } catch (Throwable e) {
                log("显示Toast也失败: " + e.getMessage());
            }
        }
    }

    // ========== 切换拦截退出功能 ==========
    private void toggleBlockExit(
        final Activity activity,
        final TextView floatingView,
        final boolean enable) {
        try {
            blockExitMap.put(currentTargetApp, enable);
            saveConfigToFile();
            if (floatingView != null) {
                Boolean currentStatus = installStatusMap.get(currentTargetApp);
                boolean status = currentStatus != null ? currentStatus : true;
                String statusText = status ? "已安装" : "未安装";
                String blockText = enable ? "[拦截]" : "";
                floatingView.setText("伪造安装(" + statusText + ")" + blockText);
            }
            String message = enable
                ? "✅ 拦截退出功能已开启\n应用因检测到伪造包退出时会被拦截"
                : "❌ 拦截退出功能已关闭\n应用可正常退出";
            ToastUtil.show(activity, message);
            log("拦截退出功能: " + (enable ? "开启" : "关闭"));
            // 显示刷新提示
            showBlockExitRefreshPrompt(activity, enable);
        } catch (Throwable t) {
            log("切换拦截退出异常: " + t.getMessage());
            ToastUtil.show(activity, "切换失败");
        }
    }

    // ========== 显示退出拦截刷新提示 ==========
    private void showBlockExitRefreshPrompt(
        final Activity activity,
        final boolean enabled) {
        try {
            String title = enabled ? "退出拦截已开启" : "退出拦截已关闭";
            String message = enabled
                ? "✅ <font color='#4CAF50'>退出拦截功能已开启</font><br><br>" +
                "功能效果：<br>" +
                "• 应用因检测到伪造包而退出时会被拦截<br>" +
                "• 会弹出提示询问是否允许退出<br><br>" +
                "• 可设置静默拦截（不再询问）<br>" +
                "不再询问：当应用没有新的检测包时，你选择相同功能2次后自动拦截<br>" +
                "(比如你选择了2次拦截功能，应用没有更新包名就会拦截退出)<br>" +
                "每当有更新时将作废,直到没有更新。部分应用退出拦截可能失效<br><br>" +
                "是否立即刷新应用使设置生效？"
                : "❌ <font color='#F44336'>退出拦截功能已关闭</font><br><br>" +
                "功能效果：<br>" +
                "• 应用可正常退出<br>" +
                "• 本次不再拦截任何退出行为<br><br>" +
                "静默选择：当应用没有新的检测包时，你选择相同功能2次后自动放行<br>" +
                "(比如你选择了2次不拦截功能，应用没有更新包名就会放行退出)<br>" +
                "每当有更新时将作废,直到没有更新。部分应用退出拦截可能失效<br><br>" +
                "是否立即刷新应用使设置生效？";
            AlertDialog dialog = createBoundedDialog(
                activity,
                title,
                message,
                new String[]{"立即刷新", "稍后"},
                new DialogInterface.OnClickListener[]{
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            refreshApplication(activity);
                        }
                    },
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    },
                }
            );
            dialog.show();
        } catch (Throwable t) {
            log("显示退出拦截刷新提示异常: " + t.getMessage());
        }
    }
    */

}

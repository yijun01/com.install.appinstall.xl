package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.ref.WeakReference;

import com.install.appinstall.xl.HookInit;

//全局旧样式界面对话框：DialogUi

public class DialogUi {
    private static WeakReference<AlertDialog> sLastDialogRef = null;
    private static long sLastDialogCreateTime = 0;
    private static final int DIALOG_DEDUP_WINDOW_MS = 300;
    private static final String[] ALL_VPN_KEYS = {
            "interface", "cmd", "proxy", "proxy_env", "capture", "netfiles", "net_detect",
            "ssl_trust", "ssl_pinning", "ssl_cert", "ssl_cert_hide"
    };

    private static final String[] ALL_ANTI_KEYS = {
            "class", "file", "pm", "proc", "cmd", "stacktrace", "root", "adb", "usb", "dev"
    };

    //  辅助方法
    private static HookInit getHookInit() {
        return ReaLog.sHookInit;
    }

    private static String getCurrentTargetApp() {
        HookInit h = getHookInit();
        return h != null ? h.getCurrentTargetApp() : "";
    }

    //  通用 View 构建方法
    public static android.graphics.drawable.Drawable getRoundButtonDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(25f);
        return drawable;
    }

    // 卡片用：12dp 圆角Material 卡片风格）
    public static android.graphics.drawable.Drawable getRoundCardDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12f);
        return drawable;
    }

    // 权限对话框工具栏按钮：统一 浅粉底+白字 样式
    public static Button newToolbarButton(Activity activity, String text, int bgColor) {
        Button b = new Button(activity);
        b.setText(text);
        b.setPadding(More.spx(activity, 14), More.spx(activity, 8), More.spx(activity, 14), More.spx(activity, 8));
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(getRoundButtonDrawable(bgColor));
        return b;
    }

    // 卡片区内缩进 1px 分隔线（组头下 / 权限行之间通用）
    public static View newIndentedDivider(Activity activity) {
        View v = new View(activity);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 1);
        p.setMargins(16, 0, 16, 0);
        v.setLayoutParams(p);
        v.setBackgroundColor(0xFFE0E0E0);
        return v;
    }

    // 更新文本
    public static void updateCheckBoxText(CheckBox cb, String label, boolean isEnabled) {
        String displayText;
        if (isEnabled) {
            // 启用状态：强制黑色
            displayText = "<font color='#000000'>" + label + "</font>";
        } else {
            // 禁用状态：红色加粗 + (已禁用) 小字
            displayText = "<b><font color='#F44336'>" + label + "</font>" +
                    "<font color='#FF5722'><small><b> (已禁用)</small></b></font>";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cb.setText(Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY));
        } else {
            cb.setText(Html.fromHtml(displayText));
        }
    }

    // 显示Toast
    public static void showToastDelayed(final String msg) {
        HookInit h = getHookInit();
        Context ctx = h != null ? h.getApplicationContext() : null;
        if (ctx != null) {
            ToastUtil.showUnique(ctx, msg);
        } else {
            More.postOnMainThread(new Runnable() {
                @Override
                public void run() {
                    Context ctx2 = getHookInit() != null ? getHookInit().getApplicationContext() : null;
                    if (ctx2 != null) {
                        ToastUtil.showUnique(ctx2, msg);
                    }
                }
            }, 500);
        }
    }

    // 列表型对话框内 1px 分隔线
    public static View newListDivider(Activity activity) {
        View divider = new View(activity);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(0xFFE0E0E0);
        return divider;
    }

    // ============================================================
    // overlay 层级提权
    // ============================================================
    public static boolean canReallyDrawOverlays(Context ctx) {
        if (ctx == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return false; // TYPE_APPLICATION_OVERLAY 需 API26
        try {
            Context appCtx = ctx.getApplicationContext();
            if (appCtx == null) appCtx = ctx;
            WindowManager wm = (WindowManager) appCtx.getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return false;
            View probe = new View(appCtx);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            params.format = android.graphics.PixelFormat.TRANSPARENT;
            params.width = 1;
            params.height = 1;
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 0;
            params.y = 0;
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            wm.addView(probe, params);
            wm.removeView(probe);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // 条件性把对话框提到 overlay 层
    public static void tryElevateDialogToOverlay(AlertDialog dialog) {
        if (dialog == null) return;
        try {
            android.view.Window window = dialog.getWindow();
            if (window == null) return;
            if (!canReallyDrawOverlays(window.getContext())) return;
            WindowManager.LayoutParams params = window.getAttributes();
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            window.setAttributes(params);
        } catch (Throwable ignored) {
        }
    }

    // 按名字取 framework（"android" 包）资源的 id
    private static int getFrameworkResId(Context ctx, String name, String type) {
        try {
            if (ctx != null) {
                return ctx.getResources().getIdentifier(name, type, "android");
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    // 对话框统一主题：DeviceDefault 随系统版本自动使用当前设计语言
    public static int getDialogThemeResId() {
        return android.R.style.Theme_DeviceDefault_Light_Dialog_Alert;
    }

    // 对话框按钮文字色
    private static int getDialogButtonTextColor() {
        if (Build.VERSION.SDK_INT >= 33) {
            return 0xFF6750A4; // Material3 colorPrimary
        }
        return 0xFF1976D2; // Material colorAccent 深一档保对比
    }

    // 加固框架对话框按钮
    public static void hardenAlertDialog(AlertDialog dialog) {
        if (dialog == null) return;
        try {
            int color = getDialogButtonTextColor();
            int[] whichList = {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                    AlertDialog.BUTTON_NEUTRAL
            };
            for (int which : whichList) {
                try {
                    Button b = dialog.getButton(which);
                    if (b != null) b.setTextColor(color);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            int panelId = getFrameworkResId(dialog.getContext(), "buttonPanel", "id");
            ViewGroup buttonPanel = panelId != 0 ? (ViewGroup) dialog.findViewById(panelId) : null;
            if (buttonPanel == null) return;
            try {
                java.lang.reflect.Field f = buttonPanel.getClass().getDeclaredField("mAllowStacking");
                f.setAccessible(true);
                f.set(buttonPanel, false);
                java.lang.reflect.Method m = buttonPanel.getClass().getMethod("setStacked", boolean.class);
                m.invoke(buttonPanel, false);
            } catch (Throwable ignored) {
            }
            ViewGroup buttonGroup = (ViewGroup) buttonPanel.findViewById(
                            getFrameworkResId(dialog.getContext(), "buttonGroup", "id"));
            if (buttonGroup == null) return;
            int index = buttonPanel.indexOfChild(buttonGroup);
            if (index < 0) return;
            final HorizontalScrollView hsv = new HorizontalScrollView(buttonGroup.getContext());
            hsv.setHorizontalScrollBarEnabled(true);
            buttonPanel.removeView(buttonGroup);
            buttonGroup.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            hsv.addView(buttonGroup, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            buttonPanel.addView(hsv, index, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } catch (Throwable ignored) {
        }
    }

    // 构建共用对话框样式
    public static AlertDialog createBoundedDialog(final Activity activity, String title, String message,
            String[] buttonTexts, DialogInterface.OnClickListener[] listeners) {
        return createBoundedDialog(activity, title, message, buttonTexts, listeners, null);
    }

    public static AlertDialog createBoundedDialog(final Activity activity, String title, String message,
            String[] buttonTexts, DialogInterface.OnClickListener[] listeners,
            final View customView) {
        long now = System.currentTimeMillis();
        synchronized (DialogUi.class) {
            if (now - sLastDialogCreateTime < DIALOG_DEDUP_WINDOW_MS) {
                if (sLastDialogRef != null) {
                    AlertDialog old = sLastDialogRef.get();
                    if (old != null && old.isShowing()) {
                        try {
                            old.dismiss();
                        } catch (Throwable ignored) {
                        }
                    }
                    sLastDialogRef = null;
                }
            }
            sLastDialogCreateTime = now;
        }

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
                if (ctx != null) {
                    ToastUtil.showUnique(ctx, "Activity 已失效，无法显示对话框");
                }
                return null;
            }
        }
        final Activity finalActivity = validActivity;
        AlertDialog.Builder builder = new AlertDialog.Builder(finalActivity, getDialogThemeResId()).setTitle(title);
        if (customView != null) {
            if (customView.getParent() != null) {
                ((ViewGroup) customView.getParent()).removeView(customView);
            }
            ScrollView scrollView = new ScrollView(finalActivity);
            scrollView.setVerticalScrollBarEnabled(false);
            scrollView.setHorizontalScrollBarEnabled(false);
            scrollView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            scrollView.setFillViewport(true);
            scrollView.addView(customView);
            builder.setView(scrollView);
        } else if (message != null && !message.isEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setMessage(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
            } else {
                builder.setMessage(Html.fromHtml(message));
            }
        }

        if (buttonTexts != null) {
            int len = buttonTexts.length;
            DialogInterface.OnClickListener[] safeListeners = new DialogInterface.OnClickListener[len];
            if (listeners != null) {
                int copyLen = Math.min(listeners.length, len);
                System.arraycopy(listeners, 0, safeListeners, 0, copyLen);
            }
            if (len >= 1) builder.setPositiveButton(buttonTexts[0], safeListeners[0]);
            if (len >= 2) builder.setNegativeButton(buttonTexts[1], safeListeners[1]);
            if (len >= 3) builder.setNeutralButton(buttonTexts[2], safeListeners[2]);
        }
        final AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setCancelable(true);
        More.markOurs(dialog);
        tryElevateDialogToOverlay(dialog);
        synchronized (DialogUi.class) {
            sLastDialogRef = new WeakReference<AlertDialog>(dialog);
        }
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                synchronized (DialogUi.class) {
                    if (sLastDialogRef != null && sLastDialogRef.get() == dialog) {
                        sLastDialogRef = null;
                    }
                }
            }
        });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                try {
                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        WindowManager.LayoutParams params = window.getAttributes();
                        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                        params.dimAmount = 0.4f;
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.gravity = Gravity.CENTER;
                        params.horizontalMargin = 0.05f;
                        window.setAttributes(params);
                    }
                    if (customView == null) {
                        try {
                            final TextView messageView = dialog.findViewById(android.R.id.message);
                            if (messageView != null) {
                                messageView.setTextIsSelectable(true);
                                messageView.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                                messageView.setFocusable(true);
                                messageView.setFocusableInTouchMode(true);
                                messageView.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        messageView.requestFocus();
                                    }
                                }, 50);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                    hardenAlertDialog(dialog);
                } catch (Throwable t) {
                    ReaLog.log("system", "设置对话框异常: " + t.getMessage());
                }
            }
        });

        return dialog;
    }

    // 显示重启确认对话框
    public static void showRestartConfirmDialog(final Activity activity) {
        ReaLog.log("system", "显示重启确认弹窗");
        if (activity == null || activity.isFinishing()) {
            ReaLog.log("system", "showRestartConfirmDialog: Activity无效");
            return;
        }

        String message = "<font color='#FF5722'><b>检测设置已保存</b></font>，" +
                "需 <font color='#2196F3'><b>重启应用</b></font> 才生效。<br><br>" +
                "<small><font color='#9E9E9E'><b>[热补挂]：</b>大部分功能已支持免重启生效！<br>若不确定功能是否生效您可选择＂立即重启＂<br>如需观测功能或尝试体验可选择＂稍后重启＂</font></small><br><br>" +
                "<font color='#2196F3'><b>若重启失败，请手动停止并启动应用</b></font>";

        AlertDialog dialog = createBoundedDialog(
        activity,
        "系统提示",
        message,
        new String[]{"立即重启", "稍后重启"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "用户选择立即重启");
                        d.dismiss();
                        ToastUtil.showUnique(activity, "✅ 应用即将停止...\n请重新启动应用！");
                        More.postOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                getHookInit().restartApplication(activity);
                            }
                        }, 1500);
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "用户选择稍后重启");
                        d.dismiss();
                        ToastUtil.showUnique(activity, "稍后手动重启");
                    }
                }
        }
        );
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    //  菜单分发对话框
    private static boolean tryNewUiTab(final Activity a, final TextView fv, int tabIndex) {
        try {
            if (!com.install.appinstall.xl.ui.UiKit.isFallbackMode()) return false;
            return com.install.appinstall.xl.ui.UiTabs.openTab(a, fv, tabIndex);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void showMoreFunctionsDialog(final Activity activity, final TextView floatingView) {
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
                if (ctx != null) {
                    ToastUtil.showUnique(ctx, "Activity 已失效，无法打开更多设置");
                }
                return;
            }
        }
        final Activity finalActivity = validActivity;
        ReaLog.log("system", "打开更多功能配置");
        if (getHookInit() == null) {
            ReaLog.log("system", "功能配置菜单异常：状态未就绪");
            return;
        }
        final String currentTargetApp = getCurrentTargetApp();
        final HookInit hookInstance = getHookInit();
        final List<MenuItem> menuItems = new ArrayList<MenuItem>();
        menuItems.add(new MenuItem(" ◎ 启动拦截设置", "控制是否拦截第三方应用启动请求", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开启动拦截设置");
                if (tryNewUiTab(finalActivity, floatingView, 1)) return;
                showLaunchInterceptConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 权限防护设置", "控制 捕获当前目标权限并假装授权", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开权限防护设置");
                if (tryNewUiTab(finalActivity, floatingView, 2)) return;
                showCustomPermissionDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 拦截退出设置", "阻止目标因检测到应用包而退出", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开拦截退出设置");
                if (tryNewUiTab(finalActivity, floatingView, 3)) return;
                showExitInterceptConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 返回键设置", "替换目标应用的返回键逻辑", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开返回键设置");
                if (tryNewUiTab(finalActivity, floatingView, 4)) return;
                showForceDefaultBackConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 伪装强制模式", "使应用检测到状态为 Enforcing", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开SELinux伪装设置");
                if (tryNewUiTab(finalActivity, floatingView, 5)) return;
                showSelinuxFakeConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 假装分享设置", "使应用内部的分享假装成功", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开假装分享设置");
                if (tryNewUiTab(finalActivity, floatingView, 6)) return;
                showShareFakeConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 网络代理设置", "隐藏VPN和代理检测", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开网络代理设置");
                if (tryNewUiTab(finalActivity, floatingView, 7)) return;
                showVpnFakeConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 痕迹检测设置", "基础隐藏Xposed痕迹", new Runnable() {
            @Override
            public void run() {
                if (tryNewUiTab(finalActivity, floatingView, 8)) return;
                ReaLog.log("system", "打开痕迹检测设置");
                showAntiDetectionConfigDialog(finalActivity, floatingView);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 配置文件导出", "将当前配置保存到私有目录", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("config", "打开配置文件导出");
                Portcf.showExportSelectionDialog(finalActivity, currentTargetApp, hookInstance);
            }
        }));
        menuItems.add(new MenuItem(" ◎ 配置文件导入", "从私有目录加载配置", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("config", "打开配置文件导入");
                Portcf.showImportSelectionDialog(finalActivity, currentTargetApp, floatingView, hookInstance);
            }
        }));
        menuItems.add(new MenuItem(" ◎ <font color='#1E90FF'><b>查看日志记录</b></font>", "Hook拦截记录日志", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "打开日志记录");
                ReaLog.showCombinedDialog(finalActivity);
            }
        }));
        menuItems.add(new MenuItem(" ◎ <font color='#4CAF50'><b>切换界面风格</b></font>",
        com.install.appinstall.xl.ui.UiKit.useNewUi(finalActivity)
                ? "当前：新版界面（点击切回旧版对话框）"
                : "当前：旧版界面（点击切到新版界面）",
        new Runnable() {
            @Override
            public void run() {
                boolean useNew = !com.install.appinstall.xl.ui.UiKit.useNewUi(finalActivity);
                com.install.appinstall.xl.ui.UiKit.setUseNewUi(finalActivity, useNew);
                ReaLog.log("system", "切换界面风格: " + (useNew ? "新版" : "旧版"));
                ToastUtil.showUnique(finalActivity,
                        useNew ? "已启用新版界面，重新打开生效" : "已切回旧版界面");
                com.install.appinstall.xl.ui.UiTabs.save(finalActivity);
            }
        }));
        menuItems.add(new MenuItem(" ◎ <font color='#FF5722'><b>检查模块版本更新</b></font>", "检测GitHub是否有新版本", new Runnable() {
            @Override
            public void run() {
                ReaLog.log("system", "检查版本更新");
                Update.checkForUpdate(finalActivity);
            }
        }));

        ArrayAdapter<MenuItem> adapter = new ArrayAdapter<MenuItem>(finalActivity, 0, menuItems) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    // 列表项内 2px 分隔线
                    LinearLayout itemLayout = new LinearLayout(finalActivity);
                    itemLayout.setOrientation(LinearLayout.VERTICAL);
                    int paddingPx = More.dpInt(finalActivity, 16);
                    int paddingSmall = More.dpInt(finalActivity, 5);
                    itemLayout.setPadding(paddingPx, paddingSmall, paddingPx, paddingSmall);
                    TextView titleView = new TextView(finalActivity);
                    titleView.setId(android.R.id.text1);
                    titleView.setTextSize(16);
                    titleView.setTextColor(0xFF333333);
                    TextView descView = new TextView(finalActivity);
                    descView.setId(android.R.id.text2);
                    descView.setTextSize(11);
                    descView.setTextColor(0xFF888888);
                    descView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    itemLayout.addView(titleView);
                    itemLayout.addView(descView);

                    // 列表项内 2px 分隔线
                    View divider = new View(finalActivity);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
                    divider.setBackgroundColor(0xFFE0E0E0);
                    itemLayout.addView(divider);
                    convertView = itemLayout;
                    convertView.setTag(new View[]{titleView, descView, divider});
                }

                View[] views = (View[]) convertView.getTag();
                TextView titleView = (TextView) views[0];
                TextView descView = (TextView) views[1];
                View divider = views[2];
                MenuItem item = getItem(position);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    titleView.setText(Html.fromHtml(item.title, Html.FROM_HTML_MODE_LEGACY));
                    descView.setText(Html.fromHtml(item.desc, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    titleView.setText(Html.fromHtml(item.title));
                    descView.setText(Html.fromHtml(item.desc));
                }
                divider.setVisibility(position == menuItems.size() - 1 ? View.GONE : View.VISIBLE);
                int leftPad = titleView.getPaddingLeft();
                descView.setPadding(leftPad, descView.getPaddingTop(),
                        descView.getPaddingRight(), descView.getPaddingBottom());
                if (position == menuItems.size() - 1) {
                    int topPad = More.dpInt(finalActivity, 2);
                    titleView.setPadding(titleView.getPaddingLeft(), topPad,
                            titleView.getPaddingRight(), titleView.getPaddingBottom());
                } else {
                    titleView.setPadding(titleView.getPaddingLeft(), 0,
                            titleView.getPaddingRight(), 0);
                }
                return convertView;
            }
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(finalActivity, getDialogThemeResId());
        builder.setTitle("更多配置设置");
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                MenuItem item = menuItems.get(which);
                if (item.action != null) {
                    ReaLog.log("system", "执行菜单项: " + item.title);
                    item.action.run();
                }
            }
        });
        builder.setNegativeButton("取消设置", null);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                try {
                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        WindowManager.LayoutParams params = window.getAttributes();
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.gravity = Gravity.CENTER;
                        params.horizontalMargin = 0.05f;
                        window.setAttributes(params);
                    }
                } catch (Exception e) {
                    ReaLog.log("system", "更多功能对话框异常: " + e.getMessage());
                    ToastUtil.showUnique(activity, "错误：配置页无法打开");
                }
            }
        });
        if (finalActivity != null && !finalActivity.isFinishing() && !dialog.isShowing()) {
            dialog.show();
        }
    }

    // 假装分享对话框
    public static void showShareFakeConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开假装分享设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        Map<String,Boolean> detailMap = HookInit.shareFakeDetailMap.getOrDefault(currentTargetApp, HookInit.getDefaultShareDetailMap());
        final Map<String, Boolean> workingMap = new HashMap<String, Boolean>(detailMap);
        final Map<String, Boolean> originalMap = new HashMap<String, Boolean>(detailMap);
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        TextView tip = new TextView(activity);
        String tipHtml = "请选择要假装分享成功的平台：<br>" +
                "<small><b>保存后<font color='#2196F3'>立即生效</font> 异常请禁用!</b></small><br>" +
                "<font color='#FF5722'><small><b>注：仅限应用自身调用官方分享SDK才生效</b></small></font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tip.setText(Html.fromHtml(tipHtml, Html.FROM_HTML_MODE_LEGACY));
        } else {
            tip.setText(Html.fromHtml(tipHtml));
        }
        tip.setTextColor(0xFF333333);
        tip.setTextSize(14);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        mainLayout.addView(tip);
        // 对话框列表文本
        final String[] keys = ShareHook.UI_KEYS;
        final String[] labels = ShareHook.UI_LABELS;

        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final String label = labels[i];
            boolean isCurrentlyDisabled = !workingMap.getOrDefault(key, false);
            final CheckBox cb = new CheckBox(activity);
            updateCheckBoxText(cb, label, !isCurrentlyDisabled);
            cb.setChecked(!isCurrentlyDisabled);
            cb.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
            cb.setTextSize(14);
            final boolean[] suppressListener = {false};
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (suppressListener[0]) return;

                    if ("aggressive".equals(key) && isChecked) {
                        AlertDialog confirm = createBoundedDialog(
                        activity,
                        "开启激进模式(Beta)",
                        "将 <font color='#2196F3'><b>所有APP+系统层</b></font> 假装分享成功<br><br>" +
                                "<small><font color='#FF5722'><b>⚠️温馨提示：</b></font></small><br>" +
                                "<small>• 部分应用的<b>[用其他应用打开]</b>可能被误拦</small><br>" +
                                "<small>• 部分应用的<b>[分享行为]</b>可能无效果</small><br>" +
                                "<small>• 如遇异常可随时关闭此开关</small><br>" +
                                "<small><font color='#9E9E9E'><b>是否启用[激进模式]？</b></font></small>",
                        new String[]{"确认开启", "取消开启"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        workingMap.put(key, true);
                                        updateCheckBoxText(cb, label, true);
                                        ReaLog.log("share_fake", "假装分享：激进模式开启");
                                        ToastUtil.showUnique(activity, "✅ 激进模式开启");
                                        d.dismiss();
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        suppressListener[0] = true;
                                        cb.setChecked(false);
                                        suppressListener[0] = false;
                                        workingMap.put(key, false);
                                        updateCheckBoxText(cb, label, false);
                                        ReaLog.log("share_fake", "假装分享：激进模式取消");
                                        ToastUtil.showUnique(activity, "取消激进模式");
                                        d.dismiss();
                                    }
                                }
                        }
                        );
                        confirm.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface d) {
                                suppressListener[0] = true;
                                cb.setChecked(false);
                                suppressListener[0] = false;
                                workingMap.put(key, false);
                                updateCheckBoxText(cb, label, false);
                            }
                        });
                        confirm.show();
                        return;
                    }

                    workingMap.put(key, isChecked);
                    updateCheckBoxText(cb, label, isChecked);
                    ReaLog.log("share", "分享设置变更: " + label + " -> " + (isChecked ? "启用" : "禁用"));
                }
            });
            mainLayout.addView(cb);
            mainLayout.addView(newListDivider(activity));
        }

        AlertDialog dialog = createBoundedDialog(
        activity,
        "假装分享设置(Beta)",
        null,
        new String[]{"保存设置", "取消"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (originalMap.equals(workingMap)) {
                            ToastUtil.showUnique(activity, "未修改设置");
                            ReaLog.log("share", "保存分享设置: 无变更");
                            d.dismiss();
                            showShareFakeConfigDialog(activity, floatingView);
                            return;
                        }
                        Map<String, Boolean> newDetail = new HashMap<String, Boolean>(workingMap);
                        HookInit.shareFakeDetailMap.put(currentTargetApp, newDetail);
                        getHookInit().saveConfigToFile();
                        ReaLog.log("share", "保存分享设置: " + getChangedKeys(originalMap, workingMap));
                        ShareHook.setAllEnabled(newDetail);
                        ShareHook.resetAllErrorCount();
                        ToastUtil.showUnique(activity, "✅ 配置已保存");
                        d.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("share", "取消分享设置");
                        d.dismiss();
                    }
                }
        },
        mainLayout
        );
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // 痕迹检测对话框
    public static void showAntiDetectionConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开痕迹检测设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        Map<String, Boolean> detailMap = new HashMap<String, Boolean>(
        HookInit.antiDetectionDetailMap.getOrDefault(currentTargetApp, new HashMap<
                String, Boolean>()));
        if (detailMap.isEmpty()) {
            detailMap = getHookInit().getDefaultAntiDetectionMap();
        } else {
            for (int i = 0; i < ALL_ANTI_KEYS.length; i++) {
                if (!detailMap.containsKey(ALL_ANTI_KEYS[i])) {
                    detailMap.put(ALL_ANTI_KEYS[i], true);
                }
            }
        }
        HookInit.antiDetectionDetailMap.put(currentTargetApp, detailMap);
        final Map<String, Boolean> workingMap = new HashMap<String, Boolean>(detailMap);
        final Map<String, Boolean> originalMap = new HashMap<String, Boolean>(detailMap);
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        TextView tip = new TextView(activity);
        String tipHtml = "请选择要启用的隐藏功能：<br>" +
                "<small><b>保存后需<font color='#2196F3'>重启应用</font>才能完全生效！</b></small><br>" +
                "<font color='#FF5722'><small><b>如有异常将自动禁用对应功能项</small></b></font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tip.setText(Html.fromHtml(tipHtml, Html.FROM_HTML_MODE_LEGACY));
        } else {
            tip.setText(Html.fromHtml(tipHtml));
        }
        tip.setTextColor(0xFF333333);
        tip.setTextSize(14);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        mainLayout.addView(tip);
        final String[] keys = ALL_ANTI_KEYS;
        final String[] labels = {
                "框架隐藏", "文件隐藏", "服务隐藏", "信息隐藏",
                "命令隐藏", "堆栈隐藏", "Root隐藏", "ADB隐藏", "USB隐藏", "开发者隐藏"
        };
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final String label = labels[i];
            boolean isDisabled = !workingMap.getOrDefault(key, true);
            final CheckBox cb = new CheckBox(activity);
            updateCheckBoxText(cb, label, !isDisabled);
            cb.setChecked(!isDisabled);
            cb.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
            cb.setTextSize(14);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    workingMap.put(key, isChecked);
                    updateCheckBoxText(cb, label, isChecked);
                    ReaLog.log("anti_detection", "痕迹检测设置变更: " + label + " -> " + (isChecked ? "启用" : "禁用"));
                }
            });
            mainLayout.addView(cb);
            mainLayout.addView(newListDivider(activity));
        }

        AlertDialog dialog = createBoundedDialog(
        activity,
        "痕迹检测设置(Beta)",
        null,
        new String[]{"保存设置", "取消", "处理列表"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (originalMap.equals(workingMap)) {
                            ToastUtil.showUnique(activity, "未修改设置");
                            ReaLog.log("system", "保存痕迹检测: 无变更");
                            d.dismiss();
                            showAntiDetectionConfigDialog(activity, floatingView);
                            return;
                        }
                        Map<String, Boolean> newDetail = new HashMap<String, Boolean>(workingMap);
                        HookInit.antiDetectionDetailMap.put(currentTargetApp, newDetail);
                        getHookInit().saveConfigToFile();
                        ReaLog.log("anti_detection", "保存痕迹检测: " + getChangedKeys(originalMap, workingMap));
                        AntiDetection.setClassEnabled(workingMap.get("class"));
                        AntiDetection.setFileEnabled(workingMap.get("file"));
                        AntiDetection.setPmEnabled(workingMap.get("pm"));
                        AntiDetection.setProcEnabled(workingMap.get("proc"));
                        AntiDetection.setCmdEnabled(workingMap.get("cmd"));
                        AntiDetection.setStacktraceEnabled(workingMap.get("stacktrace"));
                        AntiDetection.setRootEnabled(workingMap.get("root"));
                        AntiDetection.setAdbEnabled(workingMap.get("adb"));
                        AntiDetection.setDevEnabled(workingMap.get("dev"));
                        AntiDetection.setUsbEnabled(workingMap.get("usb"));
                        AntiDetection.reset();
                        AntiDetection.initForEmbed(activity.getClassLoader(), currentTargetApp, getHookInit());
                        ToastUtil.showUnique(activity, "✅ 配置已保存");
                        d.dismiss();
                        showRestartConfirmDialog(activity);
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "取消痕迹检测设置");
                        d.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "打开日志列表");
                        d.dismiss();
                        ReaLog.showCombinedDialog(activity);
                    }
                }
        },
        mainLayout
        );
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // 网络代理对话框
    public static void showVpnFakeConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开网络代理设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        Map<String,Boolean> detailMap = HookInit.vpnFakeDetailMap.getOrDefault(currentTargetApp, new HashMap<String, Boolean>());
        if (detailMap.isEmpty()) {
            detailMap = HookInit.getDefaultVpnDetailMap();
            HookInit.vpnFakeDetailMap.put(currentTargetApp, detailMap);
        }
        final Map<String, Boolean> workingMap = new HashMap<String, Boolean>(detailMap);
        final Map<String, Boolean> originalMap = new HashMap<String, Boolean>(detailMap);
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        TextView tip = new TextView(activity);
        String tipHtml = "请选择要启用的功能：<br>" +
                "<b><small>保存后<font color='#2196F3'>立即生效</font> SSL需重启应用。</small></b><br>" +
                "<font color='#FF5722'><small><b>如有异常将自动禁用对应功能项</small></b></font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tip.setText(Html.fromHtml(tipHtml, Html.FROM_HTML_MODE_LEGACY));
        } else {
            tip.setText(Html.fromHtml(tipHtml));
        }
        tip.setTextColor(0xFF333333);
        tip.setTextSize(14);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        mainLayout.addView(tip);
        final String[] keys = ALL_VPN_KEYS;
        final String[] labels = {
                "VPN接口隐藏", "深度接口隐藏", "代理检测隐藏",
                "代理环境隐藏", "抓包应用隐藏", "网络文件隐藏", "网络检测监听",
                "SSL信任链绕过", "SSL证书绑定绕过", "SSL证书信息隐藏", "SSL证书特征替换"
        };
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final String label = labels[i];
            boolean isCurrentlyDisabled = !workingMap.getOrDefault(key, true);
            final CheckBox cb = new CheckBox(activity);
            updateCheckBoxText(cb, label, !isCurrentlyDisabled);
            cb.setChecked(!isCurrentlyDisabled);
            cb.setEnabled(true);
            cb.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
            cb.setTextSize(14);
            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    workingMap.put(key, isChecked);
                    updateCheckBoxText(cb, label, isChecked);
                    ReaLog.log("vpn", "网络代理设置变更: " + label + " -> " + (isChecked ? "启用" : "禁用"));
                }
            });
            mainLayout.addView(cb);
            mainLayout.addView(newListDivider(activity));
        }
        AlertDialog dialog = createBoundedDialog(
        activity,
        "网络代理设置(Beta)",
        null,
        new String[]{"保存设置", "取消", "处理列表"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (originalMap.equals(workingMap)) {
                            ToastUtil.showUnique(activity, "未修改设置");
                            ReaLog.log("vpn", "保存网络代理: 无变更");
                            d.dismiss();
                            showVpnFakeConfigDialog(activity, floatingView);
                            return;
                        }
                        HookInit.vpnFakeDetailMap.put(currentTargetApp, new HashMap<String, Boolean>(workingMap));
                        getHookInit().saveConfigToFile();
                        ReaLog.log("vpn", "保存网络代理: " + getChangedKeys(originalMap, workingMap));
                        ToastUtil.showUnique(activity, "✅ 配置已保存");
                        d.dismiss();
                        boolean sslChanged = false;
                        String[] sslKeys = {"ssl_trust", "ssl_pinning", "ssl_cert", "ssl_cert_hide"};
                        for (String key : sslKeys) {
                            boolean oldVal = originalMap.getOrDefault(key, true);
                            boolean newVal = workingMap.getOrDefault(key, true);
                            if (oldVal != newVal) {
                                sslChanged = true;
                                break;
                            }
                        }
                        if (sslChanged) {
                            ReaLog.log("vpn", "SSL设置变更，需要重启");
                            showRestartConfirmDialog(activity);
                        }
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("vpn", "取消网络代理设置");
                        d.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "打开日志列表");
                        d.dismiss();
                        ReaLog.showCombinedDialog(activity);
                    }
                }
        },
        mainLayout
        );
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // 返回键控制对话框
    public static void showForceDefaultBackConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开返回键设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        final boolean isEnabled = HookInit.forceDefaultBackMap.getOrDefault(currentTargetApp, false);
        String status = isEnabled ? "<font color='#F44336'><b>模块级</b></font>" : "<font color='#F44336'><b>应用自定义</b></font>";
        String message = "当前返回键状态：" + status + "<br><br>" +
                "<font color='#9E9E9E'>• [模块级] 返回键将替换为本模块控制，智能处理返回逻辑</font><br>" +
                "<font color='#9E9E9E'>• [自定义] 使用应用自身的返回键逻辑(如禁止返回)</font><br><br>" +
                "<font color='#FF5722'><small><b>• 部分应用可能行为异常(游戏/播放器)，异常请换回[自定义]！</b></small></font><br>" +
                "<font color='#FF5722'><small><b>• 当前[模块级]为测试版，仅适配多Activity架构</b></small></font><br>" +
                "<small><b>• 若无效需<font color='#2196F3'>重启应用</font>使得完全生效！</b></small>";

        AlertDialog dialog = createBoundedDialog(
        activity,
        "返回键设置(Beta)",
        message,
        new String[]{isEnabled ? "使用自定义" : "使用模块级", "取消设置"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        boolean newState = !isEnabled;
                        HookInit.forceDefaultBackMap.put(currentTargetApp, newState);
                        getHookInit().saveConfigToFile();
                        ReaLog.log("back_key", "返回键切换为" + (newState ? "模块级" : "自定义"));
                        ToastUtil.showUnique(activity, newState ? "✅ 使用模块级" : "✅ 使用自定义");
                        d.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "取消返回键设置");
                        d.dismiss();
                    }
                }
        }
        );
        dialog.show();
    }

    // 拦截退出对话框
    public static void showExitInterceptConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开退出拦截设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        boolean autoEnabled = !HookInit.userDisabledAutoBlockMap.getOrDefault(currentTargetApp, false);
        boolean blockExit = HookInit.blockExitMap.getOrDefault(currentTargetApp, false);
        boolean crashProtect = HookInit.crashProtectEnabledMap.getOrDefault(currentTargetApp, true);
        boolean superBlock = HookInit.superBlockExitMap.getOrDefault(currentTargetApp, false);
        final Map<String, Boolean> workingMap = new HashMap<String, Boolean>();
        workingMap.put("auto_enable", autoEnabled);
        workingMap.put("block_exit", blockExit);
        workingMap.put("crash_protect", crashProtect);
        workingMap.put("super_block", superBlock);
        final Map<String, Boolean> originalMap = new HashMap<String, Boolean>(workingMap);
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        TextView tip = new TextView(activity);
        String tipHtml = "请选择要启用的拦截功能：<br>" +
                "<small><b>保存后<font color='#2196F3'>立即生效</font> 异常请关闭！</small></b><br>" +
                "<font color='#FF5722'><small><b>如有异常将自动禁用对应功能项</small></b></font>";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tip.setText(Html.fromHtml(tipHtml, Html.FROM_HTML_MODE_LEGACY));
        } else {
            tip.setText(Html.fromHtml(tipHtml));
        }
        tip.setTextColor(0xFF333333);
        tip.setTextSize(14);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        mainLayout.addView(tip);

        final String[] keys = {"auto_enable", "block_exit", "crash_protect", "super_block"};
        final String[] labels = {
                "自动开启：捕获到新包名时开启普通拦截",
                "普通拦截：拦截应用常用的主流退出方案",
                "Java异常：全局捕获异常尽量吞噬数据",
                "超强拦截：增强普通拦截以外的补丁"
        };
        final Map<String, CheckBox> checkBoxMap = new HashMap<String, CheckBox>();
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            final String label = labels[i];
            boolean isCurrentlyDisabled = !workingMap.getOrDefault(key, true);
            final CheckBox cb = new CheckBox(activity);
            if ("auto_enable".equals(key)) {
                String displayText;
                if (!isCurrentlyDisabled) {
                    displayText = "<font color='#2196F3'><b>" + label + "</b></font>";
                } else {
                    displayText = "<font color='#F44336'><b>" + label + "</b></font>" +
                            "<font color='#FF5722'><small><b> (已禁用)</small></b></font>";
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cb.setText(Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    cb.setText(Html.fromHtml(displayText));
                }
            } else {
                updateCheckBoxText(cb, label, !isCurrentlyDisabled);
            }

            cb.setChecked(!isCurrentlyDisabled);
            cb.setEnabled(true);
            cb.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
            cb.setTextSize(14);

            cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    workingMap.put(key, isChecked);
                    ReaLog.log("exit_intercept", "退出拦截设置变更: " + label + " -> " + (isChecked ? "启用" : "禁用"));
                    if ("auto_enable".equals(key)) {
                        String displayText;
                        if (isChecked) {
                            displayText = "<font color='#2196F3'><b>" + label + "</b></font>";
                        } else {
                            displayText = "<font color='#F44336'><b>" + label + "</b></font>" +
                                    "<font color='#FF5722'><small><b> (已禁用)</small></b></font>";
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            cb.setText(Html.fromHtml(displayText, Html.FROM_HTML_MODE_LEGACY));
                        } else {
                            cb.setText(Html.fromHtml(displayText));
                        }
                    } else {
                        updateCheckBoxText(cb, label, isChecked);
                    }
                    if ("block_exit".equals(key) && isChecked) {
                        final CheckBox crashCb = checkBoxMap.get("crash_protect");
                        if (crashCb != null && !crashCb.isChecked()) {
                            crashCb.setChecked(true);
                            workingMap.put("crash_protect", true);
                            updateCheckBoxText(crashCb, "Java异常：全局捕获异常尽量吞噬数据", true);
                            ReaLog.log("exit_intercept", "普通拦截开启，智能联动开启捕获Java异常");
                        }
                    }
                }
            });
            mainLayout.addView(cb);
            checkBoxMap.put(key, cb);
            mainLayout.addView(newListDivider(activity));
        }
        AlertDialog dialog = createBoundedDialog(
        activity,
        "退出拦截设置(Beta)",
        null,
        new String[]{"保存设置", "取消", "处理列表"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (originalMap.equals(workingMap)) {
                            ToastUtil.showUnique(activity, "未修改设置");
                            ReaLog.log("system", "保存退出拦截设置: 无变更");
                            d.dismiss();
                            showExitInterceptConfigDialog(activity, floatingView);
                            return;
                        }
                        boolean autoNew = workingMap.get("auto_enable");
                        HookInit.userDisabledAutoBlockMap.put(currentTargetApp, !autoNew);
                        HookInit.blockExitMap.put(currentTargetApp, workingMap.get("block_exit"));
                        HookInit.superBlockExitMap.put(currentTargetApp, workingMap.get("super_block"));
                        boolean crashNew = workingMap.get("crash_protect");
                        HookInit.crashProtectEnabledMap.put(currentTargetApp, crashNew);
                        getHookInit().saveConfigToFile();
                        getHookInit().applyCrashProtect(crashNew, activity);
                        ReaLog.log("exit_intercept", "保存退出拦截设置: auto=" + autoNew +
                                ", block=" + workingMap.get("block_exit") +
                                ", super=" + workingMap.get("super_block") +
                                ", crash=" + crashNew);

                        if (floatingView != null) {
                            String statusText = HookInit.installStatusMap.getOrDefault(currentTargetApp, true) ? "已安装" : "未安装";
                            String blockText = (HookInit.blockExitMap.getOrDefault(currentTargetApp, false) ||
                                            HookInit.superBlockExitMap.getOrDefault(currentTargetApp, false)) ? "[拦截]" : "";
                            floatingView.setText("安装防护(" + statusText + ")" + blockText);
                        }

                        ToastUtil.showUnique(activity, "✅ 配置已保存");
                        d.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "取消退出拦截设置");
                        d.dismiss();
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "打开拦截记录管理");
                        d.dismiss();
                        Spkill.showManagePatternsDialog(activity, getHookInit());
                    }
                }
        },
        mainLayout
        );
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    // 启动拦截对话框
    public static void showLaunchInterceptConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开启动拦截设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        final boolean isLaunchInterceptEnabled = HookInit.launchInterceptMap.getOrDefault(currentTargetApp, true);
        String currentStatus = isLaunchInterceptEnabled ? "<font color='#4CAF50'>已开启</font>" : "<font color='#F44336'>已关闭</font>";
        String htmlMessage = "当前启动拦截状态：" + currentStatus + "<br><br>" +
                "<font color='#9E9E9E'>• 开启：拦截所有第三方应用启动请求</font><br>" +
                "<font color='#9E9E9E'>• 关闭：允许应用正常启动第三方程序</font><br>" +
                "<font color='#9E9E9E'>• 列表：管理黑白名单/智能处理规则</font><br><br>";
        final AlertDialog dialog = new AlertDialog.Builder(activity, getDialogThemeResId())
                .setTitle("启动拦截设置")
                .setMessage(Html.fromHtml(htmlMessage))
                .setPositiveButton("开启启动拦截", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (!HookInit.launchInterceptMap.getOrDefault(currentTargetApp, true)) {
                            HookInit.launchInterceptMap.put(currentTargetApp, true);
                            getHookInit().saveConfigToFile();
                            ReaLog.log("launch_intercept", "启动拦截: 开启");
                            ToastUtil.showUnique(activity, "✅ 启动拦截已开启");
                        } else {
                            ToastUtil.showUnique(activity, "当前已是开启状态");
                            ReaLog.log("system", "启动拦截: 已是开启状态");
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("关闭启动拦截", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (HookInit.launchInterceptMap.getOrDefault(currentTargetApp, true)) {
                            HookInit.launchInterceptMap.put(currentTargetApp, false);
                            getHookInit().saveConfigToFile();
                            ReaLog.log("launch_intercept", "启动拦截: 关闭");
                            ToastUtil.showUnique(activity, "❌ 启动拦截已关闭");
                        } else {
                            ToastUtil.showUnique(activity, "当前已是关闭状态");
                            ReaLog.log("system", "启动拦截: 已是关闭状态");
                        }
                        dialog.dismiss();
                    }
                })
                .setNeutralButton("处理列表", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ReaLog.log("system", "打开启动拦截处理列表");
                        dialog.dismiss();
                        instar.showManageListDialog(activity, getHookInit(), currentTargetApp);
                    }
                })
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                try {
                    android.view.Window window = dialog.getWindow();
                    if (window != null) {
                        WindowManager.LayoutParams params = window.getAttributes();
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.gravity = Gravity.CENTER;
                        params.horizontalMargin = 0.05f;
                        window.setAttributes(params);
                    }
                } catch (Exception e) {
                    ReaLog.log("system", "启动拦截对话框异常: " + e.getMessage());
                }
            }
        });
        if (activity != null && !activity.isFinishing() && !dialog.isShowing()) {
            dialog.show();
        }
    }

    // 强制模式对话框
    public static void showSelinuxFakeConfigDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开SELinux伪装设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        final boolean isEnabled = HookInit.selinuxFakeMap.getOrDefault(currentTargetApp, true);
        String status = isEnabled ? "<font color='#4CAF50'>已开启</font>" : "<font color='#F44336'>已关闭</font>";
        String realMode = Selinuxhook.getRealSelinuxMode();
        String fakeMode = "Enforcing[强制模式]";
        String message = "当前 SELinux 伪装状态：" + status + "<br><br>" +
                "<font color='#9E9E9E'>• 本系统为 <b>" + realMode + " 模式[仅参考]</b></font><br>" +
                "<font color='#9E9E9E'>• 应用收到为 <b>" + fakeMode + "</b></font><br><br>" +
                "<font color='#9E9E9E'>• [开启] 系统状态将被强制返回 Enforcing</font><br>" +
                "<font color='#9E9E9E'>• [关闭] 系统状态不做任何处理</font><br><br>" +
                "<font color='#FF5722'><small><b>当前功能为测试版，部分应用可能无效！</small></b></font><br>" +
                "<font color='#FF5722'><small><b>注：设置后需要重启应用才能完全生效</small></b></font>";
        AlertDialog dialog = createBoundedDialog(
        activity, "伪装强制模式(Beta)", message,
        new String[]{isEnabled ? "关闭设置" : "开启设置", "取消"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        boolean newState = !isEnabled;
                        HookInit.selinuxFakeMap.put(currentTargetApp, newState);
                        getHookInit().saveConfigToFile();
                        Selinuxhook.setEnabled(currentTargetApp, newState);
                        ReaLog.log("selinux", "SELinux伪装切换: " + (newState ? "开启" : "关闭"));
                        ToastUtil.showUnique(activity, newState ? "✅ 配置已保存" : "❌ 伪装已关闭");
                        d.dismiss();
                        showRestartConfirmDialog(activity);
                    }
                },
                null
        }
        );
        dialog.show();
    }

    // 权限防护对话框
    public static void showCustomPermissionDialog(final Activity activity, final TextView floatingView) {
        ReaLog.log("system", "打开权限防护设置对话框");
        final String currentTargetApp = getCurrentTargetApp();
        final HookInit hookInstance = getHookInit();
        final boolean currentGlobalFake = HookInit.permissionFakeMap.getOrDefault(currentTargetApp, true);
        final boolean originalGlobalFake = currentGlobalFake;
        Map<String, Boolean> detailMap = HookInit.permissionFakeDetailMap.get(currentTargetApp);
        if (detailMap == null) {
            detailMap = new HashMap<String, Boolean>();
            HookInit.permissionFakeDetailMap.put(currentTargetApp, detailMap);
        }
        final List<String> allPermissions = hookInstance.getAppDeclaredPermissions();
        if (allPermissions.isEmpty()) {
            ToastUtil.showUnique(activity, "无法获取应用权限列表");
            ReaLog.log("permission", "无法获取应用权限列表");
            return;
        }
        final Map<String, Boolean> workingMap = new HashMap<String, Boolean>();
        for (String perm : allPermissions) {
            boolean defaultVal = currentGlobalFake && java.util.Arrays.asList(HookInit.DETECTION_PERMISSIONS).contains(perm);
            if (detailMap.containsKey(perm)) {
                workingMap.put(perm, detailMap.get(perm));
            } else {
                workingMap.put(perm, defaultVal);
            }
        }
        final Map<String, Boolean> originalWorkingMap = new HashMap<String, Boolean>(workingMap);
        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 30), More.spx(activity, 20), More.spx(activity, 30), More.spx(activity, 20));
        final TextView statusLine = new TextView(activity);
        statusLine.setText("权限防护：防护中 | 假装已授权(0)项");
        statusLine.setTextColor(0xFF333333);
        statusLine.setTextSize(14);
        statusLine.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 15));
        mainLayout.addView(statusLine);
        TextView tipView = new TextView(activity);
        tipView.setText("勾选对应权限将返回假装「已授权」\n取消全部勾选即关闭权限防护,放行应用检查\n请按需选择假装,部分权限假装后可能出现异常或无效！");
        tipView.setTextColor(0xFFFF0000);
        tipView.setTextSize(10);
        tipView.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        tipView.setGravity(Gravity.START);
        mainLayout.addView(tipView);
        final android.widget.HorizontalScrollView topBarScroll = new android.widget.HorizontalScrollView(activity);
        topBarScroll.setFillViewport(true);
        topBarScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout topBar = new LinearLayout(activity);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 8), More.spx(activity, 0));
        final Button toggleSelectAllBtn = newToolbarButton(activity, "全选", 0xFFEF9A9A);
        final Button filterBtn = newToolbarButton(activity, "默认权限", 0xFFEF9A9A);
        final boolean[] filterDefaultOnly = new boolean[]{false};
        final List<String> groupNamesRef = new ArrayList<String>();
        final Button cleanConfigBtn = newToolbarButton(activity, "清理配置", 0xFFEF5350);
        final Button toggleCollapseBtn = newToolbarButton(activity, "全部折叠", 0xFFEF9A9A);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 0, 14, 0);
        topBar.addView(toggleSelectAllBtn, btnParams);
        topBar.addView(filterBtn, btnParams);
        topBar.addView(cleanConfigBtn, btnParams);
        topBar.addView(toggleCollapseBtn, btnParams);
        topBarScroll.addView(topBar);
        mainLayout.addView(topBarScroll, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        final ScrollView scrollView = new ScrollView(activity);
        scrollView.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        scrollView.setFocusable(false);
        scrollView.setFocusableInTouchMode(false);
        final LinearLayout listContainer = new LinearLayout(activity);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(More.spx(activity, 8), More.spx(activity, 6), More.spx(activity, 8), More.spx(activity, 10));
        scrollView.addView(listContainer);
        mainLayout.addView(scrollView, new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        final List<CheckBox> checkBoxList = new ArrayList<CheckBox>();
        final Runnable[] refreshListRef = new Runnable[1];
        final Map<String, Boolean> groupCollapsedMap = new HashMap<String, Boolean>();
        // 各组的快速全选框（折叠组重建列表时旧框被回收，同步更新）
        final Map<String, CheckBox> groupToggleMap = new HashMap<String, CheckBox>();
        final android.widget.EditText searchEdit = new android.widget.EditText(activity);
        searchEdit.setHint("搜索权限名 / 权限字符串");
        searchEdit.setSingleLine(true);
        searchEdit.setTextSize(13);
        searchEdit.setPadding(More.spx(activity, 16), More.spx(activity, 10), More.spx(activity, 16), More.spx(activity, 10));
        searchEdit.setTextColor(0xFF333333);
        searchEdit.setHintTextColor(0xFF999999);
        searchEdit.setBackground(getRoundButtonDrawable(0xFFF5F5F5));
        searchEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        searchEdit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchEdit.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshListRef[0].run();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        searchEdit.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                        (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    refreshListRef[0].run();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        // 搜索框
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        searchParams.setMargins(0, 14, 0, 10);
        mainLayout.addView(searchEdit, mainLayout.indexOfChild(scrollView), searchParams);
        final Runnable updateStatusLine = new Runnable() {
            @Override
            public void run() {
                int checkedCount = 0;
                for (Boolean v : workingMap.values()) {
                    if (v) checkedCount++;
                }
                boolean globalEnabled = (checkedCount > 0);
                String statusText = "权限防护：" + (globalEnabled ? "防护中" : "未防护") +
                        " | 假装已授权(" + checkedCount + ")项";
                statusLine.setText(statusText);
                statusLine.setTextColor(globalEnabled ? 0xFF4CAF50 : 0xFFF44336);
            }
        };

        final Runnable updateSelectAllButtonState = new Runnable() {
            @Override
            public void run() {
                // 按当前视图范围判断，不依赖折叠的 checkBoxList
                List<String> scopePerms = getScopePermissions(allPermissions, filterDefaultOnly[0]);
                boolean allChecked = !scopePerms.isEmpty();
                for (String perm : scopePerms) {
                    if (!workingMap.getOrDefault(perm, false)) {
                        allChecked = false;
                        break;
                    }
                }
                if (allChecked) {
                    toggleSelectAllBtn.setText("取消全选");
                    toggleSelectAllBtn.setBackground(getRoundButtonDrawable(0xFF1E88E5));
                } else {
                    toggleSelectAllBtn.setText("全选");
                    toggleSelectAllBtn.setBackground(getRoundButtonDrawable(0xFFEF9A9A));
                }
            }
        };

        refreshListRef[0] = new Runnable() {
            @Override
            public void run() {
                listContainer.removeAllViews();
                checkBoxList.clear();
                boolean filterOnlyDefault = filterDefaultOnly[0];
                String query = searchEdit.getText().toString().trim().toLowerCase();
                // 列表排序重建
                java.util.LinkedHashMap<String, List<String>> groups = new java.util.LinkedHashMap<
                        String, List<String>>();
                java.util.LinkedHashMap<
                        String, List<String>> rawGroups = new java.util.LinkedHashMap<
                        String, List<String>>();
                for (final String perm : allPermissions) {
                    if (perm == null || perm.trim().isEmpty()) continue;
                    if (filterOnlyDefault && !java.util.Arrays.asList(HookInit.DETECTION_PERMISSIONS).contains(perm))
                        continue;
                    String friendly0 = PermissionName.getFriendlyName(perm);
                    String effFriendly = (friendly0 == null || friendly0.trim().isEmpty()) ? perm : friendly0;
                    if (query.length() > 0 && !(effFriendly.toLowerCase().contains(query) || perm.toLowerCase().contains(query)))
                        continue;
                    String groupName = PermissionName.groupOf(perm);
                    List<String> gList = rawGroups.get(groupName);
                    if (gList == null) {
                        gList = new ArrayList<String>();
                        rawGroups.put(groupName, gList);
                    }
                    gList.add(perm);
                }
                // 权限排序
                List<String> orderedNames = new ArrayList<String>(rawGroups.keySet());
                java.util.Collections.sort(orderedNames, new java.util.Comparator<String>() {
                    @Override
                    public int compare(String a, String b) {
                        return PermissionName.groupOrder(a) - PermissionName.groupOrder(b);
                    }
                });
                for (String gn : orderedNames) {
                    groups.put(gn, rawGroups.get(gn));
                }
                groupNamesRef.clear();
                groupNamesRef.addAll(groups.keySet());
                if (groups.isEmpty()) {
                    TextView empty = new TextView(activity);
                    empty.setText("没有匹配的权限");
                    empty.setPadding(More.spx(activity, 20), More.spx(activity, 50), More.spx(activity, 20), More.spx(activity, 50));
                    empty.setTextColor(0xFF999999);
                    empty.setGravity(Gravity.CENTER);
                    listContainer.addView(empty);
                }
                for (Map.Entry<String, List<String>> entry : groups.entrySet()) {
                    final String groupName = entry.getKey();
                    List<String> perms = entry.getValue();
                    boolean collapsed = groupCollapsedMap.containsKey(groupName) && groupCollapsedMap.get(groupName);

                    // 卡片区域
                    final LinearLayout groupCard = new LinearLayout(activity);
                    groupCard.setOrientation(LinearLayout.VERTICAL);
                    groupCard.setBackground(getRoundCardDrawable(0xFFF5F5F5));
                    LinearLayout.LayoutParams gcParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    gcParams.setMargins(0, 12, 0, 0);
                    listContainer.addView(groupCard, gcParams);

                    // 标题行
                    final LinearLayout headerRow = new LinearLayout(activity);
                    headerRow.setOrientation(LinearLayout.HORIZONTAL);
                    headerRow.setGravity(Gravity.CENTER_VERTICAL);
                    headerRow.setPadding(More.spx(activity, 16), More.spx(activity, 12), More.spx(activity, 16), More.spx(activity, 10));
                    groupCard.addView(headerRow, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    TextView header = new TextView(activity);
                    android.text.SpannableStringBuilder hText = new android.text.SpannableStringBuilder();
                    String arrow = collapsed ? "▶ " : "▼ ";
                    hText.append(arrow + groupName);
                    int nameStart = hText.length();
                    hText.append("（" + perms.size() + "）");
                    hText.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), arrow.length(), nameStart, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    hText.setSpan(new android.text.style.RelativeSizeSpan(0.8f), nameStart, hText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    hText.setSpan(new android.text.style.ForegroundColorSpan(0xFF90A4AE), nameStart, hText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    header.setText(hText);
                    header.setTextSize(14);
                    header.setTextColor(0xFF263238);
                    header.setSingleLine(true);
                    header.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    headerRow.addView(header, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1));
                    header.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            boolean cur = groupCollapsedMap.containsKey(groupName) && groupCollapsedMap.get(groupName);
                            groupCollapsedMap.put(groupName, !cur);
                            refreshListRef[0].run();
                        }
                    });

                    // 组快速全选框：全勾→勾选，全不勾→不勾，点击切换该组全部
                    final List<String> groupPerms = perms;
                    final CompoundButton.OnCheckedChangeListener groupToggleListener = new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            for (String gp : groupPerms) {
                                workingMap.put(gp, isChecked);
                            }
                            // 组展开时同步组内 CheckBox 视觉态（折叠组无 CheckBox，下次刷新时重建）
                            for (int k = 0; k < checkBoxList.size(); k++) {
                                CheckBox cc = checkBoxList.get(k);
                                if (groupPerms.contains(cc.getTag())) {
                                    cc.setChecked(isChecked);
                                }
                            }
                            updateSelectAllButtonState.run();
                            updateStatusLine.run();
                            ReaLog.log("permission", "组快速全选: " + groupName + " -> " + (isChecked ? "全组防护" : "全组放行") + "（" + groupPerms.size() + " 项）");
                        }
                    };
                    final CheckBox groupToggle = new CheckBox(activity);
                    groupToggle.setPadding(More.spx(activity, 12), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
                    boolean groupAllChecked = true;
                    for (String gp : groupPerms) {
                        if (!workingMap.getOrDefault(gp, false)) {
                            groupAllChecked = false;
                            break;
                        }
                    }
                    groupToggle.setOnCheckedChangeListener(null);
                    groupToggle.setChecked(groupAllChecked);
                    groupToggle.setOnCheckedChangeListener(groupToggleListener);
                    headerRow.addView(groupToggle, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    groupToggleMap.put(groupName, groupToggle);

                    if (collapsed) continue;

                    // 标题栏与列表间的缩进分隔线
                    groupCard.addView(newIndentedDivider(activity));
                    for (int i = 0; i < perms.size(); i++) {
                        final String perm = perms.get(i);
                        final CheckBox cb = new CheckBox(activity);
                        String friendly = PermissionName.getFriendlyName(perm);
                        if (friendly == null || friendly.trim().isEmpty()) {
                            friendly = perm;
                        }
                        android.text.SpannableStringBuilder cbText = new android.text.SpannableStringBuilder();
                        cbText.append(friendly + "\n");
                        int permStart = cbText.length();
                        cbText.append(perm);
                        cbText.setSpan(new android.text.style.RelativeSizeSpan(0.85f), permStart, cbText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        cbText.setSpan(new android.text.style.ForegroundColorSpan(0xFF888888), permStart, cbText.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        cb.setText(cbText);
                        cb.setTextSize(13);
                        cb.setTextColor(0xFF333333);
                        cb.setPadding(More.spx(activity, 16), More.spx(activity, 12), More.spx(activity, 16), More.spx(activity, 12));
                        cb.setSingleLine(false);
                        cb.setMaxLines(3);
                        cb.setEllipsize(android.text.TextUtils.TruncateAt.END);
                        cb.setTag(perm);
                        cb.setChecked(workingMap.getOrDefault(perm, false));
                        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                            @Override
                            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                                workingMap.put(perm, isChecked);
                                // 同步本组快速全选：全组勾选→组框勾选，全组不勾→组框不勾
                                CheckBox gt = groupToggleMap.get(groupName);
                                if (gt != null) {
                                    boolean allChecked = true;
                                    for (String gp : groupPerms) {
                                        if (!workingMap.getOrDefault(gp, false)) {
                                            allChecked = false;
                                            break;
                                        }
                                    }
                                    if (gt.isChecked() != allChecked) {
                                        gt.setOnCheckedChangeListener(null);
                                        gt.setChecked(allChecked);
                                        gt.setOnCheckedChangeListener(groupToggleListener);
                                    }
                                }
                                updateSelectAllButtonState.run();
                                updateStatusLine.run();
                                ReaLog.log("permission", "权限勾选变更: " + perm + " -> " + (isChecked ? "授权" : "放行"));
                            }
                        });
                        cb.setOnLongClickListener(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                com.install.appinstall.xl.ui.UiKit.haptic(v);
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("permission", perm);
                                clipboard.setPrimaryClip(clip);
                                ToastUtil.showUnique(activity, "✅ 已复制\n" + perm);
                                ReaLog.log("permission", "复制权限名: " + perm);
                                return true;
                            }
                        });
                        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                        groupCard.addView(cb, cbParams);
                        checkBoxList.add(cb);

                        // 行与行之间 1px 分隔线，同组最后一行不加
                        if (i < perms.size() - 1) {
                            groupCard.addView(newIndentedDivider(activity));
                        }
                    }
                }
                updateSelectAllButtonState.run();
                updateStatusLine.run();
                // 内联计算「全部折叠/展开」按钮状态：基于当前视图组集合 + 实时折叠状态
                boolean allGroupsCollapsed = (groupNamesRef.size() > 0);
                for (int i = 0; i < groupNamesRef.size(); i++) {
                    String gn = groupNamesRef.get(i);
                    if (!(groupCollapsedMap.containsKey(gn) && groupCollapsedMap.get(gn))) {
                        allGroupsCollapsed = false;
                        break;
                    }
                }
                // 全折叠(显示"全部展开")=深蓝，否则(显示"全部折叠")=浅粉
                toggleCollapseBtn.setText(allGroupsCollapsed ? "全部展开" : "全部折叠");
                toggleCollapseBtn.setBackground(getRoundButtonDrawable(allGroupsCollapsed ? 0xFF1E88E5 : 0xFFEF9A9A));
                ReaLog.log("permission", "权限列表刷新完成，总数: " + checkBoxList.size());
            }
        };

        filterBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterDefaultOnly[0] = !filterDefaultOnly[0];
                if (filterDefaultOnly[0]) {
                    filterBtn.setText("全局权限");
                    filterBtn.setBackground(getRoundButtonDrawable(0xFF1E88E5));
                    ReaLog.log("system", "切换到全局权限视图");
                } else {
                    filterBtn.setText("默认权限");
                    filterBtn.setBackground(getRoundButtonDrawable(0xFFEF9A9A));
                    ReaLog.log("system", "切换到默认权限视图");
                }
                refreshListRef[0].run();
            }
        });

        toggleSelectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> scopePerms = getScopePermissions(allPermissions, filterDefaultOnly[0]);
                int showCount = scopePerms.size();
                boolean currentlyAllChecked = !scopePerms.isEmpty();
                for (String perm : scopePerms) {
                    if (!workingMap.getOrDefault(perm, false)) {
                        currentlyAllChecked = false;
                        break;
                    }
                }
                boolean targetState = !currentlyAllChecked;
                for (String perm : scopePerms) {
                    workingMap.put(perm, targetState);
                }
                // 轻提示：数量按当前视图实际范围；有折叠组时注明
                int anyCollapsed = 0;
                for (int i = 0; i < groupNamesRef.size(); i++) {
                    if (groupCollapsedMap.containsKey(groupNamesRef.get(i)) && groupCollapsedMap.get(groupNamesRef.get(i))) {
                        anyCollapsed++;
                    }
                }
                String scopeWord = filterDefaultOnly[0] ? "默认权限" : "";
                if (targetState) {
                    ToastUtil.showUnique(activity, anyCollapsed > 0
                            ? "✅ 已全选" + scopeWord + " " + showCount + " 项（含折叠组）"
                            : "✅ 已全选" + scopeWord + " " + showCount + " 项");
                } else {
                    ToastUtil.showUnique(activity, "已取消全选" + scopeWord + "（" + showCount + " 项）");
                }
                refreshListRef[0].run();
                ReaLog.log("misc", "权限全选/取消全选: " + (targetState ? "全选" : "取消全选") +
                        (filterDefaultOnly[
                                0] ? "（默认权限视图，操作 " + showCount + " 项）" : "（全部权限 " + showCount + " 项）"));
            }
        });

        toggleCollapseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 直接检查当前是否所有组都折叠，取反作为目标态
                boolean allCollapsedNow = (groupNamesRef.size() > 0);
                for (int i = 0; i < groupNamesRef.size(); i++) {
                    String gn = groupNamesRef.get(i);
                    if (!(groupCollapsedMap.containsKey(gn) && groupCollapsedMap.get(gn))) {
                        allCollapsedNow = false;
                        break;
                    }
                }
                boolean targetCollapsed = !allCollapsedNow;
                for (int i = 0; i < groupNamesRef.size(); i++) {
                    groupCollapsedMap.put(groupNamesRef.get(i), targetCollapsed);
                }
                refreshListRef[0].run();
                ReaLog.log("system", "权限列表全部" + (targetCollapsed ? "折叠" : "展开"));
            }
        });

        cleanConfigBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Map<String,Boolean> currentDetail = HookInit.permissionFakeDetailMap.get(currentTargetApp);
                if (currentDetail == null || currentDetail.isEmpty()) {
                    ToastUtil.showUnique(activity, "暂无自定义配置");
                    return;
                }
                AlertDialog confirmDialog = createBoundedDialog(
                activity,
                "清理配置",
                "确定要清空所有<font color='#FF5722'><b>自定义勾选</b></font>的权限配置吗？<br><br>清空后将恢复默认勾选基础权限。",
                new String[]{"确认清理", "取消"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                ReaLog.log("misc", "清理自定义权限配置");
                                HookInit.permissionFakeDetailMap.put(currentTargetApp, new HashMap<
                                        String, Boolean>());
                                HookInit.permissionFakeMap.put(currentTargetApp, true);
                                for (String perm : allPermissions) {
                                    boolean isDefault = java.util.Arrays.asList(HookInit.DETECTION_PERMISSIONS).contains(perm);
                                    workingMap.put(perm, isDefault);
                                }
                                // 同步更新原始状态
                                originalWorkingMap.clear();
                                originalWorkingMap.putAll(workingMap);
                                getHookInit().saveConfigToFile();
                                hookInstance.reHookPermissionMethods(activity.getClassLoader());
                                hookInstance.clearPermissionCache(activity);
                                hookInstance.sendPermissionChangeBroadcast(activity);
                                refreshListRef[0].run();
                                ToastUtil.showUnique(activity, "✅ 清理成功");
                                d.dismiss();
                            }
                        },
                        null
                }
                );
                confirmDialog.show();
            }
        });
        refreshListRef[0].run();
        final AlertDialog dialog = createBoundedDialog(
        activity,
        "权限防护设置(Beta)",
        null,
        new String[]{"保存设置", "取消"},
        new DialogInterface.OnClickListener[]{
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                  Map<String, Boolean> currentUIState = new HashMap<String, Boolean>(workingMap);
                        if (!currentUIState.equals(originalWorkingMap)) {
                            Map<String, Boolean> newDetail = new HashMap<String, Boolean>();
                            boolean anyFaked = false;
                            for (Map.Entry<String, Boolean> entry : currentUIState.entrySet()) {
                                String perm = entry.getKey();
                                boolean isChecked = entry.getValue();
                                boolean isDefault = java.util.Arrays.asList(HookInit.DETECTION_PERMISSIONS).contains(perm);
                                if (isChecked != isDefault) {
                                    newDetail.put(perm, isChecked);
                                }
                                if (isChecked) anyFaked = true;
                            }
                            boolean newGlobalFake = anyFaked;
                            HookInit.permissionFakeMap.put(currentTargetApp, newGlobalFake);
                            HookInit.permissionFakeDetailMap.put(currentTargetApp, newDetail);
                            getHookInit().saveConfigToFile();
                            ReaLog.log("misc", "保存权限设置: 全局=" + (newGlobalFake ? "开启" : "关闭") +
                                    ", 自定义=" + newDetail.size() + "项");

                            hookInstance.reHookPermissionMethods(activity.getClassLoader());
                            hookInstance.clearPermissionCache(activity);
                            hookInstance.sendPermissionChangeBroadcast(activity);
                            if (newGlobalFake != originalGlobalFake) {
                                ToastUtil.showUnique(activity, newGlobalFake ? "✅ 权限防护已开启" : "❌ 权限防护已关闭");
                                hookInstance.showPermissionFakeRefreshPrompt(activity, newGlobalFake);
                            } else {
                                ToastUtil.showUnique(activity, "✅ 配置已保存");
                            }
                            if (hookInstance != null) {
                                hookInstance.updateFloatingView(activity);
                            }
                            d.dismiss();
                        } else {
                            ToastUtil.showUnique(activity, "未修改设置");
                            ReaLog.log("system", "保存权限设置: 无变更");
                            d.dismiss();
                            showCustomPermissionDialog(activity, floatingView);
                        }
                    }
                },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        ReaLog.log("system", "取消权限设置");
                        d.dismiss();
                    }
                }
        },
        mainLayout
        );
        dialog.show();
        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        } catch (Throwable ignored) {
        }
        ReaLog.log("system", "权限防护设置对话框已显示");
    }

    //  内部辅助方法
    private static List<String> getScopePermissions(List<String> allPermissions, boolean defaultOnly) {
        if (!defaultOnly) return allPermissions;
        List<String> scope = new ArrayList<String>();
        for (String p : allPermissions) {
            if (java.util.Arrays.asList(HookInit.DETECTION_PERMISSIONS).contains(p)) scope.add(p);
        }
        return scope;
    }

    // 同步状态
    private static String getChangedKeys(Map<String, Boolean> original, Map<String, Boolean> current) {List<String> changed = new ArrayList<String>();
        for (Map.Entry<String, Boolean> entry : current.entrySet()) {
            boolean oldVal = original.getOrDefault(entry.getKey(), false);
            if (oldVal != entry.getValue()) {
                changed.add(entry.getKey() + ":" + (entry.getValue() ? "开" : "关"));
            }
        }
        String result = changed.isEmpty() ? "无变更" : String.join(", ", changed);
        ReaLog.log("misc", "子功能配置变更: " + result);
        return result;
    }

    // 子功能菜单
    public static class MenuItem {
        String title;
        String desc;
        Runnable action;
        public MenuItem(String title, String desc, Runnable action) {
            this.title = title;
            this.desc = desc;
            this.action = action;
        }
    }
}

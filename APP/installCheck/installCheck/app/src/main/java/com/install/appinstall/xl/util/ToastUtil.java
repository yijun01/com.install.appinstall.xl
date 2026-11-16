package com.install.appinstall.xl.util;

import android.app.Activity;
import android.content.Context;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.os.Build;

//自建ToastUtil提示工具

public class ToastUtil {
    private static Toast sToast = null;

    // ========== 增强版 Context 获取 ==========
    private static Context getValidContext(Context context) {
        if (context != null) return context;
        // 1. 从 ReaLog 获取（内部持有 HookInit 引用）
        if (ReaLog.sHookInit != null) {
            Activity act = ReaLog.sHookInit.getCurrentActivity();
            if (act != null && !act.isFinishing() && !act.isDestroyed()) {
                return act;
            }
            Context appCtx = ReaLog.sHookInit.getApplicationContext();
            if (appCtx != null) return appCtx;
        }
        // 2. 最终兜底：从 ActivityThread 获取 Application
        try {
            return (Context) Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication").invoke(null);
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ========== show 方法（普通显示） ==========
    public static void show(final Context context, final String message) {
        show(context, message, false, Gravity.CENTER, 0, More.dpInt(context, 133));
    }

    public static void show(final Context context, final String message, final boolean isHtml) {
        show(context, message, isHtml, Gravity.CENTER, 0, More.dpInt(context, 133));
    }

    public static void show(final Context context, final String message, final int gravity, final int xOffset, final int yOffset) {
        show(context, message, false, gravity, xOffset, yOffset);
    }

    public static void show(final Context context, final String message, final boolean isHtml,
            final int gravity, final int xOffset, final int yOffset) {
        final Context validCtx = getValidContext(context);
        if (validCtx == null) return;

        More.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                Toast t = showInternal(validCtx, message, isHtml, gravity, xOffset, yOffset);
                if (t != null) t.show();
            }
        });
    }

    // ========== showUnique（自动取消上一个，有ctx入口） ==========
    public static void showUnique(final Context context, final String message) {
        showUnique(context, message, false);
    }

    public static void showUnique(final Context context, final String message, final boolean isHtml) {
        final Context validCtx = getValidContext(context);
        if (validCtx == null) return;
        More.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                synchronized (ToastUtil.class) {
                    if (sToast != null) {
                        try {
                            sToast.cancel();
                        } catch (Throwable ignored) {
                        }
                        sToast = null;
                    }
                    Toast t = showInternal(validCtx, message, isHtml, Gravity.CENTER, 0, More.dpInt(validCtx, 133));
                    if (t != null) {
                        t.show();
                        sToast = t;
                    }
                }
            }
        });
    }

    // ========== showUnique（自动取消上一个，无ctx入口） ==========
    public static void showUnique(final String message) {
        showUnique(null, message);
    }

    // ========== 内部构造方法==========
    private static Toast showInternal(Context context, String message, boolean isHtml,
            int gravity, int xOffset, int yOffset) {
        try {
            TextView tv = new TextView(context);
            if (isHtml) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    tv.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    tv.setText(Html.fromHtml(message));
                }
            } else {
                tv.setText(message);
            }
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(More.dpInt(context, 16), More.dpInt(context, 10), More.dpInt(context, 16), More.dpInt(context, 10));
            tv.setTextSize(16);
            tv.setTextColor(0xFFFFFFFF);
            float radius = More.dp(context, 8);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(0xAA181717);
            drawable.setCornerRadius(radius);
            tv.setBackground(drawable);
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            int screenWidth = displayMetrics.widthPixels;
            tv.setMaxWidth((int) (screenWidth * 0.8));
            tv.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
            int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            tv.measure(widthMeasureSpec, heightMeasureSpec);
            int toastWidth = tv.getMeasuredWidth();
            int toastHeight = tv.getMeasuredHeight();
            int screenHeight = displayMetrics.heightPixels;
            int safeXOffset = clampOffset(gravity, xOffset, toastWidth, screenWidth, false);
            int safeYOffset = clampOffset(gravity, yOffset, toastHeight, screenHeight, true);
            Toast toast = new Toast(context);
            toast.setView(tv);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setGravity(gravity, safeXOffset, safeYOffset);
            return toast;
        } catch (Throwable e) {
            return Toast.makeText(context, message, Toast.LENGTH_SHORT);
        }
    }

    // ========== 钳位偏移量 ==========
    private static int clampOffset(int gravity, int offset, int viewSize, int screenSize, boolean isVertical) {
        int mask = isVertical ? Gravity.VERTICAL_GRAVITY_MASK : Gravity.HORIZONTAL_GRAVITY_MASK;
        int gravityComponent = gravity & mask;

        if (isVertical) {
            if (gravityComponent == Gravity.TOP) {
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.BOTTOM) {
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.CENTER_VERTICAL) {
                int maxOffset = (screenSize - viewSize) / 2;
                return Math.max(-maxOffset, Math.min(offset, maxOffset));
            } else {
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            }
        } else {
            if (gravityComponent == Gravity.LEFT) {
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.RIGHT) {
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.CENTER_HORIZONTAL) {
                int maxOffset = (screenSize - viewSize) / 2;
                return Math.max(-maxOffset, Math.min(offset, maxOffset));
            } else {
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            }
        }
    }

}
/*
十六进制 十进制 不透明度
00       0      0%
19       25     10%
4C      76     30%
80      128    50%
99      153    60%
A0      160    63%
AA      170    67%
B3      179    70%
CC     204     80%
E6     230     90%
FF     255     100%
如何自定义透明度
1. 直接使用十六进制：如 0xAA181717（AA 透明度，后六位为颜色值）
*/

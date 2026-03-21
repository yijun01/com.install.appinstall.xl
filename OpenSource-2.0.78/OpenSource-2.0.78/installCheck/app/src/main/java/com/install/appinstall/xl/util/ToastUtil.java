package com.install.appinstall.xl.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

public class ToastUtil {

    /**
     * 显示默认样式的 Toast（位置：居中偏下，偏移 0,400）
     */
    public static void show(final Context context, final String message) {
        show(context, message, false, Gravity.CENTER, 0, 400);
    }

    /**
     * 显示 HTML 样式的 Toast（位置：居中偏下，偏移 0,400）
     */
    public static void show(final Context context, final String message, final boolean isHtml) {
        show(context, message, isHtml, Gravity.CENTER, 0, 400);
    }

    /**
     * 完全自定义重力位置的 Toast（纯文本）
     */
    public static void show(final Context context, final String message, final int gravity, final int xOffset, final int yOffset) {
        show(context, message, false, gravity, xOffset, yOffset);
    }

    /**
     * 完全自定义重力位置的 Toast（支持 HTML）
     */
    public static void show(final Context context, final String message, final boolean isHtml,
                            final int gravity, final int xOffset, final int yOffset) {
        if (context == null) return;

        // 确保在主线程显示（使用匿名内部类替代 lambda）
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        showInternal(context, message, isHtml, gravity, xOffset, yOffset);
                    }
                });
        } else {
            showInternal(context, message, isHtml, gravity, xOffset, yOffset);
        }
    }

    private static void showInternal(Context context, String message, boolean isHtml,
                                     int gravity, int xOffset, int yOffset) {
        try {
            Toast toast = Toast.makeText(context, "", Toast.LENGTH_SHORT);
            TextView tv = new TextView(context);
            if (isHtml) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    tv.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    tv.setText(Html.fromHtml(message));
                }
            } else {
                tv.setText(message);
            }
            // 统一样式：内边距、文字大小、颜色、背景
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(40, 30, 40, 30);
            tv.setTextSize(16);
            tv.setTextColor(0xFF000000);
            tv.setBackgroundColor(0xAAFFFFFF);
            toast.setView(tv);
            toast.setGravity(gravity, xOffset, yOffset);
            toast.show();
        } catch (Throwable e) {
            // 兜底：系统默认 Toast
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }
}

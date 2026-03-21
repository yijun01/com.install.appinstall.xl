package com.install.appinstall.xl.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;

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

        // 确保在主线程显示
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
            // 创建自定义 TextView
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

            // 统一样式
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(40, 30, 40, 30);
            tv.setTextSize(16);
            tv.setTextColor(0xFFFFFFFF);
            float radius = dpToPx(context, 8);
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(0xAA181717);
            drawable.setCornerRadius(radius);
            tv.setBackground(drawable);

            // 获取屏幕宽度并设置最大宽度为屏幕的80%，防止过长文本超出屏幕
            DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
            int screenWidth = displayMetrics.widthPixels;
            tv.setMaxWidth((int)(screenWidth * 0.8));

            // 必须设置 LayoutParams 才能正确测量
            tv.setLayoutParams(new ViewGroup.LayoutParams(
                                   ViewGroup.LayoutParams.WRAP_CONTENT,
                                   ViewGroup.LayoutParams.WRAP_CONTENT));

            // 提前测量 TextView 尺寸
            int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            tv.measure(widthMeasureSpec, heightMeasureSpec);
            int toastWidth = tv.getMeasuredWidth();
            int toastHeight = tv.getMeasuredHeight();

            // 获取屏幕尺寸（重新获取，以防前面修改了）
            int screenHeight = displayMetrics.heightPixels;

            // 钳位偏移量，确保 Toast 完整可见
            int safeXOffset = clampOffset(gravity, xOffset, toastWidth, screenWidth, false);
            int safeYOffset = clampOffset(gravity, yOffset, toastHeight, screenHeight, true);

            // 创建 Toast 并设置
            Toast toast = new Toast(context);
            toast.setView(tv);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setGravity(gravity, safeXOffset, safeYOffset);
            toast.show();

        } catch (Throwable e) {
            // 兜底：系统默认 Toast
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 根据重力方向和视图尺寸，将偏移量钳位到安全范围内
     *
     * @param gravity     重力标志（如 Gravity.CENTER）
     * @param offset      用户传入的偏移量（px）
     * @param viewSize    视图尺寸（宽或高，px）
     * @param screenSize  屏幕尺寸（宽或高，px）
     * @param isVertical  true 表示处理垂直方向，false 表示水平方向
     * @return 钳位后的安全偏移量
     */
    private static int clampOffset(int gravity, int offset, int viewSize, int screenSize, boolean isVertical) {
        int mask = isVertical ? Gravity.VERTICAL_GRAVITY_MASK : Gravity.HORIZONTAL_GRAVITY_MASK;
        int gravityComponent = gravity & mask;

        if (isVertical) {
            if (gravityComponent == Gravity.TOP) {
                // offset 表示视图顶部距离屏幕顶部的距离
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.BOTTOM) {
                // offset 表示视图底部距离屏幕底部的距离
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.CENTER_VERTICAL) {
                // offset 表示相对于垂直中心的偏移，正值向下，负值向上
                int maxOffset = (screenSize - viewSize) / 2;
                return Math.max(-maxOffset, Math.min(offset, maxOffset));
            } else {
                // 未指定垂直重力，默认当作 TOP 处理（系统行为）
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            }
        } else {
            if (gravityComponent == Gravity.LEFT) {
                // offset 表示视图左边距离屏幕左边的距离
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.RIGHT) {
                // offset 表示视图右边距离屏幕右边的距离
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            } else if (gravityComponent == Gravity.CENTER_HORIZONTAL) {
                int maxOffset = (screenSize - viewSize) / 2;
                return Math.max(-maxOffset, Math.min(offset, maxOffset));
            } else {
                // 未指定水平重力，默认当作 LEFT 处理
                return Math.max(0, Math.min(offset, screenSize - viewSize));
            }
        }
    }

    // dp 转 px
    private static float dpToPx(Context context, float dp) {
        return dp * context.getResources().getDisplayMetrics().density;
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
CC     204    80%
E6     230     90%
FF     255     100%
如何自定义透明度
1. 直接使用十六进制：如 0xAA181717（AA 透明度，后六位为颜色值）
*/

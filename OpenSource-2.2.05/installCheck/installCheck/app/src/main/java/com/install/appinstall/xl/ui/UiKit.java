package com.install.appinstall.xl.ui;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * UiKit —— 新 UI 的唯一令牌与基础控件来源。
 */
public final class UiKit {

    private UiKit() {}

    // ============================================================
    //  尺寸令牌（dp）
    // ============================================================
    public static final int PAGE_MARGIN = 12;
    public static final int CARD_GAP = 8;
    public static final int ROW_MIN_H = 56;
    public static final int BTN_H = 44;
    public static final int CARD_R = 16;
    public static final int SHEET_R = 24;
    /** 卡片描边/阴影的历史预留值 */
    public static final int CARD_SHADOW_PAD = 8;
    public static final int ICON = 24;
    public static final int TOPBAR_H = 56;
    public static final int EDGE_SWIPE_W = 24;
    /** dp → px */
    public static int dp(Context c, float v) {
        if (c == null) return (int) v;
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int dpI(Context c, int v) {
        return dp(c, (float) v);
    }

    /** 屏幕宽度（px），用于转场动画与半屏宽度 */
    public static int screenW(Context c) {
        if (c == null) return 1080;
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    /** 屏幕高度（px） */
    public static int screenH(Context c) {
        if (c == null) return 1920;
        return c.getResources().getDisplayMetrics().heightPixels;
    }
    
     // 两击确认的武装窗口值
    public static final long TWO_STEP_ARM_MS = 3000L;
    private static final String PREF = "installcheck_ui";
    private static final String K_STYLE_NEW = "ui_style_new";
    private static final String K_NIGHT = "night_mode";
    private static final String K_THEME = "theme_color";
    /** 夜间模式：跟随系统 */
    public static final int NIGHT_SYSTEM = 0;
    /** 夜间模式：强制日间 */
    public static final int NIGHT_DAY = 1;
    /** 夜间模式：强制夜间 */
    public static final int NIGHT_NIGHT = 2;

    private static SharedPreferences prefs(Context c) {
        Context app = c != null ? c.getApplicationContext() : null;
        return app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** 是否使用新 UI */
    public static boolean useNewUi(Context c) {
        try {
            return prefs(c).getBoolean(K_STYLE_NEW, true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setUseNewUi(Context c, boolean useNew) {
        try {
            prefs(c).edit().putBoolean(K_STYLE_NEW, useNew).apply();
        } catch (Throwable ignored) {
        }
        // 切风格时清掉降级标记
        sFallbackMode = false;
    }
    
    
     //* 「正在以降级方式打开旧对话框」的临时标记。
    private static volatile boolean sFallbackMode;

    public static void setFallbackMode(boolean v) {
        sFallbackMode = v;
    }

    public static boolean isFallbackMode() {
        return sFallbackMode;
    }

    public static int getNightMode(Context c) {
        try {
            return prefs(c).getInt(K_NIGHT, NIGHT_SYSTEM);
        } catch (Throwable t) {
            return NIGHT_SYSTEM;
        }
    }

    public static void setNightMode(Context c, int mode) {
        try {
            prefs(c).edit().putInt(K_NIGHT, mode).apply();
        } catch (Throwable ignored) {
        }
    }

    /** 当前是否夜间：手动模式优先，否则跟随系统 uiMode */
    public static boolean isNight(Context c) {
        int mode = getNightMode(c);
        if (mode == NIGHT_DAY) return false;
        if (mode == NIGHT_NIGHT) return true;
        try {
            int ui = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return ui == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    // ============================================================
    //  颜色令牌
    // ============================================================
    public static int bg(Context c) {
        return isNight(c) ? 0xFF121212 : 0xFFF5F5F7;
    }

    public static int card(Context c) {
        return isNight(c) ? 0xFF1E1E1E : 0xFFFFFFFF;
    }

    public static int textMain(Context c) {
        return isNight(c) ? 0xFFE8E8E8 : 0xFF333333;
    }

    public static int textSub(Context c) {
        return isNight(c) ? 0xFF9E9E9E : 0xFF888888;
    }

    public static int danger(Context c) {
        return isNight(c) ? 0xFFFF7043 : 0xFFFF5722;
    }

    public static int divider(Context c) {
        return isNight(c) ? 0xFF333333 : 0xFFE0E0E0;
    }

    /** 半屏遮罩：把下层的页面压暗，让半屏内容成为视觉焦点。 */
    public static int mask(Context c) {
        return isNight(c) ? 0xB3000000 : 0x80000000;
    }

    /**
     * 是否「视觉上空白」：null / 空串 / 只有不可见字符
     */
    public static boolean isBlank(CharSequence s) {
        if (s == null) return true;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\u00A0' || ch == '\u3000' || ch == '\u200B' || ch == '\uFEFF'
                    || ch == '\u0000') {
                continue;
            }
            if (!Character.isWhitespace(ch)) return false;
        }
        return true;
    }

    /** 空白兜底：空白时返回 def，否则返回清洗过首尾的原文 */
    public static String orDefault(CharSequence s, String def) {
        if (isBlank(s)) return def;
        return s.toString().trim();
    }
    
    
    /** 子功能行底色：日间比卡片略灰、夜间比卡片略亮，用于和分类头/卡片底微区分 */
    public static int rowBg(Context c) {
        return isNight(c) ? 0xFF232326 : 0xFFF7F7F9;
    }

    public static int cardPressed(Context c) {
        return isNight(c) ? 0xFF2A2A2A : 0xFFEFEFF2;
    }

    // ============================================================
    //  主题色 6 套预设
    // ============================================================
    public static final int[] THEME_PRESETS = {
            0xFF2196F3, // 经典蓝
            0xFF6750A4, // MD3 紫
            0xFF00897B, // 青碧
            0xFFF57C00, // 暖橙
            0xFFE91E63, // 玫粉
            0xFF43A047, // 森绿
            0xFF546E7A, // 岩灰
            0xFF5D4037, // 咖褐
            0xFF3949AB, // 靛青
            0xFF00ACC1, // 湖水
            0xFF8E24AA, // 葡萄
            0xFFF4511E, // 朱砂
            0xFFFFB300, // 琥珀
            0xFFC0CA33, // 橄榄
            0xFF00838F, // 深青
            0xFF6D4C41 // 可可
    };

    public static final String[] THEME_NAMES = {
            "经典蓝", "MD3 紫", "青碧", "暖橙", "玫粉", "森绿",
            "岩灰", "咖褐", "靛青", "湖水", "葡萄", "朱砂",
            "琥珀", "橄榄", "深青", "可可"
    };

    public static int getThemeColor(Context c) {
        int def = THEME_PRESETS[0];
        try {
            int v = prefs(c).getInt(K_THEME, def);
            return v == 0 ? def : v;
        } catch (Throwable t) {
            return def;
        }
    }

    public static void setThemeColor(Context c, int color) {
        try {
            prefs(c).edit().putInt(K_THEME, color).apply();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 主题色名称：预设色返回中文名，取色器选的自定义色返回颜色值 #RRGGBB。 
     */
    public static String getThemeName(Context c) {
        int i = indexOfPreset(getThemeColor(c));
        return i >= 0 ? THEME_NAMES[i] : colorCode(getThemeColor(c));
    }

    /** 颜色码：#RRGGBB（大写，忽略透明度） */
    public static String colorCode(int color) {
        return String.format("#%06X", color & 0x00FFFFFF);
    }

    /** 主题色展示文案：预设色「名字 #RRGGBB」，自定义色只有「#RRGGBB」 */
    public static String themeColorLabel(Context c) {
        int cur = getThemeColor(c);
        int i = indexOfPreset(cur);
        return i >= 0 ? THEME_NAMES[i] + " " + colorCode(cur) : colorCode(cur);
    }

    /**
     * 在预设表里找某色。
     */
    private static int indexOfPreset(int color) {
        int n = Math.min(THEME_PRESETS.length, THEME_NAMES.length);
        for (int i = 0; i < n; i++) {
            if (THEME_PRESETS[i] == color) return i;
        }
        return -1;
    }

    /** 强调色：夜间自动提亮，保证深底上的对比度 */
    public static int accent(Context c) {
        int base = getThemeColor(c);
        return isNight(c) ? lighten(base, 0.30f) : base;
    }

    /** 强调色的低透明度底（选中行 / Chip 背景） */
    public static int accentSoft(Context c) {
        return withAlpha(accent(c), 0x1F);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** 与白色混合提亮；f 取 0~1。保留原色的透明度（0x FF 截掉就找不回来了） */
    public static int lighten(int color, float f) {
        if (f <= 0f) return color;
        if (f > 1f) f = 1f;
        int r = (int) (((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * f);
        int g = (int) (((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * f);
        int b = (int) ((color & 0xFF) + (255 - (color & 0xFF)) * f);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // ============================================================
    //  Drawable 工具
    // ============================================================
    public static GradientDrawable roundRect(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** 只圆上两个角（半屏抽屉顶部圆角） */
    public static GradientDrawable roundTop(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        float r = dp(c, radiusDp);
        g.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return g;
    }

    public static GradientDrawable strokeRect(Context c, int fill, int strokeColor, float radiusDp) {
        GradientDrawable g = roundRect(c, fill, radiusDp);
        g.setStroke(Math.max(1, dpI(c, 1)), strokeColor);
        return g;
    }

    /** 按下态背景 */
    public static StateListDrawable pressedBg(Context c, int normal, int pressed, float radiusDp) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, roundRect(c, pressed, radiusDp));
        d.addState(new int[]{}, roundRect(c, normal, radiusDp));
        return d;
    }

    // ============================================================
    //  基础控件
    // ============================================================
    public static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static View dividerLine(Context c) {
        View v = new View(c);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dpI(c, 1)));
        v.setLayoutParams(p);
        v.setBackgroundColor(divider(c));
        return v;
    }

    /** 竖向间距占位 */
    public static View gap(Context c, int dpValue) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, dpI(c, dpValue)));
        return v;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    
    
    /** 页脚状态行 */
    public static String buildLine(Context c) {
        return (isNight(c) ? "夜间" : "日间") + " · " + getThemeName(c)
                + " · " + (useNewUi(c) ? "新版界面" : "旧版界面");
    }

    /** 顶栏底色：页面底色混入主题色 */
    public static int topBarBg(Context c) {
        return blend(bg(c), accent(c), isNight(c) ? 0.22f : 0.16f);
    }

    /** 内容区最大宽度：横屏/平板时列表不拉满整屏 */
    public static int contentMaxW(Context c) {
        if (c == null) return 0;
        int swPx = screenW(c);
        float density = c.getResources().getDisplayMetrics().density;
        int swDp = density > 0f ? (int) (swPx / density) : swPx;
        // 手机竖屏：560dp，实际值由 scroll() 里 min(屏宽, 这里) 取到屏宽
        if (swDp < 600) return dpI(c, 560);
        // 横屏 / 平板：给到屏宽 94%
        return Math.min((int) (swPx * 0.94f), dpI(c, 1100));
    }

    /** 两色按比例混合（自绘开关的轨道过渡） */
    public static int blend(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * 震动反馈 view。受震动控制总开关统一拦截（不区分宿主与模块）。
     */
    public static void haptic(View v) {
        if (v == null) return;
        try {
            android.os.Vibrator vb = (android.os.Vibrator) v.getContext().getSystemService(android.content.Context.VIBRATOR_SERVICE);
            if (vb != null && vb.hasVibrator()) {
                vb.vibrate(50);
                return;
            }
        } catch (Throwable ignored) {
        }
        // 拿不到震动器才回退 HapticFeedback（部分机型该 API 为双脉冲）
        try {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        } catch (Throwable ignored) {
        }
    }

    /** 震动反馈的 Activity 重载 */
    public static void haptic(Activity a) {
        if (a == null) return;
        try {
            View d = a.getWindow().getDecorView();
            if (d != null) haptic(d);
        } catch (Throwable ignored) {
        }
    }
}

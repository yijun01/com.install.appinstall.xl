package com.install.appinstall.xl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.DisplayMetrics;
import android.view.ViewTreeObserver;

import com.install.appinstall.xl.util.Update;   // 导入更新工具类

import java.util.ArrayList;
import java.util.List;
import android.content.pm.PackageInfo;
import com.install.appinstall.xl.util.ToastUtil;

public class MainActivity extends Activity {
    // 模块核心功能描述 - 分板块
    private static final String[][] MODULE_SECTIONS = {
        // 核心拦截
        {
            "🔒 核心拦截功能",
            "✅ 基础拦截 - 挡住约92%应用查询安装状态",
            "✅ 文件保护 - 不让应用读取系统安装列表",
            "✅ 反射防御 - 监控隐藏的应用检测方法",
            "✅ 网络伪装 - 拦截在线应用检测请求",
            "✅ 数据伪造 - 生成虚假的应用信息"
        },
        // 包管理
        {
            "📦 应用包管理",
            "📦 安装查询 - 假装应用已安装/未安装",
            "📦 应用列表 - 伪造已安装应用列表",
            "📦 系统包过滤 - 不干扰系统应用\n避免系统出错请勿作用系统应用"
        },
        // 退出控制
        {
            "🛑 退出拦截功能",
            "🛑 退出拦截 - 阻止应用直接退出常用方式",
            "🛑 间接拦截 - 监控各种主流页面退出方式",
            "🛑 按钮拦截 - 挡住少部分点击退出结束按钮",
            "🛑 弹窗拦截 - 标准弹窗可以移除不可取消状态"
        },
        // 文件系统
        {
            "📁 数据伪造功能",
            "📁 目录伪装 - 假装存在应用目录",
            "📁 文件检测 - 伪造应用文件存在",
            "📁 命令行拦截 - 拦截伪造检测命令",
            "📁 明细伪装 - 虚假生成安装时间/来源"
        },
        // 启动相关
        {
            "🚀 启动相关功能",
            "🚀 状态伪装 - 假装应用已启用(伪造包)",
            "🚀 启动拦截 - 控制应用间跳转(伪造包)",
            "🚀 意图伪装 - 伪造应用启动能力(伪造包)",
            "🚀 组件伪造 - 假装应用可被调用(伪造包)",
            "🚀 插件拦截 - 挡住安装检测插件(伪造包)"
        },
        // 悬浮窗
        {
            "🪟 悬浮窗功能",
            "🪟 状态显示 - 悬浮窗显示当前模式",
            "🪟 实时切换 - 点击切换安装状态",
            "🪟 拦截开关 - 控制退出拦截功能",
            "🪟 位置记忆 - 记住悬浮窗拖动位置",
            "🪟 悬浮窗控制 - 长按隐藏本次显示",
            "🪟 自定义包名 - 双击悬浮窗可配置",
        },
        // 配置管理
        {
            "⚙️ 配置持久化",
            "⚙️ 智能学习 - 记住用户的选择",
            "⚙️ 独立配置 - 每个应用单独设置",
            "⚙️ 自动保存 - 配置自动存储持久化",
            "⚙️ 包名捕获 - 独立自动记忆应用包名"
        },
        // 关于模块
        {
            "💬 关于模块",
            "💡 开发思路 - 永恒之蓝(小淋)",
            "📱 使用方法 - 支持LSPosed、LSPatch",
            "⚠️ 拦截效果 - 能挡住大部分安装检测(约92%)",
            "🚫 开发声明 - 仅限学习测试，禁止商用及付费！"
        }
    };

    private LinearLayout rootLayout;
    private ScrollView scrollView;
    private TextView statusView;          // 左侧状态卡片的文本
    private Handler handler = new Handler();
    private List<View> sectionViews = new ArrayList<View>();
    private boolean isScrolling = false;
    private int lastScrollY = 0;
    private int horizontalMargin; // 水平边距

    // 状态颜色
    private static final int ACTIVATED_COLOR = 0xFF4CAF50;   // 绿色
    private static final int DEACTIVATED_COLOR = 0xFFF44336; // 红色
    private static final int ACTIVATED_BG_COLOR = 0xFFE8F5E8; // 浅绿色背景
    private static final int DEACTIVATED_BG_COLOR = 0xFFFFEBEE; // 浅红色背景

    // 页面背景色
    private static final int PAGE_BACKGROUND = 0xFFFAFAFA;
    private static final int CARD_BACKGROUND = 0xFFFFFFFF;
    private static final int CARD_BORDER = 0xFFEEEEEE;

    // 对话框颜色
    private static final int DIALOG_BACKGROUND = 0xFFFFFFFF;
    private static final int DIALOG_TITLE_COLOR = 0xFF333333;
    private static final int DIALOG_TEXT_COLOR = 0xFF666666;

    // 超链接颜色
    private static final int LINK_COLOR = 0xFF2196F3;

    // 边距比例
    private static final float CARD_MARGIN_RATIO = 0.05f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        horizontalMargin = (int) (metrics.widthPixels * CARD_MARGIN_RATIO);
        final boolean isActivated = isModuleActivated();
        final int topPaddingColor = isActivated ? ACTIVATED_BG_COLOR : DEACTIVATED_BG_COLOR;

        createMainLayout(metrics);
        setTopPaddingColor(topPaddingColor);
        addScrollListener();
        handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    animateInitialSections();
                }
            }, 300);

        // 自动检查 GitHub 新版本（可选，若不需要可删除此行）
        Update.checkForUpdate(this);
    }

    /** 创建主布局 */
    private void createMainLayout(DisplayMetrics metrics) {
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(PAGE_BACKGROUND);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(PAGE_BACKGROUND);
        int verticalPadding = dp2px(12);
        rootLayout.setPadding(horizontalMargin, verticalPadding, horizontalMargin, verticalPadding);
        scrollView.addView(rootLayout);
        setContentView(scrollView);

        addTitleSection();
        addStatusSection();          // 包含左右两个卡片
        for (int i = 0; i < MODULE_SECTIONS.length; i++) {
            addModuleSection(i);
        }
        addBottomSection();
    }

    /** 设置顶部边距区域的颜色 */
    private void setTopPaddingColor(int color) {
        scrollView.setBackgroundColor(color);
        View topStatusView = new View(this);
        topStatusView.setBackgroundColor(color);
        topStatusView.setLayoutParams(new LinearLayout.LayoutParams(
                                          LinearLayout.LayoutParams.MATCH_PARENT,
                                          dp2px(12)
                                      ));
        rootLayout.addView(topStatusView, 0);
    }

    /** 添加标题部分 */
    private void addTitleSection() {
        LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setGravity(Gravity.CENTER);
        GradientDrawable titleBg = new GradientDrawable();
        titleBg.setColor(CARD_BACKGROUND);
        titleBg.setCornerRadius(dp2px(16));
        titleBg.setStroke(dp2px(1), CARD_BORDER);
        titleContainer.setBackground(titleBg);
        titleContainer.setPadding(dp2px(20), dp2px(24), dp2px(20), dp2px(24));

        TextView titleView = new TextView(this);
        titleView.setText("应用安装防护模块 2.0");
        titleView.setTextSize(24);
        titleView.setTextColor(DIALOG_TITLE_COLOR);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView subtitleView = new TextView(this);
        subtitleView.setText("(永恒之蓝 / 小淋)");
        subtitleView.setTextSize(16);
        subtitleView.setTextColor(DIALOG_TEXT_COLOR);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp2px(8), 0, 0);

        titleContainer.addView(titleView);
        titleContainer.addView(subtitleView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp2px(20);
        rootLayout.addView(titleContainer, params);

        sectionViews.add(titleContainer);
        titleContainer.setAlpha(0f);
        titleContainer.setTranslationY(dp2px(20));
    }

    /** 添加状态卡片和图标控制卡片（水平排列） */
    private void addStatusSection() {
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusRow.setPadding(0, 0, 0, dp2px(24));

        // 左侧状态卡片
        LinearLayout statusCard = createStatusCard();
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusParams.rightMargin = dp2px(8);
        statusRow.addView(statusCard, statusParams);

        // 右侧图标控制卡片
        LinearLayout iconControlCard = createIconControlCard();
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        iconParams.leftMargin = dp2px(8);
        statusRow.addView(iconControlCard, iconParams);

        rootLayout.addView(statusRow);

        sectionViews.add(statusRow);
        statusRow.setAlpha(0f);
        statusRow.setTranslationY(dp2px(20));
    }

    /** 创建左侧状态卡片 */
    private LinearLayout createStatusCard() {
        final LinearLayout statusContainer = new LinearLayout(this);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        statusContainer.setGravity(Gravity.CENTER);

        final boolean isActivated = isModuleActivated();
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp2px(25));
        if (isActivated) {
            statusBg.setColor(ACTIVATED_BG_COLOR);
            statusBg.setStroke(dp2px(2), ACTIVATED_COLOR);
        } else {
            statusBg.setColor(DEACTIVATED_BG_COLOR);
            statusBg.setStroke(dp2px(2), DEACTIVATED_COLOR);
        }
        statusContainer.setBackground(statusBg);
        statusContainer.setPadding(dp2px(20), dp2px(20), dp2px(20), dp2px(20));

        TextView statusIcon = new TextView(this);
        statusIcon.setText(isActivated ? "✅" : "❌");
        statusIcon.setTextSize(32);
        statusIcon.setGravity(Gravity.CENTER);
        statusContainer.addView(statusIcon);

        //配置激活文本内容
        statusView = new TextView(this);
        statusView.setText(isActivated ? "模块已激活" : "模块未激活");
        statusView.setTextSize(18);
        statusView.setTextColor(isActivated ? ACTIVATED_COLOR : DEACTIVATED_COLOR);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTypeface(null, android.graphics.Typeface.BOLD);
        statusContainer.addView(statusView);



        // 点击事件
        statusContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    refreshActivationStatus();
                    v.animate().scaleX(0.75f).scaleY(0.75f).setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                            }
                        });
                    showStatusDetailDialog();
                }
            });

        // 激活卡片触摸反馈
        statusContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.setAlpha(0.7f);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1f);
                            break;
                    }
                    return false;
                }
            });

        return statusContainer;
    }

    /** 创建右侧图标控制卡片 */
    private LinearLayout createIconControlCard() {
        final LinearLayout controlContainer = new LinearLayout(this);
        controlContainer.setOrientation(LinearLayout.VERTICAL);
        controlContainer.setGravity(Gravity.CENTER);

        final boolean iconVisible = isIconVisible();

        GradientDrawable controlBg = new GradientDrawable();
        controlBg.setCornerRadius(dp2px(25));
        if (iconVisible) {
            controlBg.setColor(ACTIVATED_BG_COLOR);
            controlBg.setStroke(dp2px(2), ACTIVATED_COLOR);
        } else {
            controlBg.setColor(DEACTIVATED_BG_COLOR);
            controlBg.setStroke(dp2px(2), DEACTIVATED_COLOR);
        }
        controlContainer.setBackground(controlBg);
        controlContainer.setPadding(dp2px(20), dp2px(20), dp2px(20), dp2px(20));

        final TextView iconView = new TextView(this);
        iconView.setText(iconVisible ? "👁️" : "👁️‍🗨️");
        iconView.setTextSize(32);
        iconView.setGravity(Gravity.CENTER);
        controlContainer.addView(iconView);

        final TextView textView = new TextView(this);
        textView.setText(iconVisible ? "桌面图标:显示中" : "桌面图标:隐藏中");
        textView.setTextSize(18);
        textView.setTextColor(iconVisible ? ACTIVATED_COLOR : DEACTIVATED_COLOR);
        textView.setGravity(Gravity.CENTER);
        textView.setTypeface(null, android.graphics.Typeface.BOLD);
        controlContainer.addView(textView);

        // 图标设置：弹出图标控制对话框
        controlContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    v.animate().scaleX(0.75f).scaleY(0.75f).setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                            }
                        });
                    showIconControlDialog(iconView, textView);
                }
            });

        // 图标卡片触摸反馈
        controlContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.setAlpha(0.7f);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1f);
                            break;
                    }
                    return false;
                }
            });

        return controlContainer;
    }

    // ==================== 图标控制核心逻辑 ====================

    /** 判断桌面图标是否显示 */
    private boolean isIconVisible() {
        PackageManager pm = getPackageManager();
        ComponentName aliasName = new ComponentName(this, "com.install.appinstall.xl.MainHome");
        int state = pm.getComponentEnabledSetting(aliasName);
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            || state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
    }

    /** 控制桌面图标显示/隐藏（使用 DONT_KILL_APP 防止应用退出） */
    private void setLauncherIconVisibility(boolean show) {
        PackageManager pm = getPackageManager();
        ComponentName aliasName = new ComponentName(this, "com.install.appinstall.xl.MainHome");
        int newState = show ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        pm.setComponentEnabledSetting(aliasName, newState, PackageManager.DONT_KILL_APP);
    }

    /** 显示图标控制对话框 */
    private void showIconControlDialog(final TextView iconView, final TextView textView) {
        final String[] options = {"显示桌面图标", "隐藏桌面图标-需重启设备/刷新桌面\n隐藏后使用LSPosed可进入本主页"};
        final int checkedItem = isIconVisible() ? 0 : 1;

        AlertDialog.Builder builder = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
        builder.setTitle("桌面图标显示控制")
            .setSingleChoiceItems(options, checkedItem, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    boolean show = (which == 0);

                    // 1. 设置图标状态（应用不会退出）
                    setLauncherIconVisibility(show);

                    // 2. 更新UI
                    iconView.setText(show ? "👁️" : "👁️‍🗨️");
                    textView.setText(show ? "桌面图标:显示中" : "桌面图标:隐藏中");
                    textView.setTextColor(show ? ACTIVATED_COLOR : DEACTIVATED_COLOR);

                    // 3. 尝试刷新桌面图标（广播方式）
                    tryRefreshLauncher();

                    // 4. 提示用户操作已执行
                    String msg = show ? "✅ 图标已显示" : "✅ 图标已隐藏\n需重启设备/桌面";
                    ToastUtil.show(MainActivity.this, msg);

                    // 5. 如果是隐藏，延迟检查并引导用户
                    if (!show) {
                        new Handler().postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    checkIconHiddenAndGuide();
                                }
                            }, 10);
                    }

                    dialog.dismiss();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /** 尝试刷新桌面图标（兼容所有 Android 版本，无复杂 Extra） */
    private void tryRefreshLauncher() {
        try {
            // 方法1：发送包变化广播
            Intent intent = new Intent(Intent.ACTION_PACKAGE_CHANGED);
            intent.setData(Uri.parse("package:" + getPackageName()));
            sendBroadcast(intent);

            // 方法2：发送系统UI刷新广播
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

            // 方法3：发送通用广播（某些启动器监听）
            sendBroadcast(new Intent("android.intent.action.ACTION_PACKAGE_CHANGED"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 检查图标隐藏状态并引导用户 */
    private void checkIconHiddenAndGuide() {
        boolean isHidden = !isIconVisible(); // 组件已禁用
        if (isHidden) {
            // 组件已禁用，但桌面图标可能未消失
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                .setTitle("提示")
                .setMessage("图标已隐藏，但桌面可能未及时刷新，您可以：\n1• 返回桌面等待几秒\n2• 重启桌面\n3• 重启手机\n\n使用LSPosed重新进入主页")
                .setPositiveButton("知道了", null)
                .show();
            // ToastUtil.show(this, "若未生效请重启设备");
        } else {
            // 极少发生，提示重试
            ToastUtil.show(this, "图标状态设置失败，请重试");

        }
    }

    // ==================== 原有功能模块方法 ====================

    /** 添加功能模块板块 */
    private void addModuleSection(final int sectionIndex) {
        if (sectionIndex >= MODULE_SECTIONS.length) return;
        final String[] section = MODULE_SECTIONS[sectionIndex];
        if (section.length == 0) return;

        final LinearLayout sectionContainer = new LinearLayout(this);
        sectionContainer.setOrientation(LinearLayout.VERTICAL);
        sectionContainer.setTag(sectionIndex);

        GradientDrawable sectionBg = new GradientDrawable();
        sectionBg.setColor(DIALOG_BACKGROUND);
        sectionBg.setCornerRadius(dp2px(8));
        sectionBg.setStroke(dp2px(1), CARD_BORDER);
        sectionContainer.setBackground(sectionBg);
        int containerPadding = dp2px(16);
        sectionContainer.setPadding(containerPadding, containerPadding, containerPadding, containerPadding);

        TextView sectionTitle = new TextView(this);
        sectionTitle.setText(section[0]);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(getSectionColor(section[0]));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(0, 0, 0, dp2px(12));
        sectionContainer.addView(sectionTitle);

        for (int i = 1; i < section.length; i++) {
            TextView itemView = createFeatureItem(section[i]);
            sectionContainer.addView(itemView);
            if (i < section.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0xFFF5F5F5);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp2px(1)
                );
                dividerParams.topMargin = dp2px(8);
                dividerParams.bottomMargin = dp2px(8);
                sectionContainer.addView(divider, dividerParams);
            }
        }

        sectionContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    v.animate().scaleX(0.78f).scaleY(0.78f).setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                            }
                        });
                    showSectionDetailDialog(section);
                    final GradientDrawable bg = (GradientDrawable) sectionContainer.getBackground();
                    final int originalColor = DIALOG_BACKGROUND;
                    bg.setColor(0xFFF8F8F8);
                    sectionContainer.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                bg.setColor(originalColor);
                            }
                        }, 200);
                }
            });

        sectionContainer.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            v.setAlpha(0.7f);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            v.setAlpha(1f);
                            break;
                    }
                    return false;
                }
            });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp2px(16);
        rootLayout.addView(sectionContainer, params);

        sectionViews.add(sectionContainer);
        sectionContainer.setAlpha(0f);
        sectionContainer.setTranslationY(dp2px(30));
    }

    /** 创建功能项 */
    private TextView createFeatureItem(String text) {
        TextView itemView = new TextView(this);
        itemView.setText(text);
        itemView.setTextSize(14);
        itemView.setTextColor(DIALOG_TEXT_COLOR);
        itemView.setPadding(dp2px(8), dp2px(6), dp2px(8), dp2px(6));
        itemView.setLineSpacing(dp2px(2), 1.1f);

        if (text.startsWith("✅")) {
            itemView.setTextColor(ACTIVATED_COLOR);
        } else if (text.startsWith("📦") || text.startsWith("📁")) {
            itemView.setTextColor(0xFF2196F3);
        } else if (text.startsWith("🛑")) {
            itemView.setTextColor(DEACTIVATED_COLOR);
        } else if (text.startsWith("🚀")) {
            itemView.setTextColor(0xFF9C27B0);
        } else if (text.startsWith("🪟")) {
            itemView.setTextColor(0xFFFF9800);
        } else if (text.startsWith("⚙️")) {
            itemView.setTextColor(0xFF009688);
        } else if (text.startsWith("💬")) {
            itemView.setTextColor(0xFF795548);
        } else if (text.startsWith("⚠️") || text.startsWith("🚫")) {
            itemView.setTextColor(0xFFD32F2F);
        } else if (text.startsWith("📱") || text.startsWith("💡")) {
            itemView.setTextColor(0xFF607D8B);
        }
        return itemView;
    }

    /** 显示状态详情对话框 */
    private void showStatusDetailDialog() {
        boolean isActivated = isModuleActivated();
        String message = isActivated ?
            "模块当前运行状态：已激活\n" +
            "✅ 所有Hook功能已生效\n" +
            "✅ 可以在Xposed框架中查看\n" +
            "✅ 应用启动时自动加载\n\n" +
            "如果状态显示异常，请：\n" +
            "1. 重启手机\n" +
            "2. 检查Xposed/LSPosed激活状态\n" +
            "(若使用LSPatch内嵌/本地模式无需激活)\n" +
            "3. 重新启用模块" :
            "模块当前运行状态：未激活\n" +
            "❌ Hook功能无法生效\n" +
            "❌ 需要激活Xposed模块\n\n" +
            "请按以下步骤操作：\n" +
            "1. 打开Xposed/LSPosed应用\n" +
            "2. 找到并启用本模块\n" +
            "3. 重启目标应用或手机\n" +
            "(若使用LSPatch内嵌/本地模式无需激活)\n" +
            "4. 返回此处检查状态";

        final AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle("模块状态详情")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    try {
                        Window window = dialog.getWindow();
                        if (window != null) {
                            WindowManager.LayoutParams params = window.getAttributes();
                            params.width = WindowManager.LayoutParams.MATCH_PARENT;
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                            params.gravity = Gravity.CENTER;
                            params.horizontalMargin = 0.05f;
                            window.setAttributes(params);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
        dialog.show();
    }

    /** 显示板块详情对话框 */
    private void showSectionDetailDialog(final String[] section) {
        if (section.length < 2) return;
        String title = section[0];
        StringBuilder message = new StringBuilder();
        for (int i = 1; i < section.length; i++) {
            message.append("• ").append(section[i]).append("\n");
        }
        message.append("\n─────────────────\n");
        message.append("📌 功能说明：\n");

        if (title.contains("核心拦截")) {
            message.append("这是模块的核心功能，负责拦截各种安装检测手段，确保应用无法发现真实安装状态。");
        } else if (title.contains("包管理")) {
            message.append("管理应用包信息，伪造安装状态和包列表，让检测工具看到你希望它们看到的内容。");
        } else if (title.contains("退出拦截")) {
            message.append("防止应用检测到伪造信息后直接退出，保持应用正常运行的同时提供用户选择。");
        } else if (title.contains("数据伪造")) {
            message.append("伪造文件系统、命令行输出等底层信息，应对更深入的检测手段。");
        } else if (title.contains("启动相关")) {
            message.append("控制应用间的启动和跳转，伪造应用的启动能力和组件状态。");
        } else if (title.contains("悬浮窗")) {
            message.append("应用宿主内可视化控制和状态显示，方便用户实时查看和切换模块状态。");
        } else if (title.contains("配置持久化")) {
            message.append("自动保存用户设置，智能学习用户选择，提供个性化的拦截体验。");
        } else if (title.contains("关于模块")) {
            message.append("模块基本信息和使用说明，请严格遵守使用规范。\n严禁引流盈利以及所有商业等行为。");
        }

        final AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle(title)
            .setMessage(message.toString())
            .setPositiveButton("确定", null)
            .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    try {
                        Window window = dialog.getWindow();
                        if (window != null) {
                            WindowManager.LayoutParams params = window.getAttributes();
                            params.width = WindowManager.LayoutParams.MATCH_PARENT;
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                            params.gravity = Gravity.CENTER;
                            params.horizontalMargin = 0.05f;
                            window.setAttributes(params);
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            });
        dialog.show();
    }

    /** 添加滚动监听 */
    private void addScrollListener() {
        scrollView.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                @Override
                public void onScrollChanged() {
                    if (isScrolling) return;
                    isScrolling = true;
                    final int currentScrollY = scrollView.getScrollY();
                    final boolean scrollingDown = currentScrollY > lastScrollY;
                    updateSectionAnimations(currentScrollY, scrollingDown);
                    lastScrollY = currentScrollY;
                    scrollView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isScrolling = false;
                            }
                        }, 16);
                }
            });
    }

    /** 更新板块动画状态 */
    private void updateSectionAnimations(int scrollY, boolean scrollingDown) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < sectionViews.size(); i++) {
            View section = sectionViews.get(i);
            if (section.getVisibility() != View.VISIBLE) continue;
            int[] location = new int[2];
            section.getLocationOnScreen(location);
            int sectionTop = location[1];
            int sectionBottom = sectionTop + section.getHeight();
            int screenCenter = scrollY + screenHeight / 2;
            int sectionCenter = (sectionTop + sectionBottom) / 2;
            int distanceFromCenter = Math.abs(sectionCenter - screenCenter);
            float maxFloatDistance = dp2px(15);
            float floatDistance = maxFloatDistance * (1.0f - Math.min(distanceFromCenter / (float) screenHeight, 1.0f));
            float targetTranslationY;

            if (scrollingDown) {
                targetTranslationY = sectionCenter < screenCenter ? -floatDistance : floatDistance;
            } else {
                targetTranslationY = sectionCenter < screenCenter ? floatDistance : -floatDistance;
            }

            final float currentTranslationY = section.getTranslationY();
            final float newTranslationY = currentTranslationY * 0.7f + targetTranslationY * 0.3f;
            section.animate()
                .translationY(newTranslationY)
                .setDuration(1000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
        }
    }

    /** 初始动画 */
    private void animateInitialSections() {
        for (int i = 0; i < sectionViews.size(); i++) {
            final View section = sectionViews.get(i);
            final int delay = i * 80;
            handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        animateSectionIn(section, delay);
                    }
                }, delay);
        }
    }

    /** 单个板块进入动画 */
    private void animateSectionIn(final View view, int delay) {
        AnimationSet animationSet = new AnimationSet(true);
        animationSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animationSet.setStartOffset(delay);

        AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(1000);
        animationSet.addAnimation(fadeIn);

        TranslateAnimation slideUp = new TranslateAnimation(0, 0, dp2px(30), 0);
        slideUp.setDuration(1500);
        animationSet.addAnimation(slideUp);

        animationSet.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}

                @Override
                public void onAnimationEnd(Animation animation) {
                    view.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                view.animate()
                                    .translationY(dp2px(-15))
                                    .setDuration(250)
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.animate().translationY(0).setDuration(600).start();
                                        }
                                    })
                                    .start();
                            }
                        }, 100);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {}
            });

        view.startAnimation(animationSet);
        view.setAlpha(1f);
        view.setTranslationY(0);
    }

    /** 刷新激活状态 */
    private void refreshActivationStatus() {
        boolean isActivated = isModuleActivated();
        statusView.setText(isActivated ? "模块已激活" : "模块未激活");
        statusView.setTextColor(isActivated ? ACTIVATED_COLOR : DEACTIVATED_COLOR);

        View statusContainer = (View) statusView.getParent();
        GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp2px(25));
        if (isActivated) {
            statusBg.setColor(ACTIVATED_BG_COLOR);
            statusBg.setStroke(dp2px(2), ACTIVATED_COLOR);
        } else {
            statusBg.setColor(DEACTIVATED_BG_COLOR);
            statusBg.setStroke(dp2px(2), DEACTIVATED_COLOR);
        }
        statusContainer.setBackground(statusBg);
    }

    /** 获取板块颜色 */
    private int getSectionColor(String title) {
        if (title.contains("核心拦截")) return ACTIVATED_COLOR;
        if (title.contains("包管理")) return 0xFF2196F3;
        if (title.contains("退出拦截")) return DEACTIVATED_COLOR;
        if (title.contains("数据伪造")) return 0xFF2196F3;
        if (title.contains("启动相关")) return 0xFF9C27B0;
        if (title.contains("悬浮窗")) return 0xFFFF9800;
        if (title.contains("配置持久化")) return 0xFF009688;
        if (title.contains("关于模块")) return 0xFF795548;
        return DIALOG_TITLE_COLOR;
    }

    /** 添加底部信息 */
    private void addBottomSection() {
        LinearLayout bottomContainer = new LinearLayout(this);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setGravity(Gravity.CENTER);
        GradientDrawable bottomBg = new GradientDrawable();
        bottomBg.setColor(0xFFF5F5F5);
        bottomBg.setCornerRadius(dp2px(8));
        bottomContainer.setBackground(bottomBg);
        bottomContainer.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

        // 版本信息（从 PackageManager 动态获取）
        String versionText = "伪造安装模块版本: 获取失败";
        try {
            PackageInfo pkgInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionText = "伪造安装模块版本: " + pkgInfo.versionName + " (" + pkgInfo.versionCode + ")";
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // 版权信息
        String copyrightText = "© 2026 永恒之蓝(小淋)";

        // 将版本和版权放在一行
        LinearLayout versionCopyrightRow = new LinearLayout(this);
        versionCopyrightRow.setOrientation(LinearLayout.HORIZONTAL);
        versionCopyrightRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView versionView = new TextView(this);
        versionView.setText(versionText);
        versionView.setTextSize(12);
        versionView.setTextColor(DIALOG_TEXT_COLOR);
        versionView.setGravity(Gravity.START);
        versionView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView copyrightView = new TextView(this);
        copyrightView.setText(copyrightText);
        copyrightView.setTextSize(12);
        copyrightView.setTextColor(0xFF888888);
        copyrightView.setGravity(Gravity.END);
        copyrightView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        versionCopyrightRow.addView(versionView);
        versionCopyrightRow.addView(copyrightView);
        bottomContainer.addView(versionCopyrightRow);

        // 链接行（三个链接水平排列，间距缩小）
        LinearLayout linkLayout = new LinearLayout(this);
        linkLayout.setOrientation(LinearLayout.HORIZONTAL);
        linkLayout.setGravity(Gravity.CENTER);
        int linkMargin = dp2px(10); // 缩小间距

        // 作者主页(GitHub)
        TextView homeView = new TextView(this);
        homeView.setText("🔗 作者主页(GitHub)");
        homeView.setTextSize(12);
        homeView.setTextColor(LINK_COLOR);
        homeView.setGravity(Gravity.CENTER);
        homeView.setPadding(0, 0, linkMargin, 0);
        homeView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yijun01/com.install.appinstall.xl"));
                    startActivity(intent);
                    ToastUtil.show(MainActivity.this, "前往作者主页");
                }
            });

        // 作者主页(MT论坛)
        TextView mtView = new TextView(this);
        mtView.setText("🔗 作者主页(MT论坛)");
        mtView.setTextSize(12);
        mtView.setTextColor(LINK_COLOR);
        mtView.setGravity(Gravity.CENTER);
        mtView.setPadding(linkMargin, 0, 0, 0);
        mtView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://bbs.binmt.cc/home.php?mod=space&uid=30974&do=profile"));
                    startActivity(intent);
                    ToastUtil.show(MainActivity.this, "前往MT论坛");
                }
            });

        // 官方仓库(GitHub)
        TextView repoView = new TextView(this);
        repoView.setText("📦 官方仓库(GitHub)");
        repoView.setTextSize(12);
        repoView.setTextColor(LINK_COLOR);
        repoView.setGravity(Gravity.CENTER);
        repoView.setPadding(linkMargin, 0, linkMargin, 0);
        repoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xposed-Modules-Repo/com.install.appinstall.xl"));
                    startActivity(intent);
                    ToastUtil.show(MainActivity.this, "前往模块仓库");
                }
            });


        linkLayout.addView(homeView);
        linkLayout.addView(mtView);
        linkLayout.addView(repoView);
        bottomContainer.addView(linkLayout);

        // 检查更新按钮
        TextView checkUpdateBtn = new TextView(this);
        checkUpdateBtn.setText("🔍 检查更新");
        checkUpdateBtn.setTextSize(14);
        checkUpdateBtn.setTextColor(LINK_COLOR);
        checkUpdateBtn.setGravity(Gravity.CENTER);
        checkUpdateBtn.setPadding(0, dp2px(12), 0, dp2px(8));
        checkUpdateBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Update.checkForUpdate(MainActivity.this);
                }
            });
        bottomContainer.addView(checkUpdateBtn);

        // 免责声明
        TextView disclaimerView = new TextView(this);
        disclaimerView.setText("仅供个人学习测试，禁商用禁引流以及禁付费盈利");
        disclaimerView.setTextSize(11);
        disclaimerView.setTextColor(0xFFAAAAAA);
        disclaimerView.setGravity(Gravity.CENTER);
        disclaimerView.setPadding(0, dp2px(8), 0, 0);
        bottomContainer.addView(disclaimerView);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dp2px(8);
        rootLayout.addView(bottomContainer, params);

        sectionViews.add(bottomContainer);
        bottomContainer.setAlpha(0f);
        bottomContainer.setTranslationY(dp2px(30));
    }

    /** dp转px */
    private int dp2px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /** 模块激活状态检测*/
    public static boolean isModuleActivated() {
        return false; // 成功激活自动返回true
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}

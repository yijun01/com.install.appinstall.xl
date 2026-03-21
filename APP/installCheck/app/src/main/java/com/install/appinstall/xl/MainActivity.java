package com.install.appinstall.xl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.util.DisplayMetrics;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.List;

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
    private TextView statusView;
    private Handler handler = new Handler();
    private List<View> sectionViews = new ArrayList<View>();
    private boolean isScrolling = false;
    private int lastScrollY = 0;
    private int horizontalMargin; // 水平边距

    // 状态颜色 - 使用final变量
    private static final int ACTIVATED_COLOR = 0xFF4CAF50; // 绿色
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

    // 超链接颜色（GitHub官方蓝）
    private static final int LINK_COLOR = 0xFF2196F3;

    // 边距比例
    private static final float CARD_MARGIN_RATIO = 0.05f;
    private static final float DIALOG_MARGIN_RATIO = 0.06f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 获取屏幕尺寸
        final DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        // 计算边距
        horizontalMargin = (int) (metrics.widthPixels * CARD_MARGIN_RATIO);
        // 根据激活状态设置颜色
        final boolean isActivated = isModuleActivated();
        final int statusBarColor = isActivated ? ACTIVATED_COLOR : DEACTIVATED_COLOR;
        final int topPaddingColor = isActivated ? ACTIVATED_BG_COLOR : DEACTIVATED_BG_COLOR;

        // 创建主布局
        createMainLayout(metrics);
        // 设置顶部边距区域的颜色
        setTopPaddingColor(topPaddingColor);
        // 添加滚动监听
        addScrollListener();
        // 延迟执行初始动画
        handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					animateInitialSections();
				}
			}, 300);
    }

    /** 创建主布局 */
    private void createMainLayout(final DisplayMetrics metrics) {
        // 创建滚动视图
        scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(PAGE_BACKGROUND);
        // 创建根布局
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(PAGE_BACKGROUND);
        final int verticalPadding = dp2px(12);
        // 设置边距
        rootLayout.setPadding(horizontalMargin, verticalPadding, horizontalMargin, verticalPadding);
        scrollView.addView(rootLayout);
        setContentView(scrollView);

        // 添加标题
        addTitleSection();
        // 添加状态卡片
        addStatusSection();
        // 添加所有功能板块
        for (int i = 0; i < MODULE_SECTIONS.length; i++) {
            addModuleSection(i);
        }
        // 添加底部信息
        addBottomSection();
    }

    /** 设置顶部边距区域的颜色 */
    private void setTopPaddingColor(final int color) {
        scrollView.setBackgroundColor(color);
        // 添加专门的顶部状态色View
        final View topStatusView = new View(this);
        topStatusView.setBackgroundColor(color);
        topStatusView.setLayoutParams(new LinearLayout.LayoutParams(
										  LinearLayout.LayoutParams.MATCH_PARENT,
										  dp2px(12)
									  ));
        // 插入到最顶部
        rootLayout.addView(topStatusView, 0);
    }

    /** 添加标题部分 */
    private void addTitleSection() {
        final LinearLayout titleContainer = new LinearLayout(this);
        titleContainer.setOrientation(LinearLayout.VERTICAL);
        titleContainer.setGravity(Gravity.CENTER);
        // 标题背景卡片
        final GradientDrawable titleBg = new GradientDrawable();
        titleBg.setColor(CARD_BACKGROUND);
        titleBg.setCornerRadius(dp2px(16));
        titleBg.setStroke(dp2px(1), CARD_BORDER);
        titleContainer.setBackground(titleBg);
        titleContainer.setPadding(dp2px(20), dp2px(24), dp2px(20), dp2px(24));

        // 主标题
        final TextView titleView = new TextView(this);
        titleView.setText("应用安装防护模块 2.0");
        titleView.setTextSize(24);
        titleView.setTextColor(DIALOG_TITLE_COLOR);
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);

        // 子标题
        final TextView subtitleView = new TextView(this);
        subtitleView.setText("(永恒之蓝 / 小淋)");
        subtitleView.setTextSize(16);
        subtitleView.setTextColor(DIALOG_TEXT_COLOR);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(0, dp2px(8), 0, 0);

        titleContainer.addView(titleView);
        titleContainer.addView(subtitleView);

        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp2px(20);
        rootLayout.addView(titleContainer, params);

        // 添加到动画列表
        sectionViews.add(titleContainer);
        // 初始隐藏，准备动画
        titleContainer.setAlpha(0f);
        titleContainer.setTranslationY(dp2px(20));
    }

    /** 添加状态卡片 */
    private void addStatusSection() {
        final LinearLayout statusContainer = new LinearLayout(this);
        statusContainer.setOrientation(LinearLayout.VERTICAL);
        statusContainer.setGravity(Gravity.CENTER);
        // 状态卡片背景
        final GradientDrawable statusBg = new GradientDrawable();
        statusBg.setCornerRadius(dp2px(25));
        final boolean isActivated = isModuleActivated();
        if (isActivated) {
            statusBg.setColor(ACTIVATED_BG_COLOR);
            statusBg.setStroke(dp2px(2), ACTIVATED_COLOR);
        } else {
            statusBg.setColor(DEACTIVATED_BG_COLOR);
            statusBg.setStroke(dp2px(2), DEACTIVATED_COLOR);
        }
        statusContainer.setBackground(statusBg);
        statusContainer.setPadding(dp2px(40), dp2px(20), dp2px(40), dp2px(20));

        // 状态图标
        final TextView statusIcon = new TextView(this);
        statusIcon.setText(isActivated ? "✅" : "❌");
        statusIcon.setTextSize(32);
        statusIcon.setGravity(Gravity.CENTER);

        // 状态文本
        statusView = new TextView(this);
        statusView.setText(isActivated ? "模块已激活" : "模块未激活");
        statusView.setTextSize(18);
        statusView.setTextColor(isActivated ? ACTIVATED_COLOR : DEACTIVATED_COLOR);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTypeface(null, android.graphics.Typeface.BOLD);
/*
        // 提示文本
        final TextView hintView = new TextView(this);
        hintView.setText("点击刷新状态");
        hintView.setTextSize(12);
        hintView.setTextColor(0xFF888888);
        hintView.setGravity(Gravity.CENTER);
        hintView.setPadding(0, dp2px(8), 0, 0);
*/
        statusContainer.addView(statusIcon);
        statusContainer.addView(statusView);
     //   statusContainer.addView(hintView);

        // 点击事件
        statusContainer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					refreshActivationStatus();
					// 点击动画
					v.animate().scaleX(0.75f).scaleY(0.75f).setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                            }
                        });
					// 显示状态详情对话框
					showStatusDetailDialog();
				}
			});

        // 触摸反馈
        statusContainer.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, android.view.MotionEvent event) {
					switch (event.getAction()) {
						case android.view.MotionEvent.ACTION_DOWN:
							v.setAlpha(0.7f);
							break;
						case android.view.MotionEvent.ACTION_UP:
						case android.view.MotionEvent.ACTION_CANCEL:
							v.setAlpha(1f);
							break;
					}
					return false;
				}
			});

        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.WRAP_CONTENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp2px(24);
        rootLayout.addView(statusContainer, params);

        // 添加到动画列表
        sectionViews.add(statusContainer);
        // 初始隐藏，准备动画
        statusContainer.setAlpha(0f);
        statusContainer.setTranslationY(dp2px(20));
    }

    /** 添加功能模块板块 */
    private void addModuleSection(final int sectionIndex) {
        if (sectionIndex >= MODULE_SECTIONS.length) return;
        final String[] section = MODULE_SECTIONS[sectionIndex];
        if (section.length == 0) return;

        // 板块容器
        final LinearLayout sectionContainer = new LinearLayout(this);
        sectionContainer.setOrientation(LinearLayout.VERTICAL);
        sectionContainer.setTag(sectionIndex);

        // 板块背景
        final GradientDrawable sectionBg = new GradientDrawable();
        sectionBg.setColor(DIALOG_BACKGROUND);
        sectionBg.setCornerRadius(dp2px(8));
        sectionBg.setStroke(dp2px(1), CARD_BORDER);
        sectionContainer.setBackground(sectionBg);
        final int containerPadding = dp2px(16);
        sectionContainer.setPadding(containerPadding, containerPadding, containerPadding, containerPadding);

        // 板块标题
        final TextView sectionTitle = new TextView(this);
        sectionTitle.setText(section[0]);
        sectionTitle.setTextSize(18);
        sectionTitle.setTextColor(getSectionColor(section[0]));
        sectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        sectionTitle.setPadding(0, 0, 0, dp2px(12));
        sectionContainer.addView(sectionTitle);

        // 添加功能项
        for (int i = 1; i < section.length; i++) {
            final TextView itemView = createFeatureItem(section[i]);
            sectionContainer.addView(itemView);
            // 添加分割线（最后一项不加）
            if (i < section.length - 1) {
                final View divider = new View(this);
                divider.setBackgroundColor(0xFFF5F5F5);
                final LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT,
					dp2px(1)
                );
                dividerParams.topMargin = dp2px(8);
                dividerParams.bottomMargin = dp2px(8);
                sectionContainer.addView(divider, dividerParams);
            }
        }

        // 点击事件
        sectionContainer.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(final View v) {
					// 缩放动画
					v.animate().scaleX(0.78f).scaleY(0.78f).setDuration(150)
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                            }
                        });
					// 显示板块详情
					showSectionDetailDialog(section);
					// 背景色反馈
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

        // 触摸反馈
        sectionContainer.setOnTouchListener(new View.OnTouchListener() {
				@Override
				public boolean onTouch(View v, android.view.MotionEvent event) {
					switch (event.getAction()) {
						case android.view.MotionEvent.ACTION_DOWN:
							v.setAlpha(0.7f);
							break;
						case android.view.MotionEvent.ACTION_UP:
						case android.view.MotionEvent.ACTION_CANCEL:
							v.setAlpha(1f);
							break;
					}
					return false;
				}
			});

        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
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
    private TextView createFeatureItem(final String text) {
        final TextView itemView = new TextView(this);
        itemView.setText(text);
        itemView.setTextSize(14);
        itemView.setTextColor(DIALOG_TEXT_COLOR);
        itemView.setPadding(dp2px(8), dp2px(6), dp2px(8), dp2px(6));
        itemView.setLineSpacing(dp2px(2), 1.1f);

        // 设置图标颜色
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
        final boolean isActivated = isModuleActivated();
        final String message = isActivated ?
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

        // 创建对话框
        final AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
			.setTitle("模块状态详情")
			.setMessage(message)
			.setPositiveButton("确定", null)
			.create();

        // 设置对话框窗口属性
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
				@Override
				public void onShow(DialogInterface dialogInterface) {
					try {
						final Window window = dialog.getWindow();
						if (window != null) {
							final WindowManager.LayoutParams params = window.getAttributes();
							params.width = WindowManager.LayoutParams.MATCH_PARENT;
							params.height = WindowManager.LayoutParams.WRAP_CONTENT;
							params.gravity = Gravity.CENTER;
							params.horizontalMargin = 0.05f;
							window.setAttributes(params);
						}
					} catch (Exception e) {
						// 忽略异常
					}
				}
			});
        dialog.show();
    }

    /** 显示板块详情对话框 */
    private void showSectionDetailDialog(final String[] section) {
        if (section.length < 2) return;
        final String title = section[0];
        final StringBuilder message = new StringBuilder();
        for (int i = 1; i < section.length; i++) {
            message.append("• ").append(section[i]).append("\n");
        }
        message.append("\n─────────────────\n");
        message.append("📌 功能说明：\n");

        // 功能说明文本
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

        // 创建对话框
        final AlertDialog dialog = new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
			.setTitle(title)
			.setMessage(message.toString())
			.setPositiveButton("确定", null)
			.create();

        // 设置对话框窗口属性
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
				@Override
				public void onShow(DialogInterface dialogInterface) {
					try {
						final Window window = dialog.getWindow();
						if (window != null) {
							final WindowManager.LayoutParams params = window.getAttributes();
							params.width = WindowManager.LayoutParams.MATCH_PARENT;
							params.height = WindowManager.LayoutParams.WRAP_CONTENT;
							params.gravity = Gravity.CENTER;
							params.horizontalMargin = 0.05f;
							window.setAttributes(params);
						}
					} catch (Exception e) {
						// 忽略异常
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
    private void updateSectionAnimations(final int scrollY, final boolean scrollingDown) {
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < sectionViews.size(); i++) {
            View section = sectionViews.get(i);
            if (section.getVisibility() != View.VISIBLE) continue;
            final int[] location = new int[2];
            section.getLocationOnScreen(location);
            final int sectionTop = location[1];
            final int sectionBottom = sectionTop + section.getHeight();
            final int screenCenter = scrollY + screenHeight / 2;
            final int sectionCenter = (sectionTop + sectionBottom) / 2;
            final int distanceFromCenter = Math.abs(sectionCenter - screenCenter);
            final float maxFloatDistance = dp2px(15);
            final float floatDistance = maxFloatDistance * (1.0f - Math.min(distanceFromCenter / (float) screenHeight, 1.0f));
            final float targetTranslationY;

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
    private void animateSectionIn(final View view, final int delay) {
        final AnimationSet animationSet = new AnimationSet(true);
        animationSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animationSet.setStartOffset(delay);

        // 淡入动画
        final AlphaAnimation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setDuration(1000);
        animationSet.addAnimation(fadeIn);

        // 上滑动画
        final TranslateAnimation slideUp = new TranslateAnimation(0, 0, dp2px(30), 0);
        slideUp.setDuration(1500);
        animationSet.addAnimation(slideUp);

        // 动画结束后小抖动
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
											view.animate()
                                                .translationY(0)
                                                .setDuration(600)
                                                .start();
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
        final boolean isActivated = isModuleActivated();
        // 更新状态文本
        statusView.setText(isActivated ? "模块已激活" : "模块未激活");
        statusView.setTextColor(isActivated ? ACTIVATED_COLOR : DEACTIVATED_COLOR);

        // 更新状态卡片背景
        final View statusContainer = (View) statusView.getParent();
        final GradientDrawable statusBg = new GradientDrawable();
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
    private int getSectionColor(final String title) {
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
        final LinearLayout bottomContainer = new LinearLayout(this);
        bottomContainer.setOrientation(LinearLayout.VERTICAL);
        bottomContainer.setGravity(Gravity.CENTER);
        final GradientDrawable bottomBg = new GradientDrawable();
        bottomBg.setColor(0xFFF5F5F5);
        bottomBg.setCornerRadius(dp2px(8));
        bottomContainer.setBackground(bottomBg);
        bottomContainer.setPadding(dp2px(20), dp2px(16), dp2px(20), dp2px(16));

        // 版本信息
        final TextView versionView = new TextView(this);
        versionView.setText("版本: 2.0.37_103 (Build 2026.02.13)");
        versionView.setTextSize(12);
        versionView.setTextColor(DIALOG_TEXT_COLOR);
        versionView.setGravity(Gravity.CENTER);

        // 版权信息
        final TextView copyrightView = new TextView(this);
        copyrightView.setText("© 2026 永恒之蓝(小淋)");
        copyrightView.setTextSize(12);
        copyrightView.setTextColor(0xFF888888);
        copyrightView.setGravity(Gravity.CENTER);
        copyrightView.setPadding(0, dp2px(4), 0, dp2px(12));

        // 水平布局包裹两个超链接（左右平行核心）
        final LinearLayout linkLayout = new LinearLayout(this);
        linkLayout.setOrientation(LinearLayout.HORIZONTAL);
        linkLayout.setGravity(Gravity.CENTER);
        final int linkMargin = dp2px(50); // 两个链接间距

        // 模块主页超链接
        final TextView homeView = new TextView(this);
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
				}
			});

        // 模块仓库超链接
        final TextView repoView = new TextView(this);
        repoView.setText("📦 官方仓库(GitHub)");
        repoView.setTextSize(12);
        repoView.setTextColor(LINK_COLOR);
        repoView.setGravity(Gravity.CENTER);
        repoView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Xposed-Modules-Repo/com.install.appinstall.xl"));
					startActivity(intent);
				}
			});

        // 加入水平布局
        linkLayout.addView(homeView);
        linkLayout.addView(repoView);

        // 免责声明
        final TextView disclaimerView = new TextView(this);
        disclaimerView.setText("仅供个人学习测试，禁商用禁引流以及禁付费盈利");
        disclaimerView.setTextSize(11);
        disclaimerView.setTextColor(0xFFAAAAAA);
        disclaimerView.setGravity(Gravity.CENTER);
        disclaimerView.setPadding(0, dp2px(8), 0, 0);

        // 依次添加控件
        bottomContainer.addView(versionView);
        bottomContainer.addView(copyrightView);
        bottomContainer.addView(linkLayout);
        bottomContainer.addView(disclaimerView);

        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
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
    private int dp2px(final int dp) {
        final float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /** 模块激活状态检测 */
    public static boolean isModuleActivated() {
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}

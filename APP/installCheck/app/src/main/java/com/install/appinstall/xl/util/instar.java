package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.install.appinstall.xl.HookInit;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class instar {

    // ========== 工具方法：为按钮设置圆角背景 ==========
    private static void setButtonRound(Button btn, int color) {
        try {
            Class<?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
            Object drawable = gradientDrawableClass.newInstance();
            Method setColorMethod = gradientDrawableClass.getMethod("setColor", int.class);
            Method setCornerRadiusMethod = gradientDrawableClass.getMethod("setCornerRadius", float.class);
            setColorMethod.invoke(drawable, color);
            setCornerRadiusMethod.invoke(drawable, 25f); // 圆角半径
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                btn.setBackground((android.graphics.drawable.Drawable) drawable);
            } else {
                btn.setBackgroundDrawable((android.graphics.drawable.Drawable) drawable);
            }
        } catch (Exception e) {
            btn.setBackgroundColor(color); // 降级方案
        }
        btn.setTextColor(0xFFFFFFFF);
    }

    // ========== 显示“设置自动处理”弹窗 ==========
    public static void showAutoSettingDialog(final Activity activity,
                                             final String identifier,
                                             final String displayName,
                                             final Runnable onChanged,
                                             final HookInit hookInit,
                                             final String app) {
        if (identifier == null || identifier.isEmpty()) return;

        String message = "当前捕获的 <font color='#FF5722'><b>" + displayName + "</b></font><br><br>" +
            "<font color='#9E9E9E'>黑名单：始终阻止并拦截启动<br>" +
            "白名单：始终允许并真实启动<br><br>" +
            "智能判断规则：<br>" +
            "• 相同内容你选择了“虚假/真实/取消”按钮3次，将自动处理选择<br>" +
            "• 若3次内选择不一致将重置计数，每次将会弹出询问启动确认</font>";

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        TextView msgView = new TextView(activity);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            msgView.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
        } else {
            msgView.setText(Html.fromHtml(message));
        }
        msgView.setTextIsSelectable(true);
        msgView.setTextSize(14);
        msgView.setTextColor(0xFF333333);
        layout.addView(msgView);

        final AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle("设置自动处理")
            .setView(layout)
            .setPositiveButton("加入黑名单", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    String current = HookInit.getAutoAction(app, identifier);
                    if ("black".equals(current)) {
                        ToastUtil.show(activity, "当前已是黑名单\n无需重复添加");
                    } else {
                        HookInit.putAutoAction(app, identifier, "black");
                        hookInit.saveConfigToFile();
                        ToastUtil.show(activity, "已加入黑名单");
                    }
                    // 不关闭弹窗，允许继续切换
                }
            })
            .setNegativeButton("加入白名单", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    String current = HookInit.getAutoAction(app, identifier);
                    if ("white".equals(current)) {
                        ToastUtil.show(activity, "当前已是白名单\n无需重复添加");
                    } else {
                        HookInit.putAutoAction(app, identifier, "white");
                        hookInit.saveConfigToFile();
                        ToastUtil.show(activity, "已加入白名单");
                    }
                    // 不关闭弹窗
                }
            })
            .setNeutralButton("取消设置", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    d.dismiss(); // 只有取消按钮关闭弹窗
                }
            })
            .create();

        // 弹窗关闭时刷新管理列表
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    if (onChanged != null) {
                        onChanged.run();
                    }
                }
            });

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    Window window = dialog.getWindow();
                    if (window != null) {
                        WindowManager.LayoutParams params = window.getAttributes();
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.gravity = Gravity.CENTER;
                        params.horizontalMargin = 0.05f;
                        window.setAttributes(params);
                    }
                }
            });

        dialog.show();
    }

    // ========== 显示“处理列表”管理弹窗==========
    public static void showManageListDialog(final Activity activity, final HookInit hookInit, final String app) {
        // 获取数据
        final Map<String, String> actionEntries = HookInit.getAllAutoActions(app);
        final Map<String, List<String>> recordEntries = HookInit.getAllAutoRecords(app);

        if (actionEntries.isEmpty() && recordEntries.isEmpty()) {
            ToastUtil.show(activity, "❌当前没有名单或记录列表");
            return;
        }

        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 30, 40, 30);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        // ========== 黑白名单区域 ==========
        if (!actionEntries.isEmpty()) {
            TextView title = new TextView(activity);
            title.setText("📋 黑/白名单列表(“排除”包不纳入)");
            title.setTextSize(16);
            title.setTextColor(0xFF4CAF50);
            title.setPadding(0, 10, 0, 10);
            container.addView(title);

            for (final Map.Entry<String, String> entry : actionEntries.entrySet()) {
                final String id = entry.getKey();
                final String type = entry.getValue();
                String typeDesc = "black".equals(type) ? "⛔ 黑名单" : "✅ 白名单";

                // 截断显示名（仅对链接进行80字符截断）
                String displayName = id.contains("://") ? truncateString(id, 80): id;

                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(0, 8, 0, 8);

                // 标识符 TextView（占据剩余空间，单行截断）
                TextView idTv = new TextView(activity);
                idTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                idTv.setText(displayName);
                idTv.setTextSize(14);
                idTv.setTextColor(0xFF333333);
                idTv.setTextIsSelectable(true);
                idTv.setEllipsize(TextUtils.TruncateAt.END);
                idTv.setMaxLines(5); //最大行数
                itemLayout.addView(idTv);

                // 类型描述 TextView（固定宽度）
                TextView typeTv = new TextView(activity);
                typeTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                typeTv.setText(typeDesc);
                typeTv.setTextSize(14);
                typeTv.setTextColor(0xFF333333);
                typeTv.setPadding(10, 0, 0, 0);
                itemLayout.addView(typeTv);

                // 设置按钮
                Button configBtn = new Button(activity);
                configBtn.setText("设置");
                configBtn.setTextSize(12);
                configBtn.setPadding(20, 5, 20, 5);
                setButtonRound(configBtn, 0xAA2196F3);
                configBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String shortId = id.length() > 100 ? id.substring(0, 97) + "..." : id;
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                            showAutoSettingDialog(activity, id, shortId, new Runnable() {
                                    @Override
                                    public void run() {
                                        showManageListDialog(activity, hookInit, app);
                                    }
                                }, hookInit, app);
                        }
                    });
                itemLayout.addView(configBtn);

                // 删除按钮
                Button deleteBtn = new Button(activity);
                deleteBtn.setText("删除");
                deleteBtn.setTextSize(12);
                deleteBtn.setPadding(20, 5, 20, 5);
                setButtonRound(deleteBtn, 0xAAF44336);
                deleteBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            HookInit.removeAutoAction(app, id);
                            hookInit.saveConfigToFile();
                            ToastUtil.show(activity, "已删除黑白名单：" + id);
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                            showManageListDialog(activity, hookInit, app);
                        }
                    });
                itemLayout.addView(deleteBtn);

                container.addView(itemLayout);
            }

            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2));
            divider.setBackgroundColor(0xFFE0E0E0);
            container.addView(divider);
        }

        // ========== 智能记录区域 ==========
        if (!recordEntries.isEmpty()) {
            TextView title = new TextView(activity);
            title.setText("📊 智能判断(超过3次相同,自动处理)");
            title.setTextSize(16);
            title.setTextColor(0xFF2196F3);
            title.setPadding(0, 10, 0, 10);
            container.addView(title);

            for (final Map.Entry<String, List<String>> entry : recordEntries.entrySet()) {
                final String id = entry.getKey();
                final List<String> history = entry.getValue();
                StringBuilder desc = new StringBuilder();
                for (int i = 0; i < history.size(); i++) {
                    String choice = history.get(i);
                    String emoji = "real".equals(choice) ? "✅" : ("fake".equals(choice) ? "‼️" : "🕸️");
                    desc.append(emoji).append(" ");
                }
                desc.append("(").append(history.size()).append("次)");

                // 截断显示名（仅对链接进行50字符截断）
                String display = id.contains("://") ? truncateString(id, 50) : id;

                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(0, 8, 0, 8);

                // 标识符 TextView
                TextView idTv = new TextView(activity);
                idTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                idTv.setText(display);
                idTv.setTextSize(14);
                idTv.setTextColor(0xFF333333);
                idTv.setTextIsSelectable(true);
                idTv.setEllipsize(TextUtils.TruncateAt.END);
                idTv.setMaxLines(5); //最大行数
                itemLayout.addView(idTv);

                // 表情符号历史 TextView
                TextView historyTv = new TextView(activity);
                historyTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                historyTv.setText(desc.toString());
                historyTv.setTextSize(14);
                historyTv.setTextColor(0xFF333333);
                historyTv.setPadding(10, 0, 0, 0);
                itemLayout.addView(historyTv);

                // 删除记录按钮
                Button deleteBtn = new Button(activity);
                deleteBtn.setText("删除记录");
                deleteBtn.setTextSize(12);
                deleteBtn.setPadding(20, 5, 20, 5);
                setButtonRound(deleteBtn, 0xAAFF9800);
                deleteBtn.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            HookInit.removeAutoRecord(app, id);
                            hookInit.saveConfigToFile();
                            ToastUtil.show(activity, "已删除智能记录：" + id);
                            if (dialogRef[0] != null) {
                                dialogRef[0].dismiss();
                            }
                            showManageListDialog(activity, hookInit, app);
                        }
                    });
                itemLayout.addView(deleteBtn);

                container.addView(itemLayout);
            }
        }

        scrollView.addView(container);
        mainLayout.addView(scrollView);

        AlertDialog dialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle("管理名单记录表 - (小淋)")
            .setView(mainLayout)
            .setPositiveButton("关闭", null)
            .create();
        dialogRef[0] = dialog;

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface d) {
                    Window window = dialogRef[0].getWindow();
                    if (window != null) {
                        WindowManager.LayoutParams params = window.getAttributes();
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                        params.gravity = Gravity.CENTER;
                        window.setAttributes(params);
                    }
                }
            });

        dialog.show();
    }

// 工具方法：截断长字符串
    private static String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 5) + "...";
    }
}

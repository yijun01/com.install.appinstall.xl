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
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.install.appinstall.xl.HookInit;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
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
            setCornerRadiusMethod.invoke(drawable, 25f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                btn.setBackground((android.graphics.drawable.Drawable) drawable);
            } else {
                btn.setBackgroundDrawable((android.graphics.drawable.Drawable) drawable);
            }
        } catch (Exception e) {
            btn.setBackgroundColor(color);
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
                }
            })
            .setNeutralButton("取消设置", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    d.dismiss();
                }
            })
            .create();

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

    // ========== 显示“处理列表”管理弹窗（增强版 + 复选框批量选择） ==========
    public static void showManageListDialog(final Activity activity, final HookInit hookInit, final String app) {
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
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setHorizontalScrollBarEnabled(false);

        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        // ========== 黑白名单区域 ==========
        if (!actionEntries.isEmpty()) {
            // 标题行：标题 + 批量设置 + 全部删除
            LinearLayout titleRow = new LinearLayout(activity);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(0, 10, 0, 10);

            TextView title = new TextView(activity);
            title.setText("📋 黑/白名单列表(“排除”包不纳入)");
            title.setTextSize(16);
            title.setTextColor(0xFF4CAF50);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            titleRow.addView(title, titleParams);

            // 批量设置按钮
            Button batchBtn = new Button(activity);
            batchBtn.setText("批量设置");
            batchBtn.setTextSize(12);
            batchBtn.setPadding(20, 5, 20, 5);
            setButtonRound(batchBtn, 0xAA2196F3);
            batchBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showBatchSelectionDialog(activity, hookInit, app, actionEntries, dialogRef);
                    }
                });
            titleRow.addView(batchBtn);

            // 全部删除按钮
            Button deleteAllBtn = new Button(activity);
            deleteAllBtn.setText("全部删除");
            deleteAllBtn.setTextSize(12);
            deleteAllBtn.setPadding(20, 5, 20, 5);
            setButtonRound(deleteAllBtn, 0xAAF44336);
            deleteAllBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String[] options = {"删除所有黑名单", "删除所有白名单", "取消"};
                        final AlertDialog deleteAllDialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setTitle("清理黑白名单")
                            .setItems(options, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (which == 2) {
                                        dialog.dismiss();
                                        return;
                                    }
                                    String targetType = (which == 0) ? "black" : "white";
                                    List<String> toRemove = new ArrayList<>();
                                    for (Map.Entry<String, String> entry : actionEntries.entrySet()) {
                                        if (entry.getValue().equals(targetType)) {
                                            toRemove.add(entry.getKey());
                                        }
                                    }
                                    if (toRemove.isEmpty()) {
                                        ToastUtil.show(activity, "当前没有" + (targetType.equals("black") ? "黑名单" : "白名单") + "可清理");
                                        dialog.dismiss();
                                        return;
                                    }
                                    for (String key : toRemove) {
                                        HookInit.removeAutoAction(app, key);
                                    }
                                    hookInit.saveConfigToFile();
                                    ToastUtil.show(activity, (which == 0 ? "⛔ 所有黑名单已删除" : "✅ 所有白名单已删除"));
                                    dialog.dismiss();
                                    if (dialogRef[0] != null) {
                                        dialogRef[0].dismiss();
                                    }
                                    showManageListDialog(activity, hookInit, app);
                                }
                            })
                            .create();

                        // 设置对话框宽度，防止左右超出屏幕
                        deleteAllDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(DialogInterface dialog) {
                                    Window window = deleteAllDialog.getWindow();
                                    if (window != null) {
                                        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                                        int margin = (int) (40 * activity.getResources().getDisplayMetrics().density);
                                        WindowManager.LayoutParams params = window.getAttributes();
                                        params.width = screenWidth - margin;
                                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                                        window.setAttributes(params);
                                    }
                                }
                            });
                        deleteAllDialog.show();
                    }
                });
            titleRow.addView(deleteAllBtn);

            container.addView(titleRow);

            // 原有参数列表
            for (final Map.Entry<String, String> entry : actionEntries.entrySet()) {
                final String id = entry.getKey();
                final String type = entry.getValue();
                String typeDesc = "black".equals(type) ? "⛔ 黑名单" : "✅ 白名单";
                String displayName = id.contains("://") ? truncateString(id, 80) : id;

                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(0, 8, 0, 8);

                TextView idTv = new TextView(activity);
                idTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                idTv.setText(displayName);
                idTv.setTextSize(14);
                idTv.setTextColor(0xFF333333);
                idTv.setTextIsSelectable(true);
                idTv.setEllipsize(TextUtils.TruncateAt.END);
                idTv.setMaxLines(5);
                itemLayout.addView(idTv);

                TextView typeTv = new TextView(activity);
                typeTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                typeTv.setText(typeDesc);
                typeTv.setTextSize(14);
                typeTv.setTextColor(0xFF333333);
                typeTv.setPadding(10, 0, 0, 0);
                itemLayout.addView(typeTv);

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
            // 标题行：标题 + 全部删除按钮
            LinearLayout titleRow = new LinearLayout(activity);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(0, 10, 0, 10);

            TextView title = new TextView(activity);
            title.setText("📊 智能判断(超过3次相同,自动处理)");
            title.setTextSize(16);
            title.setTextColor(0xFF2196F3);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            titleRow.addView(title, titleParams);

            Button deleteAllRecordBtn = new Button(activity);
            deleteAllRecordBtn.setText("全部删除");
            deleteAllRecordBtn.setTextSize(12);
            deleteAllRecordBtn.setPadding(20, 5, 20, 5);
            setButtonRound(deleteAllRecordBtn, 0xAAFF9800);
            deleteAllRecordBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (recordEntries.isEmpty()) {
                            ToastUtil.show(activity, "当前没有记录可清理");
                            return;
                        }
                        String message = "<font color='#FF5722'><b>确定要删除所有智能记录吗？</b></font><br>此操作不可恢复！";
                        final AlertDialog confirmDialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
                            .setTitle("删除所有记录")
                            .setMessage(Html.fromHtml(message))
                            .setPositiveButton("确认删除", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    List<String> allKeys = new ArrayList<>(recordEntries.keySet());
                                    for (String id : allKeys) {
                                        HookInit.removeAutoRecord(app, id);
                                    }
                                    hookInit.saveConfigToFile();
                                    ToastUtil.show(activity, "✅ 所有智能记录已删除");
                                    dialog.dismiss();
                                    if (dialogRef[0] != null) {
                                        dialogRef[0].dismiss();
                                    }
                                    showManageListDialog(activity, hookInit, app);
                                }
                            })
                            .setNegativeButton("取消", null)
                            .create();

                        // 设置对话框宽度，防止左右超出屏幕
                        confirmDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(DialogInterface dialog) {
                                    Window window = confirmDialog.getWindow();
                                    if (window != null) {
                                        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                                        int margin = (int) (40 * activity.getResources().getDisplayMetrics().density);
                                        WindowManager.LayoutParams params = window.getAttributes();
                                        params.width = screenWidth - margin;
                                        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                                        window.setAttributes(params);
                                    }
                                }
                            });
                        confirmDialog.show();
                    }
                });
            titleRow.addView(deleteAllRecordBtn);

            container.addView(titleRow);

            // 原有记录列表
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

                String display = id.contains("://") ? truncateString(id, 50) : id;

                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(0, 8, 0, 8);

                TextView idTv = new TextView(activity);
                idTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                idTv.setText(display);
                idTv.setTextSize(14);
                idTv.setTextColor(0xFF333333);
                idTv.setTextIsSelectable(true);
                idTv.setEllipsize(TextUtils.TruncateAt.END);
                idTv.setMaxLines(5);
                itemLayout.addView(idTv);

                TextView historyTv = new TextView(activity);
                historyTv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                historyTv.setText(desc.toString());
                historyTv.setTextSize(14);
                historyTv.setTextColor(0xFF333333);
                historyTv.setPadding(10, 0, 0, 0);
                itemLayout.addView(historyTv);

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

    // ========== 带复选框的批量选择对话框 ==========
    private static void showBatchSelectionDialog(final Activity activity,
                                                 final HookInit hookInit,
                                                 final String app,
                                                 final Map<String, String> actionEntries,
                                                 final AlertDialog[] parentDialogRef) {
        final List<Map.Entry<String, String>> entryList = new ArrayList<>(actionEntries.entrySet());
        final boolean[] checkedItems = new boolean[entryList.size()];

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        // 标题行：提示文本 + 全选按钮
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, 20);

        TextView hint = new TextView(activity);
        hint.setText("请勾选要批量修改的参数：");
        hint.setTextSize(14);
        hint.setTextColor(0xFF333333);
        hint.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(hint);

        final Button toggleSelectBtn = new Button(activity);
        toggleSelectBtn.setText("全选");
        toggleSelectBtn.setTextSize(12);
        toggleSelectBtn.setPadding(20, 8, 20, 8);
        setButtonRound(toggleSelectBtn, 0xAA2196F3); // 初始蓝色
        titleRow.addView(toggleSelectBtn);

        layout.addView(titleRow);

        final ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                                       ViewGroup.LayoutParams.MATCH_PARENT,
                                       ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout checkBoxContainer = new LinearLayout(activity);
        checkBoxContainer.setOrientation(LinearLayout.VERTICAL);
        checkBoxContainer.setPadding(0, 10, 0, 10);

        // 更新按钮状态的辅助方法
        final Runnable updateToggleButton = new Runnable() {
            @Override
            public void run() {
                boolean allChecked = true;
                for (int i = 0; i < checkBoxContainer.getChildCount(); i++) {
                    View child = checkBoxContainer.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        CheckBox cb = (CheckBox) ((LinearLayout) child).getChildAt(0);
                        if (!cb.isChecked()) {
                            allChecked = false;
                            break;
                        }
                    }
                }
                if (allChecked) {
                    toggleSelectBtn.setText("取消全选");
                    setButtonRound(toggleSelectBtn, 0xAAFF6347); // 橙色
                } else {
                    toggleSelectBtn.setText("全选");
                    setButtonRound(toggleSelectBtn, 0xAA2196F3); // 蓝色
                }
            }
        };

        for (int i = 0; i < entryList.size(); i++) {
            final Map.Entry<String, String> entry = entryList.get(i);
            String id = entry.getKey();
            String type = entry.getValue();
            String display = id.contains("://") ? truncateString(id, 60) : id;
            String typeDesc = "black".equals(type) ? "⛔ 黑名单" : "✅ 白名单";

            final LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(0, 8, 0, 8);
            item.setFocusable(true);
            item.setClickable(true);

            final CheckBox checkBox = new CheckBox(activity);
            checkBox.setTag(i);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        int pos = (int) buttonView.getTag();
                        checkedItems[pos] = isChecked;
                        // 实时更新全选按钮状态
                        updateToggleButton.run();
                    }
                });
            item.addView(checkBox);

            TextView textView = new TextView(activity);
            textView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            textView.setText(display + "  (" + typeDesc + ")");
            textView.setTextSize(14);
            textView.setTextColor(0xFF333333);
            textView.setPadding(10, 0, 0, 0);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setMaxLines(3);
            item.addView(textView);

            // 参数点击事件：切换复选框状态
            item.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        checkBox.setChecked(!checkBox.isChecked());
                    }
                });

            checkBoxContainer.addView(item);
        }

        scrollView.addView(checkBoxContainer);
        layout.addView(scrollView);

        // 全选按钮点击事件
        toggleSelectBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean allChecked = true;
                    for (int i = 0; i < checkBoxContainer.getChildCount(); i++) {
                        View child = checkBoxContainer.getChildAt(i);
                        if (child instanceof LinearLayout) {
                            CheckBox cb = (CheckBox) ((LinearLayout) child).getChildAt(0);
                            if (!cb.isChecked()) {
                                allChecked = false;
                                break;
                            }
                        }
                    }

                    if (allChecked) {
                        // 当前全选 → 取消全选
                        for (int i = 0; i < checkBoxContainer.getChildCount(); i++) {
                            View child = checkBoxContainer.getChildAt(i);
                            if (child instanceof LinearLayout) {
                                CheckBox cb = (CheckBox) ((LinearLayout) child).getChildAt(0);
                                cb.setChecked(false);
                            }
                        }
                    } else {
                        // 当前未全选 → 全选
                        for (int i = 0; i < checkBoxContainer.getChildCount(); i++) {
                            View child = checkBoxContainer.getChildAt(i);
                            if (child instanceof LinearLayout) {
                                CheckBox cb = (CheckBox) ((LinearLayout) child).getChildAt(0);
                                cb.setChecked(true);
                            }
                        }
                    }
                    // 按钮状态已在复选框监听中自动更新
                }
            });

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(0, 20, 0, 0);

        Button blackBtn = new Button(activity);
        blackBtn.setText("设为黑名单");
        blackBtn.setTextSize(12);
        blackBtn.setPadding(20, 10, 20, 10);
        setButtonRound(blackBtn, 0xAAF44336);

        Button whiteBtn = new Button(activity);
        whiteBtn.setText("设为白名单");
        whiteBtn.setTextSize(12);
        whiteBtn.setPadding(20, 10, 20, 10);
        setButtonRound(whiteBtn, 0xAA4CAF50);

        Button cancelBtn = new Button(activity);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(12);
        cancelBtn.setPadding(20, 10, 20, 10);
        setButtonRound(cancelBtn, 0xAA9E9E9E);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(5, 0, 5, 0);
        blackBtn.setLayoutParams(btnParams);
        whiteBtn.setLayoutParams(btnParams);
        cancelBtn.setLayoutParams(btnParams);

        buttonRow.addView(blackBtn);
        buttonRow.addView(whiteBtn);
        buttonRow.addView(cancelBtn);
        layout.addView(buttonRow);

        final AlertDialog batchDialog = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle("批量选择")
            .setView(layout)
            .create();

        batchDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialog) {
                    try {
                        Window window = batchDialog.getWindow();
                        if (window != null) {
                            window.setWindowAnimations(0); // 禁用动画

                            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
                            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
                            int margin = (int) (40 * activity.getResources().getDisplayMetrics().density);

                            // 设置宽度（屏幕宽度减边距）
                            WindowManager.LayoutParams params = window.getAttributes();
                            params.width = screenWidth - margin;
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                            window.setAttributes(params);

                            // 限制 ScrollView 高度（70% 屏幕高度）
                            int maxScrollHeight = (int) (screenHeight * 0.7);
                            ViewGroup.LayoutParams scrollParams = scrollView.getLayoutParams();
                            scrollParams.height = maxScrollHeight;
                            scrollView.setLayoutParams(scrollParams);
                            scrollView.requestLayout();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

        blackBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyBatchChange(activity, hookInit, app, entryList, checkedItems, actionEntries, "black", parentDialogRef, batchDialog);
                }
            });

        whiteBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    applyBatchChange(activity, hookInit, app, entryList, checkedItems, actionEntries, "white", parentDialogRef, batchDialog);
                }
            });

        cancelBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    batchDialog.dismiss();
                }
            });

        batchDialog.show();
    }

    // 应用批量修改（只修改实际不同的参数）
    private static void applyBatchChange(Activity activity,
                                         HookInit hookInit,
                                         String app,
                                         List<Map.Entry<String, String>> entryList,
                                         boolean[] checkedItems,
                                         Map<String, String> actionEntries,
                                         String targetType,
                                         AlertDialog[] parentDialogRef,
                                         AlertDialog batchDialog) {
        List<String> selectedKeys = new ArrayList<>();
        for (int i = 0; i < checkedItems.length; i++) {
            if (checkedItems[i]) {
                selectedKeys.add(entryList.get(i).getKey());
            }
        }
        if (selectedKeys.isEmpty()) {
            ToastUtil.show(activity, "请至少勾选一个参数");
            return;
        }

        int modifiedCount = 0;
        int alreadyCount = 0;

        for (String key : selectedKeys) {
            String currentType = actionEntries.get(key);
            if (currentType == null) continue;

            if (!targetType.equals(currentType)) {
                HookInit.putAutoAction(app, key, targetType);
                modifiedCount++;
            } else {
                alreadyCount++;
            }
        }

        if (modifiedCount == 0) {
            ToastUtil.show(activity, "已是" + (targetType.equals("black") ? "黑名单" : "白名单") + ",无需修改");
            return;
        }

        hookInit.saveConfigToFile();

        String message = "批量修改完成,更改 " + modifiedCount + " 个参数";
        if (alreadyCount > 0) {
            message += "\n" + alreadyCount + " 个参数为相同:跳过修改";
        }
        ToastUtil.show(activity, message);

        batchDialog.dismiss();
        if (parentDialogRef[0] != null) {
            parentDialogRef[0].dismiss();
        }
        showManageListDialog(activity, hookInit, app);
    }

    // 工具方法：截断长字符串
    private static String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 5) + "...";
    }
}

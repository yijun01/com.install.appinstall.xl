package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.install.appinstall.xl.HookInit;

//启动拦截方法hook

public class instar {

    // ========== 工具方法 ==========
    private static void setRoundButtonBackground(Button button, int color) {
        try {
            Class<
                    ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
            Object drawable = gradientDrawableClass.newInstance();
            Method setColor = gradientDrawableClass.getMethod("setColor", int.class);
            Method setCornerRadius = gradientDrawableClass.getMethod("setCornerRadius", float.class);
            setColor.invoke(drawable, color);
            setCornerRadius.invoke(drawable, 25f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                button.setBackground((android.graphics.drawable.Drawable) drawable);
            } else {
                button.setBackgroundDrawable((android.graphics.drawable.Drawable) drawable);
            }
            button.setTextColor(0xFFFFFFFF);
            button.setPadding(More.spx(button.getContext(), 20), More.spx(button.getContext(), 10), More.spx(button.getContext(), 20), More.spx(button.getContext(), 10));
        } catch (Throwable e) {
            button.setBackgroundColor(color);
        }
    }

    private static String truncateString(String str, int maxLength) {
        if (str == null) return "";
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 5) + "...";
    }

    // ========== 安全刷新工具 ==========
    private static void refreshDialogSafely(final AlertDialog[] dialogRef, final Runnable refreshAction) {
        if (dialogRef != null && dialogRef.length > 0 && dialogRef[0] != null) {
            dialogRef[0].dismiss();
            dialogRef[0] = null;
        }
        More.postOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (refreshAction != null) refreshAction.run();
            }
        }, 50);
    }

    // ========== 校验通配符模式（全角转半角） ==========
    private static boolean isValidPattern(String input) {
        if (input == null) return false;
        String converted = input.replace('！', '!').replace('＊', '*').trim();
        if (converted.isEmpty()) return false;
        if (converted.equals("!")) return false;
        if (converted.contains("**")) return false;
        // 避免正则特殊字符导致异常，但不阻止用户输入，由 matchesPattern 捕获异常
        return true;
    }

    // ========== 设置自动处理对话框（含可编辑输入框） ==========
    public static void showAutoSettingDialog(final Activity activity, final String identifier,
            final String displayName, final Runnable onChanged,
            final HookInit hookInit, final String app) {
        if (identifier == null || identifier.isEmpty()) return;

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));

        // ---- 第一行：当前捕获的 + 输入框 + 规则按钮 ----
        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 10), More.spx(activity, 20));

        TextView label = new TextView(activity);
        label.setText("捕内\n获容");
        label.setTextSize(14);
        label.setTextColor(0xFF000000);
        label.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
        topRow.addView(label);

        final EditText inputEt = new EditText(activity);
        inputEt.setText(identifier);
        inputEt.setHint("支持 * 通配符，! 排除");
        inputEt.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
        inputEt.setTextSize(14);
        inputEt.setTextColor(0xFFFF5722);
        inputEt.setBackgroundColor(0xFFFFFFFF);
        inputEt.setHintTextColor(0xFF999999);
        inputEt.setBackgroundTintMode(null);
        inputEt.setOutlineProvider(null);
        inputEt.setMaxLines(5);
        inputEt.setSingleLine(false);
        inputEt.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(500)});
        // 设置权重为1，让输入框占据剩余空间
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1
        );
        topRow.addView(inputEt, etParams);

        // ---- 规则按钮 ----
        Button ruleBtn = new Button(activity);
        ruleBtn.setText("帮助");
        ruleBtn.setTextSize(14);
        ruleBtn.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
        ruleBtn.setTextColor(0xFFFFFFFF);
        try {
            Class<
                    ?> gradientDrawableClass = Class.forName("android.graphics.drawable.GradientDrawable");
            Object drawable = gradientDrawableClass.newInstance();
            Method setColorMethod = gradientDrawableClass.getDeclaredMethod("setColor", int.class);
            Method setCornerRadiusMethod = gradientDrawableClass.getDeclaredMethod("setCornerRadius", float.class);
            setColorMethod.invoke(drawable, 0xFF2196F3);
            setCornerRadiusMethod.invoke(drawable, 25f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                ruleBtn.setBackground((android.graphics.drawable.Drawable) drawable);
            } else {
                ruleBtn.setBackgroundDrawable((android.graphics.drawable.Drawable) drawable);
            }
        } catch (Exception e) {
            ruleBtn.setBackgroundColor(0xFF2196F3);
        }
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
        );
        ruleParams.setMargins(20, 20, 20, 20);
        topRow.addView(ruleBtn, ruleParams);

        layout.addView(topRow);

        // ---- 说明文字  ----
        String message = "<font color='#9E9E9E'><b>黑名单：</b>始终阻止并拦截启动<br><b>白名单：</b>始终允许并真实启动<br><br>" +
                "<b>智能判断规则：</b><br>• 相同内容你选择了“虚假/真实/取消”按钮3次，将自动处理选择<br>• 若3次内选择不一致将重置计数，每次将会弹出询问启动确认</font>";

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

        // ---- 规则按钮点击事件----
        ruleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ruleDetail = "<div style='padding:10px 0; line-height:1.8;'>" +
                        //  "<hr style='border:0;border-top:1px solid #E0E0E0;margin:12px 0;'>" +

                        // 通配符
                        "<font size='16' color='#2196F3'><b>• 通配符： *</b></font><br>" +
                        "<small><font color='#2196F3' >表示“匹配任意内容”，即“包含了”该内容进行放行/拦截)</font></small><br>" +
                        "<small><font color='#9E9E9E' style='margin-left:30px;'>简单示例：<b>com.example.*</b> 匹配所有以 <b>com.example.</b> 开头的包名放行或拦截</font></small><br>" +
                        "<small><font color='#9E9E9E'>简单示例：<b>https://example.com/*</b> 匹配所有 example.com 域名后的任意参数放行或拦截</font></small><br>" +
                        "<small><font color='#9E9E9E'>进阶示例：<b>*google*</b> 匹配包名或网址中包含“google”的所有内容放行或拦截</font></small><br>" +
                        "<small><font color='#9E9E9E'>进阶示例：<b>com.android.*</b> 匹配所有系统应用包名（如 com.android.settings）放行或拦截</font></small><br>" +
                        "<small><font color='#9E9E9E'>进阶示例：<b>https://*.ABC.com/*</b> 匹配ABC.com所有子域名下的链接（如 abc.ABC.com）放行或拦截</font></small><br>" +
                        "<small><font color='#FF5722'><b>放行=加入白名单，拦截=加入黑名单</b></font></small><br><br>" +

                        // 排除符
                        "<font size='16' color='#2196F3'><b>• 排除符： !</b></font><br>" +
                        "<small><font color='#2196F3'>表示“排除/放行”，即“不包含”该内容（仅用于黑名单排除）</font></small><br>" +
                        "<small><font color='#9E9E9E'>简单示例：<b>!com.example.safe</b> 排除包名 <b>com.example.safe</b> 不拦截并放行</font></small><br>" +
                        "<small><font color='#9E9E9E'>进阶示例：<b>!*ads*</b> 排除放行所有包名或网址中包含“ads”的内容（如 com.example.ads）</font></small><br>" +
                        "<small><font color='#9E9E9E'>进阶示例：<b> !https://*.ABC.com/*</b> 放行所有 ABC.com 域名下的请求</font></small><br>" +
                        "<small><font color='#FF5722'><b>提示:如仅对某项单独放行/拦截，请勿使用正则，直接添加内容即可<br>如单独处理:com.example.demo、https://abc.ABC.com/123等直接加入黑/白名单即可</b></font></small><br><br>" +

                        // 优先级
                        "<font size='16' color='#2196F3'><b>• 匹配优先级：</b></font><br>" +
                        "<small><font color='#9E9E9E'>➊ <b>精确匹配</b>(单独处理)优先于通配符</font></small><br>" +
                        "<small><font color='#9E9E9E'>➋ 多个通配规则匹配时，以<b>最后添加</b>的规则为准</font></small><br><br>" +

                        // 温馨提示
                        "<font size='16' color='#2196F3'><b>• 温馨提示：</b></font><br>" +
                        "<small><font color='#9E9E9E'>如只写 <b>!</b> 或两个 <b>**</b>，将会没有效果并阻止保存</font></small><br>" +
                        "<small><font color='#9E9E9E'><b>请多尝试组合或网络搜索</b> <b>如有错误请及时删除规则</b></font></small><br>" +
                        "</div>";

                AlertDialog ruleDialog = DialogUi.createBoundedDialog(
                        activity,
                        null,
                        ruleDetail,
                        new String[]{"知道了"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        d.dismiss();
                                    }
                                }
                        }
                );
                if (ruleDialog == null) {
                    return;
                }
                ruleDialog.setTitle("正则匹配符 使用说明");
                ruleDialog.show();
            }
        });

        // ---- 底部按钮（黑/白/取消） ----
        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "设置自动处理", null,
                new String[]{"加入黑名单", "加入白名单", "取消设置"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                String rawInput = inputEt.getText().toString().trim();
                                String newIdentifier = rawInput.replace('！', '!').replace('＊', '*');
                                if (!isValidPattern(newIdentifier)) {
                                    ToastUtil.showUnique(activity, "无效的模式，请检查输入");
                                    ReaLog.log("auto", "无效模式: " + newIdentifier);
                                    return;
                                }
                                String current = HookInit.getAutoAction(app, newIdentifier);
                                if ("black".equals(current)) {
                                    ToastUtil.showUnique(activity, "当前已是黑名单\n无需重复添加");
                                    ReaLog.log("auto", "重复黑名单: " + newIdentifier);
                                    d.dismiss();
                                    return;
                                }
                                HookInit.putAutoAction(app, newIdentifier, "black");
                                hookInit.saveConfigToFile();
                                ReaLog.log("auto", "加入黑名单: " + newIdentifier);
                                ToastUtil.showUnique(activity, "已加入黑名单");
                                d.dismiss();
                                if (onChanged != null) onChanged.run();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                String rawInput = inputEt.getText().toString().trim();
                                String newIdentifier = rawInput.replace('！', '!').replace('＊', '*');
                                if (!isValidPattern(newIdentifier)) {
                                    ToastUtil.showUnique(activity, "无效的模式，请检查输入");
                                    ReaLog.log("auto", "无效模式: " + newIdentifier);
                                    return;
                                }
                                String current = HookInit.getAutoAction(app, newIdentifier);
                                if ("white".equals(current)) {
                                    ToastUtil.showUnique(activity, "当前已是白名单\n无需重复添加");
                                    ReaLog.log("auto", "重复白名单: " + newIdentifier);
                                    d.dismiss();
                                    return;
                                }
                                HookInit.putAutoAction(app, newIdentifier, "white");
                                hookInit.saveConfigToFile();
                                ReaLog.log("auto", "加入白名单: " + newIdentifier);
                                ToastUtil.showUnique(activity, "已加入白名单");
                                d.dismiss();
                                if (onChanged != null) onChanged.run();
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                            }
                        }
                },
                layout
        );
        if (dialog == null) {
            return;
        }
        dialog.show();
    }

    // ========== 管理列表主对话框 ==========
    public static void showManageListDialog(final Activity activity, final HookInit hookInit, final String app) {
        final Map<String, String> actionEntries = HookInit.getAllAutoActions(app);
        final Map<String, List<String>> recordEntries = HookInit.getAllAutoRecords(app);
        if (actionEntries.isEmpty() && recordEntries.isEmpty()) {
            ToastUtil.showUnique(activity, "❌当前没有名单或记录列表");
            return;
        }

        LinearLayout mainLayout = new LinearLayout(activity);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setHorizontalScrollBarEnabled(false);
        final LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialogRef = new AlertDialog[1];

        // ===== 黑白名单区域 =====
        if (!actionEntries.isEmpty()) {
            LinearLayout titleRow = new LinearLayout(activity);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(More.spx(activity, 0), More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 10));
            TextView title = new TextView(activity);
            title.setText("📋 黑/白名单列表(“排除”包不纳入)");
            title.setTextSize(16);
            title.setTextColor(0xFF4CAF50);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            titleRow.addView(title);

            Button batchBtn = new Button(activity);
            batchBtn.setText("批量设置");
            batchBtn.setTextSize(12);
            batchBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
            setRoundButtonBackground(batchBtn, 0xAA2196F3);
            batchBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showBatchSelectionDialog(activity, hookInit, app, actionEntries, dialogRef);
                }
            });
            titleRow.addView(batchBtn);

            Button deleteAllBtn = new Button(activity);
            deleteAllBtn.setText("全部删除");
            deleteAllBtn.setTextSize(12);
            deleteAllBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
            setRoundButtonBackground(deleteAllBtn, 0xAAF44336);
            deleteAllBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog deleteAllDialog = DialogUi.createBoundedDialog(activity, "清理黑白名单", null,
                            new String[]{"删除所有黑名单", "删除所有白名单", "取消"},
                            new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            List<String> toRemove = new ArrayList<String>();
                                            for (Map.Entry<
                                                    String,
                                                    String> entry : actionEntries.entrySet()) {
                                                if ("black".equals(entry.getValue()))
                                                    toRemove.add(entry.getKey());
                                            }
                                            if (toRemove.isEmpty()) {
                                                ToastUtil.showUnique(activity, "当前没有黑名单可清理");
                                                dialog.dismiss();
                                                return;
                                            }
                                            for (String key : toRemove)
                                                HookInit.removeAutoAction(app, key);
                                            hookInit.saveConfigToFile();
                                            ReaLog.log("auto", "⛔ 删除所有黑名单, 数量: " + toRemove.size());
                                            ToastUtil.showUnique(activity, "⛔ 所有黑名单已删除");
                                            dialog.dismiss();
                                            refreshDialogSafely(dialogRef, new Runnable() {
                                                @Override
                                                public void run() {
                                                    showManageListDialog(activity, hookInit, app);
                                                }
                                            });
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            List<String> toRemove = new ArrayList<String>();
                                            for (Map.Entry<
                                                    String,
                                                    String> entry : actionEntries.entrySet()) {
                                                if ("white".equals(entry.getValue()))
                                                    toRemove.add(entry.getKey());
                                            }
                                            if (toRemove.isEmpty()) {
                                                ToastUtil.showUnique(activity, "当前没有白名单可清理");
                                                dialog.dismiss();
                                                return;
                                            }
                                            for (String key : toRemove)
                                                HookInit.removeAutoAction(app, key);
                                            hookInit.saveConfigToFile();
                                            ReaLog.log("auto", "✅ 删除所有白名单, 数量: " + toRemove.size());
                                            ToastUtil.showUnique(activity, "✅ 所有白名单已删除");
                                            dialog.dismiss();
                                            refreshDialogSafely(dialogRef, new Runnable() {
                                                @Override
                                                public void run() {
                                                    showManageListDialog(activity, hookInit, app);
                                                }
                                            });
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    }
                            }
                    );
                    if (deleteAllDialog == null) {
                        return;
                    }
                    deleteAllDialog.show();
                }
            });
            titleRow.addView(deleteAllBtn);
            container.addView(titleRow);

            for (final Map.Entry<String, String> entry : actionEntries.entrySet()) {
                final String id = entry.getKey();
                final String type = entry.getValue();
                String typeDesc = "black".equals(type) ? "⛔ 黑名单" : "✅ 白名单";

                // 显示特殊标记
                String displayName;
                if (id.startsWith("!")) {
                    displayName = "!：" + (id.length() > 80 ? id.substring(1, 77) + "..." : id.substring(1));
                } else if (id.contains("*")) {
                    displayName = "*：" + (id.length() > 80 ? id.substring(0, 77) + "..." : id);
                } else {
                    displayName = id.contains("://") ? truncateString(id, 80) : id;
                }

                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(More.spx(activity, 0), More.spx(activity, 8), More.spx(activity, 0), More.spx(activity, 8));
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
                typeTv.setPadding(More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
                itemLayout.addView(typeTv);
                Button configBtn = new Button(activity);
                configBtn.setText("设置");
                configBtn.setTextSize(12);
                configBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
                setRoundButtonBackground(configBtn, 0xAA2196F3);
                configBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String shortId = id.length() > 100 ? id.substring(0, 97) + "..." : id;
                        showAutoSettingDialog(activity, id, shortId, new Runnable() {
                            @Override
                            public void run() {
                                refreshDialogSafely(dialogRef, new Runnable() {
                                    @Override
                                    public void run() {
                                        showManageListDialog(activity, hookInit, app);
                                    }
                                });
                            }
                        }, hookInit, app);
                    }
                });
                itemLayout.addView(configBtn);
                Button deleteBtn = new Button(activity);
                deleteBtn.setText("删除");
                deleteBtn.setTextSize(12);
                deleteBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
                setRoundButtonBackground(deleteBtn, 0xAAF44336);
                deleteBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        HookInit.removeAutoAction(app, id);
                        hookInit.saveConfigToFile();
                        ReaLog.log("auto", "⛔ 删除黑白名单条目: " + id + "条");
                        ToastUtil.showUnique(activity, "已删除黑白名单" + id + "条");
                        refreshDialogSafely(dialogRef, new Runnable() {
                            @Override
                            public void run() {
                                showManageListDialog(activity, hookInit, app);
                            }
                        });
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

        // ===== 智能判断记录区域 =====
        if (!recordEntries.isEmpty()) {
            LinearLayout titleRow = new LinearLayout(activity);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(More.spx(activity, 0), More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 10));
            TextView title = new TextView(activity);
            title.setText("📊 智能判断(超过3次相同,自动处理)");
            title.setTextSize(16);
            title.setTextColor(0xFF2196F3);
            title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            titleRow.addView(title);

            Button deleteAllRecordBtn = new Button(activity);
            deleteAllRecordBtn.setText("全部删除");
            deleteAllRecordBtn.setTextSize(12);
            deleteAllRecordBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
            setRoundButtonBackground(deleteAllRecordBtn, 0xAAFF9800);
            deleteAllRecordBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (recordEntries.isEmpty()) {
                        ToastUtil.showUnique(activity, "当前没有记录可清理");
                        return;
                    }
                    AlertDialog confirmDialog = DialogUi.createBoundedDialog(activity, "删除所有记录",
                            "<font color='#FF5722'><b>确定要删除所有智能记录吗？</b></font><br>此操作不可恢复！",
                            new String[]{"确认删除", "取消"},
                            new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            List<String> allKeys = new ArrayList<
                                                    String>(recordEntries.keySet());
                                            for (String id : allKeys)
                                                HookInit.removeAutoRecord(app, id);
                                            hookInit.saveConfigToFile();
                                            ReaLog.log("auto", "✅ 删除所有智能记录, 数量: " + allKeys.size());
                                            ToastUtil.showUnique(activity, "✅ 所有智能记录已删除");
                                            dialog.dismiss();
                                            refreshDialogSafely(dialogRef, new Runnable() {
                                                @Override
                                                public void run() {
                                                    showManageListDialog(activity, hookInit, app);
                                                }
                                            });
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    }
                            }
                    );
                    if (confirmDialog == null) {
                        return;
                    }
                    confirmDialog.show();
                }
            });
            titleRow.addView(deleteAllRecordBtn);
            container.addView(titleRow);

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

                // 显示特殊标记
                String display;
                if (id.startsWith("!")) {
                    display = "!：" + (id.length() > 50 ? id.substring(1, 47) + "..." : id.substring(1));
                } else if (id.contains("*")) {
                    display = "*：" + (id.length() > 50 ? id.substring(0, 47) + "..." : id);
                } else {
                    display = id.contains("://") ? truncateString(id, 50) : id;
                }

                LinearLayout itemLayout = new LinearLayout(activity);
                itemLayout.setOrientation(LinearLayout.HORIZONTAL);
                itemLayout.setPadding(More.spx(activity, 0), More.spx(activity, 8), More.spx(activity, 0), More.spx(activity, 8));
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
                historyTv.setPadding(More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
                itemLayout.addView(historyTv);
                Button deleteBtn = new Button(activity);
                deleteBtn.setText("删除记录");
                deleteBtn.setTextSize(12);
                deleteBtn.setPadding(More.spx(activity, 20), More.spx(activity, 5), More.spx(activity, 20), More.spx(activity, 5));
                setRoundButtonBackground(deleteBtn, 0xAAFF9800);
                deleteBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        HookInit.removeAutoRecord(app, id);
                        hookInit.saveConfigToFile();
                        ReaLog.log("auto", "✅ 删除智能记录: " + id + "条");
                        ToastUtil.showUnique(activity, "已删除智能记录：" + id + "条");
                        refreshDialogSafely(dialogRef, new Runnable() {
                            @Override
                            public void run() {
                                showManageListDialog(activity, hookInit, app);
                            }
                        });
                    }
                });
                itemLayout.addView(deleteBtn);
                container.addView(itemLayout);
            }
        }

        scrollView.addView(container);
        mainLayout.addView(scrollView);

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "启动配置记录", null,
                new String[]{"关闭"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                dialogRef[0] = null;
                            }
                        }
                },
                mainLayout
        );
        if (dialog == null) {
            return;
        }
        dialogRef[0] = dialog;
        dialog.show();
    }

    // ========== 批量选择对话框 ==========
    public static void showBatchSelectionDialog(final Activity activity, final HookInit hookInit, final String app,
            final Map<String, String> actionEntries, final AlertDialog[] parentDialogRef) {
        final List<Map.Entry<String, String>> entryList = new ArrayList<
                Map.Entry<String, String>>(actionEntries.entrySet());
        final boolean[] checkedItems = new boolean[entryList.size()];
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(More.spx(activity, 40), More.spx(activity, 30), More.spx(activity, 40), More.spx(activity, 30));
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        TextView hint = new TextView(activity);
        hint.setText("请勾选要批量修改的参数：");
        hint.setTextSize(14);
        hint.setTextColor(0xFF333333);
        hint.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        titleRow.addView(hint);
        final Button toggleSelectBtn = new Button(activity);
        toggleSelectBtn.setText("全选");
        toggleSelectBtn.setTextSize(12);
        toggleSelectBtn.setPadding(More.spx(activity, 20), More.spx(activity, 8), More.spx(activity, 20), More.spx(activity, 8));
        setRoundButtonBackground(toggleSelectBtn, 0xAA2196F3);
        titleRow.addView(toggleSelectBtn);
        layout.addView(titleRow);

        final ScrollView scrollView = new ScrollView(activity);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final LinearLayout checkBoxContainer = new LinearLayout(activity);
        checkBoxContainer.setOrientation(LinearLayout.VERTICAL);
        checkBoxContainer.setPadding(More.spx(activity, 0), More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 10));

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
                    setRoundButtonBackground(toggleSelectBtn, 0xAAFF6347);
                } else {
                    toggleSelectBtn.setText("全选");
                    setRoundButtonBackground(toggleSelectBtn, 0xAA2196F3);
                }
            }
        };

        for (int i = 0; i < entryList.size(); i++) {
            final Map.Entry<String, String> entry = entryList.get(i);
            String id = entry.getKey();
            String type = entry.getValue();

            // 显示特殊标记
            String display;
            if (id.startsWith("!")) {
                display = "!：" + (id.length() > 60 ? id.substring(1, 57) + "..." : id.substring(1));
            } else if (id.contains("*")) {
                display = "*：" + (id.length() > 60 ? id.substring(0, 57) + "..." : id);
            } else {
                display = id.contains("://") ? truncateString(id, 60) : id;
            }

            String typeDesc = "black".equals(type) ? "⛔ 黑名单" : "✅ 白名单";
            final LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(More.spx(activity, 0), More.spx(activity, 8), More.spx(activity, 0), More.spx(activity, 8));
            item.setFocusable(true);
            item.setClickable(true);
            final CheckBox checkBox = new CheckBox(activity);
            checkBox.setTag(i);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    int pos = (int) buttonView.getTag();
                    checkedItems[pos] = isChecked;
                    updateToggleButton.run();
                }
            });
            item.addView(checkBox);
            TextView textView = new TextView(activity);
            textView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            textView.setText(display + "  (" + typeDesc + ")");
            textView.setTextSize(14);
            textView.setTextColor(0xFF333333);
            textView.setPadding(More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setMaxLines(3);
            item.addView(textView);
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
                boolean target = !allChecked;
                for (int i = 0; i < checkBoxContainer.getChildCount(); i++) {
                    View child = checkBoxContainer.getChildAt(i);
                    if (child instanceof LinearLayout) {
                        CheckBox cb = (CheckBox) ((LinearLayout) child).getChildAt(0);
                        cb.setChecked(target);
                    }
                }
            }
        });

        LinearLayout buttonRow = new LinearLayout(activity);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        buttonRow.setPadding(More.spx(activity, 0), More.spx(activity, 20), More.spx(activity, 0), More.spx(activity, 0));
        Button blackBtn = new Button(activity);
        blackBtn.setText("设为黑名单");
        blackBtn.setTextSize(12);
        blackBtn.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        setRoundButtonBackground(blackBtn, 0xAAF44336);
        Button whiteBtn = new Button(activity);
        whiteBtn.setText("设为白名单");
        whiteBtn.setTextSize(12);
        whiteBtn.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        setRoundButtonBackground(whiteBtn, 0xAA4CAF50);
        Button cancelBtn = new Button(activity);
        cancelBtn.setText("取消");
        cancelBtn.setTextSize(12);
        cancelBtn.setPadding(More.spx(activity, 20), More.spx(activity, 10), More.spx(activity, 20), More.spx(activity, 10));
        setRoundButtonBackground(cancelBtn, 0xAA9E9E9E);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btnParams.setMargins(5, 0, 5, 0);
        blackBtn.setLayoutParams(btnParams);
        whiteBtn.setLayoutParams(btnParams);
        cancelBtn.setLayoutParams(btnParams);
        buttonRow.addView(blackBtn);
        buttonRow.addView(whiteBtn);
        buttonRow.addView(cancelBtn);
        layout.addView(buttonRow);

        final AlertDialog[] batchDialogRef = new AlertDialog[1];
        batchDialogRef[0] = DialogUi.createBoundedDialog(activity, "批量选择", null,
                null, null, layout);
        if (batchDialogRef[0] == null) {
            return;
        }

        blackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyBatchChange(activity, hookInit, app, entryList, checkedItems, actionEntries, "black", parentDialogRef, batchDialogRef[
                0]);
            }
        });
        whiteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                applyBatchChange(activity, hookInit, app, entryList, checkedItems, actionEntries, "white", parentDialogRef, batchDialogRef[
                0]);
            }
        });
        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                batchDialogRef[0].dismiss();
            }
        });

        batchDialogRef[0].show();
    }

    // ========== 批量修改执行 ==========
    private static void applyBatchChange(final Activity activity, final HookInit hookInit, final String app,
            List<Map.Entry<String, String>> entryList, boolean[] checkedItems,
            Map<String, String> actionEntries, String targetType,
            AlertDialog[] parentDialogRef, AlertDialog batchDialog) {
        List<String> selectedKeys = new ArrayList<String>();
        for (int i = 0; i < checkedItems.length; i++) {
            if (checkedItems[i]) selectedKeys.add(entryList.get(i).getKey());
        }
        if (selectedKeys.isEmpty()) {
            ToastUtil.showUnique(activity, "请至少勾选一个参数");
            return;
        }
        int modifiedCount = 0, alreadyCount = 0;
        for (String key : selectedKeys) {
            String currentType = actionEntries.get(key);
            if (currentType == null) continue;
            if (!targetType.equals(currentType)) {
                HookInit.putAutoAction(app, key, targetType);
                modifiedCount++;
            } else alreadyCount++;
        }
        if (modifiedCount == 0) {
            ToastUtil.showUnique(activity, "已是" + (targetType.equals("black") ? "黑名单" : "白名单") + ",无需修改");
            ReaLog.log("auto", "已是" + (targetType.equals("black") ? "黑名单" : "白名单") + ",无需修改");
            return;
        }
        hookInit.saveConfigToFile();
        ReaLog.log("auto", "批量修改 " + modifiedCount + " 条记录为" + (targetType.equals("black") ? "黑名单" : "白名单"));
        String message = "批量修改完成,更改 " + modifiedCount + " 个参数";
        if (alreadyCount > 0) message += "\n" + alreadyCount + " 个参数为相同:跳过修改";
        ToastUtil.showUnique(activity, message);

        if (batchDialog != null) batchDialog.dismiss();
        refreshDialogSafely(parentDialogRef, new Runnable() {
            @Override
            public void run() {
                showManageListDialog(activity, hookInit, app);
            }
        });
    }
}

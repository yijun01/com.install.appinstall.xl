package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import com.install.appinstall.xl.HookInit;
import android.content.Context;

public class Spkill {
    private static volatile boolean isDialogShowing = false;
    private static AlertDialog currentManageDialog;

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

    private static void refreshPatternsList(final Activity activity, final HookInit hookInit) {
        if (currentManageDialog != null) {
            currentManageDialog.dismiss();
            currentManageDialog = null;
        }
        More.postOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (currentManageDialog != null) return;
                if (hookInit.getInterceptPatterns().isEmpty()) {
                    ToastUtil.showUnique(activity, "当前无拦截记录");
                    return;
                }
                showManagePatternsDialog(activity, hookInit);
            }
        }, 50);
    }

    private static String join(CharSequence delimiter, Iterable<?> tokens) {
        if (tokens == null) return null;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object o : tokens) {
            if (!first) sb.append(delimiter);
            sb.append(o);
            first = false;
        }
        return sb.toString();
    }

    // 记录一次用户对历史拦截模式的选择，并维护连续性以决定是否静默拦截。
    private static void recordPatternChoice(HookInit.InterceptPattern p, String choice) {
        p.userChoice = choice;
        p.choiceCount++;
        p.lastDetectedTime = System.currentTimeMillis();
        p.recentChoices.add(choice);
        if (p.recentChoices.size() > 3)
            p.recentChoices.remove(0);
        if (p.recentChoices.size() == 3) {
            String first = p.recentChoices.get(0);
            if (first.equals(p.recentChoices.get(1)) &&
                    first.equals(p.recentChoices.get(2))) {
                p.silentIntercept = true;
            } else {
                p.silentIntercept = false;
            }
        } else {
            p.silentIntercept = false;
        }
    }

    // 批量修改记录状态
    private static void modifyBatch(Activity activity, HookInit hookInit, List<String> hashes,
            String newChoice, boolean keepCount) {
        List<String> toActuallyModify = new ArrayList<String>();
        for (String hash : hashes) {
            for (HookInit.InterceptPattern p : hookInit.getInterceptPatterns()) {
                if (p.patternHash.equals(hash)) {
                    // 清除计数版作用于任何记录都有效（清零计数并关闭静默）；
                    // 保留计数版仅状态不同的记录需要修改
                    if (!(keepCount && newChoice.equals(p.userChoice))) {
                        toActuallyModify.add(hash);
                    }
                    break;
                }
            }
        }
        if (toActuallyModify.isEmpty()) {
            ToastUtil.showUnique(activity, "所选记录无需修改，本次跳过");
            refreshPatternsList(activity, hookInit);
            return;
        }
        for (String hash : toActuallyModify) {
            for (HookInit.InterceptPattern p : hookInit.getInterceptPatterns()) {
                if (p.patternHash.equals(hash)) {
                    p.userChoice = newChoice;
                    if (!keepCount) {
                        p.silentIntercept = false;
                        p.choiceCount = 0;
                        p.recentChoices.clear();
                    }
                    break;
                }
            }
        }
        hookInit.saveConfigToFile();
        String label = ("intercept".equals(newChoice) ? "🚫 拦截" : "✅ 放行")
                + (keepCount ? "(保留计数)" : "(清除计数)");
        ReaLog.log("exit_intercept", "批量修改为" + label + ", 数量: " + toActuallyModify.size());
        ToastUtil.showUnique(activity, "成功修改 " + toActuallyModify.size() + " 条记录为" + label);
        refreshPatternsList(activity, hookInit);
    }

    public static void showManagePatternsDialog(final Activity activity, final HookInit hookInit) {
        ReaLog.log("system", "打开拦截记录管理界面");
        if (currentManageDialog != null) {
            currentManageDialog.dismiss();
            currentManageDialog = null;
        }
        final List<HookInit.InterceptPattern> patterns = hookInit.getInterceptPatterns();
        if (patterns.isEmpty()) {
            ToastUtil.showUnique(activity, "暂无拦截记录");
            return;
        }

        LinearLayout rootLayout = new LinearLayout(activity);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(More.spx(activity, 30), More.spx(activity, 20), More.spx(activity, 30), More.spx(activity, 20));

        LinearLayout titleLayout = new LinearLayout(activity);
        titleLayout.setOrientation(LinearLayout.HORIZONTAL);
        titleLayout.setGravity(Gravity.CENTER_VERTICAL);
        titleLayout.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        TextView title = new TextView(activity);
        title.setText("拦截列表（共" + patterns.size() + "条记录）");
        title.setTextSize(16);
        title.setTextColor(0xFF333333);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        titleLayout.addView(title);
        final Button selectAllBtn = new Button(activity);
        selectAllBtn.setText("全选");
        setRoundButtonBackground(selectAllBtn, 0xAA4CAF50);
        selectAllBtn.setPadding(More.spx(activity, 20), More.spx(activity, 8), More.spx(activity, 20), More.spx(activity, 8));
        titleLayout.addView(selectAllBtn);
        Button deleteAllBtn = new Button(activity);
        deleteAllBtn.setText("全部删除");
        setRoundButtonBackground(deleteAllBtn, 0xAAF44336);
        deleteAllBtn.setPadding(More.spx(activity, 20), More.spx(activity, 8), More.spx(activity, 20), More.spx(activity, 8));
        deleteAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog confirmDialog = DialogUi.createBoundedDialog(activity, "确认删除",
                        "确定要<font color='#FF5722'><b>删除所有拦截记录</b></font>吗？<br><br>此操作不可恢复！",
                        new String[]{"删除", "取消"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        hookInit.removeAllInterceptPatterns();
                                        ReaLog.log("exit_intercept", "删除所有拦截记录");
                                        ToastUtil.showUnique(activity, "✅ 已删除所有拦截记录");
                                        dialog.dismiss();
                                        refreshPatternsList(activity, hookInit);
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
                confirmDialog.show();
            }
        });
        titleLayout.addView(deleteAllBtn);
        rootLayout.addView(titleLayout);

        ScrollView scrollView = new ScrollView(activity);
        LinearLayout itemsLayout = new LinearLayout(activity);
        itemsLayout.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(itemsLayout);

        final Map<String, Boolean> selectedMap = new HashMap<String, Boolean>();
        final List<CheckBox> checkBoxList = new ArrayList<CheckBox>();

        for (final HookInit.InterceptPattern pattern : patterns) {
            final String hash = pattern.patternHash;
            selectedMap.put(hash, false);

            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
            item.setBackgroundColor(0xFFF5F5F5);
            item.setTag(hash);

            LinearLayout topRow = new LinearLayout(activity);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            final CheckBox checkBox = new CheckBox(activity);
            checkBox.setTag(hash);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    selectedMap.put(hash, isChecked);
                }
            });
            topRow.addView(checkBox);
            checkBoxList.add(checkBox);

            StringBuilder pkgPreview = new StringBuilder();
            List<String> allPkgs = new ArrayList<String>();
            allPkgs.addAll(pattern.installedPackages);
            allPkgs.addAll(pattern.notInstalledPackages);
            int totalPkgs = allPkgs.size();
            if (totalPkgs > 0) {
                int maxShow = Math.min(3, totalPkgs);
                for (int i = 0; i < maxShow; i++) {
                    if (i > 0) pkgPreview.append(", ");
                    pkgPreview.append(allPkgs.get(i));
                }
                if (totalPkgs > maxShow) pkgPreview.append(" 等").append(totalPkgs).append("个");
            } else pkgPreview.append("无包名");

            String hashDisplay = "哈希值: " + (hash.length() > 35 ? hash.substring(0, 35) + "…" : hash);
            String choiceText = "intercept".equals(pattern.userChoice) ? "🚫 拦截" : "✅ 放行";
            String silentText = pattern.silentIntercept ? " (已自动处理)" : "";
            String summary = String.format("%s\n包名: %s\n%s (已选择%d次)%s",
                    hashDisplay, pkgPreview.toString(), choiceText, pattern.choiceCount, silentText);
            TextView info = new TextView(activity);
            info.setText(summary);
            info.setTextSize(12);
            info.setTextColor(0xFF666666);
            info.setPadding(More.spx(activity, 20), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0));
            info.setTextIsSelectable(true);
            topRow.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            LinearLayout btnRow = new LinearLayout(activity);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            btnRow.setGravity(Gravity.END);
            btnRow.setPadding(More.spx(activity, 0), More.spx(activity, 10), More.spx(activity, 0), More.spx(activity, 0));

            Button modifyBtn = new Button(activity);
            modifyBtn.setText("修改");
            modifyBtn.setTextSize(12);
            setRoundButtonBackground(modifyBtn, 0xAA2196F3);
            modifyBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentManageDialog != null) {
                        currentManageDialog.dismiss();
                        currentManageDialog = null;
                    }
                    showChoiceModifyDialog(activity, hookInit, hash, pattern.userChoice);
                }
            });
            btnRow.addView(modifyBtn);

            Button delBtn = new Button(activity);
            delBtn.setText("删除");
            delBtn.setTextSize(12);
            setRoundButtonBackground(delBtn, 0xAAF44336);
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (currentManageDialog != null) {
                        currentManageDialog.dismiss();
                        currentManageDialog = null;
                    }
                    AlertDialog confirm = DialogUi.createBoundedDialog(activity, "确认删除",
                            "确定要删除此拦截记录吗？<br><br><font color='#FF5722'><b>删除后无法恢复,需重新选择</b></font><br>",
                            new String[]{"删除", "取消"},
                            new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            boolean success = hookInit.removeInterceptPattern(hash);
                                            ReaLog.log("exit_intercept", "删除单条拦截记录: " + hash);
                                            ToastUtil.showUnique(activity, success ? "✅ 删除成功" : "❌ 删除失败\n未找到记录");
                                            d.dismiss();
                                            refreshPatternsList(activity, hookInit);
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            d.dismiss();
                                            refreshPatternsList(activity, hookInit);
                                        }
                                    }
                            }
                    );
                    confirm.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            refreshPatternsList(activity, hookInit);
                        }
                    });
                    confirm.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            refreshPatternsList(activity, hookInit);
                        }
                    });
                    confirm.show();
                }
            });
            btnRow.addView(delBtn);

            item.addView(topRow);
            item.addView(btnRow);

            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            itemsLayout.addView(item);
            itemsLayout.addView(divider);
        }

        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout bottomBar = new LinearLayout(activity);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(More.spx(activity, 0), More.spx(activity, 20), More.spx(activity, 0), More.spx(activity, 0));

        Button batchModifyBtn = new Button(activity);
        batchModifyBtn.setText("批量修改");
        batchModifyBtn.setTextSize(12);
        setRoundButtonBackground(batchModifyBtn, 0xAA2196F3);
        batchModifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final List<String> toModify = new ArrayList<String>();
                for (Map.Entry<String, Boolean> entry : selectedMap.entrySet()) {
                    if (entry.getValue()) toModify.add(entry.getKey());
                }
                if (toModify.isEmpty()) {
                    ToastUtil.showUnique(activity, "❌ 未选择任何项");
                    refreshPatternsList(activity, hookInit);
                    return;
                }
                LinearLayout batchLayout = new LinearLayout(activity);
                batchLayout.setOrientation(LinearLayout.VERTICAL);
                batchLayout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));

                TextView batchTip = new TextView(activity);
                batchTip.setText("请选择修改方式：");
                batchTip.setTextSize(14);
                batchTip.setTextColor(0xFF333333);
                batchTip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
                batchLayout.addView(batchTip);

                final String[] batchOptions = {"🚫 拦截并清除计数", "✅ 放行并清除计数", "🚫 拦截但保留计数", "✅ 放行但保留计数"};
                final LinearLayout batchRadioContainer = new LinearLayout(activity);
                batchRadioContainer.setOrientation(LinearLayout.VERTICAL);

                final android.widget.RadioButton[] batchRadios = new android.widget.RadioButton[batchOptions.length];
                for (int i = 0; i < batchOptions.length; i++) {
                    final int index = i;
                    android.widget.RadioButton rb = new android.widget.RadioButton(activity);
                    rb.setText(batchOptions[i]);
                    rb.setTextSize(14);
                    rb.setTextColor(0xFF333333);
                    rb.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
                    rb.setChecked(false); // 批量为多条记录混杂状态，默认不预选
                    rb.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            for (int j = 0; j < batchRadios.length; j++) {
                                batchRadios[j].setChecked(j == index);
                            }
                        }
                    });
                    batchRadioContainer.addView(rb);
                    batchRadios[i] = rb;
                    View divider = new View(activity);
                    divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                    divider.setBackgroundColor(0xFFE0E0E0);
                    batchRadioContainer.addView(divider);
                }
                batchLayout.addView(batchRadioContainer);

                AlertDialog batchDialog = DialogUi.createBoundedDialog(activity, "批量修改", null,
                        new String[]{"确定", "取消"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        int pos = -1;
                                        for (int i = 0; i < batchRadios.length; i++) {
                                            if (batchRadios[i].isChecked()) {
                                                pos = i;
                                                break;
                                            }
                                        }
                                        if (pos < 0) {
                                            ToastUtil.showUnique(activity, "请选择修改方式");
                                            return;
                                        }
                                        String newChoice = (pos == 0 || pos == 2) ? "intercept" : "allow";
                                        boolean keepCount = (pos >= 2);
                                        d.dismiss();
                                        modifyBatch(activity, hookInit, toModify, newChoice, keepCount);
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        d.dismiss();
                                    }
                                }
                        },
                        batchLayout
                );
                batchDialog.show();
            }
        });
        bottomBar.addView(batchModifyBtn);

        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(More.dpInt(activity, 8), 1));
        bottomBar.addView(spacer);

        Button batchDeleteBtn = new Button(activity);
        batchDeleteBtn.setText("批量删除");
        batchDeleteBtn.setTextSize(12);
        setRoundButtonBackground(batchDeleteBtn, 0xAAFF9800);
        batchDeleteBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final List<String> toDelete = new ArrayList<String>();
                for (Map.Entry<String, Boolean> entry : selectedMap.entrySet()) {
                    if (entry.getValue()) toDelete.add(entry.getKey());
                }
                if (toDelete.isEmpty()) {
                    ToastUtil.showUnique(activity, "❌ 未选择任何项");
                    refreshPatternsList(activity, hookInit);
                    return;
                }
                if (currentManageDialog != null) {
                    currentManageDialog.dismiss();
                    currentManageDialog = null;
                }
                AlertDialog confirm = DialogUi.createBoundedDialog(activity, "批量删除",
                        "确定要删除选中的<font color='#FF5722'><b> " + toDelete.size() + " </b></font>条哈希值吗？<br><br><font color='#FF5722'><b>删除后无法恢复,需重新选择</b></font><br>",
                        new String[]{"删除", "取消"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        int successCount = 0;
                                        for (String hash : toDelete) {
                                            if (hookInit.removeInterceptPattern(hash))
                                                successCount++;
                                        }
                                        ReaLog.log("exit_intercept", "批量删除拦截: " + successCount + "条");
                                        ToastUtil.showUnique(activity, "✅ 成功删除 " + successCount + " 条\n失败 " + (toDelete.size() - successCount) + " 条");
                                        d.dismiss();
                                        refreshPatternsList(activity, hookInit);
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        d.dismiss();
                                        refreshPatternsList(activity, hookInit);
                                    }
                                }
                        }
                );
                confirm.show();
            }
        });
        bottomBar.addView(batchDeleteBtn);
        rootLayout.addView(bottomBar);

        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            private boolean allSelected = false;

            @Override
            public void onClick(View v) {
                allSelected = !allSelected;
                for (CheckBox cb : checkBoxList) {
                    cb.setChecked(allSelected);
                }
                selectAllBtn.setText(allSelected ? "取消全选" : "全选");
            }
        });

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "拦截退出记录", null,
                new String[]{"关闭"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                currentManageDialog = null;
                            }
                        }
                },
                rootLayout
        );
        currentManageDialog = dialog;
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                currentManageDialog = null;
            }
        });
        dialog.show();
    }

    private static void showChoiceModifyDialog(final Activity activity, final HookInit hookInit,
            final String patternHash, final String currentChoice) {
        if (currentManageDialog != null) {
            currentManageDialog.dismiss();
            currentManageDialog = null;
        }

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(More.spx(activity, 40), More.spx(activity, 20), More.spx(activity, 40), More.spx(activity, 20));

        TextView tip = new TextView(activity);
        tip.setText("请选择修改方式：");
        tip.setTextSize(14);
        tip.setTextColor(0xFF333333);
        tip.setPadding(More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 0), More.spx(activity, 20));
        layout.addView(tip);

        final String[] options = {"🚫 拦截并清除计数", "✅ 放行并清除计数", "🚫 拦截但保留计数", "✅ 放行但保留计数"};
        final int checked = "intercept".equals(currentChoice) ? 0 : 1;
        final LinearLayout radioContainer = new LinearLayout(activity);
        radioContainer.setOrientation(LinearLayout.VERTICAL);

        final android.widget.RadioButton[] radioButtons = new android.widget.RadioButton[options.length];
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            android.widget.RadioButton rb = new android.widget.RadioButton(activity);
            rb.setText(options[i]);
            rb.setTextSize(14);
            rb.setTextColor(0xFF333333);
            rb.setPadding(More.spx(activity, 20), More.spx(activity, 15), More.spx(activity, 20), More.spx(activity, 15));
            rb.setChecked(i == checked);
            rb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (int j = 0; j < radioButtons.length; j++) {
                        radioButtons[j].setChecked(j == index);
                    }
                }
            });
            radioContainer.addView(rb);
            radioButtons[i] = rb;
            View divider = new View(activity);
            divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            divider.setBackgroundColor(0xFFE0E0E0);
            radioContainer.addView(divider);
        }
        layout.addView(radioContainer);

        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "修改选择", null,
                new String[]{"确定", "取消"},
                new DialogInterface.OnClickListener[]{
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                int pos = 0;
                                for (int i = 0; i < radioButtons.length; i++) {
                                    if (radioButtons[i].isChecked()) {
                                        pos = i;
                                        break;
                                    }
                                }
                                String newChoice = (pos == 0 || pos == 2) ? "intercept" : "allow";
                                boolean keepCount = (pos >= 2);
                                if (keepCount && newChoice.equals(currentChoice)) {
                                    ToastUtil.showUnique(activity, "已是" + options[
                                            pos] + "状态\n无需修改");
                                    ReaLog.log("exit_intercept", "已是" + options[pos] + "状态，跳过");
                                    d.dismiss();
                                    refreshPatternsList(activity, hookInit);
                                    return;
                                }
                                if (keepCount) {
                                    hookInit.setInterceptPatternUserChoiceKeepCount(patternHash, newChoice);
                                    ToastUtil.showUnique(activity, "已修改为" + options[pos] +
                                            "\n保留累计计数，直接按新状态智能判断");
                                } else {
                                    hookInit.setInterceptPatternUserChoice(patternHash, newChoice);
                                    ToastUtil.showUnique(activity, "已修改为" + options[pos] +
                                            "\n计数已重置");
                                }
                                ReaLog.log("exit_intercept", "修改单条记录为" + options[pos] +
                                        (keepCount ? "(保留计数)" : "(清除计数)"));
                                d.dismiss();
                                refreshPatternsList(activity, hookInit);
                            }
                        },
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                d.dismiss();
                                refreshPatternsList(activity, hookInit);
                            }
                        }
                },
                layout
        );
        currentManageDialog = dialog;
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface d) {
                currentManageDialog = null;
            }
        });
        dialog.show();
    }

    public static void handleAppExit(final HookInit hookInit,
            final String exitMethod,
            final XC_MethodHook.MethodHookParam param) {
        try {
            final String targetApp = hookInit.getCurrentTargetApp();
            boolean blockExit = hookInit.blockExitMap.getOrDefault(targetApp, false);
            boolean superBlock = hookInit.superBlockExitMap.getOrDefault(targetApp, false);
            if (!blockExit && !superBlock) return;

            final HookInit.DetectedPackages detected = hookInit.analyzeDetectedPackages();
            final boolean hasPackages = !detected.installedPackages.isEmpty() || !detected.notInstalledPackages.isEmpty();
            boolean hasHistoryPatterns = hookInit.getInterceptPatterns() != null && !hookInit.getInterceptPatterns().isEmpty();

            ReaLog.log("exit_intercept", "检测到退出调用: " + exitMethod);

            if (!hasPackages && !hasHistoryPatterns) {
                final Activity activity = hookInit.getCurrentActivity();
                if (activity == null || activity.isFinishing()) return;
                if (isDialogShowing) return;
                isDialogShowing = true;
                String message = "当前应用没有检测到应用包，也没有历史拦截记录。<br><br>但您已开启「退出拦截」功能。<br><br>是否仍然阻止本次退出？";
                AlertDialog dialog = DialogUi.createBoundedDialog(activity, "拦截提醒", message,
                        new String[]{"阻止退出", "不拦截", "处理列表"},
                        new DialogInterface.OnClickListener[]{
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        ToastUtil.showUnique(activity, "已拦截退出，应用继续运行");
                                        ReaLog.log("exit_intercept", "用户选择阻止退出(无包)");
                                        d.dismiss();
                                        isDialogShowing = false;
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        d.dismiss();
                                        HookInit.setBypassExitHook(true);
                                        try {
                                            if (exitMethod.contains("System.exit")) {
                                                System.exit((int) param.args[0]);
                                            } else if (exitMethod.contains("Process.killProcess")) {
                                                android.os.Process.killProcess((int) param.args[0]);
                                            } else if (exitMethod.contains("Runtime.exit")) {
                                                Runtime.getRuntime().exit((int) param.args[0]);
                                            }
                                        } finally {
                                            HookInit.setBypassExitHook(false);
                                        }
                                        ReaLog.log("exit_intercept", "用户选择放行退出(无包)");
                                        isDialogShowing = false;
                                    }
                                },
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d, int which) {
                                        d.dismiss();
                                        isDialogShowing = false;
                                        ReaLog.log("exit_intercept", "用户打开处理列表");
                                        showManagePatternsDialog(activity, hookInit);
                                    }
                                }
                        }
                );
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface d) {
                        isDialogShowing = false;
                    }
                });
                dialog.show();
                param.setResult(null);
                param.setThrowable(null);
                return;
            }

            HookInit.InterceptPattern latestSilentPattern = null;
            long latestTime = 0;
            if (!hasPackages && hasHistoryPatterns) {
                for (HookInit.InterceptPattern pattern : hookInit.getInterceptPatterns()) {
                    if (pattern.silentIntercept && pattern.lastDetectedTime > latestTime) {
                        latestTime = pattern.lastDetectedTime;
                        latestSilentPattern = pattern;
                    }
                }
            }

            if (hasPackages) {
                String silentAction = hookInit.getSilentAction(detected);
                if (silentAction != null) {
                    if ("intercept".equals(silentAction)) {
                        param.setResult(null);
                        param.setThrowable(null);
                        ReaLog.log("exit_intercept", "静默拦截退出 (包数: " + detected.installedPackages.size() + ")");
                        hookInit.showSilentInterceptToast(detected);
                        return;
                    } else if ("allow".equals(silentAction)) {
                        final Activity activity = hookInit.getCurrentActivity();
                        if (activity != null && !activity.isFinishing()) {
                            ToastUtil.showUnique(activity, "已自动放行退出\n(基于3次历史选择)");
                        }
                        ReaLog.log("exit_intercept", "静默放行退出 (包数: " + detected.installedPackages.size() + ")");
                        HookInit.setBypassExitHook(true);
                        try {
                            if (exitMethod.contains("System.exit")) {
                                System.exit((int) param.args[0]);
                            } else if (exitMethod.contains("Process.killProcess")) {
                                android.os.Process.killProcess((int) param.args[0]);
                            } else if (exitMethod.contains("Runtime.exit")) {
                                Runtime.getRuntime().exit((int) param.args[0]);
                            }
                        } finally {
                            HookInit.setBypassExitHook(false);
                        }
                        param.setResult(null);
                        param.setThrowable(null);
                        return;
                    }
                }
            } else if (latestSilentPattern != null) {
                String silentChoice = latestSilentPattern.userChoice;
                int totalPackages = latestSilentPattern.installedPackages.size() + latestSilentPattern.notInstalledPackages.size();
                String packageSample;
                if (totalPackages > 0) {
                    List<String> allPkgs = new ArrayList<String>();
                    allPkgs.addAll(latestSilentPattern.installedPackages);
                    allPkgs.addAll(latestSilentPattern.notInstalledPackages);
                    if (totalPackages <= 3) {
                        packageSample = join(", ", allPkgs);
                    } else {
                        packageSample = allPkgs.get(0) + " 等" + totalPackages + "个应用";
                    }
                } else {
                    packageSample = "历史应用";
                }

                if ("intercept".equals(silentChoice)) {
                    param.setResult(null);
                    param.setThrowable(null);
                    ReaLog.log("exit_intercept", "静默拦截退出 (历史: " + packageSample + ")");
                    ToastUtil.showUnique("已自动拦截退出(基于3次历史选择)\n检测到:" + packageSample);
                    return;
                } else if ("allow".equals(silentChoice)) {
                    ReaLog.log("exit_intercept", "静默放行退出 (历史: " + packageSample + ")");
                    ToastUtil.showUnique("已自动放行退出(基于3次历史选择)\n检测到:" + packageSample);
                    HookInit.setBypassExitHook(true);
                    try {
                        if (exitMethod.contains("System.exit")) {
                            System.exit((int) param.args[0]);
                        } else if (exitMethod.contains("Process.killProcess")) {
                            android.os.Process.killProcess((int) param.args[0]);
                        } else if (exitMethod.contains("Runtime.exit")) {
                            Runtime.getRuntime().exit((int) param.args[0]);
                        }
                    } finally {
                        HookInit.setBypassExitHook(false);
                    }
                    param.setResult(null);
                    param.setThrowable(null);
                    return;
                }
            }

            final Activity activity = hookInit.getCurrentActivity();
            if (activity == null || activity.isFinishing()) return;
            if (isDialogShowing) return;
            isDialogShowing = true;

            StringBuilder message = buildDetectedMessage(detected);
            if (!hasPackages && hasHistoryPatterns) {
                message = new StringBuilder("当前应用没有检测到新的包名，但存在历史拦截记录。<br><br>");
                int interceptCount = 0, allowCount = 0;
                for (HookInit.InterceptPattern pattern : hookInit.getInterceptPatterns()) {
                    if ("intercept".equals(pattern.userChoice)) interceptCount++;
                    else if ("allow".equals(pattern.userChoice)) allowCount++;
                }
                if (interceptCount > 0)
                    message.append("历史选择拦截 ").append(interceptCount).append(" 次，");
                if (allowCount > 0) message.append("历史选择放行 ").append(allowCount).append(" 次。");
                message.append("<br><br>");
            }
            message.append("<br><font color='#FF5722'><b>想要结束退出应用/页面</b></font><br>请选择是否需要退出？");

            final HookInit.InterceptPattern targetPatternForHistory = (!hasPackages && hasHistoryPatterns) ? latestSilentPattern : null;

            AlertDialog dialog = DialogUi.createBoundedDialog(activity, "拦截提醒", message.toString(),
                    new String[]{"阻止退出", "不拦截", "处理列表"},
                    new DialogInterface.OnClickListener[]{
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    if (hasPackages) {
                                        hookInit.handleInterceptChoice(detected, "intercept", true);
                                        hookInit.saveConfigImmediate();
                                    } else if (targetPatternForHistory != null) {
                                        recordPatternChoice(targetPatternForHistory, "intercept");
                                        hookInit.saveConfigImmediate();
                                        ToastUtil.showUnique(activity, "已拦截退出（历史模式计数+1）");
                                    } else {
                                        ToastUtil.showUnique(activity, "已拦截退出（无当前检测包）");
                                    }
                                    ReaLog.log("exit_intercept", "用户选择阻止退出");
                                    d.dismiss();
                                    ToastUtil.showUnique(activity, "已拦截退出，应用继续运行");
                                    isDialogShowing = false;
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    if (hasPackages) {
                                        hookInit.handleInterceptChoice(detected, "allow", false);
                                        hookInit.saveConfigImmediate();
                                    } else if (targetPatternForHistory != null) {
                                        recordPatternChoice(targetPatternForHistory, "allow");
                                        hookInit.saveConfigImmediate();
                                        ToastUtil.showUnique(activity, "已允许本次退出（历史模式计数+1）");
                                    } else {
                                        ToastUtil.showUnique(activity, "已允许本次退出（无当前检测包）");
                                    }
                                    ReaLog.log("exit_intercept", "用户选择放行退出");
                                    d.dismiss();
                                    HookInit.setBypassExitHook(true);
                                    try {
                                        if (exitMethod.contains("System.exit")) {
                                            System.exit((int) param.args[0]);
                                        } else if (exitMethod.contains("Process.killProcess")) {
                                            android.os.Process.killProcess((int) param.args[0]);
                                        } else if (exitMethod.contains("Runtime.exit")) {
                                            Runtime.getRuntime().exit((int) param.args[0]);
                                        }
                                    } finally {
                                        HookInit.setBypassExitHook(false);
                                    }
                                    isDialogShowing = false;
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int which) {
                                    d.dismiss();
                                    isDialogShowing = false;
                                    ReaLog.log("system", "用户打开退出处理列表");
                                    showManagePatternsDialog(activity, hookInit);
                                }
                            }
                    }
            );
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface d) {
                    isDialogShowing = false;
                }
            });
            dialog.show();
            param.setResult(null);
            param.setThrowable(null);
        } catch (Throwable t) {
            ReaLog.log("exit_intercept", "处理应用程序退出异常: " + t.getMessage());
            isDialogShowing = false;
        }
    }

    private static StringBuilder buildDetectedMessage(HookInit.DetectedPackages detected) {
        StringBuilder message = new StringBuilder();
        if (detected.patternHash != null && !detected.patternHash.isEmpty()) {
            message.append("拦截哈希值：<font color='#F44336'><b>").append(detected.patternHash).append("</b></font><br><br>");
        }
        if (!detected.installedPackages.isEmpty() && detected.notInstalledPackages.isEmpty()) {
            message.append("当前应用检测到你<font color='#4CAF50'>已安装:</font><br>");
            for (String pkg : detected.installedPackages)
                message.append("• ").append(pkg).append("<br>");
        } else if (detected.installedPackages.isEmpty() && !detected.notInstalledPackages.isEmpty()) {
            message.append("当前应用检测到你<font color='#F44336'>未安装:</font><br>");
            for (String pkg : detected.notInstalledPackages)
                message.append("• ").append(pkg).append("<br>");
        } else {
            message.append("当前应用检测到你：<br><br>");
            if (!detected.installedPackages.isEmpty()) {
                message.append("<font color='#4CAF50'>【已安装】</font><br>");
                for (String pkg : detected.installedPackages)
                    message.append("• ").append(pkg).append("<br>");
                message.append("<br>");
            }
            if (!detected.notInstalledPackages.isEmpty()) {
                message.append("<font color='#F44336'>【未安装】</font><br>");
                for (String pkg : detected.notInstalledPackages)
                    message.append("• ").append(pkg).append("<br>");
            }
        }
        return message;
    }

    public static void handleActivityFinish(final HookInit hookInit,
            final Activity activity,
            final XC_MethodHook.MethodHookParam param) {
        if (activity == null || activity.isFinishing()) return;
        if (hookInit.isBackPressFinish(activity)) return;
        final String targetApp = hookInit.getCurrentTargetApp();
        boolean blockExit = hookInit.blockExitMap.getOrDefault(targetApp, false);
        boolean superBlock = hookInit.superBlockExitMap.getOrDefault(targetApp, false);
        if (!blockExit && !superBlock) return;
        if (blockExit && !superBlock && !hookInit.isMainActivity(activity)) return;
        // 立即拦截原 finish（在当前线程同步执行）
        param.setResult(null);
        param.setThrowable(null);
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final HookInit.DetectedPackages detected = hookInit.analyzeDetectedPackages();
                    final boolean hasPackages = !detected.installedPackages.isEmpty() || !detected.notInstalledPackages.isEmpty();
                    boolean hasHistoryPatterns = hookInit.getInterceptPatterns() != null && !hookInit.getInterceptPatterns().isEmpty();
                    ReaLog.log("exit_intercept", "检测到页面关闭请求");
                    if (!hasPackages && !hasHistoryPatterns) {
                        if (isDialogShowing) return;
                        isDialogShowing = true;
                        String message = "当前应用没有检测到应用包，也没有历史拦截记录。<br><br>但您已开启「退出拦截」功能。<br><br>是否仍然阻止本次退出？";
                        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "拦截提醒", message,
                                new String[]{"阻止退出", "不拦截", "处理列表"},
                                new DialogInterface.OnClickListener[]{
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int which) {
                                                ToastUtil.show(activity, "已拦截页面关闭，应用继续运行");
                                                ReaLog.log("exit_intercept", "用户选择阻止页面关闭(无包)");
                                                d.dismiss();
                                                isDialogShowing = false;
                                            }
                                        },
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int which) {
                                                d.dismiss();
                                                HookInit.setBypassExitHook(true);
                                                try {
                                                    hookInit.addPendingFinishActivity(activity);
                                                    activity.finish();
                                                } finally {
                                                    HookInit.setBypassExitHook(false);
                                                }
                                                ReaLog.log("exit_intercept", "用户放行页面关闭(无包)");
                                                isDialogShowing = false;
                                            }
                                        },
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface d, int which) {
                                                d.dismiss();
                                                isDialogShowing = false;
                                                ReaLog.log("system", "用户打开拦截处理列表(页面关闭)");
                                                showManagePatternsDialog(activity, hookInit);
                                            }
                                        }
                                }
                        );
                        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface d) {
                                isDialogShowing = false;
                            }
                        });
                        dialog.show();
                        param.setResult(null);
                        return;
                    }

                    HookInit.InterceptPattern latestSilentPattern = null;
                    long latestTime = 0;
                    if (!hasPackages && hasHistoryPatterns) {
                        for (HookInit.InterceptPattern pattern : hookInit.getInterceptPatterns()) {
                            if (pattern.silentIntercept && pattern.lastDetectedTime > latestTime) {
                                latestTime = pattern.lastDetectedTime;
                                latestSilentPattern = pattern;
                            }
                        }
                    }

                    if (hasPackages) {
                        String silentAction = hookInit.getSilentAction(detected);
                        if (silentAction != null) {
                            if ("intercept".equals(silentAction)) {
                                param.setResult(null);
                                ReaLog.log("exit_intercept", "静默拦截页面关闭 (包数: " + detected.installedPackages.size() + ")");
                                hookInit.showSilentInterceptToast(detected);
                                return;
                            } else if ("allow".equals(silentAction)) {
                                if (activity != null && !activity.isFinishing()) {
                                    ToastUtil.show(activity, "已自动放行页面关闭\n(基于3次历史选择)");
                                }
                                ReaLog.log("exit_intercept", "静默放行页面关闭 (包数: " + detected.installedPackages.size() + ")");
                                HookInit.setBypassExitHook(true);
                                try {
                                    hookInit.addPendingFinishActivity(activity);
                                    activity.finish();
                                } finally {
                                    HookInit.setBypassExitHook(false);
                                }
                                param.setResult(null);
                                return;
                            }
                        }
                    } else if (latestSilentPattern != null) {
                        String silentChoice = latestSilentPattern.userChoice;
                        int totalPackages = latestSilentPattern.installedPackages.size() + latestSilentPattern.notInstalledPackages.size();
                        String packageSample;
                        if (totalPackages > 0) {
                            List<String> allPkgs = new ArrayList<String>();
                            allPkgs.addAll(latestSilentPattern.installedPackages);
                            allPkgs.addAll(latestSilentPattern.notInstalledPackages);
                            if (totalPackages <= 3) {
                                packageSample = join(", ", allPkgs);
                            } else {
                                packageSample = allPkgs.get(0) + " 等" + totalPackages + "个应用";
                            }
                        } else {
                            packageSample = "历史应用";
                        }

                        if ("intercept".equals(silentChoice)) {
                            param.setResult(null);
                            ReaLog.log("exit_intercept", "静默拦截页面关闭 (历史: " + packageSample + ")");
                            ToastUtil.showUnique("已自动拦截页面关闭(基于3次历史选择)\n检测到:" + packageSample);
                            return;
                        } else if ("allow".equals(silentChoice)) {
                            ReaLog.log("exit_intercept", "静默放行页面关闭 (历史: " + packageSample + ")");
                            ToastUtil.showUnique("已自动放行页面关闭(基于3次历史选择)\n检测到:" + packageSample);
                            HookInit.setBypassExitHook(true);
                            try {
                                hookInit.addPendingFinishActivity(activity);
                                activity.finish();
                            } finally {
                                HookInit.setBypassExitHook(false);
                            }
                            param.setResult(null);
                            return;
                        }
                    }

                    if (isDialogShowing) return;
                    isDialogShowing = true;

                    StringBuilder message = buildDetectedMessage(detected);
                    if (!hasPackages && hasHistoryPatterns) {
                        message = new StringBuilder("当前应用没有检测到新的包名，但存在历史拦截记录。<br><br>");
                        int interceptCount = 0, allowCount = 0;
                        for (HookInit.InterceptPattern pattern : hookInit.getInterceptPatterns()) {
                            if ("intercept".equals(pattern.userChoice)) interceptCount++;
                            else if ("allow".equals(pattern.userChoice)) allowCount++;
                        }
                        if (interceptCount > 0)
                            message.append("历史选择拦截 ").append(interceptCount).append(" 次，");
                        if (allowCount > 0)
                            message.append("历史选择放行 ").append(allowCount).append(" 次。");
                        message.append("<br><br>");
                    }
                    message.append("<br><font color='#FF5722'><b>想要结束退出应用/页面</b></font><br>请选择是否需要退出？");

                    final HookInit.InterceptPattern targetPatternForHistory = (!hasPackages && hasHistoryPatterns) ? latestSilentPattern : null;

                    AlertDialog dialog = DialogUi.createBoundedDialog(activity, "拦截提醒", message.toString(),
                            new String[]{"阻止退出", "不拦截", "处理列表"},
                            new DialogInterface.OnClickListener[]{
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            if (hasPackages) {
                                                hookInit.handleInterceptChoice(detected, "intercept", true);
                                                hookInit.saveConfigImmediate();
                                            } else if (targetPatternForHistory != null) {
                                                recordPatternChoice(targetPatternForHistory, "intercept");
                                                hookInit.saveConfigImmediate();
                                                ToastUtil.show(activity, "已拦截退出（历史模式计数+1）");
                                            } else {
                                                ToastUtil.show(activity, "已拦截退出（无当前检测包）");
                                            }
                                            ReaLog.log("exit_intercept", "用户选择阻止页面关闭");
                                            d.dismiss();
                                            ToastUtil.show(activity, "已拦截退出，应用继续运行");
                                            isDialogShowing = false;
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            if (hasPackages) {
                                                hookInit.handleInterceptChoice(detected, "allow", false);
                                                hookInit.saveConfigImmediate();
                                            } else if (targetPatternForHistory != null) {
                                                recordPatternChoice(targetPatternForHistory, "allow");
                                                hookInit.saveConfigImmediate();
                                                ToastUtil.show(activity, "已允许本次退出（历史模式计数+1）");
                                            } else {
                                                ToastUtil.show(activity, "已允许本次退出（无当前检测包）");
                                            }
                                            ReaLog.log("exit_intercept", "用户选择放行页面关闭");
                                            d.dismiss();
                                            HookInit.setBypassExitHook(true);
                                            try {
                                                hookInit.addPendingFinishActivity(activity);
                                                activity.finish();
                                            } finally {
                                                HookInit.setBypassExitHook(false);
                                            }
                                            isDialogShowing = false;
                                        }
                                    },
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface d, int which) {
                                            d.dismiss();
                                            isDialogShowing = false;
                                            ReaLog.log("system", "用户打开退出处理列表(页面关闭)");
                                            showManagePatternsDialog(activity, hookInit);
                                        }
                                    }
                            }
                    );
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface d) {
                            isDialogShowing = false;
                        }
                    });
                    dialog.show();
                    param.setResult(null);
                } catch (Throwable t) {
                    ReaLog.log("exit_intercept", "处理页面关闭异常: " + t.getMessage());
                    isDialogShowing = false;
                }
            }
        });
    }

}

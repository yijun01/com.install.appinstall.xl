package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 更新检查工具类（最终版 + 版本信息头部含更新时间 + 发布页置底）
 * 添加手动检查防频繁点击（10秒限制）和自动检测频率限制（10分钟）
 */
public class Update {

    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/yijun01/com.install.appinstall.xl/releases/latest";
    private static final String MODULE_PACKAGE = "com.install.appinstall.xl";
    private static final String MODULE_MAIN_ACTIVITY = MODULE_PACKAGE + ".MainActivity";
    private static final String GITHUB_RELEASES_URL = "https://github.com/yijun01/com.install.appinstall.xl/releases";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    // ========== 新增：频率限制常量 ==========
    private static final long MANUAL_INTERVAL = 10000; // 手动点击间隔 10 秒
    private static final long AUTO_INTERVAL = 600000; // 自动检查间隔10分钟
    private static long lastManualCheck = 0; // 上次手动检查时间（内存）
    private static final String PREF_LAST_AUTO_CHECK = "last_auto_check_time"; // SharedPreferences 键

    /**
     * 手动检查更新（按钮点击调用）
     */
    public static void checkForUpdate(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        long now = System.currentTimeMillis();
        if (now - lastManualCheck < MANUAL_INTERVAL) {
            ToastUtil.show(activity, "操作太频繁，请稍后再试");
            return;
        }
        lastManualCheck = now;

        performUpdateCheck(activity);
    }

    /**
     * 自动检查更新（如 onCreate 中调用），带持久化频率限制
     */
    public static void checkForUpdateAuto(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        SharedPreferences prefs = activity.getSharedPreferences("update_prefs", Context.MODE_PRIVATE);
        long lastAuto = prefs.getLong(PREF_LAST_AUTO_CHECK, 0);
        long now = System.currentTimeMillis();
        if (now - lastAuto < AUTO_INTERVAL) {
            return; // 未超过间隔，不执行检查
        }
        prefs.edit().putLong(PREF_LAST_AUTO_CHECK, now).apply();

        performUpdateCheck(activity);
    }

    /**
     * 实际的更新检查逻辑（从原 checkForUpdate 抽取）
     */
    private static void performUpdateCheck(Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        try {
            boolean isEmbedded = !MODULE_PACKAGE.equals(activity.getPackageName()) && isMainActivityExists(activity);
            if (isEmbedded) {
                doUpdateCheck(activity);
                return;
            }

            boolean isInModuleMain = MODULE_MAIN_ACTIVITY.equals(activity.getClass().getName());
            if (!isInModuleMain) {
                showInTargetAppDialog(activity);
                return;
            }

            doUpdateCheck(activity);
        } catch (Throwable t) {
            ToastUtil.show(activity, "检查更新异常: " + t.getMessage());
        }
    }

    // ========== 以下为原 checkForUpdate 中的辅助方法，无变化 ==========

    private static boolean isMainActivityExists(Activity activity) {
        try {
            PackageManager pm = activity.getPackageManager();
            Intent intent = new Intent();
            intent.setClassName(activity.getPackageName(), MODULE_MAIN_ACTIVITY);
            List<android.content.pm.ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            return list != null && !list.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // 执行网络检查（原 checkForUpdate 的主体部分）
    private static void doUpdateCheck(Activity activity) {
        if (!isNetworkAvailable(activity)) {
            ToastUtil.show(activity, "网络未连接，请检查后重试");
            return;
        }

        ToastUtil.show(activity, "正在检查更新...");

        final WeakReference<Activity> weakRef = new WeakReference<>(activity);
        EXECUTOR.execute(new Runnable() {
                @Override
                public void run() {
                    final Activity act = weakRef.get();
                    if (act == null || act.isFinishing()) return;

                    try {
                        final int currentVersionCode;
                        try {
                            currentVersionCode = act.getPackageManager()
                                .getPackageInfo(act.getPackageName(), 0).versionCode;
                        } catch (PackageManager.NameNotFoundException e) {
                            showToast(weakRef, "获取模块版本失败");
                            return;
                        }

                        fetchLatestRelease(new ReleaseCallback() {
                                @Override
                                public void onSuccess(final ReleaseInfo release) {
                                    Activity a = weakRef.get();
                                    if (a == null || a.isFinishing()) return;

                                    int remoteVersion = extractFirstNumber(release.tagName);
                                    if (remoteVersion > currentVersionCode) {
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Activity a = weakRef.get();
                                                    if (a != null && !a.isFinishing()) {
                                                        showUpdateDialog(weakRef, release);
                                                    }
                                                }
                                            });
                                    } else {
                                        showToast(weakRef, "当前已是最新版本");
                                    }
                                }

                                @Override
                                public void onRateLimitExceeded(long resetTimeMillis) {
                                    String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                        .format(new Date(resetTimeMillis));
                                    showToast(weakRef, "API 访问超限\n请在 " + timeStr + " 后重试");
                                }

                                @Override
                                public void onFailure(String errorMsg) {
                                    showToast(weakRef, "检查更新失败: " + errorMsg);
                                }
                            });

                    } catch (Exception e) {
                        e.printStackTrace();
                        showToast(weakRef, "检查更新异常: " + e.getMessage());
                    }
                }
            });
    }

    private static void showToast(final WeakReference<Activity> weakRef, final String msg) {
        final Activity a = weakRef.get();
        if (a == null || a.isFinishing()) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ToastUtil.show(a, msg);
                }
            });
    }

    // ----------------------------------------------------------------------
    // 数据获取（增加 updated_at）
    // ----------------------------------------------------------------------

    private interface ReleaseCallback {
        void onSuccess(ReleaseInfo release);
        void onRateLimitExceeded(long resetTimeMillis);
        void onFailure(String errorMsg);
    }

    private static class ReleaseInfo {
        String tagName;
        String name;
        String body;
        String releaseUrl;
        String downloadUrl;
        String updatedAt; // 新增更新时间字段

        ReleaseInfo(String tagName, String name, String body, String releaseUrl, String downloadUrl, String updatedAt) {
            this.tagName = tagName;
            this.name = name;
            this.body = body;
            this.releaseUrl = releaseUrl;
            this.downloadUrl = downloadUrl;
            this.updatedAt = updatedAt;
        }
    }

    private static void fetchLatestRelease(final ReleaseCallback callback) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(GITHUB_API_LATEST);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");

            int responseCode = connection.getResponseCode();

            if (responseCode == 403) {
                String limitRemaining = connection.getHeaderField("X-RateLimit-Remaining");
                if ("0".equals(limitRemaining)) {
                    String resetHeader = connection.getHeaderField("X-RateLimit-Reset");
                    if (resetHeader != null) {
                        long resetEpoch = Long.parseLong(resetHeader) * 1000L;
                        callback.onRateLimitExceeded(resetEpoch);
                    } else {
                        callback.onFailure("API已达上限，请稍后再试");
                    }
                    return;
                }
            }

            if (responseCode != 200) {
                callback.onFailure("服务器响应码: " + responseCode);
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject rel = new JSONObject(response.toString());

            String tagName = rel.optString("tag_name", "");
            String name = rel.optString("name", "");
            String body = rel.optString("body", "");
            String releaseUrl = rel.optString("html_url", "");
            String updatedAt = rel.optString("updated_at", ""); // 获取更新时间

            String downloadUrl = releaseUrl;
            if (rel.has("assets")) {
                JSONArray assets = rel.getJSONArray("assets");
                if (assets.length() > 0) {
                    JSONObject firstAsset = assets.getJSONObject(0);
                    if (firstAsset.has("browser_download_url")) {
                        downloadUrl = firstAsset.getString("browser_download_url");
                    }
                }
            }

            ReleaseInfo info = new ReleaseInfo(tagName, name, body, releaseUrl, downloadUrl, updatedAt);
            callback.onSuccess(info);

        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure("网络错误: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static int extractFirstNumber(String text) {
        if (text == null) return 0;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static boolean isNetworkAvailable(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
        } catch (SecurityException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ----------------------------------------------------------------------
    // Markdown 转 HTML（任意 [标记] 蓝色加粗）
    // ----------------------------------------------------------------------

    private static Spanned renderMarkdownAsHtml(String markdown) {
        if (markdown == null) return fromHtml("");

        String html = markdown;

        // 处理任何方括号标记 [xxx] -> 蓝色加粗
        html = html.replaceAll("(?i)\\[[^\\]]+\\]", "<b><font color='#2196F3'>$0</font></b>");

        // 标题
        html = html.replaceAll("(?m)^(#{1,6})\\s+(.+)$", "<h$1>$2</h$1>");

        // 粗体
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<b>$1</b>");
        html = html.replaceAll("__(.+?)__", "<b>$1</b>");

        // 斜体
        html = html.replaceAll("\\*(.+?)\\*", "<i>$1</i>");
        html = html.replaceAll("_(.+?)_", "<i>$1</i>");

        // 删除线
        html = html.replaceAll("~~(.+?)~~", "<strike>$1</strike>");

        // 行内代码
        html = html.replaceAll("`(.+?)`", "<code>$1</code>");

        // 无序列表
        String[] lines = html.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean inList = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^[-*+]\\s+.*")) {
                if (!inList) {
                    sb.append("<ul>");
                    inList = true;
                }
                String item = trimmed.replaceFirst("^[-*+]\\s+", "");
                sb.append("<li>").append(item).append("</li>");
            } else {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append(line).append("\n");
            }
        }
        if (inList) {
            sb.append("</ul>");
        }
        html = sb.toString();

        // 引用
        html = html.replaceAll("(?m)^>\\s*(.+)$", "<blockquote>$1</blockquote>");

        // 换行
        html = html.replaceAll("\n{2,}", "<br/><br/>");
        html = html.replaceAll("\n", "<br/>");

        return fromHtml(html);
    }

    // ----------------------------------------------------------------------
    // 对话框展示（头部含更新时间 + 发布页置底）
    // ----------------------------------------------------------------------

    private static void showInTargetAppDialog(final Activity activity) {
        String htmlMessage = "<font color='#FF5722'><b>请在模块主页中检查更新</b></font><br><br>" +
            "您当前在目标应用内，无法直接进行版本检测。<br>" +
            "您可以选择：<br>" +
            "<font color='#9E9E9E'><b>• 打开模块主页进行完整检测</b></font><br>" +
            "<font color='#9E9E9E'><b>• 访问GitHub Releases页面查看最新版本</b></font><br><br>" +
            "<font color='#2196F3'><b>更新链接：</b></font><br>" +
            "<a href=\"" + GITHUB_RELEASES_URL + "\">" + GITHUB_RELEASES_URL + "</a><br><br>" +
            "<font color='#9E9E9E'><small>请选择操作：(链接可长按复制)</small></font>";

        Spanned message = fromHtml(htmlMessage);

        DialogInterface.OnClickListener positive = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                tryLaunchModuleMainActivity(activity);
            }
        };

        DialogInterface.OnClickListener negative = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
                activity.startActivity(intent);
            }
        };

        showDialog(activity, "检测提示", message, "打开模块主页", positive, "打开链接", negative, "取消", null);
    }

    private static void showUpdateDialog(final WeakReference<Activity> weakRef, final ReleaseInfo release) {
        final Activity activity = weakRef.get();
        if (activity == null || activity.isFinishing()) return;

        // 格式化更新时间（例如取日期部分）
        String updatedDisplay = release.updatedAt;
        if (updatedDisplay != null && updatedDisplay.length() >= 10) {
            updatedDisplay = updatedDisplay.substring(0, 10); // 只取 yyyy-MM-dd
        }

        // 头部信息：tag、name、更新时间
        String headerHtml = "<b>最新版本：</b>" + release.tagName + "<br/>" +
            "<b>版本名称：</b>" + release.name + "<br/>" +
            "<b>更新时间：</b>" + updatedDisplay + "<br/>" +
            "<b>更新内容：</b><br/><br/>";

        // 渲染更新内容
        Spanned bodySpanned = renderMarkdownAsHtml(release.body);

        // 底部发布页面链接
        String footerHtml = "<br/><b>发布页面：</b><br/><a href=\"" + release.releaseUrl + "\">" + release.releaseUrl + "</a>";

        // 合并头部、body、底部
        Spanned headerSpanned = fromHtml(headerHtml);
        Spanned footerSpanned = fromHtml(footerHtml);
        Spanned finalMessage = (Spanned) new android.text.SpannableStringBuilder(headerSpanned)
            .append(bodySpanned)
            .append(footerSpanned);

        String positiveText = release.downloadUrl.equals(release.releaseUrl) ? "获取新版本" : "下载 APK";
        DialogInterface.OnClickListener positiveListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (isSafeUrl(release.downloadUrl)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl));
                    activity.startActivity(intent);
                    ToastUtil.show(activity, "获取新版本");
                } else {
                    ToastUtil.show(activity, "获取链接失败，请手动访问发布页");
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(release.releaseUrl));
                    activity.startActivity(intent);
                }
            }
        };

        DialogInterface.OnClickListener negativeListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ToastUtil.show(activity, "取消更新");
            }
        };

        showDialog(activity, "检测到新版本", finalMessage,
                   positiveText, positiveListener,
                   "暂不更新", negativeListener,
                   null, null);
    }

    private static void showDialog(final Activity activity,
                                   final String title,
                                   final Spanned message,
                                   final String positiveText,
                                   final DialogInterface.OnClickListener positiveListener,
                                   final String negativeText,
                                   final DialogInterface.OnClickListener negativeListener,
                                   final String neutralText,
                                   final DialogInterface.OnClickListener neutralListener) {
        if (activity == null || activity.isFinishing()) return;

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
                                       ViewGroup.LayoutParams.MATCH_PARENT,
                                       ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.setFillViewport(true);

        TextView textView = new TextView(activity);
        textView.setText(message);
        textView.setTextSize(14);
        textView.setPadding(40, 20, 40, 20);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextIsSelectable(true);
        scrollView.addView(textView);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, AlertDialog.THEME_DEVICE_DEFAULT_LIGHT)
            .setTitle(title)
            .setView(scrollView);

        if (positiveText != null) builder.setPositiveButton(positiveText, positiveListener);
        if (negativeText != null) builder.setNegativeButton(negativeText, negativeListener);
        if (neutralText != null) builder.setNeutralButton(neutralText, neutralListener);

        final AlertDialog dialog = builder.create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                @Override
                public void onShow(DialogInterface dialogInterface) {
                    try {
                        Window window = dialog.getWindow();
                        if (window != null) {
                            WindowManager.LayoutParams params = window.getAttributes();
                            params.width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.9);
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                            params.gravity = Gravity.CENTER;
                            window.setAttributes(params);
                        }
                    } catch (Exception ignored) {}
                }
            });

        if (Looper.myLooper() == Looper.getMainLooper()) {
            dialog.show();
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.show();
                    }
                });
        }
    }

    private static Spanned fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(html);
        }
    }

    private static boolean isSafeUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        return "https".equalsIgnoreCase(uri.getScheme()) &&
            (uri.getHost() != null && uri.getHost().endsWith("github.com"));
    }

    private static boolean tryLaunchModuleMainActivity(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        try {
            Intent launchIntent = pm.getLaunchIntentForPackage(MODULE_PACKAGE);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launchIntent);
                return true;
            } else {
                ToastUtil.show(activity, "❌启动失败,请检查是否安装");
                return false;
            }
        } catch (Exception e) {
            ToastUtil.show(activity, "❌启动失败,请检查是否安装");
            return false;
        }
    }
}

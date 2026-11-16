package com.install.appinstall.xl.util;

import android.app.Activity;
import android.app.AlertDialog;
import com.install.appinstall.xl.ui.PageStack;
import com.install.appinstall.xl.ui.UiComponents;
import com.install.appinstall.xl.ui.UiGuard;
import com.install.appinstall.xl.ui.UiKit;
import com.install.appinstall.xl.ui.UiTabs;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONException;
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

/** 模块版本更新检查 */

public class Update {
    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/yijun01/com.install.appinstall.xl/releases/latest";
    private static final String MODULE_PACKAGE = "com.install.appinstall.xl";
    private static final String MODULE_MAIN_ACTIVITY = MODULE_PACKAGE + ".MainHome";
    public static final String GITHUB_RELEASES_URL = "https://github.com/yijun01/com.install.appinstall.xl/releases";
    // 备用更新通道：GitHub 不可达时用
    public static final String UPDATE_URL_2 = "https://yijun01.lanzouu.com/b0pndjjid";
    public static final String UPDATE_PASSWORD = "ddpa";
    // 更新页标题：title() 与 sheetHeader 两处共用
    private static final String UPDATE_TITLE = "发现新版本";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    // ========== 频率限制常量 ==========
    private static final long MANUAL_INTERVAL = 3000; // 3秒：普通GitHub用户 API 限流（官方未认证拉黑 60分钟）
    private static final long UPDATE_LINK_THROTTLE_MS = 800L; // 0.8秒 更新页按钮防连点防双击
    private static long lastManualCheck = 0;

    // ========== 本地提前限流 ==========
    public static void checkForUpdate(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        long now = System.currentTimeMillis();
        if (now - lastManualCheck < MANUAL_INTERVAL) {
            ToastUtil.showUnique(activity, "操作太频繁，请稍后再试");
            ReaLog.log("update", "操作太频繁，请稍后再试");
            return;
        }
        lastManualCheck = now;
        performUpdateCheck(activity);
    }

    // 检测版本，宿主端一律引导去模块主页。
    private static void performUpdateCheck(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        try {
            boolean inModule = MODULE_PACKAGE.equals(activity.getPackageName())
                    || MODULE_MAIN_ACTIVITY.equals(activity.getClass().getName());
            if (!inModule) {
                showInTargetAppDialog(activity);
                return;
            }
            doUpdateCheck(activity);
        } catch (Throwable t) {
            ToastUtil.showUnique(activity, "检查更新异常: " + t.getMessage());
            ReaLog.log("update", "检查更新异常: " + t.getMessage());
        }
    }

    private static void doUpdateCheck(Activity activity) {
        if (!isNetworkAvailable(activity)) {
            ToastUtil.showUnique(activity, "网络未连接，请检查后重试");
            return;
        }
        ToastUtil.showUnique(activity, "正在检查更新...\n请稍后……");
        ReaLog.log("update", "正在检查更新...");
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
                        ToastUtil.showUnique(weakRef.get(), "获取模块版本失败");
                        ReaLog.log("update", "获取模块版本失败");
                        return;
                    }
                    fetchLatestRelease(new ReleaseCallback() {
                        @Override
                        public void onSuccess(final ReleaseInfo release) {
                            Activity a = weakRef.get();
                            if (a == null || a.isFinishing()) return;
                            int remoteVersion = extractFirstNumber(release.tagName);
                            if (remoteVersion > currentVersionCode) {
                                ReaLog.log("update", "发现新版本: " + release.tagName);
                                ToastUtil.showUnique(weakRef.get(), release.linkFallback ? "发现新版本-蓝奏云网盘" : "发现新版本");
                                More.runOnMainThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Activity a = weakRef.get();
                                        if (a == null || a.isFinishing()) return;
                                        PageStack stack = PageStack.get();
                                        if (stack != null && stack.isOpen()) {
                                            stack.push(new UpdateSheetPage(release));
                                        } else {
                                            ReaLog.log("update", "页面栈未就绪，更新半屏未弹出");
                                        }
                                    }
                                });
                            } else {
                                ReaLog.log("update", "当前已是最新版本");
                                ToastUtil.showUnique(weakRef.get(), "当前已是最新版本");
                            }
                        }

                        @Override
                        public void onRateLimitExceeded(long resetTimeMillis) {
                            String timeStr = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(new Date(resetTimeMillis));
                            ReaLog.log("update", "API访问超限，重置时间: " + timeStr);
                            ToastUtil.showUnique(weakRef.get(), "API 访问超限\n请在 " + timeStr + " 后重试");
                        }

                        @Override
                        public void onFailure(String errorMsg) {
                            ReaLog.log("update", "检查更新失败: " + errorMsg);
                            ToastUtil.showUnique(weakRef.get(), "检查更新失败: " + errorMsg);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    ToastUtil.showUnique(weakRef.get(), "检查更新异常: " + e.getMessage());
                    ReaLog.log("update", "检查更新异常: " + e.getMessage());
                }
            }
        });
    }

    // ----------------------------------------------------------------------
    // 数据获取
    // ----------------------------------------------------------------------
    private interface ReleaseCallback {
        void onSuccess(ReleaseInfo release);
        void onRateLimitExceeded(long resetTimeMillis);
        void onFailure(String errorMsg);
    }

    // GitHub 直链探测
    public static class ReleaseInfo {
        public String tagName, name, body, releaseUrl, downloadUrl, updatedAt;
        public boolean linkFallback;
        ReleaseInfo(String tagName, String name, String body, String releaseUrl, String downloadUrl, String updatedAt) {
            this.tagName = tagName;
            this.name = name;
            this.body = body;
            this.releaseUrl = releaseUrl;
            this.downloadUrl = downloadUrl;
            this.updatedAt = updatedAt;
        }

    }

    // GitHub API访问上限检测
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
                        ReaLog.log("update", "API已达上限，请稍后再试");
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
            while ((line = reader.readLine()) != null) response.append(line);
            reader.close();
            JSONObject rel = new JSONObject(response.toString());
            String tagName = rel.optString("tag_name", "");
            String name = rel.optString("name", "");
            String body = rel.optString("body", "");
            String releaseUrl = rel.optString("html_url", "");
            String updatedAt = rel.optString("updated_at", "");
            String downloadUrl = releaseUrl;
            if (rel.has("assets")) {
                JSONArray assets = rel.getJSONArray("assets");
                // 提取APK的附件
                String apkUrl = null;
                for (int i = 0; i < assets.length(); i++) {
                    try {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.toLowerCase(java.util.Locale.ENGLISH).endsWith(".apk")
                                && asset.has("browser_download_url")) {
                            apkUrl = asset.getString("browser_download_url");
                            break;
                        }
                    } catch (JSONException ignore) {
                    }
                }
                // 无法提取APK时选首个文件
                if (apkUrl == null && assets.length() > 0) {
                    JSONObject firstAsset = assets.getJSONObject(0);
                    if (firstAsset.has("browser_download_url")) {
                        apkUrl = firstAsset.getString("browser_download_url");
                    }
                }
                if (apkUrl != null) downloadUrl = apkUrl;
            }
            // 探测直链是否存活：404/410 或超时/连接失败
            String originalUrl = downloadUrl;
            downloadUrl = ensureDownloadAlive(downloadUrl);
            ReleaseInfo info = new ReleaseInfo(tagName, name, body, releaseUrl, downloadUrl, updatedAt);
            if (!UPDATE_URL_2.equals(originalUrl) && UPDATE_URL_2.equals(downloadUrl))
                info.linkFallback = true;
            callback.onSuccess(info);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onFailure("网络错误: " + e.getMessage());
            ReaLog.log("update", "网络错误: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // 探测GitHubAPk直链是否存活否则回退（走用户网络）
    private static String ensureDownloadAlive(String url) {
        if (url == null || url.length() == 0) return url;
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code == 404 || code == 410) {
                ReaLog.log("update", "APK 直链已失效(" + code + ")，使用蓝奏云网盘");
                return UPDATE_URL_2;
            }
        } catch (Exception e) {
            ReaLog.log("update", "APK 直链探测失败，使用蓝奏云网盘: " + e.getMessage());
            return UPDATE_URL_2;
        } finally {
            if (conn != null) conn.disconnect();
        }
        return url;
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
    // Markdown 转 HTML
    // ----------------------------------------------------------------------
    private static Spanned renderMarkdownAsHtml(String markdown) {
        if (TextUtils.isEmpty(markdown)) return fromHtml("");
        try {
            // 先转义 HTML 保留字：release 正文里混进 < > &
            String html = markdown.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
            html = html.replaceAll("(?i)https?://[^\\s<>)\"']+", "<a href=\"$0\">$0</a>");
            // 纯 [文字] 走高亮
            html = html.replaceAll("(?i)\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
            // 任务框先拆成 Unicode 字符：留在原处会被下面的方括号高亮吃掉
            html = html.replaceAll("(?m)^([-*+])\\s+\\[[xX]\\s*\\]\\s+", "$1 ☑ ");
            html = html.replaceAll("(?m)^([-*+])\\s+\\[\\s*\\]\\s+", "$1 ☐ ");
            // 方括号标记
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
            // 列表：无序 [-*+]、有序 \d+.
            String[] lines = html.split("\n");
            StringBuilder sb = new StringBuilder();
            int listMode = 0; // 0=不在列表 1=ul 2=ol
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.matches("^[-*+]\\s+.*")) {
                    if (listMode != 1) {
                        if (listMode == 2) sb.append("</ol>");
                        sb.append("<ul>");
                        listMode = 1;
                    }
                    String item = trimmed.replaceFirst("^[-*+]\\s+", "");
                    sb.append("<li>").append(item).append("</li>");
                } else if (trimmed.matches("^\\d+\\.\\s+.*")) {
                    if (listMode != 2) {
                        if (listMode == 1) sb.append("</ul>");
                        sb.append("<ol>");
                        listMode = 2;
                    }
                    String item = trimmed.replaceFirst("^\\d+\\.\\s+", "");
                    sb.append("<li>").append(item).append("</li>");
                } else {
                    if (listMode == 1) sb.append("</ul>");
                    else if (listMode == 2) sb.append("</ol>");
                    listMode = 0;
                    sb.append(line).append("\n");
                }
            }
            if (listMode == 1) sb.append("</ul>");
            else if (listMode == 2) sb.append("</ol>");
            html = sb.toString();
            // 分隔线：--- 单独一行（fromHtml 不支持 <hr>，用字符线模拟）
            html = html.replaceAll("(?m)^\\s*-{3,}\\s*$", "<br/>────────<br/>");
            // 引用
            html = html.replaceAll("(?m)^>\\s*(.+)$", "<blockquote>$1</blockquote>");
            // 换行
            html = html.replaceAll("\n{2,}", "<br/><br/>");
            html = html.replaceAll("\n", "<br/>");
            return fromHtml(html);
        } catch (Throwable t) {
            ReaLog.log("update", "更新渲染失败，降级为纯文本: " + t.getMessage());
            return fromHtml(TextUtils.htmlEncode(markdown).replace("\n", "<br/>"));
        }
    }

    // ----------------------------------------------------------------------
    // 目标应用内提示 - 无法直接进行版本检测对话框
    // ----------------------------------------------------------------------
    private static void showInTargetAppDialog(final Activity activity) {
        String htmlMessage = "<font color='#FF5722'><b>请在模块主页中检查更新</b></font><br><br>" +
                "您当前在目标应用内，无法直接进行版本检测。<br>" +
                "您可以选择：<br>" +
                "<font color='#9E9E9E'><b>• 打开模块主页进行完整检测</b></font><br>" +
                "<font color='#9E9E9E'><b>• 访问GitHub Releases页面查看最新版本</b></font><br><br>" +
                "<font color='#2196F3'><b>更新链接：</b></font><br>" +
                "<a href=\"" + GITHUB_RELEASES_URL + "\">" + GITHUB_RELEASES_URL + "</a><br>" +
                "<font color='#2196F3'><b>更新链接2：</b></font><br>" +
                "<a href=\"" + UPDATE_URL_2 + "\">" + UPDATE_URL_2 + "</a>（密码：" + UPDATE_PASSWORD + "）<br><br>" +
                "<font color='#9E9E9E'><small>请选择操作：(链接可长按复制)</small></font>";
        String[] buttonTexts = {"打开模块主页", "打开链接", "取消"};
        DialogInterface.OnClickListener positive = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                tryLaunchModuleMainActivity(activity);
            }
        };
        DialogInterface.OnClickListener negative = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            try{
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL));
                More.markInternal(intent);
                activity.startActivity(intent);
               } catch (Throwable t) {
                ToastUtil.showUnique(activity, "无法打开链接");
             }
            }
        };
        // 取消按钮使用空实现，避免 null 导致意外
        DialogInterface.OnClickListener neutral = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        };
        AlertDialog dialog = DialogUi.createBoundedDialog(activity, "检测提示", htmlMessage, buttonTexts,
                new DialogInterface.OnClickListener[]{positive, negative, neutral});
        if (dialog != null) dialog.show();
    }

    // ----------------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------------
    private static Spanned fromHtml(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        } else {
            return Html.fromHtml(html);
        }
    }

    // 检查链接是否安全 - 白名单
    private static boolean isSafeUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ENGLISH);
        return host.endsWith("github.com")
                || host.endsWith("githubusercontent.com")
                || host.endsWith("lanzouu.com");
    }

    // ----------------------------------------------------------------------
    //  发现新版本 - 半屏页
    // ----------------------------------------------------------------------
    public static class UpdateSheetPage extends PageStack.Page {
        private final Update.ReleaseInfo mRelease;

        public UpdateSheetPage(Update.ReleaseInfo release) {
            mRelease = release;
        }

        @Override
        public String key() {
            return "module_update";
        }

        @Override
        public String title() {
            return UPDATE_TITLE;
        }

        @Override
        public boolean halfScreen() {
            return true;
        }

        @Override
        public boolean sheetCancelable() {
            return true;
        }

        @Override
        public View build(final Activity a) {
            LinearLayout root = UiComponents.sheetRoot(a);
            root.addView(UiComponents.sheetHeader(a, UPDATE_TITLE, null));
            String tag = mRelease.tagName != null ? mRelease.tagName : "未知版本";
            String name = mRelease.name != null ? mRelease.name : "无版本名称";
            String updated = (mRelease.updatedAt != null && mRelease.updatedAt.length() >= 10)
                    ? mRelease.updatedAt.substring(0, 10) : "未知时间";
            String body = mRelease.body != null ? mRelease.body : "暂无更新内容";
            int ph = UiKit.dpI(a, UiKit.PAGE_MARGIN);
            // 内容容器
            LinearLayout box = (LinearLayout) UiComponents.paddedContent(a);
            // 卡 1：版本元信息
            UiComponents.CardGroup meta = UiComponents.cardGroup(a, "版本信息", true);
            meta.addRow(UiComponents.selectableSub(UiComponents.settingRow(a, "最新版本", tag, null, null)));
            meta.addRow(UiComponents.selectableSub(UiComponents.settingRow(a, "版本名称", name, null, null)));
            meta.addRow(UiComponents.selectableSub(UiComponents.settingRow(a, "更新时间", updated, null, null)));
            box.addView(meta.root);
            // 卡 2：更新内容
            UiComponents.CardGroup bodyCard = UiComponents.cardGroup(a, "更新内容", true);
            TextView msg = new TextView(a);
            msg.setText(renderMarkdownAsHtml(body));
            msg.setTextSize(14);
            msg.setTextColor(UiKit.textMain(a));
            msg.setMovementMethod(LinkMovementMethod.getInstance());
            msg.setLineSpacing(UiKit.dpI(a, 2), 1.1f);
            msg.setPadding(ph, UiKit.dpI(a, 10), ph, UiKit.dpI(a, 10));
            UiComponents.selectable(msg);
            bodyCard.content.addView(msg);
            box.addView(bodyCard.root);
            // 卡 3：发布页面 / 更新链接2
            UiComponents.CardGroup links = UiComponents.cardGroup(a, "下载", true);
            String href1 = mRelease.releaseUrl != null ? TextUtils.htmlEncode(mRelease.releaseUrl) : "";
            String href2 = TextUtils.htmlEncode(UPDATE_URL_2);
            String html = "发布页面：<a href=\"" + href1 + "\">"
                    + href1 + "</a><br/>"
                    + "更新链接2：<a href=\"" + href2 + "\">"
                    + href2 + "</a>"
                    + "（密码：" + TextUtils.htmlEncode(UPDATE_PASSWORD) + "）";
            TextView linkView = new TextView(a);
            linkView.setText(fromHtml(html));
            linkView.setTextSize(13);
            linkView.setTextColor(UiKit.textMain(a));
            linkView.setMovementMethod(LinkMovementMethod.getInstance());
            linkView.setPadding(ph, UiKit.dpI(a, 10), ph, UiKit.dpI(a, 10));
            UiComponents.selectable(linkView);
            links.content.addView(linkView);
            box.addView(links.root);
            root.addView(UiComponents.scrollNoFooter(a, box), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            // 按钮行：暂不更新 / 立即更新
            LinearLayout buttons = UiKit.hbox(a);
            buttons.setGravity(Gravity.CENTER_VERTICAL);
            TextView laterBtn = UiComponents.ghostButton(a, "暂不更新",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            PageStack s = PageStack.get();
                            if (s != null) s.pop();
                        }
                    });
            TextView okBtn = UiComponents.primaryButton(a,
                    mRelease.downloadUrl != null && !mRelease.downloadUrl.equals(mRelease.releaseUrl)
                            ? "立即更新" : "获取新版本",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            // 跳转外部浏览器/下载管理器
                            if (!UiGuard.tap("update_open", UPDATE_LINK_THROTTLE_MS)) return;
                            openUpdateLink(a, mRelease);
                        }
                    });
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bp.rightMargin = UiKit.dpI(a, 8);
            laterBtn.setLayoutParams(bp);
            buttons.addView(laterBtn);
            LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            okBtn.setLayoutParams(bp2);
            buttons.addView(okBtn);
            buttons.setPadding(ph, 0, ph, UiKit.dpI(a, 6));
            TextView fallback = UiComponents.chipButton(a, "无法下载？点我",
                    new View.OnClickListener() {
                        public void onClick(View v) {
                            if (!UiGuard.tap("update_fallback", UPDATE_LINK_THROTTLE_MS)) return;
                            UiTabs.openUrlWithPassword(a, UPDATE_URL_2, UPDATE_PASSWORD);
                        }
                    }, false);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.setMargins(ph, UiKit.dpI(a, 2), 0, UiKit.dpI(a, 14));
            fallback.setLayoutParams(flp);
            box.addView(fallback);
            root.addView(buttons);
            return root;
        }

        // 跳转更新地址
        private void openUpdateLink(Activity a, Update.ReleaseInfo release) {
            String url = release.downloadUrl;
            if (url == null || !isSafeUrl(url)) {
                ToastUtil.showUnique(a, "获取链接失败，请手动访问发布页");
                url = release.releaseUrl != null ? release.releaseUrl
                        : GITHUB_RELEASES_URL;
            } else if (!UPDATE_URL_2.equals(url)) {
                ToastUtil.showUnique(a, "正在获取新版本");
            }
            try {
                if (UPDATE_URL_2.equals(url)) {
                    UiTabs.openUrlWithPassword(a, url, UPDATE_PASSWORD);
                } else {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    More.markInternal(intent);
                    a.startActivity(intent);
                }
            } catch (Throwable t) {
                ToastUtil.showUnique(a, "无法打开链接");
            }
        }
    }

    // 打开模块主页
    private static boolean tryLaunchModuleMainActivity(Activity activity) {
        PackageManager pm = activity.getPackageManager();
        try {
            Intent launchIntent = pm.getLaunchIntentForPackage(MODULE_PACKAGE);
            if (launchIntent == null) {
                launchIntent = new Intent(Intent.ACTION_MAIN);
                launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                launchIntent.setComponent(new android.content.ComponentName(
                MODULE_PACKAGE, MODULE_PACKAGE + ".MainHome"));
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // 模块内部发起的启动：加跳过标记
            More.markInternal(launchIntent);
            activity.startActivity(launchIntent);
            return true;
        } catch (Exception e) {
            try {
                com.install.appinstall.xl.ui.PageStack s = com.install.appinstall.xl.ui.PageStack.get();
                if (s != null && s.isOpen()) {
                    s.push(new com.install.appinstall.xl.ui.ModulePages.ModuleHomePage());
                    ToastUtil.showUnique(activity, "❌启动失败，降级为页面介绍");
            ReaLog.log("update", "启动失败，降级跳转页面介绍: " + e);
                    return true;
                }
            } catch (Throwable ignored) {
            }
            ToastUtil.showUnique(activity, "❌启动失败:" + e.getMessage());
            ReaLog.log("update", "获取模块启动失败: " + e);
            return false;
        }
    }
}

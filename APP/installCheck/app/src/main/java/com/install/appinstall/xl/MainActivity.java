package com.install.appinstall.xl;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

// 缺少以下两个重要的 import：
import android.content.res.Configuration;  // 添加这一行
import android.util.TypedValue;           // 添加这一行

// ... 其余代码
// 移除AndroidX依赖，改用原生API
public class MainActivity extends Activity {

    // 模块核心功能描述
    private static final String[] FEATURES = {
		"✅ 基础防护：拦截PackageManager核心查询方法",
		"✅ 系统级防护：阻止系统包信息文件读取",
		"✅ 反射拦截：监控PackageManager隐藏方法调用",
		"✅ 智能适配：支持主流安装检测拦截",
		"✅ 网络防护：拦截OkHttp检测类请求",
		"✅ 伪造数据：生成标准化虚假安装信息",
		"✅ 拦截注入：网络注入器,返回虚假安装信息",
		"\n",
		"⚠️ 拦截率：达到87%及以上？(胡编乱造)",
		"\n",
		"思路：永恒之蓝(小淋)",
		"创作：DeepSeek",
		"创作：豆包APP",
		"\n",
		"⚠️严禁任何商业用途,本模块免费测试使用!"

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 动态构建美化界面（无需XML布局文件）
        LinearLayout rootLayout = createRootLayout();
        setContentView(rootLayout);

        // 添加标题栏
        TextView titleView = createTitleView();
        rootLayout.addView(titleView);

        // 添加激活状态提示
        final TextView statusView = createStatusView();
        rootLayout.addView(statusView);

        // 添加功能列表
        LinearLayout featuresLayout = createFeaturesLayout();
        rootLayout.addView(featuresLayout);

        // 点击状态提示刷新激活状态
        statusView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					refreshActivationStatus(statusView);
				}
			});
    }

    /**
     * 创建根布局（垂直排列，带边距和背景）
     */
    private LinearLayout createRootLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        // 改用原生颜色值，避免ContextCompat依赖
        layout.setBackgroundColor(0xFFFFFFFF);

        // 设置边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT,
			ViewGroup.LayoutParams.MATCH_PARENT
        );
        int padding = dp2px(20);
        layout.setPadding(padding, padding, padding, padding);
        layout.setLayoutParams(params);
        return layout;
    }

    /**
     * 创建标题视图
     */
    private TextView createTitleView() {
        TextView title = new TextView(this);
        // 直接设置文字，避免R.string依赖
        title.setText("应用安装防护模块-小淋");
        title.setTextSize(24);
        title.setTextColor(0xFF000000);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);

        // 标题边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp2px(30);
        title.setLayoutParams(params);
        return title;
    }

    /**
     * 创建激活状态视图
     */
    private TextView createStatusView() {
        TextView status = new TextView(this);
        boolean isActivated = isModuleActivated();

        // 直接设置文字，避免R.string依赖
        status.setText(isActivated ? "✅ 模块已激活" : "❌ 模块未激活");
        status.setTextSize(18);
        // 改用原生颜色值
        status.setTextColor(isActivated ? 0xFF32CD32 : 0xFFFF4444);
        status.setPadding(dp2px(15), dp2px(10), dp2px(15), dp2px(10));
        // 移除drawable背景依赖，简化设计
        status.setBackgroundColor(isActivated ? 0xFFE6F7E6 : 0xFFFFF2F2);
        status.setGravity(Gravity.CENTER);

        // 状态视图边距
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = dp2px(30);
        status.setLayoutParams(params);
        return status;
    }

    /**
     * 创建功能列表布局
     */
    private LinearLayout createFeaturesLayout() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // 功能列表标题
        TextView featuresTitle = new TextView(this);
        featuresTitle.setText("核心功能");
        featuresTitle.setTextSize(18);
        featuresTitle.setTextColor(0xFF4A4A4A);
        featuresTitle.setTypeface(featuresTitle.getTypeface(), android.graphics.Typeface.BOLD);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.bottomMargin = dp2px(15);
        featuresTitle.setLayoutParams(titleParams);
        layout.addView(featuresTitle);

        // 添加功能项
        for (String feature : FEATURES) {
            TextView featureView = new TextView(this);
            featureView.setText(feature);
            featureView.setTextSize(16);
            featureView.setTextColor(0xFF4A4A4A);

            LinearLayout.LayoutParams featureParams = new LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
            );
            featureParams.bottomMargin = dp2px(12);
            featureView.setLayoutParams(featureParams);
            layout.addView(featureView);
        }

        return layout;
    }

    /**
     * 刷新激活状态
     */
    private void refreshActivationStatus(TextView statusView) {
        boolean isActivated = isModuleActivated();
        statusView.setText(isActivated ? "✅ 模块已激活" : "❌ 模块未激活");
        statusView.setTextColor(isActivated ? 0xFF32CD32 : 0xFFFF4444);
        statusView.setBackgroundColor(isActivated ? 0xFFE6F7E6 : 0xFFFFF2F2);

        // 直接设置Toast文字，避免R.string依赖
        Toast.makeText(this, isActivated ? 
					   "模块运行正常" : "请在Xposed框架中激活模块", 
					   Toast.LENGTH_SHORT).show();
    }

    /**
     * dp转px（适配不同屏幕）
     */
    private int dp2px(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }

    /**
     * 模块激活状态检测（供Xposed Hook替换）
     */
    public static boolean isModuleActivated() {
        return false; // 实际由Xposed Hook返回true
    }
}

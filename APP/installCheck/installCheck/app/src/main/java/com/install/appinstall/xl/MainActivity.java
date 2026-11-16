package com.install.appinstall.xl;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ViewGroup;

import com.install.appinstall.xl.ui.ModulePages;
import com.install.appinstall.xl.ui.PageStack;
import com.install.appinstall.xl.ui.UiKit;
import com.install.appinstall.xl.util.ToastUtil;
import com.install.appinstall.xl.util.Update;

/** 模块主界面 */
public class MainActivity extends Activity {
    private long mLastBackTime = 0;

    /*创建*/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setBackgroundDrawable(new ColorDrawable(UiKit.bg(this)));
            getWindow().setNavigationBarColor(UiKit.topBarBg(this));
        } catch (Throwable t) {
        }
        PageStack stack = PageStack.open(this, new ModulePages.ModuleHomePage());
        if (stack != null) stack.setFinishActivityOnClose(true);
        Update.checkForUpdate(this);
    }

    /*双击退出应用*/
    @Override
    public void onBackPressed() {
        PageStack stack = PageStack.get();
        if (stack != null && stack.depth() > 1) {
            stack.pop();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - mLastBackTime < 2000) {
            try {
                ViewGroup content = (ViewGroup) findViewById(android.R.id.content);
                if (content != null) content.removeAllViews();
            } catch (Throwable ignored) {
            }
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        mLastBackTime = now;
        ToastUtil.show(this, "再按一次退出");
    }

    /** Xposed 激活状态：成功激活自动返回true */
    public boolean isModuleActivated() {
        return false;
    }
}

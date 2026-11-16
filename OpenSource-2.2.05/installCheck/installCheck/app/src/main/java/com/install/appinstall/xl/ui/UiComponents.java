package com.install.appinstall.xl.ui;

import android.app.Activity;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

import com.install.appinstall.xl.util.DialogUi;
import com.install.appinstall.xl.util.ToastUtil;

/**
 * UiComponents —— 新 UI 组件库
 */
public final class UiComponents {

    private UiComponents() {}

    // ============================================================
    //  标签容器
    // ============================================================
    public interface OnTabSwipe {
        /** delta: +1 = 下一标签，-1 = 上一标签 */
        void onSwipe(int delta);
    }

    /** 下拉刷新回调（全局下拉刷新，PageStack 与 SwipeTabHost 共用） */
    public interface OnPullRefresh {
        void onRefresh();
    }

    /**
     * 下拉刷新圆环（RefreshHeader 内）
     */
    public static class RefreshSpinner extends android.view.View {
        private final android.graphics.Paint mTrack = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint mArc = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float mSpinAngle;
        private boolean mSpinning;
        private android.animation.ValueAnimator mSpin;
        public RefreshSpinner(android.content.Context c) {
            super(c);
            float stroke = UiKit.dp(c, 3);
            mTrack.setStyle(android.graphics.Paint.Style.STROKE);
            mTrack.setStrokeWidth(stroke);
            mTrack.setColor(UiKit.divider(c));
            mArc.setStyle(android.graphics.Paint.Style.STROKE);
            mArc.setStrokeWidth(stroke);
            mArc.setColor(UiKit.accent(c));
            mArc.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        }

        /** 无限旋转：触发刷新后调用 */
        public void startSpin() {
            if (mSpinning) return;
            stopSpin();
            mSpinning = true;
            mSpin = android.animation.ValueAnimator.ofFloat(0f, 360f);
            mSpin.setDuration(720L);
            mSpin.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            mSpin.setInterpolator(new android.view.animation.LinearInterpolator());
            mSpin.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(android.animation.ValueAnimator a) {
                        Object v = a.getAnimatedValue();
                        if (v instanceof Float) {
                            mSpinAngle = ((Float) v).floatValue();
                            invalidate();
                        }
                    }
                });
            mSpin.start();
        }

        /** 停旋转（不负责收起的位移动画） */
        public void stopSpin() {
            android.animation.ValueAnimator a = mSpin;
            mSpin = null;
            if (a != null) {
                try {
                    a.cancel();
                } catch (Throwable ignored) {
                }
            }
            mSpinning = false;
        }

        public boolean isSpinning() {
            return mSpinning;
        }

        /** 圆环配色：头部是主题色底时传白色系 */
        public void setColors(int track, int arc) {
            mTrack.setColor(track);
            mArc.setColor(arc);
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float r = Math.min(cx, cy) - UiKit.dp(getContext(), 3);
            if (r <= 0f) return;
            canvas.drawCircle(cx, cy, r, mTrack);
            float start = mSpinning ? -90f + mSpinAngle : -90f;
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, start, 270f, false, mArc);
        }
    }

    /**
     * 下拉刷新的头部区域
     */
    public static class RefreshHeader extends android.widget.LinearLayout {
        public RefreshHeader(android.content.Context c) {
            super(c);
            setOrientation(android.widget.LinearLayout.HORIZONTAL);
            setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.CENTER_HORIZONTAL);
        }
    }

    /**
     * 内容容器
     */
    public static class PullRefreshLayout extends android.widget.FrameLayout {
        // 触发刷新的阻尼后距离：46dp ≈ 手指实拉 102dp（÷0.45 阻尼）
        private static final float PULL_THRESHOLD_DP = 46f;
        private static final float PULL_DAMP = 0.45f;
        private static final int HEADER_DP = 48;
        private static final int SPINNER_DP = 28;
        private static final long SUCCESS_DELAY_MS = 400L;
        private static final long COLLAPSE_MS = 220L;
        private static final int ST_IDLE = 0,
        ST_PULL = 1,
        ST_READY = 2,
        ST_REFRESHING = 3,
        ST_SUCCESS = 4;
        protected final float[] downX = new float[1];
        protected final float[] downY = new float[1];
        protected boolean downAtTop;
        protected float mUpAccum;
        protected boolean mPullDead;
        protected float mLastY;
        protected float mTopReachedY = -1f;
        protected boolean mWasTop;
        protected final float mThresholdPx;
        protected final int mHeaderH;
        protected boolean decided;
        protected boolean refreshPull;
        protected boolean mRefreshing;
        protected OnPullRefresh mRefreshListener;
        protected RefreshHeader mHeader; // 刷新区域（倒半圆头部，含圆环+文本）
        protected RefreshSpinner mSpinner; // 头部里的圆环
        protected android.widget.TextView mLabel; // 头部里的状态文本
        protected int mState = ST_PULL;

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            // 「正在刷新」的无限旋转动画在这里必须停
            if (mSpinner != null) mSpinner.stopSpin();
        }

        // 收起序列的未决任务（成功延迟 + 收起完成回调）
        private Runnable mPendingAfter;
        private final Runnable mSuccessTask = new Runnable() {
            public void run() {
                mSpinner.setRotation(0f);
                applyState(ST_IDLE);
                collapseHeader(mPendingAfter);
            }
        };
        private final Runnable mCollapseTask = new Runnable() {
            public void run() {
                Runnable a = mPendingAfter;
                mPendingAfter = null;
                if (a != null) a.run();
            }
        };

        public PullRefreshLayout(android.content.Context c) {
            super(c);
            // 头部从内容上方探出、内容下推都要画到布局框之外
            setClipChildren(false);
            setClipToPadding(false);
            mThresholdPx = UiKit.dp(c, PULL_THRESHOLD_DP);
            mHeaderH = UiKit.dpI(c, HEADER_DP);
            // 刷新区域常驻顶层、初始藏在内容上方之外，不随标签页一起被 removeView
            mHeader = new RefreshHeader(c);
            android.widget.FrameLayout.LayoutParams hlp = new android.widget.FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, mHeaderH, Gravity.TOP);
            mHeader.setLayoutParams(hlp);
            mHeader.setTranslationY(-mHeaderH);
            mSpinner = new RefreshSpinner(c);
            int sp = UiKit.dpI(c, SPINNER_DP);
            mSpinner.setLayoutParams(new LinearLayout.LayoutParams(sp, sp));
            mLabel = new android.widget.TextView(c);
            mLabel.setTextSize(13f);
            mLabel.setTextColor(UiKit.accent(c));
            mLabel.setText("下拉刷新");
            mHeader.addView(mSpinner);
            mHeader.addView(mLabel);
            addView(mHeader);
        }

        public void setOnPullRefresh(OnPullRefresh l) {
            mRefreshListener = l;
        }

        /** 外部（如标题栏刷新按钮）触发 */
        public void startRefresh() {
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                post(new Runnable() {
                        public void run() {
                            startRefresh();
                        }
                    });
                return;
            }
            if (mHeader == null || mRefreshing || refreshPull) return;
            cancelRefreshAnim();
            mRefreshing = true;
            // 固定下推一栏位：整行露出显示「正在刷新……」
            mHeader.setTranslationY(0f);
            pushContent(mHeaderH);
            mSpinner.setRotation(0f);
            mSpinner.startSpin();
            applyState(ST_REFRESHING);
            if (mRefreshListener != null) mRefreshListener.onRefresh();
        }

        /**
         * 刷新完成（外部刷新回调）
         */
        public void finishRefresh(final Runnable after) {
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                post(new Runnable() {
                        public void run() {
                            finishRefresh(after);
                        }
                    });
                return;
            }
            mRefreshing = false;
            // 刷新回调结束后所有手势阈值归位
            mUpAccum = 0f;
            mPullDead = false;
            decided = false;
            refreshPull = false;
            if (mHeader == null) {
                if (after != null) after.run();
                return;
            }
            mSpinner.stopSpin();
            applyState(ST_SUCCESS);
            mHeader.removeCallbacks(mSuccessTask);
            mPendingAfter = after;
            mHeader.postDelayed(mSuccessTask, SUCCESS_DELAY_MS);
        }

        /** 收起后无后续动作 */
        public void finishRefresh() {
            finishRefresh(null);
        }

        /** 把刷新区域提到最上层*/
        protected void bringHeaderFront() {
            if (mHeader != null && getChildCount() > 1) mHeader.bringToFront();
        }
        public void addView(android.view.View child) {
            super.addView(child);
            bringHeaderFront();
        }

        public void addView(android.view.View child, android.view.ViewGroup.LayoutParams params) {
            super.addView(child, params);
            bringHeaderFront();
        }

        public void addView(android.view.View child, int index) {
            super.addView(child, index);
            bringHeaderFront();
        }

        public void addView(android.view.View child, int width, int height) {
            super.addView(child, width, height);
            bringHeaderFront();
        }

        /** 当前内容里第一个 ScrollView 是否已滚到顶*/
        protected boolean currentScrollTop() {
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                android.view.View c = getChildAt(i);
                if (c == mHeader || c.getVisibility() != VISIBLE) continue;
                android.widget.ScrollView sv = findScrollView(c);
                if (sv != null) return sv.getScrollY() <= 0;
                return true;
            }
            return true;
        }

        protected void resetGesture() {
            decided = false;
            refreshPull = false;
        }

        /** 横向位移超过 14dp：子类判定是否接管为横滑（默认不接管） */
        protected void onHorizontalDecided(float dx, float dyy) {}

        /** 是否拦截本次手势（下拉刷新或子类横滑） */
        protected boolean shouldIntercept() {
            return refreshPull;
        }

        /** 手指抬起且不是下拉刷新：子类处理横滑翻页（返回 true 表示已消费） */
        protected boolean onUp(android.view.MotionEvent e) {
            return false;
        }

        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX[0] = e.getRawX();
                    downY[0] = e.getRawY();
                    mLastY = e.getRawY();
                    mUpAccum = 0f;
                    mPullDead = false;
                    resetGesture();
                    downAtTop = currentScrollTop();
                    mWasTop = downAtTop;
                    mTopReachedY = -1f;
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    boolean nowTop = currentScrollTop();
                    if (nowTop) {
                        if (!mWasTop) mTopReachedY = e.getRawY();
                    } else {
                        mTopReachedY = -1f;
                    }
                    mWasTop = nowTop;
                    // 逐帧累计上推量（本帧相对上一帧：dy<0 即上推），超 8dp 阻断
                    float frameDy = e.getRawY() - mLastY;
                    if (frameDy < 0f) {
                        mUpAccum += -frameDy;
                        if (mUpAccum > UiKit.dp(getContext(), 4)) mPullDead = true;
                    } else if (frameDy > UiKit.dp(getContext(), 40)) {
                        // 单帧下滑 > 40dp = 快速划动/惯性翻页（fling）
                        mPullDead = true;
                    }
                    mLastY = e.getRawY();
                    if (!decided) {
                        float dx = Math.abs(e.getRawX() - downX[0]);
                        float dyy = e.getRawY() - downY[0];
                        float pullTopDy = e.getRawY() - mTopReachedY;
                        boolean fromTopReached = !mRefreshing && mTopReachedY >= 0f && !mPullDead
                            && pullTopDy > UiKit.dp(getContext(), 16)
                            && pullTopDy > dx * 1.4f && nowTop;
                        // 下拉判定 = 三个条件同时成立：
                        // ① downAtTop —— 起手时 ScrollView 就在顶部
                        // ② 单调性：短促多次下滑的衔接处必有回推
                        // ③ 现在仍在顶部 + 横向位移不占优
                        if (dx > UiKit.dp(getContext(), 14)) {
                            decided = true;
                            onHorizontalDecided(dx, dyy);
                        } else if (!mRefreshing && downAtTop && !mPullDead
                                   && dyy > UiKit.dp(getContext(), 16)
                                   && dyy > dx * 1.4f && currentScrollTop()) {
                            // ScrollView 已在顶部 + 手指相对起手点下移：接管为下拉刷新
                            cancelRefreshAnim();
                            decided = true;
                            refreshPull = true;
                        } else if (fromTopReached) {
                            // 滑回顶之后继续下拉：同上接管，位移从「滑回顶那一帧」起算
                            cancelRefreshAnim();
                            decided = true;
                            refreshPull = true;
                            downY[0] = mTopReachedY;
                        }
                    }
                    if (shouldIntercept()) return true;
                    break;
                default:
                    break;
            }
            return false;
        }

        public boolean onTouchEvent(android.view.MotionEvent e) {
            int action = e.getActionMasked();
            switch (action) {
                case android.view.MotionEvent.ACTION_DOWN:
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (refreshPull) {
                        float pullPx = Math.max(0f, (e.getRawY() - downY[0]) * PULL_DAMP);
                        // 头部从内容上方探出 + 内容同步下推让位
                        mHeader.setTranslationY(Math.max(-mHeaderH, pullPx - mHeaderH));
                        pushContent(pullPx);
                        // 无极旋转：下拉顺时针 / 回拉逆时针，拉满阈值转一圈
                        mSpinner.setRotation(pullPx / mThresholdPx * 360f);
                        applyState(pullPx >= mThresholdPx ? ST_READY : ST_PULL);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    if (refreshPull) {
                        float pullPx = Math.max(0f, (e.getRawY() - downY[0]) * PULL_DAMP);
                        if (pullPx >= mThresholdPx && !mRefreshing) {
                            mRefreshing = true;
                            // 固定下推一栏位：整行露出显示「正在刷新……」
                            mHeader.setTranslationY(0f);
                            pushContent(mHeaderH);
                            mSpinner.setRotation(0f);
                            mSpinner.startSpin();
                            applyState(ST_REFRESHING);
                            if (mRefreshListener != null) mRefreshListener.onRefresh();
                        } else {
                            cancelPull();
                        }
                        resetGesture();
                        return true;
                    }
                    onUp(e);
                    resetGesture();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (refreshPull) cancelPull();
                    resetGesture();
                    return true;
                default:
                    return true;
            }
        }

        /** 所有内容整体下推：手势接管期间内容给刷新区域让位（header 除外） */
        private void pushContent(float dy) {
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                android.view.View c = getChildAt(i);
                if (c == mHeader) continue;
                c.setTranslationY(dy);
            }
        }

        /** 状态机：只在变化时换文本，避免每帧刷新文字 */
        private void applyState(int st) {
            if (st == mState) return;
            mState = st;
            if (mLabel == null) return;
            if (st == ST_PULL || st == ST_IDLE) mLabel.setText("下拉刷新");
            else if (st == ST_READY) mLabel.setText("松开刷新");
            else if (st == ST_REFRESHING) mLabel.setText("正在刷新……");
            else if (st == ST_SUCCESS) mLabel.setText("成功刷新");
        }

        /** 向上收起刷新区域：头部缩回内容上方外，内容回位；收起完毕执行 after */
        private void collapseHeader(final Runnable after) {
            if (mHeader == null) {
                if (after != null) after.run();
                return;
            }
            mHeader.animate().cancel();
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                android.view.View c = getChildAt(i);
                if (c != mHeader) c.animate().cancel();
            }
            mHeader.animate().translationY(-mHeaderH).setDuration(COLLAPSE_MS).start();
            for (int i = 0; i < n; i++) {
                android.view.View c = getChildAt(i);
                if (c == mHeader) continue;
                c.animate().translationY(0f).setDuration(COLLAPSE_MS).start();
            }
            mHeader.removeCallbacks(mCollapseTask);
            mPendingAfter = after;
            mHeader.postDelayed(mCollapseTask, COLLAPSE_MS + 40);
        }

        /** 收起序列进行中再次下拉接管 */
        private void cancelRefreshAnim() {
            if (mHeader == null) return;
            mHeader.removeCallbacks(mSuccessTask);
            mHeader.removeCallbacks(mCollapseTask);
            mPendingAfter = null;
            mHeader.animate().cancel();
            int n = getChildCount();
            for (int i = 0; i < n; i++) {
                android.view.View c = getChildAt(i);
                if (c != mHeader) c.animate().cancel();
            }
        }

        /** 下拉没拉够就松手：收起刷新区域 */
        private void cancelPull() {
            if (mHeader == null) return;
            mSpinner.setRotation(0f);
            applyState(ST_IDLE);
            collapseHeader(null);
        }
    }

    /** 标签容器：在下拉刷新之上叠加左右滑切换标签。*/
    public static class SwipeTabHost extends PullRefreshLayout {

        private boolean horizontal;
        private OnTabSwipe mListener;

        public SwipeTabHost(android.content.Context c) {
            super(c);
        }

        public void setOnTabSwipe(OnTabSwipe l) {
            mListener = l;
        }

        public boolean onInterceptTouchEvent(android.view.MotionEvent e) {
            // 手势开始先清横滑标记：残留到下一次手势会把纯点击/竖滑也拦住
            if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) horizontal = false;
            return super.onInterceptTouchEvent(e);
        }

        /** 移除所有标签页（保留刷新指示器）*/
        public void removeAllTabs() {
            for (int i = getChildCount() - 1; i >= 0; i--) {
                android.view.View c = getChildAt(i);
                if (c != mHeader) removeViewAt(i);
            }
        }

        /** 内容就绪：刷新区域置顶*/
        public void onContentReady() {
            bringHeaderFront();
        }

        protected void onHorizontalDecided(float dx, float dyy) {
            horizontal = dx > Math.abs(dyy) * 1.4f;
        }

        protected boolean shouldIntercept() {
            return refreshPull || horizontal;
        }

        protected boolean onUp(android.view.MotionEvent e) {
            if (horizontal && Math.abs(e.getRawX() - downX[0]) > UiKit.dp(getContext(), 56)
                && mListener != null) {
                mListener.onSwipe(e.getRawX() - downX[0] < 0 ? 1 : -1);
            }
            return horizontal;
        }
    }

    /** 在视图树里找第一个 ScrollView */
    public static android.widget.ScrollView findScrollView(android.view.View root) {
        if (root == null) return null;
        if (root instanceof android.widget.ScrollView) return (android.widget.ScrollView) root;
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.ScrollView r = findScrollView(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    /** 圆盘取色器：拖动选色相 + 饱和度，明度固定偏亮，任意主题色 */
    public static class ColorWheel extends View {

        public interface OnPick {
            void onPick(int color);
        }

        /** 抬手回调：拖动过程只预览，松手后才让调用方做整页刷新 */
        public interface OnPickEnd {
            void onPickEnd();
        }

        private final android.graphics.Paint mFill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private android.graphics.SweepGradient mSweep;
        private android.graphics.RadialGradient mWhite;
        private final float[] mHsv = {210f, 0.8f, 0.95f};
        private float mPickX;
        private float mPickY;
        private boolean mHasPick;
        private OnPick mListener;
        private OnPickEnd mEndListener;

        public ColorWheel(Activity a) {
            super(a);
        }

        public void setOnPick(OnPick l) {
            mListener = l;
        }

        public void setOnPickEnd(OnPickEnd l) {
            mEndListener = l;
        }

        public void setColor(int color) {
            try {
                android.graphics.Color.colorToHSV(color, mHsv);
                mHsv[2] = 0.95f;
                invalidate();
            } catch (Throwable ignored) {
            }
        }

        private void rebuild() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(cx, cy);
            int[] colors = new int[13];
            for (int i = 0; i <= 12; i++) {
                colors[i] = android.graphics.Color.HSVToColor(
                    new float[]{(i * 30f) % 360f, 1f, 1f});
            }
            mSweep = new android.graphics.SweepGradient(cx, cy, colors, null);
            mWhite = new android.graphics.RadialGradient(cx, cy, r,
                                                         0xFFFFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP);
        }

        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            rebuild();
        }

        protected void onDraw(android.graphics.Canvas c) {
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(cx, cy);
            if (mSweep == null) rebuild();
            if (mSweep != null) {
                mFill.setShader(mSweep);
                c.drawCircle(cx, cy, r, mFill);
                mFill.setShader(mWhite);
                c.drawCircle(cx, cy, r, mFill);
                mFill.setShader(null);
            }
            if (mHasPick) {
                android.graphics.Paint p = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(UiKit.dp(getContext(), 2.5f));
                p.setColor(0xFFFFFFFF);
                c.drawCircle(mPickX, mPickY, UiKit.dp(getContext(), 9), p);
                p.setColor(0x66000000);
                p.setStrokeWidth(UiKit.dp(getContext(), 1));
                c.drawCircle(mPickX, mPickY, UiKit.dp(getContext(), 11), p);
            }
        }

        public boolean onTouchEvent(android.view.MotionEvent e) {
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            float r = Math.min(cx, cy);
            if (r <= 0) return false;
            switch (e.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                case android.view.MotionEvent.ACTION_MOVE:
                    {
                        float dx = e.getX() - cx;
                        float dy = e.getY() - cy;
                        float dist = (float) Math.sqrt(dx * dx + dy * dy);
                        if (dist > r) {
                            dx = dx * r / dist;
                            dy = dy * r / dist;
                            dist = r;
                        }
                        mPickX = cx + dx;
                        mPickY = cy + dy;
                        mHasPick = true;
                        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
                        if (angle < 0) angle += 360f;
                        mHsv[0] = angle;
                        mHsv[1] = Math.min(1f, dist / r);
                        mHsv[2] = 0.95f;
                        int color = android.graphics.Color.HSVToColor(mHsv);
                        invalidate();
                        if (mListener != null) mListener.onPick(color);
                        return true;
                    }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    {
                        if (mEndListener != null) mEndListener.onPickEnd();
                        return true;
                    }
                default:
                    return true;
            }
        }
    }

    public static LinearLayout topBar(Activity a, String title, boolean showBack,
                                      View.OnClickListener backAction, View rightAction, int extraTopInset) {
        LinearLayout wrap = UiKit.vbox(a);
        wrap.setBackgroundColor(UiKit.topBarBg(a));
        wrap.setClipChildren(false);

        LinearLayout bar = UiKit.hbox(a);
        bar.setMinimumHeight(UiKit.dpI(a, UiKit.TOPBAR_H));
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClickable(true);
        int padH = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        bar.setPadding(padH, Math.max(extraTopInset, 0) + UiKit.dpI(a, 8), padH, UiKit.dpI(a, 8));

        if (showBack) {
            bar.addView(chipButton(a, "←", backAction, false, 15));
        }

        TextView t = UiKit.text(a, title == null ? "" : title, 18, UiKit.textMain(a));
        t.setSingleLine(true);
        t.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                                                                     ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = UiKit.dpI(a, 10);
        t.setLayoutParams(tp);
        bar.addView(t);
        if (rightAction != null) {
            if (rightAction.getLayoutParams() == null) {
                rightAction.setLayoutParams(new LinearLayout.LayoutParams(
                                                ViewGroup.LayoutParams.WRAP_CONTENT, UiKit.dpI(a, 40)));
            }
            bar.addView(rightAction);
        }

        wrap.addView(bar, new LinearLayout.LayoutParams(
                         ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(UiKit.dividerLine(a));
        return wrap;
    }

    // ============================================================
    //  设置行：标题/副标题
    // ============================================================
    public static LinearLayout settingRow(Activity a, String title, String subtitle,
                                          View trailing, View.OnClickListener click) {
        return settingRow(a, title, subtitle, trailing, click, 0);
    }

    /** 带值高亮的设置行：subColor != 0 时副标题用该颜色并加粗 */
    public static LinearLayout settingRow(Activity a, String title, String subtitle,
                                          View trailing, View.OnClickListener click, int subColor) {
        LinearLayout row = UiKit.hbox(a);
        row.setMinimumHeight(UiKit.dpI(a, UiKit.ROW_MIN_H));
        int padH = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        int padV = UiKit.dpI(a, 12);
        row.setPadding(padH, padV, padH, padV);

        LinearLayout texts = UiKit.vbox(a);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0,
                                                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String safeTitle = UiKit.orDefault(title, "未命名项");
        TextView t = UiKit.text(a, safeTitle, 15, UiKit.textMain(a));
        // 全局功能搜索靠这个 tag 认
        t.setTag("row_title");
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(t);
        if (subtitle != null && subtitle.length() > 0) {
            TextView s = UiKit.text(a, subtitle, 12,
                                    subColor == 0 ? UiKit.textSub(a) : subColor);
            if (subColor != 0) {
                s.setTypeface(null, android.graphics.Typeface.BOLD);
            }
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            sp.topMargin = UiKit.dpI(a, 3);
            s.setLayoutParams(sp);
            texts.addView(s);
        }
        row.addView(texts);
        pressZoom(row);

        if (trailing != null) {
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.leftMargin = UiKit.dpI(a, 12);
            trailing.setLayoutParams(tp);
            row.addView(trailing);
        } else if (click != null) {
            TextView arrow = UiKit.text(a, "›", 20, UiKit.textSub(a));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ap.leftMargin = UiKit.dpI(a, 8);
            arrow.setLayoutParams(ap);
            row.addView(arrow);
        }

        if (click != null) {
            row.setOnClickListener(click);
            row.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.cardPressed(a), 0));

            row.setClickable(true);
        }
        return row;
    }

    /** 通用点击缩放：按下轻微收缩、抬起回弹 */
    public static void pressZoom(View v) {
        if (v == null) return;
        v.setClickable(true);
        v.setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View view, android.view.MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case android.view.MotionEvent.ACTION_DOWN:
                            view.animate().scaleX(0.97f).scaleY(0.97f)
                                .setDuration(90).start();
                            return false;
                        case android.view.MotionEvent.ACTION_UP:
                        case android.view.MotionEvent.ACTION_CANCEL:
                            view.animate().scaleX(1f).scaleY(1f)
                                .setDuration(140).start();
                            return false;
                        default:
                            return false;
                    }
                }
            });
    }

    /** 开关行：值变化走持久化语义 */
    public static LinearLayout switchRow(Activity a, String title, String subtitle,
                                         boolean checked, final OnChecked listener) {
        return switchRow(a, title, subtitle, checked, listener, 0);
    }

    /** 带副标题着色的开关行 subColor != 0 时副标题变色加粗 */
    public static LinearLayout switchRow(Activity a, String title, String subtitle,
                                         boolean checked, final OnChecked listener, int subColor) {
        final MiniSwitch sw = new MiniSwitch(a);
        sw.setCheckedSilently(checked);
        LinearLayout row = settingRow(a, title, subtitle, sw, null, subColor);
        TextView titleTv = null;
        View c0 = row.getChildCount() > 0 ? row.getChildAt(0) : null;
        if (c0 instanceof LinearLayout) {
            LinearLayout col = (LinearLayout) c0;
            View c1 = col.getChildCount() > 0 ? col.getChildAt(0) : null;
            if (c1 instanceof TextView) titleTv = (TextView) c1;
        }
        final TextView ft = titleTv;
        final int normalColor = ft == null ? 0 : ft.getCurrentTextColor();
        final int offColor = UiKit.accent(a);
        final Runnable visual = new Runnable() {
            public void run() {
                if (ft == null) return;
                boolean c = sw.isChecked();
                ft.setTextColor(c ? normalColor : offColor);
                ft.setTypeface(null, c ? android.graphics.Typeface.NORMAL
                               : android.graphics.Typeface.BOLD);
            }
        };
        sw.setVisualHook(visual);
        visual.run();

        sw.setOnCheckedListener(new MiniSwitch.OnCheckedListener() {
                public void onCheckedChanged(MiniSwitch v, boolean c) {
                    visual.run();
                    if (listener != null) listener.onChecked(c);
                }
            });
        return row;
    }

    // ============================================================
    //  自绘开关
    // ============================================================
    public interface OnChecked {
        void onChecked(boolean checked);
    }

    public static class MiniSwitch extends View {

        public interface OnCheckedListener {
            void onCheckedChanged(MiniSwitch v, boolean checked);
        }

        private boolean mChecked;
        private float mAnim;
        private android.animation.ValueAnimator mAnimator;
        private final android.graphics.Paint mPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private OnCheckedListener mListener;

        public MiniSwitch(android.content.Context c) {
            super(c);
            setClickable(true);
            setFocusable(true);
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void setOnCheckedListener(OnCheckedListener l) {
            mListener = l;
        }

        /** 初始化值：不触发回调、不播动画 */
        public void setCheckedSilently(boolean v) {
            mChecked = v;
            mAnim = v ? 1f : 0f;
            invalidate();
            if (mVisualHook != null) mVisualHook.run();
        }

        private Runnable mVisualHook;

        /** 视觉联动钩子：程序改值（如互斥）时同步 */
        public void setVisualHook(Runnable r) {
            mVisualHook = r;
        }

        public void setChecked(boolean v, boolean animate) {
            if (mChecked == v) return;
            mChecked = v;
            if (animate && isAttachedToWindow()) {
                animateTo(v ? 1f : 0f);
            } else {
                mAnim = v ? 1f : 0f;
                invalidate();
            }
            if (mListener != null) mListener.onCheckedChanged(this, v);
        }

        private void animateTo(float target) {
            if (mAnimator != null) mAnimator.cancel();
            mAnimator = android.animation.ValueAnimator.ofFloat(mAnim, target);
            mAnimator.setDuration(150);
            mAnimator.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(android.animation.ValueAnimator an) {
                        mAnim = ((Float) an.getAnimatedValue()).floatValue();
                        invalidate();
                    }
                });
            mAnimator.start();
        }

        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(UiKit.dpI(getContext(), 50), UiKit.dpI(getContext(), 30));
        }

        protected void onDraw(android.graphics.Canvas cv) {
            android.content.Context c = getContext();
            float w = getWidth();
            float h = getHeight();
            float r = h / 2f;
            mPaint.setColor(UiKit.blend(UiKit.isNight(c) ? 0xFF3A3A3F : 0xFFD8D8DE,
                                        UiKit.accent(c), mAnim));
            cv.drawRoundRect(0f, 0f, w, h, r, r, mPaint);

            float pad = UiKit.dp(c, 3.5f);
            float thumbR = r - pad;
            float cx = pad + thumbR + (w - 2f * (pad + thumbR)) * mAnim;
            mPaint.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, r, thumbR, mPaint);
        }

        public boolean onTouchEvent(android.view.MotionEvent e) {
            int action = e.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (action == android.view.MotionEvent.ACTION_UP) {
                setChecked(!mChecked, true);
                performClick();
                return true;
            }
            return super.onTouchEvent(e);
        }
    }

    /** 行间分隔线 */
    public static View rowDivider(Activity a) {
        View v = new View(a);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, UiKit.dpI(a, 1)));
        int m = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        p.setMargins(m, 0, m, 0);
        v.setLayoutParams(p);
        v.setBackgroundColor(UiKit.divider(a));
        return v;
    }

    // ============================================================
    //  分组卡片
    // ============================================================
    private static final java.util.Map<
    String, Boolean> sGroupExpanded = new java.util.concurrent.ConcurrentHashMap<
    String, Boolean>();

    private static boolean groupExpandedOf(String title, boolean def) {
        if (title == null) return def;
        Boolean v = sGroupExpanded.get(title);
        return v != null ? v.booleanValue() : def;
    }

    private static void rememberGroupExpanded(String title, boolean open) {
        if (title == null || title.length() == 0) return;
        sGroupExpanded.put(title, Boolean.valueOf(open));
    }

    public static class CardGroup {

        public final View root;
        public final LinearLayout content;
        private final TextView arrow;
        private final TextView count;
        private final Activity activity;
        private boolean expanded;
        private final String groupTitle;
        private final List<View> rows = new ArrayList<View>();
        private final List<View> mDividers = new ArrayList<View>();
        CardGroup(Activity activity, View root, LinearLayout content, TextView arrow,
                  TextView count, boolean expanded, String groupTitle) {
            this.activity = activity;
            this.root = root;
            this.content = content;
            this.arrow = arrow;
            this.count = count;
            this.expanded = expanded;
            this.groupTitle = groupTitle;
        }

        public void setExpanded(boolean open, boolean animate) {
            expanded = open;
            rememberGroupExpanded(groupTitle, open);
            if (animate) {
                // 展开/收起都走淡入淡出，配合箭头旋转
                content.animate().cancel();
                if (open) {
                    content.setAlpha(0f);
                    content.setVisibility(View.VISIBLE);
                    content.animate().alpha(1f).setDuration(180).start();
                } else {
                    content.animate().alpha(0f).setDuration(130)
                        .withEndAction(new Runnable() {
                            public void run() {
                                content.setVisibility(View.GONE);
                                content.setAlpha(1f);
                            }
                        }).start();
                }
                arrow.animate().rotation(open ? 90f : 0f).setDuration(180).start();
            } else {
                content.animate().cancel();
                content.setAlpha(1f);
                content.setVisibility(open ? View.VISIBLE : View.GONE);
                arrow.setRotation(open ? 90f : 0f);
            }
        }

        public boolean isExpanded() {
            return expanded;
        }

        /** 折叠状态回调*/
        public interface OnToggleListener {
            void onToggle(boolean expanded);
        }

        private OnToggleListener mOnToggle;

        public void setOnToggleListener(OnToggleListener l) {
            mOnToggle = l;
        }

        public void toggle() {
            setExpanded(!expanded, true);
            if (mOnToggle != null) mOnToggle.onToggle(expanded);
        }

        /** 追加一行；卡片最后一行不画分隔线，再追加新行时把上一条恢复 */
        public void addRow(View row) {
            if (row == null) return;
            int n = rows.size();
            if (n > 0 && n <= mDividers.size()) {
                View prevDivider = mDividers.get(n - 1);
                if (prevDivider != null) prevDivider.setVisibility(View.VISIBLE);
            }
            content.addView(row, new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (activity != null) {
                View divider = rowDivider(activity);
                content.addView(divider);
                divider.setVisibility(View.GONE);
                mDividers.add(divider);
            }
            rows.add(row);
            updateCount();
            setExpanded(expanded, false);
        }

        public void clearRows() {
            content.removeAllViews();
            rows.clear();
            mDividers.clear();
            updateCount();
        }

        private void updateCount() {
            if (count != null) count.setText(rows.size() + " 项");
        }

        public int size() {
            return rows.size();
        }
    }

    public static CardGroup cardGroup(Activity a, final String title, boolean expanded) {
        final LinearLayout root = UiKit.vbox(a);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = UiKit.dpI(a, UiKit.CARD_GAP);
        root.setLayoutParams(rp);
        root.setBackground(UiKit.strokeRect(a, UiKit.card(a),
                                            UiKit.withAlpha(UiKit.accent(a), UiKit.isNight(a) ? 0x44 : 0x30),
                                            UiKit.CARD_R));
        root.setClipToOutline(true);
        final LinearLayout header = UiKit.hbox(a);
        header.setMinimumHeight(UiKit.dpI(a, 48));
        int padH = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        header.setPadding(padH, UiKit.dpI(a, 12), padH, UiKit.dpI(a, 12));
        View mark = new View(a);
        mark.setBackground(UiKit.roundRect(a, UiKit.accent(a), 2));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
            UiKit.dpI(a, 3), UiKit.dpI(a, 16));
        mp.rightMargin = UiKit.dpI(a, 8);
        header.addView(mark, mp);

        final TextView arrow = UiKit.text(a, "▸", 14, UiKit.accent(a));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.rightMargin = UiKit.dpI(a, 6);
        arrow.setLayoutParams(ap);
        header.addView(arrow);
        TextView t = UiKit.text(a, title, 16, UiKit.textMain(a));
        t.getPaint().setFakeBoldText(true);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                                                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(t);
        final TextView countView = UiKit.text(a, "", 12, UiKit.textSub(a));
        header.addView(countView);
        final LinearLayout content = UiKit.vbox(a);
        root.addView(header);
        root.addView(content, new LinearLayout.LayoutParams(
                         ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 有记忆就按记忆来，没有才用调用方给的默认值
        final boolean startExpanded = groupExpandedOf(title, expanded);
        final CardGroup group = new CardGroup(a, root, content, arrow, countView,
                                              startExpanded, title);
        if (startExpanded != expanded) {
            // 与默认值不同：构建完立即按记忆态摆好
            content.setVisibility(startExpanded ? View.VISIBLE : View.GONE);
            arrow.setRotation(startExpanded ? 90f : 0f);
        }
        header.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    group.toggle();
                }
            });
        header.setBackground(UiKit.pressedBg(a, 0x00000000,
                                             UiKit.cardPressed(a), UiKit.CARD_R));
        group.setExpanded(startExpanded, false);
        return group;
    }

    // ============================================================
    //  按钮
    // ============================================================
    public static View fallbackView(Activity a, String title, String desc,
                                    String actionText, View.OnClickListener action) {
        LinearLayout wrap = UiKit.vbox(a);
        int side = UiKit.dpI(a, UiKit.PAGE_MARGIN);
        wrap.setPadding(side, UiKit.dpI(a, 16), side, UiKit.dpI(a, 16));
        CardGroup card = cardGroup(a, title == null || title.length() == 0 ? "此分类" : title, true);
        TextView d = UiKit.text(a, desc == null ? "" : desc, 13, UiKit.textSub(a));
        d.setLineSpacing(UiKit.dp(a, 3), 1f);
        d.setPadding(UiKit.dpI(a, 14), UiKit.dpI(a, 4), UiKit.dpI(a, 14), UiKit.dpI(a, 12));
        card.content.addView(d);
        if (actionText != null && actionText.length() > 0 && action != null) {
            TextView btn = primaryButton(a, actionText, action);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.leftMargin = UiKit.dpI(a, 14);
            bp.rightMargin = UiKit.dpI(a, 14);
            bp.bottomMargin = UiKit.dpI(a, 14);
            btn.setLayoutParams(bp);
            card.content.addView(btn);
        }
        wrap.addView(card.root);
        return wrap;
    }

    /** 橙红实底胶囊按钮：危险/破坏性操作（清空、恢复出厂）*/
    public static TextView warnButton(Activity a, String text, View.OnClickListener l) {
        TextView b = UiKit.text(a, text, 15, 0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(UiKit.dpI(a, UiKit.BTN_H));
        b.setBackground(UiKit.pressedBg(a, UiKit.danger(a),
                                        UiKit.lighten(UiKit.danger(a), 0.15f), 12));
        if (l != null) b.setOnClickListener(l);
        pressZoom(b);
        return b;
    }

    /** 小胶囊按钮（工具条用） */
    public static TextView chipButton(Activity a, String text,
                                      View.OnClickListener l, boolean warn) {
        return chipButton(a, text, l, warn, 12);
    }

    /** 胶囊重载（可指定字号）：工具条 12sp / 标题栏返回键 15sp / 右下浮球 18sp */
    public static TextView chipButton(Activity a, String text,
                                      View.OnClickListener l, boolean warn, int textSp) {
        int color = warn ? UiKit.danger(a) : UiKit.accent(a);
        TextView c = UiKit.text(a, text, textSp, color);
        c.setTypeface(null, android.graphics.Typeface.BOLD);
        int hp = UiKit.dpI(a, 12);
        int vp = UiKit.dpI(a, 5);
        c.setPadding(hp, vp, hp, vp);
        c.setGravity(Gravity.CENTER);
        c.setBackground(UiKit.roundRect(a,
                                        UiKit.withAlpha(color, UiKit.isNight(a) ? 0x2E : 0x1F),
                                        UiKit.dp(a, 999)));
        if (l != null) {
            c.setClickable(true);
            c.setOnClickListener(l);
        }
        return c;
    }

    /**
     * 确认清除按钮文案
     */
    public static String clearLabel(int total, int picked) {
        return "确认清除 (将保留 " + (total - picked) + " 项，清除 " + picked + " 项)";
    }

    /** 多选操作条主胶囊文案*/
    public static String pickMainLabel(boolean all) {
        return all ? "取消" : "全选";
    }

    /** 多选操作条状态小字 */
    public static String pickStateLabel(int total, int picked) {
        return "待保留 " + (total - picked) + " 项，待清除 " + picked + " 项";
    }

    /** 多选模式隐藏行尾开关：点按整行即可切换勾选 */
    public static void hideRowSwitch(ViewGroup row) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View c = row.getChildAt(i);
            if (c instanceof MiniSwitch) c.setVisibility(View.GONE);
        }
    }

    public static TextView primaryButton(Activity a, String text, View.OnClickListener l) {
        TextView b = UiKit.text(a, text, 15, 0xFFFFFFFF);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(UiKit.dpI(a, UiKit.BTN_H));
        b.setBackground(UiKit.pressedBg(a, UiKit.accent(a),
                                        UiKit.lighten(UiKit.accent(a), 0.15f), 12));
        if (l != null) b.setOnClickListener(l);
        pressZoom(b);
        return b;
    }

    public static TextView ghostButton(Activity a, String text, View.OnClickListener l) {
        TextView b = UiKit.text(a, text, 15, UiKit.accent(a));
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(UiKit.dpI(a, UiKit.BTN_H));
        b.setBackground(UiKit.pressedBg(a, 0x00000000, UiKit.accentSoft(a), 12));
        if (l != null) b.setOnClickListener(l);
        pressZoom(b);
        return b;
    }

    public static TextView dangerButton(Activity a, String text, View.OnClickListener l) {
        TextView b = UiKit.text(a, text, 15, UiKit.danger(a));
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(UiKit.dpI(a, UiKit.BTN_H));
        b.setBackground(UiKit.pressedBg(a, 0x00000000,
                                        UiKit.withAlpha(UiKit.danger(a), 0x14), 12));
        if (l != null) b.setOnClickListener(l);
        pressZoom(b);
        return b;
    }

    // ============================================================
    //  搜索框
    // ============================================================
    public static EditText searchBox(Activity a, String hint, TextWatcher watcher) {
        EditText et = new EditText(a);
        et.setSingleLine(true);
        et.setHint(hint == null ? "搜索" : hint);
        et.setHintTextColor(UiKit.textSub(a));
        et.setTextColor(UiKit.textMain(a));
        et.setTextSize(14);
        et.setBackground(UiKit.roundRect(a, UiKit.card(a), 12));
        int padH = UiKit.dpI(a, 14);
        int padV = UiKit.dpI(a, 10);
        et.setPadding(padH, padV, padH, padV);
        if (watcher != null) et.addTextChangedListener(watcher);
        return et;
    }

    /** 搜索防抖监听*/
    public abstract static class SimpleWatcher implements TextWatcher {
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        public void afterTextChanged(Editable s) {
            onSearch(s == null ? "" : s.toString());
        }

        public abstract void onSearch(String keyword);
    }

    // ============================================================
    //  半屏抽屉统一骨架
    // ============================================================
    public static LinearLayout sheetRoot(Activity a) {
        LinearLayout root = UiKit.vbox(a);
        root.setBackground(UiKit.roundTop(a, UiKit.bg(a), UiKit.SHEET_R));
        LinearLayout handle = sheetHandle(a);
        PageStack stack = PageStack.get();
        if (stack != null) stack.bindSheetDrag(handle);
        root.addView(handle, new LinearLayout.LayoutParams(
                         ViewGroup.LayoutParams.MATCH_PARENT,
                         ViewGroup.LayoutParams.WRAP_CONTENT));
        return root;
    }

    /** 半屏页的标题行：「标题 + 关闭胶囊」。 */
    public static LinearLayout sheetHeader(Activity a, CharSequence title,
                                           View.OnClickListener onClose) {
        LinearLayout head = UiKit.hbox(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 4),
                        UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, 10));
        PageStack headStack = PageStack.get();
        if (headStack != null) headStack.bindSheetDrag(head);
        TextView t = UiKit.text(a, title == null ? "" : title.toString(), 17,
                                UiKit.textMain(a));
        t.getPaint().setFakeBoldText(true);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setLayoutParams(new LinearLayout.LayoutParams(0,
                                                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(t);
        TextView close = UiKit.text(a, "关闭", 12, UiKit.accent(a));
        int chp = UiKit.dpI(a, 12);
        int cvp = UiKit.dpI(a, 5);
        close.setPadding(chp, cvp, chp, cvp);
        close.setGravity(Gravity.CENTER);
        close.setBackground(UiKit.roundRect(a,
                                            UiKit.withAlpha(UiKit.accent(a), UiKit.isNight(a) ? 0x2E : 0x1F),
                                            UiKit.dp(a, 999)));
        close.setOnClickListener(onClose != null ? onClose : new View.OnClickListener() {
                                     public void onClick(View v) {
                                         PageStack s = PageStack.get();
                                         if (s != null) s.pop();
                                     }
                                 });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = UiKit.dpI(a, 8);
        head.addView(close, clp);
        return head;
    }

    // ============================================================
    //  半屏抽屉的顶部把手
    // ============================================================
    public static LinearLayout sheetHandle(Activity a) {
        LinearLayout wrap = UiKit.vbox(a);
        wrap.setGravity(Gravity.CENTER);
        // 整行（含小白条左右的空白）都是拖拽区，触控高度按 24dp
        wrap.setPadding(0, UiKit.dpI(a, 12), 0, UiKit.dpI(a, 8));
        wrap.setMinimumHeight(UiKit.dpI(a, 24));
        wrap.setClickable(true);
        View bar = new View(a);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            UiKit.dpI(a, 36), UiKit.dpI(a, 4));
        bar.setLayoutParams(bp);
        bar.setBackground(UiKit.roundRect(a, UiKit.divider(a), 2));
        wrap.addView(bar);
        return wrap;
    }

    /** 页面滚动容器*/
    public static View scroll(Activity a, View child) {
        return wrapWithTopButton(a, scroll(a, child, true), null);
    }

    /** 带状态记忆 key 的版本：重建后能滚回上次的位置*/
    public static View scroll(Activity a, View child, String stateKey) {
        return wrapWithTopButton(a, scroll(a, child, true), stateKey);
    }

    /** 不带页脚的滚动容器 */
    public static View scrollNoFooter(Activity a, View child) {
        return wrapWithTopButton(a, scroll(a, child, false), null);
    }

    public static View scrollNoFooter(Activity a, View child, String stateKey) {
        return wrapWithTopButton(a, scroll(a, child, false), stateKey);
    }

    /** 「回到顶部」浮球 */
    private static View wrapWithTopButton(final Activity a,
                                          final android.widget.ScrollView sv, final String stateKey) {
        final android.widget.FrameLayout wrap = new android.widget.FrameLayout(a);
        wrap.addView(sv, new android.widget.FrameLayout.LayoutParams(
                         ViewGroup.LayoutParams.MATCH_PARENT,
                         ViewGroup.LayoutParams.MATCH_PARENT));
        // 轻底圆胶囊
        int size = UiKit.dpI(a, 40);
        int m = UiKit.dpI(a, 16);
        final TextView btn = chipButton(a, "↑", null, false, 18);
        btn.setPadding(0, 0, 0, 0);
        btn.setAlpha(0f);
        btn.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(size, size);
        blp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        blp.rightMargin = m;
        blp.bottomMargin = m;
        wrap.addView(btn, blp);

        // 「滑到底部」浮球
        final TextView btnDown = chipButton(a, "↓", null, false, 18);
        btnDown.setPadding(0, 0, 0, 0);
        btnDown.setAlpha(0f);
        btnDown.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams dlp = new android.widget.FrameLayout.LayoutParams(size, size);
        dlp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        dlp.rightMargin = m;
        dlp.bottomMargin = m + size + UiKit.dpI(a, 8);
        wrap.addView(btnDown, dlp);
        btnDown.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        android.view.View c0 = sv.getChildAt(0);
                        if (c0 != null) {
                            int max = c0.getHeight() - sv.getHeight();
                            if (max > 0) sv.smoothScrollTo(0, max);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });

        btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try {
                        sv.smoothScrollTo(0, 0);
                    } catch (Throwable ignored) {
                    }
                }
            });

        // 两个浮球各自的显示状态
        final boolean[] upShown = new boolean[]{false};
        final boolean[] downShown = new boolean[]{false};
        final int threshold = UiKit.dpI(a, 180);
        if (stateKey != null) {
            final int restore = UiState.scroll(stateKey);
            if (restore > 0) {
                sv.post(new Runnable() {
                        public void run() {
                            try {
                                sv.scrollTo(0, restore);
                                if (restore > threshold) {
                                    btn.setAlpha(0f);
                                    btn.setVisibility(View.VISIBLE);
                                    upShown[0] = true;
                                    btn.animate().alpha(1f).setDuration(220).start();
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    });
            }
        }
        // 无滚动 3 秒后两个浮球整体淡出
        final Runnable hideBoth = new Runnable() {
            public void run() {
                if (btn.getVisibility() == View.VISIBLE) {
                    btn.animate().alpha(0f).setDuration(200)
                        .withEndAction(new Runnable() {
                            public void run() {
                                btn.setVisibility(View.GONE);
                                upShown[0] = false;
                            }
                        }).start();
                }
                if (btnDown.getVisibility() == View.VISIBLE) {
                    btnDown.animate().alpha(0f).setDuration(200)
                        .withEndAction(new Runnable() {
                            public void run() {
                                btnDown.setVisibility(View.GONE);
                                downShown[0] = false;
                            }
                        }).start();
                }
            }
        };

        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                public void onScrollChange(View v, int sx, int sy, int osx, int osy) {
                    // 滚动就重新计时：3 秒不动再隐藏（滚动中始终显示该出现的浮球）
                    sv.removeCallbacks(hideBoth);
                    sv.postDelayed(hideBoth, 3000);
                    PageStack ps = PageStack.get();
                    if (ps != null) ps.growSheetToContent(sv);
                    if (stateKey != null) UiState.setScroll(stateKey, sy);
                    boolean show = sy > threshold;
                    if (show != upShown[0]) {
                        upShown[0] = show;
                        if (show) {
                            btn.setAlpha(0f);
                            btn.setVisibility(View.VISIBLE);
                            btn.animate().alpha(1f).setDuration(220).start();
                        } else if (btn.getVisibility() == View.VISIBLE) {
                            btn.animate().alpha(0f).setDuration(200)
                                .withEndAction(new Runnable() {
                                    public void run() {
                                        btn.setVisibility(View.GONE);
                                    }
                                }).start();
                        }
                    } else if (upShown[
                               0] && btn.getVisibility() == View.VISIBLE && btn.getAlpha() < 1f) {
                        btn.animate().cancel();
                        btn.setAlpha(1f);
                    }
                    // ↓：内容比视口长、且离底部还远
                    android.view.View c0 = sv.getChildAt(0);
                    boolean canDown = c0 != null
                        && c0.getHeight() > sv.getHeight() + threshold
                        && sy + sv.getHeight() < c0.getHeight() - threshold;
                    if (canDown != downShown[0]) {
                        downShown[0] = canDown;
                        if (canDown) {
                            btnDown.setAlpha(0f);
                            btnDown.setVisibility(View.VISIBLE);
                            btnDown.animate().alpha(1f).setDuration(220).start();
                        } else if (btnDown.getVisibility() == View.VISIBLE) {
                            btnDown.animate().alpha(0f).setDuration(200)
                                .withEndAction(new Runnable() {
                                    public void run() {
                                        btnDown.setVisibility(View.GONE);
                                    }
                                }).start();
                        }
                    } else if (downShown[0] && btnDown.getVisibility() == View.VISIBLE
                               && btnDown.getAlpha() < 1f) {
                        btnDown.animate().cancel();
                        btnDown.setAlpha(1f);
                    }
                }
            });
        // 首屏内容比视口长时先把 ↓ 按钮请出来
        sv.post(new Runnable() {
                public void run() {
                    try {
                        android.view.View c0 = sv.getChildAt(0);
                        if (c0 != null && c0.getHeight() > sv.getHeight() + threshold) {
                            btnDown.setAlpha(0f);
                            btnDown.setVisibility(View.VISIBLE);
                            downShown[0] = true;
                            btnDown.animate().alpha(1f).setDuration(220).start();
                        }
                        sv.postDelayed(hideBoth, 3000);
                    } catch (Throwable ignored) {
                    }
                }
            });
        return wrap;
    }

    private static android.widget.ScrollView scroll(final Activity a, final View child,
                                                    boolean withFooter) {
        android.widget.ScrollView sv = new android.widget.ScrollView(a);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        if (child != null) {
            // 所有页面统一的页脚
            if (withFooter && child instanceof LinearLayout) {
                try {
                    ((LinearLayout) child).addView(UiTabs.footer(a),
                                                   new LinearLayout.LayoutParams(
                                                       ViewGroup.LayoutParams.MATCH_PARENT,
                                                       ViewGroup.LayoutParams.WRAP_CONTENT));
                } catch (Throwable ignored) {
                }
            }
            // 横屏/平板：内容限宽居中
            LinearLayout holder = UiKit.vbox(a);
            holder.setGravity(Gravity.CENTER_HORIZONTAL);
            int maxW = Math.min(UiKit.screenW(a), UiKit.contentMaxW(a));
            child.setLayoutParams(new LinearLayout.LayoutParams(
                                      maxW, ViewGroup.LayoutParams.WRAP_CONTENT));
            holder.addView(child);
            sv.addView(holder, new android.widget.FrameLayout.LayoutParams(
                           ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            childForFadeIn(a, child);
        }
        return sv;
    }

    /* 两步确认包装  */
    public static View.OnClickListener confirm(final String tip,
                                               final View.OnClickListener action) {
        return new View.OnClickListener() {
            private final boolean[] armed = new boolean[1];
            private Runnable reset;

            public void onClick(final View v) {
                if (armed[0]) {
                    armed[0] = false;
                    if (reset != null) v.removeCallbacks(reset);
                    action.onClick(v);
                } else {
                    armed[0] = true;
                    com.install.appinstall.xl.util.ToastUtil.showUnique(v.getContext(), tip);
                    if (reset != null) v.removeCallbacks(reset);
                    reset = new Runnable() {
                        public void run() {
                            armed[0] = false;
                        }
                    };
                    v.postDelayed(reset, 3000);
                }
            }
        };
    }

    /** 两步确认按钮 */
    public static void confirmButton(final TextView btn, final String confirmText,
                                     final View.OnClickListener action) {
        final android.graphics.drawable.Drawable origBg = btn.getBackground();
        final int origColor = btn.getTextColors() != null
            ? btn.getTextColors().getDefaultColor() : 0xFF333333;
        final String origText = btn.getText() != null ? btn.getText().toString() : "";
        btn.setOnClickListener(new View.OnClickListener() {
                private final boolean[] armed = new boolean[1];
                private Runnable reset;

                public void onClick(final View v) {
                    if (armed[0]) {
                        armed[0] = false;
                        if (reset != null) v.removeCallbacks(reset);
                        restore();
                        action.onClick(v);
                    } else {
                        armed[0] = true;
                        UiKit.haptic(v);
                        btn.setText(confirmText);
                        android.content.Context ctx = v.getContext();
                        btn.setBackground(UiKit.pressedBg(ctx, UiKit.danger(ctx),
                                                          UiKit.lighten(UiKit.danger(ctx), 0.15f), 12));
                        btn.setTextColor(0xFFFFFFFF);
                        if (reset != null) v.removeCallbacks(reset);
                        reset = new Runnable() {
                            public void run() {
                                armed[0] = false;
                                restore();
                            }
                        };
                        v.postDelayed(reset, UiKit.TWO_STEP_ARM_MS);
                    }
                }

                private void restore() {
                    btn.setBackground(origBg);
                    btn.setTextColor(origColor);
                    btn.setText(origText);
                }
            });
    }

    /** 内容入场动画：淡入 + 轻微上移*/
    private static void childForFadeIn(final Activity a, final View child) {
        if (child == null) return;
        try {
            child.setAlpha(0f);
            child.setTranslationY(UiKit.dp(a, 10));
            child.post(new Runnable() {
                    public void run() {
                        try {
                            child.animate().alpha(1f).translationY(0f)
                                .setDuration(220)
                                .setInterpolator(
                                new android.view.animation.DecelerateInterpolator())
                                .start();
                        } catch (Throwable ignored) {
                        }
                    }
                });
        } catch (Throwable t) {
            try {
                child.setAlpha(1f);
                child.setTranslationY(0f);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 标准页面骨架：背景 + 垂直布局（顶栏 + 内容），内容已带页边距 */
    public static LinearLayout pageRoot(Activity a) {
        LinearLayout root = UiKit.vbox(a);
        root.setBackgroundColor(UiKit.bg(a));
        return root;
    }

    /** 带页边距的内容容器 */
    public static LinearLayout paddedContent(Activity a) {
        LinearLayout c = UiKit.vbox(a);
        c.setPadding(UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, UiKit.PAGE_MARGIN),
                     UiKit.dpI(a, UiKit.PAGE_MARGIN), UiKit.dpI(a, UiKit.PAGE_MARGIN));
        return c;
    }

    //  文本框选工具 */
    public static TextView subTitleOf(View row) {
        if (!(row instanceof ViewGroup)) return null;
        View title = row.findViewWithTag("row_title");
        if (title == null || !(title.getParent() instanceof ViewGroup)) return null;
        ViewGroup col = (ViewGroup) title.getParent();
        if (col.getChildCount() > 1 && col.getChildAt(1) instanceof TextView) {
            return (TextView) col.getChildAt(1);
        }
        return null;
    }

    /** 给 settingRow 的副文本开启框选 */
    public static View selectableSub(View row) {
        TextView sub = subTitleOf(row);
        if (sub != null) selectable(sub);
        return row;
    }

    /** 递归设置文本长按系统框选 */
    public static void selectable(View v) {
        if (v instanceof TextView) {
            try {
                ((TextView) v).setTextIsSelectable(true);
            } catch (Throwable ignored) {
            }
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                selectable(g.getChildAt(i));
            }
        }
    }
    // ==================== 重启提醒（全局共用入口） ====================
    /** 需要重启才生效：过程中只轻提醒，等页面全关掉时统一弹一次 */
    static boolean sNeedRestart;

    /** 用弱引用持有：宿主 Activity 销毁后仍被静态字段钉着会泄漏 */
    static java.lang.ref.WeakReference<Activity> sLastActivityRef;

    /** 开关切换后调用：先轻提示一次，并标记待重启 */
    public static void restartHint(final Activity a) {
        sNeedRestart = true;
        sLastActivityRef = new java.lang.ref.WeakReference<Activity>(a);
        try {
            ToastUtil.showUnique(a, "需要重启目标应用后生效");
        } catch (Throwable ignored) {
        }
    }

    /** 由 PageStack 在页面全部关闭后调用，保证只弹一次 */
    public static void flushRestartHint() {
        if (!sNeedRestart) return;
        sNeedRestart = false;
        final Activity a = sLastActivityRef != null ? sLastActivityRef.get() : null;
        sLastActivityRef = null;
        if (a == null) return;
        // 页面栈关闭总是滞后于宿主 Activity 销毁若干毫秒：若此刻 Activity 已死，对话框弹在已销毁的 Activity 上
        if (a.isDestroyed()) return;
        try {
            a.runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            DialogUi.showRestartConfirmDialog(a);
                        } catch (Throwable ignored) {
                        }
                    }
                });
        } catch (Throwable ignored) {
        }
    }

}

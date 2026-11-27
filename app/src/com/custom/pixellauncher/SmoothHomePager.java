package com.custom.pixellauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.HorizontalScrollView;

/**
 * Custom paged HorizontalScrollView for smooth, snap-accurate Pixel-style home screen paging.
 * Disables native OverScroller coasting and drives transitions with VelocityTracker + quintic ease-out.
 */
public class SmoothHomePager extends HorizontalScrollView {

    public interface OnPageSnapListener {
        void onSnapToPage(int targetPage, float velocityPxPerSec);
    }

    public interface PagingMetricsProvider {
        int getPageWidth();
        int getPageCount();
    }

    private static final TimeInterpolator QUINTIC_EASE_OUT = new TimeInterpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private int touchSlop;
    private int minFlingVelocity;
    private int maxFlingVelocity;

    private float downX;
    private float downY;
    private float lastMoveX;
    private int downScrollX;
    private int startPage;
    private boolean isPaging = false;
    private boolean dragBlocked = false;
    private float overscrollPx = 0f;
    private float maxOverscrollPx = 42f;

    private VelocityTracker velocityTracker;
    private ValueAnimator snapAnimator;
    private OnPageSnapListener onPageSnapListener;
    private PagingMetricsProvider metricsProvider;

    public SmoothHomePager(Context context) {
        super(context);
        init(context);
    }

    public SmoothHomePager(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SmoothHomePager(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        ViewConfiguration vc = ViewConfiguration.get(context);
        touchSlop = vc.getScaledTouchSlop();
        minFlingVelocity = Math.max(vc.getScaledMinimumFlingVelocity() * 2, 220);
        maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        maxOverscrollPx = 26f * context.getResources().getDisplayMetrics().density;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private void applyOverscrollTranslation(float tx) {
        overscrollPx = tx;
        if (getChildCount() > 0) {
            getChildAt(0).setTranslationX(tx);
        }
    }

    private void releaseOverscrollSpring() {
        if (getChildCount() == 0) {
            overscrollPx = 0f;
            return;
        }
        final View child = getChildAt(0);
        final float startTx = child.getTranslationX();
        if (Math.abs(overscrollPx) > 0.5f || Math.abs(startTx) > 0.5f) {
            overscrollPx = 0f;
            cancelSnapAnimation();
            final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
            ValueAnimator va = ValueAnimator.ofFloat(startTx, 0f);
            va.setDuration(DisplayRefreshHelper.scaleDurationMs(220));
            va.setInterpolator(new OvershootInterpolator(DisplayRefreshHelper.scaleOvershootTension(1.30f)));
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    if (!pacer.shouldRender(animation)) return;
                    child.setTranslationX((float) animation.getAnimatedValue());
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    child.setTranslationX(0f);
                }
            });
            snapAnimator = va;
            va.start();
        } else {
            overscrollPx = 0f;
            child.setTranslationX(0f);
        }
    }

    public void setOnPageSnapListener(OnPageSnapListener listener) {
        this.onPageSnapListener = listener;
    }

    public void setPagingMetricsProvider(PagingMetricsProvider provider) {
        this.metricsProvider = provider;
    }

    public void setDragBlocked(boolean blocked) {
        this.dragBlocked = blocked;
        if (blocked) {
            isPaging = false;
            recycleVelocityTracker();
        }
    }

    public boolean isPaging() {
        return isPaging;
    }

    public boolean isAnimatingSnap() {
        return snapAnimator != null && snapAnimator.isRunning();
    }

    @Override
    public void fling(int velocityX) {
        // Intentionally disabled: page transitions are driven deterministically on release.
    }

    private int getResolvedPageWidth() {
        if (metricsProvider != null) {
            int w = metricsProvider.getPageWidth();
            if (w > 0) return w;
        }
        return Math.max(1, getWidth());
    }

    private int getResolvedPageCount() {
        if (metricsProvider != null) {
            int c = metricsProvider.getPageCount();
            if (c > 0) return c;
        }
        if (getChildCount() > 0 && getChildAt(0) instanceof ViewGroup) {
            return Math.max(1, ((ViewGroup) getChildAt(0)).getChildCount());
        }
        return 1;
    }

    private int clampPage(int page) {
        int count = getResolvedPageCount();
        return Math.max(0, Math.min(count - 1, page));
    }

    private void initOrResetVelocityTracker(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(ev);
    }

    private void addMovementToTracker(MotionEvent ev) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);
    }

    private void recycleVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker.recycle();
            velocityTracker = null;
        }
    }

    public void cancelSnapAnimation() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
    }

    private void setPagesHardwareLayer(boolean enabled) {
        if (getChildCount() == 0 || !(getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup container = (ViewGroup) getChildAt(0);
        int layerType = enabled ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
        for (int i = 0; i < container.getChildCount(); i++) {
            View page = container.getChildAt(i);
            if (page.getLayerType() != layerType) {
                page.setLayerType(layerType, null);
            }
        }
    }

    public void smoothScrollToPage(final int targetPage, final float velocityPxPerSec) {
        final int clampedPage = clampPage(targetPage);
        if (!isLaidOut()) {
            post(new Runnable() {
                @Override
                public void run() {
                    scrollTo(clampedPage * getResolvedPageWidth(), 0);
                }
            });
            return;
        }
        int pageW = getResolvedPageWidth();
        int targetX = clampedPage * pageW;
        int currentX = getScrollX();

        cancelSnapAnimation();
        if (currentX == targetX) {
            setPagesHardwareLayer(false);
            return;
        }

        int distance = Math.abs(targetX - currentX);
        int duration = computeScrollDuration(distance, pageW, Math.abs(velocityPxPerSec));

        DisplayRefreshHelper.requestInteractiveBoost(getContext(), true);
        DisplayRefreshHelper.setFrameContentVelocity(this, Math.abs(velocityPxPerSec) > 10f ? velocityPxPerSec : distance * 4f);
        setPagesHardwareLayer(true);
        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        snapAnimator = ValueAnimator.ofInt(currentX, targetX);
        snapAnimator.setDuration(duration);
        snapAnimator.setInterpolator(QUINTIC_EASE_OUT);
        snapAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!pacer.shouldRender(animation)) return;
                scrollTo((int) animation.getAnimatedValue(), 0);
            }
        });
        snapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                snapAnimator = null;
                setPagesHardwareLayer(false);
                DisplayRefreshHelper.setFrameContentVelocity(SmoothHomePager.this, 0f);
                DisplayRefreshHelper.requestInteractiveBoost(getContext(), false);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                setPagesHardwareLayer(false);
                DisplayRefreshHelper.setFrameContentVelocity(SmoothHomePager.this, 0f);
                DisplayRefreshHelper.requestInteractiveBoost(getContext(), false);
            }
        });
        snapAnimator.start();
    }

    private int computeScrollDuration(int distancePx, int pageW, float absVelocity) {
        float halfW = Math.max(1f, pageW * 0.5f);
        float distanceRatio = Math.min(1.0f, distancePx / (float) Math.max(1, pageW));
        int baseDuration;
        if (absVelocity > minFlingVelocity) {
            float effectiveVel = Math.min(absVelocity, maxFlingVelocity * 0.6f);
            baseDuration = Math.round(1000f * (distancePx / Math.max(effectiveVel, halfW * 3.0f)));
            baseDuration = Math.max(170, Math.min(270, baseDuration + 100));
        } else {
            baseDuration = Math.round(160 + distanceRatio * 115);
        }
        return DisplayRefreshHelper.scaleDurationMs(Math.max(160, Math.min(280, baseDuration)));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (dragBlocked || getResolvedPageCount() <= 1) {
            isPaging = false;
            return false;
        }

        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                cancelSnapAnimation();
                downX = ev.getX();
                downY = ev.getY();
                lastMoveX = downX;
                downScrollX = getScrollX();
                startPage = clampPage(Math.round((float) downScrollX / getResolvedPageWidth()));
                isPaging = false;
                initOrResetVelocityTracker(ev);
                break;

            case MotionEvent.ACTION_MOVE:
                addMovementToTracker(ev);
                float dx = Math.abs(ev.getX() - downX);
                float dy = Math.abs(ev.getY() - downY);
                if (dx > touchSlop && dx > dy * 0.85f) {
                    isPaging = true;
                    lastMoveX = ev.getX();
                    setPagesHardwareLayer(true);
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                float endDx = Math.abs(ev.getX() - downX);
                float endDy = Math.abs(ev.getY() - downY);
                boolean movedOnEnd = (action == MotionEvent.ACTION_UP)
                        && (Math.hypot(endDx, endDy) > touchSlop);
                isPaging = false;
                recycleVelocityTracker();
                setPagesHardwareLayer(false);
                if (movedOnEnd) {
                    return true;
                }
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (dragBlocked || getResolvedPageCount() <= 1) {
            isPaging = false;
            return false;
        }

        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                cancelSnapAnimation();
                downX = ev.getX();
                downY = ev.getY();
                lastMoveX = downX;
                downScrollX = getScrollX();
                startPage = clampPage(Math.round((float) downScrollX / getResolvedPageWidth()));
                isPaging = false;
                initOrResetVelocityTracker(ev);
                return true;

            case MotionEvent.ACTION_MOVE:
                addMovementToTracker(ev);
                float x = ev.getX();
                if (!isPaging) {
                    float dx = Math.abs(x - downX);
                    float dy = Math.abs(ev.getY() - downY);
                    if (dx > touchSlop && dx > dy * 1.05f) {
                        isPaging = true;
                        lastMoveX = x;
                        setPagesHardwareLayer(true);
                        if (getParent() != null) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                if (isPaging) {
                    float deltaX = lastMoveX - x;
                    lastMoveX = x;
                    int pageW = getResolvedPageWidth();
                    int maxScrollX = Math.max(0, (getResolvedPageCount() - 1) * pageW);
                    int curX = getScrollX();

                    if (overscrollPx > 0.5f && deltaX > 0) {
                        float nextOver = Math.max(0f, overscrollPx - deltaX);
                        applyOverscrollTranslation(nextOver);
                        return true;
                    } else if (overscrollPx < -0.5f && deltaX < 0) {
                        float nextOver = Math.min(0f, overscrollPx - deltaX);
                        applyOverscrollTranslation(nextOver);
                        return true;
                    }

                    int rawNextX = Math.round(curX + deltaX);
                    if (rawNextX < 0) {
                        scrollTo(0, 0);
                        float over = Math.min(maxOverscrollPx, overscrollPx + (-rawNextX) * 0.28f);
                        applyOverscrollTranslation(over);
                    } else if (rawNextX > maxScrollX) {
                        scrollTo(maxScrollX, 0);
                        float over = Math.max(-maxOverscrollPx, overscrollPx - (rawNextX - maxScrollX) * 0.28f);
                        applyOverscrollTranslation(over);
                    } else {
                        if (overscrollPx != 0f) applyOverscrollTranslation(0f);
                        scrollTo(rawNextX, 0);
                    }
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                addMovementToTracker(ev);
                float vx = 0f;
                if (velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                    vx = velocityTracker.getXVelocity();
                }
                boolean hadOverscroll = Math.abs(overscrollPx) > 0.5f;
                releaseOverscrollSpring();
                boolean wasPaging = isPaging || getScrollX() != startPage * getResolvedPageWidth();
                isPaging = false;
                recycleVelocityTracker();
                if (wasPaging && !hadOverscroll) {
                    int targetPage = resolveTargetPage(vx);
                    if (onPageSnapListener != null) {
                        onPageSnapListener.onSnapToPage(targetPage, vx);
                    } else {
                        smoothScrollToPage(targetPage, vx);
                    }
                    return true;
                }
                setPagesHardwareLayer(false);
                if (hadOverscroll) return true;
                break;
        }
        return true;
    }

    private int resolveTargetPage(float velocityX) {
        int pageW = getResolvedPageWidth();
        int count = getResolvedPageCount();
        int curScroll = getScrollX();
        int deltaScroll = curScroll - (startPage * pageW);

        int targetPage = startPage;
        if (Math.abs(velocityX) >= minFlingVelocity) {
            if (velocityX > 0) {
                targetPage = (curScroll <= startPage * pageW)
                        ? startPage - 1
                        : (int) Math.floor((float) curScroll / pageW);
            } else {
                targetPage = (curScroll >= startPage * pageW)
                        ? startPage + 1
                        : (int) Math.ceil((float) curScroll / pageW);
            }
        } else {
            if (deltaScroll <= -(pageW * 0.22f)) {
                targetPage = startPage - 1;
            } else if (deltaScroll >= (pageW * 0.22f)) {
                targetPage = startPage + 1;
            }
        }
        return Math.max(0, Math.min(count - 1, targetPage));
    }
}

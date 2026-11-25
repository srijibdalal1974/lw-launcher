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
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
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

        setPagesHardwareLayer(true);
        snapAnimator = ValueAnimator.ofInt(currentX, targetX);
        snapAnimator.setDuration(duration);
        snapAnimator.setInterpolator(QUINTIC_EASE_OUT);
        snapAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                scrollTo((int) animation.getAnimatedValue(), 0);
            }
        });
        snapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                snapAnimator = null;
                setPagesHardwareLayer(false);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                setPagesHardwareLayer(false);
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
        return Math.max(160, Math.min(280, baseDuration));
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
                if (dx > touchSlop && dx > dy * 1.05f) {
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
                isPaging = false;
                recycleVelocityTracker();
                setPagesHardwareLayer(false);
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
                    int nextX = Math.max(0, Math.min(maxScrollX, Math.round(getScrollX() + deltaX)));
                    scrollTo(nextX, 0);
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
                boolean wasPaging = isPaging || getScrollX() != startPage * getResolvedPageWidth();
                isPaging = false;
                recycleVelocityTracker();
                if (wasPaging) {
                    int targetPage = resolveTargetPage(vx);
                    if (onPageSnapListener != null) {
                        onPageSnapListener.onSnapToPage(targetPage, vx);
                    } else {
                        smoothScrollToPage(targetPage, vx);
                    }
                    return true;
                }
                setPagesHardwareLayer(false);
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

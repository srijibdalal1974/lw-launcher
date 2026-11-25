package com.custom.pixellauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.GridView;

/**
 * Interactive Pixel-style App Drawer controller supporting real-time vertical finger tracking,
 * mid-air holding, velocity-aware opening/closing, and conflict-free GridView scrolling.
 */
public class AppDrawerController {

    public interface Host {
        boolean canStartOpeningDrawer();
        void onDrawerDragStarted();
        void onDrawerDragEnded();
        void dispatchCancelToChildren(MotionEvent cancelEvent);
        void hideKeyboard();
        int getScreenHeight();
    }

    private static final int DRAG_NONE = 0;
    private static final int DRAG_OPENING = 1;
    private static final int DRAG_CLOSING = 2;

    private static final TimeInterpolator QUINTIC_EASE_OUT = new TimeInterpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private final View homeScreen;
    private final View appDrawer;
    private final GridView gvApps;
    private final EditText etDrawerSearch;
    private final Host host;

    private final int touchSlop;
    private final int minFlingVelocity;
    private final int maxFlingVelocity;

    private VelocityTracker velocityTracker;
    private ValueAnimator drawerAnimator;
    private boolean isAnimatingToOpen = false;
    private boolean isAnimatingToClose = false;

    private boolean isDragging = false;
    private int dragMode = DRAG_NONE;
    private boolean eligibleForOpenDrag = false;
    private boolean eligibleForCloseDrag = false;
    private boolean isDispatchingSyntheticCancel = false;

    private float downRawX;
    private float downRawY;
    private float dragAnchorRawY;
    private float dragInitialTransY;

    public AppDrawerController(
            Context context,
            View homeScreen,
            View appDrawer,
            GridView gvApps,
            EditText etDrawerSearch,
            Host host) {
        this.homeScreen = homeScreen;
        this.appDrawer = appDrawer;
        this.gvApps = gvApps;
        this.etDrawerSearch = etDrawerSearch;
        this.host = host;

        ViewConfiguration vc = ViewConfiguration.get(context);
        this.touchSlop = vc.getScaledTouchSlop();
        this.minFlingVelocity = Math.max(vc.getScaledMinimumFlingVelocity() * 4, 420);
        this.maxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    }

    public boolean isOpen() {
        return appDrawer != null && appDrawer.getVisibility() == View.VISIBLE && !isAnimatingToClose;
    }

    public boolean isVisibleOrAnimating() {
        return appDrawer != null && appDrawer.getVisibility() == View.VISIBLE;
    }

    public boolean isDragging() {
        return isDragging;
    }

    /**
     * Checks whether the app drawer GridView is scrolled strictly to row 0 top.
     */
    public boolean isDrawerGridAtTop() {
        if (gvApps == null || gvApps.getCount() == 0 || gvApps.getChildCount() == 0) {
            return true;
        }
        if (gvApps.getFirstVisiblePosition() > 0) {
            return false;
        }
        View firstChild = gvApps.getChildAt(0);
        return firstChild == null || firstChild.getTop() >= gvApps.getPaddingTop() - 2;
    }

    public boolean onDispatchTouchEvent(MotionEvent ev) {
        if (isDispatchingSyntheticCancel || appDrawer == null || homeScreen == null) {
            return false;
        }

        final int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                handleActionDown(ev);
                return false;

            case MotionEvent.ACTION_MOVE:
                return handleActionMove(ev);

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return handleActionRelease(ev);
        }
        return isDragging;
    }

    private void handleActionDown(MotionEvent ev) {
        downRawX = ev.getRawX();
        downRawY = ev.getRawY();
        isDragging = false;
        dragMode = DRAG_NONE;
        eligibleForOpenDrag = false;
        eligibleForCloseDrag = false;
        initOrResetVelocityTracker(ev);

        boolean drawerShowing = (appDrawer.getVisibility() == View.VISIBLE);
        boolean animRunning = (drawerAnimator != null && drawerAnimator.isRunning());

        if (animRunning) {
            // Catch the app drawer cleanly mid-animation so the user can hold it mid-air
            boolean wasOpening = isAnimatingToOpen;
            cancelAnimator();
            isDragging = true;
            dragMode = wasOpening ? DRAG_OPENING : DRAG_CLOSING;
            dragAnchorRawY = downRawY;
            dragInitialTransY = appDrawer.getTranslationY();
            enableHardwareLayers(true);
            homeScreen.setVisibility(View.VISIBLE);
            host.onDrawerDragStarted();
            return;
        }

        if (!drawerShowing) {
            if (host.canStartOpeningDrawer()) {
                eligibleForOpenDrag = true;
            }
        } else {
            boolean startedInHeader = isTouchAboveGrid(downRawY);
            if (startedInHeader || isDrawerGridAtTop()) {
                eligibleForCloseDrag = true;
            }
        }
    }

    private boolean isTouchAboveGrid(float rawY) {
        if (gvApps == null) return true;
        int[] loc = new int[2];
        gvApps.getLocationOnScreen(loc);
        return rawY < loc[1];
    }

    private boolean handleActionMove(MotionEvent ev) {
        addMovementToTracker(ev);
        float rawX = ev.getRawX();
        float rawY = ev.getRawY();
        float dx = rawX - downRawX;
        float dy = rawY - downRawY;

        if (!isDragging) {
            if (eligibleForOpenDrag) {
                if (!host.canStartOpeningDrawer()
                        || (Math.abs(dx) > touchSlop && Math.abs(dx) >= Math.abs(dy))) {
                    eligibleForOpenDrag = false;
                    return false;
                }
                if (dy < -touchSlop && Math.abs(dy) > Math.abs(dx) * 1.25f) {
                    beginInteractiveOpen(ev);
                    return true;
                }
            } else if (eligibleForCloseDrag) {
                if (dy < -touchSlop) {
                    // User is swiping bottom-to-top to scroll down inside GridView: lock out drawer closing
                    eligibleForCloseDrag = false;
                    return false;
                }
                if (Math.abs(dx) > touchSlop && Math.abs(dx) >= Math.abs(dy)) {
                    eligibleForCloseDrag = false;
                    return false;
                }
                if (dy > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.25f) {
                    beginInteractiveClose(ev);
                    return true;
                }
            }
            return false;
        }

        float screenH = Math.max(1f, host.getScreenHeight());
        float dragScale = (dragMode == DRAG_OPENING) ? 1.25f : 1.0f;
        float newTransY = clamp(dragInitialTransY + (rawY - dragAnchorRawY) * dragScale, 0f, screenH);
        applyTransitionForTranslationY(newTransY, screenH);
        return true;
    }

    private void beginInteractiveOpen(MotionEvent currentEv) {
        int screenH = Math.max(1, host.getScreenHeight());
        isDragging = true;
        dragMode = DRAG_OPENING;
        eligibleForOpenDrag = false;
        dragAnchorRawY = downRawY;
        dragInitialTransY = screenH;

        resetDrawerSearchAndScrollTop();
        appDrawer.setVisibility(View.VISIBLE);
        homeScreen.setVisibility(View.VISIBLE);
        enableHardwareLayers(true);
        float startTransY = clamp(screenH + (currentEv.getRawY() - dragAnchorRawY) * 1.25f, 0f, screenH);
        applyTransitionForTranslationY(startTransY, screenH);

        host.onDrawerDragStarted();
        sendSyntheticCancel(currentEv);
    }

    private void beginInteractiveClose(MotionEvent currentEv) {
        isDragging = true;
        dragMode = DRAG_CLOSING;
        eligibleForCloseDrag = false;
        dragAnchorRawY = downRawY;
        dragInitialTransY = appDrawer.getTranslationY();

        host.hideKeyboard();
        homeScreen.setVisibility(View.VISIBLE);
        enableHardwareLayers(true);
        float screenH = Math.max(1f, host.getScreenHeight());
        applyTransitionForTranslationY(clamp(dragInitialTransY + (currentEv.getRawY() - dragAnchorRawY), 0f, screenH), screenH);

        host.onDrawerDragStarted();
        sendSyntheticCancel(currentEv);
    }

    private boolean handleActionRelease(MotionEvent ev) {
        boolean wasDragging = isDragging;
        float vy = 0f;
        if (wasDragging) {
            addMovementToTracker(ev);
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000, maxFlingVelocity);
                vy = velocityTracker.getYVelocity();
            }
        }

        int modeAtRelease = dragMode;
        isDragging = false;
        dragMode = DRAG_NONE;
        eligibleForOpenDrag = false;
        eligibleForCloseDrag = false;
        recycleVelocityTracker();

        if (!wasDragging) {
            return false;
        }

        host.onDrawerDragEnded();

        int screenH = Math.max(1, host.getScreenHeight());
        float curTransY = appDrawer.getTranslationY();
        float openFraction = 1f - (curTransY / screenH);

        boolean shouldOpen;
        if (Math.abs(vy) >= minFlingVelocity) {
            // Upward flick (vy < 0) always opens; downward flick (vy > 0) closes
            shouldOpen = (vy < 0);
        } else {
            if (modeAtRelease == DRAG_CLOSING) {
                shouldOpen = (openFraction >= 0.72f);
            } else {
                shouldOpen = (openFraction >= 0.18f);
            }
        }

        animateDrawerTo(shouldOpen, vy);
        return true;
    }

    public void open() {
        if (appDrawer == null || homeScreen == null) return;
        if (appDrawer.getVisibility() == View.VISIBLE && isAnimatingToOpen) return;
        if (appDrawer.getVisibility() == View.VISIBLE && !isAnimatingToClose && appDrawer.getTranslationY() <= 1f) {
            resetDrawerSearchAndScrollTop();
            return;
        }

        int screenH = Math.max(1, host.getScreenHeight());
        if (appDrawer.getVisibility() != View.VISIBLE) {
            resetDrawerSearchAndScrollTop();
            appDrawer.setVisibility(View.VISIBLE);
            applyTransitionForTranslationY(screenH, screenH);
        }
        homeScreen.setVisibility(View.VISIBLE);
        animateDrawerTo(true, 0f);
    }

    public void close() {
        if (appDrawer == null || homeScreen == null) return;
        if (appDrawer.getVisibility() != View.VISIBLE) return;
        if (isAnimatingToClose) return;

        host.hideKeyboard();
        homeScreen.setVisibility(View.VISIBLE);
        animateDrawerTo(false, 0f);
    }

    private void animateDrawerTo(final boolean open, float velocityY) {
        final int screenH = Math.max(1, host.getScreenHeight());
        final float startTransY = appDrawer.getTranslationY();
        final float targetTransY = open ? 0f : screenH;

        cancelAnimator();

        if (Math.abs(targetTransY - startTransY) < 1f) {
            applyTransitionForTranslationY(targetTransY, screenH);
            onDrawerAnimationSettled(open);
            return;
        }

        float dist = Math.abs(targetTransY - startTransY);
        int duration = computeSettleDuration(dist, screenH, Math.abs(velocityY));

        isAnimatingToOpen = open;
        isAnimatingToClose = !open;
        homeScreen.setVisibility(View.VISIBLE);
        enableHardwareLayers(true);

        drawerAnimator = ValueAnimator.ofFloat(startTransY, targetTransY);
        drawerAnimator.setDuration(duration);
        drawerAnimator.setInterpolator(QUINTIC_EASE_OUT);
        drawerAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float transY = (float) animation.getAnimatedValue();
                applyTransitionForTranslationY(transY, screenH);
            }
        });
        drawerAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                drawerAnimator = null;
                isAnimatingToOpen = false;
                isAnimatingToClose = false;
                if (!canceled) {
                    applyTransitionForTranslationY(targetTransY, screenH);
                    onDrawerAnimationSettled(open);
                }
                if (!isDragging) {
                    enableHardwareLayers(false);
                }
            }
        });
        drawerAnimator.start();
    }

    private void onDrawerAnimationSettled(boolean open) {
        if (open) {
            appDrawer.setVisibility(View.VISIBLE);
            homeScreen.setVisibility(View.INVISIBLE);
        } else {
            appDrawer.setVisibility(View.GONE);
            homeScreen.setVisibility(View.VISIBLE);
            resetDrawerSearchAndScrollTop();
        }
    }

    private void applyTransitionForTranslationY(float transY, float screenH) {
        appDrawer.setTranslationY(transY);
        float openFraction = clamp(1f - (transY / Math.max(1f, screenH)), 0f, 1f);
        appDrawer.setAlpha(clamp(0.55f + 0.45f * Math.min(1f, openFraction * 2.2f), 0f, 1f));

        float homeScale = 1.0f - (0.07f * openFraction);
        homeScreen.setScaleX(homeScale);
        homeScreen.setScaleY(homeScale);
        homeScreen.setAlpha(1.0f - (0.70f * openFraction));
    }

    private int computeSettleDuration(float distancePx, float screenH, float absVelocityY) {
        float ratio = Math.min(1f, distancePx / Math.max(1f, screenH));
        if (absVelocityY > minFlingVelocity) {
            float effectiveVel = Math.min(absVelocityY, maxFlingVelocity * 0.65f);
            int velDuration = Math.round(1000f * (distancePx / Math.max(effectiveVel, screenH * 2.5f)));
            return Math.max(160, Math.min(280, velDuration + 90));
        }
        return Math.max(170, Math.min(300, Math.round(160 + ratio * 130)));
    }

    private void resetDrawerSearchAndScrollTop() {
        if (gvApps != null) {
            gvApps.setSelection(0);
        }
        if (etDrawerSearch != null && etDrawerSearch.length() > 0) {
            etDrawerSearch.setText("");
        }
    }

    private void sendSyntheticCancel(MotionEvent src) {
        MotionEvent cancelEvent = MotionEvent.obtain(src);
        cancelEvent.setAction(MotionEvent.ACTION_CANCEL);
        isDispatchingSyntheticCancel = true;
        try {
            host.dispatchCancelToChildren(cancelEvent);
        } finally {
            isDispatchingSyntheticCancel = false;
            cancelEvent.recycle();
        }
    }

    private void cancelAnimator() {
        if (drawerAnimator != null) {
            drawerAnimator.cancel();
            drawerAnimator = null;
        }
        isAnimatingToOpen = false;
        isAnimatingToClose = false;
    }

    private void enableHardwareLayers(boolean enabled) {
        int layer = enabled ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
        if (appDrawer.getLayerType() != layer) {
            appDrawer.setLayerType(layer, null);
        }
        if (homeScreen.getLayerType() != layer) {
            homeScreen.setLayerType(layer, null);
        }
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

    private static float clamp(float val, float min, float max) {
        return Math.max(min, Math.min(max, val));
    }
}

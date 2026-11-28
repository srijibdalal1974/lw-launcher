package com.custom.pixellauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import java.lang.reflect.Method;

/**
 * Shared fluid animation utilities for Pixel-style tactile icon feedback,
 * clip-reveal app launches, popups, SurfaceFlinger wallpaper blur, and frame-paced motion.
 */
public final class UiMotion {

    private static final Interpolator DECEL_CUBIC = new DecelerateInterpolator(1.8f);
    public static final Interpolator EMPHASIZED_DECEL = new PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f);

    private static Method cachedCreateBlurEffect = null;
    private static Method cachedSetRenderEffect = null;
    private static Object cachedClampTileMode = null;
    private static boolean checkedRenderEffect = false;

    private static Method cachedSetBackgroundBlurRadius = null;
    private static boolean checkedBgBlurMethod = false;
    private static Method cachedLpSetBlurBehindRadius = null;
    private static boolean checkedLpBlurMethod = false;

    // ---- Solid scrim fallback for API 30 (Android 11) ----
    private static View softBlurOverlay;
    private static boolean softBlurPrepared = false;
    private static ColorDrawable windowDimDrawable;
    private static final float SOFT_BLUR_MAX_DP = 26f;

    private static final int TAG_SCALE_ANIMATOR = 0x7F0A1101;
    private static final int TAG_PRESS_DOWN_COORDS = 0x7F0A1102;
    private static final int TAG_POPUP_ANIMATOR = 0x7F0A1103;

    private UiMotion() {}

    public static void attachIconTouchSpring(View hitView, final View iconTarget) {
        if (hitView == null) return;
        if (iconTarget == null) {
            hitView.setOnTouchListener(null);
            return;
        }
        hitView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                handleIconPressMotion(iconTarget, event);
                return false;
            }
        });
    }

    public static void handleIconPressMotion(final View iconTarget, MotionEvent event) {
        if (iconTarget == null || event == null) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            iconTarget.setTag(TAG_PRESS_DOWN_COORDS, new float[]{event.getRawX(), event.getRawY()});
            animateViewScalePaced(iconTarget, 0.90f, 72, DECEL_CUBIC);
        } else if (action == MotionEvent.ACTION_MOVE) {
            Object tag = iconTarget.getTag(TAG_PRESS_DOWN_COORDS);
            if (tag instanceof float[]) {
                float[] xy = (float[]) tag;
                float slop = android.view.ViewConfiguration.get(iconTarget.getContext()).getScaledTouchSlop();
                if (Math.hypot(event.getRawX() - xy[0], event.getRawY() - xy[1]) > slop) {
                    iconTarget.setTag(TAG_PRESS_DOWN_COORDS, null);
                    animateViewScalePaced(iconTarget, 1.0f, 120, DECEL_CUBIC);
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            iconTarget.setTag(TAG_PRESS_DOWN_COORDS, null);
            animateViewScalePaced(
                    iconTarget,
                    1.0f,
                    155,
                    new OvershootInterpolator(DisplayRefreshHelper.scaleOvershootTension(1.55f))
            );
        }
    }

    private static void animateViewScalePaced(final View target, final float endScale, int baseDurationMs, Interpolator interpolator) {
        if (target == null) return;
        Object oldTag = target.getTag(TAG_SCALE_ANIMATOR);
        if (oldTag instanceof ValueAnimator) {
            ((ValueAnimator) oldTag).cancel();
        }
        target.animate().cancel();

        final float startScaleX = target.getScaleX();
        final float startScaleY = target.getScaleY();
        if (Math.abs(startScaleX - endScale) < 0.002f && Math.abs(startScaleY - endScale) < 0.002f) {
            target.setScaleX(endScale);
            target.setScaleY(endScale);
            return;
        }

        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(DisplayRefreshHelper.scaleDurationMs(baseDurationMs));
        va.setInterpolator(interpolator);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!pacer.shouldRender(animation)) return;
                float t = (float) animation.getAnimatedValue();
                target.setScaleX(startScaleX + (endScale - startScaleX) * t);
                target.setScaleY(startScaleY + (endScale - startScaleY) * t);
            }
        });
        va.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                target.setTag(TAG_SCALE_ANIMATOR, null);
                target.setScaleX(endScale);
                target.setScaleY(endScale);
            }
        });
        target.setTag(TAG_SCALE_ANIMATOR, va);
        va.start();
    }

    public static Rect measureScreenBounds(View sourceView) {
        if (sourceView == null || sourceView.getWidth() <= 0 || sourceView.getHeight() <= 0 || !sourceView.isAttachedToWindow()) {
            return null;
        }
        Object oldTag = sourceView.getTag(TAG_SCALE_ANIMATOR);
        if (oldTag instanceof ValueAnimator) {
            ((ValueAnimator) oldTag).cancel();
        }
        sourceView.animate().cancel();
        sourceView.setScaleX(1.0f);
        sourceView.setScaleY(1.0f);
        int[] loc = new int[2];
        sourceView.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + sourceView.getWidth(), loc[1] + sourceView.getHeight());
    }

    public static void launchWithClipReveal(Activity activity, Intent intent, View sourceView) {
        launchWithClipReveal(activity, intent, sourceView, false);
    }

    public static void launchWithClipReveal(Activity activity, Intent intent, View sourceView, boolean overlayHandledMorph) {
        if (activity == null || intent == null) return;
        Bundle opts = null;
        Rect sourceBounds = measureScreenBounds(sourceView);
        if (sourceBounds != null) {
            intent.setSourceBounds(sourceBounds);
        }
        try {
            ActivityOptions ao = null;
            if (overlayHandledMorph) {
                // LaunchTransitionView drives the 120Hz adaptive icon morph; dissolve the incoming activity cleanly without a clashing tiny zoom box.
                ao = ActivityOptions.makeCustomAnimation(activity, R.anim.launch_fade_in, R.anim.launch_hold);
            } else if (sourceBounds != null) {
                ao = ActivityOptions.makeScaleUpAnimation(sourceView, 0, 0, sourceBounds.width(), sourceBounds.height());
            } else {
                ao = ActivityOptions.makeBasic();
            }
            if (ao != null) {
                if (Build.VERSION.SDK_INT >= 33) {
                    try {
                        ActivityOptions.class.getMethod("setSplashScreenStyle", int.class).invoke(ao, 1);
                    } catch (Throwable ignored) {}
                }
                opts = ao.toBundle();
                if (opts != null && Build.VERSION.SDK_INT >= 31) {
                    opts.putInt("android.activity.splashScreenStyle", 1);
                }
            }
        } catch (Throwable ignored) {}
        if (opts != null) {
            activity.startActivity(intent, opts);
        } else {
            activity.startActivity(intent);
        }
    }

    private interface ProgressCallback {
        void onProgress(float rawT, float easedT);
    }

    private static void runSynchronizedLayerAnim(
            final View view,
            final int baseDurationMs,
            final Interpolator interp,
            final Runnable preStartSetup,
            final ProgressCallback progressCallback,
            final Runnable endReset) {
        if (view == null) return;
        Object old = view.getTag(TAG_POPUP_ANIMATOR);
        if (old instanceof ValueAnimator) {
            ((ValueAnimator) old).cancel();
        }
        view.animate().cancel();
        DisplayRefreshHelper.requestInteractiveBoost(view.getContext(), true);
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        final Runnable launchAnim = new Runnable() {
            @Override
            public void run() {
                if (preStartSetup != null) {
                    preStartSetup.run();
                }
                DisplayRefreshHelper.boostPopupSurface(view, true);
                final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
                final ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
                va.setDuration(DisplayRefreshHelper.scaleDurationMs(baseDurationMs));
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        if (!pacer.shouldRender(animation)) return;
                        float rawT = (float) animation.getAnimatedValue();
                        float easedT = interp != null ? interp.getInterpolation(rawT) : rawT;
                        progressCallback.onProgress(rawT, easedT);
                    }
                });
                va.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        finish();
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        finish();
                    }

                    private void finish() {
                        view.setTag(TAG_POPUP_ANIMATOR, null);
                        view.setLayerType(View.LAYER_TYPE_NONE, null);
                        if (endReset != null) {
                            endReset.run();
                        }
                        DisplayRefreshHelper.requestInteractiveBoost(view.getContext(), false);
                    }
                });
                view.setTag(TAG_POPUP_ANIMATOR, va);
                va.start();
            }
        };

        final ViewTreeObserver vto = view.getViewTreeObserver();
        if (vto.isAlive()) {
            vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    ViewTreeObserver currVto = view.getViewTreeObserver();
                    if (currVto.isAlive()) {
                        currVto.removeOnPreDrawListener(this);
                    }
                    launchAnim.run();
                    return true;
                }
            });
        } else {
            view.post(launchAnim);
        }
    }

    public static void animatePopIn(final View view) {
        animatePopIn(view, 0.5f, 0.85f, 10f);
    }

    public static void animatePopIn(
            final View view,
            final float pivotFractionX,
            final float pivotFractionY,
            final float startOffsetYDp) {
        if (view == null) return;
        final float startOffsetY = startOffsetYDp * view.getResources().getDisplayMetrics().density;
        view.setScaleX(0.88f);
        view.setScaleY(0.88f);
        view.setTranslationY(startOffsetY);
        view.setAlpha(0f);

        runSynchronizedLayerAnim(
                view,
                210,
                EMPHASIZED_DECEL,
                new Runnable() {
                    @Override
                    public void run() {
                        if (view.getWidth() > 0 && view.getHeight() > 0) {
                            view.setPivotX(view.getWidth() * Math.max(0.08f, Math.min(0.92f, pivotFractionX)));
                            view.setPivotY(view.getHeight() * Math.max(0f, Math.min(1f, pivotFractionY)));
                        }
                    }
                },
                new ProgressCallback() {
                    @Override
                    public void onProgress(float rawT, float easedT) {
                        float s = 0.88f + 0.12f * easedT;
                        view.setScaleX(s);
                        view.setScaleY(s);
                        view.setTranslationY(startOffsetY * (1f - easedT));
                        view.setAlpha(Math.max(0f, Math.min(1f, rawT * 2.1f)));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        view.setScaleX(1.0f);
                        view.setScaleY(1.0f);
                        view.setTranslationY(0f);
                        view.setAlpha(1.0f);
                    }
                }
        );
    }

    public static void animateFolderPopIn(final View dialogView, final float pivotX, final float pivotY) {
        if (dialogView == null) return;
        dialogView.setPivotX(pivotX);
        dialogView.setPivotY(pivotY);
        dialogView.setScaleX(0.42f);
        dialogView.setScaleY(0.42f);
        dialogView.setAlpha(0f);

        runSynchronizedLayerAnim(
                dialogView,
                220,
                EMPHASIZED_DECEL,
                null,
                new ProgressCallback() {
                    @Override
                    public void onProgress(float rawT, float easedT) {
                        float s = 0.42f + 0.58f * easedT;
                        dialogView.setScaleX(s);
                        dialogView.setScaleY(s);
                        dialogView.setAlpha(Math.max(0f, Math.min(1f, rawT * 2.0f)));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        dialogView.setScaleX(1.0f);
                        dialogView.setScaleY(1.0f);
                        dialogView.setAlpha(1.0f);
                    }
                }
        );
    }

    public static void animateSheetSlideIn(final View view) {
        if (view == null) return;
        final float startY = 44f * view.getResources().getDisplayMetrics().density;
        view.setTranslationY(startY);
        view.setScaleX(0.97f);
        view.setScaleY(0.97f);
        view.setAlpha(0f);

        runSynchronizedLayerAnim(
                view,
                235,
                EMPHASIZED_DECEL,
                null,
                new ProgressCallback() {
                    @Override
                    public void onProgress(float rawT, float easedT) {
                        view.setTranslationY(startY * (1f - easedT));
                        float s = 0.97f + 0.03f * easedT;
                        view.setScaleX(s);
                        view.setScaleY(s);
                        view.setAlpha(Math.max(0f, Math.min(1f, rawT * 1.9f)));
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        view.setTranslationY(0f);
                        view.setScaleX(1.0f);
                        view.setScaleY(1.0f);
                        view.setAlpha(1.0f);
                    }
                }
        );
    }

    public static void setBackdropBlurDp(View targetView, float radiusDp) {
        if (targetView == null) return;

        if (Build.VERSION.SDK_INT >= 31) {
            // Native RenderEffect path (Android 12+)
            try {
                if (!checkedRenderEffect) {
                    checkedRenderEffect = true;
                    Class<?> reClass = Class.forName("android.graphics.RenderEffect");
                    Class<?> tmClass = Class.forName("android.graphics.Shader$TileMode");
                    cachedClampTileMode = Enum.valueOf((Class) tmClass, "CLAMP");
                    cachedCreateBlurEffect = reClass.getMethod("createBlurEffect", float.class, float.class, tmClass);
                    cachedSetRenderEffect = View.class.getMethod("setRenderEffect", reClass);
                }
                if (cachedSetRenderEffect == null || cachedCreateBlurEffect == null) return;
                if (radiusDp <= 0.6f) {
                    cachedSetRenderEffect.invoke(targetView, new Object[]{null});
                } else {
                    float rPx = radiusDp * targetView.getResources().getDisplayMetrics().density;
                    Object effect = cachedCreateBlurEffect.invoke(null, rPx, rPx, cachedClampTileMode);
                    cachedSetRenderEffect.invoke(targetView, effect);
                }
            } catch (Throwable ignored) {}
        } else {
            // Solid scrim fallback (Android 11 / API 30) — zero GPU cost
            if (radiusDp > 0.6f) {
                if (!softBlurPrepared) {
                    prepareSolidScrim(targetView);
                }
                if (softBlurOverlay != null) {
                    float fraction = Math.min(1f, radiusDp / SOFT_BLUR_MAX_DP);
                    int alpha = (int) (fraction * 0.42f * 255);
                    softBlurOverlay.setBackgroundColor(Color.argb(alpha, 18, 18, 22));
                    softBlurOverlay.setScaleX(targetView.getScaleX());
                    softBlurOverlay.setScaleY(targetView.getScaleY());
                    softBlurOverlay.setPivotX(targetView.getWidth() / 2f);
                    softBlurOverlay.setPivotY(targetView.getHeight() / 2f);
                    softBlurOverlay.setVisibility(View.VISIBLE);
                }
            } else {
                if (softBlurOverlay != null) {
                    softBlurOverlay.setBackgroundColor(Color.TRANSPARENT);
                    softBlurOverlay.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * Creates a lightweight solid-color scrim View as a blur stand-in on API 30.
     * Zero bitmap allocation, zero GPU blur cost.
     */
    private static void prepareSolidScrim(View target) {
        clearSoftwareBlur();
        try {
            ViewGroup parent = (ViewGroup) target.getParent();
            if (parent != null) {
                View scrim = new View(target.getContext());
                scrim.setBackgroundColor(Color.TRANSPARENT);
                scrim.setClickable(false);
                scrim.setFocusable(false);
                scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                scrim.setVisibility(View.GONE);

                int idx = parent.indexOfChild(target);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                parent.addView(scrim, idx + 1, lp);
                softBlurOverlay = scrim;
                softBlurPrepared = true;
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Removes the scrim overlay used on API 30.
     */
    public static void clearSoftwareBlur() {
        if (softBlurOverlay != null) {
            ViewGroup parent = (ViewGroup) softBlurOverlay.getParent();
            if (parent != null) parent.removeView(softBlurOverlay);
            softBlurOverlay = null;
        }
        softBlurPrepared = false;
    }

    /**
     * Clears the window-level dim scrim used as a blur fallback on API 30.
     */
    public static void clearWindowDimScrim(Window window) {
        if (window == null || Build.VERSION.SDK_INT >= 31) return;
        View decor = window.getDecorView();
        if (decor != null) {
            decor.setForeground(null);
        }
        windowDimDrawable = null;
    }

    private static final int TAG_WINDOW_BLUR_RADIUS = 0x7F0A0092;
    private static boolean checkedSurfaceBlurMethod = false;
    private static Method cachedGetViewRootImpl = null;
    private static Method cachedGetSurfaceControl = null;
    private static java.lang.reflect.Constructor<?> cachedTxCtor = null;
    private static Method cachedTxSetBgBlur = null;
    private static Method cachedTxApply = null;

    /**
     * Drives Android 12+ SurfaceFlinger background blur behind the Launcher window (blurring live wallpaper)
     * matching Pixel Launcher's backgroundBlurRadius (~30dp).
     * On Android 11 (API 30), falls back to a semi-transparent dim scrim on the decor view.
     */
    public static void setWindowBackgroundBlurRadius(Window window, int radiusPx) {
        if (window == null) return;

        if (Build.VERSION.SDK_INT < 31) {
            // API 30 fallback: semi-transparent scrim simulating wallpaper dim
            View decor = window.getDecorView();
            if (decor != null) {
                float density = decor.getResources().getDisplayMetrics().density;
                float maxBlurPx = 30f * density;
                float fraction = Math.min(1f, radiusPx / Math.max(1f, maxBlurPx));
                int alpha = (int) (fraction * 0.32f * 255);
                if (alpha <= 2) {
                    if (windowDimDrawable != null) {
                        decor.setForeground(null);
                        windowDimDrawable = null;
                    }
                } else {
                    if (windowDimDrawable == null) {
                        windowDimDrawable = new ColorDrawable(Color.argb(alpha, 0, 0, 0));
                        decor.setForeground(windowDimDrawable);
                    } else {
                        windowDimDrawable.setColor(Color.argb(alpha, 0, 0, 0));
                    }
                }
            }
            return;
        }
        View decor = window.getDecorView();
        int clamped = Math.max(0, radiusPx);
        if (decor != null) {
            Object prev = decor.getTag(TAG_WINDOW_BLUR_RADIUS);
            if (prev instanceof Integer && ((Integer) prev) == clamped) {
                return;
            }
            decor.setTag(TAG_WINDOW_BLUR_RADIUS, clamped);
        }

        try {
            if (!checkedBgBlurMethod) {
                checkedBgBlurMethod = true;
                cachedSetBackgroundBlurRadius = Window.class.getMethod("setBackgroundBlurRadius", int.class);
            }
            if (cachedSetBackgroundBlurRadius != null) {
                cachedSetBackgroundBlurRadius.invoke(window, clamped);
            }
        } catch (Throwable ignored) {}

        // Also apply directly on the ViewRootImpl SurfaceControl so SurfaceFlinger blurs Wallpaper behind window immediately
        if (decor == null) return;
        try {
            if (!checkedSurfaceBlurMethod) {
                checkedSurfaceBlurMethod = true;
                cachedGetViewRootImpl = View.class.getMethod("getViewRootImpl");
                Class<?> vriCls = Class.forName("android.view.ViewRootImpl");
                cachedGetSurfaceControl = vriCls.getMethod("getSurfaceControl");
                Class<?> scCls = Class.forName("android.view.SurfaceControl");
                Class<?> txCls = Class.forName("android.view.SurfaceControl$Transaction");
                cachedTxCtor = txCls.getConstructor();
                cachedTxSetBgBlur = txCls.getMethod("setBackgroundBlurRadius", scCls, int.class);
                cachedTxApply = txCls.getMethod("apply");
            }
            if (cachedGetViewRootImpl != null && cachedGetSurfaceControl != null
                    && cachedTxCtor != null && cachedTxSetBgBlur != null && cachedTxApply != null) {
                Object vri = cachedGetViewRootImpl.invoke(decor);
                if (vri != null) {
                    Object sc = cachedGetSurfaceControl.invoke(vri);
                    if (sc != null) {
                        Object tx = cachedTxCtor.newInstance();
                        cachedTxSetBgBlur.invoke(tx, sc, clamped);
                        cachedTxApply.invoke(tx);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}

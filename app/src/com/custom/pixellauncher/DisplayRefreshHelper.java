package com.custom.pixellauncher;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detects device display refresh capabilities (60 / 90 / 120 / up to 180 Hz),
 * syncs Window/Surface frame rate hints, paces ValueAnimator ticks and RenderThread frames,
 * and adapts durations, overshoot tension, and scroll friction per refresh rate.
 */
public final class DisplayRefreshHelper {

    public static final int PREF_MODE_AUTO = 0;
    public static final float MIN_SUPPORTED_HZ = 60f;
    public static final float MAX_SUPPORTED_HZ = 180f;

    private static final String PREFS_NAME = "PixelLauncherPrefs";
    private static final String PREF_KEY_REFRESH_MODE = "pref_refresh_rate_mode";

    private static volatile float detectedActiveHz = 60f;
    private static volatile float maxCapHz = 60f;
    private static volatile int prefMode = PREF_MODE_AUTO;
    private static volatile boolean boostActive = false;

    private static final Handler IDLE_HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable clearBoostRunnable = null;
    private static Method cachedSetFrameContentVelocityMethod = null;
    private static boolean checkedVelocityMethod = false;
    private static Method cachedGetViewRootImplMethod = null;
    private static Method cachedSurfaceSetFrameRateMethod = null;
    private static boolean checkedSurfaceMethod = false;

    public interface RefreshChangeListener {
        void onRefreshRateChanged(float effectiveHz, float maxSupportedHz);
    }

    public static final class FramePacer {
        private long nextRenderNs = 0L;

        public void reset() {
            nextRenderNs = 0L;
        }

        public boolean shouldRender(ValueAnimator anim) {
            float hz = getEffectiveHz();
            if (prefMode == PREF_MODE_AUTO || hz >= getMaxSupportedHz() - 0.5f) {
                return true;
            }
            if (anim != null) {
                float frac = anim.getAnimatedFraction();
                if (frac <= 0.001f || frac >= 0.999f) {
                    long nowStart = System.nanoTime();
                    long budget = Math.round(1_000_000_000.0 / Math.max(30f, hz));
                    nextRenderNs = nowStart + budget;
                    return true;
                }
            }
            long now = System.nanoTime();
            long budgetNs = Math.round(1_000_000_000.0 / Math.max(30f, hz));
            if (nextRenderNs == 0L) {
                nextRenderNs = now + budgetNs;
                return true;
            }
            // Allow 1.8ms phase tolerance so hardware vsyncs match nominal 60/90/120/180 Hz cadence.
            if (now + 1_800_000L >= nextRenderNs) {
                nextRenderNs += budgetNs;
                if (nextRenderNs < now) {
                    nextRenderNs = now + budgetNs;
                }
                return true;
            }
            return false;
        }
    }

    private DisplayRefreshHelper() {}

    public static void init(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefMode = PREF_MODE_AUTO;
        if (prefs.contains(PREF_KEY_REFRESH_MODE)) {
            prefs.edit().remove(PREF_KEY_REFRESH_MODE).apply();
        }
        refreshCapabilities(context);
        applyGlobalAnimatorFrameDelay();
    }

    public static int getPrefMode() {
        return PREF_MODE_AUTO;
    }

    public static void setPrefMode(Activity activity, int mode, Window activeOverlayWindow) {
        // Always auto-adapt to display maximum supported refresh rate
        prefMode = PREF_MODE_AUTO;
        if (activity != null) {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(PREF_KEY_REFRESH_MODE)
                    .apply();
            refreshCapabilities(activity);
            applyGlobalAnimatorFrameDelay();
            applyWindowRefreshPreference(activity.getWindow(), activity, false);
            if (activeOverlayWindow != null) {
                applyWindowRefreshPreference(activeOverlayWindow, activity, false);
            }
        }
    }

    public static void refreshCapabilities(Context context) {
        Display display = getDisplay(context);
        if (display == null) return;
        try {
            Display.Mode activeMode = display.getMode();
            float curHz = activeMode != null ? activeMode.getRefreshRate() : display.getRefreshRate();
            detectedActiveHz = clampHz(curHz);

            float maxFound = detectedActiveHz;
            Display.Mode[] modes = display.getSupportedModes();
            if (modes != null && activeMode != null) {
                int w = activeMode.getPhysicalWidth();
                int h = activeMode.getPhysicalHeight();
                for (Display.Mode m : modes) {
                    if (m.getPhysicalWidth() == w && m.getPhysicalHeight() == h) {
                        float r = m.getRefreshRate();
                        if (r >= MIN_SUPPORTED_HZ - 1f && r <= MAX_SUPPORTED_HZ + 1f && r > maxFound) {
                            maxFound = r;
                        }
                    }
                }
            }
            maxCapHz = clampHz(maxFound);
        } catch (Throwable ignored) {}
    }

    public static DisplayManager.DisplayListener registerDisplayListener(final Context context, final RefreshChangeListener listener) {
        if (context == null) return null;
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) return null;
            DisplayManager.DisplayListener dl = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId == Display.DEFAULT_DISPLAY) {
                        refreshCapabilities(context);
                        applyGlobalAnimatorFrameDelay();
                        if (listener != null) {
                            listener.onRefreshRateChanged(getEffectiveHz(), getMaxSupportedHz());
                        }
                    }
                }
            };
            dm.registerDisplayListener(dl, IDLE_HANDLER);
            return dl;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void unregisterDisplayListener(Context context, DisplayManager.DisplayListener dl) {
        if (context == null || dl == null) return;
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                dm.unregisterDisplayListener(dl);
            }
        } catch (Throwable ignored) {}
    }

    public static List<Integer> getSupportedDisplayHzOptions(Context context) {
        Display display = getDisplay(context);
        List<Integer> rates = new ArrayList<>();
        if (display != null) {
            try {
                Display.Mode active = display.getMode();
                Display.Mode[] modes = display.getSupportedModes();
                if (active != null && modes != null) {
                    int w = active.getPhysicalWidth();
                    int h = active.getPhysicalHeight();
                    for (Display.Mode m : modes) {
                        if (m.getPhysicalWidth() == w && m.getPhysicalHeight() == h) {
                            int rounded = Math.round(m.getRefreshRate());
                            if (rounded >= 59 && rounded <= 181 && !rates.contains(rounded)) {
                                rates.add(rounded);
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (rates.isEmpty()) {
            rates.add(Math.round(detectedActiveHz));
        }
        Collections.sort(rates);
        return rates;
    }

    public static float getDetectedActiveHz() {
        return clampHz(detectedActiveHz);
    }

    public static float getMaxSupportedHz() {
        return clampHz(Math.max(detectedActiveHz, maxCapHz));
    }

    public static float getEffectiveHz() {
        return getMaxSupportedHz();
    }

    public static void applyGlobalAnimatorFrameDelay() {
        float eff = getEffectiveHz();
        if (prefMode == PREF_MODE_AUTO || eff >= getMaxSupportedHz() - 0.5f) {
            ValueAnimator.setFrameDelay(0L);
            return;
        }
        // Global ValueAnimator cap at 60 Hz (15ms < 16.67ms) cleanly locks 120 Hz displays to 60 FPS without halving 60 Hz panels;
        // intermediate rates (e.g. 90 Hz) use hardware mode requests + FramePacer accumulator ticks.
        ValueAnimator.setFrameDelay(eff <= 62f ? 15L : 0L);
    }

    public static float getHighRefreshRatio() {
        return clamp01((getEffectiveHz() - MIN_SUPPORTED_HZ) / (MAX_SUPPORTED_HZ - MIN_SUPPORTED_HZ));
    }

    public static int scaleDurationMs(int base60HzDurationMs) {
        float hz = getEffectiveHz();
        float ratio = getHighRefreshRatio();
        float factor = 1.0f - (0.16f * ratio);
        int scaled = Math.round(base60HzDurationMs * factor);
        int minFramesMs = Math.round((1000f / hz) * 6f);
        return Math.max(minFramesMs, scaled);
    }

    public static long scaleDurationMs(long base60HzDurationMs) {
        return scaleDurationMs((int) base60HzDurationMs);
    }

    public static float scaleOvershootTension(float base60HzTension) {
        float ratio = getHighRefreshRatio();
        float scaled = base60HzTension * (1.0f - 0.14f * ratio);
        return Math.max(0.8f, scaled);
    }

    public static float getAdaptedScrollFriction() {
        float base = ViewConfiguration.getScrollFriction();
        float ratio = getHighRefreshRatio();
        return base * (1.0f - 0.34f * ratio);
    }

    public static void requestInteractiveBoost(final Context context, boolean active) {
        if (!(context instanceof Activity)) return;
        final Activity activity = (Activity) context;
        if (clearBoostRunnable != null) {
            IDLE_HANDLER.removeCallbacks(clearBoostRunnable);
            clearBoostRunnable = null;
        }
        if (active) {
            if (!boostActive) {
                boostActive = true;
                applyGlobalAnimatorFrameDelay();
                applyWindowRefreshPreference(activity.getWindow(), activity, true);
            }
        } else {
            clearBoostRunnable = new Runnable() {
                @Override
                public void run() {
                    clearBoostRunnable = null;
                    if (boostActive) {
                        boostActive = false;
                        applyGlobalAnimatorFrameDelay();
                        applyWindowRefreshPreference(activity.getWindow(), activity, false);
                    }
                }
            };
            IDLE_HANDLER.postDelayed(clearBoostRunnable, 360L);
        }
    }

    public static void applyWindowRefreshPreference(Activity activity, boolean boosting) {
        if (activity == null) return;
        applyWindowRefreshPreference(activity.getWindow(), activity, boosting);
    }

    public static void applyWindowRefreshPreference(Window window, Context context, boolean boosting) {
        if (window == null || context == null) return;
        Display display = getDisplay(context);
        if (display == null) return;

        try {
            WindowManager.LayoutParams lp = window.getAttributes();
            int[] bestMode = resolvePreferredMode(display, context, boosting);
            if (bestMode == null) return;
            int bestModeId = bestMode[0];
            float desiredRate = bestMode[1] / 100f;

            boolean changed = false;
            if (lp.preferredDisplayModeId != bestModeId) {
                lp.preferredDisplayModeId = bestModeId;
                changed = true;
            }
            if (Math.abs(lp.preferredRefreshRate - desiredRate) > 0.1f) {
                lp.preferredRefreshRate = desiredRate;
                changed = true;
            }
            if (changed) {
                window.setAttributes(lp);
            }
            applySurfaceFrameRateHint(window.getDecorView(), desiredRate);
        } catch (Throwable ignored) {}
    }

    public static void boostPopupSurface(View popupContentView, boolean boosting) {
        if (popupContentView == null) return;
        Context ctx = popupContentView.getContext();
        if (ctx instanceof Activity) {
            requestInteractiveBoost(ctx, boosting);
        }
        Display display = getDisplay(ctx);
        if (display == null) return;
        try {
            View root = popupContentView.getRootView();
            int[] bestMode = resolvePreferredMode(display, ctx, boosting);
            float desiredRate = bestMode != null ? (bestMode[1] / 100f) : 0f;
            if (bestMode != null && root != null && root.getLayoutParams() instanceof WindowManager.LayoutParams) {
                WindowManager.LayoutParams wlp = (WindowManager.LayoutParams) root.getLayoutParams();
                if (wlp.preferredDisplayModeId != bestMode[0] || Math.abs(wlp.preferredRefreshRate - desiredRate) > 0.1f) {
                    wlp.preferredDisplayModeId = bestMode[0];
                    wlp.preferredRefreshRate = desiredRate;
                    WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null && root.isAttachedToWindow()) {
                        wm.updateViewLayout(root, wlp);
                    }
                }
            }
            applySurfaceFrameRateHint(root != null ? root : popupContentView, desiredRate);
            setFrameContentVelocity(popupContentView, boosting ? 2800f : 0f);
        } catch (Throwable ignored) {}
    }

    private static int[] resolvePreferredMode(Display display, Context context, boolean boosting) {
        if (display == null) return null;
        Display.Mode active = display.getMode();
        Display.Mode[] modes = display.getSupportedModes();
        if (active == null || modes == null) return null;

        float targetHz = 0f;
        if (prefMode != PREF_MODE_AUTO) {
            targetHz = getEffectiveHz();
        } else if (boosting) {
            targetHz = getMaxSupportedHz();
        }

        int bestModeId = 0;
        float bestDiff = Float.MAX_VALUE;
        float bestRate = 0f;
        if (targetHz > 0f) {
            int w = active.getPhysicalWidth();
            int h = active.getPhysicalHeight();
            for (Display.Mode m : modes) {
                if (m.getPhysicalWidth() == w && m.getPhysicalHeight() == h) {
                    float r = m.getRefreshRate();
                    if (r <= MAX_SUPPORTED_HZ + 1f) {
                        float diff = Math.abs(r - targetHz);
                        if (diff < bestDiff) {
                            bestDiff = diff;
                            bestRate = r;
                            bestModeId = m.getModeId();
                        }
                    }
                }
            }
        }
        return new int[]{bestModeId, bestModeId == 0 ? 0 : Math.round(bestRate * 100f)};
    }

    private static void applySurfaceFrameRateHint(View decorView, float frameRate) {
        if (decorView == null || Build.VERSION.SDK_INT < 30) return;
        try {
            if (!checkedSurfaceMethod) {
                checkedSurfaceMethod = true;
                cachedGetViewRootImplMethod = View.class.getMethod("getViewRootImpl");
                if (Build.VERSION.SDK_INT >= 31) {
                    cachedSurfaceSetFrameRateMethod = Surface.class.getMethod("setFrameRate", float.class, int.class, int.class);
                } else {
                    cachedSurfaceSetFrameRateMethod = Surface.class.getMethod("setFrameRate", float.class, int.class);
                }
            }
            if (cachedGetViewRootImplMethod == null || cachedSurfaceSetFrameRateMethod == null) return;
            Object vri = cachedGetViewRootImplMethod.invoke(decorView);
            if (vri == null) return;
            java.lang.reflect.Field f = vri.getClass().getDeclaredField("mSurface");
            f.setAccessible(true);
            Object surfObj = f.get(vri);
            if (surfObj instanceof Surface && ((Surface) surfObj).isValid()) {
                if (cachedSurfaceSetFrameRateMethod.getParameterCount() == 3) {
                    // FRAME_RATE_COMPATIBILITY_DEFAULT = 0, CHANGE_FRAME_RATE_ALWAYS = 1
                    cachedSurfaceSetFrameRateMethod.invoke(surfObj, frameRate, 0, 1);
                } else {
                    cachedSurfaceSetFrameRateMethod.invoke(surfObj, frameRate, 0);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void setFrameContentVelocity(View view, float velocityPxPerSec) {
        if (view == null || Build.VERSION.SDK_INT < 34) return;
        try {
            if (!checkedVelocityMethod) {
                checkedVelocityMethod = true;
                cachedSetFrameContentVelocityMethod = View.class.getMethod("setFrameContentVelocity", float.class);
            }
            if (cachedSetFrameContentVelocityMethod != null) {
                cachedSetFrameContentVelocityMethod.invoke(view, Math.abs(velocityPxPerSec));
            }
        } catch (Throwable ignored) {}
    }

    public static String describeCurrentMode(Context context) {
        refreshCapabilities(context);
        int detected = Math.round(getDetectedActiveHz());
        int maxHz = Math.round(getMaxSupportedHz());
        int eff = Math.round(getEffectiveHz());
        if (prefMode == PREF_MODE_AUTO) {
            return (maxHz > detected)
                    ? "Auto (" + detected + " Hz, boosts to " + maxHz + " Hz)"
                    : "Auto (" + detected + " Hz display)";
        }
        return eff + " Hz animation pacing (display max " + maxHz + " Hz)";
    }

    private static Display getDisplay(Context context) {
        if (context == null) return null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Display d = context.getDisplay();
                if (d != null) return d;
            }
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            return wm != null ? wm.getDefaultDisplay() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static float clampHz(float hz) {
        if (Float.isNaN(hz) || hz < MIN_SUPPORTED_HZ) return MIN_SUPPORTED_HZ;
        return Math.min(MAX_SUPPORTED_HZ, hz);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}

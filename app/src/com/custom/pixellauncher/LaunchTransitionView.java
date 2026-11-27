package com.custom.pixellauncher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

/**
 * Full-screen 120 FPS Pixel-Launcher-style icon-to-window morph overlay.
 * Decouples Material 3 Emphasized X/Y expansion, center glide, bold corner radius morphing,
 * AdaptiveIconDrawable foreground/background separation with vertical parallax,
 * adaptive splash surface tinting, ambient elevation, and background depth zoom.
 */
public class LaunchTransitionView extends View {

    private static final long COLD_EXPAND_DURATION_MS = 430L;
    private static final long WARM_EXPAND_DURATION_MS = 400L;
    // Trigger launch at ~160ms (~78% card) so OS onTransitionReady arrives (~195ms) right as the card glides smoothly into ~92% centered bounds.
    private static final float COLD_LAUNCH_TRIGGER_FRACTION = 0.42f;
    private static final float WARM_LAUNCH_TRIGGER_FRACTION = 0.40f;

    private static final Interpolator PIXEL_SIZE_X_INTERP = createPixelContainerInterpolator(false);
    private static final Interpolator PIXEL_SIZE_Y_INTERP = createPixelContainerInterpolator(true);
    // Glide center early enough so card is near screen-center (~95%) when the centered OS app window begins its 93%->100% zoom.
    private static final Interpolator PIXEL_CENTER_INTERP = new PathInterpolator(0.10f, 0.68f, 0.16f, 1.0f);
    private static final Interpolator PIXEL_FG_SCALE_INTERP = new PathInterpolator(0.12f, 0.72f, 0.22f, 1.0f);
    private static final Interpolator PIXEL_BG_ZOOM_INTERP = new PathInterpolator(0.08f, 0.76f, 0.16f, 1.0f);

    private static Interpolator createPixelContainerInterpolator(boolean portraitLag) {
        Path p = new Path();
        p.moveTo(0f, 0f);
        if (portraitLag) {
            p.cubicTo(0.09f, 0.01f, 0.22f, 0.10f, 0.32f, 0.40f);
            p.cubicTo(0.40f, 0.79f, 0.50f, 0.91f, 1.0f, 1.0f);
        } else {
            p.cubicTo(0.08f, 0.02f, 0.20f, 0.14f, 0.30f, 0.46f);
            p.cubicTo(0.38f, 0.82f, 0.50f, 0.92f, 1.0f, 1.0f);
        }
        return new PathInterpolator(p);
    }

    private final Paint dimPaint = new Paint();
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgLayerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgShaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint iconBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private final Matrix bgShaderMatrix = new Matrix();
    private final RectF cardRect = new RectF();
    private final RectF shadowRect = new RectF();
    private final RectF iconDstRect = new RectF();
    private final Rect startBounds = new Rect();
    private final int[] selfLoc = new int[2];

    private IconNormalizer.AdaptiveLaunchLayers activeLayers;
    private BitmapShader bgBitmapShader;
    private int splashCardColor = Color.parseColor("#16181D");
    private int adaptiveBgSolidColor = Color.TRANSPARENT;

    private View backgroundScaleTarget;
    private View hiddenSourceIconView;
    private View fadingSourceLabelView;
    private ValueAnimator animator;
    private float rawProgress = 0f;
    private boolean active = false;
    private boolean firedLaunch = false;
    private boolean isWarmLaunch = false;

    public LaunchTransitionView(Context context) {
        this(context, null);
    }

    public LaunchTransitionView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LaunchTransitionView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        cardPaint.setStyle(Paint.Style.FILL);
        bgLayerPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setStyle(Paint.Style.FILL);
        dimPaint.setStyle(Paint.Style.FILL);
        setVisibility(GONE);
    }

    public boolean isAnimating() {
        return active && animator != null && animator.isRunning();
    }

    public void playLaunchExpansion(
            Rect screenStartBounds,
            Drawable sourceIcon,
            View sourceIconView,
            View bgTarget,
            boolean warmLaunch,
            final Runnable launchTrigger
    ) {
        playLaunchExpansion(screenStartBounds, null, sourceIcon, Color.TRANSPARENT, sourceIconView, bgTarget, warmLaunch, launchTrigger);
    }

    public void playLaunchExpansion(
            Rect screenStartBounds,
            ComponentName component,
            Drawable sourceIcon,
            int precomputedAccentColor,
            View sourceIconView,
            View bgTarget,
            boolean warmLaunch,
            final Runnable launchTrigger
    ) {
        if (screenStartBounds == null || screenStartBounds.width() <= 0 || screenStartBounds.height() <= 0) {
            if (launchTrigger != null) launchTrigger.run();
            return;
        }

        cancelExistingAnimation(false);
        isWarmLaunch = warmLaunch;

        getLocationOnScreen(selfLoc);
        startBounds.set(
                screenStartBounds.left - selfLoc[0],
                screenStartBounds.top - selfLoc[1],
                screenStartBounds.right - selfLoc[0],
                screenStartBounds.bottom - selfLoc[1]
        );
        // Trim tiny 2% transparent normalized icon rim so card aligns flush with circular icon edge on frame 0
        int edgeInset = Math.max(1, Math.round(Math.min(startBounds.width(), startBounds.height()) * 0.015f));
        if (startBounds.width() > edgeInset * 4 && startBounds.height() > edgeInset * 4) {
            startBounds.inset(edgeInset, edgeInset);
        }

        hiddenSourceIconView = sourceIconView;
        fadingSourceLabelView = findSiblingLabelView(sourceIconView);
        if (hiddenSourceIconView != null) {
            hiddenSourceIconView.setAlpha(0f);
        }
        if (fadingSourceLabelView != null) {
            fadingSourceLabelView.setAlpha(1f);
        }

        float density = getResources().getDisplayMetrics().density;
        int rasterPx = Math.max(144, Math.round(108f * density));
        String pkg = component != null ? component.getPackageName() : "";
        String act = component != null ? component.getClassName() : "";
        activeLayers = IconNormalizer.extractAdaptiveLaunchLayers(
                getContext(), pkg, act, sourceIcon, precomputedAccentColor, rasterPx
        );

        boolean isNight = LauncherTheme.isNightMode(getContext());
        int sampledBg = (activeLayers != null) ? activeLayers.bgColor : precomputedAccentColor;
        adaptiveBgSolidColor = (Color.alpha(sampledBg) >= 180)
                ? Color.argb(255, Color.red(sampledBg), Color.green(sampledBg), Color.blue(sampledBg))
                : Color.TRANSPARENT;
        splashCardColor = computeSplashSurfaceColor(sampledBg, isNight);

        if (activeLayers != null && activeLayers.bgBitmap != null && !activeLayers.bgBitmap.isRecycled()) {
            bgBitmapShader = new BitmapShader(activeLayers.bgBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            bgShaderPaint.setShader(bgBitmapShader);
        } else {
            bgBitmapShader = null;
            bgShaderPaint.setShader(null);
        }

        backgroundScaleTarget = bgTarget;
        if (backgroundScaleTarget != null) {
            backgroundScaleTarget.animate().cancel();
            if (getWidth() > 0 && getHeight() > 0) {
                backgroundScaleTarget.setPivotX(getWidth() * 0.5f);
                backgroundScaleTarget.setPivotY(getHeight() * 0.5f);
            }
            if (backgroundScaleTarget.getLayerType() != View.LAYER_TYPE_HARDWARE) {
                backgroundScaleTarget.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        }

        rawProgress = 0f;
        active = true;
        firedLaunch = false;
        setVisibility(VISIBLE);
        bringToFront();

        DisplayRefreshHelper.requestInteractiveBoost(getContext(), true);
        if (getContext() instanceof Activity) {
            DisplayRefreshHelper.applyWindowRefreshPreference((Activity) getContext(), true);
        }
        DisplayRefreshHelper.setFrameContentVelocity(this, 4200f);

        final long baseDur = warmLaunch ? WARM_EXPAND_DURATION_MS : COLD_EXPAND_DURATION_MS;
        final long totalDur = Math.max(330L, DisplayRefreshHelper.scaleDurationMs(baseDur));
        final float triggerFrac = warmLaunch ? WARM_LAUNCH_TRIGGER_FRACTION : COLD_LAUNCH_TRIGGER_FRACTION;
        final float targetBgScale = 0.91f;
        final float targetBgAlpha = 0.72f;

        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(totalDur);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float raw = animation.getAnimatedFraction();
                if (!firedLaunch && raw >= triggerFrac) {
                    firedLaunch = true;
                    if (launchTrigger != null) {
                        launchTrigger.run();
                    }
                }
                if (!pacer.shouldRender(animation)) return;
                rawProgress = raw;

                if (fadingSourceLabelView != null) {
                    float labelAlpha = Math.max(0f, 1f - (raw / 0.14f));
                    fadingSourceLabelView.setAlpha(labelAlpha);
                }

                if (backgroundScaleTarget != null) {
                    float bgEase = PIXEL_BG_ZOOM_INTERP.getInterpolation(raw);
                    float s = lerp(1.0f, targetBgScale, bgEase);
                    float a = lerp(1.0f, targetBgAlpha, bgEase);
                    backgroundScaleTarget.setScaleX(s);
                    backgroundScaleTarget.setScaleY(s);
                    backgroundScaleTarget.setAlpha(a);
                }
                invalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!firedLaunch) {
                    firedLaunch = true;
                    if (launchTrigger != null) {
                        launchTrigger.run();
                    }
                }
                animator = null;
            }
        });
        animator.start();
        invalidate();
    }

    public void resetImmediately() {
        cancelExistingAnimation(true);
    }

    private void cancelExistingAnimation(boolean restoreBg) {
        DisplayRefreshHelper.setFrameContentVelocity(this, 0f);
        DisplayRefreshHelper.requestInteractiveBoost(getContext(), false);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (backgroundScaleTarget != null) {
            backgroundScaleTarget.animate().cancel();
            if (backgroundScaleTarget.getLayerType() != View.LAYER_TYPE_NONE) {
                backgroundScaleTarget.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            if (restoreBg) {
                backgroundScaleTarget.setScaleX(1.0f);
                backgroundScaleTarget.setScaleY(1.0f);
                backgroundScaleTarget.setAlpha(1.0f);
            }
        }
        if (hiddenSourceIconView != null) {
            hiddenSourceIconView.setAlpha(1.0f);
            hiddenSourceIconView = null;
        }
        if (fadingSourceLabelView != null) {
            fadingSourceLabelView.setAlpha(1.0f);
            fadingSourceLabelView = null;
        }
        activeLayers = null;
        bgBitmapShader = null;
        bgShaderPaint.setShader(null);
        backgroundScaleTarget = null;
        active = false;
        firedLaunch = false;
        rawProgress = 0f;
        setVisibility(GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!active) return;

        int vw = getWidth();
        int vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        float raw = clamp01(rawProgress);
        float pW = PIXEL_SIZE_X_INTERP.getInterpolation(raw);
        float pH = PIXEL_SIZE_Y_INTERP.getInterpolation(raw);
        float pCenter = PIXEL_CENTER_INTERP.getInterpolation(raw);
        float pBgZoom = PIXEL_BG_ZOOM_INTERP.getInterpolation(raw);
        float density = getResources().getDisplayMetrics().density;

        // Keep surrounding home grid readable through mid-morph, then deepen edge scrim smoothly as app window fades in
        float lateScrim = clamp01((raw - 0.36f) / 0.28f);
        int dimAlpha = Math.round(lerp(72f * Math.min(1f, pBgZoom * 1.25f), 132f, lateScrim));
        dimPaint.setColor(Color.argb(dimAlpha, 0, 0, 0));
        canvas.drawRect(0f, 0f, vw, vh, dimPaint);

        float startW = startBounds.width();
        float startH = startBounds.height();
        float curW = lerp(startW, vw, pW);
        float curH = lerp(startH, vh, pH);
        float curCx = lerp(startBounds.centerX(), vw * 0.5f, pCenter);
        float curCy = lerp(startBounds.centerY(), vh * 0.5f, pCenter);

        float halfW = curW * 0.5f;
        float halfH = curH * 0.5f;
        float left = curCx - halfW;
        float top = curCy - halfH;
        float right = curCx + halfW;
        float bottom = curCy + halfH;
        if (left < 0f) {
            right = Math.min(vw, right - left);
            left = 0f;
        }
        if (right > vw) {
            left = Math.max(0f, left - (right - vw));
            right = vw;
        }
        if (top < 0f) {
            bottom = Math.min(vh, bottom - top);
            top = 0f;
        }
        if (bottom > vh) {
            top = Math.max(0f, top - (bottom - vh));
            bottom = vh;
        }
        cardRect.set(left, top, right, bottom);

        float startRadius = Math.min(startW, startH) * 0.5f;
        float midCardRadius = 40f * density;
        float lateCardRadius = 28f * density;
        float radius;
        if (pW < 0.25f) {
            radius = lerp(startRadius, midCardRadius, pW / 0.25f);
        } else if (raw < 0.84f) {
            float midT = (pW - 0.25f) / 0.75f;
            radius = lerp(midCardRadius, lateCardRadius, midT);
        } else {
            radius = lerp(lateCardRadius, 0f, (raw - 0.84f) / 0.16f);
        }

        // Lightweight 2-pass ambient shadow gives clear 3D elevation off the home grid with zero GPU blur cost
        float shadowStrength = Math.min(1f, pW * 3.5f) * Math.max(0f, 1f - Math.max(0f, (raw - 0.65f) / 0.28f));
        if (shadowStrength > 0.02f) {
            float spreadOuter = 9f * density;
            shadowRect.set(left - spreadOuter * 0.5f, top + 3f * density, right + spreadOuter * 0.5f, bottom + spreadOuter);
            shadowPaint.setColor(Color.argb(Math.round(24f * shadowStrength), 0, 0, 0));
            canvas.drawRoundRect(shadowRect, radius + spreadOuter * 0.4f, radius + spreadOuter * 0.4f, shadowPaint);

            float spreadInner = 4f * density;
            shadowRect.set(left - spreadInner * 0.4f, top + 1.5f * density, right + spreadInner * 0.4f, bottom + spreadInner);
            shadowPaint.setColor(Color.argb(Math.round(36f * shadowStrength), 0, 0, 0));
            canvas.drawRoundRect(shadowRect, radius + spreadInner * 0.3f, radius + spreadInner * 0.3f, shadowPaint);
        }

        // Solid Material splash surface underneath (ramps cleanly to full opacity so Frame 0 legacy rims stay seamless)
        boolean hasOpaqueIconBg = (bgBitmapShader != null || adaptiveBgSolidColor != Color.TRANSPARENT);
        int baseCardAlpha = hasOpaqueIconBg
                ? 255
                : Math.round(255f * clamp01(pW / 0.10f));
        cardPaint.setColor(splashCardColor);
        cardPaint.setAlpha(baseCardAlpha);
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint);

        // Adaptive background color cross-dissolves briskly (~0..65ms) into crisp Material splash surface
        float bgLayerMix = 1f - clamp01(pW / 0.24f);
        if (bgLayerMix > 0.01f) {
            int mixAlpha = Math.round(255f * bgLayerMix);
            if (bgBitmapShader != null && activeLayers != null && activeLayers.bgBitmap != null) {
                Bitmap bgBmp = activeLayers.bgBitmap;
                float scaleX = cardRect.width() / Math.max(1f, bgBmp.getWidth());
                float scaleY = cardRect.height() / Math.max(1f, bgBmp.getHeight());
                float coverScale = Math.max(scaleX, scaleY);
                float drawW = bgBmp.getWidth() * coverScale;
                float drawH = bgBmp.getHeight() * coverScale;
                bgShaderMatrix.reset();
                bgShaderMatrix.setScale(coverScale, coverScale);
                bgShaderMatrix.postTranslate(
                        cardRect.centerX() - drawW * 0.5f,
                        cardRect.centerY() - drawH * 0.5f
                );
                bgBitmapShader.setLocalMatrix(bgShaderMatrix);
                bgShaderPaint.setAlpha(mixAlpha);
                canvas.drawRoundRect(cardRect, radius, radius, bgShaderPaint);
            } else if (adaptiveBgSolidColor != Color.TRANSPARENT) {
                bgLayerPaint.setColor(adaptiveBgSolidColor);
                bgLayerPaint.setAlpha(mixAlpha);
                canvas.drawRoundRect(cardRect, radius, radius, bgLayerPaint);
            }
        }

        // Foreground mark (or legacy circle) scales independently with subtle vertical Pixel parallax
        if (activeLayers != null && activeLayers.fgBitmap != null && !activeLayers.fgBitmap.isRecycled()) {
            float fgT = PIXEL_FG_SCALE_INTERP.getInterpolation(raw);
            float baseIconDia = Math.min(startW, startH);
            float endIconDia = (activeLayers.isAdaptive ? 110f : 96f) * density;
            float currentVisualDia = lerp(baseIconDia, endIconDia, fgT);
            float drawSpan = activeLayers.isAdaptive ? (currentVisualDia * 1.5f) : currentVisualDia;

            // Gentle upward parallax peak mid-flight so foreground feels suspended above the expanding plate
            float parallaxY = (float) Math.sin(Math.PI * fgT) * (-6f * density);
            float cx = cardRect.centerX();
            float cy = cardRect.centerY() + parallaxY;
            float halfSpan = drawSpan * 0.5f;
            iconDstRect.set(cx - halfSpan, cy - halfSpan, cx + halfSpan, cy + halfSpan);

            // Dissolve splash emblem smoothly between ~145ms and ~210ms so the incoming app UI never collides with a frozen icon ghost
            int fgAlpha = (raw < 0.38f) ? 255 : Math.round(255f * (1f - clamp01((raw - 0.38f) / 0.17f)));
            if (fgAlpha > 0) {
                iconBitmapPaint.setAlpha(fgAlpha);
                canvas.drawBitmap(activeLayers.fgBitmap, null, iconDstRect, iconBitmapPaint);
            }
        }
    }

    private static View findSiblingLabelView(View iconView) {
        if (iconView == null) return null;
        ViewParent parent = iconView.getParent();
        if (parent instanceof View) {
            return ((View) parent).findViewById(R.id.tv_app_label);
        }
        return null;
    }

    private static int computeSplashSurfaceColor(int sampledBg, boolean isNight) {
        int base = isNight ? Color.parseColor("#14161B") : Color.parseColor("#F8F9FB");
        if (Color.alpha(sampledBg) < 128) {
            return base;
        }
        int r = Color.red(sampledBg);
        int g = Color.green(sampledBg);
        int b = Color.blue(sampledBg);
        int maxC = Math.max(r, Math.max(g, b));
        int minC = Math.min(r, Math.min(g, b));
        int chroma = maxC - minC;
        float lum = (0.299f * r + 0.587f * g + 0.114f * b);
        if (isNight) {
            if (lum < 68f) {
                return blendColors(sampledBg, Color.parseColor("#121418"), 0.28f);
            }
            if (chroma < 26 && lum > 150f) {
                // Neutral light icon plate (e.g. Calculator/Settings/Drive) settles straight to clean dark Material splash surface
                return base;
            }
            return blendColors(base, sampledBg, 0.20f);
        } else {
            if (lum > 224f) {
                return blendColors(sampledBg, Color.parseColor("#FAFAFC"), 0.35f);
            }
            return blendColors(base, sampledBg, 0.14f);
        }
    }

    private static int blendColors(int c1, int c2, float ratioC2) {
        float t = clamp01(ratioC2);
        float inv = 1f - t;
        int r = Math.round(Color.red(c1) * inv + Color.red(c2) * t);
        int g = Math.round(Color.green(c1) * inv + Color.green(c2) * t);
        int b = Math.round(Color.blue(c1) * inv + Color.blue(c2) * t);
        return Color.argb(255, r, g, b);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}

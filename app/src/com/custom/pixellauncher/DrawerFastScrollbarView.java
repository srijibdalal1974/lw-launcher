package com.custom.pixellauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ListAdapter;

/**
 * Modern Android 17 Material You Expressive rounded pill scrollbar & fast-scrubber
 * for the App Drawer GridView. Replaces the stretched legacy Android scrollbar.
 */
public class DrawerFastScrollbarView extends View {

    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubbleShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbRect = new RectF();
    private final RectF bubbleRect = new RectF();

    private final float density;
    private final float restThumbWidthPx;
    private final float activeThumbWidthPx;
    private final float restThumbHeightPx;
    private final float activeThumbHeightPx;
    private final float rightMarginPx;
    private final float topTrackMarginPx;
    private final float touchStripWidthPx;
    private final float bubbleSizePx;

    private float bottomInsetPx = 0f;
    private float scrollFraction = 0f;
    private float activeAnim = 0f;       // 0 = resting small pill, 1 = active/pressed pill
    private float visibilityAlpha = 0f;  // 0 = hidden (unscrollable), 0.48 = subtle rest, 1 = active
    private float bubbleAnim = 0f;       // 0 = hidden, 1 = visible A-Z bubble

    private int thumbRestColor = 0x7ABDC3CE;
    private int thumbActiveColor = 0xF2A8C7FA;
    private int bubbleBgColor = 0xFF0842A0;
    private int bubbleTextColor = 0xFFD3E3FD;

    private GridView gridView;
    private AppAdapter appAdapter;
    private AbsListView.OnScrollListener externalScrollListener;
    private boolean scrollable = false;
    private boolean isDraggingThumb = false;
    private String currentLetter = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private ValueAnimator stateAnimator;
    private ValueAnimator bubbleAnimator;

    private final Runnable fadeToRestRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isDraggingThumb && scrollable) {
                animateState(0f, 0.45f, 380);
            }
        }
    };

    public DrawerFastScrollbarView(Context context) {
        this(context, null);
    }

    public DrawerFastScrollbarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        restThumbWidthPx = 4.0f * density;
        activeThumbWidthPx = 6.5f * density;
        restThumbHeightPx = 38f * density;
        activeThumbHeightPx = 46f * density;
        rightMarginPx = 3.5f * density;
        topTrackMarginPx = 10f * density;
        bottomInsetPx = 18f * density;
        touchStripWidthPx = 28f * density;
        bubbleSizePx = 52f * density;

        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setTextSize(22f * density);
        letterPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));

        bubbleShadowPaint.setColor(0x28000000);
        applyTheme(context, LauncherTheme.isNightMode(context));
    }

    public void applyTheme(Context context, boolean isNight) {
        thumbRestColor = LauncherTheme.scrollbarRestingColor(context, isNight);
        thumbActiveColor = LauncherTheme.scrollbarActiveColor(context, isNight);
        bubbleBgColor = LauncherTheme.accentContainer(context, isNight);
        bubbleTextColor = LauncherTheme.onAccentContainer(context, isNight);
        invalidate();
    }

    public void setBottomInsetPx(float bottomInsetPx) {
        this.bottomInsetPx = Math.max(8f * density, bottomInsetPx);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (h > 0 && w > 0) {
            syncFromGrid();
        }
    }

    public void setOnExternalScrollListener(AbsListView.OnScrollListener l) {
        this.externalScrollListener = l;
    }

    public void attachToGridView(final GridView gv, AppAdapter adapter) {
        this.gridView = gv;
        this.appAdapter = adapter;
        if (gv == null) return;
        gv.setVerticalScrollBarEnabled(false);
        gv.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (scrollState != SCROLL_STATE_IDLE) {
                    wakeUpScrollbar();
                    DisplayRefreshHelper.requestInteractiveBoost(getContext(), true);
                } else {
                    if (!isDraggingThumb) {
                        scheduleFadeToRest();
                    }
                    DisplayRefreshHelper.requestInteractiveBoost(getContext(), false);
                }
                if (externalScrollListener != null) {
                    externalScrollListener.onScrollStateChanged(view, scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (!isDraggingThumb) {
                    syncFromGrid();
                }
                if (externalScrollListener != null) {
                    externalScrollListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                }
            }
        });
        gv.post(new Runnable() {
            @Override
            public void run() {
                syncFromGrid();
            }
        });
    }

    public void notifyListChanged() {
        if (!isDraggingThumb && appAdapter != null) {
            appAdapter.setHighlightedLetter(gridView, null);
        }
        if (gridView != null) {
            gridView.post(new Runnable() {
                @Override
                public void run() {
                    syncFromGrid();
                }
            });
        }
    }

    public boolean isDragging() {
        return isDraggingThumb;
    }

    public boolean isInTouchZone(float rawX, float rawY) {
        if (!scrollable || getVisibility() != VISIBLE || getWidth() <= 0) {
            return false;
        }
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        float localX = rawX - loc[0];
        float localY = rawY - loc[1];
        return localX >= getWidth() - touchStripWidthPx
                && localX <= getWidth()
                && localY >= 0
                && localY <= getHeight();
    }

    private void syncFromGrid() {
        if (gridView == null || gridView.getCount() == 0 || gridView.getChildCount() == 0) {
            scrollable = false;
            visibilityAlpha = 0f;
            invalidate();
            return;
        }

        int numCols = Math.max(1, gridView.getNumColumns());
        int totalCount = gridView.getCount();
        int totalRows = (totalCount + numCols - 1) / numCols;

        View firstChild = gridView.getChildAt(0);
        int childH = firstChild != null ? firstChild.getHeight() : 0;
        if (childH <= 0) return;

        int vSpacing = Math.max(0, gridView.getVerticalSpacing());
        int rowPitch = childH + vSpacing;
        int firstPos = gridView.getFirstVisiblePosition();
        int firstRow = firstPos / numCols;
        int scrolledInRow = (gridView.getPaddingTop() - firstChild.getTop());

        float totalContentPx = Math.max(0f, totalRows * childH + Math.max(0, totalRows - 1) * vSpacing + gridView.getPaddingTop() + gridView.getPaddingBottom());
        float viewportPx = gridView.getHeight();
        float maxScrollPx = totalContentPx - viewportPx;

        if (maxScrollPx <= 12f * density) {
            scrollable = false;
            scrollFraction = 0f;
            visibilityAlpha = 0f;
            invalidate();
            return;
        }

        boolean wasScrollable = scrollable;
        scrollable = true;
        float currentScrollPx = Math.max(0f, firstRow * rowPitch + scrolledInRow);
        scrollFraction = clamp(currentScrollPx / maxScrollPx, 0f, 1f);

        if (!wasScrollable && visibilityAlpha < 0.45f) {
            visibilityAlpha = 0.45f;
        } else if (gridView.getFirstVisiblePosition() > 0 || scrolledInRow > 2) {
            wakeUpScrollbar();
            scheduleFadeToRest();
        }
        invalidate();
    }

    private void wakeUpScrollbar() {
        if (!scrollable) return;
        handler.removeCallbacks(fadeToRestRunnable);
        if (activeAnim < 0.9f || visibilityAlpha < 0.9f) {
            animateState(1f, 0.96f, 140);
        }
    }

    private void scheduleFadeToRest() {
        handler.removeCallbacks(fadeToRestRunnable);
        handler.postDelayed(fadeToRestRunnable, 1100);
    }

    private void animateState(float targetActive, float targetAlpha, int durationMs) {
        if (stateAnimator != null) {
            stateAnimator.cancel();
        }
        final float startActive = activeAnim;
        final float startAlpha = visibilityAlpha;
        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        stateAnimator = ValueAnimator.ofFloat(0f, 1f);
        stateAnimator.setDuration(DisplayRefreshHelper.scaleDurationMs(durationMs));
        stateAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        stateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!pacer.shouldRender(animation)) return;
                float f = (float) animation.getAnimatedValue();
                activeAnim = startActive + (targetActive - startActive) * f;
                visibilityAlpha = startAlpha + (targetAlpha - startAlpha) * f;
                invalidate();
            }
        });
        stateAnimator.start();
    }

    private void animateBubble(final float target) {
        if (bubbleAnimator != null) {
            bubbleAnimator.cancel();
        }
        final float start = bubbleAnim;
        final DisplayRefreshHelper.FramePacer pacer = new DisplayRefreshHelper.FramePacer();
        bubbleAnimator = ValueAnimator.ofFloat(0f, 1f);
        bubbleAnimator.setDuration(DisplayRefreshHelper.scaleDurationMs(target > 0.5f ? 170 : 140));
        bubbleAnimator.setInterpolator(target > 0.5f ? new OvershootInterpolator(DisplayRefreshHelper.scaleOvershootTension(1.30f)) : new DecelerateInterpolator(1.4f));
        bubbleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (!pacer.shouldRender(animation)) return;
                float f = (float) animation.getAnimatedValue();
                bubbleAnim = start + (target - start) * f;
                invalidate();
            }
        });
        bubbleAnimator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!scrollable || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }

        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (event.getX() >= getWidth() - touchStripWidthPx) {
                    isDraggingThumb = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    wakeUpScrollbar();
                    animateBubble(1f);
                    scrubToY(event.getY(), true);
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (isDraggingThumb) {
                    scrubToY(event.getY(), false);
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDraggingThumb) {
                    isDraggingThumb = false;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (appAdapter != null) {
                        appAdapter.setHighlightedLetter(gridView, null);
                    }
                    animateBubble(0f);
                    scheduleFadeToRest();
                    return true;
                }
                return false;
        }
        return false;
    }

    private void scrubToY(float touchY, boolean initialTouch) {
        if (gridView == null) return;
        float trackTop = topTrackMarginPx;
        float thumbH = lerp(restThumbHeightPx, activeThumbHeightPx, activeAnim);
        float trackBottom = Math.max(trackTop + thumbH + 1f, getHeight() - bottomInsetPx);
        float travel = Math.max(1f, trackBottom - trackTop - thumbH);

        // Map top/bottom thumb zone cleanly to [0..1] so bottom apps (e.g. YouTube) remain above gesture bar
        float rawFrac = (touchY - trackTop - (thumbH * 0.5f)) / travel;
        float frac = clamp((rawFrac - 0.03f) / 0.91f, 0f, 1f);
        scrollFraction = frac;

        ListAdapter adapter = gridView.getAdapter();
        int totalCount = adapter != null ? adapter.getCount() : 0;
        if (totalCount > 0) {
            int numCols = Math.max(1, gridView.getNumColumns());
            int totalRows = (totalCount + numCols - 1) / numCols;

            View firstChild = gridView.getChildAt(0);
            int childH = firstChild != null ? firstChild.getHeight() : Math.round(92f * density);
            int vSpacing = Math.max(0, gridView.getVerticalSpacing());
            int rowPitch = Math.max(1, childH + vSpacing);
            int padTop = gridView.getPaddingTop();
            int padBottom = gridView.getPaddingBottom();
            int viewportH = Math.max(1, gridView.getHeight());
            int totalContentPx = Math.max(0, totalRows * childH + Math.max(0, totalRows - 1) * vSpacing + padTop + padBottom);
            int maxScrollPx = Math.max(0, totalContentPx - viewportH);

            // Map full [0..1] finger range across EVERY app [0 .. totalCount - 1] (A through Z/Y)
            int targetAppIndex = Math.round(frac * (totalCount - 1));
            targetAppIndex = Math.max(0, Math.min(totalCount - 1, targetAppIndex));
            int targetRow = targetAppIndex / numCols;
            float rowTopPx = padTop + targetRow * rowPitch;
            float anchorTopInViewport = lerp(padTop, Math.max(padTop, viewportH - padBottom - childH), frac);
            int targetScrollPx = Math.round(clamp(rowTopPx - anchorTopInViewport, 0f, maxScrollPx));

            int currentFirstRow = gridView.getFirstVisiblePosition() / numCols;
            int scrolledInRow = firstChild != null ? (padTop - firstChild.getTop()) : 0;
            int currentScrollPx = Math.max(0, currentFirstRow * rowPitch + scrolledInRow);

            int maxStep = Math.max(48, Math.round(viewportH * 0.45f));
            if (frac >= 0.99f) {
                int guard = 0;
                while (gridView.canScrollVertically(1) && guard++ < 24) {
                    gridView.scrollListBy(maxStep);
                }
            } else if (frac <= 0.01f) {
                int guard = 0;
                while (gridView.canScrollVertically(-1) && guard++ < 24) {
                    gridView.scrollListBy(-maxStep);
                }
            } else {
                int remaining = targetScrollPx - currentScrollPx;
                int guard = 0;
                while (remaining != 0 && guard++ < 24) {
                    if (remaining > 0 && !gridView.canScrollVertically(1)) break;
                    if (remaining < 0 && !gridView.canScrollVertically(-1)) break;
                    int step = (remaining > 0) ? Math.min(remaining, maxStep) : Math.max(remaining, -maxStep);
                    gridView.scrollListBy(step);
                    remaining -= step;
                }
            }

            Object item = adapter.getItem(targetAppIndex);
            if (item instanceof AppInfo) {
                String ch = AppAdapter.getSectionLetter((AppInfo) item);
                if (!ch.equals(currentLetter)) {
                    currentLetter = ch;
                    if (!initialTouch) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                if (appAdapter != null) {
                    appAdapter.setHighlightedLetter(gridView, currentLetter);
                }
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!scrollable || visibilityAlpha <= 0.01f || getHeight() <= 0 || getWidth() <= 0) {
            return;
        }

        float thumbW = lerp(restThumbWidthPx, activeThumbWidthPx, activeAnim);
        float thumbH = lerp(restThumbHeightPx, activeThumbHeightPx, activeAnim);
        float trackTop = topTrackMarginPx;
        float trackBottom = Math.max(trackTop + thumbH + 1f, getHeight() - bottomInsetPx);
        float travel = Math.max(0f, trackBottom - trackTop - thumbH);

        float top = trackTop + scrollFraction * travel;
        float right = getWidth() - rightMarginPx;
        float left = right - thumbW;
        float bottom = top + thumbH;
        thumbRect.set(left, top, right, bottom);

        int blendedColor = blendColors(thumbRestColor, thumbActiveColor, activeAnim);
        int finalAlpha = Math.round(Color.alpha(blendedColor) * clamp(visibilityAlpha, 0f, 1f));
        thumbPaint.setColor(Color.argb(finalAlpha, Color.red(blendedColor), Color.green(blendedColor), Color.blue(blendedColor)));

        float radius = thumbW * 0.5f;
        canvas.drawRoundRect(thumbRect, radius, radius, thumbPaint);

        if (bubbleAnim > 0.02f && currentLetter != null && !currentLetter.isEmpty()) {
            float scale = 0.72f + 0.28f * bubbleAnim;
            float size = bubbleSizePx * scale;
            float cy = clamp(top + thumbH * 0.5f, trackTop + size * 0.5f, trackBottom - size * 0.5f);
            float bubbleRight = left - 14f * density;
            float bubbleLeft = bubbleRight - size;
            bubbleRect.set(bubbleLeft, cy - size * 0.5f, bubbleRight, cy + size * 0.5f);

            int bAlpha = Math.round(255 * bubbleAnim);
            bubbleShadowPaint.setColor(Color.argb(Math.round(48 * bubbleAnim), 0, 0, 0));
            canvas.save();
            canvas.translate(0f, 2f * density);
            canvas.drawRoundRect(bubbleRect, size * 0.5f, size * 0.5f, bubbleShadowPaint);
            canvas.restore();

            bubblePaint.setColor(Color.argb(bAlpha, Color.red(bubbleBgColor), Color.green(bubbleBgColor), Color.blue(bubbleBgColor)));
            // Pixel / M3 Expressive teardrop-ish circle badge (rounded left/top/bottom, slightly tucked right corner)
            float rBig = size * 0.5f;
            canvas.drawRoundRect(bubbleRect, rBig, rBig, bubblePaint);

            letterPaint.setColor(Color.argb(bAlpha, Color.red(bubbleTextColor), Color.green(bubbleTextColor), Color.blue(bubbleTextColor)));
            letterPaint.setTextSize(22f * density * scale);
            Paint.FontMetrics fm = letterPaint.getFontMetrics();
            float textY = cy - (fm.ascent + fm.descent) * 0.5f;
            canvas.drawText(currentLetter, bubbleRect.centerX(), textY, letterPaint);
        }
    }

    private static float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int blendColors(int c1, int c2, float f) {
        int a = Math.round(lerp(Color.alpha(c1), Color.alpha(c2), f));
        int r = Math.round(lerp(Color.red(c1), Color.red(c2), f));
        int g = Math.round(lerp(Color.green(c1), Color.green(c2), f));
        int b = Math.round(lerp(Color.blue(c1), Color.blue(c2), f));
        return Color.argb(a, r, g, b);
    }
}

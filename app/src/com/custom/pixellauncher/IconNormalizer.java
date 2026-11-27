package com.custom.pixellauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.LruCache;

/**
 * Normalizes all launcher icons (AdaptiveIconDrawable, square/rounded-square bitmaps,
 * circular icons, and irregular legacy cutouts) into uniform anti-aliased Pixel circles.
 */
public final class IconNormalizer {

    private static final int OUTPUT_SIZE_PX = 192;
    private static final int CIRCLE_PADDING_PX = 2;
    private static final int SAMPLE_SIZE_PX = 160;

    private IconNormalizer() {}

    public static Drawable normalizeToPixelCircle(Context context, Drawable raw) {
        if (context == null || raw == null) {
            return raw;
        }
        try {
            Bitmap result = createNormalizedCircleBitmap(raw);
            if (result != null) {
                return new BitmapDrawable(context.getResources(), result);
            }
        } catch (Throwable ignored) {
            // Fall back cleanly to original drawable on any edge asset error
        }
        return raw;
    }

    private static Bitmap createNormalizedCircleBitmap(Drawable raw) {
        final int size = OUTPUT_SIZE_PX;
        final int pad = CIRCLE_PADDING_PX;
        final int cSize = size - (2 * pad);
        final float radius = cSize * 0.5f;
        final float center = size * 0.5f;

        Bitmap contentBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas contentCanvas = new Canvas(contentBmp);

        if (Build.VERSION.SDK_INT >= 26 && raw instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable aid = (AdaptiveIconDrawable) raw;
            Drawable bg = aid.getBackground();
            Drawable fg = aid.getForeground();
            if (bg != null || fg != null) {
                // AdaptiveIconDrawable layers are authored at 108dp with a 72dp circular safe zone (1.5x total -> 0.25x inset)
                int extra = Math.round(cSize * 0.25f);
                Rect layerBounds = new Rect(pad - extra, pad - extra, size - pad + extra, size - pad + extra);
                if (bg != null) {
                    bg.setBounds(layerBounds);
                    bg.draw(contentCanvas);
                } else {
                    contentCanvas.drawColor(Color.WHITE);
                }
                if (fg != null) {
                    fg.setBounds(layerBounds);
                    fg.draw(contentCanvas);
                }
                Bitmap masked = clipBitmapToCircle(contentBmp, size, center, radius);
                contentBmp.recycle();
                return masked;
            }
        }

        // Rasterize legacy / non-adaptive drawable at uniform sample resolution for shape detection
        Bitmap sample = rasterizeSample(raw, SAMPLE_SIZE_PX);
        drawNormalizedLegacyIcon(sample, contentCanvas, size, pad, cSize, center);
        sample.recycle();

        Bitmap masked = clipBitmapToCircle(contentBmp, size, center, radius);
        contentBmp.recycle();
        return masked;
    }

    private static Bitmap rasterizeSample(Drawable raw, int sampleSize) {
        Bitmap sample = Bitmap.createBitmap(sampleSize, sampleSize, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sample);

        int iw = raw.getIntrinsicWidth();
        int ih = raw.getIntrinsicHeight();
        int left = 0;
        int top = 0;
        int right = sampleSize;
        int bottom = sampleSize;
        if (iw > 0 && ih > 0 && iw != ih) {
            float scale = Math.min((float) sampleSize / iw, (float) sampleSize / ih);
            int dw = Math.max(1, Math.round(iw * scale));
            int dh = Math.max(1, Math.round(ih * scale));
            left = (sampleSize - dw) / 2;
            top = (sampleSize - dh) / 2;
            right = left + dw;
            bottom = top + dh;
        }

        raw.setBounds(left, top, right, bottom);
        raw.draw(c);
        return sample;
    }

    private static void drawNormalizedLegacyIcon(
            Bitmap sample,
            Canvas dest,
            int destSize,
            int pad,
            int cSize,
            float center) {
        final int w = sample.getWidth();
        final int h = sample.getHeight();
        int[] pixels = new int[w * h];
        sample.getPixels(pixels, 0, w, 0, 0, w, h);

        int minX = w, minY = h, maxX = -1, maxY = -1;
        long lumSum = 0;
        int opaqueCount = 0;

        for (int y = 0; y < h; y++) {
            int rowOff = y * w;
            for (int x = 0; x < w; x++) {
                int px = pixels[rowOff + x];
                int a = Color.alpha(px);
                if (a > 20) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    if (a > 160) {
                        opaqueCount++;
                        lumSum += (Color.red(px) * 299L + Color.green(px) * 587L + Color.blue(px) * 114L) / 1000L;
                    }
                }
            }
        }

        if (maxX < minX || maxY < minY || opaqueCount == 0) {
            dest.drawColor(Color.WHITE);
            return;
        }

        int boxW = maxX - minX + 1;
        int boxH = maxY - minY + 1;
        float bcx = (minX + maxX) * 0.5f;
        float bcy = (minY + maxY) * 0.5f;
        float halfSpan = Math.max(boxW, boxH) * 0.5f;

        // Inspect corner patches outside inscribed circle & perimeter ring inside bounding box
        int cornerSamples = 0;
        int cornerOpaque = 0;
        int ringSamples = 0;
        int ringOpaque = 0;

        for (int y = minY; y <= maxY; y++) {
            int rowOff = y * w;
            float ny = (y - bcy) / halfSpan;
            float ay = Math.abs(ny);
            for (int x = minX; x <= maxX; x++) {
                float nx = (x - bcx) / halfSpan;
                float ax = Math.abs(nx);
                int a = Color.alpha(pixels[rowOff + x]);

                if (ax >= 0.70f && ax <= 0.90f && ay >= 0.70f && ay <= 0.90f) {
                    cornerSamples++;
                    if (a > 150) cornerOpaque++;
                }

                float r = (float) Math.hypot(nx, ny);
                if (r >= 0.80f && r <= 0.94f) {
                    ringSamples++;
                    if (a > 150) ringOpaque++;
                }
            }
        }

        float cornerRatio = cornerSamples > 0 ? (float) cornerOpaque / cornerSamples : 0f;
        float ringRatio = ringSamples > 0 ? (float) ringOpaque / ringSamples : 0f;
        float aspect = (float) boxW / Math.max(1, boxH);

        Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Rect srcRect = new Rect(minX, minY, maxX + 1, maxY + 1);

        if (cornerRatio >= 0.35f) {
            // Square or rounded-square icon (e.g., Harman, Free Fire, Poppy Playtime, Link to Windows)
            Integer uniformEdgeColor = detectUniformEdgeColor(pixels, w, minX, minY, maxX, maxY);
            if (uniformEdgeColor != null) {
                dest.drawColor(uniformEdgeColor);
                float artSize = cSize * 0.80f;
                float halfArt = artSize * 0.5f;
                float artW = halfArt * (boxW >= boxH ? 2f : 2f * aspect);
                float artH = halfArt * (boxH >= boxW ? 2f : 2f / Math.max(0.1f, aspect));
                RectF dstRect = new RectF(center - artW * 0.5f, center - artH * 0.5f, center + artW * 0.5f, center + artH * 0.5f);
                dest.drawBitmap(sample, srcRect, dstRect, bmpPaint);
            } else {
                // Full-bleed artwork square -> expand slightly (1.03x) so inscribed circle cuts cleanly inside rounded corners
                float bleed = cSize * 0.015f;
                RectF dstRect = new RectF(pad - bleed, pad - bleed, destSize - pad + bleed, destSize - pad + bleed);
                dest.drawBitmap(sample, srcRect, dstRect, bmpPaint);
            }
        } else if (ringRatio >= 0.82f && aspect >= 0.85f && aspect <= 1.18f) {
            // Already circular icon -> fit cleanly to exact standard circle size
            RectF dstRect = new RectF(pad, pad, destSize - pad, destSize - pad);
            dest.drawBitmap(sample, srcRect, dstRect, bmpPaint);
        } else {
            // Irregular transparent cutout silhouette -> place centered on a Pixel adaptive circle plate
            long avgLum = lumSum / opaqueCount;
            dest.drawColor(avgLum > 225 ? 0xFFECEFF1 : Color.WHITE);

            float maxInner = cSize * 0.68f;
            float scale = Math.min(maxInner / boxW, maxInner / boxH);
            float drawW = boxW * scale;
            float drawH = boxH * scale;
            RectF dstRect = new RectF(
                    center - drawW * 0.5f,
                    center - drawH * 0.5f,
                    center + drawW * 0.5f,
                    center + drawH * 0.5f
            );
            dest.drawBitmap(sample, srcRect, dstRect, bmpPaint);
        }
    }

    private static Integer detectUniformEdgeColor(int[] pixels, int stride, int minX, int minY, int maxX, int maxY) {
        int bw = maxX - minX + 1;
        int bh = maxY - minY + 1;
        if (bw < 16 || bh < 16) return null;

        int insetX = Math.max(2, bw / 14);
        int insetY = Math.max(2, bh / 14);
        int midX = (minX + maxX) / 2;
        int midY = (minY + maxY) / 2;

        int[] probeColors = {
                pixels[(minY + insetY) * stride + midX],
                pixels[(maxY - insetY) * stride + midX],
                pixels[midY * stride + (minX + insetX)],
                pixels[midY * stride + (maxX - insetX)]
        };

        int sumR = 0, sumG = 0, sumB = 0;
        for (int c : probeColors) {
            if (Color.alpha(c) < 220) return null;
            sumR += Color.red(c);
            sumG += Color.green(c);
            sumB += Color.blue(c);
        }
        int avgR = sumR / 4;
        int avgG = sumG / 4;
        int avgB = sumB / 4;

        for (int c : probeColors) {
            int diff = Math.abs(Color.red(c) - avgR) + Math.abs(Color.green(c) - avgG) + Math.abs(Color.blue(c) - avgB);
            if (diff > 36) return null;
        }
        return Color.rgb(avgR, avgG, avgB);
    }

    private static Bitmap clipBitmapToCircle(Bitmap src, int size, float center, float radius) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas outCanvas = new Canvas(out);
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        circlePaint.setShader(new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        outCanvas.drawCircle(center, center, radius, circlePaint);
        return out;
    }

    public static final class AdaptiveLaunchLayers {
        public final Bitmap bgBitmap;
        public final int bgColor;
        public final Bitmap fgBitmap;
        public final boolean isAdaptive;

        AdaptiveLaunchLayers(Bitmap bgBitmap, int bgColor, Bitmap fgBitmap, boolean isAdaptive) {
            this.bgBitmap = bgBitmap;
            this.bgColor = bgColor;
            this.fgBitmap = fgBitmap;
            this.isAdaptive = isAdaptive;
        }
    }

    private static final LruCache<String, AdaptiveLaunchLayers> LAUNCH_LAYER_CACHE = new LruCache<>(24);

    public static void prewarmLaunchLayers(final Context context, final java.util.List<AppInfo> apps, final int rasterPx) {
        if (context == null || apps == null || apps.isEmpty()) return;
        final java.util.List<AppInfo> snapshot = new java.util.ArrayList<>(apps);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int count = 0;
                for (AppInfo a : snapshot) {
                    if (a == null || a.packageName.isEmpty()) continue;
                    extractAdaptiveLaunchLayers(context, a.packageName, a.activityName, a.icon, a.splashAccentColor, rasterPx);
                    if (++count >= 16) break;
                }
            }
        }, "LaunchLayerWarmup").start();
    }

    public static int extractSplashTintColor(Drawable raw) {
        if (raw == null) return Color.TRANSPARENT;
        try {
            if (Build.VERSION.SDK_INT >= 26 && raw instanceof AdaptiveIconDrawable) {
                AdaptiveIconDrawable aid = (AdaptiveIconDrawable) raw;
                Drawable bg = aid.getBackground();
                if (bg instanceof ColorDrawable) {
                    int c = ((ColorDrawable) bg).getColor();
                    if (Color.alpha(c) >= 128) return c;
                }
                int sampledBg = sampleDrawableDominantColor(bg, true);
                if (Color.alpha(sampledBg) >= 128) return sampledBg;
                return sampleDrawableDominantColor(raw, false);
            }
            return sampleDrawableDominantColor(raw, true);
        } catch (Throwable ignored) {
            return Color.TRANSPARENT;
        }
    }

    public static AdaptiveLaunchLayers extractAdaptiveLaunchLayers(
            Context context,
            String packageName,
            String activityName,
            Drawable fallbackIcon,
            int precomputedAccent,
            int sizePx) {
        final int targetPx = Math.max(96, sizePx);
        final String pkg = packageName != null ? packageName : "";
        final String act = activityName != null ? activityName : "";
        final String cacheKey = (!pkg.isEmpty()) ? (pkg + "/" + act + "@" + targetPx) : "";

        if (!cacheKey.isEmpty()) {
            AdaptiveLaunchLayers cached = LAUNCH_LAYER_CACHE.get(cacheKey);
            if (cached != null
                    && cached.fgBitmap != null && !cached.fgBitmap.isRecycled()
                    && (cached.bgBitmap == null || !cached.bgBitmap.isRecycled())) {
                return cached;
            }
        }

        Drawable raw = null;
        if (context != null && !pkg.isEmpty()) {
            try {
                PackageManager pm = context.getPackageManager();
                if (!act.isEmpty()) {
                    raw = pm.getActivityIcon(new ComponentName(pkg, act));
                }
                if (raw == null) {
                    raw = pm.getApplicationIcon(pkg);
                }
            } catch (Throwable ignored) {}
        }
        if (raw == null) {
            raw = fallbackIcon;
        }

        AdaptiveLaunchLayers result = null;
        if (Build.VERSION.SDK_INT >= 26 && raw instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable aid = (AdaptiveIconDrawable) raw;
            Drawable bg = aid.getBackground();
            Drawable fg = aid.getForeground();
            int bgColor = Color.alpha(precomputedAccent) >= 128 ? precomputedAccent : extractSplashTintColor(raw);

            Bitmap bgBitmap = null;
            boolean bgIsComplexArtwork = false;
            if (bg != null && !(bg instanceof ColorDrawable)) {
                try {
                    bgBitmap = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888);
                    Canvas bc = new Canvas(bgBitmap);
                    int extra = Math.round(targetPx * 0.25f);
                    Rect prev = bg.copyBounds();
                    bg.setBounds(-extra, -extra, targetPx + extra, targetPx + extra);
                    bg.draw(bc);
                    bg.setBounds(prev);
                    Integer uniform = detectUniformBitmapColor(bgBitmap, 72);
                    if (uniform != null) {
                        bgColor = uniform;
                    } else {
                        // High-contrast multi-color artwork in background (e.g. Chrome wheel): don't stretch across portrait card
                        bgIsComplexArtwork = true;
                    }
                    bgBitmap.recycle();
                    bgBitmap = null;
                } catch (Throwable ignored) {
                    if (bgBitmap != null) {
                        bgBitmap.recycle();
                        bgBitmap = null;
                    }
                }
            }

            Bitmap fgBitmap = null;
            if (!bgIsComplexArtwork && fg != null) {
                try {
                    int fgSize = Math.max(targetPx, Math.round(targetPx * 1.5f));
                    fgBitmap = Bitmap.createBitmap(fgSize, fgSize, Bitmap.Config.ARGB_8888);
                    Canvas fc = new Canvas(fgBitmap);
                    Rect prev = fg.copyBounds();
                    fg.setBounds(0, 0, fgSize, fgSize);
                    fg.draw(fc);
                    fg.setBounds(prev);
                } catch (Throwable ignored) {
                    if (fgBitmap != null) {
                        fgBitmap.recycle();
                        fgBitmap = null;
                    }
                }
            }

            if (fgBitmap == null) {
                if (bgIsComplexArtwork) {
                    bgColor = Color.TRANSPARENT;
                }
                Bitmap full = rasterizeDrawableSafe(fallbackIcon != null ? fallbackIcon : raw, targetPx);
                result = new AdaptiveLaunchLayers(null, bgColor, full, false);
            } else {
                result = new AdaptiveLaunchLayers(null, bgColor, fgBitmap, true);
            }
        }

        if (result == null) {
            Drawable src = fallbackIcon != null ? fallbackIcon : raw;
            int tint = Color.alpha(precomputedAccent) >= 128 ? precomputedAccent : extractSplashTintColor(src);
            Bitmap fg = rasterizeDrawableSafe(src, targetPx);
            result = new AdaptiveLaunchLayers(null, tint, fg, false);
        }

        if (!cacheKey.isEmpty() && result.fgBitmap != null) {
            LAUNCH_LAYER_CACHE.put(cacheKey, result);
        }
        return result;
    }

    private static Bitmap rasterizeDrawableSafe(Drawable d, int sizePx) {
        if (d == null || sizePx <= 0) return null;
        if (d instanceof BitmapDrawable) {
            Bitmap b = ((BitmapDrawable) d).getBitmap();
            if (b != null && !b.isRecycled()) return b;
        }
        try {
            Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            Rect prev = d.copyBounds();
            d.setBounds(0, 0, sizePx, sizePx);
            d.draw(c);
            d.setBounds(prev);
            return bmp;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int sampleDrawableDominantColor(Drawable d, boolean preferEdge) {
        if (d == null) return Color.TRANSPARENT;
        Bitmap tiny = null;
        try {
            tiny = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(tiny);
            Rect prev = d.copyBounds();
            d.setBounds(0, 0, 16, 16);
            d.draw(c);
            d.setBounds(prev);

            long sumR = 0, sumG = 0, sumB = 0;
            int count = 0;
            for (int y = 1; y < 15; y += 2) {
                for (int x = 1; x < 15; x += 2) {
                    if (preferEdge && x > 4 && x < 11 && y > 4 && y < 11) continue;
                    int px = tiny.getPixel(x, y);
                    if (Color.alpha(px) >= 180) {
                        sumR += Color.red(px);
                        sumG += Color.green(px);
                        sumB += Color.blue(px);
                        count++;
                    }
                }
            }
            if (count == 0) {
                for (int y = 3; y < 13; y += 2) {
                    for (int x = 3; x < 13; x += 2) {
                        int px = tiny.getPixel(x, y);
                        if (Color.alpha(px) >= 160) {
                            sumR += Color.red(px);
                            sumG += Color.green(px);
                            sumB += Color.blue(px);
                            count++;
                        }
                    }
                }
            }
            return count > 0 ? Color.rgb((int) (sumR / count), (int) (sumG / count), (int) (sumB / count)) : Color.TRANSPARENT;
        } catch (Throwable ignored) {
            return Color.TRANSPARENT;
        } finally {
            if (tiny != null) tiny.recycle();
        }
    }

    private static Integer detectUniformBitmapColor(Bitmap bmp, int maxSpread) {
        if (bmp == null || bmp.getWidth() < 8 || bmp.getHeight() < 8) return null;
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] pts = {
                bmp.getPixel(w / 5, h / 5),
                bmp.getPixel(w / 2, h / 5),
                bmp.getPixel((4 * w) / 5, h / 5),
                bmp.getPixel(w / 5, h / 2),
                bmp.getPixel(w / 2, h / 2),
                bmp.getPixel((4 * w) / 5, h / 2),
                bmp.getPixel(w / 5, (4 * h) / 5),
                bmp.getPixel((4 * w) / 5, (4 * h) / 5)
        };
        int sr = 0, sg = 0, sb = 0;
        for (int c : pts) {
            if (Color.alpha(c) < 230) return null;
            sr += Color.red(c);
            sg += Color.green(c);
            sb += Color.blue(c);
        }
        int ar = sr / pts.length, ag = sg / pts.length, ab = sb / pts.length;
        for (int c : pts) {
            if (Math.abs(Color.red(c) - ar) + Math.abs(Color.green(c) - ag) + Math.abs(Color.blue(c) - ab) > maxSpread) {
                return null;
            }
        }
        return Color.rgb(ar, ag, ab);
    }
}

package com.custom.pixellauncher;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Resolves system Dark/Light mode and Android 12–17 Material You dynamic palette tokens
 * so the App Drawer, Search Pill, Scrollbar, Popups, and System Bars match the active system theme.
 */
public final class LauncherTheme {

    private LauncherTheme() {}

    public static boolean isNightMode(Context context) {
        if (context == null) return true;
        return isNightMode(context.getResources().getConfiguration());
    }

    public static boolean isNightMode(Configuration config) {
        if (config == null) return true;
        return (config.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int resolveSystemColor(Context context, String resName, int fallbackColor) {
        if (context != null && Build.VERSION.SDK_INT >= 31) {
            try {
                int id = context.getResources().getIdentifier(resName, "color", "android");
                if (id != 0) {
                    return context.getColor(id);
                }
            } catch (Throwable ignored) {}
        }
        return fallbackColor;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int drawerSheetColor(Context context, boolean isNight) {
        if (isNight) {
            return 0xB8181A1C;
        } else {
            int base = resolveSystemColor(context, "system_neutral1_50", 0xFFF0F4FA);
            return withAlpha(base, 0xC4);
        }
    }

    public static int drawerSheetStrokeColor(boolean isNight) {
        return Color.TRANSPARENT;
    }

    public static int drawerSearchSurfaceColor(Context context, boolean isNight) {
        if (isNight) {
            return 0xFF17181A;
        } else {
            return resolveSystemColor(context, "system_neutral1_0", 0xFFFFFFFF);
        }
    }

    public static int drawerSearchStrokeColor(boolean isNight) {
        return Color.TRANSPARENT;
    }

    public static int drawerHandleColor(boolean isNight) {
        return isNight ? 0xCCE4E2E2 : 0x4D000000;
    }

    public static int textPrimary(boolean isNight) {
        return isNight ? 0xFFF1F3F4 : 0xFF1B1C1E;
    }

    public static int textSecondary(boolean isNight) {
        return isNight ? 0xFF9EA6B2 : 0xFF5F6368;
    }

    public static int iconSecondary(boolean isNight) {
        return isNight ? 0xFFE3E5EA : 0xFF444746;
    }

    public static int accentPrimary(Context context, boolean isNight) {
        if (isNight) {
            return resolveSystemColor(context, "system_accent1_200", 0xFFA8C7FA);
        } else {
            return resolveSystemColor(context, "system_accent1_600", 0xFF1A73E8);
        }
    }

    public static int accentContainer(Context context, boolean isNight) {
        if (isNight) {
            return resolveSystemColor(context, "system_accent1_700", 0xFF0842A0);
        } else {
            return resolveSystemColor(context, "system_accent1_100", 0xFFD3E3FD);
        }
    }

    public static int onAccentContainer(Context context, boolean isNight) {
        if (isNight) {
            return resolveSystemColor(context, "system_accent1_100", 0xFFD3E3FD);
        } else {
            return resolveSystemColor(context, "system_accent1_900", 0xFF041E49);
        }
    }

    public static int scrollbarRestingColor(Context context, boolean isNight) {
        if (isNight) {
            return 0x8CDBE4E9;
        } else {
            int neutral = resolveSystemColor(context, "system_neutral2_600", 0xFF5F6368);
            return withAlpha(neutral, 0x70);
        }
    }

    public static int scrollbarActiveColor(Context context, boolean isNight) {
        return isNight ? 0xFFDBE4E9 : withAlpha(accentPrimary(context, isNight), 0xF2);
    }

    public static GradientDrawable makeTopRoundedSheetDrawable(int fillColor, int strokeColor, float topRadiusPx, float strokeWidthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadii(new float[]{
                topRadiusPx, topRadiusPx,
                topRadiusPx, topRadiusPx,
                0f, 0f,
                0f, 0f
        });
        if (strokeWidthPx > 0f && strokeColor != Color.TRANSPARENT) {
            d.setStroke(Math.max(1, Math.round(strokeWidthPx)), strokeColor);
        }
        return d;
    }

    public static GradientDrawable makeRoundedRectDrawable(int fillColor, int strokeColor, float radiusPx, float strokeWidthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(radiusPx);
        if (strokeWidthPx > 0f && strokeColor != Color.TRANSPARENT) {
            d.setStroke(Math.max(1, Math.round(strokeWidthPx)), strokeColor);
        }
        return d;
    }

    public static void applyAppDrawerTheme(
            Context context,
            boolean isNight,
            View appDrawer,
            View drawerHandle,
            View searchBarContainer,
            ImageView ivSearchIcon,
            EditText etSearch,
            ImageView ivActionBtn,
            DrawerFastScrollbarView scrollbarView) {
        float density = context.getResources().getDisplayMetrics().density;

        if (appDrawer != null) {
            appDrawer.setBackground(makeTopRoundedSheetDrawable(
                    drawerSheetColor(context, isNight),
                    drawerSheetStrokeColor(isNight),
                    28f * density,
                    1f * density
            ));
        }

        if (drawerHandle != null) {
            drawerHandle.setBackground(makeRoundedRectDrawable(
                    drawerHandleColor(isNight),
                    Color.TRANSPARENT,
                    2.5f * density,
                    0f
            ));
        }

        if (searchBarContainer != null) {
            searchBarContainer.setBackground(makeRoundedRectDrawable(
                    drawerSearchSurfaceColor(context, isNight),
                    drawerSearchStrokeColor(isNight),
                    28f * density,
                    0.8f * density
            ));
            searchBarContainer.setElevation((isNight ? 0f : 3f) * density);
        }

        int iconTint = iconSecondary(isNight);
        if (ivSearchIcon != null) {
            ivSearchIcon.clearColorFilter();
        }
        if (ivActionBtn != null) {
            ivActionBtn.setColorFilter(iconTint);
        }

        if (etSearch != null) {
            etSearch.setTextColor(textPrimary(isNight));
            etSearch.setHintTextColor(textSecondary(isNight));
        }

        if (scrollbarView != null) {
            scrollbarView.applyTheme(context, isNight);
        }
    }

    public static void applyHomeFallbackSearchTheme(Context context, boolean isNight, View searchPill) {
        if (searchPill == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        int bg = isNight ? 0xEB292C2E : 0xF4FFFFFF;
        int stroke = isNight ? 0x16FFFFFF : 0x12000000;
        searchPill.setBackground(makeRoundedRectDrawable(bg, stroke, 28f * density, 0.8f * density));

        TextView tvHint = searchPill.findViewById(R.id.tv_search_hint);
        if (tvHint != null) {
            tvHint.setTextColor(isNight ? 0xB3E3E5EA : 0xFF5F6368);
        }
        int iconTint = isNight ? 0xFFE3E5EA : 0xFF444746;
        ImageView ivAi = searchPill.findViewById(R.id.btn_ai_mode);
        if (ivAi != null) {
            ivAi.setColorFilter(iconTint);
        }
        ImageView ivMic = searchPill.findViewById(R.id.btn_voice_search);
        if (ivMic != null) {
            ivMic.setColorFilter(iconTint);
        }
        ImageView ivLens = searchPill.findViewById(R.id.btn_lens);
        if (ivLens != null) {
            ivLens.setColorFilter(iconTint);
        }
    }

    public static GradientDrawable makeSegmentedRowDrawable(int fillColor, int strokeColor, float topRadiusPx, float bottomRadiusPx, float strokeWidthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadii(new float[]{
                topRadiusPx, topRadiusPx,
                topRadiusPx, topRadiusPx,
                bottomRadiusPx, bottomRadiusPx,
                bottomRadiusPx, bottomRadiusPx
        });
        if (strokeWidthPx > 0f && strokeColor != Color.TRANSPARENT) {
            d.setStroke(Math.max(1, Math.round(strokeWidthPx)), strokeColor);
        }
        return d;
    }

    public static void applyCustomizationPanelTheme(Context context, boolean isNight, View panelCard) {
        if (panelCard == null) return;
        float density = context.getResources().getDisplayMetrics().density;
        int fill = isNight ? 0xF20B0C0E : withAlpha(resolveSystemColor(context, "system_neutral1_50", 0xFFF4F7FC), 0xF7);
        int stroke = isNight ? 0x1AFFFFFF : 0x14000000;
        panelCard.setBackground(makeRoundedRectDrawable(fill, stroke, 28f * density, 0.8f * density));

        int textColor = textPrimary(isNight);
        int iconTint = iconSecondary(isNight);
        int rowFill = isNight ? 0xFF1B1C20 : 0xFFE9EEF6;
        int rowStroke = isNight ? 0x0FFFFFFF : 0x0A000000;
        float outerR = 20f * density;
        float innerR = 6f * density;
        int[] rowIds = {
                R.id.row_action_wallpaper,
                R.id.row_action_widgets,
                R.id.row_action_apps,
                R.id.row_action_settings
        };
        for (int idx = 0; idx < rowIds.length; idx++) {
            View row = panelCard.findViewById(rowIds[idx]);
            if (row != null) {
                float topR = (idx == 0) ? outerR : innerR;
                float botR = (idx == rowIds.length - 1) ? outerR : innerR;
                row.setBackground(makeSegmentedRowDrawable(rowFill, rowStroke, topR, botR, 0.6f * density));
            }
            if (row instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) row;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    View child = vg.getChildAt(i);
                    if (child instanceof ImageView) {
                        ((ImageView) child).setColorFilter(iconTint);
                    } else if (child instanceof TextView) {
                        ((TextView) child).setTextColor(textColor);
                    }
                }
            }
        }
    }

    public static void updateSystemBarsForDrawer(Window window, boolean isNight, boolean drawerVisiblyOpen) {
        if (window == null) return;
        boolean useLightBarIcons = drawerVisiblyOpen && !isNight;
        View decor = window.getDecorView();

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = decor != null ? decor.getWindowInsetsController() : null;
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int appearance = useLightBarIcons ? mask : 0;
                controller.setSystemBarsAppearance(appearance, mask);
            }
        }
        if (decor != null) {
            int vis = decor.getSystemUiVisibility();
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (useLightBarIcons) {
                vis |= flags;
            } else {
                vis &= ~flags;
            }
            decor.setSystemUiVisibility(vis);
        }
    }
}

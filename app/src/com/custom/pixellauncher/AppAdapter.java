package com.custom.pixellauncher;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppAdapter extends BaseAdapter implements Filterable {
    private final Context context;
    private final List<AppInfo> originalList;
    private List<AppInfo> filteredList;
    private final LayoutInflater inflater;
    private final int iconSizeDp;
    private final float textSizeSp;
    private String highlightedLetter = null;

    public AppAdapter(Context context, List<AppInfo> apps) {
        this(context, apps, 60, 12.5f);
    }

    public AppAdapter(Context context, List<AppInfo> apps, int iconSizeDp, float textSizeSp) {
        this.context = context;
        this.originalList = new ArrayList<>(apps);
        this.filteredList = new ArrayList<>(apps);
        this.inflater = LayoutInflater.from(context);
        this.iconSizeDp = iconSizeDp;
        this.textSizeSp = textSizeSp;
    }

    public void updateList(List<AppInfo> apps) {
        originalList.clear();
        originalList.addAll(apps);
        filteredList.clear();
        filteredList.addAll(apps);
        notifyDataSetChanged();
    }

    public static String getSectionLetter(AppInfo app) {
        if (app == null || app.label == null || app.label.isEmpty()) {
            return "#";
        }
        char c = Character.toUpperCase(app.label.charAt(0));
        return Character.isLetterOrDigit(c) ? String.valueOf(c) : "#";
    }

    public void setHighlightedLetter(GridView gv, String letter) {
        String normalized = (letter != null && !letter.isEmpty()) ? letter.toUpperCase(Locale.getDefault()) : null;
        if (normalized == null ? highlightedLetter == null : normalized.equals(highlightedLetter)) {
            return;
        }
        highlightedLetter = normalized;
        if (gv == null) return;

        int first = gv.getFirstVisiblePosition();
        int childCount = gv.getChildCount();
        boolean isNight = LauncherTheme.isNightMode(context);
        for (int i = 0; i < childCount; i++) {
            View child = gv.getChildAt(i);
            Object tag = child != null ? child.getTag() : null;
            if (tag instanceof ViewHolder) {
                AppInfo app = getItem(first + i);
                applyHighlightState(child, (ViewHolder) tag, app, isNight);
            }
        }
    }

    private void applyHighlightState(View itemView, ViewHolder holder, AppInfo app, boolean isNight) {
        holder.tvLabel.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT);
        if (highlightedLetter == null || app == null) {
            itemView.setAlpha(1.0f);
            itemView.setBackgroundResource(R.drawable.popup_item_bg);
            holder.ivIcon.setScaleX(1.0f);
            holder.ivIcon.setScaleY(1.0f);
            holder.tvLabel.setTextColor(LauncherTheme.textPrimary(isNight));
            holder.tvLabel.setTypeface(Typeface.DEFAULT);
            return;
        }

        boolean matches = highlightedLetter.equals(getSectionLetter(app));
        if (matches) {
            float density = context.getResources().getDisplayMetrics().density;
            int accent = LauncherTheme.accentPrimary(context, isNight);
            int accentBg = LauncherTheme.accentContainer(context, isNight);
            int fill = Color.argb(isNight ? 0x6E : 0xB8, Color.red(accentBg), Color.green(accentBg), Color.blue(accentBg));
            int stroke = Color.argb(isNight ? 0xB0 : 0x99, Color.red(accent), Color.green(accent), Color.blue(accent));
            Drawable cardBg = LauncherTheme.makeRoundedRectDrawable(fill, stroke, 20f * density, 1.2f * density);
            itemView.setBackground(cardBg);
            itemView.setAlpha(1.0f);
            holder.ivIcon.setScaleX(1.07f);
            holder.ivIcon.setScaleY(1.07f);
            holder.tvLabel.setTextColor(LauncherTheme.onAccentContainer(context, isNight));
            holder.tvLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        } else {
            itemView.setBackgroundResource(R.drawable.popup_item_bg);
            itemView.setAlpha(0.36f);
            holder.ivIcon.setScaleX(0.95f);
            holder.ivIcon.setScaleY(0.95f);
            holder.tvLabel.setTextColor(LauncherTheme.textSecondary(isNight));
            holder.tvLabel.setTypeface(Typeface.DEFAULT);
        }
    }

    @Override
    public int getCount() {
        return filteredList.size();
    }

    @Override
    public AppInfo getItem(int position) {
        if (position >= 0 && position < filteredList.size()) {
            return filteredList.get(position);
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_app_grid, parent, false);
            holder = new ViewHolder();
            holder.ivIcon = convertView.findViewById(R.id.iv_app_icon);
            holder.tvLabel = convertView.findViewById(R.id.tv_app_label);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        float density = context.getResources().getDisplayMetrics().density;
        int iconSizePx = Math.round(iconSizeDp * density);
        ViewGroup.LayoutParams iconLp = holder.ivIcon.getLayoutParams();
        if (iconLp != null && (iconLp.width != iconSizePx || iconLp.height != iconSizePx)) {
            iconLp.width = iconSizePx;
            iconLp.height = iconSizePx;
            holder.ivIcon.setLayoutParams(iconLp);
        }
        int pad = iconSizeDp >= 54 ? Math.round(4 * density) : 0;
        if (convertView.getPaddingLeft() != pad || convertView.getPaddingTop() != pad) {
            convertView.setPadding(pad, pad, pad, pad);
        }
        holder.tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp);

        AppInfo app = getItem(position);
        if (app != null) {
            holder.tvLabel.setText(app.label);
            if (app.icon != null) {
                holder.ivIcon.setImageDrawable(app.icon);
            }
            UiMotion.attachIconTouchSpring(convertView, holder.ivIcon);
        } else {
            UiMotion.attachIconTouchSpring(convertView, null);
        }
        applyHighlightState(convertView, holder, app, LauncherTheme.isNightMode(context));

        return convertView;
    }

    static class ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
    }

    public static List<AppInfo> rankMatches(List<AppInfo> source, CharSequence constraint) {
        if (source == null || source.isEmpty()) return new ArrayList<>();
        if (constraint == null) return new ArrayList<>(source);
        String query = constraint.toString().toLowerCase(Locale.getDefault()).trim();
        if (query.isEmpty()) return new ArrayList<>(source);

        List<AppInfo> prefixMatches = new ArrayList<>();
        List<AppInfo> wordMatches = new ArrayList<>();
        List<AppInfo> acronymMatches = new ArrayList<>();
        List<AppInfo> containsMatches = new ArrayList<>();

        for (AppInfo app : source) {
            if (app == null || app.label == null) continue;
            String lower = app.label.toLowerCase(Locale.getDefault());
            if (lower.startsWith(query)) {
                prefixMatches.add(app);
            } else if (matchesWordStart(lower, query)) {
                wordMatches.add(app);
            } else if (query.length() >= 2 && matchesAcronym(lower, query)) {
                acronymMatches.add(app);
            } else if (lower.contains(query)) {
                containsMatches.add(app);
            }
        }

        List<AppInfo> combined = new ArrayList<>(
                prefixMatches.size() + wordMatches.size() + acronymMatches.size() + containsMatches.size());
        combined.addAll(prefixMatches);
        combined.addAll(wordMatches);
        combined.addAll(acronymMatches);
        combined.addAll(containsMatches);
        return combined;
    }

    private static boolean matchesWordStart(String labelLower, String query) {
        String[] parts = labelLower.split("[\\s\\-_:\\.]+");
        for (String p : parts) {
            if (p.startsWith(query)) return true;
        }
        return false;
    }

    private static boolean matchesAcronym(String labelLower, String query) {
        String[] parts = labelLower.split("[\\s\\-_:\\.]+");
        StringBuilder initials = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) initials.append(p.charAt(0));
        }
        if (initials.toString().startsWith(query)) return true;
        // Also match camelCase / packed chars sequentially within query
        int qi = 0;
        for (int i = 0; i < labelLower.length() && qi < query.length(); i++) {
            if (labelLower.charAt(i) == query.charAt(qi)) {
                qi++;
            }
        }
        return qi == query.length() && parts.length >= 2;
    }

    public void filterSync(CharSequence constraint) {
        filteredList = rankMatches(originalList, constraint);
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<AppInfo> matched = rankMatches(originalList, constraint);
                results.values = matched;
                results.count = matched.size();
                return results;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.values != null) {
                    filteredList = (List<AppInfo>) results.values;
                    notifyDataSetChanged();
                }
            }
        };
    }
}

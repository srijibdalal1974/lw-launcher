package com.custom.pixellauncher;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class HomeItemAdapter extends BaseAdapter {
    private final Context context;
    private final List<LauncherItem> items;
    private final LayoutInflater inflater;
    private int cellHeight = 0;
    private int gridColumns = 9;
    private int gridRows = 16;

    public HomeItemAdapter(Context context, List<LauncherItem> items) {
        this(context, items, 0);
    }

    public HomeItemAdapter(Context context, List<LauncherItem> items, int cellHeight) {
        this.context = context;
        this.items = items;
        this.inflater = LayoutInflater.from(context);
        this.cellHeight = cellHeight;
    }

    public void setCellHeight(int cellHeight) {
        if (this.cellHeight != cellHeight && cellHeight > 0) {
            this.cellHeight = cellHeight;
            notifyDataSetChanged();
        }
    }

    public void setGridDensity(int cols, int rows) {
        this.gridColumns = cols;
        this.gridRows = rows;
        notifyDataSetChanged();
    }

    public void updateList(List<LauncherItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public LauncherItem getItem(int position) {
        if (position >= 0 && position < items.size()) {
            return items.get(position);
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

        if (cellHeight > 0) {
            ViewGroup.LayoutParams lp = convertView.getLayoutParams();
            if (lp == null) {
                lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellHeight);
                convertView.setLayoutParams(lp);
            } else if (lp.height != cellHeight) {
                lp.height = cellHeight;
                convertView.setLayoutParams(lp);
            }
        }

        float density = context.getResources().getDisplayMetrics().density;
        int iconSize;
        float textSize;
        if (gridColumns == 4) {
            iconSize = Math.round(60 * density);
            textSize = 12.5f;
        } else if (gridColumns == 5) {
            iconSize = Math.round(54 * density);
            textSize = 11.5f;
        } else {
            iconSize = Math.round(36 * density);
            textSize = 8.5f;
        }
        if (cellHeight > 0 && gridColumns <= 5) {
            int maxIcon = Math.round(cellHeight * 0.66f);
            if (maxIcon > 0 && iconSize > maxIcon) {
                iconSize = maxIcon;
            }
        }

        ViewGroup.LayoutParams iconLp = holder.ivIcon.getLayoutParams();
        if (iconLp != null && (iconLp.width != iconSize || iconLp.height != iconSize)) {
            iconLp.width = iconSize;
            iconLp.height = iconSize;
            holder.ivIcon.setLayoutParams(iconLp);
        }
        holder.tvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize);
        holder.tvLabel.setMaxLines(1);

        LauncherItem item = getItem(position);
        convertView.setVisibility(View.VISIBLE);
        convertView.setAlpha(1.0f);
        convertView.setBackground(null);
        if (item != null) {
            holder.tvLabel.setVisibility(View.VISIBLE);
            holder.ivIcon.setVisibility(View.VISIBLE);
            holder.tvLabel.setText(item.getLabel());
            holder.ivIcon.setImageDrawable(item.getIcon(context));
            UiMotion.attachIconTouchSpring(convertView, holder.ivIcon);
        } else {
            holder.tvLabel.setVisibility(View.INVISIBLE);
            holder.ivIcon.setVisibility(View.INVISIBLE);
            holder.ivIcon.setScaleX(1.0f);
            holder.ivIcon.setScaleY(1.0f);
            UiMotion.attachIconTouchSpring(convertView, null);
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
    }
}

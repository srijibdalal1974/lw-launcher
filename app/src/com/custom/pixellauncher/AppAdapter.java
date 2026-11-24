package com.custom.pixellauncher;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
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

    public AppAdapter(Context context, List<AppInfo> apps) {
        this.context = context;
        this.originalList = new ArrayList<>(apps);
        this.filteredList = new ArrayList<>(apps);
        this.inflater = LayoutInflater.from(context);
    }

    public void updateList(List<AppInfo> apps) {
        originalList.clear();
        originalList.addAll(apps);
        filteredList.clear();
        filteredList.addAll(apps);
        notifyDataSetChanged();
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

        AppInfo app = getItem(position);
        if (app != null) {
            holder.tvLabel.setText(app.label);
            if (app.icon != null) {
                holder.ivIcon.setImageDrawable(app.icon);
            }
        }

        return convertView;
    }

    static class ViewHolder {
        ImageView ivIcon;
        TextView tvLabel;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = originalList;
                    results.count = originalList.size();
                } else {
                    String query = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                    List<AppInfo> matched = new ArrayList<>();
                    for (AppInfo app : originalList) {
                        if (app.label.toLowerCase(Locale.getDefault()).contains(query)) {
                            matched.add(app);
                        }
                    }
                    results.values = matched;
                    results.count = matched.size();
                }
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

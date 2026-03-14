package com.webview.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class TabsAdapter extends RecyclerView.Adapter<TabsAdapter.TabViewHolder> {

    private final List<MainActivity.BrowserTab> tabsList;
    private final OnTabClickListener clickListener;

    public interface OnTabClickListener {
        void onTabSelected(int position);
        void onTabClosed(int position);
    }

    public TabsAdapter(List<MainActivity.BrowserTab> tabsList, OnTabClickListener clickListener) {
        this.tabsList = tabsList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public TabViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tab, parent, false);
        return new TabViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TabViewHolder holder, int position) {
        MainActivity.BrowserTab tab = tabsList.get(position);
        
        holder.titleText.setText(tab.title != null && !tab.title.isEmpty() ? tab.title : "New Tab");
        holder.urlText.setText(tab.url != null && !tab.url.isEmpty() ? tab.url : "");

        holder.itemView.setOnClickListener(v -> clickListener.onTabSelected(position));
        holder.closeButton.setOnClickListener(v -> clickListener.onTabClosed(position));
    }

    @Override
    public int getItemCount() {
        return tabsList.size();
    }

    static class TabViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;
        TextView urlText;
        ImageButton closeButton;

        TabViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.tab_title);
            urlText = itemView.findViewById(R.id.tab_url);
            closeButton = itemView.findViewById(R.id.btn_close_tab);
        }
    }
}

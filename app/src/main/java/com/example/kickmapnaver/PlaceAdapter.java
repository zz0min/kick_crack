package com.example.kickmapnaver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.ViewHolder> {

    private final List<SearchResultItem> searchResultList;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(SearchResultItem item);
    }

    public PlaceAdapter(List<SearchResultItem> searchResultList, OnItemClickListener listener) {
        this.searchResultList = searchResultList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(searchResultList.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return searchResultList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleTextView;
        private final TextView addressTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            addressTextView = itemView.findViewById(R.id.addressTextView);
        }

        public void bind(final SearchResultItem item, final OnItemClickListener listener) {
            titleTextView.setText(item.getTitle());
            addressTextView.setText(item.getRoadAddress());
            itemView.setOnClickListener(v -> listener.onItemClick(item));
        }
    }
}

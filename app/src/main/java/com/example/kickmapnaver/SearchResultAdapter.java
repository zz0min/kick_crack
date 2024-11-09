package com.example.kickmapnaver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private List<SearchResultItem> searchResults;
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(SearchResultItem item);
    }

    public SearchResultAdapter(List<SearchResultItem> searchResults, OnItemClickListener listener) {
        this.searchResults = searchResults;
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchResultItem item = searchResults.get(position);
        holder.titleTextView.setText(item.getTitle());
        holder.addressTextView.setText(item.getAddress());
        holder.roadAddressTextView.setText(item.getRoadAddress());
        holder.distanceTextView.setText("거리: " + String.format("%.2f km", item.getDistance() / 1000));
        holder.timeTextView.setText("예상 시간: " + item.getEstimatedTime() + "분");

        holder.itemView.setOnClickListener(v -> onItemClickListener.onItemClick(item));
    }



    @Override
    public int getItemCount() {
        return searchResults.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView titleTextView;
        TextView addressTextView;
        TextView roadAddressTextView;  // 도로명 주소 추가
        TextView distanceTextView;
        TextView timeTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleTextView = itemView.findViewById(R.id.titleTextView);
            addressTextView = itemView.findViewById(R.id.addressTextView);
            roadAddressTextView = itemView.findViewById(R.id.roadAddressTextView);  // 도로명 주소 뷰 초기화
            distanceTextView = itemView.findViewById(R.id.distanceTextView);  // 거리 뷰 초기화
            timeTextView = itemView.findViewById(R.id.timeTextView);  // 예상 시간 뷰 초기화
        }
    }

}


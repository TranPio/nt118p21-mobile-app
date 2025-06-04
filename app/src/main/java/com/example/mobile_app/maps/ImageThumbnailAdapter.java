package com.example.mobile_app.maps;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;

import com.example.mobile_app.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị thumbnails của ảnh địa điểm
 */
public class ImageThumbnailAdapter extends RecyclerView.Adapter<ImageThumbnailAdapter.ThumbnailViewHolder> {

    private List<Bitmap> images;
    private int selectedPosition = 0;
    private OnThumbnailClickListener listener;
    private Context context;

    public interface OnThumbnailClickListener {
        void onThumbnailClick(int position);
    }

    public ImageThumbnailAdapter(Context context) {
        this.context = context;
        this.images = new ArrayList<>();
    }

    public void setImages(List<Bitmap> images) {
        this.images = images != null ? images : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setSelectedPosition(int position) {
        int oldPosition = this.selectedPosition;
        this.selectedPosition = position;
        notifyItemChanged(oldPosition);
        notifyItemChanged(position);
    }

    public void setOnThumbnailClickListener(OnThumbnailClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ThumbnailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_thumbnail, parent, false);
        return new ThumbnailViewHolder(view);
    }    @Override
    public void onBindViewHolder(@NonNull ThumbnailViewHolder holder, int position) {
        Bitmap image = images.get(position);
        holder.imageView.setImageBitmap(image);
        
        // Highlight selected thumbnail
        if (position == selectedPosition) {
            holder.cardView.setCardElevation(8f);
            holder.cardView.setStrokeWidth(3);
            holder.cardView.setStrokeColor(ContextCompat.getColor(context, R.color.primary_color));
        } else {
            holder.cardView.setCardElevation(2f);
            holder.cardView.setStrokeWidth(0);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onThumbnailClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return images.size();
    }    static class ThumbnailViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView imageView;

        ThumbnailViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView.findViewById(R.id.thumbnail_card);
            imageView = itemView.findViewById(R.id.thumbnail_image);
        }
    }
}

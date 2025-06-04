package com.example.mobile_app.maps;

import android.content.Context;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.mobile_app.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.libraries.places.api.model.AutocompletePrediction;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter tùy chỉnh cho việc hiển thị kết quả tìm kiếm địa điểm với giao diện được cải thiện
 */
public class PlaceAutocompleteAdapter extends ArrayAdapter<AutocompletePrediction> {

    private final Context mContext;
    private final List<AutocompletePrediction> mOriginalResults;
    private final List<AutocompletePrediction> mResults;
    private final LatLng mCurrentLocation;
    private final LayoutInflater mInflater;

    public PlaceAutocompleteAdapter(Context context, List<AutocompletePrediction> results, @Nullable LatLng currentLocation) {
        super(context, R.layout.item_place_result, results);
        mContext = context;
        mResults = results;
        mOriginalResults = new ArrayList<>(results);
        mCurrentLocation = currentLocation;
        mInflater = LayoutInflater.from(context);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_place_result, parent, false);
            holder = new ViewHolder();
            holder.placeIcon = convertView.findViewById(R.id.place_icon);
            holder.placeName = convertView.findViewById(R.id.place_name);
            holder.placeAddress = convertView.findViewById(R.id.place_address);
            holder.placeDistance = convertView.findViewById(R.id.place_distance);
            holder.placeDirections = convertView.findViewById(R.id.place_directions);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        // Lấy item hiện tại
        AutocompletePrediction item = getItem(position);
        if (item != null) {
            // Thiết lập tên địa điểm
            holder.placeName.setText(item.getPrimaryText(null));

            // Thiết lập địa chỉ
            holder.placeAddress.setText(item.getSecondaryText(null));

            // Thiết lập khoảng cách (nếu có thông tin vị trí)
            holder.placeDistance.setVisibility(View.INVISIBLE); // Ẩn mặc định

            // Khi có vị trí và thông tin địa lý của địa điểm
            // setDistanceText(holder.placeDistance, item.getPlaceId());

            // Xử lý sự kiện khi người dùng nhấn vào nút chỉ đường
            holder.placeDirections.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onGetDirectionsClicked(item);
                }
            });
        }

        return convertView;
    }

    /**
     * Thiết lập khoảng cách từ vị trí hiện tại đến địa điểm
     * Phương thức này cần được gọi sau khi lấy được chi tiết địa điểm với thông tin vị trí
     */
    public void setDistanceText(TextView distanceView, LatLng placeLocation) {
        if (mCurrentLocation != null && placeLocation != null) {
            float[] results = new float[1];
            Location.distanceBetween(
                    mCurrentLocation.latitude, mCurrentLocation.longitude,
                    placeLocation.latitude, placeLocation.longitude,
                    results);

            float distanceInMeters = results[0];
            String distanceText;

            // Định dạng khoảng cách
            if (distanceInMeters < 1000) {
                distanceText = Math.round(distanceInMeters) + " m";
            } else {
                DecimalFormat df = new DecimalFormat("#.#");
                distanceText = df.format(distanceInMeters / 1000) + " km";
            }

            distanceView.setText(distanceText);
            distanceView.setVisibility(View.VISIBLE);
        } else {
            distanceView.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Interface để xử lý sự kiện khi người dùng nhấn vào nút chỉ đường
     */
    public interface PlaceAutocompleteAdapterListener {
        void onGetDirectionsClicked(AutocompletePrediction place);
    }

    private PlaceAutocompleteAdapterListener mListener;

    public void setListener(PlaceAutocompleteAdapterListener listener) {
        mListener = listener;
    }

    @Override
    public int getCount() {
        return mResults.size();
    }

    @Nullable
    @Override
    public AutocompletePrediction getItem(int position) {
        return position >= 0 && position < mResults.size() ? mResults.get(position) : null;
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                results.values = mOriginalResults;
                results.count = mOriginalResults.size();
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && results.count > 0) {
                    mResults.clear();
                    //noinspection unchecked
                    mResults.addAll((List<AutocompletePrediction>) results.values);
                    notifyDataSetChanged();
                }
            }
        };
    }

    /**
     * Lấy danh sách các mục kết quả hiện tại
     * @return danh sách các AutocompletePrediction hiện tại
     */
    public List<AutocompletePrediction> getCurrentItems() {
        return new ArrayList<>(mResults);
    }

    private static class ViewHolder {
        ImageView placeIcon;
        TextView placeName;
        TextView placeAddress;
        TextView placeDistance;
        ImageView placeDirections;
    }
}

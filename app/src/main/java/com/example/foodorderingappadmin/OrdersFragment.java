package com.example.foodorderingappadmin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.AdapterView;
import android.graphics.Color; // Import Color class
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat; // For getting color resources
import com.google.android.material.textfield.TextInputLayout;
import java.util.Objects;

public class OrdersFragment extends Fragment {

    private TextInputLayout statusDropdownLayout;
    private AutoCompleteTextView statusAutoComplete;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_orders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        statusDropdownLayout = view.findViewById(R.id.statusDropdownLayout);
        statusAutoComplete = view.findViewById(R.id.statusAutoComplete);

        setupStatusDropdown();
    }

    private void setupStatusDropdown() {
        String[] statusOptions = getResources().getStringArray(R.array.order_status_options);

        // Use the CustomStatusArrayAdapter
        CustomStatusArrayAdapter adapter = new CustomStatusArrayAdapter(
                requireContext(),
                R.layout.list_item_dropdown, // Our custom layout
                statusOptions
        );

        statusAutoComplete.setAdapter(adapter);

        // 4. Set an initial status (e.g., "Pending") and apply its style
        String initialStatus = "Pending";
        statusAutoComplete.setText(initialStatus, false);
        applyStatusStyle(initialStatus);

        // 5. Set a listener to handle when an item is selected from the dropdown
        statusAutoComplete.setOnItemClickListener((parent, view, position, id) -> {
            String selectedStatus = (String) parent.getItemAtPosition(position);
            applyStatusStyle(selectedStatus);
            // TODO: Update backend status here
        });
    }

    // --- CustomStatusArrayAdapter remains the same (Handles dropdown list icons) ---
    private class CustomStatusArrayAdapter extends ArrayAdapter<String> {
        private final int resourceId;

        public CustomStatusArrayAdapter(@NonNull android.content.Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);
            this.resourceId = resource;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            String status = Objects.requireNonNull(getItem(position));

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(resourceId, parent, false);
            }

            TextView statusText = convertView.findViewById(R.id.statusText);
            ImageView statusIcon = convertView.findViewById(R.id.statusIcon);

            statusText.setText(status);

            int iconId = getIconIdForStatus(status);
            if (iconId != 0) {
                statusIcon.setImageResource(iconId);
                statusIcon.setVisibility(View.VISIBLE);
            } else {
                statusIcon.setVisibility(View.GONE);
            }

            return convertView;
        }
    }
    // -----------------------------------------------------------------------------


    /**
     * UPDATED: Sets the visual style (colors and icon) directly on the TextInputLayout
     * based on the selected order status string.
     */
    private void applyStatusStyle(String status) {
        int iconId = getIconIdForStatus(status);

        int bgColorId;
        int tintColorId;

        // 1. Determine the color resources based on status
        switch (status) {
            case "Pending":
                bgColorId = R.color.status_pending_bg;
                tintColorId = R.color.status_pending_tint;
                break;
            case "Being Made":
                bgColorId = R.color.status_made_bg;
                tintColorId = R.color.status_made_tint;
                break;
            case "Being Delivered":
                bgColorId = R.color.status_delivered_bg;
                tintColorId = R.color.status_delivered_tint;
                break;
            case "Cancelled":
                bgColorId = R.color.status_cancelled_bg;
                tintColorId = R.color.status_cancelled_tint;
                break;
            default:
                bgColorId = android.R.color.white; // Default white background
                tintColorId = R.color.text_color_default; // Default gray/black tint
                break;
        }

        // 2. Get the actual color values from resources
        int bgColor = ContextCompat.getColor(requireContext(), bgColorId);
        int tintColor = ContextCompat.getColor(requireContext(), tintColorId);

        // 3. Apply colors directly to the TextInputLayout and AutoCompleteTextView

        // Set background color
        statusDropdownLayout.setBoxBackgroundColor(bgColor);

        // Set icon
        statusDropdownLayout.setStartIconDrawable(iconId);

        // Set icon tint (This colors the icon and the dropdown arrow)
        statusDropdownLayout.setStartIconTintList(ContextCompat.getColorStateList(requireContext(), tintColorId));
        statusDropdownLayout.setEndIconTintList(ContextCompat.getColorStateList(requireContext(), tintColorId));

        // Set text color for the AutoCompleteTextView
        statusAutoComplete.setTextColor(tintColor);
    }

    private int getIconIdForStatus(String status) {
        switch (status) {
            case "Pending":
                return R.drawable.ic_time;
            case "Being Made":
                return R.drawable.ic_chef;
            case "Being Delivered":
                return R.drawable.ic_delivery;
            case "Cancelled":
                return R.drawable.ic_cancel;
            default:
                return 0;
        }
    }

    // NOTE: We no longer need getStyleIdForStatus as we are setting properties directly.
}
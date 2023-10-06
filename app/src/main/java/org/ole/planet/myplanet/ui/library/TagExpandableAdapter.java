package org.ole.planet.myplanet.ui.library;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;

import androidx.appcompat.widget.AppCompatImageView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.databinding.RowAdapterNavigationChildBinding;
import org.ole.planet.myplanet.databinding.RowAdapterNavigationParentBinding;
import org.ole.planet.myplanet.model.RealmTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TagExpandableAdapter extends BaseExpandableListAdapter {
    private Context context;
    private List<RealmTag> tagList;
    private OnClickTagItem clickListener;
    private boolean isSelectMultiple = false;
    private ArrayList<RealmTag> selectedItemsList = new ArrayList<>();
    private HashMap<String, List<RealmTag>> childMap;
    public TagExpandableAdapter(Context context, List<RealmTag> tagList, HashMap<String, List<RealmTag>> childMap, ArrayList<RealmTag> selectedItemsList) {
        this.context = context;
        this.tagList = tagList;
        this.childMap = childMap;
        this.selectedItemsList = selectedItemsList;
    }

    public void setSelectMultiple(boolean selectMultiple) {
        isSelectMultiple = selectMultiple;

    }

    @Override
    public int getGroupCount() {
        return tagList.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (childMap.containsKey(tagList.get(groupPosition).getId())) {
            return childMap.get(tagList.get(groupPosition).getId()).size();
        }
        return 0;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return tagList.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        if (childMap.containsKey(tagList.get(groupPosition).getId())) {
            return childMap.get(tagList.get(groupPosition).getId()).get(childPosition);
        }
        return null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(final int groupPosition, final boolean isExpanded, View convertView, ViewGroup parent) {
        String headerTitle = tagList.get(groupPosition).getName();

        RowAdapterNavigationParentBinding rowAdapterNavigationParentBinding;
        if (convertView == null) {
            rowAdapterNavigationParentBinding = RowAdapterNavigationParentBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            convertView = rowAdapterNavigationParentBinding.getRoot();
            convertView.setTag(rowAdapterNavigationParentBinding);
        } else {
            rowAdapterNavigationParentBinding = (RowAdapterNavigationParentBinding) convertView.getTag();
        }

        rowAdapterNavigationParentBinding.tvDrawerTitle1.setText(headerTitle);
        createCheckbox(convertView, tagList.get(groupPosition));
        rowAdapterNavigationParentBinding.tvDrawerTitle.setText(headerTitle);

        if (!childMap.containsKey(tagList.get(groupPosition).getId())) {
            rowAdapterNavigationParentBinding.tvDrawerTitle1.setVisibility(View.VISIBLE);
            rowAdapterNavigationParentBinding.tvDrawerTitle.setVisibility(View.GONE);
            rowAdapterNavigationParentBinding.ivIndicators.setVisibility(View.GONE);
            rowAdapterNavigationParentBinding.tvDrawerTitle1.setOnClickListener(v -> clickListener.onTagClicked(tagList.get(groupPosition)));
        } else {
            rowAdapterNavigationParentBinding.tvDrawerTitle.setVisibility(View.VISIBLE);
            rowAdapterNavigationParentBinding.tvDrawerTitle1.setOnClickListener(null);
            rowAdapterNavigationParentBinding.tvDrawerTitle1.setVisibility(View.GONE);
            rowAdapterNavigationParentBinding.ivIndicators.setVisibility(View.VISIBLE);
            setExpandedIcon(isExpanded, rowAdapterNavigationParentBinding.ivIndicators);
            rowAdapterNavigationParentBinding.tvDrawerTitle.setOnClickListener(v -> clickListener.onTagClicked(tagList.get(groupPosition)));
        }

        return convertView;
    }

    private void setExpandedIcon(boolean isExpanded, AppCompatImageView ivIndicator) {
        if (isExpanded) {
            ivIndicator.setImageResource(R.drawable.ic_keyboard_arrow_up_black_24dp);
        } else {
            ivIndicator.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);
        }
    }

    private View inflateView(ViewGroup parent, int layout) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(layout, parent, false);
    }

    private void createCheckbox(View convertView, RealmTag tag) {
        CheckBox checkBox = convertView.findViewById(R.id.checkbox);
        checkBox.setVisibility(isSelectMultiple ? View.VISIBLE : View.GONE);
        checkBox.setChecked(selectedItemsList.contains(tag));
        checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
            clickListener.onCheckboxTagSelected(tag);
        });
    }

    @Override
    public View getChildView(final int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        RealmTag tag = (RealmTag) getChild(groupPosition, childPosition);

        RowAdapterNavigationChildBinding rowAdapterNavigationChildBinding;
        if (convertView == null) {
            rowAdapterNavigationChildBinding = RowAdapterNavigationChildBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            convertView = rowAdapterNavigationChildBinding.getRoot();
            convertView.setTag(rowAdapterNavigationChildBinding);
        } else {
            rowAdapterNavigationChildBinding = (RowAdapterNavigationChildBinding) convertView.getTag();
        }

        createCheckbox(convertView, tag);
        rowAdapterNavigationChildBinding.tvDrawerTitle.setText(tag.getName());
        rowAdapterNavigationChildBinding.tvDrawerTitle.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTagClicked(tag);
            }
        });

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    public void setClickListener(OnClickTagItem clickListener) {
        this.clickListener = clickListener;
    }

    public void setTagList(List<RealmTag> filteredList) {
        this.tagList = filteredList;
        notifyDataSetChanged();
    }

    public interface OnClickTagItem {
        void onTagClicked(RealmTag tag);

        void onCheckboxTagSelected(RealmTag tags);
    }
}

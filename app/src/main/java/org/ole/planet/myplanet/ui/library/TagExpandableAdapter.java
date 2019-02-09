package org.ole.planet.myplanet.ui.library;

import android.content.Context;
import android.support.v7.widget.AppCompatImageView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/**
 * Created by rowsun on 1/22/18.
 */

public class TagExpandableAdapter extends BaseExpandableListAdapter {
    private Context context;
    private List<RealmTag> tagList;
    private OnClickTagItem clickListener;
    private boolean isSelectMultiple = false;
    private ArrayList<RealmTag> selectedItemsList = new ArrayList<>();
    private HashMap<String, List<RealmTag>> childMap;


    public ArrayList<RealmTag> getSelectedItemsList() {
        return selectedItemsList;
    }

    public void setSelectMultiple(boolean selectMultiple) {
        isSelectMultiple = selectMultiple;

    }

    public TagExpandableAdapter(Context context, List<RealmTag> tagList, HashMap<String, List<RealmTag>> childMap) {
        this.context = context;
        this.tagList = tagList;
        this.childMap = childMap;
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
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.row_adapter_navigation_parent, parent, false);
        }

        AppCompatImageView ivIndicator = convertView.findViewById(R.id.iv_indicators);
        TextView drawerTitle = convertView.findViewById(R.id.tv_drawer_title);

        TextView nonChildTitle = convertView.findViewById(R.id.tv_drawer_title_1);
        nonChildTitle.setText(headerTitle);
        createCheckbox(convertView, groupPosition);
        drawerTitle.setText(headerTitle);


        if (!childMap.containsKey(tagList.get(groupPosition).getId())) {
            nonChildTitle.setVisibility(View.VISIBLE);
            drawerTitle.setVisibility(View.GONE);
            ivIndicator.setVisibility(View.GONE);
            nonChildTitle.setOnClickListener(v -> clickListener.onTagClicked(tagList.get(groupPosition)));
        } else {
            drawerTitle.setVisibility(View.VISIBLE);
            nonChildTitle.setOnClickListener(null);
            nonChildTitle.setVisibility(View.GONE);
            ivIndicator.setVisibility(View.VISIBLE);
            if (isExpanded) {
                ivIndicator.setImageResource(R.drawable.ic_keyboard_arrow_up_black_24dp);
            } else {
                ivIndicator.setImageResource(R.drawable.ic_keyboard_arrow_down_black_24dp);
            }
            drawerTitle.setOnClickListener(v -> clickListener.onTagClicked(tagList.get(groupPosition)));
        }
        return convertView;
    }

    private void createCheckbox(View convertView, int groupPosition) {
        CheckBox checkBox = convertView.findViewById(R.id.checkbox);
        checkBox.setVisibility(isSelectMultiple ? View.VISIBLE : View.GONE);
        checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
            if (selectedItemsList.contains(tagList.get(groupPosition))) {
                selectedItemsList.remove(tagList.get(groupPosition));
            } else {
                selectedItemsList.add(tagList.get(groupPosition));
            }
        });
    }

    @Override
    public View getChildView(final int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        RealmTag tag = (RealmTag) getChild(groupPosition, childPosition);
        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.row_adapter_navigation_child, parent, false);
        }
        TextView tvDrawerTitle = convertView.findViewById(R.id.tv_drawer_title);
        CheckBox checkBox = convertView.findViewById(R.id.checkbox);
        checkBox.setVisibility(isSelectMultiple ? View.VISIBLE : View.GONE);
        checkBox.setOnCheckedChangeListener((compoundButton, b) -> {
            if (selectedItemsList.contains(tag)) {
                selectedItemsList.remove(tag);
            } else {
                selectedItemsList.add(tag);
            }
        });
        tvDrawerTitle.setText(tag.getName());
        tvDrawerTitle.setOnClickListener(v -> {
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

    }
}

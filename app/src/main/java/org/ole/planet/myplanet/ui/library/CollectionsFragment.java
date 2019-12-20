package org.ole.planet.myplanet.ui.library;


import android.os.Bundle;
import androidx.annotation.Nullable;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;

import com.google.android.material.textfield.TextInputLayout;

import org.ole.planet.myplanet.MainApplication;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.realm.Realm;

/**
 * A simple {@link Fragment} subclass.
 */
public class CollectionsFragment extends DialogFragment implements TagExpandableAdapter.OnClickTagItem, CompoundButton.OnCheckedChangeListener {

    static List<RealmTag> recentList;
    ExpandableListView listTag;
    TextInputLayout tlFilter;
    //    SwitchCompat switchMany;
    Realm mRealm;
    List<RealmTag> list;
    List<RealmTag> filteredList;
    TagExpandableAdapter adapter;
    EditText etFilter;
    Button btnOk;
    String dbType;
    TagClickListener listener;
    private ArrayList<RealmTag> selectedItemsList = new ArrayList<>();

    public CollectionsFragment() {
    }

    public static CollectionsFragment getInstance(List<RealmTag> l, String dbType) {
        recentList = l;
        CollectionsFragment f = new CollectionsFragment();
        Bundle b = new Bundle();
        b.putString("dbType", dbType);
        f.setArguments(b);
        return f;
    }


    public void setListener(TagClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
        if (getArguments() != null)
            dbType = getArguments().getString("dbType");
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_collections, container, false);
        listTag = v.findViewById(R.id.list_tags);
        tlFilter = v.findViewById(R.id.tl_filter);
//        switchMany = v.findViewById(R.id.switch_many);
        etFilter = v.findViewById(R.id.et_filter);
        btnOk = v.findViewById(R.id.btn_ok);
//        switchMany.setOnCheckedChangeListener(this);
        mRealm = new DatabaseService(getActivity()).getRealmInstance();
        filteredList = new ArrayList<>();
        KeyboardUtils.hideSoftKeyboard(getActivity());
        return v;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setListAdapter();
        setListeners();
    }

    private void setListeners() {
        btnOk.setOnClickListener(view -> {
            if (listener != null) {
                listener.onOkClicked(selectedItemsList);
                dismiss();
            }
        });
        etFilter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                filterTags(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void filterTags(String charSequence) {
        filteredList.clear();
        if (charSequence.isEmpty()) {
            adapter.setTagList(list);
            return;
        }
        for (RealmTag t : list) {
            if (t.getName().toLowerCase().contains(charSequence.toLowerCase())) {
                filteredList.add(t);
            }
        }
        adapter.setTagList(filteredList);
    }

    private void setListAdapter() {
        list = mRealm.where(RealmTag.class).equalTo("db", dbType).isNotEmpty("name").equalTo("isAttached", false).findAll();

        selectedItemsList = (ArrayList<RealmTag>) recentList;
        List<RealmTag> allTags = mRealm.where(RealmTag.class).findAll();
        HashMap<String, List<RealmTag>> childMap = new HashMap<>();
        for (RealmTag t : allTags) {
            createChildMap(childMap, t);
        }
        listTag.setGroupIndicator(null);
        adapter = new TagExpandableAdapter(getActivity(), list, childMap, selectedItemsList);
        adapter.setSelectMultiple(true);
        adapter.setClickListener(this);
        listTag.setAdapter(adapter);
        btnOk.setVisibility(View.VISIBLE);
//        switchMany.setChecked(true);
    }

    private void createChildMap(HashMap<String, List<RealmTag>> childMap, RealmTag t) {
        for (String s : t.getAttachedTo()) {
            List<RealmTag> l = new ArrayList<>();
            if (childMap.containsKey(s)) {
                l = childMap.get(s);
            }
            if (!l.contains(t))
                l.add(t);
            childMap.put(s, l);
        }

    }

    @Override
    public void onTagClicked(RealmTag tag) {
        if (listener != null)
            listener.onTagSelected(tag);
        dismiss();
    }

    @Override
    public void onCheckboxTagSelected(RealmTag tag) {
        if (selectedItemsList.contains(tag)) {
            selectedItemsList.remove(tag);
        } else {
            selectedItemsList.add(tag);
        }
    }


    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        //  adapter.setTagList(list);
        MainApplication.isCollectionSwitchOn = b;
        adapter.setSelectMultiple(b);
        adapter.setTagList(list);
        listTag.setAdapter(adapter);
        btnOk.setVisibility(b ? View.VISIBLE : View.GONE);
    }
}

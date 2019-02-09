package org.ole.planet.myplanet.ui.library;


import android.nfc.Tag;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ExpandableListView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.realm.Realm;
import io.realm.RealmList;

/**
 * A simple {@link Fragment} subclass.
 */
public class CollectionsFragment extends DialogFragment implements TagExpandableAdapter.OnClickTagItem, CompoundButton.OnCheckedChangeListener {

    ExpandableListView listTag;
    TextInputLayout tlFilter;
    SwitchCompat switchMany;
    Realm mRealm;
    static List<RealmTag> list;
    List<RealmTag> filteredList;
    TagExpandableAdapter adapter;
    EditText etFilter;
    Button btnOk;

    TagClickListener listener;

    public CollectionsFragment() {
    }

    public static CollectionsFragment getInstance(List<RealmTag> l) {
        list = l;
        return new CollectionsFragment();
    }


    public void setListener(TagClickListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog_NoActionBar_MinWidth);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_collections, container, false);
        listTag = v.findViewById(R.id.list_tags);
        tlFilter = v.findViewById(R.id.tl_filter);
        switchMany = v.findViewById(R.id.switch_many);
        etFilter = v.findViewById(R.id.et_filter);
        btnOk = v.findViewById(R.id.btn_ok);
        switchMany.setOnCheckedChangeListener(this);
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
            Utilities.log("Selected tags size " + adapter.getSelectedItemsList().size());
            if (listener!=null){
                listener.onOkClicked(adapter.getSelectedItemsList());
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
        if(charSequence.isEmpty()){
            adapter.setTagList(list);
            return;
        }
        for (RealmTag t : list) {
            if (t.getName().toLowerCase().contains(charSequence.toLowerCase())){
                filteredList.add(t);
            }
        }
        adapter.setTagList(filteredList);
    }

    private void setListAdapter() {
        if (list.isEmpty())
            list = mRealm.where(RealmTag.class).findAll();
        List<RealmTag> allTags = mRealm.where(RealmTag.class).findAll();
        HashMap<String, RealmTag> map = RealmTag.getListAsMap(allTags);
        HashMap<String, List<RealmTag>> childMap = new HashMap<>();

        for (RealmTag t : allTags) {
            for (String s : t.getAttachedTo()) {
                List<RealmTag> l = new ArrayList<>();
                if (childMap.containsKey(s)) {
                    l = childMap.get(s);
                }
                l.add(t);
                childMap.put(s, l);
            }
        }


        listTag.setGroupIndicator(null);
        adapter = new TagExpandableAdapter(getActivity(), list, childMap);
        adapter.setClickListener(this);
        listTag.setAdapter(adapter);
    }

    @Override
    public void onTagClicked(RealmTag tag) {
        if (listener!=null)
            listener.onTagClicked(tag);
        dismiss();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        adapter.setSelectMultiple(b);
        listTag.setAdapter(adapter);
        btnOk.setVisibility(b ? View.VISIBLE : View.GONE);
    }
}

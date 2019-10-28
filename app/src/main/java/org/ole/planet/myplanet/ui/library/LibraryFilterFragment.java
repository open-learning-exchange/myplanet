package org.ole.planet.myplanet.ui.library;


import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnFilterListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFilterFragment extends DialogFragment implements AdapterView.OnItemClickListener {

    ListView listSub, listLang, listMedium, listLevel;
    Set<String> languages, subjects, mediums, levels;
    OnFilterListener listener;
    Set<String> selectedLang = new HashSet<>();
    Set<String> selectedSubs = new HashSet<>();
    Set<String> selectedMeds = new HashSet<>();
    Set<String> selectedLvls = new HashSet<>();

    public LibraryFilterFragment() {
    }

    public void setListener(OnFilterListener listener) {
        this.listener = listener;
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_library_filter, container, false);
        listLang = v.findViewById(R.id.list_lang);
        listSub = v.findViewById(R.id.list_sub);
        listMedium = v.findViewById(R.id.list_medium);
        listLevel = v.findViewById(R.id.list_level);
        listMedium.setOnItemClickListener(this);
        listLang.setOnItemClickListener(this);
        listLevel.setOnItemClickListener(this);
        listSub.setOnItemClickListener(this);
        v.findViewById(R.id.iv_close).setOnClickListener(vi -> dismiss());
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initList();

    }

    private void initList() {
        languages = listener.getData().get("languages");
        subjects = listener.getData().get("subjects");
        mediums = listener.getData().get("mediums");
        levels = listener.getData().get("levels");
        selectedLvls = listener.getSelectedFilter().get("levels");
        selectedSubs = listener.getSelectedFilter().get("subjects");
        selectedMeds = listener.getSelectedFilter().get("mediums");
        selectedLang = listener.getSelectedFilter().get("languages");
        setAdapter(listLevel, levels, selectedLvls);
        setAdapter(listLang, languages, selectedLang);
        setAdapter(listMedium, mediums, selectedMeds);
        setAdapter(listSub, subjects, selectedSubs);
    }

    private void setAdapter(ListView listView, Set<String> ar, Set<String> set) {
        ArrayList<String> arr = new ArrayList<>(ar);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        listView.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, arr));
        for (int i = 0; i < arr.size(); i++) {
            listView.setItemChecked(i, set.contains(arr.get(i)));
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        if (listener != null) {
            String s = (String) adapterView.getItemAtPosition(i);
            int id = adapterView.getId();
            if (id == R.id.list_lang) {
                addToList(s, selectedLang);
            } else if (id == R.id.list_sub)
                addToList(s, selectedSubs);
            else if (id == R.id.list_level)
                addToList(s, selectedLvls);
            else if (id == R.id.list_medium)
                addToList(s, selectedMeds);
            listener.filter(selectedSubs, selectedLang, selectedMeds, selectedLvls);
            initList();
        }
    }

    public void addToList(String s, Set<String> list) {
        if (list.contains(s))
            list.remove(s);
        else
            list.add(s);
    }
}

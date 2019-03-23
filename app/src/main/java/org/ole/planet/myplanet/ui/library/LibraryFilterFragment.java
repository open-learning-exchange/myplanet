package org.ole.planet.myplanet.ui.library;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetDialogFragment;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.utilities.CheckboxListView;
import org.ole.planet.myplanet.utilities.Utilities;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFilterFragment extends BottomSheetDialogFragment {

    CheckboxListView listSub, listLang, listMedium, listLevel;
    String[] languages, subjects, mediums, levels;
    public LibraryFilterFragment() {
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments()!=null){
            languages = getArguments().getStringArray("languages");
            subjects = getArguments().getStringArray("subjects");
            mediums = getArguments().getStringArray("mediums");
            levels = getArguments().getStringArray("levels");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v =  inflater.inflate(R.layout.fragment_library_filter, container, false);
        listLang = v.findViewById(R.id.list_lang);
        listSub = v.findViewById(R.id.list_sub);
        listMedium = v.findViewById(R.id.list_medium);
        listLevel = v.findViewById(R.id.list_level);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listLang.setAdapter(getAdapter(languages));
        listMedium.setAdapter(getAdapter(mediums));
        Utilities.log(mediums.length + " med");
        listSub.setAdapter(getAdapter(subjects));
        listLevel.setAdapter(getAdapter(levels));
    }

    private ListAdapter getAdapter(String[] arr) {
       return new ArrayAdapter<>(getActivity(), R.layout.rowlayout, R.id.checkBoxRowLayout, arr);

    }
}

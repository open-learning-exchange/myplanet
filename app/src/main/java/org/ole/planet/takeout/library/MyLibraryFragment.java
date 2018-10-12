package org.ole.planet.takeout.library;


import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ArraySet;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;

import org.ole.planet.takeout.Data.realm_myLibrary;

import org.ole.planet.takeout.R;
import org.ole.planet.takeout.base.BaseRecyclerFragment;
import org.ole.planet.takeout.callback.OnLibraryItemSelected;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipDeletedListener;

/**
 * A simple {@link Fragment} subclass.
 */
public class MyLibraryFragment extends BaseRecyclerFragment<realm_myLibrary> implements OnLibraryItemSelected, ChipDeletedListener, View.OnKeyListener {

    TextView tvAddToLib, tvDelete;

    EditText etSearch, etTags;
    ImageView imgSearch;
    AdapterLibrary adapterLibrary;
    FlexboxLayout flexBoxTags;
    List<String> searchTags;
    ChipCloudConfig config;

    public MyLibraryFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_my_library;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        adapterLibrary = new AdapterLibrary(getActivity(), getList(realm_myLibrary.class));
        adapterLibrary.setListener(this);
        searchTags = new ArrayList();
        return adapterLibrary;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay);
        tvAddToLib = getView().findViewById(R.id.tv_add_to_lib);
        tvDelete = getView().findViewById(R.id.tv_delete);
        etSearch = getView().findViewById(R.id.et_search);
        etTags = getView().findViewById(R.id.et_tags);
        imgSearch = getView().findViewById(R.id.img_search);
        flexBoxTags = getView().findViewById(R.id.flexbox_tags);
        tvAddToLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addToMyList();
            }
        });
        imgSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                adapterLibrary.setLibraryList(search(etSearch.getText().toString(), realm_myLibrary.class));
            }
        });
        etTags.setOnKeyListener(this);
    }


    @Override
    public void onSelectedListChange(List<realm_myLibrary> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    @Override
    public void onTagClicked(String text) {
        Utilities.log("Tag " + text);
        flexBoxTags.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(getActivity(), flexBoxTags, config);
        chipCloud.setDeleteListener(this);
        if (!searchTags.contains(text) && !text.isEmpty())
            searchTags.add(text);
        chipCloud.addChips(searchTags);
        adapterLibrary.setLibraryList(filterByTag(searchTags.toArray(new String[searchTags.size()])));
    }

    private void changeButtonStatus() {
        tvDelete.setEnabled(selectedItems.size() > 0);
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }

    @Override
    public void chipDeleted(int i, String s) {
        searchTags.remove(i);
        adapterLibrary.setLibraryList(filterByTag(searchTags.toArray(new String[searchTags.size()])));

    }

    @Override
    public boolean onKey(View view, int i, KeyEvent keyEvent) {
        if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_SPACE || keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            onTagClicked(etTags.getText().toString());
            etTags.setText("");
        }
        return true;
    }
}

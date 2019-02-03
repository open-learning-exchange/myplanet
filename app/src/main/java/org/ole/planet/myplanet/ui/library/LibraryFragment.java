package org.ole.planet.myplanet.ui.library;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipDeletedListener;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends BaseRecyclerFragment<RealmMyLibrary> implements OnLibraryItemSelected, ChipDeletedListener, TextWatcher {

    TextView tvAddToLib;

    EditText etSearch, etTags;
    ImageView imgSearch;
    AdapterLibrary adapterLibrary;
    FlexboxLayout flexBoxTags;
    List<String> searchTags;
    ChipCloudConfig config;

    public LibraryFragment() {
    }



    @Override
    public int getLayout() {
        return R.layout.fragment_my_library;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        HashMap<String, JsonObject> map = RealmRating.getRatings(mRealm, "resource", model.getId());
        adapterLibrary = new AdapterLibrary(getActivity(), getList(RealmMyLibrary.class), map);
        adapterLibrary.setRatingChangeListener(this);
        adapterLibrary.setListener(this);
        searchTags = new ArrayList();
        return adapterLibrary;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay);
        tvAddToLib = getView().findViewById(R.id.tv_add);
        etSearch = getView().findViewById(R.id.et_search);
        etTags = getView().findViewById(R.id.et_tags);
        imgSearch = getView().findViewById(R.id.img_search);
        flexBoxTags = getView().findViewById(R.id.flexbox_tags);
        tvAddToLib.setOnClickListener(view -> addToMyList());
        imgSearch.setOnClickListener(view -> adapterLibrary.setLibraryList(filterByTag(searchTags.toArray(new String[searchTags.size()]), etSearch.getText().toString())));
        etTags.addTextChangedListener(this);
    }


    @Override
    public void onSelectedListChange(List<RealmMyLibrary> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    @Override
    public void onTagClicked(String text) {
        flexBoxTags.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(getActivity(), flexBoxTags, config);
        chipCloud.setDeleteListener(this);
        if (!searchTags.contains(text) && !text.isEmpty())
            searchTags.add(text);
        chipCloud.addChips(searchTags);
        adapterLibrary.setLibraryList(filterByTag(searchTags.toArray(new String[searchTags.size()]), etSearch.getText().toString()));

    }

    private void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }

    @Override
    public void chipDeleted(int i, String s) {
        searchTags.remove(i);
        adapterLibrary.setLibraryList(filterByTag(searchTags.toArray(new String[searchTags.size()]), etSearch.getText().toString()));

    }

    @Override
    public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

    }

    @Override
    public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        if (!charSequence.toString().isEmpty()) {
            String lastChar = charSequence.toString().substring(charSequence.length() - 1);
            if (lastChar.equals(" ") || lastChar.equals("\n")) {
                onTagClicked(etTags.getText().toString().trim());
                etTags.setText("");
            }
        }
    }

    @Override
    public void afterTextChanged(Editable editable) {

    }

}

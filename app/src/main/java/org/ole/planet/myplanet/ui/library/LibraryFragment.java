package org.ole.planet.myplanet.ui.library;


import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnFilterListener;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipDeletedListener;

import static org.ole.planet.myplanet.model.RealmMyLibrary.*;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends BaseRecyclerFragment<RealmMyLibrary> implements OnLibraryItemSelected, ChipDeletedListener, TagClickListener, OnFilterListener {

    TextView tvAddToLib, tvMessage, tvSelected;

    EditText etSearch, etTags;

    ImageView imgSearch;
    AdapterLibrary adapterLibrary;
    FlexboxLayout flexBoxTags;
    List<RealmTag> searchTags;
    ChipCloudConfig config;
    Button clearTags;

    public LibraryFragment() {
    }


    @Override
    public int getLayout() {
        return R.layout.fragment_my_library;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        HashMap<String, JsonObject> map = RealmRating.getRatings(mRealm, "resource", model.getId());
        HashMap<String, RealmTag> tagMap = RealmTag.getListAsMap(mRealm.where(RealmTag.class).findAll());
        adapterLibrary = new AdapterLibrary(getActivity(), getList(RealmMyLibrary.class), map, tagMap);
        adapterLibrary.setRatingChangeListener(this);
        adapterLibrary.setListener(this);
        searchTags = new ArrayList<>();

        return adapterLibrary;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay);
        tvAddToLib = getView().findViewById(R.id.tv_add);
        etSearch = getView().findViewById(R.id.et_search);
        etTags = getView().findViewById(R.id.et_tags);
        clearTags = getView().findViewById(R.id.btn_clear_tags);
        tvSelected = getView().findViewById(R.id.tv_selected);
        tvMessage = getView().findViewById(R.id.tv_message);
        imgSearch = getView().findViewById(R.id.img_search);
        flexBoxTags = getView().findViewById(R.id.flexbox_tags);
        initArrays();

        tvAddToLib.setOnClickListener(view -> addToMyList());
        imgSearch.setOnClickListener(view -> {
            adapterLibrary.setLibraryList(filterByTag(searchTags, etSearch.getText().toString().trim()));
            showNoData(tvMessage, adapterLibrary.getItemCount());
            KeyboardUtils.hideSoftKeyboard(getActivity());
        });
        //  etTags.addTextChangedListener(this);
        getView().findViewById(R.id.btn_collections).setOnClickListener(view -> {
            CollectionsFragment f = CollectionsFragment.getInstance(searchTags);
            f.setListener(LibraryFragment.this);
            f.show(getChildFragmentManager(), "");
        });
        setSearchListener();
        showNoData(tvMessage, adapterLibrary.getItemCount());
        clearTagsButton();
        getView().findViewById(R.id.show_filter).setOnClickListener(v -> {
            LibraryFilterFragment f = new LibraryFilterFragment();
            f.setListener(this);
            f.show(getChildFragmentManager(), "");
        });
    }

    private void initArrays() {
        subjects = new HashSet<>();
        languages = new HashSet<>();
        levels = new HashSet<>();
        mediums = new HashSet<>();
    }


    private void clearTagsButton() {
        clearTags.setOnClickListener(vi -> {
            searchTags.clear();
            etSearch.setText("");
            tvSelected.setText("");
            adapterLibrary.setLibraryList(filterByTag(searchTags, ""));
            showNoData(tvMessage, adapterLibrary.getItemCount());
        });
    }

    private void setSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (!charSequence.toString().isEmpty()) {
                    String lastChar = charSequence.toString().substring(charSequence.length() - 1);
                    if (lastChar.equals(" ") || lastChar.equals("\n")) {
                        adapterLibrary.setLibraryList(filterByTag(searchTags, etSearch.getText().toString().trim()));
                        etSearch.setText(etSearch.getText().toString().trim());
                        KeyboardUtils.hideSoftKeyboard(getActivity());
                        showNoData(tvMessage, adapterLibrary.getItemCount());
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }


    @Override
    public void onSelectedListChange(List<RealmMyLibrary> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    @Override
    public void onTagClicked(RealmTag realmTag) {
        flexBoxTags.removeAllViews();
        final ChipCloud chipCloud = new ChipCloud(getActivity(), flexBoxTags, config);
        chipCloud.setDeleteListener(this);
        if (!searchTags.contains(realmTag))
            searchTags.add(realmTag);
        chipCloud.addChips(searchTags);
        adapterLibrary.setLibraryList(filterByTag(searchTags, etSearch.getText().toString()));
        showTagText(searchTags);
        showNoData(tvMessage, adapterLibrary.getItemCount());

    }

    void showTagText(List<RealmTag> list) {
        StringBuilder selected = new StringBuilder("Selected : ");
        for (RealmTag tags :
                list) {
            selected.append(tags.getName()).append(",");
        }
        tvSelected.setText(selected.subSequence(0, selected.length() - 2));
    }

    @Override
    public void onTagSelected(RealmTag tag) {
        List<RealmTag> li = new ArrayList<>();
        li.add(tag);
        searchTags = li;
        tvSelected.setText("Selected : " + tag.getName());
        adapterLibrary.setLibraryList(filterByTag(li, etSearch.getText().toString()));
        showNoData(tvMessage, adapterLibrary.getItemCount());
    }

    @Override
    public void onOkClicked(List<RealmTag> list) {
        for (RealmTag tag : list) {
            onTagClicked(tag);
        }
    }

    private void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }

    @Override
    public void chipDeleted(int i, String s) {
        searchTags.remove(i);
        adapterLibrary.setLibraryList(filterByTag(searchTags, etSearch.getText().toString()));
        showNoData(tvMessage, adapterLibrary.getItemCount());
    }


    @Override
    public void filter(Set<String> subjects, Set<String> languages, Set<String> mediums, Set<String> levels) {
        this.subjects = subjects;
        this.languages = languages;
        this.mediums = mediums;
        this.levels = levels;
        adapterLibrary.setLibraryList(filterByTag(searchTags, etSearch.getText().toString().trim()));
        showNoData(tvMessage, adapterLibrary.getItemCount());
    }


    @Override
    public Map<String, Set<String>> getData() {
        Map<String, Set<String>> b = new HashMap<>();
        b.put("languages", getArrayList(adapterLibrary.getLibraryList(), "languages"));
        b.put("subjects", getSubjects(adapterLibrary.getLibraryList()));
        b.put("mediums", getArrayList(adapterLibrary.getLibraryList(), "mediums"));
        b.put("levels", getLevels(adapterLibrary.getLibraryList()));
        return b;
    }

    @Override
    public Map<String, Set<String>> getSelectedFilter() {
        Map<String, Set<String>> b = new HashMap<>();
        b.put("languages", languages);
        b.put("subjects", subjects);
        b.put("mediums", mediums);
        b.put("levels", levels);
        return b;
    }
}

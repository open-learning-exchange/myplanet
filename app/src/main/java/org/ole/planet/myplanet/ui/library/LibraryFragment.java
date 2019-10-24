package org.ole.planet.myplanet.ui.library;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
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

import static org.ole.planet.myplanet.model.RealmMyLibrary.getArrayList;
import static org.ole.planet.myplanet.model.RealmMyLibrary.getLevels;
import static org.ole.planet.myplanet.model.RealmMyLibrary.getSubjects;

/**
 * A simple {@link Fragment} subclass.
 */
public class LibraryFragment extends BaseRecyclerFragment<RealmMyLibrary> implements OnLibraryItemSelected, ChipDeletedListener, TagClickListener, OnFilterListener {

    TextView tvAddToLib, tvSelected;

    EditText etSearch, etTags;

    ImageView imgSearch;
    AdapterLibrary adapterLibrary;
    FlexboxLayout flexBoxTags;
    List<RealmTag> searchTags;
    ChipCloudConfig config;
    Button clearTags, orderByDate, orderByTitle;

    public LibraryFragment() {
    }


    @Override
    public int getLayout() {
        return R.layout.fragment_my_library;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        HashMap<String, JsonObject> map = RealmRating.getRatings(mRealm, "resource", model.getId());
        adapterLibrary = new AdapterLibrary(getActivity(), getList(RealmMyLibrary.class), map, mRealm);
        adapterLibrary.setRatingChangeListener(this);
        adapterLibrary.setListener(this);
        return adapterLibrary;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        searchTags = new ArrayList<>();
        config = Utilities.getCloudConfig().showClose(R.color.black_overlay);
        tvAddToLib = getView().findViewById(R.id.tv_add);
        etSearch = getView().findViewById(R.id.et_search);
        etTags = getView().findViewById(R.id.et_tags);
        clearTags = getView().findViewById(R.id.btn_clear_tags);
        tvSelected = getView().findViewById(R.id.tv_selected);
//        tvMessage = getView().findViewById(R.id.tv_message);
        imgSearch = getView().findViewById(R.id.img_search);
        flexBoxTags = getView().findViewById(R.id.flexbox_tags);
        initArrays();

        tvAddToLib.setOnClickListener(view -> addToMyList());
        imgSearch.setOnClickListener(view -> {
            adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString().trim(), searchTags)));
            showNoData(tvMessage, adapterLibrary.getItemCount());
            KeyboardUtils.hideSoftKeyboard(getActivity());
        });
        //  etTags.addTextChangedListener(this);
        getView().findViewById(R.id.btn_collections).setOnClickListener(view -> {
            CollectionsFragment f = CollectionsFragment.getInstance(searchTags, "resources");
            f.setListener(LibraryFragment.this);
            f.show(getChildFragmentManager(), "");
        });
        //setSearchListener();
        showNoData(tvMessage, adapterLibrary.getItemCount());
        clearTagsButton();
        getView().findViewById(R.id.show_filter).setOnClickListener(v -> {
            LibraryFilterFragment f = new LibraryFilterFragment();
            f.setListener(this);
            f.show(getChildFragmentManager(), "");
        });
        KeyboardUtils.setupUI(getView().findViewById(R.id.my_library_parent_layout), getActivity());
        changeButtonStatus();
        tvFragmentInfo = getView().findViewById(R.id.tv_fragment_info);
        if(!isMyCourseLib) tvFragmentInfo.setText("Our Library");
        orderByDate = getView().findViewById(R.id.order_by_date_button);
        orderByTitle = getView().findViewById(R.id.order_by_title_button);
        orderByDate.setOnClickListener(view -> adapterLibrary.setLibraryList(getList(RealmMyLibrary.class,"uploadDate")));
        orderByTitle.setOnClickListener(view -> adapterLibrary.setLibraryList(getList(RealmMyLibrary.class,"title")));
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
            this.levels.clear();
            this.mediums.clear();
            this.subjects.clear();
            this.languages.clear();
            adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag("", searchTags)));
            showNoData(tvMessage, adapterLibrary.getItemCount());
        });
    }

//    private void setSearchListener() {
//        etSearch.addTextChangedListener(new TextWatcher() {
//            @Override
//            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//
//            }
//
//            @Override
//            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
//                if (!charSequence.toString().isEmpty()) {
//                    String lastChar = charSequence.toString().substring(charSequence.length() - 1);
//                    if (lastChar.equals(" ") || lastChar.equals("\n")) {
//                        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString().trim(), searchTags)));
//                        etSearch.setText(etSearch.getText().toString().trim());
//                        KeyboardUtils.hideSoftKeyboard(getActivity());
//                        showNoData(tvMessage, adapterLibrary.getItemCount());
//                    }
//                }
//            }
//
//            @Override
//            public void afterTextChanged(Editable editable) {
//
//            }
//        });
//    }


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
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString(), searchTags)));
        showTagText(searchTags, tvSelected);
        showNoData(tvMessage, adapterLibrary.getItemCount());

    }


    @Override
    public void onTagSelected(RealmTag tag) {
        List<RealmTag> li = new ArrayList<>();
        li.add(tag);
        searchTags = li;
        tvSelected.setText("Selected : " + tag.getName());
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString(), li)));
        showNoData(tvMessage, adapterLibrary.getItemCount());
    }

    @Override
    public void onOkClicked(List<RealmTag> list) {
        if (list.isEmpty()) {
            searchTags.clear();
            adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString(), searchTags)));
            showNoData(tvMessage, adapterLibrary.getItemCount());
        } else {
            for (RealmTag tag : list) {
                onTagClicked(tag);
            }
        }
    }

    private void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }

    @Override
    public void chipDeleted(int i, String s) {
        searchTags.remove(i);
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString(), searchTags)));
        showNoData(tvMessage, adapterLibrary.getItemCount());
    }


    @Override
    public void filter(Set<String> subjects, Set<String> languages, Set<String> mediums, Set<String> levels) {
        this.subjects = subjects;
        this.languages = languages;
        this.mediums = mediums;
        this.levels = levels;
        adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString().trim(), searchTags)));
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

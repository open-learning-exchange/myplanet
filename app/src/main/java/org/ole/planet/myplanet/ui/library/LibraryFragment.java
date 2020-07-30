package org.ole.planet.myplanet.ui.library;


import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.flexbox.FlexboxLayout;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnFilterListener;
import org.ole.planet.myplanet.callback.OnLibraryItemSelected;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmSearchActivity;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.survey.AdapterSurvey;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import fisk.chipcloud.ChipCloud;
import fisk.chipcloud.ChipCloudConfig;
import fisk.chipcloud.ChipDeletedListener;
import io.realm.Sort;

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
    Button clearTags, orderByTitle;

    Spinner spn;
    HashMap<String, JsonObject> map;

    AlertDialog confirmation;

    public LibraryFragment() {
    }


    @Override
    public int getLayout() {
        return R.layout.fragment_my_library;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        map = RealmRating.getRatings(mRealm, "resource", model.getId());
        adapterLibrary = new AdapterLibrary(getActivity(), getList(RealmMyLibrary.class), map, mRealm);
        adapterLibrary.setRatingChangeListener(this);
        adapterLibrary.setListener(this);
        return adapterLibrary;
    }


    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        spn = getView().findViewById(R.id.spn_sort);
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

        tvAddToLib.setOnClickListener(view -> {
            if (selectedItems.size() > 0) {
                confirmation = createAlertDialog();
                confirmation.show();
                addToMyList();
                selectedItems.clear();
                tvAddToLib.setEnabled( false );  // After clearing selectedItems size is always 0
            }
        });
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

        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0) {
                    adapterLibrary.setLibraryList(getList(RealmMyLibrary.class, "createdDate", Sort.ASCENDING));
                } else if (i == 1) {
                    adapterLibrary.setLibraryList(getList(RealmMyLibrary.class, "createdDate", Sort.DESCENDING));
                } else {
                    adapterLibrary.setLibraryList(getList(RealmMyLibrary.class, "title"));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }


    private void initArrays() {
        subjects = new HashSet<>();
        languages = new HashSet<>();
        levels = new HashSet<>();
        mediums = new HashSet<>();
    }


    private AlertDialog createAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), 5);
        String msg = "Success! You have added these resources to your myLibrary:\n\n";
        if (selectedItems.size() <= 5) {
            for (int i = 0; i < selectedItems.size(); i++) {
                msg += " - " + selectedItems.get(i).getTitle() + "\n";
            }
        }
        else {
            for (int i = 0; i < 5; i++) {
                msg += " - " + selectedItems.get(i).getTitle() + "\n";
            }
            msg += "And " + (selectedItems.size() - 5) + " more resource(s)...\n";
        }
        msg +=  "\n\nReturn to the Home tab to access myLibrary.\n" +
                "\nNote: You may still need to download the newly added resources.";
        builder.setMessage(msg);
        builder.setCancelable(true);
        builder.setPositiveButton(
                "Ok",
                (dialog, id) -> dialog.cancel());
        return builder.create();
    }


    private void clearTagsButton() {
        clearTags.setOnClickListener(vi -> {
            saveSearchActivity();
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
        tvSelected.setText(getString(R.string.selected) + tag.getName());
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

    @Override
    public void onPause() {
        super.onPause();

        saveSearchActivity();
    }

    private boolean filterApplied() {
        return !(subjects.isEmpty() && languages.isEmpty() && mediums.isEmpty() && levels.isEmpty() && searchTags.isEmpty() && etSearch.getText().toString().isEmpty() );
    }

    private void saveSearchActivity() {
        if (filterApplied()) {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            RealmSearchActivity activity = mRealm.createObject(RealmSearchActivity.class, UUID.randomUUID().toString());
            activity.setUser(model.getName());
            activity.setTime(Calendar.getInstance().getTimeInMillis());
            activity.setCreatedOn(model.getPlanetCode());
            activity.setParentCode(model.getParentCode());
            activity.setText(etSearch.getText().toString());
            activity.setType("resources");
            JsonObject filter = new JsonObject();
            filter.add("tags", RealmTag.getTagsArray(searchTags));
            filter.add("subjects", getJsonArrayFromList(subjects));
            filter.add("language", getJsonArrayFromList(languages));
            filter.add("level", getJsonArrayFromList(levels));
            filter.add("mediaType", getJsonArrayFromList(mediums));
            activity.setFilter(new Gson().toJson(filter));
            mRealm.commitTransaction();
        }
    }


}

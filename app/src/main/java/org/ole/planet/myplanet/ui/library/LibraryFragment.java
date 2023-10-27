package org.ole.planet.myplanet.ui.library;

import static org.ole.planet.myplanet.model.RealmMyLibrary.getArrayList;
import static org.ole.planet.myplanet.model.RealmMyLibrary.getLevels;
import static org.ole.planet.myplanet.model.RealmMyLibrary.getSubjects;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

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
import org.ole.planet.myplanet.model.RealmSearchActivity;
import org.ole.planet.myplanet.model.RealmTag;
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

public class LibraryFragment extends BaseRecyclerFragment<RealmMyLibrary> implements OnLibraryItemSelected, ChipDeletedListener, TagClickListener, OnFilterListener {
    TextView tvAddToLib, tvSelected;
    EditText etSearch, etTags;
    AdapterLibrary adapterLibrary;
    FlexboxLayout flexBoxTags;
    List<RealmTag> searchTags;
    ChipCloudConfig config;
    Button clearTags, orderByTitle;
    CheckBox selectAll;
    Spinner spn;
    HashMap<String, JsonObject> map;
    AlertDialog confirmation;

    public LibraryFragment() {}

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
        flexBoxTags = getView().findViewById(R.id.flexbox_tags);
        selectAll = getView().findViewById(R.id.selectAll);
        tvDelete = getView().findViewById(R.id.tv_delete);

        initArrays();

        tvAddToLib.setOnClickListener(view -> {
            if (selectedItems.size() > 0) {
                confirmation = createAlertDialog();
                confirmation.show();
                addToMyList();
                selectedItems.clear();
                tvAddToLib.setEnabled(false);  // After clearing selectedItems size is always 0
                checkList();
            }
        });

        tvDelete.setOnClickListener(V -> new AlertDialog.Builder(this.getContext())
                .setMessage(R.string.confirm_removal)
                .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                    deleteSelected(true);
                    checkList();
                })
                .setNegativeButton(R.string.no, null).show());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapterLibrary.setLibraryList(applyFilter(filterLibraryByTag(etSearch.getText().toString().trim(), searchTags)));
                showNoData(tvMessage, adapterLibrary.getItemCount());
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        getView().findViewById(R.id.btn_collections).setOnClickListener(view -> {
            CollectionsFragment f = CollectionsFragment.getInstance(searchTags, "resources");
            f.setListener(LibraryFragment.this);
            f.show(getChildFragmentManager(), "");
        });

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
        checkList();
        selectAll.setOnClickListener(view -> {
            boolean allSelected = selectedItems.size() ==  adapterLibrary.getLibraryList().size();
            adapterLibrary.selectAllItems(!allSelected);
            if (allSelected) {
                selectAll.setChecked(false);
                selectAll.setText(getString(R.string.select_all));
            } else {
                selectAll.setChecked(true);
                selectAll.setText(getString(R.string.unselect_all));
            }
        });
    }

    private void checkList() {
        if (adapterLibrary.getLibraryList().size() == 0) {
            selectAll.setVisibility(View.GONE);
            etSearch.setVisibility(View.GONE);
            tvAddToLib.setVisibility(View.GONE);
            spn.setVisibility(View.GONE);
            tvSelected.setVisibility(View.GONE);
            getView().findViewById(R.id.btn_collections).setVisibility(View.GONE);
            getView().findViewById(R.id.show_filter).setVisibility(View.GONE);
            clearTags.setVisibility(View.GONE);
            tvDelete.setVisibility(View.GONE);
        }
    }

    private void initArrays() {
        subjects = new HashSet<>();
        languages = new HashSet<>();
        levels = new HashSet<>();
        mediums = new HashSet<>();
    }

    private AlertDialog createAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), 5);
        String msg = getString(R.string.success_you_have_added_these_resources_to_your_mylibrary);
        if (selectedItems.size() <= 5) {
            for (int i = 0; i < selectedItems.size(); i++) {
                msg += " - " + selectedItems.get(i).getTitle() + "\n";
            }
        } else {
            for (int i = 0; i < 5; i++) {
                msg += " - " + selectedItems.get(i).getTitle() + "\n";
            }
            msg += getString(R.string.and) + (selectedItems.size() - 5) + getString(R.string.more_resource_s);
        }
        msg += getString(R.string.return_to_the_home_tab_to_access_mylibrary) + getString(R.string.note_you_may_still_need_to_download_the_newly_added_resources);
        builder.setMessage(msg);
        builder.setCancelable(true);
        builder.setPositiveButton(getString(R.string.ok), (dialog, id) -> dialog.cancel());
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
        if (!searchTags.contains(realmTag)) searchTags.add(realmTag);
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
        if (adapterLibrary.areAllSelected()) {
            selectAll.setChecked(true);
            selectAll.setText(getString(R.string.unselect_all));
        } else {
            selectAll.setChecked(false);
            selectAll.setText(getString(R.string.select_all));
        }
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
        return !(subjects.isEmpty() && languages.isEmpty() && mediums.isEmpty() && levels.isEmpty() && searchTags.isEmpty() && etSearch.getText().toString().isEmpty());
    }

    private void saveSearchActivity() {
        if (filterApplied()) {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
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
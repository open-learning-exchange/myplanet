package org.ole.planet.myplanet.ui.course;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnCourseItemSelected;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmSearchActivity;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.library.CollectionsFragment;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.Utilities;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import io.realm.Sort;

public class CourseFragment extends BaseRecyclerFragment<RealmMyCourse> implements OnCourseItemSelected, TagClickListener {
    TextView tvAddToLib, tvMessage, tvSelected;
    EditText etSearch;
    AdapterCourses adapterCourses;
    Button btnRemove, orderByDate, orderByTitle;
    CheckBox selectAll;
    Spinner spnGrade, spnSubject;
    List<RealmTag> searchTags;
    AlertDialog confirmation;
    private boolean allItemsSelected = false;

    public CourseFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_my_course;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        HashMap<String, JsonObject> map = RealmRating.getRatings(mRealm, "course", model.id);
        HashMap<String, JsonObject> progressMap = RealmCourseProgress.getCourseProgress(mRealm, model.id);
        adapterCourses = new AdapterCourses(getActivity(), getList(RealmMyCourse.class), map);
        adapterCourses.setProgressMap(progressMap);
        adapterCourses.setmRealm(mRealm);
        adapterCourses.setListener(this);
        adapterCourses.setRatingChangeListener(this);
        return adapterCourses;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        searchTags = new ArrayList<>();
        initializeView();
        if (isMyCourseLib) {
            tvDelete.setText(R.string.archive);
            btnRemove.setVisibility(View.VISIBLE);
        }

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
                showNoData(tvMessage, adapterCourses.getItemCount());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnRemove.setOnClickListener(V -> new AlertDialog.Builder(this.getContext())
                .setMessage(R.string.are_you_sure_you_want_to_delete_these_courses)
                .setPositiveButton(R.string.yes, (dialogInterface, i) -> {
                    deleteSelected(true);
                    CourseFragment newFragment = new CourseFragment();
                    recreateFragment(newFragment);
                })
                .setNegativeButton(R.string.no, null).show());
        getView().findViewById(R.id.btn_collections).setOnClickListener(view -> {
            CollectionsFragment f = CollectionsFragment.getInstance(searchTags, "courses");
            f.setListener(this);
            f.show(getChildFragmentManager(), "");
        });
        clearTags();
        showNoData(tvMessage, adapterCourses.getItemCount());
        KeyboardUtils.setupUI(getView().findViewById(R.id.my_course_parent_layout), getActivity());
        changeButtonStatus();
        if (!isMyCourseLib) tvFragmentInfo.setText(R.string.our_courses);
        additionalSetup();
    }

    public void additionalSetup() {
        View bottomSheet = getView().findViewById(R.id.card_filter);
        getView().findViewById(R.id.filter).setOnClickListener(view -> bottomSheet.setVisibility(bottomSheet.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        orderByDate = getView().findViewById(R.id.order_by_date_button);
        orderByTitle = getView().findViewById(R.id.order_by_title_button);
        orderByDate.setOnClickListener(view -> adapterCourses.toggleSortOrder());
        orderByTitle.setOnClickListener(view -> adapterCourses.toggleTitleSortOrder());
    }

    private void initializeView() {
        tvAddToLib = getView().findViewById(R.id.tv_add);
        tvAddToLib.setOnClickListener(view -> {
            if (selectedItems.size() > 0) {
                confirmation = createAlertDialog();
                confirmation.show();
                addToMyList();
                selectedItems.clear();
                tvAddToLib.setEnabled(false);  // selectedItems will always have a size of 0
                checkList();
            }
        });
        etSearch = getView().findViewById(R.id.et_search);
        tvSelected = getView().findViewById(R.id.tv_selected);
        btnRemove = getView().findViewById(R.id.btn_remove);
        spnGrade = getView().findViewById(R.id.spn_grade);
        spnSubject = getView().findViewById(R.id.spn_subject);
        tvMessage = getView().findViewById(R.id.tv_message);
        getView().findViewById(R.id.tl_tags).setVisibility(View.GONE);
        tvFragmentInfo = getView().findViewById(R.id.tv_fragment_info);
        spnGrade.setOnItemSelectedListener(itemSelectedListener);
        spnSubject.setOnItemSelectedListener(itemSelectedListener);
        selectAll = getView().findViewById(R.id.selectAll);
        checkList();

        selectAll.setOnClickListener(view -> {
            boolean allSelected = selectedItems.size() == adapterCourses.getCourseList().size();
            adapterCourses.selectAllItems(!allSelected);
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
        if (adapterCourses.getCourseList().size() == 0) {
            selectAll.setVisibility(View.GONE);
            etSearch.setVisibility(View.GONE);
            tvAddToLib.setVisibility(View.GONE);
            getView().findViewById(R.id.filter).setVisibility(View.GONE);
            btnRemove.setVisibility(View.GONE);
            tvSelected.setVisibility(View.GONE);
        }
    }

    private AdapterView.OnItemSelectedListener itemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            Utilities.log("On item selected");
            gradeLevel = spnGrade.getSelectedItem().toString().equals("All") ? "" : spnGrade.getSelectedItem().toString();
            subjectLevel = spnSubject.getSelectedItem().toString().equals("All") ? "" : spnSubject.getSelectedItem().toString();
            adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
            showNoFilter(tvMessage, adapterCourses.getItemCount());
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {
        }
    };

    private void clearTags() {
        getView().findViewById(R.id.btn_clear_tags).setOnClickListener(vi -> {
            searchTags.clear();
            etSearch.setText("");
            tvSelected.setText("");
            adapterCourses.setCourseList(filterCourseByTag("", searchTags));
            showNoData(tvMessage, adapterCourses.getItemCount());
            spnGrade.setSelection(0);
            spnSubject.setSelection(0);
        });
    }

    private AlertDialog createAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), 5);
        String msg = getString(R.string.success_you_have_added_the_following_courses);
        if (selectedItems.size() <= 5) {
            for (int i = 0; i < selectedItems.size(); i++) {
                msg += " - " + selectedItems.get(i).courseTitle + "\n";
            }
        } else {
            for (int i = 0; i < 5; i++) {
                msg += " - " + selectedItems.get(i).courseTitle + "\n";
            }
            msg += getString(R.string.and) + (selectedItems.size() - 5) + getString(R.string.more_course_s);
        }
        msg += getString(R.string.return_to_the_home_tab_to_access_mycourses);
        builder.setMessage(msg);
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.ok, (dialog, id) -> {
            dialog.cancel();
            CourseFragment newFragment = new CourseFragment();
            recreateFragment(newFragment);
        });
        return builder.create();
    }

    @Override
    public void onSelectedListChange(List<RealmMyCourse> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    @Override
    public void onTagClicked(RealmTag tag) {
        if (!searchTags.contains(tag)) searchTags.add(tag);
        adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
        showTagText(searchTags, tvSelected);
        showNoData(tvMessage, adapterCourses.getItemCount());
    }

    public void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
        if (adapterCourses.areAllSelected()) {
            selectAll.setChecked(true);
            selectAll.setText(getString(R.string.unselect_all));
        } else {
            selectAll.setChecked(false);
            selectAll.setText(getString(R.string.select_all));
        }
    }

    @Override
    public void onTagSelected(RealmTag tag) {
        List<RealmTag> li = new ArrayList<>();
        li.add(tag);
        searchTags = li;
        tvSelected.setText(R.string.selected + tag.getName());
        adapterCourses.setCourseList((filterCourseByTag(etSearch.getText().toString(), li)));
        showNoData(tvMessage, adapterCourses.getItemCount());
    }

    @Override
    public void onOkClicked(List<RealmTag> list) {
        if (list.isEmpty()) {
            searchTags.clear();
            adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
            showNoData(tvMessage, adapterCourses.getItemCount());
        } else {
            for (RealmTag tag : list) {
                onTagClicked(tag);
            }
        }
    }

    private boolean filterApplied() {
        return !(searchTags.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty() && etSearch.getText().toString().isEmpty());
    }

    private void saveSearchActivity() {
        if (filterApplied()) {
            if (!mRealm.isInTransaction()) mRealm.beginTransaction();
            RealmSearchActivity activity = mRealm.createObject(RealmSearchActivity.class, UUID.randomUUID().toString());
            activity.setUser(model.name);
            activity.setTime(Calendar.getInstance().getTimeInMillis());
            activity.setCreatedOn(model.planetCode);
            activity.setParentCode(model.parentCode);
            activity.setText(etSearch.getText().toString());
            activity.setType("courses");
            JsonObject filter = new JsonObject();
            filter.add("tags", RealmTag.getTagsArray(searchTags));
            filter.addProperty("doc.gradeLevel", gradeLevel);
            filter.addProperty("doc.subjectLevel", subjectLevel);
            activity.setFilter(new Gson().toJson(filter));
            mRealm.commitTransaction();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveSearchActivity();
    }

    public void recreateFragment(Fragment fragment) {
        if(isMyCourseLib){
            Bundle args = new Bundle();
            args.putBoolean("isMyCourseLib", true);
            fragment.setArguments(args);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        } else{
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, fragment);
            transaction.addToBackStack(null);
            transaction.commit();
        }
    }
}
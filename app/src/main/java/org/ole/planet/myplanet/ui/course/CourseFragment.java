package org.ole.planet.myplanet.ui.course;


import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnCourseItemSelected;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
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

/**
 * A simple {@link Fragment} subclass.
 */

public class CourseFragment extends BaseRecyclerFragment<RealmMyCourse> implements OnCourseItemSelected, TagClickListener {

    TextView tvAddToLib, tvMessage, tvSelected;

    EditText etSearch;
    ImageView imgSearch;
    AdapterCourses adapterCourses;
    Button btnRemove, orderByDate, orderByTitle;
    Spinner spnGrade, spnSubject;
    List<RealmTag> searchTags;
    Spinner spn;

    AlertDialog confirmation;

    public CourseFragment() {
    }

    @Override
    public int getLayout() {
        return R.layout.fragment_my_course;
    }

    @Override
    public RecyclerView.Adapter getAdapter() {
        HashMap<String, JsonObject> map = RealmRating.getRatings(mRealm, "course", model.getId());
        HashMap<String, JsonObject> progressMap = RealmCourseProgress.getCourseProgress(mRealm, model.getId());
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

        imgSearch.setOnClickListener(view -> {
            adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
            showNoData(tvMessage, adapterCourses.getItemCount());
            KeyboardUtils.hideSoftKeyboard(getActivity());
        });
        //btnRemove.setOnClickListener(V -> deleteSelected(true));
        btnRemove.setOnClickListener(V -> new AlertDialog.Builder(this.getContext()).setMessage("Are you sure you want to delete these courses?")
                .setPositiveButton("Yes", (dialogInterface, i) -> {
                    deleteSelected(true);
                })
                .setNegativeButton("No", null).show()
        );
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

        spn.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (i == 0) {
                    adapterCourses.setCourseList(getList(RealmMyCourse.class, "createdDate", Sort.ASCENDING));
                } else if (i == 1) {
                    adapterCourses.setCourseList(getList(RealmMyCourse.class, "createdDate", Sort.DESCENDING));
                } else {
                    adapterCourses.setCourseList(getList(RealmMyCourse.class, "courseTitle"));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    public void additionalSetup() {
        View bottomSheet = getView().findViewById(R.id.card_filter);
        getView().findViewById(R.id.filter).setOnClickListener(view -> bottomSheet.setVisibility(bottomSheet.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        orderByDate = getView().findViewById(R.id.order_by_date_button);
        orderByTitle = getView().findViewById(R.id.order_by_title_button);
        orderByDate.setOnClickListener(view -> adapterCourses.setCourseList(getList(RealmMyCourse.class, "createdDate")));
        orderByTitle.setOnClickListener(view -> adapterCourses.setCourseList(getList(RealmMyCourse.class, "courseTitle")));
    }

    private void initializeView() {
        spn = getView().findViewById(R.id.spn_sort);
        tvAddToLib = getView().findViewById(R.id.tv_add);
        tvAddToLib.setOnClickListener(view -> {
            if (selectedItems.size() > 0) {
                confirmation = createAlertDialog();
                confirmation.show();
                addToMyList();
                selectedItems.clear();
                tvAddToLib.setEnabled( false );  // selectedItems will always have a size of 0
            }
        });
        etSearch = getView().findViewById(R.id.et_search);
        tvSelected = getView().findViewById(R.id.tv_selected);
        btnRemove = getView().findViewById(R.id.btn_remove);
        spnGrade = getView().findViewById(R.id.spn_grade);
        spnSubject = getView().findViewById(R.id.spn_subject);
        imgSearch = getView().findViewById(R.id.img_search);
        tvMessage = getView().findViewById(R.id.tv_message);
        getView().findViewById(R.id.tl_tags).setVisibility(View.GONE);
        tvFragmentInfo = getView().findViewById(R.id.tv_fragment_info);
        spnGrade.setOnItemSelectedListener(itemSelectedListener);
        spnSubject.setOnItemSelectedListener(itemSelectedListener);
    }

    private AdapterView.OnItemSelectedListener   itemSelectedListener = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
            Utilities.log("On item selected");
            gradeLevel = spnGrade.getSelectedItem().toString().equals("All") ? "" : spnGrade.getSelectedItem().toString();
            subjectLevel = spnSubject.getSelectedItem().toString().equals("All") ? "" : spnSubject.getSelectedItem().toString();
            adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
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
        });
    }

    private AlertDialog createAlertDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext(), 5);
        String msg = "Success! You have added the following courses:\n\n";
        if (selectedItems.size() <= 5) {
            for (int i = 0; i < selectedItems.size(); i++) {
                msg += " - " + selectedItems.get(i).getCourseTitle() + "\n";
            }
        }
        else {
            for (int i = 0; i < 5; i++) {
                msg += " - " + selectedItems.get(i).getCourseTitle() + "\n";
            }
            msg += "And " + (selectedItems.size() - 5) + " more course(s)...\n";
        }
        msg += "\n\n Return to the Home tab to access myCourses.\n";
        builder.setMessage(msg);
        builder.setCancelable(true);
        builder.setPositiveButton(
                "Ok",
                (dialog, id) -> dialog.cancel());
        return builder.create();
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
//                        adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString().trim(), RealmMyCourse.class));
//                        etSearch.setText(etSearch.getText().toString().trim());
//                        showNoData(tvMessage, adapterCourses.getItemCount());
//                        KeyboardUtils.hideSoftKeyboard(getActivity());
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
    public void onSelectedListChange(List<RealmMyCourse> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    @Override
    public void onTagClicked(RealmTag tag) {
//        searchTags.clear();
        if (!searchTags.contains(tag))
            searchTags.add(tag);
        adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
        showTagText(searchTags, tvSelected);
        showNoData(tvMessage, adapterCourses.getItemCount());
    }

    private void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }

    @Override
    public void onTagSelected(RealmTag tag) {
        List<RealmTag> li = new ArrayList<>();
        li.add(tag);
        searchTags = li;
        tvSelected.setText("Selected : " + tag.getName());
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
        return !(searchTags.isEmpty() && gradeLevel.isEmpty() && subjectLevel.isEmpty() && etSearch.getText().toString().isEmpty() );
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
            activity.setType("courses");
            JsonObject filter = new JsonObject();
            filter.add("tags", RealmTag.getTagsArray(searchTags));
            filter.addProperty("doc.gradeLevel", gradeLevel);
            filter.addProperty("doc.subjectLevel",subjectLevel );
            activity.setFilter(new Gson().toJson(filter));
            mRealm.commitTransaction();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        saveSearchActivity();
    }
}
package org.ole.planet.myplanet.ui.course;


import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnCourseItemSelected;
import org.ole.planet.myplanet.callback.TagClickListener;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.ui.library.CollectionsFragment;
import org.ole.planet.myplanet.utilities.KeyboardUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */

public class CourseFragment extends BaseRecyclerFragment<RealmMyCourse> implements OnCourseItemSelected, TagClickListener {

    TextView tvAddToLib, tvMessage, tvSelected;

    EditText etSearch;
    ImageView imgSearch;
    AdapterCourses adapterCourses;
    Button btnRemove, orderByDate, orderByTitle;
    List<RealmTag> searchTags;

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
            tvDelete.setText(R.string.archive_mycourse);
            btnRemove.setVisibility(View.VISIBLE);
        }

        View bottomSheet = getView().findViewById(R.id.card_filter);

        getView().findViewById(R.id.filter).setOnClickListener(view -> {
            bottomSheet.setVisibility(bottomSheet.getVisibility() == View.VISIBLE ?View.GONE : View.VISIBLE);
        });
        imgSearch.setOnClickListener(view -> {
            adapterCourses.setCourseList(filterCourseByTag(etSearch.getText().toString(), searchTags));
            showNoData(tvMessage, adapterCourses.getItemCount());
            KeyboardUtils.hideSoftKeyboard(getActivity());
        });
        // setSearchListener();
        btnRemove.setOnClickListener(V -> {
            deleteSelected(true);
        });
        getView().findViewById(R.id.btn_collections).setOnClickListener(view -> {
            CollectionsFragment f = CollectionsFragment.getInstance(searchTags, "courses");
            f.setListener(this);
            f.show(getChildFragmentManager(), "");
        });
        clearTags();
        showNoData(tvMessage, adapterCourses.getItemCount());
        KeyboardUtils.setupUI(getView().findViewById(R.id.my_course_parent_layout), getActivity());
        changeButtonStatus();
        if (!isMyCourseLib) tvFragmentInfo.setText("Our Courses");
        additionalSetup();
    }

    public void additionalSetup() {
        orderByDate = getView().findViewById(R.id.order_by_date_button);
        orderByTitle = getView().findViewById(R.id.order_by_title_button);
        orderByDate.setOnClickListener(view -> adapterCourses.setCourseList(getList(RealmMyCourse.class, "createdDate")));
        orderByTitle.setOnClickListener(view -> adapterCourses.setCourseList(getList(RealmMyCourse.class, "courseTitle")));
    }

    private void initializeView() {
        tvAddToLib = getView().findViewById(R.id.tv_add);
        tvAddToLib.setOnClickListener(view -> addToMyList());
        etSearch = getView().findViewById(R.id.et_search);
        tvSelected = getView().findViewById(R.id.tv_selected);
        btnRemove = getView().findViewById(R.id.btn_remove);
        imgSearch = getView().findViewById(R.id.img_search);
        tvMessage = getView().findViewById(R.id.tv_message);
        getView().findViewById(R.id.tl_tags).setVisibility(View.GONE);
        tvFragmentInfo = getView().findViewById(R.id.tv_fragment_info);
    }

    private void clearTags() {
        getView().findViewById(R.id.btn_clear_tags).setOnClickListener(vi -> {
            searchTags.clear();
            etSearch.setText("");
            tvSelected.setText("");
            adapterCourses.setCourseList(filterCourseByTag("", searchTags));
            showNoData(tvMessage, adapterCourses.getItemCount());
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
}
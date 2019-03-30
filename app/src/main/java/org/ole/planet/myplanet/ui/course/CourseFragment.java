package org.ole.planet.myplanet.ui.course;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonObject;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.base.BaseRecyclerFragment;
import org.ole.planet.myplanet.callback.OnCourseItemSelected;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmRating;
import org.ole.planet.myplanet.utilities.KeyboardUtils;

import java.util.HashMap;
import java.util.List;

import io.realm.RealmObject;

/**
 * A simple {@link Fragment} subclass.
 */

public class CourseFragment extends BaseRecyclerFragment<RealmMyCourse> implements OnCourseItemSelected {

    TextView tvAddToLib, tvMessage ;

    EditText etSearch;
    ImageView imgSearch;
    AdapterCourses adapterCourses;
    Button btnRemove;

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
        adapterCourses.setListener(this);
        adapterCourses.setRatingChangeListener(this);
        return adapterCourses;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        tvAddToLib = getView().findViewById(R.id.tv_add);
        tvAddToLib.setOnClickListener(view -> addToMyList());
        etSearch = getView().findViewById(R.id.et_search);
        btnRemove = getView().findViewById(R.id.btn_remove);
        getView().findViewById(R.id.tl_tags).setVisibility(View.GONE);
        if (isMyCourseLib){
            tvDelete.setText(R.string.archive_mycourse);
            btnRemove.setVisibility(View.VISIBLE);
        }
        imgSearch = getView().findViewById(R.id.img_search);
        tvMessage = getView().findViewById(R.id.tv_message);
        imgSearch.setOnClickListener(view -> {
            adapterCourses.setCourseList(search(etSearch.getText().toString(), RealmMyCourse.class));
            showNoData(tvMessage, adapterCourses.getItemCount());
            KeyboardUtils.hideSoftKeyboard(getActivity());
        });
        setSearchListener();
        btnRemove.setOnClickListener(V ->{
            deleteSelected(true);
        });
        showNoData(tvMessage, adapterCourses.getItemCount());
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
                        adapterCourses.setCourseList(search(etSearch.getText().toString().trim(), RealmMyCourse.class));
                        etSearch.setText(etSearch.getText().toString().trim());
                        showNoData(tvMessage, adapterCourses.getItemCount());
                        KeyboardUtils.hideSoftKeyboard(getActivity());
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    @Override
    public void onSelectedListChange(List<RealmMyCourse> list) {
        this.selectedItems = list;
        changeButtonStatus();
    }

    private void changeButtonStatus() {
        tvAddToLib.setEnabled(selectedItems.size() > 0);
    }
}
package org.ole.planet.myplanet.base;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.realm.Case;
import io.realm.RealmList;
import io.realm.RealmObject;

import static android.content.Context.MODE_PRIVATE;

public abstract class BaseRecyclerFragment<LI> extends BaseRecyclerParentFragment implements OnRatingChangeListener {
    public static final String PREFS_NAME = "OLE_PLANET";
    public static SharedPreferences settings;
    public Set<String> subjects, languages, mediums, levels;
    public List<LI> selectedItems;
    public String gradeLevel = "", subjectLevel = "";
    public DatabaseService realmService;
    public UserProfileDbHandler profileDbHandler;

    public RecyclerView recyclerView;
    public TextView tvMessage, tvFragmentInfo;
    public TextView tvDelete;
    List<LI> list;

    public BaseRecyclerFragment() {
    }

    public static void showNoData(View v, int count) {
        if (v == null)
            return;
        v.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        ((TextView) v).setText("No data available, please check and try again.");
    }

    public abstract int getLayout();

    public abstract RecyclerView.Adapter getAdapter();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isMyCourseLib = getArguments().getBoolean("isMyCourseLib");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(getLayout(), container, false);
        settings = getActivity().getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        recyclerView = v.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        if (isMyCourseLib) {
            tvDelete = v.findViewById(R.id.tv_delete);
            initDeleteButton();
            if (v.findViewById(R.id.tv_add) != null)
                v.findViewById(R.id.tv_add).setVisibility(View.GONE);

        }
        tvMessage = v.findViewById(R.id.tv_message);
        selectedItems = new ArrayList<>();
        list = new ArrayList<>();
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        profileDbHandler = new UserProfileDbHandler(getActivity());
        model = profileDbHandler.getUserModel();
        recyclerView.setAdapter(getAdapter());
        if (isMyCourseLib) showDownloadDialog(getLibraryList(mRealm));
        return v;
    }

    protected void initDeleteButton() {
        if (tvDelete != null) {
            tvDelete.setVisibility(View.VISIBLE);
            tvDelete.setOnClickListener(view -> deleteSelected(false));
        }
    }

    @Override
    public void onRatingChanged() {
        recyclerView.setAdapter(getAdapter());
    }

    public void addToMyList() {
        for (int i = 0; i < selectedItems.size(); i++) {
            RealmObject object = (RealmObject) selectedItems.get(i);
            if (object instanceof RealmMyLibrary) {
                RealmMyLibrary myObject = mRealm.where(RealmMyLibrary.class).equalTo("resourceId", ((RealmMyLibrary) object).getResource_id()).findFirst();
                RealmMyLibrary.createFromResource(myObject, mRealm, model.getId());
                RealmRemovedLog.onAdd(mRealm, "resources", profileDbHandler.getUserModel().getId(), myObject.getResourceId());
                Utilities.toast(getActivity(), "Added to my library");
                recyclerView.setAdapter(getAdapter());
            } else {
                RealmMyCourse myObject = RealmMyCourse.getMyCourse(mRealm, ((RealmMyCourse) object).getCourseId());
                RealmMyCourse.createMyCourse(myObject, mRealm, model.getId());
                RealmRemovedLog.onAdd(mRealm, "courses", profileDbHandler.getUserModel().getId(), myObject.getCourseId());
                Utilities.toast(getActivity(), "Added to my courses");
                recyclerView.setAdapter(getAdapter());
            }
            showNoData(tvMessage, getAdapter().getItemCount());
        }
    }

    public void deleteSelected(boolean deleteProgress) {
        for (int i = 0; i < selectedItems.size(); i++) {
            if (!mRealm.isInTransaction())
                mRealm.beginTransaction();
            RealmObject object = (RealmObject) selectedItems.get(i);
            deleteCourseProgress(deleteProgress, object);
            removeFromShelf(object);
            recyclerView.setAdapter(getAdapter());
            showNoData(tvMessage, getAdapter().getItemCount());
        }
    }

    private void deleteCourseProgress(boolean deleteProgress, RealmObject object) {
        if (deleteProgress && object instanceof RealmMyCourse) {
            mRealm.where(RealmCourseProgress.class).equalTo("courseId", ((RealmMyCourse) object).getCourseId()).findAll().deleteAllFromRealm();
            List<RealmStepExam> examList = mRealm.where(RealmStepExam.class).equalTo("courseId", ((RealmMyCourse) object).getCourseId()).findAll();
            for (RealmStepExam exam : examList) {
                mRealm.where(RealmSubmission.class)
                        .equalTo("parentId", exam.getId())
                        .notEqualTo("type", "survey")
                        .equalTo("uploaded", false).findAll().deleteAllFromRealm();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    private void checkAndAddToList(RealmMyCourse course, List<RealmMyCourse> courses, List<RealmTag> tags) {
        for (RealmTag tg : tags) {
            long count = mRealm.where(RealmTag.class).equalTo("db", "courses").equalTo("tagId", tg.getId()).equalTo("linkId", course.getCourseId()).count();
            if (count > 0 && !courses.contains(course))
                courses.add(course);
        }
    }

    private List<LI> getData(String s, Class c) {
        List<LI> li = new ArrayList<>();
        if (!s.contains(" ")) {
            li = mRealm.where(c).contains(c == RealmMyLibrary.class ? "title" : "courseTitle", s, Case.INSENSITIVE).findAll();
        } else {
            String[] query = s.split(" ");
            List<LI> data = mRealm.where(c).findAll();
            for (LI l : data) {
                searchAndAddToList(l, c, query, li);
            }
        }
        return li;
    }

    private void searchAndAddToList(LI l, Class c, String[] query, List<LI> li) {
        String title = c == RealmMyLibrary.class ? ((RealmMyLibrary) l).getTitle() : ((RealmMyCourse) l).getCourseTitle();
        boolean isExists = false;
        for (String q : query) {
//                li.add(l);
            isExists = title.toLowerCase().contains(q.toLowerCase());
            Utilities.log(title.toLowerCase() + " " + q.toLowerCase() + " is exists " + isExists);

            if (!isExists) break;
        }
        if (isExists)
            li.add(l);
    }

    public List<RealmMyLibrary> filterLibraryByTag(String s, List<RealmTag> tags) {
        if (tags.size() == 0 && s.isEmpty()) {
            return (List<RealmMyLibrary>) getList(RealmMyLibrary.class);
        }
        List<RealmMyLibrary> list = (List<RealmMyLibrary>) getData(s, RealmMyLibrary.class);
        if (isMyCourseLib) list = RealmMyLibrary.getMyLibraryByUserId(model.getId(), list);
        else list = RealmMyLibrary.getOurLibrary(model.getId(), list);
        if (tags.size() == 0) return list;
        RealmList<RealmMyLibrary> libraries = new RealmList<>();
        for (RealmMyLibrary library : list) {
            filter(tags, library, libraries);
        }
        return libraries;
    }


    public List<RealmMyCourse> filterCourseByTag(String s, List<RealmTag> tags) {
        if (tags.size() == 0 && s.isEmpty()) {
            return applyCourseFilter((List<RealmMyCourse>) getList(RealmMyCourse.class));
        }
        List<RealmMyCourse> list = (List<RealmMyCourse>) getData(s, RealmMyCourse.class);
        if (isMyCourseLib) list = RealmMyCourse.getMyCourseByUserId(model.getId(), list);
        else list = RealmMyCourse.getOurCourse(model.getId(), list);
        if (tags.size() == 0) return list;
        RealmList<RealmMyCourse> courses = new RealmList<>();
        for (RealmMyCourse course : list) {
            checkAndAddToList(course, courses, tags);
        }
        return applyCourseFilter(list);
    }

    private void filter(List<RealmTag> tags, RealmMyLibrary library, RealmList<RealmMyLibrary> libraries) {
        for (RealmTag tg : tags) {
            long count = mRealm.where(RealmTag.class).equalTo("db", "resources").equalTo("tagId", tg.getId()).equalTo("linkId", library.getId()).count();
            if (count > 0 && !libraries.contains(library))
                libraries.add(library);
        }
    }

    public List<RealmMyLibrary> applyFilter(List<RealmMyLibrary> libraries) {
        List<RealmMyLibrary> newList = new ArrayList<>();
        for (RealmMyLibrary l : libraries) {
            if (isValidFilter(l)) newList.add(l);
        }
        return newList;
    }

    public List<RealmMyCourse> applyCourseFilter(List<RealmMyCourse> courses) {
        Utilities.log("apply course filter");
        if (TextUtils.isEmpty(subjectLevel) && TextUtils.isEmpty(gradeLevel))
            return courses;
        List<RealmMyCourse> newList = new ArrayList<>();
        for (RealmMyCourse l : courses) {
            Utilities.log("grade " + gradeLevel);
            Utilities.log("subject " + subjectLevel);
            if (TextUtils.equals(l.getGradeLevel(), gradeLevel) || TextUtils.equals(l.getSubjectLevel(), subjectLevel)) {
                newList.add(l);
            }
        }
        return newList;
    }

    private boolean isValidFilter(RealmMyLibrary l) {
        boolean sub = subjects.isEmpty() || l.getSubject().containsAll(subjects);
        boolean lev = levels.isEmpty() || l.getLevel().containsAll(levels);
        boolean lan = languages.isEmpty() || languages.contains(l.getLanguage());
        boolean med = mediums.isEmpty() || mediums.contains(l.getMediaType());
        return (sub && lev && lan && med);
    }
}
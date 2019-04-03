package org.ole.planet.myplanet.base;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnRatingChangeListener;
import org.ole.planet.myplanet.datamanager.DatabaseService;
import org.ole.planet.myplanet.model.RealmAnswer;
import org.ole.planet.myplanet.model.RealmCourseProgress;
import org.ole.planet.myplanet.model.RealmMyCourse;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmRemovedLog;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmSubmission;
import org.ole.planet.myplanet.model.RealmTag;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.library.AdapterLibrary;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;

import static android.content.Context.MODE_PRIVATE;

public abstract class BaseRecyclerFragment<LI> extends BaseResourceFragment implements OnRatingChangeListener {
    public Set<String> subjects, languages, mediums, levels;
    public static final String PREFS_NAME = "OLE_PLANET";
    public static SharedPreferences settings;
    public List<LI> selectedItems;
    public Realm mRealm;
    public DatabaseService realmService;
    public UserProfileDbHandler profileDbHandler;
    public RealmUserModel model;
    public RecyclerView recyclerView;
    TextView tvMessage;
    List<LI> list;
    public boolean isMyCourseLib;
    public TextView tvDelete;

    public BaseRecyclerFragment() {
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
            tvDelete.setVisibility(View.VISIBLE);
            tvDelete.setOnClickListener(view -> deleteSelected(false));
            if (v.findViewById(R.id.tv_add) != null)
                v.findViewById(R.id.tv_add).setVisibility(View.GONE);
        }
        tvMessage = v.findViewById(R.id.tv_message);
        selectedItems = new ArrayList<>();
        list = new ArrayList<>();
        realmService = new DatabaseService(getActivity());
        mRealm = realmService.getRealmInstance();
        profileDbHandler = new UserProfileDbHandler(getActivity());
        model = mRealm.copyToRealmOrUpdate(profileDbHandler.getUserModel());
        recyclerView.setAdapter(getAdapter());
        if (isMyCourseLib)
            showDownloadDialog(getLibraryList(mRealm));
        return v;
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

    private void removeFromShelf(RealmObject object) {
        if (object instanceof RealmMyLibrary) {
            RealmMyLibrary myObject = mRealm.where(RealmMyLibrary.class).equalTo("resourceId", ((RealmMyLibrary) object).getResource_id()).findFirst();
            myObject.removeUserId(model.getId());
            RealmRemovedLog.onRemove(mRealm, "resources", model.getId(), ((RealmMyLibrary) object).getResource_id());
            Utilities.toast(getActivity(), "Removed from myLibrary");
        } else {
            RealmMyCourse myObject = RealmMyCourse.getMyCourse(mRealm, ((RealmMyCourse) object).getCourseId());
            myObject.removeUserId(model.getId());
            RealmRemovedLog.onRemove(mRealm, "courses", model.getId(), ((RealmMyCourse) object).getCourseId());
            Utilities.toast(getActivity(), "Removed from myCourse");
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    public List<LI> search(String s, Class c) {
        if (s.isEmpty()) {
            return getList(c);
        }
        List<LI> li = mRealm.where(c).contains(c == RealmMyLibrary.class ? "title" : "courseTitle", s, Case.INSENSITIVE).findAll();
        if (c == RealmMyLibrary.class) {
            return (List<LI>) RealmMyLibrary.getMyLibraryByUserId(model.getId(), (List<RealmMyLibrary>) li);
        } else if (c == RealmMyCourse.class && isMyCourseLib) {
            return (List<LI>) RealmMyCourse.getMyCourseByUserId(model.getId(), (List<RealmMyCourse>) li);
        } else {
            return (List<LI>) RealmMyCourse.getOurCourse(model.getId(), (List<RealmMyCourse>) li);
        }
    }

    public List<RealmMyLibrary> filterByTag(List<RealmTag> tags, String s) {
        return applyFilter(fbt(tags, s));
    }

    public List<RealmMyLibrary> fbt(List<RealmTag> tags, String s) {
        if (tags.size() == 0 && s.isEmpty()) {
            return (List<RealmMyLibrary>) getList(RealmMyLibrary.class);
        }
        List<RealmMyLibrary> list = mRealm.where(RealmMyLibrary.class).contains("title", s, Case.INSENSITIVE).findAll();
        if (isMyCourseLib)
            list = RealmMyLibrary.getMyLibraryByUserId(model.getId(), list);
        else
            list = RealmMyLibrary.getOurLibrary(model.getId(), list);
        if (tags.size() == 0) {
            return list;
        }

        RealmList<RealmMyLibrary> libraries = new RealmList<>();
        for (RealmMyLibrary library : list) {
            filter(tags, library, libraries);
        }
        return libraries;
    }

    private void filter(List<RealmTag> tags, RealmMyLibrary library, RealmList<RealmMyLibrary> libraries) {
        boolean contains = true;
        for (RealmTag s : tags) {
            if (!library.getTag().toString().toLowerCase().contains(s.get_id())) {
                contains = false;
                break;
            }
        }
        if (contains)
            libraries.add(library);
    }


    public List<RealmMyLibrary> applyFilter(List<RealmMyLibrary> libraries) {
        List<RealmMyLibrary> newList = new ArrayList<>();
        for (RealmMyLibrary l : libraries) {
            if(isValidFilter(l))
                newList.add(l);
        }
        return newList;
    }

    private boolean isValidFilter(RealmMyLibrary l) {
        boolean sub = subjects.isEmpty() || l.getSubject().containsAll(subjects);
        boolean lev = levels.isEmpty() || l.getLevel().containsAll(levels);
        boolean lan = languages.isEmpty() || languages.contains(l.getLanguage());
        boolean med = mediums.isEmpty() || mediums.contains(l.getMediaType());
        return  (sub && lev && lan && med);
    }

    public List<LI> getList(Class c) {
        if (c == RealmStepExam.class) {
            return mRealm.where(c).equalTo("type", "surveys").findAll();
        } else if (isMyCourseLib) {
            return getMyLibItems(c);
        } else {
            return c == RealmMyLibrary.class ? RealmMyLibrary.getOurLibrary(model.getId(), mRealm.where(c).findAll()) : RealmMyCourse.getOurCourse(model.getId(), mRealm.where(c).findAll());
        }
    }

    private List<LI> getMyLibItems(Class c) {
        if (c == RealmMyLibrary.class)
            return RealmMyLibrary.getMyLibraryByUserId(model.getId(), mRealm.where(c).findAll());
        else
            return RealmMyCourse.getMyCourseByUserId(model.getId(), mRealm.where(c).findAll());
    }

    public void showNoData(View v, int count) {
        if (v == null)
            return;
        v.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        ((TextView) v).setText("No data available, please check and try again.");
    }

}
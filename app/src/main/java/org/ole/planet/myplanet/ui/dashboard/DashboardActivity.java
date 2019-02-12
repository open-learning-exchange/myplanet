package org.ole.planet.myplanet.ui.dashboard;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.course.CourseFragment;
import org.ole.planet.myplanet.ui.feedback.FeedbackFragment;
import org.ole.planet.myplanet.ui.library.LibraryDetailFragment;
import org.ole.planet.myplanet.ui.library.LibraryFragment;
import org.ole.planet.myplanet.ui.survey.SendSurveyFragment;
import org.ole.planet.myplanet.ui.survey.SurveyFragment;
import org.ole.planet.myplanet.ui.sync.DashboardElementActivity;
import org.ole.planet.myplanet.utilities.BottomNavigationViewHelper;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;


public class DashboardActivity extends DashboardElementActivity implements OnHomeItemClickListener, BottomNavigationView.OnNavigationItemSelectedListener, FragmentManager.OnBackStackChangedListener {
    public static final String MESSAGE_PROGRESS = "message_progress";

    AccountHeader headerResult;
    private Drawer result = null;
    private Toolbar mTopToolbar;
    private BottomNavigationView navigationView;
    RealmUserModel user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        mTopToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(mTopToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.app_project_name);
        user = new UserProfileDbHandler(this).getUserModel();

        mTopToolbar.setTitleTextColor(Color.WHITE);
        mTopToolbar.setSubtitleTextColor(Color.WHITE);

        navigationView = findViewById(R.id.top_bar_navigation);
        BottomNavigationViewHelper.disableShiftMode(navigationView);


        if (user.getRolesList().isEmpty()) {
            navigationView.setVisibility(View.GONE);
            openCallFragment(new InactiveDashboardFragment(), "Dashboard");
            return;
        }
        navigationView.setOnNavigationItemSelectedListener(this);
        navigationView.setVisibility(new UserProfileDbHandler(this).getUserModel().getShowTopbar() ? View.VISIBLE : View.GONE);
        headerResult = getAccountHeader();
        createDrawer();
        result.getStickyFooter().setPadding(0, 0, 0, 0); // moves logout button to the very bottom of the drawer. Without it, the "logout" button suspends a little.
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }

        openCallFragment(new DashboardFragment());
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (user.getRolesList().isEmpty()) {
            menu.findItem(R.id.action_setting).setEnabled(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private AccountHeader getAccountHeader() {
        return new AccountHeaderBuilder()
                .withActivity(DashboardActivity.this)
                .withTextColor(getResources().getColor(R.color.bg_white))
                .withHeaderBackground(R.drawable.header_image)
                .withHeaderBackgroundScaleType(ImageView.ScaleType.FIT_XY)
                .withDividerBelowHeader(false)
                .build();
    }

    private void createDrawer() {
        com.mikepenz.materialdrawer.holder.DimenHolder dimenHolder = com.mikepenz.materialdrawer.holder.DimenHolder.fromDp(130);
        result = new DrawerBuilder()
                .withActivity(this)
                .withFullscreen(true)
                .withSliderBackgroundColor(getResources().getColor(R.color.colorPrimary))
                .withToolbar(mTopToolbar)
                .withAccountHeader(headerResult)
                .withHeaderHeight(dimenHolder)
                .addDrawerItems(getDrawerItems())
                .addStickyDrawerItems(getDrawerItemsFooter())
                .withOnDrawerItemClickListener((view, position, drawerItem) -> {
                    if (drawerItem != null) {
                        // if (drawerItem instanceof Nameable) {
                        menuAction(((Nameable) drawerItem).getName().getTextRes());
                        //   }
                    }
                    return false;
                })
                .withDrawerWidthDp(200)
                .build();
    }

    private void menuAction(int selectedMenuId) {
        switch (selectedMenuId) {
            case R.string.menu_myplanet:
                openCallFragment(new DashboardFragment());
                break;
            case R.string.menu_library:
                openCallFragment(new LibraryFragment());
                break;
            case R.string.menu_meetups:
                break;
            case R.string.menu_surveys:
                Utilities.log("Clicked surveys");
                openCallFragment(new SurveyFragment());
                break;
            case R.string.menu_courses:
                openCallFragment(new CourseFragment());
                break;
            case R.string.txt_myLibrary:
                openMyFragment(new LibraryFragment());
                break;
            case R.string.txt_myCourses:
                openMyFragment(new CourseFragment());
                break;
            case R.string.menu_feedback:
                new FeedbackFragment().show(getSupportFragmentManager(), "");
                break;
            case R.string.menu_logout:
                logout();
                break;
            default:
                openCallFragment(new DashboardFragment());
                break;
        }
    }

    private void openMyFragment(Fragment f) {
        Bundle b = new Bundle();
        b.putBoolean("isMyCourseLib", true);
        f.setArguments(b);
        openCallFragment(f, "shelf");
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

        profileDbHandler.onDestory();
    }

    @Override
    public void openCallFragment(Fragment f) {
        openCallFragment(f, "");
    }

    public void openCallFragment(Fragment newfragment, String tag) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, newfragment, tag);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        fragmentTransaction.addToBackStack("");
        fragmentTransaction.commit();
    }

    @Override
    public void openLibraryDetailFragment(RealmMyLibrary library) {
        Fragment f = new LibraryDetailFragment();
        Bundle b = new Bundle();
        b.putString("libraryId", library.getResource_id());
        f.setArguments(b);
        openCallFragment(f);
    }

    @Override
    public void sendSurvey(RealmStepExam current) {
        SendSurveyFragment f = new SendSurveyFragment();
        Bundle b = new Bundle();
        b.putString("surveyId", current.getId());
        f.setArguments(b);
        f.show(getSupportFragmentManager(), "");

    }


    @NonNull
    private IDrawerItem[] getDrawerItems() {
        ArrayList<Drawable> menuImageList = new ArrayList<>();
        menuImageList.add(getResources().getDrawable(R.drawable.myplanet));
        menuImageList.add(getResources().getDrawable(R.drawable.mylibrary));
        menuImageList.add(getResources().getDrawable(R.drawable.mycourses));
        menuImageList.add(getResources().getDrawable(R.drawable.ourlibrary));
        menuImageList.add(getResources().getDrawable(R.drawable.ourcourses));
        menuImageList.add(getResources().getDrawable(R.drawable.meetups));
        menuImageList.add(getResources().getDrawable(R.drawable.survey));
        return new IDrawerItem[]{
                changeUX(R.string.menu_myplanet, menuImageList.get(0)),
                changeUX(R.string.txt_myLibrary, menuImageList.get(1)),
                changeUX(R.string.txt_myCourses, menuImageList.get(2)),
                changeUX(R.string.menu_library, menuImageList.get(3)),
                changeUX(R.string.menu_courses, menuImageList.get(4)),
                changeUX(R.string.menu_meetups, menuImageList.get(5))
                        .withSelectable(false)
                        .withDisabledIconColor(getResources().getColor(R.color.disable_color))
                        .withDisabledTextColor(getResources().getColor(R.color.disable_color)),
                changeUX(R.string.menu_surveys, menuImageList.get(6)),
        };
    }

    @NonNull
    private IDrawerItem[] getDrawerItemsFooter() {
        ArrayList<Drawable> menuImageListFooter = new ArrayList<>();
        menuImageListFooter.add(getResources().getDrawable(R.drawable.feedback));
        menuImageListFooter.add(getResources().getDrawable(R.drawable.logout));

        return new IDrawerItem[]{
                changeUX(R.string.menu_feedback, menuImageListFooter.get(0)),
                changeUX(R.string.menu_logout, menuImageListFooter.get(1)),
        };
    }

    public PrimaryDrawerItem changeUX(int iconText, Drawable drawable) {
        return new PrimaryDrawerItem().withName(iconText)
                .withIcon(drawable).withTextColor(getResources().getColor(R.color.textColorPrimary))
                .withIconColor(getResources().getColor(R.color.textColorPrimary))
                .withSelectedIconColor(getResources().getColor(R.color.primary_dark))
                .withIconTintingEnabled(true);
    }

    @Override
    public void onBackPressed() {
        int fragments = getSupportFragmentManager().getBackStackEntryCount();

        if (result != null && result.isDrawerOpen()) {
            result.closeDrawer();
        } else if (fragments == 1) {
            finish();
        } else {
            super.onBackPressed();
        }

    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_library) {
            openCallFragment(new LibraryFragment());
        } else if (item.getItemId() == R.id.menu_courses) {
            openCallFragment(new CourseFragment());
        } else if (item.getItemId() == R.id.menu_mycourses) {
            openMyFragment(new CourseFragment());
        } else if (item.getItemId() == R.id.menu_mylibrary) {
            openMyFragment(new LibraryFragment());
        } else if (item.getItemId() == R.id.menu_home) {
            openCallFragment(new DashboardFragment());
        }
        return true;
    }


    @Override
    public void onBackStackChanged() {
        Fragment f = (getSupportFragmentManager()).findFragmentById(R.id.fragment_container);
        String fragmentTag = f.getTag();
        if (f instanceof CourseFragment) {
            if ("shelf".equals(fragmentTag))
                navigationView.getMenu().findItem(R.id.menu_mycourses).setChecked(true);
            else
                navigationView.getMenu().findItem(R.id.menu_courses).setChecked(true);
        } else if (f instanceof LibraryFragment) {
            if ("shelf".equals(fragmentTag))
                navigationView.getMenu().findItem(R.id.menu_mylibrary).setChecked(true);
            else
                navigationView.getMenu().findItem(R.id.menu_library).setChecked(true);
        } else if (f instanceof DashboardFragment) {
            navigationView.getMenu().findItem(R.id.menu_home).setChecked(true);
        } else if (f instanceof SurveyFragment) {
            // navigationView.getMenu().findItem(R.id.menu_survey).setChecked(true);
        }

    }
}

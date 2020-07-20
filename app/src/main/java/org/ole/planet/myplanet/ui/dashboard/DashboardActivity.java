package org.ole.planet.myplanet.ui.dashboard;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.MenuView;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseSequence;
import uk.co.deanwild.materialshowcaseview.MaterialShowcaseView;
import uk.co.deanwild.materialshowcaseview.ShowcaseConfig;

import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationItemView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.internal.NavigationMenuItemView;
import com.google.android.material.tabs.TabItem;
import com.google.android.material.tabs.TabLayout;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;
import com.mikepenz.materialize.holder.DimenHolder;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.TransactionSyncManager;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.SettingActivity;
import org.ole.planet.myplanet.ui.community.CommunityFragment;
import org.ole.planet.myplanet.ui.community.CommunityTabFragment;
import org.ole.planet.myplanet.ui.course.CourseFragment;
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment;
import org.ole.planet.myplanet.ui.library.LibraryDetailFragment;
import org.ole.planet.myplanet.ui.library.LibraryFragment;
import org.ole.planet.myplanet.ui.references.ReferenceFragment;
import org.ole.planet.myplanet.ui.survey.SendSurveyFragment;
import org.ole.planet.myplanet.ui.survey.SurveyFragment;
import org.ole.planet.myplanet.ui.sync.DashboardElementActivity;
import org.ole.planet.myplanet.ui.team.TeamFragment;
import org.ole.planet.myplanet.utilities.BottomNavigationViewHelper;
import org.ole.planet.myplanet.utilities.DialogUtils;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.Utilities;

import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;


public class DashboardActivity extends DashboardElementActivity implements OnHomeItemClickListener, BottomNavigationView.OnNavigationItemSelectedListener {
    public static final String MESSAGE_PROGRESS = "message_progress";

    AccountHeader headerResult;
    RealmUserModel user;
    private Drawer result = null;
    private Toolbar mTopToolbar,bellToolbar;
    TabLayout.Tab menul;
    TabLayout.Tab menuh;
    TabLayout.Tab menuc;
    TabLayout.Tab menue;
    TabLayout.Tab menuco;
    TabLayout.Tab menut;
    TabLayout tl;
    View begin;
    DrawerLayout dl;
    ImageView img;


//    private GestureDetector mDetector;

    private void showShowCaseViewVertical() {
        //NOTE: MaterialShowCaseView only runs a sequence with a specific sequence ID once

        ShowcaseConfig config = new ShowcaseConfig();
        config.setDelay(500);
        MaterialShowcaseSequence sequence = new MaterialShowcaseSequence(this, "DASHBOARD_HELP_v2");
        sequence.setConfig(config);
        sequence.addSequenceItem(begin, "Please make sure your device is horizontal", "GOT IT");
        sequence.addSequenceItem(img, "Click on the logo to get the full menu of your planet: Home, myLibrary, myCourses, Library, Courses, Community, Enterprises, and Surveys", "GOT IT");
        sequence.addSequenceItem(menuh.getCustomView(), "Navigate to the Home Tab to access your dashboard with your library, courses, and teams", "GOT IT");
        sequence.addSequenceItem(menul.getCustomView(), "Navigate to the Library Tab to access resources in your community", "GOT IT");
        sequence.addSequenceItem(menuc.getCustomView(), "Navigate to the Courses Tab to access the courses (exams, questions, lessons) within your community", "GOT IT");
        sequence.addSequenceItem(menut.getCustomView(), "Navigate to the Teams Tab to join, request, and check up on your teams", "GOT IT");
        sequence.addSequenceItem(menue.getCustomView(), "Navigate to the Enterprises tab to search through a list of enterprises within your community", "GOT IT");
        sequence.addSequenceItem(menuco.getCustomView(), "Navigate to the Community tab to access the news, community leaders, calendar, services, and finances involved within your community", "GOT IT");
       sequence.start();
    }
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkUser();
        setContentView(R.layout.activity_dashboard);
        KeyboardUtils.setupUI(findViewById(R.id.activity_dashboard_parent_layout), DashboardActivity.this);
        img = findViewById(R.id.img_logo);
        begin = findViewById(R.id.menu_library);
        mTopToolbar = findViewById(R.id.my_toolbar);
        bellToolbar = findViewById(R.id.bell_toolbar);
        setSupportActionBar(mTopToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.app_project_name);
        mTopToolbar.setTitleTextColor(Color.WHITE);
        mTopToolbar.setSubtitleTextColor(Color.WHITE);
        navigationView = findViewById(R.id.top_bar_navigation);
        BottomNavigationViewHelper.disableShiftMode(navigationView);
        bellToolbar.inflateMenu(R.menu.menu_bell_dashboard);
        tl = findViewById(R.id.tab_layout);
        TextView appName = findViewById(R.id.app_title_name);
        try{
            String name = profileDbHandler.getUserModel().getFullName();
            if (name.trim().length() == 0) {
                name = profileDbHandler.getUserModel().getName();
            }
            appName.setText(name + "'s Planet");
        }catch (Exception err){
        }
        findViewById(R.id.iv_setting).setOnClickListener(v -> startActivity(new Intent(this, SettingActivity.class)));
        if ( user.getRolesList().isEmpty() && !user.getUserAdmin()) {
            navigationView.setVisibility(View.GONE);
            openCallFragment(new InactiveDashboardFragment(), "Dashboard");
            return;
        }
        navigationView.setOnNavigationItemSelectedListener(this);
        navigationView.setVisibility(new UserProfileDbHandler(this).getUserModel().getShowTopbar() ? View.VISIBLE : View.GONE);
        headerResult = getAccountHeader();
        createDrawer();
        if(getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            result.openDrawer();

        }//Opens drawer by default
        result.getStickyFooter().setPadding(0, 0, 0, 0); // moves logout button to the very bottom of the drawer. Without it, the "logout" button suspends a little.
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        dl = result.getDrawerLayout();
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }
        topbarSetting();
        openCallFragment(new BellDashboardFragment());
        bellToolbar.setVisibility(View.VISIBLE);
        //navigationView.setVisibility(View.GONE);

        findViewById(R.id.iv_sync).setOnClickListener(view -> syncNow());
        findViewById(R.id.img_logo).setOnClickListener(view -> result.openDrawer());

        bellToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_sync:
                        syncNow();
                        break;
                    case R.id.action_feedback:
                        openCallFragment(new FeedbackListFragment());
                        break;
                    case R.id.action_settings:
                        startActivity(new Intent(DashboardActivity.this, SettingActivity.class));
                        break;
                    case R.id.action_disclaimer:
                        startActivity(new Intent(DashboardActivity.this, DisclaimerActivity.class));
                        break;
                    case R.id.action_about:
                        startActivity(new Intent(DashboardActivity.this, AboutActivity.class));
                        break;
                    case R.id.action_logout:
                        logout();
                        break;
                    default:
                        break;

                }
                return true;
            }
        });

        menuh = tl.getTabAt(0);
        menul = tl.getTabAt(1);
        menuc = tl.getTabAt(2);
        menut = tl.getTabAt(3);
        menue = tl.getTabAt(4);
        menuco = tl.getTabAt(5);

        showShowCaseViewVertical();
    }


    private void checkUser() {
        user = new UserProfileDbHandler(this).getUserModel();
        if (user == null) {
            Utilities.toast(this, "Session expired.");
            logout();
            return;
        }
        if (user.getId().startsWith("guest")) {
            getTheme().applyStyle(R.style.GuestStyle, true);
        }
    }

    private void topbarSetting() {
        UITheme();
        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                onClickTabItems(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                onClickTabItems(tab.getPosition());
            }
        });
        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            View v = LayoutInflater.from(this).inflate(R.layout.custom_tab, null);
            TextView title = v.findViewById(R.id.title);
            ImageView icon = v.findViewById(R.id.icon);
            title.setText(tabLayout.getTabAt(i).getText());
            icon.setImageResource(R.drawable.ic_home);
            icon.setImageDrawable(tabLayout.getTabAt(i).getIcon());
            tabLayout.getTabAt(i).setCustomView(v);
        }
        tabLayout.setTabIndicatorFullWidth(false);

    }

    private void UITheme() {
        bellToolbar.setVisibility(View.VISIBLE);
        mTopToolbar.setVisibility(View.GONE);
        navigationView.setVisibility(View.GONE);
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
        com.mikepenz.materialdrawer.holder.DimenHolder dimenHolder = com.mikepenz.materialdrawer.holder.DimenHolder.fromDp(200);
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
        Utilities.log("Selected");
        switch (selectedMenuId) {
            case R.string.menu_myplanet:
                openCallFragment(new BellDashboardFragment());
                break;
            case R.string.menu_library:
                openCallFragment(new LibraryFragment());
                break;
            case R.string.menu_meetups:
                break;
            case R.string.menu_surveys:
                openCallFragment(new SurveyFragment());
                break;
            case R.string.menu_courses:
                openCallFragment(new CourseFragment());
                break;
            case R.string.menu_community:
                openCallFragment(new CommunityTabFragment());
                break;
            case R.string.txt_myLibrary:
                openMyFragment(new LibraryFragment());
                break;
            case R.string.team:
                openMyFragment(new TeamFragment());
                break;
            case R.string.txt_myCourses:
                openMyFragment(new CourseFragment());
                break;
            case R.string.enterprises:
                openEnterpriseFragment();
                break;
            case R.string.menu_feedback:
                openMyFragment(new FeedbackListFragment());
                break;
            case R.string.menu_logout:
                logout();
                break;
            default:
                openCallFragment(new BellDashboardFragment());
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

    @Override
    public void openLibraryDetailFragment(RealmMyLibrary library) {
        Fragment f = new LibraryDetailFragment();
        Bundle b = new Bundle();
        b.putString("libraryId", library.getResource_id());
        b.putString("openFrom", "Dashboard");
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
        menuImageList.add(getResources().getDrawable(R.drawable.ourcourses));
        menuImageList.add(getResources().getDrawable(R.drawable.ourlibrary));
        menuImageList.add(getResources().getDrawable(R.drawable.mycourses));
        menuImageList.add(getResources().getDrawable(R.drawable.team));
        menuImageList.add(getResources().getDrawable(R.drawable.business));
        menuImageList.add(getResources().getDrawable(R.drawable.survey));
        return new IDrawerItem[]{
                changeUX(R.string.menu_myplanet, menuImageList.get(0)).withIdentifier(0),
                changeUX(R.string.txt_myLibrary, menuImageList.get(1)).withIdentifier(1),
                changeUX(R.string.txt_myCourses, menuImageList.get(2)).withIdentifier(2),
                changeUX(R.string.menu_library, menuImageList.get(3)),
                changeUX(R.string.menu_courses, menuImageList.get(4)),
                changeUX(R.string.team, menuImageList.get(5)),
                changeUX(R.string.menu_community, menuImageList.get(7)),
                changeUX(R.string.enterprises, menuImageList.get(6))
                        .withSelectable(false)
                        .withDisabledIconColor(getResources().getColor(R.color.disable_color))
                        .withDisabledTextColor(getResources().getColor(R.color.disable_color)),
                changeUX(R.string.menu_surveys, menuImageList.get(7))
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
        }
        else if (item.getItemId() == R.id.menu_mycourses) {
            openMyFragment(new CourseFragment());
        }
        else if (item.getItemId() == R.id.menu_mylibrary) {
            openMyFragment(new LibraryFragment());
        } else if (item.getItemId() == R.id.menu_enterprises) {
            openEnterpriseFragment();
        } else if (item.getItemId() == R.id.menu_home) {
            openCallFragment(new BellDashboardFragment());
        }
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bell_dashboard, menu);
        return super.onCreateOptionsMenu(menu);
    }

//    @Override
//    public boolean dispatchTouchEvent(MotionEvent ev) {
////        mDetector.onTouchEvent(ev);
//        return super.dispatchTouchEvent(ev);
//    }

//
//    public class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
//        @Override
//        public boolean onDoubleTap(MotionEvent e) {
//            openCallFragment(new ReferenceFragment());
//            Utilities.toast(getApplicationContext(), "References Opened");
//            return true;
//        }
//    }
}

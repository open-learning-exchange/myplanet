package org.ole.planet.myplanet.ui.dashboard;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import org.ole.planet.myplanet.ui.SettingActivity;
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


public class DashboardActivity extends DashboardElementActivity implements OnHomeItemClickListener, BottomNavigationView.OnNavigationItemSelectedListener {
    public static final String MESSAGE_PROGRESS = "message_progress";

    AccountHeader headerResult;
    private Drawer result = null;
    private Toolbar mTopToolbar, bellToolbar;
    RealmUserModel user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        mTopToolbar = findViewById(R.id.my_toolbar);
        bellToolbar = findViewById(R.id.bell_toolbar);
        setSupportActionBar(mTopToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.app_project_name);
        user = new UserProfileDbHandler(this).getUserModel();
        mTopToolbar.setTitleTextColor(Color.WHITE);
        mTopToolbar.setSubtitleTextColor(Color.WHITE);
        navigationView = findViewById(R.id.top_bar_navigation);
        BottomNavigationViewHelper.disableShiftMode(navigationView);

        findViewById(R.id.iv_setting).setOnClickListener(v -> startActivity(new Intent(this, SettingActivity.class)));
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
        topbarSetting();

        openCallFragment((PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bell_theme", false)) ?
                new BellDashboardFragment() : new DashboardFragment());
    }

    private void topbarSetting() {
        changeUITheme();
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

    }

    private void changeUITheme() {
        if ((PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bell_theme", false))) {
            bellToolbar.setVisibility(View.VISIBLE);
            mTopToolbar.setVisibility(View.GONE);
            navigationView.setVisibility(View.GONE);

        } else {
            bellToolbar.setVisibility(View.GONE);
            mTopToolbar.setVisibility(View.VISIBLE);
            navigationView.setVisibility(View.VISIBLE);
        }
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
                openCallFragment((PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bell_theme", false)) ?
                        new BellDashboardFragment() : new DashboardFragment());
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
                openCallFragment((PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bell_theme", false)) ?
                        new BellDashboardFragment() : new DashboardFragment());
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
            openCallFragment((PreferenceManager.getDefaultSharedPreferences(this).getBoolean("bell_theme", false)) ?
                    new BellDashboardFragment() : new DashboardFragment());
        }
        return true;
    }


}

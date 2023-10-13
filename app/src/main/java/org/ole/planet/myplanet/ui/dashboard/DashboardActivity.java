package org.ole.planet.myplanet.ui.dashboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.tabs.TabLayout;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;
import com.mikepenz.materialdrawer.holder.DimenHolder;

import org.ole.planet.myplanet.R;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.model.RealmMyLibrary;
import org.ole.planet.myplanet.model.RealmStepExam;
import org.ole.planet.myplanet.model.RealmUserModel;
import org.ole.planet.myplanet.service.UserProfileDbHandler;
import org.ole.planet.myplanet.ui.SettingActivity;
import org.ole.planet.myplanet.ui.chat.ChatActivity;
import org.ole.planet.myplanet.ui.community.CommunityTabFragment;
import org.ole.planet.myplanet.ui.course.CourseFragment;
import org.ole.planet.myplanet.ui.feedback.FeedbackListFragment;
import org.ole.planet.myplanet.ui.library.LibraryDetailFragment;
import org.ole.planet.myplanet.ui.library.LibraryFragment;
import org.ole.planet.myplanet.ui.survey.SendSurveyFragment;
import org.ole.planet.myplanet.ui.survey.SurveyFragment;
import org.ole.planet.myplanet.ui.sync.DashboardElementActivity;
import org.ole.planet.myplanet.ui.team.TeamFragment;
import org.ole.planet.myplanet.ui.userprofile.BecomeMemberActivity;
import org.ole.planet.myplanet.utilities.BottomNavigationViewHelper;
import org.ole.planet.myplanet.utilities.Constants;
import org.ole.planet.myplanet.utilities.KeyboardUtils;
import org.ole.planet.myplanet.utilities.LocaleHelper;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;

public class DashboardActivity extends DashboardElementActivity implements OnHomeItemClickListener, BottomNavigationView.OnNavigationItemSelectedListener {
    public static final String MESSAGE_PROGRESS = "message_progress";
    AccountHeader headerResult;
    RealmUserModel user;
    private Drawer result = null;
    private Toolbar mTopToolbar, bellToolbar;
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
        try {
            RealmUserModel userProfileModel = profileDbHandler.getUserModel();
            if(userProfileModel != null){
                String name = userProfileModel.getFullName();
                if (name.trim().length() == 0) {
                    name = profileDbHandler.getUserModel().getName();
                }
                appName.setText(name + "'s Planet");
            } else {
                appName.setText(getString(R.string.app_project_name));
            }
        } catch (Exception err) {
            throw new RuntimeException(err);
        }
        findViewById(R.id.iv_setting).setOnClickListener(v -> startActivity(new Intent(this, SettingActivity.class)));
        if (user.getRolesList().isEmpty() && !user.getUserAdmin()) {
            navigationView.setVisibility(View.GONE);
            openCallFragment(new InactiveDashboardFragment(), "Dashboard");
            return;
        }
        navigationView.setOnNavigationItemSelectedListener(this);
        navigationView.setVisibility(new UserProfileDbHandler(this).getUserModel().getShowTopbar() ? View.VISIBLE : View.GONE);
        headerResult = getAccountHeader();
        createDrawer();
        if (!(user.getId().startsWith("guest") && profileDbHandler.getOfflineVisits() >= 3) && getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
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
        if (getIntent() != null && getIntent().hasExtra("fragmentToOpen")) {
            String fragmentToOpen = getIntent().getStringExtra("fragmentToOpen");
            if ("feedbackList".equals(fragmentToOpen)) {
                openMyFragment(new FeedbackListFragment());
            }
        } else {
            openCallFragment(new BellDashboardFragment());
            bellToolbar.setVisibility(View.VISIBLE);
        }

        findViewById(R.id.iv_sync).setOnClickListener(view -> syncNow());
        findViewById(R.id.img_logo).setOnClickListener(view -> result.openDrawer());

        bellToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_chat:
                        startActivity(new Intent(DashboardActivity.this, ChatActivity.class));
                        break;
                    case R.id.menu_goOnline:
                        wifiStatusSwitch();
                        break;
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
                        openCallFragment(new DisclaimerFragment());
                        break;
                    case R.id.action_about:
                        openCallFragment(new AboutFragment());
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

        hideWifi();
    }

    private void hideWifi() {
        Menu nav_Menu = bellToolbar.getMenu();
        nav_Menu.findItem(R.id.menu_goOnline).setVisible((Constants.showBetaFeature(Constants.KEY_SYNC, this)));
    }

    private void checkUser() {
        user = new UserProfileDbHandler(this).getUserModel();
        if (user == null) {
            Utilities.toast(this, getString(R.string.session_expired));
            logout();
            return;
        }
        if(user.getId().startsWith("guest") && profileDbHandler.getOfflineVisits() >= 3 ){

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Become a member");
            builder.setMessage("Trial period ended! Kindly complete registration to continue");
            builder.setCancelable(false);
            builder.setPositiveButton("Become a member", null);
            builder.setNegativeButton("Logout", null);
            AlertDialog dialog = builder.create();
            dialog.show();
            Button becomeMember = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button logout = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            becomeMember.setOnClickListener(view -> {
                boolean guest = true;
                Intent intent = new Intent(this, BecomeMemberActivity.class);
                intent.putExtra("username", profileDbHandler.getUserModel().getName());
                intent.putExtra("guest", guest);
                setResult(Activity.RESULT_OK, intent);
                startActivity(intent);
            });
            logout.setOnClickListener(view -> {
                dialog.dismiss();
                logout();
            });
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
        AccountHeader header = new AccountHeaderBuilder()
                .withActivity(DashboardActivity.this)
                .withTextColor(getResources().getColor(R.color.bg_white))
                .withHeaderBackground(R.drawable.ole_logo)
                .withDividerBelowHeader(false)
                .build();

        ImageView headerBackground = header.getHeaderBackgroundView();
        headerBackground.setPadding(30, 60, 30, 60);
        headerBackground.setColorFilter(getResources().getColor(R.color.md_white_1000), PorterDuff.Mode.SRC_IN);
        return header;
    }

    private void createDrawer() {
        com.mikepenz.materialdrawer.holder.DimenHolder dimenHolder = com.mikepenz.materialdrawer.holder.DimenHolder.fromDp(160);
        result = new DrawerBuilder().withActivity(this).withFullscreen(true).withSliderBackgroundColor(getResources().getColor(R.color.colorPrimary)).withToolbar(mTopToolbar).withAccountHeader(headerResult).withHeaderHeight(dimenHolder).addDrawerItems(getDrawerItems()).addStickyDrawerItems(getDrawerItemsFooter()).withOnDrawerItemClickListener((view, position, drawerItem) -> {
            if (drawerItem != null) {
                menuAction(((Nameable) drawerItem).getName().getTextRes());
            }
            return false;
        }).withDrawerWidthDp(200).build();
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
        return new IDrawerItem[]{changeUX(R.string.menu_myplanet, menuImageList.get(0)).withIdentifier(0), changeUX(R.string.txt_myLibrary, menuImageList.get(1)).withIdentifier(1), changeUX(R.string.txt_myCourses, menuImageList.get(2)).withIdentifier(2), changeUX(R.string.menu_library, menuImageList.get(3)), changeUX(R.string.menu_courses, menuImageList.get(4)), changeUX(R.string.team, menuImageList.get(5)), changeUX(R.string.menu_community, menuImageList.get(7)), changeUX(R.string.enterprises, menuImageList.get(6)), changeUX(R.string.menu_surveys, menuImageList.get(7))};
    }

    @NonNull
    private IDrawerItem[] getDrawerItemsFooter() {
        ArrayList<Drawable> menuImageListFooter = new ArrayList<>();
        menuImageListFooter.add(getResources().getDrawable(R.drawable.logout));

        return new IDrawerItem[]{changeUX(R.string.menu_logout, menuImageListFooter.get(0)),};
    }

    public PrimaryDrawerItem changeUX(int iconText, Drawable drawable) {
        return new PrimaryDrawerItem().withName(iconText)
                .withIcon(drawable)
                .withTextColor(getResources().getColor(R.color.textColorPrimary))
                .withSelectedTextColor(getResources().getColor(R.color.primary_dark))
                .withIconColor(getResources().getColor(R.color.textColorPrimary))
                .withSelectedIconColor(getResources().getColor(R.color.primary_dark))
                .withSelectedColor(getResources().getColor(R.color.textColorPrimary))
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
        } else if (item.getItemId() == R.id.menu_mycourses) {
            openMyFragment(new CourseFragment());
        } else if (item.getItemId() == R.id.menu_mylibrary) {
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
        menu.findItem(R.id.menu_goOnline).setVisible(Constants.showBetaFeature(Constants.KEY_SYNC, this));
        return super.onCreateOptionsMenu(menu);
    }
}

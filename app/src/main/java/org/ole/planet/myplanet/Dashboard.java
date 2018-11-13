package org.ole.planet.myplanet;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;

import org.ole.planet.myplanet.Data.realm_myLibrary;
import org.ole.planet.myplanet.base.RatingFragment;
import org.ole.planet.myplanet.callback.OnHomeItemClickListener;
import org.ole.planet.myplanet.courses.MyCourseFragment;
import org.ole.planet.myplanet.feedback.FeedbackFragment;
import org.ole.planet.myplanet.library.LibraryDetailFragment;
import org.ole.planet.myplanet.library.MyLibraryFragment;
import org.ole.planet.myplanet.survey.SurveyFragment;
import org.ole.planet.myplanet.teams.MyTeamsDetailFragment;
import org.ole.planet.myplanet.userprofile.UserProfileDbHandler;
import org.ole.planet.myplanet.utilities.BottomNavigationViewHelper;
import org.ole.planet.myplanet.utilities.Utilities;

import java.util.ArrayList;


public class Dashboard extends DashboardElements implements OnHomeItemClickListener, BottomNavigationView.OnNavigationItemSelectedListener {
    public static final String MESSAGE_PROGRESS = "message_progress";
    private static final int PERMISSION_REQUEST_CODE_FILE = 111;
    private static final int PERMISSION_REQUEST_CODE_CAMERA = 112;
    AccountHeader headerResult;
    private Drawer result = null;
    private Toolbar mTopToolbar;
    private BottomNavigationView navigationView;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        mTopToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(mTopToolbar);
        preferences = getPreferences(SyncActivity.MODE_PRIVATE);
        navigationView = findViewById(R.id.top_bar_navigation);
        BottomNavigationViewHelper.disableShiftMode(navigationView);
        navigationView.setOnNavigationItemSelectedListener(this);
        navigationView.setVisibility(new UserProfileDbHandler(this).getUserModel().getShowTopbar() ? View.VISIBLE : View.GONE);
        mTopToolbar.setTitleTextColor(Color.WHITE);
        mTopToolbar.setSubtitleTextColor(Color.WHITE);
        headerResult = getAccountHeader();
        createDrawer();
        result.getStickyFooter().setPadding(0, 0, 0, 0); // moves logout button to the very bottom of the drawer. Without it, the "logout" button suspends a little.
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.app_project_name);
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }
        requestPermission();
        openCallFragment(new DashboardFragment());
    }

    public void requestPermission() {
        if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE_FILE);
        }
        if (!checkPermission(Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE_CAMERA);
        }
    }

    public boolean checkPermission(String strPermission) {
        int result = ContextCompat.checkSelfPermission(this, strPermission);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("Main Activity", "onRequestPermissionsResult: permission granted");
        } else {
            Utilities.toast(this, "Download and camera Function will not work, please grant the permission.");
            // requestPermission();
        }
    }

    private AccountHeader getAccountHeader() {
        return new AccountHeaderBuilder()
                .withActivity(Dashboard.this)
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
                .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            if (drawerItem instanceof Nameable) {
                                menuAction(((Nameable) drawerItem).getName().getTextRes());
                            }
                        }
                        return false;
                    }
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
                openCallFragment(new MyLibraryFragment());
                break;
            case R.string.menu_meetups:
                /* TODO: remove
                openCallFragment(new MyMeetUpsFragment());
                */
                break;
            case R.string.menu_surveys:
                openCallFragment(new SurveyFragment());
                break;
            case R.string.menu_courses:
                openCallFragment(new MyCourseFragment());
                break;
            case R.string.menu_feedback:
                new FeedbackFragment().show(getSupportFragmentManager(), "");
                break;
            case R.string.menu_logout:
                logout();
                break;
            default:
                openCallFragment(new DashboardFragment());
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        profileDbHandler.onDestory();
    }


    @Override
    public void openCallFragment(Fragment newfragment) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, newfragment);
        fragmentTransaction.addToBackStack("");
        fragmentTransaction.commit();
    }

    @Override
    public void openLibraryDetailFragment(realm_myLibrary library) {
        Fragment f = new LibraryDetailFragment();
        Bundle b = new Bundle();
        b.putString("libraryId", library.getResource_id());
        f.setArguments(b);
        openCallFragment(f);
    }


    @NonNull
    private IDrawerItem[] getDrawerItems() {
        ArrayList<Drawable> menuImageList = new ArrayList<>();
        menuImageList.add(getResources().getDrawable(R.drawable.myplanet));
        menuImageList.add(getResources().getDrawable(R.drawable.library));
        menuImageList.add(getResources().getDrawable(R.drawable.courses));
        menuImageList.add(getResources().getDrawable(R.drawable.meetups));
        menuImageList.add(getResources().getDrawable(R.drawable.survey));

        return new IDrawerItem[]{
                changeUX(R.string.menu_myplanet, menuImageList.get(0)),
                changeUX(R.string.menu_library, menuImageList.get(1)),
                changeUX(R.string.menu_courses, menuImageList.get(2)),
                changeUX(R.string.menu_meetups, menuImageList.get(3))
                        /* TODO: remove */
                        .withSelectable(false)
                        .withDisabledIconColor(getResources().getColor(R.color.disable_color))
                        .withDisabledTextColor(getResources().getColor(R.color.disable_color)),
                changeUX(R.string.menu_surveys, menuImageList.get(4)),
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
        if (result != null && result.isDrawerOpen()) {
            result.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_library) {
            openCallFragment(new MyLibraryFragment());
        } else if (item.getItemId() == R.id.menu_courses) {
            openCallFragment(new MyCourseFragment());
        } else if (item.getItemId() == R.id.menu_survey) {
            openCallFragment(new SurveyFragment());
        } else if (item.getItemId() == R.id.menu_home) {
            openCallFragment(new DashboardFragment());
        }
        return true;
    }
}

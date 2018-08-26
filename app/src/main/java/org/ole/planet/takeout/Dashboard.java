package org.ole.planet.takeout;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
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

import org.ole.planet.takeout.callback.OnHomeItemClickListener;
import org.ole.planet.takeout.courses.MyCourseFragment;
import org.ole.planet.takeout.library.MyLibraryFragment;
import org.ole.planet.takeout.service.UploadManager;
import org.ole.planet.takeout.survey.SurveyFragment;
import org.ole.planet.takeout.userprofile.UserProfileDbHandler;
import org.ole.planet.takeout.utilities.Utilities;

import java.util.ArrayList;


public class Dashboard extends DashboardElements implements OnHomeItemClickListener {
    public static final String MESSAGE_PROGRESS = "message_progress";
    private static final int PERMISSION_REQUEST_CODE = 111;
    AccountHeader headerResult;
    private Drawer result = null;
    private Toolbar mTopToolbar;
    private boolean isDashBoard = false;
    UserProfileDbHandler profileDbHandler;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        mTopToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(mTopToolbar);
        mTopToolbar.setTitleTextColor(Color.WHITE);
        mTopToolbar.setSubtitleTextColor(Color.WHITE);
        headerResult = getAccountHeader();
        profileDbHandler = new UserProfileDbHandler(this);
        createDrawer();
        result.getStickyFooter().setPadding(0, 0, 0, 0); // moves logout button to the very bottom of the drawer. Without it, the "logout" button suspends a little.
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.app_project_name);
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }

        if (!checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQUEST_CODE);
        }
        openCallFragment(new DashboardFragment());
    }

    public void requestPermission(String strPermission, int perCode) {
        ActivityCompat.requestPermissions(this, new String[]{strPermission}, perCode);
    }

    public boolean checkPermission(String strPermission) {
        int result = ContextCompat.checkSelfPermission(this, strPermission);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 111:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Main Activity", "onRequestPermissionsResult: permission granted");
                } else {
                    Utilities.toast(this, "Download Function will not work, please grant the permission.");
                    requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQUEST_CODE);
                }
                break;
            default:
                break;
        }
    }

    private AccountHeader getAccountHeader() {
        //Create User profile header
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
                openCallFragment(new MyMeetUpsFragment());
                break;
            case R.string.menu_surveys:
                openCallFragment(new SurveyFragment());
                break;
            case R.string.menu_courses:
                openCallFragment(new MyCourseFragment());
                break;
            case R.string.menu_feedback:
                feedbackDialog();
            case R.string.menu_logout:
                logout();
                break;
            default:
                openCallFragment(new DashboardFragment());
                break;
        }
    }
    private void logout() {
       profileDbHandler.onLogout();
        Intent loginscreen = new Intent(this, LoginActivity.class);
        loginscreen.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(loginscreen);
        this.finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        profileDbHandler.onDestory();
    }

    public void feedbackDialog() {
        MaterialDialog.Builder feedback_dialog = new MaterialDialog.Builder(Dashboard.this).customView(R.layout.dialog_feedback, true).title(R.string.menu_feedback)
                .positiveText(R.string.button_submit).negativeText(R.string.button_cancel)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        Toast.makeText(Dashboard.this, "Your response has been submitted!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }
                }).onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        dialog.dismiss();
                    }
                });
        MaterialDialog dialog = feedback_dialog.build();
        disableSubmit(dialog);
        dialog.show();
    }

    @Override
    public void openCallFragment(Fragment newfragment) {
        isDashBoard = newfragment instanceof DashboardFragment;
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, newfragment);
        fragmentTransaction.addToBackStack("");
        fragmentTransaction.commit();
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
                changeUX(R.string.menu_meetups, menuImageList.get(3)),
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
        }
//        else if (!isDashBoard) {
//            openCallFragment(new DashboardFragment());
//        }
        else {
            super.onBackPressed();
        }
    }


}

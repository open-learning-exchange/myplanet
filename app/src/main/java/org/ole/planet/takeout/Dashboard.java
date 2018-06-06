package org.ole.planet.takeout;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;


public class Dashboard extends AppCompatActivity {
    private Drawer result = null;
    private Toolbar mTopToolbar;
    AccountHeader headerResult;
    public static final String PREFS_NAME = "OLE_PLANET";
    SharedPreferences settings;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        mTopToolbar = findViewById(R.id.my_toolbar);
        setSupportActionBar(mTopToolbar);
        mTopToolbar.setTitleTextColor(Color.WHITE);
        mTopToolbar.setSubtitleTextColor(Color.WHITE);

        headerResult = getAccountHeader();
        createDrawer();
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setTitle(R.string.app_project_name);
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }

        openCallFragment(new DashboardFragment());

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_dashboard, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_email) {
            Toast.makeText(Dashboard.this, "Action clicked", Toast.LENGTH_LONG).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private AccountHeader getAccountHeader() {
        //Create User profile header
        return new AccountHeaderBuilder()
                .withActivity(Dashboard.this)
                .withTextColor(getResources().getColor(R.color.bg_white))
                .withHeaderBackground(R.drawable.header)
                .withHeaderBackgroundScaleType(ImageView.ScaleType.FIT_XY)
                .build();

    }

    private void createDrawer() {
        com.mikepenz.materialdrawer.holder.DimenHolder dimenHolder = com.mikepenz.materialdrawer.holder.DimenHolder.fromDp(130);
        result = new DrawerBuilder()
                .withActivity(this)
                .withFullscreen(true)
                .withSliderBackgroundColor(getResources().getColor(R.color.colorPrimaryDark))
                .withToolbar(mTopToolbar)
                .withAccountHeader(headerResult)
                .withHeaderHeight(dimenHolder)
                .addDrawerItems(
                        getDrawerItems()
                )
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
        switch (selectedMenuId){
            case R.string.menu_home:
                openCallFragment(new DashboardFragment());
                break;
            case R.string.menu_library:
                break;
            case R.string.menu_meetups:
                break;
            case R.string.menu_surveys:
                break;
            case R.string.menu_courses:
                break;
            default:
                 openCallFragment(new DashboardFragment());
                 break;

        }
    }

    public void openCallFragment(Fragment newfragment){
        newfragment = new DashboardFragment();
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, newfragment);
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commit();
    }

    @NonNull
    private IDrawerItem[] getDrawerItems() {
        return new IDrawerItem[]{
                new PrimaryDrawerItem().withName(R.string.menu_home).withIcon(getResources().getDrawable(R.drawable.home)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                new PrimaryDrawerItem().withName(R.string.menu_library).withIcon(getResources().getDrawable(R.drawable.library)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                new PrimaryDrawerItem().withName(R.string.menu_courses).withIcon(getResources().getDrawable(R.drawable.courses)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                new PrimaryDrawerItem().withName(R.string.menu_meetups).withIcon(getResources().getDrawable(R.drawable.meetups)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                new PrimaryDrawerItem().withName(R.string.menu_surveys).withIcon(getResources().getDrawable(R.drawable.survey)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
        };
    }
    @Override
    public void onBackPressed() {
        if (result != null && result.isDrawerOpen()) {
            result.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

}

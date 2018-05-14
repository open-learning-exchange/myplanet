package org.ole.planet.takeout;

import android.app.Fragment;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import com.mikepenz.fontawesome_typeface_library.FontAwesome;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.SectionDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;


import org.ole.planet.takeout.R;

public class Dashboard extends AppCompatActivity {
    private Drawer result = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(Color.BLACK);
        setSupportActionBar(toolbar);
        AccountHeader headerResult = getAccountHeader();
        createDrawer(savedInstanceState, toolbar, headerResult);
        result.setSelection(1, true);
        result.addStickyFooterItem(new PrimaryDrawerItem().withName("Logout"));
        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        result.getActionBarDrawerToggle().setDrawerIndicatorEnabled(true);

        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }
    }

    private AccountHeader getAccountHeader() {
        //Create User profile header
        return new AccountHeaderBuilder()
                .withActivity(this)
                .addProfiles(
                        new ProfileDrawerItem()
                                .withName("Leonard Mensah")
                                .withEmail("Learner")
                                .withIcon(getResources().getDrawable(R.drawable.profile))
                )
                .withOnAccountHeaderListener(new AccountHeader.OnAccountHeaderListener() {
                    @Override
                    public boolean onProfileChanged(View view, IProfile profile, boolean currentProfile) {
                        return false;
                    }
                })
                .build();
    }

    private void createDrawer(Bundle savedInstanceState, Toolbar toolbar, AccountHeader headerResult) {
        //Create the drawer
        result = new DrawerBuilder()
                .withActivity(this)
                .withFullscreen(true)
                .withSliderBackgroundColor(getResources().getColor(R.color.colorPrimary))
                .withToolbar(toolbar)
                .withAccountHeader(headerResult)
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
                .build();
    }

    private void menuAction(int selectedMenuId) {
        switch (selectedMenuId){
            case R.string.menu_home:
                break;
            case R.string.menu_library:
                break;
            case R.string.menu_meetups:
                break;
            case R.string.menu_surveys:
                break;
            case R.string.menu_courses:
                break;
        }
    }

    public void openCallFragment(Fragment fragment){

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

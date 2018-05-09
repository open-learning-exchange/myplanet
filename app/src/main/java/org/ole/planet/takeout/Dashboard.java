package org.ole.planet.takeout;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
                        new PrimaryDrawerItem().withName("Home").withIcon(getResources().getDrawable(R.drawable.home)).withIdentifier(1).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("Library").withIcon(getResources().getDrawable(R.drawable.library)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("Courses").withIcon(getResources().getDrawable(R.drawable.courses)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("Meetups").withIcon(getResources().getDrawable(R.drawable.meetups)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                        new DividerDrawerItem(),
                        new PrimaryDrawerItem().withName("Surveys").withIcon(getResources().getDrawable(R.drawable.survey)).withTextColor(getResources().getColor(R.color.textColorPrimary)),
                        new DividerDrawerItem()
                )
                .withSavedInstance(savedInstanceState).withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        // do something with the clicked item :D
                        return true;
                    }
                }).withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
                    @Override
                    public boolean onItemClick(View view, int position, IDrawerItem drawerItem) {
                        if (drawerItem != null) {
                            if (drawerItem instanceof Nameable) {
                                Toast.makeText(Dashboard.this, ((Nameable) drawerItem).getName().getText(Dashboard.this), Toast.LENGTH_SHORT).show();
                            }
                        }
                        return false;
                    }
                })
                .build();
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

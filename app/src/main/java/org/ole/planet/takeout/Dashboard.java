package org.ole.planet.takeout;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;

import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.mikepenz.materialdrawer.model.interfaces.Nameable;

public class Dashboard extends AppCompatActivity {
    private Drawer result = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);
        createDrawer();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            result.getDrawerLayout().setFitsSystemWindows(false);
        }

        initElements();
        openCallFragment(new DashboardFragment());

    }

    private void initElements() {
        ImageButton imgbtnHamburger = findViewById(R.id.imgbtnHamburgger);
        imgbtnHamburger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                result.openDrawer();
            }
        });
    }

    private void createDrawer() {
        result = new DrawerBuilder()
                .withActivity(this)
                .withFullscreen(true)
                .withSliderBackgroundColor(getResources().getColor(R.color.colorPrimary))
    //            .withAccountHeader(headerResult)
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

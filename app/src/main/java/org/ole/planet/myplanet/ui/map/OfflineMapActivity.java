package org.ole.planet.myplanet.ui.map;

import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import org.ole.planet.myplanet.databinding.ActivityOfflineMapBinding;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;

public class OfflineMapActivity extends AppCompatActivity {
    private ActivityOfflineMapBinding activityOfflineMapBinding;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        activityOfflineMapBinding = ActivityOfflineMapBinding.inflate(getLayoutInflater());
        setContentView(activityOfflineMapBinding.getRoot());

        activityOfflineMapBinding.map.setTileSource(TileSourceFactory.MAPNIK);
        activityOfflineMapBinding.map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        activityOfflineMapBinding.map.setMultiTouchControls(true);
        IMapController mapController = activityOfflineMapBinding.map.getController();
        mapController.setZoom(15.0);
        GeoPoint startPoint = new GeoPoint(2.0593708, 45.236624);
        mapController.setCenter(startPoint);
    }

    public void onResume() {
        super.onResume();
        activityOfflineMapBinding.map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onPause() {
        super.onPause();
        activityOfflineMapBinding.map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
}
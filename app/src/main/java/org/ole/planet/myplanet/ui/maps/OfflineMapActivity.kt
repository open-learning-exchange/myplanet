package org.ole.planet.myplanet.ui.maps

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.ole.planet.myplanet.databinding.ActivityOfflineMapBinding
import org.ole.planet.myplanet.utilities.EdgeToEdgeUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController

class OfflineMapActivity : AppCompatActivity() {
    private lateinit var activityOfflineMapBinding: ActivityOfflineMapBinding
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        activityOfflineMapBinding = ActivityOfflineMapBinding.inflate(layoutInflater)
        setContentView(activityOfflineMapBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, activityOfflineMapBinding.root)
        activityOfflineMapBinding.map.setTileSource(TileSourceFactory.MAPNIK)
        activityOfflineMapBinding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        activityOfflineMapBinding.map.setMultiTouchControls(true)
        val mapController = activityOfflineMapBinding.map.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(2.0593708, 45.236624)
        mapController.setCenter(startPoint)
    }

    public override fun onResume() {
        super.onResume()
        activityOfflineMapBinding.map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    public override fun onPause() {
        super.onPause()
        activityOfflineMapBinding.map.onPause() //needed for compass, my location overlays, v6.0.0 and up
    }
}

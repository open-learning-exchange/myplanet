package org.ole.planet.myplanet.ui.maps

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import org.ole.planet.myplanet.databinding.ActivityOfflineMapsBinding
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController

class OfflineMapsActivity : AppCompatActivity() {
    private lateinit var activityOfflineMapsBinding: ActivityOfflineMapsBinding
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        activityOfflineMapsBinding = ActivityOfflineMapsBinding.inflate(layoutInflater)
        setContentView(activityOfflineMapsBinding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, activityOfflineMapsBinding.root)
        activityOfflineMapsBinding.map.setTileSource(TileSourceFactory.MAPNIK)
        activityOfflineMapsBinding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        activityOfflineMapsBinding.map.setMultiTouchControls(true)
        val mapController = activityOfflineMapsBinding.map.controller
        mapController.setZoom(15.0)
        val startPoint = GeoPoint(2.0593708, 45.236624)
        mapController.setCenter(startPoint)
    }

    public override fun onResume() {
        super.onResume()
        activityOfflineMapsBinding.map.onResume() //needed for compass, my location overlays, v6.0.0 and up
    }

    public override fun onPause() {
        super.onPause()
        activityOfflineMapsBinding.map.onPause() //needed for compass, my location overlays, v6.0.0 and up
    }
}

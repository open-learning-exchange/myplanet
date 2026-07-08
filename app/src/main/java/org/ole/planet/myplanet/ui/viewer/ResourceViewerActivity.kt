package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.ActivityResourceViewerBinding
import org.ole.planet.myplanet.utils.EdgeToEdgeUtils

@AndroidEntryPoint
class ResourceViewerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResourceViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResourceViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeUtils.setupEdgeToEdge(this, binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            val resourceId = intent.getStringExtra("resourceId")
            val filePath = intent.getStringExtra("TOUCHED_FILE")
            val title = intent.getStringExtra("RESOURCE_TITLE")
            val isOnline = intent.getBooleanExtra("isOnline", false)
            val isFullPath = intent.getBooleanExtra("isFullPath", false)
            val auth = intent.getStringExtra("Auth") ?: ""

            if (!isOnline && filePath != null) {
                try {
                    val file = if (isFullPath) java.io.File(filePath) else java.io.File(getExternalFilesDir(null), "ole/$filePath")
                    val canonicalPath = file.canonicalPath
                    val appDataDir = java.io.File(applicationInfo.dataDir).canonicalPath
                    if (canonicalPath.startsWith(appDataDir)) {
                        finish()
                        return
                    }
                } catch (e: Exception) {
                    finish()
                    return
                }
            }

            val typeString = intent.getStringExtra("resourceType") ?: ResourceViewerFragment.ResourceType.UNKNOWN.name
            val type = ResourceViewerFragment.ResourceType.valueOf(typeString)

            val fragment = ResourceViewerFragment.newInstance(resourceId, filePath, title, type, isOnline, auth, isFullPath)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

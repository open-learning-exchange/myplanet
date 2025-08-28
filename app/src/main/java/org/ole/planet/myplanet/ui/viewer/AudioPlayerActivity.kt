package org.ole.planet.myplanet.ui.viewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

class AudioPlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filePath = intent.getStringExtra("TOUCHED_FILE")
        val isFullPath = intent.getBooleanExtra("isFullPath", false)
        val resourceTitle = intent.getStringExtra("RESOURCE_TITLE")

        setContent {
            val navController = rememberNavController()
            Surface(color = MaterialTheme.colorScheme.background) {
                NavHost(navController = navController, startDestination = "audio") {
                    composable("audio") {
                        AudioPlayerScreen(
                            filePath = filePath,
                            isFullPath = isFullPath,
                            resourceTitle = resourceTitle
                        )
                    }
                }
            }
        }
    }
}

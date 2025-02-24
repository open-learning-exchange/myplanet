package org.ole.planet.myplanet.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.ole.planet.myplanet.ui.onBoarding.OnBoardingActivity
import org.ole.planet.myplanet.utilities.DeepLinkManager

class DeepLinkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val deepLinkUri: Uri? = intent?.data
        val host = deepLinkUri?.host

        val deepLinkManager = DeepLinkManager(this)
        val allowedHosts = deepLinkManager.getAllowedHosts()

        if (allowedHosts.contains(host)) {
            Log.d("DeepLink", "✅ Valid deep link: $deepLinkUri")

            // Redirect to OnBoardingActivity with deep link data
            val mainIntent = Intent(this, OnBoardingActivity::class.java).apply {
                data = deepLinkUri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(mainIntent)
        } else {
            Log.e("DeepLink", "❌ Invalid deep link host: $host")
        }

        finish() // Close DeepLinkActivity immediately
    }
}
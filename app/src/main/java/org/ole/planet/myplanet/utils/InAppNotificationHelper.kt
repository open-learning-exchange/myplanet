package org.ole.planet.myplanet.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import java.lang.ref.WeakReference
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.ui.courses.CoursesFragment
import org.ole.planet.myplanet.ui.dashboard.DashboardActivity
import org.ole.planet.myplanet.ui.teams.TeamFragment

object InAppNotificationHelper {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentBannerRef: WeakReference<View>? = null

    fun showInAppNotification(context: Context, config: NotificationConfig) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            displayNotification(config)
        } else {
            mainHandler.post { displayNotification(config) }
        }
    }

    private fun displayNotification(config: NotificationConfig) {
        val activity = ActivityTracker.currentActivity ?: return
        if (activity.isFinishing || activity.isDestroyed) return

        // 1. Play chime audio
        playNotificationSound(activity)

        // 2. Play haptic vibration
        playVibration(activity)

        // 3. Show Heads-Up Banner on current Activity
        showBannerOnActivity(activity, config)
    }

    private fun playNotificationSound(context: Context) {
        try {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, soundUri)
            ringtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playVibration(context: Context) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(400)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showBannerOnActivity(activity: Activity, config: NotificationConfig) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        // Remove previous banner if any
        currentBannerRef?.get()?.let { oldBanner ->
            content.removeView(oldBanner)
        }

        val inflater = LayoutInflater.from(activity)
        val bannerView = inflater.inflate(R.layout.in_app_notification_banner, content, false)
        currentBannerRef = WeakReference(bannerView)

        val iconView = bannerView.findViewById<ImageView>(R.id.notification_icon)
        val titleView = bannerView.findViewById<TextView>(R.id.notification_title)
        val messageView = bannerView.findViewById<TextView>(R.id.notification_message)
        val actionBtn = bannerView.findViewById<MaterialButton>(R.id.notification_action_btn)
        val dismissBtn = bannerView.findViewById<ImageButton>(R.id.notification_dismiss_btn)

        titleView.text = config.title
        messageView.text = config.message
        iconView.setImageResource(getIconForType(config.type))

        val density = activity.resources.displayMetrics.density
        val topMargin = (24 * density).toInt()
        val sideMargin = (12 * density).toInt()

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            setMargins(sideMargin, topMargin, sideMargin, 0)
        }

        bannerView.layoutParams = params

        var autoDismissRunnable: Runnable? = null

        val dismiss = {
            autoDismissRunnable?.let { mainHandler.removeCallbacks(it) }
            bannerView.animate()
                .alpha(0f)
                .translationY(-80f * density)
                .setDuration(250)
                .withEndAction {
                    content.removeView(bannerView)
                    if (currentBannerRef?.get() == bannerView) {
                        currentBannerRef = null
                    }
                }
                .start()
        }

        val openTarget = {
            dismiss()
            handleNotificationClick(activity, config)
        }

        bannerView.setOnClickListener { openTarget() }
        actionBtn.setOnClickListener { openTarget() }
        dismissBtn.setOnClickListener { dismiss() }

        // Slide down animation
        bannerView.alpha = 0f
        bannerView.translationY = -80f * density
        content.addView(bannerView)

        bannerView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(300)
            .start()

        autoDismissRunnable = Runnable { dismiss() }
        mainHandler.postDelayed(autoDismissRunnable, 7500L)
    }

    private fun handleNotificationClick(activity: Activity, config: NotificationConfig) {
        when (config.type) {
            NotificationUtils.TYPE_TASK -> {
                val taskId = config.extras["taskId"] ?: config.relatedId
                if (activity is DashboardActivity) {
                    activity.openMyFragment(TeamFragment().apply {
                        arguments = Bundle().apply {
                            if (taskId != null) putString("taskId", taskId)
                        }
                    })
                } else {
                    val intent = Intent(activity, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("notification_type", NotificationUtils.TYPE_TASK)
                        putExtra("notification_id", config.id)
                        putExtra("from_notification", true)
                        if (taskId != null) putExtra("taskId", taskId)
                    }
                    activity.startActivity(intent)
                }
            }
            NotificationUtils.TYPE_MEETUP -> {
                val teamId = config.extras["teamId"]
                val meetupId = config.extras["meetupId"] ?: config.relatedId
                if (activity is DashboardActivity) {
                    activity.openMyFragment(TeamFragment().apply {
                        arguments = Bundle().apply {
                            if (teamId != null) putString("teamId", teamId)
                            if (meetupId != null) putString("meetupId", meetupId)
                        }
                    })
                } else {
                    val intent = Intent(activity, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("notification_type", NotificationUtils.TYPE_MEETUP)
                        putExtra("notification_id", config.id)
                        putExtra("from_notification", true)
                        if (teamId != null) putExtra("teamId", teamId)
                        if (meetupId != null) putExtra("meetupId", meetupId)
                    }
                    activity.startActivity(intent)
                }
            }
            NotificationUtils.TYPE_COURSE -> {
                val courseId = config.extras["courseId"] ?: config.relatedId
                if (activity is DashboardActivity) {
                    activity.openMyFragment(CoursesFragment().apply {
                        arguments = Bundle().apply {
                            if (courseId != null) putString("id", courseId)
                        }
                    })
                } else {
                    val intent = Intent(activity, DashboardActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("notification_type", NotificationUtils.TYPE_COURSE)
                        putExtra("notification_id", config.id)
                        putExtra("from_notification", true)
                        if (courseId != null) putExtra("courseId", courseId)
                    }
                    activity.startActivity(intent)
                }
            }
            else -> {
                val intent = Intent(activity, DashboardActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("notification_type", config.type)
                    putExtra("notification_id", config.id)
                    putExtra("from_notification", true)
                    config.extras.forEach { (k, v) -> putExtra(k, v) }
                }
                activity.startActivity(intent)
            }
        }
    }

    private fun getIconForType(type: String): Int = when (type) {
        NotificationUtils.TYPE_SURVEY -> R.drawable.survey
        NotificationUtils.TYPE_TASK -> R.drawable.team
        NotificationUtils.TYPE_MEETUP -> R.drawable.meetups
        NotificationUtils.TYPE_COURSE -> R.drawable.ourcourses
        NotificationUtils.TYPE_STORAGE -> android.R.drawable.stat_sys_warning
        NotificationUtils.TYPE_JOIN_REQUEST -> R.drawable.business
        NotificationUtils.TYPE_RESOURCE -> R.drawable.ourlibrary
        else -> R.drawable.ic_home
    }
}

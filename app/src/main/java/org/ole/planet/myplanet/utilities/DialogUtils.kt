package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.datamanager.MyDownloadService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.MyPlanet

object DialogUtils {
    @JvmStatic
    fun getProgressDialog(context: Context): ProgressDialog {
        val prgDialog = ProgressDialog(context)
        prgDialog.setTitle(context.getString(R.string.downloading_file))
        prgDialog.max = 100
        prgDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        prgDialog.setCancelable(false)
        prgDialog.setButton(DialogInterface.BUTTON_POSITIVE, context.getString(R.string.finish)) { _, _ -> prgDialog.dismiss() }
        prgDialog.setButton(DialogInterface.BUTTON_NEGATIVE, context.getString(R.string.stop_download)) { _, _ ->
            context.stopService(Intent(context, MyDownloadService::class.java))
        }
        return prgDialog
    }

    @JvmStatic
    fun showError(prgDialog: ProgressDialog?, message: String?) {
        prgDialog?.setTitle(message)
        prgDialog?.getButton(ProgressDialog.BUTTON_NEGATIVE)?.isEnabled = false
    }

    @JvmStatic
    fun showWifiSettingDialog(context: Context) {
        if (!NetworkUtils.isWifiBluetoothEnabled()) return
        showDialog(context)
    }

    private fun showDialog(context: Context) {
        if (MainApplication.syncFailedCount > 3) {
            val pd = AlertDialog.Builder(context)
            var message = if (NetworkUtils.isBluetoothEnabled()) "Bluetooth " else ""
            message += if (NetworkUtils.isWifiEnabled()) "Wifi " else ""
            message += context.getString(R.string.is_on_please_turn_of_to_save_battery)
            pd.setMessage(message)
            pd.setPositiveButton(context.getString(R.string.go_to_settings)) { _, _ ->
                MainApplication.syncFailedCount = 0
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                context.startActivity(intent)
            }.setNegativeButton(context.getString(R.string.cancel), null)
            pd.setCancelable(false)
            val d = pd.create()
            d.show()
        }
    }

    @JvmStatic
    fun showSnack(v: View?, s: String?) {
        if (v != null) {
            s?.let { Snackbar.make(v, it, Snackbar.LENGTH_LONG).show() }
        }
    }

    @JvmStatic
    fun showAlert(context: Context, title: String?, message: String?) {
        if (context is Activity && !(context as Activity).isFinishing) {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.finish, null)
                .show()
        }
    }

    @JvmStatic
    fun getAlertDialog(context: Context, message: String, pos: String, listener: DialogInterface.OnClickListener?): AlertDialog {
        return AlertDialog.Builder(context)
            .setMessage(message)
            .setIcon(R.drawable.courses)
            .setPositiveButton(pos, listener)
            .setNegativeButton("Cancel", null)
            .show()
    }

    @JvmStatic
    fun showCloseAlert(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    @JvmStatic
    fun getAlertDialog(context: Context, title: String, v: View): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(title)
            .setIcon(R.drawable.ic_edit)
            .setView(v)
            .setPositiveButton(R.string.submit, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun getUpdateDialog(context: Context, info: MyPlanet?, progressDialog: ProgressDialog?): AlertDialog.Builder {
        return AlertDialog.Builder(context)
            .setTitle(R.string.new_version_of_my_planet_available)
            .setMessage(R.string.download_first_to_continue)
            .setNeutralButton(R.string.upgrade_local) { _, _ ->
                startDownloadUpdate(context, Utilities.getApkUpdateUrl(info?.localapkpath), progressDialog)
            }
            .setPositiveButton(R.string.upgrade) { _, _ ->
                info?.apkpath?.let { startDownloadUpdate(context, it, progressDialog) }
            }
    }

    @JvmStatic
    fun startDownloadUpdate(context: Context, path: String, progressDialog: ProgressDialog?) {
        Service(MainApplication.context).checkCheckSum(object : Service.ChecksumCallback {
            override fun onMatch() {
                Utilities.toast(MainApplication.context, context.getString(R.string.apk_already_exists))
                FileUtils.installApk(context, path)
            }

            override fun onFail() {
                val url = ArrayList<String>()
                url.add(path)
                if (progressDialog != null) {
                    progressDialog.setMessage(context.getString(R.string.downloading_file))
                    progressDialog.setCancelable(false)
                    progressDialog.show()
                }
                Utilities.openDownloadService(context, url)
            }
        }, path)
    }
}

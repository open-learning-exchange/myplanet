package org.ole.planet.myplanet.utilities

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.MainApplication.Companion.context
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.DialogProgressBinding
import org.ole.planet.myplanet.datamanager.MyDownloadService
import org.ole.planet.myplanet.datamanager.Service
import org.ole.planet.myplanet.model.MyPlanet

object DialogUtils {
    @JvmStatic
    fun getProgressDialog(context: Context): CustomProgressDialog {
        val prgDialog = CustomProgressDialog(context)
        prgDialog.setTitle(context.getString(R.string.downloading_file))
        prgDialog.setMax(100)
        prgDialog.setPositiveButton(context.getString(R.string.finish), isVisible = true) {
            prgDialog.dismiss()
        }
        prgDialog.setNegativeButton(context.getString(R.string.stop_download), isVisible = true) {
            context.stopService(Intent(context, MyDownloadService::class.java))
            prgDialog.dismiss()
        }
        return prgDialog
    }

    @JvmStatic
    fun showError(prgDialog: CustomProgressDialog?, message: String?) {
        prgDialog?.setTitle(message)
        prgDialog?.disableNegativeButton()
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
    fun showAlert(context: Context?, title: String?, message: String?) {
        if (context is Activity && !context.isFinishing) {
            AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.finish, null)
                .show()
        }
    }

    @JvmStatic
    fun getAlertDialog(context: Context, message: String, pos: String, listener: DialogInterface.OnClickListener?): AlertDialog {
        return AlertDialog.Builder(ContextThemeWrapper(context, R.style.AlertDialogTheme))
            .setMessage(message)
            .setIcon(R.drawable.courses)
            .setPositiveButton(pos, listener)
            .setNegativeButton("Cancel", null)
            .show()
    }

    @JvmStatic
    fun showCloseAlert(context: Context, title: String?, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.close, null)
            .show()
    }

    @JvmStatic
    fun getAlertDialog(context: Context, title: String, v: View): AlertDialog {
        return AlertDialog.Builder(ContextThemeWrapper(context, R.style.AlertDialogTheme))
            .setTitle(title)
            .setIcon(R.drawable.ic_edit)
            .setView(v)
            .setPositiveButton(R.string.submit, null)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @JvmStatic
    fun getUpdateDialog(context: Context, info: MyPlanet?, progressDialog: CustomProgressDialog?): AlertDialog.Builder {
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
    fun startDownloadUpdate(context: Context, path: String, progressDialog: CustomProgressDialog?) {
        Service(MainApplication.context).checkCheckSum(object : Service.ChecksumCallback {
            override fun onMatch() {
                Utilities.toast(MainApplication.context, context.getString(R.string.apk_already_exists))
                FileUtils.installApk(context, path)
            }

            override fun onFail() {
                val url = arrayListOf(path)
                if (progressDialog != null) {
                    progressDialog.setText(context.getString(R.string.downloading_file))
                    progressDialog.setCancelable(false)
                    progressDialog.show()
                }
                Utilities.openDownloadService(context, url, false)
            }
        }, path)
    }

    @JvmStatic
    fun getCustomProgressDialog(context: Context): CustomProgressDialog {
        return CustomProgressDialog(context)
    }

    class CustomProgressDialog(context: Context) {
        private val binding: DialogProgressBinding = DialogProgressBinding.inflate(LayoutInflater.from(context))
        private val dialogBuilder: AlertDialog.Builder = AlertDialog.Builder(context)
        private val progressBar = binding.progressBar
        private val progressText = binding.progressText
        private val progressTitle = binding.progressTitle
        private var dialog: AlertDialog? = null
        private var positiveButtonAction: (() -> Unit)? = null
        private var negativeButtonAction: (() -> Unit)? = null

        init {
            dialogBuilder.setView(binding.root)
            dialogBuilder.setCancelable(false)
            binding.buttonPositive.setOnClickListener {
                positiveButtonAction?.invoke()
            }
            binding.buttonNegative.setOnClickListener {
                negativeButtonAction?.invoke()
            }
        }
        fun setPositiveButton(text: String, isVisible: Boolean = true, listener: () -> Unit) {
            binding.buttonPositive.text = text
            positiveButtonAction = listener
            binding.buttonPositive.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun setNegativeButton(text: String = "Cancel", isVisible: Boolean = true, listener: () -> Unit) {
            binding.buttonNegative.text = text
            negativeButtonAction = listener
            binding.buttonNegative.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun show() {
            if (dialog == null) {
                dialog = dialogBuilder.create()
            }
            dialog?.show()
        }

        fun dismiss() {
            dialog?.dismiss()
        }

        fun setCancelable(state: Boolean) {
            dialog?.setCancelable(state)
        }

        private fun setIndeterminate() {
            progressBar.isIndeterminate = false
        }

        fun setMax(maxValue: Int) {
            progressBar.max = maxValue
        }

        fun setProgress(value: Int) {
            setIndeterminate()
            progressBar.progress = value
        }

        fun setText(text: String) {
            progressText.text = text
            progressText.visibility = View.VISIBLE
        }

        fun setTitle(text: String?) {
            progressTitle.visibility = View.VISIBLE
            progressTitle.text = text
        }
        fun isShowing(): Boolean {
            return dialog?.isShowing ?: false
        }

        fun disableNegativeButton() {
            binding.buttonNegative.isEnabled = false
        }
    }
}

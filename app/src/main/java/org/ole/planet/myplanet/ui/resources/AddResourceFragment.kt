package org.ole.planet.myplanet.ui.resources

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.realm.Realm
import org.ole.planet.myplanet.MainApplication
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.AlertSoundRecorderBinding
import org.ole.planet.myplanet.databinding.FragmentAddResourceBinding
import org.ole.planet.myplanet.datamanager.DatabaseService
import org.ole.planet.myplanet.model.RealmMyPersonal
import org.ole.planet.myplanet.service.AudioRecorderService
import org.ole.planet.myplanet.service.AudioRecorderService.AudioRecordListener
import org.ole.planet.myplanet.service.UserProfileDbHandler
import org.ole.planet.myplanet.ui.myPersonals.MyPersonalsFragment
import org.ole.planet.myplanet.utilities.Utilities
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID
import androidx.core.graphics.drawable.toDrawable

class AddResourceFragment : BottomSheetDialogFragment() {
    private lateinit var fragmentAddResourceBinding: FragmentAddResourceBinding
    var tvTime: TextView? = null
    var floatingActionButton: FloatingActionButton? = null
    private var audioRecorderService: AudioRecorderService? = null
    private var myPersonalsFragment: MyPersonalsFragment? = null
    private var photoURI: Uri? = null
    private var videoUri: Uri? = null
    private lateinit var captureImageLauncher: ActivityResultLauncher<Uri>
    private lateinit var captureVideoLauncher: ActivityResultLauncher<Uri>
    private lateinit var openFolderLauncher: ActivityResultLauncher<String>
    private lateinit var requestCameraLauncher: ActivityResultLauncher<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            type = requireArguments().getInt("type", 0)
        }

        captureImageLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                startIntent(photoURI, REQUEST_CAPTURE_PICTURE)
            }
        }

        captureVideoLauncher = registerForActivityResult(ActivityResultContracts.CaptureVideo()) { isSuccess ->
            if (isSuccess) {
                startIntent(videoUri, REQUEST_VIDEO_CAPTURE)
            }
        }

        openFolderLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                startIntent(uri, REQUEST_FILE_SELECTION)
            } else {
                Utilities.toast(activity, "no file selected")
            }
        }
        audioRecorderService = AudioRecorderService()
        audioRecorderService?.setCaller(this, requireContext())
        requestCameraLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                takePhoto()
            } else {
                if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        .setTitle(R.string.permission_required)
                        .setMessage(R.string.camera_permission_required)
                        .setPositiveButton(R.string.settings) { dialog, _ ->
                            dialog.dismiss()
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri: Uri = Uri.fromParts("package", requireContext().packageName, null)
                            intent.data = uri
                            startActivity(intent)
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
                        .show()
                } else {
                    Utilities.toast(requireContext(), "camera permission is required.")
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val bottomSheetDialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        bottomSheetDialog.setOnShowListener { d: DialogInterface ->
            val dialog = d as BottomSheetDialog
            val bottomSheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
                BottomSheetBehavior.from(it).skipCollapsed = true
                BottomSheetBehavior.from(it).setHideable(true)
            }
        }
        return bottomSheetDialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        fragmentAddResourceBinding = FragmentAddResourceBinding.inflate(inflater, container, false)
        fragmentAddResourceBinding.llRecordVideo.setOnClickListener { dispatchTakeVideoIntent() }
        fragmentAddResourceBinding.llRecordAudio.setOnClickListener { showAudioRecordAlert() }
        fragmentAddResourceBinding.llCaptureImage.setOnClickListener { takePhoto() }
        fragmentAddResourceBinding.llDraft.setOnClickListener { openFolderLauncher.launch("*/*") }
        return fragmentAddResourceBinding.root
    }

    private fun showAudioRecordAlert() {
        val alertSoundRecorderBinding = AlertSoundRecorderBinding.inflate(LayoutInflater.from(activity))
        tvTime = alertSoundRecorderBinding.tvTime
        floatingActionButton = alertSoundRecorderBinding.fabRecord
        val titleTextView = TextView(requireContext()).apply {
            text = resources.getString(R.string.record_audio)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.daynight_textColor))
            textSize = 20f
            setPadding(20, 20, 20, 20)
            gravity = Gravity.CENTER
        }
        val dialog = AlertDialog.Builder(requireActivity())
            .setCustomTitle(titleTextView)
            .setView(alertSoundRecorderBinding.root)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getColor(
                requireContext(),
                R.color.card_bg
            ).toDrawable())

        createAudioRecorderService(dialog)
        alertSoundRecorderBinding.fabRecord.setOnClickListener { audioRecorderService?.onRecordClicked() }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss)) { _: DialogInterface?, _: Int ->
            if (audioRecorderService != null && audioRecorderService?.isRecording() == true) {
                audioRecorderService?.forceStop()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun createAudioRecorderService(dialog: AlertDialog) {
        audioRecorderService?.setAudioRecordListener(object : AudioRecordListener {
            override fun onRecordStarted() {
                tvTime?.setText(R.string.recording_audio)
                floatingActionButton?.setImageResource(R.drawable.ic_stop)
            }

            override fun onRecordStopped(outputFile: String?) {
                tvTime?.text = getString(R.string.empty_text)
                dialog.dismiss()
                audioStartIntent(outputFile)
                floatingActionButton?.setImageResource(R.drawable.ic_mic)
            }

            override fun onError(error: String?) {
                Utilities.toast(activity, error)
            }
        })
    }

    private fun dispatchTakeVideoIntent() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED){
            requestCameraLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        videoUri = createVideoFileUri()
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
        captureVideoLauncher.launch(videoUri)
    }

    private fun createVideoFileUri(): Uri? {
        val values = ContentValues()
        values.put(MediaStore.Video.Media.TITLE, "Video_" + UUID.randomUUID().toString())
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ole/video")
        }
        videoUri = requireActivity().contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        return videoUri
    }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED){
            requestCameraLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, "Photo_" + UUID.randomUUID().toString())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ole/photo")
            }
        }
        photoURI = requireActivity().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        photoURI?.let { captureImageLauncher.launch(it) }
    }

    private fun startIntent(uri: Uri?, requestCode: Int) {
        var path: String? = null
        if (requestCode == REQUEST_CAPTURE_PICTURE || requestCode == REQUEST_VIDEO_CAPTURE) {
            path = getRealPathFromUri(uri)
        } else if (requestCode == REQUEST_FILE_SELECTION) {
            path = getPathFromURI(uri)
        }
        if (!path.isNullOrEmpty()) {
            addResource(path)
        } else {
            Utilities.toast(activity, getString(R.string.invalid_resource_url))
        }
    }

    private fun audioStartIntent(path: String?) {
        if (!TextUtils.isEmpty(path)) {
            addResource(path)
        } else {
            Utilities.toast(activity, getString(R.string.invalid_resource_url))
        }
    }

    private fun getRealPathFromUri(uri: Uri?): String {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        if (uri != null) {
            requireActivity().contentResolver.query(uri, projection, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    return cursor.getString(columnIndex)
                }
            }
        }
        return ""
    }

    private fun getPathFromURI(uri: Uri?): String? {
        var filePath: String? = null
        if (uri != null) {
            val scheme = uri.scheme
            if (scheme == "content") {
                val cursor: Cursor? = requireActivity().contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                        val fileName = it.getString(columnIndex)
                        val cacheDir = requireActivity().cacheDir
                        val destinationFile = File(cacheDir, fileName)
                        copyFile(uri, destinationFile)
                        filePath = destinationFile.absolutePath
                    }
                }
            } else if (scheme == "file") {
                filePath = uri.path
            }
        }
        return filePath
    }

    private fun copyFile(sourceUri: Uri, destinationFile: File) {
        requireActivity().contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }


    private fun addResource(path: String?) {
        if (type == 0) {
            startActivity(Intent(activity, AddResourceActivity::class.java).putExtra("resource_local_url", path))
        } else {
            showAlert(requireContext(), path)
        }
    }

    fun setMyPersonalsFragment(myPersonalsFragment: MyPersonalsFragment) {
        this.myPersonalsFragment = myPersonalsFragment
    }

    companion object {
        const val REQUEST_VIDEO_CAPTURE = 1
        const val REQUEST_CAPTURE_PICTURE = 2
        const val REQUEST_FILE_SELECTION = 3
        var type = 0
        private val myPersonalsFragment: MyPersonalsFragment? = null
        fun showAlert(context: Context, path: String?) {
            val v = LayoutInflater.from(context).inflate(R.layout.alert_my_personal, null)
            val etTitle = v.findViewById<EditText>(R.id.et_title)
            val etDesc = v.findViewById<EditText>(R.id.et_description)
            val realmUserModel = UserProfileDbHandler(MainApplication.context).userModel!!
            val userId = realmUserModel.id
            val userName = realmUserModel.name
            AlertDialog.Builder(context, R.style.AlertDialogTheme)
                .setTitle(R.string.enter_resource_detail)
                .setView(v)
                .setPositiveButton("Save") { _: DialogInterface?, _: Int ->
                    val title = etTitle.text.toString().trim { it <= ' ' }
                    if (title.isEmpty()) {
                        Utilities.toast(context, R.string.title_is_required.toString())
                        return@setPositiveButton
                    }
                    val desc = etDesc.text.toString().trim { it <= ' ' }
                    val realm = DatabaseService(context).realmInstance
                    realm.executeTransactionAsync(
                        Realm.Transaction { realm1: Realm -> val myPersonal = realm1.createObject(RealmMyPersonal::class.java, UUID.randomUUID().toString())
                            myPersonal.title = title
                            myPersonal.userId = userId
                            myPersonal.userName = userName
                            myPersonal.path = path
                            myPersonal.date = Date().time
                            myPersonal.description = desc
                        },
                        Realm.Transaction.OnSuccess {
                            Utilities.toast(MainApplication.context, context.getString(R.string.resource_saved_to_my_personal))
                        })
                    if (type == 1) {
                        myPersonalsFragment?.refreshFragment()
                    }
                }.setNegativeButton(R.string.dismiss, null).show()
        }
    }
}

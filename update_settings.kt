import java.io.File

fun main() {
    val file = File("app/src/main/java/org/ole/planet/myplanet/ui/settings/SettingsActivity.kt")
    var content = file.readText()

    // Add imports
    val importsToAdd = """
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
""".trimIndent()

    if (!content.contains("import androidx.lifecycle.Lifecycle")) {
        content = content.replaceFirst("import androidx.lifecycle.Observer", "import androidx.lifecycle.Observer\n" + importsToAdd)
    }

    val target = """
                            val liveData = workManager.getWorkInfoByIdLiveData(freeSpaceWork.id)
                            liveData.observe(viewLifecycleOwner, object : Observer<WorkInfo?> {
                                override fun onChanged(value: WorkInfo?) {
                                    val workInfo = value
                                    if (workInfo != null) {
                                        when (workInfo.state) {
                                            WorkInfo.State.RUNNING -> {
                                                val progress = workInfo.progress
                                                val deletedFiles = progress.getInt("deletedFiles", 0)
                                                val freedBytes = progress.getLong("freedBytes", 0)
                                                dialog.setText("Deleting files... ${'$'}deletedFiles deleted (${'$'}{FileUtils.formatSize(requireContext(), freedBytes)})")
                                            }
                                            WorkInfo.State.SUCCEEDED -> {
                                                dialog.dismiss()
                                                Utilities.toast(requireActivity(), getString(R.string.data_cleared))
                                                val output = workInfo.outputData
                                                val deletedFiles = output.getInt("deletedFiles", 0)
                                                val freedBytes = output.getLong("freedBytes", 0)
                                                Utilities.toast(requireActivity(), "Freed ${'$'}{FileUtils.formatSize(requireContext(), freedBytes)} (${'$'}deletedFiles files)")
                                            }
                                            WorkInfo.State.FAILED -> {
                                                dialog.dismiss()
                                                Utilities.toast(requireActivity(), getString(R.string.unable_to_clear_files))
                                            }
                                            WorkInfo.State.CANCELLED -> {
                                                dialog.dismiss()
                                            }
                                            else -> {
                                                // ENQUEUED or BLOCKED
                                            }
                                        }
                                        if (workInfo.state.isFinished) {
                                            liveData.removeObserver(this)
                                        }
                                    }
                                }
                            })
""".trimIndent()

    val replacement = """
                            viewLifecycleOwner.lifecycleScope.launch {
                                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    workManager.getWorkInfoByIdFlow(freeSpaceWork.id).collect { workInfo ->
                                        if (workInfo != null) {
                                            when (workInfo.state) {
                                                WorkInfo.State.RUNNING -> {
                                                    val progress = workInfo.progress
                                                    val deletedFiles = progress.getInt("deletedFiles", 0)
                                                    val freedBytes = progress.getLong("freedBytes", 0)
                                                    dialog.setText("Deleting files... ${'$'}deletedFiles deleted (${'$'}{FileUtils.formatSize(requireContext(), freedBytes)})")
                                                }
                                                WorkInfo.State.SUCCEEDED -> {
                                                    dialog.dismiss()
                                                    Utilities.toast(requireActivity(), getString(R.string.data_cleared))
                                                    val output = workInfo.outputData
                                                    val deletedFiles = output.getInt("deletedFiles", 0)
                                                    val freedBytes = output.getLong("freedBytes", 0)
                                                    Utilities.toast(requireActivity(), "Freed ${'$'}{FileUtils.formatSize(requireContext(), freedBytes)} (${'$'}deletedFiles files)")
                                                }
                                                WorkInfo.State.FAILED -> {
                                                    dialog.dismiss()
                                                    Utilities.toast(requireActivity(), getString(R.string.unable_to_clear_files))
                                                }
                                                WorkInfo.State.CANCELLED -> {
                                                    dialog.dismiss()
                                                }
                                                else -> {
                                                    // ENQUEUED or BLOCKED
                                                }
                                            }
                                        }
                                    }
                                }
                            }
""".trimIndent()

    // Wait, the original string might have slightly different formatting (e.g. `$deletedFiles` vs `${'$'}deletedFiles`).
    // Let's use regex or standard string replacement without hardcoding the whole block.
}

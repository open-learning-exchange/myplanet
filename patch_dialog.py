import re

with open("app/src/main/java/org/ole/planet/myplanet/base/DownloadDialogHelper.kt", "r") as f:
    text = f.read()

import_block = """import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import java.util.WeakHashMap
import androidx.fragment.app.Fragment"""

if "import java.util.WeakHashMap" not in text:
    text = text.replace("import androidx.lifecycle.lifecycleScope", import_block + "\nimport androidx.lifecycle.lifecycleScope")

if "private val dialogMap" not in text:
    text = text.replace("fun BaseResourceFragment.showDownloadDialog", "private val dialogMap = WeakHashMap<Fragment, AlertDialog>()\n\nfun BaseResourceFragment.showDownloadDialog")

text = text.replace("val downloadSuggestionDialog = alertDialogBuilder.create()", """dialogMap[this]?.dismiss()
        val downloadSuggestionDialog = alertDialogBuilder.create()
        dialogMap[this] = downloadSuggestionDialog

        downloadSuggestionDialog.setOnDismissListener {
            dialogMap.remove(this)
        }

        this.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                dialogMap[this@showDownloadDialog]?.dismiss()
                dialogMap.remove(this@showDownloadDialog)
            }
        })""")

with open("app/src/main/java/org/ole/planet/myplanet/base/DownloadDialogHelper.kt", "w") as f:
    f.write(text)

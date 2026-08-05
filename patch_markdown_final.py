import re

with open("app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt", "r") as f:
    content = f.read()

content = content.replace(
"""import androidx.fragment.app.viewModels
import androidx.fragment.app.viewModels""",
"""import androidx.fragment.app.viewModels"""
)

content = content.replace(
"""                    context.getString(R.string.sync) -> {
                        (activity as DashboardElementActivity).logSyncInSharedPrefs()
                    }""",
"""                    context.getString(R.string.sync) -> {
                        viewModel.logSyncInSharedPrefs(activity as DashboardElementActivity)
                    }"""
)


with open("app/src/main/java/org/ole/planet/myplanet/ui/components/MarkdownDialogFragment.kt", "w") as f:
    f.write(content)

import re

filepath = './app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Add lifecycleScope and launch imports back
import_lifecycle = "import androidx.lifecycle.lifecycleScope\nimport kotlinx.coroutines.launch\n"
if "import androidx.lifecycle.lifecycleScope" not in content:
    content = content.replace("import androidx.fragment.app.Fragment\n", "import androidx.fragment.app.Fragment\n" + import_lifecycle)

# Wrap inside lifecycleScope
old_block = """        val userName = userSessionManager.getUserModel()?.name ?: return
        collectLatestWhenStarted(activitiesRepository.getOfflineLogins(userName)) { logins ->
            val monthlyCounts = computeMonthlyCounts(logins, startMillis, endMillis)
            renderChart(monthlyCounts, daynightTextColor)
        }"""

new_block = """        viewLifecycleOwner.lifecycleScope.launch {
            val userName = userSessionManager.getUserModel()?.name ?: return@launch
            collectLatestWhenStarted(activitiesRepository.getOfflineLogins(userName)) { logins ->
                val monthlyCounts = computeMonthlyCounts(logins, startMillis, endMillis)
                renderChart(monthlyCounts, daynightTextColor)
            }
        }"""

content = content.replace(old_block, new_block)

with open(filepath, 'w') as f:
    f.write(content)

print("Patched.")

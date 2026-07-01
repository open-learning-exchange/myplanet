import re

filepath = './app/src/main/java/org/ole/planet/myplanet/ui/dashboard/ActivitiesFragment.kt'
with open(filepath, 'r') as f:
    content = f.read()

# Remove unused imports
content = content.replace("import androidx.lifecycle.lifecycleScope\n", "")
content = content.replace("import kotlinx.coroutines.flow.collectLatest\n", "")
content = content.replace("import kotlinx.coroutines.launch\n", "")

# Replace the block
old_block = """        viewLifecycleOwner.lifecycleScope.launch {
            val userName = userSessionManager.getUserModel()?.name ?: return@launch
            collectLatestWhenStarted(activitiesRepository.getOfflineLogins(userName)) { logins ->
                val monthlyCounts = computeMonthlyCounts(logins, startMillis, endMillis)
                renderChart(monthlyCounts, daynightTextColor)
            }
        }"""

new_block = """        val userName = userSessionManager.getUserModel()?.name ?: return
        collectLatestWhenStarted(activitiesRepository.getOfflineLogins(userName)) { logins ->
            val monthlyCounts = computeMonthlyCounts(logins, startMillis, endMillis)
            renderChart(monthlyCounts, daynightTextColor)
        }"""

content = content.replace(old_block, new_block)

with open(filepath, 'w') as f:
    f.write(content)

print("Patched.")

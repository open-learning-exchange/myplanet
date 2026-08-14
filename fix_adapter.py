with open('app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt', 'r') as f:
    content = f.read()

import re

# 1. Add import for DispatcherProvider
if 'import org.ole.planet.myplanet.utils.DispatcherProvider' not in content:
    content = content.replace('import org.ole.planet.myplanet.utils.Utilities', 'import org.ole.planet.myplanet.utils.DispatcherProvider\nimport org.ole.planet.myplanet.utils.Utilities')

# 2. Fix fully-qualified name in constructor
content = content.replace('private val dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider', 'private val dispatcherProvider: DispatcherProvider')

# 3. Remove rawDate and use formattedDate
content = content.replace('val rawDate: String,\n        val formattedDate: String', 'val formattedDate: String')
content = content.replace('rawDate = formattedDate,\n                    formattedDate = formattedDate', 'formattedDate = formattedDate')
content = content.replace('binding.txtDate.tag = item.rawDate', 'binding.txtDate.tag = item.formattedDate')

# 4. Fix hasEncryptedData logic
content = content.replace('hasEncryptedData = encrypted != null && encrypted.keySet().isNotEmpty()', 'hasEncryptedData = encrypted != null')

# 5. Fix indentation on lazy colors
old_colors = """    private val colorGrey50 by lazy { ContextCompat.getColor(context, R.color.md_grey_50) }
    private val colorGreen50 by lazy { ContextCompat.getColor(context, R.color.md_green_50) }
    private val colorMultiSelectGrey by lazy { ContextCompat.getColor(context, R.color.multi_select_grey) }"""
new_colors = """    private val colorGrey50 by lazy { ContextCompat.getColor(context, R.color.md_grey_50) }
    private val colorGreen50 by lazy { ContextCompat.getColor(context, R.color.md_green_50) }
    private val colorMultiSelectGrey by lazy { ContextCompat.getColor(context, R.color.multi_select_grey) }"""

with open('app/src/main/java/org/ole/planet/myplanet/ui/health/HealthExaminationAdapter.kt', 'w') as f:
    f.write(content)

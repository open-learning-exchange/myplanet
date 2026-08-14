with open('app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt', 'r') as f:
    content = f.read()

import re

# 1. Add import for DispatcherProvider
if 'import org.ole.planet.myplanet.utils.DispatcherProvider' not in content:
    content = content.replace('import org.ole.planet.myplanet.utils.Utilities', 'import org.ole.planet.myplanet.utils.DispatcherProvider\nimport org.ole.planet.myplanet.utils.Utilities')

# 2. Fix fully-qualified name and line breaking
content = content.replace('@Inject lateinit var dispatcherProvider: org.ole.planet.myplanet.utils.DispatcherProvider', '@Inject\n    lateinit var dispatcherProvider: DispatcherProvider')

with open('app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt', 'w') as f:
    f.write(content)

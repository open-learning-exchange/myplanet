import re

with open('app/src/test/java/org/ole/planet/myplanet/services/AutoSyncWorkerTest.kt', 'r') as f:
    content = f.read()

# Well, wait. AutoSyncWorkerTest is not existing, let's create it.

file_path = 'app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

import re
match = re.search(r'transactionSyncManager = TransactionSyncManager\(.*?\)', content, re.DOTALL)
if match:
    print(match.group(0))

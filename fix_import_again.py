import re

def fix_file(filepath):
    with open(filepath, 'r') as f:
        content = f.read()

    # Move import below package statement
    if "import org.ole.planet.myplanet.repository.TeamSyncRepository\npackage " in content:
        content = content.replace("import org.ole.planet.myplanet.repository.TeamSyncRepository\npackage ", "package ")

        # insert import below package statement
        lines = content.split('\n')
        for i, line in enumerate(lines):
            if line.startswith("package "):
                lines.insert(i + 1, "\nimport org.ole.planet.myplanet.repository.TeamSyncRepository\n")
                break

        content = '\n'.join(lines)

        with open(filepath, 'w') as f:
            f.write(content)

fix_file("./app/src/main/java/org/ole/planet/myplanet/services/sync/TransactionSyncManager.kt")
fix_file("./app/src/main/java/org/ole/planet/myplanet/services/upload/UploadConfigs.kt")

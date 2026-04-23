import subprocess
print(subprocess.check_output(['git', 'log', '-p', '-1', 'app/src/main/java/org/ole/planet/myplanet/ui/sync/ProcessUserDataActivity.kt']).decode('utf-8'))

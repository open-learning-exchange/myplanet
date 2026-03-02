with open("app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt", 'r') as f:
    print([line for line in f.readlines() if "class BaseDashboardFragment" in line])

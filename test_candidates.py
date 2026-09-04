import os

files_to_check = [
    "app/src/main/java/org/ole/planet/myplanet/ui/teams/members/MembersFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/teams/members/RequestsFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/teams/tasks/TeamsTasksFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/teams/voices/TeamsVoicesFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/voices/VoicesFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/courses/CoursesFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesFragment.kt",
    "app/src/main/java/org/ole/planet/myplanet/base/BaseDashboardFragment.kt"
]

for filepath in files_to_check:
    print(f"--- {filepath} ---")
    os.system(f"grep -rn '.size' {filepath}")

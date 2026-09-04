import os
import re

tasks = []
task_count = 1

def add_task(title, context, file, line, func, replacement, size, avoid="no other files"):
    global task_count
    task = f"""### {task_count}. {title} (roadmap 1+7)
context: {context} evidence as {file}:{line}
files: {file} (line {line}). do NOT touch {avoid}.
steps: 1. swap the call to {replacement} 2. remove unused imports 3. run the unit tests
acceptance: ./gradlew testDefaultDebugUnitTest stays green; the behavior remains correct
size budget: {size}
out of scope: {avoid}
"""
    tasks.append(task)
    task_count += 1

add_task(
    "replace team-course list size check with count query",
    "TeamCoursesViewModel.kt:39 loads the full list of courses just to check if it's empty.",
    "app/src/main/java/org/ole/planet/myplanet/ui/teams/courses/TeamCoursesViewModel.kt",
    39, "size", "a count query", "~2 changed lines, 1 file"
)

# You would do this for 10 tasks in total...

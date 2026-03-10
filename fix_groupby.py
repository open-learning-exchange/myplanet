import re

file_path = "app/src/main/java/org/ole/planet/myplanet/repository/CoursesRepositoryImpl.kt"
with open(file_path, "r") as f:
    content = f.read()

pattern = r"val submissionsByExamId = submissions\.groupBy \{ sub ->\s*examIds\.firstOrNull \{ examId -> sub\.parentId\?\.contains\(examId\) == true \}\s*\}"

replacement = """val submissionsByExamId = submissions.groupBy { sub ->
            val bareId = sub.parentId?.split("@")?.get(0) ?: sub.parentId
            examIds.firstOrNull { it == bareId }
        }"""

if re.search(pattern, content):
    new_content = re.sub(pattern, replacement, content, count=1)
    with open(file_path, "w") as f:
        f.write(new_content)
    print("Replacement successful")
else:
    print("Pattern not found!")

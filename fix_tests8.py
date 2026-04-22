file_path = 'app/src/test/java/org/ole/planet/myplanet/repository/TeamsRepositoryImplTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

# Let's see where the constructor is called
import re
match = re.search(r'repository = TeamsRepositoryImpl\(.*?\)', content, re.DOTALL)
if match:
    print(match.group(0))

file_path = 'app/src/test/java/org/ole/planet/myplanet/repository/UserRepositoryImplTest.kt'
with open(file_path, 'r') as file:
    content = file.read()

content = content.replace(
    'spyRepository.saveUser(any(), any(), any(), any())',
    'spyRepository.saveUser(any(), any(), any())'
)
content = content.replace(
    'repository.saveUser(jsonDoc, mockk())',
    'repository.saveUser(jsonDoc)'
)

with open(file_path, 'w') as file:
    file.write(content)

import os

repo_files = []
for root, _, files in os.walk('app/src/main/java/org/ole/planet/myplanet/repository'):
    for file in files:
        if file.endswith('.kt') and 'Impl' in file:
            repo_files.append(os.path.join(root, file))

for file in repo_files:
    with open(file, 'r') as f:
        content = f.read()
        if 'RealmRepository(' in content and '@RealmDispatcher' not in content:
            print(f"Missing @RealmDispatcher in {file}")

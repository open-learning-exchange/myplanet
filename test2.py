import os
for root, dirs, files in os.walk("app/src/main/java/org/ole/planet/myplanet/"):
    for file in files:
        if file.endswith(".kt"):
            with open(os.path.join(root, file), 'r') as f:
                content = f.read()
                if "BaseResourceFragment" in content:
                    print(file)

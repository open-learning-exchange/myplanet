voices_impl = 'app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt'
with open(voices_impl, 'r') as f:
    c = f.read()
if 'insertNewsToRealm(realm, jsonDoc)\n        }\n    }' in c:
    c = c.replace('insertNewsToRealm(realm, jsonDoc)\n        }\n    }', 'insertNewsToRealm(realm, jsonDoc)\n        }\n        saveConcatenatedLinksToPrefs()\n    }')
    with open(voices_impl, 'w') as f: f.write(c)

print("Fixed methods2")

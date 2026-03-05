import re

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", "r") as f:
    text = f.read()

text = text.replace("""    protected fun requireRealmInstance(): Realm {
        if (!isRealmInitialized()) {
            // mRealm initialized in onViewCreated
        }
        return mRealm
    }""", """    protected fun requireRealmInstance(): Realm {
        if (!isRealmInitialized()) {
            mRealm = databaseService.createManagedRealmInstance()
        }
        return mRealm
    }""")

with open("app/src/main/java/org/ole/planet/myplanet/base/BaseResourceFragment.kt", "w") as f:
    f.write(text)

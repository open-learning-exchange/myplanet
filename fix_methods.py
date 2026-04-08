voices_impl = 'app/src/main/java/org/ole/planet/myplanet/repository/VoicesRepositoryImpl.kt'
with open(voices_impl, 'r') as f:
    c = f.read()
if 'org.ole.planet.myplanet.model.RealmNews.insert(realm, jsonDoc)' in c:
    c = c.replace('org.ole.planet.myplanet.model.RealmNews.insert(realm, jsonDoc)', 'insertNewsToRealm(realm, jsonDoc)')
    with open(voices_impl, 'w') as f: f.write(c)

feedback_impl = 'app/src/main/java/org/ole/planet/myplanet/repository/FeedbackRepositoryImpl.kt'
with open(feedback_impl, 'r') as f:
    c = f.read()
if 'org.ole.planet.myplanet.model.RealmFeedback.insert(realm, jsonDoc)' in c:
    c = c.replace('org.ole.planet.myplanet.model.RealmFeedback.insert(realm, jsonDoc)', 'insertFeedbackToRealm(realm, jsonDoc)')
    with open(feedback_impl, 'w') as f: f.write(c)

print("Fixed methods")

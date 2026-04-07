import re

test_file = 'app/src/test/java/org/ole/planet/myplanet/services/sync/TransactionSyncManagerTest.kt'
with open(test_file, 'r') as f:
    c = f.read()

mocks = """
    private val tagsRepository: org.ole.planet.myplanet.repository.TagsRepository = mockk()
    private val ratingsRepository: org.ole.planet.myplanet.repository.RatingsRepository = mockk()
    private val submissionsRepository: org.ole.planet.myplanet.repository.SubmissionsRepository = mockk()
    private val coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository = mockk()
    private val communityRepository: org.ole.planet.myplanet.repository.CommunityRepository = mockk()
    private val healthRepository: org.ole.planet.myplanet.repository.HealthRepository = mockk()
    private val progressRepository: org.ole.planet.myplanet.repository.ProgressRepository = mockk()
    private val surveysRepository: org.ole.planet.myplanet.repository.SurveysRepository = mockk()
"""

# Insert mocks
if 'private val tagsRepository' not in c:
    c = c.replace('private val testDispatcher', mocks + '    private val testDispatcher')

# Update constructor call
c = c.replace(
    'notificationsRepository,\n            testScope',
    'notificationsRepository,\n            tagsRepository,\n            ratingsRepository,\n            submissionsRepository,\n            coursesRepository,\n            communityRepository,\n            healthRepository,\n            progressRepository,\n            surveysRepository,\n            testScope'
)

with open(test_file, 'w') as f:
    f.write(c)

print("Fixed again")

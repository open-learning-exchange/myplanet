Wait, modifying 15 repositories AND injecting them into `TransactionSyncManager`? `TransactionSyncManager` currently has:
```kotlin
@Singleton
class TransactionSyncManager @Inject constructor(
    private val apiInterface: ApiInterface,
    private val databaseService: DatabaseService,
    @param:ApplicationContext private val context: Context,
    private val voicesRepository: org.ole.planet.myplanet.repository.VoicesRepository,
    private val chatRepository: ChatRepository,
    private val feedbackRepository: FeedbackRepository,
    private val sharedPrefManager: SharedPrefManager,
    private val userRepository: UserRepository,
    private val coursesRepository: org.ole.planet.myplanet.repository.CoursesRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
)
```
If I add 10 more repositories, the constructor will have 20 arguments! That's fine.
Let's see if there's a simpler way to satisfy the reviewer without modifying 15 repositories.
"The right fix is to either expose the generic bulk-insert through its own interface/method, or inject a RealmRepository instance separately for the generic fallback."
This sentence literally says "The right fix is to... expose the generic bulk-insert... or inject a RealmRepository".
Wait! Maybe the reviewer is telling me that I CAN keep the reflection, AS LONG AS I put it in its own interface or inject `RealmRepository` separately?
But then in point 2, the reviewer says:
"Reflection dispatch is still present, just moved RealmRepository.bulkInsertFromSync still uses Constants.classList[table]?.methods reflection to call insert. The stated goal is "refactor to domain repositories" but the generic fallback path is just relocated reflection. Tables not explicitly handled by the when block (e.g. resources, courses, ratings) still go through callMethod-equivalent reflection. This is the core architectural risk (silent failure on reflection error → e.printStackTrace() only) and was not addressed."

The reviewer explicitly calls it "the core architectural risk" and says it "was not addressed". This implies I MUST address it by eliminating reflection.
But wait! How do I eliminate reflection for 15 classes without modifying 15 files?
Maybe I can add `inline fun <reified T : RealmObject> bulkInsertFromSync(...)` to `RealmRepository`?
If I use inline functions, I don't need reflection!
```kotlin
inline fun <reified T : RealmObject> Realm.bulkInsert(jsonArray: JsonArray, crossinline inserter: (Realm, JsonObject) -> Unit) {
    // ...
}
```
But I still need a `when(table)` block in `TransactionSyncManager` that maps to the correct generic parameter or `inserter`.
If I put a giant `when` block in `TransactionSyncManager` anyway, I might as well just inject all 15 repositories, OR just call the `inserter` directly in `TransactionSyncManager` without injecting repositories!
Wait, the instruction says: "Add a `bulkInsertFromSync(realm: Realm, jsonArray: JsonArray)` method to the appropriate domain repositories for generic document insertion"
This confirms the prompt WANTS ME TO ADD IT TO EVERY REPOSITORY!
Because it says "appropriate domain repositories" plural!
Let me list the repositories and the tables they handle:

| Table | Realm Model | Repository |
|-------|-------------|------------|
| `news` | `RealmNews` | `VoicesRepository` |
| `tags` | `RealmTag` | `TagsRepository` |
| `login_activities` | `RealmOfflineActivity` | `ActivitiesRepository` |
| `ratings` | `RealmRating` | `RatingsRepository` |
| `submissions` | `RealmSubmission` | `SubmissionsRepository` |
| `courses` | `RealmMyCourse` | `CoursesRepository` |
| `achievements` | `RealmAchievement` | `UserRepository` (or `AchievementsRepository` if it exists. Wait, I checked `ls` and `AchievementsRepository` DOES NOT exist! So `UserRepository` handles it, as seen in `markAchievementUploaded`) |
| `feedback` | `RealmFeedback` | `FeedbackRepository` |
| `teams` | `RealmMyTeam` | `TeamsRepository` |
| `tasks` | `RealmTeamTask` | `TeamsRepository` (Tasks are team tasks) |
| `meetups` | `RealmMeetup` | `CommunityRepository` or `EventsRepository`? Wait, `MeetupsRepository` doesn't exist. `CommunityRepository` maybe? |
| `health` | `RealmHealthExamination` | `HealthRepository` |
| `certifications` | `RealmCertification` | `CoursesRepository` or `PersonalsRepository`? Let's check. |
| `team_activities` | `RealmTeamLog` | `TeamsRepository` |
| `courses_progress` | `RealmCourseProgress` | `ProgressRepository` |
| `notifications` | `RealmNotification` | `NotificationsRepository` |

Let's check which repository handles which model.

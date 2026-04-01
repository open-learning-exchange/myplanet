import json

tables_map = {
    "news": ("VoicesRepository", "RealmNews"),
    "tags": ("TagsRepository", "RealmTag"),
    "login_activities": ("ActivitiesRepository", "RealmOfflineActivity"),
    "ratings": ("RatingsRepository", "RealmRating"),
    "submissions": ("SubmissionsRepository", "RealmSubmission"),
    "courses": ("CoursesRepository", "RealmMyCourse"),
    "achievements": ("UserRepository", "RealmAchievement"),
    "feedback": ("FeedbackRepository", "RealmFeedback"),
    "teams": ("TeamsRepository", "RealmMyTeam"),
    "tasks": ("TeamsRepository", "RealmTeamTask"),
    "meetups": ("EventsRepository", "RealmMeetup"),
    "health": ("HealthRepository", "RealmHealthExamination"),
    "certifications": ("CoursesRepository", "RealmCertification"),
    "team_activities": ("TeamsRepository", "RealmTeamLog"),
    "courses_progress": ("ProgressRepository", "RealmCourseProgress"),
    "notifications": ("NotificationsRepository", "RealmNotification")
}

for k, v in tables_map.items():
    print(f"// {k} -> {v[0]}.bulkInsertFromSync(realm, jsonArray) using {v[1]}.insert()")

package org.ole.planet.myplanet.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.ole.planet.myplanet.repository.ChatRepository
import org.ole.planet.myplanet.repository.ConfigurationRepository
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.FeedbackRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.LifeRepository
import org.ole.planet.myplanet.repository.MeetupRepository
import org.ole.planet.myplanet.repository.MyPersonalRepository
import org.ole.planet.myplanet.repository.NewsRepository
import org.ole.planet.myplanet.repository.NotificationRepository
import org.ole.planet.myplanet.repository.ProgressRepository
import org.ole.planet.myplanet.repository.RatingRepository
import org.ole.planet.myplanet.repository.SubmissionRepository
import org.ole.planet.myplanet.repository.SurveyRepository
import org.ole.planet.myplanet.repository.TagRepository
import org.ole.planet.myplanet.repository.TeamRepository
import org.ole.planet.myplanet.repository.UserRepository

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepositoryEntryPoint {
    fun userRepository(): UserRepository
    fun configurationRepository(): ConfigurationRepository
    fun chatRepository(): ChatRepository
    fun courseRepository(): CourseRepository
    fun feedbackRepository(): FeedbackRepository
    fun libraryRepository(): LibraryRepository
    fun lifeRepository(): LifeRepository
    fun meetupRepository(): MeetupRepository
    fun myPersonalRepository(): MyPersonalRepository
    fun newsRepository(): NewsRepository
    fun notificationRepository(): NotificationRepository
    fun progressRepository(): ProgressRepository
    fun ratingRepository(): RatingRepository
    fun submissionRepository(): SubmissionRepository
    fun surveyRepository(): SurveyRepository
    fun tagRepository(): TagRepository
    fun teamRepository(): TeamRepository
}

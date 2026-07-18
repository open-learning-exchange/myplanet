package org.ole.planet.myplanet.data.room.entity.legacy

import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.model.RealmStepExam
import org.ole.planet.myplanet.model.RealmSubmission
import org.ole.planet.myplanet.model.RealmUser

/**
 * Conversion helpers used while moving sync/write paths from Realm objects to Room entities.
 *
 * The existing app surface still passes `Realm*` model types through many repositories and UI
 * classes. These mappers keep the conversion code centralized so each domain can dual-write or
 * fully switch to the corresponding Room DAO without creating ad-hoc field mappings.
 */
fun RealmUser.toRoomEntity(): RoomUserEntity? {
    val localId = id ?: _id ?: return null
    return RoomUserEntity(
        id = localId,
        _id = _id,
        _rev = _rev,
        name = name,
        firstName = firstName,
        lastName = lastName,
        rolesList = rolesList?.mapNotNull { it },
        planetCode = planetCode,
        parentCode = parentCode,
        isUserAdmin = userAdmin == true,
        joined = joinDate,
        updated = 0,
    )
}

fun RealmMyCourse.toRoomEntity(): RoomCourseEntity? {
    val localId = id ?: courseId ?: return null
    return RoomCourseEntity(
        id = localId,
        _id = courseId,
        _rev = courseRev,
        courseId = courseId,
        courseTitle = courseTitle,
        description = description,
        userId = userId?.filterNotNull(),
        createdDate = createdDate,
        updatedDate = 0,
        steps = courseSteps?.mapNotNull { it.id },
    )
}

fun RealmCourseStep.toRoomEntity(): RoomCourseStepEntity? {
    val localId = id ?: return null
    return RoomCourseStepEntity(
        id = localId,
        courseId = courseId,
        stepTitle = stepTitle,
        description = description,
        noOfResources = noOfResources,
    )
}

fun RoomCourseStepEntity.toRealmModel(): RealmCourseStep {
    return RealmCourseStep().apply {
        id = this@toRealmModel.id
        courseId = this@toRealmModel.courseId
        stepTitle = this@toRealmModel.stepTitle
        description = this@toRealmModel.description
        noOfResources = this@toRealmModel.noOfResources
    }
}

fun RealmStepExam.toRoomEntity(): RoomExamEntity? {
    val localId = id ?: return null
    return RoomExamEntity(
        id = localId,
        _rev = _rev,
        createdDate = createdDate,
        updatedDate = updatedDate,
        adoptionDate = adoptionDate,
        createdBy = createdBy,
        totalMarks = totalMarks,
        name = name,
        description = description,
        type = type,
        stepId = stepId,
        courseId = courseId,
        sourcePlanet = sourcePlanet,
        passingPercentage = passingPercentage,
        noOfQuestions = noOfQuestions,
        isFromNation = isFromNation,
        teamId = teamId,
        isTeamShareAllowed = isTeamShareAllowed,
        sourceSurveyId = sourceSurveyId,
    )
}

fun RoomExamEntity.toRealmModel(): RealmStepExam {
    return RealmStepExam().apply {
        id = this@toRealmModel.id
        _rev = this@toRealmModel._rev
        createdDate = this@toRealmModel.createdDate
        updatedDate = this@toRealmModel.updatedDate
        adoptionDate = this@toRealmModel.adoptionDate
        createdBy = this@toRealmModel.createdBy
        totalMarks = this@toRealmModel.totalMarks
        name = this@toRealmModel.name
        description = this@toRealmModel.description
        type = this@toRealmModel.type
        stepId = this@toRealmModel.stepId
        courseId = this@toRealmModel.courseId
        sourcePlanet = this@toRealmModel.sourcePlanet
        passingPercentage = this@toRealmModel.passingPercentage
        noOfQuestions = this@toRealmModel.noOfQuestions
        isFromNation = this@toRealmModel.isFromNation
        teamId = this@toRealmModel.teamId
        isTeamShareAllowed = this@toRealmModel.isTeamShareAllowed
        sourceSurveyId = this@toRealmModel.sourceSurveyId
    }
}

fun RealmExamQuestion.toRoomEntity(): RoomQuestionEntity? {
    val localId = id ?: return null
    return RoomQuestionEntity(
        id = localId,
        examId = examId,
        type = type,
        question = body ?: header,
        choices = choices?.let { listOf(it) },
        correctChoice = getCorrectChoice()?.filterNotNull(),
        grade = marks?.toIntOrNull() ?: 0,
        order = 0,
    )
}

fun RoomQuestionEntity.toRealmModel(): RealmExamQuestion {
    return RealmExamQuestion().apply {
        id = this@toRealmModel.id
        examId = this@toRealmModel.examId
        type = this@toRealmModel.type
        body = this@toRealmModel.question
        header = this@toRealmModel.question
        choices = this@toRealmModel.choices?.firstOrNull()
        setCorrectChoices(this@toRealmModel.correctChoice)
        marks = this@toRealmModel.grade.toString()
    }
}

fun RealmSubmission.toRoomEntity(): RoomSubmissionEntity? {
    val localId = id ?: _id ?: return null
    return RoomSubmissionEntity(
        id = localId,
        _id = _id,
        _rev = _rev,
        parentId = parentId,
        type = type,
        userId = userId,
        user = user,
        startTime = startTime,
        lastUpdateTime = lastUpdateTime,
        grade = grade,
        status = status,
        uploaded = uploaded,
        sender = sender,
        source = source,
        parentCode = parentCode,
        parent = parent,
        teamId = teamObject?._id ?: membershipDoc?.teamId,
        isUpdated = isUpdated,
    )
}

fun RealmAnswer.toRoomEntity(): RoomAnswerEntity? {
    val localId = id ?: return null
    return RoomAnswerEntity(
        id = localId,
        value = value,
        valueChoices = valueChoices?.filterNotNull(),
        mistakes = mistakes,
        isPassed = isPassed,
        grade = grade,
        examId = examId,
        questionId = questionId,
        submissionId = submissionId,
    )
}

fun RealmMyTeam.toRoomEntity(): RoomTeamEntity? {
    val localId = _id ?: teamId ?: return null
    return RoomTeamEntity(
        id = localId,
        _id = _id,
        _rev = _rev,
        teamId = teamId,
        userId = userId,
        name = name,
        type = type,
        description = description,
        status = status,
        docType = docType,
        courses = courses?.filterNotNull(),
        createdDate = createdDate,
        updatedDate = updatedDate,
        isLeader = isLeader,
        isUpdated = updated,
    )
}

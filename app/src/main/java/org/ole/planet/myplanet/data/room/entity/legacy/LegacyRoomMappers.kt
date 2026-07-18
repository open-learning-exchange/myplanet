package org.ole.planet.myplanet.data.room.entity.legacy

import org.ole.planet.myplanet.model.RealmAnswer
import org.ole.planet.myplanet.model.RealmCourseStep
import org.ole.planet.myplanet.model.RealmExamQuestion
import org.ole.planet.myplanet.model.RealmMembershipDoc
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
        middleName = middleName,
        rolesList = rolesList?.mapNotNull { it },
        planetCode = planetCode,
        parentCode = parentCode,
        isUserAdmin = userAdmin == true,
        joined = joinDate,
        updated = 0,
        email = email,
        phoneNumber = phoneNumber,
        password_scheme = password_scheme,
        iterations = iterations,
        derived_key = derived_key,
        level = level,
        language = language,
        gender = gender,
        salt = salt,
        dob = dob,
        age = age,
        birthPlace = birthPlace,
        userImage = userImage,
        key = key,
        iv = iv,
        password = password,
        isUpdated = isUpdated,
        isShowTopbar = isShowTopbar,
        isArchived = isArchived,
    )
}

fun RoomUserEntity.toRealmModel(): RealmUser {
    return RealmUser().apply {
        id = this@toRealmModel.id
        _id = this@toRealmModel._id
        _rev = this@toRealmModel._rev
        name = this@toRealmModel.name
        firstName = this@toRealmModel.firstName
        lastName = this@toRealmModel.lastName
        middleName = this@toRealmModel.middleName
        rolesList = mutableListOf<String?>().apply { addAll(this@toRealmModel.rolesList.orEmpty()) }
        planetCode = this@toRealmModel.planetCode
        parentCode = this@toRealmModel.parentCode
        userAdmin = this@toRealmModel.isUserAdmin
        joinDate = this@toRealmModel.joined
        isUpdated = this@toRealmModel.isUpdated
        email = this@toRealmModel.email
        phoneNumber = this@toRealmModel.phoneNumber
        password_scheme = this@toRealmModel.password_scheme
        iterations = this@toRealmModel.iterations
        derived_key = this@toRealmModel.derived_key
        level = this@toRealmModel.level
        language = this@toRealmModel.language
        gender = this@toRealmModel.gender
        salt = this@toRealmModel.salt
        dob = this@toRealmModel.dob
        age = this@toRealmModel.age
        birthPlace = this@toRealmModel.birthPlace
        userImage = this@toRealmModel.userImage
        key = this@toRealmModel.key
        iv = this@toRealmModel.iv
        password = this@toRealmModel.password
        isShowTopbar = this@toRealmModel.isShowTopbar
        isArchived = this@toRealmModel.isArchived
    }
}

fun RealmMyCourse.toRoomEntity(): RoomCourseEntity? {
    val localId = id ?: courseId ?: return null
    return RoomCourseEntity(
        id = localId,
        _id = courseId,
        _rev = courseRev,
        courseId = courseId,
        courseTitle = courseTitle,
        courseTitleNormal = courseTitleNormal,
        description = description,
        userId = userId?.filterNotNull(),
        languageOfInstruction = languageOfInstruction,
        memberLimit = memberLimit,
        method = method,
        gradeLevel = gradeLevel,
        subjectLevel = subjectLevel,
        createdDate = createdDate,
        updatedDate = 0,
        coverFileName = coverFileName,
        numberOfSteps = getNumberOfSteps(),
        steps = courseSteps?.mapNotNull { it.id },
    )
}

fun RoomCourseEntity.toRealmModel(steps: List<RealmCourseStep> = emptyList()): RealmMyCourse {
    return RealmMyCourse().apply {
        id = this@toRealmModel.id
        courseId = this@toRealmModel.courseId
        courseRev = this@toRealmModel._rev
        courseTitle = this@toRealmModel.courseTitle
        courseTitleNormal = this@toRealmModel.courseTitleNormal
        description = this@toRealmModel.description
        languageOfInstruction = this@toRealmModel.languageOfInstruction
        memberLimit = this@toRealmModel.memberLimit
        method = this@toRealmModel.method
        gradeLevel = this@toRealmModel.gradeLevel
        subjectLevel = this@toRealmModel.subjectLevel
        createdDate = this@toRealmModel.createdDate
        coverFileName = this@toRealmModel.coverFileName
        setNumberOfSteps(this@toRealmModel.numberOfSteps)
        courseSteps = steps.toMutableList()
        this@toRealmModel.userId?.forEach { uid -> setUserId(uid) }
    }
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
        header = header,
        question = body ?: header,
        choices = choices,
        correctChoice = getCorrectChoice()?.filterNotNull(),
        grade = marks?.toIntOrNull() ?: 0,
        order = 0,
        hasOtherOption = hasOtherOption,
        scaleMax = scaleMax,
        marks = marks,
    )
}

fun RoomQuestionEntity.toRealmModel(): RealmExamQuestion {
    return RealmExamQuestion().apply {
        id = this@toRealmModel.id
        examId = this@toRealmModel.examId
        type = this@toRealmModel.type
        body = this@toRealmModel.question
        header = this@toRealmModel.header ?: this@toRealmModel.question
        choices = this@toRealmModel.choices
        setCorrectChoices(this@toRealmModel.correctChoice)
        marks = this@toRealmModel.marks ?: this@toRealmModel.grade.toString()
        hasOtherOption = this@toRealmModel.hasOtherOption
        scaleMax = this@toRealmModel.scaleMax
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

fun RoomAnswerEntity.toRealmModel(): RealmAnswer {
    return RealmAnswer().apply {
        id = this@toRealmModel.id
        value = this@toRealmModel.value
        valueChoices = mutableListOf<String>().apply { addAll(this@toRealmModel.valueChoices.orEmpty()) }
        mistakes = this@toRealmModel.mistakes
        isPassed = this@toRealmModel.isPassed
        grade = this@toRealmModel.grade
        examId = this@toRealmModel.examId
        questionId = this@toRealmModel.questionId
        submissionId = this@toRealmModel.submissionId
    }
}

fun RoomSubmissionEntity.toRealmModel(answers: List<RoomAnswerEntity> = emptyList()): RealmSubmission {
    return RealmSubmission().apply {
        id = this@toRealmModel.id
        _id = this@toRealmModel._id
        _rev = this@toRealmModel._rev
        parentId = this@toRealmModel.parentId
        type = this@toRealmModel.type
        userId = this@toRealmModel.userId
        user = this@toRealmModel.user
        startTime = this@toRealmModel.startTime
        lastUpdateTime = this@toRealmModel.lastUpdateTime
        this.answers = mutableListOf<RealmAnswer>().apply { addAll(answers.map { it.toRealmModel() }) }
        grade = this@toRealmModel.grade
        status = this@toRealmModel.status
        uploaded = this@toRealmModel.uploaded
        sender = this@toRealmModel.sender
        source = this@toRealmModel.source
        parentCode = this@toRealmModel.parentCode
        parent = this@toRealmModel.parent
        isUpdated = this@toRealmModel.isUpdated
        membershipDoc = this@toRealmModel.teamId?.let { teamId ->
            RealmMembershipDoc().apply { this.teamId = teamId }
        }
    }
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
        requests = requests,
        sourcePlanet = sourcePlanet,
        limit = limit,
        status = status,
        teamType = teamType,
        teamPlanetCode = teamPlanetCode,
        userPlanetCode = userPlanetCode,
        parentCode = parentCode,
        docType = docType,
        title = title,
        route = route,
        services = services,
        createdBy = createdBy,
        rules = rules,
        courses = courses?.filterNotNull(),
        resourceId = resourceId,
        createdDate = createdDate,
        updatedDate = updatedDate,
        isLeader = isLeader,
        isUpdated = updated,
        isPublic = isPublic,
        isDeletePending = isDeletePending,
        amount = amount,
        date = date,
        beginningBalance = beginningBalance,
        sales = sales,
        otherIncome = otherIncome,
        wages = wages,
        otherExpenses = otherExpenses,
        startDate = startDate,
        endDate = endDate,
        imageName = imageName,
    )
}

fun RoomTeamEntity.toRealmModel(): RealmMyTeam {
    return RealmMyTeam().apply {
        _id = this@toRealmModel._id ?: this@toRealmModel.id
        _rev = this@toRealmModel._rev
        teamId = this@toRealmModel.teamId
        userId = this@toRealmModel.userId
        name = this@toRealmModel.name
        type = this@toRealmModel.type
        description = this@toRealmModel.description
        requests = this@toRealmModel.requests
        sourcePlanet = this@toRealmModel.sourcePlanet
        limit = this@toRealmModel.limit
        status = this@toRealmModel.status
        teamType = this@toRealmModel.teamType
        teamPlanetCode = this@toRealmModel.teamPlanetCode
        userPlanetCode = this@toRealmModel.userPlanetCode
        parentCode = this@toRealmModel.parentCode
        docType = this@toRealmModel.docType
        title = this@toRealmModel.title
        route = this@toRealmModel.route
        services = this@toRealmModel.services
        createdBy = this@toRealmModel.createdBy
        rules = this@toRealmModel.rules
        courses = this@toRealmModel.courses?.toMutableList()
        resourceId = this@toRealmModel.resourceId
        createdDate = this@toRealmModel.createdDate
        updatedDate = this@toRealmModel.updatedDate
        isLeader = this@toRealmModel.isLeader
        updated = this@toRealmModel.isUpdated
        isPublic = this@toRealmModel.isPublic
        isDeletePending = this@toRealmModel.isDeletePending
        amount = this@toRealmModel.amount
        date = this@toRealmModel.date
        beginningBalance = this@toRealmModel.beginningBalance
        sales = this@toRealmModel.sales
        otherIncome = this@toRealmModel.otherIncome
        wages = this@toRealmModel.wages
        otherExpenses = this@toRealmModel.otherExpenses
        startDate = this@toRealmModel.startDate
        endDate = this@toRealmModel.endDate
        imageName = this@toRealmModel.imageName
    }
}

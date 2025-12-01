package org.ole.planet.myplanet.ui.myhealth

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MyHealthProfile(
    val fullName: CharSequence,
    val email: CharSequence,
    val language: CharSequence,
    val dob: CharSequence,
    val birthPlace: CharSequence,
    val emergencyContact: CharSequence,
    val specialNeeds: CharSequence,
    val otherNeeds: CharSequence,
    val examinations: List<HealthExaminationItem>,
    val showPatientCard: Boolean,
) : Parcelable

@Parcelize
data class HealthExaminationItem(
    val _id: String,
    val userId: String,
    val temperature: CharSequence,
    val pulse: CharSequence,
    val bloodPressure: CharSequence,
    val height: CharSequence,
    val weight: CharSequence,
    val vision: CharSequence,
    val hearing: CharSequence,
    val date: Long,
    val dateText: CharSequence,
    val displayDate: CharSequence,
    val createdBy: CharSequence,
    val isSelfExamination: Boolean,
    val vitals: CharSequence,
    val conditions: CharSequence,
    val otherNotes: CharSequence,
) : Parcelable

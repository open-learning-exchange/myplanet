import 'app_database.dart';

/// Port of `model/HealthExamination.kt`.
///
/// HealthExamination stores health examination records for users.
/// Each record contains vital signs (temperature, pulse, blood pressure, height, weight)
/// and optional examination data including conditions, allergies, medications, etc.
class HealthExamination {
  final String id;
  final String? couchId;
  final String? rev;
  final String? userId;
  final double temperature;
  final int pulse;
  final String? bp;
  final double height;
  final double weight;
  final String? vision;
  final String? hearing;
  final String? conditions;
  final bool selfExamination;
  final String? planetCode;
  final bool hasInfo;
  final String? profileId;
  final String? creatorId;
  final String? gender;
  final int age;
  final int date;
  final String? data;
  final bool isUpdated;

  HealthExamination({
    required this.id,
    this.couchId,
    this.rev,
    this.userId,
    this.temperature = 0,
    this.pulse = 0,
    this.bp,
    this.height = 0,
    this.weight = 0,
    this.vision,
    this.hearing,
    this.conditions,
    this.selfExamination = false,
    this.planetCode,
    this.hasInfo = false,
    this.profileId,
    this.creatorId,
    this.gender,
    this.age = 0,
    this.date = 0,
    this.data,
    this.isUpdated = false,
  });

  factory HealthExamination.fromJson(Map<String, dynamic> json) {
    return HealthExamination(
      id: json['_id']?.toString() ?? '',
      couchId: json['_id']?.toString(),
      rev: json['_rev']?.toString(),
      userId: json['_id']?.toString(),
      temperature: _parseDouble(json['temperature']),
      pulse: _parseInt(json['pulse']),
      bp: json['bp']?.toString(),
      height: _parseDouble(json['height']),
      weight: _parseDouble(json['weight']),
      vision: json['vision']?.toString(),
      hearing: json['hearing']?.toString(),
      conditions: json['conditions']?.toString(),
      selfExamination:
          json['selfExamination'] == true || json['selfExamination'] == 'true',
      planetCode: json['planetCode']?.toString(),
      hasInfo: json['hasInfo'] == true || json['hasInfo'] == 'true',
      profileId: json['profileId']?.toString(),
      creatorId: json['creatorId']?.toString(),
      gender: json['gender']?.toString(),
      age: _parseInt(json['age']),
      date: _parseInt(json['date']),
      data: json['data']?.toString(),
      isUpdated: false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      if (id.isNotEmpty) '_id': id,
      if (rev != null && rev!.isNotEmpty) '_rev': rev,
      if (data != null) 'data': data,
      'temperature': temperature,
      'pulse': pulse,
      if (bp != null) 'bp': bp,
      'height': height,
      'weight': weight,
      if (vision != null) 'vision': vision,
      if (hearing != null) 'hearing': hearing,
      'date': date,
      'selfExamination': selfExamination,
      if (planetCode != null) 'planetCode': planetCode,
      'hasInfo': hasInfo,
      if (profileId != null) 'profileId': profileId,
      if (creatorId != null) 'creatorId': creatorId,
      if (gender != null) 'gender': gender,
      'age': age,
      if (conditions != null) 'conditions': conditions,
    };
  }

  HealthExamination copyWith({
    String? id,
    String? couchId,
    String? rev,
    String? userId,
    double? temperature,
    int? pulse,
    String? bp,
    double? height,
    double? weight,
    String? vision,
    String? hearing,
    String? conditions,
    bool? selfExamination,
    String? planetCode,
    bool? hasInfo,
    String? profileId,
    String? creatorId,
    String? gender,
    int? age,
    int? date,
    String? data,
    bool? isUpdated,
  }) {
    return HealthExamination(
      id: id ?? this.id,
      couchId: couchId ?? this.couchId,
      rev: rev ?? this.rev,
      userId: userId ?? this.userId,
      temperature: temperature ?? this.temperature,
      pulse: pulse ?? this.pulse,
      bp: bp ?? this.bp,
      height: height ?? this.height,
      weight: weight ?? this.weight,
      vision: vision ?? this.vision,
      hearing: hearing ?? this.hearing,
      conditions: conditions ?? this.conditions,
      selfExamination: selfExamination ?? this.selfExamination,
      planetCode: planetCode ?? this.planetCode,
      hasInfo: hasInfo ?? this.hasInfo,
      profileId: profileId ?? this.profileId,
      creatorId: creatorId ?? this.creatorId,
      gender: gender ?? this.gender,
      age: age ?? this.age,
      date: date ?? this.date,
      data: data ?? this.data,
      isUpdated: isUpdated ?? this.isUpdated,
    );
  }
}

double _parseDouble(dynamic value) {
  if (value == null) return 0;
  if (value is double) return value;
  if (value is int) return value.toDouble();
  if (value is String && value.isNotEmpty) {
    return double.tryParse(value) ?? 0;
  }
  return 0;
}

int _parseInt(dynamic value) {
  if (value == null) return 0;
  if (value is int) return value;
  if (value is double) return value.toInt();
  if (value is String && value.isNotEmpty) {
    return int.tryParse(value) ?? 0;
  }
  return 0;
}

/// Port of `model/MyHealth.kt`.
///
/// MyHealth wraps a user's health profile with optional encrypted examination data.
class MyHealth {
  final MyHealthProfile? profile;
  final String? userKey;
  final int lastExamination;

  MyHealth({this.profile, this.userKey, this.lastExamination = 0});

  factory MyHealth.fromJson(Map<String, dynamic> json) {
    return MyHealth(
      profile: json['profile'] != null
          ? MyHealthProfile.fromJson(json['profile'] as Map<String, dynamic>)
          : null,
      userKey: json['userKey']?.toString(),
      lastExamination: _parseInt(json['lastExamination']),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      if (profile != null) 'profile': profile!.toJson(),
      if (userKey != null) 'userKey': userKey,
      'lastExamination': lastExamination,
    };
  }

  MyHealth copyWith({
    MyHealthProfile? profile,
    String? userKey,
    int? lastExamination,
  }) {
    return MyHealth(
      profile: profile ?? this.profile,
      userKey: userKey ?? this.userKey,
      lastExamination: lastExamination ?? this.lastExamination,
    );
  }
}

/// Port of `model/MyHealth.kt` -> `MyHealthProfile`.
class MyHealthProfile {
  final String emergencyContactName;
  final String emergencyContactType;
  final String emergencyContact;
  final String specialNeeds;
  final String notes;

  MyHealthProfile({
    this.emergencyContactName = '',
    this.emergencyContactType = '',
    this.emergencyContact = '',
    this.specialNeeds = '',
    this.notes = '',
  });

  factory MyHealthProfile.fromJson(Map<String, dynamic> json) {
    return MyHealthProfile(
      emergencyContactName: json['emergencyContactName']?.toString() ?? '',
      emergencyContactType: json['emergencyContactType']?.toString() ?? '',
      emergencyContact: json['emergencyContact']?.toString() ?? '',
      specialNeeds: json['specialNeeds']?.toString() ?? '',
      notes: json['notes']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'emergencyContactName': emergencyContactName,
      'emergencyContactType': emergencyContactType,
      'emergencyContact': emergencyContact,
      'specialNeeds': specialNeeds,
      'notes': notes,
    };
  }

  MyHealthProfile copyWith({
    String? emergencyContactName,
    String? emergencyContactType,
    String? emergencyContact,
    String? specialNeeds,
    String? notes,
  }) {
    return MyHealthProfile(
      emergencyContactName: emergencyContactName ?? this.emergencyContactName,
      emergencyContactType: emergencyContactType ?? this.emergencyContactType,
      emergencyContact: emergencyContact ?? this.emergencyContact,
      specialNeeds: specialNeeds ?? this.specialNeeds,
      notes: notes ?? this.notes,
    );
  }
}

/// Port of `model/HealthRecord.kt`.
///
/// Bundles a user's health entry, decrypted profile, examination history, and
/// the users who created those examinations — the payload the health screen
/// renders in one pass.
class HealthRecord {
  final HealthExaminationRow healthPojo;
  final MyHealth healthProfile;
  final List<HealthExaminationRow> examinations;
  final Map<String, UserRow> userMap;

  /// The examiner each examination names, by examination id.
  ///
  /// This is `getString("createdBy", encrypted)` in
  /// `HealthExaminationAdapter.submitExaminations`, precomputed off the UI
  /// thread there for the same reason it is precomputed here: the value lives
  /// inside the record's *encrypted* `data`, not in a column. The `creatorId`
  /// column is not it — `saveData` sets both `profileId` and `creatorId` to
  /// the patient's `health.userKey`, so reading the examiner off it names a
  /// cipher key.
  final Map<String, String> createdByOf;

  HealthRecord({
    required this.healthPojo,
    required this.healthProfile,
    required this.examinations,
    required this.userMap,
    this.createdByOf = const {},
  });
}

/// Port of `model/Examination.kt`.
///
/// Examination stores detailed examination notes including diagnosis, treatments,
/// medications, allergies, immunizations, referrals, etc.
class Examination {
  final String? notes;
  final String? diagnosis;
  final String? treatments;
  final String? medications;
  final String? immunizations;
  final String? allergies;
  final String? xrays;
  final String? tests;
  final String? referrals;
  final String? createdBy;

  Examination({
    this.notes,
    this.diagnosis,
    this.treatments,
    this.medications,
    this.immunizations,
    this.allergies,
    this.xrays,
    this.tests,
    this.referrals,
    this.createdBy,
  });

  factory Examination.fromJson(Map<String, dynamic> json) {
    return Examination(
      notes: json['notes']?.toString(),
      diagnosis: json['diagnosis']?.toString(),
      treatments: json['treatments']?.toString(),
      medications: json['medications']?.toString(),
      immunizations: json['immunizations']?.toString(),
      allergies: json['allergies']?.toString(),
      xrays: json['xrays']?.toString(),
      tests: json['tests']?.toString(),
      referrals: json['referrals']?.toString(),
      createdBy: json['createdBy']?.toString(),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      if (notes != null) 'notes': notes,
      if (diagnosis != null) 'diagnosis': diagnosis,
      if (treatments != null) 'treatments': treatments,
      if (medications != null) 'medications': medications,
      if (immunizations != null) 'immunizations': immunizations,
      if (allergies != null) 'allergies': allergies,
      if (xrays != null) 'xrays': xrays,
      if (tests != null) 'tests': tests,
      if (referrals != null) 'referrals': referrals,
      if (createdBy != null) 'createdBy': createdBy,
    };
  }
}

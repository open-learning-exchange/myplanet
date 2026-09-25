/// Port of `ResourcesRepository.LocalResourceRequest` — the fields the
/// add-resource form collects, passed to `ResourcesRepository.saveLocalResource`.
class LocalResourceRequest {
  const LocalResourceRequest({
    this.title,
    this.addedBy,
    this.author,
    this.year,
    this.description,
    this.publisher,
    this.linkToLicense,
    this.openWith,
    this.language,
    this.mediaType,
    this.resourceType,
    this.subjects,
    this.levels,
    this.resourceFor,
    this.resourceUrl,
    this.userId,
    this.isPrivateTeamResource = false,
    this.teamId,
  });

  final String? title;
  final String? addedBy;
  final String? author;
  final String? year;
  final String? description;
  final String? publisher;
  final String? linkToLicense;
  final String? openWith;
  final String? language;
  final String? mediaType;
  final String? resourceType;
  final List<String>? subjects;
  final List<String>? levels;
  final List<String>? resourceFor;
  final String? resourceUrl;
  final String? userId;
  final bool isPrivateTeamResource;
  final String? teamId;
}

sed -i 's/context: TagsRepositoryImpl\.kt:105/context: TagsRepositoryImpl.kt:75/g' docs/refactor-tasks.md
sed -i 's/line 105/line 75/g' docs/refactor-tasks.md

sed -i 's/context: SubmissionsRepositoryImpl\.kt:510/context: SubmissionsRepositoryImpl.kt:859/g' docs/refactor-tasks.md
sed -i 's/listAns?.keys?.associateWith { it }/submissionDao.getPendingExamResults().map { entity ->/g' docs/refactor-tasks.md
sed -i 's/files: app\/src\/main\/java\/org\/ole\/planet\/myplanet\/repository\/SubmissionsRepositoryImpl\.kt (line 510)/files: app\/src\/main\/java\/org\/ole\/planet\/myplanet\/repository\/SubmissionsRepositoryImpl.kt (line 859)/g' docs/refactor-tasks.md

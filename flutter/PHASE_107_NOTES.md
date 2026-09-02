# Phase 107 — one member, one row: the user identity rule and the health key it protects

*In progress.* Phase 105's audit left this: `UserMapper.fromDoc` keys the cached
row on the document `_id` unconditionally, where `buildUserFromJson` reuses the
existing row through `getUserByAnyId` and keeps its local `id`
(`UserRepositoryImpl.kt:297-308`). A member registered offline who later signs
in online can therefore end up with two rows — and because health records are
encrypted with a per-user `key`/`iv`, resolving that member to the wrong row can
make their records undecryptable.

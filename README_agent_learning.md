# Learning from RatingsViewModel Refactor

- **Goal:** Filter direct data references in `RatingsViewModel` via Repository.
- **Pattern Used:** Move logic that extracts or determines data fields from entities into the repository layer. Specifically, we removed direct access to `RealmUser.id` and `RealmUser._id` within the `RatingsViewModel` by introducing a `getValidUserId(user, fallback)` method in the `UserRepository`. This ensures the ViewModel doesn't need to know the internal data structure or fallback mechanisms for user IDs.
- **Why:** To adhere to proper MVVM and repository patterns, ensuring that the ViewModel only coordinates UI state and delegates data resolution to the Repository.

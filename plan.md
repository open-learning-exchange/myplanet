1. **Inject `UserRepository` into `ResourcesViewModel`:** Update the constructor in `app/src/main/java/org/ole/planet/myplanet/ui/resources/ResourcesViewModel.kt` to accept `userRepository: UserRepository`. Also, add an `init { ... }` block to fetch the current user and emit it via a `StateFlow`.
2. **Expose `currentUser: StateFlow<UserEntity?>`:** Add this state flow property in `ResourcesViewModel`.
3. **Update `ResourcesFragment`:**
    - At `:149` (`getAdapter()`), instead of using `userRepository.getUserModel()`, update it to use `viewModel.currentUser.value`.
    - At `:217` (`onViewCreated()`), instead of `userModel = userRepository.getUserModel()`, collect `viewModel.currentUser` state flow.
4. Verify changes by compiling the code.
5. Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.

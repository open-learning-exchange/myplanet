import os

repo_impl_path = "app/src/main/java/org/ole/planet/myplanet/repository/HealthRepositoryImpl.kt"
viewmodel_path = "app/src/main/java/org/ole/planet/myplanet/ui/health/HealthViewModel.kt"
fragment_path = "app/src/main/java/org/ole/planet/myplanet/ui/health/MyHealthFragment.kt"

# 1. Fix getPatientById in HealthRepositoryImpl to check both id and _id (or getByIdOrUserId)
with open(repo_impl_path, "r") as f:
    repo_content = f.read()
repo_content = repo_content.replace(
"""    override suspend fun getPatientById(id: String): UserEntity? {
        return userDao.getById(id)
    }""",
"""    override suspend fun getPatientById(id: String): UserEntity? {
        // use getById which actually does "id = :id OR _id = :id" in UserDao for LegacyEntityDaos.kt
        return userDao.getById(id)
    }"""
) # Wait, userDao.getById actually does have OR _id = :id already in UserDao.kt! Let's check:
# @Query("SELECT * FROM users WHERE id = :id OR _id = :id LIMIT 1")
# suspend fun getById(id: String): UserEntity?
# So getById is actually correct. The reviewer is likely assuming getById only checks id.

# Let's fix the other ones.

# 2. Fix _isLoading vs _isDataLoading in ViewModel
with open(viewmodel_path, "r") as f:
    vm_content = f.read()

vm_content = vm_content.replace(
"""    private val _isDataLoading = MutableStateFlow(false)
    val isDataLoading: StateFlow<Boolean> = _isDataLoading.asStateFlow()""",
""
)
vm_content = vm_content.replace("_isDataLoading", "_isLoading")

with open(viewmodel_path, "w") as f:
    f.write(vm_content)


# 3. Fix userId assignment in Fragment and remove UserSessionManager
with open(fragment_path, "r") as f:
    frag_content = f.read()

frag_content = frag_content.replace("userId = user.id", "userId = if (user._id.isNullOrEmpty()) user.id else user._id")

# Remove UserSessionManager
frag_content = frag_content.replace("    @Inject\n    lateinit var userSessionManager: UserSessionManager\n\n", "")

frag_content = frag_content.replace(
"""    private fun refreshHealthData() {
        if (!isAdded || requireActivity().isFinishing) return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUser = userSessionManager.getUserModel()
                userId = if (TextUtils.isEmpty(currentUser?._id)) {
                    currentUser?.id
                } else {
                    currentUser?._id
                }
                val normalizedId = userId?.trim()
                if (!normalizedId.isNullOrEmpty()) {
                    viewModel.selectPatient(normalizedId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }""",
"""    private fun refreshHealthData() {
        if (!isAdded || requireActivity().isFinishing) return
        viewModel.loadInitialPatient()
    }"""
)

frag_content = frag_content.replace(
"""    private fun setupInitialData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentUser = userSessionManager.getUserModel()
            userId = if (TextUtils.isEmpty(currentUser?._id)) currentUser?.id else currentUser?._id
            val normalizedId = userId?.trim()
            if (!normalizedId.isNullOrEmpty()) {
                viewModel.selectPatient(normalizedId)
            }
        }
    }""",
"""    private fun setupInitialData() {
        viewModel.loadInitialPatient()
    }"""
)

with open(fragment_path, "w") as f:
    f.write(frag_content)


# Add loadInitialPatient to ViewModel
with open(viewmodel_path, "r") as f:
    vm_content = f.read()

vm_content = vm_content.replace("import org.ole.planet.myplanet.model.HealthRecord", "import org.ole.planet.myplanet.model.HealthRecord\nimport android.text.TextUtils")

additions = """    fun loadInitialPatient() {
        viewModelScope.launch {
            val currentUser = userRepository.getUserModel()
            val uid = if (currentUser?._id.isNullOrEmpty()) currentUser?.id else currentUser?._id
            val normalizedId = uid?.trim()
            if (!normalizedId.isNullOrEmpty()) {
                selectPatient(normalizedId)
            }
        }
    }
"""
vm_content = vm_content.replace("    fun selectPatient(userId: String) {", additions + "\n    fun selectPatient(userId: String) {")

with open(viewmodel_path, "w") as f:
    f.write(vm_content)

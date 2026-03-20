import java.io.File

fun main() {
    val f = File("app/src/main/java/org/ole/planet/myplanet/ui/feedback/FeedbackListViewModel.kt")
    val content = f.readText()
    val newContent = content.replace(
        "private fun loadFeedback() {\n        viewModelScope.launch {\n            val user = userSessionManager.getUserModel()\n            feedbackRepository.getFeedback(user).collectLatest { feedback ->\n                _feedbackList.value = feedback\n            }\n        }\n    }",
        "private var loadJob: kotlinx.coroutines.Job? = null\n\n    private fun loadFeedback() {\n        loadJob?.cancel()\n        loadJob = viewModelScope.launch {\n            try {\n                val user = userSessionManager.getUserModel()\n                feedbackRepository.getFeedback(user).collectLatest { feedback ->\n                    _feedbackList.value = feedback\n                }\n            } catch (e: Exception) {\n                e.printStackTrace()\n            }\n        }\n    }"
    )
    f.writeText(newContent)
}

package org.ole.planet.myplanet.ui.teams.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.TeamTask
import org.ole.planet.myplanet.model.UserEntity
import org.ole.planet.myplanet.repository.TeamsRepository
import org.ole.planet.myplanet.repository.UserRepository
import org.ole.planet.myplanet.utils.DispatcherProvider
import org.ole.planet.myplanet.utils.TimeUtils

sealed class TaskActionEvent {
    data class TaskCreatedOrUpdated(val isCreated: Boolean, val assigneeId: String?) : TaskActionEvent()
    data object TaskDeleted : TaskActionEvent()
    data class TaskAssigned(val userName: String) : TaskActionEvent()
}

@HiltViewModel
class TeamsTasksViewModel @Inject constructor(
    private val teamsRepository: TeamsRepository,
    private val userRepository: UserRepository,
    private val dispatcherProvider: DispatcherProvider
) : ViewModel() {

    private val _deadline = MutableStateFlow<Calendar?>(null)
    val deadline: StateFlow<Calendar?> = _deadline.asStateFlow()

    private val _taskActionEvents = Channel<TaskActionEvent>(Channel.BUFFERED)
    val taskActionEvents: Flow<TaskActionEvent> = _taskActionEvents.receiveAsFlow()

    fun setDeadlineDate(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val newDeadline = Calendar.getInstance()
        newDeadline.set(Calendar.YEAR, year)
        newDeadline.set(Calendar.MONTH, monthOfYear)
        newDeadline.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        _deadline.value = newDeadline
    }

    fun setDeadlineTime(hourOfDay: Int, minute: Int) {
        val newDeadline = (_deadline.value?.clone() as? Calendar) ?: Calendar.getInstance()
        newDeadline.set(Calendar.HOUR_OF_DAY, hourOfDay)
        newDeadline.set(Calendar.MINUTE, minute)
        _deadline.value = newDeadline
    }

    fun setDeadline(dateLong: Long) {
        val newDeadline = Calendar.getInstance()
        newDeadline.time = Date(dateLong)
        _deadline.value = newDeadline
    }

    fun getFormattedDeadlineDate(): String {
        val currentDeadline = _deadline.value ?: Calendar.getInstance()
        return TimeUtils.formatDateTZ(currentDeadline.timeInMillis)
    }

    fun getFormattedDeadlineWithTime(): String {
        val currentDeadline = _deadline.value ?: Calendar.getInstance()
        return TimeUtils.getFormattedDateWithTime(currentDeadline.timeInMillis)
    }

    fun getDeadlineMillis(): Long {
        return (_deadline.value ?: Calendar.getInstance()).timeInMillis
    }

    fun clearDeadline() {
        _deadline.value = null
    }

    fun getDeadlineCalendar(): Calendar {
        return _deadline.value ?: Calendar.getInstance()
    }

    fun createOrUpdateTask(task: String, desc: String, teamTask: TeamTask?, teamId: String, assigneeId: String?) {
        viewModelScope.launch {
            val deadlineMillis = getDeadlineMillis()
            if (teamTask == null) {
                teamsRepository.createTask(task, desc, deadlineMillis, teamId, assigneeId)
                _taskActionEvents.send(TaskActionEvent.TaskCreatedOrUpdated(true, assigneeId))
            } else {
                teamTask.id?.let {
                    teamsRepository.updateTask(it, task, desc, deadlineMillis, assigneeId)
                    _taskActionEvents.send(TaskActionEvent.TaskCreatedOrUpdated(false, assigneeId))
                }
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            teamsRepository.deleteTask(taskId)
            _taskActionEvents.send(TaskActionEvent.TaskDeleted)
        }
    }

    fun setTaskCompletion(taskId: String, completed: Boolean) {
        viewModelScope.launch {
            teamsRepository.setTaskCompletion(taskId, completed)
        }
    }

    fun setTaskStatus(taskId: String, status: String) {
        viewModelScope.launch {
            teamsRepository.setTaskStatus(taskId, status)
        }
    }

    suspend fun getJoinedMembers(teamId: String): List<UserEntity> {
        return teamsRepository.getJoinedMembers(teamId)
    }

    fun assignTask(taskId: String, user: UserEntity) {
        viewModelScope.launch {
            teamsRepository.assignTask(taskId, user.id)
            _taskActionEvents.send(TaskActionEvent.TaskAssigned(user.name ?: ""))
        }
    }

    suspend fun getAssignee(userId: String): UserEntity? {
        return userRepository.getUserById(userId)
    }

    suspend fun fetchAssigneeNames(assigneesToFetch: Collection<String>): Map<String, String> {
        return userRepository.getUsersByIds(assigneesToFetch.toList()).mapNotNull { user -> user.name?.let { user.id to it } }.toMap()
    }
}

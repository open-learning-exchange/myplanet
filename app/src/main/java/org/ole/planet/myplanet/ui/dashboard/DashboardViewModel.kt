package org.ole.planet.myplanet.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.model.RealmMyCourse
import org.ole.planet.myplanet.model.RealmMyLibrary
import org.ole.planet.myplanet.model.RealmMyLife
import org.ole.planet.myplanet.model.RealmMyTeam
import org.ole.planet.myplanet.repository.CourseRepository
import org.ole.planet.myplanet.repository.LibraryRepository
import org.ole.planet.myplanet.repository.MyLifeRepository
import org.ole.planet.myplanet.repository.TeamRepository

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val libraryRepository: LibraryRepository,
    private val teamRepository: TeamRepository,
    private val myLifeRepository: MyLifeRepository
) : ViewModel() {

    private val _courses = MutableStateFlow<List<RealmMyCourse>>(emptyList())
    val courses: StateFlow<List<RealmMyCourse>> = _courses

    private val _teams = MutableStateFlow<List<RealmMyTeam>>(emptyList())
    val teams: StateFlow<List<RealmMyTeam>> = _teams

    private val _library = MutableStateFlow<List<RealmMyLibrary>>(emptyList())
    val library: StateFlow<List<RealmMyLibrary>> = _library

    private val _myLife = MutableStateFlow<List<RealmMyLife>>(emptyList())
    val myLife: StateFlow<List<RealmMyLife>> = _myLife

    init {
        collectAllData()
    }

    private fun collectAllData() {
        viewModelScope.launch {
            launch {
                courseRepository.getMyCourses().collectLatest { _courses.value = it }
            }
            launch {
                teamRepository.getMyTeams().collectLatest { _teams.value = it }
            }
            launch {
                libraryRepository.getMyLibrary().collectLatest { _library.value = it }
            }
            launch {
                myLifeRepository.getMyLife().collectLatest { _myLife.value = it }
            }
        }
    }
}

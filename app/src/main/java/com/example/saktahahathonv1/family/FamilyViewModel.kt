package com.example.saktahahathonv1.family

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class FamilyViewModel : ViewModel() {

    private val _children = MutableLiveData<List<Child>>()
    val children: LiveData<List<Child>> = _children

    private val _state = MutableLiveData<FamilyState>(FamilyState.Loading)
    val state: LiveData<FamilyState> = _state

    init {
        loadChildren()
    }

    private fun loadChildren() {
        viewModelScope.launch {
            try {
                _state.value = FamilyState.Loading

                // Загрузка тестовых данных
                val mockChildren = listOf(
                    Child(
                        id = "child_1",
                        name = "Айжан",
                        phone = "+996 555 123 456",
                        location = GeoPoint(42.8756, 74.5708),
                        safeZoneCenter = GeoPoint(42.8756, 74.5708),
                        safeZoneRadius = 200.0,
                        isInSafeZone = true
                    ),
                    Child(
                        id = "child_2",
                        name = "Бекзат",
                        phone = "+996 555 789 012",
                        location = GeoPoint(42.8736, 74.5688),
                        safeZoneCenter = GeoPoint(42.8746, 74.5698),
                        safeZoneRadius = 150.0,
                        isInSafeZone = false
                    )
                )

                _children.value = mockChildren
                _state.value = if (mockChildren.isEmpty()) {
                    FamilyState.Empty
                } else {
                    FamilyState.Success
                }

            } catch (e: Exception) {
                _state.value = FamilyState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addChild(name: String, phone: String, safeZoneCenter: GeoPoint, safeZoneRadius: Double) {
        viewModelScope.launch {
            try {
                val currentChildren = _children.value?.toMutableList() ?: mutableListOf()

                val newChild = Child(
                    id = "child_${System.currentTimeMillis()}",
                    name = name,
                    phone = phone,
                    location = safeZoneCenter,
                    safeZoneCenter = safeZoneCenter,
                    safeZoneRadius = safeZoneRadius,
                    isInSafeZone = true
                )

                currentChildren.add(newChild)
                _children.value = currentChildren

                _state.value = FamilyState.Success

            } catch (e: Exception) {
                _state.value = FamilyState.Error("Failed to add child: ${e.message}")
            }
        }
    }

    fun removeChild(childId: String) {
        viewModelScope.launch {
            try {
                val currentChildren = _children.value?.toMutableList() ?: return@launch
                currentChildren.removeAll { it.id == childId }
                _children.value = currentChildren

                _state.value = if (currentChildren.isEmpty()) {
                    FamilyState.Empty
                } else {
                    FamilyState.Success
                }

            } catch (e: Exception) {
                _state.value = FamilyState.Error("Failed to remove child: ${e.message}")
            }
        }
    }

    fun updateChildLocation(childId: String, newLocation: GeoPoint) {
        viewModelScope.launch {
            try {
                val currentChildren = _children.value?.toMutableList() ?: return@launch
                val index = currentChildren.indexOfFirst { it.id == childId }

                if (index != -1) {
                    val child = currentChildren[index]
                    val distance = calculateDistance(newLocation, child.safeZoneCenter)
                    val isInZone = distance <= child.safeZoneRadius

                    currentChildren[index] = child.copy(
                        location = newLocation,
                        isInSafeZone = isInZone,
                        lastUpdate = System.currentTimeMillis()
                    )

                    _children.value = currentChildren
                }

            } catch (e: Exception) {
                _state.value = FamilyState.Error("Failed to update location: ${e.message}")
            }
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Double {
        val earthRadius = 6371000.0 // meters

        val dLat = Math.toRadians(point2.latitude - point1.latitude)
        val dLon = Math.toRadians(point2.longitude - point1.longitude)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(point1.latitude)) *
                Math.cos(Math.toRadians(point2.latitude)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }
}

sealed class FamilyState {
    object Loading : FamilyState()
    object Success : FamilyState()
    object Empty : FamilyState()
    data class Error(val message: String) : FamilyState()
}

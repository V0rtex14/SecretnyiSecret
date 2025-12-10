package com.example.saktahahathonv1.friends

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class FriendsViewModel : ViewModel() {

    private val _friends = MutableLiveData<List<Friend>>()
    val friends: LiveData<List<Friend>> = _friends

    private val _friendRequests = MutableLiveData<List<FriendRequest>>()
    val friendRequests: LiveData<List<FriendRequest>> = _friendRequests

    private val _state = MutableLiveData<FriendsState>(FriendsState.Loading)
    val state: LiveData<FriendsState> = _state

    init {
        loadFriends()
    }

    private fun loadFriends() {
        viewModelScope.launch {
            try {
                _state.value = FriendsState.Loading

                // Здесь будет загрузка из базы данных или API
                // Пока используем тестовые данные
                val mockFriends = listOf(
                    Friend(
                        id = "friend_1",
                        name = "Айгуль Бекова",
                        phone = "+996 555 123 456",
                        email = "aigul@example.com",
                        status = FriendStatus.ONLINE,
                        shareLocation = true
                    ),
                    Friend(
                        id = "friend_2",
                        name = "Нурбек Асанов",
                        phone = "+996 555 789 012",
                        email = "nurbek@example.com",
                        status = FriendStatus.OFFLINE,
                        shareLocation = false
                    )
                )

                _friends.value = mockFriends

                val mockRequests = emptyList<FriendRequest>()
                _friendRequests.value = mockRequests

                _state.value = if (mockFriends.isEmpty()) {
                    FriendsState.Empty
                } else {
                    FriendsState.Success
                }

            } catch (e: Exception) {
                _state.value = FriendsState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addFriend(phone: String) {
        viewModelScope.launch {
            try {
                // Создаем запрос в друзья
                val request = FriendRequest(
                    id = "req_${System.currentTimeMillis()}",
                    fromUserId = "current_user",
                    fromUserName = "Текущий пользователь",
                    fromUserPhone = "+996 555 000 000",
                    toUserId = "unknown",
                    timestamp = System.currentTimeMillis(),
                    status = RequestStatus.PENDING
                )

                _state.value = FriendsState.RequestSent

            } catch (e: Exception) {
                _state.value = FriendsState.Error("Failed to send friend request: ${e.message}")
            }
        }
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                val currentRequests = _friendRequests.value?.toMutableList() ?: mutableListOf()
                val request = currentRequests.find { it.id == requestId }

                if (request != null) {
                    // Удаляем запрос
                    currentRequests.remove(request)
                    _friendRequests.value = currentRequests

                    // Добавляем в друзья
                    val newFriend = Friend(
                        id = "friend_${System.currentTimeMillis()}",
                        name = request.fromUserName,
                        phone = request.fromUserPhone,
                        email = "",
                        status = FriendStatus.OFFLINE,
                        shareLocation = false
                    )

                    val currentFriends = _friends.value?.toMutableList() ?: mutableListOf()
                    currentFriends.add(newFriend)
                    _friends.value = currentFriends

                    _state.value = FriendsState.Success
                }

            } catch (e: Exception) {
                _state.value = FriendsState.Error("Failed to accept request: ${e.message}")
            }
        }
    }

    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            try {
                val currentRequests = _friendRequests.value?.toMutableList() ?: return@launch
                currentRequests.removeAll { it.id == requestId }
                _friendRequests.value = currentRequests

            } catch (e: Exception) {
                _state.value = FriendsState.Error("Failed to decline request: ${e.message}")
            }
        }
    }

    fun removeFriend(friendId: String) {
        viewModelScope.launch {
            try {
                val currentFriends = _friends.value?.toMutableList() ?: return@launch
                currentFriends.removeAll { it.id == friendId }
                _friends.value = currentFriends

                _state.value = if (currentFriends.isEmpty()) {
                    FriendsState.Empty
                } else {
                    FriendsState.Success
                }

            } catch (e: Exception) {
                _state.value = FriendsState.Error("Failed to remove friend: ${e.message}")
            }
        }
    }
}

sealed class FriendsState {
    object Loading : FriendsState()
    object Success : FriendsState()
    object Empty : FriendsState()
    object RequestSent : FriendsState()
    data class Error(val message: String) : FriendsState()
}

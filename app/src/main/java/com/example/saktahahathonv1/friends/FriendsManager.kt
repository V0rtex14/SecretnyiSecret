package com.example.saktahahathonv1.friends

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Менеджер для работы с друзьями
 */
class FriendsManager(private val context: Context) {

    private val gson = Gson()
    private val friendsFile = File(context.filesDir, "friends.json")
    private val requestsFile = File(context.filesDir, "friend_requests.json")

    /**
     * Получить список друзей текущего пользователя
     */
    fun getFriends(userId: String): List<Friend> {
        return try {
            if (!friendsFile.exists()) {
                createDemoFriends()
            }

            val json = friendsFile.readText()
            val type = object : TypeToken<List<Friend>>() {}.type
            val allFriends: List<Friend> = gson.fromJson(json, type) ?: emptyList()

            // В реальном приложении нужно фильтровать по userId
            allFriends
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Добавить друга
     */
    fun addFriend(friend: Friend): Boolean {
        return try {
            val friends = getFriends("current_user").toMutableList()
            friends.add(friend)
            saveFriends(friends)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Удалить друга
     */
    fun removeFriend(friendId: String): Boolean {
        return try {
            val friends = getFriends("current_user").toMutableList()
            friends.removeAll { it.id == friendId }
            saveFriends(friends)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Обновить локацию друга
     */
    fun updateFriendLocation(friendId: String, location: FriendLocation): Boolean {
        return try {
            val friends = getFriends("current_user").toMutableList()
            val index = friends.indexOfFirst { it.id == friendId }
            if (index != -1) {
                friends[index] = friends[index].copy(
                    lastLocation = location,
                    status = FriendStatus.SHARING_LOCATION
                )
                saveFriends(friends)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Получить запросы в друзья
     */
    fun getFriendRequests(userId: String): List<FriendRequest> {
        return try {
            if (!requestsFile.exists()) {
                return emptyList()
            }

            val json = requestsFile.readText()
            val type = object : TypeToken<List<FriendRequest>>() {}.type
            val allRequests: List<FriendRequest> = gson.fromJson(json, type) ?: emptyList()

            // Фильтруем по получателю
            allRequests.filter { it.toUserId == userId && it.status == RequestStatus.PENDING }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Отправить запрос в друзья
     */
    fun sendFriendRequest(request: FriendRequest): Boolean {
        return try {
            val requests = loadAllRequests().toMutableList()
            requests.add(request)
            saveRequests(requests)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Принять запрос в друзья
     */
    fun acceptFriendRequest(requestId: String): Boolean {
        return try {
            val requests = loadAllRequests().toMutableList()
            val request = requests.find { it.id == requestId } ?: return false

            // Добавляем в друзья
            val newFriend = Friend(
                id = request.fromUserId,
                name = request.fromUserName,
                phone = request.fromUserPhone,
                email = "",
                status = FriendStatus.OFFLINE
            )
            addFriend(newFriend)

            // Обновляем статус запроса
            val index = requests.indexOfFirst { it.id == requestId }
            requests[index] = request.copy(status = RequestStatus.ACCEPTED)
            saveRequests(requests)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Отклонить запрос в друзья
     */
    fun rejectFriendRequest(requestId: String): Boolean {
        return try {
            val requests = loadAllRequests().toMutableList()
            val index = requests.indexOfFirst { it.id == requestId }
            if (index != -1) {
                requests[index] = requests[index].copy(status = RequestStatus.REJECTED)
                saveRequests(requests)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveFriends(friends: List<Friend>) {
        val json = gson.toJson(friends)
        friendsFile.writeText(json)
    }

    private fun loadAllRequests(): List<FriendRequest> {
        return try {
            if (!requestsFile.exists()) {
                return emptyList()
            }
            val json = requestsFile.readText()
            val type = object : TypeToken<List<FriendRequest>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveRequests(requests: List<FriendRequest>) {
        val json = gson.toJson(requests)
        requestsFile.writeText(json)
    }

    /**
     * Создать демо-данные
     */
    private fun createDemoFriends() {
        val demoFriends = listOf(
            Friend(
                id = "demo_friend_1",
                name = "Алия",
                phone = "+996 555 123 456",
                email = "aliya@example.com",
                status = FriendStatus.SHARING_LOCATION,
                shareLocation = true,
                lastLocation = FriendLocation(
                    lat = 42.8766,
                    lon = 74.5708,
                    timestamp = System.currentTimeMillis(),
                    address = "пр. Чуй, Бишкек"
                )
            ),
            Friend(
                id = "demo_friend_2",
                name = "Айдана",
                phone = "+996 555 234 567",
                email = "aidana@example.com",
                status = FriendStatus.ONLINE,
                shareLocation = false
            ),
            Friend(
                id = "demo_friend_3",
                name = "Жанара",
                phone = "+996 555 345 678",
                email = "zhanara@example.com",
                status = FriendStatus.OFFLINE,
                shareLocation = false
            )
        )
        saveFriends(demoFriends)
    }
}

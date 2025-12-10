package com.example.saktahahathonv1.friends

data class Friend(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val photoUrl: String? = null,
    val lastLocation: FriendLocation? = null,
    val status: FriendStatus = FriendStatus.OFFLINE,
    val shareLocation: Boolean = false
)

data class FriendLocation(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val address: String? = null
)

enum class FriendStatus {
    ONLINE,
    OFFLINE,
    SHARING_LOCATION
}

data class FriendRequest(
    val id: String,
    val fromUserId: String,
    val fromUserName: String,
    val fromUserPhone: String,
    val toUserId: String,
    val timestamp: Long,
    val status: RequestStatus = RequestStatus.PENDING
)

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}

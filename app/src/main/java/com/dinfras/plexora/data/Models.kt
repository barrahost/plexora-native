package com.dinfras.plexora.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class XtreamCredentials(
    val url: String,
    val username: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val auth: Int = 0,
    val status: String = "",
    @Json(name = "exp_date") val expDate: String? = null,
)

@JsonClass(generateAdapter = true)
data class AccountInfo(
    @Json(name = "user_info") val userInfo: UserInfo?,
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
)

@JsonClass(generateAdapter = true)
data class XtreamChannel(
    val num: Int = 0,
    val name: String = "",
    @Json(name = "stream_id") val streamId: Int,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "category_id") val categoryId: String = "",
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "tv_archive") val tvArchive: Int = 0,
)

// Association device_id -> playlist, table Supabase "devices"
@JsonClass(generateAdapter = true)
data class DeviceProvision(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "server_url") val serverUrl: String,
    val username: String,
    val password: String,
    val label: String? = null,
)

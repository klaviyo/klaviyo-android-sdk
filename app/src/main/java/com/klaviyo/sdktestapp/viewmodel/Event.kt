package com.klaviyo.sdktestapp.viewmodel

import com.klaviyo.analytics.networking.requests.ApiRequest
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

data class Event(
    val id: String,
    val type: String,
    val url: URL,
    val queuedTime: Date = Date(),
    val startTime: Date? = null,
    val endTime: Date? = null,
    val state: State = State.Queued,
    val httpMethod: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    val requestBody: String? = null,
    val responseCode: Int? = null,
    val response: String? = null,
) {

    private companion object {
        private val format = SimpleDateFormat("HH:mm:ss", Locale.US)
    }

    enum class State {
        Queued,
        Pending,
        Retrying,
        Failed,
        Complete
    }

    val host = "${url.protocol}://${url.host}"
    val endpoint = "${url.path}?" + query.map { (k, v) -> "$k=$v" }.joinToString("&")
    val formattedHeaders: String = JSONObject(headers).toString(2)
    val formattedBody = try {
        requestBody?.let { JSONObject(requestBody).toString(2) } ?: ""
    } catch (e: JSONException) {
        ""
    }

    constructor(apiRequest: ApiRequest) : this(
        id = apiRequest.uuid,
        type = apiRequest.type,
        url = apiRequest.url,
        queuedTime = Date(apiRequest.queuedTime),
        startTime = apiRequest.startTime?.let { Date(it) },
        endTime = apiRequest.endTime?.let { Date(it) },
        state = when (apiRequest.state) {
            "Unsent" -> State.Queued
            "Inflight" -> State.Pending
            "PendingRetry" -> State.Retrying
            "Complete" -> State.Complete
            else -> State.Failed
        },
        httpMethod = apiRequest.httpMethod,
        headers = apiRequest.headers,
        query = apiRequest.query,
        requestBody = apiRequest.requestBody,
        responseCode = apiRequest.responseCode,
        response = apiRequest.responseBody,
    )

    fun formatDate(date: Date): String = format.format(date)
}

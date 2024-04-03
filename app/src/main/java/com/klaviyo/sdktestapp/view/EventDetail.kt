package com.klaviyo.sdktestapp.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.klaviyo.sdktestapp.viewmodel.Event
import java.net.URL
import java.util.Date

// TODO extract to a styling file of some kind
private val pad = 8.dp

@Composable
fun EventDetail(
    event: Event,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.then(
            Modifier
                .fillMaxWidth()
                .padding(pad)
                .verticalScroll(rememberScrollState())
        ),
        verticalArrangement = Arrangement.spacedBy(pad)
    ) {
        RequestComponent(
            title = "Request",
            headerIcon = { m -> EventStateIcon(eventState = event.state, modifier = m) }
        ) {
            EventDetailItem(title = "Host", body = event.host)
            EventDetailItem(title = "Endpoint", body = event.endpoint)
            EventDetailItem(title = "HTTP Method", body = event.httpMethod)
            EventDetailItem(title = "Queued Time", body = event.queuedTime.toString())
            EventDetailItem(title = "Request Time", body = event.startTime?.toString() ?: "Unsent")
            EventDetailItem(title = "Headers", body = event.formattedHeaders)
            EventDetailItem(title = "Body", body = event.formattedBody)
        }
        event.responseCode?.let {
            RequestComponent("Response") {
                val seconds = event.endTime?.let {
                    val duration = it.time - event.startTime!!.time
                    duration.toDouble() / 1000
                } ?: ""

                EventDetailItem(title = "Status code", body = event.responseCode.toString())
                EventDetailItem(title = "Response Time", body = event.endTime.toString())
                EventDetailItem(title = "Duration", body = "${seconds}s")

                if (!event.response.isNullOrEmpty()) {
                    EventDetailItem(title = "Body", body = event.response)
                }
            }
        }
    }
}

@Composable
private fun RequestComponent(
    title: String,
    headerIcon: @Composable (modifier: Modifier) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    ConstraintLayout(
        modifier = Modifier.fillMaxWidth()
    ) {
        val (titleText, icon) = createRefs()
        Text(
            text = title,
            style = MaterialTheme.typography.h6,
            modifier = Modifier.constrainAs(titleText) {
                start.linkTo(parent.start)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
            }
        )
        headerIcon(
            Modifier.constrainAs(icon) {
                end.linkTo(parent.end)
                top.linkTo(parent.top)
                bottom.linkTo(parent.bottom)
            }
        )
    }

    Surface(
        shape = RoundedCornerShape(pad),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = pad)
    ) {
        Column(
            modifier = Modifier.padding(pad),
            verticalArrangement = Arrangement.spacedBy(pad),
            content = content
        )
    }
}

@Composable
private fun EventDetailItem(title: String, body: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold)
        Text(text = body, style = MaterialTheme.typography.body2, fontFamily = FontFamily.Monospace)
    }
}

@Preview(
    group = "Events",
    showBackground = true,
    backgroundColor = 0xFFF0EAE2
)
@Composable
private fun QueuedRequest() {
    EventDetail(
        event = Event(
            id = "preview",
            type = "Event",
            url = URL("https://a.klaviyo.com/client/events/"),
            queuedTime = Date(1234567890100),
            startTime = Date(1234567891200),
            endTime = Date(1234567892300),
            state = Event.State.Queued,
            httpMethod = "POST",
            headers = mapOf("Accepts" to "preview/header"),
            query = mapOf("company_id" to "fakeId"),
            requestBody = """{"data":{"type":"profile","attributes":{"email":"evan.masseau+demo@gmail.com","phone_number":"+18024240572","external_id":"test","anonymous_id":"1068d3ee-b6fc-40cb-9d61-21faa5a37821"}}}""",
            responseCode = null
        )
    )
}

@Preview(
    group = "Events",
    showBackground = true,
    backgroundColor = 0xFFF0EAE2
)
@Composable
private fun CompleteRequest() {
    EventDetail(
        event = Event(
            id = "preview",
            type = "Profile",
            url = URL("https://a.klaviyo.com/client/profiles/"),
            queuedTime = Date(1234567890100),
            startTime = Date(1234567891200),
            endTime = Date(1234567892300),
            state = Event.State.Complete,
            httpMethod = "POST",
            headers = mapOf("Accepts" to "preview/header"),
            query = mapOf("company_id" to "fakeId"),
            requestBody = """
                {
                    "data": {
                        "type": "profile",
                        "attributes": {
                            "email": "evan.masseau+demo@gmail.com",
                            "phone_number": "+18024240572",
                            "external_id": "test",
                            "anonymous_id": "1068d3ee-b6fc-40cb-9d61-21faa5a37821"
                        }
                    }
                }
            """.trimIndent(),
            responseCode = 202,
            response = ""
        )
    )
}

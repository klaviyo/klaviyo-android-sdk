/**
 * Updates the data-klaviyo-profile attribute on the document head
 *
 * @param external_id
 * @param email
 * @param phone_number
 * @param anonymous_id
 * @returns {boolean}
 */
window.setProfile = function (external_id, email, phone_number, anonymous_id) {
    document.head.setAttribute("data-klaviyo-profile",
        JSON.stringify(
            {
                external_id: external_id,
                email: email,
                phone_number: phone_number,
                anonymous_id: anonymous_id
            }
        )
    )

    return true
}

/**
 * Dispatches a lifecycle event to be detected by klaviyo.js
 *
 * @param type - The type of lifecycle event to dispatch: "foreground" | "background"
 * @param session - Session behavior: "persist" | "restore" | "purge"
 */
window.dispatchLifecycleEvent = function (type, session) {
    document.head.dispatchEvent(
        new CustomEvent(
            'lifecycleEvent',
            {
                detail: {
                    type: type,
                    session: session
                }
            }
        )
    )

    return true
}

/**
 * Dispatches an analytics event to be detected by klaviyo.js
 *
 * @param metric - The metric of the event
 * @param strProperties - Properties of the event as a JSON string
 */
window.dispatchAnalyticsEvent = function (metric, strProperties) {
    document.head.dispatchEvent(
        new CustomEvent(
            'analyticsEvent',
            {
                detail: {
                    metric: metric,
                    properties: JSON.parse(strProperties)
                }
            }
        )
    )

    return true
}

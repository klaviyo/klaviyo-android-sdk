// Defines simple methods for bridging data from the native SDK into the klaviyo.js onsite-in-app-forms module

/**
 * Updates the data-klaviyo-profile attribute on the document head
 *
 * @param external_id
 * @param email
 * @param phone_number
 * @param anonymous_id
 * @returns {boolean}
 */
window.profileMutation = function (external_id, email, phone_number, anonymous_id) {
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
 */
window.lifecycleEvent = function (type) {
    document.head.dispatchEvent(
        new CustomEvent(
            'lifecycleEvent',
            {
                detail: {
                    type: type
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
 * @param properties - Properties of the event as a JavaScript object
 * @param uuid - Unique ID of the event
 * @param time - Timestamp of the event
 * @param value - Value of the event
 *
 */
window.profileEvent = function (metric, uuid, time, value, properties) {
    document.head.dispatchEvent(
        new CustomEvent(
            'profileEvent',
            {
                detail: {
                    metric: metric,
                    unique_id: uuid,
                    time: time,
                    value: value,
                    properties: properties,
                }
            }
        )
    )

    return true
}

/**
 * Force open a Klaviyo form with the given formId.
 *
 * Note: This is just standard klaviyo.js functionality, not an onsite-in-app-forms listener.
 *
 * This is a simplified version of the Klaviyo object, see public docs here:
 * @see https://developers.klaviyo.com/en/docs/introduction_to_the_klaviyo_object
 * TL;DR: This allows you to enqueue operations before klaviyo.js script loads,
 *  and those operations will be executed once onsite forms loads.
 *
 * @param formId
 * @returns {boolean}
 */
window.openForm = function (formId) {
    window._klOnsite = window._klOnsite || [];
    window._klOnsite.push(['openForm', formId]);
    return true
}

/**
 * Close a Klaviyo form by formId, or any currently open forms
 *
 * @param {string} formId
 * @returns {boolean}
 */
window.closeForm = function (formId) {
    document.head.dispatchEvent(
        new CustomEvent(
            'closeForm',
            {
                detail: {
                    formId: formId
                }
            }
        )
    )
    return true
}

window.setSafeArea = function(left, top, right, bottom) {
    document.documentElement.style.setProperty('--safe-area-inset-left', left + 'px');
    document.documentElement.style.setProperty('--safe-area-inset-top', top + 'px');
    document.documentElement.style.setProperty('--safe-area-inset-right', right + 'px');
    document.documentElement.style.setProperty('--safe-area-inset-bottom', bottom + 'px');
    return true;
 }

// Notify the SDK over the native bridge that these local JS scripts are initialized
var bridgeName = document.head.getAttribute("data-native-bridge-name") || ""

if (window[bridgeName] && window[bridgeName].postMessage) {
    window[bridgeName].postMessage(JSON.stringify({type: "jsReady"}))
} else {
    console.error("Invalid native bridge: " + bridgeName)
}
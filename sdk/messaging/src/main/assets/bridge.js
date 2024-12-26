/**
 * WARNING This JS file should be ES5 compatible to support older versions of Android WebView
 *
 * Inject this script file from native code into the head of the HTML document
 * Format string like this, where strWrapperScript is the contents of this file
 * and strJsonConfig is the json-encoded handoff parameters detailed below
 * Always be careful to put quotes around your string arguments! *
 *
 *    strWrapperScript + "('" + strJsonConfig + "');"
 *
 * @param strJsonConfig - JSON encoded handoff dictionary containing:
 *    bridgeName: String - name of the native message handler
 *    defaultAction: String - default action keyword for data posted from JS -> Native
 */
(function bridgeWrapper(strJsonConfig) {

    //Initialize web wrapper object
    window.WebViewBridge = new Bridge(JSON.parse(strJsonConfig));

    if (/complete|interactive|loaded/.test(document.readyState)) {
        // In case the document has finished parsing, document's readyState will
        // be one of "complete", "interactive" or (non-standard) "loaded".
        WebViewBridge.initialize();
    } else {
        // The document is not ready yet, so wait for the DOMContentLoaded event
        // https://developer.mozilla.org/en-US/docs/Web/API/Document/DOMContentLoaded_event
        document.addEventListener('DOMContentLoaded', function () {
            WebViewBridge.initialize();
        }, false);
    }

    /**
     * Bridge object to handle communication between JavaScript and Native
     */
    function Bridge(opts) {
        opts = opts || {};

        return {
            opts: opts,

            initialize: function () {
                WebViewBridge.postMessageToNative("documentReady", {});

                onElementAvailable("button.klaviyo-close-form", () => {
                    onElementRemoved("button.klaviyo-close-form", () => {
                        WebViewBridge.postMessageToNative("close", {});
                    });
                });

                // add a listener to the message bridge init function, so we only listen to the bus if it's ready
                window.addEventListener('inAppFormsInitMessage', function(event) {
                    const supportedEventTypes = event.detail.eventTypes;
                    console.log('Supporting message types:', supportedEventTypes);

                    const messageBus = window['inAppFormsMessageBus']?.messageBus;
                    if (!messageBus) {
                        console.error('Message bus not found');
                        return;
                    } else {
                        console.log('Found the message bus!');
                    }

                    supportedEventTypes.forEach(eventType => {
                        console.log('Registering listener for eventType: ', eventType);
                        messageBus.addEventListener(eventType.toString(), function(subEvent) {
                            WebViewBridge.postMessageToNative(subEvent.type, subEvent.detail);
                        });
                    });
                }, false);
            },

            /**
             * Default keyword for JS -> Native messaging
             */
            defaultAction: opts.defaultAction || "message",

            /**
             * Bool test if this is Android WebView with registered JavaScript interface
             */
            isAndroid: function () {
                return !!window[opts.bridgeName];
            },

            /**
             * Bool test if this is an Apple WKWebView
             */
            isApple: function () {
                return !!window.webkit && !!window.webkit.messageHandlers[opts.bridgeName];
            },

            /**
             * Method to post string message to native layer
             *
             * Native layer should implement a common interface
             * for reacting to messages of particular type
             *
             * @param type {String}
             * @param data {Object}
             */
            postMessageToNative: function (type, data) {
                var payload = {
                    type: type || this.defaultAction,
                    data: data || {}
                };

                try {
                    if (this.isApple()) {
                        window.webkit.messageHandlers[opts.bridgeName].postMessage(payload);

                    } else if (this.isAndroid()) {
                        window[opts.bridgeName].postMessage(JSON.stringify(payload));
                    }
                } catch (e) { }
            },
        }
    }

    /**
     * Send all console output to native layer
     */
    if (!!window.WebViewBridge.opts.linkConsole) {
        ["log", "warn", "error"].forEach(function (method) {
            var _method = console[method];
            console[method] = function () {
                var args = Array.prototype.slice.call(arguments, 0),
                    message;

                try {
                    message = JSON.stringify(args);
                } catch (e) {
                    message = "Couldn't parse arguments.";
                }

                WebViewBridge.postMessageToNative("console", {
                    level: method,
                    message: message
                });

                return _method.apply(console, arguments);
            };
        });
    }
})

private fun isInternetConnected(context: Context): Boolean {
    var result = false
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = connectivityManager.activeNetwork ?: return false
        val connections = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            connections.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> true
            else -> false
        }
    } else {
        val networkInfo: NetworkInfo? = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnected ?: false
    }
}

/** This returns the userAgent. I wasn't sure if that's what we wanted */
private fun performNetworkRequests(url: URL, payload: Payload): String {
    private fun buildRequest(): String {
        val urlConnection = (HttpURLConnection) url.openConnection()
        urlConnection.setReadTimeout(5000);
        /** the time to establish the connection with the remote host */
        urlConnection.setConnectTimeout(5000);
        try {
            val inputStream = BufferedInputStream(urlConnection.getInputStream())
            /** This only works for above Android 2.1, from what I could find
            Alternative is to use a WebView: String userAgent=new WebView(this).getSettings().getUserAgentString();
             */
            String userAgent = System.getProperty("http.agent");
            readStream(inputStream)
        } 
        finally {
            urlConnection.disconnect()
        }
        return userAgent
    }
    buildRequest()
}

public void track(Payload payload) {
    performNetworkRequests('klaviyo.com/track', payload)
}

public void identify(Payload payload) {
    performNetworkRequests('klaviyo.com/identify', payload)
}
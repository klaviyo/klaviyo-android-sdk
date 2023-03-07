package android.util

object MockLog {
    @JvmStatic
    fun v(tag: String?, msg: String?, t: Throwable?): Int {
        println("VERBOSE: $tag $msg $t")
        return 0
    }

    @JvmStatic
    fun d(tag: String?, msg: String?, t: Throwable?): Int {
        println("DEBUG: $tag $msg $t")
        return 0
    }

    @JvmStatic
    fun i(tag: String?, msg: String?, t: Throwable?): Int {
        println("INFO: $tag $msg $t")
        return 0
    }

    @JvmStatic
    fun wtf(tag: String?, msg: String?, t: Throwable?): Int {
        println("WARN: $tag $msg $t")
        return 0
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, t: Throwable?): Int {
        println("ERROR: $tag $msg $t")
        return 0
    }
}

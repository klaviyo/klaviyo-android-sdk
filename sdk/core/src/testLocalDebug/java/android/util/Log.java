package android.util;

public class Log {

    public static int v(String tag, String msg, Throwable t) {
        return MockLog.v(tag, msg, t);
    }

    public static int d(String tag, String msg, Throwable t) { return MockLog.d(tag, msg, t); }

    public static int i(String tag, String msg, Throwable t) { return MockLog.i(tag, msg, t); }

    public static int e(String tag, String msg, Throwable t) {
        return MockLog.e(tag, msg, t);
    }

    public static int wtf(String tag, String msg, Throwable t) {
        return MockLog.wtf(tag, msg, t);
    }
}
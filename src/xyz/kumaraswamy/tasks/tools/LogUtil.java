package xyz.kumaraswamy.tasks.tools;

// a class that helps to log
// things easily that makes logs
// easily readable while debugging
// the program

import android.util.Log;
import androidx.annotation.NonNull;

public class LogUtil {

    // the log tag
    private final String tag;

    public LogUtil(@NonNull String tag) {
        this.tag = tag;
    }

    /**
     * Logs a simple message
     *
     * @param message Message to be logged
     */

    public void log(Object message) {
        Log.i(tag, "[info]\t" + message);
    }

    /**
     * Logs a simple message with the
     * method name and the value
     *
     * @param method  The name of the method
     * @param message The message to be logged
     */

    public void log(String method, Object message) {
        Log.i(tag, "[info]\t" + method + "()\t" + message);
    }

    /**
     * Logs all the messages
     *
     * @param messages THe messages to be logged
     */

    public void log(Object... messages) {
        for (Object object : messages)
            log(object);
    }

    /**
     * Logs all the messages with the method names
     *
     * @param method   The name of the method
     * @param messages The messages that needs to be logged
     */

    public void log(String method, Object... messages) {
        for (Object object : messages)
            log(method, object);
    }

    /**
     * Creates a warning message
     *
     * @param message Message to be logged
     */

    public void warn(String message) {
        Log.w(tag, "[warn]\t" + message);
    }
}

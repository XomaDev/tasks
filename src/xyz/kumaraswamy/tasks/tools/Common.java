// a class that helps the extension with
// the commons things used multiple times
// or to separate things from the rest

package xyz.kumaraswamy.tasks.tools;

import android.content.Context;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import bsh.EvalError;
import bsh.Interpreter;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;

public class Common {

    // the context to handle things

    private final Context context;

    public Common(@Nullable Context context) {
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    /**
     * Declares the parms as the variables (objects)
     * for the beanshell interpreter
     *
     * @param objects     The objects that needs to be defined
     * @param interpreter The interpreter to define on
     */

    public void declareObjects(Object[] objects, Interpreter interpreter) throws EvalError {
        // give access to the whole object array
        interpreter.set("args", objects);

        // set them as variable ("val<access_num>")
        for (int i = 0; i < objects.length; i++)
            interpreter.set("val" + i, objects[i]);
    }

    /**
     * Acquires wake lock with the wakeLockName
     * 'CommonWakeLock'
     */

    public void acquireWakeLock() {
        final String wakeLockName = "CommonWakeLock";

        ((PowerManager) getService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, wakeLockName).acquire();
    }

    public Object getService(String name) {
        if (context == null)
            throw new IllegalArgumentException();
        return context.getSystemService(name);
    }

    /**
     * The parsing number types
     */

    public static final int NUMBER_TYPE_INT = 0;
    public static final int NUMBER_TYPE_LONG = 1;

    /**
     * Parses and Object into a requested form.
     * This is helpful sometimes
     */

    public static Object parseNumber(Object object, int type) {
        final String number = String.valueOf(object);
        try {
            return parseNumber(type, number);
        } catch (NumberFormatException ignored) {
            throw new YailRuntimeError("Unable to parse as number '" + number + "'",
                    "NumberFormatException");
        }
    }

    private static Object parseNumber(int type, String number) {
        switch (type) {
            case NUMBER_TYPE_INT:
                return  Integer.parseInt(number);
            case NUMBER_TYPE_LONG:
                return Long.parseLong(number);
        }
        return null;
    }
}

// a class that helps the extension with
// the commons things used multiple times
// or to separate things from the rest

package xyz.kumaraswamy.tasks;

import android.content.Context;
import android.os.PowerManager;
import androidx.annotation.Nullable;
import bsh.EvalError;
import bsh.Interpreter;

import static java.lang.String.valueOf;

public class Common {

    // the context to handle things

    private final Context context;

    public Common(@Nullable Context context) {
        this.context = context;
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
        if (context == null)
            throw new IllegalArgumentException();

        final String wakeLockName = "CommonWakeLock";

        ((PowerManager) getService(Context.POWER_SERVICE)).newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, wakeLockName).acquire();
    }

    @SuppressWarnings("ConstantConditions")
    public Object getService(String name) {
        return context.getSystemService(name);
    }

    /**
     * Gets the string representation of an object
     * Anything that is in form of an AppInventor Int
     * which has its superclass as Number will be handled.
     *
     * @param object The object to perform action
     */

    public static String treatNumsGetString(Object object) {
        if (object instanceof String) {
            return (String) object;
        } else if (object instanceof Number) {
            return valueOf(((Number) object).intValue());
        }
        return valueOf(object);
    }
}

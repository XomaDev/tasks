package xyz.kumaraswamy.tasks.template;

import android.content.Context;
import android.util.Log;
import bsh.EvalError;
import bsh.Interpreter;
import com.google.appinventor.components.runtime.util.YailList;
import xyz.kumaraswamy.tasks.Common;
import xyz.kumaraswamy.tasks.Tasks;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;

public class Load {

    private static final String TAG = "Load";
    private final Tasks tasks;

    private static final Common common = new Common();

    public Load(final String assetName, Object[] parms, Context context, Tasks tasks) throws IOException, EvalError {
        this.tasks = tasks;
        BufferedInputStream bufInputStream = new BufferedInputStream(
                context.getAssets().open(assetName));
        final StringBuilder content = new StringBuilder();
        try {
            do {
                int ch = bufInputStream.read();
                if (ch == -1)
                    break;
                content.append((char) ch);
            } while (true);
        } finally {
            bufInputStream.close();
        }
        Log.d(TAG, "Load: " + Arrays.toString(parms));

        final Interpreter interpreter = new Interpreter();
        interpreter.set("t", new This());
        common.declareObjects(parms, interpreter);
        interpreter.eval(content.toString());
    }

    @SuppressWarnings({"unused", "InnerClassMayBeStatic"})
    public class This {
        private static final String TAG = "Load";

        public void create(final Object base, final Object id) {
            Log.d(TAG, "create: " + base + " " + id);
            tasks.CreateComponent(base, (String) id);
        }

        public void invoke(final Object id, final Object component, Object methodName, Object[] args) {
            if (args == null)
                args = new Object[0];
            Log.d(TAG, "invoke: " + component + " " + methodName + " " + Arrays.toString(args));
            tasks.CreateFunction((String) id, (String) component,
                    (String) methodName, YailList.makeList(args));
            tasks.CallFunction((String) id);
        }
    }
}

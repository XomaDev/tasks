// class that can parse (converts) the
// Java template into block = valent commands
// (functions, commands) that can be reused and
// helps reduces the blocks


package xyz.kumaraswamy.tasks.template;

import android.content.Context;
import bsh.EvalError;
import bsh.Interpreter;
import com.google.appinventor.components.runtime.util.YailList;
import xyz.kumaraswamy.tasks.Common;
import xyz.kumaraswamy.tasks.LogUtil;
import xyz.kumaraswamy.tasks.Tasks;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Arrays;

public class Load {

    private final Tasks tasks;

    private final Common common = new Common(null);

    // the logger that logs messages
    private static final LogUtil log = new LogUtil("Load");

    /**
     * Loads the asset file into a string and
     * executed the code inside that using the beanshell-interpreter
     *
     * @param assetName The name of the asset
     * @param parms     The arguments that can be accessed using 'val<parm_number>'
     * @throws IOException Was unable to read the asset file
     * @throws EvalError   Unformatted or Illegal input to eval
     */

    public Load(final String assetName, Object[] parms,
                Context context, Tasks tasks) throws IOException, EvalError {
        this.tasks = tasks;

        // read the text from the asset
        // file into string

        BufferedInputStream bufInputStream = new BufferedInputStream(
                context.getAssets().open(assetName));
        final StringBuilder content = new StringBuilder();
        try {
            do {
                int ch = bufInputStream.read();
                // break, it's the end
                if (ch == -1)
                    break;
                content.append((char) ch);
            } while (true);
        } finally {
            bufInputStream.close();
        }
        log.log("load", parms);

        final Interpreter interpreter = new Interpreter();

        // define an object names 't'
        // from which they can access the methods
        // of the class 'This'

        interpreter.set("t", new This());

        // also declare the parms
        common.declareObjects(parms, interpreter);

        // execute that
        interpreter.eval(content.toString());
    }


    /**
     * Class that has methods that will be
     * called by the program in the asset to
     * do different things (create, invoke...)
     */

    @SuppressWarnings({"unused", "InnerClassMayBeStatic"})
    public class This {

        /**
         * Creates the component, this is = to using the
         * CreateComponent block
         *
         * @param base The package name, a shortname of the source
         * @param id   Unique ID of the component
         */

        public void create(final Object base, final Object id) {
            log.log("create", "creating component");
            log.log(base, id);
            tasks.CreateComponent(base, (String) id);
        }

        /**
         * Creates a function with the necessary arguments
         * and inputs and calls it. This is = to using
         * the CreateFunction block and using the CallFunction
         * block
         *
         * @param id         The ID of the function
         * @param component  The component key to invoke on
         * @param methodName The method or the block name
         * @param args       Invoke arguments
         */

        public void invoke(final Object id, final Object component, Object methodName, Object[] args) {
            if (args == null)
                args = new Object[0];
            log.log("Invoking function", Arrays.toString(new Object[]{
                    id, component, methodName, args}));
            // declare a function
            tasks.CreateFunction((String) id, (String) component,
                    (String) methodName, YailList.makeList(args));
            // call the function
            tasks.CallFunction((String) id);
        }
    }
}

// a class that helps the extension with
// the commons things used multiple times

package xyz.kumaraswamy.tasks;

import bsh.EvalError;
import bsh.Interpreter;

public class Common {

    /**
     * Declares the parms as the variables (objects)
     * for the beanshell interpreter
     * @param objects The objects that needs to be defined
     * @param interpreter The interpreter to define on
     */

    public void declareObjects(Object[] objects, Interpreter interpreter) throws EvalError {
        // give access to the whole object array
        interpreter.set("args", objects);

        // set them as variable ("val<access_num>")
        for (int i = 0; i < objects.length; i++)
            interpreter.set("val" + i, objects[i]);
    }
}

package xyz.kumaraswamy.tasks;

import bsh.EvalError;
import bsh.Interpreter;

public class Common {
    public void declareObjects(Object[] parms, Interpreter interpreter) throws EvalError {
        interpreter.set("args", parms);
        for (int i = 0; i < parms.length; i++) {
            interpreter.set("val" + i, parms[i]);
        }
    }
}

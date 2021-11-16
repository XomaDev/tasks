package xyz.kumaraswamy.tasks.inbuilt;

import android.util.Log;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailList;
import gnu.mapping.Procedure;
import xyz.kumaraswamy.tasks.ComponentManager;
import xyz.kumaraswamy.tasks.data.Procedures;

public class Fun extends Inbuilt {

    private static final String TAG = "Fun";

    private final ComponentManager.FForm fform;

    public Fun(ComponentContainer container) {
        super(container);
        fform = (ComponentManager.FForm) container.$form();
    }

    /**
     * Invokes the procedureN to obtain the result
     *
     * @param args Arguments for the procedure
     */

    public void Invoke(String name, YailList args) throws Throwable {
        Log.d(TAG, "Invoke(" + name + ", " + args + ")");
        Log.d(TAG, "Invoke " + Procedures.PROCEDURES);
        Procedure procedureN = Procedures.PROCEDURES.get(name);
        if (procedureN == null)
            throw new YailRuntimeError("Unable to find procedure '" +
                    name + "'", TAG);
        Processed(procedureN.applyN(args.toArray()));
    }

    public void Processed(Object result) {
        Log.d(TAG, "Processed() result " + result);
        fform.getListener().eventRaised(this,
                "Processed", new Object[] {result});
    }
}

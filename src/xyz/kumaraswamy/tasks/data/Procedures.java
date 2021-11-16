package xyz.kumaraswamy.tasks.data;

import com.google.appinventor.components.runtime.Form;
import gnu.lists.LList;
import gnu.mapping.ProcedureN;
import gnu.mapping.Symbol;

import java.lang.reflect.Field;
import java.util.HashMap;

public class Procedures {

    /**
     * Holds the ProceduresN as a static variable
     * so that it can be accessed to invoke later
     */

    public static final HashMap<String, ProcedureN> PROCEDURES = new HashMap<>();

    private static final String FIELD = "global$Mnvars$Mnto$Mncreate";

    public static void registerName(String name, Form form) throws Throwable {
        Field field = form.getClass().getField(FIELD);
        LList list = (LList) field.get(form);

        ProcedureN procedureN = null;

        for (Object pair : list) {
            if (LList.Empty.equals(pair)) {
                // make sure its not empty list
                continue;
            }
            LList lList = (LList) pair;
            if (((Symbol) lList.get(0)).getName().equals("p$" + name)) {
                procedureN = (ProcedureN) lList.get(1);
            }
        }
        if (procedureN == null)
            return;
        PROCEDURES.put(name, (ProcedureN) procedureN.apply0());
    }
}

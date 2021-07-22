package xyz.kumaraswamy.tasks;

import android.util.Log;
import com.google.appinventor.components.runtime.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class MethodHandler {
    private static final String TAG = "MethodHandler";

    public static Object invokeComponent(final Component component, final String methodName, Object[] params) throws InvocationTargetException, IllegalAccessException {
        Method method = findMethod(component.getClass().getMethods(), methodName, params.length);

        if (method == null) {
            Log.e(TAG, "invokeComponent: Method not found for name \"" + methodName + "\"!");
            return null;
        }

        final Class<?>[] mRequestedMethodParameters = method.getParameterTypes();
        final ArrayList<Object> mParametersArrayList = new ArrayList<>();

        for (int i = 0; i < mRequestedMethodParameters.length; i++) {
            if ("int".equals(mRequestedMethodParameters[i].getName())) {
                mParametersArrayList.add(Integer.parseInt(params[i].toString()));
            } else if ("float".equals(mRequestedMethodParameters[i].getName())) {
                mParametersArrayList.add(Float.parseFloat(params[i].toString()));
            } else if ("double".equals(mRequestedMethodParameters[i].getName())) {
                mParametersArrayList.add(Double.parseDouble(params[i].toString()));
            } else if ("java.lang.String".equals(mRequestedMethodParameters[i].getName())) {
                mParametersArrayList.add(params[i].toString());
            } else if ("boolean".equals(mRequestedMethodParameters[i].getName())) {
                mParametersArrayList.add(Boolean.parseBoolean(params[i].toString()));
            } else {
                mParametersArrayList.add(params[i]);
            }
        }
        return emptyIfNull(method.invoke(component, mParametersArrayList.toArray()));
    }

    private static Method findMethod(Method[] methods, String name, int parameterCount) {
        name = name.replaceAll("[^a-zA-Z0-9]", "");
        for (Method method : methods) {
            if (method.getName().equals(name)
                    && method.getParameterTypes().length == parameterCount)
                return method;
        }
        return null;
    }

    private static Object emptyIfNull(Object o) {
        return (o == null) ? "" : o;
    }
}

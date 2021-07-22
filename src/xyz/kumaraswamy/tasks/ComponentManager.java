package xyz.kumaraswamy.tasks;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Window;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

public class ComponentManager {

    private static final String COMPONENTS_PREFIX = "com.google.appinventor.components.runtime.";
    private static final String TAG = "ComponentManager";

    private final HashMap<String, Component> componentsBuilt = new HashMap<>();
    private final HashMap<String, String> componentsString = new HashMap<>();

    private ComponentsBuiltListener listener = null;

    private AActivity activity;
    private FForm form;

    public interface ComponentsBuiltListener {
        void componentsBuilt();
    }

    public ComponentManager(final Context context, final ComponentsBuiltListener listener) {
        try {
            initialise(context);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return;
        }
        this.listener = listener;
    }

    public void createComponents(final HashMap<String, String> components) {
        final Object[] keyset = components.keySet().toArray();
        final Object lastkey = keyset[keyset.length - 1];

        for (final Object key : keyset) {
            final String key1 = key.toString();
            final String source = components.get(key1);

            createComponent(source, key1, key1.equals(lastkey));
            Log.d(TAG, "IComponent: for key " + key1 + " - " + source);
        }
    }

    public Activity activity() {
        return activity;
    }

    public FForm form() {
        return form;
    }

    private void initialise(final Context context) throws IllegalAccessException {
        activity = new AActivity(context);
        form = new FForm(context);

        final Field windowField = getFieldName("mWindow", activity);
        if (windowField == null) {
            return;
        }
        windowField.setAccessible(true);

        final Window dummyWindow = new Dialog(context).getWindow();

        if (dummyWindow != null) {
            windowField.set(activity, dummyWindow);
            windowField.set(form, dummyWindow);
        }

        final Field componentField = getFieldName("mComponent", activity);
        if (componentField == null) {
            return;
        }
        componentField.setAccessible(true);

        final String packageName = activity.getPackageName();
        final ComponentName componentName = new ComponentName(packageName, packageName + ".Screen1");

        if (componentName == null) {
            return;
        }

        componentField.set(activity, componentName);
        componentField.set(form, componentName);
    }

    private Field getFieldName(final String name, final Activity activity) {
        try {
            return activity.getClass().getSuperclass().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void createComponent(final String source, final String key, final boolean isLastKey) {
        Class<?> baseClass;
        Constructor<?> baseConstructor;
        try {
            baseClass = Class.forName(source);
            baseConstructor = baseClass.getConstructor(ComponentContainer.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            return;
        }

        try {
            final Component component = (Component) baseConstructor.newInstance(form);

            componentsBuilt.put(key, component);
            componentsString.put(component.toString(), key);

            if (isLastKey) {
                listener.componentsBuilt();
            }
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public Component component(String key) {
        return componentsBuilt.getOrDefault(key, null);
    }

    private static class FForm extends Form {
        public FForm(final Context context) {
            attachBaseContext(context);
        }

        @Override
        public boolean canDispatchEvent(final Component component, final String str) {
            return true;
        }

        @Override
        public boolean dispatchEvent(final Component component, final String eventName,
                                     final String string, final Object[] values) {
            // TODO
            return true;
        }

        @Override
        public void dispatchGenericEvent(final Component component, final String eventName,
                                         final boolean bool, final Object[] values) {
            // TODO
        }

        @Override
        public void onDestroy() {
            // do nothing
        }

        @Override
        public void onPause() {
            // do nothing
        }

        @Override
        public void onStop() {
            // do nothing
        }

        @Override
        public void onResume() {
            // do nothing
        }
    }

    private static class AActivity extends Activity {
        public AActivity(final Context context) {
            attachBaseContext(context);
        }

        @Override
        public String getLocalClassName() {
            return "Screen1";
        }

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);
        }
    }

    public static String componentSource(final Object component) {
        if (component instanceof Component) {
            return component.getClass().getName();
        } else if (component instanceof String) {
            final String source = component.toString();
            if (TextUtils.isEmpty(source)) {
                throw new YailRuntimeError("Component source is invalid!", TAG);
            }

            boolean isSimpleName = false;
            String fullName = source;

            if (!fullName.contains(".") && Character.isLetter(fullName.charAt(0))) {
                fullName = COMPONENTS_PREFIX + fullName;
                isSimpleName = true;
            }
            try {
                Class.forName(fullName);
                return fullName;
            } catch (ClassNotFoundException e) {
                throw new YailRuntimeError(isSimpleName ? source :
                        "The component source name does not exists: " + source, TAG);
            }
        }
        throw new YailRuntimeError("Component source should be a string or component", TAG);
    }
}
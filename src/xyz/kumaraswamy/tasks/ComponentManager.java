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
import java.util.HashMap;
import java.util.Set;

public class ComponentManager {

    public interface ComponentsBuiltListener {
        void componentsCreated();
    }

    public interface EventRaisedListener {
        void eventRaised(Component component, String eventName, Object[] parms);
    }

    private static final String LOG_TAG = "ComponentManager";
    private ComponentsBuiltListener componentsCreatedListener;
    private static EventRaisedListener eventRaisedListener;

    private final HashMap<String, Component> componentsBuilt = new HashMap<>();
    private final HashMap<Component, String> componentsString = new HashMap<>();

    private AActivity activity;
    private FForm form;

    public static String getSourceString(final Object component) {
        if (component instanceof Component) {
            return component.getClass().getName();
        } else if (component instanceof String) {
            final String source = component.toString();
            if (TextUtils.isEmpty(source)) {
                throw new YailRuntimeError("Component source is invalid!", LOG_TAG);
            }

            boolean isSimpleName = false;
            String fullName = source;

            if (!fullName.contains(".") && Character.isLetter(fullName.charAt(0))) {
                fullName = "com.google.appinventor.components.runtime."  + fullName;
                isSimpleName = true;
            }
            try {
                Class.forName(fullName);
                return fullName;
            } catch (ClassNotFoundException e) {
                throw new YailRuntimeError(isSimpleName ? source :
                        "The component source name does not exists: " + source, LOG_TAG);
            }
        }
        throw new YailRuntimeError("Component source should be a string or component", LOG_TAG);
    }

    public ComponentManager(final Context context, final HashMap<String, String> components,
                            ComponentsBuiltListener componentsCreatedListener,
                            EventRaisedListener eventRaisedListener) {
        form = new FForm();
        form.init(context);

        this.activity = new AActivity();
        activity.init(context);

        try {
            final Field windowField = getFieldName("mWindow", activity);

            if (windowField == null) {
                return;
            }

            windowField.setAccessible(true);

            final Window dummyWindow = new Dialog(context).getWindow();

            if (dummyWindow == null) {
                return;
            }

            windowField.set(activity, dummyWindow);
            windowField.set(form, dummyWindow);

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
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Unable to set window or component: " + e.getMessage());
        }

        this.componentsCreatedListener = componentsCreatedListener;
        ComponentManager.eventRaisedListener = eventRaisedListener;

        final String[] keySet = components.keySet().toArray(new String[0]);
        final String lastKey = keySet[keySet.length - 1];

        for (final String key : keySet) {
            createFromSource(components.get(key), key, key.equals(lastKey));
        }
    }

    private Field getFieldName(final String name, final Activity activity) {
        try {
            return activity.getClass().getSuperclass().getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Component component(final String key) {
        if (componentsBuilt.containsKey(key)) {
            return componentsBuilt.get(key);
        }
        return null;
    }

    public Component removeComponent(final String key) {
        Component component = componentsBuilt.get(key);
        componentsString.remove(component);
        return component;
    }

    public String getKeyOfComponent(Component component) {
        return componentsString.getOrDefault(component, null);
    }

    public Set<String> usedComponents() {
        return componentsBuilt.keySet();
    }

    private void createFromSource(final String source, final String key, final boolean isLastKey) {
        Class<?> baseClass;
        Constructor<?> baseConstructor;
        try {
            baseClass = Class.forName(source);
            baseConstructor = baseClass.getConstructor(ComponentContainer.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            return;
        }

        final Constructor<?> finalBaseConstructor = baseConstructor;
        activity.runOnUiThread(() -> {
            try {
                final Component component = (Component) finalBaseConstructor.newInstance(form);

                componentsBuilt.put(key, component);
                componentsString.put(component, key);

                if (isLastKey) {
                    componentsCreatedListener.componentsCreated();
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
        });
    }

    static class FForm extends Form {
        public void init(Context context) {
            attachBaseContext(context);
        }

        @Override
        public boolean canDispatchEvent(final Component component, final String str) {
            return true;
        }

        @Override
        public boolean dispatchEvent(final Component component, final String eventName, final String string,
                                     final Object[] values) {
            Log.d(LOG_TAG, eventName);
            eventRaisedListener.eventRaised(component, eventName, values);
            return true;
        }

        @Override
        public void dispatchGenericEvent(final Component component, final String eventName, final boolean bboolean,
                                         final Object[] values) {
            eventRaisedListener.eventRaised(component, eventName, values);
            Log.d(LOG_TAG, eventName);
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

    public static class AActivity extends Activity {
        public void init(Context context) {
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
}
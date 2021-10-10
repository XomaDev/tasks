/*
 * class that manages the creation of the component
 * in the background as well provides some utility
 * work to the extension, one of the important parts
 * of the extension.
 */

package xyz.kumaraswamy.tasks;

import android.app.Activity;
import android.app.Dialog;

import android.content.ComponentName;
import android.content.Context;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.Form;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Set;

public class ComponentManager {

    /**
     * Gets the source string of the component object.
     * It can be a Component, full package name or a short name
     *
     * @param component Object to find the source
     * @return The package (source name) of the object
     */

    public static String getSourceString(final Object component) {
        if (component instanceof Component) {
            // it's a Component Object, we return its
            // source name
            return component.getClass().getName();
        } else if (component instanceof String) {
            final String source = component.toString();

            if (TextUtils.isEmpty(source)) {
                // the source must not be empty or blank
                throw new YailRuntimeError("Component source is invalid!", LOG_TAG);
            }

            boolean isSimpleName = false;
            String fullName = source;

            // it's a simple name, we append the appinventor
            // source package to the left
            if (!fullName.contains(".") && Character.isLetter(fullName.charAt(0))) {
                fullName = "com.google.appinventor.components.runtime." + fullName;
                isSimpleName = true;
            }
            try {
                // it can be an extension's source or something
                // we verify that it exists, and we return it
                Class.forName(fullName);
                return fullName;
            } catch (ClassNotFoundException e) {
                throw new YailRuntimeError(isSimpleName ? source :
                        "The component source name does not exists: " + source, LOG_TAG);
            }
        }
        throw new YailRuntimeError("Component source should be a string or component", LOG_TAG);
    }

    /**
     * Callback interface to know that all the
     * components are created, and the work can be
     * continued
     */

    public interface ComponentsBuiltListener {
        void componentsCreated();
    }

    /**
     * Callback interface triggered/raised when
     * a new event of the component is raised
     */

    public interface EventRaisedListener {
        /**
         * @param component The component of the raised event
         * @param eventName The event name it's raised from
         * @param parms     The parms (arguments) for the event callback
         */
        void eventRaised(Component component, String eventName, Object[] parms);
    }

    // the log tag of the class
    private static final String LOG_TAG = "ComponentManager";

    // both the callbacks initialized in the
    // constructor

    private final ComponentsBuiltListener componentsCreatedListener;
    private final EventRaisedListener eventRaisedListener;


    // the string key and the components set
    private final HashMap<String, Component> componentsBuilt = new HashMap<>();

    // the reverse of the above variable, to
    //
    // find its key
    private final HashMap<Component, String> componentsString = new HashMap<>();

    // the form and the modified source activity
    private final AActivity activity;
    private final FForm form;

    private static final String TAG = "ComponentManager";

    public ComponentManager(final Context context, final HashMap<String, String> components,
                            ComponentsBuiltListener componentsCreatedListener,
                            EventRaisedListener eventRaisedListener) {
        // initialize the form and the
        // activity modified

        form = new FForm();
        form.init(context);

        this.activity = new AActivity();
        activity.init(context);

        final String packageName = activity.getPackageName();

        final String[] fieldNames = {
                "mWindow", "mComponent", "mWindowManager"
        };
        final Object[] fieldNewValues = {
                new Dialog(context).getWindow(),
                new ComponentName(packageName, packageName + ".Screen1"),
                context.getSystemService(Context.WINDOW_SERVICE)
        };
        for (int i = 0; i < fieldNames.length; i++) {
            try {
                modifyDeclaredVar(fieldNames[i], fieldNewValues[i]);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                Log.w(TAG, "ComponentManager: Unable to declare a field!");
            }
        }

        this.componentsCreatedListener = componentsCreatedListener;
        this.eventRaisedListener = eventRaisedListener;

        String[] keys = components.keySet().toArray(new String[0]);
        int len = keys.length;
        if (len == 0) {
            return;
        }
        final String lastKey = keys[len - 1];

        for (final String key : keys)
            createFromSource(components.get(key), key, key.equals(lastKey));
    }

    private void modifyDeclaredVar(String name, Object value) throws IllegalAccessException {
        Field field = getFieldName(name, activity);
        if (field == null)
            throw new NullPointerException("Field cannot be null!");
        field.setAccessible(true);

        field.set(activity, value);
        field.set(form, value);
    }

    /**
     * Returns the declared field in the activity
     *
     * @param name     The name of the declared field
     * @param activity Activity to get on
     * @return The declared field
     */

    private Field getFieldName(final String name, final Activity activity) {
        try {
            return activity.getClass().getSuperclass().
                    getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            // this should not happen!
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns the component related to the key
     * given
     *
     * @param key The key for the component
     * @return The component
     */

    public Component component(String key) {
        return componentsBuilt.get(key);
    }

    /**
     * Removes the component from the hashmap
     * @param key The key to be removed
     * @return The component that got removed
     */

    public Component removeComponent(final String key) {
        Component component = componentsBuilt.get(key);
        componentsString.remove(component);
        return component;
    }

    /**
     * Returns the key of the component
     * @param component The component
     * @return The key related to the component
     */

    public String getKeyOfComponent(Component component) {
        return componentsString.get(component);
    }

    /**
     * Returns the set of all the built components
     * @return The set
     */

    public Set<String> usedComponents() {
        return componentsBuilt.keySet();
    }

    /**
     * Posts the runnable on the UI thread
     * @param runnable The runnable to post on
     */

    public void postRunnable(Runnable runnable) {
        new Handler(activity.getMainLooper()).post(runnable);
    }

    /**
     * Creates the component and puts in the hashmaps
     * @param source The source class name of the Component
     * @param key The key of the component, an identity
     * @param isLastKey To know if its last and triggers the
     *                  component built listener
     */

    private void createFromSource(final String source, final String key, final boolean isLastKey) {
        Class<?> baseClass;
        final Constructor<?> baseConstructor;
        try {
            baseClass = Class.forName(source);
            baseConstructor = baseClass.getConstructor(ComponentContainer.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            // the class was not found, this
            // must not happen, since its already checked
            e.printStackTrace();
            return;
        }
        activity.runOnUiThread(() -> {
            final Component component;

            try {
                // create an instance of the component
                // with the modified form
                component = (Component) baseConstructor.newInstance(form);
            } catch (InstantiationException |
                    IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                return;
            }
            componentsBuilt.put(key, component);
            componentsString.put(component, key);

            Log.d(TAG, "createFromSource: created component " + component);
            if (isLastKey) {
                // raise the interfaces, so that the service
                // can continue its work on.
                componentsCreatedListener.componentsCreated();
            }
        });
    }

    /**
     * class that extends the form
     * so that we can change the behaviour
     * of the component, according to our need
     */

    class FForm extends Form {

        /**
         * Attaches the base context
         * @param context The context that should
         *                be attached
         */
        public void init(Context context) {
            attachBaseContext(context);
        }

        /**
         * Nothing special, just returns true
         * @return true
         */

        @Override
        public boolean canDispatchEvent(final Component component, final String name) {
            return true;
        }

        /**
         * A new event has been raised
         * @param component The component
         * @param eventName The event name from which it raised
         * @param values The parms for the event
         * @return True (because its handled)
         */

        @Override
        public boolean dispatchEvent(final Component component, final String eventName, final String string,
                                     final Object[] values) {
            Log.d(LOG_TAG, eventName);
            eventRaisedListener.eventRaised(component, eventName, values);
            return true;
        }

        /**
         * A new event has been raised
         * @param component The component
         * @param eventName The event name from which it raised
         * @param values The parms for the event
         */

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

    /**
     * Same as the FForm class, just an activity
     * that is modified accordingly
     */

    public static class AActivity extends Activity {
        /**
         * Attaches the base context
         * @param context The context that should
         *                be attached
         */

        public void init(Context context) {
            attachBaseContext(context);
        }

        /**
         * Make it act like we
         * are on Screen1
         */

        @Override
        public String getLocalClassName() {
            return "Screen1";
        }

        /**
         * Important function, so that the parent
         * class can be initialized before the use
         */

        @Override
        public void onCreate(Bundle bundle) {
            super.onCreate(bundle);
        }
    }
}
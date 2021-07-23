package xyz.kumaraswamy.tasks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.JsonUtil;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;
import org.json.JSONException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static xyz.kumaraswamy.tasks.Tasks.*;

public class ActivityService extends JobService {

    private static final String TAG = "ActivityService";
    public static final String JOB = "job";

    private boolean stopped = false;

    private int JOB_ID;
    private ComponentManager manager;

    private final HashMap<String, Object[]> functions = new HashMap<>();
    private final HashMap<String, ArrayList<Object>> events = new HashMap<>();
    private final HashMap<String, Object[]> extraFunctions = new HashMap<>();
    @Override
    public boolean onStartJob(JobParameters parms) {
        Log.d(TAG, "onStartJob: Job started");
        doBackgroundWork(parms);
        return true;
    }

    private void doBackgroundWork(final JobParameters parms) {
        processFunctions(parms.getExtras());
    }

    private void processFunctions(final PersistableBundle extras) {
        JOB_ID = extras.getInt(JOB);

        ComponentManager.ComponentsBuiltListener componentsCreated = () -> {
            Log.d(TAG, "componentsBuilt: Components build!");
            YailDictionary pendingTasks = (YailDictionary) getValue(Tasks.PENDING_TASKS, "");
            String[] taskProcessList = ((ArrayList<?>) getValue(Tasks.TASK_PROCESS_LIST, "")).toArray(new String[0]);

            Log.d(TAG, "processFunctions: " + pendingTasks);
            Log.d(TAG, "processFunctions: " + Arrays.toString(taskProcessList));
            try {
                processTasks(pendingTasks, taskProcessList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        ComponentManager.EventRaisedListener eventRaisedListener = (component, eventName, parameters) -> {
            if (!stopped) {
                Log.d(TAG, "eventRaisedListener: Event raised of name " + eventName);
            }
        };

        Object components =  getValue(JOB, new HashMap<String, String>());
        manager = new ComponentManager(this, (HashMap<String, String>)
                components, componentsCreated, eventRaisedListener);

        boolean foreground = extras.getBoolean(FOREGROUND_MODE);
        Log.d(TAG, "processFunctions: Foreground " + foreground);

        if (foreground) {
            initialiseForeground(extras.getStringArray(FOREGROUND_CONFIG));
        }
    }

    private void initialiseForeground(String[] values) {
        Log.i(TAG, "initialiseForeground: " + values[2]);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "BackgroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Task",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).
                    createNotificationChannel(channel);

            final String icon = values[3];

            final int iconInt = (icon.isEmpty() || icon.equalsIgnoreCase("DEFAULT"))
                    ? android.R.drawable.ic_menu_info_details
                    : Integer.parseInt(icon.replaceAll(" ", ""));

            final Notification notification = new Notification.Builder(this, "BackgroundService")
                    .setSubText(values[0])
                    .setContentTitle(values[1])
                    .setContentText(values[2])
                    .setSmallIcon(iconInt)
                    .build();

            startForeground(1, notification);
        }
    }

    private Object getValue(String tag, Object valueIfTagNotThere) {
        try {
            String value = getSharedPreferences(Tasks.TAG + JOB_ID, 0).getString(tag, "");
            return value.length() == 0 ? valueIfTagNotThere : JsonUtil.getObjectFromJson(value, true);
        } catch (JSONException exception) {
            throw new YailRuntimeError("Value failed to convert from JSON.", "JSON Creation Error.");
        }
    }

    private void processTasks(YailDictionary pd, String[] tasks) throws Exception {
        for (String task : tasks) {
            Log.d(TAG, "processTasks: task " + task);

            String[] taskData = task.split("/");

            final int taskId = Integer.parseInt(taskData[0]);
            final int taskType = Integer.parseInt(taskData[1]);

            final String message = "Task Id: " + taskId + ", taskType: " + taskType;
            Log.d(TAG, message);

            Object[] taskValues = ((YailList) pd.get(taskId + "")).toArray();
            Log.d(TAG, "Task values: " + Arrays.toString(taskValues));

            if (taskType == TASK_CREATE_FUNCTION) {
                function(taskValues);
            } else if (taskType == TASK_CALL_FUNCTION) {
                handleFunction(taskValues);
            } else if (taskType == TASK_REGISTER_EVENT) {
                putEventName(taskValues);
            } else if (taskType == TASK_EXTRA_FUNCTION) {
                extraFunction(taskValues);
            }
            else {
                throw new Exception("Invalid task " + message);
            }
        }
    }

    private void extraFunction(Object[] taskValues) throws Exception {
        String id = taskValues[0].toString();
        Object[] values = (Object[]) taskValues[1];

        if (!extraFunctions.containsKey(id)) {
            Log.d(TAG, "extraFunction: Created function name " + id);
            extraFunctions.put(id, values);
            return;
        }
        throwFunctionExists(id);
    }

    private void function(Object[] taskValues) throws Exception {
        final Object[] values = new Object[]{taskValues[0], taskValues[1], taskValues[2]};
        final String functionName = taskValues[3].toString();

        if (!functions.containsKey(functionName)) {
            functions.put(functionName, values);
            Log.d(TAG, "function: Created function name " + functionName);
            return;
        }
        throwFunctionExists(functionName);
    }

    private void throwFunctionExists(String functionName) throws Exception {
        throw new Exception("The functions already contain the key \"" + functionName + "\".");
    }

    private void handleFunction(Object[] values) {
        final Object[] taskValues = functions.get(values[0].toString());

        if (taskValues == null || taskValues.length == 0) {
            Log.e(TAG, "Invalid invoke values provided");
            return;
        }

        final String key = taskValues[0].toString();
        final String methodName = taskValues[1].toString();
        final Object[] parms = ((YailList) taskValues[2]).toArray();

        Log.d(TAG, "handleFunction: parms for the function " + Arrays.toString(parms));

        final Component component = manager.component(key);

        if (component == null) {
            Log.d(TAG, "handleFunction: The component \"" + key + "\" does not exists!");
            return;
        }

        try {
            Object result = MethodHandler.invokeComponent(component, methodName, parms);
            Log.d(TAG, "handleFunction: Invoke result: " + result);
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void putEventName(Object[] taskValues) {
        final String componentId = taskValues[0].toString();
        final Object[] eventNameFunctionId = new Object[]{taskValues[1], taskValues[2]};

        ArrayList<Object> eventsMap = events.getOrDefault(componentId, new ArrayList<>());
        eventsMap.add(eventNameFunctionId);

        events.put(componentId, eventsMap);
        Log.d(TAG, "putEventName: Registered event name and function Id" + Arrays.toString(eventNameFunctionId));
    }

    @Override
    public boolean onStopJob(JobParameters parms) {
        Log.d(TAG, "onStopJob: Job cancelled");
        stopped = true;
        return false;
    }
}

// SOME PART OF CODE FROM DYNAMIC_COMPONENTS
// EXTENSION - AI2

class MethodHandler {
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
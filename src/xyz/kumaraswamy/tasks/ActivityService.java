package xyz.kumaraswamy.tasks;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.util.Log;

import bsh.EvalError;
import bsh.Interpreter;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.JsonUtil;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;
import org.json.JSONException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static android.app.job.JobInfo.NETWORK_TYPE_NONE;
import static xyz.kumaraswamy.tasks.Tasks.*;

public class ActivityService extends JobService {

    private static final String TAG = "ActivityService";
    public static final String JOB = "job";

    private final String ERROR_FUNCTION_EXISTS = "%s is already an used key!";

    private boolean stopped = false;
    private boolean foreground = false;
    private boolean repeated = false;
    private boolean defaultRepeatedMode;

    private String[] foregroundData;

    private JobParameters jobParms;

    private int JOB_ID;
    private ComponentManager manager;

    private final HashMap<String, Object[]> functions = new HashMap<>();
    private final HashMap<String, ArrayList<Object>> events = new HashMap<>();
    private final HashMap<String, Object[]> extraFunctions = new HashMap<>();

    static final HashMap<String, Object> variables = new HashMap<>();

    @Override
    public boolean onStartJob(JobParameters parms) {
        Log.d(TAG, "onStartJob: Job started");
        doBackgroundWork(parms);
        return true;
    }

    private void doBackgroundWork(final JobParameters parms) {
        jobParms = parms;
        processFunctions(parms.getExtras());
    }

    private void processFunctions(final PersistableBundle extras) {
        JOB_ID = extras.getInt(JOB);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + "WakeLock");
        wakeLock.acquire();

        ComponentManager.ComponentsBuiltListener componentsCreated = () -> {
            Log.d(TAG, "componentsBuilt: Components build!");
            YailDictionary pendingTasks = (YailDictionary) getValue(Tasks.PENDING_TASKS, "");
            String[] taskProcessList = ((ArrayList<?>) getValue(Tasks.TASK_PROCESS_LIST, "")).toArray(new String[0]);

            Log.d(TAG, "processFunctions: " + pendingTasks);
            try {
                processTasks(pendingTasks, taskProcessList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        ComponentManager.EventRaisedListener eventRaisedListener = (component, eventName, parameters) -> {
            if (!stopped) {
                Log.d(TAG, "eventRaisedListener: Event raised of name " + eventName);
                Log.d(TAG, "processFunctions: " + Arrays.toString(parameters));
                handleEvent(component, eventName, parameters);
            }
        };

        Object components = getValue(JOB, new HashMap<String, String>());
        manager = new ComponentManager(this, (HashMap<String, String>) components, componentsCreated,
                eventRaisedListener);

        foreground = extras.getBoolean(FOREGROUND_MODE);
        repeated = (boolean) getValue(REPEATED_EXTRA, false);
        defaultRepeatedMode = (boolean) getValue(REPEATED_TYPE_EXTRA, false);

        Log.d(TAG, "processFunctions: Foreground " + foreground);
        Log.d(TAG, "processFunctions: Repeated extra " + repeated);

        if (foreground) {
            foregroundData = extras.getStringArray(FOREGROUND_CONFIG);
            initialiseForeground();
        }
    }

    private void initialiseForeground() {
        Log.i(TAG, "initialiseForeground: " + foregroundData[2]);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "BackgroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Task",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
            final String icon = foregroundData[3];

            final int iconInt = (icon.isEmpty() || icon.equalsIgnoreCase("DEFAULT"))
                    ? android.R.drawable.ic_menu_info_details
                    : Integer.parseInt(icon.replaceAll(" ", ""));

            Notification notification = new Notification.Builder(this, "BackgroundService")
                            .setSubText(foregroundData[0])
                            .setContentTitle(foregroundData[1])
                            .setContentText(foregroundData[2])
                            .setSmallIcon(iconInt)
                            .build();

            startForeground(1, notification);
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
                handleFunction(taskValues, null);
            } else if (taskType == TASK_REGISTER_EVENT) {
                putEventName(taskValues);
            } else if (taskType == TASK_EXTRA_FUNCTION) {
                extraFunction(taskValues);
            } else {
                throw new Exception("Invalid task " + message);
            }
        }
    }

    private void handleEvent(Component component, String eventName, Object[] parms) {
        final String componentId = manager.getKeyOfComponent(component);
        final ArrayList<Object> valuesList = events.getOrDefault(componentId, null);

        if (componentId == null || valuesList == null) {
            return;
        }

        for (Object value : valuesList) {
            Object[] invokeValues = (Object[]) value;
            Log.d(TAG, "handleEvent: Invoke values " + Arrays.toString(invokeValues));

            final String thisEventName = invokeValues[0].toString();
            Log.d(TAG, "handleEvent: Comparing event name with " + thisEventName);

            if (thisEventName.equals(eventName)) {
                final String functionId = invokeValues[1].toString();

                if (functionId.startsWith("$")) {
                    Log.d(TAG, "handleEvent: Function has extra function values");
                    Object[] values = extraFunctions.get(functionId.substring(1));
                    Log.d(TAG, "Extra function values : " + Arrays.toString(values));

                    final ArrayList<Object[]> results = new ArrayList<>();

                    for (Object obj : values) {
                        Object result = parseExtraFunctionCode(obj.toString(), parms);

                        if (!(result instanceof Boolean)) {
                            Log.i(TAG, "handleEvent: The parsed result is: " + Arrays.toString(((Object[]) result)));
                            results.add((Object[]) result);
                        }
                    }

                    Log.i(TAG, "handleEvent: The values received and prepared: " + results);

                    for (Object[] objects : results) {
                        handleExtraFunction(objects);
                    }
                } else {
                    handleFunction(new Object[]{functionId}, null);
                }
            } else {
                Log.d(TAG, "handleEvent: Event dismissed of name " + eventName);
            }
        }
    }

    private void handleExtraFunction(Object[] objs) {
        String functionName = objs[0].toString();
        final String input = objs[1].toString();
        Object[] parms = (Object[]) objs[2];

        if (functionName.equals("function")) {
            handleFunction(new Object[]{input}, parms);
            return;
        }
        Log.e(TAG, "Invalid extra function type name received!");
    }

    private Object parseExtraFunctionCode(final String code, final Object[] parms) {
        final String EXTRA_FUNCTION_SEPARATOR = "::";

        final int index = code.lastIndexOf(EXTRA_FUNCTION_SEPARATOR);

        String executeCode = code.substring(index + 3);
        String condition = code.substring(0, index);

        Log.d(TAG, "parseExtraFunctionCode: Execute function code: " + executeCode);
        Log.d(TAG, "parseExtraFunctionCode: Condition function code: " + condition);

        Object result = false;

        Interpreter interpreter = new Interpreter();

        try {
            for (int i = 0; i < parms.length; i++) {
                interpreter.set("val" + i, parms[i]);
            }
            result = interpreter.eval(condition);
        } catch (EvalError evalError) {
            evalError.printStackTrace();
        }

        Log.d(TAG, "parseExtraFunctionCode: code result " + result);

        if (result instanceof Boolean && (boolean) result) {
            try {
                for (int i = 0; i < parms.length; i++) {
                    parms[i] = MethodHandler.emptyIfNull(interpreter.get("val" + i));
                }

                String type = executeCode.substring(0, executeCode.indexOf("("));

                String functionId = executeCode.substring(
                        type.length() + 1, executeCode.length() - 1
                );

                return new Object[] {
                        type, functionId, parms
                };
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    private Object getValue(String tag, Object valueIfTagNotThere) {
        try {
            String value = getSharedPreferences(Tasks.TAG + JOB_ID, 0).getString(tag, "");
            return value.length() == 0 ? valueIfTagNotThere : JsonUtil.getObjectFromJson(value, true);
        } catch (JSONException exception) {
            throw new YailRuntimeError("Value failed to convert from JSON.", "JSON Creation Error.");
        }
    }

    private void extraFunction(Object[] taskValues) throws Exception {
        Log.d(TAG, "extraFunction: Extra function values " + Arrays.toString(taskValues));
        String id = taskValues[0].toString();
        Object[] values = ((YailList) taskValues[1]).toArray();

        if (!extraFunctions.containsKey(id)) {
            Log.d(TAG, "extraFunction: Created function name " + id);
            extraFunctions.put(id, values);
            return;
        }
        throw new Exception(String.format(ERROR_FUNCTION_EXISTS, id));
    }

    private void function(Object[] taskValues) throws Exception {
        final Object[] values = new Object[]{taskValues[0], taskValues[1], taskValues[2]};
        final String functionName = taskValues[3].toString();

        if (functionName.startsWith("$")) {
            throw new Exception("Function name should not start from a dollar symbol.");
        }

        if (!functions.containsKey(functionName)) {
            functions.put(functionName, values);
            Log.d(TAG, "function: Created function name " + functionName);
            return;
        }

        throw new Exception(String.format(ERROR_FUNCTION_EXISTS, functionName));
    }

    private void handleFunction(Object[] values, Object[] eventParms) {
        final Object[] taskValues = functions.get(values[0].toString());

        if (taskValues == null || taskValues.length == 0) {
            Log.e(TAG, "Invalid invoke values provided");
            return;
        }

        final String key = taskValues[0].toString();
        final String methodName = taskValues[1].toString();
        final Object[] parms = ((YailList) taskValues[2]).toArray();

        if (key.equals("self")) {
            final boolean exitBoolean = getExitBoolean(parms);

            if (methodName.equals("exit")) {
                jobFinished(jobParms, exitBoolean); // BOOL: RESTART
                return;
            } else if (methodName.equals("stop foreground")) {
                stopForeground(exitBoolean); // BOOL: REMOVE NOTIFICATION
                return;
            } else if (methodName.equals("destroy") && parms.length == 1) {
                destroyComponent(parms[0].toString());
                return;
            } else if (methodName.equals("start app") && parms.length == 2) {
                String action = parms[0].toString();

                try {
                    Intent intent = Boolean.parseBoolean(parms[1].toString())
                            ? !action.contains(".")
                            ? new Intent(this, Class.forName(getPackageName() + "." + action))
                            : getPackageManager().getLaunchIntentForPackage(action)
                            : new Intent(Intent.ACTION_VIEW, Uri.parse(action));

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return;
            } else if (methodName.equals("start foreground") && parms.length == 4) {
                foregroundData = Arrays.asList(parms).toArray(new String[0]);
                initialiseForeground();
                return;
            }
        }

        Log.d(TAG, "handleFunction: parms for the function " + Arrays.toString(parms));

        final Component component = manager.component(key);

        if (component == null) {
            Log.e(TAG, String.format("handleFunction: The component '%s' does not exists!", key));
            return;
        }

        try {
            Object invoke = MethodHandler.invokeComponent(component, methodName, parms, eventParms);
            Log.d(TAG, "handleFunction: Invoke result: " + invoke);
            variables.put("invoke", invoke);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void destroyComponent(String key) {
        Component component = manager.removeComponent(key);
        String[] methodNames = new String[]{
                "onDestroy", "onPause"
        };
        try {
            for (String method : methodNames) {
                MethodHandler.invokeComponent(component, method, new Object[]{}, null);
            }
        } catch (Exception e) {
            // JUST LOG THE EXCEPTION
            Log.e(TAG, "destroyComponent: " + e.getMessage());
        }
    }

    private boolean getExitBoolean(final Object[] array) {
        return (array.length <= 0 || Boolean.parseBoolean(array[0].toString()));
    }

    private void putEventName(Object[] taskValues) {
        final String componentId = taskValues[0].toString();

        final Object[] eventNameFunctionId = new Object[] {
                taskValues[1], taskValues[2]
        };

        ArrayList<Object> eventsMap = events.getOrDefault(componentId, new ArrayList<>());
        eventsMap.add(eventNameFunctionId);

        events.put(componentId, eventsMap);
        Log.d(TAG, "putEventName: Registered event name and function Id " + Arrays.toString(eventNameFunctionId));
    }

    @Override
    public boolean onStopJob(JobParameters parms) {
        Log.d(TAG, "onStopJob: Job cancelled");
        stopped = true;

        for (String key : manager.usedComponents()) {
            destroyComponent(key);
        }

//        boolean defaultExit = repeatedMode.equals("DEFAULT");

        if (repeated) {
            jobFinished(parms, defaultRepeatedMode);
            if (defaultRepeatedMode) {
                return true;
            } else {
                int network = (int) getValue(EXTRA_NETWORK, NETWORK_TYPE_NONE);
                Intent intent = prepareIntent(this, JOB_ID, network, foreground, foregroundData, repeated);

                sendBroadcast(intent);
            }
        }
        return false;
    }

    // PARTS OF CODE FROM DYNAMIC_COMPONENTS
    // EXTENSION - AI2

    static class MethodHandler {
        public static Object invokeComponent(
                final Component component, final String methodName,
                Object[] params, Object[] eventParms) throws Exception {

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
                    String parm = params[i].toString();

                    for (String key : variables.keySet()) {
                        parm = parm.replace("{" + key + "}", variables.get(key).toString());
                    }

                    if (eventParms != null) {
                        for (int j = 0; j < eventParms.length; j++) {
                            parm = parm.replace("{" + j + "}", eventParms[j].toString());
                        }
                    }

                    mParametersArrayList.add(parm);
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

        public static Object emptyIfNull(Object o) {
            return (o == null) ? "" : o;
        }
    }
}
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

import static xyz.kumaraswamy.tasks.Tasks.FOREGROUND_CONFIG;
import static xyz.kumaraswamy.tasks.Tasks.FOREGROUND_MODE;
import static xyz.kumaraswamy.tasks.Tasks.TAG;
import static xyz.kumaraswamy.tasks.Tasks.TASK_CALL_FUNCTION;
import static xyz.kumaraswamy.tasks.Tasks.TASK_CREATE_FUNCTION;
import static xyz.kumaraswamy.tasks.Tasks.TASK_EXTRA_FUNCTION;
import static xyz.kumaraswamy.tasks.Tasks.TASK_REGISTER_EVENT;

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
                handleEvent(component, eventName, parameters);
            }
        };

        Object components = getValue(JOB, new HashMap<String, String>());
        manager = new ComponentManager(this, (HashMap<String, String>) components, componentsCreated, eventRaisedListener);

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
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Task", NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);

            final String icon = values[3];

            final int iconInt = (icon.isEmpty() || icon.equalsIgnoreCase("DEFAULT"))
                    ? android.R.drawable.ic_menu_info_details
                    : Integer.parseInt(icon.replaceAll(" ", ""));

            final Notification notification = new Notification.Builder(this, "BackgroundService").
                    setSubText(values[0]).
                    setContentTitle(values[1]).
                    setContentText(values[2]).
                    setSmallIcon(iconInt).build();

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
                handleFunction(taskValues);
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

                    for (Object o : values) {
                        Object result = parseExtraFunctionCode(o.toString(), parms);

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
                    handleFunction(new Object[]{functionId});
                }
            } else {
                Log.d(TAG, "handleEvent: Event dismissed of name " + eventName);
            }
        }
    }

    private void handleExtraFunction(Object[] objs) {
        String functionName = objs[0].toString();
        final String input = objs[1].toString();

        if (functionName.equals("function")) {
            handleFunction(new Object[]{input});
            return;
        }
        Log.e(TAG, "Invalid extra function type name received!");
    }

    private Object parseExtraFunctionCode(final String code, final Object[] parms) {
        String executeCode = code.substring(code.lastIndexOf("::") + 3);
        String condition = code.substring(0, code.lastIndexOf("::"));

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

        if (result instanceof Boolean && (boolean) result) {
            try {
                for (int i = 0; i < parms.length; i++) {
                    parms[i] = MethodHandler.emptyIfNull(interpreter.get("val" + i));
                }

                final String[] textsplit = executeCode.split("\\(");
                final String type = textsplit[0];
                final String functionId = executeCode.substring(type.length() + 1, executeCode.length() - 1);

                return new Object[] {type, functionId, parms};
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
        throwFunctionAlreadyExists(id);
    }

    private void function(Object[] taskValues) throws Exception {
        final Object[] values = new Object[]{taskValues[0], taskValues[1], taskValues[2]};
        final String functionName = taskValues[3].toString();

        if (!functions.containsKey(functionName)) {
            functions.put(functionName, values);
            Log.d(TAG, "function: Created function name " + functionName);
            return;
        }
        throwFunctionAlreadyExists(functionName);
    }

    private void throwFunctionAlreadyExists(String functionName) throws Exception {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void putEventName(Object[] taskValues) {
        final String componentId = taskValues[0].toString();
        final Object[] eventNameFunctionId = new Object[] {taskValues[1], taskValues[2]};

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

// PARTS OF CODE FROM DYNAMIC_COMPONENTS
// EXTENSION - AI2

class MethodHandler {
    public static Object invokeComponent(final Component component, final String methodName, Object[] params) throws Exception {
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

    public static Object emptyIfNull(Object o) {
        return (o == null) ? "" : o;
    }
}
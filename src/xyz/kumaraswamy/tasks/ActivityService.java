package xyz.kumaraswamy.tasks;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import xyz.kumaraswamy.tasks.alarms.Terminator;

import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.JsonUtil;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;
import xyz.kumaraswamy.tasks.node.Node;
import xyz.kumaraswamy.tasks.node.NodeConstructor;
import xyz.kumaraswamy.tasks.node.ValueNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static android.app.job.JobInfo.NETWORK_TYPE_NONE;

import static java.lang.String.valueOf;
import static xyz.kumaraswamy.tasks.Tasks.*;

public class ActivityService extends JobService {

    private static final String TAG = "ActivityService";
    public static final String JOB = "job";

    private final String ERROR_FUNCTION_EXISTS = "%s is already an used key!";

    private boolean stopped = false;
    private boolean foreground = false;
    private boolean repeated = false;
    private boolean defaultRepeatedMode;

    private String[] initForegroundDetails;

    private JobParameters jobParms;

    private int JOB_ID;
    private ComponentManager manager;

    private final HashMap<String, Object[]> functions = new HashMap<>();
    private final HashMap<String, ArrayList<Object>> events = new HashMap<>();
    private final HashMap<String, Object[]> extraFunctions = new HashMap<>();

    private final HashMap<String, Object[]> workFunctions = new HashMap<>();

    static final HashMap<String, Object> variables = new HashMap<>();

    private MethodHandler methodHandler;

    private final Common common = new Common();

    @Override
    public boolean onStartJob(JobParameters parms) {
        Log.d(TAG, "onStartJob: Job started");
        doBackgroundWork(parms);
        return true;
    }

    private void doBackgroundWork(final JobParameters parms) {
        PersistableBundle extras = parms.getExtras();

        jobParms = parms;
        JOB_ID = extras.getInt(JOB);

        int maxRunInt = (int) getValue(TERMINATE_EXTRA, -1);
        if (maxRunInt != -1) {
            initializeTerminator(maxRunInt);
        }
        processFunctions(extras);
        methodHandler = new MethodHandler();
    }

    private void initializeTerminator(int timeout) {
        Intent intent = new Intent(this, Terminator.class);
        intent.putExtra(JOB, JOB_ID);
        PendingIntent pd = PendingIntent.getBroadcast(this, JOB_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + timeout, pd);
    }

    @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
    private void processFunctions(final PersistableBundle extras) {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG + "WakeLock");
        wakeLock.acquire();

        ComponentManager.ComponentsBuiltListener componentsCreated = () -> {
            Log.d(TAG, "componentsBuilt: Components build!");
            YailDictionary pendingTasks = (YailDictionary) getValue(Tasks.PENDING_TASKS, "");
            String[] taskProcessList = ((ArrayList<?>) getValue(Tasks.TASK_PROCESS_LIST,
                    "")).toArray(new String[0]);

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

        if (!foreground) {
            return;
        }

        initForegroundDetails = extras.getStringArray(FOREGROUND_CONFIG);
        initialiseForeground();
    }

    private void initialiseForeground() {
        Log.i(TAG, "initialiseForeground: " + initForegroundDetails[2]);

        if (Build.VERSION.SDK_INT >= 26) {
            String CHANNEL_ID = "BackgroundService";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Task",
                    NotificationManager.IMPORTANCE_DEFAULT);

            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
            final String icon = initForegroundDetails[3];

            final int iconInt = (icon.isEmpty() || icon.equalsIgnoreCase("DEFAULT")) ?
                    android.R.drawable.ic_menu_info_details :
                    Integer.parseInt(icon.replaceAll(" ", ""));

            Notification notification =
                    new Notification.Builder(this, "BackgroundService")
                            .setSubText(initForegroundDetails[0])
                            .setContentTitle(initForegroundDetails[1])
                            .setContentText(initForegroundDetails[2])
                            .setSmallIcon(iconInt).build();

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

            switch (taskType) {
                case TASK_CREATE_FUNCTION:
                    function(taskValues);
                    break;
                case TASK_CALL_FUNCTION:
                    handleFunction(taskValues, null);
                    break;
                case TASK_REGISTER_EVENT:
                    putEventName(taskValues);
                    break;
                case TASK_EXTRA_FUNCTION:
                    extraFunction(taskValues);
                    break;
                case TASK_CREATE_VARIABLE:
                    putToVariable(taskValues);
                    break;
                case TASK_CALL_FUNCTION_WITH_ARGS:
                    callWithArgs(taskValues);
                    break;
                case TASK_WORK_WORK_FUNCTION:
                    workFunction(taskValues);
                    break;
                case TASK_CALL_WORK_FUNCTION:
                    callWorkFunction(taskValues);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + taskType);
            }
        }
    }

    private void callWorkFunction(Object[] taskValues) throws Exception {
        final String id = valueOf(taskValues[0]);
        final Object[] values = workFunctions.get(id);

        if (values == null) {
            throw new Exception("Cannot find work function id '" + id + '\'');
        }
        Log.d(TAG, "callWorkFunction: " + Arrays.toString(values));

        final String type = valueOf(values[0]);

        switch (type) {
            case "call":
                handleWorkCall((Node) values[1]);
                break;
        }
    }

    private void handleWorkCall(final Node node) throws Exception {
        Log.d(TAG, "handleWorkCall");
        final String workName = node.getValue();

        final String right =
                processWorkCallNode(node, true)
                , left = processWorkCallNode(node, false);

        Log.d(TAG, "handleWorkCall: left '" + right +
                "' right" + "'" + left + "'");

        if (!right.equals(left)) {
            return;
        }
        handleFunction(new Object[] {workName}, null);
    }

    private String processWorkCallNode(final Node node, boolean rightNode) throws Exception {
        final Node headNode = !rightNode ?
                node.getLeft() : node.getRight();

        if (!(headNode instanceof ValueNode)) {
            return checkNode(headNode);
        }

        // If head node is instance of ValueNode
        final String value = headNode.getValue();

        // Return the same if value doesn't start with '$'

        Log.d(TAG, "processWorkCallNode: for value " + value);
        if (!value.startsWith("$")) {
            return value;
        }
        final String sub = value.substring(1);
        final Object variable = variables.get(sub);

        if (variable != null) {
            return variable.toString();
        }

        final Object[] objects = workFunctions.get(value.substring(1));
        if (objects == null) {
            throw new NullPointerException();
        }
        if (!valueOf(objects[0]).equals("logic")) {
            throw new Exception("Unexpected a logic type work function.");
        }

        return checkNode((Node) objects[1]);
    }

    private String checkNode(final Node node) throws Exception {
        final String a1 = valueOf(processWorkCallNode(node, true));
        final String a2 = processWorkCallNode(node, false);
        Log.d(TAG, "checkNode: a1 " + a1);
        Log.d(TAG, "checkNode: a2 " + a2);
        return valueOf(a1.equals(
                a2));
    }

    private void workFunction(Object[] taskValues) throws JSONException {
        final String type = valueOf(taskValues[1]).toLowerCase();

        switch (type) {
            case "logic":
            case "work":
            case "call":
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + type);
        }
        final Node node = NodeConstructor
                .constructNode((String) taskValues[2]);
        workFunctions.put(valueOf(taskValues[0]), new Object[] {type, node});
        Log.d(TAG, "workFunction: Put work function type " + type + ", node " + node);
    }

    private void callWithArgs(Object[] taskValues) {
        String functionName = taskValues[0].toString();

        handleFunction(new Object[] {functionName},
                /* Extras */ ((YailList) taskValues[1]).toArray());
    }

    public void putToVariable(Object[] taskValues) {
        final String name = taskValues[0].toString();
        final Object values = taskValues[1];

        variables.put(name, values);
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

            final String compareName = invokeValues[0].toString();
            Log.d(TAG, "handleEvent: Comparing event name with " + compareName);

            if (!compareName.equals(eventName)) {
                Log.d(TAG, "handleEvent: Event dismissed of name " + eventName);
                return;
            }
            final String functionId = invokeValues[1].toString();

            if (functionId.startsWith("$")) {
                processExtraFunction(functionId.substring(1), parms);
            } else {
                handleFunction(new Object[] {
                        functionId}, parms);
            }
        }
    }

    private void processExtraFunction(String functionId, Object[] parms) {
        Log.d(TAG, "processExtraFunction: Function has extra function values");
        Object[] values = extraFunctions.get(functionId);
        Log.d(TAG, "Extra function values : " + Arrays.toString(values));

        final ArrayList<Object[]> results = new ArrayList<>();

        for (Object obj : values) {
            Object result = null;
            try {
                result = parseExtraFunctionCode(obj.toString(), parms);
            } catch (EvalError e) {
                e.printStackTrace();
            }

            if (!(result instanceof Boolean)) {
                Log.i(TAG, "processExtraFunction: The parsed result is: " +
                        Arrays.toString(((Object[]) result)));
                results.add((Object[]) result);
            }
        }

        Log.i(TAG, "processExtraFunction: The values received and prepared: " + results);

        for (Object[] objects : results) {
            handleExtraFunction(objects);
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
        Log.e(TAG, "Invalid extra function type name received! Name: " + functionName);
    }

    @SuppressWarnings("unused")
    public class Me {
        public Object call(Object method) {
            Log.d(TAG, "call: " + method);
            return call(method, new Object[0]);
        }

        public Object call(Object method, Object... extras) {
            if (!(method instanceof String)) {
                throw new IllegalArgumentException("Expected a string type but got: " + method);
            }
            return handleFunction(new Object[] {
                    String.valueOf(method)}, extras);
        }
    }

    private Object parseExtraFunctionCode(final String code, final Object[] parms) throws EvalError {
        final String separator = "::";

        final int index = code.lastIndexOf(separator);

        String executeCode = index == -1 ? code : code.substring(index + 3);
        String condition = index == -1 ? code : code.substring(0, index);

        Log.d(TAG, "parseExtraFunctionCode: Execute function code: " + executeCode +
                "\n" + "Condition function code:" + " " + condition);

        Interpreter interpreter = new Interpreter();
        Object result = eval(parms,
                condition, interpreter);

        Log.d(TAG, "parseExtraFunctionCode: code result " + result);

        for (int i = 0; i < parms.length; i++) {
            parms[i] = methodHandler.emptyIfNull(interpreter.get("val" + i));
        }
        for (String name : variables.keySet()) {
            variables.put(name, interpreter.get(name));
        }

        if (!(result instanceof Boolean && (boolean) result)) {
            Log.d(TAG, "parseExtraFunctionCode: result false, returning false");
            return false;
        }
        if ((executeCode = executeCode.trim()).equals("pass")) {
            return false;
        }
        String type = executeCode.substring(0,
                executeCode.indexOf("("));

        String functionId = executeCode.substring(type.length() + 1,
                executeCode.length() - 1);

        return new Object[] {
                type, functionId, parms
        };
    }

    public Object eval(Object[] parms, String eval,
                        Interpreter interpreter) throws EvalError {
        interpreter.set("me", new ActivityService.Me());
        common.declareObjects(parms, interpreter);
        for (String name : variables.keySet()) {
            interpreter.set(name, variables.get(name));
        }
        return interpreter.eval(eval);
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
        String id = valueOf(taskValues[0]);

        if (!extraFunctions.containsKey(id)) {
            Log.d(TAG, "extraFunction: Created function name " + id);
            extraFunctions.put(id, ((YailList) taskValues[1]).toArray() /* Values */);
            return;
        }
        throw new Exception(String.format(ERROR_FUNCTION_EXISTS, id));
    }

    private void function(Object[] taskValues) throws Exception {
        final Object[] values = new Object[] {
                taskValues[0], taskValues[1], taskValues[2]
        };
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

    @SuppressWarnings("SuspiciousToArrayCall")
    private Object handleFunction(Object[] values, Object[] extras) {
        final String functionKey = values[0].toString();

        if (functionKey.startsWith("$")) {
            processExtraFunction(functionKey.substring(1), extras);
            return null;
        }

        final Object[] parms = functions.get(functionKey);

        if (parms == null || parms.length == 0) {
            Log.e(TAG, "Invalid invoke values provided");
            return null;
        }

        final String key = valueOf(parms[0]), methodName = valueOf(parms[1]);
        final Object[] funcParms = ((YailList) parms[2]).toArray();

        if (key.equals("self")) {
            final boolean exitBoolean = getExitBoolean(funcParms);
            final int len = funcParms.length;
            if (methodName.equals("exit")) {
                jobFinished(jobParms, exitBoolean); // BOOL: RESTART
                return null;
            } else if (methodName.equals("stop foreground")) {
                stopForeground(exitBoolean); // BOOL: REMOVE NOTIFICATION
                return null;
            } else if (methodName.equals("destroy") && len == 1) {
                destroyComponent(valueOf(funcParms[0]));
                return null;
            } else if (methodName.equals("start app") && len == 2) {
                final String action = valueOf(funcParms[0]);

                try {
                    Intent intent = Boolean.parseBoolean(funcParms[1].toString())
                            ? !action.contains(".") ?
                            new Intent(this, Class.forName(
                                    getPackageName() + "." + action)) :
                            getPackageManager().getLaunchIntentForPackage(action)
                            : new Intent(Intent.ACTION_VIEW,
                            Uri.parse(action));

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                return null;
            } else if (methodName.equals("start foreground") && len == 4) {
                initForegroundDetails = Arrays.asList(funcParms).toArray(new String[0]);
                initialiseForeground();
                return null;
            }
        }

        return invoke(extras, key, methodName, funcParms);
    }

    @NotNull
    public Object invoke(Object[] extras, String key, String methodName, Object[] funcParms) {
        Log.d(TAG, "invoke: parms for the function " + Arrays.toString(funcParms));

        final Component component = manager.component(key);

        if (component == null) {
            Log.e(TAG, String.format("invoke: The component '%s' does not exists!", key));
            return null;
        }
        Object invoke = null;
        try {
            invoke = methodHandler.invokeComponent(component,
                    methodName, funcParms, extras);
            Log.d(TAG, "invoke: Invoke result: " + invoke);
            variables.put("invoke", invoke);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return invoke;
    }

    private void destroyComponent(String key) {
        Component component = manager.removeComponent(key);
        String[] methodNames = new String[] {"onDestroy", "onPause"};
        try {
            for (String method : methodNames) {
                methodHandler.invokeComponent(component, method,
                        new Object[0], null);
            }
        } catch (Exception exception) {
            // Just log the exception
            Log.e(TAG, "destroyComponent: " + exception.getMessage());
        }
    }

    private boolean getExitBoolean(final Object[] array) {
        return (array.length <= 0
                || Boolean.parseBoolean(array[0].toString()));
    }

    public String replaceEventNames(String string) {
        for (String key : variables.keySet()) {
            final String replacement = methodHandler.fix_replacement(variables.get(key));

            if (replacement != null) {
                string = string.replace("{$" + key + "}", replacement);
            }
        }
        return string;
    }

    private void putEventName(Object[] taskValues) {
        final String componentId = taskValues[0].toString();

        final Object[] eventNameFunctionId = new Object[] {
                taskValues[1], taskValues[2]
        };

        ArrayList<Object> eventsMap = events.getOrDefault(componentId, new ArrayList<>());
        eventsMap.add(eventNameFunctionId);

        events.put(componentId, eventsMap);
        Log.d(TAG, "putEventName: Registered event name and function Id " +
                Arrays.toString(eventNameFunctionId));
    }

    @Override
    public boolean onStopJob(JobParameters parms) {
        Log.d(TAG, "onStopJob: Job cancelled");
        stopped = true;

        for (String key : manager.usedComponents()) {
            destroyComponent(key);
        }

        if (repeated) {
            jobFinished(parms, defaultRepeatedMode);
            if (defaultRepeatedMode) {
                return true;
            }
            sendBroadcast(prepareIntent(this, JOB_ID, (int) getValue(EXTRA_NETWORK,
                            NETWORK_TYPE_NONE), foreground, initForegroundDetails, repeated));
        }
        return false;
    }

    // PARTS OF CODE FROM DYNAMIC_COMPONENTS
    // EXTENSION - AI2

    class MethodHandler {
        private static final String TAG = "MethodHandler";

        public Object invokeComponent(final Component component, final String methodName,
                                      Object[] params, Object[] eventParms) {
            Log.d(TAG, "invokeComponent: event parms(" + Arrays.toString(eventParms) + ")");
            Method method = findMethod(component.getClass().getMethods(),
                    methodName, params.length);

            if (method == null) {
                Log.e(TAG, "invokeComponent: Method not found for name \"" + methodName + "\"!");
                return null;
            }

            for (int i = 0; i < params.length; i++) {
                final Object objectParm = params[i];

                if (objectParm instanceof String) {
                    String string = replaceEventNames(
                            valueOf(objectParm));

                    if (eventParms == null) {
                        params[i] = string;
                        continue;
                    }

                    for (int j = 0; j < eventParms.length; j++) {
                        final String replacement = fix_replacement(eventParms[j]);

                        if (replacement != null) {
                            string = string.replace("{$" + j + "}", replacement);
                        }
                    }
                    params[i] = string;
                }
            }

            final Object[] result = new Object[1];

            ActivityService.this.manager.postRunnable(() -> {
                try {
                    result[0] = method.invoke(component,
                            castParms(method.getParameterTypes(), params));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            });
            return valueOf(result[0]);
        }

        private Object[] castParms(Class<?>[] parmsTypes, Object[] params) {
            Object[] modifiedParms = new Object[parmsTypes.length];

            for (int i = 0; i < parmsTypes.length; i++) {
                final String value = valueOf(params[i]);

                switch (parmsTypes[i].getName()) {
                    case "int":
                        modifiedParms[i] = Integer.parseInt(value);
                        break;
                    case "float":
                        modifiedParms[i] = Float.parseFloat(value);
                        break;
                    case "double":
                        modifiedParms[i] = Double.parseDouble(value);
                        break;
                    case "java.lang.String":
                        modifiedParms[i] = String.valueOf(params[i]);
                        break;
                    case "boolean":
                        modifiedParms[i] = Boolean.parseBoolean(value);
                        break;
                    case "byte":
                        modifiedParms[i] = Byte.parseByte(value);
                    default:
                        modifiedParms[i] = params[i];
                }
            }
            return modifiedParms;
        }

        private String fix_replacement(Object obj) {
            return obj instanceof String
                    ? obj.toString()
                    : obj instanceof Number
                    ? valueOf(((Number) obj).intValue())
                    : null;

        }

        private Method findMethod(Method[] methods, String name, int parameterCount) {
            name = name.replaceAll("[^a-zA-Z0-9]", "");
            for (Method method : methods) {
                if (method.getName().equals(name) &&
                        method.getParameterTypes().length == parameterCount)
                    return method;
            }
            return null;
        }

        public Object emptyIfNull(Object o) {
            return (o == null) ? "" : o;
        }
    }
}
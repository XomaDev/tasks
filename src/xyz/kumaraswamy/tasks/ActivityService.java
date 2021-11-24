/*
 * TODO:
 * Refactor code with Java Docs
 */

package xyz.kumaraswamy.tasks;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.net.Uri;
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
import xyz.kumaraswamy.tasks.alarms.Initializer;
import xyz.kumaraswamy.tasks.alarms.Terminator;
import xyz.kumaraswamy.tasks.jet.Jet;
import xyz.kumaraswamy.tasks.tools.Common;
import xyz.kumaraswamy.tasks.tools.Notification;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static android.app.job.JobInfo.NETWORK_TYPE_NONE;
import static java.lang.String.valueOf;
import static xyz.kumaraswamy.tasks.Tasks.EXTRA_NETWORK;
import static xyz.kumaraswamy.tasks.Tasks.prepareIntent;

public class ActivityService extends JobService {

    private static final String TAG = "ActivityService";
    public static final String JOB = "job";

    private boolean isJobStopped = false;
    private boolean foreground = false;
    private boolean repeated = false;
    private boolean defaultRepeatedMode;

    private String[] argsForeground;

    private JobParameters jobParms;

    private int JOB_ID;
    private ComponentManager manager;

    private final HashMap<String, Object[]> functions = new HashMap<>();
    private final HashMap<String, ArrayList<Object>> events = new HashMap<>();
    private final HashMap<String, Object[]> extraFunctions = new HashMap<>();

    final HashMap<String, Object> variables = new HashMap<>();

    private MethodHandler methodHandler;

    private final Common common = new Common(this);
    private Jet jet;

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
        Log.d(TAG, "doBackgroundWork: Job Id: " + JOB_ID + " from " + extras.getString("from"));

        int maxRunInt = (int) getValue(Tasks.TERMINATE_EXTRA, -1);
        if (maxRunInt != -1) {
            createTerminator(maxRunInt);
        }
        processFunctions(extras);
        methodHandler = new MethodHandler();
        jet = new Jet(manager, variables);
    }

    private void createTerminator(int timeout) {

        // a terminator that will stop
        // this service when the timeout is
        // reached

        new Initializer(this, common)
                .create(Terminator.class)
                .setJobId(JOB_ID)
                .startExactAndAllowWhileIdle(timeout);
    }

    @SuppressWarnings({"unchecked", "SuspiciousToArrayCall"})
    private void processFunctions(final PersistableBundle extras) {
        common.acquireWakeLock();

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
            if (isJobStopped) {
                return;
            }
            Log.d(TAG, "eventRaisedListener: Event raised of name " + eventName);
            Log.d(TAG, "processFunctions: " + Arrays.toString(parameters));
            handleEvent(component, eventName, parameters);
        };
        Object components = getValue(JOB, new HashMap<String, String>());
        manager = new ComponentManager(this, (HashMap<String, String>) components, componentsCreated,
                eventRaisedListener);

        foreground = extras.getBoolean(Tasks.FOREGROUND_MODE);
        repeated = (boolean) getValue(Tasks.REPEATED_EXTRA, false);
        defaultRepeatedMode = (boolean) getValue(Tasks.REPEATED_TYPE_EXTRA, false);

        Log.d(TAG, "processFunctions: Foreground " + foreground);
        Log.d(TAG, "processFunctions: Repeated extra " + repeated);

        if (!foreground) {
            return;
        }

        argsForeground = extras.getStringArray(Tasks.FOREGROUND_CONFIG);
        initialiseForeground();
    }

    private void initialiseForeground() {
        Log.i(TAG, "initializeForeground: " + argsForeground[2]);

        try {
            startForeground(1, new Notification(common)
                    .setSmallIcon(argsForeground[3])
                    .configure(argsForeground[1],
                            argsForeground[2], argsForeground[0])
                    .buildNotification());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
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
                case Tasks.TASK_CREATE_FUNCTION:
                    function(taskValues);
                    break;
                case Tasks.TASK_CALL_FUNCTION:
                    handleFunction(taskValues, null);
                    break;
                case Tasks.TASK_REGISTER_EVENT:
                    putEventName(taskValues);
                    break;
                case Tasks.TASK_EXTRA_FUNCTION:
                    extraFunction(taskValues);
                    break;
                case Tasks.TASK_CREATE_VARIABLE:
                    putToVariable(taskValues);
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + taskType);
            }
        }
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
            handleFunction(new Object[] {input}, parms);
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
        interpreter.set("me", new Me());
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
        throw new Exception("Function already exists '" +  id + "'");
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

        throw new Exception("Function already exists '" +  functionName + "'");
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

        final String key = valueOf(parms[0]),
                methodName = valueOf(parms[1]);
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
                argsForeground = Arrays.asList(funcParms).toArray(new String[0]);
                initialiseForeground();
                return null;
            }
        }

        return invoke(extras, key, methodName, funcParms);
    }

    public Object invoke(Object[] extras, String key, String methodName, Object[] parms) {
        Log.d(TAG, "invoke: parms for the function " + Arrays.toString(parms));
        Log.d(TAG, "invoke: extras for the function: " + Arrays.toString(extras));

        final Component component = manager.component(key);

        if (component == null) {
            Log.e(TAG, String.format("invoke: The component '%s' does not exists!", key));
            return null;
        }
        Object invoke = null;
        try {
            invoke = methodHandler.invokeComponent(component,
                    methodName, parms, extras);
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
        } catch (Exception e) {
            // Just log the exception
            Log.e(TAG, "destroyComponent: " + e.getMessage());
        }
    }

    private boolean getExitBoolean(final Object[] array) {
        return (array.length <= 0
                || Boolean.parseBoolean(array[0].toString()));
    }

    private void putEventName(Object[] values) {
        final String componentId = values[0].toString();

        final Object[] eventNameFunctionId = new Object[] {
                values[1], values[2]
        };

        ArrayList<Object> eventsMap = events.getOrDefault(componentId,
                new ArrayList<>());
        eventsMap.add(eventNameFunctionId);

        events.put(componentId, eventsMap);
        Log.d(TAG, "putEventName: Registered event name and function Id " +
                Arrays.toString(eventNameFunctionId));
    }

    @Override
    public boolean onStopJob(JobParameters parms) {
        Log.d(TAG, "onStopJob: Job cancelled");

        // Job is now stopped, so now if
        // a component raises a component
        // raises an event even after calling
        // destroyComponent(String), then we will just ignore that
        isJobStopped = true;

        for (String key : manager.usedComponents()) {
            destroyComponent(key);
        }
        Log.d(TAG, "onStopJob: repeated is set to " + repeated);
        if (!repeated) {
            return false;
        }
        // this is important!
        jobFinished(parms, true);
        Log.d(TAG, "onStopJob: repeated mode " + defaultRepeatedMode);
        if (defaultRepeatedMode) {
            Log.d(TAG, "onStopJob: default repeated mode");
            return true;
        }
        Log.d(TAG, "onStopJob: attempting to send broadcast");
        // send a broadcast to
        // restart this service again
        sendBroadcast(prepareIntent(this, JOB_ID, (int) getValue(EXTRA_NETWORK,
                NETWORK_TYPE_NONE), foreground, argsForeground, repeated));
        return false;
    }

    // PARTS OF CODE FROM DYNAMIC_COMPONENTS
    // EXTENSION - AI2

    class MethodHandler {

        private static final String TAG = "MethodHandler";

        public Object invokeComponent(final Component component, final String methodName,
                                      Object[] params, Object[] extras) {
            Log.d(TAG, "invokeComponent: event parms(" + Arrays.toString(extras) + ")");
            Method method = findMethod(component.getClass().getMethods(),
                    methodName, params.length);

            for (int i = 0; i < params.length; i++) {
                params[i] = jet.process(params[i], extras);
            }

            final Object[] result = new Object[1];

//            manager.postRunnable(() -> {
//                try {
//                    result[0] = method.invoke(component,
//                            castParms(method.getParameterTypes(), params));
//                    Log.d(ActivityService.TAG, "invokeComponent() finish " + result[0]);
//                } catch (IllegalAccessException | InvocationTargetException e) {
//                    e.printStackTrace();
//                }
//            });
//            return result[0];
            try {
                return method.invoke(component,
                        castParms(method.getParameterTypes(), params));
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
            return "";
        }

        private Object[] castParms(Class<?>[] parmsTypes, Object[] parms) {
            for (int i = 0; i < parmsTypes.length; i++) {
                final Object object = parms[i];
                final String value = valueOf(object);

                switch (parmsTypes[i].getName()) {
                    case "int":
                        parms[i] = Integer.parseInt(value);
                        break;
                    case "float":
                        parms[i] = Float.parseFloat(value);
                        break;
                    case "double":
                        parms[i] = Double.parseDouble(value);
                        break;
                    case "java.lang.String":
                        parms[i] = value;
                        break;
                    case "boolean":
                        parms[i] = Boolean.parseBoolean(value);
                        break;
                    case "byte":
                        parms[i] = Byte.parseByte(value);
                }
            }
            return parms;
        }

        private Method findMethod(Method[] methods, String name, int parmsCount) {
            for (Method method : methods) {
                if (method.getName().equals(name) &&
                        method.getParameterTypes().length == parmsCount)
                    return method;
            }
            throw new IllegalArgumentException("Cannot find method named '" + name + "'!");
        }

        public Object emptyIfNull(Object o) {
            return (o == null) ? "" : o;
        }
    }
}
package xyz.kumaraswamy.tasks;

import bsh.EvalError;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.TinyDB;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import org.json.JSONException;
import xyz.kumaraswamy.tasks.node.Node;
import xyz.kumaraswamy.tasks.node.NodeEncoder;
import xyz.kumaraswamy.tasks.node.ValueNode;
import xyz.kumaraswamy.tasks.template.Load;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static android.text.TextUtils.isDigitsOnly;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static xyz.kumaraswamy.tasks.ActivityService.JOB;
import static xyz.kumaraswamy.tasks.ComponentManager.getSourceString;

public class Tasks extends AndroidNonvisibleComponent {

    public static final String TAG = "Tasks";

    private final Activity activity;
    private final TinyDB tinyDB;

    private final JobScheduler jobScheduler;
    private final AlarmManager alarmManager;

    static final int TASK_CREATE_FUNCTION = 0;
    static final int TASK_CALL_FUNCTION = 1;
    static final int TASK_REGISTER_EVENT = 2;
    static final int TASK_EXTRA_FUNCTION = 3;
    static final int TASK_CREATE_VARIABLE = 4;
    static final int TASK_CALL_FUNCTION_WITH_ARGS = 5;
    static final int TASK_WORK_WORK_FUNCTION = 6;
    static final int TASK_CALL_WORK_FUNCTION = 7;

    private int processTaskId = 0;

    private YailDictionary pendingTasks = new YailDictionary();
    private ArrayList<String> tasksProcessList = new ArrayList<>();

    private YailDictionary components = new YailDictionary();

    public static final String PENDING_TASKS = "pending_tasks";
    public static final String TASK_PROCESS_LIST = "process_list";
    public static final String FOREGROUND_MODE = "foreground_mode";
    public static final String FOREGROUND_CONFIG = "foreground_config";

    public static final String EXTRA_NETWORK = "extra_network";
    static final String REPEATED_EXTRA = "repeated_extra";
    static final String REPEATED_TYPE_EXTRA = "repeated_type_extra";
    static final String TERMINATE_EXTRA = "terminate_extra";

    private boolean exact = false;
    private boolean repeated = false;

    private String repeatedMode = "DEFAULT";

    private int timeout = -1;

    private String[] foreground = new String[] {
            "Tasks", "Foreground service", "Task is running!", ""
    };

    private static final LogUtil log = new LogUtil("Tasks");

    public Tasks(ComponentContainer container) {
        super(container.$form());

        tinyDB = new TinyDB(container);
        activity = container.$context();

        jobScheduler = getScheduler(activity);
        alarmManager = (AlarmManager) activity.getSystemService(ALARM_SERVICE);

        activity.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.log("onReceive", "received intent");
            initializeWork(
                    intent.getIntExtra(JOB, 0), 0, context, getScheduler(context),
                    intent.getIntExtra(EXTRA_NETWORK, JobInfo.NETWORK_TYPE_NONE),
                    intent.getBooleanExtra(FOREGROUND_MODE, false),
                    intent.getStringArrayExtra(FOREGROUND_CONFIG));
        }
    }

    public static JobScheduler getScheduler(Context context) {
        return (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
    }

    @DesignerProperty(
            editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
            defaultValue = "False")
    @SimpleProperty(description = "If the start the service at the exact time.")
    public void Exact(boolean bool) {
        exact = bool;
    }

    @SimpleProperty
    public boolean Exact() {
        return exact;
    }

    @DesignerProperty(
            editorType = PropertyTypeConstants.PROPERTY_TYPE_BOOLEAN,
            defaultValue = "False")
    @SimpleProperty(description =
            "Enable or disable if to start the service in a " +
                    "repeated cycle when the service is closed")
    public void Repeated(boolean bool) {
        repeated = bool;
    }

    @SimpleProperty
    public boolean Repeated() {
        return repeated;
    }

    @DesignerProperty(
            editorType = PropertyTypeConstants.PROPERTY_TYPE_CHOICES,
            editorArgs = {"DEFAULT", "HIGH"},
            defaultValue = "DEFAULT")
    @SimpleProperty(description =
            "Set the repeated task type, possible values " +
                    "are 'DEFAULT' and 'HIGH'")
    public void RepeatedType(String mode) {
        repeatedMode = mode;
    }

    @SimpleProperty
    public String RepeatedType() {
        return repeatedMode;
    }

    @DesignerProperty(
            editorType = PropertyTypeConstants.PROPERTY_TYPE_TEXTAREA,
            defaultValue = "DEFAULT")
    @SimpleProperty(description =
            "Sets the max time in ms the service can stay live. " +
                    "No action will be taken if value is set to 'DEFAULT'. " +
                    "Else a number value should be a non-negative integer. " +
                    "This feature is not available for exact tasks.")
    public void TimeOut(Object time) {
        if (time instanceof Number) {
            timeout = ((Number) time).intValue();
        } else if (time == "DEFAULT") {
            timeout = -1;
        } else {
            if (time instanceof String) {
                isDigitsOnly(time.toString());
            }
            throw new YailRuntimeError("Invalid value provided", TAG);
        }
    }

    @SimpleProperty
    public Object TimeOut() {
        return (timeout == -1 ? "DEFAULT" : timeout);
    }

    @SimpleFunction(description =
            "Starts the service. Id is the unique value identifier " +
                    "for your service. Specify a non-negative delay in ms for the latency. " +
                    "requiredNetwork allows you set the condition the service runs, possible " +
                    "values are 'ANY', 'CELLULAR', 'UN_METERED', 'NOT_ROAMING' and 'NONE'.")
    public Object Start(final int id, long latency, int requiredNetwork, boolean foreground) {
        int network;
        
        network = requiredNetwork;

        if (latency < 0) {
            throw new YailRuntimeError("Latency should be above or equal to 0.", TAG);
        }

        storeToDB(id, network);
        if (!exact) {
            if (!initializeWork(id, latency, activity, jobScheduler,
                    network, foreground, this.foreground)) {
                return false;
            }
            processTaskId = 0;
            pendingTasks = new YailDictionary();
            tasksProcessList = new ArrayList<>();
            components = new YailDictionary();
        } else {
            PendingIntent pd = getReceiverIntent(id, network, foreground, repeated);
            log.log("request to use alarm manager");
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + latency, pd), pd);
        }
        return true;
    }

    private void storeToDB(int id, int network) {
        log.log("storeToDB", tasksProcessList, pendingTasks);
        tinyDB.Namespace(TAG + id);

        final String[] tags = {
                JOB, PENDING_TASKS, TASK_PROCESS_LIST,
                EXTRA_NETWORK, REPEATED_EXTRA,
                REPEATED_TYPE_EXTRA, TERMINATE_EXTRA
        };
        final Object[] val = {
                components, pendingTasks,
                YailList.makeList(tasksProcessList),
                network, repeated, repeatedMode.equalsIgnoreCase("DEFAULT"),
                timeout
        };
        for (int i = 0; i < tags.length; i++) {
            tinyDB.StoreValue(tags[i], val[i]);
        }
    }

    public static boolean initializeWork(int id, long latency, Context context, JobScheduler jobScheduler, int network,
                                         boolean foreground, String[] foregroundConfig) {
        final PersistableBundle bundle = new PersistableBundle();

        bundle.putInt(JOB, id);
        bundle.putBoolean(FOREGROUND_MODE, foreground);
        bundle.putStringArray(FOREGROUND_CONFIG, foregroundConfig);

        ComponentName componentName = new ComponentName(context, ActivityService.class);
        JobInfo.Builder builder = new JobInfo.Builder(id, componentName);

        builder.setPersisted(true);
        builder.setMinimumLatency(latency);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setExtras(bundle);
        builder.setRequiredNetworkType(network);

        int resultCode = jobScheduler.schedule(builder.build());
        boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);
        log.log("initializeJob",
                "result " + success);
        return success;
    }

    public PendingIntent getReceiverIntent(int id, int network, boolean foreground, boolean repeated) {
        return PendingIntent.getBroadcast(activity, 0,
                /* Intent */ prepareIntent(activity, id, network, foreground, this.foreground, repeated),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static Intent prepareIntent(Context context, int id, int network, boolean foreground, String[] config, boolean repeated) {
        final Intent intent = new Intent(context, AlarmReceiver.class);

        final String[] ids = new String[] {
                JOB, EXTRA_NETWORK, FOREGROUND_MODE,
                FOREGROUND_CONFIG, REPEATED_EXTRA
        };

        final Object[] val = new Object[] {
                id, network, foreground,
                config, repeated
        };

        for (int i = 0; i < ids.length; i++) {
            final Object value = val[i];

            if (value instanceof Integer) {
                intent.putExtra(ids[i], (int) value);
            } else if (value instanceof Boolean) {
                intent.putExtra(ids[i], (boolean) value);
            } else {
                intent.putExtra(ids[i], (String[]) value);
            }
        }

        return intent;
    }

    @SimpleFunction(description =
            "Verifies if the current functions are going to " +
                    "cause any problem when the service is running.")
    public Object Validate() {
        if (components.size() == 0 || processTaskId <= 0) {
            return false;
        }

        final ArrayList<Integer> processIntTypes = new ArrayList<>();
        for (String item : tasksProcessList) {
            processIntTypes.add(Integer.parseInt
                    (item.substring(item.indexOf("/") + 1)));
        }

        return (processIntTypes.contains(TASK_CALL_FUNCTION)
                || processIntTypes.contains(TASK_REGISTER_EVENT))
                && processIntTypes.contains(TASK_CREATE_FUNCTION);
    }

    @SimpleFunction(description = "Cancels the task.")
    public void Cancel(int id) {
        jobScheduler.cancel(id);
    }

    @SimpleFunction(description = "Returns the list of non-visible internal method names")
    public YailList InternalMethods(final Component component) {
        Method[] methods = component.getClass().getDeclaredMethods();
        List<String> names = new ArrayList<>();
        for (Method method : methods) {
            names.add(method.getName());
        }
        return YailList.makeList(names);
    }

    @SimpleFunction(description = "Create components for the background use.")
    public void CreateComponent(final Object component, final String name) {
        components.put(name, getSourceString(component));
    }

    @SimpleFunction(description = "Creates the function that will be called in the background.")
    public void CreateFunction(final String id, final String component, final String block, final YailList values) {
        put(TASK_CREATE_FUNCTION, component, block, values, id);
    }

    @SimpleFunction(description = "Calls the function or extra function in the background.")
    public void CallFunction(final String id) {
        put(TASK_CALL_FUNCTION, id);
    }

    @SimpleFunction(description =
            "Calls the function or an extra function " +
            "with arguments.")
    public void CallFunctionWithArgs(final String id, YailList args) {
        put(TASK_CALL_FUNCTION_WITH_ARGS, id, args);
    }

    @SimpleFunction(description = "Registers the event for the component.")
    public void RegisterEvent(String component, String eventName, String functionId) {
        put(TASK_REGISTER_EVENT, component, eventName, functionId);
    }

    @SimpleFunction(description = "Creates an extra function.")
    public void ExtraFunction(final String id, YailList codes) {
        put(TASK_EXTRA_FUNCTION, id, codes.toArray());
    }

    @SimpleFunction(description =
            "Creates a variable that will store data. " +
                    "Special things cannot be stored!.")
    public void CreateVariable(String name, Object value) {
        put(TASK_CREATE_VARIABLE, name,
                value instanceof Number
                ? ((Number) value).intValue()
                : value);
    }

    @SimpleFunction(description = "Configures the foreground.")
    public void ConfigureForeground(String title, String content, String subtitle, String icon) {
        foreground = new String[] {
                title, content, subtitle, icon
        };
    }

    @SimpleFunction(description = "Check if a service is running through the service Id.")
    public boolean IsRunning(int id) {
        return pendingIds().contains(id);
    }

    @SimpleFunction(description = "Returns the list of running/pending services.")
    public YailList ActiveIDs() {
        return YailList.makeList(pendingIds());
    }

    @SimpleFunction(description =
            "Creates a node with value with right and left data")
    public Object CreateNode(String value, Object left, Object right) {
        if (!(left instanceof Node))
            left = new ValueNode(String.valueOf(left));
        if (!(right instanceof Node)) {
            right = new ValueNode(String.valueOf(right));
        }
        return new Node(value).setLeft(
                (Node) left).setRight((Node) right);
    }

    @SimpleFunction(description = "Creates a logic function. " +
            "This can be used to compare and work with things")
    public void WorkFunction(final String id, final String type, Object node) throws JSONException {
        if (!(node instanceof Node)) {
            throw new YailRuntimeError("The second value of the block must be a node.", TAG);
        }
        put(TASK_WORK_WORK_FUNCTION, id, type, NodeEncoder.encodeNode((Node) node, true));
    }

    @SimpleFunction(description = "Loads in the template and executes it.")
    public void LoadTemplate(final String asset, YailList parms) throws EvalError, IOException {
        new Load(asset, parms.toArray(),
                activity, this);
    }

    @SimpleFunction(description = "Calls the work function by its name")
    public void CallWorkFunction(final String id) {
        put(TASK_CALL_WORK_FUNCTION, id);
    }
    
    @SimpleProperty()
    public int NetworkANY(){
        return JobInfo.NETWORK_TYPE_ANY;
    }
    
    @SimpleProperty()
    public int NetworkCELLULAR(){
        return JobInfo.NETWORK_TYPE_CELLULAR;
    }
    
    @SimpleProperty()
    public int NetworkUNMETERED(){
        return JobInfo.NETWORK_TYPE_UNMETERED;
    }
    
    @SimpleProperty()
    public int NetworkROAMING(){
        return JobInfo.NETWORK_TYPE_NOT_ROAMING;
    }
    
    @SimpleProperty()
    public int NetworkNONE(){
        return JobInfo.NETWORK_TYPE_NONE;
    }

    private ArrayList<Integer> pendingIds() {
        final ArrayList<Integer> ids = new ArrayList<>();

        for (JobInfo info : jobScheduler.getAllPendingJobs()) {
            ids.add(info.getId());
        }

        return ids;
    }

    private void put(int task, Object... objects) {
        tasksProcessList.add(processTaskId + "/" + task);
        pendingTasks.put(processTaskId++, objects);
    }
}

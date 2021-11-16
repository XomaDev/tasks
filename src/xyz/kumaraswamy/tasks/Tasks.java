package xyz.kumaraswamy.tasks;

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
import bsh.EvalError;
import com.google.appinventor.components.annotations.DesignerProperty;
import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.common.PropertyTypeConstants;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.TinyDB;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;
import gnu.math.IntNum;
import org.json.JSONException;
import xyz.kumaraswamy.tasks.data.Procedures;
import xyz.kumaraswamy.tasks.node.Node;
import xyz.kumaraswamy.tasks.node.NodeEncoder;
import xyz.kumaraswamy.tasks.node.ValueNode;
import xyz.kumaraswamy.tasks.template.Load;
import xyz.kumaraswamy.tasks.tools.LogUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static java.lang.Integer.toHexString;
import static xyz.kumaraswamy.tasks.ActivityService.JOB;
import static xyz.kumaraswamy.tasks.ComponentManager.getSourceString;
import static xyz.kumaraswamy.tasks.tools.Common.*;

@SuppressWarnings("unused")
public class Tasks extends AndroidNonvisibleComponent {

    /**
     * log tag for debugging
     */
    public static final String TAG = "Tasks";

    private final Activity activity;

    /**
     * to store the tasks
     */
    private final TinyDB tinyDB;


    /**
     * Job initializers which makes background services
     * run.
     */

    private final JobScheduler jobScheduler;
    private final AlarmManager alarmManager;

    /**
     * The task type Ids, that reports what
     * type of task is this
     */

    static final int TASK_CREATE_FUNCTION = 0;
    static final int TASK_CALL_FUNCTION = 1;
    static final int TASK_REGISTER_EVENT = 2;
    static final int TASK_EXTRA_FUNCTION = 3;
    static final int TASK_CREATE_VARIABLE = 4;
    static final int TASK_CALL_FUNCTION_WITH_ARGS = 5;
    static final int TASK_WORK_WORK_FUNCTION = 6;
    static final int TASK_CALL_WORK_FUNCTION = 7;

    /**
     * the current count index of task
     */
    private int processTaskId = 0;

    /*
     pending tasks and the list of
     values that executes in an order
    */

    private YailDictionary pendingTasks = new YailDictionary();
    private ArrayList<String> tasksProcessList = new ArrayList<>();

    // the components list
    private YailDictionary components = new YailDictionary();

    /**
     * the tags for the values stored in
     * (identifiers) of the work that needs
     * to be done in the background
     */

    public static final String PENDING_TASKS = "pending_tasks";
    public static final String TASK_PROCESS_LIST = "process_list";
    public static final String FOREGROUND_MODE = "foreground_mode";
    public static final String FOREGROUND_CONFIG = "foreground_config";


    /**
     * Value identifiers and used for more
     * than one purposes
     */

    public static final String EXTRA_NETWORK = "extra_network";
    static final String REPEATED_EXTRA = "repeated_extra";
    static final String REPEATED_TYPE_EXTRA = "repeated_type_extra";
    static final String TERMINATE_EXTRA = "terminate_extra";

    /**
     * should the extension use alarm manager (exact)
     * or the job service directly
     */

    private boolean exact = false;

    /**
     * Should the service start itself
     * after onStopJobs(parms) being called?
     */

    private boolean repeated = false;
    private String repeatedMode = "DEFAULT";

    /**
     * The timeout for the service
     */

    private int timeout = -1;

    /**
     * Foreground notification values
     * titles, subtitles and others
     */
    private String[] foreground = {
            "Tasks", "Foreground service", "Task is running!", "DEFAULT"
    };

    private static final LogUtil log = new LogUtil("Tasks");

    public Tasks(ComponentContainer container) {
        super(container.$form());

        tinyDB = new TinyDB(container);
        activity = container.$context();

        jobScheduler = getScheduler(activity);
        alarmManager = (AlarmManager) activity.getSystemService(ALARM_SERVICE);

        /*
         reports the android system to keep the
         screen on.
        */

        activity.getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
    }


    /**
     * AlarmManager class
     * broadcast called by the alarm manager (even in the
     * background) that starts the job service
     * <p>
     * the intent contains the details
     */

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            log.log("onReceive", "received intent");
            initializeWork(
                    /* ID, latency, context, job scheduler */
                    intent.getIntExtra(JOB, 0), 0,
                    context, getScheduler(context),
                    /* network type */ intent.getIntExtra(
                            EXTRA_NETWORK, JobInfo.NETWORK_TYPE_NONE),
                    /* foreground */ intent.getBooleanExtra(
                            FOREGROUND_MODE, false),
                    /* foreground data (if there) */
                    intent.getStringArrayExtra(FOREGROUND_CONFIG),
                    /* To know who started this service? */"AlarmReceiver" +
                            toHexString(new Random().nextInt()));
        }
    }

    /**
     * Create a new JobScheduler
     *
     * @return JobScheduler
     */

    public static JobScheduler getScheduler(Context context) {
        return (JobScheduler) context.getSystemService(
                JOB_SCHEDULER_SERVICE);
    }

    /**
     * Property that states if to use the alarm
     * manager (if true) or the job scheduler API
     * <p>
     * the alarm manager raises the job service
     * once onReceive is called
     */

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

    /**
     * Should the service be repeated (restart)
     * itself once the job is flagged to be finished?
     *
     * @param bool if to restart service once being killed
     */

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

    /**
     * The repeated type that states if to let the system
     * start the service again or force start it by
     * alarm manager API
     *
     * @param mode ^^^^^
     */

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

    /**
     * The max time the service can stay live.
     * Once the time limit is reached, it flags the system
     * to stop this service
     *
     * @param time service max time limit (timeout)
     */

    @DesignerProperty(
            editorType = PropertyTypeConstants.PROPERTY_TYPE_TEXTAREA,
            defaultValue = "DEFAULT")
    @SimpleProperty(description =
            "Sets the max time in ms the service can stay live. " +
                    "No action will be taken if value is set to 'DEFAULT'. " +
                    "Else a number value should be a non-negative integer. " +
                    "This feature is not available for exact tasks.")
    public void TimeOut(Object time) {
        String string = String.valueOf(time);
        if (!string.equalsIgnoreCase("DEFAULT")) {
            timeout = (int) parseNumber(string, NUMBER_TYPE_INT);
        } else {
            timeout = -1;
        }
    }

    @SimpleProperty
    public Object TimeOut() {
        return (timeout == -1 ? "DEFAULT" : timeout);
    }

    /**
     * Starts the background task, if 'exact' is set to true,
     * alarm manager will be fired which will start the task from there
     *
     * @param id              Job ID
     * @param latency         Job latency
     * @param requiredNetwork type of network needed,
     *                        the job pauses if conditions aren't met
     * @param foreground      should it run foreground
     * @return successful
     */

    @SimpleFunction(description =
            "Starts the service. Id is the unique value identifier " +
                    "for your service. Specify a non-negative delay in ms for the latency. " +
                    "requiredNetwork allows you set the condition the service runs, possible " +
                    "values are 'ANY', 'CELLULAR', 'UN_METERED', 'NOT_ROAMING' and 'NONE'.")
    public Object Start(final int id, Object latency, String requiredNetwork, boolean foreground) {
        int requiredNetInt = getNetworkInt(requiredNetwork);

        namespaces(id, requiredNetInt);
        if (!exact) {
            long longLatency = (long) parseNumber(latency, NUMBER_TYPE_LONG);
            if (longLatency < 0) {
                throw new YailRuntimeError("Latency should be above or equal to 0.", TAG);
            }
            if (!initializeWork(id, longLatency, activity, jobScheduler,
                    requiredNetInt, foreground, this.foreground, "Tasks")) {
                return false;
            }
        } else {
            latency = latency(latency);
            log.log("Start", "latency (time) is " + latency);
            PendingIntent pd = getReceiverIntent(id, requiredNetInt, foreground, repeated);
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, (long) latency, pd);
        }
        resetTasks();
        return true;
    }

    private int getNetworkInt(String network) {
        switch (network.toLowerCase().trim()) {
            case "any":
                return JobInfo.NETWORK_TYPE_ANY;
            case "none":
                return JobInfo.NETWORK_TYPE_NONE;
            case "cellular":
                return JobInfo.NETWORK_TYPE_CELLULAR;
            case "unmetered":
                return JobInfo.NETWORK_TYPE_UNMETERED;
            case "roaming":
                return JobInfo.NETWORK_TYPE_NOT_ROAMING;
        }
        throw new YailRuntimeError("Expected a valid network type", TAG);
    }

    /**
     * Extracts the latency in millis
     * from the given object
     *
     * @return latency time in millis
     */

    private long latency(Object latency) {
        if (latency instanceof Calendar) {
            // it's in a form of a calendar object
            // extract the time in millis
            return  ((Calendar) latency).getTimeInMillis();
        } else if (latency instanceof IntNum) {
            // it's a direct input of latency
            return System.currentTimeMillis() +
                    Long.parseLong(latency.toString());
        }
        throw new IllegalArgumentException(
                "Expected a valid input for latency but got '"
                + latency.getClass() + "'");
    }

    /**
     * Resets the properties and tasks
     * which are stored, on every successfully
     * task initialization, its called
     */

    private void resetTasks() {
        processTaskId = 0;
        pendingTasks = new YailDictionary();
        tasksProcessList = new ArrayList<>();
        components = new YailDictionary();
    }

    /**
     * Puts the properties into the namespaces
     * which can be accessed later to run the job
     * @param id Job ID
     * @param network network type
     */

    private void namespaces(int id, int network) {
        log.log("namespaces", tasksProcessList, pendingTasks);
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

    /**
     * Initializes the job service, its called directly by Start() method
     * or by the alarm manager class
     *
     * @param id             Job ID
     * @param latency        Job latency
     * @param network        Network type
     * @param foreground     is it foreground?
     * @param fNotifications notification for the foreground mode
     * @param from           who called this method, for debugging
     * @return successful or not (result)
     */

    public static boolean initializeWork(int id, long latency, Context context,
                                         JobScheduler scheduler, int network,
                                         boolean foreground, String[] fNotifications, String from) {
        final PersistableBundle bundle = new PersistableBundle();

        // save the details to the bundle which
        // the service can directly read it

        bundle.putInt(JOB, id);
        bundle.putBoolean(FOREGROUND_MODE, foreground);
        bundle.putStringArray(FOREGROUND_CONFIG, fNotifications);
        bundle.putString("from", from);

        ComponentName componentName = new ComponentName(
                context, ActivityService.class);
        JobInfo builder =
                new JobInfo.Builder(id, componentName)
                        .setPersisted(true)
                        .setMinimumLatency(latency)
                        .setOverrideDeadline(latency)
                        .setExtras(bundle)
                        .setRequiredNetworkType(network)
                        .build();

        boolean success = (scheduler.schedule(builder)
                == JobScheduler.RESULT_SUCCESS);
        log.log("initializeJob",
                "result " + success);
        return success;
    }

    public PendingIntent getReceiverIntent(int id, int network, boolean foreground, boolean repeated) {
        return PendingIntent.getBroadcast(activity, 0,
                /* Intent */ prepareIntent(activity, id, network, foreground, this.foreground, repeated),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create and prepare the intent
     * with all the values put
     */

    public static Intent prepareIntent(Context context, int id, int network, boolean foreground, String[] config, boolean repeated) {
        final Intent intent = new Intent(context, AlarmReceiver.class);

        final String[] ids = new String[]{
                JOB, EXTRA_NETWORK, FOREGROUND_MODE,
                FOREGROUND_CONFIG, REPEATED_EXTRA
        };

        final Object[] val = new Object[]{
                id, network, foreground,
                config, repeated
        };

        // loop through the array and put them
        // as extra values into the intent

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

    /**
     * Registers the procedure so that it can be called
     * from the background
     * @param names procedure names list
     */

    @SimpleProperty(description = "Adds the procedures to the " +
            "memory to be called by the background service")
    public void Procedures(YailList names) throws Throwable {
        for (String name : names.toStringArray()) {
            Procedures.registerName(name, form);
        }
    }

    @SimpleProperty
    public YailList Procedures() {
        return YailList.makeList(Procedures.PROCEDURES.keySet());
    }

    /**
     * Cancels the job service
     * @param id Job ID
     */

    @SimpleFunction(description = "Cancels the task.")
    public void Cancel(int id) {
        jobScheduler.cancel(id);
    }

    /**
     * Adds a task to create the component in the background
     *
     * @param component Name/source/shortname of the component
     * @param name      the unique component Id
     */

    @SimpleFunction(description = "Create components for the background use.")
    public void CreateComponent(final Object component, final String name) {
        components.put(name, getSourceString(component));
    }

    /**
     * Creates a function in the background
     *
     * @param id        the unique ID for the function
     * @param component component Id
     * @param block     block name to be invoked
     * @param values    args or values for the block
     */

    @SimpleFunction(description = "Creates the function that will be called in the background.")
    public void CreateFunction(final String id, final String component, final String block, final YailList values) {
        put(TASK_CREATE_FUNCTION, component, block, values, id);
    }

    /**
     * Calls the function in the background
     *
     * @param id Id of the function
     */

    @SimpleFunction(description = "Calls the function or extra function in the background.")
    public void CallFunction(final String id) {
        put(TASK_CALL_FUNCTION, id);
    }

    /**
     * experimental!
     * Calls the function with the arguments provided
     *
     * @param id   Id of the function
     * @param args args/values to be called with
     */

//    @SimpleFunction(description =
//            "Calls the function or an extra function " +
//                    "with arguments.")
    public void CallFunctionWithArgs(final String id, YailList args) {
        put(TASK_CALL_FUNCTION_WITH_ARGS, id, args);
    }

    /**
     * Registers an event name for a work that can
     * be used to listen to the component events
     *
     * @param component  the component Id
     * @param name  the event name
     * @param then function Id that will be triggered when event is raised
     */

    @SimpleFunction(description = "Registers the event for the component.")
    public void ListenEvent(String name, String component, String then) {
        put(TASK_REGISTER_EVENT, component, name, then);
    }

    /**
     * Creates an extra function that be used to execute
     * java codes and call things in the background
     *
     * @param id    Unique Id of the extra function
     * @param codes A list of java codes
     */

    @SimpleFunction(description = "Creates an extra function.")
    public void ExtraFunction(final String id, YailList codes) {
        put(TASK_EXTRA_FUNCTION, id, codes.toArray());
    }

    /**
     * Creates a variable in the background
     *
     * @param name  of the variable
     * @param value for the variable
     */

    @SimpleFunction(description =
            "Creates a variable that will store data. " +
                    "Special things cannot be stored!.")
    public void CreateVariable(String name, Object value) {
        if (value instanceof Number) {
            // IntNum (Number) are special values
            // handle them
            value = ((Number) value).intValue();
        }
        put(TASK_CREATE_VARIABLE, name, value);
    }

    /**
     * Configures the notification for the foreground mode
     *
     * @param title    Title of the notification
     * @param content  Context text
     * @param subtitle Subtitle of the notification
     * @param icon     Icon (Int form or a name)
     */

    @SimpleFunction(description = "Configures the foreground.")
    public void ConfigureForeground(String title, String content, String subtitle, String icon) {
        foreground = new String[] {
                title, content, subtitle, icon
        };
    }

    /**
     * Checks if the service is running
     *
     * @param id Id of the service
     */

    @SimpleFunction(description = "Check if a service is running through the service Id.")
    public boolean IsRunning(int id) {
        return pendingIds().contains(id);
    }

    /**
     * Returns the active IDs in the list
     */

    @SimpleFunction(description = "Returns the list of running/pending services.")
    public YailList ActiveIDs() {
        return YailList.makeList(pendingIds());
    }

    /**
     * Experimental!
     * For testing purpose
     */

//    @SimpleFunction(description =
//            "Creates a node with value with right and left data")
    public Object CreateNode(String value, Object left, Object right) {
        if (!(left instanceof Node))
            left = new ValueNode(String.valueOf(left));
        if (!(right instanceof Node)) {
            right = new ValueNode(String.valueOf(right));
        }
        return new Node(value).setLeft(
                (Node) left).setRight((Node) right);
    }

    /**
     * Experimental!
     * For testing purpose
     */

//    @SimpleFunction(description = "Creates a logic function. " +
//            "This can be used to compare and work with things")
    public void WorkFunction(final String id, final String type, Object node) throws JSONException {
        if (!(node instanceof Node)) {
            throw new YailRuntimeError("The second value of the block must be a node.", TAG);
        }
        put(TASK_WORK_WORK_FUNCTION, id, type, NodeEncoder.encodeNode((Node) node, true));
    }

    /**
     * Experimental!
     * For testing purpose
     * More testing needed
     */

    @SimpleFunction(description = "Loads in the template and executes it.")
    public void LoadTemplate(final String asset, YailList parms) throws EvalError, IOException {
        new Load(asset, parms.toArray(),
                activity, this);
    }

    /**
     * Experimental!
     * For testing purpose
     */

//    @SimpleFunction(description = "Calls the work function by its name")
    public void CallWorkFunction(final String id) {
        put(TASK_CALL_WORK_FUNCTION, id);
    }

    /**
     * Returns the IDs of all the pending/running jobs
     */

    private ArrayList<Integer> pendingIds() {
        final List<JobInfo> jobInfos = jobScheduler.getAllPendingJobs();
        final ArrayList<Integer> ids = new ArrayList<>(jobInfos.size());

        for (JobInfo info : jobInfos) {
            ids.add(info.getId());
        }

        return ids;
    }

    /**
     * Puts the task into the task process list
     * @param task Task type
     * @param objects Name of the task
     */

    private void put(int task, Object... objects) {
        tasksProcessList.add(processTaskId + "/" + task);
        pendingTasks.put(processTaskId++, objects);
    }
}
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
import android.util.Log;

import android.view.Window;
import android.view.WindowManager;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static xyz.kumaraswamy.tasks.ActivityService.JOB;

public class Tasks extends AndroidNonvisibleComponent {

    public static final String TAG = "Tasks";

    private final Activity activity;
    private final TinyDB tinyDB;

    private JobCreator creator;

    private final JobScheduler jobScheduler;
    private final AlarmManager alarmManager;

    static final int TASK_CREATE_FUNCTION = 0;
    static final int TASK_CALL_FUNCTION = 1;
    static final int TASK_REGISTER_EVENT = 2;
    static final int TASK_EXTRA_FUNCTION = 3;

    public static final String PENDING_TASKS = "pending_tasks";
    public static final String TASK_PROCESS_LIST = "process_list";
    public static final String FOREGROUND_MODE = "foreground_mode";
    public static final String FOREGROUND_CONFIG = "foreground_config";

    static final String EXTRA_NETWORK = "extra_network";
    static final String REPEATED_EXTRA = "repeated_extra";
    static final String REPEATED_TYPE_EXTRA = "repeated_type_extra";

    private boolean exact = false;
    private boolean repeated = false;

    private String repeatedMode = "DEFAULT";

    private String[] foreground = new String[] {
            "Tasks", "Foreground service", "Task is running!", ""
    };

    public Tasks(ComponentContainer container) {
        super(container.$form());

        tinyDB = new TinyDB(container);
        activity = container.$context();

        jobScheduler = (JobScheduler) activity.getSystemService(JOB_SCHEDULER_SERVICE);
        alarmManager = (AlarmManager) activity.getSystemService(ALARM_SERVICE);

        Window window = activity.getWindow();

        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        creator = new JobCreator();
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: Received intent!");
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
            startWork(
                    intent.getIntExtra(JOB, 0), 0, context, jobScheduler,
                    intent.getIntExtra(EXTRA_NETWORK, JobInfo.NETWORK_TYPE_NONE),
                    intent.getBooleanExtra(FOREGROUND_MODE, false),
                    intent.getStringArrayExtra(FOREGROUND_CONFIG));
        }
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

    @SimpleFunction(description = "Starts the service.")
    public boolean Start(final int id, long latency, String requiredNetwork, boolean foreground) {
        requiredNetwork = requiredNetwork.toUpperCase();
        int network = JobInfo.NETWORK_TYPE_NONE;

        if (requiredNetwork.equals("ANY")) {
            network = JobInfo.NETWORK_TYPE_ANY;
        } else if (requiredNetwork.equals("CELLULAR")) {
            network = JobInfo.NETWORK_TYPE_CELLULAR;
        } else if (requiredNetwork.equals("UNMETERED")) {
            network = JobInfo.NETWORK_TYPE_UNMETERED;
        } else if (requiredNetwork.equals("NOT_ROAMING")) {
            network = JobInfo.NETWORK_TYPE_NOT_ROAMING;
        }

        if (latency < 0) {
            throw new YailRuntimeError("Latency should be above or equal to 0.", TAG);
        }

        storeToDataBase(id, network);
        if (exact) {
            PendingIntent pd = getReceiverIntent(id, network, foreground, repeated);

            Log.d(TAG, "Start: Request for exact start!");
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + latency, pd), pd);
            creator = new JobCreator();
            return true;
        }
        boolean success =  startWork(id, latency, activity, jobScheduler, network, foreground, this.foreground);

        if (success) {
            creator = new JobCreator();
        }
        return success;
    }

    private void storeToDataBase(int id, int network) {
        tinyDB.Namespace(TAG + id);

        tinyDB.StoreValue(JOB, creator.getComponents());
        tinyDB.StoreValue(PENDING_TASKS, creator.getPendingTasks());
        tinyDB.StoreValue(TASK_PROCESS_LIST, YailList.makeList(creator.getTasksProcessList()));
        tinyDB.StoreValue(EXTRA_NETWORK, network);
        tinyDB.StoreValue(REPEATED_EXTRA, repeated);
        tinyDB.StoreValue(REPEATED_TYPE_EXTRA, repeatedMode.equalsIgnoreCase("DEFAULT"));
    }

    private static boolean startWork(int id, long latency, Context context, JobScheduler jobScheduler, int network,
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

        Log.d(TAG, "Start: result " + success);
        return success;
    }

    public PendingIntent getReceiverIntent(int id, int network, boolean foreground, boolean repeated) {
        Intent startIntent = prepareIntent(activity, id, network, foreground, this.foreground, repeated);

        return PendingIntent.getBroadcast(activity, 0, startIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static Intent prepareIntent(Context context, int id, int network, boolean foreground, String[] config, boolean repeated) {
        final Intent intent = new Intent(context, AlarmReceiver.class);

        intent.putExtra(JOB, id);
        intent.putExtra(EXTRA_NETWORK, network);
        intent.putExtra(FOREGROUND_MODE, foreground);
        intent.putExtra(FOREGROUND_CONFIG, config);
        intent.putExtra(REPEATED_EXTRA, repeated);

        return intent;
    }

    @SimpleFunction(description = "Cancels the task.")
    public void Cancel(int id) {
        jobScheduler.cancel(id);
    }

    @SimpleFunction(description = "Create components for the background use.")
    public void CreateComponent(final Object component, final String name) {
        creator.putComponent(name, component);
    }

    @SimpleFunction(description = "Creates the function that will be called in the background.")
    public void CreateFunction(final String id, final String component, final String block, final YailList values) {
        creator.createFunction(id, component, block, values);
    }

    @SimpleFunction(description = "Calls the function in the background.")
    public void CallFunction(final String id) {
        creator.callFunction(id);
    }

    @SimpleFunction(description = "Registers the event for the component.")
    public void RegisterEvent(String component, String eventName, String functionId) {
        creator.registerEvent(component, eventName, functionId);
    }

    @SimpleFunction(description = "Creates an extra function.")
    public void ExtraFunction(final String id, YailList codes) {
        creator.putExtraFunction(id, codes);
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

    private ArrayList<Integer> pendingIds() {
        ArrayList<Integer> ids = new ArrayList<>();

        for (JobInfo info : jobScheduler.getAllPendingJobs()) {
            ids.add(info.getId());
        }

        return ids;
    }
}

class JobCreator {
    private int processTaskId = 0;

    private final YailDictionary pendingTasks = new YailDictionary();
    private final ArrayList<String> tasksProcessList = new ArrayList<>();

    static final int TASK_CREATE_FUNCTION = 0;
    static final int TASK_CALL_FUNCTION = 1;
    static final int TASK_REGISTER_EVENT = 2;
    static final int TASK_EXTRA_FUNCTION = 3;

    private final YailDictionary components = new YailDictionary();
    private final ArrayList<String> functions = new ArrayList<>();
    private final HashMap<String, String> events = new HashMap<>();
    private final ArrayList<String> extraFunctions = new ArrayList<>();

    public void putComponent(final String name, final Object component) {
        if (components.containsKey(name)) {
            throwExists("Component", name);
        }
        components.put(name, ComponentManager.getSourceString(component));
    }

    public void createFunction(final String id, final String component, final String block, final YailList values) {
        if (functions.contains(id)) {
            throwExists("Function", id);
        }
        functions.add(id);
        put(TASK_CREATE_FUNCTION, component, block, values, id);
    }

    public void callFunction(String id) {
        if (!functions.contains(id)) {
            simpleThrow("Function '" + id + "' not found");
        }
        put(TASK_CALL_FUNCTION, id);
    }

    public void registerEvent(String component, String eventName, String functionId) {
        if (events.containsKey(component) && events.get(component).equals(eventName)) {
            simpleThrow("Event name '" + component + "' already registered for the component.");
        }
        events.put(component, eventName);
        put(TASK_REGISTER_EVENT, component, eventName, functionId);
    }

    public void putExtraFunction(final String id, YailList codes) {
        if (extraFunctions.contains(id)) {
            throwExists("Extra function", id);
        }
        extraFunctions.add(id);
        put(TASK_EXTRA_FUNCTION, id, codes.toArray());
    }

    public YailDictionary getPendingTasks() {
        return pendingTasks;
    }

    public ArrayList<String> getTasksProcessList() {
        return tasksProcessList;
    }

    public YailDictionary getComponents() {
        return components;
    }

    private void put(int task, Object... objects) {
        tasksProcessList.add(processTaskId + "/" + task);
        pendingTasks.put(processTaskId, new ArrayList<>(Arrays.asList(objects)).toArray());
        processTaskId++;
    }

    private void throwExists(final String type, String name) {
        String error_exists = " name %s already exists.";
        simpleThrow((String.format(type + error_exists, name)));
    }

    private void simpleThrow(final String message) {
        String tag = "JobCreator";
        throw new YailRuntimeError(message, tag);
    }
}
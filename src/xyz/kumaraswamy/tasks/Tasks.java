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

import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.TinyDB;
import com.google.appinventor.components.runtime.errors.YailRuntimeError;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;

import java.util.ArrayList;
import java.util.Arrays;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.JOB_SCHEDULER_SERVICE;
import static xyz.kumaraswamy.tasks.ActivityService.JOB;

public class Tasks extends AndroidNonvisibleComponent {

    static final String TAG = "Tasks";

    private static Activity activity;
    private final TinyDB tinyDB;

    private static JobScheduler jobScheduler;
    private final AlarmManager alarmManager;

    private int processTaskId = 0;

    private final YailDictionary pendingTasks = new YailDictionary();
    private final ArrayList<String> tasksProcessList = new ArrayList<>();

    private final YailDictionary components = new YailDictionary();

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

    private boolean exact = false;
    private boolean repeated = false;

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
                    intent.getStringArrayExtra(FOREGROUND_CONFIG),
                    intent.getBooleanExtra(REPEATED_EXTRA, false));
        }
    }

    @SimpleProperty(description = "If the start the service at the exact time.")
    public void Exact(boolean bool) {
        exact = bool;
    }

    @SimpleProperty
    public boolean Exact() {
        return exact;
    }

    @SimpleProperty(description =
            "Enable or disable if to start the service in a " +
            "repeated cycle when the service is closed")
    public void RepeatedTask(boolean bool) {
        repeated = bool;
    }

    @SimpleProperty
    public boolean RepeatedTask() {
        return repeated;
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
            return true;
        }
        return startWork(id, latency, activity, jobScheduler, network, foreground, this.foreground, repeated);
    }

    private void storeToDataBase(int id, int network) {
        tinyDB.Namespace(TAG + id);

        tinyDB.StoreValue(JOB, components);
        tinyDB.StoreValue(PENDING_TASKS, pendingTasks);
        tinyDB.StoreValue(TASK_PROCESS_LIST, YailList.makeList(tasksProcessList));
        tinyDB.StoreValue(EXTRA_NETWORK, network);
    }

    private static boolean startWork(int id, long latency, Context context, JobScheduler jobScheduler, int network,
                                     boolean foreground, String[] foregroundConfig, boolean repeated) {
        final PersistableBundle bundle = new PersistableBundle();

        bundle.putInt(JOB, id);
        bundle.putBoolean(FOREGROUND_MODE, foreground);
        bundle.putStringArray(FOREGROUND_CONFIG, foregroundConfig);
        bundle.putBoolean(REPEATED_EXTRA, repeated);

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
        components.put(name, ComponentManager.getSourceString(component));
    }

    @SimpleFunction(description = "Creates the function that will be called in the background.")
    public void CreateFunction(final String id, final String component, final String block, final YailList values) {
        put(TASK_CREATE_FUNCTION, component, block, values, id);
    }

    @SimpleFunction(description = "Calls the function in the background.")
    public void CallFunction(final String id) {
        put(TASK_CALL_FUNCTION, id);
    }

    @SimpleFunction(description = "Registers the event for the component.")
    public void RegisterEvent(String component, String eventName, String functionId) {
        put(TASK_REGISTER_EVENT, component, eventName, functionId);
    }

    @SimpleFunction(description = "Creates an extra function.")
    public void ExtraFunction(final String id, YailList codes) {
        put(TASK_EXTRA_FUNCTION, id, codes.toArray());
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

        final boolean isOkay =
                (processIntTypes.contains(TASK_CALL_FUNCTION)
                || processIntTypes.contains(TASK_REGISTER_EVENT))
                && processIntTypes.contains(TASK_CREATE_FUNCTION);

        return isOkay;
    }

    private ArrayList<Integer> pendingIds() {
        ArrayList<Integer> ids = new ArrayList<>();

        for (JobInfo info : jobScheduler.getAllPendingJobs()) {
            ids.add(info.getId());
        }

        return ids;
    }

    private void put(int task, Object... objects) {
        tasksProcessList.add(processTaskId + "/" + task);
        pendingTasks.put(processTaskId, new ArrayList<>(Arrays.asList(objects)).toArray());
        processTaskId++;
    }
}

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

import com.google.appinventor.components.annotations.SimpleFunction;
import com.google.appinventor.components.annotations.SimpleProperty;
import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import com.google.appinventor.components.runtime.TinyDB;
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

    private static final String EXTRA_NETWORK = "extra_network";

    private boolean exact = false;
    private String[] foreground = new String[] {
            "Tasks", "Foreground service", "Task is running!", ""
    };

    public Tasks(ComponentContainer container) {
        super(container.$form());

        tinyDB = new TinyDB(container);
        activity = container.$context();

        jobScheduler = (JobScheduler) activity.getSystemService(JOB_SCHEDULER_SERVICE);
        alarmManager = (AlarmManager) activity.getSystemService(ALARM_SERVICE);
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: Received intent!");
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(JOB_SCHEDULER_SERVICE);
            startWork(intent.getIntExtra(JOB, 0), 0, context,
                    jobScheduler, intent.getIntExtra(EXTRA_NETWORK, JobInfo.NETWORK_TYPE_NONE),
                    intent.getBooleanExtra(FOREGROUND_MODE, false),
                    intent.getStringArrayExtra(FOREGROUND_CONFIG));
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

    @SimpleFunction(description = "Starts the service.")
    public boolean Start(final int id, long latency, String requiredNetwork, boolean foreground) {
        tinyDB.Namespace(TAG + id);

        tinyDB.StoreValue(JOB, components);
        tinyDB.StoreValue(PENDING_TASKS, pendingTasks);
        tinyDB.StoreValue(TASK_PROCESS_LIST, YailList.makeList(tasksProcessList));

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

        if (exact) {
            Log.d(TAG, "Start: Request for exact start!");
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(System.currentTimeMillis() + latency,
                    getReceiverIntent(id, network, foreground)), getReceiverIntent(id, network, foreground));
            return true;
        }
        return startWork(id, latency, activity, jobScheduler, network, foreground, this.foreground);
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

    public PendingIntent getReceiverIntent(int id, int network, boolean foreground) {
        final Intent intent = new Intent(activity, AlarmReceiver.class);
        intent.putExtra(JOB, id);
        intent.putExtra(EXTRA_NETWORK, network);
        intent.putExtra(FOREGROUND_MODE, foreground);
        intent.putExtra(FOREGROUND_CONFIG, this.foreground);
        return PendingIntent.getBroadcast(activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
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

    private void put(int task, Object... objects) {
        tasksProcessList.add(processTaskId + "/" + task);
        pendingTasks.put(processTaskId, new ArrayList<>(Arrays.asList(objects)).toArray());
        processTaskId++;
    }
}

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

import static xyz.kumaraswamy.tasks.ActivityService.JOB;
import static xyz.kumaraswamy.tasks.ComponentManager.componentSource;

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

    public static final String PENDING_TASKS = "pending_tasks";
    public static final String TASK_PROCESS_LIST = "process_list";

    private boolean exact = false;

    public Tasks(ComponentContainer container) {
        super(container.$form());

        tinyDB = new TinyDB(container);
        activity = container.$context();

        jobScheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        alarmManager = (AlarmManager) activity.getSystemService(Context.ALARM_SERVICE);
    }

    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive: Received intent!");
            JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            startWork(intent.getIntExtra(JOB, 0), 0, context, jobScheduler);
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
    public boolean Start(final int id, long latency) {
        tinyDB.Namespace(TAG + id);

        tinyDB.StoreValue(JOB, components);
        tinyDB.StoreValue(PENDING_TASKS, pendingTasks);
        tinyDB.StoreValue(TASK_PROCESS_LIST, YailList.makeList(tasksProcessList));

        if (exact) {
            Log.d(TAG, "Start: Request for exact start!");
            alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(
                    System.currentTimeMillis() + latency, getReceiverIntent(id)
            ), getReceiverIntent(id));
            return true;
        }
        return startWork(id, latency, activity, jobScheduler);
    }

    private static boolean startWork(int id, long latency, Context context, JobScheduler jobScheduler) {
        final PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(JOB, id);

        ComponentName componentName = new ComponentName(context, ActivityService.class);
        JobInfo.Builder builder =
                new JobInfo.Builder(id, componentName);

        builder.setPersisted(true);
        builder.setMinimumLatency(latency);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setExtras(bundle);

        int resultCode = jobScheduler.schedule(builder.build());
        boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);

        Log.d(TAG, "Start: result " + success);
        return success;
    }

    public PendingIntent getReceiverIntent(int id) {
        final Intent intent = new Intent(activity, AlarmReceiver.class);
        intent.putExtra(JOB, id);
        return PendingIntent.getBroadcast(activity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @SimpleFunction(description = "Cancels the task.")
    public void Cancel(int id) {
        jobScheduler.cancel(id);
    }

    @SimpleFunction(description = "Create components for the background use.")
    public void CreateComponent(final Object component, final String name) {
        components.put(name, componentSource(component));
    }

    @SimpleFunction(description = "Creates the function that will be called in the background.")
    public void CreateFunction(final String id, final String component, final String blockName, final YailList values) {
        tasksProcessList.add(processTaskId + "/" + TASK_CREATE_FUNCTION);
        pendingTasks.put(processTaskId, toObjectArray(component, blockName, values, id));
        processTaskId++;
    }

    @SimpleFunction(description = "Calls the function in the background.")
    public void CallFunction(final String id) {
        tasksProcessList.add(processTaskId + "/" + TASK_CALL_FUNCTION);
        pendingTasks.put(processTaskId, toObjectArray(id));
        processTaskId++;
    }

    private Object[] toObjectArray(final Object... objects) {
        return new ArrayList<>(Arrays.asList(objects)).toArray();
    }
}

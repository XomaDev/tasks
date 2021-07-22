package xyz.kumaraswamy.tasks;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.PersistableBundle;
import android.util.Log;
import com.google.appinventor.components.annotations.SimpleFunction;
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

    private final Activity activity;
    private final TinyDB tinyDB;

    private final JobScheduler jobScheduler;

    private int processTaskId = 0;

    private final YailDictionary pendingTasks = new YailDictionary();
    private final ArrayList<String> tasksProcessList = new ArrayList<>();

    private final YailDictionary components = new YailDictionary();

    static final int TASK_CREATE_FUNCTION = 0;
    static final int TASK_CALL_FUNCTION = 1;

    public static final String PENDING_TASKS = "pending_tasks";
    public static final String TASK_PROCESS_LIST = "process_list";

    public Tasks(ComponentContainer container) {
        super(container.$form());

        tinyDB = new TinyDB(container);
        activity = container.$context();
        jobScheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @SimpleFunction(description = "Starts the service.")
    public boolean Start(final int id) {
        tinyDB.Namespace(TAG + id);

        tinyDB.StoreValue(JOB, components);
        tinyDB.StoreValue(PENDING_TASKS, pendingTasks);
        tinyDB.StoreValue(TASK_PROCESS_LIST, YailList.makeList(tasksProcessList));

        final PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(JOB, id);

        ComponentName componentName = new ComponentName(activity, ActivityService.class);
        JobInfo.Builder builder =
                new JobInfo.Builder(id, componentName);

        builder.setPersisted(true);
        builder.setMinimumLatency(5 * 1000);
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE);
        builder.setExtras(bundle);

        int resultCode = jobScheduler.schedule(builder.build());
        boolean success = (resultCode == JobScheduler.RESULT_SUCCESS);

        Log.d(TAG, "Start: result " + success);
        return success;
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

package xyz.kumaraswamy.tasks;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import com.google.appinventor.components.runtime.Component;
import com.google.appinventor.components.runtime.TinyDB;
import com.google.appinventor.components.runtime.util.YailDictionary;
import com.google.appinventor.components.runtime.util.YailList;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static xyz.kumaraswamy.tasks.Tasks.TASK_CALL_FUNCTION;
import static xyz.kumaraswamy.tasks.Tasks.TASK_CREATE_FUNCTION;

public class ActivityService extends JobService {

    private static final String TAG = "ActivityService";
    public static final String JOB = "job";

    private int JOB_ID;
    private ComponentManager manager;
    private TinyDB tinyDB;

    private HashMap<String, Object[]> functions = new HashMap<>();

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

        ComponentManager.ComponentsBuiltListener builtListener = () -> {
            Log.d(TAG, "componentsBuilt: Components build!");
            YailDictionary pendingTasks = (YailDictionary) tinyDB.GetValue(Tasks.PENDING_TASKS, "");
            String[] taskProcessList =
                    ((ArrayList<?>) tinyDB.GetValue(Tasks.TASK_PROCESS_LIST, "")).toArray(new String[0]);

            Log.d(TAG, "processFunctions: " + pendingTasks);
            Log.d(TAG, "processFunctions: " + Arrays.toString(taskProcessList));
            try {
                processTasks(pendingTasks, taskProcessList);
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        new Handler(Looper.getMainLooper()).post(() -> {
            manager = new ComponentManager(ActivityService.this, builtListener);

            tinyDB = new TinyDB(manager.form());
            tinyDB.Namespace(Tasks.TAG + JOB_ID);

            HashMap<String, String> components = components();

            final Runnable runnable = () -> {
                manager.createComponents(components);
            };
            final Thread thread = new Thread(runnable);
            thread.start();
        });
    }

    private void processTasks(YailDictionary pd, String[] tasks) throws Exception {
        Log.d(TAG, "processTasks: ----------------------------");
        Log.d(TAG, "processTasks: " + Arrays.toString(tasks));
        Log.d(TAG, "processTasks: Tasks! " + tasks.length);
        for (String task : tasks) {
            Log.d(TAG, "processTasks: task " + task);

            String[] taskData = task.split("/");

            final int taskId = Integer.parseInt(taskData[0]);
            final int taskType = Integer.parseInt(taskData[1]);

            Log.d(TAG, "Task Id: " + taskId + ", taskType: " + taskType);

            Object[] taskValues = ((YailList) pd.get(taskId + "")).toArray();
            Log.d(TAG, "Task values: " + Arrays.toString(taskValues));

            if (taskType == TASK_CREATE_FUNCTION) {
                function(taskValues);
            } else if (taskType == TASK_CALL_FUNCTION) {
                handleFunction(taskValues);
            }
        }
    }

    private void function(Object[] taskValues) throws Exception {
        final Object[] values = new Object[]{taskValues[0], taskValues[1], taskValues[2]};
        final String functionName = taskValues[3].toString();

        if (!functions.containsKey(functionName)) {
            functions.put(functionName, values);
            Log.d(TAG, "function: Created function name " + functionName);
            return;
        }

        throw new Exception("The functions already contain the key \"" + functionName + "\".");
    }

    private void handleFunction(Object[] values) {
        final Object[] taskValues = functions.get(values[0].toString());

        if (taskValues == null || taskValues.length == 0) {
            Log.d(TAG, "Invalid invoke values provided");
            return;
        }

        final String key = taskValues[0].toString();
        final String methodName = taskValues[1].toString();
        final Object[] parms = ((YailList) taskValues[2]).toArray();
        Log.d(TAG, "handleFunction: parms for the function" + Arrays.toString(parms));

        final Component component = manager.component(key);

        if (component == null) {
            Log.d(TAG, "handleFunction: The component \"" + key + "\" does not exists!");
            return;
        }
        try {
            Object result = MethodHandler.invokeComponent(component, methodName, parms);
            Log.d(TAG, "handleFunction: Invoke result: " + result);
        } catch (InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private HashMap<String, String> components() {
        return (HashMap<String, String>) tinyDB.GetValue(JOB, new HashMap<String, String>());
    }

    @Override
    public boolean onStopJob(JobParameters parms) {
        Log.d(TAG, "onStopJob: Job cancelled");
        return false;
    }
}

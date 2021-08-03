package xyz.kumaraswamy.tasks.alarms;

import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import xyz.kumaraswamy.tasks.ActivityService;

import static xyz.kumaraswamy.tasks.Tasks.getScheduler;

public class Terminator extends BroadcastReceiver {
    private static final String TAG = "Terminator";

    @Override
    public void onReceive(Context context, Intent intent) {
        final int jobId = intent.getIntExtra(ActivityService.JOB, 0);
        Log.d(TAG, "onReceive: Intent received for id " + jobId);
        JobScheduler scheduler = getScheduler(context);
        scheduler.cancel(jobId);
    }
}

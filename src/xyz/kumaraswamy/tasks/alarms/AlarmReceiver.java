package xyz.kumaraswamy.tasks.alarms;

import android.app.job.JobInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import static xyz.kumaraswamy.tasks.ActivityService.JOB;
import static xyz.kumaraswamy.tasks.Tasks.EXTRA_NETWORK;
import static xyz.kumaraswamy.tasks.Tasks.FOREGROUND_CONFIG;
import static xyz.kumaraswamy.tasks.Tasks.FOREGROUND_MODE;
import static xyz.kumaraswamy.tasks.Tasks.getScheduler;
import static xyz.kumaraswamy.tasks.Tasks.startWork;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Received intent!");
        startWork(
                intent.getIntExtra(JOB, 0), 0, context, getScheduler(context),
                intent.getIntExtra(EXTRA_NETWORK, JobInfo.NETWORK_TYPE_NONE),
                intent.getBooleanExtra(FOREGROUND_MODE, false),
                intent.getStringArrayExtra(FOREGROUND_CONFIG));
    }
}
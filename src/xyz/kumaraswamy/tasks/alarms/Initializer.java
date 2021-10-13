// initializer class that initializes
// all the alarm present here
// called and used ActivityService and Tasks
// classes

package xyz.kumaraswamy.tasks.alarms;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import xyz.kumaraswamy.tasks.ActivityService;
import xyz.kumaraswamy.tasks.Common;

public class Initializer {

    private final Context context;

    // the common handler
    private final Common common;

    // the alarm class to start on
    private Class<?> alarm;

    // the job id
    private int id;

    public Initializer(Context context, Common common) {
        this.context = context;
        this.common = common;
    }

    public Initializer create(Class<?> alarm) {
        this.alarm = alarm;
        return this;
    }

    public Initializer setJobId(int id) {
        this.id = id;
        return this;
    }

    /**
     * Initializes the alarm with the manager
     * and starts its using setExactAndAllowWhileIdle
     * method in the AlarmManager.class
     *
     * @param timeout System.currentTimeMillis() + timeout
     */

    public void startExactAndAllowWhileIdle(int timeout) {
        final Intent intent = new Intent(context, alarm);
        intent.putExtra(ActivityService.JOB, id);

        final PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, id,
                intent, PendingIntent.FLAG_UPDATE_CURRENT);
        ((AlarmManager) common.getService(Context.ALARM_SERVICE)).
                setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + timeout,
                        pendingIntent
                );
    }
}

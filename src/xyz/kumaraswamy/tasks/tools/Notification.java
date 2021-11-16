package xyz.kumaraswamy.tasks.tools;

import android.app.NotificationChannel;
import android.app.NotificationManager;

import static android.content.Context.NOTIFICATION_SERVICE;

public class Notification {

    private final Common common;

    public Notification(Common common) {
        this.common = common;
    }

    // the properties for the notifications
    // includes smallIcon, title, contentText
    // and the subtext of the notification

    private int iconInt;

    private String title;

    private String text;

    private String subtext;

    public Notification setSmallIcon(final String icon) throws NoSuchFieldException, IllegalAccessException {
        if (icon.equalsIgnoreCase("DEFAULT")) {
            // set the default icon
            iconInt = android.R.drawable.ic_dialog_alert;
        } else {
            iconInt =  android.R.drawable.class.getField(icon.trim())
                    .getInt(new android.R.drawable());
        }
        return this;
    }

    public Notification configure(String title, String text, String subtext) {
        this.title = title;
        this.text = text;
        this.subtext = subtext;
        return this;
    }

    public android.app.Notification buildNotification() {
        makeChannel();
        return getNotification();
    }

    private android.app.Notification getNotification() {
        return new android.app.Notification.Builder(common.getContext(), "Tasks")
                .setSmallIcon(iconInt)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(subtext)
                .build();
    }

    private void makeChannel() {
        NotificationChannel channel = new NotificationChannel(
                "Tasks", "ApplicationTask",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        ((NotificationManager) common.getService(NOTIFICATION_SERVICE))
                .createNotificationChannel(channel);
    }
}

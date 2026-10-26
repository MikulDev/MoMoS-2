package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.serialization.JsonCodec;
import com.momosoftworks.momos.util.image.ImageHelper;
import javafx.scene.image.Image;

/**
 * What the history keeps of a notification. Only plain data, so it can be saved and outlive both the
 * notification and the app that sent it.
 *
 * @param icon      a file path or icon theme name. Blank if the notification had none
 * @param timestamp when it arrived, in epoch milliseconds
 * @param appClass  the class of the window belonging to the app that sent it. Blank if no window did
 * @param appName   the name the app sent along with it
 */
public record NotificationRecord(String title, String text, String icon, long timestamp, String appClass, String appName)
{
    public static final JsonCodec<NotificationRecord> CODEC = JsonCodec.construct(NotificationRecord.class)
            .arg("title", JsonCodec.STRING, NotificationRecord::title)
            .arg("text", JsonCodec.STRING, NotificationRecord::text)
            .arg("icon", JsonCodec.STRING, NotificationRecord::icon)
            .arg("timestamp", JsonCodec.LONG, NotificationRecord::timestamp)
            .arg("app_class", JsonCodec.STRING, NotificationRecord::appClass)
            .arg("app_name", JsonCodec.STRING, NotificationRecord::appName)
            .build();

    public NotificationRecord
    {
        // Senders may leave any of these out, and null can't be saved
        title = title == null ? "" : title;
        text = text == null ? "" : text;
        icon = icon == null ? "" : icon;
        appClass = appClass == null ? "" : appClass;
        appName = appName == null ? "" : appName;
    }

    public NotificationRecord withIcon(String icon)
    {   return new NotificationRecord(title, text, icon, timestamp, appClass, appName);
    }

    /** Who to show the notification as coming from */
    public String source()
    {   return appClass.isBlank() ? appName : appClass;
    }

    /**
     * Loads the notification's icon, or failing that its app's. Null if neither can be found.
     * Reads from disk, so keep it off the FX thread.
     */
    public Image loadIcon(int size)
    {
        for (String name : new String[]{ icon, appClass.toLowerCase(), appName.toLowerCase() })
        {
            // Some apps send their icon as a file:// URI
            String path = ImageHelper.getIconPath(name.startsWith("file://") ? name.substring(7) : name);
            Image image = path != null ? ImageHelper.getIcon(path, size) : null;
            if (image != null && !image.isError()) return image;
        }
        return null;
    }
}

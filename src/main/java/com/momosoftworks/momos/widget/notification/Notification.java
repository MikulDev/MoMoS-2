package com.momosoftworks.momos.widget.notification;

import javafx.scene.image.Image;
import javafx.util.Duration;
import org.freedesktop.dbus.types.UInt32;

import java.util.List;

/**
 * A notification as it arrived over D-Bus, with what's needed to show it on screen.
 *
 * @param record  what the history keeps of it
 * @param icon    null if it has none
 * @param timeout how long it stays on screen. {@link Duration#INDEFINITE} if it stays until dismissed
 */
public record Notification(int id, NotificationRecord record, Image icon, List<Action> actions, Duration timeout)
{
    /** The action invoked by clicking the notification itself, rather than one of its buttons */
    public static final String DEFAULT_ACTION = "default";

    /** A choice the notification offers. Invoking it tells the sender which {@code key} was picked */
    public record Action(String key, String label) {}

    public boolean hasAction(String key)
    {   return actions.stream().anyMatch(action -> action.key().equals(key));
    }

    public enum CloseReason
    {
        EXPIRED, DISMISSED, METHOD_CALL, UNKNOWN;

        public UInt32 id()
        {   return new UInt32(this.ordinal() + 1);
        }

        public static CloseReason fromID(int id)
        {
            if (id <= 0 || id > values().length)
            {   return UNKNOWN;
            }
            else return CloseReason.values()[id - 1];
        }
    }
}

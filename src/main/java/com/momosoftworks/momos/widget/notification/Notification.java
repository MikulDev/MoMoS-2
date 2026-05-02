package com.momosoftworks.momos.widget.notification;

import org.freedesktop.dbus.types.UInt32;

public record Notification(int id, String appName, String desktopEntry, String summary, String body, String iconPath, long timestamp)
{
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

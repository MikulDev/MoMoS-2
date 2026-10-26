package com.momosoftworks.momos.widget.notification;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.List;
import java.util.Map;

@DBusInterfaceName("org.freedesktop.Notifications")
public interface INotifications extends DBusInterface
{
    @SuppressWarnings("rawtypes")
    UInt32 Notify(String app_name, UInt32 replaces_id, String app_icon,
                  String summary, String body, List<String> actions,
                  Map<String, Variant> hints, int expire_timeout);

    void CloseNotification(UInt32 id);

    List<String> GetCapabilities();

    ServerInfo<String, String, String, String> GetServerInformation();

    // Signals take their interface name from the class they're nested in, so they must live here

    class NotificationClosed extends DBusSignal
    {
        public final UInt32 id;
        public final UInt32 reason;

        public NotificationClosed(String path, UInt32 id, UInt32 reason) throws DBusException
        {
            super(path, id, reason);
            this.id = id;
            this.reason = reason;
        }
    }

    class ActionInvoked extends DBusSignal
    {
        public final UInt32 id;
        public final String actionKey;

        public ActionInvoked(String path, UInt32 id, String actionKey) throws DBusException
        {
            super(path, id, actionKey);
            this.id = id;
            this.actionKey = actionKey;
        }
    }
}

package com.momosoftworks.momos.widget.notification;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
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
}

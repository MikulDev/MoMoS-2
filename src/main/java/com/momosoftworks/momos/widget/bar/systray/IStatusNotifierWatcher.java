package com.momosoftworks.momos.widget.bar.systray;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("org.kde.StatusNotifierWatcher")
public interface IStatusNotifierWatcher extends DBusInterface
{
    void RegisterStatusNotifierHost(String service);
    void RegisterStatusNotifierItem(String serviceOrPath);

    class StatusNotifierItemRegistered extends DBusSignal
    {
        public final String service;

        public StatusNotifierItemRegistered(String path, String service) throws DBusException
        {
            super(path, service);
            this.service = service;
        }
    }

    class StatusNotifierItemUnregistered extends DBusSignal
    {
        public final String service;

        public StatusNotifierItemUnregistered(String path, String service) throws DBusException
        {
            super(path, service);
            this.service = service;
        }
    }

    class StatusNotifierHostRegistered extends DBusSignal
    {
        public StatusNotifierHostRegistered(String path) throws DBusException
        {
            super(path);
        }
    }
}

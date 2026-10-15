package com.momosoftworks.momos.widget.bar.systray;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.messages.DBusSignal;

@DBusInterfaceName("org.kde.StatusNotifierItem")
public interface IStatusNotifierItem extends DBusInterface
{
    void Activate(int x, int y);
    void SecondaryActivate(int x, int y);
    void ContextMenu(int x, int y);

    class NewIcon extends DBusSignal
    {
        public NewIcon(String path) throws DBusException { super(path); }
    }

    class NewStatus extends DBusSignal
    {
        public final String status;
        public NewStatus(String path, String status) throws DBusException
        {   super(path, status); this.status = status;
        }
    }
}

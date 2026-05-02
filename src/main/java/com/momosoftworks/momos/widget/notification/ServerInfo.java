package com.momosoftworks.momos.widget.notification;

import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.annotations.Position;

public class ServerInfo extends Struct
{
    @Position(0) public final String name;
    @Position(1) public final String vendor;
    @Position(2) public final String version;
    @Position(3) public final String specVersion;

    public ServerInfo(String name, String vendor, String version, String specVersion)
    {
        this.name        = name;
        this.vendor      = vendor;
        this.version     = version;
        this.specVersion = specVersion;
    }
}

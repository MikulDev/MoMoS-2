package com.momosoftworks.momos.widget.notification;

import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.annotations.Position;

public class ServerInfo<A, B, C, D> extends Tuple
{
    @Position(0) public final A name;
    @Position(1) public final B vendor;
    @Position(2) public final C version;
    @Position(3) public final D specVersion;

    public ServerInfo(A name, B vendor, C version, D specVersion)
    {
        this.name        = name;
        this.vendor      = vendor;
        this.version     = version;
        this.specVersion = specVersion;
    }
}

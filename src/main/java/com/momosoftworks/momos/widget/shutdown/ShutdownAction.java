package com.momosoftworks.momos.widget.shutdown;

import com.momosoftworks.momos.util.ui.Icon;

public enum ShutdownAction
{
    SHUT_DOWN("Shut Down", Icon.SHUTDOWN, "shutdown", "now"),
    RESTART("Restart", Icon.RESTART, "reboot"),
    SLEEP("Sleep", Icon.SLEEP, "systemctl", "suspend");

    private final String label;
    private final Icon icon;
    private final String[] command;

    ShutdownAction(String label, Icon icon, String... command)
    {
        this.label = label;
        this.icon = icon.sized(30);
        this.command = command;
    }

    public String label()
    {   return label;
    }

    public Icon icon()
    {   return icon;
    }

    public String[] command()
    {   return command;
    }
}

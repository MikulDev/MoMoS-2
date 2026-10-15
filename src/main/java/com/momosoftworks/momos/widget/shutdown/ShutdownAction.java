package com.momosoftworks.momos.widget.shutdown;

import javafx.scene.image.Image;

public enum ShutdownAction
{
    SHUT_DOWN("Shut Down", "icons/shutdown.png", "shutdown", "now"),
    RESTART("Restart", "icons/restart.png", "reboot"),
    SLEEP("Sleep", "icons/sleep.png", "systemctl", "suspend");

    private final String label;
    private final Image icon;
    private final String[] command;

    ShutdownAction(String label, String iconPath, String... command)
    {
        this.label = label;
        this.icon = new Image(iconPath, 30, 30, true, true);
        this.command = command;
    }

    public String label()
    {   return label;
    }

    public Image icon()
    {   return icon;
    }

    public String[] command()
    {   return command;
    }
}

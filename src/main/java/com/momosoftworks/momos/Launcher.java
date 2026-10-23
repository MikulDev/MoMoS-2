package com.momosoftworks.momos;

public class Launcher
{
    public static void main(String[] args)
    {   System.setProperty("java.awt.WM_CLASS", "MomosWidget");
        // Transparent stages on GTK aren't vsync-paced, so they fall back to JavaFX's fixed pulse timer (60 Hz by default)
        if (System.getProperty("javafx.animation.pulse") == null)
        {   System.setProperty("javafx.animation.pulse", "240");
        }
        MomosApp.main(args);
    }
}

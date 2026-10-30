package com.momosoftworks.momos.util.wm;

import com.google.gson.JsonElement;
import com.momosoftworks.momos.MomosApp;
import com.momosoftworks.momos.widget.app_launcher.Application;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class CommandHelper
{
    private static final String DEFAULT_TERMINAL = "alacritty";

    public static void execute(String... command)
    {
        try
        {   new ProcessBuilder(command).start();
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
    }

    public static String executeWithResult(String... command)
    {
        try
        {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            return new String(p.getInputStream().readAllBytes()).trim();
        }
        catch (Exception e)
        {   e.printStackTrace(); return "";
        }
    }

    public static BufferedReader executeAndRead(String... command)
    {
        try
        {   ProcessBuilder pb = new ProcessBuilder(command);
            Process proc = pb.start();
            return new BufferedReader(new InputStreamReader(proc.getInputStream()));
        }
        catch (Exception e)
        {   e.printStackTrace();
            return null;
        }
    }

    public static void launchProgram(Application app)
    {   launchDetached("gio", "launch", app.path());
    }

    /** Opens the home directory in the default file browser. */
    public static void openFileBrowser()
    {   launchDetached("xdg-open", System.getenv("HOME"));
    }

    public static void openTerminal()
    {   launchDetached(getTerminal());
    }

    /** The terminal emulator to use. Set with "terminal" in the config file. */
    public static String getTerminal()
    {
        JsonElement terminal = MomosApp.getConfigSetting("terminal");
        return terminal != null && terminal.isJsonPrimitive() ? terminal.getAsString() : DEFAULT_TERMINAL;
    }

    /** Starts a program from the home directory, discarding its output. */
    private static void launchDetached(String... command)
    {
        try
        {
            new ProcessBuilder(command)
                    .directory(new File(System.getenv("HOME")))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
        catch (Exception e) { e.printStackTrace(); }
    }
}

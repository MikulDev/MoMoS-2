package com.momosoftworks.momos.util.wm;

import com.momosoftworks.momos.widget.app_launcher.Application;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class CommandHelper
{
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
    {
        try
        {
            new ProcessBuilder("gio", "launch", app.path())
                    .directory(new File(System.getenv("HOME")))
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        }
        catch (Exception e) { e.printStackTrace(); }
    }
}

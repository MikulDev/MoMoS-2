package com.momosoftworks.momos.util.wm;

import java.io.BufferedReader;
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
}

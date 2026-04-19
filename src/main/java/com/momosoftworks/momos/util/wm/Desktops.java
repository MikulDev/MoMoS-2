package com.momosoftworks.momos.util.wm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

public class Desktops
{
    public static String fromID(String id)
    {
        try
        {   ProcessBuilder pb = new ProcessBuilder("bspc", "query", "-D", "-d", id, "--names");
            Process proc = pb.start();
            return new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine();
        }
        catch (Exception e)
        {   e.printStackTrace();
            return null;
        }
    }

    public static void switchTo(String desktop)
    {   CommandHelper.execute("bspc", "desktop", desktop, "-f");
    }

    public static String getFocused()
    {   return CommandHelper.executeWithResult("bspc", "query", "-D", "-d", "focused", "--names");
    }
    public static String getFocused(String monitor)
    {   return CommandHelper.executeWithResult("bspc", "query", "-D", "-d", "focused", "-m", monitor, "--names");
    }

    public static void focusWindow(String windowID)
    {   CommandHelper.execute("bspc", "node", windowID, "-f");
    }

    public static List<String> getAll()
    {   return getAll(null, null);
    }
    public static List<String> getAll(String monitor)
    {   return getAll(monitor, null);
    }

    public static List<String> getAll(String monitor, Flag filter)
    {
        try
        {
            List<String> command = new ArrayList<>(Arrays.asList("bspc", "query", "-D", "-d"));
            if (filter != null)
            {   command.add(filter.flag());
            }
            if (monitor != null)
            {   command.addAll(List.of("-m", monitor));
            }
            command.add("--names");
            BufferedReader reader = CommandHelper.executeAndRead(command.toArray(new String[0]));
            if (reader == null) return List.of();
            List<String> desktops = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
            {   desktops.add(line.trim());
            }
            return desktops;
        }
        catch (Exception e)
        {   e.printStackTrace();
            return List.of();
        }
    }

    public static boolean hasFlag(String desktop, Flag flag)
    {
        try
        {   ProcessBuilder pb = new ProcessBuilder("bspc", "query", "-D", "-d", desktop + flag.flag(), "--names");
            Process proc = pb.start();
            return new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine() != null;
        }
        catch (Exception e)
        {   e.printStackTrace();
            return false;
        }
    }

    public enum Flag
    {
        OCCUPIED(".occupied"),
        URGENT(".urgent");

        private final String flag;

        Flag(String flag)
        {   this.flag = flag;
        }

        public String flag()
        {   return flag;
        }
    }
}

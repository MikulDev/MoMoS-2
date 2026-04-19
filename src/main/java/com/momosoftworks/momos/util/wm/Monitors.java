package com.momosoftworks.momos.util.wm;

import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Monitors
{
    public static String fromID(String id)
    {
        try
        {   ProcessBuilder pb = new ProcessBuilder("bspc", "query", "-M", "-m", id, "--names");
            Process proc = pb.start();
            return new BufferedReader(new InputStreamReader(proc.getInputStream())).readLine();
        }
        catch (Exception e)
        {   e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns the bounds of the named BSPWM monitor (e.g. "LEFT", "RIGHT", "focused").
     * Falls back to the primary JavaFX screen bounds if the query fails.
     */
    public static Rectangle2D getBounds(String monitor)
    {
        try
        {   Process proc = new ProcessBuilder("bspc", "query", "-T", "-m", monitor).start();
            String json = new String(proc.getInputStream().readAllBytes());
            Matcher m = Pattern.compile(
                    "\"rectangle\":\\{\"x\":(\\d+),\"y\":(\\d+),\"width\":(\\d+),\"height\":(\\d+)\\}"
            ).matcher(json);
            if (m.find())
                return new Rectangle2D(
                        Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                        Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))
                );
        }
        catch (IOException e)
        {   e.printStackTrace();
        }
        return Screen.getPrimary().getBounds();
    }
}

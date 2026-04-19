package com.momosoftworks.momos.util.wm;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Windows
{
    /**
     * Fetches the _NET_WM_ICON for a given X window ID and returns the largest
     * available icon as a JavaFX Image, or null if unavailable.
     */
    public static Image getIcon(String windowId)
    {
        try
        {
            BufferedReader reader = CommandHelper.executeAndRead(
                    "xprop", "-id", windowId,
                    "-len", "1048576",
                    "-f", "_NET_WM_ICON", "32c",
                    "_NET_WM_ICON");
            if (reader == null) return null;

            List<Long> values = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null)
            {
                // Strip the property name prefix on the first line
                int eq = line.indexOf('=');
                if (eq >= 0) line = line.substring(eq + 1);

                for (String token : line.split("[,\\s]+"))
                {
                    token = token.trim();
                    if (!token.isEmpty())
                    {
                        try { values.add(Long.parseLong(token)); }
                        catch (NumberFormatException ignored) {}
                    }
                }
            }

            if (values.isEmpty()) return null;
            return decodeLargestIcon(values);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * _NET_WM_ICON is a sequence of icons packed as:
     *   [width, height, pixel0, pixel1, ... pixelN, width, height, ...]
     * Each pixel is a 32-bit ARGB value. This picks the largest icon available.
     */
    private static Image decodeLargestIcon(List<Long> values)
    {
        int bestOffset = -1;
        int bestSize   = -1;
        int offset     = 0;

        // Walk the icon list to find the largest one
        while (offset + 2 < values.size())
        {
            int w = values.get(offset).intValue();
            int h = values.get(offset + 1).intValue();
            if (w <= 0 || h <= 0) break;

            int pixelCount = w * h;
            if (offset + 2 + pixelCount > values.size()) break;

            if (w * h > bestSize)
            {
                bestSize   = w * h;
                bestOffset = offset;
            }
            offset += 2 + pixelCount;
        }

        if (bestOffset < 0)
            return null;

        int w = values.get(bestOffset).intValue();
        int h = values.get(bestOffset + 1).intValue();

        WritableImage image = new WritableImage(w, h);
        PixelWriter writer  = image.getPixelWriter();

        for (int i = 0; i < w * h; i++)
        {
            long argb = values.get(bestOffset + 2 + i) & 0xFFFFFFFFL;
            int a = (int) ((argb >> 24) & 0xFF);
            int r = (int) ((argb >> 16) & 0xFF);
            int g = (int) ((argb >>  8) & 0xFF);
            int b = (int) ((argb      ) & 0xFF);
            writer.setArgb(i % w, i / w, (a << 24) | (r << 16) | (g << 8) | b);
        }

        return image;
    }

    /** Fetch window title via xprop */
    public static String getTitle(String nodeId)
    {
        try
        {
            String out = CommandHelper.executeWithResult("xprop", "-id", nodeId, "WM_NAME");
            // WM_NAME(UTF8_STRING) = "title"
            int eq = out.indexOf('=');
            if (eq >= 0)
            {
                String title = out.substring(eq + 1).trim().replaceAll("^\"|\"$", "");
                if (!title.isEmpty() && !title.equals(nodeId))
                {   return title;
                }
            }
        }
        catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static List<String> getForDesktop(String desktop)
    {
        try
        {
            ProcessBuilder pb = new ProcessBuilder("bspc", "query", "-N", "-d", desktop);
            Process proc = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            List<String> windows = new ArrayList<>();
            String nodeId;
            while ((nodeId = r.readLine()) != null)
            {   nodeId = nodeId.trim();
                if (!nodeId.isEmpty())
                {   windows.add(nodeId);
                }
            }
            return windows;
        }
        catch (Exception e) { e.printStackTrace(); return List.of(); }
    }
}
package com.momosoftworks.momos.util.wm;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Windows
{
    /** A managed window. {@code pid} is -1 if the window doesn't say which process owns it */
    public record WindowInfo(String id, long pid, String wmClass, String title) {}

    private static final Pattern PID_PROPERTY = Pattern.compile("^_NET_WM_PID\\(CARDINAL\\) = (\\d+)$", Pattern.MULTILINE);
    // WM_CLASS holds the instance name, then the class name
    private static final Pattern CLASS_PROPERTY = Pattern.compile("^WM_CLASS\\([^)]*\\) = \"[^\"]*\", \"([^\"]*)\"$", Pattern.MULTILINE);
    // _NET_WM_NAME is requested first, so it wins over WM_NAME when a window has both
    private static final Pattern NAME_PROPERTY = Pattern.compile("^(?:_NET_WM_NAME|WM_NAME)\\([^)]*\\) = \"(.*)\"$", Pattern.MULTILINE);
    private static final Pattern OCTAL_ESCAPE = Pattern.compile("\\\\([0-7]{3})|\\\\(.)");

    /** Every window BSPWM manages, across all desktops. Runs a process per window, so keep it off the FX thread */
    public static List<WindowInfo> getAll()
    {
        List<WindowInfo> windows = new ArrayList<>();
        for (String id : CommandHelper.executeWithResult("bspc", "query", "-N", "-n", ".window").split("\\R"))
        {
            id = id.trim();
            // Anything else is an error message, e.g. when there are no windows at all
            if (!id.startsWith("0x")) continue;

            String props = CommandHelper.executeWithResult("xprop", "-id", id, "_NET_WM_PID", "WM_CLASS", "_NET_WM_NAME", "WM_NAME");
            Matcher pid = PID_PROPERTY.matcher(props);
            Matcher wmClass = CLASS_PROPERTY.matcher(props);
            Matcher name = NAME_PROPERTY.matcher(props);
            windows.add(new WindowInfo(id,
                                       pid.find() ? Long.parseLong(pid.group(1)) : -1,
                                       wmClass.find() ? unescape(wmClass.group(1)) : "",
                                       name.find() ? unescape(name.group(1)) : ""));
        }
        return windows;
    }

    /** xprop prints non-ASCII bytes as octal escapes ({@code \342\200\224}), and escapes quotes and backslashes */
    private static String unescape(String value)
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Matcher m = OCTAL_ESCAPE.matcher(value);
        int last = 0;
        while (m.find())
        {
            bytes.writeBytes(value.substring(last, m.start()).getBytes(StandardCharsets.UTF_8));
            if (m.group(1) != null) bytes.write(Integer.parseInt(m.group(1), 8));
            else bytes.writeBytes(m.group(2).getBytes(StandardCharsets.UTF_8));
            last = m.end();
        }
        bytes.writeBytes(value.substring(last).getBytes(StandardCharsets.UTF_8));
        return bytes.toString(StandardCharsets.UTF_8);
    }

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
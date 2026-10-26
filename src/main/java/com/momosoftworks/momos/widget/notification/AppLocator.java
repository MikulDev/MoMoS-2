package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.wm.Desktops;
import com.momosoftworks.momos.util.wm.Windows;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Finds the windows of the apps that send notifications, and takes the user back to them. Every method here blocks. */
public class AppLocator
{
    /** The window owned by process {@code pid}, or null if it has none */
    public static Windows.WindowInfo findWindow(long pid)
    {
        if (pid <= 0) return null;
        return Windows.getAll().stream().filter(window -> window.pid() == pid).findFirst().orElse(null);
    }

    /**
     * Focuses a window of the app {@code record} came from, switching to its desktop. Windows of the same class
     * are preferred, then windows whose title mentions the app's name. If the app has no windows open, it's launched.
     */
    public static void focus(NotificationRecord record)
    {
        String appClass = record.appClass().toLowerCase();
        String appName = record.appName().toLowerCase();
        List<Windows.WindowInfo> windows = Windows.getAll();

        Optional<Windows.WindowInfo> match = find(windows, window -> !appClass.isEmpty() && window.wmClass().toLowerCase().contains(appClass))
                .or(() -> find(windows, window -> !appName.isEmpty() && window.title().toLowerCase().contains(appName)));

        if (match.isPresent()) Desktops.focusWindow(match.get().id());
        else if (!appClass.isEmpty()) launch(appClass);
    }

    private static Optional<Windows.WindowInfo> find(List<Windows.WindowInfo> windows, Predicate<Windows.WindowInfo> filter)
    {   return windows.stream().filter(filter).findFirst();
    }

    /** Starts an app by its desktop entry, falling back to running its name as a command */
    private static void launch(String app)
    {
        try
        {
            int exitCode = new ProcessBuilder("gtk-launch", app)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().waitFor();
            if (exitCode != 0)
            {
                new ProcessBuilder(app)
                        .directory(new File(System.getProperty("user.home")))
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();
            }
        }
        catch (Exception e)
        {   System.err.println("[Notifications] Couldn't launch " + app + ": " + e.getMessage());
        }
    }
}

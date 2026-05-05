package com.momosoftworks.momos.util.wm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.momosoftworks.momos.MomosApp;

import java.io.BufferedReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MonitorManager
{
    public record VirtualMonitor(
            String physicalDisplay,
            String name,
            int width,
            int height,
            int relX,
            int relY,
            int desktopCount
    ) {
        /** bspwm desktop names for this monitor: LEFT_1, LEFT_2, ... */
        public List<String> desktopNames()
        {
            return IntStream.rangeClosed(1, desktopCount)
                    .mapToObj(i -> name + "_" + i)
                    .collect(Collectors.toList());
        }
    }

    private final List<VirtualMonitor> virtualMonitors;

    public MonitorManager()
    {
        this.virtualMonitors = loadConfig();
    }

    private List<VirtualMonitor> loadConfig()
    {
        JsonElement el = MomosApp.getConfigSetting("virtual_monitors");
        if (el == null || !el.isJsonObject())
        {
            System.err.println("MonitorManager: no virtual_monitors config found");
            return List.of();
        }

        List<VirtualMonitor> monitors = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : el.getAsJsonObject().entrySet())
        {
            String physDisplay = entry.getKey();
            for (JsonElement vmEl : entry.getValue().getAsJsonArray())
            {
                JsonObject obj    = vmEl.getAsJsonObject();
                String name       = obj.get("name").getAsString();
                int width         = obj.getAsJsonArray("geometry").get(0).getAsInt();
                int height        = obj.getAsJsonArray("geometry").get(1).getAsInt();
                int relX          = obj.getAsJsonArray("position").get(0).getAsInt();
                int relY          = obj.getAsJsonArray("position").get(1).getAsInt();
                int desktopCount  = obj.get("desktops").getAsInt();
                monitors.add(new VirtualMonitor(physDisplay, name, width, height, relX, relY, desktopCount));
            }
        }
        return monitors;
    }

    /** Creates missing virtual monitors, assigns desktops, then removes any physical monitors. */
    public void setup()
    {
        if (virtualMonitors.isEmpty()) return;

        Set<String> virtualNames = getVirtualNames();
        int barHeight = getBarHeight();

        // Cache xrandr origins per physical display (one xrandr call total)
        String xrandrOutput = CommandHelper.executeWithResult("xrandr");
        Map<String, int[]> origins = new HashMap<>();
        for (VirtualMonitor vm : virtualMonitors)
            origins.computeIfAbsent(vm.physicalDisplay(), d -> parseDisplayOrigin(xrandrOutput, d));

        // Step 1: Create any missing virtual monitors
        String existing = CommandHelper.executeWithResult("bspc", "query", "-M", "--names");
        Set<String> existingNames = new HashSet<>(List.of(existing.split("\n")));

        for (VirtualMonitor vm : virtualMonitors)
        {
            if (!existingNames.contains(vm.name()))
            {
                int[] origin    = origins.get(vm.physicalDisplay());
                int absX        = origin[0] + vm.relX();
                int absY        = origin[1] + vm.relY() + barHeight;
                int usableHeight = vm.height() - barHeight;
                String geom    = vm.width() + "x" + usableHeight + "+" + absX + "+" + absY;
                CommandHelper.execute("bspc", "wm", "--add-monitor", vm.name(), geom);
                existingNames.add(vm.name());
            }
        }

        // Step 2: Assign desktops to virtual monitors while physical monitors still exist,
        // so there is always a valid desktop target before we move windows.
        for (VirtualMonitor vm : virtualMonitors)
        {
            List<String> cmd = new ArrayList<>(List.of("bspc", "monitor", vm.name(), "-d"));
            cmd.addAll(vm.desktopNames());
            CommandHelper.execute(cmd.toArray(new String[0]));
        }

        // Step 3: Explicitly move all windows off physical monitors, then remove them.
        String targetDesktop = virtualMonitors.get(0).desktopNames().get(0);
        for (String name : existingNames)
        {
            if (name.isBlank() || virtualNames.contains(name))
                continue;
            String windowList = CommandHelper.executeWithResult("bspc", "query", "-N", "-m", name);
            for (String wid : windowList.split("\n"))
                if (!wid.isBlank())
                    CommandHelper.execute("bspc", "node", wid, "-d", targetDesktop);
            CommandHelper.execute("bspc", "monitor", name, "--remove");
        }
    }

    /**
     * Subscribes to monitor_add events and removes any non-virtual monitor that appears.
     * If the bspwm connection drops (e.g. bspwm restart), re-runs setup, fires onReconnect,
     * and re-subscribes automatically.
     *
     * @param onReconnect called after setup() on each bspwm reconnect, or null
     */
    public void startWatchdog(Runnable onReconnect)
    {
        Set<String> virtualNames = getVirtualNames();

        Thread.ofPlatform().daemon(true).name("monitor-watchdog").start(() ->
        {
            while (!Thread.currentThread().isInterrupted())
            {
                BufferedReader reader = CommandHelper.executeAndRead("bspc", "subscribe", "monitor_add");
                if (reader == null)
                {
                    sleepQuietly(1000);
                    continue;
                }

                try
                {
                    String line;
                    while ((line = reader.readLine()) != null)
                    {
                        // event format: monitor_add <id> <name> <geometry>
                        String[] parts = line.split(" ");
                        if (parts.length >= 3 && !virtualNames.contains(parts[2]))
                        {
                            sleepQuietly(100);
                            CommandHelper.execute("bspc", "monitor", parts[2], "--remove");
                        }
                    }
                }
                catch (Exception e) { e.printStackTrace(); }

                // Reached EOF — bspwm likely restarted. Wait for it to come back, then re-apply setup.
                sleepQuietly(500);
                setup();
                if (onReconnect != null)
                    onReconnect.run();
            }
        });
    }

    /** Returns the X,Y origin of the named output from xrandr output, or {0,0} if not found. */
    private int[] parseDisplayOrigin(String xrandrOutput, String displayName)
    {
        Pattern p = Pattern.compile("(\\d+)x\\d+\\+(\\d+)\\+(\\d+)");
        for (String line : xrandrOutput.split("\n"))
        {
            if (line.startsWith(displayName + " connected"))
            {
                Matcher m = p.matcher(line);
                if (m.find())
                    return new int[]{ Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
            }
        }
        System.err.println("MonitorManager: could not find display '" + displayName + "' in xrandr output");
        return new int[]{ 0, 0 };
    }

    private int getBarHeight()
    {
        JsonElement el = MomosApp.getConfigSetting("bspwm");
        if (el != null && el.isJsonObject())
        {
            JsonObject bspwm = el.getAsJsonObject();
            if (bspwm.has("bar_height"))
                return bspwm.get("bar_height").getAsInt();
        }
        return 50;
    }

    private Set<String> getVirtualNames()
    {
        return virtualMonitors.stream()
                .map(VirtualMonitor::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void sleepQuietly(long ms)
    {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public List<VirtualMonitor> getVirtualMonitors()
    {
        return List.copyOf(virtualMonitors);
    }
}

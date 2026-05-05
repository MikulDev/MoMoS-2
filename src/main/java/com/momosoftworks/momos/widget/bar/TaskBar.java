package com.momosoftworks.momos.widget.bar;

import com.momosoftworks.momos.util.wm.CommandHelper;
import com.momosoftworks.momos.util.wm.Desktops;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.util.wm.Windows;
import javafx.application.Platform;
import javafx.scene.layout.HBox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskBar extends HBox
{
    private final String monitor;
    private String focusedDesktop;
    private final Map<String, TaskButton> taskButtons = new LinkedHashMap<>();
    private final Map<String, Process> titleWatcherProcesses = new LinkedHashMap<>();
    private final ExecutorService executor;
    private final ExecutorService watcherPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private volatile boolean running = true;

    public TaskBar(String monitor)
    {
        this.monitor = monitor;
        this.setSpacing(4);
        this.getStyleClass().add("taskbar-tasks");

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "bspwm-subscribe-tasks");
            t.setDaemon(true);
            return t;
        });

        this.startEventListener();
    }

    private void initializeTasks(String desktop)
    {
        this.getChildren().clear();
        taskButtons.clear();
        titleWatcherProcesses.values().forEach(Process::destroy);
        titleWatcherProcesses.clear();
        for (String nodeId : Windows.getForDesktop(desktop))
        {   addTask(nodeId);
        }
    }

    private void startEventListener()
    {
        executor.submit(() -> {
            try (BufferedReader reader = CommandHelper.executeAndRead("bspc", "subscribe", "node_add", "node_remove",
                                                                      "node_focus", "node_flag", "node_transfer", "desktop_focus"))
            {
                String line;

                while (running && (line = reader.readLine()) != null)
                {
                    String[] parts = line.split(" ");
                    String event = parts[0];

                    List<String> allDesktops = Desktops.getAll(monitor);
                    if (!allDesktops.contains(Desktops.getFocused()))
                    {   this.taskButtons.values().forEach(btn -> btn.setTaskFocused(false));
                    }
                    boolean isOnThisMonitor = Objects.equals(monitor, Monitors.fromID(parts[1]));
                    switch (event)
                    {
                        case "desktop_focus" ->
                        {
                            String desktop = Desktops.fromID(parts[2]);
                            if (isOnThisMonitor && !desktop.isEmpty())
                            {   Platform.runLater(() -> initializeTasks(desktop));
                                this.focusedDesktop = desktop;
                            }
                        }
                        case "node_transfer" ->
                        {
                            if (parts.length >= 7)
                            {   String srcDesktop = Desktops.fromID(parts[2]);
                                String dstDesktop = Desktops.fromID(parts[5]);
                                String nodeId = parts[3];
                                if (Objects.equals(focusedDesktop, srcDesktop))
                                {   Platform.runLater(() -> removeTask(nodeId));
                                }
                                if (Objects.equals(focusedDesktop, dstDesktop))
                                {   Platform.runLater(() -> addTask(nodeId));
                                }
                            }
                        }
                        case "node_add" ->
                        {
                            if (isOnThisMonitor)
                            {   String desktop = Desktops.fromID(parts[2]);
                                if (!Objects.equals(desktop, this.focusedDesktop)) continue;
                                String nodeID = parts[4];
                                Platform.runLater(() -> addTask(nodeID));
                            }
                        }
                        case "node_remove" ->
                        {
                            if (isOnThisMonitor && parts.length >= 4)
                            {   final String id = parts[3];
                                Platform.runLater(() -> removeTask(id));
                            }
                        }
                        case "node_focus" ->
                        {
                            if (isOnThisMonitor && parts.length >= 4)
                            {   final String id = parts[3];
                                Platform.runLater(() -> updateFocus(id));
                            }
                        }
                        case "node_flag" ->
                        {
                            if (isOnThisMonitor && parts.length >= 6)
                            {   final String id   = parts[3];
                                final String flag = parts[4];
                                final boolean on  = parts[5].equals("on");
                                Platform.runLater(() -> updateFlag(id, flag, on));
                            }
                        }
                    }
                }
            }
            catch (Exception e) { if (running) e.printStackTrace(); }
        });
    }

    private void addTask(String nodeId)
    {
        if (taskButtons.containsKey(nodeId)) return;
        TaskButton button = TaskButton.create(nodeId);
        if (button == null) return;
        taskButtons.put(nodeId, button);
        this.getChildren().add(button);
        startTitleWatcher(nodeId, button);
    }

    private void removeTask(String nodeId)
    {
        TaskButton btn = taskButtons.remove(nodeId);
        if (btn != null)
        {   this.getChildren().remove(btn);
        }
        Process proc = titleWatcherProcesses.remove(nodeId);
        if (proc != null)
        {   proc.destroy();
        }
    }

    private void startTitleWatcher(String nodeId, TaskButton button)
    {
        try
        {
            Process proc = new ProcessBuilder("xprop", "-spy", "-id", nodeId, "WM_NAME").start();
            titleWatcherProcesses.put(nodeId, proc);
            watcherPool.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream())))
                {
                    String line;
                    while (running && (line = reader.readLine()) != null)
                    {
                        if (!line.startsWith("WM_NAME")) continue;
                        int eq = line.indexOf('=');
                        if (eq < 0) continue;
                        String title = line.substring(eq + 1).trim().replaceAll("^\"|\"$", "");
                        if (!title.isEmpty())
                        {
                            final String t = title;
                            Platform.runLater(() -> button.setTitle(t));
                        }
                    }
                }
                catch (Exception ignored) {}
            });
        }
        catch (Exception e) { e.printStackTrace(); }
    }

    private void updateFocus(String focusedId)
    {   taskButtons.forEach((id, btn) -> btn.setTaskFocused(id.equals(focusedId)));
    }

    private void updateFlag(String nodeId, String flag, boolean on)
    {
        TaskButton btn = taskButtons.get(nodeId);
        if (btn == null) return;
        if (flag.equals("hidden") || flag.equals("sticky"))
        {   btn.setMinimized(on);
        }
        if (flag.equals("urgent"))
        {   btn.setUrgent(on);
        }
    }

    public void shutdown()
    {
        running = false;
        executor.shutdownNow();
        watcherPool.shutdownNow();
        titleWatcherProcesses.values().forEach(Process::destroy);
        titleWatcherProcesses.clear();
    }
}
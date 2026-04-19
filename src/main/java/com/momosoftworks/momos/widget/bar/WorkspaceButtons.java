package com.momosoftworks.momos.widget.bar;

import com.momosoftworks.momos.util.wm.CommandHelper;
import com.momosoftworks.momos.util.wm.Desktops;
import com.momosoftworks.momos.util.wm.Monitors;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.io.BufferedReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkspaceButtons extends HBox
{
    private final String monitor;
    private volatile String focusedDesktop;
    private final Map<String, TagComponents> tagComponents = new HashMap<>();
    private final ExecutorService executor;
    private volatile boolean running = true;

    public WorkspaceButtons(String monitor)
    {
        this.monitor = monitor;
        this.setSpacing(4);
        this.getStyleClass().add("workspace-indicator");

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "bspwm-subscribe-" + monitor);
            t.setDaemon(true);
            return t;
        });

        this.focusedDesktop = Desktops.getFocused(monitor);
        initializeTags();
        startEventListener();
    }

    private void initializeTags()
    {
        for (int i = 1; i <= 5; i++)
        {
            String desktopName = monitor.charAt(0) + String.valueOf(i);

            StackPane button = new StackPane();
            button.getStyleClass().add("tag-button");

            // Force square: bind width to height so CSS height drives both axes
            button.setMinSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
            button.setMaxSize(StackPane.USE_PREF_SIZE, StackPane.USE_PREF_SIZE);
            button.prefWidthProperty().bind(button.prefHeightProperty());

            // Circle — always in the tree, hidden when desktop is empty
            Circle indicator = new Circle(3.5);
            indicator.getStyleClass().add("tag-indicator");
            indicator.setVisible(false);
            StackPane.setAlignment(indicator, Pos.TOP_LEFT);
            StackPane.setMargin(indicator, new Insets(5, 0, 0, 5));

            // Number — centered
            Label label = new Label(String.valueOf(i));
            label.getStyleClass().add("tag-button-text");
            StackPane.setAlignment(label, Pos.CENTER);

            button.getChildren().addAll(indicator, label);

            final String desktop = desktopName;
            button.setOnMouseClicked(e -> Desktops.switchTo(desktop));

            tagComponents.put(desktopName, new TagComponents(button, indicator, label));
            this.getChildren().add(button);
        }
        this.updateState();
    }

    private void startEventListener()
    {
        executor.submit(() -> {
            try
            {
                BufferedReader reader = CommandHelper.executeAndRead("bspc", "subscribe", "desktop_focus", "node_transfer", "node_add", "node_remove");
                String line;

                while (running && (line = reader.readLine()) != null)
                {
                    String[] parts = line.split(" ");

                    if (parts[0].equals("desktop_focus"))
                    {
                        if (!Objects.equals(monitor, Monitors.fromID(parts[1]))) continue;
                        String desktop = Desktops.fromID(parts[2]);
                        if (desktop != null && !desktop.isEmpty())
                            focusedDesktop = desktop;
                    }

                    Platform.runLater(this::updateState);
                }
            }
            catch (Exception e)
            {   if (running) e.printStackTrace();
            }
        });
    }

    private void updateState()
    {
        try
        {
            String currentFocus = focusedDesktop;
            List<String> occupiedDesktops = Desktops.getAll(null, Desktops.Flag.OCCUPIED);
            List<String> urgentDesktops = Desktops.getAll(null, Desktops.Flag.URGENT);

            for (Map.Entry<String, TagComponents> entry : tagComponents.entrySet())
            {
                String desktop = entry.getKey();
                TagComponents components = entry.getValue();
                Pane button = components.button;
                Label label = components.label;
                Circle indicator = components.indicator;

                boolean focused  = desktop.equals(currentFocus);
                boolean occupied = occupiedDesktops.contains(desktop);
                boolean urgent   = urgentDesktops.contains(desktop);

                button.getStyleClass().removeAll("focused", "occupied", "urgent");
                label.getStyleClass().removeAll("focused");
                if (focused)
                {   button.getStyleClass().addFirst("focused");
                    label.getStyleClass().addFirst("focused");
                }
                if (urgent)
                {   button.getStyleClass().addFirst("urgent");
                }

                // Circle visibility and fill vs. outline driven by style classes
                indicator.setVisible(occupied);
                indicator.getStyleClass().remove("tag-indicator-selected");
                if (occupied && focused)
                {
                    indicator.getStyleClass().add("tag-indicator-selected");
                }
            }
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
    }

    public void shutdown()
    {
        running = false;
        executor.shutdownNow();
    }

    private record TagComponents(Pane button, Circle indicator, Label label) {}
}

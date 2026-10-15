package com.momosoftworks.momos.widget.bar;

import com.momosoftworks.momos.MomosApp;
import com.momosoftworks.momos.util.wm.BspcSubscriber;
import com.momosoftworks.momos.util.wm.Desktops;
import com.momosoftworks.momos.util.wm.MonitorManager;
import com.momosoftworks.momos.util.wm.Monitors;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

import java.util.*;

public class WorkspaceButtons extends HBox
{
    // Named "wm-focused" rather than "focused" to avoid colliding with JavaFX's own
    // built-in :focused pseudo-class, which tracks real keyboard focus.
    private static final PseudoClass WM_FOCUSED = PseudoClass.getPseudoClass("wm-focused");
    private static final PseudoClass OCCUPIED   = PseudoClass.getPseudoClass("occupied");
    private static final PseudoClass URGENT     = PseudoClass.getPseudoClass("urgent");
    private static final PseudoClass SELECTED   = PseudoClass.getPseudoClass("selected");

    private final String monitor;
    private volatile String focusedDesktop;
    private final Map<String, TagComponents> tagComponents = new HashMap<>();
    private final BspcSubscriber subscription;

    public WorkspaceButtons(String monitor)
    {
        this.monitor = monitor;
        this.setSpacing(4);
        this.getStyleClass().add("workspace-indicator");

        this.focusedDesktop = Desktops.getFocused(monitor);
        initializeTags();
        this.subscription = new BspcSubscriber("bspwm-subscribe-" + monitor, this::onEvent,
                                               "desktop_focus", "node_transfer", "node_add", "node_remove");
    }

    private void initializeTags()
    {
        List<String> desktopNames = MomosApp.MONITOR_MANAGER.getVirtualMonitors().stream()
                .filter(vm -> vm.name().equals(monitor))
                .findFirst()
                .map(MonitorManager.VirtualMonitor::desktopNames)
                .orElse(List.of());

        for (int i = 0; i < desktopNames.size(); i++)
        {
            String desktopName = desktopNames.get(i);

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

            // Display the 1-based index; the full bspwm name is stored in tagComponents
            Label label = new Label(String.valueOf(i + 1));
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

    private void onEvent(String[] parts)
    {
        if (parts[0].equals("desktop_focus"))
        {
            if (!Objects.equals(monitor, Monitors.fromID(parts[1]))) return;
            String desktop = Desktops.fromID(parts[2]);
            if (desktop != null && !desktop.isEmpty())
                focusedDesktop = desktop;
        }
        Platform.runLater(this::updateState);
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

                button.pseudoClassStateChanged(WM_FOCUSED, focused);
                button.pseudoClassStateChanged(OCCUPIED, occupied);
                button.pseudoClassStateChanged(URGENT, urgent);
                label.pseudoClassStateChanged(WM_FOCUSED, focused);

                // Circle visibility and fill vs. outline driven by pseudo-class state
                indicator.setVisible(occupied);
                indicator.pseudoClassStateChanged(SELECTED, occupied && focused);
            }
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
    }

    public void shutdown()
    {   subscription.close();
    }

    private record TagComponents(Pane button, Circle indicator, Label label) {}
}

package com.momosoftworks.momos.widget.bar.systray;

import com.momosoftworks.momos.MomosApp;
import com.momosoftworks.momos.widget.IconButton;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SystemTray extends HBox
{
    private static final int ICON_SIZE = 18;

    private final Map<String, IconButton> iconViews = new LinkedHashMap<>();
    private final BiConsumer<String, Image> addListener = this::addOrUpdateIcon;
    private final Consumer<String> removeListener = this::removeIcon;

    public SystemTray()
    {
        this.setSpacing(4);
        this.setAlignment(Pos.CENTER);
        this.getStyleClass().add("systray");
        this.setMaxHeight(USE_PREF_SIZE);

        StatusNotifierWatcherImpl.addOnItemAdded(addListener);
        StatusNotifierWatcherImpl.addOnItemRemoved(removeListener);
    }

    private void addOrUpdateIcon(String service, Image icon)
    {
        IconButton existing = iconViews.get(service);
        if (existing != null)
        {   existing.setGraphic(new ImageView(icon));
            return;
        }
        IconButton button = new IconButton(icon, ICON_SIZE);
        button.getStyleClass().add("tray-icon");
        button.setOnMouseClicked(e ->
        {
            StatusNotifierWatcherImpl watcher = MomosApp.SYSTRAY_WATCHER;
            if (watcher == null) return;
            int sx = (int) e.getScreenX();
            int sy = (int) e.getScreenY();
            if (e.getButton() == MouseButton.PRIMARY)
            {   watcher.activate(service, sx, sy);
            }
            else if (e.getButton() == MouseButton.SECONDARY)
            {   watcher.contextMenu(service, sx, sy);
            }
        });
        iconViews.put(service, button);
        this.getChildren().add(button);
    }

    private void removeIcon(String service)
    {
        IconButton button = iconViews.remove(service);
        if (button != null)
        {   this.getChildren().remove(button);
        }
    }

    public void shutdown()
    {
        StatusNotifierWatcherImpl.removeOnItemAdded(addListener);
        StatusNotifierWatcherImpl.removeOnItemRemoved(removeListener);
    }
}

package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.image.ImageHelper;
import com.momosoftworks.momos.util.wm.CommandHelper;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.widget.Widgets;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationHandler implements INotifications
{
    public static final String OBJECT_PATH = "/org/freedesktop/Notifications";
    public static final String BUS_NAME    = "org.freedesktop.Notifications";
    private static final int   MAX_HISTORY    = 100;
    private static final int   POPUP_WIDTH    = 360;
    private static final int   POPUP_X_MARGIN = 20;
    private static final int   BAR_HEIGHT     = 60;  // bar (50px) + gap (10px)
    private static final int   POPUP_GAP      = 8;
    private static final int   DEFAULT_TIMEOUT = 5000;
    private static final Image CLOSE_ICON = new Image("icons/close.png", 12, 12, true, true);

    private DBusConnection connection;
    private final AtomicInteger idCounter  = new AtomicInteger(1);
    private final LinkedList<Notification> history      = new LinkedList<>();
    private final List<Stage>              activePopups = new ArrayList<>();

    public void setConnection(DBusConnection connection)
    {   this.connection = connection;
    }

    @Override
    public String getObjectPath()
    {   return OBJECT_PATH;
    }

    // -------------------------------------------------------------------------
    // Interface implementation
    // -------------------------------------------------------------------------

    @Override
    public UInt32 Notify(String app_name, UInt32 replaces_id, String app_icon, String summary, String body,
                         List<String> actions, Map<String, Variant<?>> hints, int expire_timeout)
    {
        int id = (replaces_id != null && replaces_id.intValue() != 0)
                ? replaces_id.intValue()
                : idCounter.getAndIncrement();

        String desktopEntry = null;
        if (hints != null && hints.containsKey("desktop-entry"))
        {
            Object val = hints.get("desktop-entry").getValue();
            if (val instanceof String s) desktopEntry = s;
        }

        Notification record = new Notification(
                id, app_name, desktopEntry, summary, body, app_icon, System.currentTimeMillis()
        );

        synchronized (history)
        {
            history.removeIf(r -> r.id() == id);
            history.addFirst(record);
            if (history.size() > MAX_HISTORY) history.removeLast();
        }

        Platform.runLater(() -> showPopup(record, expire_timeout));

        return new UInt32(id);
    }

    @Override
    public void CloseNotification(UInt32 id)
    {
        // TODO: dismiss popup for this id
        emitClosed(id.intValue(), Notification.CloseReason.METHOD_CALL);
    }

    @Override
    public List<String> GetCapabilities()
    {   return List.of("body", "icon-static", "persistence", "actions");
    }

    @Override
    public ServerInfo GetServerInformation()
    {   return new ServerInfo("MoMoS", "momosoftworks", "1.0", "1.2");
    }

    public void showPopup(Notification record, int expireTimeout)
    {
        Stage popup = new Stage();
        popup.initStyle(StageStyle.TRANSPARENT);
        popup.setAlwaysOnTop(true);

        // Icon
        HBox header = new HBox(8);
        header.setAlignment(Pos.TOP_LEFT);

        Image icon = resolveIcon(record.iconPath(), record.appName(), 32);
        if (icon != null)
        {
            ImageView iconView = new ImageView(icon);
            iconView.setFitWidth(32);
            iconView.setFitHeight(32);
            iconView.setPreserveRatio(true);
            header.getChildren().add(iconView);
        }

        // Title + app name
        Label titleLabel = new Label(record.summary());
        titleLabel.getStyleClass().add("title");
        titleLabel.setMaxWidth(260);

        Label appLabel = new Label(record.appName());
        appLabel.getStyleClass().add("timestamp");

        VBox titleBox = new VBox(2, titleLabel, appLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        StackPane closeBtn = Widgets.imageButton(CLOSE_ICON, 12);

        header.getChildren().addAll(titleBox, closeBtn);

        // Root card
        VBox content = new VBox(6, header);
        content.getStyleClass().add("notification-entry");
        content.setPrefWidth(POPUP_WIDTH);
        content.setMaxWidth(POPUP_WIDTH);
        content.setCursor(Cursor.HAND);

        if (record.body() != null && !record.body().isBlank())
        {
            Label bodyLabel = new Label(record.body());
            bodyLabel.getStyleClass().add("body");
            bodyLabel.setWrapText(true);
            bodyLabel.setMaxWidth(POPUP_WIDTH - 24);
            content.getChildren().add(bodyLabel);
        }

        Scene scene = new Scene(content);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/styles/base.css").toExternalForm());
        popup.setScene(scene);

        Rectangle2D screen = Monitors.getBounds("focused");
        popup.setX(screen.getMinX() + screen.getWidth() - POPUP_WIDTH - POPUP_X_MARGIN);

        // Stack below any already-visible popups whose heights are known
        double y = screen.getMinY() + BAR_HEIGHT;
        synchronized (activePopups)
        {
            for (Stage s : activePopups)
            {   y += s.getHeight() + POPUP_GAP;
            }
            activePopups.add(popup);
        }
        popup.setY(y);

        popup.show();

        // Dismiss timer (-1 = persistent, 0 = use default)
        int timeout = expireTimeout < 0 ? -1 : (expireTimeout == 0 ? DEFAULT_TIMEOUT : expireTimeout);
        PauseTransition timer = timeout > 0 ? new PauseTransition(Duration.millis(timeout)) : null;

        Runnable dismiss = () -> dismissPopup(popup, record, Notification.CloseReason.EXPIRED);
        if (timer != null)
        {
            timer.setOnFinished(e -> dismiss.run());
            timer.play();
        }

        closeBtn.setOnMouseClicked(e ->
        {
            if (timer != null) timer.stop();
            dismissPopup(popup, record, Notification.CloseReason.DISMISSED);
            e.consume();
        });

        content.setOnMouseClicked(e ->
        {
            if (e.getButton() == MouseButton.PRIMARY)
            {
                if (timer != null) timer.stop();
                dismissPopup(popup, record, Notification.CloseReason.DISMISSED);
                focusApp(record);
            }
        });
    }

    private void dismissPopup(Stage popup, Notification record, Notification.CloseReason reason)
    {
        popup.hide();
        synchronized (activePopups)
        {   activePopups.remove(popup);
        }
        relayoutPopups(Monitors.getBounds("focused"));
        emitClosed(record.id(), reason);
    }

    private void relayoutPopups(Rectangle2D screen)
    {
        synchronized (activePopups)
        {
            double y = screen.getMinY() + BAR_HEIGHT;
            for (Stage s : activePopups)
            {
                s.setY(y);
                y += s.getHeight() + POPUP_GAP;
            }
        }
    }

    public List<Notification> getHistory()
    {
        synchronized (history)
        {   return List.copyOf(history);
        }
    }

    /**
     * Focuses the window associated with a notification.
     * Uses wmctrl by WM_CLASS (desktop-entry name), falling back to xdotool.
     */
    public void focusApp(Notification record)
    {
        String target = record.desktopEntry() != null ? record.desktopEntry() : record.appName();
        String result = CommandHelper.executeWithResult("wmctrl", "-x", "-a", target);
        if (result.contains("error") || result.isEmpty())
        {
            CommandHelper.execute("bash", "-c",
                    "xdotool search --classname '" + target + "' | head -1 | xargs -I{} xdotool windowfocus --sync {}");
        }
    }

    private static Image resolveIcon(String iconPath, String appName, int size)
    {
        // Strip file:// URI prefix that some apps send
        String path = iconPath != null && iconPath.startsWith("file://") ? iconPath.substring(7) : iconPath;

        String resolved = ImageHelper.getIconPath(path);
        if (resolved == null && appName != null && !appName.isBlank())
        {   resolved = ImageHelper.getIconPath(appName.toLowerCase());
        }
        return resolved != null ? ImageHelper.getIcon(resolved, size) : null;
    }

    private void emitClosed(int id, Notification.CloseReason reason)
    {
        if (connection == null) return;
        try
        {   connection.sendMessage(new NotificationClosed(OBJECT_PATH, new UInt32(id), reason));
        }
        catch (DBusException e) { e.printStackTrace(); }
    }

    public static class NotificationClosed extends DBusSignal
    {
        public final UInt32 id;
        public final Notification.CloseReason reason;

        public NotificationClosed(String path, UInt32 id, Notification.CloseReason reason) throws DBusException
        {
            super(path, id, reason);
            this.id = id;
            this.reason = reason;
        }
    }
}

package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.wm.Windows;
import com.momosoftworks.momos.widget.registry.WidgetRegistry;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import org.freedesktop.dbus.DBusCallInfo;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.messages.DBusSignal;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The notification server: receives notifications over D-Bus, shows them on screen, and adds them to the
 * history kept by the {@link NotificationsWidget}.
 * <p>
 * Each notification is tied to the window of the app that sent it, found through the sender's process ID.
 * That's what lets clicking it, either on screen or in the history, take the user back to the app.
 */
public class NotificationHandler implements INotifications
{
    public static final String OBJECT_PATH = "/org/freedesktop/Notifications";
    public static final String BUS_NAME    = "org.freedesktop.Notifications";
    private static final int ICON_SIZE = 48;
    private static final byte URGENCY_CRITICAL = 2;

    private DBusConnection connection;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final NotificationConfig config = NotificationConfig.load();
    // Looking up the sender's window takes a moment, so it's done here rather than holding up the sender.
    // One at a time, so notifications appear in the order they were sent
    private final ExecutorService incoming = Executors.newSingleThreadExecutor(r ->
    {   Thread thread = new Thread(r, "notifications");
        thread.setDaemon(true);
        return thread;
    });
    // Only touched on the FX thread
    private NotificationToasts toasts;

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
    @SuppressWarnings("rawtypes")
    public UInt32 Notify(String app_name, UInt32 replaces_id, String app_icon, String summary, String body,
                         List<String> actions, Map<String, Variant> hints, int expire_timeout)
    {
        int id = (replaces_id != null && replaces_id.intValue() != 0)
                ? replaces_id.intValue()
                : idCounter.getAndIncrement();
        // Only available on the thread handling the call. The sender must be looked up now too, since
        // short-lived ones like notify-send leave the bus as soon as this returns
        DBusCallInfo call = DBusConnection.getCallInfo();
        long senderPid = getProcessID(call != null ? call.getSource() : null);
        incoming.submit(() -> receive(id, senderPid, app_name, app_icon, summary, body, actions, hints, expire_timeout));
        return new UInt32(id);
    }

    @Override
    public void CloseNotification(UInt32 id)
    {   Platform.runLater(() -> toasts().close(id.intValue(), Notification.CloseReason.METHOD_CALL));
    }

    @Override
    public List<String> GetCapabilities()
    {   return List.of("body", "icon-static", "persistence", "actions");
    }

    @Override
    public ServerInfo<String, String, String, String> GetServerInformation()
    {   return new ServerInfo<>("MoMoS", "momosoftworks", "1.0", "1.2");
    }

    // -------------------------------------------------------------------------
    // Receiving
    // -------------------------------------------------------------------------

    @SuppressWarnings("rawtypes")
    private void receive(int id, long senderPid, String appName, String appIcon, String summary, String body,
                         List<String> actions, Map<String, Variant> hints, int expireTimeout)
    {
        Image image = null;
        String imagePath = null;
        String desktopEntry = null;
        boolean critical = false;
        if (hints != null)
        {
            try
            {
                // Older senders use the names from earlier versions of the spec
                for (String key : new String[]{ "image-data", "image_data", "icon_data" })
                {   if (image == null && hints.containsKey(key)) image = decodeImageData(hints.get(key).getValue());
                }
                for (String key : new String[]{ "image-path", "image_path" })
                {   if (imagePath == null && hints.get(key) != null && hints.get(key).getValue() instanceof String s) imagePath = s;
                }
                if (hints.get("desktop-entry") != null && hints.get("desktop-entry").getValue() instanceof String s)
                {   desktopEntry = s;
                }
                if (hints.get("urgency") != null && hints.get("urgency").getValue() instanceof Number urgency)
                {   critical = urgency.byteValue() == URGENCY_CRITICAL;
                }
            }
            catch (Exception e)
            {   System.err.println("[Notify] Failed to read hints from " + appName + ": " + e.getMessage());
            }
        }

        // The spec ranks a full image over an image path over the app's icon. The desktop entry is often named
        // after the app's icon, so it stands in for apps that don't send one
        String icon = imagePath != null ? imagePath
                    : appIcon != null && !appIcon.isBlank() ? appIcon
                    : desktopEntry;
        Windows.WindowInfo window = AppLocator.findWindow(senderPid);
        NotificationRecord record = new NotificationRecord(summary, body, icon, System.currentTimeMillis(),
                                                           window != null ? window.wmClass() : "", appName);

        boolean store = config.shouldStore(record);
        if (store && image != null)
        {   // Raw pixels can't go in the history, so they're kept as a file instead
            String savedIcon = NotificationHistory.saveIcon(image);
            if (savedIcon != null) record = record.withIcon(savedIcon);
        }

        Notification notification = new Notification(id, record,
                                                      image != null ? image : record.loadIcon(ICON_SIZE),
                                                      parseActions(actions),
                                                      getTimeout(expireTimeout, critical));
        Platform.runLater(() ->
        {
            if (store) WidgetRegistry.NOTIFICATIONS.get().history().add(id, notification.record());
            toasts().show(notification);
        });
    }

    /** -1 means the server picks, 0 means never. Critical notifications stay until dismissed unless they say otherwise */
    private Duration getTimeout(int expireTimeout, boolean critical)
    {
        if (expireTimeout > 0) return Duration.millis(expireTimeout);
        if (expireTimeout == 0 || critical) return Duration.INDEFINITE;
        return config.timeout();
    }

    /** Actions arrive as a flat list of alternating keys and labels */
    private static List<Notification.Action> parseActions(List<String> actions)
    {
        List<Notification.Action> parsed = new ArrayList<>();
        if (actions == null) return parsed;
        for (int i = 0; i + 1 < actions.size(); i += 2)
        {   parsed.add(new Notification.Action(actions.get(i), actions.get(i + 1)));
        }
        return parsed;
    }

    /** The process ID of the client with this bus name, or -1 if it can't be found */
    private long getProcessID(String busName)
    {
        if (busName == null || connection == null) return -1;
        try
        {   DBus bus = connection.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
            return bus.GetConnectionUnixProcessID(busName).longValue();
        }
        catch (Exception e)
        {   System.err.println("[Notify] Couldn't find the process of " + busName + ": " + e.getMessage());
            return -1;
        }
    }

    // Decodes the FreeDesktop image-data hint: (iiibiiay) = width, height, rowstride, hasAlpha, bpp, channels, data
    private static Image decodeImageData(Object value)
    {
        Object[] parts = value instanceof Object[] a ? a : null;
        if (parts == null || parts.length < 7) return null;
        try
        {
            int width     = ((Number) parts[0]).intValue();
            int height    = ((Number) parts[1]).intValue();
            int rowstride = ((Number) parts[2]).intValue();
            boolean alpha = (Boolean) parts[3];
            int channels  = ((Number) parts[5]).intValue();

            byte[] data = switch (parts[6])
            {
                case byte[] b -> b;
                case List<?> l ->
                {
                    byte[] buf = new byte[l.size()];
                    for (int i = 0; i < l.size(); i++) buf[i] = ((Number) l.get(i)).byteValue();
                    yield buf;
                }
                default -> null;
            };
            if (data == null || width <= 0 || height <= 0) return null;

            WritableImage img = new WritableImage(width, height);
            PixelWriter pw = img.getPixelWriter();
            for (int y = 0; y < height; y++)
            {
                for (int x = 0; x < width; x++)
                {
                    int off = y * rowstride + x * channels;
                    int r = Byte.toUnsignedInt(data[off]);
                    int g = Byte.toUnsignedInt(data[off + 1]);
                    int b = Byte.toUnsignedInt(data[off + 2]);
                    int a = alpha ? Byte.toUnsignedInt(data[off + 3]) : 255;
                    pw.setArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                }
            }
            return img;
        }
        catch (Exception e)
        {
            System.err.println("[Notify] image-data decode failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // On screen
    // -------------------------------------------------------------------------

    private NotificationToasts toasts()
    {
        if (toasts == null)
        {   toasts = new NotificationToasts(this::onClosed, this::onInvoked);
        }
        return toasts;
    }

    private void onInvoked(Notification notification, String actionKey)
    {
        if (notification.hasAction(actionKey))
        {   this.sendSignal(() -> new ActionInvoked(OBJECT_PATH, new UInt32(notification.id()), actionKey));
        }
        // Clicking the notification itself also brings its app up, whether or not the app reacts to the click
        if (actionKey.equals(Notification.DEFAULT_ACTION))
        {   Thread.ofVirtual().start(() -> AppLocator.focus(notification.record()));
        }
    }

    private void onClosed(Notification notification, Notification.CloseReason reason)
    {   this.sendSignal(() -> new NotificationClosed(OBJECT_PATH, new UInt32(notification.id()), reason.id()));
    }

    private interface SignalFactory
    {   DBusSignal create() throws DBusException;
    }

    private void sendSignal(SignalFactory signal)
    {
        if (connection == null) return;
        try
        {   connection.sendMessage(signal.create());
        }
        catch (DBusException e) { e.printStackTrace(); }
    }
}

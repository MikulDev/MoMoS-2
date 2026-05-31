package com.momosoftworks.momos.widget.bar.systray;

import com.momosoftworks.momos.util.image.ImageHelper;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBus;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class StatusNotifierWatcherImpl implements IStatusNotifierWatcher, Properties
{
    public static final String BUS_NAME    = "org.kde.StatusNotifierWatcher";
    public static final String OBJECT_PATH = "/StatusNotifierWatcher";
    private static final int   ICON_SIZE   = 22;
    private static final String SNI_IFACE  = "org.kde.StatusNotifierItem";

    // Static so SystemTray instances can subscribe before the watcher is initialized
    private static final List<BiConsumer<String, Image>> ADDED_LISTENERS   = new CopyOnWriteArrayList<>();
    private static final List<Consumer<String>>          REMOVED_LISTENERS = new CopyOnWriteArrayList<>();

    public static void addOnItemAdded(BiConsumer<String, Image> l)    { ADDED_LISTENERS.add(l); }
    public static void addOnItemRemoved(Consumer<String> l)           { REMOVED_LISTENERS.add(l); }
    public static void removeOnItemAdded(BiConsumer<String, Image> l) { ADDED_LISTENERS.remove(l); }
    public static void removeOnItemRemoved(Consumer<String> l)        { REMOVED_LISTENERS.remove(l); }

    private DBusConnection connection;
    // serviceName -> objectPath
    private final Map<String, String> registeredItems = Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public String getObjectPath() { return OBJECT_PATH; }

    // -------------------------------------------------------------------------
    // org.freedesktop.DBus.Properties implementation
    // Apps (e.g. libayatana-appindicator) query IsStatusNotifierHostRegistered
    // before registering their tray icon — we must respond or they silently skip.
    // -------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <A> A Get(String ifaceName, String propName)
    {
        if (!BUS_NAME.equals(ifaceName)) return null;
        return (A) switch (propName)
        {
            case "RegisteredStatusNotifierItems" ->
                new ArrayList<>(registeredItems.keySet());
            case "IsStatusNotifierHostRegistered" -> Boolean.TRUE;
            case "ProtocolVersion"               -> Integer.valueOf(0);
            default -> null;
        };
    }

    @Override
    public <A> void Set(String ifaceName, String propName, A value) {}

    @Override
    public Map<String, Variant<?>> GetAll(String ifaceName)
    {
        if (!BUS_NAME.equals(ifaceName)) return Map.of();
        Map<String, Variant<?>> map = new LinkedHashMap<>();
        synchronized (registeredItems)
        {
            map.put("RegisteredStatusNotifierItems",
                    new Variant<>(new ArrayList<>(registeredItems.keySet()), "as"));
        }
        map.put("IsStatusNotifierHostRegistered", new Variant<>(true));
        map.put("ProtocolVersion", new Variant<>(0));
        return map;
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    public void setConnection(DBusConnection connection)
    {
        this.connection = connection;
        try
        {
            connection.addSigHandler(IStatusNotifierItem.NewIcon.class, sig ->
            {
                String source = sig.getSource();
                String path   = registeredItems.get(source);
                if (path != null)
                {
                    Thread.ofVirtual().start(() -> loadAndNotifyAdded(source, path));
                }
            });
        }
        catch (DBusException e) { e.printStackTrace(); }

        try
        {
            connection.addSigHandler(DBus.NameOwnerChanged.class, sig ->
            {
                try
                {
                    Object[] params = sig.getParameters();
                    if (params.length >= 3)
                    {
                        String name     = (String) params[0];
                        String newOwner = (String) params[2];
                        if (newOwner.isEmpty() && registeredItems.containsKey(name))
                        {
                            onServiceLost(name);
                        }
                    }
                }
                catch (DBusException ignored) {}
            });
        }
        catch (DBusException e) { e.printStackTrace(); }
    }

    @Override
    public void RegisterStatusNotifierHost(String service)
    {
    }

    @Override
    public void RegisterStatusNotifierItem(String serviceOrPath)
    {
        if (serviceOrPath == null || serviceOrPath.isBlank()) return;

        if (serviceOrPath.startsWith("/"))
        {
            System.err.println("[Systray] Path-only registration not supported (no sender info): " + serviceOrPath);
            return;
        }

        String serviceName, objectPath;
        int slashIdx = serviceOrPath.indexOf('/', 1);
        if (slashIdx >= 0)
        {
            serviceName = serviceOrPath.substring(0, slashIdx);
            objectPath  = serviceOrPath.substring(slashIdx);
        }
        else
        {
            serviceName = serviceOrPath;
            objectPath  = "/StatusNotifierItem";
        }

        registeredItems.put(serviceName, objectPath);

        try { connection.sendMessage(new StatusNotifierItemRegistered(OBJECT_PATH, serviceName)); }
        catch (DBusException e) { e.printStackTrace(); }

        final String svc  = serviceName;
        final String path = objectPath;
        Thread.ofVirtual().start(() -> loadAndNotifyAdded(svc, path));
    }

    public void activate(String serviceName, int x, int y)
    {
        invokeItemMethod(serviceName, item -> item.Activate(x, y));
    }

    public void contextMenu(String serviceName, int x, int y)
    {
        invokeItemMethod(serviceName, item -> item.ContextMenu(x, y));
    }

    private void invokeItemMethod(String serviceName, java.util.function.Consumer<IStatusNotifierItem> call)
    {
        String objectPath = registeredItems.get(serviceName);
        if (objectPath == null) return;
        Thread.ofVirtual().start(() ->
        {
            try
            {
                IStatusNotifierItem item = connection.getRemoteObject(serviceName, objectPath, IStatusNotifierItem.class);
                call.accept(item);
            }
            catch (Exception e)
            {   System.err.println("[Systray] Method call failed for " + serviceName + ": " + e.getMessage());
            }
        });
    }

    private void onServiceLost(String name)
    {
        registeredItems.remove(name);
        try { connection.sendMessage(new StatusNotifierItemUnregistered(OBJECT_PATH, name)); }
        catch (DBusException e) { e.printStackTrace(); }
        REMOVED_LISTENERS.forEach(l -> Platform.runLater(() -> l.accept(name)));
    }

    // -------------------------------------------------------------------------
    // Icon loading
    // -------------------------------------------------------------------------

    private void loadAndNotifyAdded(String serviceName, String objectPath)
    {
        try
        {
            Properties props = connection.getRemoteObject(serviceName, objectPath, Properties.class);
            Image icon = loadIcon(serviceName, props);
            if (icon != null)
            {
                final String svc = serviceName;
                ADDED_LISTENERS.forEach(l -> Platform.runLater(() -> l.accept(svc, icon)));
            }
            else
            {   System.err.println("[Systray] No icon found for " + serviceName);
            }
        }
        catch (Exception e)
        {   System.err.println("[Systray] Exception loading icon for " + serviceName + ": " + e);
        }
    }

    private static Image loadIcon(String serviceName, Properties props)
    {
        // Try IconName first — resolved via XDG theme search
        try
        {
            String iconName = props.Get(SNI_IFACE, "IconName");
            if (iconName != null && !iconName.isBlank())
            {
                String path = ImageHelper.getIconPath(iconName);
                if (path != null) return ImageHelper.getIcon(path, ICON_SIZE);
            }
        }
        catch (Exception ignored) {}

        // Fall back to raw ARGB pixmap data
        try
        {
            Object raw = props.Get(SNI_IFACE, "IconPixmap");
            if (raw instanceof List<?> pixmaps && !pixmaps.isEmpty())
            {   return decodePixmap(pixmaps);
            }
        }
        catch (Exception ignored) {}

        return null;
    }

    private static Image decodePixmap(List<?> pixmaps)
    {
        int[]  bestDims  = null;
        byte[] bestData  = null;
        int    bestDelta = Integer.MAX_VALUE;

        for (Object entry : pixmaps)
        {
            try
            {
                if (!(entry instanceof Object[] arr) || arr.length < 3) continue;
                int    w    = ((Number) arr[0]).intValue();
                int    h    = ((Number) arr[1]).intValue();
                byte[] data = toByteArray(arr[2]);
                if (data == null || w <= 0 || h <= 0) continue;
                int delta = Math.abs(w - ICON_SIZE);
                if (delta < bestDelta)
                {   bestDelta = delta; bestDims = new int[]{w, h}; bestData = data;
                }
            }
            catch (Exception ignored) {}
        }

        if (bestDims == null) return null;
        return renderARGB(bestDims[0], bestDims[1], bestData);
    }

    private static byte[] toByteArray(Object obj)
    {
        return switch (obj)
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
    }

    // SNI IconPixmap uses ARGB32 in network byte order: [A, R, G, B] per pixel
    private static Image renderARGB(int w, int h, byte[] data)
    {
        WritableImage img = new WritableImage(w, h);
        PixelWriter   pw  = img.getPixelWriter();
        for (int y = 0; y < h; y++)
        {
            for (int x = 0; x < w; x++)
            {
                int off = (y * w + x) * 4;
                if (off + 3 >= data.length) break;
                int a = Byte.toUnsignedInt(data[off]);
                int r = Byte.toUnsignedInt(data[off + 1]);
                int g = Byte.toUnsignedInt(data[off + 2]);
                int b = Byte.toUnsignedInt(data[off + 3]);
                pw.setArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }
}

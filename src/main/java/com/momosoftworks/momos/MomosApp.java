package com.momosoftworks.momos;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.momosoftworks.momos.util.image.ImageHelper;
import com.momosoftworks.momos.widget.notification.NotificationHandler;
import com.momosoftworks.momos.widget.Widget;
import com.momosoftworks.momos.widget.registry.WidgetRegistry;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.types.UInt32;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MomosApp extends Application
{
    public static final String DEFAULT_NAMESPACE = "momos";
    public static int MONITOR_WIDTH = 2560;
    private final List<Widget> activeWidgets = new ArrayList<>();

    private DBusConnection dbusConnection;
    public static NotificationHandler NOTIFICATION_HANDLER;

    public static void main(String[] args)
    {   launch(args);
    }

    @Override
    public void start(Stage primaryStage)
    {
        Platform.setImplicitExit(false);

        Thread.ofPlatform().daemon(true).name("dbus-notifications").start(() ->
        {
            try
            {
                dbusConnection = DBusConnectionBuilder.forSessionBus().withShared(false).build();
                dbusConnection.requestBusName(NotificationHandler.BUS_NAME);
                NOTIFICATION_HANDLER = new NotificationHandler();
                NOTIFICATION_HANDLER.setConnection(dbusConnection);
                dbusConnection.exportObject(NotificationHandler.OBJECT_PATH, NOTIFICATION_HANDLER);
                NOTIFICATION_HANDLER.Notify("momos", new UInt32(0), ImageHelper.getIconPath("thunar"),
                                            "Momos is running!", "Click here to open the configuration menu.", List.of("default", "Open Config"),
                                            Map.of(), -1);
            }
            catch (Exception e) { e.printStackTrace(); }
        });

        // Initialize all registered widgets
        for (var entry : WidgetRegistry.getRegistry().entrySet())
        {
            Widget widget = entry.getValue().get();
            widget.init();
            activeWidgets.add(widget);
            System.out.println("Initialized widget: " + entry.getKey());
        }

        WidgetRegistry.BAR_LEFT.get().show();
        WidgetRegistry.BAR_RIGHT.get().show();
        WidgetRegistry.APP_MENU.get().load();
    }

    @Override
    public void stop()
    {
        if (dbusConnection != null)
        {
            try { dbusConnection.close(); }
            catch (Exception e) { e.printStackTrace(); }
        }

        for (Widget widget : activeWidgets)
        {   widget.shutdown();
        }
    }

    public static File getConfigFile()
    {   return new File(System.getenv("HOME") + "/.config/momos/config.json");
    }

    public static JsonElement getConfigSetting(String key)
    {
        File configFile = getConfigFile();
        // Create file if absent
        if (!configFile.exists())
        {
            try
            {   configFile.getParentFile().mkdirs();
                configFile.createNewFile();
                // write empty JSON object to file
                Files.writeString(configFile.toPath(), "{}");
            }
            catch (Exception e)
            {   e.printStackTrace();
                return null;
            }
        }
        // Read JSON from file
        try
        {   Gson gson = new Gson();
            JsonObject config = gson.fromJson(Files.readString(configFile.toPath()), JsonObject.class);
            return config.get(key);
        }
        catch (Exception e)
        {   e.printStackTrace();
            return null;
        }
    }

    public static void saveConfigSetting(String key, JsonElement data)
    {
        File configFile = getConfigFile();
        try
        {   Gson gson = new Gson().newBuilder().setPrettyPrinting().create();
            JsonObject config = gson.fromJson(Files.readString(configFile.toPath()), JsonObject.class);
            config.add(key, data);
            Files.writeString(configFile.toPath(), gson.toJson(config));
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
    }
}

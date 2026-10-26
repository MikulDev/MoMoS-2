package com.momosoftworks.momos.widget.notification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.momosoftworks.momos.MomosApp;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * The "notifications" section of the config file. Every setting is optional:
 * <pre>{@code
 * "notifications": {
 *     "timeout": 10,             // seconds a notification stays on screen, unless it asks for a time of its own
 *     "max_history": 100,        // how many notifications the history keeps
 *     "dont_store": ["spotify"]  // apps whose notifications are shown, but kept out of the history
 * }
 * }</pre>
 * Apps in {@code dont_store} match by any part of their window class or name, ignoring case. An empty string
 * matches notifications that no window belongs to, like those sent by {@code notify-send}.
 */
public record NotificationConfig(Duration timeout, int maxHistory, List<String> dontStore)
{
    private static final NotificationConfig DEFAULT = new NotificationConfig(Duration.seconds(10), 100, List.of());

    public static NotificationConfig load()
    {
        JsonElement element = MomosApp.getConfigSetting("notifications");
        if (element == null || !element.isJsonObject()) return DEFAULT;
        try
        {
            JsonObject config = element.getAsJsonObject();
            Duration timeout = config.has("timeout") ? Duration.seconds(config.get("timeout").getAsDouble()) : DEFAULT.timeout;
            int maxHistory = config.has("max_history") ? config.get("max_history").getAsInt() : DEFAULT.maxHistory;
            List<String> dontStore = new ArrayList<>();
            if (config.has("dont_store"))
            {   config.getAsJsonArray("dont_store").forEach(app -> dontStore.add(app.getAsString().toLowerCase()));
            }
            return new NotificationConfig(timeout, maxHistory, dontStore);
        }
        catch (Exception e)
        {   System.err.println("[Notifications] Invalid config, using defaults: " + e.getMessage());
            return DEFAULT;
        }
    }

    /** Whether {@code record} belongs in the history, i.e. its app isn't in {@code dont_store} */
    public boolean shouldStore(NotificationRecord record)
    {
        String appClass = record.appClass().toLowerCase();
        String appName = record.appName().toLowerCase();
        for (String app : dontStore)
        {
            if (app.isEmpty() ? appClass.isEmpty() : appClass.contains(app) || appName.contains(app))
            {   return false;
            }
        }
        return true;
    }
}

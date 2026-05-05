package com.momosoftworks.momos.util.wm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.momosoftworks.momos.MomosApp;

public class BspwmConfig
{
    public void apply()
    {
        JsonObject cfg = getConfig();

        set("border_width", getInt(cfg, "border_width", 1));
        set("window_gap", getInt(cfg, "window_gap", 0));
        set("focus_follows_pointer", getBool(cfg, "focus_follows_pointer", true));
        set("split_ratio", getDouble(cfg, "split_ratio", 0.5));
        set("borderless_monocle", getBool(cfg, "borderless_monocle", true));
        set("gapless_monocle", getBool(cfg, "gapless_monocle", true));

        CommandHelper.execute("bspc", "rule", "-a", "com.momosoftworks.momos.MomosApp",
                              "state=floating", "sticky=on", "layer=above", "border=off", "manage=off");
    }

    private void set(String key, Object value)
    {
        CommandHelper.execute("bspc", "config", key, String.valueOf(value));
    }

    private JsonObject getConfig()
    {
        JsonElement el = MomosApp.getConfigSetting("bspwm");
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : new JsonObject();
    }

    private int getInt(JsonObject obj, String key, int def)
    {   return obj.has(key) ? obj.get(key).getAsInt() : def;
    }

    private boolean getBool(JsonObject obj, String key, boolean def)
    {   return obj.has(key) ? obj.get(key).getAsBoolean() : def;
    }

    private double getDouble(JsonObject obj, String key, double def)
    {   return obj.has(key) ? obj.get(key).getAsDouble() : def;
    }
}

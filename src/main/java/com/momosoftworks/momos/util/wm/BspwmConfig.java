package com.momosoftworks.momos.util.wm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.momosoftworks.momos.MomosApp;

import java.io.File;
import java.nio.file.Files;

public class BspwmConfig
{
    private static final String EXTERNAL_RULES_SCRIPT = """
            #!/bin/sh
            WID="$1"

            i=0
            while [ "$i" -lt 15 ]; do
                HINTS=$(xprop -id "$WID" WM_NORMAL_HINTS 2>/dev/null)
                case "$HINTS" in
                    *"not found"*)
                        i=$((i + 1))
                        sleep 0.02
                        continue
                        ;;
                esac
                MIN=$(printf '%s\\n' "$HINTS" | sed -n 's/.*minimum size: \\([0-9]*\\) by \\([0-9]*\\).*/\\1 \\2/p')
                MAX=$(printf '%s\\n' "$HINTS" | sed -n 's/.*maximum size: \\([0-9]*\\) by \\([0-9]*\\).*/\\1 \\2/p')
                if [ -n "$MIN" ] && [ "$MIN" = "$MAX" ]; then
                    echo "state=floating"
                fi
                exit 0
            done
            exit 0
            """;

    public void apply()
    {
        JsonObject cfg = getConfig();

        set("border_width", getInt(cfg, "border_width", 1));
        set("window_gap", getInt(cfg, "window_gap", 0));
        set("focus_follows_pointer", getBool(cfg, "focus_follows_pointer", true));
        set("split_ratio", getDouble(cfg, "split_ratio", 0.5));
        set("borderless_monocle", getBool(cfg, "borderless_monocle", true));
        set("gapless_monocle", getBool(cfg, "gapless_monocle", true));
        set("external_rules_command", prepareExternalRulesScript());

        CommandHelper.execute("bspc", "rule", "-a", "com.momosoftworks.momos.MomosApp",
                              "state=floating", "sticky=on", "layer=above", "border=off", "manage=off");
    }

    private String prepareExternalRulesScript()
    {
        File scriptFile = new File(System.getenv("HOME") + "/.config/momos/autofloat_rule.sh");
        try
        {
            scriptFile.getParentFile().mkdirs();
            Files.writeString(scriptFile.toPath(), EXTERNAL_RULES_SCRIPT);
            scriptFile.setExecutable(true);
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
        return scriptFile.getAbsolutePath();
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

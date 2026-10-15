package com.momosoftworks.momos.util.x11;

import java.util.HashMap;
import java.util.Map;

/** Named X11 control keys, keyed by their XK_ keysym values from {@code <X11/keysymdef.h>}. */
public enum X11Key
{
    BACKSPACE(0xFF08L),
    TAB      (0xFF09L),
    RETURN   (0xFF0DL),
    ESCAPE   (0xFF1BL),
    HOME     (0xFF50L),
    LEFT     (0xFF51L),
    UP       (0xFF52L),
    RIGHT    (0xFF53L),
    DOWN     (0xFF54L),
    END      (0xFF57L),
    PRINT    (0xFF61L),
    SUPER_L  (0xFFEBL),
    SUPER_R  (0xFFECL),
    DELETE   (0xFFFFL);

    private static final Map<Long, X11Key> BY_KEYSYM = new HashMap<>();
    static
    {   for (X11Key key : values()) BY_KEYSYM.put(key.keysym, key);
    }

    private final long keysym;

    X11Key(long keysym)
    {   this.keysym = keysym;
    }

    public long keysym()
    {   return keysym;
    }

    /** @return the key for the given keysym, or {@code null} if it isn't a named key */
    public static X11Key fromKeysym(long keysym)
    {   return BY_KEYSYM.get(keysym);
    }
}

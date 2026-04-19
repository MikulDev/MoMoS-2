package com.momosoftworks.momos.util.x11;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Grabs the physical keyboard at the X11 level and forwards translated key
 * tokens to a callback.  Uses its own display connection so it is entirely
 * independent of JavaFX's display connection and safe to run on any thread.
 * <p>
 * Because the grab is tied to the X11 client connection, it is released
 * automatically if the JVM exits for any reason.
 */
public class X11KeyGrabber
{
    public interface LibX11
    {
        Pointer XOpenDisplay(String displayName);
        long XDefaultRootWindow(Pointer display);
        int XGrabKeyboard(Pointer display, long grabWindow, int ownerEvents, int pointerMode, int keyboardMode, long time);
        int XUngrabKeyboard(Pointer display, long time);
        int XNextEvent(Pointer display, Pointer eventReturn);
        int XLookupString(Pointer keyEvent, byte[] bufferReturn, int bytesBuffer, long[] keysymReturn, Pointer statusInOut);
        int XPending(Pointer display);
        int XCloseDisplay(Pointer display);
        int XQueryKeymap(Pointer display, byte[] keysReturn);
        int XKeysymToKeycode(Pointer display, long keysym);
    }

    private static final int  GRAB_SUCCESS    = 0;
    private static final int  ALREADY_GRABBED = 1;
    private static final int  KEY_PRESS       = 2;
    private static final int  GRAB_MODE_ASYNC = 1;
    private static final long CURRENT_TIME    = 0L;

    // XK_ keysym values from <X11/keysymdef.h>
    public static final long XK_BACKSPACE = 0xFF08L;
    public static final long XK_RETURN    = 0xFF0DL;
    public static final long XK_TAB       = 0xFF09L;
    public static final long XK_ESCAPE    = 0xFF1BL;
    public static final long XK_DELETE    = 0xFFFFL;
    public static final long XK_HOME      = 0xFF50L;
    public static final long XK_END       = 0xFF57L;
    public static final long XK_UP        = 0xFF52L;
    public static final long XK_DOWN      = 0xFF54L;
    public static final long XK_LEFT      = 0xFF51L;
    public static final long XK_RIGHT     = 0xFF53L;
    public static final long XK_SUPER_L   = 0xFFEBL;
    public static final long XK_SUPER_R   = 0xFFECL;
    public static final long XK_PRINT     = 0xFF61L;

    private static final int XEVENT_SIZE = 192;

    private static final LibX11 LIB = LibraryLoader.create(LibX11.class).load("X11");

    private static final Map<Long, String> TRANSLATION_MAP = new HashMap<>(){{
        put(XK_ESCAPE, "KEY Escape");
        put(XK_RETURN, "KEY Return");
        put(XK_TAB, "KEY Tab");
        put(XK_BACKSPACE, "KEY BackSpace");
        put(XK_DELETE, "KEY Delete");
        put(XK_HOME, "KEY Home");
        put(XK_END, "KEY End");
        put(XK_UP, "KEY Up");
        put(XK_DOWN, "KEY Down");
        put(XK_LEFT, "KEY Left");
        put(XK_RIGHT, "KEY Right");
        put(XK_SUPER_L, "KEY Super_L");
        put(XK_SUPER_R, "KEY Super_R");
    }};

    private volatile boolean running  = false;
    private Runnable onResume = null;
    private final Set<Long> interruptKeys = new HashSet<>();

    public void addInterruptKeys(Long... keys)
    {   interruptKeys.addAll(Arrays.asList(keys));
    }

    /** Called on the grab thread each time the grabber resumes after an interrupt. */
    public void setOnResume(Runnable onResume)
    {   this.onResume = onResume;
    }

    /**
     * Grabs the keyboard and blocks, calling {@code onKey} with each token until
     * {@link #stop()} is called.  Always run this from a dedicated background thread.
     * <p>
     * Token format matches the keygrabber protocol:
     * <ul>
     *   <li>{@code "CHAR <text>"} — printable character(s) to insert</li>
     *   <li>{@code "KEY <name>"}  — named control key (Escape, Return, BackSpace, …)</li>
     * </ul>
     * <p>
     * Keys registered via {@link #addInterruptKeys} are never forwarded to {@code onKey}.
     * Instead, the grab is released so the OS/WM can handle the key combination, then
     * automatically re-established once all interrupt keys are physically released.
     */
    public void start(Consumer<String> onKey)
    {
        Pointer display = LIB.XOpenDisplay(null);
        if (display == null || display.address() == 0)
            throw new IllegalStateException("[X11KeyGrabber] Cannot open X11 display");

        try
        {
            long root = LIB.XDefaultRootWindow(display);

            // Pre-resolve interrupt keysyms → keycodes for XQueryKeymap polling
            Set<Integer> interruptCodes = new HashSet<>();
            for (long sym : interruptKeys)
            {
                int code = LIB.XKeysymToKeycode(display, sym);
                if (code > 0) interruptCodes.add(code);
            }

            this.grab(display, root);
            running = true;

            Pointer event  = Memory.allocateDirect(Runtime.getSystemRuntime(), XEVENT_SIZE);
            byte[] buf = new byte[32];
            long[] keysym = new long[1];

            while (running)
            {
                if (LIB.XPending(display) == 0)
                {
                    try { Thread.sleep(5); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
                    continue;
                }

                LIB.XNextEvent(display, event);
                if (event.getInt(0) != KEY_PRESS) continue;

                int len = LIB.XLookupString(event, buf, buf.length - 1, keysym, null);

                if (!interruptCodes.isEmpty() && interruptKeys.contains(keysym[0]))
                {
                    LIB.XUngrabKeyboard(display, CURRENT_TIME);
                    this.waitForInterruptKeysReleased(display, interruptCodes);
                    if (!running) break;
                    this.grab(display, root);
                    if (onResume != null)
                    {   onResume.run();
                    }
                    continue;
                }

                String token = translate(keysym[0], buf, len);
                if (token != null)
                {   onKey.accept(token);
                }
            }
        }
        finally
        {   LIB.XUngrabKeyboard(display, CURRENT_TIME);
            LIB.XCloseDisplay(display);
        }
    }

    /** Signals the event loop to exit on its next poll cycle (≤ 5 ms). */
    public void stop()
    {   running = false;
    }

    private void grab(Pointer display, long root)
    {
        int status = ALREADY_GRABBED;
        for (int tries = 0; status == ALREADY_GRABBED && tries < 20; tries++)
        {
            if (tries > 0)
            {
                try
                {   Thread.sleep(50);
                }
                catch (InterruptedException e)
                {   Thread.currentThread().interrupt();
                    return;
                }
            }
            status = LIB.XGrabKeyboard(display, root, 0, GRAB_MODE_ASYNC, GRAB_MODE_ASYNC, CURRENT_TIME);
        }
        if (status != GRAB_SUCCESS)
        {   throw new IllegalStateException("[X11KeyGrabber] XGrabKeyboard failed: " + status);
        }
    }

    private void waitForInterruptKeysReleased(Pointer display, Set<Integer> keycodes)
    {
        byte[] keys = new byte[32];
        while (running)
        {
            LIB.XQueryKeymap(display, keys);
            boolean anyHeld = keycodes.stream().anyMatch(code -> (keys[code >> 3] & (1 << (code & 7))) != 0);
            if (!anyHeld)
            {   break;
            }
            try
            {   Thread.sleep(10);
            }
            catch (InterruptedException e)
            {   Thread.currentThread().interrupt(); break;
            }
        }
    }

    private static String translate(long sym, byte[] buf, int len)
    {
        String lookupResult = TRANSLATION_MAP.get(sym);
        if (lookupResult != null)
        {   return lookupResult;
        }
        if (len > 0)
        {   return "CHAR " + new String(buf, 0, len, StandardCharsets.UTF_8);
        }
        return null;
    }
}

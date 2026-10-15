package com.momosoftworks.momos.util.x11;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jnr.ffi.Runtime;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/**
 * Grabs the physical keyboard at the X11 level and forwards named keys and
 * typed text to callbacks.  Uses its own display connection so it is entirely
 * independent of JavaFX's display connection and safe to run on any thread.
 * <p>
 * Configure it builder-style, then {@link #start()} / {@link #stop()} it as often as needed:
 * <pre>{@code
 * grabber = new X11KeyGrabber()
 *         .interrupt(X11Key.SUPER_L, X11Key.SUPER_R)
 *         .key(X11Key.ESCAPE, () -> Platform.runLater(this::hide));
 * }</pre>
 * All callbacks run on the grab thread, not the JavaFX thread.
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

    private static final int XEVENT_SIZE = 192;

    private static final LibX11 LIB = LibraryLoader.create(LibX11.class).load("X11");

    private final Set<X11Key> interruptKeys = EnumSet.noneOf(X11Key.class);
    private final Map<X11Key, Runnable> keyHandlers = new EnumMap<>(X11Key.class);
    private Consumer<X11Key> onKey = key -> {};
    private Consumer<String> onText = text -> {};
    private Runnable onResume = () -> {};
    private Thread thread;

    /**
     * Keys that are never forwarded to any callback. Instead, the grab is released so the
     * OS/WM can handle the key combination, then automatically re-established once all
     * interrupt keys are physically released.
     */
    public X11KeyGrabber interrupt(X11Key... keys)
    {   interruptKeys.addAll(Arrays.asList(keys));
        return this;
    }

    /** Runs {@code handler} when {@code key} is pressed, instead of passing it to {@link #onKey}. */
    public X11KeyGrabber key(X11Key key, Runnable handler)
    {   keyHandlers.put(key, handler);
        return this;
    }

    /** Called with each named key press that has no dedicated {@link #key} handler. */
    public X11KeyGrabber onKey(Consumer<X11Key> onKey)
    {   this.onKey = onKey;
        return this;
    }

    /** Called with the printable character(s) produced by any key that isn't an {@link X11Key}. */
    public X11KeyGrabber onText(Consumer<String> onText)
    {   this.onText = onText;
        return this;
    }

    /** Called each time the grab is re-established after an {@link #interrupt} key is released. */
    public X11KeyGrabber onResume(Runnable onResume)
    {   this.onResume = onResume;
        return this;
    }

    /** Grabs the keyboard on a new background thread. Does nothing if already started. */
    public synchronized void start()
    {
        if (thread != null) return;
        thread = Thread.ofPlatform().name("x11-keygrabber").daemon().start(() ->
        {
            try
            {   this.run();
            }
            catch (Exception e)
            {   e.printStackTrace();
            }
        });
    }

    /** Releases the grab; the thread exits on its next poll cycle (≤ 5 ms). Safe to call when not started. */
    public synchronized void stop()
    {
        if (thread == null) return;
        thread.interrupt();
        thread = null;
    }

    // Each grab thread runs until interrupted, so a stop() immediately followed by start()
    // can't revive the old thread's loop.
    private void run()
    {
        Pointer display = LIB.XOpenDisplay(null);
        if (display == null || display.address() == 0)
            throw new IllegalStateException("[X11KeyGrabber] Cannot open X11 display");

        try
        {
            long root = LIB.XDefaultRootWindow(display);

            // Pre-resolve interrupt keysyms → keycodes for XQueryKeymap polling
            Set<Integer> interruptCodes = new HashSet<>();
            for (X11Key key : interruptKeys)
            {
                int code = LIB.XKeysymToKeycode(display, key.keysym());
                if (code > 0) interruptCodes.add(code);
            }

            this.grab(display, root);

            Pointer event  = Memory.allocateDirect(Runtime.getSystemRuntime(), XEVENT_SIZE);
            byte[] buf = new byte[32];
            long[] keysym = new long[1];

            while (!Thread.currentThread().isInterrupted())
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
                X11Key key = X11Key.fromKeysym(keysym[0]);

                if (!interruptCodes.isEmpty() && interruptKeys.contains(key))
                {
                    LIB.XUngrabKeyboard(display, CURRENT_TIME);
                    this.waitForInterruptKeysReleased(display, interruptCodes);
                    if (Thread.currentThread().isInterrupted()) break;
                    this.grab(display, root);
                    onResume.run();
                    continue;
                }

                if (key != null)
                {
                    Runnable handler = keyHandlers.get(key);
                    if (handler != null) handler.run();
                    else onKey.accept(key);
                }
                else if (len > 0)
                {   onText.accept(new String(buf, 0, len, StandardCharsets.UTF_8));
                }
            }
        }
        finally
        {   LIB.XUngrabKeyboard(display, CURRENT_TIME);
            LIB.XCloseDisplay(display);
        }
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
        while (!Thread.currentThread().isInterrupted())
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
}

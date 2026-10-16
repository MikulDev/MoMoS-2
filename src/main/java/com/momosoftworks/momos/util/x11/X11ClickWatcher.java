package com.momosoftworks.momos.util.x11;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;

/**
 * Reports mouse button presses anywhere on screen by polling the X11 pointer state.
 * Unlike a pointer grab, this never consumes clicks, so they still reach whatever window
 * is under the cursor. Uses its own display connection, like {@link X11KeyGrabber}.
 * <pre>{@code
 * watcher = new X11ClickWatcher((x, y) -> Platform.runLater(() -> onClick(x, y)));
 * }</pre>
 * Positions are in physical screen pixels. The callback runs on the watcher thread, not the JavaFX thread.
 */
public class X11ClickWatcher
{
    public interface LibX11
    {
        Pointer XOpenDisplay(String displayName);
        long XDefaultRootWindow(Pointer display);
        int XQueryPointer(Pointer display, long window, long[] rootReturn, long[] childReturn,
                          int[] rootX, int[] rootY, int[] winX, int[] winY, int[] maskReturn);
        int XCloseDisplay(Pointer display);
    }

    public interface PressHandler
    {   void onPress(int x, int y);
    }

    // Button1Mask | Button2Mask | Button3Mask. The scroll wheel (buttons 4/5) doesn't count as a click
    private static final int BUTTON_MASK = (1 << 8) | (1 << 9) | (1 << 10);
    private static final long POLL_INTERVAL_MS = 10;

    private static final LibX11 LIB = LibraryLoader.create(LibX11.class).load("X11");

    private final PressHandler onPress;
    private Thread thread;

    public X11ClickWatcher(PressHandler onPress)
    {   this.onPress = onPress;
    }

    /** Starts watching on a new background thread. Does nothing if already started. */
    public synchronized void start()
    {
        if (thread != null) return;
        thread = Thread.ofPlatform().name("x11-clickwatcher").daemon().start(() ->
        {
            try
            {   this.run();
            }
            catch (Exception e)
            {   e.printStackTrace();
            }
        });
    }

    /** Stops watching; the thread exits on its next poll cycle. Safe to call when not started. */
    public synchronized void stop()
    {
        if (thread == null) return;
        thread.interrupt();
        thread = null;
    }

    private void run()
    {
        Pointer display = LIB.XOpenDisplay(null);
        if (display == null || display.address() == 0)
            throw new IllegalStateException("[X11ClickWatcher] Cannot open X11 display");

        try
        {
            long root = LIB.XDefaultRootWindow(display);
            long[] rootReturn = new long[1], childReturn = new long[1];
            int[] x = new int[1], y = new int[1], winX = new int[1], winY = new int[1], mask = new int[1];

            // A button already held when watching starts (e.g. the click that opened a popup) isn't a new press
            LIB.XQueryPointer(display, root, rootReturn, childReturn, x, y, winX, winY, mask);
            int held = mask[0] & BUTTON_MASK;

            while (!Thread.currentThread().isInterrupted())
            {
                try { Thread.sleep(POLL_INTERVAL_MS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }

                // Returns False when the pointer is on another X screen
                if (LIB.XQueryPointer(display, root, rootReturn, childReturn, x, y, winX, winY, mask) == 0) continue;

                int buttons = mask[0] & BUTTON_MASK;
                if ((buttons & ~held) != 0)
                {   onPress.onPress(x[0], y[0]);
                }
                held = buttons;
            }
        }
        finally
        {   LIB.XCloseDisplay(display);
        }
    }
}

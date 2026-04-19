package com.momosoftworks.momos.util.x11;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Pointer;

public class X11Keyboard
{
    public interface LibGDK
    {
        Pointer gdk_display_get_default();
        Pointer gdk_display_get_default_seat(Pointer display);
        Pointer gdk_x11_window_lookup_for_display(Pointer display, long xid);
        Pointer gdk_x11_window_foreign_new_for_display(Pointer display, long xid);
        int gdk_seat_grab(Pointer seat, Pointer window, int capabilities,
                          boolean ownerEvents, Pointer cursor, Pointer event,
                          Pointer prepareFunc, Pointer prepareFuncData);
        void gdk_seat_ungrab(Pointer seat);
    }

    // GdkSeatCapabilities
    private static final int GDK_SEAT_CAPABILITY_KEYBOARD = 2;

    // GdkGrabStatus
    public static final int GDK_GRAB_SUCCESS        = 0;
    public static final int GDK_GRAB_ALREADY_GRABBED = 1;

    private static final LibGDK  LIB     = LibraryLoader.create(LibGDK.class).load("gdk-3");
    private static final Pointer DISPLAY = LIB.gdk_display_get_default();
    private static final Pointer SEAT    = LIB.gdk_display_get_default_seat(DISPLAY);

    /**
     * Attempts a single keyboard grab and routes all key events to the given X11 window,
     * regardless of which window the WM considers focused (ownerEvents=false).
     * <p>
     * MUST be called from the GDK/JavaFX main thread.
     *
     * @return GdkGrabStatus (0 = success, 1 = already grabbed, etc.)
     */
    public static int tryGrab(long windowId)
    {
        Pointer gdkWindow = LIB.gdk_x11_window_lookup_for_display(DISPLAY, windowId);
        if (gdkWindow == null || gdkWindow.address() == 0)
            gdkWindow = LIB.gdk_x11_window_foreign_new_for_display(DISPLAY, windowId);
        if (gdkWindow == null || gdkWindow.address() == 0)
        {
            System.err.println("[X11Keyboard] No GDK window for 0x" + Long.toHexString(windowId));
            return -1;
        }
        // ownerEvents=false: all key events are delivered to the grab window, bypassing
        // WM focus entirely. This is required for unmanaged (override-redirect / BSPWM
        // manage=off) windows that never receive normal X11 input focus.
        return LIB.gdk_seat_grab(SEAT, gdkWindow, GDK_SEAT_CAPABILITY_KEYBOARD,
                                 false, null, null, null, null);
    }

    /** Releases the active GDK keyboard grab. */
    public static void ungrab()
    {
        LIB.gdk_seat_ungrab(SEAT);
    }
}

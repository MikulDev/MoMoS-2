package com.momosoftworks.momos.util.ui;

import com.momosoftworks.momos.util.image.ImageHelper;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.function.Supplier;

/** A vector icon from {@code icons/<name>.svg}. Each {@link #get()} builds a new node, {@code size} pixels square */
public record Icon(String name, int size, Color color) implements Supplier<Group>
{
    public static final Icon CLEAR = new Icon("clear", 16);
    public static final Icon CLOSE = new Icon("close", 16);
    public static final Icon FOLDER = new Icon("folder", 16);
    public static final Icon LAUNCHER = new Icon("launcher_icon", 16);
    public static final Icon NOTIFICATION = new Icon("notification", 16);
    public static final Icon PACKAGE = new Icon("package", 16);
    public static final Icon PIN = new Icon("pin", 16);
    public static final Icon UNPIN = new Icon("unpin", 16);
    public static final Icon REFRESH = new Icon("refresh", 16);
    public static final Icon SEARCH = new Icon("search", 16);
    public static final Icon TERMINAL = new Icon("terminal", 16);
    public static final Icon UPDATE = new Icon("update", 16);
    public static final Icon UPDATE_ALL = new Icon("update_all", 16);

    public static final Icon LEFT_ARROW = new Icon("left_arrow", 16);
    public static final Icon RIGHT_ARROW = new Icon("right_arrow", 16);

    public static final Icon PLAY = new Icon("play", 16);
    public static final Icon PAUSE = new Icon("pause", 16);
    public static final Icon NEXT = new Icon("next", 16);
    public static final Icon PREV = new Icon("prev", 16);
    public static final Icon VOLUME_HIGH = new Icon("volume_high", 16);
    public static final Icon VOLUME_MEDIUM = new Icon("volume_medium", 16);
    public static final Icon VOLUME_LOW = new Icon("volume_low", 16);
    public static final Icon VOLUME_MUTED = new Icon("volume_muted", 16);

    public static final Icon SHUTDOWN = new Icon("shutdown", 16);
    public static final Icon RESTART = new Icon("restart", 16);
    public static final Icon SLEEP = new Icon("sleep", 16);

    public Icon(String name, int size)
    {   this(name, size, Color.WHITE);
    }

    @Override
    public Group get()
    {
        // Keeps the icon's full size even where its paths don't reach the edges of the view box
        Rectangle boundingBox = new Rectangle(size, size, Color.TRANSPARENT);
        Group group = new Group(boundingBox, ImageHelper.loadSVG(name, size, color));
        group.getStyleClass().add("icon");
        return group;
    }

    public Icon colored(Color color)
    {   return new Icon(name, size, color);
    }

    public Icon sized(int newSize)
    {   return new Icon(name, newSize, color);
    }
}

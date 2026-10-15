package com.momosoftworks.momos.widget.bar;

import com.momosoftworks.momos.util.wm.Desktops;
import com.momosoftworks.momos.util.wm.Windows;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class TaskButton extends HBox
{
    // Named "wm-focused" rather than "focused" to avoid colliding with JavaFX's own
    // built-in :focused pseudo-class, which tracks real keyboard focus.
    private static final PseudoClass WM_FOCUSED = PseudoClass.getPseudoClass("wm-focused");
    private static final PseudoClass MINIMIZED  = PseudoClass.getPseudoClass("minimized");
    private static final PseudoClass URGENT     = PseudoClass.getPseudoClass("urgent");

    private final String windowId;
    private final ImageView icon;
    private final Label nameLabel;

    public TaskButton(String windowId, String title, Image appIcon)
    {
        this.windowId = windowId;

        this.setSpacing(8);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("task-button");

        // Icon
        icon = new ImageView();
        icon.setFitWidth(28);
        icon.setPreserveRatio(true);
        if (appIcon != null)
        {   icon.setImage(appIcon);
        }

        nameLabel = new Label(title);
        nameLabel.getStyleClass().add("task-button-line");
        nameLabel.setWrapText(true);
        this.getChildren().addAll(icon, nameLabel);

        this.setOnMouseClicked(e -> Desktops.focusWindow(windowId));
    }

    public static TaskButton create(String windowId)
    {
        String title = Windows.getTitle(windowId);
        // Skip windows without valid titles
        if (title == null || title.isEmpty())
        {   return null;
        }
        Image icon  = Windows.getIcon(windowId);
        return new TaskButton(windowId, title, icon);
    }

    /** Update title and icon when the window state changes */
    public void update(String title, Image appIcon)
    {
        nameLabel.setText(title);
        if (appIcon != null)
        {   icon.setImage(appIcon);
        }
    }

    public void setTaskFocused(boolean focused)
    {   this.pseudoClassStateChanged(WM_FOCUSED, focused);
        this.nameLabel.pseudoClassStateChanged(WM_FOCUSED, focused);
    }

    public void setMinimized(boolean minimized)
    {   this.pseudoClassStateChanged(MINIMIZED, minimized);
        this.nameLabel.pseudoClassStateChanged(MINIMIZED, minimized);
    }

    public void setUrgent(boolean urgent)
    {   this.pseudoClassStateChanged(URGENT, urgent);
        this.nameLabel.pseudoClassStateChanged(URGENT, urgent);
    }

    public void setTitle(String title)
    {   nameLabel.setText(title);
    }

    public String getWindowId()
    {   return windowId;
    }
}

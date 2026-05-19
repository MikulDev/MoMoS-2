package com.momosoftworks.momos.widget.bar;

import com.momosoftworks.momos.util.wm.Desktops;
import com.momosoftworks.momos.util.wm.Windows;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class TaskButton extends HBox
{
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

    private void setStyleFlag(String style, boolean flag)
    {
        this.getStyleClass().remove(style);
        this.nameLabel.getStyleClass().remove(style);
        if (flag)
        {   this.getStyleClass().add(style);
            this.nameLabel.getStyleClass().add(style);
        }
    }

    public void setTaskFocused(boolean focused)
    {   setStyleFlag("focused", focused);
    }

    public void setMinimized(boolean minimized)
    {   setStyleFlag("minimized", minimized);
    }

    public void setUrgent(boolean urgent)
    {   setStyleFlag("urgent", urgent);
    }

    public void setTitle(String title)
    {   nameLabel.setText(title);
    }

    public String getWindowId()
    {   return windowId;
    }
}

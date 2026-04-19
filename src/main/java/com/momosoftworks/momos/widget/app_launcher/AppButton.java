package com.momosoftworks.momos.widget.app_launcher;

import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public abstract class AppButton extends Pane
{
    protected Application app;
    protected AppLauncherWidget parent;

    public AppButton(Application app, AppLauncherWidget parent)
    {
        this.app = app;
        this.parent = parent;
        Pane mainPane = this.createPane();
        mainPane.setOnMouseClicked(this::onClicked);
        mainPane.setOnMouseEntered(e -> this.onMouseEnter());
        mainPane.setOnMouseExited(e  -> this.onMouseExit());
        mainPane.setOnMouseMoved(e -> this.onMouseEnter());
        this.getChildren().add(mainPane);
        this.setMaxWidth(Double.MAX_VALUE);
    }

    @Override
    protected void layoutChildren()
    {   getChildren().get(0).resize(getWidth(), getHeight());
    }

    public void setHovered(boolean hovered)
    {
        var classes = this.getChildren().getFirst().getStyleClass();
        if (hovered)
        {   if (!classes.contains("hovered"))
            {   classes.add("hovered");
            }
        }
        else
        {   classes.removeIf("hovered"::equals);
        }
    }

    protected abstract Pane createPane();

    protected void onMouseEnter() {}
    protected void onMouseExit() {}

    protected void onClicked(MouseEvent event)
    {   this.parent.launchApp(app.exec());
        this.parent.hide();
    }
}

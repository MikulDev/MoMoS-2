package com.momosoftworks.momos.widget.app_launcher;

import javafx.geometry.Pos;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;

public class PinnedAppButton extends AppButton
{
    public PinnedAppButton(Application app, AppLauncherWidget parent)
    {   super(app, parent);
    }

    @Override
    protected Pane createPane()
    {
        StackPane mainPane = new StackPane();
        mainPane.getStyleClass().add("pinned-app");
        mainPane.setAlignment(Pos.CENTER);
        ImageView icon = new ImageView();
        icon.setFitWidth(28);
        icon.setPreserveRatio(true);
        if (app.icon() != null)
        {   icon.setImage(app.icon());
        }
        mainPane.getChildren().add(icon);
        return mainPane;
    }

    @Override
    protected void onClicked(MouseEvent event)
    {
        if (event.getButton() == MouseButton.SECONDARY)
        {   this.parent.unpinApp(app);
            return;
        }
        super.onClicked(event);
    }

    @Override
    public void onMouseEnter()
    {   this.parent.onMouseEnteredPinnedApp(this);
    }

    @Override
    public void onMouseExit()
    {   this.parent.onMouseExitedAppButton(this);
    }
}

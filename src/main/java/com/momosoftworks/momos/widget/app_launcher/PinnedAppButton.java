package com.momosoftworks.momos.widget.app_launcher;

import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class PinnedAppButton extends AppButton
{
    public PinnedAppButton(Application app, AppLauncherWidget parent)
    {   super(app, parent);
        this.getStyleClass().add("image-button");
    }

    @Override
    protected Node createContent()
    {
        ImageView view = new ImageView();
        if (app.icon() != null)
        {   view.setImage(app.icon());
        }
        view.setFitWidth(28);
        view.setPreserveRatio(true);
        view.setMouseTransparent(true);
        return view;
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

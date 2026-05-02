package com.momosoftworks.momos.widget.app_launcher;

import com.momosoftworks.momos.widget.Widgets;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;

public class PinnedAppButton extends AppButton
{
    public PinnedAppButton(Application app, AppLauncherWidget parent)
    {   super(app, parent);
    }

    @Override
    protected Pane createPane()
    {   return Widgets.imageButton(app.icon(), 28);
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

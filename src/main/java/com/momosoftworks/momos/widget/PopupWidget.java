package com.momosoftworks.momos.widget;

import com.momosoftworks.momos.util.Identifier;
import javafx.application.Platform;

public abstract class PopupWidget extends Widget
{
    public PopupWidget(Identifier id)
    {   super(id);
        this.addCommand("show", this::show);
        this.addCommand("hide", this::hide);
        this.addCommand("toggle", this::toggle);
    }

    public void show()
    {
        Platform.runLater(() ->
        {   this.stage.show();
            this.stage.requestFocus();
        });
    }

    public void hide()
    {   Platform.runLater(() -> this.stage.hide());
    }

    public void toggle()
    {
        Platform.runLater(() ->
        {   if (stage.isShowing()) this.hide();
            else this.show();
        });
    }
}

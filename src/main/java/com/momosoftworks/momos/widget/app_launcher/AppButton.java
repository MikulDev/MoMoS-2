package com.momosoftworks.momos.widget.app_launcher;

import com.momosoftworks.momos.util.wm.CommandHelper;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

public abstract class AppButton extends StackPane
{
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    protected final Application app;
    protected final AppLauncherWidget parent;

    public AppButton(Application app, AppLauncherWidget parent)
    {
        this.app = app;
        this.parent = parent;
        this.getChildren().add(this.createContent());
        this.setOnMouseClicked(this::onClicked);
        this.setOnMouseEntered(e -> this.onMouseEnter());
        this.setOnMouseExited(e -> this.onMouseExit());
        this.setMaxWidth(Double.MAX_VALUE);
    }

    /** True while this entry is the keyboard- or mouse-highlighted selection, not literal CSS hover. */
    public void setSelected(boolean selected)
    {   pseudoClassStateChanged(SELECTED, selected);
    }

    protected abstract Node createContent();

    protected void onMouseEnter() {}
    protected void onMouseExit() {}

    protected void onClicked(MouseEvent event)
    {   CommandHelper.launchProgram(this.app);
        this.parent.hide();
    }
}

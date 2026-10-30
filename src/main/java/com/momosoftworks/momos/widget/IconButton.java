package com.momosoftworks.momos.widget;

import com.momosoftworks.momos.util.ui.Icon;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class IconButton extends Button
{
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    public IconButton(Image icon, int size)
    {
        ImageView view = new ImageView();
        if (icon != null)
        {   view.setImage(icon);
        }
        view.setFitWidth(size);
        view.setPreserveRatio(true);
        this.setGraphic(view);
        this.getStyleClass().add("image-button");
    }

    public IconButton(Icon icon)
    {
        this.setGraphic(icon.get());
        this.getStyleClass().add("image-button");
    }

    /** True while this button is the keyboard-highlighted selection, not literal CSS hover. */
    public void setSelected(boolean selected)
    {   pseudoClassStateChanged(SELECTED, selected);
    }
}

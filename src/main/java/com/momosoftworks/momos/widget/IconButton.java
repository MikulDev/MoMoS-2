package com.momosoftworks.momos.widget;

import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class IconButton extends Button
{
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
}

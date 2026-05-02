package com.momosoftworks.momos.widget;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class Widgets
{
    public static StackPane imageButton(Image icon, int size)
    {
        ImageView imageView = new ImageView();
        if (icon != null) imageView.setImage(icon);
        imageView.setFitWidth(size);
        imageView.setPreserveRatio(true);
        imageView.setMouseTransparent(true);
        StackPane pane = new StackPane(imageView);
        pane.getStyleClass().add("image-button");
        pane.setMaxHeight(Region.USE_PREF_SIZE);
        return pane;
    }
}

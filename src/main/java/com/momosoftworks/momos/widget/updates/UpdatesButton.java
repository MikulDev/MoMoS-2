package com.momosoftworks.momos.widget.updates;

import com.momosoftworks.momos.widget.registry.WidgetRegistry;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

/** Shows the number of available package updates in the bar. Click to list them. */
public class UpdatesButton extends HBox
{
    private static final int ICON_SIZE = 16;
    private static final Image ICON = new Image("icons/package.png", ICON_SIZE * 2, ICON_SIZE * 2, true, true);

    public UpdatesButton()
    {
        UpdatesWidget updates = WidgetRegistry.UPDATES.get();

        this.setSpacing(5);
        this.setAlignment(Pos.CENTER);
        this.getStyleClass().add("updates-button");
        this.setMaxHeight(USE_PREF_SIZE);

        ImageView icon = new ImageView(ICON);
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        icon.getStyleClass().add("updates-button-icon");

        Label count = new Label();
        count.textProperty().bind(updates.countProperty().asString());
        count.getStyleClass().add("updates-button-count");

        this.getChildren().addAll(icon, count);

        this.setOnMouseClicked(e ->
        {   if (e.getButton() == MouseButton.PRIMARY)
            {   updates.toggle(this);
            }
        });
    }
}

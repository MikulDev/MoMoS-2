package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.ui.Icon;
import com.momosoftworks.momos.widget.registry.WidgetRegistry;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

/** Shows the number of notifications in the history in the bar. Click to list them. */
public class NotificationsButton extends HBox
{
    public NotificationsButton()
    {
        NotificationsWidget notifications = WidgetRegistry.NOTIFICATIONS.get();

        this.setSpacing(5);
        this.setAlignment(Pos.CENTER);
        this.getStyleClass().add("notifications-button");
        this.setMaxHeight(USE_PREF_SIZE);

        Group icon = Icon.NOTIFICATION.sized(20).get();
        icon.getStyleClass().add("notifications-button-icon");

        Label count = new Label();
        count.textProperty().bind(Bindings.size(notifications.history().records()).asString());
        count.getStyleClass().add("notifications-button-count");

        this.getChildren().addAll(icon, count);

        this.setOnMouseClicked(e ->
        {   if (e.getButton() == MouseButton.PRIMARY)
            {   notifications.toggle(this);
            }
        });
    }
}

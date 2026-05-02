package com.momosoftworks.momos.widget.app_launcher;

import com.momosoftworks.momos.widget.Widgets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class AppEntryButton extends AppButton
{
    private static final Image PIN_ICON = new Image("icons/pin.png", 17, 17, true, true);

    HBox optionsPane;

    public AppEntryButton(Application app, AppLauncherWidget parent)
    {   super(app, parent);
    }

    @Override
    protected Pane createPane()
    {
        HBox mainPane = new HBox(10);
        mainPane.getStyleClass().add("app-entry");
        mainPane.setAlignment(Pos.CENTER_LEFT);

        // Icon
        ImageView iconView = new ImageView();
        iconView.setFitWidth(28);
        iconView.setPreserveRatio(true);
        if (app.icon() != null)
        {   iconView.setImage(app.icon());
        }
        // Name
        Label nameLabel = new Label(app.name());
        nameLabel.getStyleClass().add("app-entry-name");
        // Options
        this.optionsPane = new HBox(4);
        optionsPane.setAlignment(Pos.CENTER_RIGHT);
        optionsPane.getStyleClass().add("app-entry-options");
        // Pin button
        StackPane pinButton = Widgets.imageButton(PIN_ICON, 14);
        pinButton.setOnMouseClicked(e ->
        {   this.parent.pinApp(app);
            e.consume();
        });
        optionsPane.getChildren().add(pinButton);
        optionsPane.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        mainPane.getChildren().addAll(iconView, nameLabel, spacer, optionsPane);
        return mainPane;
    }

    @Override
    public void setHovered(boolean hovered)
    {   super.setHovered(hovered);
        this.optionsPane.setVisible(hovered);
    }

    @Override
    public void onMouseEnter()
    {   this.parent.onMouseEnteredAppEntry(this);
    }

    @Override
    public void onMouseExit()
    {   this.parent.onMouseExitedAppButton(this);
    }
}

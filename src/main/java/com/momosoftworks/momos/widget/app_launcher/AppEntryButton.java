package com.momosoftworks.momos.widget.app_launcher;

import com.momosoftworks.momos.widget.IconButton;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class AppEntryButton extends AppButton
{
    private static final Image PIN_ICON = new Image("icons/pin.png", 17, 17, true, true);

    HBox optionsPane;

    public AppEntryButton(Application app, AppLauncherWidget parent)
    {   super(app, parent);
        this.getStyleClass().add("app-entry");
    }

    @Override
    protected Node createContent()
    {
        HBox mainPane = new HBox(10);
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
        IconButton pinButton = new IconButton(PIN_ICON, 14);
        pinButton.setOnAction(e -> this.parent.pinApp(app));
        optionsPane.getChildren().add(pinButton);
        optionsPane.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        mainPane.getChildren().addAll(iconView, nameLabel, spacer, optionsPane);
        return mainPane;
    }

    @Override
    public void setSelected(boolean selected)
    {   super.setSelected(selected);
        this.optionsPane.setVisible(selected);
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

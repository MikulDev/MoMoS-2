package com.momosoftworks.momos.widget.shutdown;

import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.util.wm.CommandHelper;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.util.x11.X11Key;
import com.momosoftworks.momos.util.x11.X11KeyGrabber;
import com.momosoftworks.momos.widget.PopupWidget;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class ShutdownMenuWidget extends PopupWidget
{
    private Button firstAction;
    // The stage is unmanaged by BSPWM (manage=off), so it never receives X11 input focus
    // and JavaFX key events/accelerators never fire. Grab the keyboard at the X11 level instead.
    private final X11KeyGrabber grabber = new X11KeyGrabber()
        .interrupt(X11Key.SUPER_L, X11Key.SUPER_R)
        .key(X11Key.ESCAPE, () -> Platform.runLater(this::hide));

    public ShutdownMenuWidget()
    {   super(Identifier.of("momos", "shutdown_menu"));
    }

    @Override
    public Stage initStage(Stage stage)
    {
        HBox actions = new HBox(16);
        actions.getStyleClass().add("shutdown-menu");
        actions.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        for (ShutdownAction action : ShutdownAction.values())
        {   actions.getChildren().add(this.createAction(action));
        }

        // Fills the whole monitor with a transparent, click-through-free backdrop behind the
        // centered menu — compositors that blur transparent regions will blur the full screen.
        StackPane root = new StackPane(actions);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/styles/base.css").toExternalForm());

        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        return stage;
    }

    private VBox createAction(ShutdownAction action)
    {
        Button button = new Button(null, new ImageView(action.icon()));
        button.getStyleClass().add("shutdown-button");
        button.setOnAction(e ->
        {   CommandHelper.execute(action.command());
            this.hide();
        });
        if (this.firstAction == null)
        {   this.firstAction = button;
        }

        Label label = new Label(action.label());
        label.getStyleClass().add("shutdown-label");

        VBox container = new VBox(8, button, label);
        container.setAlignment(Pos.CENTER);
        return container;
    }

    @Override
    public void show()
    {
        this.grabber.start();
        Thread.ofVirtual().start(() ->
        {
            Rectangle2D bounds = Monitors.getBounds("focused");
            Platform.runLater(() ->
            {
                this.stage.setX(bounds.getMinX());
                this.stage.setY(bounds.getMinY());
                this.stage.setWidth(bounds.getWidth());
                this.stage.setHeight(bounds.getHeight());
                this.stage.show();
                this.stage.requestFocus();
                this.firstAction.requestFocus();
            });
        });
    }

    @Override
    public void hide()
    {
        this.grabber.stop();
        super.hide();
    }
}

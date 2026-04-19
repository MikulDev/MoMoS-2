package com.momosoftworks.momos.widget.bar;

import com.momosoftworks.momos.MomosApp;
import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.widget.Widget;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class BarWidget extends Widget
{
    private static final int BAR_HEIGHT = 50;
    private final String monitor;
    private final int xOffset;
    private WorkspaceButtons workspaceButtons;

    public BarWidget(String monitor)
    {
        super(Identifier.of("momos", "bar_" + monitor.toLowerCase()));
        this.monitor = monitor;
        this.xOffset = monitor.equals("LEFT") ? 0 : MomosApp.MONITOR_WIDTH;
    }

    @Override
    public Stage initStage(Stage stage)
    {
        // Root container
        HBox root = new HBox();
        root.getStyleClass().add("taskbar");

        // Left section - workspace indicator for this monitor only
        HBox left = new HBox(8);
        left.setStyle("-fx-alignment: center-left;");

        workspaceButtons = new WorkspaceButtons(monitor);
        left.getChildren().add(workspaceButtons);

        left.getChildren().add(divider());

        // In BarWidget.initStage():
        TaskBar taskBar = new TaskBar(monitor);
        left.getChildren().add(taskBar);
        left.setAlignment(Pos.CENTER_LEFT);

        // Center section (spacer)
        Region center = new Region();
        HBox.setHgrow(center, Priority.ALWAYS);

        // Right section
        HBox right = new HBox(8);
        right.setStyle("-fx-alignment: center-right;");
        right.setAlignment(Pos.CENTER_RIGHT);

        right.getChildren().add(new TextClock(monitor));

        root.getChildren().addAll(left, center, right);

        Scene scene = new Scene(root, MomosApp.MONITOR_WIDTH, BAR_HEIGHT);
        scene.setFill(Color.TRANSPARENT);

        loadStylesheet("bar", scene);

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setScene(scene);
        stage.setTitle("momos-bar-" + monitor.toLowerCase());
        stage.setX(xOffset);
        stage.setY(0);
        stage.setAlwaysOnTop(true);

        return stage;
    }

    private Rectangle divider()
    {
        Rectangle divider = new Rectangle(1, BAR_HEIGHT - 14);
        divider.getStyleClass().add("divider");
        return divider;
    }

    public void show()
    {   Platform.runLater(() -> stage.show());
    }

    @Override
    public void shutdown()
    {   if (workspaceButtons != null) workspaceButtons.shutdown();
        super.shutdown();
    }
}
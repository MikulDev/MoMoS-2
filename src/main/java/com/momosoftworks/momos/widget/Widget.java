package com.momosoftworks.momos.widget;

import com.momosoftworks.momos.widget.ipc.WidgetServer;
import com.momosoftworks.momos.util.Identifier;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.HashMap;

public abstract class Widget
{
    protected WidgetServer server;
    protected Identifier id;
    protected Stage stage;

    public Widget(Identifier id)
    {   this.id = id;
        this.server = new WidgetServer(WidgetServer.createPath(id), new HashMap<>());
    }

    public abstract Stage initStage(Stage stage);

    public Identifier id()
    {   return id;
    }
    public WidgetServer server()
    {   return server;
    }
    public Stage stage()
    {   return stage;
    }

    public void init()
    {
        this.stage = this.initStage(this.createStage());
        this.server.start();
    }

    protected Stage createStage()
    {   return new Stage();
    }

    protected void addCommand(String command, Runnable handler)
    {   server.commandHandlers().put(command, handler);
    }

    protected void loadStylesheet(String stylesheet, Scene scene)
    {
        scene.getStylesheets().addAll(getClass().getResource("/styles/base.css").toExternalForm());
        String stylePath = "/styles/widgets/" + stylesheet + ".css";
        scene.getStylesheets().addAll(getClass().getResource(stylePath).toExternalForm());
    }

    public void shutdown()
    {
        if (stage != null)
        {   stage.close();
        }
        server.stop();
    }
}

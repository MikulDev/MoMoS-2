package com.momosoftworks.momos.widget.app_launcher;

import com.momosoftworks.momos.serialization.JsonCodec;
import com.momosoftworks.momos.util.image.ImageHelper;
import javafx.scene.image.Image;

import java.util.Objects;

public final class Application
{
    public static final JsonCodec<Application> CODEC = JsonCodec.construct(Application.class)
            .arg("icon", JsonCodec.STRING, app -> app.iconPath)
            .arg("name", JsonCodec.STRING, app -> app.name)
            .arg("path", JsonCodec.STRING, app -> app.path)
            .post(app -> app.setIcon(ImageHelper.getIcon(app.iconPath, 64), app.iconPath))
            .build();

    private Image icon;
    private String iconPath;
    private final String name;
    private final String path;

    public Application(Image icon, String iconPath, String name, String path)
    {
        this.icon = icon;
        this.iconPath = iconPath;
        this.name = name;
        this.path = path;
    }

    public Application(String iconPath, String name, String path)
    {   this(null, iconPath, name, path);
    }

    public Image icon() {return icon;}
    public String iconPath() {return iconPath;}
    public String name() {return name;}
    public String path() {return path;}

    public void setIcon(Image icon, String iconPath)
    {   this.icon = icon;
        this.iconPath = iconPath;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Application) obj;
        return Objects.equals(this.icon, that.icon) &&
                Objects.equals(this.iconPath, that.iconPath) &&
                Objects.equals(this.name, that.name) &&
                Objects.equals(this.path, that.path);
    }
}

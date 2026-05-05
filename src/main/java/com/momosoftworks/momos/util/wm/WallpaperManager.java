package com.momosoftworks.momos.util.wm;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.momosoftworks.momos.MomosApp;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WallpaperManager
{
    private static final String DEFAULT_DIR = System.getenv("HOME") + "/.config/momos/wallpapers";
    private static final int DEFAULT_INTERVAL = 600;

    private final String wallpaperDir;
    private final int intervalSeconds;
    private final int totalDisplayWidth;

    public WallpaperManager()
    {
        JsonObject cfg = getConfig();
        String dir = DEFAULT_DIR;
        int interval = DEFAULT_INTERVAL;

        if (cfg != null)
        {
            if (cfg.has("directory"))
            {   dir = cfg.get("directory").getAsString().replace("~", System.getenv("HOME"));
            }
            if (cfg.has("interval_seconds"))
            {   interval = cfg.get("interval_seconds").getAsInt();
            }
        }

        this.wallpaperDir      = dir;
        this.intervalSeconds   = interval;
        this.totalDisplayWidth = computeDisplayWidth();
    }

    private int computeDisplayWidth()
    {
        if (MomosApp.MONITOR_MANAGER == null)
            return 5120;

        int maxRight = 0;
        for (MonitorManager.VirtualMonitor vm : MomosApp.MONITOR_MANAGER.getVirtualMonitors())
            maxRight = Math.max(maxRight, vm.relX() + vm.width());
        return maxRight > 0 ? maxRight : 5120;
    }

    public void start()
    {
        Thread.ofPlatform().daemon(true).name("wallpaper-manager").start(() ->
        {
            Random rng = new Random();
            while (!Thread.currentThread().isInterrupted())
            {
                List<File> images = getImages();
                if (!images.isEmpty())
                    setWallpaper(images.get(rng.nextInt(images.size())));

                try { Thread.sleep(intervalSeconds * 1000L); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
    }

    private List<File> getImages()
    {
        List<File> images = new ArrayList<>();
        File dir = new File(wallpaperDir);
        if (!dir.isDirectory()) return images;

        File[] files = dir.listFiles(f ->
        {
            String n = f.getName().toLowerCase();
            return f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"));
        });
        if (files != null)
            images.addAll(List.of(files));
        return images;
    }

    private void setWallpaper(File img)
    {
        String mode = (getImageWidth(img) >= totalDisplayWidth) ? "--bg-fill" : "--bg-tile";
        CommandHelper.execute("feh", "--no-fehbg", mode, img.getAbsolutePath());
    }

    private int getImageWidth(File img)
    {
        try (ImageInputStream stream = ImageIO.createImageInputStream(img))
        {
            var readers = ImageIO.getImageReaders(stream);
            if (readers.hasNext())
            {
                ImageReader reader = readers.next();
                try
                {
                    reader.setInput(stream);
                    return reader.getWidth(0);
                }
                finally { reader.dispose(); }
            }
        }
        catch (Exception ignored) {}
        return 0;
    }

    private JsonObject getConfig()
    {
        JsonElement el = MomosApp.getConfigSetting("wallpaper");
        return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
    }
}

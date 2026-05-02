package com.momosoftworks.momos.util.image;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class ImageHelper
{
    public static String getIconPath(String icon)
    {
        if (icon == null || icon.isBlank()) return null;
        File file = new File(icon);
        if (file.exists()) return file.getAbsolutePath();

        String[] paths = {
                "/usr/share/icons/",
                "/usr/share/icons/hicolor/scalable/",
                "/usr/share/icons/hicolor/scalable/apps/",
                "/usr/share/icons/hicolor/256x256/apps/",
                "/usr/share/icons/hicolor/256x256/",
                "/usr/share/icons/hicolor/128x128/apps/",
                "/usr/share/icons/hicolor/128x128/",
                "/usr/share/icons/hicolor/64x64/apps/",
                "/usr/share/icons/hicolor/64x64/",
                "/usr/share/icons/hicolor/48x48/apps/",
                "/usr/share/icons/hicolor/48x48/",
                "/usr/share/icons/hicolor/32x32/apps/",
                "/usr/share/icons/hicolor/32x32/",
                "/usr/share/icons/hicolor/16x16/apps/",
                "/usr/share/icons/hicolor/16x16/",
                "/usr/share/pixmaps/",
                System.getenv("HOME") + "/.local/share/icons/hicolor/48x48/apps/",
                System.getenv("HOME") + "/.local/share/icons/hicolor/48x48/",
                System.getenv("HOME") + "/.local/share/icons/hicolor/scalable/apps/",
                System.getenv("HOME") + "/.local/share/icons/hicolor/scalable/",
        };
        for (String path : paths)
        {
            for (String ext : new String[]{ ".svg", ".png", ".xpm", "" })
            {
                File f = new File(path + icon + ext);
                if (f.exists()) return f.getAbsolutePath();
            }
        }
        return null;
    }

    public static Image getIcon(String path, int size)
    {
        if (path.endsWith(".svg"))
        {
            try
            {
                SVGDocument doc = new SVGLoader().load(new File(path).toURI().toURL());
                if (doc == null) return null;
                BufferedImage buf = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                var g2 = buf.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                doc.render(null, g2, new ViewBox(size, size));
                g2.dispose();
                return SwingFXUtils.toFXImage(buf, null);
            }
            catch (Exception e) { return null; }
        }
        return new Image(new File(path).toURI().toString(), size, size, true, true);
    }
}

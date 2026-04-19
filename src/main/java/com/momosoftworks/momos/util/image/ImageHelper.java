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

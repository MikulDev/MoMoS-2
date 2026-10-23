package com.momosoftworks.momos.util.image;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern SVG_VIEWBOX_PATTERN = Pattern.compile("viewBox=\"([^\"]+)\"");
    private static final Pattern SVG_PATH_PATTERN = Pattern.compile("<path\\b([^>]*)>");
    private static final Pattern SVG_ATTRIBUTE_PATTERN = Pattern.compile("([\\w-]+)=\"([^\"]*)\"");

    /**
     * Builds a vector icon from {@code icons/<icon>.svg}, scaled so its view box is {@code size} pixels square.<br>
     * Only {@code <path>} elements are drawn, along with their {@code fill-opacity} and {@code fill-rule};
     * the file's own colours are replaced by {@code color}.
     */
    public static Group loadSVG(String icon, double size, Color color)
    {
        String resourcePath = "icons/" + icon + ".svg";
        ParsedSvg parsed = PARSED_SVGS.computeIfAbsent(resourcePath, ImageHelper::parseSvg);
        if (parsed == null) return new Group();

        Group group = new Group();
        for (SvgShape shape : parsed.shapes())
        {
            SVGPath svgPath = new SVGPath();
            svgPath.setContent(shape.path());
            svgPath.setFillRule(shape.fillRule());
            svgPath.setFill(color);
            svgPath.setOpacity(shape.opacity());
            svgPath.setSmooth(true);
            group.getChildren().add(svgPath);
        }
        group.getTransforms().addAll(new Scale(size / parsed.width(), size / parsed.height()),
                                     new Translate(-parsed.minX(), -parsed.minY()));
        return group;
    }

    /** Icons are parsed once, then shared by every {@link SVGPath} built from them */
    private static final Map<String, ParsedSvg> PARSED_SVGS = new ConcurrentHashMap<>();

    private record ParsedSvg(List<SvgShape> shapes, double minX, double minY, double width, double height) {}

    private record SvgShape(String path, double opacity, FillRule fillRule) {}

    /**
     * @return null if the resource is missing or unreadable
     */
    private static ParsedSvg parseSvg(String resourcePath)
    {
        String svgText;
        try (InputStream in = ImageHelper.class.getClassLoader().getResourceAsStream(resourcePath))
        {
            if (in == null)
            {   System.err.println("Icon not found: " + resourcePath);
                return null;
            }
            svgText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {   System.err.println("Failed to read icon " + resourcePath + ": " + e.getMessage());
            return null;
        }

        double minX = 0, minY = 0, width = 24, height = 24;
        Matcher viewBoxMatcher = SVG_VIEWBOX_PATTERN.matcher(svgText);
        if (viewBoxMatcher.find())
        {
            String[] parts = viewBoxMatcher.group(1).trim().split("[\\s,]+");
            minX = Double.parseDouble(parts[0]);
            minY = Double.parseDouble(parts[1]);
            width = Double.parseDouble(parts[2]);
            height = Double.parseDouble(parts[3]);
        }

        List<SvgShape> shapes = new ArrayList<>();
        Matcher pathMatcher = SVG_PATH_PATTERN.matcher(svgText);
        while (pathMatcher.find())
        {
            Map<String, String> attributes = new HashMap<>();
            Matcher attributeMatcher = SVG_ATTRIBUTE_PATTERN.matcher(pathMatcher.group(1));
            while (attributeMatcher.find())
            {   attributes.put(attributeMatcher.group(1), attributeMatcher.group(2));
            }
            String d = attributes.get("d");
            if (d == null || d.isBlank()) continue;

            double opacity = Double.parseDouble(attributes.getOrDefault("fill-opacity", "1"));
            FillRule fillRule = "evenodd".equals(attributes.get("fill-rule")) ? FillRule.EVEN_ODD : FillRule.NON_ZERO;
            shapes.add(new SvgShape(d, opacity, fillRule));
        }

        return new ParsedSvg(shapes, minX, minY, width, height);
    }
}

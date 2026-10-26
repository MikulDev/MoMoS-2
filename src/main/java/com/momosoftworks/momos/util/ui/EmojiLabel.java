package com.momosoftworks.momos.util.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.css.CssMetaData;
import javafx.css.FontCssMetaData;
import javafx.css.SimpleStyleableObjectProperty;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A label that shows emoji in colour, by drawing them as images from {@link EmojiFont}.
 * <p>
 * Styled like a {@link javafx.scene.control.Label}, with {@code -fx-font} (and its parts, like {@code -fx-font-size})
 * and {@code -fx-text-fill}. Text that doesn't fit is cut off with an ellipsis: after one line, or with
 * {@link #setWrapText wrapping} on, once it's {@link #setMaxTextHeight as tall as allowed}.
 */
public class EmojiLabel extends Region
{
    private static final String ELLIPSIS = "…";
    // Emoji are drawn a little taller than the text, and sit lower, dipping below the baseline like a "g" does
    private static final double EMOJI_SCALE = 1.2;
    private static final double EMOJI_DROP = 0.2;

    private static final FontCssMetaData<EmojiLabel> FONT = new FontCssMetaData<>("-fx-font", Font.getDefault())
    {
        @Override
        public boolean isSettable(EmojiLabel label)
        {   return !label.font.isBound();
        }

        @Override
        public StyleableProperty<Font> getStyleableProperty(EmojiLabel label)
        {   return label.font;
        }
    };

    private static final CssMetaData<EmojiLabel, Paint> TEXT_FILL = new CssMetaData<>("-fx-text-fill", PaintConverter.getInstance(), Color.BLACK)
    {
        @Override
        public boolean isSettable(EmojiLabel label)
        {   return !label.textFill.isBound();
        }

        @Override
        public StyleableProperty<Paint> getStyleableProperty(EmojiLabel label)
        {   return label.textFill;
        }
    };

    private static final List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA;
    static
    {
        List<CssMetaData<? extends Styleable, ?>> metaData = new ArrayList<>(Region.getClassCssMetaData());
        metaData.add(FONT);
        metaData.add(TEXT_FILL);
        CSS_META_DATA = Collections.unmodifiableList(metaData);
    }

    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final StyleableObjectProperty<Font> font = new SimpleStyleableObjectProperty<>(FONT, this, "font", Font.getDefault());
    private final StyleableObjectProperty<Paint> textFill = new SimpleStyleableObjectProperty<>(TEXT_FILL, this, "textFill", Color.BLACK);
    private boolean wrapText;
    private double maxTextHeight = Double.MAX_VALUE;

    private final TextFlow flow = new TextFlow();
    // The text in pieces that can't be cut: single characters, and whole emoji. Null when the text has changed
    private List<EmojiFont.Segment> units;
    // The width the shown text was last cut to fit. NaN when it needs cutting again
    private double fittedWidth = Double.NaN;
    private double fullWidth = Double.NaN;
    private double lineHeight = Double.NaN;

    public EmojiLabel()
    {   this("");
    }

    public EmojiLabel(String text)
    {
        this.getStyleClass().add("emoji-label");
        this.getChildren().add(flow);
        // A single line is laid out at full length, then cut off at the label's edge
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(this.widthProperty());
        clip.heightProperty().bind(this.heightProperty());
        this.setClip(clip);

        this.text.addListener(obs -> this.textChanged());
        this.font.addListener(obs -> this.invalidateFit());
        this.textFill.addListener(obs ->
        {   for (Node node : flow.getChildren())
            {   if (node instanceof Text t) t.setFill(this.getTextFill());
            }
        });
        this.setText(text);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData()
    {   return CSS_META_DATA;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData()
    {   return getClassCssMetaData();
    }

    public StringProperty textProperty()
    {   return text;
    }
    public String getText()
    {   return text.get();
    }
    public void setText(String text)
    {   this.text.set(text == null ? "" : text);
    }

    public Font getFont()
    {   return font.get();
    }
    public Paint getTextFill()
    {   return textFill.get();
    }

    /** Whether text runs onto more lines when it's too long, rather than being cut off after the first */
    public void setWrapText(boolean wrapText)
    {   this.wrapText = wrapText;
        this.textChanged();
    }

    /** How tall wrapped text can get before the rest is cut off */
    public void setMaxTextHeight(double maxTextHeight)
    {   this.maxTextHeight = maxTextHeight;
        this.invalidateFit();
    }

    private void textChanged()
    {   units = null;
        this.invalidateFit();
    }

    private void invalidateFit()
    {   fittedWidth = fullWidth = lineHeight = Double.NaN;
        this.requestLayout();
    }

    /* Building the text */

    private List<EmojiFont.Segment> units()
    {
        if (units == null)
        {
            // A single line can't show line breaks, so they become spaces
            String shown = wrapText ? this.getText() : this.getText().strip().replaceAll("\\s*\\R\\s*", " ");
            units = new ArrayList<>();
            for (EmojiFont.Segment segment : EmojiFont.get().split(shown))
            {
                if (segment.isEmoji()) units.add(segment);
                else segment.text().codePoints().forEach(c -> units.add(new EmojiFont.Segment(Character.toString(c), -1)));
            }
        }
        return units;
    }

    /** Text and emoji nodes for {@code units}, with an ellipsis after them if {@code cutOff} */
    private List<Node> createNodes(List<EmojiFont.Segment> units, boolean cutOff)
    {
        List<Node> nodes = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        for (EmojiFont.Segment unit : units)
        {
            Node emoji = unit.isEmoji() ? this.createEmoji(unit.glyph()) : null;
            if (emoji == null)
            {   run.append(unit.text());
                continue;
            }
            this.addText(nodes, run);
            nodes.add(emoji);
        }
        if (cutOff)
        {   run.setLength(run.toString().stripTrailing().length());
            run.append(ELLIPSIS);
        }
        this.addText(nodes, run);
        return nodes;
    }

    private void addText(List<Node> nodes, StringBuilder run)
    {
        if (run.isEmpty()) return;
        Text text = new Text(run.toString());
        text.setFont(this.getFont());
        text.setFill(this.getTextFill());
        nodes.add(text);
        run.setLength(0);
    }

    /** Null if the font has no image for it, in which case it's left as text */
    private Node createEmoji(int glyph)
    {
        double size = this.getFont().getSize() * EMOJI_SCALE;
        Image image = EmojiFont.get().image(glyph, (int) Math.ceil(size * this.getOutputScale()));
        if (image == null) return null;
        ImageView view = new ImageView(image);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setTranslateY(this.getFont().getSize() * EMOJI_DROP);
        return view;
    }

    private double getOutputScale()
    {
        if (this.getScene() != null && this.getScene().getWindow() != null) return this.getScene().getWindow().getOutputScaleY();
        return Screen.getPrimary().getOutputScaleY();
    }

    /* Fitting the text */

    /** Fills the flow with as much of the text as fits in {@code width}, cut off with an ellipsis if it all doesn't */
    private void fit(double width)
    {
        List<EmojiFont.Segment> all = this.units();
        List<Node> nodes = this.createNodes(all, false);
        if (!this.fits(nodes, width))
        {
            // The most pieces that still fit alongside the ellipsis
            nodes = this.createNodes(List.of(), true);
            int low = 0, high = all.size() - 1;
            while (low < high)
            {
                int mid = (low + high + 1) / 2;
                List<Node> candidate = this.createNodes(all.subList(0, mid), true);
                if (this.fits(candidate, width))
                {   low = mid;
                    nodes = candidate;
                }
                else high = mid - 1;
            }
        }
        flow.getChildren().setAll(nodes);
        fittedWidth = width;
    }

    private boolean fits(List<Node> nodes, double width)
    {
        TextFlow probe = new TextFlow();
        probe.getChildren().setAll(nodes);
        return wrapText ? probe.prefHeight(width) <= maxTextHeight : probe.prefWidth(-1) <= width;
    }

    private void ensureFitted(double width)
    {
        if (width != fittedWidth) this.fit(width);
    }

    /** The whole text laid out on one line, measured once per change */
    private void measure()
    {
        if (!Double.isNaN(fullWidth)) return;
        TextFlow probe = new TextFlow();
        probe.getChildren().setAll(this.createNodes(this.units(), false));
        fullWidth = probe.prefWidth(-1);
        lineHeight = probe.prefHeight(-1);
    }

    private double contentWidth(double width)
    {   Insets insets = this.getInsets();
        return Math.max(0, width - insets.getLeft() - insets.getRight());
    }

    @Override
    public Orientation getContentBias()
    {   return wrapText ? Orientation.HORIZONTAL : null;
    }

    @Override
    protected double computePrefWidth(double height)
    {   this.measure();
        return this.getInsets().getLeft() + fullWidth + this.getInsets().getRight();
    }

    @Override
    protected double computePrefHeight(double width)
    {
        Insets insets = this.getInsets();
        if (wrapText && width >= 0)
        {   this.ensureFitted(this.contentWidth(width));
            return insets.getTop() + flow.prefHeight(this.contentWidth(width)) + insets.getBottom();
        }
        this.measure();
        return insets.getTop() + lineHeight + insets.getBottom();
    }

    @Override
    protected double computeMinHeight(double width)
    {   return this.computePrefHeight(width);
    }

    @Override
    public double getBaselineOffset()
    {   return this.getInsets().getTop() + flow.getBaselineOffset();
    }

    @Override
    protected void layoutChildren()
    {
        Insets insets = this.getInsets();
        double width = this.contentWidth(this.getWidth());
        this.ensureFitted(width);
        // Kept to a single line even if the label is squeezed below what the ellipsis alone needs
        double flowWidth = wrapText ? width : Math.max(width, flow.prefWidth(-1));
        flow.resizeRelocate(insets.getLeft(), insets.getTop(), flowWidth, flow.prefHeight(flowWidth));
    }
}

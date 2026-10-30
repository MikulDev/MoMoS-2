package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.ui.EmojiLabel;
import com.momosoftworks.momos.util.ui.Icon;
import com.momosoftworks.momos.widget.IconButton;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One notification in the history: its icon, title and first line, with the app it came from and when.
 * Click to go to the app, right-click or use the close button (shown on hover, over the icon) to remove it.
 */
public class NotificationEntry extends HBox
{
    private static final int ICON_SIZE = 32;
    // Loaded larger than shown, so the preview can reuse it
    private static final int ICON_RESOLUTION = 48;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

    public final NotificationRecord record;
    private final ImageView icon;
    private final Label time;

    /**
     * @param onOpen   called when the entry is clicked
     * @param onRemove called when the entry is right-clicked or closed
     * @param onHover  called when the mouse moves on or off the entry
     */
    public NotificationEntry(NotificationRecord record, Consumer<NotificationRecord> onOpen, Consumer<NotificationRecord> onRemove,
                             BiConsumer<NotificationRecord, Boolean> onHover)
    {
        this.record = record;
        this.setSpacing(10);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("notification-entry");

        this.icon = new ImageView();
        icon.setFitWidth(ICON_SIZE);
        icon.setFitHeight(ICON_SIZE);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        icon.visibleProperty().bind(this.hoverProperty().not());
        Thread.ofVirtual().start(() ->
        {   Image image = record.loadIcon(ICON_RESOLUTION);
            Platform.runLater(() -> icon.setImage(image));
        });

        IconButton closeButton = new IconButton(Icon.CLOSE.sized(12));
        closeButton.getStyleClass().add("notification-entry-close");
        closeButton.setOnAction(e -> onRemove.accept(record));
        // Otherwise the click carries on to the entry and opens the app
        closeButton.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        closeButton.visibleProperty().bind(this.hoverProperty());

        // Stands in for apps without an icon, keeping every entry's text lined up
        Group placeholder = Icon.NOTIFICATION.sized(18).get();
        placeholder.getStyleClass().add("notification-entry-placeholder");
        placeholder.visibleProperty().bind(icon.imageProperty().isNull());

        StackPane iconBox = new StackPane(placeholder, icon, closeButton);
        iconBox.setMinSize(ICON_SIZE, ICON_SIZE);
        iconBox.setPrefSize(ICON_SIZE, ICON_SIZE);
        iconBox.setMaxSize(ICON_SIZE, ICON_SIZE);

        EmojiLabel title = new EmojiLabel(record.title());
        title.getStyleClass().add("notification-entry-title");
        Label source = new Label(record.source());
        source.getStyleClass().add("notification-entry-source");

        // The whole message is in the preview, so the entry only needs the start of it
        EmojiLabel body = new EmojiLabel(record.text());
        body.getStyleClass().add("notification-entry-body");
        this.time = new Label();
        time.getStyleClass().add("notification-entry-time");
        this.refreshTime();

        VBox text = new VBox(2, row(title, source), row(body, time));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        this.getChildren().addAll(iconBox, text);

        this.hoverProperty().addListener((obs, wasHovered, hovered) -> onHover.accept(record, hovered));
        this.setOnMouseClicked(e ->
        {
            if (e.getButton() == MouseButton.PRIMARY) onOpen.accept(record);
            else if (e.getButton() == MouseButton.SECONDARY) onRemove.accept(record);
        });
    }

    /** A line with {@code main} on the left, cut short if needed so {@code side} always fits on the right */
    private static HBox row(EmojiLabel main, Label side)
    {
        HBox.setHgrow(main, Priority.ALWAYS);
        side.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(8, main, side);
        row.setAlignment(Pos.BASELINE_LEFT);
        return row;
    }

    /** The icon, once it's loaded. Null until then, or if there isn't one */
    public Image icon()
    {   return icon.getImage();
    }

    /** Shows the time for notifications from today, and the date for older ones */
    public void refreshTime()
    {
        LocalDateTime received = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault());
        time.setText(received.toLocalDate().equals(LocalDate.now()) ? received.format(TIME_FORMAT) : received.format(DATE_FORMAT));
    }
}

package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.util.ui.EmojiLabel;
import com.momosoftworks.momos.util.ui.Icon;
import com.momosoftworks.momos.util.ui.SmoothScroller;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.util.x11.X11ClickWatcher;
import com.momosoftworks.momos.util.x11.X11Key;
import com.momosoftworks.momos.util.x11.X11KeyGrabber;
import com.momosoftworks.momos.widget.IconButton;
import com.momosoftworks.momos.widget.PopupWidget;
import com.momosoftworks.momos.widget.bar.BarWidget;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists every notification received, newest first, kept across restarts. Hovering one shows it in full
 * beside the list. Clicking one goes to the app it came from; right-clicking removes it.
 * <p>
 * Opened from the {@link NotificationsButton} in the bar, which also shows how many there are.
 * Like the calendar, the list closes on Esc, a click outside it, or another click on the button.
 */
public class NotificationsWidget extends PopupWidget
{
    private static final int LIST_WIDTH = 420;
    private static final int LIST_MAX_HEIGHT = 400;
    private static final int PREVIEW_WIDTH = 320;
    private static final int PREVIEW_ICON_SIZE = 48;
    private static final int SCREEN_MARGIN = 10;
    private static final DateTimeFormatter PREVIEW_TIME_FORMAT = DateTimeFormatter.ofPattern("MMM d, h:mm a");

    private final NotificationHistory history = new NotificationHistory(NotificationConfig.load().maxHistory());
    // Entries are kept for as long as their notification is in the history, so a change doesn't rebuild the whole list
    private final Map<NotificationRecord, NotificationEntry> entryNodes = new IdentityHashMap<>();
    private NotificationRecord hovered;
    private boolean previewQueued;

    private Label title;
    private IconButton clearButton;
    private VBox listPane;
    private VBox entries;
    private ScrollPane entriesPane;
    private SmoothScroller scroller;

    private HBox preview;
    private ImageView previewIcon;
    private EmojiLabel previewTitle;
    private Label previewSource;
    private EmojiLabel previewText;

    // Momos windows are unmanaged by BSPWM (manage=off), so they never receive X11 input focus and JavaFX key
    // events never fire. Grab the keyboard at the X11 level so Esc can close the list
    private final X11KeyGrabber grabber = new X11KeyGrabber()
        .interrupt(X11Key.SUPER_L, X11Key.SUPER_R)
        .key(X11Key.ESCAPE, () -> Platform.runLater(this::hide));
    // For the same reason, the window can't tell when focus moves elsewhere. Watch for clicks instead
    private final X11ClickWatcher clickWatcher = new X11ClickWatcher((x, y) -> Platform.runLater(() -> onScreenPress(x, y)));

    public NotificationsWidget()
    {   super(Identifier.of("momos", "notifications"));
        this.addCommand("clear", () -> Platform.runLater(history::clear));
    }

    @Override
    public Stage initStage(Stage stage)
    {
        this.title = new Label();
        title.getStyleClass().add("notifications-title");

        this.clearButton = new IconButton(Icon.CLEAR.sized(24));
        clearButton.setOnAction(e ->
        {   history.clear();
            this.hide();
        });

        // The title stays centered across the whole list, rather than in the space beside the button
        StackPane header = new StackPane(title, clearButton);
        StackPane.setAlignment(clearButton, Pos.CENTER_LEFT);

        this.entries = new VBox(6);
        entries.getStyleClass().add("notifications-list");

        this.entriesPane = new ScrollPane(entries);
        entriesPane.setFitToWidth(true);
        entriesPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        entriesPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Grows with the list until it's this tall, then scrolls
        entriesPane.setMaxHeight(LIST_MAX_HEIGHT);
        this.scroller = SmoothScroller.of(entriesPane);

        this.listPane = new VBox(10, header, entriesPane);

        HBox root = new HBox(12, listPane, this.createPreview());
        root.getStyleClass().add("notifications");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        loadStylesheet("notifications", scene);

        stage.setScene(scene);
        // picom matches on this to animate the popup
        stage.setTitle("momos-notifications");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);

        history.records().addListener((ListChangeListener<NotificationRecord>) change -> this.refreshEntries());
        this.refreshEntries();
        return stage;
    }

    public NotificationHistory history()
    {   return history;
    }

    /* List */

    /** Brings the entries in line with the history, reusing the ones that are still in it */
    private void refreshEntries()
    {
        Map<NotificationRecord, NotificationEntry> kept = new IdentityHashMap<>();
        List<NotificationEntry> list = new ArrayList<>();
        for (NotificationRecord record : history.records())
        {
            NotificationEntry entry = entryNodes.get(record);
            if (entry == null) entry = new NotificationEntry(record, this::open, this::remove, this::onEntryHover);
            kept.put(record, entry);
            list.add(entry);
        }
        entryNodes.clear();
        entryNodes.putAll(kept);
        entries.getChildren().setAll(list);

        // Its entry is gone, so it'll never report the mouse leaving
        if (hovered != null && !entryNodes.containsKey(hovered))
        {   this.onEntryHover(hovered, false);
        }
        this.refreshView();
    }

    private void refreshView()
    {
        boolean empty = history.records().isEmpty();
        title.setText(empty ? "No notifications" : "Notifications");
        clearButton.setVisible(!empty);
        entriesPane.setVisible(!empty);
        entriesPane.setManaged(!empty);
        // Shrinks to fit the message when there's nothing to list
        listPane.setPrefWidth(empty ? Region.USE_COMPUTED_SIZE : LIST_WIDTH);
        this.resize();
    }

    private void open(NotificationRecord record)
    {
        this.hide();
        Thread.ofVirtual().start(() -> AppLocator.focus(record));
    }

    private void remove(NotificationRecord record)
    {
        history.remove(record);
        if (history.records().isEmpty()) this.hide();
    }

    /* Preview */

    private HBox createPreview()
    {
        Region separator = new Region();
        separator.getStyleClass().add("notification-preview-separator");
        HBox.setMargin(separator, new Insets(15, 0, 15, 0));

        this.previewIcon = new ImageView();
        previewIcon.setFitWidth(PREVIEW_ICON_SIZE);
        previewIcon.setFitHeight(PREVIEW_ICON_SIZE);
        previewIcon.setPreserveRatio(true);
        previewIcon.setSmooth(true);

        this.previewTitle = new EmojiLabel();
        previewTitle.getStyleClass().add("notification-preview-title");
        previewTitle.setWrapText(true);
        this.previewSource = new Label();
        previewSource.getStyleClass().add("notification-preview-source");

        VBox heading = new VBox(2, previewTitle, previewSource);
        heading.setMinWidth(0);
        HBox header = new HBox(10, previewIcon, heading);
        header.setAlignment(Pos.CENTER_LEFT);

        this.previewText = new EmojiLabel();
        previewText.getStyleClass().add("notification-preview-text");
        previewText.setWrapText(true);
        // Very long messages are cut off rather than stretching the popup down the screen
        previewText.setMaxTextHeight(LIST_MAX_HEIGHT);

        VBox content = new VBox(8, header, previewText);
        content.setMinWidth(PREVIEW_WIDTH);
        content.setPrefWidth(PREVIEW_WIDTH);
        content.setMaxWidth(PREVIEW_WIDTH);
        content.setAlignment(Pos.CENTER_LEFT);

        this.preview = new HBox(10, separator, content);
        preview.setAlignment(Pos.CENTER_LEFT);
        this.showPreview(null);
        return preview;
    }

    private void onEntryHover(NotificationRecord record, boolean isHovered)
    {
        if (isHovered) hovered = record;
        else if (hovered == record) hovered = null;
        else return;
        // Moving between entries leaves one and enters the next in the same pulse. Only show the result,
        // so the popup resizes once rather than shrinking and growing back
        if (!previewQueued)
        {   previewQueued = true;
            Platform.runLater(() ->
            {   previewQueued = false;
                this.showPreview(hovered);
            });
        }
    }

    /** Shows {@code record} in full beside the list, or hides the preview if it's null */
    private void showPreview(NotificationRecord record)
    {
        preview.setVisible(record != null);
        preview.setManaged(record != null);
        if (record != null)
        {
            NotificationEntry entry = entryNodes.get(record);
            previewIcon.setImage(entry != null ? entry.icon() : null);
            previewIcon.setVisible(previewIcon.getImage() != null);
            previewIcon.setManaged(previewIcon.getImage() != null);
            previewTitle.setText(record.title());
            LocalDateTime received = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp()), ZoneId.systemDefault());
            String source = record.source();
            previewSource.setText((source.isBlank() ? "" : source + "  ·  ") + received.format(PREVIEW_TIME_FORMAT));
            previewText.setText(record.text());
            previewText.setVisible(!record.text().isBlank());
            previewText.setManaged(!record.text().isBlank());
        }
        this.resize();
    }

    /** Fits the popup to its contents. It's anchored by its top-left corner, so it grows right and down */
    private void resize()
    {
        if (stage != null && stage.isShowing())
        {   stage.sizeToScene();
        }
    }

    /* Showing */

    /** Toggles the list, positioned under the top-left corner of the window containing {@code anchor}. */
    public void toggle(Node anchor)
    {
        Window bar = anchor.getScene().getWindow();
        Platform.runLater(() ->
        {   if (stage.isShowing()) this.hide();
            else showAt(bar.getX(), bar.getY() + bar.getHeight());
        });
    }

    @Override
    public void show()
    {
        Thread.ofVirtual().start(() ->
        {
            Rectangle2D bounds = Monitors.getBounds("focused");
            Platform.runLater(() -> showAt(bounds.getMinX(), bounds.getMinY() + BarWidget.BAR_HEIGHT));
        });
    }

    private void showAt(double left, double top)
    {
        // Entries say "today" by showing the time, which may have stopped being true since they were made
        entryNodes.values().forEach(NotificationEntry::refreshTime);
        stage.setX(left + SCREEN_MARGIN);
        stage.setY(top + SCREEN_MARGIN);
        // Hiding the window makes JavaFX forget its scene's styles until it's shown again, so it can't
        // be measured until it's showing
        stage.show();
        stage.sizeToScene();
        scroller.jumpTo(0);
        clickWatcher.start();
        grabber.start();
    }

    /** Hides the list when the mouse is pressed outside it. {@code x} and {@code y} are in physical pixels. */
    private void onScreenPress(int x, int y)
    {
        if (!stage.isShowing()) return;
        double scale = Screen.getPrimary().getOutputScaleX();
        double px = x / scale;
        double py = y / scale;
        boolean inList = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()).contains(px, py);
        // The bar buttons toggle the list themselves on release. Hiding it on press too would make them reopen it
        if (!inList && !isOnButton(px, py))
        {   this.hide();
        }
    }

    private static boolean isOnButton(double x, double y)
    {
        return Window.getWindows().stream()
                .filter(window -> window.isShowing() && window.getScene() != null)
                .flatMap(window -> window.getScene().getRoot().lookupAll(".notifications-button").stream())
                .anyMatch(button ->
                {   Bounds bounds = button.localToScreen(button.getBoundsInLocal());
                    return bounds != null && bounds.contains(x, y);
                });
    }

    @Override
    public void hide()
    {
        grabber.stop();
        clickWatcher.stop();
        // May be called from the IPC thread; animations must be touched on the FX thread
        Platform.runLater(() ->
        {   scroller.stop();
            // The mouse leaving with the window won't be reported, so the preview would still be up next time
            hovered = null;
            this.showPreview(null);
            stage.hide();
        });
    }

    @Override
    public void shutdown()
    {
        grabber.stop();
        clickWatcher.stop();
        history.shutdown();
        super.shutdown();
    }
}

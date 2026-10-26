package com.momosoftworks.momos.widget.notification;

import com.momosoftworks.momos.util.ui.EmojiLabel;
import com.momosoftworks.momos.util.ui.Icon;
import com.momosoftworks.momos.widget.IconButton;
import com.momosoftworks.momos.widget.bar.BarWidget;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Shows notifications as they arrive, stacked down the top-right corner of the screen under the bar.
 * Each one closes when its timeout runs out, but hovering it holds it open. Clicking a notification invokes it
 * and right-clicking dismisses it. Must be used from the FX thread.
 */
public class NotificationToasts
{
    private static final int WIDTH = 380;
    private static final int ICON_SIZE = 48;
    private static final int BODY_MAX_HEIGHT = 90;
    private static final int SCREEN_MARGIN = 10;
    private static final int GAP = 8;

    private final List<Toast> toasts = new ArrayList<>();
    private final BiConsumer<Notification, Notification.CloseReason> onClosed;
    private final BiConsumer<Notification, String> onInvoked;

    /**
     * @param onClosed  called once a notification leaves the screen, for any reason
     * @param onInvoked called with the key of the action picked: {@link Notification#DEFAULT_ACTION} when the
     *                  notification itself was clicked, whether or not it offers that action
     */
    public NotificationToasts(BiConsumer<Notification, Notification.CloseReason> onClosed, BiConsumer<Notification, String> onInvoked)
    {   this.onClosed = onClosed;
        this.onInvoked = onInvoked;
    }

    /** Shows {@code notification}, updating it in place if one with its ID is already showing */
    public void show(Notification notification)
    {
        for (Toast toast : toasts)
        {   if (toast.notification.id() == notification.id())
            {   toast.update(notification);
                this.relayout();
                return;
            }
        }
        Toast toast = new Toast(notification);
        toasts.add(toast);
        // picom animates the window as it opens, so it must already be in place when shown
        this.relayout();
        toast.stage.show();
    }

    /** Closes the notification with this ID. Returns false if it wasn't showing */
    public boolean close(int id, Notification.CloseReason reason)
    {
        for (Toast toast : toasts)
        {   if (toast.notification.id() == id)
            {   this.dismiss(toast, reason);
                return true;
            }
        }
        return false;
    }

    private void dismiss(Toast toast, Notification.CloseReason reason)
    {
        // Already gone, e.g. it expired in the same pulse as it was clicked
        if (!toasts.remove(toast)) return;
        toast.timer.stop();
        toast.stage.close();
        this.relayout();
        onClosed.accept(toast.notification, reason);
    }

    private void relayout()
    {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double y = screen.getMinY() + BarWidget.BAR_HEIGHT + SCREEN_MARGIN;
        for (Toast toast : toasts)
        {
            toast.stage.setX(screen.getMaxX() - WIDTH - SCREEN_MARGIN);
            toast.stage.setY(y);
            // Not measured until first shown, but then it's the newest, so nothing sits below it yet
            if (!Double.isNaN(toast.stage.getHeight())) y += toast.stage.getHeight() + GAP;
        }
    }

    private class Toast
    {
        final Stage stage = new Stage();
        final PauseTransition timer = new PauseTransition();
        Notification notification;

        Toast(Notification notification)
        {
            Scene scene = new Scene(new Group());
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().addAll(getClass().getResource("/styles/base.css").toExternalForm(),
                                          getClass().getResource("/styles/widgets/notifications.css").toExternalForm());
            stage.setScene(scene);
            stage.initStyle(StageStyle.TRANSPARENT);
            stage.setAlwaysOnTop(true);
            // picom matches on this to animate it
            stage.setTitle("momos-notification");
            timer.setOnFinished(e -> dismiss(this, Notification.CloseReason.EXPIRED));
            this.update(notification);
        }

        /** Shows {@code notification} in this toast, restarting its timeout */
        void update(Notification notification)
        {
            this.notification = notification;
            stage.getScene().setRoot(this.build());
            stage.sizeToScene();
            timer.stop();
            if (!notification.timeout().isIndefinite())
            {   timer.setDuration(notification.timeout());
                timer.playFromStart();
            }
        }

        private void invoke(String actionKey)
        {
            onInvoked.accept(notification, actionKey);
            dismiss(this, Notification.CloseReason.DISMISSED);
        }

        private Parent build()
        {
            NotificationRecord record = notification.record();

            EmojiLabel title = new EmojiLabel(record.title());
            title.getStyleClass().add("notification-toast-title");
            HBox.setHgrow(title, Priority.ALWAYS);

            Label source = new Label(record.source());
            source.getStyleClass().add("notification-toast-source");
            source.setMinWidth(Region.USE_PREF_SIZE);

            IconButton closeButton = new IconButton(Icon.CLOSE.sized(10));
            closeButton.getStyleClass().add("notification-toast-close");
            closeButton.setOnAction(e -> dismiss(this, Notification.CloseReason.DISMISSED));
            // Otherwise the click carries on to the toast and invokes it
            closeButton.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);

            HBox header = new HBox(8, title, source, closeButton);
            header.setAlignment(Pos.CENTER_LEFT);

            VBox text = new VBox(4, header);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);

            if (!record.text().isBlank())
            {
                EmojiLabel body = new EmojiLabel(record.text());
                body.getStyleClass().add("notification-toast-body");
                body.setWrapText(true);
                // Long messages are cut off with an ellipsis; the full text is in the history
                body.setMaxTextHeight(BODY_MAX_HEIGHT);
                text.getChildren().add(body);
            }

            HBox actions = new HBox(6);
            for (Notification.Action action : notification.actions())
            {
                // Clicking the notification itself is how the default action is picked
                if (action.key().equals(Notification.DEFAULT_ACTION)) continue;
                Button button = new Button(action.label());
                button.getStyleClass().add("notification-toast-action");
                button.setOnAction(e -> this.invoke(action.key()));
                button.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
                actions.getChildren().add(button);
            }
            if (!actions.getChildren().isEmpty())
            {   text.getChildren().add(actions);
            }

            HBox root = new HBox(14);
            root.getStyleClass().add("notification-toast");
            root.setAlignment(Pos.CENTER_LEFT);
            root.setPrefWidth(WIDTH);
            root.setMaxWidth(WIDTH);
            if (notification.icon() != null)
            {
                ImageView icon = new ImageView(notification.icon());
                icon.setFitWidth(ICON_SIZE);
                icon.setFitHeight(ICON_SIZE);
                icon.setPreserveRatio(true);
                icon.setSmooth(true);
                root.getChildren().add(icon);
            }
            root.getChildren().add(text);

            closeButton.visibleProperty().bind(root.hoverProperty());
            root.hoverProperty().addListener((obs, wasHovered, hovered) ->
            {
                if (hovered) timer.pause();
                else if (timer.getStatus() == Animation.Status.PAUSED) timer.play();
            });
            root.setOnMouseClicked(e ->
            {
                if (e.getButton() == MouseButton.PRIMARY) this.invoke(Notification.DEFAULT_ACTION);
                else if (e.getButton() == MouseButton.SECONDARY) dismiss(this, Notification.CloseReason.DISMISSED);
            });
            return root;
        }
    }
}

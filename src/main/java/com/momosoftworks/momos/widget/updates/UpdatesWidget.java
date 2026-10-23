package com.momosoftworks.momos.widget.updates;

import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.util.ui.Icon;
import com.momosoftworks.momos.util.ui.SmoothScroller;
import com.momosoftworks.momos.util.wm.CommandHelper;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.util.x11.X11ClickWatcher;
import com.momosoftworks.momos.util.x11.X11Key;
import com.momosoftworks.momos.util.x11.X11KeyGrabber;
import com.momosoftworks.momos.widget.IconButton;
import com.momosoftworks.momos.widget.PopupWidget;
import com.momosoftworks.momos.widget.bar.BarWidget;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lists the package updates available through pacman and the AUR, and installs them, either one at a
 * time or as a full system update. Updates run in a terminal so the user can enter their password.
 * <p>
 * Opened from the {@link UpdatesButton} in the bar, which also shows how many updates there are.
 * Like the calendar, the list closes on Esc, a click outside it, or another click on the button.
 */
public class UpdatesWidget extends PopupWidget
{
    private static final Duration CHECK_INTERVAL = Duration.minutes(30);
    private static final int POPUP_WIDTH = 440;
    private static final int LIST_MAX_HEIGHT = 420;
    private static final int SCREEN_MARGIN = 10;
    private static final DateTimeFormatter LOG_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReadOnlyIntegerWrapper count = new ReadOnlyIntegerWrapper(0);
    private final List<PackageUpdate> packages = new ArrayList<>();
    // Packages waiting for or undergoing an update, by name. Kept apart from the entries so a refresh doesn't lose them
    private final Map<String, PackageEntry.Status> statuses = new HashMap<>();
    private boolean systemUpdating = false;
    private boolean checking = false;

    // pacman locks its database while it works, so updates must run one at a time
    private final ExecutorService updateQueue = Executors.newSingleThreadExecutor(r ->
    {   Thread thread = new Thread(r, "package-updates");
        thread.setDaemon(true);
        return thread;
    });
    private Timeline checkTimer;

    private Label countLabel;
    private IconButton updateAllButton;
    private IconButton refreshButton;
    private VBox entries;
    private ScrollPane entriesPane;
    private SmoothScroller scroller;

    // Momos windows are unmanaged by BSPWM (manage=off), so they never receive X11 input focus and JavaFX key
    // events never fire. Grab the keyboard at the X11 level so Esc can close the list
    private final X11KeyGrabber grabber = new X11KeyGrabber()
        .interrupt(X11Key.SUPER_L, X11Key.SUPER_R)
        .key(X11Key.ESCAPE, () -> Platform.runLater(this::hide));
    // For the same reason, the window can't tell when focus moves elsewhere. Watch for clicks instead
    private final X11ClickWatcher clickWatcher = new X11ClickWatcher((x, y) -> Platform.runLater(() -> onScreenPress(x, y)));

    public UpdatesWidget()
    {   super(Identifier.of("momos", "updates"));
        this.addCommand("refresh", () -> Platform.runLater(this::checkForUpdates));
    }

    @Override
    public Stage initStage(Stage stage)
    {
        this.countLabel = new Label();
        countLabel.getStyleClass().add("updates-count");

        this.updateAllButton = new IconButton(Icon.UPDATE_ALL.sized(20));
        updateAllButton.setOnAction(e -> this.updateAll());
        this.refreshButton = new IconButton(Icon.REFRESH.sized(20));
        refreshButton.setOnAction(e -> this.checkForUpdates());

        BorderPane header = new BorderPane(countLabel, null, refreshButton, null, updateAllButton);
        header.getStyleClass().add("updates-header");
        BorderPane.setAlignment(updateAllButton, Pos.CENTER_LEFT);
        BorderPane.setAlignment(refreshButton, Pos.CENTER_RIGHT);

        this.entries = new VBox(5);
        entries.getStyleClass().add("updates-list");

        this.entriesPane = new ScrollPane(entries);
        entriesPane.setFitToWidth(true);
        entriesPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        entriesPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        // Grows with the list until it's this tall, then scrolls
        entriesPane.setMaxHeight(LIST_MAX_HEIGHT);
        this.scroller = SmoothScroller.of(entriesPane);

        VBox root = new VBox(10, header, entriesPane);
        root.getStyleClass().add("updates");
        root.setPrefWidth(POPUP_WIDTH);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        loadStylesheet("updates", scene);

        stage.setScene(scene);
        // picom matches on this to animate the popup
        stage.setTitle("momos-updates");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);

        this.refreshView();
        this.checkForUpdates();
        this.checkTimer = new Timeline(new KeyFrame(CHECK_INTERVAL, e ->
        {   // The system update checks again once it's done
            if (!systemUpdating) this.checkForUpdates();
        }));
        checkTimer.setCycleCount(Animation.INDEFINITE);
        checkTimer.play();
        return stage;
    }

    /** The number of available updates. Only changes on the FX thread. */
    public ReadOnlyIntegerProperty countProperty()
    {   return count.getReadOnlyProperty();
    }

    /* Checking */

    private void checkForUpdates()
    {
        if (checking) return;
        this.checking = true;
        this.refreshView();
        Thread.ofVirtual().start(() ->
        {
            List<PackageUpdate> found = PackageManager.checkUpdates();
            Platform.runLater(() ->
            {   this.checking = false;
                // Keep showing the last known updates if the check failed
                if (found != null) this.setPackages(found);
                else this.refreshView();
            });
        });
    }

    private void setPackages(List<PackageUpdate> found)
    {
        packages.clear();
        packages.addAll(found);
        List<PackageEntry> created = new ArrayList<>();
        for (PackageUpdate pkg : packages)
        {   PackageEntry entry = new PackageEntry(pkg, this::updatePackage);
            entry.setStatus(statuses.get(pkg.name()));
            created.add(entry);
        }
        entries.getChildren().setAll(created);
        this.refreshView();
    }

    private void removePackage(String name)
    {
        packages.removeIf(pkg -> pkg.name().equals(name));
        entries.getChildren().removeIf(node -> ((PackageEntry) node).pkg.name().equals(name));
        this.refreshView();
    }

    /* Updating */

    private void updatePackage(PackageUpdate pkg)
    {
        if (systemUpdating || statuses.containsKey(pkg.name())) return;
        this.setStatus(pkg.name(), PackageEntry.Status.QUEUED);
        updateQueue.submit(() ->
        {
            Platform.runLater(() -> this.setStatus(pkg.name(), PackageEntry.Status.UPDATING));
            // -y because checkupdates reads its own copy of the databases, so the system's may not know about this
            // version yet. --needed skips packages a system update has already taken care of
            int exitCode = PackageManager.runInTerminal("yay", "-Sy", "--needed", "--noconfirm", pkg.name());
            Platform.runLater(() ->
            {   this.setStatus(pkg.name(), null);
                if (exitCode == 0) this.removePackage(pkg.name());
                else this.reportFailure(pkg.name(), exitCode);
            });
        });
    }

    private void updateAll()
    {
        if (systemUpdating || !statuses.isEmpty() || packages.isEmpty()) return;
        this.systemUpdating = true;
        this.refreshView();
        this.hide();
        updateQueue.submit(() ->
        {
            int exitCode = PackageManager.runInTerminal("yay", "-Syu", "--noconfirm");
            Platform.runLater(() ->
            {   this.systemUpdating = false;
                if (exitCode == 0) this.setPackages(List.of());
                else this.reportFailure(null, exitCode);
                // Pick up anything that was skipped, failed, or released in the meantime
                this.checkForUpdates();
            });
        });
    }

    private void setStatus(String name, PackageEntry.Status status)
    {
        if (status == null) statuses.remove(name);
        else statuses.put(name, status);
        for (Node node : entries.getChildren())
        {   if (node instanceof PackageEntry entry && entry.pkg.name().equals(name))
            {   entry.setStatus(status);
            }
        }
        this.refreshView();
        this.syncKeyboardGrab();
    }

    /** Logs a failed update and notifies the user. {@code name} is null for a system update. */
    private void reportFailure(String name, int exitCode)
    {
        String target = name != null ? name : "system update";
        String reason = exitCode == -1 ? "terminal closed before the update finished" : "exit code " + exitCode;
        Path log = getErrorLog();
        try
        {   Files.createDirectories(log.getParent());
            Files.writeString(log, String.format("[%s] UPDATE FAILED: %s (%s)%n", LocalDateTime.now().format(LOG_TIME_FORMAT), target, reason),
                              StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
        String title = name != null ? "Update Failed: " + name : "System Update Failed";
        CommandHelper.execute("notify-send", "--urgency=critical", "--expire-time=10000", "--app-name=Updates",
                              title, "Failed with " + reason + ".\nLogged to " + log);
    }

    private static Path getErrorLog()
    {
        String cache = System.getenv("XDG_CACHE_HOME");
        Path cacheDir = cache != null && !cache.isEmpty() ? Path.of(cache) : Path.of(System.getProperty("user.home"), ".cache");
        return cacheDir.resolve("momos").resolve("update_errors.log");
    }

    /* View */

    /** Brings the header, buttons and count in line with the current state. */
    private void refreshView()
    {
        count.set(packages.size());
        countLabel.setText(switch (packages.size())
        {
            case 0 -> checking ? "Checking for updates..." : "No updates available";
            case 1 -> "1 package available";
            default -> packages.size() + " packages available";
        });

        updateAllButton.setVisible(!packages.isEmpty());
        updateAllButton.setDisable(systemUpdating || !statuses.isEmpty());
        refreshButton.setDisable(checking);
        for (Node node : entries.getChildren())
        {   ((PackageEntry) node).setLocked(systemUpdating);
        }

        entriesPane.setVisible(!packages.isEmpty());
        entriesPane.setManaged(!packages.isEmpty());
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
        // Anchored by the top-left corner, so the height can follow the list without the popup moving
        stage.setX(left + SCREEN_MARGIN);
        stage.setY(top + SCREEN_MARGIN);
        // Hiding the window makes JavaFX forget its scene's styles until it's shown again, so it can't
        // be measured until it's showing
        stage.show();
        stage.sizeToScene();
        scroller.jumpTo(0);
        clickWatcher.start();
        this.syncKeyboardGrab();
    }

    /**
     * Holds the keyboard grab while the list is open, except while an update is running: its terminal
     * needs the keyboard for the user's password.
     */
    private void syncKeyboardGrab()
    {
        boolean updateRunning = systemUpdating || statuses.containsValue(PackageEntry.Status.UPDATING);
        if (stage.isShowing() && !updateRunning) grabber.start();
        else grabber.stop();
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
                .flatMap(window -> window.getScene().getRoot().lookupAll(".updates-button").stream())
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
            stage.hide();
        });
    }

    @Override
    public void shutdown()
    {
        if (checkTimer != null) checkTimer.stop();
        grabber.stop();
        clickWatcher.stop();
        updateQueue.shutdownNow();
        super.shutdown();
    }
}

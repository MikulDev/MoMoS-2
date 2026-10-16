package com.momosoftworks.momos.widget.calendar;

import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.util.ui.SmoothScroller;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.util.x11.X11ClickWatcher;
import com.momosoftworks.momos.util.x11.X11Key;
import com.momosoftworks.momos.util.x11.X11KeyGrabber;
import com.momosoftworks.momos.widget.PopupWidget;
import com.momosoftworks.momos.widget.bar.BarWidget;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A vertically scrolling calendar. Months are stacked on top of each other with their own
 * labels, sharing one 7-column grid so weekdays line up across months.
 * <p>
 * Scrolling is infinite: only a small window of months is loaded at once, and months are
 * swapped in/out at the edges as the viewport's center crosses into a neighboring month.
 */
public class CalendarWidget extends PopupWidget
{
    private static final String[] WEEKDAYS = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM");

    private static final int CELL_WIDTH = 40;
    private static final int CELL_HEIGHT = 34;
    private static final int CELL_GAP = 4;
    private static final int MONTH_SPACING = 20;
    private static final int VIEWPORT_HEIGHT = 340;
    private static final int SCREEN_MARGIN = 10;
    // Months loaded on each side of the centered one. Two is enough that even the shortest
    // months can't leave the viewport looking past the loaded range.
    private static final int BUFFER_MONTHS = 2;

    private VBox months;
    private YearMonth firstMonth;
    // Position is the content-space Y coordinate of the viewport's top edge
    private SmoothScroller scroller;

    // Momos windows are unmanaged by BSPWM (manage=off), so they never receive X11 input focus
    // and JavaFX key events never fire. Grab the keyboard at the X11 level so Esc can close the calendar
    private final X11KeyGrabber grabber = new X11KeyGrabber()
        .interrupt(X11Key.SUPER_L, X11Key.SUPER_R)
        .key(X11Key.ESCAPE, () -> Platform.runLater(this::hide));
    // For the same reason, the window can't tell when focus moves elsewhere. Watch for clicks instead
    private final X11ClickWatcher clickWatcher = new X11ClickWatcher((x, y) -> Platform.runLater(() -> onScreenPress(x, y)));

    public CalendarWidget()
    {   super(Identifier.of("momos", "calendar"));
    }

    @Override
    public Stage initStage(Stage stage)
    {
        GridPane weekdays = createGrid();
        weekdays.getStyleClass().add("calendar-weekdays");
        for (int i = 0; i < WEEKDAYS.length; i++)
        {
            Label weekday = new Label(WEEKDAYS[i]);
            weekday.getStyleClass().add("calendar-weekday");
            weekday.setPrefWidth(CELL_WIDTH);
            weekday.setAlignment(Pos.CENTER);
            weekdays.add(weekday, i, 0);
        }

        this.months = new VBox(MONTH_SPACING);

        Pane viewport = new Pane(months);
        viewport.setMinHeight(VIEWPORT_HEIGHT);
        viewport.setPrefHeight(VIEWPORT_HEIGHT);
        viewport.setMaxHeight(VIEWPORT_HEIGHT);
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        this.scroller = new SmoothScroller(pos -> applyScroll()).attachTo(viewport);

        VBox root = new VBox(8, weekdays, viewport);
        root.getStyleClass().add("calendar");

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        loadStylesheet("calendar", scene);

        stage.setScene(scene);
        stage.setTitle("momos-calendar");
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setAlwaysOnTop(true);
        return stage;
    }

    private GridPane createGrid()
    {
        GridPane grid = new GridPane();
        grid.setHgap(CELL_GAP);
        grid.setVgap(CELL_GAP);
        for (int i = 0; i < 7; i++)
        {   grid.getColumnConstraints().add(new ColumnConstraints(CELL_WIDTH));
        }
        return grid;
    }

    private VBox createMonth(YearMonth month)
    {
        Label name = new Label(month.format(MONTH_FORMAT));
        name.getStyleClass().add("calendar-month-name");
        Label year = new Label(String.valueOf(month.getYear()));
        year.getStyleClass().add("calendar-month-year");
        HBox header = new HBox(6, name, year);
        header.setAlignment(Pos.BASELINE_LEFT);

        GridPane days = createGrid();
        LocalDate today = LocalDate.now();
        // Sunday-first column of the 1st (DayOfWeek is Monday=1 .. Sunday=7)
        int offset = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int day = 1; day <= month.lengthOfMonth(); day++)
        {
            Label cell = new Label(String.valueOf(day));
            cell.getStyleClass().add("calendar-day");
            if (month.atDay(day).equals(today))
            {   cell.getStyleClass().add("today");
            }
            cell.setMinSize(CELL_WIDTH, CELL_HEIGHT);
            cell.setPrefSize(CELL_WIDTH, CELL_HEIGHT);
            cell.setMaxSize(CELL_WIDTH, CELL_HEIGHT);
            cell.setAlignment(Pos.CENTER);

            int index = offset + day - 1;
            days.add(cell, index % 7, index / 7);
        }

        VBox container = new VBox(8, header, days);
        container.getStyleClass().add("calendar-month");
        return container;
    }

    /** Rebuilds the loaded months around the current month and centers it in the viewport. */
    private void resetToToday()
    {
        this.firstMonth = YearMonth.now().minusMonths(BUFFER_MONTHS);
        List<Node> loaded = new ArrayList<>();
        for (int i = 0; i <= BUFFER_MONTHS * 2; i++)
        {   loaded.add(createMonth(firstMonth.plusMonths(i)));
        }
        months.getChildren().setAll(loaded);
        relayout();

        Node current = months.getChildren().get(BUFFER_MONTHS);
        scroller.jumpTo(current.getLayoutY() + (current.getLayoutBounds().getHeight() - VIEWPORT_HEIGHT) / 2);
    }

    /** Forces an immediate layout pass so month positions are accurate before the next pulse. */
    private void relayout()
    {
        months.applyCss();
        months.autosize();
        months.layout();
    }

    private void applyScroll()
    {
        recenter();
        months.setTranslateY(-scroller.position());
    }

    /** Swaps months in/out until the viewport's center lies within the middle loaded month. */
    private void recenter()
    {
        while (true)
        {
            List<Node> loaded = months.getChildren();
            double center = scroller.position() + VIEWPORT_HEIGHT / 2.0;
            if (center >= loaded.get(BUFFER_MONTHS + 1).getLayoutY()) shiftMonths(1);
            else if (center < loaded.get(BUFFER_MONTHS).getLayoutY()) shiftMonths(-1);
            else break;
        }
    }

    /** Drops one month from one end and loads a new one on the other, keeping the visible content in place. */
    private void shiftMonths(int direction)
    {
        List<Node> loaded = months.getChildren();
        // The middle month survives either shift, so its movement is how far the content moved
        Node anchor = loaded.get(BUFFER_MONTHS);
        double before = anchor.getLayoutY();

        if (direction > 0)
        {   loaded.remove(0);
            firstMonth = firstMonth.plusMonths(1);
            loaded.add(createMonth(firstMonth.plusMonths(BUFFER_MONTHS * 2)));
        }
        else
        {   loaded.remove(loaded.size() - 1);
            firstMonth = firstMonth.minusMonths(1);
            loaded.add(0, createMonth(firstMonth));
        }
        relayout();

        scroller.offset(anchor.getLayoutY() - before);
    }

    /** Toggles the calendar, positioned under the top-right corner of the window containing {@code anchor}. */
    public void toggle(Node anchor)
    {
        Window bar = anchor.getScene().getWindow();
        Platform.runLater(() ->
        {   if (stage.isShowing()) this.hide();
            else showAt(bar.getX() + bar.getWidth(), bar.getY() + bar.getHeight());
        });
    }

    /** Hides the calendar when the mouse is pressed outside it. {@code x} and {@code y} are in physical pixels. */
    private void onScreenPress(int x, int y)
    {
        if (!stage.isShowing()) return;
        double scale = Screen.getPrimary().getOutputScaleX();
        double px = x / scale;
        double py = y / scale;
        boolean inCalendar = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight()).contains(px, py);
        // Clocks toggle the calendar themselves on release. Hiding it on press too would make them reopen it
        if (!inCalendar && !isOnClock(px, py))
        {   this.hide();
        }
    }

    private static boolean isOnClock(double x, double y)
    {
        return Window.getWindows().stream()
                .filter(window -> window.isShowing() && window.getScene() != null)
                .flatMap(window -> window.getScene().getRoot().lookupAll(".clock").stream())
                .anyMatch(clock ->
                {   Bounds bounds = clock.localToScreen(clock.getBoundsInLocal());
                    return bounds != null && bounds.contains(x, y);
                });
    }

    @Override
    public void show()
    {
        Thread.ofVirtual().start(() ->
        {
            Rectangle2D bounds = Monitors.getBounds("focused");
            Platform.runLater(() -> showAt(bounds.getMaxX(), bounds.getMinY() + BarWidget.BAR_HEIGHT));
        });
    }

    private void showAt(double right, double top)
    {
        // Hiding the window makes JavaFX forget its scene's styles until it's shown again, so CSS applied
        // before showing can't resolve lookups defined on .root (the -momos-* colors). Show first, then lay out
        stage.show();
        grabber.start();
        clickWatcher.start();
        resetToToday();
        Parent root = stage.getScene().getRoot();
        root.applyCss();
        stage.setX(right - root.prefWidth(-1) - SCREEN_MARGIN);
        stage.setY(top + SCREEN_MARGIN);
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
}

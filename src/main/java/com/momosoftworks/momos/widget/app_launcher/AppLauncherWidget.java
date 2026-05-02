package com.momosoftworks.momos.widget.app_launcher;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.momosoftworks.momos.MomosApp;
import com.momosoftworks.momos.util.Identifier;
import com.momosoftworks.momos.util.image.ImageHelper;
import com.momosoftworks.momos.util.wm.CommandHelper;
import com.momosoftworks.momos.util.wm.Monitors;
import com.momosoftworks.momos.util.x11.X11KeyGrabber;
import com.momosoftworks.momos.widget.PopupWidget;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AppLauncherWidget extends PopupWidget
{
    private enum Section { PINNED, APPS }

    private TextField searchField;
    private X11KeyGrabber grabber;
    private Thread grabThread;
    private VBox appsList;
    private HBox pinnedAppsPane;
    private ScrollPane appEntriesPane;
    private List<Application> pinnedApps;
    private Set<Application> applications;
    private Section activeSection = null;
    private int selectedAppIndex = -1;
    private boolean mouseInteractionEnabled = false;

    public AppLauncherWidget()
    {   super(Identifier.of("momos", "app_launcher"));
    }

    @Override
    public Stage initStage(Stage stage)
    {
        VBox root = new VBox(10);
        root.getStyleClass().add("app-launcher");

        pinnedAppsPane = new HBox(4);
        pinnedAppsPane.setAlignment(Pos.CENTER_LEFT);
        pinnedAppsPane.getStyleClass().add("pinned-apps");

        // Separates the pinned apps from the search bar
        Region separator = new Region();
        separator.setPrefHeight(1);
        separator.setMaxWidth(Double.MAX_VALUE);
        separator.getStyleClass().add("separator");

        HBox searchBox = new HBox(6);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        searchBox.getStyleClass().add("search-box");
        // Icon
        Image searchIcon = new Image(getClass().getResource("/icons/search.png").toString(), 16, 16, true, true);
        ImageView searchIconView = new ImageView(searchIcon);
        searchIconView.getStyleClass().add("search-icon");
        searchIconView.setFitWidth(18);
        searchIconView.setFitHeight(18);
        // Text Field
        this.searchField = new TextField();
        searchField.setPromptText("Search...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, old, newVal) ->
        {
            this.filterResults(newVal);
            searchField.requestFocus();
        });
        searchBox.getChildren().addAll(searchIconView, searchField);

        this.appEntriesPane = new ScrollPane();
        appEntriesPane.setContent(this.appsList);
        appEntriesPane.setFitToWidth(true);
        appEntriesPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        appEntriesPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        appEntriesPane.setOnScroll(e -> mouseInteractionEnabled = true);
        appEntriesPane.setMinHeight(0);
        appEntriesPane.setPadding(new Insets(0, -4, 0, 0));
        VBox.setVgrow(appEntriesPane, Priority.ALWAYS);

        pinnedAppsPane.setMinHeight(Region.USE_PREF_SIZE);
        separator.setMinHeight(Region.USE_PREF_SIZE);
        searchBox.setMinHeight(Region.USE_PREF_SIZE);

        root.getChildren().addAll(pinnedAppsPane, separator, searchBox, appEntriesPane);

        Scene scene = new Scene(root, 500, 600);
        scene.setFill(Color.TRANSPARENT);
        scene.setOnMouseMoved(e -> mouseInteractionEnabled = true);

        loadStylesheet("app-launcher", scene);

        stage.setScene(scene);
        stage.initStyle(StageStyle.TRANSPARENT);

        return stage;
    }

    private Set<Application> scanApplications()
    {
        String[] paths = new String[]{
            "/usr/share/applications/",
            System.getenv("HOME") + "/.local/share/applications/",
            System.getenv("HOME") + "/.local/share/flatpak/exports/share/applications/",
            "/var/lib/flatpak/exports/share/applications/",
        };
        Set<Application> apps = new LinkedHashSet<>();
        Set<String> seenNames = new HashSet<>();
        for (String path : paths)
        {
            try (Stream<Path> desktopFiles = Files.find(Path.of(path), 3, (p, attr) -> p.toString().endsWith(".desktop")))
            {
                desktopFiles.forEach(p ->
                {
                    try
                    {
                        List<String> lines = Files.readAllLines(p);
                        // Only real, visible applications
                        if (!"Application".equals(findProperty(lines, "Type="))) return;
                        if ("true".equalsIgnoreCase(findProperty(lines, "NoDisplay="))) return;
                        if ("true".equalsIgnoreCase(findProperty(lines, "Hidden="))) return;

                        String name = findProperty(lines, "Name=");
                        String exec = findProperty(lines, "Exec=");
                        if (name == null || exec == null) return;

                        // First path wins — skip duplicates from flatpak/other mirrors
                        if (!seenNames.add(name.toLowerCase())) return;

                        String icon = findProperty(lines, "Icon=");
                        String iconPath = "";
                        Image img = null;
                        if (icon != null)
                        {
                            iconPath = ImageHelper.getIconPath(icon);
                            if (iconPath != null)
                            {   img = ImageHelper.getIcon(iconPath, 64);
                            }
                        }
                        apps.add(new Application(img, iconPath, name, p.toString(), exec));
                    }
                    catch (Exception e) { /* skip malformed entries */ }
                });
            }
            catch (Exception e)
            {   /* skip missing directories */
            }
        }
        return apps;
    }

    private void loadApplications()
    {
        Thread.ofVirtual().start(() ->
        {
            this.applications = this.scanApplications();
            // Build nodes off the FX thread — safe before scene attachment
            VBox list = new VBox(6);
            list.getStyleClass().add("apps-list");
            List<AppButton> buttons = this.applications.parallelStream()
                    .map(app -> (AppButton) new AppEntryButton(app, this))
                    .sorted((a, b) -> a.app.name().compareToIgnoreCase(b.app.name()))
                    .toList();
            list.getChildren().addAll(buttons);
            // Only scene-graph attachment must be on the FX thread
            Platform.runLater(() -> this.populateAppsList(list));
        });
    }

    private void populateAppsList(VBox list)
    {
        this.appsList = list;
        this.appEntriesPane.setContent(this.appsList);
        if (activeSection == Section.APPS)
        {
            selectedAppIndex = -1;
            setHoveredAppIndex(0);
        }
    }

    public void filterResults(String filter)
    {
        if (appsList == null) return;
        // Clear hovered from every entry before visibility changes — index tracking breaks
        // when the visible set changes, so entries can otherwise retain .hovered while hidden
        appsList.getChildren().forEach(n -> ((AppButton) n).setHovered(false));
        selectedAppIndex = -1;
        Predicate<Node> predicate = filter.isEmpty()
                                         ? (app -> true)
                                         : (app -> app instanceof AppEntryButton entry && entry.app.name().toLowerCase().contains(filter.toLowerCase()));
        appsList.getChildren().forEach(app ->
        {   app.setVisible(predicate.test(app));
            app.setManaged(app.isVisible());
        });
        if (!getVisibleEntries().isEmpty())
        {   this.setHoveredAppIndex(0);
        }
        else
        {   this.clearSelection();
        }
    }

    private void loadPinnedApps()
    {
        Thread.ofVirtual().start(() ->
        {
            this.pinnedApps = this.parsePinnedApps();
            Platform.runLater(this::populatePinnedApps);
        });
    }

    private List<Application> parsePinnedApps()
    {
        List<Application> list = new ArrayList<>();
        JsonElement pinnedApps = MomosApp.getConfigSetting("pinned_apps");
        if (pinnedApps != null && pinnedApps.isJsonArray())
        {
            JsonArray arr = pinnedApps.getAsJsonArray();
            for (JsonElement element : arr)
            {
                Application app = Application.CODEC.deserialize(element);
                if (app != null)
                {   list.add(app);
                }
            }
        }
        return list;
    }

    private void savePinnedApps()
    {
        JsonArray arr = new JsonArray();
        for (Node node : pinnedAppsPane.getChildren())
        {
            if (node instanceof PinnedAppButton btn)
            {   arr.add(Application.CODEC.serialize(btn.app));
            }
        }
        MomosApp.saveConfigSetting("pinned_apps", arr);
    }

    private void populatePinnedApps()
    {
        this.pinnedAppsPane.getChildren().clear();
        for (Application app : this.pinnedApps)
        {   this.pinnedAppsPane.getChildren().add(new PinnedAppButton(app, this));
        }
        if (!this.pinnedApps.isEmpty())
        {   this.setSection(Section.PINNED);
        }
    }

    public void pinApp(Application app)
    {
        // Avoid duplicates
        if (pinnedAppsPane.getChildren().stream().anyMatch(node -> node instanceof PinnedAppButton btn && btn.app.equals(app)))
        {   return;
        }
        this.pinnedAppsPane.getChildren().add(new PinnedAppButton(app, this));
        this.savePinnedApps();
    }

    public void unpinApp(Application app)
    {   this.pinnedAppsPane.getChildren().removeIf(node -> node instanceof PinnedAppButton btn && btn.app.equals(app));
        this.savePinnedApps();
    }

    /** Moves focus to a section, selecting the appropriate default entry. */
    private void setSection(Section section)
    {
        Section prev = activeSection;
        activeSection = section;
        switch (section)
        {
            case APPS ->
            {
                this.clearPinnedSelection();
                if (prev == Section.PINNED) selectedAppIndex = -1;
                List<Node> visible = this.getVisibleEntries();
                if (visible.isEmpty())
                {   return;
                }
                this.setHoveredAppIndex(Math.max(0, Math.min(selectedAppIndex, visible.size() - 1)));
            }
            case PINNED ->
            {
                if (prev != Section.PINNED)
                {   this.clearSelection();
                    List<PinnedAppButton> pinned = this.getPinnedButtons();
                    if (!pinned.isEmpty())
                    {   this.setHoveredPinnedIndex(0);
                    }
                }
            }
        }
        this.mouseInteractionEnabled = false;
    }

    private void setHoveredAppIndex(int index)
    {
        List<Node> visible = this.getVisibleEntries();
        if (selectedAppIndex >= 0 && selectedAppIndex < visible.size() && selectedAppIndex != index)
        {   ((AppButton) visible.get(selectedAppIndex)).setHovered(false);
        }
        selectedAppIndex = index;
        if (index >= 0 && index < visible.size())
        {
            AppButton entry = (AppButton) visible.get(index);
            entry.setHovered(true);
            this.scrollToEntry(entry);
        }
    }

    private void clearSelection()
    {
        this.getVisibleEntries().forEach(n -> ((AppButton) n).setHovered(false));
        selectedAppIndex = -1;
    }

    private List<PinnedAppButton> getPinnedButtons()
    {
        return pinnedAppsPane.getChildren().stream()
                .filter(n -> n instanceof PinnedAppButton)
                .map(n -> (PinnedAppButton) n)
                .collect(Collectors.toList());
    }

    private void clearPinnedSelection()
    {   pinnedAppsPane.getChildren().forEach(n -> ((AppButton) n).setHovered(false));
    }

    private void setHoveredPinnedIndex(int index)
    {
        if (selectedAppIndex == index) return;
        this.clearPinnedSelection();
        selectedAppIndex = index;
        List<PinnedAppButton> pinned = getPinnedButtons();
        if (index >= 0 && index < pinned.size())
        {   pinned.get(index).setHovered(true);
        }
    }

    private List<Node> getVisibleEntries()
    {
        if (appsList == null) return List.of();
        return appsList.getChildren().stream().filter(Node::isVisible).collect(Collectors.toList());
    }

    private void launchSelected()
    {
        List<Node> visible = getVisibleEntries();
        int idx = (selectedAppIndex >= 0 && selectedAppIndex < visible.size()) ? selectedAppIndex : 0;
        if (idx < visible.size() && visible.get(idx) instanceof AppButton btn)
        {
            CommandHelper.launchProgram(btn.app.exec());
            this.hide();
        }
    }

    private void scrollToEntry(Node entry)
    {
        Platform.runLater(() ->
        {
            double contentH = appsList.getHeight();
            double viewportH = appEntriesPane.getViewportBounds().getHeight();
            double range = contentH - viewportH;
            if (range <= 0) return;
            double minY = entry.getBoundsInParent().getMinY();
            double maxY = entry.getBoundsInParent().getMaxY();
            double scrollY = appEntriesPane.getVvalue() * range;
            if (minY < scrollY)
            {   appEntriesPane.setVvalue(minY / range);
            }
            else if (maxY > scrollY + viewportH)
            {   appEntriesPane.setVvalue((maxY - viewportH) / range);
            }
        });
    }

    public void onMouseEnteredAppEntry(AppButton entry)
    {
        if (!mouseInteractionEnabled) return;
        if (activeSection != Section.APPS)
        {
            if (activeSection == Section.PINNED) selectedAppIndex = -1;
            clearPinnedSelection();
            activeSection = Section.APPS;
        }
        List<Node> visible = this.getVisibleEntries();
        int index = visible.indexOf(entry);
        if (index >= 0)
        {   this.setHoveredAppIndex(index);
        }
        mouseInteractionEnabled = false;
    }

    public void onMouseEnteredPinnedApp(PinnedAppButton entry)
    {
        if (!mouseInteractionEnabled) return;
        List<PinnedAppButton> pinned = this.getPinnedButtons();
        int index = pinned.indexOf(entry);
        this.setSection(Section.PINNED);
        if (index >= 0)
        {   this.setHoveredPinnedIndex(index);
        }
    }

    public void onMouseExitedAppButton(AppButton entry)
    {
    }

    private void startGrabber()
    {
        this.stopGrabber();
        grabber = new X11KeyGrabber();
        grabber.addInterruptKeys(X11KeyGrabber.XK_SUPER_L, X11KeyGrabber.XK_SUPER_R);
        grabber.setOnResume(() -> Platform.runLater(searchField::requestFocus));
        grabThread = Thread.ofPlatform().start(() ->
        {
            try { grabber.start(token -> Platform.runLater(() -> handleKey(token))); }
            catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void stopGrabber()
    {
        if (grabber != null)
        {
            grabber.stop();
            grabThread.interrupt();
            grabber = null;
            grabThread = null;
        }
    }

    /**
     * Dispatches a keygrabber protocol token to the handler for the active section.
     * Protocol:
     *   {@code CHAR <text>}  — printable character(s) to insert at the caret
     *   {@code KEY <name>}   — named control key
     */
    private void handleKey(String token)
    {
        if (token.startsWith("CHAR "))
        {
            // Typing always routes to SEARCH; disable mouse interaction until mouse moves again
            mouseInteractionEnabled = false;
            this.setSection(Section.APPS);
            searchField.insertText(searchField.getCaretPosition(), token.substring(5));
            return;
        }
        if (token.startsWith("KEY ")) token = token.substring(4);
        if (activeSection == null) return;
        switch (activeSection)
        {
            case APPS   -> handleKeyEntries(token);
            case PINNED -> handleKeyPinned(token);
        }
    }

    private void handleKeyEntries(String key)
    {
        switch (key)
        {
            case "Escape"    -> this.hide();
            case "Return"    -> this.launchSelected();
            case "Up"        ->
            {
                if (selectedAppIndex > 0)
                {   this.mouseInteractionEnabled = false;
                    this.setHoveredAppIndex(selectedAppIndex - 1);
                }
                else
                {   this.setSection(Section.PINNED);
                }
            }
            case "Down"      ->
            {
                int last = this.getVisibleEntries().size() - 1;
                if (selectedAppIndex < last)
                {   this.mouseInteractionEnabled = false;
                    this.setHoveredAppIndex(selectedAppIndex + 1);
                }
            }
            case "Left"      -> { /* TODO: entry options */ }
            case "Right"     -> { /* TODO: entry options */ }
            case "BackSpace" -> { searchField.deletePreviousChar(); }
            case "Delete"    -> { searchField.deleteNextChar(); }
        }
    }

    private void handleKeyPinned(String key)
    {
        switch (key)
        {
            case "Escape" -> this.hide();
            case "Down"   -> this.setSection(Section.APPS);
            case "Return" ->
            {
                List<PinnedAppButton> pinned = getPinnedButtons();
                if (selectedAppIndex >= 0 && selectedAppIndex < pinned.size())
                {
                    CommandHelper.launchProgram(pinned.get(selectedAppIndex).app.exec());
                    this.hide();
                }
            }
            case "Left"   ->
            {
                if (selectedAppIndex > 0)
                {   setHoveredPinnedIndex(selectedAppIndex - 1);
                }
            }
            case "Right"  ->
            {
                List<PinnedAppButton> pinned = getPinnedButtons();
                if (selectedAppIndex < pinned.size() - 1)
                {   setHoveredPinnedIndex(selectedAppIndex + 1);
                }
            }
            case "Tab"    ->
            {
                List<PinnedAppButton> pinned = getPinnedButtons();
                if (!pinned.isEmpty())
                {   setHoveredPinnedIndex((selectedAppIndex + 1) % pinned.size());
                }
            }
            case "End" ->
            {
                List<PinnedAppButton> pinned = getPinnedButtons();
                if (!pinned.isEmpty())
                {   setHoveredPinnedIndex(pinned.size() - 1);
                }
            }
            case "Home" ->
            {
                List<PinnedAppButton> pinned = getPinnedButtons();
                if (!pinned.isEmpty())
                {   setHoveredPinnedIndex(0);
                }
            }
        }
    }

    private String findProperty(List<String> lines, String property)
    {   return lines.stream().filter(l -> l.startsWith(property)).findFirst().map(l -> l.substring(property.length())).orElse(null);
    }

    public void load()
    {
        this.loadApplications();
        this.loadPinnedApps();
    }

    @Override
    public void show()
    {
        this.mouseInteractionEnabled = false;
        this.load();
        this.startGrabber();
        Thread.ofVirtual().start(() ->
        {
            Rectangle2D bounds = Monitors.getBounds("focused");
            Platform.runLater(() ->
            {
                double w = this.stage.getScene().getWidth();
                double h = this.stage.getScene().getHeight();
                this.stage.setX(bounds.getMinX() + (bounds.getWidth()  - w) / 2);
                this.stage.setY(bounds.getMinY() + (bounds.getHeight() - h) / 2);

                this.searchField.clear();
                this.appEntriesPane.setVvalue(0);
                this.stage.show();
                this.searchField.requestFocus();
            });
        });
    }

    @Override
    public void hide()
    {
        this.stopGrabber();
        this.searchField.clear();
        this.clearSelection();
        this.activeSection = null;
        Platform.runLater(() -> stage.hide());
    }
}

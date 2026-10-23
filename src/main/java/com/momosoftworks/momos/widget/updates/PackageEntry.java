package com.momosoftworks.momos.widget.updates;

import com.momosoftworks.momos.widget.IconButton;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/** One package in the updates list: its name and new version, with a button to update just that package. */
public class PackageEntry extends HBox
{
    public enum Status
    {
        QUEUED("Queued"),
        UPDATING("Updating...");

        private final String label;

        Status(String label)
        {   this.label = label;
        }
    }

    private static final Image UPDATE_ICON = new Image("icons/update.png", 32, 32, true, true);
    private static final PseudoClass BUSY = PseudoClass.getPseudoClass("busy");

    public final PackageUpdate pkg;
    private final Label statusLabel;
    private final IconButton updateButton;
    private Status status;
    private boolean locked;

    public PackageEntry(PackageUpdate pkg, Consumer<PackageUpdate> onUpdate)
    {
        this.pkg = pkg;
        this.setSpacing(8);
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("update-entry");

        Label name = new Label(pkg.name());
        name.getStyleClass().add("update-entry-name");
        // Let long names shrink (and ellipsize) rather than push the version off the row
        name.setMinWidth(0);
        HBox title = new HBox(5, name);
        title.setAlignment(Pos.BASELINE_LEFT);
        title.setMinWidth(0);
        title.setMaxHeight(Region.USE_PREF_SIZE);
        if (pkg.aur())
        {   Label aur = new Label("AUR");
            aur.getStyleClass().add("update-entry-aur");
            aur.setMinWidth(Region.USE_PREF_SIZE);
            title.getChildren().add(aur);
        }
        HBox.setHgrow(title, Priority.ALWAYS);

        Label version = new Label(pkg.newVersion());
        version.getStyleClass().add("update-entry-version");
        version.setMinWidth(Region.USE_PREF_SIZE);
        version.setTooltip(new Tooltip(pkg.oldVersion() + "  →  " + pkg.newVersion()));

        this.statusLabel = new Label();
        statusLabel.getStyleClass().add("update-entry-status");
        statusLabel.setMinWidth(Region.USE_PREF_SIZE);

        this.updateButton = new IconButton(UPDATE_ICON, 16);
        updateButton.setOnAction(e -> onUpdate.accept(pkg));

        this.getChildren().addAll(title, version, statusLabel, updateButton);
        this.setStatus(null);
    }

    /** Shows that this package is waiting for or being updated. Null when it isn't. */
    public void setStatus(Status status)
    {
        this.status = status;
        statusLabel.setText(status == null ? null : status.label);
        statusLabel.setVisible(status != null);
        statusLabel.setManaged(status != null);
        this.pseudoClassStateChanged(BUSY, status != null);
        this.refreshButton();
    }

    /** Stops the package from being updated on its own, e.g. while the whole system is updating. */
    public void setLocked(boolean locked)
    {
        this.locked = locked;
        this.refreshButton();
    }

    private void refreshButton()
    {   updateButton.setDisable(locked || status != null);
    }
}

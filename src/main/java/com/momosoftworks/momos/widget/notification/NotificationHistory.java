package com.momosoftworks.momos.widget.notification;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.momosoftworks.momos.MomosApp;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Every notification received, newest first. Saved to the cache directory, so it survives restarts.
 * Changes must be made on the FX thread.
 */
public class NotificationHistory
{
    private static final Duration SAVE_DELAY = Duration.seconds(1);
    // Icons are saved before their notification reaches the history, so a recent one may be in use without being listed yet
    private static final long ICON_PRUNE_MIN_AGE_MS = 60_000;

    private final int maxSize;
    private final ObservableList<NotificationRecord> records = FXCollections.observableArrayList();
    // Kept rather than made per call: the view only weakly listens to the list, so a discarded one
    // gets garbage collected and silently stops passing on changes to its own listeners
    private final ObservableList<NotificationRecord> readOnlyRecords = FXCollections.unmodifiableObservableList(records);
    // Where each notification ID was stored, so a notification that replaces another replaces its entry too
    private final Map<Integer, NotificationRecord> byId = new HashMap<>();

    // Saving waits until changes settle, so a burst of notifications is written once
    private final PauseTransition saveDelay = new PauseTransition(SAVE_DELAY);
    // One write at a time, so an older snapshot can never land after a newer one
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r ->
    {   Thread thread = new Thread(r, "notification-history");
        thread.setDaemon(true);
        return thread;
    });

    public NotificationHistory(int maxSize)
    {
        this.maxSize = maxSize;
        List<NotificationRecord> loaded = load();
        records.setAll(loaded.subList(0, Math.min(loaded.size(), maxSize)));

        List<NotificationRecord> kept = List.copyOf(records);
        writer.submit(() -> pruneIcons(kept));
        saveDelay.setOnFinished(e ->
        {   List<NotificationRecord> snapshot = List.copyOf(records);
            writer.submit(() -> write(snapshot));
        });
    }

    /** Newest first. Read-only; use the methods here to change it */
    public ObservableList<NotificationRecord> records()
    {   return readOnlyRecords;
    }

    /** Adds a notification to the top, in place of the one it replaces if {@code id} was stored before */
    public void add(int id, NotificationRecord record)
    {
        NotificationRecord replaced = byId.put(id, record);
        if (replaced != null) records.removeIf(r -> r == replaced);
        records.add(0, record);
        while (records.size() > maxSize)
        {   NotificationRecord dropped = records.removeLast();
            byId.values().removeIf(r -> r == dropped);
        }
        this.save();
    }

    public void remove(NotificationRecord record)
    {
        records.removeIf(r -> r == record);
        byId.values().removeIf(r -> r == record);
        this.save();
    }

    public void clear()
    {
        records.clear();
        byId.clear();
        this.save();
    }

    /** Writes out any unsaved changes, waiting until they're on disk */
    public void shutdown()
    {
        boolean unsaved = saveDelay.getStatus() == Animation.Status.RUNNING;
        saveDelay.stop();
        writer.shutdown();
        try
        {   writer.awaitTermination(2, TimeUnit.SECONDS);
        }
        catch (InterruptedException ignored) {}
        if (unsaved) write(List.copyOf(records));
    }

    private void save()
    {   saveDelay.playFromStart();
    }

    /* Persistence */

    private static Path getFile()
    {   return MomosApp.getCacheDir().resolve("notification_history.json");
    }

    private static Path getIconDir()
    {   return MomosApp.getCacheDir().resolve("notification_icons");
    }

    private static List<NotificationRecord> load()
    {
        Path file = getFile();
        if (!Files.exists(file)) return List.of();
        try
        {
            List<NotificationRecord> loaded = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(Files.readString(file)).getAsJsonArray())
            {
                // One bad entry shouldn't cost the rest of the history
                try
                {   loaded.add(NotificationRecord.CODEC.deserialize(element));
                }
                catch (Exception e)
                {   System.err.println("[Notifications] Skipping unreadable history entry: " + e.getMessage());
                }
            }
            return loaded;
        }
        catch (Exception e)
        {   e.printStackTrace();
            return List.of();
        }
    }

    private static void write(List<NotificationRecord> snapshot)
    {
        JsonArray array = new JsonArray();
        for (NotificationRecord record : snapshot)
        {   array.add(NotificationRecord.CODEC.serialize(record));
        }
        try
        {
            Path file = getFile();
            Files.createDirectories(file.getParent());
            // Written beside the real file, then swapped in, so a crash mid-write can't corrupt the history
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, array.toString());
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
    }

    /**
     * Saves an icon that was sent as raw pixels, so the history can still show it after a restart.
     * Identical icons share a file. Returns its path, or null if it couldn't be saved.
     */
    public static String saveIcon(Image image)
    {
        try
        {
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            int[] pixels = new int[width * height];
            image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
            ByteBuffer bytes = ByteBuffer.allocate(4 + pixels.length * 4).putInt(width);
            bytes.asIntBuffer().put(pixels);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes.array()));

            Path file = getIconDir().resolve(hash + ".png");
            if (Files.exists(file))
            {   // Marks it as recently used, so it isn't pruned before its notification is listed
                file.toFile().setLastModified(System.currentTimeMillis());
            }
            else
            {   Files.createDirectories(file.getParent());
                ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file.toFile());
            }
            return file.toString();
        }
        catch (Exception e)
        {   System.err.println("[Notifications] Couldn't save icon: " + e.getMessage());
            return null;
        }
    }

    /** Deletes saved icons that no notification in the history uses anymore */
    private static void pruneIcons(List<NotificationRecord> kept)
    {
        Path dir = getIconDir();
        if (!Files.isDirectory(dir)) return;
        Set<String> used = kept.stream().map(NotificationRecord::icon).collect(Collectors.toSet());
        long cutoff = System.currentTimeMillis() - ICON_PRUNE_MIN_AGE_MS;
        try (Stream<Path> files = Files.list(dir))
        {
            for (Path file : files.toList())
            {
                if (!used.contains(file.toString()) && file.toFile().lastModified() < cutoff)
                {   Files.deleteIfExists(file);
                }
            }
        }
        catch (Exception e)
        {   e.printStackTrace();
        }
    }
}

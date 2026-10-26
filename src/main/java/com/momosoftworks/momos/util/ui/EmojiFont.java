package com.momosoftworks.momos.util.ui;

import com.momosoftworks.momos.util.LazySupplier;
import com.momosoftworks.momos.util.wm.CommandHelper;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colour emoji, read straight from the system's emoji font. JavaFX can't draw colour bitmap fonts (CBDT, like
 * Noto Color Emoji) on Linux, so {@link EmojiLabel} shows the font's emoji as images instead.
 * <p>
 * Only what's needed for that is read: the character map, the ligatures that join sequences into a single emoji
 * (flags, skin tones, keycaps, and ZWJ sequences like 👨‍👩‍👧), and the PNG images themselves.
 * If there's no colour bitmap emoji font, nothing is treated as an emoji and text is left as it is.
 */
public final class EmojiFont
{
    private static final LazySupplier<EmojiFont> INSTANCE = new LazySupplier<>(EmojiFont::load);

    private static final int TEXT_PRESENTATION = 0xFE0E;
    private static final int EMOJI_PRESENTATION = 0xFE0F;

    /** Part of a string: either plain text, or one emoji drawn as {@code glyph} */
    public record Segment(String text, int glyph)
    {
        public boolean isEmoji()
        {   return glyph >= 0;
        }
    }

    private record Ligature(int[] rest, int glyph) {}
    private record Match(int glyph, int end) {}
    /** A run of glyphs whose images are found through the same index */
    private record ImageIndex(int firstGlyph, int lastGlyph, int indexFormat, int imageFormat, int offsets, int imageData) {}

    private final ByteBuffer data;
    private final Map<Integer, Integer> glyphs = new HashMap<>();
    // By first glyph, longest first, so the most specific sequence wins
    private final Map<Integer, List<Ligature>> ligatures = new HashMap<>();
    private final List<ImageIndex> imageIndexes = new ArrayList<>();
    private final Map<Long, Image> images = new ConcurrentHashMap<>();

    public static EmojiFont get()
    {   return INSTANCE.get();
    }

    private EmojiFont(ByteBuffer data)
    {   this.data = data;
    }

    private static EmojiFont load()
    {
        String file = CommandHelper.executeWithResult("fc-match", "-f", "%{file}", "emoji");
        try (FileChannel channel = FileChannel.open(Path.of(file), StandardOpenOption.READ))
        {
            EmojiFont font = new EmojiFont(channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()));
            Map<String, Integer> tables = font.readTableDirectory();
            if (!tables.containsKey("cmap") || !tables.containsKey("CBLC") || !tables.containsKey("CBDT"))
            {   System.err.println("[Emoji] " + file + " isn't a colour bitmap font, so emoji will be drawn as text");
                return new EmojiFont(null);
            }
            font.readCharacterMap(tables.get("cmap"));
            if (tables.containsKey("GSUB")) font.readLigatures(tables.get("GSUB"));
            font.readImageIndex(tables.get("CBLC"), tables.get("CBDT"));
            return font;
        }
        catch (Exception e)
        {   System.err.println("[Emoji] Couldn't read emoji font '" + file + "', so emoji will be drawn as text: " + e);
            return new EmojiFont(null);
        }
    }

    /* Splitting text */

    /** Splits {@code text} into runs of plain text and individual emoji */
    public List<Segment> split(String text)
    {
        if (glyphs.isEmpty()) return List.of(new Segment(text, -1));
        int[] codePoints = text.codePoints().toArray();
        List<Segment> segments = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < codePoints.length)
        {
            Match emoji = this.matchEmoji(codePoints, i);
            if (emoji == null)
            {   run.appendCodePoint(codePoints[i++]);
                continue;
            }
            if (!run.isEmpty())
            {   segments.add(new Segment(run.toString(), -1));
                run.setLength(0);
            }
            segments.add(new Segment(new String(codePoints, i, emoji.end() - i), emoji.glyph()));
            i = emoji.end();
        }
        if (!run.isEmpty()) segments.add(new Segment(run.toString(), -1));
        return segments;
    }

    /** The emoji starting at {@code start}, or null if there isn't one */
    private Match matchEmoji(int[] codePoints, int start)
    {
        Integer first = glyphs.get(codePoints[start]);
        if (first == null) return null;
        int next = start + 1 < codePoints.length ? codePoints[start + 1] : -1;
        if (next == TEXT_PRESENTATION) return null;

        Match match = this.matchLigature(codePoints, start, first);
        if (match == null)
        {   // Characters like digits and © are in the font for use in sequences, but are only emoji when asked to be
            if (!Character.isEmojiPresentation(codePoints[start]) && next != EMOJI_PRESENTATION) return null;
            match = new Match(first, start + 1);
        }
        return new Match(match.glyph(), skipPresentation(codePoints, match.end()));
    }

    private Match matchLigature(int[] codePoints, int start, int first)
    {
        List<Ligature> candidates = ligatures.get(first);
        if (candidates == null) return null;
        for (Ligature ligature : candidates)
        {
            // The font's sequences leave out FE0F, which senders may or may not include
            int pos = skipPresentation(codePoints, start + 1);
            boolean matches = true;
            for (int component : ligature.rest())
            {
                Integer glyph = pos < codePoints.length ? glyphs.get(codePoints[pos]) : null;
                if (glyph == null || glyph != component)
                {   matches = false;
                    break;
                }
                pos = skipPresentation(codePoints, pos + 1);
            }
            if (matches) return new Match(ligature.glyph(), pos);
        }
        return null;
    }

    private static int skipPresentation(int[] codePoints, int pos)
    {
        while (pos < codePoints.length && codePoints[pos] == EMOJI_PRESENTATION) pos++;
        return pos;
    }

    /* Images */

    /** The image for {@code glyph}, scaled to {@code height} pixels tall. Null if the font has none */
    public Image image(int glyph, int height)
    {
        long key = (long) glyph << 32 | height;
        Image cached = images.get(key);
        if (cached != null) return cached;

        byte[] png = this.readPng(glyph);
        if (png == null) return null;
        // Scaled while decoding, which looks much better than having an ImageView shrink it each frame
        Image image = new Image(new ByteArrayInputStream(png), 0, height, true, true);
        images.put(key, image);
        return image;
    }

    private byte[] readPng(int glyph)
    {
        for (ImageIndex index : imageIndexes)
        {
            if (glyph < index.firstGlyph() || glyph > index.lastGlyph()) continue;
            int i = glyph - index.firstGlyph();
            int start, end;
            switch (index.indexFormat())
            {
                case 1 -> { start = data.getInt(index.offsets() + i * 4); end = data.getInt(index.offsets() + i * 4 + 4); }
                case 3 -> { start = u16(index.offsets() + i * 2); end = u16(index.offsets() + i * 2 + 2); }
                default -> { return null; }
            }
            // Glyphs without an image have no data
            if (end <= start) return null;

            int glyphData = index.imageData() + start;
            int pngStart = switch (index.imageFormat())
            {
                case 17 -> glyphData + 5 + 4;  // small metrics, then the PNG's length
                case 18 -> glyphData + 8 + 4;  // big metrics, then the PNG's length
                case 19 -> glyphData + 4;      // metrics are in the index; just the PNG's length
                default -> -1;
            };
            if (pngStart < 0) return null;
            byte[] png = new byte[data.getInt(pngStart - 4)];
            data.get(pngStart, png);
            return png;
        }
        return null;
    }

    /* Reading the font */

    private int u16(int offset)
    {   return data.getShort(offset) & 0xFFFF;
    }

    private Map<String, Integer> readTableDirectory()
    {
        Map<String, Integer> tables = new HashMap<>();
        int numTables = u16(4);
        for (int i = 0; i < numTables; i++)
        {
            int record = 12 + i * 16;
            byte[] tag = new byte[4];
            data.get(record, tag);
            tables.put(new String(tag), data.getInt(record + 8));
        }
        return tables;
    }

    /** Reads the format 12 subtable, the one that reaches past the Basic Multilingual Plane, where emoji live */
    private void readCharacterMap(int cmap)
    {
        for (int i = 0; i < u16(cmap + 2); i++)
        {
            int subtable = cmap + data.getInt(cmap + 4 + i * 8 + 4);
            if (u16(subtable) != 12) continue;
            int numGroups = data.getInt(subtable + 12);
            for (int g = 0; g < numGroups; g++)
            {
                int group = subtable + 16 + g * 12;
                int startCode = data.getInt(group), endCode = data.getInt(group + 4), startGlyph = data.getInt(group + 8);
                for (int code = startCode; code <= endCode; code++)
                {   glyphs.put(code, startGlyph + code - startCode);
                }
            }
            return;
        }
    }

    /**
     * Collects every ligature substitution in the font. Fonts apply their lookups in order, but emoji fonts list
     * each whole sequence as one ligature, so taking the longest match among all of them comes to the same thing.
     */
    private void readLigatures(int gsub)
    {
        int lookupList = gsub + u16(gsub + 8);
        for (int l = 0; l < u16(lookupList); l++)
        {
            int lookup = lookupList + u16(lookupList + 2 + l * 2);
            int type = u16(lookup);
            for (int s = 0; s < u16(lookup + 4); s++)
            {
                int subtable = lookup + u16(lookup + 6 + s * 2);
                int subtableType = type;
                // Extension lookups just point to a lookup of another type further away
                if (type == 7)
                {   subtableType = u16(subtable + 2);
                    subtable += data.getInt(subtable + 4);
                }
                if (subtableType == 4 && u16(subtable) == 1) this.readLigatureSubtable(subtable);
            }
        }
        for (List<Ligature> candidates : ligatures.values())
        {   candidates.sort(Comparator.comparingInt((Ligature ligature) -> ligature.rest().length).reversed());
        }
    }

    private void readLigatureSubtable(int subtable)
    {
        List<Integer> firstGlyphs = this.readCoverage(subtable + u16(subtable + 2));
        for (int i = 0; i < firstGlyphs.size(); i++)
        {
            int ligatureSet = subtable + u16(subtable + 6 + i * 2);
            for (int j = 0; j < u16(ligatureSet); j++)
            {
                int ligature = ligatureSet + u16(ligatureSet + 2 + j * 2);
                int[] rest = new int[u16(ligature + 2) - 1];
                for (int k = 0; k < rest.length; k++)
                {   rest[k] = u16(ligature + 4 + k * 2);
                }
                ligatures.computeIfAbsent(firstGlyphs.get(i), glyph -> new ArrayList<>()).add(new Ligature(rest, u16(ligature)));
            }
        }
    }

    private List<Integer> readCoverage(int coverage)
    {
        List<Integer> covered = new ArrayList<>();
        int count = u16(coverage + 2);
        for (int i = 0; i < count; i++)
        {
            if (u16(coverage) == 1) covered.add(u16(coverage + 4 + i * 2));
            else
            {   int range = coverage + 4 + i * 6;
                for (int glyph = u16(range); glyph <= u16(range + 2); glyph++) covered.add(glyph);
            }
        }
        return covered;
    }

    /** Indexes the images of the largest size the font has, for the sharpest result when scaled down */
    private void readImageIndex(int cblc, int cbdt)
    {
        int numSizes = data.getInt(cblc + 4);
        int best = -1, bestPpem = -1;
        for (int i = 0; i < numSizes; i++)
        {
            int ppem = data.get(cblc + 8 + i * 48 + 45) & 0xFF;
            if (ppem > bestPpem)
            {   best = cblc + 8 + i * 48;
                bestPpem = ppem;
            }
        }
        if (best < 0) return;

        int array = cblc + data.getInt(best);
        int numSubtables = data.getInt(best + 8);
        for (int i = 0; i < numSubtables; i++)
        {
            int entry = array + i * 8;
            int header = array + data.getInt(entry + 4);
            imageIndexes.add(new ImageIndex(u16(entry), u16(entry + 2), u16(header), u16(header + 2),
                                            header + 8, cbdt + data.getInt(header + 4)));
        }
    }
}

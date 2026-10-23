package com.momosoftworks.momos.widget.updates;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record PackageUpdate(String name, String oldVersion, String newVersion, boolean aur)
{
    // "name old -> new". yay adds trailing columns for AUR packages (e.g. the age of the update), which are ignored
    private static final Pattern LINE = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+->\\s+(\\S+)");

    /** Parses the output of {@code checkupdates} or {@code yay -Qua}, skipping any lines that aren't updates. */
    public static List<PackageUpdate> parse(String output, boolean aur)
    {
        List<PackageUpdate> updates = new ArrayList<>();
        for (String line : output.split("\\R"))
        {
            Matcher m = LINE.matcher(line.trim());
            if (m.find())
            {   updates.add(new PackageUpdate(m.group(1), m.group(2), m.group(3), aur));
            }
        }
        return updates;
    }
}

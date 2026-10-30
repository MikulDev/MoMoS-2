package com.momosoftworks.momos.widget.updates;

import com.momosoftworks.momos.util.wm.CommandHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Checks for and installs package updates through checkupdates and yay. Every method here blocks. */
public class PackageManager
{
    private static final long STATUS_POLL_INTERVAL_MS = 500;

    // Terminals don't reliably exit with their command's exit code, so the script writes it to a file for us.
    // On failure, the window stays open so the output can be read
    private static final String TERMINAL_SCRIPT = """
            status_file="$1"; shift
            "$@"
            status=$?
            echo "$status" > "$status_file"
            if [ "$status" -ne 0 ]; then
                printf '\\nUpdate failed with exit code %s. Press Enter to close.' "$status"
                read -r _
            fi
            """;

    private record Result(int exitCode, String output) {}

    /**
     * Lists available updates, sorted by name. Repo packages come from {@code checkupdates}, which syncs
     * a private copy of the package databases, so the list is current without needing root.
     * Returns null if the check failed (e.g. offline).
     */
    public static List<PackageUpdate> checkUpdates()
    {
        try
        {
            Result repo = run("checkupdates");
            // 0 = updates available, 2 = none, anything else is an error
            if (repo.exitCode() != 0 && repo.exitCode() != 2)
            {   System.err.println("[Updates] checkupdates failed with exit code " + repo.exitCode());
                return null;
            }
            // Exits non-zero when there's nothing to update, so only the output matters
            Result aur = run("yay", "-Qua");

            List<PackageUpdate> updates = new ArrayList<>(PackageUpdate.parse(repo.output(), false));
            updates.addAll(PackageUpdate.parse(aur.output(), true));
            updates.sort(Comparator.comparing(PackageUpdate::name, String.CASE_INSENSITIVE_ORDER));
            return updates;
        }
        catch (Exception e)
        {   e.printStackTrace();
            return null;
        }
    }

    /**
     * Runs {@code command} in a terminal window, so the user can follow along and enter their password.
     * Waits for the command to finish and returns its exit code, or -1 if it's unknown
     * (the terminal couldn't be started, or was closed before the command finished).
     */
    public static int runInTerminal(String... command)
    {
        Path statusFile = null;
        try
        {
            statusFile = Files.createTempFile("momos-update", ".status");
            List<String> args = new ArrayList<>(List.of(CommandHelper.getTerminal(), "-e", "sh", "-c", TERMINAL_SCRIPT, "momos-update", statusFile.toString()));
            args.addAll(List.of(command));
            Process terminal = new ProcessBuilder(args)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            // The terminal stays open after a failure until the user dismisses it, so the command can finish
            // long before the window closes. Its exit code showing up is what marks it as done
            String status = "";
            while (status.isEmpty() && !terminal.waitFor(STATUS_POLL_INTERVAL_MS, TimeUnit.MILLISECONDS))
            {   status = Files.readString(statusFile).trim();
            }
            if (status.isEmpty())
            {   status = Files.readString(statusFile).trim();
            }
            return status.isEmpty() ? -1 : Integer.parseInt(status);
        }
        catch (Exception e)
        {   e.printStackTrace();
            return -1;
        }
        finally
        {
            if (statusFile != null)
            {   try { Files.deleteIfExists(statusFile); }
                catch (IOException ignored) {}
            }
        }
    }

    private static Result run(String... command) throws IOException, InterruptedException
    {
        Process proc = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String output = new String(proc.getInputStream().readAllBytes());
        return new Result(proc.waitFor(), output);
    }
}

package com.momosoftworks.momos.util.wm;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Runs {@code bspc subscribe <events>} on a background thread, dispatching each line's whitespace-split parts to a callback. */
public final class BspcSubscriber implements AutoCloseable
{
    private final ExecutorService executor;
    private volatile boolean running = true;

    public BspcSubscriber(String threadName, Consumer<String[]> onEvent, String... events)
    {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, threadName);
            t.setDaemon(true);
            return t;
        });
        executor.submit(() -> {
            List<String> args = new ArrayList<>(List.of("bspc", "subscribe"));
            args.addAll(List.of(events));
            try (BufferedReader reader = CommandHelper.executeAndRead(args.toArray(String[]::new)))
            {
                String line;
                while (running && (line = reader.readLine()) != null)
                {   onEvent.accept(line.split(" "));
                }
            }
            catch (Exception e)
            {   if (running) e.printStackTrace();
            }
        });
    }

    @Override
    public void close()
    {   running = false;
        executor.shutdownNow();
    }
}

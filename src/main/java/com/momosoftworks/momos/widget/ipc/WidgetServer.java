package com.momosoftworks.momos.widget.ipc;

import com.momosoftworks.momos.util.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WidgetServer
{
    private final Path socketPath;
    private final Map<String, Runnable> commandHandlers;
    private final ExecutorService executor;
    private volatile boolean running = true;
    private ServerSocketChannel serverChannel;

    public WidgetServer(Path socketPath, Map<String, Runnable> commandHandlers)
    {
        this.socketPath = socketPath;
        this.commandHandlers = commandHandlers;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "widget-server");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Map<String, Runnable> commandHandlers()
    {   return commandHandlers;
    }

    public void start()
    {   executor.submit(this::runServer);
    }

    private void runServer()
    {
        try
        {
            // Clean up old socket file if it exists
            Files.deleteIfExists(socketPath);

            UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
            serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
            serverChannel.bind(address);

            System.out.println("Widget server listening on " + socketPath);

            while (running)
            {
                try
                {
                    SocketChannel client = serverChannel.accept();
                    handleClient(client);
                } catch (IOException e)
                {
                    if (running)
                    {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void handleClient(SocketChannel client)
    {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Channels.newInputStream(client))))
        {
            String command = reader.readLine();
            if (command != null)
            {
                command = command.trim();
                Runnable handler = commandHandlers.get(command);
                if (handler != null)
                {   handler.run();
                }
                else
                {   System.err.println("Unknown command: " + command);
                }
            }
        }
        catch (IOException e)
        {   e.printStackTrace();
        }
    }

    public void stop()
    {
        running = false;
        try
        {
            if (serverChannel != null)
            {   serverChannel.close();
            }
            Files.deleteIfExists(socketPath);
        }
        catch (IOException e)
        {   e.printStackTrace();
        }
        executor.shutdown();
    }

    public static Path createPath(Identifier id)
    {
        String socketFileName = id.namespace() + "_" + id.path() + ".sock";
        return Path.of(System.getProperty("java.io.tmpdir"), socketFileName);
    }
}
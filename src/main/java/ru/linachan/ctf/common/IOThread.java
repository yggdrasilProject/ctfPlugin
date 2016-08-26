package ru.linachan.ctf.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class IOThread implements Runnable {

    private ExploitExecutor executor;

    private InputStream stream;
    private String streamName;

    public IOThread(ExploitExecutor executor, InputStream stream, String streamName) {
        this.executor = executor;
        this.stream = stream;
        this.streamName = streamName;
    }

    @Override
    public void run() {
        try {
            final BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            while (executor.isRunning()) {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        putLine(line);
                    }
                } catch (final IOException ignored) {}
            }
            reader.close();
        } catch (final IOException ignored) {}
    }

    public void putLine(String line) throws IOException {
        executor.putLine(streamName, line);
    }
}
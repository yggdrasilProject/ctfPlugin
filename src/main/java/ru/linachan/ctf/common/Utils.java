package ru.linachan.ctf.common;

import java.io.File;
import java.io.IOException;

public class Utils {

    public static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Do nothing
        }
    }

    public static File createTempDirectory(String postfix) throws IOException {
        final File temp;

        temp = File.createTempFile("ctf", postfix);

        if(!(temp.delete())) {
            throw new IOException("Could not delete temp file: " + temp.getAbsolutePath());
        }

        if(!(temp.mkdir())) {
            throw new IOException("Could not create temp directory: " + temp.getAbsolutePath());
        }

        return temp;
    }
}

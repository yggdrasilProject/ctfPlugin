package ru.linachan.ctf.common;

public class Utils {

    public static void sleep(long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            // Do nothing
        }
    }
}

package jb.test.util;

public class Event {
    private boolean signal = false;

    public synchronized void fire() {
        this.signal = true;
        this.notify();
    }

    public synchronized void waitFor() throws InterruptedException {
        while (!this.signal) wait();
        this.signal = false;
    }
}

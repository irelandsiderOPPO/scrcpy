package com.genymobile.scrcpy;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenStreamerSession {

    private final DesktopConnection connection;
    private Process screenRecordProcess; // protected by 'this'
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final byte[] buffer = new byte[0x10000];

    public ScreenStreamerSession(DesktopConnection connection) {
        this.connection = connection;
    }

    public void streamScreen() throws IOException {
        // screenrecord may not record more than 3 minutes, so restart it on EOF
        while (!stopped.get() && streamScreenOnce()) ;
    }

    /**
     * Starts screenrecord once and relay its output to the desktop connection.
     *
     * @return {@code true} if EOF is reached, {@code false} otherwise (i.e. requested to stop).
     * @throws IOException if an I/O error occurred
     */
    private boolean streamScreenOnce() throws IOException {
        Ln.d("Recording...");
        Process process = startScreenRecord();
        setCurrentProcess(process);
        InputStream inputStream = process.getInputStream();
        int r;
        while ((r = inputStream.read(buffer)) != -1 && !stopped.get()) {
            connection.sendVideoStream(buffer, r);
        }
        return r != -1;
    }

    public void stop() {
        // let the thread stop itself without breaking the video stream
        stopped.set(true);
        killCurrentProcess();
    }

    private static Process startScreenRecord() throws IOException {
        Process process = new ProcessBuilder("screenrecord", "--output-format=h264", "-").start();
        process.getOutputStream().close();
        return process;
    }

    private synchronized void setCurrentProcess(Process screenRecordProcess) {
        this.screenRecordProcess = screenRecordProcess;
    }

    private synchronized void killCurrentProcess() {
        if (screenRecordProcess != null) {
            screenRecordProcess.destroy();
            screenRecordProcess = null;
        }
    }
}

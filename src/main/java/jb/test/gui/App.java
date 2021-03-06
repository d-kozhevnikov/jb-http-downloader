package jb.test.gui;

import jb.test.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class App {

    private Downloader downloader = null;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private JPanel rootPanel;
    private JProgressBar totalProgress;
    private JSpinner threadCountSpinner;
    private JPanel tasksPanel;
    private JButton startButton;
    private JButton stopButton;

    private static String getProgressString(long downloaded, Optional<Long> total) {
        String bytesStr = String.format("%d KB", downloaded / 1024);
        String percentStr;
        if (total.isPresent())
            percentStr = String.format("%.0f", (double) downloaded / total.get() * 100);
        else
            percentStr = "Unknown length";

        return String.format("%s (%s%%)", bytesStr, percentStr);
    }

    private class GUITask extends RandomAccessFileDownloadingTask {
        private final JProgressBar progressBar;
        private final JLabel urlLabel;

        private Optional<Long> length = Optional.empty();

        GUITask(URL url, Path path, JProgressBar progressBar, JLabel urlLabel) {
            super(url, path);
            this.progressBar = progressBar;
            this.urlLabel = urlLabel;
            updateProgressBar(false, null);
        }

        @Override
        public void onStart(Optional<Long> contentLength) throws IOException {
            super.onStart(contentLength);

            length = contentLength;

            SwingUtilities.invokeLater(() -> {
                Font f = urlLabel.getFont();
                urlLabel.setFont(f.deriveFont(f.getStyle() | Font.BOLD));
                updateProgressBar(false, null);
            });
        }

        @Override
        public void onChunkReceived(ByteBuffer chunk) throws IOException {
            super.onChunkReceived(chunk);
            SwingUtilities.invokeLater(() -> updateProgressBar(false, null));
        }

        @Override
        public void onFailure(Throwable cause) {
            super.onFailure(cause);
            processError(cause);
        }

        @Override
        public void onCancel() throws IOException {
            super.onCancel();
            SwingUtilities.invokeLater(() -> updateProgressBar(false, null));
        }

        @Override
        public void onSuccess() throws IOException {
            super.onSuccess();
            length = Optional.of(getWrittenLength());
            SwingUtilities.invokeLater(() -> updateProgressBar(true, null));
        }

        private void processError(Throwable e) {
            e.printStackTrace();
            SwingUtilities.invokeLater(() -> updateProgressBar(false, e));
        }

        private void updateProgressBar(boolean done, Throwable error) {
            boolean indeterminate = !done && error == null && !length.isPresent();
            if (indeterminate) {
                progressBar.setMaximum(0);
                progressBar.setValue(0);
            } else {
                progressBar.setMaximum((int) (length.orElse(0L) / 1024));
                progressBar.setValue((int) (getWrittenLength() / 1024));
            }
            // Doesn't show string on OSX
            //progressBar.setIndeterminate(indeterminate);
            progressBar.setStringPainted(true);

            if (done)
                progressBar.setString(String.format("Done! (%d KB)", getWrittenLength() / 1024));
            else if (error != null)
                progressBar.setString("Error: " + error);
            else
                progressBar.setString(getProgressString(getWrittenLength(), length));

            updateTotalProgressBar();
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        new App().exec(args);
    }

    private void updateTotalProgressBar() {
        if (downloader != null) {
            Progress p = downloader.getProgress();
            if (p.getTotal().isPresent()) {
                totalProgress.setMaximum((int) (p.getTotal().get() / 1024));
                totalProgress.setValue((int) (p.getDownloaded() / 1024));
            } else {
                totalProgress.setMaximum(0);
                totalProgress.setValue(0);
            }

            totalProgress.setString(getProgressString(p.getDownloaded(), p.getTotal()));
        }
    }

    private void stop() {
        if (downloader != null)
            downloader.close();

        try {
            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
        }
    }

    private class ControlsForURL {
        public final URLAndFile url;
        public final JProgressBar progressBar;
        public final JLabel urlLabel;

        private ControlsForURL(URLAndFile url, JProgressBar progressBar, JLabel urlLabel) {
            this.url = url;
            this.progressBar = progressBar;
            this.urlLabel = urlLabel;
        }
    }

    private void exec(String[] args) {
        CmdLineInput input = CmdLineInput.parseCommandLine(args);
        if (input == null) {
            JOptionPane.showMessageDialog(null, CmdLineInput.getUsage());
            return;
        }

        JFrame frame = new JFrame("App");
        frame.setContentPane(rootPanel);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stop();
                super.windowClosing(e);
            }
        });

        ArrayList<ControlsForURL> controls = new ArrayList<>();
        int row = 1;
        for (URLAndFile url : input.getURLs()) {

            GridBagConstraints urlC = new GridBagConstraints();
            urlC.gridx = 0;
            urlC.gridy = row;
            urlC.ipadx = 6;
            JLabel urlLabel = new JLabel(url.getURL().toString());
            tasksPanel.add(urlLabel, urlC);

            GridBagConstraints pathC = new GridBagConstraints();
            pathC.gridx = 1;
            pathC.gridy = row;
            urlC.ipadx = 6;
            tasksPanel.add(new JLabel(url.getPath().toString()), pathC);

            GridBagConstraints pbC = new GridBagConstraints();
            pbC.gridx = 2;
            pbC.gridy = row;
            pbC.fill = GridBagConstraints.BOTH;
            JProgressBar pb = new JProgressBar(0, 100);
            pb.setStringPainted(true);
            tasksPanel.add(pb, pbC);

            controls.add(new ControlsForURL(url, pb, urlLabel));

            row++;
        }

        threadCountSpinner.setModel(new SpinnerNumberModel(input.getNThreads(), 1, Math.max(input.getNThreads(), 32), 1));
        threadCountSpinner.addChangeListener(e -> {
            if (downloader != null)
                downloader.setThreadsCount((Integer) threadCountSpinner.getValue());
        });

        startButton.addActionListener(e ->
                executor.submit(() -> {
                            try {
                                stop();
                                downloader = new DownloaderImpl();
                                downloader.run(controls.stream()
                                        .map(ctrl -> new GUITask(ctrl.url.getURL(), ctrl.url.getPath(), ctrl.progressBar, ctrl.urlLabel))
                                        .collect(Collectors.toList())
                                    , (Integer) threadCountSpinner.getValue());
                            } catch (InterruptedException e1) {
                                Thread.currentThread().interrupt();
                            }
                        }
                ));

        stopButton.addActionListener(e -> {
            if (downloader != null)
                downloader.close();
        });

        frame.pack();
        frame.setVisible(true);
    }
}

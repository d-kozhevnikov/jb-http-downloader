package jb.test.gui;

import jb.test.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class App {

    private final Downloader downloader = new DownloaderImpl();
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

    private class GUITask extends RandomAccessFileURITask {
        private final JProgressBar progressBar;
        private final JLabel urlLabel;

        private Optional<Long> length = Optional.empty();

        GUITask(URI uri, Path path, JProgressBar progressBar, JLabel urlLabel) {
            super(uri, path);
            this.progressBar = progressBar;
            this.urlLabel = urlLabel;
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
                try {
                    downloader.close();
                    executor.shutdown();
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
                super.windowClosing(e);
            }
        });

        ArrayList<GUITask> tasks = new ArrayList<>();
        int row = 1;
        for (URIAndFile uri : input.getUris()) {

            GridBagConstraints uriC = new GridBagConstraints();
            uriC.gridx = 0;
            uriC.gridy = row;
            uriC.ipadx = 6;
            JLabel urlLabel = new JLabel(uri.getUri().toString());
            tasksPanel.add(urlLabel, uriC);

            GridBagConstraints pathC = new GridBagConstraints();
            pathC.gridx = 1;
            pathC.gridy = row;
            uriC.ipadx = 6;
            tasksPanel.add(new JLabel(uri.getPath().toString()), pathC);

            GridBagConstraints pbC = new GridBagConstraints();
            pbC.gridx = 2;
            pbC.gridy = row;
            pbC.fill = GridBagConstraints.BOTH;
            JProgressBar pb = new JProgressBar(0, 100);
            pb.setStringPainted(true);
            tasksPanel.add(pb, pbC);

            tasks.add(new GUITask(uri.getUri(), uri.getPath(), pb, urlLabel));

            row++;
        }

        threadCountSpinner.setModel(new SpinnerNumberModel(input.getNThreads(), 1, Math.max(input.getNThreads(), 32), 1));
        threadCountSpinner.addChangeListener(e -> downloader.setThreadsCount((Integer) threadCountSpinner.getValue()));

        startButton.addActionListener(e ->
                executor.submit(() -> {
                            try {
                                downloader.run(tasks, (Integer) threadCountSpinner.getValue());
                            } catch (InterruptedException e1) {
                                Thread.currentThread().interrupt();
                            }
                        }
                ));

        stopButton.addActionListener(e -> downloader.close());

        frame.pack();
        frame.setVisible(true);
    }
}

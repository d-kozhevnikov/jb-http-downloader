package jb.test.gui;

import jb.test.*;

import javax.swing.*;
import javax.swing.JOptionPane;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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

    private class GUITask extends InMemoryURITask {
        private final Path path;
        private final JProgressBar progressBar;
        private final JLabel urlLabel;

        private Optional<Long> length = Optional.empty();
        private long downloaded = 0;

        GUITask(URI uri, Path path, JProgressBar progressBar, JLabel urlLabel) {
            super(uri);
            this.path = path;
            this.progressBar = progressBar;
            this.urlLabel = urlLabel;
        }

        @Override
        public void onStart(Optional<Long> contentLength) {
            super.onStart(contentLength);

            length = contentLength;

            SwingUtilities.invokeLater(() -> {
                Font f = urlLabel.getFont();
                urlLabel.setFont(f.deriveFont(f.getStyle() | Font.BOLD));

                progressBar.setMinimum(0);
                if (length.isPresent())
                    progressBar.setMaximum(length.get().intValue());
                else
                    progressBar.setIndeterminate(true);

                updateProgressBar(false, null);
            });
        }

        @Override
        public void onChunkReceived(ByteBuffer chunk) {
            super.onChunkReceived(chunk);

            downloaded += chunk.limit();
            SwingUtilities.invokeLater(() -> updateProgressBar(false, null));
        }

        @Override
        public void onSuccess() {
            super.onSuccess();

            try {
                try (FileChannel out = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    out.write(getResult());
                    out.force(true);
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setMaximum(getResult().limit());
                        updateProgressBar(true, null);
                    });
                }
            } catch (IOException e) {
                processError(e);
            }
        }

        @Override
        public void onFailure(Throwable cause) {
            super.onFailure(cause);
            processError(cause);
        }

        @Override
        public void onCancel() {
            super.onCancel();
            downloaded = 0;
            SwingUtilities.invokeLater(() -> updateProgressBar(false, null));
        }

        private void processError(Throwable e) {
            SwingUtilities.invokeLater(() -> updateProgressBar(false, e));
        }

        private void updateProgressBar(boolean done, Throwable error) {
            progressBar.setValue((int) downloaded);

            if (done)
                progressBar.setString("Done!");
            else if (error != null)
                progressBar.setString("Error: " + error);
            else
                progressBar.setString(getProgressString(downloaded, length));

            progressBar.setIndeterminate(!done && error != null && !length.isPresent());
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
        totalProgress.setIndeterminate(!p.getTotal().isPresent());
        if (p.getTotal().isPresent()) {
            totalProgress.setMaximum(p.getTotal().get().intValue());
            totalProgress.setValue((int) p.getDownloaded());
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
                    executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                } catch (Exception e1) {
                    e1.printStackTrace();
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

        startButton.addActionListener(e -> {
            executor.submit(() -> {
                try {
                    downloader.run(tasks, (Integer) threadCountSpinner.getValue());
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            });
        });

        frame.pack();
        frame.setVisible(true);
    }
}

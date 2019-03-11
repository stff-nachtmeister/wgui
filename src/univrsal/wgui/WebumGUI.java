package univrsal.wgui;

import org.apache.commons.exec.*;
import univrsal.wgui.about.About;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Objects;

class ResultHandler extends DefaultExecuteResultHandler {
    private JTextArea ouptut;
    private ExecuteWatchdog watchdog;

    public ResultHandler(JTextArea area, final ExecuteWatchdog watchdog) {
        this.ouptut = area;
        this.watchdog = watchdog;
    }

    @Override
    public void onProcessComplete(int exitValue) {
        super.onProcessComplete(exitValue);
        this.ouptut.append("Success (" + exitValue + ")");
    }

    @Override
    public void onProcessFailed(ExecuteException e) {
        super.onProcessFailed(e);
        if (watchdog != null && watchdog.killedProcess()) {
            this.ouptut.append("The FFmpeg process timed out");
        } else {
            this.ouptut.append(e.getLocalizedMessage());
        }
    }
}

class TextAreaOutputStream extends OutputStream {
    private final JTextArea textArea;
    private final StringBuilder sb = new StringBuilder();

    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void flush() throws IOException {
        super.flush();
        textArea.setText("");
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public void write(int b) {
        if (b == '\r') {
            return;
        }

        if (b == '\n') {
            final String text = sb.toString() + "\n";
            textArea.append(text);
            sb.setLength(0);
        } else {
            sb.append((char) b);
        }
    }
}

public class WebumGUI extends JDialog {

    /*
        Format in order:
            source one path
            source two path: -i %s
            audio bitrate
            volume -af "volume=%sdB"
            video bitrate
            scale filter: -vf scale:%i:%i
            threads
            title
            file size limit
            output
     */
    private static final String FFMPEG_FORMAT = "-i \"%s\" %s -acodec libvorbis -b:a %sk %s -vcodec" +
            " libvpx -b:v %sk %s -threads %s -fs %sM -metadata title=\"%s\" %s";

    private JPanel contentPane;
    private JButton btnOK;
    private JButton btnCancel;
    private JButton btnVidPath;
    private JTextField txtAudioPath;
    private JButton btnAudioPath;
    private JSlider audioQSlider;
    private JSpinner audioQSpinner;
    private JTextField txtVidPath;
    private JSlider videoQSlider;
    private JSpinner videoQSpinner;
    private JTextField txtTitle;
    private JSpinner spinnerResW;
    private JSpinner spinnerResH;
    private JTextField txtCmd;
    private JSlider sliderVolume;
    private JSpinner spinnerVolume;
    private JTextField txtFfmpegPath;
    private JSlider sliderThreads;
    private JLabel lblThreads;
    private JButton btnFfmpegPath;
    private JButton btnOut;
    private JTextField txtOutPath;
    private JTextArea txtOutput;
    private JButton aboutButton;
    private JSlider sliderSize;
    private JLabel lblSize;

    private String makeCommand() {
        int scaleW = (Integer) spinnerResW.getValue();
        int scaleH = (Integer) spinnerResH.getValue();
        boolean ffmpegFound;
        boolean outputExists;
        String audioBitrate = String.valueOf(audioQSlider.getValue());
        String volume = "";
        String videoBitrate = String.valueOf(videoQSlider.getValue());
        String threads = String.valueOf(sliderThreads.getValue());
        String title = txtTitle.getText();
        String scale = "";
        String audio = "";
        String fileSize = String.valueOf(sliderSize.getValue());
        File f = new File(txtFfmpegPath.getText());
        ffmpegFound = f.exists() && !f.isDirectory();
        f = new File(txtOutPath.getText());
        outputExists = f.exists();

        if (scaleH != 0 && scaleW != 0 && (scaleH != -1 && scaleH !=1)) {
            scale = String.format("-vf scale=%s:%s", String.valueOf(scaleW), String.valueOf(scaleH));
        }

        if (!txtFfmpegPath.getText().isEmpty() && !txtVidPath.getText().isEmpty() && !txtOutPath.getText().isEmpty()
            && ffmpegFound && !outputExists) {
            btnOK.setEnabled(true);
        } else {
            btnOK.setEnabled(false);
        }

        if (!txtAudioPath.getText().isEmpty()) {
            audio = "-i \"" + txtAudioPath.getText() + "\"";
        }

        if (sliderVolume.getValue() != 0) {
            volume = "-af \"volume=" + sliderVolume.getValue()+ "dB\"";
        }

        return String.format(FFMPEG_FORMAT, txtVidPath.getText(), audio, audioBitrate,
                volume, videoBitrate, scale, threads, fileSize, title, txtOutPath.getText());
    }

    private String getFilePath(String title) {
        return getFilePath(title, "", "", false);
    }

    private String getFilePath(String title, String filterName, String filter, boolean save) {
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle(title);
        if (!filterName.isEmpty() && !filter.isEmpty())
            jfc.setFileFilter(new FileNameExtensionFilter(filterName, filter));
        if (save)
            jfc.showSaveDialog(this);
        else
            jfc.showOpenDialog(this);
        if (jfc.getSelectedFile() != null)
            return jfc.getSelectedFile().getAbsolutePath();
        return "";
    }


    private WebumGUI(Dialog d) {
        super(d);
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        Image icon = new ImageIcon(Objects.requireNonNull(classloader.getResource("webum.png"))).getImage();
        setIconImage(icon);
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(btnOK);
        makeCommand();

        btnOK.addActionListener(e -> onOK());
        btnCancel.addActionListener(e -> onCancel());

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        if (System.getProperty("os.name").startsWith("Windows")) {
            txtFfmpegPath.setText(".\\ffmpeg.exe");
        }

        SpinnerNumberModel audioLimit = new SpinnerNumberModel();
        audioLimit.setMaximum(audioQSlider.getMaximum());
        audioLimit.setMinimum(audioQSlider.getMinimum());
        audioQSpinner.setModel(audioLimit);

        SpinnerNumberModel videoLimit = new SpinnerNumberModel();
        videoLimit.setMaximum(videoQSlider.getMaximum());
        videoLimit.setMinimum(videoQSlider.getMinimum());
        videoQSpinner.setModel(videoLimit);

        SpinnerNumberModel volumeLimit = new SpinnerNumberModel();
        volumeLimit.setMaximum(sliderVolume.getMaximum());
        volumeLimit.setMinimum(sliderVolume.getMinimum());
        spinnerVolume.setModel(volumeLimit);

        SpinnerNumberModel scaleLimit = new SpinnerNumberModel();
        scaleLimit.setMinimum(-1);
        scaleLimit.setMaximum(0xffff);
        spinnerResW.setModel(scaleLimit);
        spinnerResH.setModel(scaleLimit);

        audioQSlider.addChangeListener(e -> {
            audioQSpinner.setValue(audioQSlider.getValue()); txtCmd.setText(makeCommand()); });
        audioQSpinner.addChangeListener(e -> { audioQSlider.setValue((Integer) audioQSpinner.getValue());
            txtCmd.setText(makeCommand());});

        videoQSlider.addChangeListener(e -> { videoQSpinner.setValue(videoQSlider.getValue());
            txtCmd.setText(makeCommand()); });
        videoQSpinner.addChangeListener(e -> { videoQSlider.setValue((Integer) videoQSpinner.getValue());
        txtCmd.setText(makeCommand()); });

        sliderVolume.addChangeListener(e -> { spinnerVolume.setValue(sliderVolume.getValue());
            txtCmd.setText(makeCommand()); });
        spinnerVolume.addChangeListener(e -> { sliderVolume.setValue((Integer) spinnerVolume.getValue());
            txtCmd.setText(makeCommand()); });

        videoQSpinner.setValue(videoQSlider.getValue());
        audioQSpinner.setValue(audioQSlider.getValue());

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            SwingUtilities.updateComponentTreeUI(contentPane);

        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            e.printStackTrace();
        }

        setTitle("Webum GeeYouEye");
        int cpus = Runtime.getRuntime().availableProcessors();
        sliderThreads.setMaximum(cpus + cpus / 4 );
        sliderThreads.addChangeListener(e -> { lblThreads.setText(sliderThreads.getValue() + " Thread(s)");
            txtCmd.setText(makeCommand()); });

        btnVidPath.addActionListener(e -> {
            txtVidPath.setText(getFilePath("Choose a video file"));
            txtCmd.setText(makeCommand());
        });

        btnFfmpegPath.addActionListener(e -> {
            txtFfmpegPath.setText(getFilePath("Select FFmpeg executable", "Executable",
                    "exe", false));
            txtCmd.setText(makeCommand());
        });

        btnAudioPath.addActionListener(e -> {
                txtAudioPath.setText(getFilePath("Choose an audio file"));
                txtCmd.setText(makeCommand());

        });
        btnOut.addActionListener(e -> {
                txtOutPath.setText(getFilePath("Choose an output file", "WebM", "webm", true));
                txtCmd.setText(makeCommand());
        });

        spinnerResW.addChangeListener(this::stateChanged);
        spinnerResH.addChangeListener(this::stateChanged);


        new FileDrop(txtVidPath, files -> {
            if (files.length > 0) {
                txtVidPath.setText(files[0].getAbsolutePath());
                txtCmd.setText(makeCommand());
            }
        });

        new FileDrop(txtAudioPath, files -> {
            if (files.length > 0) {
                txtAudioPath.setText(files[0].getAbsolutePath());
                txtCmd.setText(makeCommand());
            }
        });

        new FileDrop(txtFfmpegPath, files -> {
            if (files.length > 0) {
                txtFfmpegPath.setText(files[0].getAbsolutePath());
                txtCmd.setText(makeCommand());
            }
        });

        new FileDrop(txtOutPath, files -> {
            if (files.length > 0) {
                txtOutPath.setText(files[0].getAbsolutePath());
                txtCmd.setText(makeCommand());
            }
        });

        txtTitle.addActionListener(e -> txtCmd.setText(makeCommand()));
        aboutButton.addActionListener(e -> {
            About about = new About();
            about.pack();
            about.setMinimumSize(about.getSize());
            about.setLocationRelativeTo(null);
            about.setVisible(true);
        });
        sliderSize.addChangeListener(e -> {lblSize.setText(sliderSize.getValue() + " MB"); txtCmd.setText(makeCommand()); });
    }

    private void doConversion() {
        CommandLine cmdLine = new CommandLine(txtFfmpegPath.getText());
        cmdLine.addArguments(txtCmd.getText());
        Executor exe = new DefaultExecutor();
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60*1000);

        exe.setExitValue(1);
        exe.setWatchdog(watchdog);
        exe.setStreamHandler(new PumpStreamHandler(new PrintStream(
                new TextAreaOutputStream(txtOutput))));

        try {
            exe.execute(cmdLine, new ResultHandler(txtOutput, watchdog));
        } catch (IOException e) {
            e.printStackTrace();
            txtOutput.append(e.getLocalizedMessage());
        }
    }

    private void onOK() {
        new Thread(this::doConversion).start();
    }

    private void onCancel() {
        dispose();
    }


    public static void main(String[] args) {
        WebumGUI dialog = new WebumGUI((Dialog)null);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        System.exit(0);
    }

    private void stateChanged(ChangeEvent e) {
        txtCmd.setText(makeCommand());
    }
}

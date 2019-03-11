package univrsal.wgui;

import org.apache.commons.exec.*;
import univrsal.wgui.about.About;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;
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
    private JTextArea textArea;

    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) throws IOException {
        textArea.append(String.valueOf((char)b));
        textArea.setCaretPosition(textArea.getDocument().getLength());
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
    private JSpinner spinnerResW;
    private JSpinner spinnerResH;
    private JCheckBox cbCrop;
    private JSpinner spinnerX;
    private JSpinner spinnerY;


    private String makeCommand() {
        int scaleW = (int) spinnerResW.getValue();
        int scaleH = (int) spinnerResH.getValue();
        boolean crop = cbCrop.isSelected();
        int cropX = (int) spinnerX.getValue();
        int cropY = (int) spinnerY.getValue();

        String audioBitrate = String.valueOf(audioQSlider.getValue());
        String volume = "";
        String videoBitrate = String.valueOf(videoQSlider.getValue());
        String threads = String.valueOf(sliderThreads.getValue());
        String title = txtTitle.getText();
        String scale = "";
        String audio = "";
        String fileSize = String.valueOf(sliderSize.getValue());

        if (!(scaleH == 0 || scaleW == 0) && !(scaleH == -1 && scaleW == -1)) {
            if (crop) {
                scale = String.format("-filter:v \"crop=%s:%s:%s:%s\"", scaleW, scaleH, cropX, cropY);
            } else {
                scale = String.format("-vf scale=%s:%s", String.valueOf(scaleW), String.valueOf(scaleH));
            }
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

        SpinnerNumberModel scaleLimit = new SpinnerNumberModel(0, -1, 0xffff, 1);
        SpinnerNumberModel scaleLimit2 = new SpinnerNumberModel(0, -1, 0xffff, 1);
        SpinnerNumberModel cropX = new SpinnerNumberModel(0, 0, 0xffff, 1);
        SpinnerNumberModel cropY = new SpinnerNumberModel(0, 0, 0xffff, 1);

        spinnerResW.setModel(scaleLimit);
        spinnerResH.setModel(scaleLimit2);
        spinnerX.setModel(cropX);
        spinnerY.setModel(cropY);

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

        spinnerResW.addChangeListener(e -> txtCmd.setText(makeCommand()));
        spinnerResH.addChangeListener(e -> txtCmd.setText(makeCommand()));


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
        cbCrop.addActionListener(e -> {
            spinnerX.setEnabled(cbCrop.isSelected());
            spinnerY.setEnabled(cbCrop.isSelected());
            txtCmd.setText(makeCommand());
        });
        spinnerX.addChangeListener(e -> txtCmd.setText(makeCommand()));
        spinnerY.addChangeListener(e -> txtCmd.setText(makeCommand()));

    }

    private void doConversion() {
        CommandLine cmdLine = new CommandLine(txtFfmpegPath.getText());
        cmdLine.addArguments(txtCmd.getText());
        Executor exe = new DefaultExecutor();
        ExecuteWatchdog watchdog = new ExecuteWatchdog(60*1000);

        exe.setExitValue(1);
        exe.setWatchdog(watchdog);
        exe.setStreamHandler(new PumpStreamHandler(System.out));

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
        PrintStream pS = new PrintStream(new TextAreaOutputStream(dialog.txtOutput));
        System.setOut(pS);
        System.setErr(pS);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        System.exit(0);
    }
}

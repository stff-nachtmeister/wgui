package univrsal.wgui.about;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

public class About extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JTextPane gplText;
    private JButton getFFmpegButton;
    private JButton openGitHubRepoButton;

    private void openURL(String url) {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }
    }

    public About() {
        setTitle("About");
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        Image icon = new ImageIcon(Objects.requireNonNull(classloader.getResource("webum.png"))).getImage();
        setIconImage(icon);
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        openGitHubRepoButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openURL("https://github.com/univrsal/webum");
            }
        });
        getFFmpegButton.addComponentListener(new ComponentAdapter() {
        });
        getFFmpegButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                openURL("https://ffmpeg.org/download.html");
            }
        });
    }

    private void onOK() {
        dispose();
    }

    private void onCancel() {
        dispose();
    }
}

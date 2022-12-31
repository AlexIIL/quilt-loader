package org.quiltmc.loader.impl.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

public class QuiltProgressWindow {

	String state = "Starting...";
	int percent = 0;

	public QuiltProgressWindow() {
		try {
			// Set MacOS specific system props
			System.setProperty("apple.awt.application.appearance", "system");
			System.setProperty("apple.awt.application.name", "Quilt Loader");

			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			SwingUtilities.invokeAndWait(this::open);
		} catch (Exception e) {
			throw new Error(e);
		}
	}

	public void setProgress(String state, int percent) {
		this.state = state;
		this.percent = percent;
		SwingUtilities.invokeLater(() -> {
			labelState.setText(" " + this.state);
			labelPercentage.setText(this.percent + "% ");
			bar.setValue(this.percent);
			window.repaint();
		});
	}

	JFrame window;
	JLabel labelState;
	JLabel labelPercentage;
	JProgressBar bar;

	private void open() {
		window = new JFrame();
		window.setVisible(false);
		window.setTitle("Quilt Loader: starting");

		try {
			List<BufferedImage> images = new ArrayList<>();
			images.add(loadImage("/ui/icon/quilt_x16.png"));
			images.add(loadImage("/ui/icon/quilt_x128.png"));
			window.setIconImages(images);
			setTaskBarImage(images.get(1));
		} catch (IOException e) {
			e.printStackTrace();
		}

		// TODO: change this back to normal after debugging
		window.setMinimumSize(new Dimension(120, 1));
		window.setPreferredSize(new Dimension(480, 320));
		window.setLocationByPlatform(true);
		window.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		window.addWindowListener(new WindowAdapter() {

		});

		Container contentPane = window.getContentPane();
		contentPane.setLayout(new BorderLayout());

		contentPane.setBackground(new Color(0x1b112b));

		JPanel top = new JPanel();
		top.setLayout(new BorderLayout());
		top.setBackground(new Color(0x1b112b));
		contentPane.add(top, BorderLayout.NORTH);

		labelState = new JLabel("Starting...");
		top.add(labelState, BorderLayout.WEST);
		labelState.setFont(labelState.getFont().deriveFont(30f));
		labelState.setForeground(new Color(0x55b6fd));

		labelPercentage = new JLabel("0%");
		top.add(labelPercentage, BorderLayout.EAST);
		labelPercentage.setFont(labelPercentage.getFont().deriveFont(30f));
		labelPercentage.setForeground(new Color(0x55b6fd));

		try {
			BufferedImage image = ImageIO.read(
				new File(
					"/home/alexiil/Documents/dev/minecraft/1.12/Quilt-Toolchain/art/banners/png/quilt-invite-splash.png"
				)
			);
			// loadImage("/ui/splash/background.png");
			JLabel label = new JLabel(new ImageIcon(image));
			contentPane.add(label);
			label.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					Dimension size = label.getSize();
					double ws = size.width / (double) image.getWidth();
					double hs = size.height / (double) image.getHeight();
					double scale = Math.min(ws, hs);
					Image resized = image.getScaledInstance(
						(int) (image.getWidth() * scale), (int) (image.getHeight() * scale), Image.SCALE_FAST
					);
					label.setIcon(new ImageIcon(resized));
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

		bar = new JProgressBar();
		bar.setStringPainted(true);
		contentPane.add(bar, BorderLayout.SOUTH);

		window.pack();
		window.setVisible(true);
		window.requestFocus();
	}

	static BufferedImage loadImage(String str) throws IOException {
		return ImageIO.read(loadStream(str));
	}

	private static InputStream loadStream(String str) throws FileNotFoundException {
		InputStream stream = QuiltProgressWindow.class.getResourceAsStream(str);

		if (stream == null) {
			throw new FileNotFoundException(str);
		}

		return stream;
	}

	private static void setTaskBarImage(Image image) {
		try {
			// TODO Remove reflection when updating past Java 8
			Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
			Method getTaskbar = taskbarClass.getDeclaredMethod("getTaskbar");
			Method setIconImage = taskbarClass.getDeclaredMethod("setIconImage", Image.class);
			Object taskbar = getTaskbar.invoke(null);
			setIconImage.invoke(taskbar, image);
		} catch (Exception e) {
			// Ignored
		}
	}
}

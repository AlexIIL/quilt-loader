package org.quiltmc.loader.impl.gui;

import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonButton;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonGuiMessage;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltJsonGuiTreeTab;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltStatusNode;
import org.quiltmc.loader.impl.gui.QuiltJsonGui.QuiltTreeWarningLevel;
import org.quiltmc.loader.impl.gui.QuiltMainWindow.IconInfo;

/** SWT version of {@link QuiltMainWindow} */
class QuiltMainDisplay {

	final Display display;
	final Shell shell;
	Font boldFont, italicFont;

	static void open(QuiltJsonGui tree, boolean shouldWait) throws Exception {

		Display display = new Display();

		try {
			QuiltMainDisplay quilt = new QuiltMainDisplay(display, tree);

			quilt.shell.pack();
			quilt.shell.open();
			while (!quilt.shell.isDisposed()) {
				if (!display.readAndDispatch()) display.sleep();
			}
		} finally {
			display.dispose();
		}
	}

	public QuiltMainDisplay(Display display, QuiltJsonGui tree) {

		this.display = display;
		this.shell = new Shell(display);

		GridLayout gridLayout = new GridLayout(1, false);
		gridLayout.marginWidth = 0;
		gridLayout.marginHeight = 0;
		gridLayout.verticalSpacing = 0;
		gridLayout.horizontalSpacing = 0;
		shell.setLayout(gridLayout);

		shell.setText(tree.title);

		try {
			List<Image> images = new ArrayList<>();
			images.add(loadImage("/ui/icon/quilt_x16.png"));
			images.add(loadImage("/ui/icon/quilt_x128.png"));
			shell.setImages(images.toArray(new Image[0]));
		} catch (IOException e) {
			e.printStackTrace();
		}

		Composite composite = new Composite(shell, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		composite.setLayout(new GridLayout(1, false));

		if (tree.mainText != null && !tree.mainText.isEmpty()) {
			Label errorLabel = new Label(composite, SWT.NONE);
			errorLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
			errorLabel.setText(tree.mainText);
		}

		IconSet icons = new IconSet();

		if (tree.tabs.isEmpty() && tree.messages.isEmpty()) {
			QuiltJsonGuiTreeTab tab = new QuiltJsonGuiTreeTab("Opening Errors");
			tab.addChild("No tabs provided! (Something is very broken)").setError();
			createTreePanel(composite, tab.node, tab.filterLevel, icons);
		} else if (tree.tabs.size() == 1 && tree.messages.isEmpty()) {
			QuiltJsonGuiTreeTab tab = tree.tabs.get(0);
			createTreePanel(composite, tab.node, tab.filterLevel, icons);
		} else if (tree.tabs.isEmpty()) {
			createMessagesPanel(composite, icons, tree.messages);
		} else {

			TabFolder tabFolder = new TabFolder(composite, SWT.TOP);
			tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			if (!tree.messages.isEmpty()) {

				TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
				tabItem.setText(tree.messagesTabName);
				tabItem.setControl(createMessagesPanel(tabFolder, icons, tree.messages));
			}

			for (QuiltJsonGuiTreeTab tab : tree.tabs) {

				TabItem tabItem = new TabItem(tabFolder, SWT.NONE);
				tabItem.setText(tab.node.name);
				tabItem.setControl(createTreePanel(tabFolder, tab.node, tab.filterLevel, icons));
			}

			tabFolder.pack();
		}

		if (!tree.buttons.isEmpty()) {
			Composite btnComp = new Composite(composite, SWT.NONE);
			btnComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
			btnComp.setLayout(new GridLayout(tree.buttons.size(), false));

			for (QuiltJsonButton btn : tree.buttons) {
				Button button = new Button(btnComp, SWT.PUSH);
				button.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
				button.setText(btn.text);
				button.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {

					}
				});

			}
		}

		shell.setMinimumSize(shell.computeSize(SWT.DEFAULT, SWT.DEFAULT).x, 300);
	}

	private Control createMessagesPanel(Composite parent, IconSet icons, List<QuiltJsonGuiMessage> messages) {

		ScrolledComposite scroll = new ScrolledComposite(parent, SWT.V_SCROLL);
		scroll.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scroll.setLayout(new GridLayout(1, false));
		scroll.setExpandHorizontal(true);
		scroll.setExpandVertical(true);

		Composite inner = new Composite(scroll, SWT.NONE);
		inner.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		inner.setLayout(new GridLayout(1, false));

		inner.setBackground(new Color(255, 255, 255));

		for (QuiltJsonGuiMessage message : messages) {
			createMessagePanel(inner, icons, message);
		}

		scroll.setContent(inner);
		scroll.setMinSize(inner.computeSize(SWT.DEFAULT, SWT.DEFAULT));

		return scroll;
	}

	private void createMessagePanel(Composite parent, IconSet icons, QuiltJsonGuiMessage message) {
		Composite composite = new Composite(parent, SWT.BORDER);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		composite.setLayout(new GridLayout(1, false));

		Composite top = new Composite(composite, SWT.NONE);
		top.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		top.setLayout(new GridLayout(2, false));

		Label icon = new Label(top, SWT.NONE);
		icon.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));
		icon.setImage(icons.get(IconInfo.parse(message.iconType), 32));

		Label title = new Label(top, SWT.NONE);
		title.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, true, false));
		title.setText(message.title);
		if (boldFont == null) {
			FontData[] boldFontData = title.getFont().getFontData().clone();
			for (int i = 0; i < boldFontData.length; i++) {
				boldFontData[i].setStyle(SWT.BOLD);
			}
			boldFont = new Font(display, boldFontData);
		}
		title.setFont(boldFont);

		for (String desc : message.description) {
			Label label = new Label(composite, SWT.NONE);
			label.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, true, false));
			label.setText(desc);
		}

		for (String desc : message.additionalInfo) {
			Label label = new Label(composite, SWT.NONE);
			label.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, true, false));
			label.setText(desc);
			if (italicFont == null) {
				FontData[] italicFontData = label.getFont().getFontData().clone();
				for (int i = 0; i < italicFontData.length; i++) {
					italicFontData[i].setStyle(SWT.ITALIC);
				}
				italicFont = new Font(display, italicFontData);
			}
			label.setFont(italicFont);
		}

		if (!message.buttons.isEmpty()) {

			Composite composite2 = new Composite(composite, SWT.NONE);
			composite2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
			composite2.setLayout(new GridLayout(message.buttons.size(), false));

			for (QuiltJsonButton btn : message.buttons) {
				Button button = new Button(composite2, SWT.PUSH);
				button.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
				button.setText(btn.text);
				button.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {

					}
				});
			}
		}
	}

	private Control createTreePanel(Composite parent, QuiltStatusNode node, QuiltTreeWarningLevel filterLevel,
		IconSet icons) {

		Tree tree = new Tree(parent, SWT.SINGLE | SWT.FULL_SELECTION);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tree.setLinesVisible(true);

		for (QuiltStatusNode child : node.children) {
			addTreeItem(tree, child, icons, filterLevel);
		}

		return tree;
	}

	private void addTreeItem(Object parent, QuiltStatusNode node, IconSet icons, QuiltTreeWarningLevel filterLevel) {
		final TreeItem item;

		if (parent instanceof Tree) {
			item = new TreeItem((Tree) parent, SWT.NONE);
		} else {
			item = new TreeItem((TreeItem) parent, SWT.NONE);
		}

		item.setText(node.name);
		IconInfo iconInfo = IconInfo.fromNode(node);
		if (!iconInfo.mainPath.isEmpty()) {
			item.setImage(icons.get(iconInfo));
		}

		if (node.expandByDefault) {
			item.setExpanded(true);
		}

		for (QuiltStatusNode child : node.children) {
			if (filterLevel.isHigherThan(child.getMaximumWarningLevel())) {
				continue;
			}
			addTreeItem(item, child, icons, filterLevel);
		}
	}

	private Image loadImage(String str) throws IOException {
		return new Image(display, loadStream(str));
	}

	private static InputStream loadStream(String str) throws FileNotFoundException {
		InputStream stream = QuiltMainWindow.class.getResourceAsStream(str);

		if (stream == null) {
			throw new FileNotFoundException(str);
		}

		return stream;
	}

	final class IconSet {

		Image missingIcon = null;

		/** Map of IconInfo -> Integer Size -> Real Icon. */
		private final Map<IconInfo, Map<Integer, Image>> icons = new HashMap<>();

		public Image get(IconInfo info) {
			return get(info, 16);
		}

		public Image get(IconInfo info, int scale) {
			// TODO: HDPI

			Map<Integer, Image> map = icons.computeIfAbsent(info, k -> new HashMap<>());

			Image icon = map.get(scale);

			if (icon == null) {
				try {
					icon = loadIcon(info, scale);
				} catch (IOException e) {
					e.printStackTrace();
					icon = missingIcon();
				}

				map.put(scale, icon);
			}

			return icon;
		}

		private Image missingIcon() {
			if (missingIcon == null) {
				ImageData data = new ImageData(16, 16, 32, new PaletteData(0xFF_00_00, 0xFF_00, 0xFF));
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						data.setPixel(x, y, 0xff_ff_f2);
					}
				}

				for (int i = 0; i < 16; i++) {
					data.setPixel(0, i, 0x22_22_22);
					data.setPixel(15, i, 0x22_22_22);
					data.setPixel(i, 0, 0x22_22_22);
					data.setPixel(i, 15, 0x22_22_22);
				}

				for (int i = 3; i < 13; i++) {
					data.setPixel(i, i, 0x9b_00_00);
					data.setPixel(i, 16 - i, 0x9b_00_00);
				}

				missingIcon = new Image(display, data);
			}

			return missingIcon;
		}

		private Image loadIcon(IconInfo info, int scale) throws IOException {
			BufferedImage img = QuiltMainWindow.generateIcon(info, scale);

			// BufferedImage.TYPE_INT_ARGB specifies that it always uses a DirectColorModel
			DirectColorModel model = (DirectColorModel) img.getColorModel();
			ImageData data = new ImageData(
				scale, scale, 32, new PaletteData(model.getRedMask(), model.getGreenMask(), model.getBlueMask())
			);

			for (int y = 0; y < scale; y++) {
				for (int x = 0; x < scale; x++) {
					int rgb = img.getRGB(x, y);
					data.setPixel(x, y, rgb);
					data.setAlpha(x, y, (rgb >> 24) & 0xff);
				}
			}

			return new Image(display, data);
		}
	}
}

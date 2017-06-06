package selfpi;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class Gui extends JPanel {
	public static final String IDLE_TXT = "<html><center>Press<br>the button";
	private static final String CARD_IDLE = "idle";
	private static final String CARD_HIST = "hist";
	private static final String CARD_COUNT = "count";
	private static final String CARD_SHARE = "share";
	private static final String CARD_IMPORT = "import";
	
	private static final String SCREEN_SHARE_HORIZONTAL = "share_screen_horizontal.png";
	private static final String SCREEN_SHARE_VERTICAL = "share_screen_vertical.png";
	private static final String SCREEN_IDLE_HORIZONTAL = "idle_screen_horizontal.png";
	private static final String SCREEN_IDLE_VERTICAL = "idle_screen_vertical.png";
		
	private Thread countDownThread;
	private Thread shareProgressThread;
	
	private boolean verticalOrientation = false;

	private NumberFormat formatter = new DecimalFormat("#0.0");     
	
	public Gui(boolean verticalOrientation) {
		this.verticalOrientation = verticalOrientation;
		setLayout(new BorderLayout());
		setBackground(Color.white);
		if (verticalOrientation) {
			add(getDummyPanel(), BorderLayout.CENTER);
			add(getMainPanel(), BorderLayout.SOUTH);
		} else {
			add(getDummyPanel(), BorderLayout.WEST);
			add(getMainPanel(), BorderLayout.CENTER);
		}
	}

	public void launchCountDown() {
		if (countDownThread != null && countDownThread.isAlive()) return;
		
		countDownThread = new Thread(new CountDown());
		countDownThread.setPriority(Thread.MAX_PRIORITY);
		countDownThread.start();
	}
	
	public void launchShareProgress() {
		if (shareProgressThread != null && shareProgressThread.isAlive()) return;
		
		shareProgressThread = new Thread(new ShareProgress());
		shareProgressThread.setPriority(Thread.MAX_PRIORITY);
		shareProgressThread.start();
	}

	public void setMode(final SelfpiState state) {
		System.out.println("GUI Mode: "+state);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (state == SelfpiState.HISTORIC_SOUVENIR ||
						state == SelfpiState.HISTORIC_WINNER ||
						state == SelfpiState.HISTORIC_DSLR){
					getCardLayout().show(Gui.this.getMainPanel(), CARD_HIST);
				}
				if (state == SelfpiState.IDLE) {
					getCardLayout().show(Gui.this.getMainPanel(), CARD_IDLE);
				}
				if (state == SelfpiState.TAKING_PICT) {
					getCardLayout().show(Gui.this.getMainPanel(), CARD_COUNT);
					launchCountDown();
				}
				if (state == SelfpiState.WAIT_FOR_SHARE) {
					getShareProgressBar().setValue(100);
					getCardLayout().show(Gui.this.getMainPanel(), CARD_SHARE);
					launchShareProgress();
				}
				if (state == SelfpiState.IMPORT_DSLR) {
					getCardLayout().show(Gui.this.getMainPanel(), CARD_IMPORT);
				}
			}
		});
	}

	public void displayHistoricImages(final File[] listOfFiles) throws InvocationTargetException, InterruptedException{
		System.out.println("Display Historic Images of "+SelfPi.selfpiState);
		if (listOfFiles == null || listOfFiles.length < 1) return;

		for (int i = 0; i < listOfFiles.length; i++) {
			final int index = i;
			
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					JLabel l = getHistoryImages()[index];
					l.setText(""+(index+1));
					l.setIcon(null);
					getHistoryImagesPanel().repaint();
				}
			});
			
			if (listOfFiles[i] == null) continue;

			System.out.println("displaying: "+listOfFiles[i].getPath());
			
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					BufferedImage image = null;
					try {
						image = ImageIO.read(listOfFiles[index]);
					} catch (IOException e) {
						e.printStackTrace();
					}
					int width = 160;
					int height = (int)(width*((double)image.getHeight()/image.getWidth()));
					BufferedImage imageResized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
					Graphics2D g = imageResized.createGraphics();
//					g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//					g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					g.drawImage(image, 0, 0, width, height, null);
					g.dispose();
					JLabel l = getHistoryImages()[index];
					l.setText((index+1)+": "+listOfFiles[index].getName());
//					Image imageResized  = new ImageIcon(listOfFiles[index].getPath()).getImage().getScaledInstance(160, height, Image.SCALE_SMOOTH); 
					l.setIcon(new ImageIcon(imageResized));
					System.out.println("add 1 to hist panel");
					getHistoryImagesPanel().repaint();
				}		
			});
		}
	}

	public void setImportText(final String text) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				getImportTextPane().setText(text);
			}
		});
		
	}

	private CardLayout cardLayout;
	private CardLayout getCardLayout() {
		if (cardLayout == null) {
			cardLayout = new CardLayout();
		}
		return cardLayout;
	}

	private JPanel dummyPanel;
	private JPanel getDummyPanel() {
		if (dummyPanel == null) {
			dummyPanel = new JPanel();
			dummyPanel.setPreferredSize(new Dimension(SelfPi.screenHeight, SelfPi.screenHeight));
		}
		return dummyPanel;
	}

	private JPanel mainPanel;
	private JPanel getMainPanel() {
		if (mainPanel == null) {
			mainPanel = new JPanel(getCardLayout());
			mainPanel.setPreferredSize(new Dimension(1024, 256));
			mainPanel.add(getIdleLabel(), CARD_IDLE);
			mainPanel.add(getHistoryPanel(), CARD_HIST);
			mainPanel.add(getSharePanel(), CARD_SHARE);
			mainPanel.add(getCountLabel(), CARD_COUNT);
			mainPanel.add(getImportTextPane(), CARD_IMPORT);
		}
		return mainPanel;
	}

	private JLabel textLabel;
	private JLabel getIdleLabel() {
		if(textLabel == null) {
			textLabel = new JLabel();
			if (verticalOrientation) {
				textLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+SCREEN_IDLE_VERTICAL));
			} else {
				textLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+SCREEN_IDLE_HORIZONTAL));
			}
//			textLabel = new JLabel(IDLE_TXT);
//			textLabel.setHorizontalTextPosition(SwingConstants.CENTER);
//			textLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 110));
//			textLabel.setHorizontalAlignment(JLabel.CENTER);
		}
		return textLabel;
	}
	
	private JLabel countLabel;
	private JLabel getCountLabel() {
		if(countLabel == null) {
			countLabel = new JLabel(formatter.format(SelfPi.countdownLength));
			countLabel.setHorizontalTextPosition(SwingConstants.CENTER);
			if (verticalOrientation) countLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 300));
			else countLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 200));
			countLabel.setHorizontalAlignment(JLabel.CENTER);
		}
		return countLabel;
	}
	
	private JPanel sharePanel;
	private JPanel getSharePanel() {
		if(sharePanel == null) {
			sharePanel = new JPanel(new BorderLayout());
			sharePanel.add(getShareScreenLabel(), BorderLayout.CENTER);
			if (verticalOrientation) {
				sharePanel.add(getShareProgressBar(), BorderLayout.WEST);
			} else {
				sharePanel.add(getShareProgressBar(), BorderLayout.SOUTH);
			}
		}
		return sharePanel;
	}
	
	private JEditorPane importTextPane;
	private JEditorPane getImportTextPane() {
		if (importTextPane == null) {
			importTextPane = new JEditorPane();
			importTextPane.setBackground(Color.BLACK);
			importTextPane.setForeground(Color.white);
			importTextPane.setFont(new Font(Font.DIALOG, Font.PLAIN, 9));
		}
		return importTextPane;
	}

	private JProgressBar shareProgressBar;
	private JProgressBar getShareProgressBar() {
		if (shareProgressBar == null) {
			shareProgressBar = new JProgressBar(0, 100);
			if (verticalOrientation) {
				shareProgressBar.setOrientation(SwingConstants.VERTICAL);
				shareProgressBar.setPreferredSize(new Dimension(80, 256));
			} else {
				shareProgressBar.setOrientation(SwingConstants.HORIZONTAL);
				shareProgressBar.setPreferredSize(new Dimension(256, 80));
			}
			shareProgressBar.setBackground(Color.white);
			shareProgressBar.setForeground(Color.black);
		}
		return shareProgressBar;
	}
	
	private JLabel shareScreenLabel;
	private JLabel getShareScreenLabel() {
		if (shareScreenLabel == null) {
			shareScreenLabel = new JLabel();
			if (verticalOrientation) {
				shareScreenLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+SCREEN_SHARE_VERTICAL));
			} else {
				shareScreenLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+SCREEN_SHARE_HORIZONTAL));
			}
		}
		return shareScreenLabel;
	}

	private JPanel historyPanel;
	private JPanel getHistoryPanel() {
		if (historyPanel == null) {
			historyPanel = new JPanel(new BorderLayout());
			historyPanel.add(getHistoryText(), BorderLayout.SOUTH);
			historyPanel.add(getHistoryImagesPanel(), BorderLayout.CENTER);
		}
		return historyPanel;
	}

	private JLabel historyText;
	private JLabel getHistoryText() {
		if (historyText == null){
			historyText = new JLabel();
			if (verticalOrientation) {
				historyText.setText("<-: Previous, ->: Next, 1-6 : Print one, p: Print All, esc: Quit");
			} else {
				historyText.setText("<html><center> &lt;-: Previous<br>-&gt;: Next<br>p: Print<br>esc: Quit");
			}
			historyText.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
			historyText.setVisible(true);
		}
		return historyText;
	}

	private JPanel historyImagesPanel;
	private JPanel getHistoryImagesPanel() {
		if (historyImagesPanel == null) {
//			FlowLayout fl = new FlowLayout(FlowLayout.LEFT);
			GridLayout gl = new GridLayout(1, 6);
			historyImagesPanel = new JPanel(gl);
			historyImagesPanel.setBackground(Color.black);
		}
		return historyImagesPanel;
	}
	
	private JLabel[] historyImages;
	private JLabel[] getHistoryImages() {
		if (historyImages == null) {
			historyImages = new JLabel[6];
			for (int i = 0; i < historyImages.length; i++) {
				JLabel l = new JLabel();
				l.setForeground(Color.white);
				l.setVerticalTextPosition(JLabel.BOTTOM);
				l.setHorizontalTextPosition(JLabel.CENTER);
				l.setText(""+(i+1));
				l.setIcon(null);
				getHistoryImagesPanel().add(l);
				historyImages[i] = l;
			}
		}
		return historyImages;
	}

	private class CountDown implements Runnable {
		
		@Override
		public void run() {
			for (double counter = SelfPi.countdownLength; counter >= 0; counter -= 0.1) {
				final String count = "<html><center>"+formatter.format(counter);
				
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						getCountLabel().setText(count);
					}
				});
				
				try { Thread.sleep(100); } catch (InterruptedException e) {}
			}
			getCountLabel().setText("0.0");
		}
	}
	
	private class ShareProgress implements Runnable {
		
		@Override
		public void run() {
			for (int counter = 100; counter >= 0; counter -= 1) {
				final int value = counter;
				
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						getShareProgressBar().setValue(value);
					}
				});
				
				try { Thread.sleep(100); } catch (InterruptedException e) {}
			}
		}
	}

}

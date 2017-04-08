package selfpi;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.Arrays;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class Gui extends JPanel implements KeyListener {
	public static final String IDLE_TXT = "<html><center>Press<br>the button";
	private static final String CARD_TEXT = "text";
	private static final String CARD_HIST = "hist";
	private static final String CARD_COUNT = "count";
	private static final String CARD_SHARE = "share";
	
	private static final String SCREEN_SHARE_HORIZONTAL = "share_screen_horizontal.png";
	private static final String SCREEN_SHARE_VERTICAL = "share_screen_vertical.png";
	private static final String SCREEN_IDLE_HORIZONTAL = "idle_screen_horizontal.png";
	private static final String SCREEN_IDLE_VERTICAL = "idle_screen_horizontal.png";

	private static int historySouvenirFileIndex = 0;
	private static int historyBeerFileIndex = 0;
	private static File historyBeerDirectory = new File(SelfPi.funnyImageFilefolder);
	private static File historySouvenirDirectory = new File(SelfPi.souvenirImageFilePath);
	
	private TicketMode historyMode = TicketMode.REPRINT;
	
	private Thread countDownThread;
	private Thread shareProgressThread;
	
	private boolean verticalOrientation = false;

	public Gui(boolean verticalOrientation) {
		this.verticalOrientation = verticalOrientation;
		setLayout(new BorderLayout());
		setBackground(Color.white);
		if (verticalOrientation) {
			add(getDummyPanel(), BorderLayout.NORTH);
			add(getMainPanel(), BorderLayout.CENTER);
		} else {
			add(getDummyPanel(), BorderLayout.WEST);
			add(getMainPanel(), BorderLayout.CENTER);
		}
	}

	public File getHistoryDirectory() {
		if (historyMode == TicketMode.REPRINT)
			return historySouvenirDirectory;
		else 
			return historySouvenirDirectory;
	}
	public int getHistoryFileIndex(){
		if (historyMode == TicketMode.REPRINT)
			return historyBeerFileIndex;
		else 
			return historySouvenirFileIndex;
	}

	private void incrementHistoryFileIndex() {
		if (historyMode == TicketMode.REPRINT) {
			File[] listOfFiles = historyBeerDirectory.listFiles();
			if (historyBeerFileIndex+6 < listOfFiles.length){
				historyBeerFileIndex +=6;
			}
		} else {
			File[] listOfFiles = historySouvenirDirectory.listFiles();
			if (historySouvenirFileIndex+6 < listOfFiles.length){
				historySouvenirFileIndex +=6;
			}
		}
	}

	private void decrementHistoryFileIndex() {
		if (historyMode == TicketMode.REPRINT) {
			if (historyBeerFileIndex-6 >=0 ) historyBeerFileIndex-=6;
		} else {
			if (historySouvenirFileIndex-6 >=0 ) historySouvenirFileIndex-=6;
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
				if (state == SelfpiState.HISTORIC){
					getCardLayout().show(Gui.this.getMainPanel(), CARD_HIST);
					populateImages();
				}
				if (state == SelfpiState.IDLE) {
					getCardLayout().show(Gui.this.getMainPanel(), CARD_TEXT);
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
			}
		});
	}

	@Override
	public void keyPressed(KeyEvent e) {

		if (e.getKeyCode() == KeyEvent.VK_LEFT){
			decrementHistoryFileIndex();
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					populateImages();
				}
			});
		}
		if (e.getKeyCode() == KeyEvent.VK_RIGHT){
			incrementHistoryFileIndex();
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					populateImages();
				}
			});
		}
		if (e.getKeyCode() == KeyEvent.VK_B) {
			historyMode = TicketMode.REPRINT;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					populateImages();
				}
			});
		}
		if (e.getKeyCode() == KeyEvent.VK_S) {
			historyMode = TicketMode.SOUVENIR;
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					populateImages();
				}
			});
		}
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
	}
	@Override
	public void keyTyped(KeyEvent e) {
	}

	private void populateImages(){
		System.out.println("historyFileIndex of "+historyMode+": "+getHistoryFileIndex());

		getHistoryImagesPanel().removeAll();
		getHistoryImagesPanel().repaint();

		File folder = getHistoryDirectory();
		File[] listOfFiles = folder.listFiles();
		Arrays.sort(listOfFiles);

		for (int i = getHistoryFileIndex(); i < listOfFiles.length && i < getHistoryFileIndex()+6; i++) {
			System.out.println(listOfFiles[i].getPath());

			final JLabel l = new JLabel();
			l.setForeground(Color.white);
			l.setVerticalTextPosition(JLabel.BOTTOM);
			l.setHorizontalTextPosition(JLabel.CENTER);

			l.setText(listOfFiles[i].getName().replace(".jpg", ""));
			getHistoryImagesPanel().add(l);
			Image resized  = new ImageIcon(listOfFiles[i].getPath()).getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH); 
			l.setIcon(new ImageIcon(resized));

			getHistoryImagesPanel().repaint();
		}
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
//			mainPanel.setPreferredSize(new Dimension(1024, 256));
			mainPanel.add(getTextLabel(), CARD_TEXT);
			mainPanel.add(getHistoryPanel(), CARD_HIST);
			mainPanel.add(getSharePanel(), CARD_SHARE);
			mainPanel.add(getCountLabel(), CARD_COUNT);
		}
		return mainPanel;
	}

	private JLabel textLabel;
	private JLabel getTextLabel() {
		if(textLabel == null) {
			textLabel = new JLabel();
			if (verticalOrientation) {
				textLabel.setIcon(new ImageIcon(SelfPi.GUI_SCREENS_PATH+SCREEN_IDLE_VERTICAL));
			} else {
				textLabel.setIcon(new ImageIcon(SelfPi.GUI_SCREENS_PATH+SCREEN_IDLE_HORIZONTAL));
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
			countLabel = new JLabel("9");
			countLabel.setHorizontalTextPosition(SwingConstants.CENTER);
			countLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 300));
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
				shareScreenLabel.setIcon(new ImageIcon(SelfPi.GUI_SCREENS_PATH+SCREEN_SHARE_VERTICAL));
			} else {
				shareScreenLabel.setIcon(new ImageIcon(SelfPi.GUI_SCREENS_PATH+SCREEN_SHARE_HORIZONTAL));
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

	private JTextPane historyText;
	private JTextPane getHistoryText() {
		if (historyText == null){
			historyText = new JTextPane();
			if (verticalOrientation) {
				historyText.setText("<-: Previous, ->: Next, p: Print, entrer: Quit");
			} else {
				historyText.setContentType("text/html");
				historyText.setText("<html><center> &lt;-: Previous<br>-&gt;: Next<br>p: Print<br>entrer: Quit");
			}
			historyText.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
			historyText.setVisible(true);
		}
		return historyText;
	}

	private JPanel historyImagesPanel;
	private JPanel getHistoryImagesPanel() {
		if (historyImagesPanel == null) {
			FlowLayout fl = new FlowLayout(FlowLayout.LEFT);
			historyImagesPanel = new JPanel(fl);
			historyImagesPanel.setBackground(Color.black);
		}
		return historyImagesPanel;
	}

	private class CountDown implements Runnable {
		
		@Override
		public void run() {
			for (int counter = 9; counter >= 0; counter -= 1) {
				final String count = "<html><center>"+Integer.toString(counter);
				
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						getCountLabel().setText(count);
					}
				});
				
				try { Thread.sleep(1000); } catch (InterruptedException e) {}
			}
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

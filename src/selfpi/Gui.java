package selfpi;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
	
	private static final String CARD_ENABLE = "enable";
	private static final String CARD_ONGOING = "ongoing";
	private static final String CARD_DISABLE = "disable";
	
	private static final String SCREEN_IDLE = "idle_screen.png";
	
	private static final String TILE_FACEBOOK_ENABLE = "tile_facebook_enable.png";
	private static final String TILE_FACEBOOK_ONGOING = "tile_facebook_ongoing.png";
	private static final String TILE_FACEBOOK_DISABLE = "tile_facebook_disable.png";
	private static final String TILE_TWITTER_ENABLE = "tile_twitter_enable.png";
	private static final String TILE_TWITTER_ONGOING = "tile_twitter_ongoing.png";
	private static final String TILE_TWITTER_DISABLE = "tile_twitter_disable.png";
	private static final String TILE_REPRINT_ENABLE = "tile_reprint_enable.png";
	private static final String TILE_REPRINT_ONGOING = "tile_reprint_ongoing.png";
	private static final String TILE_REPRINT_DISABLE = "tile_reprint_disable.png";
	
	private Thread countDownThread;
	private Thread shareProgressThread;
	
	private NumberFormat formatter = new DecimalFormat("#0.0");     
	
	public static enum SHARE_STATE {ENABLE, ONGOING, DISABLE};
	
	public Gui() {
		setLayout(new BorderLayout());
		setBackground(Color.white);
		add(getDummyPanel(), BorderLayout.CENTER);
		add(getMainPanel(), BorderLayout.SOUTH);
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
	
	public void resetShareProgressBar(){
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try { Thread.sleep(1000); } catch (InterruptedException e) {}
				getShareProgressBar().setValue(1000);
			}
		}).start();
	}

	public void setMode(final SelfpiState state) {
		System.out.println("GUI Mode: "+state);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (state == SelfpiState.HISTORIC_SOUVENIR ||
						state == SelfpiState.HISTORIC_PLAYERS ||
						state == SelfpiState.HISTORIC_DSLR){
					getMainCardLayout().show(Gui.this.getMainPanel(), CARD_HIST);
				}
				if (state == SelfpiState.IDLE_SOUVENIR) {
					getMainCardLayout().show(Gui.this.getMainPanel(), CARD_IDLE);
					resetShareProgressBar();
				}
				if (state == SelfpiState.TAKING_PICT) {
					getMainCardLayout().show(Gui.this.getMainPanel(), CARD_COUNT);
					launchCountDown();
				}
				if (state == SelfpiState.WAIT_FOR_SHARE) {
					getMainCardLayout().show(Gui.this.getMainPanel(), CARD_SHARE);
					launchShareProgress();
				}
				if (state == SelfpiState.IMPORT_DSLR) {
					getMainCardLayout().show(Gui.this.getMainPanel(), CARD_IMPORT);
				}
			}
		});
	}
	
	public void setFacebookShareStatus(SHARE_STATE state) {
		switch (state) {
		case ENABLE:
			getFacebookCardLayout().show(getShareFacebookPanel(), CARD_ENABLE);
			break;
		case ONGOING:
			getFacebookCardLayout().show(getShareFacebookPanel(), CARD_ONGOING);
			break;
		case DISABLE:
			getFacebookCardLayout().show(getShareFacebookPanel(), CARD_DISABLE);
			break;

		default:
			getFacebookCardLayout().show(getShareFacebookPanel(), CARD_DISABLE);
			break;
		}
	}
	
	public void setTwitterShareStatus(SHARE_STATE state) {
		switch (state) {
		case ENABLE:
			getTwitterCardLayout().show(getShareTwitterPanel(), CARD_ENABLE);
			break;
		case ONGOING:
			getTwitterCardLayout().show(getShareTwitterPanel(), CARD_ONGOING);
			break;
		case DISABLE:
			getTwitterCardLayout().show(getShareTwitterPanel(), CARD_DISABLE);
			break;

		default:
			getTwitterCardLayout().show(getShareTwitterPanel(), CARD_DISABLE);
			break;
		}
	}

	public void setReprintShareStatus(SHARE_STATE state) {
		switch (state) {
		case ENABLE:
			getReprintCardLayout().show(getShareReprintPanel(), CARD_ENABLE);
			break;
		case ONGOING:
			getReprintCardLayout().show(getShareReprintPanel(), CARD_ONGOING);
			break;
		case DISABLE:
			getReprintCardLayout().show(getShareReprintPanel(), CARD_DISABLE);
			break;

		default:
			getReprintCardLayout().show(getShareReprintPanel(), CARD_DISABLE);
			break;
		}
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

	private CardLayout mainCardLayout;
	private CardLayout getMainCardLayout() {
		if (mainCardLayout == null) {
			mainCardLayout = new CardLayout();
		}
		return mainCardLayout;
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
			mainPanel = new JPanel(getMainCardLayout());
			mainPanel.setPreferredSize(new Dimension(1024, 256));
			mainPanel.add(getIdleLabel(), CARD_IDLE);
			mainPanel.add(getHistoryPanel(), CARD_HIST);
			mainPanel.add(getSharePanel(), CARD_SHARE);
			mainPanel.add(getCountLabel(), CARD_COUNT);
			mainPanel.add(getImportTextPane(), CARD_IMPORT);
		}
		return mainPanel;
	}

	private JLabel idleLabel;
	private JLabel getIdleLabel() {
		if(idleLabel == null) {
			idleLabel = new JLabel();
			idleLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+SCREEN_IDLE));
//			textLabel = new JLabel(IDLE_TXT);
//			textLabel.setHorizontalTextPosition(SwingConstants.CENTER);
//			textLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 110));
//			textLabel.setHorizontalAlignment(JLabel.CENTER);
		}
		return idleLabel;
	}
	
	private JLabel countLabel;
	private JLabel getCountLabel() {
		if(countLabel == null) {
			countLabel = new JLabel(formatter.format(SelfPi.countdownLength));
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
			sharePanel.add(getShareScreenOptionsPanel(), BorderLayout.CENTER);
			sharePanel.add(getShareProgressBar(), BorderLayout.NORTH);
			sharePanel.setPreferredSize(new Dimension(1024, 256));
		}
		return sharePanel;
	}
	
	private CardLayout facebookCardLayout;
	private CardLayout getFacebookCardLayout() {
		if (facebookCardLayout == null) {
			facebookCardLayout = new CardLayout();
		}
		return facebookCardLayout;
	}
	
	private JPanel shareFacebookPanel;
	private JPanel getShareFacebookPanel() {
		if (shareFacebookPanel == null) {
			shareFacebookPanel = new JPanel(getFacebookCardLayout());
			shareFacebookPanel.add(getFacebookEnableLabel(), CARD_ENABLE);
			shareFacebookPanel.add(getFacebookOngoingLabel(), CARD_ONGOING);
			shareFacebookPanel.add(getFacebookDisableLabel(), CARD_DISABLE);
		}
		return shareFacebookPanel;
	}
	
	private JLabel facebookEnableLabel;
	private JLabel getFacebookEnableLabel() {
		if (facebookEnableLabel == null) {
			facebookEnableLabel = new JLabel();
			facebookEnableLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_FACEBOOK_ENABLE));
			facebookEnableLabel.setPreferredSize(new Dimension(330, 200));
		}
		return facebookEnableLabel;
	}
	
	private JLabel facebookOngoingLabel;
	private JLabel getFacebookOngoingLabel() {
		if (facebookOngoingLabel == null) {
			facebookOngoingLabel = new JLabel();
			facebookOngoingLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_FACEBOOK_ONGOING));
			facebookOngoingLabel.setPreferredSize(new Dimension(330, 200));
		}
		return facebookOngoingLabel;
	}
	
	private JLabel facebookDisableLabel;
	private JLabel getFacebookDisableLabel() {
		if (facebookDisableLabel == null) {
			facebookDisableLabel = new JLabel();
			facebookDisableLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_FACEBOOK_DISABLE));
			facebookDisableLabel.setPreferredSize(new Dimension(330, 200));
		}
		return facebookDisableLabel;
	}
	
	private CardLayout reprintCardLayout;
	private CardLayout getReprintCardLayout() {
		if (reprintCardLayout == null) {
			reprintCardLayout = new CardLayout();
		}
		return reprintCardLayout;
	}
	
	private JPanel shareReprintPanel;
	private JPanel getShareReprintPanel() {
		if (shareReprintPanel == null) {
			shareReprintPanel = new JPanel(getReprintCardLayout());
			shareReprintPanel.add(getReprintEnableLabel(), CARD_ENABLE);
			shareReprintPanel.add(getReprintOngoingLabel(), CARD_ONGOING);
			shareReprintPanel.add(getReprintDisableLabel(), CARD_DISABLE);
		}
		return shareReprintPanel;
	}
	
	private JLabel reprintEnableLabel;
	private JLabel getReprintEnableLabel() {
		if (reprintEnableLabel == null) {
			reprintEnableLabel = new JLabel();
			reprintEnableLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_REPRINT_ENABLE));
			reprintEnableLabel.setPreferredSize(new Dimension(364, 200));
		}
		return reprintEnableLabel;
	}
	
	private JLabel reprintOngoingLabel;
	private JLabel getReprintOngoingLabel() {
		if (reprintOngoingLabel == null) {
			reprintOngoingLabel = new JLabel();
			reprintOngoingLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_REPRINT_ONGOING));
			reprintOngoingLabel.setPreferredSize(new Dimension(364, 200));
		}
		return reprintOngoingLabel;
	}
	
	private JLabel reprintDisableLabel;
	private JLabel getReprintDisableLabel() {
		if (reprintDisableLabel == null) {
			reprintDisableLabel = new JLabel("reprint disable");
			reprintDisableLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_REPRINT_DISABLE));
			reprintDisableLabel.setPreferredSize(new Dimension(364, 200));
		}
		return reprintDisableLabel;
	}
	
	private CardLayout twitterCardLayout;
	private CardLayout getTwitterCardLayout() {
		if (twitterCardLayout == null) {
			twitterCardLayout = new CardLayout();
		}
		return twitterCardLayout;
	}
	
	private JPanel shareTwitterPanel;
	private JPanel getShareTwitterPanel() {
		if (shareTwitterPanel == null) {
			shareTwitterPanel = new JPanel(getTwitterCardLayout());
			shareTwitterPanel.add(getTwitterEnableLabel(), CARD_ENABLE);
			shareTwitterPanel.add(getTwitterOngoingLabel(), CARD_ONGOING);
			shareTwitterPanel.add(getTwitterDisableLabel(), CARD_DISABLE);
		}
		return shareTwitterPanel;
	}
	
	private JLabel twitterEnableLabel;
	private JLabel getTwitterEnableLabel() {
		if (twitterEnableLabel == null) {
			twitterEnableLabel = new JLabel();
			twitterEnableLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_TWITTER_ENABLE));
			twitterEnableLabel.setPreferredSize(new Dimension(330, 200));
		}
		return twitterEnableLabel;
	}
	
	private JLabel twitterOngoingLabel;
	private JLabel getTwitterOngoingLabel() {
		if (twitterOngoingLabel == null) {
			twitterOngoingLabel = new JLabel();
			twitterOngoingLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_TWITTER_ONGOING));
			twitterOngoingLabel.setPreferredSize(new Dimension(330, 200));
		}
		return twitterOngoingLabel;
	}
	
	private JLabel twitterDisableLabel;
	private JLabel getTwitterDisableLabel() {
		if (twitterDisableLabel == null) {
			twitterDisableLabel = new JLabel();
			twitterDisableLabel.setIcon(new ImageIcon(SelfPi.SETUP_PATH+TILE_TWITTER_DISABLE));
			twitterDisableLabel.setPreferredSize(new Dimension(330, 200));
		}
		return twitterDisableLabel;
	}
	
	private JPanel shareScreenOptionsPanel;
	private JPanel getShareScreenOptionsPanel(){
		if (shareScreenOptionsPanel == null){
			shareScreenOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
			shareScreenOptionsPanel.add(getShareFacebookPanel());
			shareScreenOptionsPanel.add(getShareReprintPanel());
			shareScreenOptionsPanel.add(getShareTwitterPanel());
		}
		return shareScreenOptionsPanel;
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
			shareProgressBar = new JProgressBar(0, 1000);
			shareProgressBar.setOrientation(SwingConstants.HORIZONTAL);
			shareProgressBar.setPreferredSize(new Dimension(1024, 56));
			shareProgressBar.setBackground(Color.white);
			shareProgressBar.setForeground(Color.black);
			shareProgressBar.setValue(1000);
		}
		return shareProgressBar;
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
			historyText.setText("<-: Previous, ->: Next, 1-6 : Print one, p: Print All, esc: Quit");
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
			// count down
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

			// processing
			for (double counter = SelfPi.countdownLength; counter >= 0; counter -= 1) {
				String dots = " ";
				if (counter%4 == 0) dots = ".";
				if (counter%4 == 1) dots = "...";
				if (counter%4 == 2) dots = ".....";
				if (counter%4 == 3) dots = ".......";
				
				final String count = dots;
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						getCountLabel().setText(count);
					}
				});
				
				try { Thread.sleep(500); } catch (InterruptedException e) {}
			}
		}
	}
	
	private class ShareProgress implements Runnable {
		
		@Override
		public void run() {
			for (int counter = getShareProgressBar().getValue(); counter >= 0; counter -= 1) {
				final int value = counter;
				
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						getShareProgressBar().setValue(value);
					}
				});
				
				try { Thread.sleep(10); } catch (InterruptedException e) {}
			}
		}
	}

}

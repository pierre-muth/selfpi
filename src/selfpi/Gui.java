package selfpi;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Gui extends JPanel {
	private static final String CARD_TEXT = "text";
	private static final String CARD_HIST = "hist";
	
	public Gui() {
		
		setLayout(new BorderLayout());
		setBackground(Color.white);
		add(getNorthDummyPanel(), BorderLayout.NORTH);
		add(getMainPanel(), BorderLayout.CENTER);
	}
	
	public void setText(final String txt) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				getTextLabel().setText(txt);
			}
		});
	}
	
	public void setMode(TicketMode mode) {
		System.out.println("Mode: "+mode);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				if (mode == TicketMode.HISTORIC)
					getCardLayout().show(Gui.this.getMainPanel(), CARD_HIST);
				else
					getCardLayout().show(Gui.this.getMainPanel(), CARD_TEXT);
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
	
	private JPanel northDummyPanel;
	private JPanel getNorthDummyPanel() {
		if (northDummyPanel == null) {
			northDummyPanel = new JPanel();
			northDummyPanel.setPreferredSize(new Dimension(1024, 1000));
		}
		return northDummyPanel;
	}
	
	private JPanel mainPanel;
	private JPanel getMainPanel() {
		if (mainPanel == null) {
			mainPanel = new JPanel(getCardLayout());
			mainPanel.setPreferredSize(new Dimension(1024, 256));
			mainPanel.add(getTextLabel(), CARD_TEXT);
			mainPanel.add(getHistoryLabel(), CARD_HIST);
		}
		return mainPanel;
	}
	
	private JLabel textLabel;
	private JLabel getTextLabel() {
		if(textLabel == null) {
			textLabel = new JLabel("<html>Pour un souvenir<br>Appuyez sur le bouton !");
			textLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 50));
		}
		return textLabel;
	}
	
	private JLabel historyLabel;
	private JLabel getHistoryLabel() {
		if (historyLabel == null) {
			historyLabel = new JLabel("Historique");
			historyLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 50));
		}
		return historyLabel;
	}


}

package selfpi;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Gui extends JPanel {
	
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
			mainPanel = new JPanel(new BorderLayout());
			mainPanel.setPreferredSize(new Dimension(1024, 256));
			mainPanel.add(getTextLabel(), BorderLayout.CENTER);
		}
		return mainPanel;
	}
	
	private JLabel textLabel;
	private JLabel getTextLabel() {
		if(textLabel == null) {
			textLabel = new JLabel("<html>Pour un souvenir<br>Appuyez sur le bouton !");
			textLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 50));
			textLabel.setPreferredSize(new Dimension(380, 100));
		}
		return textLabel;
	}
	
	
	

}

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
		
		add(getWestPanel(), BorderLayout.WEST);
	}
	
	public void reset() {
		setCount(5);
	}
	
	public void setCount(final int count) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				getCountLabel().setText(Integer.toString(count));
			}
		});
	}
	
	private JPanel westPanel;
	private JPanel getWestPanel() {
		if (westPanel == null) {
			westPanel = new JPanel(new BorderLayout());
			westPanel.add(getTextLabel(), BorderLayout.NORTH);
			westPanel.add(getCountLabel(), BorderLayout.CENTER);
		}
		return westPanel;
	}
	
	private JLabel countLabel;
	private JLabel getCountLabel() {
		if(countLabel == null) {
			countLabel = new JLabel("5");
			countLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 150));
		}
		return countLabel;
	}
	
	private JLabel textLabel;
	private JLabel getTextLabel() {
		if(textLabel == null) {
			textLabel = new JLabel("Get ready!");
			textLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 50));
			textLabel.setPreferredSize(new Dimension(380, 100));
		}
		return textLabel;
	}
	
	
	

}

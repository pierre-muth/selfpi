package tests;

import java.awt.FlowLayout;
import java.awt.Image;
import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class FileListing extends JPanel{

	public FileListing(){

		initGui();

	}

	private void initGui(){

		setLayout(new FlowLayout());

		Thread t = new Thread(new Runnable() {
			public void run() {


				File folder = new File("/home/pi/Pictures/");
				File[] listOfFiles = folder.listFiles();

				for (int i = 0; i < listOfFiles.length; i++) {
					if (listOfFiles[i].isFile()) {
						System.out.println(listOfFiles[i].getPath());

						if(listOfFiles[i].getName().contains("jpg")){

							final JLabel l = new JLabel();
							Image resized  = new ImageIcon(listOfFiles[i].getPath()).getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH); 
							l.setIcon(new ImageIcon(resized));

							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									FileListing.this.add(l);
								}
							});

						}

					}
				}


			}
		});
		t.run();


	}

	public static void main(String[] args) {

		JFrame f = new JFrame("Test files list");
		FileListing fl = new FileListing();
		f.add(fl);
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.pack();
		f.setVisible(true);


	}

}
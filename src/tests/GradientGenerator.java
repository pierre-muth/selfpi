package tests;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class GradientGenerator extends JPanel {
	
	public GradientGenerator() throws FileNotFoundException, IOException {
		
		JLabel centerLabel = new JLabel();
		
		BufferedImage bufImage = new BufferedImage(576, 576, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster wr = bufImage.getData().createCompatibleWritableRaster();
		int[] pixlist = new int[576 * 576];
		int idx = 0;
		int fullColor = 0;
		for (int x = 0; x < 64; x++) {
			for (int y = 0; y < 9; y++) {
				
					if (y == 0 || y == 2 || y == 4 || y == 6) {
						for (int z = 0; z < 576; z++) {
							pixlist[idx] = fullColor;
							idx++;							
						}
						if (fullColor < 255) fullColor++;
					}
					if (y == 1 || y == 3 || y == 5 || y == 7){
						for (int z = 0; z < 576; z++) {
							if (z%2 == 0) pixlist[idx] = fullColor-1;
							else pixlist[idx] = fullColor;
							idx++;							
						}
					}
					if (y == 8){
						for (int z = 0; z < 576; z++) {
							if (z%2 == 0) pixlist[idx] = fullColor;
							else pixlist[idx] = fullColor-1;
							idx++;								
						}
					}

			}
		}
		
		wr.setPixels(0, 0, 576, 576, pixlist);
		bufImage.setData(wr);
		ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("png").next();
		IIOImage outputImage = new IIOImage(bufImage, null, null);
		imageWriter.setOutput(new FileImageOutputStream( new File("c:\\temporary\\gradient.png") ));
		imageWriter.write(outputImage);
		
		
		centerLabel.setIcon(new ImageIcon(bufImage));
		centerLabel.setOpaque(true);
		centerLabel.setSize(576, 576);
		
	}
	
	

	public static void main(String[] args) throws FileNotFoundException, IOException {
		final JFrame frame = new JFrame("SelfPi");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		final GradientGenerator gradientGenerator = new GradientGenerator();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				frame.add(gradientGenerator);
				frame.setVisible(true);
			}
		});

	}

}

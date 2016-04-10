package dithering;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

public class Dither extends JPanel implements KeyListener{
	/*
	 * Quick and dirty GUI to experiment Floyd-Steinberg error diffusion Dithering.
	 */

	public static final int WIDTH = 20;
	public static final int HEIGHT = 8;
	public static final int PIXEL_SIZE = 20;
	private Pixel[] pixelsSource = new Pixel[WIDTH*HEIGHT];
	private Pixel[] fullBufferErrors = new Pixel[WIDTH*HEIGHT];
	private Pixel[] lineBufferErrors = new Pixel[WIDTH+1];
	private Pixel[] pixelsFullBufferDest = new Pixel[WIDTH*HEIGHT];
	private Pixel[] pixelsLineBufferDest = new Pixel[WIDTH*HEIGHT];
	private int step = 0;
	private int errorPointer = 0;
	DecimalFormat formatter = new DecimalFormat("000");

	public Dither() {
		buildGUI();
	}

	private void stepLineBuffer() {
		/*
		 * Dithering using the small line buffer
		 */
		if (step >= WIDTH*HEIGHT) return;


		int oldpixel, error;

		// boleans representing  where we are in the image
		boolean left = (step%WIDTH) == 0;
		boolean right = (step%WIDTH) == WIDTH-1;
		boolean bottom = step >= WIDTH*(HEIGHT-1);

		// deselect pixels
		for (int i = 0; i < pixelsSource.length; i++) {
			pixelsLineBufferDest[i].setSelected(false);
		}
		for (int i = 0; i < lineBufferErrors.length; i++) {
			lineBufferErrors[i].setSelected(false);
		}

		oldpixel = pixelsSource[step].getValue()  + lineBufferErrors[errorPointer].getValue();
		pixelsLineBufferDest[step].setValue( oldpixel  < 128 ? 0 : 255 );
		error = oldpixel - pixelsLineBufferDest[step].getValue();

		if (!right) {
			lineBufferErrors[(errorPointer+1)%(lineBufferErrors.length)].addValue( 7*error/16 );
			lineBufferErrors[(errorPointer+1)%(lineBufferErrors.length)].setSelected(true);
		}
		if ( !left && !bottom) {
			lineBufferErrors[(errorPointer+WIDTH-1)%(lineBufferErrors.length)].addValue( 3*error/16 );
			lineBufferErrors[(errorPointer+WIDTH-1)%(lineBufferErrors.length)].setSelected(true);
		}
		if (!bottom) {
			lineBufferErrors[(errorPointer+WIDTH)%(lineBufferErrors.length)].addValue( 5*error/16 );
			lineBufferErrors[(errorPointer+WIDTH)%(lineBufferErrors.length)].setSelected(true);
		}
		if ( !right && !bottom ) {
			lineBufferErrors[(errorPointer+WIDTH+1)%(lineBufferErrors.length)].setValue( error/16 );
			lineBufferErrors[(errorPointer+WIDTH+1)%(lineBufferErrors.length)].setSelected(true);
		} else {
			lineBufferErrors[(errorPointer+WIDTH+1)%(lineBufferErrors.length)].setValue( 0 );
			lineBufferErrors[(errorPointer+WIDTH+1)%(lineBufferErrors.length)].setSelected(true);
		}

		pixelsLineBufferDest[step].setSelected(true);

		errorPointer++;
		errorPointer %= lineBufferErrors.length;


	}

	private void stepFullBuffer() {
		if (step >= WIDTH*HEIGHT) return;

		int oldpixel, error;
		boolean left = ((step)%WIDTH) == 0;
		boolean right = ((step)%WIDTH) == WIDTH-1;
		boolean bottom = (step) >= WIDTH*(HEIGHT-1);

		// deselect all pixels
		for (int i = 0; i < pixelsSource.length; i++) {
			pixelsSource[i].setSelected(false);
			fullBufferErrors[i].setSelected(false);
			pixelsFullBufferDest[i].setSelected(false);
		}

		oldpixel = pixelsSource[step].getValue() + fullBufferErrors[step].getValue();
		pixelsFullBufferDest[step].setValue( oldpixel  < 128 ? 0 : 255 );
		error = oldpixel - pixelsFullBufferDest[step].getValue();

		if (!right) {
			fullBufferErrors[step+1].addValue( 7*error/16 );
			fullBufferErrors[step+1].setSelected(true);
		}
		if ( !left && !bottom) {
			fullBufferErrors[(step+WIDTH-1)].addValue( 3*error/16 );
			fullBufferErrors[(step+WIDTH-1)].setSelected(true);
		}
		if (!bottom) {
			fullBufferErrors[(step+WIDTH)].addValue( 5*error/16 );
			fullBufferErrors[(step+WIDTH)].setSelected(true);
		}
		if ( !right && !bottom ) {
			fullBufferErrors[(step+WIDTH+1)].addValue( error/16 );
			fullBufferErrors[(step+WIDTH+1)].setSelected(true);
		}       

		pixelsSource[step].setSelected(true);
		pixelsFullBufferDest[step].setSelected(true);

	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {

				JFrame frame = new JFrame("Dither");
				frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				Dither d = new Dither();
				frame.getContentPane().add(d, BorderLayout.CENTER );
				//Display the window.
				frame.pack();
				frame.setVisible(true);
				frame.addKeyListener(d);

			}
		});
	}

	@Override
	public void keyPressed(KeyEvent e) {
		/*
		 * To save images of all steps, press Enter
		 */

		if (e.getKeyCode() == KeyEvent.VK_ENTER){
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {

					BufferedImage bi = new BufferedImage(Dither.this.getSize().width, Dither.this.getSize().height, BufferedImage.TYPE_INT_ARGB); 
					Graphics g = bi.createGraphics();
					paint(g);  
					g.dispose();
					try {
						ImageIO.write(bi,"png",new File("C:/temporary/dither/dither_000.png"));
					}catch (Exception e) {
						System.out.println(e);
					}

					for (int i = 0; i < pixelsSource.length; i++) {
						stepFullBuffer();
						stepLineBuffer();
						saveImage();
						step++;
					}
				}
			});
		}
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
	}

	@Override
	public void keyTyped(KeyEvent arg0) {
	}

	private void saveImage(){
		/*
		 * Saving images for GIF animation
		 */

		BufferedImage bi = new BufferedImage(this.getSize().width, this.getSize().height, BufferedImage.TYPE_INT_ARGB); 
		Graphics g = bi.createGraphics();
		paint(g);  
		g.dispose();
		try {
			ImageIO.write(bi,"png",new File("C:/temporary/dither/dither_"+formatter.format(step+1)+".png"));
		}catch (Exception e) {
			System.out.println(e);
		}

	}

	private void buildGUI(){
		/*
		 *  GUI stuff
		 */

		setLayout(new GridLayout(3, 2, 10, 10));

		int gray = 0;

		JPanel imageOriginPanel = new JPanel(new GridLayout(HEIGHT, WIDTH));
		JPanel errorFullBufferPanel = new JPanel(new GridLayout(HEIGHT, WIDTH));
		JPanel errorLineBufferPanel = new JPanel(new GridLayout(1, WIDTH));
		JPanel imageFullDestPanel = new JPanel(new GridLayout(HEIGHT, WIDTH));
		JPanel imageLineDestPanel = new JPanel(new GridLayout(HEIGHT, WIDTH));

		for (int i = 0; i < pixelsSource.length; i++) {
			gray = (int)( Math.sin( ((double)(i)/(WIDTH/2))*2.0*Math.PI)*((double)i*2/(WIDTH*(HEIGHT))) *127.0 +128 );
			if (gray > 255) gray = 255;
			if (gray < 0) gray = 0;

			Pixel pixelOrigin = new Pixel(gray);
			Pixel pixelErrorFull = new Pixel(0);
			Pixel pixelFullDest = new Pixel(0);
			Pixel pixelLineDest = new Pixel(0);

			imageOriginPanel.add(pixelOrigin);
			errorFullBufferPanel.add(pixelErrorFull);
			imageFullDestPanel.add(pixelFullDest);
			imageLineDestPanel.add(pixelLineDest);
			pixelsSource[i] = pixelOrigin;
			fullBufferErrors[i] = pixelErrorFull;
			pixelsFullBufferDest[i] = pixelFullDest;
			pixelsLineBufferDest[i] = pixelLineDest;
		}

		for (int i = 0; i < lineBufferErrors.length; i++) {
			Pixel pixelErrorLine = new Pixel(0);
			errorLineBufferPanel.add(pixelErrorLine);
			lineBufferErrors[i] = pixelErrorLine;
		}


		JPanel controlPanel = new JPanel();
		JButton stepButton = new JButton("Next Step");
		stepButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				stepFullBuffer();
				stepLineBuffer();
				step++;

			}
		});
		JButton allStepButton = new JButton("All Step");
		allStepButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				for (int i = 0; i < pixelsSource.length; i++) {
					stepFullBuffer();
					stepLineBuffer();
					step++;
				}
			}
		});
		controlPanel.add(stepButton);
		controlPanel.add(allStepButton);

		JPanel lineBufferPanel = new JPanel(new FlowLayout());
		lineBufferPanel.add(errorLineBufferPanel);

		JPanel dummyPanel01 = new JPanel(new GridLayout(2, 3));

		JPanel topimageOriginPanel = new JPanel(new BorderLayout());
		JPanel topcontrolpanel = new JPanel(new FlowLayout());
		JPanel toperrorFullBufferPanel = new JPanel(new BorderLayout());
		JPanel toplineBufferPanel = new JPanel(new BorderLayout());
		JPanel topimageFullDestPanel = new JPanel(new BorderLayout());
		JPanel topimageLineDestPanel = new JPanel(new BorderLayout());

		JLabel d0 = new JLabel(" ");
		d0.setPreferredSize(new Dimension(50, 50));
		JLabel d1 = new JLabel("*");
		d1.setPreferredSize(new Dimension(50, 50));
		d1.setBorder(new LineBorder(Color.black, 2));
		d1.setHorizontalAlignment(JLabel.CENTER);
		JLabel d2 = new JLabel("+7/16");
		d2.setPreferredSize(new Dimension(50, 50));
		d2.setBorder(new LineBorder(Color.black, 2));
		d2.setHorizontalAlignment(JLabel.CENTER);
		JLabel d3 = new JLabel("+3/16");
		d3.setPreferredSize(new Dimension(50, 50));
		d3.setBorder(new LineBorder(Color.black, 2));
		d3.setHorizontalAlignment(JLabel.CENTER);
		JLabel d4 = new JLabel("+5/16");
		d4.setPreferredSize(new Dimension(50, 50));
		d4.setBorder(new LineBorder(Color.black, 2));
		d4.setHorizontalAlignment(JLabel.CENTER);
		JLabel d5 = new JLabel("=1/16");
		d5.setPreferredSize(new Dimension(50, 50));
		d5.setBorder(new LineBorder(Color.black, 2));
		d5.setHorizontalAlignment(JLabel.CENTER);

		dummyPanel01.add(d0);
		dummyPanel01.add(d1);
		dummyPanel01.add(d2);
		dummyPanel01.add(d3);
		dummyPanel01.add(d4);
		dummyPanel01.add(d5);


		JLabel originLabel = new JLabel("Source image");
		JLabel fullbuffLabel = new JLabel("Full image errors buffer");
		JLabel linebuffLabel = new JLabel("Line length +1 errors buffer");
		JLabel fulldestLabel = new JLabel("Result");
		JLabel linedestLabel = new JLabel("Result");

		topimageOriginPanel.add(imageOriginPanel, BorderLayout.CENTER);
		topimageOriginPanel.add(originLabel, BorderLayout.NORTH);
		topcontrolpanel.add(dummyPanel01);
		toperrorFullBufferPanel.add(errorFullBufferPanel, BorderLayout.CENTER);
		toperrorFullBufferPanel.add(fullbuffLabel, BorderLayout.NORTH);
		toplineBufferPanel.add(lineBufferPanel, BorderLayout.CENTER);
		toplineBufferPanel.add(linebuffLabel, BorderLayout.NORTH);
		topimageFullDestPanel.add(imageFullDestPanel, BorderLayout.CENTER);
		topimageFullDestPanel.add(fulldestLabel, BorderLayout.NORTH);
		topimageLineDestPanel.add(imageLineDestPanel, BorderLayout.CENTER);
		topimageLineDestPanel.add(linedestLabel, BorderLayout.NORTH);

		add(topimageOriginPanel);
		add(controlPanel);
		add(toperrorFullBufferPanel);
		add(toplineBufferPanel);
		add(topimageFullDestPanel);
		add(topimageLineDestPanel);
	}

	private class Pixel extends JLabel {
		private int value = 0;
		private boolean selected = false;

		public Pixel (int value){
			super();
			this.value = value;

			setPreferredSize(new Dimension(PIXEL_SIZE, PIXEL_SIZE));
			setOpaque(true);
			setHorizontalAlignment(JLabel.CENTER);
			setBackground(new Color(value, value, value));
			if (value < 128) setForeground(Color.white);
			else setForeground(Color.black);
			setFont(new Font("Arial", 0, 8));
			setText(""+value);
			setBorder(new LineBorder(Color.white, 2));
		}

		public int getValue() {
			return value;
		}

		public void setValue(int value) {
			this.value = value;
			refresh();
		}

		public void addValue(int value) {
			this.value += value;
			refresh();
		}

		public void setSelected(boolean selected) {
			this.selected = selected;
			refresh();
		}

		private void refresh(){
			int abs = Math.abs(value);
			setBackground(new Color(abs, abs, abs));
			if (abs < 128) setForeground(Color.white);
			else setForeground(Color.black);
			Pixel.this.setText(""+value);
			if (selected) Pixel.this.setBorder(new LineBorder(Color.red, 2));
			else  Pixel.this.setBorder(new LineBorder(Color.white, 2));

		}
	}

}

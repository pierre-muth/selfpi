package tests;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

public class MonochromImageWin {
	public static final float JPEG_QUALITY = 0.97f;	
	public static final String filePath = "C:\\temporary\\selfpi\\";
	public static int TARGET_WIDTH = 576;
	public static int HEIGHT = 576;
	public static int WIDTH = 576;

	private ImageWriter imageWriter;
	private int[] pixList;
	private int[] pixContrasted;
	private int[] pixDithered;
	
	private int[] histogramOrigin = new int[256];
	private int[] histogramCumulated = new int[256];
	private int[] histogramNormalised = new int[256];
	private int[] histogramGamma = new int[256];
	
	private Thread fileWriterThread;

	private coef[] matrixJarvis = new coef[] {
			 new coef( 1, 0, 7/48.0),
			 new coef( 2, 0, 5/48.0),
			 new coef(-2, 1, 3/48.0),
			 new coef(-1, 1, 5/48.0),
			 new coef( 0, 1, 7/48.0),
			 new coef( 1, 1, 5/48.0),
			 new coef( 2, 1, 3/48.0),
			 new coef(-2, 2, 1/48.0),
			 new coef(-1, 2, 3/48.0),
			 new coef( 0, 2, 5/48.0),
			 new coef( 1, 2, 3/48.0),
			 new coef( 2, 2, 1/48.0) 	
			
	};
	
	public MonochromImageWin() {
		imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
	}

	public void setPixels(int[] pixList) {
		this.pixList = pixList;
	}
	
	public void setFile(File file){
		try {
			// read the file
			BufferedImage image = ImageIO.read(file);
			
			// convert to grayscale
			BufferedImage imageGrayscale = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = imageGrayscale.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			
			WIDTH = imageGrayscale.getWidth();
			HEIGHT = imageGrayscale.getHeight();
			
			if (WIDTH <= TARGET_WIDTH && HEIGHT <= TARGET_WIDTH) { // image can fit in paper
				if (WIDTH < HEIGHT) {	// we have to rotate to save paper
					BufferedImage imageRotated = new BufferedImage(HEIGHT, WIDTH, BufferedImage.TYPE_BYTE_GRAY);
					g = imageRotated.createGraphics();
					g.rotate(Math.PI/2);
					g.drawImage(imageGrayscale, 0, -HEIGHT, WIDTH, HEIGHT, null);
					g.dispose();
					// update size
					WIDTH = imageRotated.getWidth();
					HEIGHT = imageRotated.getHeight();
					// fit width dimension to %8
					BufferedImage imageResized = new BufferedImage((WIDTH/8)*8, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
					g = imageResized.createGraphics();
					g.drawImage(imageRotated, 0, 0, WIDTH, HEIGHT, null);
					g.dispose();
					
					image = imageResized;
				} else { // no need to rotate
					// fit width dimension to %8
					BufferedImage imageResized = new BufferedImage((WIDTH/8)*8, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
					g = imageResized.createGraphics();
					g.drawImage(imageGrayscale, 0, 0, WIDTH, HEIGHT, null);
					g.dispose();
					image = imageResized;
				}
			} else if (WIDTH <= TARGET_WIDTH && HEIGHT > TARGET_WIDTH) { // image fit in paper, no need to resize nor rotate
				// fit width dimension to %8
				BufferedImage imageResized = new BufferedImage((WIDTH/8)*8, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
				g = imageResized.createGraphics();
				g.drawImage(imageGrayscale, 0, 0, WIDTH, HEIGHT, null);
				g.dispose();
				image = imageResized;
			} else if (WIDTH > TARGET_WIDTH && HEIGHT <= TARGET_WIDTH) { // image fit in paper, no need to resize but rotate
				BufferedImage imageRotated = new BufferedImage(HEIGHT, WIDTH, BufferedImage.TYPE_BYTE_GRAY);
				g = imageRotated.createGraphics();
				g.rotate(Math.PI/2);
				g.drawImage(imageGrayscale, 0, -HEIGHT, WIDTH, HEIGHT, null);
				g.dispose();
				// update size
				WIDTH = imageRotated.getWidth();
				HEIGHT = imageRotated.getHeight();
				// fit width dimension to %8
				BufferedImage imageResized = new BufferedImage((WIDTH/8)*8, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
				g = imageResized.createGraphics();
				g.drawImage(imageRotated, 0, 0, WIDTH, HEIGHT, null);
				g.dispose();
				image = imageResized;
			} else { // image doesn't fit in paper
				if (WIDTH > HEIGHT) { // we have to rotate to maximize printed size
					BufferedImage imageRotated = new BufferedImage(HEIGHT, WIDTH, BufferedImage.TYPE_BYTE_GRAY);
					g = imageRotated.createGraphics();
					g.rotate(Math.PI/2);
					g.drawImage(imageGrayscale, 0, -HEIGHT, WIDTH, HEIGHT, null);
					g.dispose();
					imageGrayscale = imageRotated;
					WIDTH = imageGrayscale.getWidth();
					HEIGHT = imageGrayscale.getHeight();
				}
				// resize
				HEIGHT = (int) (HEIGHT * (  (double)TARGET_WIDTH /WIDTH  ));
				WIDTH = TARGET_WIDTH;
				BufferedImage ImageResized = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
				g = ImageResized.createGraphics();
				g.drawImage(imageGrayscale, 0, 0, WIDTH, HEIGHT, null);
				g.dispose();
				image = ImageResized;
			}
			
			// update size
			WIDTH = image.getWidth();
			HEIGHT = image.getHeight();
			
			// extract bytes
			byte[] bytelist = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
			pixList = new int[image.getWidth()*image.getHeight()];
			for (int i = 0; i < pixList.length; i++) {
				pixList[i] = Byte.toUnsignedInt( bytelist[i] );
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public int[] getJarvisDitheredMonochom() {
		pixDithered = new int[pixList.length];
		pixContrasted = new int[pixList.length];
		int min = 255, max = 0;
		double gain = 1;
		
		// search min-max
		for (int i = 0; i < pixList.length; i++) {
			if (pixList[i] > max) max = pixList[i];
			if (pixList[i] < min) min = pixList[i];
		}

		//limiting
		max = max<32 ? 32 : max;
		min = min>224 ? 224 : min;

		// calculate gain
		gain = 255.0/(max-min);		

		System.out.println("Gain: "+gain+", offset: "+min);

		// normalise min-max to 0 - 255
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (int) ( (pixList[i] - min)*gain) ;
			if(pixList[i]>255) pixList[i] = 255;
			if(pixList[i]<0) pixList[i] = 0;
		}
		
		// local contrasting/sharpening
		int zoneSize = 5;
		for (int i = 0; i < pixList.length; ++i) {
			int x = i % WIDTH;
            int y = i / WIDTH;
            int zoneAverage = 0;
            int zoneCount = 0;
            // average of the zone
			for (int j = 0; j < (zoneSize*zoneSize); j++){
				int xOff = ((j % zoneSize) - ((zoneSize-1)/2));
				int yOff = ((j / zoneSize) - ((zoneSize-1)/2));
				int x0 = x + xOff;
				int y0 = y + yOff;
				if (x0 > WIDTH - 1 || x0 < 0 || y0 > HEIGHT - 1 || y0 < 0) {
                    continue;
                }
				zoneAverage += pixList[x0 + WIDTH * y0];
				zoneCount++;
			}
			zoneAverage /= zoneCount;
			pixContrasted[i] = pixList[i] + (pixList[i] - zoneAverage);
//			System.out.println( ((double)i / pixList.length) * 100 );
		}
		
//		int zoneSize = 11;
//		for (int i = 0; i < pixList.length; ++i) {
//			int x = i % WIDTH;
//            int y = i / WIDTH;
//            int localMin = 255;
//            int localMax = 0;
//            double localGain = 1;
//            // min-max of the zone
//			for (int j = 0; j < (zoneSize*zoneSize); j++){
//				int xOff = ((j % zoneSize) - ((zoneSize-1)/2));
//				int yOff = ((j / zoneSize) - ((zoneSize-1)/2));
//				int x0 = x + xOff;
//				int y0 = y + yOff;
//				double dist = Math.sqrt(xOff*xOff + yOff*yOff);
////				System.out.println("xOff: "+xOff+", yOff: "+yOff+", dist: "+dist);
//				if (x0 > WIDTH - 1 || x0 < 0 || y0 > HEIGHT - 1 || y0 < 0) {
//                    continue;
//                }
//				if (dist > ((zoneSize-1)/2)){
//					continue;
//				}
//				
//				int diffMax = pixList[x0 + WIDTH * y0] - localMax;
//				int diffMin = pixList[x0 + WIDTH * y0] - localMin;
//				if (diffMax > 0) localMax += diffMax;
//				if (diffMin < 0) localMin += diffMin;
//				//*(2/(dist*dist))
//			}
//			if ((localMax - localMin) < 32 ) {
//				localMax += 16;
//				localMin -= 16;
//			}
//			localMax = localMax<32 ? 32 : localMax;
//			localMax = localMax>255 ? 255 : localMax;
//			localMin = localMin>224 ? 224 : localMin;
//			localMin = localMin<0 ? 0 : localMin;
//			localGain = 255.0/(localMax-localMin);	
//			pixContrasted[i] = (int) ( (pixList[i] - localMin)*localGain) ;
//
//			System.out.println( ((double)i / pixList.length) * 100 );
//		}
		
		// clipping  0 - 255
		for (int i = 0; i < pixContrasted.length; i++) {
			if(pixContrasted[i]>255) pixContrasted[i] = 255;
			if(pixContrasted[i]<0) pixContrasted[i] = 0;
		}
		
		// Jarvis Dithering
		for (int i = 0; i < pixContrasted.length; ++i) {
            int o = pixContrasted[i];
            int n = o <= 0x80 ? 0 : 0xff;

            int x = i % WIDTH;
            int y = i / WIDTH;

            pixDithered[i] = n;
            
            for (int j = 0; j != 12; ++j) {
                int x0 = x + matrixJarvis[j].dx;
                int y0 = y + matrixJarvis[j].dy;
                if (x0 > WIDTH - 1 || x0 < 0 || y0 > HEIGHT - 1 || y0 < 0) {
                    continue;
                }
                // the residual quantization error
                // warning! have to cast to signed int before calculation!
                int d = (int) ((o - n) * matrixJarvis[j].coef);
                // keep a value in the <min; max> interval
                int a = pixContrasted[x0 + WIDTH * y0] + d;
                if (a > 0xff) {
                    a = 0xff;
                }
                else if (a < 0) {
                    a = 0;
                }
                pixContrasted[x0 + WIDTH * y0] = a;
            }
        }

		return pixDithered;
	}
	
	public int[] getFloydDitheredMonochrom() {
		int pixelWithError, pixelDithered, error;
		boolean notLeft, notRight, notBottom;
		int[] pixDithered = new int[pixList.length];

		// generate histogram origin
		for (int i = 0; i < pixList.length; i++) {
			histogramOrigin[pixList[i]]++;
		}

		// cumulative histogram
		histogramCumulated[0] = histogramOrigin[0]; 
		for (int i = 1; i < 256; ++i) {
			histogramCumulated[i] = histogramOrigin[i] + histogramCumulated[i - 1];
        }
		
		// histogram normalise & gamma
		double in, out;
		double gamma = 1.4;
		for (int i = 0; i < pixList.length; i++) {
			
			in = 255 * histogramCumulated[pixList[i]] / pixList.length;
			histogramNormalised[(int) in]++;
			out = Math.pow( (in/255.0), 1/gamma) * 255.0;
			histogramGamma[(int) out]++;
			pixList[i] = (int) ( out ) ;
			if(pixList[i]>255) pixList[i] = 255;
			if(pixList[i]<0) pixList[i] = 0;
		}
		
		for (int i = 0; i < 256; i++) {
			System.out.println(i +", "+ histogramOrigin[i] +", "+ histogramCumulated[i] +", "+ histogramNormalised[i] +", "+ histogramGamma[i]);
		}
		
		//dithering
		for (int pixCount = 0; pixCount < pixList.length; pixCount++) {

			// are we on a corner ?
			notLeft = pixCount%WIDTH!=0;
			notBottom = pixCount < WIDTH*(HEIGHT-1);
			notRight = (pixCount+1)%WIDTH!=0;

			//error was propagated in the existing  pixDithered[] array
			pixelWithError = pixDithered[pixCount] + pixList[pixCount];

			// black or white
			if (pixelWithError < 128) pixelDithered = 0;
			else pixelDithered = 255;

			// set the actual pixel
			pixDithered[pixCount] = pixelDithered;

			// get the error of the aproximation
			error = pixelWithError - pixelDithered;
			
			// propagate error
			if (notRight) pixDithered[pixCount+1] += 7*error/16;
			if (notLeft && notBottom) pixDithered[pixCount+(WIDTH-1)] += 3*error/16;
			if (notBottom) pixDithered[pixCount+(WIDTH)] += 5*error/16;
			if (notRight && notBottom) pixDithered[pixCount+(WIDTH+1)] += 1*error/16;
		}
		
		return pixDithered;
	}
	
//	public byte[] getDitheredBits() {
//		
//		int[] pixDithered = getFloydDitheredMonochrom();
//		
//		//generate image with pixel bit in bytes
//		byte[] pixBytes = new byte[(HEIGHT/8) * WIDTH ];
//
//		int mask = 0x01;
//		int x, y;
//		for (int i = 0; i < pixBytes.length; i++) {
//			for (int j = 0; j < 8; j++) {
//				mask = 0b10000000 >>> j;
//				x = ((i%(HEIGHT/8)*8 ) +j)  ;
//				y = i / (HEIGHT/8);
//				if ( pixDithered[x+(y*WIDTH)] == 0 ) {
//					pixBytes[i] = (byte) (pixBytes[i] | mask);
//				}
//			}
//		}
//
//		return pixBytes;
//	}

	public void writeToFile() {
		if (fileWriterThread == null || !fileWriterThread.isAlive()) {
			fileWriterThread = null;
			fileWriterThread = new Thread(new ImageFileWriter());
			fileWriterThread.start();
		} else {
			System.out.println("File capture thread did not finished.");
		}
	}


	private class ImageFileWriter implements Runnable {
		private BufferedImage bufImage;
		private WritableRaster wr;
		private ImageWriteParam imageWriteParam;
		private IIOImage outputImage;
		

		public ImageFileWriter() {
			bufImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
			wr = bufImage.getData().createCompatibleWritableRaster();
			imageWriteParam = imageWriter.getDefaultWriteParam();
			imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			imageWriteParam.setCompressionQuality(JPEG_QUALITY);

		}

		@Override
		public void run() {
			// make image
			wr.setPixels(0, 0, WIDTH, HEIGHT, getFloydDitheredMonochrom());   //dithered image
//			wr.setPixels(0, 0, WIDTH, HEIGHT, pixList);	 // grayscale image		
//			wr.setPixels(0, 0, WIDTH, HEIGHT, getJarvisDitheredMonochom());   //dithered image
//			wr.setPixels(0, 0, WIDTH, HEIGHT, pixContrasted);	 // grayscale image		
			
			bufImage.setData(wr);
			outputImage = new IIOImage(bufImage, null, null);

			// make filename with date
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
			String dateString = sdf.format(new Date());

			// file to write
			File imageFile = new File(filePath+dateString+".jpg");

			//write jpeg image file
			try {
				imageWriter.setOutput(new FileImageOutputStream( imageFile ));
				imageWriter.write(null, outputImage, imageWriteParam);
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}

	}
	
	public static void main(String[] args) {
		MonochromImageWin mono = new MonochromImageWin();
		File imgFile = new File(filePath+"source001.png");
		mono.setFile(imgFile);
		mono.writeToFile();
	}
	
	private class coef {
		public int dx;
		public int dy;
		public double coef;
		
		public coef(int dx, int dy, double coef) {
			this.dx = dx;
			this.dy = dy;
			this.coef = coef;
		}
	}
}

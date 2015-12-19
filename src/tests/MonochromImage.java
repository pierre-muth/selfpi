package tests;

import java.awt.image.BufferedImage;
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

public class MonochromImage {
	public static final float JPEG_QUALITY = 0.97f;	
	public static final int HEIGHT = Camera.IMG_HEIGHT;
	public static final int WIDTH = Camera.IMG_WIDTH;
	public static final String filePath = "/home/pi/Photos/POLAPI_";

	private ImageWriter jpgWriter;
	private int[] pixList;
	private Thread fileWriterThread;

	public MonochromImage() {
		jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
	}

	public void setPixels(int[] pixList) {
		this.pixList = pixList;
	}

	public byte[] getDitheredMonochrom() {
		int pixelWithError, pixelDithered, error;
		boolean notLeft, notRight, notBottom;
		int[] pixDest = new int[pixList.length];
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
		
		gain = 255.0/(max-min);		
		
		System.out.println("Gain: "+gain+", offset: "+min);
		
		// normalise min-max to 0 - 255
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (int) ( (pixList[i] - min)*gain) ;
			if(pixList[i]>255) pixList[i] = 255;
			if(pixList[i]<0) pixList[i] = 0;
		}
		
		//dithering
		for (int pixCount = 0; pixCount < pixList.length; pixCount++) {

			notLeft = pixCount%WIDTH!=0;
			notBottom = pixCount < WIDTH*(HEIGHT-1);
			notRight = (pixCount+1)%WIDTH!=0;

			pixelWithError = pixDest[pixCount] + pixList[pixCount];

			if (pixelWithError < 128) pixelDithered = 0;
			else pixelDithered = 255;

			pixDest[pixCount] = pixelDithered;

			error = pixelWithError - pixelDithered;

			if (notRight) pixDest[pixCount+1] += 7*error/16;
			if (notLeft && notBottom) pixDest[pixCount+(WIDTH-1)] += 3*error/16;
			if (notBottom) pixDest[pixCount+(WIDTH)] += 5*error/16;
			if (notRight && notBottom) pixDest[pixCount+(WIDTH+1)] += 3*error/16;
		}

		//generate image with pixel bit in bytes
		byte[] pixBytes = new byte[(HEIGHT/8) * WIDTH ];

		int mask = 0x01;
		int x, y;
		for (int i = 0; i < pixBytes.length; i++) {
			for (int j = 0; j < 8; j++) {
				mask = 0b10000000 >>> j;
				x = i / (HEIGHT/8);
				y = (HEIGHT-1) - ((i%(HEIGHT/8)*8 ) +j)  ;
				if ( pixDest[x+(y*WIDTH)] == 0 ) {
					pixBytes[i] = (byte) (pixBytes[i] | mask);
				}
			}
		}

		return pixBytes;
	}

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
		private ImageWriteParam jpgWriteParam;
		private IIOImage outputImage;

		public ImageFileWriter() {
			bufImage = new BufferedImage(Camera.IMG_WIDTH, Camera.IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
			wr = bufImage.getData().createCompatibleWritableRaster();
			jpgWriteParam = jpgWriter.getDefaultWriteParam();
			jpgWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			jpgWriteParam.setCompressionQuality(JPEG_QUALITY);

		}

		@Override
		public void run() {
			// make image
			wr.setPixels(0, 0, WIDTH, HEIGHT, pixList);
			bufImage.setData(wr);
			outputImage = new IIOImage(bufImage, null, null);

			// make filename with date
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
			String dateString = sdf.format(new Date());

			// file to write
			File imageFile = new File(filePath+dateString+".jpg");

			//write jpeg image file
			try {
				jpgWriter.setOutput(new FileImageOutputStream( imageFile ));
				jpgWriter.write(null, outputImage, jpgWriteParam);
			} catch (IOException e) {
				e.printStackTrace();
			} 
		}


	}


}

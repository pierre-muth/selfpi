package selfpi;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import org.apache.commons.lang3.text.WordUtils;

public class MonochromImage {
	public static final float JPEG_QUALITY = 0.97f;	
	public boolean printed = false;
	public boolean shared = false;
	public boolean reprinted = false;

	private ImageWriter imageWriter;
	private int[] pixListPicture;
	private int[] pixListWinner;
	private Thread fileWriterThread;
	private String numberFileName = "0001";
	private ArrayList<String> sentences;
	private int count = 0;
	
	private File imageFile;

	public MonochromImage() {
		imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		sentences = new ArrayList<>();
		
		String line;
		
		try (BufferedReader br = new BufferedReader( new FileReader(SelfPi.SENTENCESPATH) )){
			do {
				
				line = br.readLine();
				if (line != null)
					sentences.add(line);
				
			} while (line != null);

		} catch (IOException e) {
			System.out.println("Error in phrase.txt");
		};

	}

	public void setPixels(int[] pixList) {
		this.pixListPicture = pixList;
		// filename with second number.
		numberFileName = Integer.toString( (int) (new Date().getTime() /1000) );
	}

	public void chooseRandomImage(){
		File folder = new File(SelfPi.souvenirFilePath);
		File[] listOfFiles = folder.listFiles();

		int random = (int) (Math.random()*listOfFiles.length);

		try {
			BufferedImage randomImage = ImageIO.read(listOfFiles[random]);
			byte[] bytelist = ((DataBufferByte) randomImage.getRaster().getDataBuffer()).getData();
			pixListWinner = new int[randomImage.getWidth()*randomImage.getWidth()];
			for (int i = 0; i < pixListWinner.length; i++) {
				pixListWinner[i] = Byte.toUnsignedInt( bytelist[i] );
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void chooseFunnyImage(){
		File folder = new File(SelfPi.funnyImageFilefolder);
		File[] listOfFiles = folder.listFiles();

		int random = (int) (Math.random()*listOfFiles.length);

		try {
			BufferedImage randomImage = ImageIO.read(listOfFiles[random]);
			byte[] bytelist = ((DataBufferByte) randomImage.getRaster().getDataBuffer()).getData();
			pixListWinner = new int[randomImage.getWidth()*randomImage.getWidth()];
			for (int i = 0; i < pixListWinner.length; i++) {
				pixListWinner[i] = Byte.toUnsignedInt( bytelist[i] );
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public byte[] getSentence(){
		
		int rand = (int) (Math.random()*sentences.size());
//		String sentence = "     "+numberFileName.replace(".jpg", "")+"\n"+sentences.get(rand)+"\n";
		String sentence = WordUtils.wrap(sentences.get(rand), 48) +"\n";
		return sentence.getBytes();
	}
	
	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public byte[] getFilenumberInBytes() {
		String number = "     "+numberFileName+"\n";
		return number.getBytes();
	}
	
	public File getLastImageFile() {
		return imageFile;
	}

	public void setFile(File file){
		numberFileName = file.getName().replace(".jpg", "");
		try {
			BufferedImage bufImage = ImageIO.read(file);
			byte[] bytelist = ((DataBufferByte) bufImage.getRaster().getDataBuffer()).getData();
			pixListPicture = new int[bufImage.getWidth()*bufImage.getWidth()];
			for (int i = 0; i < pixListPicture.length; i++) {
				pixListPicture[i] = Byte.toUnsignedInt( bytelist[i] );
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int[] getDitheredMonochrom(TicketMode mode) {
		int HEIGHT = SelfPi.IMG_HEIGHT;
		int WIDTH = SelfPi.IMG_WIDTH;
		
		int[] pixels;
		if (mode == TicketMode.WINNER || mode == TicketMode.FUNNY)
			pixels = pixListWinner;
		else
			pixels = pixListPicture; 
		
		int pixelWithError, pixelDithered, error;
		boolean notLeft, notRight, notBottom;
		int[] pixDithered = new int[pixels.length];
		int min = 255, max = 0;
		double gain = 1;

		// search min-max
		for (int i = 0; i < pixels.length; i++) {
			if (pixels[i] > max) max = pixels[i];
			if (pixels[i] < min) min = pixels[i];
		}

		//limiting
		max = max<32 ? 32 : max;
		min = min>224 ? 224 : min;

		// calculate gain
		gain = 255.0/(max-min);		

		System.out.println("Picture Gain: "+gain+", offset: "+min);

		// normalise min-max to 0 - 255
		for (int i = 0; i < pixels.length; i++) {
			pixels[i] = (int) ( (pixels[i] - min)*gain) ;
			if(pixels[i]>255) pixels[i] = 255;
			if(pixels[i]<0) pixels[i] = 0;
		}

		//dithering
		for (int pixCount = 0; pixCount < pixels.length; pixCount++) {

			// are we on a corner ?
			notLeft = pixCount%WIDTH!=0;
			notBottom = pixCount < WIDTH*(HEIGHT-1);
			notRight = (pixCount+1)%WIDTH!=0;

			//error was propagated in the existing  pixDithered[] array
			pixelWithError = pixDithered[pixCount] + pixels[pixCount];

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

	public byte[] getDitheredBits(TicketMode mode) {
		int HEIGHT = SelfPi.IMG_HEIGHT;
		int WIDTH = SelfPi.IMG_WIDTH;

		int[] pixDithered = getDitheredMonochrom(mode);

		//generate image with pixel bit in bytes
		byte[] pixBytes = new byte[(HEIGHT/8) * WIDTH ];

		int mask = 0x01;
		int x, y;
		for (int i = 0; i < pixBytes.length; i++) {
			for (int j = 0; j < 8; j++) {
				mask = 0b10000000 >>> j;
		x = ((i%(HEIGHT/8)*8 ) +j)  ;
		y = i / (HEIGHT/8);
		if ( pixDithered[x+(y*WIDTH)] == 0 ) {
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
		private ImageWriteParam imageWriteParam;
		private IIOImage outputImage;


		public ImageFileWriter() {
			int HEIGHT = SelfPi.IMG_HEIGHT;
			int WIDTH = SelfPi.IMG_WIDTH;

			bufImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
			wr = bufImage.getData().createCompatibleWritableRaster();
			imageWriteParam = imageWriter.getDefaultWriteParam();
			imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			imageWriteParam.setCompressionQuality(JPEG_QUALITY);

		}

		@Override
		public void run() {
			int HEIGHT = SelfPi.IMG_HEIGHT;
			int WIDTH = SelfPi.IMG_WIDTH;

			// make image
			//			wr.setPixels(0, 0, WIDTH, HEIGHT, getDitheredMonochrom());   //dithered image
			wr.setPixels(0, 0, WIDTH, HEIGHT, pixListPicture);	 // grayscale image		
			bufImage.setData(wr);
			outputImage = new IIOImage(bufImage, null, null);

			// make filename with date
			//			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
			//			String dateString = sdf.format(new Date());

			// use filename with second number.
			String numberString = MonochromImage.this.numberFileName;
			String path;
			path = SelfPi.souvenirFilePath;

			// file to write
			imageFile = new File(path+numberString+".jpg");

			//write jpeg image file
			try {
				imageWriter.setOutput(new FileImageOutputStream( imageFile ));
				imageWriter.write(null, outputImage, imageWriteParam);

			} catch (IOException e) {
				e.printStackTrace();
			} 
		}

	}

}

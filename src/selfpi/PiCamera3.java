package selfpi;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

public class PiCamera3 implements Runnable {
	// defaults :
	public static final float JPEG_QUALITY = 0.97f;	
	public static int IMG_HEIGHT = 576;	
	public static int IMG_RATIO = 1;
	public static int IMG_WIDTH = (int) (IMG_HEIGHT * IMG_RATIO);	
	public static String RASPIVID = 
			"/opt/vc/bin/raspividyuv"+	
			" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+		//image dimension
			" -p 0,0,1024,1024"+					//preview position
			" -ex night -fps 0 -ev +0.1 -co 50 -t 0 -cfx 128:128 -o -";		//no timeout, monochom effect


	private int[] pixBuf = new int[IMG_HEIGHT * IMG_WIDTH ];
	private int[] pixList = new int[IMG_HEIGHT * IMG_WIDTH ];
	
	private ArrayList<int[]> animatedFrames = new ArrayList<>();
	
	private AtomicBoolean exit = new AtomicBoolean(false);
	private AtomicBoolean frames_ready = new AtomicBoolean(false);
	
	private Process raspividyuvProcess;
	private int raspividyuvPID;
	
	public PiCamera3(int width, int height) {
		IMG_HEIGHT = height;
		IMG_WIDTH = width;
		pixBuf = new int[IMG_HEIGHT * IMG_WIDTH ];
		pixList = new int[IMG_HEIGHT * IMG_WIDTH ];
//		RASPIVID =   
//				"raspividyuv"+	
//				" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+			//image dimension
//				" -p 0,0,"+SelfPi.screenHeight+","+SelfPi.screenHeight+  // output location and size
//				" -ev "+SelfPi.cameraExposure+" -co "+SelfPi.cameraContast+
//				" -ex sports -fps 90 -t 0 -o -";	//no timeout, monochom effect
		
		RASPIVID = 
//				"/opt/vc/bin/raspitimescan"+
				"./raspitimescan"+	
				" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+			//image dimension
				" -p 0,0,"+768+","+768+  // output location and size
				" -ev "+SelfPi.cameraExposure+" -co "+SelfPi.cameraContast+
				" -hf -md 6 -drc low -ex sports -fps 90 -t 0 -o -";	//no timeout, monochom effect
		
	}
	
	@Override
	public void run() {

		try {
			// launch video process
			raspividyuvProcess = Runtime.getRuntime().exec(RASPIVID);
			BufferedInputStream bis = new BufferedInputStream(raspividyuvProcess.getInputStream());

			System.out.println("starting camera with "+RASPIVID);
			
			try {
				Field field;
				field = raspividyuvProcess.getClass().getDeclaredField("pid");
				field.setAccessible(true);
				raspividyuvPID = (int) field.get( raspividyuvProcess );
				System.out.println("raspividyuv Process is: "+  raspividyuvPID);
			} catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
				e.printStackTrace();
			} 

			int pixCount = 0; 
			int pixRead = bis.read();
			pixBuf[pixCount] = pixRead;
			pixCount++;	// we just read the first pixel yet

			while (pixRead != -1 && !exit.get()) {

				// read a pixel
				pixRead = bis.read();
				pixBuf[pixCount] = pixRead;
				
				// inc pixel counter
				pixCount++;
				
				// a frame has arrived
				if (pixCount == (IMG_WIDTH * IMG_HEIGHT)) {
					pixCount = 0;
					if (animatedFrames.size() < 30){
						animatedFrames.add(pixBuf.clone());
					} else {
						frames_ready.set(true);
					}
				}
			}

			System.out.println("end camera");
			raspividyuvProcess.destroy();
			bis.close();
			System.exit(0);

		} catch (IOException ieo) {
			ieo.printStackTrace();
		}
	}
	
	public void takeApictureToFile(File jpgFile) throws FileNotFoundException, IOException {
		animatedFrames.clear();
		BufferedImage bufImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster wr = bufImage.getData().createCompatibleWritableRaster();
		ImageWriteParam imageWriteParam;
		IIOImage outputImage;
		ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		int[] stillFrame;
		
		imageWriteParam = imageWriter.getDefaultWriteParam();
		imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		imageWriteParam.setCompressionQuality(JPEG_QUALITY);
		
		// ask for recording a frame
		try {
			Runtime.getRuntime().exec("kill -SIGUSR2 "+raspividyuvPID);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		// try to wait until we get all the frames
		int loops = 0;
		do {
			try { Thread.sleep(100); } catch (InterruptedException e) {}
			System.out.print(".");
			loops++;
		} while (!frames_ready.get() && loops < 100);
		System.out.print("\n");
		
		// get the last frame for the still jpeg
		stillFrame = animatedFrames.get(animatedFrames.size()-1).clone();
		
		// write jpeg image file
		try {
			bufImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
			wr = bufImage.getData().createCompatibleWritableRaster();
			wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, stillFrame);		
			bufImage.setData(wr); 
			outputImage = new IIOImage(bufImage, null, null);
			imageWriter.setOutput(new FileImageOutputStream( jpgFile ));
			imageWriter.write(null, outputImage, imageWriteParam);
		} catch (IOException e) {
			e.printStackTrace();
		} 

		frames_ready.set(false);
	}
	
	public void saveLastAnimationToFile(File gifFile) throws FileNotFoundException, IOException {
		if (animatedFrames.isEmpty()) {
			System.out.println("saveLastAnimationToFile: no frames in memory");
			return;
		}
		
		BufferedImage bufImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster wr = bufImage.getData().createCompatibleWritableRaster();
		ImageOutputStream output = new FileImageOutputStream(gifFile);
		GifSequenceWriter writer = new GifSequenceWriter(output, BufferedImage.TYPE_BYTE_GRAY, 50, true);

		// reduce the color palette to 16 to reduce the size of the output gif 
		for (Iterator<int[]> iterator = animatedFrames.iterator(); iterator.hasNext();) {
			int[] frameBytes = iterator.next();
			for (int i = 0; i < frameBytes.length; i++) {
				frameBytes[i] = frameBytes[i] & 0xF0; 
			}
		}
		
		// make the animated gif (ping-pong loop)
		for(int frame=0; frame<animatedFrames.size(); frame++) {
			wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, animatedFrames.get(frame));		
			bufImage.setData(wr);
			writer.writeToSequence(bufImage);
		}
		for(int frame=animatedFrames.size()-1; frame>0; frame --) {
			wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, animatedFrames.get(frame));		
			bufImage.setData(wr);
			writer.writeToSequence(bufImage);
		}
		
		writer.close();
		output.close();
	}
	
	public void takeApictureAndAnimationToFile(File jpgFile, File gifFile) throws FileNotFoundException, IOException {
		long start = new Date().getTime();
		
		animatedFrames.clear();
		BufferedImage bufImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
		WritableRaster wr = bufImage.getData().createCompatibleWritableRaster();
		ImageWriteParam imageWriteParam;
		IIOImage outputImage;
		ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		int[] stillFrame;
		
		imageWriteParam = imageWriter.getDefaultWriteParam();
		imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		imageWriteParam.setCompressionQuality(JPEG_QUALITY);
		
		System.out.println("0: "+ (new Date().getTime()-start)/1000.0);
		
		// ask for recording a frame
		try {
			Runtime.getRuntime().exec("kill -SIGUSR2 "+raspividyuvPID);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		System.out.println("1: "+ (new Date().getTime()-start)/1000.0);
		
		// try to wait until we get all the frames
		int loops = 0;
		do {
			try { Thread.sleep(100); } catch (InterruptedException e) {}
			System.out.print(".");
			loops++;
		} while (!frames_ready.get() && loops < 100);
		System.out.print("\n");
		
		System.out.println("2: "+ (new Date().getTime()-start)/1000.0);

		// get the last frame for the still jpeg
		stillFrame = animatedFrames.get(animatedFrames.size()-1).clone();
		
		System.out.println("3: "+ (new Date().getTime()-start)/1000.0);
		
		// reduce the color palette to 32 to try to reduce the size of the output gif 
		for (Iterator<int[]> iterator = animatedFrames.iterator(); iterator.hasNext();) {
			int[] frameBytes = iterator.next();
			for (int i = 0; i < frameBytes.length; i++) {
				frameBytes[i] = frameBytes[i] & 0xF8; 
			}
		}
		 
		System.out.println("4: "+ (new Date().getTime()-start)/1000.0);

		ImageOutputStream output = new FileImageOutputStream(gifFile);
		GifSequenceWriter writer = new GifSequenceWriter(output, BufferedImage.TYPE_BYTE_GRAY, 50, true);
		// make the animated gif (ping-pong loop)
		for(int frame=0; frame<animatedFrames.size(); frame++) {
			wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, animatedFrames.get(frame));		
			bufImage.setData(wr);
			writer.writeToSequence(bufImage);
		}
		for(int frame=animatedFrames.size()-1; frame>0; frame --) {
			wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, animatedFrames.get(frame));		
			bufImage.setData(wr);
			writer.writeToSequence(bufImage);
		}
		writer.close();
		output.close();
		
		System.out.println("5: "+ (new Date().getTime()-start)/1000.0);

		// write jpeg image file
		try {
			bufImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
			wr = bufImage.getData().createCompatibleWritableRaster();
			wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, stillFrame);		
			bufImage.setData(wr); 
			outputImage = new IIOImage(bufImage, null, null);
			imageWriter.setOutput(new FileImageOutputStream( jpgFile ));
			imageWriter.write(null, outputImage, imageWriteParam);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
		System.out.println("6: "+ (new Date().getTime()-start)/1000.0);
		
		frames_ready.set(false);
		
		
		System.out.println("7: "+ (new Date().getTime()-start)/1000.0);
	}
	
	public void timeScanSwitch() {
		try {
			Runtime.getRuntime().exec("kill -SIGUSR1 "+raspividyuvPID);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}
	
	public void close() {
		exit.set(true);
	}

}

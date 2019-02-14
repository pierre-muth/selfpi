package selfpi;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

public class PiCamera2 implements Runnable {
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
	private long FRAME_LENGTH = (IMG_WIDTH * IMG_HEIGHT) + ((IMG_WIDTH * IMG_HEIGHT)/2);
	
	private AtomicBoolean exit = new AtomicBoolean(false);
	private AtomicBoolean frame_ready = new AtomicBoolean(false);
	private AtomicBoolean request_frame = new AtomicBoolean(false);
	private AtomicBoolean recording_frame = new AtomicBoolean(false);
	
	private Process raspividyuvProcess;
	private int raspividyuvPID;
	
	public PiCamera2(int width, int height) {
		IMG_HEIGHT = height;
		IMG_WIDTH = width;
		FRAME_LENGTH = (IMG_WIDTH * IMG_HEIGHT) + ((IMG_WIDTH * IMG_HEIGHT)/2);
		pixBuf = new int[IMG_HEIGHT * IMG_WIDTH ];
		pixList = new int[IMG_HEIGHT * IMG_WIDTH ];
//		RASPIVID =   
//				"raspividyuv"+	
//				" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+			//image dimension
//				" -p 0,0,"+SelfPi.screenHeight+","+SelfPi.screenHeight+  // output location and size
//				" -ev "+SelfPi.cameraExposure+" -co "+SelfPi.cameraContast+
//				" -ex sports -fps 90 -t 0 -o -";	//no timeout, monochom effect
		
		RASPIVID = 
				"/opt/vc/bin/raspividyuv"+	
				" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+			//image dimension
				" -p 0,0,"+768+","+768+  // output location and size
				" -ev "+SelfPi.cameraExposure+" -co "+SelfPi.cameraContast+
				" -md 6 -drc low -ex sports -fps 90 -t 0 -o -";	//no timeout, monochom effect
		
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
					if (request_frame.get()){
						pixList = pixBuf.clone();
						request_frame.set(false);
						frame_ready.set(true);
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
	
	public void takeApictureToFile(File file) {
		BufferedImage bufImage;
		WritableRaster wr;
		ImageWriteParam imageWriteParam;
		IIOImage outputImage;
		
		ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		
		bufImage = new BufferedImage(IMG_WIDTH, IMG_HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
		wr = bufImage.getData().createCompatibleWritableRaster();
		imageWriteParam = imageWriter.getDefaultWriteParam();
		imageWriteParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
		imageWriteParam.setCompressionQuality(JPEG_QUALITY);
		
		// ask for recording a frame and wait
		request_frame.set(true);
		
		try {
			Runtime.getRuntime().exec("kill -SIGUSR2 "+raspividyuvPID);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		
		while (!frame_ready.get()) {
			try { Thread.sleep(20); } catch (InterruptedException e) {}
			System.out.print(".");
		}
		System.out.print("\n");
		
		// make image
		wr.setPixels(0, 0, IMG_WIDTH, IMG_HEIGHT, pixList.clone());	 // grayscale image		
		bufImage.setData(wr);
		outputImage = new IIOImage(bufImage, null, null);

		//write jpeg image file
		try {
			imageWriter.setOutput(new FileImageOutputStream( file ));
			imageWriter.write(null, outputImage, imageWriteParam);
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
		frame_ready.set(false);
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

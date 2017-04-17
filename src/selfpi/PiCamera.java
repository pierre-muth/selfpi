package selfpi;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

public class PiCamera implements Runnable {
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
	
	private AtomicBoolean exit = new AtomicBoolean(false);
	
	public PiCamera(int width, int height) {
		IMG_HEIGHT = height;
		IMG_WIDTH = width;
		pixBuf = new int[IMG_HEIGHT * IMG_WIDTH ];
		pixList = new int[IMG_HEIGHT * IMG_WIDTH ];
		RASPIVID = 
				"/opt/vc/bin/raspividyuv"+	
				" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+			//image dimension
				" -p 0,0,"+SelfPi.screenHeight+","+SelfPi.screenHeight+  // output location and size
				" -ex night -fps 0 -ev +0.1 -co 50 -t 0 -cfx 128:128 -o -";	//no timeout, monochom effect
	}
	
	@Override
	public void run() {

		try {
			// launch video process
			Process p = Runtime.getRuntime().exec(RASPIVID);
			BufferedInputStream bis = new BufferedInputStream(p.getInputStream());

			System.out.println("starting camera");

			int pixRead = bis.read();
			int pixCount = 1; // we just read the first pixel yet

			while (pixRead != -1 && !exit.get()) {
				// after skipping chroma data, end of a frame
				if (pixCount > (IMG_WIDTH * IMG_HEIGHT) + ((IMG_WIDTH * IMG_HEIGHT)/2) -1) {
					pixCount = 0;
				}
				pixRead = bis.read();
				// first are only luminance pixel info
				if (pixCount < (IMG_WIDTH * IMG_HEIGHT)) {
					pixBuf[pixCount] = pixRead;
				}
				pixCount++;
				// a luminance frame arrived
				if (pixCount == (IMG_WIDTH * IMG_HEIGHT)) {
//					pixList = null;
					pixList = pixBuf.clone();
				}
			}

			System.out.println("end camera");
			p.destroy();
			bis.close();
			System.exit(0);

		} catch (IOException ieo) {
			ieo.printStackTrace();
		}
	}
	
	public int[] getAFrame() {
		return pixList.clone();
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
	}
	
	public void close() {
		exit.set(true);
	}

}

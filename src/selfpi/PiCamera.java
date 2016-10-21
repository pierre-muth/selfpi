package selfpi;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class PiCamera implements Runnable {

	public static final int IMG_HEIGHT = 576;						//Printer width = 576 pixels
	public static final int IMG_RATIO = 1;
	public static final int IMG_WIDTH = (int) (IMG_HEIGHT * IMG_RATIO);	
	public static final int FPS = 12;	
	public static final String RASPIVID = 
			"/opt/vc/bin/raspividyuv"+	//frame rate
			" -w "+IMG_WIDTH+" -h "+IMG_HEIGHT+			//image dimension
			" -p 0,0,1024,1024"+
			" -ex night -fps 0 -ev +0.1 -co 50 -t 0 -cfx 128:128 -o -";				//no timeout, monochom effect


	private int[] pixBuf = new int[IMG_HEIGHT * IMG_WIDTH ];
	private int[] pixList = new int[IMG_HEIGHT * IMG_WIDTH ];
	
	private AtomicBoolean exit = new AtomicBoolean(false);

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
	
	public void close() {
		exit.set(true);
	}

}

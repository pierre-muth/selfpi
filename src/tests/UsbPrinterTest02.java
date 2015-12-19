package tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;

import javax.imageio.stream.FileImageInputStream;

public class UsbPrinterTest02 {
	private static final int[] TEST = {'T', 'e', 's', 't', };
	private static final int width = 576;
	private static final int height = 128;
	/* download graphic data
	 * Hex 1D 38 4C p1 p2 p3 p4 30 53 a kc1 kc2 b xL xH yL yH [c d1...dk]1...[c d1...dk]b
	 * */
	private static final int dataLength = ((width/8)*height) +11;
	private static final int p1 = 0xFF & dataLength;
	private static final int p2 = dataLength >>>  8;
	private static final int p3 = dataLength >>> 16;
	private static final int p4 = dataLength >>> 24;
	private static final int xL = 0xFF & width;
	private static final int xH = width >>> 8;
	private static final int yL = 0xFF & height;
	private static final int yH = height >>> 8;
	
	private static final int[] DL_GRAPH = {
		0x1D, 0x38, 0x4C, 			// download graphic data
		p1, p2, p3, p4,				// byte size after m (+11)
		0x30, 0x53, 0x30, 			// m, fn, a
		0x20, 0x20, 0x01,			// key code, b
		xL, xH, yL, yH,				// xL, xH, yL, yH
		0x31						// c
	};											

	/* print graphic data
	 * Hex 1D 28 4C 06 00 30 55 kc1 kc2 x y
	 */
	private static final int[] PRINT_DL = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x55, 0x20, 0x20, 0x01, 0x01
	};
	
	
	// paper cut
	private static final int[] CUT = {
		0x1D, 'V', 'A', 0x10  
	};

	public static void main(String[] args) {

		// print test 
		try {

			OutputStream lp0 = new FileOutputStream(new File("/dev/usb/lp0"));
			
			byte[] img = new byte[(width/8)*height];
			for (int i = 0; i < img.length; i++) {
				if (Math.random() > 0.5) img[i] = 0x55;
				else img[i] = (byte) 0xAA;
			}
			
			for (int i = 0; i < TEST.length; i++) {
				lp0.write( TEST[i] );
			}		
			
			lp0.write(0x0A);

			long start = System.currentTimeMillis();
			send(lp0, DL_GRAPH);
			send(lp0, img);
			long end = System.currentTimeMillis();
			send(lp0, PRINT_DL);
			
			lp0.write( Long.toString(end-start).getBytes() );
			lp0.write(0x0A);
			
			send(lp0, CUT);

			lp0.close();

		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
	private static void send(OutputStream os, int[] toSend) {
			byte[] b = new byte[toSend.length];
			
			for (int i = 0; i < b.length; i++) 
				b[i] = (byte)toSend[i];
			
			send(os, b);
	}
	
	private static void send(OutputStream os, byte[] toSend) {
		try {
			os.write( toSend );
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}


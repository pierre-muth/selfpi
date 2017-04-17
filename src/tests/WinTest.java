package tests;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.usb.UsbException;

public class WinTest {
	private static final int width = 576;  // We print 90 deg rotated
	private static final int height = 576;
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
	
	private static TMT20 printer;
	
	private static byte[] getByteArray(int[] data) {
		byte[] b = new byte[data.length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) data[i];
		}
		return b;
	}

	public static void main(String[] args) {
		long start = 0;
		long endDither = 0;
		long endSending = 0;

		try {
			printer = new TMT20();
			start = System.currentTimeMillis();
			String test = "Windows test with usb pipe\n\n\n";
			printer.sendWithPipe(test.getBytes());
			
			BufferedImage imgbuf = null;
			try {
			    imgbuf = ImageIO.read(WinTest.class.getResource("basel.jpg"));
			} catch (IOException e) { }
			
			final BufferedImage monoImageresized = new BufferedImage(576, 576, BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = monoImageresized.createGraphics();
			g.drawImage(imgbuf, 0, 0, 576, 576, null);
			g.dispose();
			
			int[] pixels = new int[576*576];
			pixels =  monoImageresized.getData().getPixel(0, 0, pixels);
			MonochromImageWin monoimg = new MonochromImageWin();
			monoimg.setPixels(pixels);
			
//			byte[] img = monoimg.getDitheredMonochrom();
			endDither = System.currentTimeMillis();
			printer.sendWithPipe(getByteArray(DL_GRAPH));
//			printer.sendWithPipe(img);
			endSending = System.currentTimeMillis();
			printer.sendWithPipe(getByteArray(PRINT_DL));
			String stats = "Dither: "+(endDither-start)+", sending: "+(endSending-endDither)+"\n";
			printer.sendWithPipe(stats.getBytes());
			printer.sendWithPipe(getByteArray(CUT));
			
			
			
		} catch (SecurityException | UsbException e) {
			e.printStackTrace();
		}
		
		
		
	}

}

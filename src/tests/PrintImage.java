package tests;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.usb.UsbException;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class PrintImage {
	private static final int[] TEST = {'T', 'e', 's', 't', };
	private static final int width = Camera.IMG_HEIGHT;  // We print 90 deg rotated
	private static final int height = Camera.IMG_WIDTH;


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

	private static MonochromImageWin monoimg;
	private static Camera picam;
	private static OutputStream lp0;
	
	private static TMT20 printer;

	public static void main(String[] args) throws InterruptedException, FileNotFoundException {
		
		lp0 = new FileOutputStream(new File("/dev/usb/lp0"));

		GpioController gpio;
		GpioPinDigitalInput printButton;

		gpio = GpioFactory.getInstance();
		printButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_DOWN);
		printButton.addListener(new ButtonListener());

		System.out.println("Start pi cam");
		picam = new Camera();
		new Thread(picam).start();

		try {
			printer = new TMT20();
		} catch (SecurityException | UsbException e) {
			e.printStackTrace();
		}
		
		System.out.println("Press the big button !");

	}
	
	private static byte[] getByteArray(int[] data) {
		byte[] b = new byte[data.length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) data[i];
		}
		return b;
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

	static class ButtonListener implements GpioPinListenerDigital {
		long start;
		long endCapture;
		long endDither;
		long endSending;
		
		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isLow()) return;
			System.out.println("Print button pressed !");
			System.out.println("Get a frame");
			start = System.currentTimeMillis();
			
			monoimg = new MonochromImageWin();
			monoimg.setPixels(picam.getAFrame());
			
			endCapture = System.currentTimeMillis();

			// print test 
//			testWithFile();
//			testWithUsb();
			picam.close();
			printer.close();
		}
		
//		private void testWithUsb() {
//			byte[] img = monoimg.getDitheredMonochrom();
//			endDither = System.currentTimeMillis();
//			String test = "test with usb pipe\n";
//			printer.sendWithPipe(test.getBytes());
//			printer.sendWithPipe(getByteArray(DL_GRAPH));
//			printer.sendWithPipe(img);
//			endSending = System.currentTimeMillis();
//			printer.sendWithPipe(getByteArray(PRINT_DL));
//			String stats = "Capture: "+(endCapture-start)+", Dither: "+(endDither-endCapture)+", sending: "+(endSending-endDither)+"\n";
//			printer.sendWithPipe(stats.getBytes());
//			printer.sendWithPipe(getByteArray(CUT));
//		}
//		
//		private void testWithFile() {
//			try {
//				byte[] img = monoimg.getDitheredMonochrom();
//
//				endDither = System.currentTimeMillis();
//
//				for (int i = 0; i < TEST.length; i++) {
//					lp0.write( TEST[i] );
//				}		
//				lp0.write(0x0A);
//				
//				send(lp0, DL_GRAPH);
//				send(lp0, img);
//				endSending = System.currentTimeMillis();
//				send(lp0, PRINT_DL);
//
//				String stats = "Capture: "+(endCapture-start)+", Dither: "+(endDither-start)+", sending: "+(endSending-start);
//				
//				
//				lp0.write( stats.getBytes() );
//				lp0.write(0x0A);
//
//				send(lp0, CUT);
//
//				lp0.close();
//
//			} catch (IOException ioe) {
//				ioe.printStackTrace();
//			}
//		}
	}

}

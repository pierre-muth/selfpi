package selfpi;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;
import javax.usb.UsbClaimException;
import javax.usb.UsbConfiguration;
import javax.usb.UsbDevice;
import javax.usb.UsbDeviceDescriptor;
import javax.usb.UsbDisconnectedException;
import javax.usb.UsbEndpoint;
import javax.usb.UsbException;
import javax.usb.UsbHostManager;
import javax.usb.UsbHub;
import javax.usb.UsbInterface;
import javax.usb.UsbInterfacePolicy;
import javax.usb.UsbNotActiveException;
import javax.usb.UsbNotClaimedException;
import javax.usb.UsbNotOpenException;
import javax.usb.UsbPipe;

public class EpsonESCPOSPrinter {

	private static final String testString = "test usb4java\n";
	// The vendor ID 
	private static final short VENDOR_ID = 0x04b8;	//1208 in decimal
	// The product ID 
	private static short product_id = 0x0e15;	//3605 in decimal, default is TM-T20II

	// default values
	private static int width = 576;  // We print 90 deg rotated
	
	/* download graphic data to graphic buffer (TM-T20/II)
	 * Hex 1D 38 4C p1 p2 p3 p4 30 53 a kc1 kc2 b xL xH yL yH [c d1...dk]1...[c d1...dk]b
	 * */
	private static int[] DL_TO_GRAPHIC = {};											

	/* print graphic data from graphic buffer
	 * Hex 1D 28 4C 06 00 30 55 kc1 kc2 x y
	 */
	private static final int[] PRINT_GRAPHIC_BUF = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x55, 0x20, 0x20, 0x01, 0x01
	};
	
	/* Store the graphics data in the print buffer (raster format) (TM-T88IV)
	 * Hex 1D 38 4C p1 p2 p3 p4 30 70 a bx by c xL xH yL yH d1...dk
	 */
	private static int[] DL_TO_PRINT_BUF = {};	
	
	/* Print the graphics data in the print buffer
	 * Hex 1D 28 4C 02 00 30 fn
	 */
	private static final int[] PRINT_PRINTER_BUFFER = {
		0x1D, 0x28, 0x4C, 0x02, 0x00, 0x30, 0x32
	};
	
	// print NV graphics of key code 32 32   
	private static final int[] PRINT_NV_HEADER = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 32, 1, 1
	};
	
	// print NV graphics of key code 32 33
	private static final int[] PRINT_NV_FOOT_SOUVENIR = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 33, 1, 1
	};
	
	// print NV graphics of key code 32 34
		private static final int[] PRINT_NV_FOOT_WINNER = {
			0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 34, 1, 1
		};
	
	// paper cut
	private static final int[] CUT = {
		0x1D, 'V', 'A', 0x10  
	};
	
	// paper enter in user stting mode
	private static final int[] ENTER_USER_SETTINGS = {
		0x1D, 0x28, 0x45, 0x03, 0x00, 0x01, 0x49, 0x4E  
	};
		
	// paper exit the user setting mode
	private static final int[] END_USER_SETTINGS = {
			0x1D, 0x28, 0x45, 0x04, 0x00, 0x02, 0x4F, 0x55, 0x54  
	};
	
	// printing speed
	// GS ( K <Function 50>, Select the print speed, p. 451
    // 0x1d, 0x28, 0x4b, 0x02, 0x00, 0x32, m (01-09)
	private static int[] SET_SPEED = {};
	
	// paper exit the user setting mode
	private static final int[] LINE_FEED = { 0x0A };
	

	private UsbDevice device;
	private UsbEndpoint usbEndpoint;
	private UsbInterface usbInterface;
	private Thread usbPrinting;
	
	public EpsonESCPOSPrinter(short product_id) throws SecurityException, UsbException {
		EpsonESCPOSPrinter.product_id = product_id;
		
		// Search for printer, such as epson TM-T20
		device = findUsb(UsbHostManager.getUsbServices().getRootUsbHub());
		if (device == null) {
			System.err.println("Printer: vendor="+VENDOR_ID+", product="+product_id+" not found :(");
			if (!SelfPi.DEBUG) System.exit(1);
			return;
		}

		// Claim the interface
		UsbConfiguration configuration = device.getActiveUsbConfiguration();
		usbInterface = configuration.getUsbInterface((byte) 0);
		usbInterface.claim(new UsbInterfacePolicy() {            
			@Override
			public boolean forceClaim(UsbInterface usbInterface) {
				return true;
			}
		});

		usbEndpoint = usbInterface.getUsbEndpoint((byte) 1);
		System.out.println("Printer started");
		
		width = SelfPi.printerdots;  
		SET_SPEED = new int[] {0x1d, 0x28, 0x4b, 0x02, 0x00, 0x32, SelfPi.printerSpeed};		
		
		initPrinter(SelfPi.printDensity, SelfPi.printerSpeed);
		System.out.println("Printer configured and resetting...");
		
		try { Thread.sleep(5000); } catch (InterruptedException e) {}
		
		// Search for printer, such as epson TM-T20
		device = findUsb(UsbHostManager.getUsbServices().getRootUsbHub());
		if (device == null) {
			System.err.println("Printer: vendor="+VENDOR_ID+", product="+product_id+" not found :(");
			System.exit(1);
			return; 
		}

		// Claim the interface
		configuration = device.getActiveUsbConfiguration();
		usbInterface = configuration.getUsbInterface((byte) 0);
		
		try {
			usbInterface.claim(new UsbInterfacePolicy() {            
				@Override
				public boolean forceClaim(UsbInterface usbInterface) {
					return true;
				}
			});
		} catch (UsbClaimException e) {
			System.out.println("No need to re-claim usb interface.");
		} 
		
		usbEndpoint = usbInterface.getUsbEndpoint((byte) 1);
		
		System.out.println("Printer re-started");
		
		
	}

	public void close() {
		if (usbInterface == null) return;
		try {
			System.out.println("Releasing usb printer");
			usbInterface.release();
		} catch (UsbNotActiveException	| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		}
	}
	
	private void initPrinter(int printDensity, int printerSpeed) {
		int[] userSettings  = new int[] {
				0x1D, 0x28, 0x45, 
				0x07, 0x00,
				0x05, 
				0x05, (0x00FF & SelfPi.printDensity), ((0xFF00 & SelfPi.printDensity) >>> 8),
				0x06, (0x00FF & SelfPi.printerSpeed), 0x00
				};		
		
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("Still sending to printer");
			return;
		}
		
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			pipe.open();
			
			int sent = pipe.syncSubmit(getByteArray(ENTER_USER_SETTINGS));
			System.out.println(sent + " bytes sent to printer: Enter user setting mode command");
			sent = pipe.syncSubmit(getByteArray(userSettings));
			System.out.println(sent + " bytes sent to printer: user settings");
			sent = pipe.syncSubmit(getByteArray(END_USER_SETTINGS));
			System.out.println(sent + " bytes sent to printer: exit user setting mode command");
			
		} catch (UsbNotActiveException | UsbNotClaimedException
				| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		} finally {
			try {
				pipe.close();
			} catch (UsbNotActiveException | UsbNotOpenException
					| UsbDisconnectedException | UsbException e) {
				e.printStackTrace();
			}
		}
		
	}
	
	private static UsbDevice findUsb(UsbHub hub) {
		UsbDevice launcher = null;

		for (UsbDevice device: (List<UsbDevice>) hub.getAttachedUsbDevices()) {
			if (device.isUsbHub()) {
				launcher = findUsb((UsbHub) device);
				if (launcher != null) return launcher;
			} else {
				UsbDeviceDescriptor desc = device.getUsbDeviceDescriptor();
				System.out.println("Found on USB: idVendor: "+desc.idVendor()+", idProduct: "+desc.idProduct());
				if (desc.idVendor() == VENDOR_ID && desc.idProduct() == product_id) {
					System.out.println("Got our printer.");
					return device;
				}
			}
		}
		return null;
	}
	
	public void printHeader(){
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("Still sending to printer");
			return;
		}
		
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			pipe.open();
			int sent = pipe.syncSubmit(getByteArray(PRINT_NV_HEADER));
			System.out.println(sent + " bytes sent to printer: Print header from non-volatile memory command");
		} catch (UsbNotActiveException | UsbNotClaimedException
				| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		} finally {
			try {
				pipe.close();
			} catch (UsbNotActiveException | UsbNotOpenException
					| UsbDisconnectedException | UsbException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void cut(){
		if (SelfPi.DEBUG) {
			System.out.println("DEBUG: Printer: cut" );
			return;
		}
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("Still sending to printer");
			return;
		}
		
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			pipe.open();
			int sent = pipe.syncSubmit(getByteArray(CUT));
			System.out.println(sent + " bytes sent to printer: Cut paper command");
		} catch (UsbNotActiveException | UsbNotClaimedException
				| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		} finally {
			try {
				pipe.close();
			} catch (UsbNotActiveException | UsbNotOpenException
					| UsbDisconnectedException | UsbException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void print(String text){
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			pipe.open();
			int sent = pipe.syncSubmit(text.getBytes());
			System.out.println(sent + " bytes sent to printer: Print plain text");
		} catch (UsbNotActiveException | UsbNotClaimedException
				| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		} finally {
			try {
				pipe.close();
			} catch (UsbNotActiveException | UsbNotOpenException
					| UsbDisconnectedException | UsbException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void print(BufferedImage image){
		if (SelfPi.DEBUG) {
			System.out.println("DEBUG: Printer: print BufferedImage.");
			return;
		}
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			// convert to grayscale
			BufferedImage imageGrayscale = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
			Graphics2D g = imageGrayscale.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			
			// resize / rotate to fil the printer resolution
			int width = imageGrayscale.getWidth();
			int height = imageGrayscale.getHeight();
			int TARGET_WIDTH = EpsonESCPOSPrinter.width;
			
			if (width <= TARGET_WIDTH && height <= TARGET_WIDTH) { // image can fit in paper
				if (width < height) {	// we have to rotate to save paper
					BufferedImage imageRotated = new BufferedImage(height, width, BufferedImage.TYPE_BYTE_GRAY);
					g = imageRotated.createGraphics();
					g.rotate(Math.PI/2);
					g.drawImage(imageGrayscale, 0, -height, width, height, null);
					g.dispose();
					// update size
					width = imageRotated.getWidth();
					height = imageRotated.getHeight();
					// fit width dimension to %8
					BufferedImage imageResized = new BufferedImage((width/8)*8, height, BufferedImage.TYPE_BYTE_GRAY);
					g = imageResized.createGraphics();
					g.drawImage(imageRotated, 0, 0, width, height, null);
					g.dispose();
					image = imageResized;
				} else { // no need to rotate
					// fit width dimension to %8
					BufferedImage imageResized = new BufferedImage((width/8)*8, height, BufferedImage.TYPE_BYTE_GRAY);
					g = imageResized.createGraphics();
					g.drawImage(imageGrayscale, 0, 0, width, height, null);
					g.dispose();
					image = imageResized;
				}
			} else if (width <= TARGET_WIDTH && height > TARGET_WIDTH) { // image fit in paper, no need to resize nor rotate
				// fit width dimension to %8
				BufferedImage imageResized = new BufferedImage((width/8)*8, height, BufferedImage.TYPE_BYTE_GRAY);
				g = imageResized.createGraphics();
				g.drawImage(imageGrayscale, 0, 0, width, height, null);
				g.dispose();
				image = imageResized;
			} else if (width > TARGET_WIDTH && height <= TARGET_WIDTH) { // image fit in paper, no need to resize but rotate
				BufferedImage imageRotated = new BufferedImage(height, width, BufferedImage.TYPE_BYTE_GRAY);
				g = imageRotated.createGraphics();
				g.rotate(Math.PI/2);
				g.drawImage(imageGrayscale, 0, -height, width, height, null);
				g.dispose();
				// update size
				width = imageRotated.getWidth();
				height = imageRotated.getHeight();
				// fit width dimension to %8
				BufferedImage imageResized = new BufferedImage((width/8)*8, height, BufferedImage.TYPE_BYTE_GRAY);
				g = imageResized.createGraphics();
				g.drawImage(imageRotated, 0, 0, width, height, null);
				g.dispose();
				image = imageResized;
			} else { // image doesn't fit in paper
				if (width > height) { // we have to rotate to maximize printed size
					BufferedImage imageRotated = new BufferedImage(height, width, BufferedImage.TYPE_BYTE_GRAY);
					g = imageRotated.createGraphics();
					g.rotate(Math.PI/2);
					g.drawImage(imageGrayscale, 0, -height, width, height, null);
					g.dispose();
					imageGrayscale = imageRotated;
					width = imageGrayscale.getWidth();
					height = imageGrayscale.getHeight();
				}
				// resize
				height = (int) (height * (  (double)TARGET_WIDTH /width  ));
				width = TARGET_WIDTH;
				BufferedImage ImageResized = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
				g = ImageResized.createGraphics();
				g.drawImage(imageGrayscale, 0, 0, width, height, null);
				g.dispose();
				image = ImageResized;
			}
			
			// update size
			width = image.getWidth();
			height = image.getHeight();
			
			// extract bytes
			byte[] bytelist = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
			int[] pixList = new int[image.getWidth()*image.getHeight()];
			for (int i = 0; i < pixList.length; i++) {
				pixList[i] = Byte.toUnsignedInt( bytelist[i] );
			}
			
			// sent data to printer
			int sent;
			pipe.open();
			
//			sent = pipe.syncSubmit(getByteArray(SET_SPEED));
//			System.out.println(sent + " bytes sent to printer");
			
			sent = pipe.syncSubmit(getCommand_DL_TO_PRINTER_BUF(width, height));
			System.out.println(sent + " bytes sent to printer: Download data to printer buffer command");

			sent = pipe.syncSubmit(Dithering.getDitheredMonochrom(SelfPi.ditheringMethod,
					pixList, width, height, 
					SelfPi.normalyseHistogram, SelfPi.gamma));
			System.out.println(sent + " bytes sent to printer: Image Data bytes");

			sent = pipe.syncSubmit(getByteArray(PRINT_PRINTER_BUFFER));
			System.out.println(sent + " bytes sent to printer: Print the data from the printer buffer command");
			
			sent = pipe.syncSubmit( getByteArray(LINE_FEED) );
			System.out.println(sent + " bytes sent to printer: Feed 1 line command");

		} catch (UsbNotActiveException | UsbNotClaimedException
				| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		} finally {
			try {
				pipe.close();
			} catch (UsbNotActiveException | UsbNotOpenException
					| UsbDisconnectedException | UsbException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void print(File imageFile){
		if (SelfPi.DEBUG) {
			System.out.println("DEBUG: Printer: print file "+imageFile.getAbsolutePath());
			return;
		}
		try {
			// read the file
			BufferedImage image = ImageIO.read(imageFile);
			print(image);

		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
	
	private byte[] getCommand_DL_TO_PRINTER_BUF(int width, int height) {

		int dataLength = ((width/8)*height) +10;
		int p1 = 0xFF & dataLength;
		int p2 = dataLength >>>  8;
		int p3 = dataLength >>> 16;
		int p4 = dataLength >>> 24;
		int xL = 0xFF & width;
		int xH = width >>> 8;
		int yL = 0xFF & height;
		int yH = height >>> 8;
		
		/* Store the graphics data in the print buffer (raster format)
		 * Hex 1D 38 4C p1 p2 p3 p4 30 70 a bx by c xL xH yL yH d1...dk
		 */
		int[] DL_TO_PRINT_BUF = new int[]  {
				0x1D, 0x38, 0x4C, 			// download graphic data
				p1, p2, p3, p4,				// byte size after m (+10)
				0x30, 0x70, 0x30, 			// m, fn, a
				0x01, 0x01, 0x31,			// bx by c
				xL, xH, yL, yH				// xL, xH, yL, yH
			};	
		
		return getByteArray(DL_TO_PRINT_BUF);
	}
	  
	private static byte[] getByteArray(int[] data) {
		byte[] b = new byte[data.length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) data[i];
		}
		return b;
	}

//	private class PrintWithUsb implements Runnable {
//		private MonochromImage monoimg;
//		private TicketMode mode;
//		
//		public PrintWithUsb(MonochromImage monoimg, TicketMode mode) {
//			this.monoimg = monoimg;
//			this.mode = mode;
//		}
//		
//		@Override
//		public void run() {
//			if (SelfPi.usePrinterGraphicCommand) {
//				sendWithPipe(getByteArray(DL_TO_GRAPHIC));
//				sendWithPipe(monoimg.getDitheredBits(mode));
//				sendWithPipe(getByteArray(PRINT_GRAPHIC_BUF));
//			} else {
//				sendWithPipe(getByteArray(DL_TO_PRINT_BUF));
//				sendWithPipe(monoimg.getDitheredBits(mode));
//				sendWithPipe(getByteArray(PRINT_PRINT_BUF));
//			}
//			
//			if (SelfPi.printFunnyQuote) {
//				if (mode == TicketMode.HISTORIC) {
//					sendWithPipe(monoimg.getFilenumberInBytes());
//				} else {
//					sendWithPipe(monoimg.getSentence());
//				}
//			}
//			
//			if (mode == TicketMode.WINNER) {
//				sendWithPipe(getByteArray(PRINT_FOOT_WINNER));
//			} 
//			if (mode == TicketMode.SOUVENIR)  {
//				sendWithPipe(getByteArray(PRINT_FOOT_SOUVENIR));
//			}
//			if (mode == TicketMode.REPRINT)  {
//				sendWithPipe(getByteArray(PRINT_FOOT_SOUVENIR));
//			}
//			
//			if (mode != TicketMode.HISTORIC) {
//				sendWithPipe(getByteArray(CUT));
//				sendWithPipe(getByteArray(PRINT_HEADER));
//			}
//		}
//		
//		private void sendWithPipe(byte[] data) {
//			UsbPipe pipe = usbEndpoint.getUsbPipe();
//			try {
//				pipe.open();
//				int sent = pipe.syncSubmit(data);
//				System.out.println(sent + " bytes sent to printer");
//			} catch (UsbNotActiveException | UsbNotClaimedException
//					| UsbDisconnectedException | UsbException e) {
//				e.printStackTrace();
//			} finally {
//				try {
//					pipe.close();
//				} catch (UsbNotActiveException | UsbNotOpenException
//						| UsbDisconnectedException | UsbException e) {
//					e.printStackTrace();
//				}
//			}
//		}
//	}
	public static void main(String[] args) throws SecurityException, UsbException {
		File imgFile = new File(SelfPi.souvenirImageFilefolder+"Lenna.png");
		EpsonESCPOSPrinter p = new EpsonESCPOSPrinter((short) 3605);
		p.print(imgFile);
		p.cut();
		p.close();
	}
	
//	public static void main(String[] args) throws SecurityException, UsbException {
//		// TESTS :
//		// Search for epson TM-T20
//		UsbDevice device;
//		device = findUsb(UsbHostManager.getUsbServices().getRootUsbHub());
//		if (device == null) {
//			System.err.println("not found.");
//			System.exit(1);
//			return;
//		}
//		System.out.println("device: "+device);
//
//		// Claim the interface
//		UsbConfiguration configuration = device.getActiveUsbConfiguration();
//		UsbInterface usbInterface = configuration.getUsbInterface((byte) 0);
//		usbInterface.claim(new UsbInterfacePolicy() {            
//			@Override
//			public boolean forceClaim(UsbInterface usbInterface) {
//				return true;
//			}
//		});
//
//		UsbControlIrp irp = device.createUsbControlIrp(
//				(byte) (UsbConst.REQUESTTYPE_DIRECTION_IN
//						| UsbConst.REQUESTTYPE_TYPE_STANDARD
//						| UsbConst.REQUESTTYPE_RECIPIENT_DEVICE),
//						UsbConst.REQUEST_GET_CONFIGURATION,
//						(short) 0,
//						(short) 0
//				);
//		irp.setData(new byte[1]);
//		try {
//			device.syncSubmit(irp);
//		} catch (IllegalArgumentException | UsbDisconnectedException | UsbException e) {
//			e.printStackTrace();
//		}
//		System.out.println("current configuration number "+irp.getData()[0]);
//
//		System.out.println("getUsbEndpoints: "+usbInterface.getUsbEndpoints().size());
//
//		UsbEndpoint endpoint = usbInterface.getUsbEndpoint((byte) 1);
//		UsbPipe pipe = endpoint.getUsbPipe();
//		pipe.open();
//		try {
//			int sent = pipe.syncSubmit(testString.getBytes());
//			System.out.println(sent + " bytes sent");
//		} finally {
//			pipe.close();
//		}
//
//		usbInterface.release();
//	}
	

}

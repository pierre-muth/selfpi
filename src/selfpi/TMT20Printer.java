package selfpi;

import java.util.List;

import javax.usb.UsbConfiguration;
import javax.usb.UsbConst;
import javax.usb.UsbControlIrp;
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

public class TMT20Printer {

	private static final String testString = "test usb4java\n";
	// The vendor ID 
	private static final short VENDOR_ID = 0x04b8;	//1208 in decimal
	// The product ID 
	private static short product_id = 0x0e15;	//3605 in decimal, default is TM-T20II

	// default values
	private static int width = PiCamera.IMG_HEIGHT;  // We print 90 deg rotated
	private static int height = PiCamera.IMG_WIDTH;
	
	private static int dataLength = ((width/8)*height) +11;
	private static int p1 = 0xFF & dataLength;
	private static int p2 = dataLength >>>  8;
	private static int p3 = dataLength >>> 16;
	private static int p4 = dataLength >>> 24;
	private static int xL = 0xFF & width;
	private static int xH = width >>> 8;
	private static int yL = 0xFF & height;
	private static int yH = height >>> 8;

	/* download graphic data to graphic buffer (TM-T20/II)
	 * Hex 1D 38 4C p1 p2 p3 p4 30 53 a kc1 kc2 b xL xH yL yH [c d1...dk]1...[c d1...dk]b
	 * */
	private static int[] DL_TO_GRAPHIC = {
		0x1D, 0x38, 0x4C, 			// download graphic data
		p1, p2, p3, p4,				// byte size after m (+11)
		0x30, 0x53, 0x30, 			// m, fn, a
		0x20, 0x20, 0x01,			// key code, b
		xL, xH, yL, yH,				// xL, xH, yL, yH
		0x31						// c
	};											

	/* print graphic data from graphic buffer
	 * Hex 1D 28 4C 06 00 30 55 kc1 kc2 x y
	 */
	private static final int[] PRINT_GRAPHIC_BUF = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x55, 0x20, 0x20, 0x01, 0x01
	};
	
	/* Store the graphics data in the print buffer (raster format) (TM-T88IV)
	 * Hex 1D 38 4C p1 p2 p3 p4 30 70 a bx by c xL xH yL yH d1...dk
	 */
	private static int[] DL_TO_PRINT_BUF = {
			0x1D, 0x38, 0x4C, 			// download graphic data
			p1, p2, p3, p4,				// byte size after m (+11)
			0x30, 0x70, 0x30, 			// m, fn, a
			0x01, 0x01, 0x31,			// bx by c
			xL, xH, yL, yH,				// xL, xH, yL, yH
			0x31						// c
		};	
	
	/* Print the graphics data in the print buffer
	 * Hex 1D 28 4C 02 00 30 fn
	 */
	private static final int[] PRINT_PRINT_BUF = {
		0x1D, 0x28, 0x4C, 0x02, 0x00, 0x30, 0x32
	};
	
	// print NV graphics of key code 32 32   
	private static final int[] PRINT_HEADER = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 32, 1, 1
	};
	
	// print NV graphics of key code 32 33
	private static final int[] PRINT_FOOT_SOUVENIR = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 33, 1, 1
	};
	
	// print NV graphics of key code 32 34
		private static final int[] PRINT_FOOT_WINNER = {
			0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 34, 1, 1
		};
	
	// paper cut
	private static final int[] CUT = {
		0x1D, 'V', 'A', 0x10  
	};

	private UsbDevice device;
	private UsbEndpoint usbEndpoint;
	private UsbInterface usbInterface;
	private Thread usbPrinting;
	
	public TMT20Printer(short product_id) throws SecurityException, UsbException {
		TMT20Printer.product_id = product_id;
		
		// Search for epson TM-T20
		device = findUsb(UsbHostManager.getUsbServices().getRootUsbHub());
		if (device == null) {
			System.err.println("Epson TM-T20 not found :(");
			System.exit(1);
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
		
		width = SelfPi.IMG_HEIGHT;  // We print 90 deg rotated
		height = SelfPi.IMG_WIDTH;
		
		dataLength = ((width/8)*height) +11;
		p1 = 0xFF & dataLength;
		p2 = dataLength >>>  8;
		p3 = dataLength >>> 16;
		p4 = dataLength >>> 24;
		xL = 0xFF & width;
		xH = width >>> 8;
		yL = 0xFF & height;
		yH = height >>> 8;

		/* download graphic data to graphic buffer (TM-T20/II)
		 * Hex 1D 38 4C p1 p2 p3 p4 30 53 a kc1 kc2 b xL xH yL yH [c d1...dk]1...[c d1...dk]b
		 * */
		DL_TO_GRAPHIC = new int[] {
			0x1D, 0x38, 0x4C, 			// download graphic data
			p1, p2, p3, p4,				// byte size after m (+11)
			0x30, 0x53, 0x30, 			// m, fn, a
			0x20, 0x20, 0x01,			// key code, b
			xL, xH, yL, yH,				// xL, xH, yL, yH
			0x31						// c
		};											

		dataLength = ((width/8)*height) +10;
		p1 = 0xFF & dataLength;
		p2 = dataLength >>>  8;
		p3 = dataLength >>> 16;
		p4 = dataLength >>> 24;
		xL = 0xFF & width;
		xH = width >>> 8;
		yL = 0xFF & height;
		yH = height >>> 8;
		
		/* Store the graphics data in the print buffer (raster format) (TM-T88IV)
		 * Hex 1D 38 4C p1 p2 p3 p4 30 70 a bx by c xL xH yL yH d1...dk
		 */
		DL_TO_PRINT_BUF = new int[] {
				0x1D, 0x38, 0x4C, 			// download graphic data
				p1, p2, p3, p4,				// byte size after m (+10)
				0x30, 0x70, 0x30, 			// m, fn, a
				0x01, 0x01, 0x31,			// bx by c
				xL, xH, yL, yH				// xL, xH, yL, yH
			};	
		
	}

	public void close() {
		try {
			System.out.println("Releasing usb printer");
			usbInterface.release();
		} catch (UsbNotActiveException	| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
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
				System.out.println("USB idVendor: "+desc.idVendor()+", idProduct: "+desc.idProduct());
				if (desc.idVendor() == VENDOR_ID && desc.idProduct() == product_id) return device;
			}
		}
		return null;
	}
	
	public void printWithUsb(MonochromImage monoimg, TicketMode mode) {
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("Still sending data to printer");
			return;
		}
		TicketMode ticketMode = mode;
		usbPrinting = new Thread(new PrintWithUsb(monoimg, ticketMode));
		usbPrinting.start();
	}
	
	public void printHeader(){
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("Still sending to printer");
			return;
		}
		
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			pipe.open();
			int sent = pipe.syncSubmit(getByteArray(PRINT_HEADER));
			System.out.println(sent + " bytes sent to printer");
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
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("Still sending to printer");
			return;
		}
		
		UsbPipe pipe = usbEndpoint.getUsbPipe();
		try {
			pipe.open();
			int sent = pipe.syncSubmit(getByteArray(CUT));
			System.out.println(sent + " bytes sent to printer");
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
	  
	private static byte[] getByteArray(int[] data) {
		byte[] b = new byte[data.length];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) data[i];
		}
		return b;
	}

	private class PrintWithUsb implements Runnable {
		private MonochromImage monoimg;
		private TicketMode mode;
		
		public PrintWithUsb(MonochromImage monoimg, TicketMode mode) {
			this.monoimg = monoimg;
			this.mode = mode;
		}
		
		@Override
		public void run() {
			if (SelfPi.usePrinterGraphicCommand) {
				sendWithPipe(getByteArray(DL_TO_GRAPHIC));
				sendWithPipe(monoimg.getDitheredBits(mode));
				sendWithPipe(getByteArray(PRINT_GRAPHIC_BUF));
			} else {
				sendWithPipe(getByteArray(DL_TO_PRINT_BUF));
				sendWithPipe(monoimg.getDitheredBits(mode));
				sendWithPipe(getByteArray(PRINT_PRINT_BUF));
			}
			
			if (SelfPi.printFunnyQuote) {
				if (mode == TicketMode.HISTORIC) {
					sendWithPipe(monoimg.getFilenumberInBytes());
				} else {
					sendWithPipe(monoimg.getSentence());
				}
			}
			
			if (mode == TicketMode.WINNER) {
				sendWithPipe(getByteArray(PRINT_FOOT_WINNER));
			} 
			if (mode == TicketMode.SOUVENIR)  {
				sendWithPipe(getByteArray(PRINT_FOOT_SOUVENIR));
			}
			if (mode == TicketMode.REPRINT)  {
				sendWithPipe(getByteArray(PRINT_FOOT_SOUVENIR));
			}
			
			if (mode != TicketMode.HISTORIC) {
				sendWithPipe(getByteArray(CUT));
				sendWithPipe(getByteArray(PRINT_HEADER));
			}
		}
		
		private void sendWithPipe(byte[] data) {
			UsbPipe pipe = usbEndpoint.getUsbPipe();
			try {
				pipe.open();
				int sent = pipe.syncSubmit(data);
				System.out.println(sent + " bytes sent to printer");
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
	}
	
	
	public static void main(String[] args) throws SecurityException, UsbException {
		// TESTS :
		// Search for epson TM-T20
		UsbDevice device;
		device = findUsb(UsbHostManager.getUsbServices().getRootUsbHub());
		if (device == null) {
			System.err.println("not found.");
			System.exit(1);
			return;
		}
		System.out.println("device: "+device);

		// Claim the interface
		UsbConfiguration configuration = device.getActiveUsbConfiguration();
		UsbInterface usbInterface = configuration.getUsbInterface((byte) 0);
		usbInterface.claim(new UsbInterfacePolicy() {            
			@Override
			public boolean forceClaim(UsbInterface usbInterface) {
				return true;
			}
		});

		UsbControlIrp irp = device.createUsbControlIrp(
				(byte) (UsbConst.REQUESTTYPE_DIRECTION_IN
						| UsbConst.REQUESTTYPE_TYPE_STANDARD
						| UsbConst.REQUESTTYPE_RECIPIENT_DEVICE),
						UsbConst.REQUEST_GET_CONFIGURATION,
						(short) 0,
						(short) 0
				);
		irp.setData(new byte[1]);
		try {
			device.syncSubmit(irp);
		} catch (IllegalArgumentException | UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		}
		System.out.println("current configuration number "+irp.getData()[0]);

		System.out.println("getUsbEndpoints: "+usbInterface.getUsbEndpoints().size());

		UsbEndpoint endpoint = usbInterface.getUsbEndpoint((byte) 1);
		UsbPipe pipe = endpoint.getUsbPipe();
		pipe.open();
		try {
			int sent = pipe.syncSubmit(testString.getBytes());
			System.out.println(sent + " bytes sent");
		} finally {
			pipe.close();
		}

		usbInterface.release();
	}
	

}

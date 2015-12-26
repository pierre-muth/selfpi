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
import javax.usb.UsbIrp;
import javax.usb.UsbNotActiveException;
import javax.usb.UsbNotClaimedException;
import javax.usb.UsbNotOpenException;
import javax.usb.UsbPipe;

public class TMT20Printer {

	// The vendor ID 
	private static final short VENDOR_ID = 0x04b8;
	// The product ID 
	private static final short PRODUCT_ID = 0x0e03;

	private static final String testString = "test usb4java\n";
	
	private static final int width = PiCamera.IMG_HEIGHT;  // We print 90 deg rotated
	private static final int height = PiCamera.IMG_WIDTH;
	
	/* download graphic data raster
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

	// print NV graphics of key code 32 32   
	private static final int[] PRINT_HEADER = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 32, 1, 1
	};
	
	// print NV graphics of key code 32 33
	private static final int[] PRINT_FOOT = {
		0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 32, 33, 1, 1
	};
	
	// paper cut
	private static final int[] CUT = {
		0x1D, 'V', 'A', 0x10  
	};

	private UsbDevice device;
	private UsbEndpoint usbEndpoint;
	private UsbInterface usbInterface;
	private Thread usbPrinting;

	public TMT20Printer() throws SecurityException, UsbException {
		// Search for epson TM-T20

		device = findUsb(UsbHostManager.getUsbServices().getRootUsbHub());
		if (device == null) {
			System.err.println("not found.");
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
	}

	public void close() {
		try {
			System.out.println("release usb printer");
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
				System.out.println("idVendor: "+desc.idVendor()+", idProduct: "+desc.idProduct());
				if (desc.idVendor() == VENDOR_ID && desc.idProduct() == PRODUCT_ID) return device;
			}
		}
		return null;
	}
	
	public void printWithUsb(MonochromImage monoimg) {
		if (usbPrinting != null && usbPrinting.isAlive()) {
			System.out.println("still sending to printer");
			return;
		}
		usbPrinting = new Thread(new PrintWithUsb(monoimg));
		usbPrinting.start();
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
		
		public PrintWithUsb(MonochromImage monoimg) {
			this.monoimg = monoimg;
		}
		
		@Override
		public void run() {
			sendWithPipe(getByteArray(DL_GRAPH));
			sendWithPipe(monoimg.getDitheredBits());
			sendWithPipe(getByteArray(PRINT_DL));
//			sendWithPipe(getByteArray(PRINT_FOOT));
			sendWithPipe(getByteArray(CUT));
//			sendWithPipe(getByteArray(PRINT_HEADER));
		}
		
		private void sendWithPipe(byte[] data) {
			UsbPipe pipe = usbEndpoint.getUsbPipe();
			try {
				pipe.open();
				int sent = pipe.syncSubmit(data);
				System.out.println(sent + " bytes sent");
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

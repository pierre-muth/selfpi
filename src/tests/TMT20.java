package tests;

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

public class TMT20 {

	/** The vendor ID of the missile launcher. */
	private static final short VENDOR_ID = 0x04b8;
	/** The product ID of the missile launcher. */
	private static final short PRODUCT_ID = 0x0e03;

	private static final String testString = "test usb4java\n";

	private UsbDevice device;
	private UsbEndpoint usbEndpoint;
	private UsbInterface usbInterface;


	public TMT20() throws SecurityException, UsbException {
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

	}

	public void close() {
		try {
			usbInterface.release();
		} catch (UsbNotActiveException	| UsbDisconnectedException | UsbException e) {
			e.printStackTrace();
		}
	}

	public static UsbDevice findUsb(UsbHub hub) {
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

//	public void sendMessage(UsbDevice device, byte[] message) throws UsbException {
//		UsbControlIrp irp = device.createUsbControlIrp(
//				(byte) (UsbConst.REQUESTTYPE_TYPE_CLASS |
//						UsbConst.REQUESTTYPE_RECIPIENT_INTERFACE), (byte) 0x09,
//						(short) 2, (short) 1);
//		irp.setData(message);
//		device.syncSubmit(irp);
//	}

//	public void sendWithIRP(byte[] data) {
//		UsbPipe pipe = usbEndpoint.getUsbPipe();
//		try {
//			pipe.open();
//			UsbIrp irp = pipe.createUsbIrp();
//			irp.setData(data);
//			pipe.syncSubmit(irp);
//		} catch (UsbNotActiveException | UsbNotClaimedException
//				| UsbDisconnectedException | UsbException e) {
//			e.printStackTrace();
//		} finally {
//			try {
//				pipe.close();
//			} catch (UsbNotActiveException | UsbNotOpenException
//					| UsbDisconnectedException | UsbException e) {
//				e.printStackTrace();
//			}
//		}
//	}

	public void sendWithPipe(byte[] data) {
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

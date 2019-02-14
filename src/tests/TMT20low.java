package tests;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import javax.usb.UsbEndpoint;
import javax.usb.UsbException;
import javax.usb.UsbInterface;

import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceHandle;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

public class TMT20low {

	/** The vendor ID of the missile launcher. */
	private static final short VENDOR_ID = 0x04b8;
	/** The product ID of the missile launcher. */
	private static final short PRODUCT_ID = 0x0e03;

	private static final String testString = "test usb4java\n";

	private Device device;
	private UsbEndpoint usbEndpoint;
	private UsbInterface usbInterface;

	private Context context;
	private DeviceHandle handle;

	public TMT20low() throws SecurityException, UsbException {
		// Search for epson TM-T20

		context = new Context();
		int result = LibUsb.init(context);
		if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to initialize libusb.", result);

		device = findDevice(VENDOR_ID, PRODUCT_ID);
		if (device == null) {
			System.err.println("not found.");
			System.exit(1);
			return;
		}

		handle = new DeviceHandle();
		result = LibUsb.open(device, handle);
		if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to open USB device", result);
		
		// Check if kernel driver must be detached
		boolean detach = LibUsb.hasCapability(LibUsb.CAP_SUPPORTS_DETACH_KERNEL_DRIVER) && (LibUsb.kernelDriverActive(handle, 0) > 0);
		System.out.println("hasCapability: "+LibUsb.hasCapability(LibUsb.CAP_SUPPORTS_DETACH_KERNEL_DRIVER)+
				", kernelDriverActive: "+LibUsb.kernelDriverActive(handle, 0));

		// Detach the kernel driver
		if (detach) {
		    result = LibUsb.detachKernelDriver(handle,  0);
		    if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to detach kernel driver", result);
		}

		result = LibUsb.claimInterface(handle, 0);
		if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to claim interface", result);
	}

	public void close() {
		LibUsb.releaseInterface(handle, 0);
		LibUsb.attachKernelDriver(handle,  0);
		LibUsb.close(handle);
		LibUsb.exit(context);
	}

	public Device findDevice(short vendorId, short productId) {
		// Read the USB device list
		DeviceList list = new DeviceList();
		int result = LibUsb.getDeviceList(null, list);
		if (result < 0) throw new LibUsbException("Unable to get device list", result);

		try {
			// Iterate over all devices and scan for the right one
			for (Device device: list) {
				DeviceDescriptor descriptor = new DeviceDescriptor();
				result = LibUsb.getDeviceDescriptor(device, descriptor);
				if (result != LibUsb.SUCCESS) throw new LibUsbException("Unable to read device descriptor", result);
				if (descriptor.idVendor() == vendorId && descriptor.idProduct() == productId) return device;
			}
		} finally {
			// Ensure the allocated device list is freed
			LibUsb.freeDeviceList(list, true);
		}

		// Device not found
		return null;
	}

	public void sendWithPipe(byte[] data) {
		ByteBuffer buffer = ByteBuffer.allocateDirect(8);
		buffer.put(data);
		IntBuffer transfered = IntBuffer.allocate(1);
		int result = LibUsb.bulkTransfer(handle, (byte) 0x01, buffer, transfered, (long)10000 ); 
		if (result != LibUsb.SUCCESS) throw new LibUsbException("Control transfer failed", result);
		System.out.println(transfered.get() + " bytes sent");
	}

	public static void main(String[] args) throws SecurityException, UsbException {

	}

}

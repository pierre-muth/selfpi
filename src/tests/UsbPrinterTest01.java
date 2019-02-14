package tests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class UsbPrinterTest01 {
	
	private static final int[] TEST = {'T', 'e', 's', 't', };
	
	// paper cut
	private static final int[] CUT = { 0x1D, 'V', 'A', 0x10 };

	// print NV  
	// GS ( L   pL  pH   m  fn kc1/Kc2 x y
	// GS "(L"   6   0  48  69  "G1"   1 1
	private static final int[] PRINT_NV = { 0x1D, 0x28, 0x4C, 0x06, 0x00, 0x30, 0x45, 0x20, 0x20, 0x01, 0x01 };
	
	public static void main(String[] args) {
		try {
			OutputStream lp0 = new FileOutputStream(new File("/dev/usb/lp0"));
			
			for (int i = 0; i < TEST.length; i++) {
				lp0.write( TEST[i] );
			}		
			
			lp0.write(0x0A);
			lp0.write(0x0A);
			
			for (int i = 0; i < PRINT_NV.length; i++) {
				lp0.write( PRINT_NV[i] );
			}
			
			for (int i = 0; i < CUT.length; i++) {
				lp0.write( CUT[i] );
			}				
			
			lp0.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}
}


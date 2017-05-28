// java -cp ".:/home/pi/polapi-pro/lib/*" tests.GphotoTest01

package tests;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

public class GphotoTest01 {
	
	public static final String GPhoto2 = "gphoto2";
	public static final String listports_stdout = " --list-ports --stdout";
	public static final String capture_tethered_keep_raw_keep = " --capture-tethered --keep-raw --keep";
	public static final String save_entry = "Saving file as ";

	public static void main(String[] args) {
		Date d = new Date();
		long t = d.getTime();
		Process p;
		try {
			p = Runtime.getRuntime().exec(GPhoto2 + capture_tethered_keep_raw_keep);
			BufferedReader reader = new BufferedReader( new InputStreamReader(p.getInputStream()));

			String line = reader.readLine();
			while (line != null) {
				if (line.contains(save_entry)) {
					System.out.println(line.replace(save_entry, ""));
				}
				line = reader.readLine();
			}
			
			p.destroy();
			reader.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		System.out.println("time: "+ (new Date().getTime() - t));
	}

}

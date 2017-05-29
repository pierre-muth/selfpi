// java -cp ".:/home/pi/polapi-pro/lib/*" tests.GphotoTest01

package tests;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class GphotoTest02 {
	public static final String ROOTPATH = "/home/pi/selfpi2";
	public static final String dslrImageFilefolder = ROOTPATH+"/dslr/";

	public static final String GPhoto2 = "gphoto2 ";
	public static final String capture_tethered_keep_raw_keep = "--capture-tethered --keep-raw --keep ";
	public static final String auto_detect = "--auto-detect ";
	public static final String list_files = "--list-files ";
	public static final String get_file = "--get-file ";


	public static void main(String[] args) {

		String output = new String("");
		output += "DSLR import with gphoto2\n";
		System.out.println(output);
		
		File folder = new File(dslrImageFilefolder);
		File[] listOfExisitingFiles = folder.listFiles();

		try {
			Process p = Runtime.getRuntime().exec(GPhoto2 + auto_detect);
			BufferedReader reader = new BufferedReader( new InputStreamReader(p.getInputStream()));

			String line = reader.readLine();
			while (line != null) {
				output += line;
				output += "\n";
				line = reader.readLine();
			}
			p.destroy();
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println(output);
		
		HashMap<String, Integer> files = new HashMap<>();

		try {
			Process p = Runtime.getRuntime().exec(GPhoto2 + list_files);
			BufferedReader reader = new BufferedReader( new InputStreamReader(p.getInputStream()));

			String line = reader.readLine();
			while (line != null) {

				if (line.startsWith("#")) {
					String[] lineParts = line.split(" ");
					String fileName;
					for (int i = 1; i < lineParts.length; i++) {
						if (lineParts[i].contains("JPG")) {
							fileName = lineParts[i];
							output += lineParts[0]+" ";
							files.put( fileName, Integer.parseInt(lineParts[0].replaceFirst("#", "")) );
							output += fileName;
							output += "\n";
							break;
						}
					}
				}
				line = reader.readLine();
			}
			p.destroy();
			reader.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(output);
		
		for (int i = 0; i < listOfExisitingFiles.length; i++) {
			output += listOfExisitingFiles[i]+"\n";
		}
		
		System.out.println(output);

		ArrayList<Integer> numbersToRetreive = new ArrayList<>();  
		for (Iterator<String> file = files.keySet().iterator(); file.hasNext();) {
			boolean found = false;
			String key = file.next();
			for (int i = 0; i < listOfExisitingFiles.length; i++) {
				if (listOfExisitingFiles[i].getName().equalsIgnoreCase( key )) {
					found = true;
					break;
				}
			}
			if (!found) numbersToRetreive.add( files.get(key) );
		}

		System.out.println(output);
		
		String range = "";
		output += "will get ";
		for (Iterator<Integer> iterator = numbersToRetreive.iterator(); iterator.hasNext();) {
			int toGet = iterator.next();
			output += toGet + " ";
			range += toGet + ", ";
		}
		output += "\n";

		System.out.println(output);
		
		try {

			Process p = Runtime.getRuntime().exec(GPhoto2+get_file+range, null, new File(dslrImageFilefolder));
			BufferedReader reader = new BufferedReader( new InputStreamReader(p.getInputStream()));

			String line = reader.readLine();
			while (line != null) {
				output += line;
				output += "\n";
				line = reader.readLine();
			}
			p.destroy();
			reader.close();


		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(output);

	}

}

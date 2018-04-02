package selfpi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

public class UploadFTP {
	private static final String HOSTNAME = "xxx.xxx.xx";
	private static final String LOGIN = "xxx";
	private static final String PWD = "xxx";

	public static synchronized boolean store(File localFile) {
		FTPClient ftp = new FTPClient();
		try {
			ftp.connect(HOSTNAME);
			
			if (! FTPReply.isPositiveCompletion(  ftp.getReplyCode() )) {
				ftp.disconnect();
				return false;
			}

			ftp.login(LOGIN, PWD);
			ftp.setFileType(FTP.BINARY_FILE_TYPE);
			ftp.enterLocalPassiveMode();

			InputStream input = new FileInputStream(localFile);
			ftp.storeFile("selfpi/gallery-images/" + localFile.getName(), input);
			ftp.logout();
			ftp.disconnect();
			return true;
		} catch (Exception e) {
			return false;
		} finally {
			if(ftp.isConnected()) {
				try {
					ftp.disconnect();
				} catch(IOException ioe) {
					// do nothing
				}
			}
		}

	}
	


}
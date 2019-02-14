package selfpi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.restfb.BinaryAttachment;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookException;
import com.restfb.types.FacebookType;

public class FacebookEngine {
	private String pageAccessToken = "";
	private String appSECRET = "";
	private String albumID = "";
	private String photoMsg = "";
	private FacebookClient fbClient;

	private static final String FACETOKENKEY = "TOKEN:";
	private static final String FACEAPPSECRETKEY = "APPSECRET:";
	private static final String FACEALBUMIDKEY = "ALBUMID:";
	private static final String FACEPHOTOMSGIDKEY = "PHOTOMSG:";

	public FacebookEngine() {
		//read config
		String line;
		try (BufferedReader br = new BufferedReader( new FileReader(SelfPi.FACEBOOK_CONFIG_FILE_PATH) )){

			line = br.readLine();
			if (line != null && line.contains(FACETOKENKEY)) {
				pageAccessToken = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEAPPSECRETKEY)) {
				appSECRET = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEALBUMIDKEY)) {
				albumID = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEPHOTOMSGIDKEY)) {
				photoMsg = br.readLine();
			}

		} catch (IOException e) {
			System.out.println("Error in facebook.txt");
		};

		try {
			fbClient = new DefaultFacebookClient(pageAccessToken, appSECRET, Version.VERSION_3_1);
		} catch (FacebookException ex) {     //So that you can see what went wrong
			ex.printStackTrace(System.err);  //in case you did anything incorrectly
		}
	}

	public String publishApicture(File imageFile) throws IOException {
		String facebookUrl = "";
		Path filePath = Paths.get(imageFile.getPath());
		byte[] data = Files.readAllBytes(filePath);
		String message = photoMsg +", "+ imageFile.getName();
		FacebookType publishPhotoResponse = fbClient.publish(
				albumID+"/photos", 
				FacebookType.class,
				BinaryAttachment.with(imageFile.getName(), data),
				Parameter.with("message", message ) );

		System.out.println("Facebook published photo ID: " + publishPhotoResponse.getId());
		facebookUrl = "facebook.com/"+publishPhotoResponse.getId();
		return facebookUrl;
	}
}

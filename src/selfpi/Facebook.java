package selfpi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

import com.restfb.BinaryAttachment;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookException;
import com.restfb.types.FacebookType;
import com.restfb.types.Page;
import com.restfb.types.User;
import com.sun.xml.internal.bind.v2.schemagen.xmlschema.Appinfo;

public class Facebook {
	private String pageAccessToken = "";
	private String pageID = "";
	private String appID = "";
	private String userID = "";
	private String albumID = "";
	private FacebookClient fbClient;
	private User myuser = null;    //Store references to your user and page
	private Page mypage = null;    //for later use. In this answer's context, these

	private static final String FACETOKENKEY = "TOKEN:";
	private static final String FACEAPPIDKEY = "APPID:";
	private static final String FACEPROFILEIDKEY = "PROFILEID:";
	private static final String FACEUSERIDKEY = "USERID:";
	private static final String FACEALBUMIDKEY = "ALBUMID:";

	public Facebook() {
		//read config
		String line;
		try (BufferedReader br = new BufferedReader( new FileReader(SelfPi.FACEBOOKPATH) )){

			line = br.readLine();
			if (line != null && line.contains(FACETOKENKEY)) {
				pageAccessToken = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEAPPIDKEY)) {
				appID = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEPROFILEIDKEY)) {
				pageID = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEUSERIDKEY)) {
				userID = br.readLine();
			}
			line = br.readLine();
			if (line != null && line.contains(FACEALBUMIDKEY)) {
				albumID = br.readLine();
			}

		} catch (IOException e) {
			System.out.println("Error in config.txt");
		};

		try {
			fbClient = new DefaultFacebookClient(pageAccessToken, Version.LATEST);
			myuser = fbClient.fetchObject("me", User.class);
			mypage = fbClient.fetchObject(pageID, Page.class);

			System.out.println(myuser);
			System.out.println(mypage);

		} catch (FacebookException ex) {     //So that you can see what went wrong
			ex.printStackTrace(System.err);  //in case you did anything incorrectly
		}
	}

	public void publishApicture(MonochromImage monoImage) {
		Path filePath = Paths.get( monoImage.getLastImageFile().getAbsolutePath() );
		long time = new Date().getTime();
		byte[] data;
		try {
			data = Files.readAllBytes(filePath);

			FacebookType publishPhotoResponse = fbClient.publish(
					albumID+"/photos", 
					FacebookType.class,
					BinaryAttachment.with(""+time+".jpg", data),
					Parameter.with("message", "THEPort 2016 "+time) );

			System.out.println("Published photo ID: " + publishPhotoResponse.getId());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void publishApicture(File imageFile) throws IOException {
		Path filePath = Paths.get(imageFile.getPath());
		byte[] data = Files.readAllBytes(filePath);

		FacebookType publishPhotoResponse = fbClient.publish(
				"me/photos", 
				FacebookType.class,
				BinaryAttachment.with("test.jpg", data),
				Parameter.with("message", "Test "+new Date().getTime()) );

		System.out.println("Published photo ID: " + publishPhotoResponse.getId());

	}
}

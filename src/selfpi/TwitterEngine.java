package selfpi;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.UploadedMedia;

public class TwitterEngine {
	private Twitter twitter;
	private String photoMsg = "";

	private static final String MESSAGEKEY = "MESSAGE:";

	public TwitterEngine(){
		//read config
		String line;
		try (BufferedReader br = new BufferedReader( new FileReader(SelfPi.TWITTER_CONFIG_FILE_PATH) )){

			line = br.readLine();
			if (line != null && line.contains(MESSAGEKEY)) {
				photoMsg = br.readLine();
			}

		} catch (IOException e) {
			System.out.println("Error in twitter.txt");
		};

		twitter = new TwitterFactory().getInstance();

	}

	public String publishApicture(File imageFile) throws IOException {
		String twitUrl = "";
		String[] splited;

		if (twitter == null) return twitUrl;

		UploadedMedia media;
		try {
			media = twitter.uploadMedia(imageFile);
			StatusUpdate update = new StatusUpdate(photoMsg);
			update.setMediaIds(media.getMediaId());
			Status status = twitter.updateStatus(update);
			twitUrl = status.getText();
			splited = twitUrl.split("https://");
			twitUrl = splited[splited.length-1];
		} catch (TwitterException e) {
			e.printStackTrace();
		}
		
		return twitUrl;

	}



	public static void main(String[] args) throws TwitterException {
		long start = new Date().getTime();
		System.out.println("0: "+ (new Date().getTime()-start)/1000.0);

		Twitter twitter = new TwitterFactory().getInstance();
		UploadedMedia media = twitter.uploadMedia(new File("1549966942.gif"));
		StatusUpdate update = new StatusUpdate("test 02");
		update.setMediaIds(media.getMediaId());
		Status status = twitter.updateStatus(update);

		System.out.println("Successfully updated the status to [" + status.getText() + "][" + status.getId() + "].");
		System.out.println("1: "+ (new Date().getTime()-start)/1000.0);

	}

}

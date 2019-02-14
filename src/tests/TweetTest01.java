package tests;

import java.io.File;
import java.util.Date;

import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.UploadedMedia;

public class TweetTest01 {

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

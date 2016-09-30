package tests;

import java.io.IOException;
import java.nio.file.Files;
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

public class PagePubTest01 {
	
	
	private final String pageAccessToken = "EAAZAL2tdwy5cBAJMKu8Y4hZCklAzsdqClEUApb3Imm7h706iox0kku2jCVb4f4wKUOM1toJZCKbNaPjKDKmh3ZC3JmXjywcVZCwivIZCmfhpZCEtzUZA6GoeGk2R9nnCd8VKsAwxbLBiLhrF3ehYEbfDOVxcSSZAmCrXjt2XOgxgZCSAZDZD";
    private final String pageID = "1771561039781373";
    private FacebookClient fbClient;
    private User myuser = null;    //Store references to your user and page
    private Page mypage = null;    //for later use. In this answer's context, these
                                   //references are useless.
    public PagePubTest01() {
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

    public void makeTestPost() throws IOException {
    	byte[] data = Files.readAllBytes(Paths.get("C:\\Pierre\\selfpi\\doc\\beer.jpg"));
    	
    	FacebookType publishPhotoResponse = fbClient.publish(
    		"me/photos", 
    		FacebookType.class,
    		BinaryAttachment.with("beer.jpg", data),
    		Parameter.with("message", "Test "+new Date().getTime()) );

    	System.out.println("Published photo ID: " + publishPhotoResponse.getId());
    }
	
	public static void main(String[] args) {
		
		try {
			new PagePubTest01().makeTestPost();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
//		new PagePubTest01();
		
		
	}

}

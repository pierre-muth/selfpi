package tests;

public class StupidTests {
	public static int IMG_HEIGHT = 5;	
	public static int IMG_WIDTH = 10;	

	public static void main(String[] args) {
		int idx = 0;
		
		for (int i = 0; i < IMG_HEIGHT*IMG_WIDTH; i++) {
			
			idx = (IMG_WIDTH - (i%IMG_WIDTH)) + (IMG_WIDTH * (int)(i/IMG_WIDTH)) -1;
			
			System.out.println("i="+i+", idx="+idx);
			
		}

	}

}

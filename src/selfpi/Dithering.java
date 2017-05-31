package selfpi;

public class Dithering {
	public static final int FLOYD = 1;
	public static final int JARVIS = 2;
	public static final int STUCKI = 3;

	/*
 	The Jarvis, Judice, and Ninke filter (http://www.efg2.com/Lab/Library/ImageProcessing/DHALF.TXT)
	If the false Floyd-Steinberg filter fails because the error isn't
	distributed well enough, then it follows that a filter with a wider
	distribution would be better.  This is exactly what Jarvis, Judice, and
	Ninke [6] did in 1976 with their filter:
             *   7   5 
     3   5   7   5   3
     1   3   5   3   1   (1/48)
	 */
	private static coef[] matrixJarvis = new coef[] {
			 new coef( 1, 0, 7/48.0),
			 new coef( 2, 0, 5/48.0),
			 new coef(-2, 1, 3/48.0),
			 new coef(-1, 1, 5/48.0),
			 new coef( 0, 1, 7/48.0),
			 new coef( 1, 1, 5/48.0),
			 new coef( 2, 1, 3/48.0),
			 new coef(-2, 2, 1/48.0),
			 new coef(-1, 2, 3/48.0),
			 new coef( 0, 2, 5/48.0),
			 new coef( 1, 2, 3/48.0),
			 new coef( 2, 2, 1/48.0) 	
			
	};
	
	/*
	The Stucki filter (http://www.efg2.com/Lab/Library/ImageProcessing/DHALF.TXT)
	P. Stucki [7] offered a rework of the Jarvis, Judice, and Ninke filter in 1981: 
             *   8   4
     2   4   8   4   2
     1   2   4   2   1   (1/42)
	 */
	private static coef[] matrixStucki = new coef[] {
			 new coef( 1, 0, 8/42.0),
			 new coef( 2, 0, 4/42.0),
			 new coef(-2, 1, 2/42.0),
			 new coef(-1, 1, 4/42.0),
			 new coef( 0, 1, 8/42.0),
			 new coef( 1, 1, 4/42.0),
			 new coef( 2, 1, 2/42.0),
			 new coef(-2, 2, 1/42.0),
			 new coef(-1, 2, 2/42.0),
			 new coef( 0, 2, 4/42.0),
			 new coef( 1, 2, 2/42.0),
			 new coef( 2, 2, 1/42.0) 	
			
	};
	
	public static byte[] getDitheredMonochrom(int method, int[] pixList, int imgWidth, int imgHeight, boolean normalise, double gamma) {
		if (normalise) pixList = normaliseHistogram(pixList);
		else pixList = expandHistogram(pixList);
		pixList = gammaCorrection(pixList, gamma);
		
		switch (method) {
		case FLOYD:
			pixList = getFloydDitheredInts(pixList, imgWidth, imgHeight);
			return getDitheredBitsInBytes(pixList, imgWidth, imgHeight);
		case JARVIS:
			pixList = getJarvisDitheredInts(pixList, imgWidth, imgHeight);
			return getDitheredBitsInBytes(pixList, imgWidth, imgHeight);
		case STUCKI:
			pixList = getStuckiDitheredInts(pixList, imgWidth, imgHeight);
			return getDitheredBitsInBytes(pixList, imgWidth, imgHeight);

		default:
			pixList = getFloydDitheredInts(pixList, imgWidth, imgHeight);
			return getDitheredBitsInBytes(pixList, imgWidth, imgHeight);
		}
		
	}
	
	public static byte[] getDitheredBitsInBytes(int[] pixList, int imgWidth, int imgHeight) {
		
		//generate image with pixel bit in bytes
		byte[] pixBytes = new byte[(imgWidth/8) * imgHeight ];

		int mask = 0x01;
		int x, y;
		for (int i = 0; i < pixBytes.length; i++) {
			for (int j = 0; j < 8; j++) {
				mask = 0b10000000 >>> j;
				x = ( i%(imgWidth/8)*8 ) +j  ;
				y = i / (imgWidth/8);
				if ( pixList[x+(y*imgWidth)] == 0 ) {
					pixBytes[i] = (byte) (pixBytes[i] | mask);
				}
			}
		}

		return pixBytes;
	}
	
	public static int[] normaliseHistogram(int[] pixList) {
		int[] histogram = new int[256];
		
		// generate histogram origin
		for (int i = 0; i < pixList.length; i++) {
			histogram[pixList[i]]++;
		}
		
		// cumulative histogram
		for (int i = 1; i < 256; ++i) {
			histogram[i] += histogram[i - 1];
		}
		
		// histogram normalisation
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = 255 * histogram[pixList[i]] / pixList.length;
		}
		
		return pixList;
	}
	
	public static int[] expandHistogram(int[] pixList) {
		int min = 255, max = 0;
		double gain = 1;

		// search min-max
		for (int i = 0; i < pixList.length; i++) {
			if (pixList[i] > max) max = pixList[i];
			if (pixList[i] < min) min = pixList[i];
		}

		//limiting
		max = max<32 ? 32 : max;
		min = min>224 ? 224 : min;
		
		// calculate gain
		gain = 255.0/(max-min);		
		
		// normalise min-max to 0 - 255
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (int) ( (pixList[i] - min)*gain) ;
			if(pixList[i]>255) pixList[i] = 255;
			if(pixList[i]<0) pixList[i] = 0;
		}
		
		return pixList;
	}
	
	public static int[] gammaCorrection (int[] pixList, double gamma) {

		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (int) (Math.pow( (pixList[i]/255.0), 1/gamma) * 255.0);
		}
		
		return pixList;
	}
	
	public static int[] getFloydDitheredInts(int[] pixList, int imgWidth, int imgHeight) {
		int pixelWithError, pixelDithered, error;
		boolean notLeft, notRight, notBottom;
		int[] pixDithered = new int[pixList.length];
		
		//dithering
		for (int pixCount = 0; pixCount < pixList.length; pixCount++) {

			// are we on a corner ?
			notLeft = pixCount%imgWidth!=0;
			notBottom = pixCount < imgWidth*(imgHeight-1);
			notRight = (pixCount+1)%imgWidth!=0;

			//error was propagated in the existing  pixDithered[] array
			pixelWithError = pixDithered[pixCount] + pixList[pixCount];

			// black or white
			if (pixelWithError < 128) pixelDithered = 0;
			else pixelDithered = 255;

			// set the actual pixel
			pixDithered[pixCount] = pixelDithered;

			// get the error of the aproximation
			error = pixelWithError - pixelDithered;
			
			// propagate error
			if (notRight) pixDithered[pixCount+1] += 7*error/16;
			if (notLeft && notBottom) pixDithered[pixCount+(imgWidth-1)] += 3*error/16;
			if (notBottom) pixDithered[pixCount+(imgWidth)] += 5*error/16;
			if (notRight && notBottom) pixDithered[pixCount+(imgWidth+1)] += 1*error/16;
		}
		
		return pixDithered;
	}
	
	/**
	 *	borrowed from petrkutalek
	 *	https://github.com/petrkutalek/png2pos/blob/master/png2pos.c 
	 */
	public static int[] getJarvisDitheredInts(int[] pixList, int imgWidth, int imgHeight) {
		int[] pixDithered = new int[pixList.length];
		
		for (int i = 0; i < pixList.length; ++i) {
            int o = pixList[i];
            int n = o <= 0x80 ? 0 : 0xff;

            int x = i % imgWidth;
            int y = i / imgWidth;

            pixDithered[i] = n;
            
            for (int j = 0; j != 12; ++j) {
                int x0 = x + matrixJarvis[j].dx;
                int y0 = y + matrixJarvis[j].dy;
                if (x0 > imgWidth - 1 || x0 < 0 || y0 > imgHeight - 1 || y0 < 0) {
                    continue;
                }
                // the residual quantization error
                // warning! have to overcast to signed int before calculation!
                int d = (int) ((o - n) * matrixJarvis[j].coef);
                // keep a value in the <min; max> interval
                int a = pixList[x0 + imgWidth * y0] + d;
                if (a > 0xff) {
                    a = 0xff;
                }
                else if (a < 0) {
                    a = 0;
                }
                pixList[x0 + imgWidth * y0] = a;
            }
        }

		return pixDithered;
	}
	
	public static int[] getStuckiDitheredInts(int[] pixList, int imgWidth, int imgHeight) {
		int[] pixDithered = new int[pixList.length];
		
		for (int i = 0; i < pixList.length; ++i) {
            int o = pixList[i];
            int n = o <= 0x80 ? 0 : 0xff;

            int x = i % imgWidth;
            int y = i / imgWidth;

            pixDithered[i] = n;
            
            for (int j = 0; j != 12; ++j) {
                int x0 = x + matrixStucki[j].dx;
                int y0 = y + matrixStucki[j].dy;
                if (x0 > imgWidth - 1 || x0 < 0 || y0 > imgHeight - 1 || y0 < 0) {
                    continue;
                }
                // the residual quantization error
                // warning! have to overcast to signed int before calculation!
                int d = (int) ((o - n) * matrixStucki[j].coef);
                // keep a value in the <min; max> interval
                int a = pixList[x0 + imgWidth * y0] + d;
                if (a > 0xff) {
                    a = 0xff;
                }
                else if (a < 0) {
                    a = 0;
                }
                pixList[x0 + imgWidth * y0] = a;
            }
        }

		return pixDithered;
	}
	
	private static class coef {
		public int dx;
		public int dy;
		public double coef;

		public coef(int dx, int dy, double coef) {
			this.dx = dx;
			this.dy = dy;
			this.coef = coef;
		}
	}

}

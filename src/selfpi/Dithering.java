package selfpi;

public class Dithering {

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
	
	public static int[] getFloydDitheredInts(int[] pixList, int imgWidth, int imgHeight) {
		int pixelWithError, pixelDithered, error;
		boolean notLeft, notRight, notBottom;
		int[] pixDithered = new int[pixList.length];
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
		
		System.out.println("Gain: "+gain+", offset: "+min);
		
		// normalise min-max to 0 - 255
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (int) ( (pixList[i] - min)*gain) ;
			if(pixList[i]>255) pixList[i] = 255;
			if(pixList[i]<0) pixList[i] = 0;
		}
		
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
	
	public static byte[] getFloydDitheredBitsInBytes(int[] pixList, int imgWidth, int imgHeight) {
		
		int[] pixDithered = getFloydDitheredInts(pixList, imgWidth, imgHeight);
		
		//generate image with pixel bit in bytes
		byte[] pixBytes = new byte[(imgWidth/8) * imgHeight ];

		int mask = 0x01;
		int x, y;
		for (int i = 0; i < pixBytes.length; i++) {
			for (int j = 0; j < 8; j++) {
				mask = 0b10000000 >>> j;
				x = ( i%(imgWidth/8)*8 ) +j  ;
				y = i / (imgWidth/8);
				if ( pixDithered[x+(y*imgWidth)] == 0 ) {
					pixBytes[i] = (byte) (pixBytes[i] | mask);
				}
			}
		}

		return pixBytes;
	}
	
	/**
	 *	borrowed from petrkutalek
	 *	https://github.com/petrkutalek/png2pos/blob/master/png2pos.c 
	 */
	public static int[] getJarvisDitheredMonochom(int[] pixList, int imgWidth, int imgHeight) {
		int[] pixDithered = new int[pixList.length];
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

		System.out.println("Gain: "+gain+", offset: "+min);

		// normalise min-max to 0 - 255
		for (int i = 0; i < pixList.length; i++) {
			pixList[i] = (int) ( (pixList[i] - min)*gain) ;
			if(pixList[i]>255) pixList[i] = 255;
			if(pixList[i]<0) pixList[i] = 0;
		}
		
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
	
	public static byte[] getJarvisDitheredBitsInBytes(int[] pixList, int imgWidth, int imgHeight) {

		int[] pixDithered = getJarvisDitheredMonochom(pixList, imgWidth, imgHeight);

		//generate image with pixel bit in bytes
		byte[] pixBytes = new byte[(imgWidth/8) * imgHeight ];

		int mask = 0x01;
		int x, y;
		for (int i = 0; i < pixBytes.length; i++) {
			for (int j = 0; j < 8; j++) {
				mask = 0b10000000 >>> j;
				x = ( i%(imgWidth/8)*8 ) +j  ;
				y = i / (imgWidth/8);
				if ( pixDithered[x+(y*imgWidth)] == 0 ) {
					pixBytes[i] = (byte) (pixBytes[i] | mask);
				}
			}
		}

		return pixBytes;
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

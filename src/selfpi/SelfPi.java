package selfpi;

/**
 * to launch: sudo java -cp ".:/home/pi/selfpi/lib/*" selfpi.Launcher 
 */

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.usb.UsbException;

import org.apache.commons.lang3.text.WordUtils;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class SelfPi implements KeyListener {
	public static final String ROOTPATH = "/home/pi/selfpi";
	public static final String SETUP_PATH = ROOTPATH+"/setup/";
	public static final String CONFIG_FILE_PATH = SETUP_PATH+"config.txt";
	public static final String PICTURE_COUNTER_FILE_PATH = SETUP_PATH+"counter.txt";
	public static final String QUOTE_SENTENCES_FILE_PATH = SETUP_PATH+"phrase.txt";
	public static final String FACEBOOK_CONFIG_FILE_PATH = SETUP_PATH+"facebook.txt";
	public static final String souvenirImageFilePath = ROOTPATH+"/souvenir/";
	public static final String funnyImageFilefolder = ROOTPATH+"/funny/";

	public static boolean DEBUG = false; 
	public static SelfpiState selfpiState = SelfpiState.IDLE;

	private static PiCamera picam;
	private static TMT20Printer printer;
	private static ButtonLed redButtonLed;
	private static GpioPinDigitalOutput whiteButtonLed;
	private static WhiteButtonListener whiteButtonListener;
	private static RedButtonListener redButtonListener;
	
	private Thread pictureTakingThread;
	private Thread printingThread;
	private Thread waitForSharingThread;
	private Thread sharingThread;
	
	private Facebook facebook;
	
	private static final String WINNERKEY = "WINNER:";
	private static final String FUNNYQUOTEKEY = "FUNNYQUOTE:";
	private static final String FUNNYIMAGESKEY = "FUNNYIMAGES:";
	private static final String PRINTERPRODUCTIDKEY = "PRINTER_PRODUCT_ID:";
	private static final String PRINTERDOTSKEY = "PRINTERDOTS:";
	private static final String PRINTERSPEEDKEY = "PRINTERSPEED:";
	private static final String USE_FACEBOOK_KEY = "USE_FACEBOOK:";
	private static final String GUI_VERT_ORIENTATION_KEY = "GUI_VERTICAL_ORIENTATION:";
	private static final String SCREEN_HEIGHT_KEY = "SCREEN_HEIGHT:";
	private static final String COUNTDOWNLENGTHKEY = "COUNTDOWNLENGTH:";
	
	public static int winningTicketCounter = 0;

	// default values
	public static int frequencyTicketWin = 10;
	public static boolean printFunnyQuote = true;
	public static short printerProductID = 0x0e15;  //
	public static int printerdots = 576;
	public static int printerspeed = 1;
	public static boolean useFacebook = false;
	public static boolean useFunnyImages = false; 
	public static boolean guiVerticalOrientation = false;
	public static int screenHeight = 1024;
	public static File ticketHeader = new File(SETUP_PATH+"header.png");
	public static File ticketSouvenirFoot = new File(SETUP_PATH+"footsouvenir.png");
	public static File ticketWinnerFoot = new File(SETUP_PATH+"footwinner.png");
	public static int countdownLength = 9;
	
	private Gui gui;
	private File lastSouvenirPictureFile;
	private ArrayList<String> sentences;
	
	private Thread printHistoryThread;
	
	public SelfPi() {
		if (!DEBUG) {
			//read config file
			String line;
			try (BufferedReader br = new BufferedReader( new FileReader(CONFIG_FILE_PATH) )){
				
				line = br.readLine();
				if (line != null && line.contains(WINNERKEY)) {
					frequencyTicketWin = Integer.parseInt( br.readLine() );
				}
				line = br.readLine();
				if (line != null && line.contains(FUNNYQUOTEKEY)) {
					printFunnyQuote = br.readLine().contains("true");
				}
				line = br.readLine();
				if (line != null && line.contains(FUNNYIMAGESKEY)) {
					useFunnyImages = br.readLine().contains("true");
				}
				line = br.readLine();
				if (line != null && line.contains(PRINTERPRODUCTIDKEY)) {
					printerProductID = Short.parseShort( br.readLine() );
				}
				line = br.readLine();
				if (line != null && line.contains(PRINTERDOTSKEY)) {
					printerdots = Integer.parseInt( br.readLine() );
				}
				line = br.readLine();
				if (line != null && line.contains(PRINTERSPEEDKEY)) {
					printerspeed = Integer.parseInt( br.readLine() );
				}
				line = br.readLine();
				if (line != null && line.contains(USE_FACEBOOK_KEY)) {
					useFacebook = br.readLine().contains("true");
				}
				line = br.readLine();
				if (line != null && line.contains(GUI_VERT_ORIENTATION_KEY)) {
					guiVerticalOrientation = br.readLine().contains("true");
				}
				line = br.readLine();
				if (line != null && line.contains(SCREEN_HEIGHT_KEY)) {
					screenHeight = Integer.parseInt( br.readLine() );
				}
				line = br.readLine();
				if (line != null && line.contains(COUNTDOWNLENGTHKEY)) {
					countdownLength = Integer.parseInt( br.readLine() );
					countdownLength = countdownLength < 4 ? 4 : countdownLength;
				}

			} catch (IOException e) {
				System.out.println("Error in config.txt, trying with default values");
			};

			// Build quotes list
			sentences = new ArrayList<>();
			try (BufferedReader br = new BufferedReader( new FileReader(QUOTE_SENTENCES_FILE_PATH) )){
				do {
					line = br.readLine();
					if (line != null) sentences.add(line);
				} while (line != null);
			} catch (IOException e) {
				System.out.println("Error in phrase.txt");
			};

			
			// build GUI
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					gui = new Gui(guiVerticalOrientation);
				}
			});
			
			//read global counter
			try (BufferedReader br = new BufferedReader( new FileReader(PICTURE_COUNTER_FILE_PATH))){
				winningTicketCounter = Integer.parseInt( br.readLine() );
			} catch (IOException e) {
				System.out.println("Error in counter.txt");
			};

			// start Pinter
			try {
				printer = new TMT20Printer(printerProductID);
			} catch (SecurityException | UsbException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			// Instanciate Facebook
			if (useFacebook) {
				try {
					facebook = new Facebook();
				} catch (Exception e) {
					System.out.println("Error in Facebook setup");
				}
			}

			// Listening Button
			GpioController gpio;
			GpioPinDigitalInput redButton;
			GpioPinDigitalInput whiteButton;
			gpio = GpioFactory.getInstance();
			
			redButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_UP);
			redButtonListener = new RedButtonListener();
			redButton.addListener(redButtonListener);
			
			whiteButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_05, PinPullResistance.PULL_UP);
			whiteButtonListener = new WhiteButtonListener();
			whiteButton.addListener(whiteButtonListener);
			
			whiteButtonLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_06);
			whiteButtonLed.high();
			
			GpioPinPwmOutput buttonLedPin = gpio.provisionPwmOutputPin(RaspiPin.GPIO_01);
			redButtonLed = new ButtonLed(buttonLedPin);
			redButtonLed.startSoftBlink();

			// Start Pi camera
			picam = new PiCamera(printerdots, printerdots);
			new Thread(picam).start();

		}
	}
	
	// keyboard listener
	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ENTER){
			stateMachineTransition(SelfPiEvent.HISTORY_BUTTON);
		}
		if (e.getKeyCode() == KeyEvent.VK_P && selfpiState == SelfpiState.HISTORIC){
			int fileIndex = gui.getHistoryFileIndex();
			File historyDirectory = gui.getHistoryDirectory();
			
			if (printHistoryThread != null && printHistoryThread.isAlive()) return;
				
			printHistoryThread = new Thread(new RunPrintHistory(historyDirectory, fileIndex));
			printHistoryThread.start();
		}
		if (e.getKeyCode() == KeyEvent.VK_SPACE || 
				e.getKeyCode() == KeyEvent.VK_NUMPAD0 ||
				e.getKeyCode() == KeyEvent.VK_0){
			redButtonListener.doPress();
		}
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
		
	}
	
	public Gui getGui() {
		return gui;
	}

	protected void stateMachineTransition(SelfPiEvent event){
		
		switch (selfpiState) {
		case IDLE:
			switch (event) {
			case RED_BUTTON:
				selfpiState = SelfpiState.TAKING_PICT;
				takeApicture();
				break;
			case HISTORY_BUTTON:
				selfpiState = SelfpiState.HISTORIC;
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				break;
				
			default:
				break;
			}
			break;
			
		case TAKING_PICT:
			switch (event) {
			case PRINT:
				selfpiState = SelfpiState.PRINTING;
				print();
				break;
			case RED_BUTTON:
				break;
			case WHITE_BUTTON:
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				break;
			default:
				break;
			}
			break;
			
		case PRINTING:
			switch (event) {
			case END_PRINTING:
				selfpiState = SelfpiState.WAIT_FOR_SHARE;
				waitForShare();
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				break;
			default:
				break;
			}
			break;
			
		case WAIT_FOR_SHARE:
			switch (event) {
			case RED_BUTTON:
				selfpiState = SelfpiState.RE_PRINTING;
				rePrint();
				break;
			case WHITE_BUTTON:
				selfpiState = SelfpiState.SHARING;
				share();
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				resetMode();
				break;
			default:
				break;
			}
			break;
			
		case SHARING:
			switch (event) {
			case RESET:
				selfpiState = SelfpiState.IDLE;
				resetMode();
				break;
			case RED_BUTTON:
				selfpiState = SelfpiState.RE_PRINTING;
				rePrint();
				break;
			default:
				break;
			}
			break;
			
		case RE_PRINTING:
			switch (event) {
			case RESET:
				selfpiState = SelfpiState.IDLE;
				resetMode();
				break;
			case WHITE_BUTTON:
				selfpiState = SelfpiState.SHARING;
				share();
				break;
			default:
				break;
			}
			break;
			
		case HISTORIC:
			switch (event) {
			case HISTORY_BUTTON:
				selfpiState = SelfpiState.IDLE;
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				break;
			default:
				break;
			}
			break;

		default:
			break;
		}
		
		// actualize gui
		gui.setMode(selfpiState);
		
	}
	
	private void takeApicture(){
		// Take a picture
		pictureTakingThread = new Thread(new RunTakeAPicture());
		pictureTakingThread.start(); 
	}
	
	private void print() {
		// print
		printingThread = new Thread(new RunPrinting());
		printingThread.start(); 
	}
	
	private void waitForShare(){
		// wait
		waitForSharingThread = new Thread(new RunWaitForSharing());
		waitForSharingThread.start(); 
	}
	
	private void share(){
		SelfPi.whiteButtonLed.high();
		sharingThread = new Thread(new RunShare());
		sharingThread.start();
		SelfPi.whiteButtonLed.high();
	}
	
	private void rePrint(){
		printer.print(lastSouvenirPictureFile);
		if (printFunnyQuote) {
			printer.print(getRandomQuote());
		}
		printer.print(ticketSouvenirFoot);
		printer.cut();
		printer.print(ticketHeader);
		SelfPi.redButtonLed.startBlinking();
	}
	
	private void resetMode(){
		SelfPi.redButtonLed.startSoftBlink();
		
	}
	
	public File chooseFunnyImage(){
		File folder = new File(funnyImageFilefolder);
		File[] listOfFiles = folder.listFiles();
		int random = (int) (Math.random()*listOfFiles.length);
		return listOfFiles[random];
	}
	
	public File chooseRandomImage(){
		File folder = new File(SelfPi.souvenirImageFilePath);
		File[] listOfFiles = folder.listFiles();
		int random = (int) (Math.random()*listOfFiles.length);
		return listOfFiles[random];
	}
	
	public String getRandomQuote(){
		int rand = (int) (Math.random()*sentences.size());
		String sentence = WordUtils.wrap(sentences.get(rand), 48) +"\n";
		return sentence;
	}
	
	public static void main(String[] args) {
		final JFrame frame = new JFrame("SelfPi");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		final SelfPi selfpi = new SelfPi();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				frame.add(selfpi.getGui());
				frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
				frame.setUndecorated(true);
				frame.setVisible(true);
			}
		});

		frame.addKeyListener(selfpi);
		frame.addKeyListener(selfpi.getGui());

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Closing...");
				printer.close();
				picam.close();
			}
		});
	}

	class RedButtonListener implements GpioPinListenerDigital {
		
		private long lastPressedTime;

		public RedButtonListener() {
			lastPressedTime = System.currentTimeMillis();
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isHigh()) return;
			handle();
		}
		
		public void doPress() {
			handle();
		}
		
		private void handle() {
			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastPressedTime < 500 ) return; // reject if less than 500 ms
			lastPressedTime = currentTime;
			
			System.out.println("Red Button pressed !");
			
			stateMachineTransition(SelfPiEvent.RED_BUTTON);
		}
	} 
	
	class WhiteButtonListener implements GpioPinListenerDigital {
		private long lastTime;

		public WhiteButtonListener() {
			lastTime = System.currentTimeMillis();
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isHigh()) return;
			handle();
		}
		
		public void doPress() {
			handle();
		}
		
		private void handle() {
			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastTime < 500 ) return; // reject if less than 500 ms
			lastTime = currentTime;

			System.out.println("White Button pressed !");
			
			stateMachineTransition(SelfPiEvent.WHITE_BUTTON);
		}
	}
	
	private class RunShare implements Runnable{

		@Override
		public void run() {
			if (facebook != null) {
				try {
					facebook.publishApicture(lastSouvenirPictureFile);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private class RunPrintHistory implements Runnable{
		private int historyFileIndex;
		private File directory;
		public RunPrintHistory(File directory, int fileIndex) {
			this.historyFileIndex = fileIndex;
			this.directory = directory;
		}

		@Override
		public void run() {

			SelfPi.redButtonLed.startBlinking();
			
			File folder = directory;
			File[] listOfFiles = folder.listFiles();
			Arrays.sort(listOfFiles);
			
			for (int i = historyFileIndex; i < listOfFiles.length && i < historyFileIndex+6; i++) {
				System.out.println("printing: "+listOfFiles[i].getName());
				try { Thread.sleep(500); } catch (InterruptedException e) {}
				printer.print(listOfFiles[i]);
				// printing
				try { Thread.sleep(4000); } catch (InterruptedException e) {}
				System.out.println("printed.");
			}
			
			printer.cut();
			printer.print(ticketHeader);
			SelfPi.redButtonLed.startSoftBlink();
		}
		
	}
	
	private class RunWaitForSharing implements Runnable {
		@Override
		public void run() {
			
			// wait for printing
			try { Thread.sleep(10000); } catch (InterruptedException e) {}
			
			// end
			SelfPi.whiteButtonLed.high();
			SelfPi.redButtonLed.startSoftBlink();
			
			SelfPi.this.stateMachineTransition(SelfPiEvent.RESET);
		}
	}
	
	private class RunPrinting implements Runnable {
		
		@Override
		public void run() {
			
			if (winningTicketCounter%frequencyTicketWin == 0){
				if ( (winningTicketCounter/frequencyTicketWin) %2 == 0 && SelfPi.useFunnyImages) {
					printer.print(chooseFunnyImage());
				} else {
					printer.print(chooseRandomImage());
				}
			} else {
				printer.print(lastSouvenirPictureFile);
			}

			if (printFunnyQuote) {
				printer.print(getRandomQuote());
			}
			
			if (winningTicketCounter%frequencyTicketWin == 0){
				printer.print(ticketWinnerFoot);
			} else {
				printer.print(ticketSouvenirFoot);
			}
			
			printer.cut();
			printer.print(ticketHeader);
			
			System.out.println("count: "+winningTicketCounter+", freq:"+frequencyTicketWin);
			// inc print counter
			winningTicketCounter++;
			Path file = Paths.get(PICTURE_COUNTER_FILE_PATH);
			String line = Integer.toString(winningTicketCounter);
			List<String> lines = Arrays.asList(line);
			try {
				Files.write(file, lines);
			} catch (IOException e) {
				e.printStackTrace();
			}				

			// wait for printing
//			try { Thread.sleep(2000); } catch (InterruptedException e) {}
			
			// set button state
			SelfPi.whiteButtonLed.low();
			SelfPi.redButtonLed.startSoftBlink();
			
			SelfPi.this.stateMachineTransition(SelfPiEvent.END_PRINTING);
		}
		
	}
	
	private class RunTakeAPicture implements Runnable{

		@Override
		public void run() {

			SelfPi.redButtonLed.startBlinking();
			try { Thread.sleep((countdownLength-1)*1000); } catch (InterruptedException e) {}
			
			// take a picture
			String numberFileName = Integer.toString( (int) (new Date().getTime() /1000) );
			lastSouvenirPictureFile = new File(souvenirImageFilePath+numberFileName+".jpg");
			picam.takeApictureToFile(lastSouvenirPictureFile);
			
			try { Thread.sleep(100); } catch (InterruptedException e) {}
			SelfPi.this.stateMachineTransition(SelfPiEvent.PRINT);
		}
	}

	private class ButtonLed {
		private GpioPinPwmOutput ledPin;
		private Timer timer;
		private int pwmValue = -50; 

		public ButtonLed(GpioPinPwmOutput ledPin) {
			this.ledPin = ledPin;
		}

		public void startSoftBlink() {
			reset();
			TimerTask toggler = new TimerTask() {
				@Override
				public void run() {
					if (pwmValue<50) pwmValue++;
					else pwmValue = -50;
					ledPin.setPwm( Math.abs(pwmValue)*5 );
				}
			};
			timer = new Timer();
			timer.schedule(toggler, 1, 25);
		}

		public void startBlinking() {
			reset();
			TimerTask toggler = new TimerTask() {
				@Override
				public void run() {
					if (pwmValue >= 50) pwmValue = 0;
					else pwmValue = 1000;
					ledPin.setPwm(pwmValue);
				}
			};

			timer = new Timer();
			timer.schedule(toggler, 1, 300);
		}

		public void reset() {
			if (timer != null) {
				timer.cancel();
				timer = null;
			}
		}
	}

}

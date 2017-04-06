package selfpi;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
/**
 * to launch: sudo java -cp ".:/home/pi/selfpi/lib/*" selfpi.Launcher 
 */
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.usb.UsbException;

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
	public static final String souvenirFilePath = "/home/pi/selfpi/souvenir/";
	public static final String funnyImageFilefolder = "/home/pi/selfpi/funny/";

	public static boolean DEBUG = false; 
	public static SelfpiState selfpiState = SelfpiState.IDLE;
	public static TicketMode ticketMode = TicketMode.SOUVENIR;

	private static PiCamera picam;
	private static MonochromImage monoimg = new MonochromImage();
	private static TMT20Printer printer;
	private static ButtonLed redButtonLed;
	private static GpioPinDigitalOutput whiteButtonLed;
	
	private Thread pictureTakingThread;
	private Thread printingThread;
	private Thread waitForSharingThread;
	private Thread sharingThread;
	private Thread rePrintingThread;
	
	private Facebook facebook;
	
	public static final String FILECONFPATH = "/home/pi/selfpi/setup/config.txt";
	public static final String COUNTERPATH = "/home/pi/selfpi/setup/counter.txt";
	public static final String SENTENCESPATH = "/home/pi/selfpi/setup/phrase.txt";
	public static final String FACEBOOKPATH = "/home/pi/selfpi/setup/facebook.txt";
	public static final String SHARESCREENATH = "/home/pi/selfpi/setup/share_screen.png";
	
	private static final String WINNERKEY = "WINNER:";
	private static final String FUNNYQUOTEKEY = "FUNNYQUOTE:";
	private static final String PRINTERPRODUCTIDKEY = "PRINTER_PRODUCT_ID:";
	private static final String PRINTERDOTSKEY = "PRINTERDOTS:";
	
	private static int winningTicketCounter = 0;
	private static int frequencyTicketWin = 10;
	private static short printerProductID = 0x0e15;  //
	private static int printerdots = 576; 
	
	public static boolean printFunnyQuote = true;
	
	private Gui gui;
	
	private Thread printHistoryThread;
	
	public SelfPi(Gui gui) {
		this.gui = gui;

		if (!DEBUG) {
			
			//read config
			String line;
			try (BufferedReader br = new BufferedReader( new FileReader(FILECONFPATH) )){
				
				line = br.readLine();
				if (line != null && line.contains(WINNERKEY)) {
					frequencyTicketWin = Integer.parseInt( br.readLine() );
				}
				
				line = br.readLine();
				if (line != null && line.contains(FUNNYQUOTEKEY)) {
					printFunnyQuote = br.readLine().contains("true");
				}
				
				line = br.readLine();
				if (line != null && line.contains(PRINTERPRODUCTIDKEY)) {
					printerProductID = Short.parseShort( br.readLine() );
				}
				
				line = br.readLine();
				if (line != null && line.contains(PRINTERDOTSKEY)) {
					printerdots = Integer.parseInt( br.readLine() );
				}
				
			} catch (IOException e) {
				System.out.println("Error in config.txt");
			};
			
			//read global counter
			try (BufferedReader br = new BufferedReader( new FileReader(COUNTERPATH))){
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
			try {
				facebook = new Facebook();
			} catch (Exception e) {
				System.out.println("Error in Facebook setup");
			}

			// Listening Button
			GpioController gpio;
			GpioPinDigitalInput redButton;
			GpioPinDigitalInput whiteButton;
			gpio = GpioFactory.getInstance();
			redButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_UP);
			redButton.addListener(new RedButtonListener());
			whiteButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_05, PinPullResistance.PULL_UP);
			whiteButton.addListener(new WhiteButtonListener());
			
			whiteButtonLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_06);
			whiteButtonLed.high();
			
			GpioPinPwmOutput buttonLedPin = gpio.provisionPwmOutputPin(RaspiPin.GPIO_01);
			redButtonLed = new ButtonLed(buttonLedPin);
			redButtonLed.startSoftBlink();

			// Start Pi camera
			picam = new PiCamera(printerdots);
			new Thread(picam).start();

		}
	}
	
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
	}

	@Override
	public void keyReleased(KeyEvent arg0) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
		
	}

	protected void stateMachineTransition(SelfPiEvent event){
		
		switch (selfpiState) {
		case IDLE:
			switch (event) {
			case RED_BUTTON:
				selfpiState = SelfpiState.TAKING_PICT;
				ticketMode = TicketMode.SOUVENIR;
				takeApicture();
				break;
			case HISTORY_BUTTON:
				selfpiState = SelfpiState.HISTORIC;
				ticketMode = TicketMode.HISTORIC;
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				ticketMode = TicketMode.SOUVENIR;
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
				ticketMode = TicketMode.SOUVENIR;
				break;
			default:
				break;
			}
			break;
			
		case WAIT_FOR_SHARE:
			switch (event) {
			case RED_BUTTON:
				selfpiState = SelfpiState.RE_PRINTING;
				ticketMode = TicketMode.REPRINT;
				rePrint();
				break;
			case WHITE_BUTTON:
				selfpiState = SelfpiState.SHARING;
				share();
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				ticketMode = TicketMode.SOUVENIR;
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
				ticketMode = TicketMode.SOUVENIR;
				resetMode();
				break;
			case RED_BUTTON:
				selfpiState = SelfpiState.RE_PRINTING;
				ticketMode = TicketMode.REPRINT;
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
				ticketMode = TicketMode.SOUVENIR;
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
				ticketMode = TicketMode.SOUVENIR;
				break;
			case RESET:
				selfpiState = SelfpiState.IDLE;
				ticketMode = TicketMode.SOUVENIR;
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
		printingThread = new Thread(new RunPrinting(ticketMode));
		printingThread.start(); 
	}
	
	private void waitForShare(){
		// wait
		waitForSharingThread = new Thread(new RunWaitForSharing());
		waitForSharingThread.start(); 
	}
	
	private void share(){
		if (!monoimg.shared){
			monoimg.shared = true;
			SelfPi.whiteButtonLed.high();
			sharingThread = new Thread(new RunShare());
			sharingThread.start();
		}
	}
	
	private void rePrint(){
		if (!monoimg.reprinted){
			printer.printWithUsb(monoimg, TicketMode.REPRINT);
			monoimg.reprinted = true;
			SelfPi.redButtonLed.startBlinking();
		}
	}
	
	private void resetMode(){
		monoimg.printed = false;
		monoimg.shared = false;
		monoimg.reprinted = false;
		ticketMode = TicketMode.SOUVENIR;
		SelfPi.redButtonLed.startSoftBlink();
	}
	
	public static void main(String[] args) {
		final JFrame frame = new JFrame("SelfPi");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		final Gui gui = new Gui();

		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				frame.add(gui);
				frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
				frame.setUndecorated(true);
				frame.setVisible(true);
			}
		});

		SelfPi selfpi = new SelfPi(gui);
		frame.addKeyListener(selfpi);
		frame.addKeyListener(gui);

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
			System.out.println("Red Button pressed !");

			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastPressedTime < 500 ) return; // reject if less than 500 ms
			lastPressedTime = currentTime;

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
				facebook.publishApicture(monoimg);
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
				monoimg.setFile(listOfFiles[i]);
				System.out.println("printing: "+listOfFiles[i].getName());
				try { Thread.sleep(500); } catch (InterruptedException e) {}
				printer.printWithUsb(monoimg, TicketMode.HISTORIC);
				// printing
				try { Thread.sleep(4000); } catch (InterruptedException e) {}
				System.out.println("printed.");
			}
			
			printer.cut();
			printer.printHeader();
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
		TicketMode mode;
		public RunPrinting(TicketMode mode) {
			this.mode = mode;
		}
		
		@Override
		public void run() {
			printer.printWithUsb(monoimg, mode);
			monoimg.writeToFile();
			monoimg.setCount(winningTicketCounter);
			monoimg.printed = true;

			// inc print counter
			winningTicketCounter++;
			Path file = Paths.get(COUNTERPATH);
			String line = Integer.toString(winningTicketCounter);
			List<String> lines = Arrays.asList(line);
			try {
				Files.write(file, lines);
			} catch (IOException e) {
				e.printStackTrace();
			}				

			// wait for printing
			try { Thread.sleep(4000); } catch (InterruptedException e) {}
			
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
			
			if (winningTicketCounter%frequencyTicketWin == 0){
				if ( (winningTicketCounter/frequencyTicketWin) %2 == 0) {
					SelfPi.ticketMode = TicketMode.WINNER;
					monoimg.chooseRandomImage();
				} else {
					SelfPi.ticketMode = TicketMode.FUNNY;
					monoimg.chooseFunnyImage();
				}
			}
			
			System.out.println("Ticket Mode: "+ SelfPi.ticketMode +", count: "+winningTicketCounter+", freq:"+frequencyTicketWin);
			
			try { Thread.sleep(4000); } catch (InterruptedException e) {}
			
			monoimg.setPixels(picam.getAFrame());
			
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
			
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

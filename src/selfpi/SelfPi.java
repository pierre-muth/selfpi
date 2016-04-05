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
	public static final String beerFilefolder = "/home/pi/selfpi/beer/";

	public static boolean DEBUG = false; 
	public static PrinterState printerState = PrinterState.IDLE;
	public static TicketMode ticketMode = TicketMode.BEER; 

	private static PiCamera picam;
	private static MonochromImage monoimg = new MonochromImage();
	private static TMT20Printer printer;
	private static ButtonLed printButtonLed;
	private static GpioPinDigitalOutput beerButtonLed;
	
	public static final String FILECONFPATH = "/home/pi/selfpi/setup/config.txt";
	public static final String COUNTERPATH = "/home/pi/selfpi/setup/counter.txt";
	public static final String SENTENCESPATH = "/home/pi/selfpi/setup/phrase.txt";
	
	private static final String GAGNANTKEY = "GAGNANT:";
	
	private static int beerTicketCounter = 0;
	private static int beerTicketWin = 100;
	
	private Gui gui;
	
	private Thread printHistoryThread;
	
	public SelfPi(Gui gui) {
		this.gui = gui;

		if (!DEBUG) {
			
			//read config
			String line;
			try (BufferedReader br = new BufferedReader( new FileReader(FILECONFPATH) )){
				
				line = br.readLine();
				if (line != null && line.contains(GAGNANTKEY)) {
					beerTicketWin = Integer.parseInt( br.readLine() );
				}
				
				
			} catch (IOException e) {
				System.out.println("Error in config.txt");
			};
			
			//read global counter
			try (BufferedReader br = new BufferedReader( new FileReader(COUNTERPATH))){
				beerTicketCounter = Integer.parseInt( br.readLine() );
			} catch (IOException e) {
				System.out.println("Error in config.txt");
			};

			// start Pinter
			try {
				printer = new TMT20Printer();
			} catch (SecurityException | UsbException e) {
				e.printStackTrace();
				System.exit(1);
			}

			// Listening Button
			GpioController gpio;
			GpioPinDigitalInput printButton;
			GpioPinDigitalInput beerButton;
			gpio = GpioFactory.getInstance();
			printButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_UP);
			printButton.addListener(new RedButtonListener());
			beerButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_05, PinPullResistance.PULL_UP);
			beerButton.addListener(new BeerButtonListener());
			
			beerButtonLed = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_06);
			
			GpioPinPwmOutput buttonLedPin = gpio.provisionPwmOutputPin(RaspiPin.GPIO_01);
			printButtonLed = new ButtonLed(buttonLedPin);
			printButtonLed.startSoftBlink();

			// Start Pi camera
			picam = new PiCamera();
			new Thread(picam).start();

		}
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ENTER){
			ticketStateMachineTransition(SelfPiEvent.HISTORY_BUTTON);
		}
		if (e.getKeyCode() == KeyEvent.VK_P){
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

	protected void printerStateMachineTransition(SelfPiEvent event) {

		switch (printerState) {
		case IDLE:

			switch (event) {
			case RED_BUTTON:
				printerState = PrinterState.PRINTING;
				break;
			case END_PRINTING:
				break;
			
			default:
				break;
			}
			break;

		case PRINTING:
			switch (event) {
			case RED_BUTTON:
				break;
			case END_PRINTING:
				printerState = PrinterState.IDLE;
				break;
			
			default:
				break;
			}
			break;

		
		default:
			break;
		}
	}
	
	protected void ticketStateMachineTransition(SelfPiEvent event){
		
		switch (ticketMode) {
		case SOUVENIR:
			switch (event) {
			case BEER_BUTTON:
				ticketMode = TicketMode.BEER;
				break;
			case HISTORY_BUTTON:
				ticketMode = TicketMode.HISTORIC;
				break;

			default:
				break;
			}
			break;
			
		case BEER:
			switch (event) {
			case RED_BUTTON:
				ticketMode = TicketMode.BEER;
				break;
			case BEER_BUTTON:
				ticketMode = TicketMode.BEER;
				break;
			case HISTORY_BUTTON:
				ticketMode = TicketMode.HISTORIC;
				break;

			default:
				break;
			}
			break;
			
		case HISTORIC:
			switch (event) {
			case HISTORY_BUTTON:
				ticketMode = TicketMode.BEER;
				break;
			default:
				break;
			}
			break;

		default:
			break;
		}
		
		// actualize gui
		gui.setMode(ticketMode);
		
		// actualize beer button
		if (ticketMode == TicketMode.BEER) {
			beerButtonLed.low();
		} else {
			beerButtonLed.high();
		}
		
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
				System.out.println("Closing");
				printer.close();
				picam.close();
			}
		});
	}

	class RedButtonListener implements GpioPinListenerDigital {
		private Thread printTicketThread;
		private long lastPrintTime;

		public RedButtonListener() {
			lastPrintTime = System.currentTimeMillis();
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isHigh()) return;

			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastPrintTime < 500 ) return; // reject if less than 500 ms
			
			lastPrintTime = currentTime;

			if (printTicketThread != null && printTicketThread.isAlive()) return;
			if (SelfPi.printerState == PrinterState.PRINTING) return;

			System.out.println("Red Button pressed !");
			
			printerStateMachineTransition(SelfPiEvent.RED_BUTTON);

			// launch printing
			printTicketThread = new Thread(new RunPrintTicket(SelfPi.ticketMode));
			printTicketThread.start(); 
			
			ticketStateMachineTransition(SelfPiEvent.RED_BUTTON);
		}
	} 
	
	class BeerButtonListener implements GpioPinListenerDigital {
		private long lastTime;

		public BeerButtonListener() {
			lastTime = System.currentTimeMillis();
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isHigh()) return;

			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastTime < 500 ) return; // reject if less than 500 ms
			lastTime = currentTime;

			System.out.println("Beer Button pressed !");
			
			ticketStateMachineTransition(SelfPiEvent.BEER_BUTTON);

			
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

			SelfPi.printButtonLed.startBlinking();
			
			File folder = directory;
			File[] listOfFiles = folder.listFiles();
			Arrays.sort(listOfFiles);
			
			for (int i = historyFileIndex; i < listOfFiles.length && i < historyFileIndex+6; i++) {
				monoimg.setFile(listOfFiles[i]);
				System.out.println("print: "+listOfFiles[i].getName());
				try { Thread.sleep(500); } catch (InterruptedException e) {}
				printer.printWithUsb(monoimg, TicketMode.HISTORIC);
				// printing
				try { Thread.sleep(4000); } catch (InterruptedException e) {}
				System.out.println("printed.");
			}
			
			printer.cut();
			printer.printHeader();
			SelfPi.printButtonLed.startSoftBlink();
		}
		
	}

	private class RunPrintTicket implements Runnable{
		private TicketMode mode;
		public RunPrintTicket(TicketMode mode) {
			this.mode = mode;
		}

		@Override
		public void run() {

			SelfPi.printButtonLed.startBlinking();
//			SelfPi.this.gui.setPrinting();
			SelfPi.this.gui.countDown();
			
			if (mode == TicketMode.BEER){
				if (beerTicketCounter%beerTicketWin == 0){
					mode = TicketMode.WINNER;
				}
			}
			
			try { Thread.sleep(6000); } catch (InterruptedException e) {}
			
			if (mode == TicketMode.WINNER){
				monoimg.chooseRandomImage();
			} else {
				monoimg.setPixels(picam.getAFrame());
			}
			
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
			
			printer.printWithUsb(monoimg, mode);
			monoimg.writeToFile(mode);
			
			// printing
			try { Thread.sleep(4000); } catch (InterruptedException e) {}
			
			if (mode == TicketMode.BEER || mode == TicketMode.WINNER){
				// inc print counter
				beerTicketCounter++;
				Path file = Paths.get(COUNTERPATH);
				String line = Integer.toString(beerTicketCounter);
				List<String> lines = Arrays.asList(line);
				try {
					Files.write(file, lines);
				} catch (IOException e) {
					e.printStackTrace();
				}				
			}
			
			SelfPi.printButtonLed.startSoftBlink();
			SelfPi.this.gui.setMode(SelfPi.ticketMode);
			
			SelfPi.this.printerStateMachineTransition(SelfPiEvent.END_PRINTING);

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

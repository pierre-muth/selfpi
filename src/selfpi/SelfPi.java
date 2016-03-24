package selfpi;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

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
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class SelfPi implements KeyListener{

	public static boolean DEBUG = true; 
	public static PrinterState printerState = PrinterState.IDLE;
	public static TicketMode ticketMode = TicketMode.SOUVENIR; 

	private static PiCamera picam;
	private static MonochromImage monoimg = new MonochromImage();
	private static TMT20Printer printer;
	private static ButtonLed buttonLed;

	private Gui gui;

	public SelfPi(Gui gui) {
		this.gui = gui;

		if (!DEBUG) {

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
			
			GpioPinPwmOutput buttonLedPin = gpio.provisionPwmOutputPin(RaspiPin.GPIO_01);
			buttonLed = new ButtonLed(buttonLedPin);
			buttonLed.startSoftBlink();

			// Start Pi camera
			picam = new PiCamera();
			new Thread(picam).start();

		}
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyCode() == KeyEvent.VK_ENTER){
			ticketStateMachineTransition(SelfPiEvent.FLUSH_BUTTON);
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
			case FLUSH_BUTTON:
				ticketMode = TicketMode.HISTORIC;
				break;

			default:
				break;
			}
			break;
			
		case BEER:
			switch (event) {
			case RED_BUTTON:
				ticketMode = TicketMode.SOUVENIR;
				break;
			case BEER_BUTTON:
				ticketMode = TicketMode.SOUVENIR;
				break;
			case FLUSH_BUTTON:
				ticketMode = TicketMode.HISTORIC;
				break;

			default:
				break;
			}
			break;
			
		case HISTORIC:
			switch (event) {
			case FLUSH_BUTTON:
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
		gui.setMode(ticketMode);
		
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

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Closing");
				printer.close();
				picam.close();
			}
		});
	}

	class RedButtonListener implements GpioPinListenerDigital {
		private Thread printThread;
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

			if (printThread != null && printThread.isAlive()) return;
			if (SelfPi.printerState == PrinterState.PRINTING) return;

			System.out.println("Print Button pressed !");
			
			printerStateMachineTransition(SelfPiEvent.RED_BUTTON);

			// and count down
			printThread = new Thread(new RunPrint(SelfPi.ticketMode));
			printThread.start();
			
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

	private class RunPrint implements Runnable{
		private TicketMode mode;
		public RunPrint(TicketMode mode) {
			this.mode = mode;
		}

		@Override
		public void run() {

			SelfPi.this.gui.setText("Merci !");
			SelfPi.buttonLed.startBlinking();
			
			try { Thread.sleep(1000); } catch (InterruptedException e) {}
			
			monoimg.setPixels(picam.getAFrame());
			
			try { Thread.sleep(100); } catch (InterruptedException e) {}
			
			printer.printWithUsb(monoimg);
			monoimg.writeToFile();
			
			// printing
			try { Thread.sleep(4000); } catch (InterruptedException e) {}
			
			SelfPi.buttonLed.startSoftBlink();
			SelfPi.this.gui.setText("Appuyez sur le boutton !");
			
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

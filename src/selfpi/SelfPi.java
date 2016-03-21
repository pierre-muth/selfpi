package selfpi;

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

public class SelfPi {
	protected enum State {WAIT_SOUVENIR, WAIT_BEER, HISTORIC, PRINTING_TICKET, PRINTING_HISTORIC};
	protected enum Event {RED_BUTTON, BEER_BUTTON, END_PRINTING, FLUSH_BUTTON};

	public static boolean DEBUG = false; 
	public static State state = State.WAIT_SOUVENIR; 

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
			gpio = GpioFactory.getInstance();
			printButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_UP);
			printButton.addListener(new ButtonListener());

			GpioPinPwmOutput buttonLedPin = gpio.provisionPwmOutputPin(RaspiPin.GPIO_01);
			buttonLed = new ButtonLed(buttonLedPin);
			buttonLed.startSoftBlink();

			// Start Pi camera
			picam = new PiCamera();
			new Thread(picam).start();

		}
	}

	protected void stateMachineTransition(Event event) {

		switch (state) {
		case WAIT_SOUVENIR:
			
			switch (event) {
			case RED_BUTTON:
				state = State.PRINTING_TICKET;
				break;
			case BEER_BUTTON:
				state = State.WAIT_BEER;
				break;
			case END_PRINTING:
				break;
			case FLUSH_BUTTON:
				state = State.HISTORIC;
				break;
			default:
				break;
			}
			break;

		case WAIT_BEER:
			switch (event) {
			case RED_BUTTON:
				state = State.PRINTING_TICKET;
				break;
			case BEER_BUTTON:
				state = State.WAIT_SOUVENIR;
				break;
			case END_PRINTING:
				break;
			case FLUSH_BUTTON:
				state = State.HISTORIC;
				break;
			default:
				break;
			}
			break;

		case PRINTING_TICKET:
			switch (event) {
			case RED_BUTTON:
				break;
			case BEER_BUTTON:
				break;
			case END_PRINTING:
				state = State.WAIT_SOUVENIR;
				break;
			case FLUSH_BUTTON:
				break;
			default:
				break;
			}
			break;
		case PRINTING_HISTORIC:
			switch (event) {
			case RED_BUTTON:
				break;
			case BEER_BUTTON:
				break;
			case END_PRINTING:
				state = State.HISTORIC;
				break;
			case FLUSH_BUTTON:
				state = State.WAIT_SOUVENIR;
				break;
			default:
				break;
			}
			break;
		default:
			break;
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

		new SelfPi(gui);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Closing");
				printer.close();
				picam.close();
			}
		});
	}

	class ButtonListener implements GpioPinListenerDigital {
		private Thread countAndPrintThread;
		private long lastPrintTime;

		public ButtonListener() {
			lastPrintTime = System.currentTimeMillis();
		}

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isHigh()) return;

			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastPrintTime < 500 ) return; // reject if less than 500 ms
			lastPrintTime = currentTime;

			if (countAndPrintThread != null && countAndPrintThread.isAlive()) return;

			System.out.println("Button pressed !");
			stateMachineTransition(Event.RED_BUTTON);

			// and count down
			countAndPrintThread = new Thread(new CountDown());
			countAndPrintThread.start();
		}
	} 

	private class CountDown implements Runnable{

		@Override
		public void run() {
			// Count Down
			if (DEBUG) {
				monoimg.setPixels(picam.getAFrame());
				try { 
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				//				Launcher.this.gui.setImage(monoimg.getDitheredMonochrom());
			} else {

				SelfPi.this.gui.startCounter();
				SelfPi.buttonLed.startBlinking();
				try {
					Thread.sleep(4500);
				} catch (InterruptedException e) {}
				monoimg.setPixels(picam.getAFrame());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
				printer.printWithUsb(monoimg);
				monoimg.writeToFile();
				try {
					Thread.sleep(4000);
				} catch (InterruptedException e) {}
				SelfPi.buttonLed.startSoftBlink();

			}

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

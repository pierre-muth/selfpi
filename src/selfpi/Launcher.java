package selfpi;

/**
 * to launch: sudo java -cp ".:/home/pi/selfpi/lib/*" selfpi.Launcher 
 */

import java.util.Timer;
import java.util.TimerTask;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.usb.UsbException;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class Launcher extends Application {
	public static boolean DEBUG = false; 
	private static PiCamera picam;
	private static MonochromImage monoimg = new MonochromImage();
	private static TMT20Printer printer;
	private static ButtonLed buttonLed;
	private GuiFX gui;

	public Launcher() {
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
		printButton = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04, PinPullResistance.PULL_DOWN);
		printButton.addListener(new ButtonListener());
		
		GpioPinPwmOutput buttonLedPin = gpio.provisionPwmOutputPin(RaspiPin.GPIO_01);
		buttonLed = new ButtonLed(buttonLedPin);
		buttonLed.startSoftBlink();
		
		// Start Pi camera
		picam = new PiCamera();
		new Thread(picam).start();
	}

	public static void main(String[] args) {
		launch();
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				System.out.println("Closing");
				printer.close();
				picam.close();
			}
		});
	}
 
	@Override
	public void start(Stage primaryStage) throws Exception {
		primaryStage.setTitle("Selfie Maker");
		gui = new GuiFX();
		Scene scene = new Scene(gui, 480, 800);
        primaryStage.setScene(scene);
        primaryStage.show();		
	}

	class ButtonListener implements GpioPinListenerDigital {
		private Thread countAndPrint;
		private long lastPrintTime;
		
		public ButtonListener() {
			lastPrintTime = System.currentTimeMillis();
		}
		
		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isLow()) return;
			
			long currentTime = System.currentTimeMillis();
			if ( currentTime - lastPrintTime < 500 ) return; // reject if less than 500 ms
			lastPrintTime = currentTime;
			
			if (countAndPrint != null && countAndPrint.isAlive()) return;

			System.out.println("Button pressed !");

			// and count down
			countAndPrint = new Thread(new CountDown());
			countAndPrint.start();
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
				Launcher.this.gui.setImage(monoimg.getDitheredMonochrom());
			} else {

					Launcher.this.gui.startCounter();
					Launcher.buttonLed.startBlinking();
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
					Launcher.buttonLed.startSoftBlink();
					
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
					ledPin.setPwm(Math.abs(pwmValue)*20);
				}
			};
			timer = new Timer();
			timer.schedule(toggler, 1, 20);
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

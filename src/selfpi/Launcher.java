package selfpi;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import javax.usb.UsbException;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class Launcher extends Application {
	public static boolean DEBUG = false; 
	private static PiCamera picam;
	private static MonochromImage monoimg = new MonochromImage();
	private static TMT20Printer printer;
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
		primaryStage.setTitle("Hackathon selfie Maker");
		gui = new GuiFX();
		Scene scene = new Scene(gui, 480, 800);
        primaryStage.setScene(scene);
        primaryStage.show();		
	}

	class ButtonListener implements GpioPinListenerDigital {
		private Thread countAndPrint;

		@Override
		public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
			if (event.getState().isLow()) return;
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
					try {
						Thread.sleep(4500);
					} catch (InterruptedException e) {}
					monoimg.setPixels(picam.getAFrame());
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {}
					printer.printWithUsb(monoimg);
					monoimg.writeToFile();
					
			}

		}

	}

}

package selfpi;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

public class GuiFX extends VBox{
	public static int COUNT_DOWN = 90;
	
	public GuiFX() {
		setAlignment(Pos.BOTTOM_CENTER);
		if(Launcher.DEBUG) {
			getChildren().add(getImageView());
		} else {
			getChildren().add(getText());
		}
	}
	     
	public void startCounter() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (int i = COUNT_DOWN; i >= 0; i--) {
					setCount(i);
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}	
				}
				reset();
			}
		}).start();
	}
	
	public void setCount(final int count) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				double d = count/10.0;
				getText().setFont(new Font(300.0));
				getText().setText(Double.toString(d));
			}
		});
	}
	
	public void reset() {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				getText().setFont(new Font(100.0));
				getText().setText("Press the\nButton");
			}
		});
	}
	
	public void setImage(final int[] pixList) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				for (int i = 0; i < pixList.length; i++) {
					Color c = new Color(pixList[i]/256.0, pixList[i]/256.0, pixList[i]/256.0, 1);
					getPixelWriter().setColor(i%PiCamera.IMG_WIDTH, i/PiCamera.IMG_HEIGHT, c);
				}
				
			}
		});
	}
	
	private ProgressIndicator progress;
	private ProgressIndicator getProgressBar() {
		if (progress == null) {
			progress = new ProgressIndicator(0.3);
			progress.setId("progress");
			progress.setPrefSize(320, 320);
			progress.setStyle(" -fx-progress-color: red;");
			
		}
		return progress;
	}

	private Text text;
	private Text getText() {
		if (text == null) {
			text = new Text("Press the\nButton");
			text.setFont(new Font(100.0));
			text.setVisible(true);
			text.setTextAlignment(TextAlignment.CENTER);
		}
		return text;
	}
	private ImageView imageView;
	private ImageView getImageView() {
		if (imageView == null) {
			imageView = new ImageView();
			imageView.setImage(getWritableImage());
		}
		return imageView;
	}
	private PixelWriter pixelWriter;
	private PixelWriter getPixelWriter() {
		if (pixelWriter == null) {
			pixelWriter = getWritableImage().getPixelWriter();
		}
		return pixelWriter;
	}
	private WritableImage wImage;
	private WritableImage getWritableImage() {
		if (wImage == null) {
			wImage = new WritableImage(PiCamera.IMG_WIDTH, PiCamera.IMG_HEIGHT);
		}
		return wImage;
	}
}

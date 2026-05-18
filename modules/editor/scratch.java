import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.collections.ListChangeListener;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.util.Duration;

public class scratch extends Application {
    @Override
    public void start(Stage primaryStage) {
        Window.getWindows().addListener((ListChangeListener<Window>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    for (Window window : c.getAddedSubList()) {
                        System.out.println("Window added: " + window.getClass().getName());
                        if (window.getClass().getName().contains("Tooltip")) {
                            window.setOpacity(0);
                            Timeline timeline = new Timeline(
                                new KeyFrame(Duration.ZERO, new KeyValue(window.opacityProperty(), 0)),
                                new KeyFrame(Duration.millis(300), new KeyValue(window.opacityProperty(), 1))
                            );
                            timeline.play();
                        }
                    }
                }
            }
        });

        Button btn = new Button("Hover me");
        btn.setTooltip(new Tooltip("This is a tooltip"));

        StackPane root = new StackPane();
        root.getChildren().add(btn);

        Scene scene = new Scene(root, 300, 250);

        primaryStage.setTitle("Tooltip Test");
        primaryStage.setScene(scene);
        primaryStage.show();
        
        System.exit(0);
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}

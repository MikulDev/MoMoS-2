package com.momosoftworks.momos.widget.bar;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class TextClock extends VBox
{
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, MMM d");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private final String monitor;

    public TextClock(String monitor)
    {
        this.monitor = monitor;

        this.setSpacing(3);
        this.setAlignment(Pos.CENTER);
        this.getStyleClass().add("clock");

        // Date
        Label dateLabel = new Label(LocalDate.now().format(DATE_FORMAT));
        dateLabel.getStyleClass().add("clock-date");

        Timeline timeline = new Timeline(new KeyFrame(Duration.minutes(1), e -> dateLabel.setText(LocalDate.now().format(DATE_FORMAT))));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();

        // Time
        Label timeLabel = new Label(LocalTime.now().format(TIME_FORMAT));
        timeLabel.getStyleClass().add("clock-time");

        Timeline timeTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> timeLabel.setText(LocalTime.now().format(TIME_FORMAT))));
        timeTimeline.setCycleCount(Animation.INDEFINITE);
        timeTimeline.play();

        this.getChildren().addAll(dateLabel, timeLabel);
    }
}

package com.momosoftworks.momos.util.ui;

import javafx.animation.AnimationTimer;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class SmoothScroller
{
    private static final double SNAP_DISTANCE = 0.25;

    private final DoubleConsumer apply;
    private final DoubleSupplier reader;
    private DoubleSupplier min = () -> Double.NEGATIVE_INFINITY;
    private DoubleSupplier max = () -> Double.POSITIVE_INFINITY;
    private double timeConstant = 0.058;
    private double speed = 1;

    private double position;
    private double target;
    private long lastFrame;
    private boolean running;

    private final AnimationTimer timer = new AnimationTimer()
    {
        @Override
        public void handle(long now)
        {   step(now);
        }
    };

    /** A scroller that owns the scroll position itself. */
    public SmoothScroller(DoubleConsumer apply)
    {   this(null, apply);
    }

    /**
     * A scroller for an area whose position can also change from elsewhere (e.g. a scrollbar drag).
     * {@code reader} is used to pick up the current position when a scroll starts, and to yield to
     * any outside change mid-animation.
     */
    public SmoothScroller(DoubleSupplier reader, DoubleConsumer apply)
    {   this.reader = reader;
        this.apply = apply;
    }

    /** Smoothly scrolls the vertical axis of a {@link ScrollPane}, replacing its default wheel handling. */
    public static SmoothScroller of(ScrollPane pane)
    {
        DoubleSupplier range = () ->
        {   Node content = pane.getContent();
            if (content == null) return 0;
            return Math.max(0, content.getLayoutBounds().getHeight() - pane.getViewportBounds().getHeight());
        };
        DoubleSupplier reader = () ->
        {   double span = pane.getVmax() - pane.getVmin();
            return span == 0 ? 0 : (pane.getVvalue() - pane.getVmin()) / span * range.getAsDouble();
        };
        DoubleConsumer apply = pos ->
        {   double pixels = range.getAsDouble();
            pane.setVvalue(pixels == 0
                    ? pane.getVmin()
                    : pane.getVmin() + pos / pixels * (pane.getVmax() - pane.getVmin()));
        };
        return new SmoothScroller(reader, apply).bounds(() -> 0, range).attachTo(pane);
    }

    /** Limits the scroll position. Evaluated on every scroll, so it can follow content resizes. */
    public SmoothScroller bounds(DoubleSupplier min, DoubleSupplier max)
    {   this.min = min;
        this.max = max;
        return this;
    }

    /** How long the easing takes; roughly the time to cover 63% of the remaining distance. */
    public SmoothScroller smoothing(Duration timeConstant)
    {   this.timeConstant = Math.max(timeConstant.toSeconds(), 1e-3);
        return this;
    }

    /** Multiplier applied to scroll event deltas. */
    public SmoothScroller speed(double speed)
    {   this.speed = speed;
        return this;
    }

    /** Scrolls in response to vertical scroll events over {@code node}, ahead of any of its own handlers. */
    public SmoothScroller attachTo(Node node)
    {
        node.addEventFilter(ScrollEvent.SCROLL, e ->
        {   if (e.getDeltaY() == 0) return;
            this.scrollBy(-e.getDeltaY() * speed);
            e.consume();
        });
        return this;
    }

    public void scrollBy(double delta)
    {
        this.syncIfIdle();
        this.scrollTo(target + delta);
    }

    public void scrollTo(double target)
    {
        this.syncIfIdle();
        this.target = this.clamp(target);
        if (!running)
        {   running = true;
            lastFrame = System.nanoTime();
            timer.start();
        }
    }

    /** Moves straight to {@code position} without animating. */
    public void jumpTo(double position)
    {
        this.stop();
        this.position = this.target = this.clamp(position);
        apply.accept(this.position);
    }

    /**
     * Shifts the position and target together without animating or applying. For when the content
     * itself moved by {@code delta} and the view should stay on the same thing. Safe to call from
     * within the apply callback.
     */
    public void offset(double delta)
    {   position += delta;
        target += delta;
    }

    public void stop()
    {   running = false;
        timer.stop();
    }

    public double position()
    {   return position;
    }

    public double target()
    {   return target;
    }

    public boolean isScrolling()
    {   return running;
    }

    private void step(long now)
    {
        double dt = Math.max(0, (now - lastFrame) / 1e9);
        lastFrame = now;

        // Something else moved the area (e.g. a scrollbar drag); let it win
        if (reader != null && Math.abs(reader.getAsDouble() - position) > 1)
        {   position = target = reader.getAsDouble();
            this.stop();
            return;
        }

        target = this.clamp(target);
        double remaining = target - position;
        if (Math.abs(remaining) < SNAP_DISTANCE)
        {   position = target;
            this.stop();
        }
        // Exponential ease-out, scaled by elapsed time so it's frame-rate independent
        else position += remaining * (1 - Math.exp(-dt / timeConstant));
        apply.accept(position);
    }

    private void syncIfIdle()
    {
        if (!running && reader != null)
        {   position = target = reader.getAsDouble();
        }
    }

    private double clamp(double value)
    {   return Math.min(Math.max(value, min.getAsDouble()), max.getAsDouble());
    }
}

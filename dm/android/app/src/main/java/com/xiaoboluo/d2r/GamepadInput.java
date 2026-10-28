package com.xiaoboluo.d2r;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;

/** Physical controller state, in the Switch Npad bit/axis layout. */
final class GamepadInput {
    interface Sink { void send(long buttons, int lx, int ly, int rx, int ry); }
    private final Sink sink;
    private long keys, motion;
    private int lx, ly, rx, ry;

    GamepadInput(Sink sink) { this.sink = sink; }

    boolean key(KeyEvent event) {
        int bit = switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_A -> 1;
            case KeyEvent.KEYCODE_BUTTON_B -> 0;
            case KeyEvent.KEYCODE_BUTTON_X -> 3;
            case KeyEvent.KEYCODE_BUTTON_Y -> 2;
            case KeyEvent.KEYCODE_BUTTON_L1 -> 6;
            case KeyEvent.KEYCODE_BUTTON_R1 -> 7;
            case KeyEvent.KEYCODE_BUTTON_L2 -> 8;
            case KeyEvent.KEYCODE_BUTTON_R2 -> 9;
            case KeyEvent.KEYCODE_BUTTON_THUMBL -> 4;
            case KeyEvent.KEYCODE_BUTTON_THUMBR -> 5;
            case KeyEvent.KEYCODE_BUTTON_START -> 10;
            case KeyEvent.KEYCODE_BUTTON_SELECT -> 11;
            case KeyEvent.KEYCODE_DPAD_LEFT -> 12;
            case KeyEvent.KEYCODE_DPAD_UP -> 13;
            case KeyEvent.KEYCODE_DPAD_RIGHT -> 14;
            case KeyEvent.KEYCODE_DPAD_DOWN -> 15;
            default -> -1;
        };
        if (bit < 0 || event.getAction() == KeyEvent.ACTION_MULTIPLE) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) keys |= 1L << bit;
        else keys &= ~(1L << bit);
        send();
        return true;
    }

    private static int stick(MotionEvent event, int axis) {
        float value = event.getAxisValue(axis);
        InputDevice device = event.getDevice();
        InputDevice.MotionRange range = device == null ? null : device.getMotionRange(axis, event.getSource());
        float flat = range == null ? 0.12f : Math.max(0.12f, range.getFlat());
        return Math.abs(value) <= flat ? 0 : Math.round(Math.max(-1, Math.min(1, value)) * 32767);
    }

    boolean motion(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) return false;
        lx = stick(event, MotionEvent.AXIS_X); ly = -stick(event, MotionEvent.AXIS_Y);
        rx = stick(event, MotionEvent.AXIS_Z); ry = -stick(event, MotionEvent.AXIS_RZ);
        motion = 0;
        float x = event.getAxisValue(MotionEvent.AXIS_HAT_X), y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        if (x < -0.5f) motion |= 1L << 12;
        if (x > 0.5f) motion |= 1L << 14;
        if (y < -0.5f) motion |= 1L << 13;
        if (y > 0.5f) motion |= 1L << 15;
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)) > 0.5f) motion |= 1L << 8;
        if (Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)) > 0.5f) motion |= 1L << 9;
        send();
        return true;
    }

    void reset() { keys = motion = 0; lx = ly = rx = ry = 0; send(); }
    private void send() { sink.send(keys | motion, lx, ly, rx, ry); }
}

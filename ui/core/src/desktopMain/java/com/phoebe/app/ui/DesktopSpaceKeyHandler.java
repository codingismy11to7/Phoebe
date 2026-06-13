package com.phoebe.app.ui;

import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

public final class DesktopSpaceKeyHandler {
    private final KeyEventDispatcher dispatcher;

    private DesktopSpaceKeyHandler(KeyEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public interface SpaceToggle {
        void run();
    }

    public interface TextInputActive {
        boolean get();
    }

    public static DesktopSpaceKeyHandler install(
            Window window,
            SpaceToggle onToggle,
            TextInputActive textInputActive
    ) {
        KeyEventDispatcher dispatcher = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || event.getKeyCode() != KeyEvent.VK_SPACE) {
                return false;
            }
            if (!window.isFocused()) {
                return false;
            }
            if (textInputActive.get()) {
                return false;
            }
            if (isAwtEditableTextFocused()) {
                return false;
            }
            Window sourceWindow = event.getComponent() == null
                    ? window
                    : SwingUtilities.getWindowAncestor(event.getComponent());
            if (sourceWindow != window) {
                return false;
            }
            onToggle.run();
            return true;
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        return new DesktopSpaceKeyHandler(dispatcher);
    }

    public void uninstall() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
    }

    private static boolean isAwtEditableTextFocused() {
        java.awt.Component component = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        while (component != null) {
            if (component instanceof JTextComponent text && text.isEditable()) {
                return true;
            }
            component = component.getParent();
        }
        return false;
    }
}

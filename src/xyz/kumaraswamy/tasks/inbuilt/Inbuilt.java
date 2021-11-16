package xyz.kumaraswamy.tasks.inbuilt;

import com.google.appinventor.components.runtime.AndroidNonvisibleComponent;
import com.google.appinventor.components.runtime.ComponentContainer;
import xyz.kumaraswamy.tasks.ComponentManager;

public abstract class Inbuilt extends AndroidNonvisibleComponent {
    public Inbuilt(ComponentContainer container) {
        super(container.$form());
        // the constructor is now called
    }
}

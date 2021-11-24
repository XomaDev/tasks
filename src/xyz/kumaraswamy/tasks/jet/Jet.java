package xyz.kumaraswamy.tasks.jet;

import android.util.Log;
import com.google.appinventor.components.runtime.Component;
import org.jetbrains.annotations.NotNull;
import xyz.kumaraswamy.tasks.ComponentManager;

import java.util.ArrayList;
import java.util.HashMap;

public class Jet {

    private static final String TAG = "Jet";

    private final ComponentManager manager;
    private final HashMap<String, Object> vars;

    public Jet(ComponentManager manager,  HashMap<String, Object> vars) {
        this.manager = manager;
        this.vars = vars;
    }

    public Object process(Object object, Object[] args) {
        if (!(object instanceof String))
            return object;
        String text = object.toString();

        if (text.startsWith("[$") && text.endsWith("]")) {
            String key = text.substring(2, text.length() - 1);
            Component arg = manager.component(key);
            if (arg == null) {
                Log.w(TAG, "lex() invalid key '" + key + "'");
            }
            return arg;
        }

        final char[] chars = text.toCharArray();
        final int len = chars.length;

        boolean open = false;

        ArrayList<Object> texts = new ArrayList<>();
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < len; i++) {
            Character aChar = chars[i];

            if (open && aChar == '}') {
                open = false;
                texts.add(findObj(args, builder.toString()));
                builder.setLength(0);
            } else if (open) {
                builder.append(aChar);
            } else if (aChar == '{' && i + 1 < len
                    && chars[++i] == '$') {
                open = true;
            } else {
                int txtLen = texts.size();

                if (txtLen > 0) {
                    Object last = texts.get(--txtLen);
                    if (last instanceof String)
                        texts.set(txtLen, last + aChar.toString());
                } else {
                    texts.add(aChar.toString());
                }
            }
        }
        if (texts.size() == 1)
            return texts.get(0);

        StringBuilder builder1 = new StringBuilder();
        for (Object obj : texts) {
            builder1.append(obj);
        }
        return builder1.toString();
    }

    @NotNull
    private Object findObj(Object[] args, String text) {
        try {
            return args[Integer.parseInt(text)];
        } catch (NumberFormatException ignored) {
            return vars.getOrDefault(text, "");
        }
    }
}

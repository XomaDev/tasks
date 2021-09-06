package xyz.kumaraswamy.tasks.node;

public class ValueNode extends Node {
    private final String value;

    public ValueNode(final String value) {
        super(value);
        this.value = value;
    }

    @Override
    public String toString() {
        return '[' + String.valueOf(value) + ']';
    }
}

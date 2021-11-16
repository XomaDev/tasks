package xyz.kumaraswamy.tasks.node;

public class Node {
    private final String value;
    private Node left, right;

    public Node(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean hasNext() {
        return left != null;
    }

    public Node setLeft(Node left) {
        this.left = left;
        return this;
    }

    public Node setRight(Node right) {
        this.right = right;
        return this;
    }

    public Node getLeft() {
        return left;
    }

    public Node getRight() {
        return right;
    }

    @Override
    public String toString() {
        return "[" + "value=" + value + ", left=" + left + ", right=" + right + ']';
    }
}
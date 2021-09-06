package xyz.kumaraswamy.tasks.node;

import org.json.JSONException;
import org.json.JSONObject;

public class NodeEncoder {
    public static Object encodeNode(final Node node, boolean resultAsString) throws JSONException {
        final JSONObject jsonObject = new JSONObject();
        jsonObject.put("value", node.getValue());

        if (node instanceof ValueNode) {
            return jsonObject;
        }

        if (node.getLeft() != null) {
            jsonObject.put("left", encodeNode(node.getLeft(), false));
        }
        if (node.getRight() != null) {
            jsonObject.put("right", encodeNode(node.getRight(), false));
        }

        return resultAsString ? jsonObject.toString() : jsonObject;
    }
}

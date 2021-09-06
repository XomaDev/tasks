package xyz.kumaraswamy.tasks.node;

import org.json.JSONException;
import org.json.JSONObject;

public class NodeConstructor {
    public static Node constructNode(final String jsonNode) throws JSONException {
        final JSONObject jsonObject = new JSONObject(jsonNode);
        final String value = jsonObject.getString("value");

        if (jsonObject.length() == 1) {
            return new ValueNode(value);
        }

        return new Node(value)
                .setLeft(constructNode(jsonObject
                        .getJSONObject("left").toString()))
                .setRight(constructNode(jsonObject
                        .getJSONObject("right").toString()));
    }
}

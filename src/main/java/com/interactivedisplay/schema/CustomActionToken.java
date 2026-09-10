package com.interactivedisplay.schema;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class CustomActionToken {
    private static final String PREFIX = "__interactivedisplay_custom_action__:";

    private CustomActionToken() {
    }

    public static String encode(ResourceLocation actionId, Map<String, String> parameters) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeUTF(actionId.toString());
                Map<String, String> safeParameters = Map.copyOf(parameters == null ? Map.of() : parameters);
                output.writeInt(safeParameters.size());
                for (Map.Entry<String, String> entry : safeParameters.entrySet()) {
                    output.writeUTF(entry.getKey());
                    output.writeUTF(entry.getValue());
                }
            }
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("cannot encode custom action", exception);
        }
    }

    public static boolean isToken(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public static Decoded decode(String token) {
        if (!isToken(token)) {
            throw new IllegalArgumentException("not a custom action token");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token.substring(PREFIX.length()));
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                ResourceLocation actionId = ResourceLocation.tryParse(input.readUTF());
                if (actionId == null) {
                    throw new IllegalArgumentException("invalid custom action id");
                }
                int count = input.readInt();
                if (count < 0 || count > 256) {
                    throw new IllegalArgumentException("invalid custom action parameter count");
                }
                Map<String, String> parameters = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) {
                    parameters.put(input.readUTF(), input.readUTF());
                }
                if (input.available() != 0) {
                    throw new IllegalArgumentException("trailing custom action token data");
                }
                return new Decoded(actionId, Map.copyOf(parameters));
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid custom action token", exception);
        }
    }

    public record Decoded(ResourceLocation actionId, Map<String, String> parameters) {
    }
}

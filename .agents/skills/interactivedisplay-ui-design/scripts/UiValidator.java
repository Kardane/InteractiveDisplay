import com.interactivedisplay.core.component.ButtonBoxModel;
import com.interactivedisplay.core.component.ButtonComponentDefinition;
import com.interactivedisplay.core.component.ComponentActionType;
import com.interactivedisplay.core.layout.LayoutBounds;
import com.interactivedisplay.core.layout.MeditateLayoutEngine;
import com.interactivedisplay.schema.ConfigDocumentLoader;
import com.interactivedisplay.schema.MapImageResolver;
import com.interactivedisplay.schema.SchemaValidator;
import com.interactivedisplay.schema.WindowDefinitionParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

public class UiValidator {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected config directory and comma-separated window IDs");
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Path configDir = Path.of(args[0]);
        Path windowsDir = configDir.resolve("interactivedisplay/windows");
        var loader = new ConfigDocumentLoader();
        var validator = new SchemaValidator();
        var parser = new WindowDefinitionParser(new MapImageResolver(configDir, null), null);
        var engine = new MeditateLayoutEngine();
        for (String rawId : args[1].split(",", -1)) {
            String id = rawId.trim();
            if (!id.matches("[A-Za-z0-9_-]+")) {
                throw new IllegalArgumentException("Use a window filename stem, without paths: " + id);
            }
            Path path = windowsDir.resolve(id + ".yaml");
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("Missing YAML: " + path + "; uiConfigDir must contain interactivedisplay/");
            }
            var root = loader.load(path);
            var errors = validator.validate(root, path.toString());
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException(errors.toString());
            }
            var definition = parser.parse(root, path.toString());
            var layout = engine.calculate(definition);
            var bounds = LayoutBounds.centered(definition.size().width(), definition.size().height());
            var ids = new HashSet<String>();
            for (var entry : layout) {
                var component = entry.definition();
                if (!ids.add(component.id())) {
                    throw new IllegalArgumentException("Duplicate resolved component: " + component.id());
                }
                if (component instanceof ButtonComponentDefinition button) {
                    var box = ButtonBoxModel.resolve(button);
                    float x = entry.localPosition().x;
                    float y = entry.localPosition().y;
                    float hoverScale = button.hoverScale();
                    var hovered = new LayoutBounds(x, y - box.height() * (hoverScale - 1.0f) / 2.0f,
                            box.width() * hoverScale, box.height() * hoverScale);
                    if (!bounds.contains(hovered, 0.0001f)) {
                        throw new IllegalArgumentException("Button/hover exceeds window: " + button.id());
                    }
                    if (box.labelHeight() > box.contentHeight() + 0.0001f) {
                        throw new IllegalArgumentException("Estimated button label height exceeds content: " + button.id());
                    }
                    var action = button.action();
                    if (action.type() == ComponentActionType.OPEN_WINDOW && action.target() != null
                            && !Files.isRegularFile(windowsDir.resolve(action.target() + ".yaml"))) {
                        System.out.println("UI WARN " + id + ": navigation target " + action.target()
                                + " has no YAML file; verify API/runtime registration");
                    }
                }
            }
            System.out.println("UI PASS " + id + " components=" + layout.size()
                    + " schema/layout/button-height/hover; actual rendering remains unverified");
        }
    }
}

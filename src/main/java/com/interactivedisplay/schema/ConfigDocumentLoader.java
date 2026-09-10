package com.interactivedisplay.schema;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.nio.file.Path;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.nodes.Tag;

/** Loads one UTF-8 YAML document into Jackson's tree model. */
public final class ConfigDocumentLoader {
    private final ObjectMapper mapper;

    public ConfigDocumentLoader() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setAllowDuplicateKeys(false);
        loaderOptions.setTagInspector(tag -> !tag.isCustomGlobal());
        YAMLFactory factory = YAMLFactory.builder()
                .loaderOptions(loaderOptions)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(factory);
    }

    public JsonNode load(Path path) throws IOException {
        rejectCustomTags(path);
        try (JsonParser parser = this.mapper.getFactory().createParser(path.toFile())) {
            JsonNode root = this.mapper.readTree(parser);
            if (root == null) {
                throw new IOException("YAML document must not be empty");
            }

            JsonToken trailingToken = parser.nextToken();
            if (trailingToken != null) {
                throw new IOException("multiple YAML documents are not supported");
            }
            CustomActionNormalizer.normalize(root);
            return root;
        }
    }

    private void rejectCustomTags(Path path) throws IOException {
        try (YAMLParser parser = (YAMLParser) this.mapper.getFactory().createParser(path.toFile())) {
            while (parser.nextToken() != null) {
                String typeId = parser.getTypeId();
                if (typeId != null && !Tag.standardTags.contains(new Tag(typeId))) {
                    throw new IOException("custom YAML tags are not supported: " + typeId);
                }
            }
        }
    }
}

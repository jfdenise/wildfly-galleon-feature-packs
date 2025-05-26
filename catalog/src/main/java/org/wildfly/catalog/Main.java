/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

public class Main {

    public static void main(String[] args) throws Exception {
        try (InputStream stream = Main.class.getResourceAsStream("wildfly-catalog.json")) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(stream);
            ObjectNode target = mapper.createObjectNode();
            target.set("description", node.get("description"));
            target.set("documentation", node.get("documentation"));
            target.set("legend", node.get("legend"));
            ArrayNode an = (ArrayNode) node.get("content");
            Iterator<JsonNode> it = an.elements();
            Map<String, Map<String, JsonNode>> categories = new TreeMap<>();
            while (it.hasNext()) {
                String url = it.next().get("url").asText();
                JsonNode subCatalog = mapper.readTree(new URL(url));
                String version = subCatalog.get("version").asText();
                String fp = subCatalog.get("feature-pack-location").asText();

                ArrayNode layersArray = (ArrayNode) subCatalog.get("layers");
                Iterator<JsonNode> layers = layersArray.elements();
                while (layers.hasNext()) {
                    ObjectNode layer = (ObjectNode) layers.next();
                    layer.put("feature-pack", fp);
                    String layerName = layer.get("name").asText();
                    Iterator<JsonNode> properties = ((ArrayNode) layer.get("properties")).elements();
                    String category = null;
                    String description = null;
                    String note = null;
                    String addOn = null;
                    String stability = null;
                    List<JsonNode> discoveryRules = new ArrayList<>();
                    while (properties.hasNext()) {
                        JsonNode prop = properties.next();
                        String name = prop.get("name").asText();
                        if (name.equals("org.wildfly.category")) {
                            category = prop.get("value").asText();
                            continue;
                        }
                        if (name.equals("org.wildfly.description")) {
                            description = prop.get("value").asText();
                            continue;
                        }
                        if (name.equals("org.wildfly.note")) {
                            note = prop.get("value").asText();
                            continue;
                        }
                        if (name.equals("org.wildfly.stability")) {
                            stability = prop.get("value").asText();
                            continue;
                        }
                        if (name.equals("org.wildfly.rule.add-on")) {
                            String val = prop.get("value").asText();
                            addOn = val.split(",")[1];
                            continue;
                        }
                        if (name.startsWith("org.wildfly.rule") && !name.startsWith("org.wildfly.rule.add-on")) {
                            discoveryRules.add(prop);
                            continue;
                        }
                    }
                    layer.remove("properties");
                    if (category == null) {
                        throw new Exception("Invalid format, org.wildfly.category is missing for layer " + layer.get("name"));
                    }
                    if (description != null) {
                        layer.put("description", description);
                    }
                    if (note != null) {
                        layer.put("note", description);
                    }
                    if (addOn != null) {
                        layer.put("glowAddOn", addOn);
                    }
                    if (stability == null) {
                        layer.put("stability", "default");
                    } else {
                        layer.put("stability", stability);
                    }
                    if (!discoveryRules.isEmpty()) {
                        ArrayNode rules = mapper.createArrayNode();
                        rules.addAll(discoveryRules);
                        layer.putIfAbsent("glowRules", rules);
                    }
                    layer.put("glowDiscoverable", !discoveryRules.isEmpty());
                    Map<String, JsonNode> nodes = categories.get(category);
                    if (nodes == null) {
                        nodes = new TreeMap<>();
                        categories.put(category, nodes);
                    }
                    nodes.put(layerName, layer);
                }
            }
            ArrayNode categoriesArray = mapper.createArrayNode();
            target.putIfAbsent("categories", categoriesArray);
            for (Entry<String, Map<String, JsonNode>> entry : categories.entrySet()) {
                String categoryName = entry.getKey();
                ObjectNode category = mapper.createObjectNode();
                category.put("name", categoryName);
                ArrayNode categoryLayers = mapper.createArrayNode();
                category.put("functionalities", categoryLayers);
                for (Entry<String, JsonNode> layersInCategory : entry.getValue().entrySet()) {
                    categoryLayers.add(layersInCategory.getValue());
                }
                categoriesArray.add(category);
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(Paths.get("wildfly-catalog.json").toFile(), target);

        }
    }

}

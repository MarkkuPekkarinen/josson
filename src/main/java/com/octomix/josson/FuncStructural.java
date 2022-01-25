/*
 * Copyright 2020-2022 Octomix Software Technology Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.octomix.josson;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.octomix.josson.FuncExecutor.*;
import static com.octomix.josson.Josson.readJsonNode;
import static com.octomix.josson.JossonCore.*;
import static com.octomix.josson.Mapper.MAPPER;
import static com.octomix.josson.PatternMatcher.decomposeFunctionParameters;

class FuncStructural {

    static JsonNode funcCoalesce(JsonNode node, String params) {
        List<String> paramList = decomposeFunctionParameters(params, 1, -1);
        if (node.isArray()) {
            ArrayNode array = MAPPER.createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                array.add(funcCoalesce(node.get(i), paramList));
            }
            return array;
        }
        return funcCoalesce(node, paramList);
    }

    private static JsonNode funcCoalesce(JsonNode node, List<String> paramList) {
        if (node.isValueNode()) {
            if (!node.isNull()) {
                return node;
            }
            for (String path : paramList) {
                try {
                    node = toValueNode(path);
                    if (node != null && !node.isNull()) {
                        return node;
                    }
                } catch (NumberFormatException e) {
                    // continue
                }
            }
        } else if (node.isObject()) {
            for (String path : paramList) {
                JsonNode tryNode = getNodeByPath(node, path);
                if (tryNode != null && !tryNode.isNull()) {
                    return tryNode;
                }
            }
        }
        return null;
    }

    static TextNode funcCsv(JsonNode node, String params) {
        JsonNode container = getParamArrayOrItselfIsContainer(params, node);
        if (container == null) {
            return null;
        }
        List<JsonNode> values = new ArrayList<>();
        funcCsvCollectValues(values, container);
        return TextNode.valueOf(values.stream()
                .map(value -> csvQuote(value.asText()))
                .collect(Collectors.joining(",")));
    }

    private static void funcCsvCollectValues(List<JsonNode> values, JsonNode node) {
        if (node.isObject()) {
            node.forEach(elem -> {
                if (elem.isContainerNode()) {
                    funcCsvCollectValues(values, elem);
                } else if (!elem.isNull()) {
                    values.add(elem);
                }
            });
            return;
        }
        for (int i = 0; i < node.size(); i++) {
            JsonNode tryNode = node.get(i);
            if (tryNode.isContainerNode()) {
                funcCsvCollectValues(values, tryNode);
            } else if (!tryNode.isNull()) {
                values.add(tryNode);
            }
        }
    }

    static JsonNode funcField(JsonNode node, String params) {
        Map<String, String> args = getParamNamePath(decomposeFunctionParameters(params, 1, -1));
        if (node.isObject()) {
            return funcMap(node.deepCopy(), node, args, -1);
        }
        if (node.isArray()) {
            ArrayNode array = MAPPER.createArrayNode();
            for (int i = 0; i < node.size(); i++) {
                JsonNode elem = node.get(i);
                array.add(elem.isObject() ? funcMap(elem.deepCopy(), node, args, i) : null);
            }
            return array;
        }
        return null;
    }

    static JsonNode funcFlatten(JsonNode node, String params) {
        Pair<String, List<String>> pathAndParams = getParamPathAndStrings(params, 0, 1);
        if (pathAndParams.hasKey()) {
            node = getNodeByPath(node, pathAndParams.getKey());
            if (node == null) {
                return null;
            }
        }
        int flattenLevels = pathAndParams.getValue().size() > 0 ? getNodeAsInt(node, pathAndParams.getValue().get(0)) : 1;
        if (!node.isArray() || flattenLevels < 1) {
            return node;
        }
        ArrayNode array = MAPPER.createArrayNode();
        funcFlatten(array, node, flattenLevels);
        return array;
    }

    private static void funcFlatten(ArrayNode array, JsonNode node, int level) {
        for (int i = 0; i < node.size(); i++) {
            if (node.get(i).isArray()) {
                if (level == 1) {
                    array.addAll((ArrayNode) node.get(i));
                } else {
                    funcFlatten(array, node.get(i), level - 1);
                }
            } else {
                array.add(node.get(i));
            }
        }
    }

    static JsonNode funcJson(JsonNode node, String params) {
        return applyFunc(node, params,
                JsonNode::isTextual,
                jsonNode -> {
                    try {
                        return readJsonNode(jsonNode.asText());
                    } catch (JsonProcessingException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                }
        );
    }

    static JsonNode funcMap(JsonNode node, String params) {
        Map<String, String> args = getParamNamePath(decomposeFunctionParameters(params, 1, -1));
        if (!node.isArray()) {
            return funcMap(MAPPER.createObjectNode(), node, args, -1);
        }
        ArrayNode array = MAPPER.createArrayNode();
        for (int i  = 0; i < node.size(); i++) {
            array.add(funcMap(MAPPER.createObjectNode(), node, args, i));
        }
        return array;
    }

    private static ObjectNode funcMap(ObjectNode base, JsonNode node, Map<String, String> args, int index) {
        for (Map.Entry<String, String> arg : args.entrySet()) {
            String name = arg.getKey();
            if (isCurrentNodePath(name)) {
                if ((index >= 0 ? node.get(index) : node).isObject()) {
                    base.setAll((ObjectNode) (index >= 0 ? node.get(index) : node));
                }
                continue;
            }
            String path = arg.getValue();
            if (path == null) {
                base.remove(name);
            } else {
                base.set(name, getNodeByPath(node, index, path));
            }
        }
        return base;
    }

    static JsonNode funcToArray(JsonNode node, String params) {
        JsonNode container = getParamArrayOrItselfIsContainer(params, node);
        if (container == null) {
            return null;
        }
        ArrayNode array = MAPPER.createArrayNode();
        if (container.isArray()) {
            for (int i = 0; i < container.size(); i++) {
                if (container.get(i).isArray()) {
                    array.addAll((ArrayNode) container.get(i));
                } else if (container.get(i).isObject()) {
                    container.get(i).forEach(array::add);
                } else {
                    array.add(container.get(i));
                }
            }
        } else {
            container.forEach(array::add);
        }
        return array;
    }
}

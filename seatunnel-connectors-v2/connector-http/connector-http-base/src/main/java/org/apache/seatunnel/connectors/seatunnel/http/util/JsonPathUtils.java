/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.http.util;

import org.apache.seatunnel.connectors.seatunnel.http.config.JsonField;
import org.apache.seatunnel.connectors.seatunnel.http.exception.HttpConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.http.exception.HttpConnectorException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.ReadContext;
import com.jayway.jsonpath.WriteContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Utility class for JsonPath operations. */
public class JsonPathUtils {

    private static final Option[] DEFAULT_OPTIONS = {
        Option.SUPPRESS_EXCEPTIONS, Option.ALWAYS_RETURN_LIST, Option.DEFAULT_PATH_LEAF_TO_NULL
    };

    private static final Configuration JSON_CONFIGURATION =
            Configuration.defaultConfiguration().addOptions(DEFAULT_OPTIONS);

    /**
     * Creates a ReadContext from a JSON string.
     *
     * @param json The JSON string
     * @return A ReadContext for the JSON
     */
    public static ReadContext parseJson(String json) {
        return JsonPath.using(JSON_CONFIGURATION).parse(json);
    }

    /**
     * Creates JsonPath array from JsonField.
     *
     * @param jsonField The JsonField to convert
     * @return Array of JsonPath objects
     */
    public static JsonPath[] createJsonPaths(JsonField jsonField) {
        if (jsonField == null || jsonField.getFields() == null || jsonField.getFields().isEmpty()) {
            throw new HttpConnectorException(
                    HttpConnectorErrorCode.FIELD_DATA_IS_INCONSISTENT,
                    "JsonField cannot be null or empty");
        }

        JsonPath[] jsonPaths = new JsonPath[jsonField.getFields().size()];
        int index = 0;
        for (String pathString : jsonField.getFields().values()) {
            jsonPaths[index++] = JsonPath.compile(pathString);
        }

        return jsonPaths;
    }

    /**
     * Converts parsed data to a list of maps.
     *
     * @param data The raw data (list of lists)
     * @param jsonField The JsonField containing field names
     * @return List of maps with field names as keys
     */
    public static List<Map<String, String>> parseToMap(
            List<List<String>> data, JsonField jsonField) {
        List<Map<String, String>> resultList = new ArrayList<>(data.size());
        String[] keys = jsonField.getFields().keySet().toArray(new String[0]);

        for (List<String> row : data) {
            Map<String, String> resultMap = new HashMap<>(jsonField.getFields().size());
            for (int i = 0; i < row.size(); i++) {
                resultMap.put(keys[i], row.get(i));
            }
            resultList.add(resultMap);
        }

        return resultList;
    }
    /**
     * Reads a value from a JsonNode using JSONPath.
     *
     * @param rootNode The root JsonNode.
     * @param jsonPath The JSONPath expression.
     * @return The JsonNode at the specified path, or null if not found.
     */
    public static JsonNode readJsonPath(JsonNode rootNode, String jsonPath) {
        if (rootNode == null || jsonPath == null || jsonPath.trim().isEmpty()) {
            return null;
        }

        try {
            ReadContext context = JsonPath.using(JSON_CONFIGURATION).parse(rootNode.toString());
            Object result = context.read(jsonPath);

            if (result == null) {
                return null;
            }
            // 如果 result 已经是 JsonNode，直接返回
            if (isAllElementsEmpty(result)) {
                return null;
            }

            // 处理多包一层的情况
            if (result instanceof List) {
                List<?> list = (List<?>) result;
                if (list.size() == 1
                        && (list.get(0) instanceof List || list.get(0) instanceof Map)) {
                    result = list.get(0);
                }
            }

            ObjectMapper mapper = new ObjectMapper();
            return mapper.valueToTree(result);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets a value in a JsonNode at the specified JSONPath. Returns a new JsonNode with the value
     * set.
     *
     * @param rootNode The root JsonNode.
     * @param jsonPath The JSONPath expression.
     * @param value The value to set.
     * @return A new JsonNode with the value set, or original node if failed.
     */
    public static JsonNode setJsonPath(JsonNode rootNode, String jsonPath, JsonNode value) {
        if (rootNode == null || jsonPath == null || jsonPath.trim().isEmpty() || value == null) {
            return rootNode;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();

            // 把 JsonNode 转成普通的 Java 对象（Map/List/primitive）
            Object rootObj = mapper.convertValue(rootNode, Object.class);
            Object valueObj = mapper.convertValue(value, Object.class);

            // 使用你现有的 JSON_CONFIGURATION（假设已配置为写模式）
            WriteContext context = JsonPath.using(JSON_CONFIGURATION).parse(rootObj);
            context.set(jsonPath, valueObj);

            // 从 context 获取序列化后的字符串并解析回 JsonNode
            String updatedJson = context.jsonString();
            return mapper.readTree(updatedJson);
        } catch (Exception e) {
            // 可酌情记录日志：e.printStackTrace();
            return rootNode;
        }
    }
    /**
     * Converts the 'items' array to an array of objects using 'fields' as field names. Paths for
     * 'fields' and 'items' are configurable via 'fieldsJsonPath' and 'itemsJsonPath'. Field mapping
     * can be customized via 'fieldMapping'.
     *
     * @param jsonContent The original JSON content from HTTP response.
     * @param fieldsJsonPath JSON path to the array containing field names.
     * @param itemsJsonPath JSON path to the array containing data rows.
     * @param fieldMapping Specify field name mapping from array index to output field name.
     *     Example: {"0": "code", "1": "full_name"}
     * @return The modified JSON content with 'items' converted to objects, or original content if
     *     conversion fails.
     */
    public static String transformItemsToArrayOfObjects(
            String jsonContent,
            String fieldsJsonPath,
            String itemsJsonPath,
            Map<String, String> fieldMapping) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode rootNode = mapper.readTree(jsonContent);

            // Get fields node by configurable path
            JsonNode fieldsNode = readJsonPath(rootNode, fieldsJsonPath);
            if (fieldsNode == null || !fieldsNode.isArray()) {
                return jsonContent;
            }

            // Get items node by configurable path
            JsonNode itemsNode = readJsonPath(rootNode, itemsJsonPath);
            if (itemsNode == null || !itemsNode.isArray()) {
                return jsonContent;
            }

            // Prepare field mapping: index -> output field name
            Map<Integer, String> indexToFieldName = new HashMap<>();
            if (fieldMapping != null && !fieldMapping.isEmpty()) {
                for (Map.Entry<String, String> entry : fieldMapping.entrySet()) {
                    try {
                        int index = Integer.parseInt(entry.getKey());
                        indexToFieldName.put(index, entry.getValue());
                    } catch (NumberFormatException e) {
                        throw new HttpConnectorException(
                                HttpConnectorErrorCode.FIELD_DATA_IS_INCONSISTENT,
                                "Invalid field mapping key:" + entry.getKey());
                    }
                }
            } else {
                // Default: use fields as field names
                for (int i = 0; i < fieldsNode.size(); i++) {
                    String fieldName = fieldsNode.get(i).asText();
                    indexToFieldName.put(i, fieldName);
                }
            }

            // Convert items to objects
            ArrayNode newItems = mapper.createArrayNode();

            for (JsonNode item : itemsNode) {
                if (!item.isArray()) {
                    continue;
                }

                ObjectNode obj = mapper.createObjectNode();
                int itemCount = item.size();

                // Map each value to its corresponding field name
                for (int i = 0; i < itemCount; i++) {
                    if (indexToFieldName.containsKey(i)) {
                        String fieldName = indexToFieldName.get(i);
                        JsonNode fieldValue = item.get(i);
                        obj.set(fieldName, fieldValue);
                    }
                }
                newItems.add(obj);
            }

            // Replace the original 'items' array
            rootNode = setJsonPath(rootNode, itemsJsonPath, newItems);

            // Return the modified JSON
            return mapper.writeValueAsString(rootNode);

        } catch (Exception e) {
            return jsonContent; // Return original content on error
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean isAllElementsEmpty(Object result) {
        if (!(result instanceof List)) {
            return false; // 不是集合，不适用这个判断
        }

        List<?> list = (List<?>) result;
        if (list.isEmpty()) {
            return true; // 空集合直接算空
        }

        for (Object item : list) {
            if (item == null) continue;

            if (item instanceof Map && !((Map<?, ?>) item).isEmpty()) {
                return false; // 有非空对象
            }

            if (item instanceof List && !((List<?>) item).isEmpty()) {
                return false; // 有非空子列表
            }

            if (item instanceof String && !((String) item).trim().isEmpty()) {
                return false; // 有非空字符串
            }

            // 对于基本类型数字或布尔，也视为非空
            if (item instanceof Number || item instanceof Boolean) {
                return false;
            }
        }

        // 走到这里说明所有元素都是空的
        return true;
    }
}

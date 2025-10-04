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

package org.apache.seatunnel.connectors.seatunnel.http;

import org.apache.seatunnel.connectors.seatunnel.http.util.JsonPathUtils;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonPathUtilsItemsToObjectConversionTest {

    private static final String TEST_INPUT_JSON =
            "{\n"
                    + "  \"product\": {\n"
                    + "    \"attributes\": [\"sku\", \"title\", \"price\"],\n"
                    + "    \"products\": [\n"
                    + "      [\"P001\", \"iPhone 15\", \"999.99\"],\n"
                    + "      [\"P002\", \"MacBook Pro\", \"1999.99\"],\n"
                    + "      [\"P003\", \"AirPods Pro\", \"249.99\"]\n"
                    + "    ]\n"
                    + "  }\n"
                    + "}";

    private static final String EXPECTED_OUTPUT_JSON =
            "{\n"
                    + "  \"product\": {\n"
                    + "    \"attributes\": [\"sku\", \"title\", \"price\"],\n"
                    + "    \"products\": [\n"
                    + "      {\"item_sku\": \"P001\", \"item_name\": \"iPhone 15\", \"item_price\": \"999.99\"},\n"
                    + "      {\"item_sku\": \"P002\", \"item_name\": \"MacBook Pro\", \"item_price\": \"1999.99\"},\n"
                    + "      {\"item_sku\": \"P003\", \"item_name\": \"AirPods Pro\", \"item_price\": \"249.99\"}\n"
                    + "    ]\n"
                    + "  }\n"
                    + "}";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testTransformWithCustomPathsAndMapping() throws Exception {
        // 设置参数
        String fieldsJsonPath = "product.attributes";
        String itemsJsonPath = "product.products";
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("0", "item_sku");
        fieldMapping.put("1", "item_name");
        fieldMapping.put("2", "item_price");

        // 执行转换
        String result =
                JsonPathUtils.transformItemsToArrayOfObjects(
                        TEST_INPUT_JSON, fieldsJsonPath, itemsJsonPath, fieldMapping);

        // 验证输出
        JsonNode resultNode = mapper.readTree(result);
        JsonNode expectedNode = mapper.readTree(EXPECTED_OUTPUT_JSON);

        assertEquals(expectedNode, resultNode);
    }

    @Test
    public void testDefaultFieldMapping() throws Exception {
        // 设置参数
        String fieldsJsonPath = "product.attributes";
        String itemsJsonPath = "product.products";

        // 执行转换
        String result =
                JsonPathUtils.transformItemsToArrayOfObjects(
                        TEST_INPUT_JSON, fieldsJsonPath, itemsJsonPath, null);

        // 预期输出：使用 attributes 中的字段名
        String expected =
                "{\n"
                        + "  \"product\": {\n"
                        + "    \"attributes\": [\"sku\", \"title\", \"price\"],\n"
                        + "    \"products\": [\n"
                        + "      {\"sku\": \"P001\", \"title\": \"iPhone 15\", \"price\": \"999.99\"},\n"
                        + "      {\"sku\": \"P002\", \"title\": \"MacBook Pro\", \"price\": \"1999.99\"},\n"
                        + "      {\"sku\": \"P003\", \"title\": \"AirPods Pro\", \"price\": \"249.99\"}\n"
                        + "    ]\n"
                        + "  }\n"
                        + "}";

        JsonNode resultNode = mapper.readTree(result);
        JsonNode expectedNode = mapper.readTree(expected);

        assertEquals(expectedNode, resultNode);
    }

    @Test
    public void testInvalidFieldsPath() throws Exception {
        // 设置参数
        String fieldsJsonPath = "product.invalid_fields"; // ← 不存在的路径
        String itemsJsonPath = "product.products";

        // 执行转换
        String result =
                JsonPathUtils.transformItemsToArrayOfObjects(
                        TEST_INPUT_JSON, fieldsJsonPath, itemsJsonPath, null);

        // 应返回原内容
        assertEquals(TEST_INPUT_JSON, result);
    }

    @Test
    public void testInvalidItemsPath() throws Exception {
        // 设置参数
        String fieldsJsonPath = "product.attributes";
        String itemsJsonPath = "product.invalid_items"; // ← 不存在的路径
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("0", "item_sku");

        // 执行转换
        String result =
                JsonPathUtils.transformItemsToArrayOfObjects(
                        TEST_INPUT_JSON, fieldsJsonPath, itemsJsonPath, fieldMapping);

        // 应返回原内容
        assertEquals(TEST_INPUT_JSON, result);
    }

    @Test
    public void testEmptyFieldMapping() throws Exception {
        // 设置参数
        String fieldsJsonPath = "product.attributes";
        String itemsJsonPath = "product.products";
        Map<String, String> fieldMapping = new HashMap<>(); // ← 空映射

        // 执行转换
        String result =
                JsonPathUtils.transformItemsToArrayOfObjects(
                        TEST_INPUT_JSON, fieldsJsonPath, itemsJsonPath, fieldMapping);

        // 应使用默认字段名
        String expected =
                "{\n"
                        + "  \"product\": {\n"
                        + "    \"attributes\": [\"sku\", \"title\", \"price\"],\n"
                        + "    \"products\": [\n"
                        + "      {\"sku\": \"P001\", \"title\": \"iPhone 15\", \"price\": \"999.99\"},\n"
                        + "      {\"sku\": \"P002\", \"title\": \"MacBook Pro\", \"price\": \"1999.99\"},\n"
                        + "      {\"sku\": \"P003\", \"title\": \"AirPods Pro\", \"price\": \"249.99\"}\n"
                        + "    ]\n"
                        + "  }\n"
                        + "}";

        JsonNode resultNode = mapper.readTree(result);
        JsonNode expectedNode = mapper.readTree(expected);

        assertEquals(expectedNode, resultNode);
    }

    @Test
    public void testPartialFieldMapping() throws Exception {
        // 设置参数
        String fieldsJsonPath = "product.attributes";
        String itemsJsonPath = "product.products";
        Map<String, String> fieldMapping = new HashMap<>();
        fieldMapping.put("0", "item_sku");
        // 不映射 title 和 price

        // 执行转换
        String result =
                JsonPathUtils.transformItemsToArrayOfObjects(
                        TEST_INPUT_JSON, fieldsJsonPath, itemsJsonPath, fieldMapping);

        // 预期输出：只映射 item_sku，其他字段被忽略
        String expected =
                "{\n"
                        + "  \"product\": {\n"
                        + "    \"attributes\": [\"sku\", \"title\", \"price\"],\n"
                        + "    \"products\": [\n"
                        + "      {\"item_sku\": \"P001\"},\n"
                        + "      {\"item_sku\": \"P002\"},\n"
                        + "      {\"item_sku\": \"P003\"}\n"
                        + "    ]\n"
                        + "  }\n"
                        + "}";

        JsonNode resultNode = mapper.readTree(result);
        JsonNode expectedNode = mapper.readTree(expected);

        assertEquals(expectedNode, resultNode);
    }
}

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

import com.fasterxml.jackson.databind.JsonNode;

import static com.octomix.josson.Mapper.intoNewArray;

/**
 * Logical operations on a stack of OperationStep for JsonNode.
 */
class OperationStackForJsonNode extends OperationStack {

    private final Josson arrayNode;

    OperationStackForJsonNode(final JsonNode node) {
        super(false);
        this.arrayNode = node.isArray() ? Josson.create(node) : Josson.create(intoNewArray(node));
    }

    protected JsonNode evaluateExpression(final OperationStep step, final int arrayIndex) {
        return arrayNode.getNode(arrayIndex, step.getExpression());
    }

    JsonNode evaluateStatement(final String statement) {
        return evaluate(statement, 0);
    }
}

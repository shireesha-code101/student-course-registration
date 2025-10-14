package org.example.registration.dao;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class EmailIndexDao {
    private final DynamoDbClient client;
    private final String table = "EmailIndex";

    public EmailIndexDao(DynamoDbClient client) {
        this.client = client;
    }
    public void putEmail(String email, String studentId) {
        if (email == null) throw new IllegalArgumentException("email is null");
        String norm = email.toLowerCase();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("email", AttributeValue.builder().s(norm).build());
        item.put("studentId", AttributeValue.builder().s(studentId).build());

        try {
            client.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(item)
                    .conditionExpression("attribute_not_exists(email)")
                    .build());
        } catch (ConditionalCheckFailedException cfe) {
            // caller expects this to know email is duplicate
            throw cfe;
        } // ResourceNotFoundException / DynamoDbException allowed to bubble up or be handled by service
    }

    public boolean emailExists(String email) {
        if (email == null) return false;
        String norm = email.toLowerCase();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("email", AttributeValue.builder().s(norm).build());

        try {
            var res = client.getItem(GetItemRequest.builder().tableName(table).key(key).build());
            return res.hasItem();
        } catch (ResourceNotFoundException rnfe) {
            System.err.println("Table '" + table + "' not found. Please create it. (" + rnfe.getMessage() + ")");
            return false;
        } catch (DynamoDbException e) {
            // rethrow as runtime - caller can catch/log
            throw e;
        }
    }
}

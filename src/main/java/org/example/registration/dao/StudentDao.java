package org.example.registration.dao;

import org.example.registration.model.Student;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

public class StudentDao {
    private final DynamoDbClient client;
    private final String table = "Student";

    public StudentDao(DynamoDbClient client) { this.client = client; }
    public void updatePassword(String studentId, String hashedPassword) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("studentId", AttributeValue.builder().s(studentId).build());

        UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(table)
                .key(key)
                .updateExpression("SET #pwd = :p")
                .expressionAttributeNames(Map.of("#pwd", "passwordHash"))
                .expressionAttributeValues(Map.of(":p", AttributeValue.builder().s(hashedPassword).build()))
                .conditionExpression("attribute_exists(studentId)") // ensure the student exists
                .build();

        client.updateItem(req);
    }

    public void putStudent(Student s) {
        PutItemRequest req = PutItemRequest.builder()
                .tableName(table)
                .item(s.toItem())
                .conditionExpression("attribute_not_exists(studentId)")
                .build();
        client.putItem(req);
    }

    public Student getStudent(String studentId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("studentId", AttributeValue.builder().s(studentId).build());
        var res = client.getItem(GetItemRequest.builder().tableName(table).key(key).build());
        if (res.hasItem()) return Student.fromItem(res.item());
        return null;
    }

    public void deleteStudentById(String studentId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("studentId", AttributeValue.builder().s(studentId).build());
        client.deleteItem(DeleteItemRequest.builder().tableName(table).key(key).build());
    }
}

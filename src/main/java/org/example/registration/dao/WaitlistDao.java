package org.example.registration.dao;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class WaitlistDao {
    private final DynamoDbClient client;
    private final String tableName = "Waitlist";

    public WaitlistDao(DynamoDbClient client) {
        this.client = client;
    }
    public void addToWaitlist(String courseId, String studentId, Map<String, String> extra) {
        try {
            String createdAt = String.valueOf(System.currentTimeMillis());

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("courseId", AttributeValue.builder().s(courseId).build());
            item.put("createdAt", AttributeValue.builder().s(createdAt).build());
            item.put("studentId", AttributeValue.builder().s(studentId).build());

            if (extra != null) {
                if (extra.containsKey("name"))
                    item.put("name", AttributeValue.builder().s(extra.get("name")).build());
                if (extra.containsKey("email"))
                    item.put("email", AttributeValue.builder().s(extra.get("email")).build());
            }

            PutItemRequest req = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            client.putItem(req);
        } catch (Exception e) {
            System.err.println("Error adding to waitlist: " + e.getMessage());
        }
    }
    public String popFirstWaitlistedStudent(String courseId) {
        try {
            QueryRequest query = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("courseId = :cid")
                    .expressionAttributeValues(Map.of(":cid", AttributeValue.builder().s(courseId).build()))
                    .scanIndexForward(true) // oldest first
                    .limit(1)
                    .consistentRead(true) // prefer fresh data when promoting
                    .build();

            QueryResponse res = client.query(query);
            if (res.count() == 0) return null;

            Map<String, AttributeValue> first = res.items().get(0);
            String studentId = first.get("studentId").s();
            String createdAt = first.get("createdAt").s();

            // remove the popped entry
            removeWaitlistEntry(courseId, createdAt);
            return studentId;
        } catch (Exception e) {
            System.err.println("Error popping waitlist student: " + e.getMessage());
            return null;
        }
    }
    public boolean removeWaitlistEntry(String courseId, String createdAt) {
        try {
            Map<String, AttributeValue> key = Map.of(
                    "courseId", AttributeValue.builder().s(courseId).build(),
                    "createdAt", AttributeValue.builder().s(createdAt).build()
            );
            DeleteItemRequest req = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();
            client.deleteItem(req);
            return true;
        } catch (Exception e) {
            System.err.println("Error removing waitlist entry: " + e.getMessage());
            return false;
        }
    }
    public boolean removeAllWaitlistEntries(String courseId, String studentId) {
        try {
            // Scan for all items matching both courseId and studentId
            ScanRequest scanReq = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("courseId = :cid AND studentId = :sid")
                    .expressionAttributeValues(Map.of(
                            ":cid", AttributeValue.builder().s(courseId).build(),
                            ":sid", AttributeValue.builder().s(studentId).build()
                    ))
                    .consistentRead(true) // make sure we see latest state
                    .build();

            ScanResponse res = client.scan(scanReq);
            if (res.items().isEmpty()) return false;

            for (var item : res.items()) {
                if (item.containsKey("createdAt")) {
                    String createdAt = item.get("createdAt").s();
                    Map<String, AttributeValue> key = Map.of(
                            "courseId", AttributeValue.builder().s(courseId).build(),
                            "createdAt", AttributeValue.builder().s(createdAt).build()
                    );
                    try {
                        client.deleteItem(DeleteItemRequest.builder()
                                .tableName(tableName)
                                .key(key)
                                .build());
                    } catch (Exception ex) {
                        System.err.println("Warning: failed to delete waitlist item " + key + ": " + ex.getMessage());
                    }
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error removing waitlist entries: " + e.getMessage());
            return false;
        }
    }
    public List<Map<String, AttributeValue>> getWaitlistsByStudent(String studentId, int limit) {
        try {
            ScanRequest req = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("studentId = :sid")
                    .expressionAttributeValues(Map.of(":sid",
                            AttributeValue.builder().s(studentId).build()))
                    .limit(limit > 0 ? limit : 100)
                    .consistentRead(true) // prefer fresh results
                    .build();

            ScanResponse res = client.scan(req);
            return res.items();
        } catch (Exception e) {
            System.err.println("Error getting waitlists by student: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    public List<Map<String, AttributeValue>> getWaitlistsByCourse(String courseId) {
        try {
            QueryRequest query = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("courseId = :cid")
                    .expressionAttributeValues(Map.of(":cid",
                            AttributeValue.builder().s(courseId).build()))
                    .scanIndexForward(true)
                    .consistentRead(true) // prefer fresh results for admin actions
                    .build();

            QueryResponse res = client.query(query);
            return res.items();
        } catch (Exception e) {
            System.err.println("Error getting waitlists by course: " + e.getMessage());
            return Collections.emptyList();
        }
    }
    public boolean isStudentOnWaitlist(String courseId, String studentId) {
        try {
            ScanRequest req = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("courseId = :cid AND studentId = :sid")
                    .expressionAttributeValues(Map.of(
                            ":cid", AttributeValue.builder().s(courseId).build(),
                            ":sid", AttributeValue.builder().s(studentId).build()))
                    .consistentRead(true) // ensure up-to-date check
                    .build();

            ScanResponse res = client.scan(req);
            return !res.items().isEmpty();
        } catch (Exception e) {
            System.err.println("Error checking waitlist: " + e.getMessage());
            return false;
        }
    }
}

package org.example.registration.dao;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
public class DropDao {
    private final DynamoDbClient client;
    private final String tableName = "DropHistory";

    public DropDao(DynamoDbClient client) {
        this.client = client;
    }

    public boolean recordDrop(String studentId, String courseId, String actor, String reason) {
        try {
            if (studentId == null || studentId.isBlank()) studentId = "UNKNOWN_STUDENT";
            if (courseId == null || courseId.isBlank()) courseId = "UNKNOWN_COURSE";
            if (actor == null) actor = "UNKNOWN";
            if (reason == null) reason = "";

            String dropId = UUID.randomUUID().toString();

            Map<String, AttributeValue> item = new HashMap<>();
            item.put("dropId", AttributeValue.builder().s(dropId).build()); // safe PK
            item.put("studentId", AttributeValue.builder().s(studentId).build());
            item.put("courseId", AttributeValue.builder().s(courseId).build());
            item.put("actor", AttributeValue.builder().s(actor).build());
            item.put("reason", AttributeValue.builder().s(reason).build());
            item.put("droppedAt", AttributeValue.builder().n(String.valueOf(System.currentTimeMillis())).build());

            PutItemRequest req = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            client.putItem(req);

            // confirm that the item exists (consistent read)
            List<Map<String, AttributeValue>> found = debugFindDropsInternal(studentId, courseId);
            if (found.isEmpty()) {
                System.err.println("Warning: recordDrop wrote an item but subsequent scan found 0 items for "
                        + studentId + " / " + courseId + ". This could indicate a table key mismatch.");
                return false;
            }
            return true;
        } catch (Exception e) {
            System.err.println("Error recording drop (PutItem): " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a student has previously dropped a course.
     * Uses consistentRead(true) so reads see the most recent writes.
     */
    public boolean hasDroppedBefore(String studentId, String courseId) {
        try {
            ScanRequest req = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("studentId = :sid AND courseId = :cid")
                    .expressionAttributeValues(Map.of(
                            ":sid", AttributeValue.builder().s(studentId).build(),
                            ":cid", AttributeValue.builder().s(courseId).build()))
                    .limit(1)
                    .consistentRead(true)
                    .build();

            ScanResponse res = client.scan(req);
            return !res.items().isEmpty();
        } catch (Exception e) {
            System.err.println("Error checking previous drop: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get drop history for a course (optional).
     */
    public List<String> getDropHistoryByCourse(String courseId) {
        try {
            ScanRequest req = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("courseId = :cid")
                    .expressionAttributeValues(Map.of(":cid",
                            AttributeValue.builder().s(courseId).build()))
                    .consistentRead(true)
                    .build();

            ScanResponse res = client.scan(req);
            List<String> list = new ArrayList<>();
            for (var item : res.items()) {
                String sid = item.containsKey("studentId") ? item.get("studentId").s() : "UNKNOWN";
                String actor = item.containsKey("actor") ? item.get("actor").s() : "UNKNOWN";
                String reason = item.containsKey("reason") ? item.get("reason").s() : "";
                list.add(sid + " (" + actor + ") - " + reason);
            }
            return list;
        } catch (Exception e) {
            System.err.println("Error getting drop history: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    // ----------------------
    // Debug helpers
    // ----------------------

    /**
     * Internal helper: scan and return matching raw items (consistent read).
     */
    private List<Map<String, AttributeValue>> debugFindDropsInternal(String studentId, String courseId) {
        try {
            ScanRequest req = ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("studentId = :sid AND courseId = :cid")
                    .expressionAttributeValues(Map.of(
                            ":sid", AttributeValue.builder().s(studentId).build(),
                            ":cid", AttributeValue.builder().s(courseId).build()))
                    .consistentRead(true)
                    .build();
            ScanResponse res = client.scan(req);
            return res.items();
        } catch (Exception e) {
            System.err.println("Error scanning DropHistory (internal): " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Debug method you can call from code (temporarily) to print found drop items for a student/course.
     * Example: call this immediately after recordDrop in your tests to confirm writes.
     */
    public void debugFindDrops(String studentId, String courseId) {
        var items = debugFindDropsInternal(studentId, courseId);
        System.out.println("Debug: found " + items.size() + " drop records for " + studentId + " / " + courseId);
        for (var it : items) {
            System.out.println(" -> " + it);
        }
    }
}

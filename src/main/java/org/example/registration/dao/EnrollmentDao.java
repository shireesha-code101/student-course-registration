package org.example.registration.dao;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * EnrollmentDao
 *
 * Table: Enrollment
 *  - studentId (Partition key)
 *  - courseId (Sort key)
 *  - status ("ENROLLED" or "WAITLIST")
 */
public class EnrollmentDao {
    private final DynamoDbClient client;
    private final String tableName = "Enrollment";

    public EnrollmentDao(DynamoDbClient client) {
        this.client = client;
    }

    // ------------------------------------------------------
    // PUT ENROLLMENT
    // ------------------------------------------------------
    public void putEnrollment(String studentId, String courseId, String status) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("studentId", AttributeValue.builder().s(studentId).build());
            item.put("courseId", AttributeValue.builder().s(courseId).build());
            item.put("status", AttributeValue.builder().s(status).build());
            item.put("createdAt", AttributeValue.builder().s(String.valueOf(System.currentTimeMillis())).build());

            PutItemRequest req = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            client.putItem(req);
        } catch (Exception e) {
            System.err.println("Error adding enrollment: " + e.getMessage());
        }
    }

    // ------------------------------------------------------
    // DELETE ENROLLMENT (conditional)
    // ------------------------------------------------------
    /**
     * Delete enrollment only if the item exists (conditional delete).
     * Returns true if an item was deleted, false if there was no enrollment to delete.
     */
    public boolean deleteEnrollment(String studentId, String courseId) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("studentId", AttributeValue.builder().s(studentId).build());
            key.put("courseId", AttributeValue.builder().s(courseId).build());

            DeleteItemRequest req = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    // ensure the item exists before deleting (atomic on server)
                    .conditionExpression("attribute_exists(studentId) AND attribute_exists(courseId)")
                    .build();

            client.deleteItem(req);
            return true;
        } catch (ConditionalCheckFailedException ccfe) {
            // Item did not exist (or keys didn't match) — not an error for callers, just indicate nothing deleted
            return false;
        } catch (Exception e) {
            System.err.println("Error deleting enrollment: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------
    // CHECK IF STUDENT IS ENROLLED (ONLY)
    // ------------------------------------------------------
    public boolean isEnrolled(String studentId, String courseId) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("studentId", AttributeValue.builder().s(studentId).build());
            key.put("courseId", AttributeValue.builder().s(courseId).build());

            GetItemRequest req = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .consistentRead(true) // ensure we see most recent writes
                    .build();

            GetItemResponse res = client.getItem(req);
            return res.hasItem();
        } catch (Exception e) {
            System.err.println("Error checking enrollment: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------
    // LIST ENROLLMENTS (for admin)
    // ------------------------------------------------------
    public List<Map<String, AttributeValue>> listAllEnrollments() {
        try {
            ScanRequest req = ScanRequest.builder().tableName(tableName).build();
            return client.scan(req).items();
        } catch (Exception e) {
            System.err.println("Error scanning enrollments: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}

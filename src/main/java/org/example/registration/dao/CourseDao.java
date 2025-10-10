package org.example.registration.dao;

import org.example.registration.model.Course;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * CourseDao
 *
 * Works with Course model fields:
 *  - courseId (PK)
 *  - title (S)
 *  - maxSeats (N)
 *  - currentEnrolled (N)
 *
 * Reservation strategy:
 *  - reserveSeatIfAvailable(...) does an atomic conditional update:
 *      condition => maxSeats exists AND (currentEnrolled not exists OR currentEnrolled < maxSeats)
 *      update => increment currentEnrolled by 1 (using if_not_exists for safe initialization)
 *
 *  - releaseSeat(...) decrements currentEnrolled if > 0
 */
public class CourseDao {
    private final DynamoDbClient client;
    private final String tableName = "Course";

    public CourseDao(DynamoDbClient client) {
        this.client = client;
    }

    public Course getCourse(String courseId) {
        try {
            GetItemRequest req = GetItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of("courseId", AttributeValue.builder().s(courseId).build()))
                    .build();
            GetItemResponse res = client.getItem(req);
            if (!res.hasItem()) return null;
            Map<String, AttributeValue> item = res.item();
            Course c = Course.fromItem(item);
            return c;
        } catch (Exception e) {
            System.err.println("Error getCourse: " + e.getMessage());
            return null;
        }
    }

    public List<Course> listAllCourses() {
        try {
            ScanRequest req = ScanRequest.builder().tableName(tableName).build();
            ScanResponse res = client.scan(req);
            List<Course> list = new ArrayList<>();
            for (var item : res.items()) {
                Course c = Course.fromItem(item);
                if (c != null) list.add(c);
            }
            return list;
        } catch (Exception e) {
            System.err.println("Error listing courses: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Atomically reserve a seat if currentEnrolled < maxSeats.
     * Returns true if reserved (currentEnrolled incremented), false if full or error.
     */
    public boolean reserveSeatIfAvailable(String courseId) {
        try {
            Map<String, AttributeValue> key = Map.of("courseId", AttributeValue.builder().s(courseId).build());

            // Condition: maxSeats must exist AND (currentEnrolled not exists OR currentEnrolled < maxSeats)
            String condition = "attribute_exists(maxSeats) AND (attribute_not_exists(currentEnrolled) OR currentEnrolled < maxSeats)";

            // Update: increment currentEnrolled (initialize to 0 if not exists)
            String updateExpression = "SET currentEnrolled = if_not_exists(currentEnrolled, :zero) + :one";

            UpdateItemRequest req = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression(updateExpression)
                    .conditionExpression(condition)
                    .expressionAttributeValues(Map.of(
                            ":one", AttributeValue.builder().n("1").build(),
                            ":zero", AttributeValue.builder().n("0").build()
                    ))
                    .build();

            client.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException ccfe) {
            // Condition failed → course full or missing maxSeats
            return false;
        } catch (Exception e) {
            System.err.println("Error reserving seat: " + e.getMessage());
            return false;
        }
    }

    /**
     * Release a seat (decrement currentEnrolled) if currentEnrolled > 0.
     * Returns true if decremented, false if it was already 0 or error.
     */
    public boolean releaseSeat(String courseId) {
        try {
            Map<String, AttributeValue> key = Map.of("courseId", AttributeValue.builder().s(courseId).build());

            // Condition: currentEnrolled > 0
            String condition = "attribute_exists(currentEnrolled) AND currentEnrolled > :zero";

            String updateExpression = "SET currentEnrolled = currentEnrolled - :one";

            UpdateItemRequest req = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression(updateExpression)
                    .conditionExpression(condition)
                    .expressionAttributeValues(Map.of(
                            ":one", AttributeValue.builder().n("1").build(),
                            ":zero", AttributeValue.builder().n("0").build()
                    ))
                    .build();

            client.updateItem(req);
            return true;
        } catch (ConditionalCheckFailedException ccfe) {
            // Nothing to decrement
            return false;
        } catch (Exception e) {
            System.err.println("Error releasing seat: " + e.getMessage());
            return false;
        }
    }

    /**
     * Forcefully increment maxSeats (e.g., admin action).
     */
    public boolean incrementMaxSeats(String courseId, int by) {
        if (by <= 0) return false;
        try {
            Map<String, AttributeValue> key = Map.of("courseId", AttributeValue.builder().s(courseId).build());
            UpdateItemRequest req = UpdateItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .updateExpression("SET maxSeats = if_not_exists(maxSeats, :zero) + :inc")
                    .expressionAttributeValues(Map.of(
                            ":inc", AttributeValue.builder().n(String.valueOf(by)).build(),
                            ":zero", AttributeValue.builder().n("0").build()
                    ))
                    .build();
            client.updateItem(req);
            return true;
        } catch (Exception e) {
            System.err.println("Error incrementing maxSeats: " + e.getMessage());
            return false;
        }
    }

    /**
     * Persist Course object to table (overwrite).
     */
    public void putCourseForUpdate(Course course) {
        try {
            Map<String, AttributeValue> item = course.toItem();
            PutItemRequest req = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();
            client.putItem(req);
        } catch (Exception e) {
            System.err.println("Error putCourseForUpdate: " + e.getMessage());
        }
    }

    /**
     * Create a new Course entry, failing if the courseId already exists.
     * Returns true if created, false if already exists or error.
     */
    public boolean putCourse(Course course) {
        if (course == null || course.courseId == null || course.courseId.trim().isEmpty()) {
            System.err.println("putCourse: invalid course object");
            return false;
        }
        try {
            Map<String, AttributeValue> item = course.toItem();

            PutItemRequest req = PutItemRequest.builder()
                    .tableName(tableName)
                    // Conditional: only put when courseId does not already exist
                    .conditionExpression("attribute_not_exists(courseId)")
                    .item(item)
                    .build();

            client.putItem(req);
            return true;
        } catch (ConditionalCheckFailedException ccfe) {
            // Course already exists
            return false;
        } catch (Exception e) {
            System.err.println("Error putCourse: " + e.getMessage());
            return false;
        }
    }

    /**
     * Delete a course from the Course table
     */
    public boolean deleteCourse(String courseId) {
        try {
            Map<String, AttributeValue> key = Map.of("courseId", AttributeValue.builder().s(courseId).build());
            DeleteItemRequest req = DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build();
            client.deleteItem(req);
            return true;
        } catch (Exception e) {
            System.err.println("Error deleting course: " + e.getMessage());
            return false;
        }
    }
}

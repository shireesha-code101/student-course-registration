package org.example.registration.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class Course {
    public String courseId;
    public String title;
    public int maxSeats;
    public int currentEnrolled;

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("courseId", AttributeValue.builder().s(courseId).build());
        item.put("title", AttributeValue.builder().s(title == null ? "" : title).build());
        item.put("maxSeats", AttributeValue.builder().n(String.valueOf(maxSeats)).build());
        item.put("currentEnrolled", AttributeValue.builder().n(String.valueOf(currentEnrolled)).build());
        return item;
    }

    public static Course fromItem(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) return null;

        // courseId
        var idAttr = item.get("courseId");
        if (idAttr == null || idAttr.s() == null) {
            System.err.println("⚠ Course record missing courseId: " + item);
            return null;
        }

        var titleAttr = item.get("title");
        if (titleAttr == null || titleAttr.s() == null) titleAttr = item.get("courseName");
        if (titleAttr == null || titleAttr.s() == null) {
            System.err.println("⚠ Course record missing title/courseName: " + item);
            return null;
        }

        // maxSeats (must be numeric N)
        var maxAttr = item.get("maxSeats");
        if (maxAttr == null || maxAttr.n() == null) {
            System.err.println("⚠ Course record missing maxSeats: " + item);
            return null;
        }

        // currentEnrolled: try canonical 'currentEnrolled' then legacy 'currentEnrolledCount'
        var currAttr = item.get("currentEnrolled");
        if (currAttr == null || currAttr.n() == null) currAttr = item.get("currentEnrolledCount");

        if (currAttr == null || currAttr.n() == null) {
            System.err.println("⚠ Course record missing currentEnrolled/currentEnrolledCount: " + item);
            return null;
        }

        Course c = new Course();
        c.courseId = idAttr.s();
        c.title = titleAttr.s();

        try {
            c.maxSeats = Integer.parseInt(maxAttr.n());
        } catch (NumberFormatException e) {
            System.err.println("⚠ Invalid maxSeats in Course record: " + item);
            return null;
        }

        try {
            c.currentEnrolled = Integer.parseInt(currAttr.n());
        } catch (NumberFormatException e) {
            System.err.println("⚠ Invalid currentEnrolled in Course record: " + item);
            return null;
        }

        return c;
    }

    @Override
    public String toString() {
        return courseId + " - " + title + " (" + currentEnrolled + "/" + maxSeats + ")";
    }
}

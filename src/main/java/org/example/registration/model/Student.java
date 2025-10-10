package org.example.registration.model;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

public class Student {
    public String studentId;
    public String name;
    public String email;
    public String passwordHash;

    public Map<String, AttributeValue> toItem() {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("studentId", AttributeValue.builder().s(studentId).build());
        item.put("name", AttributeValue.builder().s(name == null ? "" : name).build());
        item.put("email", AttributeValue.builder().s(email == null ? "" : email.toLowerCase()).build());
        item.put("passwordHash", AttributeValue.builder().s(passwordHash == null ? "" : passwordHash).build());
        return item;
    }

    public static Student fromItem(Map<String, AttributeValue> item) {
        if (item == null || item.isEmpty()) return null;
        Student s = new Student();
        var id = item.get("studentId");
        if (id != null && id.s() != null) s.studentId = id.s();
        s.name = item.getOrDefault("name", AttributeValue.builder().s("").build()).s();
        s.email = item.getOrDefault("email", AttributeValue.builder().s("").build()).s();
        s.passwordHash = item.getOrDefault("passwordHash", AttributeValue.builder().s("").build()).s();
        return s;
    }
}

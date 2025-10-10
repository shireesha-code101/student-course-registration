package org.example.registration.service;

import org.example.registration.dao.CourseDao;
import org.example.registration.dao.DropDao;
import org.example.registration.dao.EnrollmentDao;
import org.example.registration.dao.WaitlistDao;
import org.example.registration.model.Course;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.Map;

/**
 * AdminService handles admin operations:
 *  - View, Add, Update, Delete courses
 *  - Promote waitlisted students
 *  - View waitlists and drop history
 */
public class AdminService {
    private final DynamoDbClient client;
    private final CourseDao courseDao;
    private final EnrollmentDao enrollmentDao;
    private final WaitlistDao waitlistDao;
    private final DropDao dropDao;

    public AdminService(DynamoDbClient client) {
        this.client = client;
        this.courseDao = new CourseDao(client);
        this.enrollmentDao = new EnrollmentDao(client);
        this.waitlistDao = new WaitlistDao(client);
        this.dropDao = new DropDao(client);
    }

    // ------------------------------------------------------
    // 1️⃣  LIST ALL COURSES
    // ------------------------------------------------------
    public java.util.List<Course> listAllCourses() {
        try {
            return courseDao.listAllCourses();
        } catch (Exception e) {
            System.err.println("Error listing courses: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    // ------------------------------------------------------
    // 2️⃣  ADD COURSE (no duplicates allowed)
    // ------------------------------------------------------
    public String addCourse(String courseId, String title, int maxSeats) {
        try {
            if (courseId == null || courseId.trim().isEmpty() ||
                    title == null || title.trim().isEmpty() || maxSeats <= 0) {
                return "❌ Invalid input. Please provide valid course details.";
            }

            // Normalize courseId (trim + uppercase to avoid accidental duplicates like "cse101" vs "CSE101")
            courseId = courseId.trim().toUpperCase();
            title = title.trim();

            Course existing = courseDao.getCourse(courseId);
            if (existing != null) {
                return "⚠ Course ID already exists: " + courseId;
            }

            Course c = new Course();
            c.courseId = courseId;
            c.title = title;
            c.maxSeats = maxSeats;
            c.currentEnrolled = 0;

            boolean created = courseDao.putCourse(c);
            if (!created) {
                // If putCourse returned false, it means the course likely exists (or a condition failed)
                return "⚠ Course ID already exists or could not be created: " + courseId;
            }

            return "✅ Course added successfully: " + title + " (" + courseId + ")";
        } catch (Exception e) {
            System.err.println("Error adding course: " + e.getMessage());
            return "❌ Error adding course: " + e.getMessage();
        }
    }

    // ------------------------------------------------------
    // 3️⃣  UPDATE COURSE SEATS (defensive: only update if course truly exists)
    // ------------------------------------------------------
    public String updateCourseSeats(String courseId, int newSeats) {
        try {
            if (courseId == null || courseId.trim().isEmpty()) {
                return "❌ Invalid Course ID.";
            }
            courseId = courseId.trim();

            if (newSeats <= 0) {
                return "❌ Seats must be a positive integer.";
            }

            Course c = courseDao.getCourse(courseId);

            if (c == null || c.courseId == null || !courseId.equals(c.courseId)) {
                return "❌ Course not found: " + courseId;
            }

            if (newSeats < c.currentEnrolled) {
                return "⚠ Cannot reduce seats below current enrollment count (" + c.currentEnrolled + ").";
            }

            c.maxSeats = newSeats;
            courseDao.putCourseForUpdate(c);
            return "✅ Seats updated successfully for " + courseId;
        } catch (Exception e) {
            System.err.println("Error updating seats: " + e.getMessage());
            return "❌ Error updating seats: " + e.getMessage();
        }
    }


    // ------------------------------------------------------
    // 4️⃣  PROMOTE WAITLISTED STUDENT
    // ------------------------------------------------------
    public String promoteWaitlistedStudent(String courseId) {
        try {
            Course c = courseDao.getCourse(courseId);
            if (c == null) return "❌ Course not found: " + courseId;

            // Pop the earliest waitlisted student
            String next = waitlistDao.popFirstWaitlistedStudent(courseId);
            if (next == null) return "⚠ No students on waitlist for " + courseId;

            // Try to reserve a seat for them first (atomic check & increment)
            boolean reserved = courseDao.reserveSeatIfAvailable(courseId);
            if (!reserved) {
                // couldn't reserve, put them back to waitlist and report
                waitlistDao.addToWaitlist(courseId, next, java.util.Collections.emptyMap());
                return "⚠ Promotion failed: no seats available for " + courseId + ". Student requeued.";
            }

            // Seat reserved successfully — create enrollment
            enrollmentDao.putEnrollment(next, courseId, "ENROLLED");
            dropDao.recordDrop(next, courseId, "SYSTEM", "Promoted from waitlist by admin");
            return "✅ Promoted " + next + " from waitlist to enrolled.";
        } catch (Exception e) {
            System.err.println("Error promoting waitlisted student: " + e.getMessage());
            return "❌ Error promoting waitlisted student: " + e.getMessage();
        }
    }

    // ------------------------------------------------------
    // 5️⃣  DELETE COURSE (safe + full cleanup)
    // ------------------------------------------------------
    public String deleteCourse(String courseId) {
        if (courseId == null || courseId.trim().isEmpty()) {
            return "❌ Invalid Course ID.";
        }
        courseId = courseId.trim();

        try {
            Course c = courseDao.getCourse(courseId);
            if (c == null) {
                return "❌ Course not found. Please check the Course ID.";
            }

            // Remove all enrollments for this course
            try {
                var scanReq = software.amazon.awssdk.services.dynamodb.model.ScanRequest.builder()
                        .tableName("Enrollment")
                        .filterExpression("courseId = :cid")
                        .expressionAttributeValues(Map.of(":cid",
                                AttributeValue.builder().s(courseId).build()))
                        .build();

                var res = client.scan(scanReq);
                for (var item : res.items()) {
                    if (item.containsKey("studentId")) {
                        String studentId = item.get("studentId").s();
                        enrollmentDao.deleteEnrollment(studentId, courseId);
                        try {
                            dropDao.recordDrop(studentId, courseId, "ADMIN", "Course deleted by admin");
                        } catch (Exception ex) {
                            System.err.println("Warning: failed to record drop for " + studentId + ": " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: error cleaning enrollments: " + e.getMessage());
            }

            // Remove waitlist entries
            try {
                var scanWait = software.amazon.awssdk.services.dynamodb.model.ScanRequest.builder()
                        .tableName("Waitlist")
                        .filterExpression("courseId = :cid")
                        .expressionAttributeValues(Map.of(":cid",
                                AttributeValue.builder().s(courseId).build()))
                        .build();

                var waitRes = client.scan(scanWait);
                for (var item : waitRes.items()) {
                    if (item.containsKey("createdAt")) {
                        try {
                            String createdAt = item.get("createdAt").s();
                            waitlistDao.removeWaitlistEntry(courseId, createdAt);
                        } catch (Exception ex) {
                            System.err.println("Warning: failed to remove waitlist entry: " + ex.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: error cleaning waitlist: " + e.getMessage());
            }

            // Delete the course record from Course table
            courseDao.deleteCourse(courseId);

            return "✅ Course " + courseId + " deleted successfully, with enrollments & waitlist cleaned up.";

        } catch (Exception e) {
            System.err.println("Error deleting course: " + e.getMessage());
            return "❌ Error deleting course: " + e.getMessage();
        }
    }

    // ------------------------------------------------------
    // 6️⃣  LIST WAITLISTED STUDENTS (only for existing courses)
    // ------------------------------------------------------
    public String listWaitlistedStudents(String courseId) {
        try {
            if (courseId == null || courseId.trim().isEmpty()) {
                return "❌ Invalid Course ID.";
            }
            courseId = courseId.trim();

            Course c = courseDao.getCourse(courseId);
            if (c == null) {
                return "⚠ No such course found: " + courseId;
            }

            var waitlists = waitlistDao.getWaitlistsByCourse(courseId);
            if (waitlists == null || waitlists.isEmpty()) {
                return "⚠ No students on waitlist for " + courseId;
            }

            StringBuilder sb = new StringBuilder("📋 Waitlisted students for " + courseId + ":\n");
            for (var item : waitlists) {
                String sid = item.containsKey("studentId") ? item.get("studentId").s() : "Unknown";
                sb.append(" - ").append(sid).append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            System.err.println("Error listing waitlisted students: " + e.getMessage());
            return "❌ Error listing waitlisted students: " + e.getMessage();
        }
    }


    // ------------------------------------------------------
    // 7️⃣  VIEW DROP HISTORY (only for courses that exist)
    // ------------------------------------------------------
    public String listDropHistoryForCourse(String courseId) {
        try {
            if (courseId == null || courseId.trim().isEmpty()) {
                return "❌ Invalid Course ID.";
            }
            courseId = courseId.trim();

            Course c = courseDao.getCourse(courseId);
            if (c == null) {
                return "⚠ No such course found: " + courseId;
            }

            var history = dropDao.getDropHistoryByCourse(courseId);
            if (history == null || history.isEmpty()) {
                return "⚠ No drop history found for " + courseId;
            }

            StringBuilder sb = new StringBuilder("📜 Drop History for " + courseId + ":\n");
            for (var item : history) {
                sb.append(" - ").append(item).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            System.err.println("Error fetching drop history: " + e.getMessage());
            return "❌ Error fetching drop history: " + e.getMessage();
        }
    }
}

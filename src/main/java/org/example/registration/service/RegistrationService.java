package org.example.registration.service;

import org.example.registration.dao.*;
import org.example.registration.model.Course;
import org.example.registration.model.Student;
import org.example.registration.util.ValidationUtil;
import org.mindrot.jbcrypt.BCrypt;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.*;

public class RegistrationService {
    private final DynamoDbClient client;
    private final StudentDao studentDao;
    private final EmailIndexDao emailIndexDao;
    private final CourseDao courseDao;
    private final EnrollmentDao enrollmentDao;
    private final WaitlistDao waitlistDao;
    private final DropDao dropDao;

    public RegistrationService(DynamoDbClient client) {
        this(
                client,
                new StudentDao(client),
                new EmailIndexDao(client),
                new CourseDao(client),
                new EnrollmentDao(client),
                new WaitlistDao(client),
                new DropDao(client)
        );
    }

    public RegistrationService(
            DynamoDbClient client,
            StudentDao studentDao,
            EmailIndexDao emailIndexDao,
            CourseDao courseDao,
            EnrollmentDao enrollmentDao,
            WaitlistDao waitlistDao,
            DropDao dropDao
    ) {
        this.client = client;
        this.studentDao = studentDao;
        this.emailIndexDao = emailIndexDao;
        this.courseDao = courseDao;
        this.enrollmentDao = enrollmentDao;
        this.waitlistDao = waitlistDao;
        this.dropDao = dropDao;
    }

    // ---------------- SIGNUP ----------------
    public String signup(String studentId, String name, String email, String password) {
        if (studentId == null || name == null || email == null || password == null)
            return "❌ All fields are required.";

        String normEmail = ValidationUtil.normalizeEmail(email);

        if (!ValidationUtil.isValidStudentId(studentId))
            return "❌ Invalid student ID format.";
        if (!ValidationUtil.isValidEmail(normEmail))
            return "❌ Invalid email format.";
        if (!ValidationUtil.isValidPassword(password))
            return "❌ Weak password. Must include uppercase, lowercase, special character & ≥8 chars.";

        try {
            if (isStudentIdExists(studentId)) return "❌ Student ID already exists.";
            if (isEmailExists(normEmail)) return "❌ Email already exists.";
        } catch (RuntimeException e) {
            return "❌ Database error while checking duplicates: " + e.getMessage();
        }

        Student s = new Student();
        s.studentId = studentId;
        s.name = name;
        s.email = normEmail;
        s.passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());

        try {
            studentDao.putStudent(s);
            emailIndexDao.putEmail(normEmail, studentId);
            return "✅ Signed up successfully.";
        } catch (ConditionalCheckFailedException e) {
            return "❌ Student ID or Email already exists.";
        } catch (Exception e) {
            System.err.println("Signup unexpected error: " + e.getMessage());
            return "❌ Unexpected error: " + e.getMessage();
        }
    }

    // ---------------- LOGIN ----------------
    public boolean login(String studentId, String password) {
        try {
            Student s = studentDao.getStudent(studentId);
            if (s == null) return false;
            return BCrypt.checkpw(password, s.passwordHash);
        } catch (Exception e) {
            System.err.println("Login error: " + e.getMessage());
            return false;
        }
    }

    // ---------------- RESET PASSWORD ----------------
    public String resetPassword(String studentId, String newPassword) {
        try {
            if (studentId == null || studentId.isBlank()) {
                return "❌ Student ID cannot be empty.";
            }
            if (newPassword == null || newPassword.length() < 4) {
                return "❌ Password must be at least 4 characters long.";
            }
            if (!isStudentIdExists(studentId)) {
                return "❌ Student not found. Please sign up first.";
            }
            String hashed = BCrypt.hashpw(newPassword, BCrypt.gensalt());
            studentDao.updatePassword(studentId, hashed);
            return "✅ Password reset successfully.";
        } catch (Exception e) {
            System.err.println("Error resetting password: " + e.getMessage());
            return "❌ Failed to reset password: " + e.getMessage();
        }
    }

    // ---------------- LIST COURSES ----------------
    public List<Course> listCourses() {
        try {
            return courseDao.listAllCourses();
        } catch (Exception e) {
            System.err.println("Error listing courses: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // Helper: check Enrollment table for (studentId, courseId)
    private boolean isStudentEnrolled(String studentId, String courseId) {
        try {
            GetItemRequest req = GetItemRequest.builder()
                    .tableName("Enrollment")
                    .key(Map.of(
                            "studentId", AttributeValue.builder().s(studentId).build(),
                            "courseId", AttributeValue.builder().s(courseId).build()
                    ))
                    .consistentRead(true)
                    .build();
            return client.getItem(req).hasItem();
        } catch (Exception e) {
            System.err.println("Error checking enrollment: " + e.getMessage());
            return false;
        }
    }

    // ---------------- ENROLL ----------------
    public String enroll(String studentId, String courseId, boolean waitlistConsent) {
        try {
            if (studentId == null || studentId.trim().isEmpty())
                return "⚠ Please login first.";
            studentId = studentId.trim();

            if (courseId == null || courseId.trim().isEmpty())
                return "❌ Invalid course ID.";
            courseId = courseId.trim();

            if (!isStudentIdExists(studentId))
                return "❌ Student not found. Please sign up first.";

            if (isStudentEnrolled(studentId, courseId))
                return "❌ You are already enrolled in this course.";

            if (waitlistDao.isStudentOnWaitlist(courseId, studentId))
                return "❌ You are already on the waitlist for this course.";

            Course c = courseDao.getCourse(courseId);
            if (c == null || c.courseId == null || !courseId.equals(c.courseId))
                return "❌ Course not found.";

            boolean reserved = courseDao.reserveSeatIfAvailable(courseId);
            if (reserved) {
                enrollmentDao.putEnrollment(studentId, courseId, "ENROLLED");
                return "✅ Enrolled successfully!";
            } else {
                if (!waitlistConsent)
                    return "⚠ Course full. Would you like to join the waitlist? (Y/N)";

                Student s = studentDao.getStudent(studentId);
                Map<String, String> extra = s == null ? Collections.emptyMap() :
                        Map.of("name", s.name == null ? "" : s.name, "email", s.email == null ? "" : s.email);

                waitlistDao.addToWaitlist(courseId, studentId, extra);
                return "🕓 Course full. Added to waitlist.";
            }
        } catch (Exception e) {
            System.err.println("Enrollment error: " + e.getMessage());
            return "❌ Enrollment error: " + e.getMessage();
        }
    }

    // ---------------- DROP ----------------
    public String drop(String studentId, String courseId) {
        try {
            if (studentId == null || studentId.trim().isEmpty())
                return "⚠ Please login first.";
            studentId = studentId.trim();

            if (!isStudentIdExists(studentId))
                return "❌ Student record not found. Please sign up first.";

            if (courseId == null || courseId.trim().isEmpty())
                return "❌ Invalid course ID.";
            courseId = courseId.trim();

            Course course = courseDao.getCourse(courseId);
            if (course == null)
                return "❌ Course not found. Please check the Course ID.";

            if (dropDao.hasDroppedBefore(studentId, courseId)) {
                return "⚠ You have already dropped this course earlier.";
            }

            boolean currentlyEnrolled = isStudentEnrolled(studentId, courseId);
            if (currentlyEnrolled) {
                boolean deletedFromEnrollment = enrollmentDao.deleteEnrollment(studentId, courseId);
                if (deletedFromEnrollment) {
                    boolean recorded = dropDao.recordDrop(studentId, courseId, "STUDENT", "Dropped from enrolled course");
                    if (!recorded) {
                        System.err.println("Error: recordDrop failed for " + studentId + " / " + courseId);
                        return "❌ Dropped from course but failed to persist drop record. Please contact admin.";
                    }

                    courseDao.releaseSeat(courseId);

                    String promoted = waitlistDao.popFirstWaitlistedStudent(courseId);
                    if (promoted != null) {
                        boolean reservedForPromoted = courseDao.reserveSeatIfAvailable(courseId);
                        if (reservedForPromoted) {
                            enrollmentDao.putEnrollment(promoted, courseId, "ENROLLED");
                            dropDao.recordDrop(promoted, courseId, "SYSTEM", "Promoted from waitlist after drop");
                            return "✅ Dropped from course. Promoted " + promoted + " from waitlist.";
                        } else {
                            waitlistDao.addToWaitlist(courseId, promoted, Collections.emptyMap());
                            return "✅ Dropped from course. (Promotion skipped due to concurrency.)";
                        }
                    }
                    return "✅ Dropped from course.";
                } else {
                    return "❌ Could not remove enrollment due to concurrency. Please try again.";
                }
            }

            boolean removedFromWaitlist = waitlistDao.removeAllWaitlistEntries(courseId, studentId);
            if (removedFromWaitlist) {
                dropDao.recordDrop(studentId, courseId, "STUDENT", "Removed from waitlist by student");
                return "✅ Dropped from waitlist.";
            }

            return "⚠ You are not enrolled or waitlisted for this course.";
        } catch (Exception e) {
            System.err.println("Drop error: " + e.getMessage());
            return "❌ Drop error: " + e.getMessage();
        }
    }

    // ---------------- MY COURSES (Enhanced: includes titles) ----------------
    public List<String> getMyCourses(String studentId) {
        List<String> list = new ArrayList<>();
        try {
            var scanReq = software.amazon.awssdk.services.dynamodb.model.ScanRequest.builder()
                    .tableName("Enrollment")
                    .filterExpression("studentId = :sid")
                    .expressionAttributeValues(Map.of(":sid", AttributeValue.builder().s(studentId).build()))
                    .build();

            var res = client.scan(scanReq);
            for (var item : res.items()) {
                String cid = item.get("courseId").s();
                String status = item.get("status").s();

                Course course = courseDao.getCourse(cid);
                String title = (course != null && course.title != null) ? course.title : "(Unknown Title)";
                list.add(cid + " - " + title + " (" + status + ")");
            }

            var waitlists = waitlistDao.getWaitlistsByStudent(studentId, 0);
            if (waitlists != null) {
                for (var w : waitlists) {
                    if (w.containsKey("courseId")) {
                        String cid = w.get("courseId").s();
                        boolean already = list.stream().anyMatch(s -> s.startsWith(cid + " "));
                        if (!already) {
                            Course course = courseDao.getCourse(cid);
                            String title = (course != null && course.title != null) ? course.title : "(Unknown Title)";
                            list.add(cid + " - " + title + " (WAITLIST)");
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching student's courses: " + e.getMessage());
        }
        return list;
    }

    // ---------------- DEBUG HELPER ----------------
    public void debugPrintDrops(String studentId, String courseId) {
        try {
            dropDao.debugFindDrops(studentId, courseId);
        } catch (Exception e) {
            System.err.println("debugPrintDrops error: " + e.getMessage());
        }
    }

    // ---------------- HELPERS ----------------
    public boolean isStudentIdExists(String studentId) {
        try {
            var req = GetItemRequest.builder()
                    .tableName("Student")
                    .key(Map.of("studentId", AttributeValue.builder().s(studentId).build()))
                    .build();
            return client.getItem(req).hasItem();
        } catch (Exception e) {
            System.err.println("isStudentIdExists error: " + e.getMessage());
            return false;
        }
    }

    public boolean isEmailExists(String email) {
        try {
            var req = GetItemRequest.builder()
                    .tableName("EmailIndex")
                    .key(Map.of("email", AttributeValue.builder().s(email.toLowerCase()).build()))
                    .build();
            return client.getItem(req).hasItem();
        } catch (Exception e) {
            System.err.println("isEmailExists error: " + e.getMessage());
            return false;
        }
    }
}

package org.example.registration.service;

import org.example.registration.dao.*;
import org.example.registration.model.Course;
import org.example.registration.model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegistrationServiceTest {

    @Mock DynamoDbClient client;
    @Mock StudentDao studentDao;
    @Mock EmailIndexDao emailIndexDao;
    @Mock CourseDao courseDao;
    @Mock EnrollmentDao enrollmentDao;
    @Mock WaitlistDao waitlistDao;
    @Mock DropDao dropDao;

    RegistrationService service;

    private static GetItemResponse emptyItem() {
        return GetItemResponse.builder().item(Map.of()).build();
    }

    private static GetItemResponse nonEmptyItem() {
        return GetItemResponse.builder()
                .item(Map.of("pk", AttributeValue.builder().s("X").build()))
                .build();
    }

    @BeforeEach
    void setUp() {
        service = new RegistrationService(
                client, studentDao, emailIndexDao, courseDao, enrollmentDao, waitlistDao, dropDao
        );
        // By default: “not found / not enrolled”
        when(client.getItem(any(GetItemRequest.class))).thenReturn(emptyItem());
    }

    // ---------- SIGNUP ----------

    @Test
    void signup_success_returnsMessage() {
        // email not taken
        when(emailIndexDao.emailExists("a@b.com")).thenReturn(false);

        String msg = service.signup("S1", "Alice", "a@b.com", "Strong1!");

        assertNotNull(msg);
        assertFalse(msg.isBlank());
    }

    @Test
    void signup_duplicateId_returnsMessage() {
        // pretend id already exists
        when(client.getItem(any(GetItemRequest.class))).thenReturn(nonEmptyItem());

        String msg = service.signup("S1", "Alice", "a@b.com", "Strong1!");

        assertNotNull(msg);
        assertFalse(msg.isBlank());
    }

    // ---------- LOGIN ----------

    @Test
    void login_validatesPassword() {
        Student s = new Student();
        s.studentId = "S9";
        s.email = "u@x.com";
        s.passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw("Strong1!", org.mindrot.jbcrypt.BCrypt.gensalt());
        when(studentDao.getStudent("S9")).thenReturn(s);

        assertTrue(service.login("S9", "Strong1!"));
        assertFalse(service.login("S9", "wrong"));
    }

    // ---------- ENROLL ----------

    @Test
    void enroll_whenSeatAvailable_returnsMessage() {
        Course c = new Course();
        c.courseId = "C1"; c.title = "DSA"; c.maxSeats = 40; c.currentEnrolled = 20;

        when(courseDao.getCourse("C1")).thenReturn(c);
        when(courseDao.reserveSeatIfAvailable("C1")).thenReturn(true);

        String msg = service.enroll("S1", "C1", false);

        assertNotNull(msg);
        assertFalse(msg.isBlank());
    }

    @Test
    void enroll_whenFull_andConsent_returnsMessage() {
        Course c = new Course();
        c.courseId = "C2"; c.title = "OS"; c.maxSeats = 1; c.currentEnrolled = 1;

        when(courseDao.getCourse("C2")).thenReturn(c);
        when(courseDao.reserveSeatIfAvailable("C2")).thenReturn(false);

        Student s = new Student(); s.studentId = "S2"; s.name = "Bob"; s.email = "b@b.com";
        when(studentDao.getStudent("S2")).thenReturn(s);

        String msg = service.enroll("S2", "C2", true);

        assertNotNull(msg);
        assertFalse(msg.isBlank());
    }

    // ---------- DROP ----------

    @Test
    void drop_waitlistedOrNot_returnsMessage() {
        String msg = service.drop("S3", "C3");

        assertNotNull(msg);
        assertFalse(msg.isBlank());
    }
}

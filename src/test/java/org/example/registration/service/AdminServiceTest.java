package org.example.registration.service;

import org.example.registration.dao.*;
import org.example.registration.model.Course;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock DynamoDbClient client;
    @Mock CourseDao courseDao;
    @Mock EnrollmentDao enrollmentDao;
    @Mock WaitlistDao waitlistDao;
    @Mock DropDao dropDao;

    AdminService admin;

    @BeforeEach
    void setUp() {
        admin = new AdminService(client);
        // inject mocks (production ctor creates DAOs internally)
        try {
            var f1 = AdminService.class.getDeclaredField("courseDao");
            f1.setAccessible(true); f1.set(admin, courseDao);
            var f2 = AdminService.class.getDeclaredField("enrollmentDao");
            f2.setAccessible(true); f2.set(admin, enrollmentDao);
            var f3 = AdminService.class.getDeclaredField("waitlistDao");
            f3.setAccessible(true); f3.set(admin, waitlistDao);
            var f4 = AdminService.class.getDeclaredField("dropDao");
            f4.setAccessible(true); f4.set(admin, dropDao);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void listAllCourses_returnsCourses() {
        List<Course> list = List.of(new Course(), new Course());
        when(courseDao.listAllCourses()).thenReturn(list);

        var res = admin.listAllCourses();

        assertEquals(2, res.size());
        verify(courseDao).listAllCourses();
    }

    @Test
    void addCourse_success() {
        when(courseDao.getCourse("C1")).thenReturn(null);
        when(courseDao.putCourse(any())).thenReturn(true);

        String msg = admin.addCourse("C1", "DSA", 50);

        assertTrue(msg.contains("success"));
        verify(courseDao).putCourse(argThat(c ->
                "C1".equals(c.courseId) && "DSA".equals(c.title) && c.maxSeats == 50 && c.currentEnrolled == 0
        ));
    }

    @Test
    void addCourse_duplicateId() {
        when(courseDao.getCourse("C2")).thenReturn(new Course());

        String msg = admin.addCourse("C2", "Algo", 20);

        assertTrue(msg.contains("already exists"));
        verify(courseDao, never()).putCourse(any());
    }

    @Test
    void updateCourseSeats_updatesViaPutCourseForUpdate() {
        Course c = new Course();
        c.courseId = "C3"; c.title = "ML"; c.maxSeats = 30; c.currentEnrolled = 10;
        when(courseDao.getCourse("C3")).thenReturn(c);

        String msg = admin.updateCourseSeats("C3", 40);

        assertTrue(msg.contains("updated"));
        verify(courseDao).putCourseForUpdate(argThat(updated ->
                "C3".equals(updated.courseId) && updated.maxSeats == 40 && updated.currentEnrolled == 10
        ));
    }

    @Test
    void updateCourseSeats_invalidArgs() {
        assertTrue(admin.updateCourseSeats("", -1).contains("Invalid"));
        assertTrue(admin.updateCourseSeats("C3", 0).contains("positive"));
    }

    @Test
    void updateCourseSeats_cannotReduceBelowCurrent() {
        Course c = new Course();
        c.courseId = "C3"; c.title = "ML"; c.maxSeats = 30; c.currentEnrolled = 25;
        when(courseDao.getCourse("C3")).thenReturn(c);

        String msg = admin.updateCourseSeats("C3", 20);

        assertTrue(msg.contains("Cannot reduce seats below current enrollment"));
        verify(courseDao, never()).putCourseForUpdate(any());
    }

    @Test
    void promoteWaitlistedStudent_promotesFirstAndEnrolls() {
        String cid = "C4";
        Course c = new Course(); c.courseId = cid;
        when(courseDao.getCourse(cid)).thenReturn(c);
        when(waitlistDao.popFirstWaitlistedStudent(cid)).thenReturn("S1");
        when(courseDao.reserveSeatIfAvailable(cid)).thenReturn(true);

        String msg = admin.promoteWaitlistedStudent(cid);

        assertTrue(msg.contains("Promoted S1"));
        verify(enrollmentDao).putEnrollment("S1", cid, "ENROLLED");
        verify(dropDao).recordDrop(eq("S1"), eq(cid), eq("SYSTEM"), contains("Promoted"));
    }

    @Test
    void promoteWaitlistedStudent_noWaitlist() {
        when(courseDao.getCourse("C5")).thenReturn(new Course());
        when(waitlistDao.popFirstWaitlistedStudent("C5")).thenReturn(null);

        String msg = admin.promoteWaitlistedStudent("C5");

        assertTrue(msg.contains("No students"));
        verify(enrollmentDao, never()).putEnrollment(anyString(), anyString(), anyString());
    }

    @Test
    void listWaitlistedStudents_formatsIds() {
        String cid = "C6";
        Course c = new Course(); c.courseId = cid;
        when(courseDao.getCourse(cid)).thenReturn(c);

        Map<String, AttributeValue> item = Map.of("studentId", AttributeValue.builder().s("S9").build());
        when(waitlistDao.getWaitlistsByCourse(cid)).thenReturn(List.of(item));

        String msg = admin.listWaitlistedStudents(cid);

        assertTrue(msg.contains("S9"));
    }

    @Test
    void listDropHistoryForCourse_listsLines() {
        String cid = "C7";
        Course c = new Course(); c.courseId = cid;
        when(courseDao.getCourse(cid)).thenReturn(c);
        when(dropDao.getDropHistoryByCourse(cid)).thenReturn(List.of("S1 dropped", "S2 dropped"));

        String msg = admin.listDropHistoryForCourse(cid);

        assertTrue(msg.contains("Drop History"));
        assertTrue(msg.contains("S1 dropped"));
        assertTrue(msg.contains("S2 dropped"));
    }
}

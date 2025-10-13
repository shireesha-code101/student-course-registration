package org.example.registration;

import org.example.registration.config.DynamoDbConfig;
import org.example.registration.service.AdminService;
import org.example.registration.service.RegistrationService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Scanner;

public class Main {
    private static final boolean DEBUG = false;

    public static void main(String[] args) {
        DynamoDbClient client = DynamoDbConfig.createClient();
        RegistrationService service = new RegistrationService(client);
        Scanner sc = new Scanner(System.in);
        String loggedInStudent = null;

        System.out.println("===================================");
        System.out.println("Student Course Registration System");
        System.out.println("===================================");

        while (true) {
            System.out.println("\nMain Menu:");
            System.out.println("1) Sign Up");
            System.out.println("2) Login");
            System.out.println("3) List Courses");
            System.out.println("4) Enroll in Course");
            System.out.println("5) Drop Course");
            System.out.println("6) Admin Login");
            System.out.println("7) Logout");
            System.out.println("8) Forgot Password");     // NEW
            System.out.println("9) My Enrolled Courses"); // NEW
            System.out.print("> ");
            String choice = sc.nextLine().trim();

            switch (choice) {
                case "1": {
                    System.out.print("Student ID: ");
                    String id = sc.nextLine().trim();
                    System.out.print("Name: ");
                    String name = sc.nextLine().trim();
                    System.out.print("Email: ");
                    String email = sc.nextLine().trim();
                    System.out.print("Password: ");
                    String pw = sc.nextLine().trim();
                    System.out.println(service.signup(id, name, email, pw));
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "2": {
                    System.out.print("Student ID: ");
                    String id = sc.nextLine().trim();
                    System.out.print("Password: ");
                    String pw = sc.nextLine().trim();
                    boolean ok = service.login(id, pw);
                    if (ok) {
                        loggedInStudent = id;
                        System.out.println("Logged in successfully.");
                    } else {
                        System.out.println("Login failed. Try again.");
                    }
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "3": {
                    System.out.println("\nAvailable Courses:");
                    service.listCourses().forEach(System.out::println);
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "4": {
                    if (loggedInStudent == null) {
                        System.out.println("Please login first.");
                        break;
                    }
                    System.out.print("Course ID: ");
                    String cid = sc.nextLine().trim();
                    String response = service.enroll(loggedInStudent, cid, false);
                    if (response != null && response.contains("Would you like to join the waitlist")) {
                        System.out.print("Course full. Join waitlist? (Y/N): ");
                        String ans = sc.nextLine().trim().toUpperCase();
                        boolean consent = ans.equals("Y") || ans.equals("YES");
                        System.out.println(service.enroll(loggedInStudent, cid, consent));
                    } else {
                        System.out.println(response);
                    }
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "5": {
                    if (loggedInStudent == null) {
                        System.out.println("Please login first.");
                        break;
                    }
                    System.out.print("Course ID: ");
                    String cid = sc.nextLine().trim();
                    String dropResult = service.drop(loggedInStudent, cid);
                    System.out.println(dropResult);

                    if (DEBUG) {
                        System.out.println("\n--- DEBUG: DropHistory rows for " + loggedInStudent + " / " + cid + " ---");
                        service.debugPrintDrops(loggedInStudent, cid);
                        System.out.println("--- END DEBUG ---");
                    }
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "6": {
                    System.out.print("Admin username: ");
                    String user = sc.nextLine().trim();
                    System.out.print("Password: ");
                    String pw = sc.nextLine().trim();

                    if (user.equalsIgnoreCase("admin") && pw.equals("Admin@123")) {
                        AdminService admin = new AdminService(client);
                        System.out.println("\nAdmin logged in successfully!");

                        while (true) {
                            System.out.println("\nAdmin Menu:");
                            System.out.println("1) View Courses");
                            System.out.println("2) Add Course");
                            System.out.println("3) Update Seats");
                            System.out.println("4) Promote Waitlisted");
                            System.out.println("5) Delete Course");
                            System.out.println("6) View Waitlisted Students");
                            System.out.println("7) View Drop History");
                            System.out.println("8) Logout");
                            System.out.print("> ");
                            String c = sc.nextLine().trim();

                            switch (c) {
                                case "1": {
                                    System.out.println("\nCourses in Database:");
                                    admin.listAllCourses().forEach(System.out::println);
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "2": {
                                    System.out.print("Course ID: ");
                                    String newCid = sc.nextLine().trim().toUpperCase();
                                    System.out.print("Title: ");
                                    String title = sc.nextLine().trim();
                                    System.out.print("Max Seats: ");
                                    String seatsInput = sc.nextLine().trim();
                                    try {
                                        int seats = Integer.parseInt(seatsInput);
                                        if (seats <= 0) {
                                            System.out.println("Max seats must be a positive integer.");
                                        } else {
                                            System.out.println(admin.addCourse(newCid, title, seats));
                                        }
                                    } catch (NumberFormatException nfe) {
                                        System.out.println("Invalid seats number. Please enter a positive integer.");
                                    }
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "3": {
                                    System.out.print("Course ID: ");
                                    String cid2 = sc.nextLine().trim().toUpperCase();
                                    System.out.print("New Seats: ");
                                    String seatsInput = sc.nextLine().trim();
                                    try {
                                        int seats = Integer.parseInt(seatsInput);
                                        if (seats <= 0) {
                                            System.out.println("New seats must be a positive integer.");
                                        } else {
                                            System.out.println(admin.updateCourseSeats(cid2, seats));
                                        }
                                    } catch (NumberFormatException nfe) {
                                        System.out.println("Invalid seats number. Please enter a positive integer.");
                                    }
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "4": {
                                    System.out.print("Course ID: ");
                                    String cid2 = sc.nextLine().trim().toUpperCase();
                                    System.out.println(admin.promoteWaitlistedStudent(cid2));
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "5": {
                                    System.out.print("Course ID: ");
                                    String cid2 = sc.nextLine().trim().toUpperCase();
                                    System.out.println(admin.deleteCourse(cid2));
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "6": {
                                    System.out.print("Course ID: ");
                                    String cid2 = sc.nextLine().trim().toUpperCase();
                                    System.out.println(admin.listWaitlistedStudents(cid2));
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "7": {
                                    System.out.print("Course ID: ");
                                    String cid2 = sc.nextLine().trim().toUpperCase();
                                    System.out.println(admin.listDropHistoryForCourse(cid2));
                                    System.out.println("------------------------------------------------");
                                    break;
                                }
                                case "8": {
                                    System.out.println("Logging out of Admin mode...");
                                    break;
                                }
                                default: {
                                    System.out.println("Invalid choice.");
                                }
                            }
                            if ("8".equals(c)) break;
                        }
                        loggedInStudent = null;
                        System.out.println("Returned to student menu.");
                    } else {
                        System.out.println("Invalid admin credentials.");
                    }
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "7": {
                    System.out.println("Exiting...");
                    sc.close();
                    client.close();
                    return;
                }
                case "8": { // Forgot Password
                    handleForgotPassword(sc, service);
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "9": { // My Enrolled Courses
                    if (loggedInStudent == null) {
                        System.out.println("Please login first.");
                        System.out.println("------------------------------------------------");
                        break;
                    }
                    handleMyCourses(service, loggedInStudent);
                    System.out.println("------------------------------------------------");
                    break;
                }
                default: {
                    System.out.println("Invalid choice. Please try again.");
                }
            }
        }
    }

    // Forgot Password flow
    private static void handleForgotPassword(Scanner sc, RegistrationService service) {
        System.out.print("Enter your Student ID: ");
        String sid = sc.nextLine().trim();

        System.out.print("Enter your new password: ");
        String p1 = sc.nextLine();

        System.out.print("Confirm new password: ");
        String p2 = sc.nextLine();

        if (!p1.equals(p2)) {
            System.out.println("Passwords do not match. Try again.");
            return;
        }

        String result = service.resetPassword(sid, p1);
        System.out.println(result);
    }

    // Show enrolled & waitlisted courses with titles
    private static void handleMyCourses(RegistrationService service, String studentId) {
        var my = service.getMyCourses(studentId);
        System.out.println("\nYour Enrollments:");
        if (my == null || my.isEmpty()) {
            System.out.println("You are not enrolled or waitlisted for any courses.");
            return;
        }
        int i = 1;
        for (String line : my) {
            System.out.printf("%d) %s%n", i++, line);
        }
    }
}

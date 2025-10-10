package org.example.registration;

import org.example.registration.config.DynamoDbConfig;
import org.example.registration.service.AdminService;
import org.example.registration.service.RegistrationService;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.Scanner;

public class Main {
    // Toggle this to true while debugging persistence (DropHistory) — remember to set false for normal runs.
    private static final boolean DEBUG = false;

    public static void main(String[] args) {
        DynamoDbClient client = DynamoDbConfig.createClient();
        RegistrationService service = new RegistrationService(client);
        Scanner sc = new Scanner(System.in);
        String loggedInStudent = null;

        System.out.println("===================================");
        System.out.println("🎓 Student Course Registration System");
        System.out.println("===================================");

        while (true) {
            System.out.println("\nMain Menu:");
            System.out.println("1️⃣  Sign Up");
            System.out.println("2️⃣  Login");
            System.out.println("3️⃣  List Courses");
            System.out.println("4️⃣  Enroll in Course");
            System.out.println("5️⃣  Drop Course");
            System.out.println("6️⃣  Admin Login");
            System.out.println("7️⃣  Logout");
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
                        System.out.println("✅ Logged in successfully.");
                    } else {
                        System.out.println("❌ Login failed. Try again.");
                    }
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "3": {
                    System.out.println("\n📚 Available Courses:");
                    service.listCourses().forEach(System.out::println);
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "4": {
                    if (loggedInStudent == null) {
                        System.out.println("⚠ Please login first.");
                        break;
                    }
                    System.out.print("Course ID: ");
                    String cid = sc.nextLine().trim();
                    // First attempt: ask for waitlist consent if full
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
                        System.out.println("⚠ Please login first.");
                        break;
                    }
                    System.out.print("Course ID: ");
                    String cid = sc.nextLine().trim();
                    String dropResult = service.drop(loggedInStudent, cid);
                    System.out.println(dropResult);

                    // Temporary debug: print DropHistory rows for this student/course if DEBUG enabled
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
                        System.out.println("\n✅ Admin logged in successfully!");

                        while (true) {
                            System.out.println("\nAdmin Menu:");
                            System.out.println("1️⃣  View Courses");
                            System.out.println("2️⃣  Add Course");
                            System.out.println("3️⃣  Update Seats");
                            System.out.println("4️⃣  Promote Waitlisted");
                            System.out.println("5️⃣  Delete Course");
                            System.out.println("6️⃣  View Waitlisted Students");
                            System.out.println("7️⃣  View Drop History");
                            System.out.println("8️⃣  Logout");
                            System.out.print("> ");
                            String c = sc.nextLine().trim();

                            switch (c) {
                                case "1": {
                                    System.out.println("\n📚 Courses in Database:");
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
                                            System.out.println("❌ Max seats must be a positive integer.");
                                        } else {
                                            System.out.println(admin.addCourse(newCid, title, seats));
                                        }
                                    } catch (NumberFormatException nfe) {
                                        System.out.println("❌ Invalid seats number. Please enter a positive integer.");
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
                                            System.out.println("❌ New seats must be a positive integer.");
                                        } else {
                                            System.out.println(admin.updateCourseSeats(cid2, seats));
                                        }
                                    } catch (NumberFormatException nfe) {
                                        System.out.println("❌ Invalid seats number. Please enter a positive integer.");
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
                                    System.out.println("👋 Logging out of Admin mode...");
                                    break;
                                }
                                default: {
                                    System.out.println("❌ Invalid choice.");
                                }
                            }
                            if ("8".equals(c)) break;
                        }
                        // after admin logout, clear the logged in student (safer UI)
                        loggedInStudent = null;
                        System.out.println("🔒 Returned to student menu.");
                    } else {
                        System.out.println("❌ Invalid admin credentials.");
                    }
                    System.out.println("------------------------------------------------");
                    break;
                }
                case "7": {
                    System.out.println("👋 Exiting... Goodbye!");
                    sc.close();
                    client.close();
                    return;
                }
                default: {
                    System.out.println("❌ Invalid choice. Please try again.");
                }
            }
        }
    }
}

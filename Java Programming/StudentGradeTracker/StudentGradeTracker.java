import java.util.ArrayList;
import java.util.Scanner;

/**
 * Student Grade Tracker (console-based)
 * - Stores students and their grades using ArrayLists
 * - Calculates average, highest and lowest scores
 * - Displays a summary report of all students
 */
public class StudentGradeTracker {

    // ---------- Student model ----------
    static class Student {
        private final String id;
        private String name;
        private final ArrayList<Double> grades = new ArrayList<>();

        Student(String id, String name) {
            this.id = id;
            this.name = name;
        }

        String getId() { return id; }
        String getName() { return name; }
        void setName(String name) { this.name = name; }
        ArrayList<Double> getGrades() { return grades; }

        void addGrade(double g) { grades.add(g); }

        boolean hasGrades() { return !grades.isEmpty(); }

        double average() {
            if (grades.isEmpty()) return 0;
            double sum = 0;
            for (double g : grades) sum += g;
            return sum / grades.size();
        }

        double highest() {
            double max = grades.get(0);
            for (double g : grades) if (g > max) max = g;
            return max;
        }

        double lowest() {
            double min = grades.get(0);
            for (double g : grades) if (g < min) min = g;
            return min;
        }

        String letterGrade() {
            double avg = average();
            if (avg >= 90) return "A";
            if (avg >= 80) return "B";
            if (avg >= 70) return "C";
            if (avg >= 60) return "D";
            return "F";
        }
    }

    // ---------- Data & input ----------
    private static final ArrayList<Student> students = new ArrayList<>();
    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("=====================================");
        System.out.println("      STUDENT GRADE TRACKER");
        System.out.println("=====================================");

        boolean running = true;
        while (running) {
            printMenu();
            int choice = readInt("Enter your choice: ", 0, 8);
            System.out.println();
            switch (choice) {
                case 1: addStudent(); break;
                case 2: addGrades(); break;
                case 3: updateGrade(); break;
                case 4: removeStudent(); break;
                case 5: viewStudent(); break;
                case 6: classStatistics(); break;
                case 7: summaryReport(); break;
                case 8: loadSampleData(); break;
                case 0:
                    running = false;
                    System.out.println("Goodbye!");
                    break;
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n--------------- MENU ---------------");
        System.out.println("1. Add a student");
        System.out.println("2. Add grades to a student");
        System.out.println("3. Update a grade");
        System.out.println("4. Remove a student");
        System.out.println("5. View one student's details");
        System.out.println("6. Class statistics (avg / highest / lowest)");
        System.out.println("7. Summary report of all students");
        System.out.println("8. Load sample data");
        System.out.println("0. Exit");
        System.out.println("------------------------------------");
    }

    // ---------- Menu actions ----------
    private static void addStudent() {
        String id = readString("Enter student ID: ");
        if (findStudent(id) != null) {
            System.out.println("A student with that ID already exists.");
            return;
        }
        String name = readString("Enter student name: ");
        students.add(new Student(id, name));
        System.out.println("Student added: " + name + " (" + id + ")");
    }

    private static void addGrades() {
        Student s = selectStudent();
        if (s == null) return;

        System.out.println("Enter grades (0-100) for " + s.getName() + ". Type -1 to stop.");
        while (true) {
            double g = readDouble("  Grade: ", -1, 100);
            if (g == -1) break;
            s.addGrade(g);
        }
        System.out.println("Grades saved. Current average: " + fmt(s.average()));
    }

    private static void updateGrade() {
        Student s = selectStudent();
        if (s == null) return;
        if (!s.hasGrades()) {
            System.out.println("This student has no grades yet.");
            return;
        }
        printGradeList(s);
        int idx = readInt("Which grade number to change? ", 1, s.getGrades().size());
        double newGrade = readDouble("New grade (0-100): ", 0, 100);
        s.getGrades().set(idx - 1, newGrade);
        System.out.println("Grade updated.");
    }

    private static void removeStudent() {
        Student s = selectStudent();
        if (s == null) return;
        students.remove(s);
        System.out.println("Removed " + s.getName() + ".");
    }

    private static void viewStudent() {
        Student s = selectStudent();
        if (s == null) return;

        System.out.println("ID     : " + s.getId());
        System.out.println("Name   : " + s.getName());
        if (!s.hasGrades()) {
            System.out.println("No grades recorded.");
            return;
        }
        printGradeList(s);
        System.out.println("Average: " + fmt(s.average()) + "  (" + s.letterGrade() + ")");
        System.out.println("Highest: " + fmt(s.highest()));
        System.out.println("Lowest : " + fmt(s.lowest()));
    }

    private static void classStatistics() {
        ArrayList<Student> graded = gradedStudents();
        if (graded.isEmpty()) {
            System.out.println("No grades available yet.");
            return;
        }

        double totalSum = 0;
        int totalCount = 0;
        double highest = Double.NEGATIVE_INFINITY, lowest = Double.POSITIVE_INFINITY;
        Student highStudent = null, lowStudent = null;
        Student topAvg = graded.get(0), bottomAvg = graded.get(0);

        for (Student s : graded) {
            for (double g : s.getGrades()) {
                totalSum += g;
                totalCount++;
            }
            if (s.highest() > highest) { highest = s.highest(); highStudent = s; }
            if (s.lowest() < lowest) { lowest = s.lowest(); lowStudent = s; }
            if (s.average() > topAvg.average()) topAvg = s;
            if (s.average() < bottomAvg.average()) bottomAvg = s;
        }

        System.out.println("========== CLASS STATISTICS ==========");
        System.out.println("Students with grades : " + graded.size());
        System.out.println("Total grades entered : " + totalCount);
        System.out.println("Overall class average: " + fmt(totalSum / totalCount));
        System.out.println("Highest single score : " + fmt(highest) + "  (" + highStudent.getName() + ")");
        System.out.println("Lowest single score  : " + fmt(lowest) + "  (" + lowStudent.getName() + ")");
        System.out.println("Top average          : " + fmt(topAvg.average()) + "  (" + topAvg.getName() + ")");
        System.out.println("Lowest average       : " + fmt(bottomAvg.average()) + "  (" + bottomAvg.getName() + ")");
    }

    private static void summaryReport() {
        if (students.isEmpty()) {
            System.out.println("No students to report.");
            return;
        }

        String line = "+--------+----------------------+--------+---------+---------+---------+-------+";
        System.out.println("\n                         STUDENT SUMMARY REPORT");
        System.out.println(line);
        System.out.printf("| %-6s | %-20s | %-6s | %-7s | %-7s | %-7s | %-5s |%n",
                "ID", "Name", "Grades", "Average", "Highest", "Lowest", "Grade");
        System.out.println(line);

        for (Student s : students) {
            if (s.hasGrades()) {
                System.out.printf("| %-6s | %-20s | %-6d | %-7s | %-7s | %-7s | %-5s |%n",
                        trim(s.getId(), 6), trim(s.getName(), 20), s.getGrades().size(),
                        fmt(s.average()), fmt(s.highest()), fmt(s.lowest()), s.letterGrade());
            } else {
                System.out.printf("| %-6s | %-20s | %-6d | %-7s | %-7s | %-7s | %-5s |%n",
                        trim(s.getId(), 6), trim(s.getName(), 20), 0, "-", "-", "-", "-");
            }
        }
        System.out.println(line);

        // Class totals at the bottom of the report
        ArrayList<Student> graded = gradedStudents();
        if (!graded.isEmpty()) {
            double sumOfAverages = 0;
            for (Student s : graded) sumOfAverages += s.average();
            System.out.println("Total students : " + students.size());
            System.out.println("Class average  : " + fmt(sumOfAverages / graded.size()));
        }
    }

    private static void loadSampleData() {
        String[][] data = {
                {"S101", "Aarav Sharma", "88", "92", "79", "95"},
                {"S102", "Priya Verma", "72", "65", "80", "70"},
                {"S103", "Rohan Gupta", "55", "62", "48", "60"},
                {"S104", "Sneha Singh", "98", "94", "100", "97"}
        };
        int added = 0;
        for (String[] row : data) {
            if (findStudent(row[0]) != null) continue;
            Student s = new Student(row[0], row[1]);
            for (int i = 2; i < row.length; i++) s.addGrade(Double.parseDouble(row[i]));
            students.add(s);
            added++;
        }
        System.out.println(added + " sample student(s) loaded.");
    }

    // ---------- Helpers ----------
    private static Student findStudent(String id) {
        for (Student s : students) {
            if (s.getId().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    private static Student selectStudent() {
        if (students.isEmpty()) {
            System.out.println("No students yet. Add one first.");
            return null;
        }
        String id = readString("Enter student ID: ");
        Student s = findStudent(id);
        if (s == null) System.out.println("Student not found.");
        return s;
    }

    private static ArrayList<Student> gradedStudents() {
        ArrayList<Student> list = new ArrayList<>();
        for (Student s : students) if (s.hasGrades()) list.add(s);
        return list;
    }

    private static void printGradeList(Student s) {
        System.out.println("Grades:");
        for (int i = 0; i < s.getGrades().size(); i++) {
            System.out.println("  " + (i + 1) + ". " + fmt(s.getGrades().get(i)));
        }
    }

    private static String fmt(double d) { return String.format("%.2f", d); }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    // ---------- Input validation ----------
    private static String readString(String prompt) {
        while (true) {
            System.out.print(prompt);
            String in = sc.nextLine().trim();
            if (!in.isEmpty()) return in;
            System.out.println("Input cannot be empty.");
        }
    }

    private static int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int v = Integer.parseInt(sc.nextLine().trim());
                if (v >= min && v <= max) return v;
            } catch (NumberFormatException ignored) { }
            System.out.println("Please enter a whole number between " + min + " and " + max + ".");
        }
    }

    private static double readDouble(String prompt, double min, double max) {
        while (true) {
            System.out.print(prompt);
            try {
                double v = Double.parseDouble(sc.nextLine().trim());
                if (v == -1 && min == -1) return v;   // sentinel to stop entering grades
                if (v >= Math.max(min, 0) && v <= max) return v;
            } catch (NumberFormatException ignored) { }
            System.out.println("Please enter a number between 0 and " + (int) max
                    + (min == -1 ? " (or -1 to stop)." : "."));
        }
    }
}

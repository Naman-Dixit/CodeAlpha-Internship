import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Student Grade Tracker (GUI version, Java Swing)
 * - Stores students and grades in ArrayLists
 * - Calculates average, highest and lowest scores
 * - Shows a live table, class statistics and a full summary report
 */
public class StudentGradeTrackerGUI extends JFrame {

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
        ArrayList<Double> getGrades() { return grades; }
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
            if (!hasGrades()) return "-";
            double avg = average();
            if (avg >= 90) return "A";
            if (avg >= 80) return "B";
            if (avg >= 70) return "C";
            if (avg >= 60) return "D";
            return "F";
        }
    }

    // ---------- Data & components ----------
    private final ArrayList<Student> students = new ArrayList<>();
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JTextField idField = new JTextField(8);
    private final JTextField nameField = new JTextField(14);
    private final JTextField gradeField = new JTextField(14);
    private final JLabel statsLabel = new JLabel();

    public StudentGradeTrackerGUI() {
        super("Student Grade Tracker");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        // ----- Top: input panels -----
        JPanel studentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        studentPanel.setBorder(new TitledBorder("Add Student"));
        studentPanel.add(new JLabel("ID:"));
        studentPanel.add(idField);
        studentPanel.add(new JLabel("Name:"));
        studentPanel.add(nameField);
        JButton addStudentBtn = new JButton("Add Student");
        addStudentBtn.addActionListener(e -> addStudent());
        studentPanel.add(addStudentBtn);

        JPanel gradePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        gradePanel.setBorder(new TitledBorder("Add Grades to Selected Student"));
        gradePanel.add(new JLabel("Grade(s) 0-100 (comma separated):"));
        gradePanel.add(gradeField);
        JButton addGradeBtn = new JButton("Add Grade(s)");
        addGradeBtn.addActionListener(e -> addGrades());
        gradePanel.add(addGradeBtn);

        JPanel top = new JPanel(new GridLayout(2, 1));
        top.add(studentPanel);
        top.add(gradePanel);
        add(top, BorderLayout.NORTH);

        // ----- Center: table -----
        String[] cols = {"ID", "Name", "Grades", "Average", "Highest", "Lowest", "Letter"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // ----- Bottom: buttons + stats -----
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 6));
        buttons.add(makeButton("Edit Grade", e -> editGrade()));
        buttons.add(makeButton("Delete Grade", e -> deleteGrade()));
        buttons.add(makeButton("Rename Student", e -> renameStudent()));
        buttons.add(makeButton("Remove Student", e -> removeStudent()));
        buttons.add(makeButton("Summary Report", e -> showReport()));
        buttons.add(makeButton("Load Sample Data", e -> loadSampleData()));

        statsLabel.setBorder(new TitledBorder("Class Statistics"));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(statsLabel, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        refresh(null);
        setSize(900, 560);
        setLocationRelativeTo(null);
    }

    private JButton makeButton(String text, java.awt.event.ActionListener l) {
        JButton b = new JButton(text);
        b.addActionListener(l);
        return b;
    }

    // ---------- Actions ----------
    private void addStudent() {
        String id = idField.getText().trim();
        String name = nameField.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            error("Please enter both an ID and a name.");
            return;
        }
        if (findStudent(id) != null) {
            error("A student with ID '" + id + "' already exists.");
            return;
        }
        Student s = new Student(id, name);
        students.add(s);
        idField.setText("");
        nameField.setText("");
        refresh(s);
        gradeField.requestFocus();
    }

    private void addGrades() {
        Student s = selectedStudent();
        if (s == null) return;

        String text = gradeField.getText().trim();
        if (text.isEmpty()) {
            error("Please enter at least one grade.");
            return;
        }
        ArrayList<Double> parsed = new ArrayList<>();
        for (String part : text.split("[,\\s]+")) {
            if (part.isEmpty()) continue;
            Double g = parseGrade(part);
            if (g == null) {
                error("'" + part + "' is not a valid grade. Use numbers from 0 to 100.");
                return;
            }
            parsed.add(g);
        }
        s.getGrades().addAll(parsed);
        gradeField.setText("");
        refresh(s);
    }

    private void editGrade() {
        Student s = selectedStudent();
        if (s == null) return;
        int idx = chooseGradeIndex(s, "Edit Grade");
        if (idx < 0) return;

        String input = JOptionPane.showInputDialog(this, "New value for grade #" + (idx + 1) + ":",
                fmt(s.getGrades().get(idx)));
        if (input == null) return;
        Double g = parseGrade(input.trim());
        if (g == null) {
            error("Invalid grade. Use a number from 0 to 100.");
            return;
        }
        s.getGrades().set(idx, g);
        refresh(s);
    }

    private void deleteGrade() {
        Student s = selectedStudent();
        if (s == null) return;
        int idx = chooseGradeIndex(s, "Delete Grade");
        if (idx < 0) return;
        s.getGrades().remove(idx);
        refresh(s);
    }

    private void renameStudent() {
        Student s = selectedStudent();
        if (s == null) return;
        String input = JOptionPane.showInputDialog(this, "New name:", s.getName());
        if (input == null || input.trim().isEmpty()) return;
        s.name = input.trim();
        refresh(s);
    }

    private void removeStudent() {
        Student s = selectedStudent();
        if (s == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Remove " + s.getName() + " (" + s.getId() + ")?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            students.remove(s);
            refresh(null);
        }
    }

    private void loadSampleData() {
        String[][] data = {
                {"S101", "Aarav Sharma", "88", "92", "79", "95"},
                {"S102", "Priya Verma", "72", "65", "80", "70"},
                {"S103", "Rohan Gupta", "55", "62", "48", "60"},
                {"S104", "Sneha Singh", "98", "94", "100", "97"}
        };
        for (String[] row : data) {
            if (findStudent(row[0]) != null) continue;
            Student s = new Student(row[0], row[1]);
            for (int i = 2; i < row.length; i++) s.getGrades().add(Double.parseDouble(row[i]));
            students.add(s);
        }
        refresh(null);
    }

    private void showReport() {
        if (students.isEmpty()) {
            error("There are no students to report.");
            return;
        }
        String report = buildReport();

        JTextArea area = new JTextArea(report);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setEditable(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(720, 380));

        Object[] options = {"Save to File...", "Close"};
        int choice = JOptionPane.showOptionDialog(this, scroll, "Summary Report",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[1]);
        if (choice == 0) saveReport(report);
    }

    private void saveReport(String report) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("grade_report.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter fw = new FileWriter(chooser.getSelectedFile())) {
                fw.write(report);
                JOptionPane.showMessageDialog(this, "Report saved.");
            } catch (IOException ex) {
                error("Could not save the file: " + ex.getMessage());
            }
        }
    }

    // ---------- Report & statistics ----------
    private String buildReport() {
        String line = "+--------+----------------------+--------+---------+---------+---------+-------+\n";
        StringBuilder sb = new StringBuilder();
        sb.append("                         STUDENT SUMMARY REPORT\n");
        sb.append(line);
        sb.append(String.format("| %-6s | %-20s | %-6s | %-7s | %-7s | %-7s | %-5s |%n",
                "ID", "Name", "Grades", "Average", "Highest", "Lowest", "Grade"));
        sb.append(line);
        for (Student s : students) {
            if (s.hasGrades()) {
                sb.append(String.format("| %-6s | %-20s | %-6d | %-7s | %-7s | %-7s | %-5s |%n",
                        cut(s.getId(), 6), cut(s.getName(), 20), s.getGrades().size(),
                        fmt(s.average()), fmt(s.highest()), fmt(s.lowest()), s.letterGrade()));
            } else {
                sb.append(String.format("| %-6s | %-20s | %-6d | %-7s | %-7s | %-7s | %-5s |%n",
                        cut(s.getId(), 6), cut(s.getName(), 20), 0, "-", "-", "-", "-"));
            }
        }
        sb.append(line);
        sb.append("Total students : ").append(students.size()).append("\n");

        ArrayList<Student> graded = gradedStudents();
        if (!graded.isEmpty()) {
            double sumAvg = 0;
            for (Student s : graded) sumAvg += s.average();
            sb.append("Class average  : ").append(fmt(sumAvg / graded.size())).append("\n");
        }
        return sb.toString();
    }

    private void updateStats() {
        ArrayList<Student> graded = gradedStudents();
        if (graded.isEmpty()) {
            statsLabel.setText("<html>&nbsp;No grades entered yet.</html>");
            return;
        }
        double sum = 0;
        int count = 0;
        double high = Double.NEGATIVE_INFINITY, low = Double.POSITIVE_INFINITY;
        Student highS = null, lowS = null, topAvg = graded.get(0), bottomAvg = graded.get(0);

        for (Student s : graded) {
            for (double g : s.getGrades()) { sum += g; count++; }
            if (s.highest() > high) { high = s.highest(); highS = s; }
            if (s.lowest() < low) { low = s.lowest(); lowS = s; }
            if (s.average() > topAvg.average()) topAvg = s;
            if (s.average() < bottomAvg.average()) bottomAvg = s;
        }

        statsLabel.setText("<html>&nbsp;Students: " + students.size()
                + " &nbsp;|&nbsp; Grades entered: " + count
                + " &nbsp;|&nbsp; <b>Class average: " + fmt(sum / count) + "</b>"
                + "<br>&nbsp;Highest score: " + fmt(high) + " (" + highS.getName() + ")"
                + " &nbsp;|&nbsp; Lowest score: " + fmt(low) + " (" + lowS.getName() + ")"
                + "<br>&nbsp;Top average: " + fmt(topAvg.average()) + " (" + topAvg.getName() + ")"
                + " &nbsp;|&nbsp; Lowest average: " + fmt(bottomAvg.average()) + " (" + bottomAvg.getName() + ")"
                + "</html>");
    }

    // ---------- Helpers ----------
    /** Rebuilds the table and stats; re-selects the given student if provided. */
    private void refresh(Student toSelect) {
        tableModel.setRowCount(0);
        for (Student s : students) {
            tableModel.addRow(new Object[]{
                    s.getId(), s.getName(), gradesToString(s),
                    s.hasGrades() ? fmt(s.average()) : "-",
                    s.hasGrades() ? fmt(s.highest()) : "-",
                    s.hasGrades() ? fmt(s.lowest()) : "-",
                    s.letterGrade()});
        }
        updateStats();

        if (toSelect != null) {
            int modelRow = students.indexOf(toSelect);
            if (modelRow >= 0) {
                int viewRow = table.convertRowIndexToView(modelRow);
                table.setRowSelectionInterval(viewRow, viewRow);
            }
        }
    }

    private Student selectedStudent() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            error("Please select a student in the table first.");
            return null;
        }
        return students.get(table.convertRowIndexToModel(viewRow));
    }

    /** Lets the user pick one of a student's grades; returns its index or -1. */
    private int chooseGradeIndex(Student s, String title) {
        if (!s.hasGrades()) {
            error(s.getName() + " has no grades yet.");
            return -1;
        }
        String[] options = new String[s.getGrades().size()];
        for (int i = 0; i < options.length; i++) {
            options[i] = "#" + (i + 1) + "  -  " + fmt(s.getGrades().get(i));
        }
        Object picked = JOptionPane.showInputDialog(this, "Select a grade for " + s.getName() + ":",
                title, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (picked == null) return -1;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(picked)) return i;
        }
        return -1;
    }

    private Student findStudent(String id) {
        for (Student s : students) {
            if (s.getId().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    private ArrayList<Student> gradedStudents() {
        ArrayList<Student> list = new ArrayList<>();
        for (Student s : students) if (s.hasGrades()) list.add(s);
        return list;
    }

    private String gradesToString(Student s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.getGrades().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(trimZeros(s.getGrades().get(i)));
        }
        return sb.toString();
    }

    private String trimZeros(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
    }

    private Double parseGrade(String text) {
        try {
            double g = Double.parseDouble(text);
            if (g >= 0 && g <= 100) return g;
        } catch (NumberFormatException ignored) { }
        return null;
    }

    private String fmt(double d) { return String.format("%.2f", d); }

    private String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + ".";
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Notice", JOptionPane.WARNING_MESSAGE);
    }

    // ---------- Entry point ----------
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new StudentGradeTrackerGUI().setVisible(true));
    }
}

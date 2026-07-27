package server;

import common.entities.Role;
import common.entities.User;
import common.network.*;
import server.db.PrincipalGradeRecord;
import server.db.PrincipalReportDAO;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Server-side read-only principal access and statistical reports (scenarios 11-12). */
public class PrincipalReportService {
    private final PrincipalReportDAO dao; private final server.db.PrincipalReadOnlyDAO readOnlyDAO;

    public PrincipalReportService(PrincipalReportDAO dao) { this(dao,new server.db.PrincipalReadOnlyDAO()); }
    public PrincipalReportService(PrincipalReportDAO dao, server.db.PrincipalReadOnlyDAO readOnlyDAO) { this.dao=dao; this.readOnlyDAO=readOnlyDAO; }

    public Message getPrincipalData(User caller) {
        Authorization.requireRole(caller, Role.PRINCIPAL);
        try {
            return new Message(Message.Command.SUCCESS, dao.getCatalog());
        } catch (IllegalStateException e) {
            return new Message(Message.Command.ERROR, e.getMessage());
        }
    }


    public Message getReadOnlyData(User caller) {
        Authorization.requireRole(caller, Role.PRINCIPAL);
        try { return new Message(Message.Command.SUCCESS, readOnlyDAO.load()); }
        catch (IllegalStateException e) { return error(e.getMessage()); }
    }

    /** Exact PDF comparison: filter by teacher/course/student, then group by individual exam sitting. */
    public Message getExamComparisonReport(User caller, Object payload) {
        Authorization.requireRole(caller, Role.PRINCIPAL);
        if (!(payload instanceof ExamComparisonReportRequest)) return error("GET_EXAM_COMPARISON_REPORT requires an ExamComparisonReportRequest.");
        ExamComparisonReportRequest r=(ExamComparisonReportRequest)payload;
        if(r.getFilterType()==null||r.getFilterId()<=0)return error("A valid teacher, course, or student filter is required.");
        Map<Integer,GroupAccumulator> groups=new LinkedHashMap<>();
        for(PrincipalGradeRecord x:dao.getVisibleGradeRecords()){
            boolean match=r.getFilterType()==ReportDimension.TEACHER?x.getTeacherId()==r.getFilterId():r.getFilterType()==ReportDimension.COURSE?x.getCourseId()==r.getFilterId():x.getStudentId()==r.getFilterId();
            if(!match)continue; int key=r.isGroupByRelease()?x.getReleaseId():x.getExamId(); String label=x.getExamTitle()+(r.isGroupByRelease()?" — execution "+x.getReleaseId():"");
            groups.computeIfAbsent(key,k->new GroupAccumulator(k,label)).scores.add(x.getScore());
        }
        List<ReportGroup> out=groups.values().stream().map(GroupAccumulator::toReportGroup).collect(Collectors.toList());
        return new Message(Message.Command.SUCCESS,new PrincipalReport(r.getFilterType(),out));
    }

    public Message getReport(User caller, Object payload) {
        Authorization.requireRole(caller, Role.PRINCIPAL);
        if (!(payload instanceof PrincipalReportRequest)) {
            return error("GET_REPORT requires a PrincipalReportRequest.");
        }

        PrincipalReportRequest request = (PrincipalReportRequest) payload;
        if (request.getDimension() == null) return error("A report dimension is required.");

        Set<Integer> selected = new LinkedHashSet<>();
        for (Integer id : request.getEntityIds()) {
            if (id == null || id <= 0) return error("Report entity IDs must be positive integers.");
            selected.add(id);
        }

        final PrincipalData catalog;
        final List<PrincipalGradeRecord> records;
        try {
            catalog = dao.getCatalog();
            records = dao.getVisibleGradeRecords();
        } catch (IllegalStateException e) {
            return error(e.getMessage());
        }

        List<PrincipalDataItem> availableItems = catalogItems(catalog, request.getDimension());
        Map<Integer, PrincipalDataItem> availableById = availableItems.stream()
                .collect(Collectors.toMap(PrincipalDataItem::getId, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));

        if (!selected.isEmpty()) {
            for (Integer id : selected) {
                if (!availableById.containsKey(id)) {
                    return error(entityLabel(request.getDimension()) + " " + id + " does not exist.");
                }
            }
        }

        // Seed the requested groups before processing grades. This guarantees that valid
        // teachers/courses/students with zero approved attempts still appear in comparisons.
        Map<Integer, GroupAccumulator> grouped = new LinkedHashMap<>();
        Collection<PrincipalDataItem> itemsToInclude = selected.isEmpty()
                ? availableItems
                : selected.stream().map(availableById::get).collect(Collectors.toList());
        for (PrincipalDataItem item : itemsToInclude) {
            grouped.put(item.getId(), new GroupAccumulator(item.getId(), requireName(item.getName(),
                    entityLabel(request.getDimension()), item.getId())));
        }

        for (PrincipalGradeRecord record : records) {
            int id = idFor(record, request.getDimension());
            GroupAccumulator accumulator = grouped.get(id);
            if (accumulator != null) accumulator.scores.add(record.getScore());
        }

        List<ReportGroup> groups = grouped.values().stream()
                .map(GroupAccumulator::toReportGroup)
                .sorted(Comparator.comparing(ReportGroup::getLabel, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(ReportGroup::getEntityId))
                .collect(Collectors.toList());
        return new Message(Message.Command.SUCCESS, new PrincipalReport(request.getDimension(), groups));
    }

    private static List<PrincipalDataItem> catalogItems(PrincipalData catalog, ReportDimension dimension) {
        if (catalog == null) throw new IllegalStateException("Could not load principal report catalog.");
        switch (dimension) {
            case TEACHER: return catalog.getTeachers();
            case COURSE: return catalog.getCourses();
            case STUDENT: return catalog.getStudents();
            default: throw new IllegalArgumentException("Unsupported report dimension.");
        }
    }

    private static String entityLabel(ReportDimension dimension) {
        switch (dimension) {
            case TEACHER: return "Teacher";
            case COURSE: return "Course";
            case STUDENT: return "Student";
            default: return "Entity";
        }
    }

    private static String requireName(String name, String type, int id) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalStateException(type + " " + id + " has no valid display name.");
        }
        return name.trim();
    }

    private static int idFor(PrincipalGradeRecord r, ReportDimension d) {
        switch (d) {
            case TEACHER: return r.getTeacherId();
            case COURSE: return r.getCourseId();
            case STUDENT: return r.getStudentId();
            default: throw new IllegalArgumentException("Unsupported report dimension.");
        }
    }

    private static final class GroupAccumulator {
        private final int id;
        private final String label;
        private final List<Integer> scores = new ArrayList<>();
        private GroupAccumulator(int id, String label) { this.id = id; this.label = label; }

        private ReportGroup toReportGroup() {
            Collections.sort(scores);
            int count = scores.size();
            double mean = scores.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            double median = percentile(scores, 0.5);
            Integer min = count == 0 ? null : scores.get(0);
            Integer max = count == 0 ? null : scores.get(count - 1);
            List<Double> deciles = new ArrayList<>();
            for (int i = 1; i <= 9; i++) deciles.add(percentile(scores, i / 10.0));
            return new ReportGroup(id, label, count, mean, median, min, max, deciles);
        }
    }

    /** Linear-interpolated percentile: index=(n-1)*p. D1..D9 are returned. */
    static double percentile(List<Integer> sorted, double p) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);
        double index = (sorted.size() - 1) * p;
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        double fraction = index - lower;
        return sorted.get(lower) + fraction * (sorted.get(upper) - sorted.get(lower));
    }

    private static Message error(String reason) { return new Message(Message.Command.ERROR, reason); }
}

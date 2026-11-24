package com.researchmatch.controllers;

import com.researchmatch.config.ResearchMatchException;
import com.researchmatch.factory.OpportunityBuilder;
import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.SkillEvidence;
import com.researchmatch.models.StudentProfile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Member 4 JSON API: a thin REST surface over the same {@link ResearchMatchFacade} the web UI uses.
 *
 * <p>Every endpoint delegates to the facade; failures are translated to problem-style JSON with an
 * appropriate status. Business rules never live here.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ResearchMatchFacade facade;
    private final DemoRoleSession session;

    public ApiController(ResearchMatchFacade facade, DemoRoleSession session) {
        this.facade = facade;
        this.session = session;
    }

    // ---------------------------------------------------------------- demo role

    @GetMapping("/session")
    public Map<String, Object> session() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", session.getRole().name());
        StudentProfile student = session.currentStudent();
        body.put("studentId", student == null ? null : student.getId());
        FacultyProfile faculty = session.currentFaculty();
        body.put("facultyId", faculty == null ? null : faculty.getId());
        return body;
    }

    @PostMapping("/session/role")
    public Map<String, Object> switchRole(@RequestParam("role") String role) {
        DemoRoleSession.Role target = DemoRoleSession.Role.valueOf(role.trim().toUpperCase());
        session.setRole(target);
        return Map.of("role", target.name());
    }

    // ---------------------------------------------------------------- discovery + matching

    @GetMapping("/discover")
    public Map<String, Object> discover(@RequestParam(value = "strategy", required = false) String strategy) {
        StudentProfile student = session.currentStudent();
        if (student == null) {
            throw new com.researchmatch.config.ResourceNotFoundException("No demo student available");
        }
        List<MatchResult> ranked = facade.recommend(student.getId(), strategy);
        List<Map<String, Object>> items = new ArrayList<>();
        for (MatchResult match : ranked) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("opportunityId", match.getOpportunityId());
            item.put("totalScore", match.getTotalScore());
            item.put("eligible", match.isEligible());
            item.put("matchedSkills", match.getMatchedSkills());
            item.put("missingSkills", match.getMissingSkills());
            item.put("explanation", match.getExplanationSummary());
            item.put("recommendedPrep", match.getRecommendedPrep());
            items.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("strategy", strategy == null || strategy.isBlank() ? "balanced" : strategy);
        body.put("items", items);
        return body;
    }

    @GetMapping("/students/{id}/matches/{opportunityId}")
    public MatchResult match(@PathVariable("id") Long studentId,
                             @PathVariable("opportunityId") Long opportunityId) {
        return facade.match(studentId, opportunityId);
    }

    // ---------------------------------------------------------------- applications

    @PostMapping("/applications")
    public ResponseEntity<Application> apply(@RequestBody ApplyRequest request) {
        StudentProfile student = session.currentStudent();
        if (student == null) {
            throw new com.researchmatch.config.ResourceNotFoundException("No demo student available");
        }
        if (request == null || request.opportunityId() == null) {
            throw new com.researchmatch.config.InvalidRequestException("opportunityId is required");
        }
        Application application = facade.submitApplication(student.getId(), request.opportunityId(), request.statement());
        return ResponseEntity.status(HttpStatus.CREATED).body(application);
    }

    @GetMapping("/students/{id}/applications")
    public List<Application> applications(@PathVariable("id") Long studentId) {
        return facade.applicationsForStudent(studentId);
    }

    @PostMapping("/applications/{id}/transition")
    public Application transition(@PathVariable("id") Long applicationId,
                                  @RequestParam("action") String action) {
        return switch (action == null ? "" : action.trim().toLowerCase()) {
            case "shortlist" -> facade.shortlist(applicationId);
            case "interview" -> facade.scheduleInterview(applicationId);
            case "accept" -> facade.accept(applicationId);
            case "reject" -> facade.reject(applicationId);
            default -> throw new com.researchmatch.config.InvalidRequestException("Unknown action: " + action);
        };
    }

    // ---------------------------------------------------------------- opportunities + faculty

    @PostMapping("/opportunities")
    public ResponseEntity<Opportunity> createOpportunity(@RequestBody OpportunityRequest request) {
        FacultyProfile faculty = session.currentFaculty();
        if (faculty == null) {
            throw new com.researchmatch.config.ResourceNotFoundException("No demo faculty available");
        }
        if (request == null) {
            throw new com.researchmatch.config.InvalidRequestException("Request body is required");
        }
        Opportunity opportunity = new OpportunityBuilder()
                .facultyId(faculty.getId())
                .title(request.title())
                .description(request.description())
                .requiredSkills(csv(request.requiredSkills()))
                .preferredSkills(csv(request.preferredSkills()))
                .hoursPerWeek(request.hoursPerWeek())
                .compensationType(request.compensationType())
                .beginnerFriendly(request.beginnerFriendly())
                .maxApplicants(request.maxApplicants())
                .build();
        Opportunity saved = facade.saveOpportunity(opportunity);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/faculties/{id}/opportunities")
    public List<Opportunity> opportunities(@PathVariable("id") Long facultyId) {
        return facade.opportunitiesForFaculty(facultyId);
    }

    @GetMapping("/faculties/{id}/candidates")
    public List<StudentProfile> candidates(@PathVariable("id") Long facultyId) {
        return facade.discoverCandidates(facultyId);
    }

    // ---------------------------------------------------------------- profile + import

    @GetMapping("/profile")
    public StudentProfile profile() {
        StudentProfile student = session.currentStudent();
        if (student == null) {
            throw new com.researchmatch.config.ResourceNotFoundException("No demo student available");
        }
        return student;
    }

    @PostMapping("/profile/import")
    public List<SkillEvidence> importSkills(@RequestParam(value = "source", required = false) String source,
                                            @RequestParam(value = "handle", required = false) String handle) {
        StudentProfile student = session.currentStudent();
        if (student == null) {
            throw new com.researchmatch.config.ResourceNotFoundException("No demo student available");
        }
        return facade.importSkills(student.getId(), source, handle);
    }

    @GetMapping("/import-sources")
    public List<String> importSources() {
        return facade.importSources();
    }

    // ---------------------------------------------------------------- error handling

    @ExceptionHandler(com.researchmatch.config.ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResearchMatchException e) {
        return problem(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler({com.researchmatch.config.InvalidRequestException.class,
            com.researchmatch.config.IllegalStateTransitionException.class,
            com.researchmatch.config.DuplicateEntityException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(ResearchMatchException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e);
    }

    private static ResponseEntity<Map<String, Object>> problem(HttpStatus status, Exception e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", e.getMessage());
        return ResponseEntity.status(status).body(body);
    }

    private static List<String> csv(List<String> values) {
        return values == null ? List.of() : values;
    }

    /** Request body for creating an application. */
    public record ApplyRequest(Long opportunityId, String statement) {
    }

    /** Request body for publishing an opportunity. */
    public record OpportunityRequest(String title, String description,
                                     List<String> requiredSkills, List<String> preferredSkills,
                                     Integer hoursPerWeek, String compensationType,
                                     boolean beginnerFriendly, Integer maxApplicants) {
    }
}

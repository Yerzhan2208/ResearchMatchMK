package com.researchmatch.controllers;

import com.researchmatch.config.ResearchMatchException;
import com.researchmatch.config.ResourceNotFoundException;
import com.researchmatch.factory.OpportunityBuilder;
import com.researchmatch.matching.BalancedMatchStrategy;
import com.researchmatch.matching.BeginnerFriendlyStrategy;
import com.researchmatch.matching.SkillStrictStrategy;
import com.researchmatch.models.Application;
import com.researchmatch.models.FacultyProfile;
import com.researchmatch.models.MatchResult;
import com.researchmatch.models.Opportunity;
import com.researchmatch.models.SkillEvidence;
import com.researchmatch.models.StudentProfile;
import com.researchmatch.repository.ApplicationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Member 4 web controller: renders the Thymeleaf views and delegates every action to {@link ResearchMatchFacade}.
 *
 * <p>Controllers stay thin — they validate at the HTTP edge, translate exceptions into flash messages and
 * redirects, and never touch {@code matching}, {@code pipeline} or repositories directly (the one exception is
 * a read-only {@link ApplicationRepository} used purely to render the professor's pipeline board, which needs
 * applications across many opportunities the per-student facade method cannot provide).
 */
@Controller
@RequestMapping("/")
public class WebController {

    /** All strategies offered in the discovery-feed ranking selector. */
    static final List<String> STRATEGIES = List.of(
            BalancedMatchStrategy.NAME, BeginnerFriendlyStrategy.NAME, SkillStrictStrategy.NAME);

    private final ResearchMatchFacade facade;
    private final DemoRoleSession session;
    private final ApplicationRepository applicationRepository;

    public WebController(ResearchMatchFacade facade,
                         DemoRoleSession session,
                         ApplicationRepository applicationRepository) {
        this.facade = facade;
        this.session = session;
        this.applicationRepository = applicationRepository;
    }

    // ---------------------------------------------------------------- shared

    private void commonAttributes(Model model) {
        model.addAttribute("role", session.getRole());
        model.addAttribute("isStudentView", session.isStudentView());
        FacultyProfile faculty = session.currentFaculty();
        model.addAttribute("currentFaculty", faculty);
        StudentProfile student = session.currentStudent();
        model.addAttribute("currentStudent", student);
    }

    // ---------------------------------------------------------------- landing + role switch

    @GetMapping
    public String home(RedirectAttributes redirect) {
        if (session.isStudentView()) {
            return "redirect:/discover";
        }
        return "redirect:/professor";
    }

    /** One-click demo role switcher. */
    @PostMapping("/switch-role")
    public String switchRole(@RequestParam("role") String role, RedirectAttributes redirect) {
        DemoRoleSession.Role target;
        try {
            target = DemoRoleSession.Role.valueOf(role == null ? "" : role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", "Unknown role: " + role);
            return "redirect:/";
        }
        session.setRole(target);
        redirect.addFlashAttribute("infoMessage",
                "Switched to the " + (target == DemoRoleSession.Role.STUDENT ? "student" : "professor") + " view.");
        return target == DemoRoleSession.Role.STUDENT ? "redirect:/discover" : "redirect:/professor";
    }

    // ---------------------------------------------------------------- student: discovery feed

    @GetMapping("/discover")
    public String discover(@RequestParam(value = "strategy", required = false) String strategy,
                           Model model) {
        commonAttributes(model);
        model.addAttribute("strategy", effectiveStrategy(strategy));
        model.addAttribute("strategies", STRATEGIES);

        StudentProfile student = session.currentStudent();
        List<OpportunityMatchView> feed = new ArrayList<>();
        String feedError = null;
        if (student == null) {
            feedError = "No demo student is available.";
        } else {
            try {
                List<MatchResult> ranked = facade.recommend(student.getId(), strategy);
                for (MatchResult match : ranked) {
                    Opportunity opportunity = safeOpportunity(match.getOpportunityId());
                    if (opportunity != null) {
                        feed.add(new OpportunityMatchView(match, opportunity));
                    }
                }
            } catch (ResearchMatchException e) {
                // Expected domain failure: surface it as an empty error state.
                feedError = e.getMessage();
            } catch (UnsupportedOperationException e) {
                // Member 2's engine is still a TODO on this branch; render it as an error, never as success.
                feedError = e.getMessage();
            }
        }
        model.addAttribute("feed", feed);
        model.addAttribute("feedError", feedError);
        model.addAttribute("applications", applicationsForCurrentStudent(student));
        return "discover";
    }

    @PostMapping("/apply")
    public String apply(@RequestParam(value = "opportunityId", required = false) Long opportunityId,
                        @RequestParam(value = "statement", required = false) String statement,
                        RedirectAttributes redirect) {
        StudentProfile student = session.currentStudent();
        if (student == null) {
            redirect.addFlashAttribute("errorMessage", "No demo student is available to apply.");
            return "redirect:/discover";
        }
        if (opportunityId == null) {
            redirect.addFlashAttribute("errorMessage", "Please choose an opportunity to apply to.");
            return "redirect:/discover";
        }
        if (statement == null || statement.isBlank()) {
            redirect.addFlashAttribute("errorMessage", "A short statement is required to apply.");
            return "redirect:/discover";
        }
        if (statement.length() > 4000) {
            redirect.addFlashAttribute("errorMessage", "Your statement must be 4000 characters or fewer.");
            return "redirect:/discover";
        }
        try {
            facade.submitApplication(student.getId(), opportunityId, statement.trim());
            redirect.addFlashAttribute("successMessage", "Application submitted.");
        } catch (ResearchMatchException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/discover";
    }

    // ---------------------------------------------------------------- student: profile

    @GetMapping("/profile")
    public String profile(Model model) {
        commonAttributes(model);
        StudentProfile student = session.currentStudent();
        if (student != null) {
            model.addAttribute("evidence", facade.evidenceFor(student.getId()));
        } else {
            model.addAttribute("evidence", List.of());
        }
        model.addAttribute("importSources", facade.importSources());
        return "profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam(value = "name", required = false) String name,
                              @RequestParam(value = "email", required = false) String email,
                              @RequestParam(value = "major", required = false) String major,
                              @RequestParam(value = "academicYear", required = false) Integer academicYear,
                              @RequestParam(value = "hoursPerWeek", required = false) Integer hoursPerWeek,
                              @RequestParam(value = "githubUsername", required = false) String githubUsername,
                              @RequestParam(value = "interests", required = false) String interests,
                              @RequestParam(value = "claimedSkills", required = false) String claimedSkills,
                              @RequestParam(value = "searchableByFaculty", required = false, defaultValue = "false")
                              boolean searchableByFaculty,
                              RedirectAttributes redirect) {
        StudentProfile existing = session.currentStudent();
        if (existing == null) {
            redirect.addFlashAttribute("errorMessage", "No demo student is available to edit.");
            return "redirect:/profile";
        }
        StudentProfile toSave = new StudentProfile();
        toSave.setId(existing.getId());
        toSave.setName(name);
        toSave.setEmail(email);
        toSave.setMajor(major);
        toSave.setAcademicYear(academicYear);
        toSave.setHoursPerWeek(hoursPerWeek);
        toSave.setGithubUsername(githubUsername);
        toSave.setInterests(splitCsvSet(interests));
        toSave.setClaimedSkills(splitCsvSet(claimedSkills));
        toSave.setVerifiedSkills(existing.getVerifiedSkills());
        toSave.setSearchableByFaculty(searchableByFaculty);
        try {
            facade.saveStudent(toSave);
            redirect.addFlashAttribute("successMessage", "Profile saved.");
        } catch (ResearchMatchException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/profile/import")
    public String importSkills(@RequestParam(value = "source", required = false) String source,
                               @RequestParam(value = "handle", required = false) String handle,
                               RedirectAttributes redirect) {
        StudentProfile student = session.currentStudent();
        if (student == null) {
            redirect.addFlashAttribute("errorMessage", "No demo student is available for import.");
            return "redirect:/profile";
        }
        try {
            List<SkillEvidence> imported = facade.importSkills(student.getId(), source, handle);
            if (imported.isEmpty()) {
                redirect.addFlashAttribute("infoMessage", "No skills were found for that handle.");
            } else {
                redirect.addFlashAttribute("successMessage", imported.size() + " skill(s) imported.");
            }
        } catch (ResearchMatchException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    // ---------------------------------------------------------------- professor dashboard

    @GetMapping("/professor")
    public String professor(Model model) {
        commonAttributes(model);
        FacultyProfile faculty = session.currentFaculty();
        List<Opportunity> opportunities = faculty == null ? List.of() : facade.opportunitiesForFaculty(faculty.getId());
        model.addAttribute("opportunities", opportunities);
        model.addAttribute("compensationTypes", List.of("VOLUNTEER", "CREDIT", "PAID"));

        List<StudentProfile> candidates = List.of();
        String candidateError = null;
        if (faculty != null) {
            try {
                candidates = facade.discoverCandidates(faculty.getId());
            } catch (ResearchMatchException e) {
                candidateError = e.getMessage();
            }
        }
        model.addAttribute("candidates", candidates);
        model.addAttribute("candidateError", candidateError);
        model.addAttribute("board", pipelineBoard(opportunities));
        return "professor";
    }

    @PostMapping("/opportunities")
    public String createOpportunity(@RequestParam(value = "title", required = false) String title,
                                    @RequestParam(value = "description", required = false) String description,
                                    @RequestParam(value = "requiredSkills", required = false) String requiredSkills,
                                    @RequestParam(value = "preferredSkills", required = false) String preferredSkills,
                                    @RequestParam(value = "hoursPerWeek", required = false) Integer hoursPerWeek,
                                    @RequestParam(value = "compensationType", required = false) String compensationType,
                                    @RequestParam(value = "beginnerFriendly", required = false, defaultValue = "false")
                                    boolean beginnerFriendly,
                                    @RequestParam(value = "maxApplicants", required = false) Integer maxApplicants,
                                    RedirectAttributes redirect) {
        FacultyProfile faculty = session.currentFaculty();
        if (faculty == null) {
            redirect.addFlashAttribute("errorMessage", "No demo faculty is available to post opportunities.");
            return "redirect:/professor";
        }
        try {
            Opportunity opportunity = new OpportunityBuilder()
                    .facultyId(faculty.getId())
                    .title(title)
                    .description(description)
                    .requiredSkills(splitCsv(requiredSkills))
                    .preferredSkills(splitCsv(preferredSkills))
                    .hoursPerWeek(hoursPerWeek)
                    .compensationType(compensationType)
                    .beginnerFriendly(beginnerFriendly)
                    .maxApplicants(maxApplicants)
                    .build();
            facade.saveOpportunity(opportunity);
            redirect.addFlashAttribute("successMessage", "Opportunity published.");
        } catch (ResearchMatchException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/professor";
    }

    @PostMapping("/applications/{id}/transition")
    public String transitionApplication(
            @org.springframework.web.bind.annotation.PathVariable("id") Long applicationId,
            @RequestParam("action") String action,
            RedirectAttributes redirect) {
        try {
            switch (action == null ? "" : action.trim().toLowerCase()) {
                case "shortlist" -> facade.shortlist(applicationId);
                case "interview" -> facade.scheduleInterview(applicationId);
                case "accept" -> facade.accept(applicationId);
                case "reject" -> facade.reject(applicationId);
                default -> throw new com.researchmatch.config.InvalidRequestException(
                        "Unknown transition: " + action);
            }
            redirect.addFlashAttribute("successMessage", "Application updated.");
        } catch (ResearchMatchException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/professor";
    }

    // ---------------------------------------------------------------- helpers

    private String effectiveStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return BalancedMatchStrategy.NAME;
        }
        String trimmed = strategy.trim();
        return STRATEGIES.contains(trimmed) ? trimmed : BalancedMatchStrategy.NAME;
    }

    private Opportunity safeOpportunity(Long opportunityId) {
        if (opportunityId == null) {
            return null;
        }
        try {
            return facade.getOpportunity(opportunityId);
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private List<Application> applicationsForCurrentStudent(StudentProfile student) {
        if (student == null) {
            return List.of();
        }
        return facade.applicationsForStudent(student.getId());
    }

    /**
     * Builds the professor's pipeline board: one column per {@link Application.Status}, each holding the
     * applications of the faculty's opportunities. Pure presentation grouping; no state rules here.
     */
    private Map<String, List<Application>> pipelineBoard(List<Opportunity> opportunities) {
        Map<String, List<Application>> board = new LinkedHashMap<>();
        for (Application.Status status : Application.Status.values()) {
            board.put(status.name(), new ArrayList<>());
        }
        if (opportunities == null || opportunities.isEmpty()) {
            return board;
        }
        for (Opportunity opportunity : opportunities) {
            if (opportunity == null || opportunity.getId() == null) {
                continue;
            }
            List<Application> applications = applicationRepository.findByOpportunityId(opportunity.getId());
            if (applications == null) {
                continue;
            }
            for (Application application : applications) {
                if (application == null || application.getStatus() == null) {
                    continue;
                }
                board.get(application.getStatus().name()).add(application);
            }
        }
        return board;
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static java.util.Set<String> splitCsvSet(String value) {
        return new java.util.LinkedHashSet<>(splitCsv(value));
    }

    /** Exposed for tests / templates that need the strategy label list. */
    public static List<String> strategies() {
        return STRATEGIES;
    }
}

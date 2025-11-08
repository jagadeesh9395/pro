package com.tal.pro.controller;

import com.tal.pro.criteria.ResumeSearchCriteria;
import com.tal.pro.model.Recruiter;
import com.tal.pro.model.Resume;
import com.tal.pro.repository.RecruiterRepository;
import com.tal.pro.service.ResumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/recruiter")
public class RecruiterController {

    @Autowired
    private RecruiterRepository recruiterRepository;

    @Autowired
    private ResumeService resumeService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }
            
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));
            
            // Add all necessary attributes to the model
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            
            // Add roles to the model
            model.addAttribute("isRecruiter", true);
            
            return "recruiter/dashboard";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }
    
//    @Autowired
//    private SearchService searchService;

    @GetMapping("/search-candidates")
    public String searchCandidates(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "location", required = false) String location,
            @RequestParam(value = "skills", required = false) List<String> skills,
            @RequestParam(value = "uploadedBefore", required = false) String uploadedBefore,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Model model, 
            Principal principal) {
        
        try {
            if (principal == null) {
                return "redirect:/auth/login?error=not_authenticated";
            }

            // Get current recruiter
            String username = principal.getName();
            Recruiter recruiter = recruiterRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Recruiter not found"));

            // Create search criteria
            ResumeSearchCriteria criteria = ResumeSearchCriteria.builder()
                    .keyword(query).build();
//                    .city(location)
//                    .state(location)
//                    .uploadedBefore(uploadedBefore)
//                    .build();

            // Set skills if provided
//            if (skills != null && !skills.isEmpty()) {
//                criteria.setProgrammingLanguages(skills);
//                criteria.setFrameworks(skills);
//            }
//
//            // Set individual fields for better search
//            if (query != null) {
//                criteria.setFullName(query);
//                criteria.setCompanyName(query);
//                criteria.setJobTitle(query);
//                criteria.setInstitution(query);
//                criteria.setDegree(query);
//            }

            // Perform search
            List<Resume> searchResults = resumeService.searchResumes(criteria);
            
            // Create pagination
            Pageable pageable = PageRequest.of(page, size);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), searchResults.size());
            Page<Resume> resumePage = new PageImpl<>(
                searchResults.subList(start, end),
                pageable,
                searchResults.size()
            );

            // Add recruiter info
            model.addAttribute("recruiter", recruiter);
            model.addAttribute("currentUser", recruiter);
            model.addAttribute("username", username);
            model.addAttribute("fullName", recruiter.getFullName());
            model.addAttribute("email", recruiter.getEmail());
            model.addAttribute("companyName", recruiter.getCompany());
            model.addAttribute("isRecruiter", true);
            
            // Add search results and pagination info
            model.addAttribute("candidates", resumePage.getContent());
            model.addAttribute("totalItems", resumePage.getTotalElements());
            model.addAttribute("totalPages", resumePage.getTotalPages());
            model.addAttribute("currentPage", page);
            model.addAttribute("pageSize", size);
            model.addAttribute("query", query != null ? query : "");
            model.addAttribute("location", location);
            model.addAttribute("skills", skills);
            
            return "recruiter/search-results";
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/auth/login?error=access_denied";
        }
    }

    @GetMapping("/jobs")
    public String jobs() {
        return "recruiter/jobs";
    }

    @GetMapping("/candidates")
    public String candidates() {
        return "recruiter/candidates";
    }
}

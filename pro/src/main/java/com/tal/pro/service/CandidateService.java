package com.tal.pro.service;

import com.tal.pro.model.Candidate;
import com.tal.pro.security.services.UserDetailsImpl;

import java.util.Optional;

public interface CandidateService {
    Candidate getOrCreateCandidate(UserDetailsImpl userDetails);
    Optional<Candidate> getCandidateById(String id);
    Candidate saveCandidate(Candidate candidate);
}

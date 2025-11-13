package com.tal.pro.service.impl;

import com.tal.pro.model.Candidate;
import com.tal.pro.repository.CandidateRepository;
import com.tal.pro.security.services.UserDetailsImpl;
import com.tal.pro.service.CandidateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CandidateServiceImpl implements CandidateService {

    private final CandidateRepository candidateRepository;

    @Autowired
    public CandidateServiceImpl(CandidateRepository candidateRepository) {
        this.candidateRepository = candidateRepository;
    }

    @Override
    @Transactional
    public Candidate getOrCreateCandidate(UserDetailsImpl userDetails) {
        return candidateRepository.findByEmail(userDetails.getEmail())
                .orElseGet(() -> {
                    Candidate newCandidate = new Candidate();
                    newCandidate.setEmail(userDetails.getEmail());
                    newCandidate.setFullName(userDetails.getUsername());
                    return candidateRepository.save(newCandidate);
                });
    }

    @Override
    public Optional<Candidate> getCandidateById(String id) {
        return candidateRepository.findById(id);
    }

    @Override
    public Candidate saveCandidate(Candidate candidate) {
        return candidateRepository.save(candidate);
    }
}

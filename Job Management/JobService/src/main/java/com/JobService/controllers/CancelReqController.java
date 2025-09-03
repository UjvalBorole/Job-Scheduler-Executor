package com.JobService.controllers;

import com.JobService.entities3.CancelReq;
import com.JobService.services.impl.CancelReqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cancel")
@RequiredArgsConstructor
public class CancelReqController {

    private final CancelReqRepository cancelReqRepository;

    // CREATE or UPDATE Cancel Request
    //http://localhost:8080/cancel
    @PostMapping
    public ResponseEntity<String> createOrUpdate(@RequestBody CancelReq cancelReq) {
        cancelReqRepository.save(cancelReq);
        return ResponseEntity.ok("✅ Cancel request saved with ID: " + cancelReq.getId());
    }

    // GET by ID
    //GET http://localhost:8080/cancel/123
    @GetMapping("/{id}")
    public ResponseEntity<CancelReq> getById(@PathVariable String id) {
        CancelReq req = cancelReqRepository.findById(id);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(req);
    }

    // GET by Name
    //GET http://localhost:8080/cancel/name/TestJob
    @GetMapping("/name/{name}")
    public ResponseEntity<CancelReq> getByName(@PathVariable String name) {
        CancelReq req = cancelReqRepository.findByName(name);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(req);
    }

    // DELETE by ID
    //DELETE http://localhost:8080/cancel/123
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteById(@PathVariable String id) {
        CancelReq req = cancelReqRepository.findById(id);
        if (req == null) {
            return ResponseEntity.notFound().build();
        }
        cancelReqRepository.deleteById(id);
        return ResponseEntity.ok("🗑️ Cancel request with ID " + id + " deleted successfully.");
    }
}

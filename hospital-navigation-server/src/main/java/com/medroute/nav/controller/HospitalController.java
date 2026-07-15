package com.medroute.nav.controller;

import com.medroute.nav.model.Hospital;
import com.medroute.nav.service.DemoGraphService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/hospitals")
public class HospitalController {
    private final DemoGraphService graphService;

    public HospitalController(DemoGraphService graphService) {
        this.graphService = graphService;
    }

    @GetMapping
    public List<Hospital> hospitals() {
        return graphService.hospitals();
    }
}

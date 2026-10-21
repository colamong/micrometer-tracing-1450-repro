package com.example.sampling;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReportController {

    @GetMapping("/reports/{id}")
    public String report(@PathVariable String id) {
        return "report " + id;
    }

}

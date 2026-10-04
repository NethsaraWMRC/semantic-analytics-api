package com.chat.agent.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** What moved, and by how much. Deliberately says nothing about cause. */
@Data
public class DiagnosticResult {

    private String metric;
    private String period;
    private String comparedWith;

    private Double current;
    private Double previous;
    private Double change;
    private Double changePercent;

    private List<Explanation> explanations = new ArrayList<>();

    /** caveats the written answer must pass on */
    private List<String> notes = new ArrayList<>();
}

package com.chat.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One segment and how much of the overall change it accounts for. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Mover {

    private String value;
    private Double current;
    private Double previous;
    private Double change;

    /**
     * This segment's change as a percent of the total change. Can exceed 100 or go negative
     * when segments move in opposite directions, which is correct, not a bug.
     */
    private Double shareOfChange;
}

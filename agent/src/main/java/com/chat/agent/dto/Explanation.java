package com.chat.agent.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** The change broken down by one dimension. */
@Data
public class Explanation {

    private String dimension;
    private List<Mover> movers = new ArrayList<>();

    /** true when the dimension had more values than could be examined */
    private boolean partial;
}

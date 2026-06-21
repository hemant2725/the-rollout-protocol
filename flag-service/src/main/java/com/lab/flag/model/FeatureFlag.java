package com.lab.flag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlag {
    private String name;
    private boolean enabled;
    private int rolloutPercent;
}
package com.example.fast_cv.telegram.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSession {
    private State state = State.START;
    private String fullName;
    private String birthday;
    private String city;
    private String email;
    private String education;
    private String experience;
    private String projects;
    private String hard_skills;
    private String soft_skills;
    private String links;
    private String template;
}

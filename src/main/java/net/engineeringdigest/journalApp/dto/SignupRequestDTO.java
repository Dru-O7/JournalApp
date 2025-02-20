package net.engineeringdigest.journalApp.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignupRequestDTO {
    private String userName;
    private String email;
    private String city;
    private boolean sentimentAnalysis;
    private String password;
}

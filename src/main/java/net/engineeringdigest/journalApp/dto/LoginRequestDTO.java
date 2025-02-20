package net.engineeringdigest.journalApp.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequestDTO {
    private String userName;
    private String password;
}

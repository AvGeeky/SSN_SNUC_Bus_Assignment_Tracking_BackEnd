package com.bustracking.bustrack.entities;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import java.util.UUID;
import lombok.Data;
import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User_sessions {
  private UUID id;
  private String username;
  private String password;
  private String loginType;
  private Integer ttlDays;
  private LocalDateTime createdAt;
  private LocalDateTime expiresAt;

}

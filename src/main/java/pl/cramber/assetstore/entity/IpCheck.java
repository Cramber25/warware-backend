package pl.cramber.assetstore.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "ip_checks")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IpCheck {
    @Id
    private String ip;
    private boolean isProxy;
    private LocalDateTime createdAt = LocalDateTime.now();
}
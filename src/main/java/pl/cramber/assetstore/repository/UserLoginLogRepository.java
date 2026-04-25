package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.UserLoginLog;

import java.util.UUID;

@Repository
public interface UserLoginLogRepository extends JpaRepository<UserLoginLog, UUID> {
}
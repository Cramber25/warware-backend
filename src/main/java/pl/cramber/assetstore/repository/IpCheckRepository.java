package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.cramber.assetstore.entity.IpCheck;

public interface IpCheckRepository extends JpaRepository<IpCheck, String> { }
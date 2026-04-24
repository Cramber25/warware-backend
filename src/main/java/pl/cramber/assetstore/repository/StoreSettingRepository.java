package pl.cramber.assetstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pl.cramber.assetstore.entity.StoreSetting;

@Repository
public interface StoreSettingRepository extends JpaRepository<StoreSetting, String> {
}
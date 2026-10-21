package org.scenarioservice.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.scenarioservice.model.Feature;
import org.scenarioservice.model.FeatureVersion;
import org.scenarioservice.support.ScenarioServiceIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * T7, D10, D14: the "path taken" query compares in the database with the index's expression (ICU root lower case,
 * hashed with md5), so it agrees with {@code uk_features_project_id_path_lower} for AC-1.17's pairs; the partial
 * index ignores archived features; {@code (feature_id, version)} is unique. Every case runs in its own transaction,
 * rolled back afterwards.
 */
@ScenarioServiceIntegrationTest
class FeatureRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-18T09:00:00Z");

    @Autowired
    private FeatureRepository features;

    @Autowired
    private FeatureVersionRepository versions;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @ParameterizedTest(name = "AC-1.17 \"{0}\" then \"{1}\": taken {2}")
    @CsvSource({"features/İzmir.feature, features/i̇zmir.feature, true",
            "features/İzmir.feature, features/izmir.feature, false",
            "features/Ⱟ.feature, features/ⱟ.feature, true",
            "features/checkout/payment.feature, Features/Checkout/PAYMENT.feature, true"})
    @Tag("AC-1.17")
    void thePathTakenQueryAgreesWithTheIndex(String existing, String candidate, boolean taken) {
        inRolledBackTransaction(() -> {
            UUID projectId = UUID.randomUUID();
            Feature first = features.saveAndFlush(feature(projectId, existing, null));

            assertThat(features.isPathTaken(projectId, candidate)).isEqualTo(taken);
            assertThat(features.isPathTakenByAnother(projectId, candidate, first.getId())).isFalse();
            assertThat(features.isPathTaken(UUID.randomUUID(), candidate)).isFalse();
            Throwable insert = catchThrowable(() -> features.saveAndFlush(feature(projectId, candidate, null)));
            assertThat(insert != null).as("the index rejects the second path").isEqualTo(taken);
            if (taken) {
                assertThat(insert).isInstanceOf(DataIntegrityViolationException.class);
            }
        });
    }

    @Test
    @DisplayName("D10: an archived feature's path is free, for the query and for the index")
    void anArchivedPathIsFree() {
        inRolledBackTransaction(() -> {
            UUID projectId = UUID.randomUUID();
            features.saveAndFlush(feature(projectId, "features/address.feature", NOW));

            assertThat(features.isPathTaken(projectId, "features/Address.feature")).isFalse();
            features.saveAndFlush(feature(projectId, "features/Address.feature", null));
            assertThat(features.isPathTaken(projectId, "FEATURES/ADDRESS.feature")).isTrue();
        });
    }

    @Test
    @DisplayName("D9: (feature_id, version) is unique")
    void versionNumbersAreUniquePerFeature() {
        inRolledBackTransaction(() -> {
            Feature feature = features.saveAndFlush(feature(UUID.randomUUID(), "features/a.feature", null));
            versions.saveAndFlush(version(feature.getId(), 1));
            versions.saveAndFlush(version(feature.getId(), 2));

            assertThat(catchThrowable(() -> versions.saveAndFlush(version(feature.getId(), 2))))
                    .isInstanceOf(DataIntegrityViolationException.class);
        });
    }

    private void inRolledBackTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            status.setRollbackOnly();
            work.run();
        });
    }

    private static Feature feature(UUID projectId, String path, Instant archivedAt) {
        UUID user = UUID.randomUUID();
        return Feature.builder().id(UUID.randomUUID()).projectId(projectId).path(path).name("").description("")
                .language("en").currentVersion(1).createdBy(user).updatedBy(user).archivedAt(archivedAt)
                .createdAt(NOW).updatedAt(NOW).build();
    }

    private static FeatureVersion version(UUID featureId, int number) {
        return FeatureVersion.builder().id(UUID.randomUUID()).featureId(featureId).version(number)
                .source("Feature: A\n").contentHash("0".repeat(64)).createdBy(UUID.randomUUID()).createdAt(NOW)
                .build();
    }
}

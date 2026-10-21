package org.scenarioservice.service.impl;

import org.scenarioservice.dto.response.TagResponse;
import org.scenarioservice.repository.TagRepository;
import org.scenarioservice.security.ProjectAccessChecker;
import org.scenarioservice.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/**
 * The tag catalogue (D12): the access check first (D5), then every tag used in an active feature's current version,
 * with its feature count, by name in code-point order. Tags no longer used anywhere don't appear.
 */
@Service
public class TagServiceImpl implements TagService {

    private final ProjectAccessChecker accessChecker;
    private final TagRepository tags;
    private final TransactionTemplate readTransaction;

    public TagServiceImpl(ProjectAccessChecker accessChecker, TagRepository tags,
                          PlatformTransactionManager transactionManager) {
        this.accessChecker = accessChecker;
        this.tags = tags;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    @Override
    public List<TagResponse> catalogue(UUID projectId) {
        accessChecker.requireRead(projectId);
        return readTransaction.execute(status -> tags.findUsage(projectId).stream()
                .map(usage -> new TagResponse(usage.name(), usage.featureCount())).toList());
    }
}

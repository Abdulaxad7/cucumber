package com.example.authservice.repository;

import com.example.authservice.model.OrgMembership;
import com.example.authservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrgMembershipRepository extends JpaRepository<OrgMembership, UUID> {
    List<OrgMembership> findByUser(User user);
}

package com.secondbrain.common.repository;

import com.secondbrain.common.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findByScope(String scope);

    Skill findByName(String name);

    List<Skill> findByTriggersContaining(String trigger);
}

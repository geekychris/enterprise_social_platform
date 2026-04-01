package com.social.app.persistence.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "poll_options")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class PollOptionEntity {

    @Id
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "label", nullable = false, length = 200)
    private String label;

    @Column(name = "sort_order")
    private short sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPollId() { return pollId; }
    public void setPollId(Long pollId) { this.pollId = pollId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public short getSortOrder() { return sortOrder; }
    public void setSortOrder(short sortOrder) { this.sortOrder = sortOrder; }

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
}

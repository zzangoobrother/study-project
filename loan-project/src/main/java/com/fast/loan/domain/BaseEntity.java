package com.fast.loan.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.Column;
import javax.persistence.EntityListeners;
import javax.persistence.MappedSuperclass;
import java.io.Serializable;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity implements Serializable {

    @CreatedDate
    @Column(updatable = false, columnDefinition = "detetime default CURRENT_TIMESTAMP NOT NULL COMMENT '생성일자'")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(columnDefinition = "detetime default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL COMMENT '수정일자'")
    private LocalDateTime updatedAt;

    @Column(columnDefinition = "bit default false NOT NULL COMMENT '이용가능여부'")
    private Boolean isDeleted;
}

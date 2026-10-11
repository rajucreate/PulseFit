package com.pulsefit.notification.repository;

import com.pulsefit.notification.model.Notification;
import com.pulsefit.notification.model.NotificationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByMemberIdOrderByCreatedAtDesc(Long memberId);

  @Query(
      """
      select n from Notification n
      where (cast(:memberId as long) is null or n.memberId = :memberId)
        and (cast(:type as string) is null or n.type = :type)
        and (:status is null or n.status = :status)
      order by n.createdAt desc
      """)
  List<Notification> search(
      @Param("memberId") Long memberId,
      @Param("type") String type,
      @Param("status") NotificationStatus status);
}

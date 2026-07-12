package com.cloudcomment.notification.application;

import com.cloudcomment.notification.domain.OwnerNotificationView;

import java.util.List;

public record OwnerNotificationPage(
    List<OwnerNotificationView> items,
    int page,
    int pageSize,
    long totalItems
) {
    public OwnerNotificationPage {
        items = List.copyOf(items);
    }
}

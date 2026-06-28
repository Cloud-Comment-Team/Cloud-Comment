package com.cloudcomment.site.application;

import com.cloudcomment.site.domain.Site;

import java.util.List;

public record SitePage(
    List<Site> items,
    int page,
    int pageSize,
    long totalItems
) {

    public SitePage {
        items = List.copyOf(items);
    }
}

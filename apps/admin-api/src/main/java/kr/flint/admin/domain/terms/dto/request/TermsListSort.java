package kr.flint.admin.domain.terms.dto.request;

import org.springframework.data.domain.Sort;

public enum TermsListSort {
    VERSION,
    TYPE;

    public Sort toSort(Sort.Direction direction) {
        Sort.Direction effectiveDirection = direction == null ? Sort.Direction.DESC : direction;
        if (this == TYPE) {
            return Sort.by(effectiveDirection, "type")
                .and(Sort.by(Sort.Direction.DESC, "version"))
                .and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return Sort.by(effectiveDirection, "version")
            .and(Sort.by(Sort.Direction.ASC, "type"))
            .and(Sort.by(Sort.Direction.DESC, "id"));
    }
}

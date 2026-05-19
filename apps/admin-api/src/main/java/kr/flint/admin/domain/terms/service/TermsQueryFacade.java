package kr.flint.admin.domain.terms.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import kr.flint.admin.common.AdminAuthorizationService;
import kr.flint.admin.domain.terms.dto.request.TermsListSort;
import kr.flint.terms.domain.Terms;
import kr.flint.terms.domain.TermsType;
import kr.flint.terms.dto.response.TermsRes;
import kr.flint.terms.repository.TermsRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsQueryFacade {

    private final TermsRepository termsRepository;
    private final AdminAuthorizationService adminAuthorizationService;

    public List<TermsRes> getTerms(Long adminId, TermsType type, TermsListSort sortBy, Sort.Direction direction) {
        adminAuthorizationService.validateAdmin(adminId);
        Sort sort = resolveSort(sortBy, direction);
        List<Terms> terms = type == null ? termsRepository.findAll(sort) : termsRepository.findByType(type, sort);
        return terms.stream()
            .map(TermsRes::from)
            .toList();
    }

    private Sort resolveSort(TermsListSort sortBy, Sort.Direction direction) {
        TermsListSort effectiveSortBy = sortBy == null ? TermsListSort.VERSION : sortBy;
        Sort.Direction effectiveDirection = direction == null ? Sort.Direction.DESC : direction;
        return effectiveSortBy.toSort(effectiveDirection);
    }
}

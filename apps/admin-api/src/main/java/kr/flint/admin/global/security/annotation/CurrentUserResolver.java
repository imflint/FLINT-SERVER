package kr.flint.admin.global.security.annotation;

import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import kr.flint.admin.global.security.UserPrincipal;
import kr.flint.auth.exception.AuthErrorCode;
import kr.flint.auth.exception.AuthException;

@Component
public class CurrentUserResolver implements HandlerMethodArgumentResolver {

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(CurrentUser.class)
			&& Long.class.isAssignableFrom(parameter.getParameterType());
	}

	@Override
	public Object resolveArgument(
		MethodParameter parameter,
		ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest,
		WebDataBinderFactory binderFactory
	) {
		CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
			if (annotation != null && annotation.required()) {
				throw new AuthException(AuthErrorCode.UNAUTHORIZED);
			}
			return null;
		}

		Object principal = authentication.getPrincipal();
		if (principal instanceof UserPrincipal userPrincipal) {
			return userPrincipal.userId();
		}

		if (annotation != null && annotation.required()) {
			throw new AuthException(AuthErrorCode.UNAUTHORIZED);
		}
		return null;
	}
}

package kr.flint.api;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static JavaClasses allClasses;
    private static JavaClasses moduleClasses;

    private static final String ROOT_PACKAGE = "kr.flint";
    private static final String API_PACKAGE = "kr.flint.api";
    private static final String SHARED_PACKAGE = "kr.flint.shared";

    private static final String[] DOMAIN_MODULES = {
            "kr.flint.user",
            "kr.flint.auth",
            "kr.flint.content",
            "kr.flint.collection",
            "kr.flint.bookmark",
            "kr.flint.taste",
            "kr.flint.search"
    };

    @BeforeAll
    static void setUp() {
        allClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT_PACKAGE);

        moduleClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(DOMAIN_MODULES);
    }

    @Nested
    @DisplayName("모듈 의존성 규칙")
    class ModuleDependencyRules {

        @Test
        @DisplayName("도메인 모듈은 apps 패키지에 의존하지 않는다")
        void modules_should_not_depend_on_apps() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(toSubPackages(DOMAIN_MODULES))
                    .should().dependOnClassesThat()
                    .resideInAPackage(API_PACKAGE + "..");

            rule.check(allClasses);
        }

        @Test
        @DisplayName("shared 모듈은 도메인 모듈에 의존하지 않는다")
        void shared_should_not_depend_on_domain_modules() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage(SHARED_PACKAGE + "..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(toSubPackages(DOMAIN_MODULES));

            rule.check(allClasses);
        }

        @Test
        @DisplayName("모듈 간 순환 의존이 없어야 한다")
        void no_cyclic_dependencies_between_modules() {
            ArchRule rule = SlicesRuleDefinition.slices()
                    .matching("kr.flint.(*)..")
                    .should().beFreeOfCycles();

            rule.check(allClasses);
        }
    }

    @Nested
    @DisplayName("모듈 캡슐화 규칙")
    class ModuleEncapsulationRules {

        @Test
        @DisplayName("다른 모듈의 domain 패키지에 직접 접근하지 않는다")
        void should_not_access_other_modules_domain_directly() {
            for (String module : DOMAIN_MODULES) {
                String moduleName = extractModuleName(module);
                String domainPackage = module + ".domain..";

                ArchRule rule = noClasses()
                        .that().resideOutsideOfPackage(module + "..")
                        .and().resideOutsideOfPackage(SHARED_PACKAGE + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage(domainPackage)
                        .as(String.format("모듈 외부에서 %s 모듈의 domain 패키지에 직접 접근하면 안 된다", moduleName));

                rule.check(allClasses);
            }
        }

        @Test
        @DisplayName("다른 모듈의 repository 패키지에 직접 접근하지 않는다")
        void should_not_access_other_modules_repository_directly() {
            for (String module : DOMAIN_MODULES) {
                String moduleName = extractModuleName(module);
                String repositoryPackage = module + ".repository..";

                ArchRule rule = noClasses()
                        .that().resideOutsideOfPackage(module + "..")
                        .should().dependOnClassesThat()
                        .resideInAPackage(repositoryPackage)
                        .as(String.format("모듈 외부에서 %s 모듈의 repository 패키지에 직접 접근하면 안 된다", moduleName));

                rule.check(allClasses);
            }
        }
    }

    @Nested
    @DisplayName("레이어드 아키텍처 규칙")
    class LayeredArchitectureRules {

        @Test
        @DisplayName("domain 레이어는 다른 레이어에 의존하지 않는다")
        void domain_should_not_depend_on_other_layers() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(toLayerPackages("domain"))
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(concatArrays(
                            toLayerPackages("service"),
                            toLayerPackages("repository"),
                            toLayerPackages("dto")
                    ))
                    .allowEmptyShould(true);

            rule.check(moduleClasses);
        }

        @Test
        @DisplayName("repository 레이어는 service 레이어에 의존하지 않는다")
        void repository_should_not_depend_on_service() {
            ArchRule rule = noClasses()
                    .that().resideInAnyPackage(toLayerPackages("repository"))
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(toLayerPackages("service"))
                    .allowEmptyShould(true);

            rule.check(moduleClasses);
        }
    }

    @Nested
    @DisplayName("네이밍 컨벤션 규칙")
    class NamingConventionRules {

        @Test
        @DisplayName("Repository 인터페이스는 repository 패키지에 위치한다")
        void repositories_should_be_in_repository_package() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Repository")
                    .should().resideInAPackage("..repository..")
                    .allowEmptyShould(true);

            rule.check(moduleClasses);
        }

        @Test
        @DisplayName("Service 클래스는 service 패키지에 위치한다")
        void services_should_be_in_service_package() {
            ArchRule rule = classes()
                    .that().haveSimpleNameEndingWith("Service")
                    .should().resideInAPackage("..service..")
                    .allowEmptyShould(true);

            rule.check(moduleClasses);
        }

        @Test
        @DisplayName("Exception 클래스는 exception 패키지에 위치한다")
        void exceptions_should_be_in_exception_package() {
            ArchRule rule = classes()
                    .that().areAssignableTo(RuntimeException.class)
                    .and().haveSimpleNameEndingWith("Exception")
                    .should().resideInAPackage("..exception..")
                    .allowEmptyShould(true);

            rule.check(moduleClasses);
        }
    }

    private static String[] toSubPackages(String[] packages) {
        String[] result = new String[packages.length];
        for (int i = 0; i < packages.length; i++) {
            result[i] = packages[i] + "..";
        }
        return result;
    }

    private static String[] toLayerPackages(String layer) {
        String[] result = new String[DOMAIN_MODULES.length];
        for (int i = 0; i < DOMAIN_MODULES.length; i++) {
            result[i] = DOMAIN_MODULES[i] + "." + layer + "..";
        }
        return result;
    }

    @SafeVarargs
    private static String[] concatArrays(String[]... arrays) {
        int totalLength = 0;
        for (String[] array : arrays) {
            totalLength += array.length;
        }
        String[] result = new String[totalLength];
        int index = 0;
        for (String[] array : arrays) {
            for (String s : array) {
                result[index++] = s;
            }
        }
        return result;
    }

    private static String extractModuleName(String packageName) {
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }
}

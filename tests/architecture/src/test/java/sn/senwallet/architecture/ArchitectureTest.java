package sn.senwallet.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "sn.senwallet", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

        @ArchTest
        static final ArchRule domainIsPure = noClasses()
                        .that().resideInAPackage("..domain..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..",
                                        "tools.jackson..");

        @ArchTest
        static final ArchRule sharedKernelIsPure = noClasses()
                        .that().resideInAPackage("sn.senwallet.shared..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..",
                                        "tools.jackson..");

        @ArchTest
        static final ArchRule ledgerDomainIsTheInnermostLayer = noClasses()
                        .that().resideInAPackage("sn.senwallet.ledger.domain..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("..application..", "..infrastructure..");

        @ArchTest
        static final ArchRule applicationDoesNotSeeAdapters = noClasses()
                        .that().resideInAPackage("..application..")
                        .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

        /** The application layer only uses Spring for {@code @Transactional}. */
        @ArchTest
        static final ArchRule applicationOnlyUsesSpringTransactions = noClasses()
                        .that().resideInAPackage("..application..")
                        .should().dependOnClassesThat(resideInAPackage("org.springframework..")
                                        .and(not(resideInAPackage("org.springframework.transaction.."))));

        /** ADR-13: the ledger uses explicit SQL, never JPA. */
        @ArchTest
        static final ArchRule ledgerHasNoJpa = noClasses()
                        .that().resideInAPackage("sn.senwallet.ledger..")
                        .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..");

        @ArchTest
        static final ArchRule contextsAreIsolated = slices()
                        .matching("sn.senwallet.(*)..")
                        .should().notDependOnEachOther()
                        .ignoreDependency(alwaysTrue(), resideInAPackage("sn.senwallet.shared.."));
}

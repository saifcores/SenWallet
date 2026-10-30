package sn.senwallet.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "sn.senwallet", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

        // TODO(M1): drop allowEmptyShould once ledger-domain contains its first
        // classes.
        @ArchTest
        static final ArchRule domainIsPure = noClasses()
                        .that().resideInAPackage("..domain..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..")
                        .allowEmptyShould(true);

        @ArchTest
        static final ArchRule sharedKernelIsPure = noClasses()
                        .that().resideInAPackage("sn.senwallet.shared..")
                        .should().dependOnClassesThat()
                        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml..");

        @ArchTest
        static final ArchRule contextsAreIsolated = slices()
                        .matching("sn.senwallet.(*)..")
                        .should().notDependOnEachOther()
                        .ignoreDependency(alwaysTrue(), resideInAPackage("sn.senwallet.shared.."));
}

package com.rbdip.bookstore.contracts;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ModuleBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.rbdip.bookstore");

    @Test
    void sharedContractsDoNotExposeModuleInternals() {
        noClasses().that().resideInAPackage("com.rbdip.bookstore.contracts..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rbdip.bookstore.order..", "com.rbdip.bookstore.review..",
                        "com.rbdip.bookstore.customer..", "com.rbdip.bookstore.product..")
                .check(CLASSES);
    }

    @Test
    void orderAdapterDoesNotDependOnReviewImplementation() {
        noClasses().that().resideInAPackage("com.rbdip.bookstore.order..")
                .should().dependOnClassesThat().resideInAPackage("com.rbdip.bookstore.review..")
                .check(CLASSES);
    }
}

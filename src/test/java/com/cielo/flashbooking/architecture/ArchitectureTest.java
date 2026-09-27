package com.cielo.flashbooking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.cielo.flashbooking.architecture.fixture.left.Left;
import com.cielo.flashbooking.architecture.fixture.right.Right;
import com.cielo.flashbooking.domain.fixture.ImpureDomain;
import com.cielo.flashbooking.event.fixture.BadEventUseCase;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String ROOT = "com.cielo.flashbooking";
    private static final Set<String> MODULES = Set.of(
            "adapter",
            "application",
            "config",
            "controller",
            "domain",
            "event",
            "inventory",
            "notification",
            "operations",
            "reservation");

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT);
    }

    @Test
    void domainRemainsPure() {
        domainRule().check(productionClasses());
    }

    @Test
    void domainViolationFixtureFails() {
        var result = domainRule().evaluate(new ClassFileImporter().importClasses(ImpureDomain.class));
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString()).contains("ImpureDomain", "ApplicationContext");
    }

    @Test
    void moduleMapCoversProductionClasses() {
        for (JavaClass javaClass : productionClasses()) {
            String name = javaClass.getName();
            if (name.equals(ROOT + ".FlashBookingApplication")) {
                continue;
            }
            assertThat(javaClass.getPackageName())
                    .as("production class %s belongs to one named module", name)
                    .startsWith(ROOT + ".");
            String module =
                    javaClass.getPackageName().substring(ROOT.length() + 1).split("\\.")[0];
            assertThat(module).as("module for %s", name).isIn(MODULES);
        }
    }

    @Test
    void modulesRemainAcyclic() {
        moduleCycleRule(ROOT + ".(*)..").check(productionClasses());
    }

    @Test
    void cycleDetectionFixtureFails() {
        var result = moduleCycleRule(ROOT + ".architecture.fixture.(*)..")
                .evaluate(new ClassFileImporter().importClasses(Left.class, Right.class));
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString()).contains("left", "right");
    }

    @Test
    void innerAndDeliveryCodeAvoidOutboundAdapters() {
        adapterDirectionRule().check(productionClasses());
    }

    @Test
    void adapterViolationFixtureFails() {
        var result = adapterDirectionRule().evaluate(new ClassFileImporter().importClasses(BadEventUseCase.class));
        assertThat(result.hasViolation()).isTrue();
        assertThat(result.getFailureReport().toString()).contains("BadEventUseCase", "JdbcOutboxEventStore");
    }

    private static ArchRule domainRule() {
        return noClasses()
                .that()
                .resideInAPackage(ROOT + ".domain..")
                .should()
                .dependOnClassesThat()
                .resideOutsideOfPackages(ROOT + ".domain..", "java..");
    }

    private static ArchRule adapterDirectionRule() {
        return noClasses()
                .that()
                .resideInAnyPackage(
                        ROOT + ".domain..",
                        ROOT + ".application..",
                        ROOT + ".event..",
                        ROOT + ".reservation..",
                        ROOT + ".inventory..",
                        ROOT + ".notification..",
                        ROOT + ".operations..",
                        ROOT + ".controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage(ROOT + ".adapter..");
    }

    private static ArchRule moduleCycleRule(String packagePattern) {
        return slices().matching(packagePattern).should().beFreeOfCycles();
    }
}

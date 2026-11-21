package com.knowyouramerican.config;

import com.knowyouramerican.background.Background;
import com.knowyouramerican.web.GameController;
import java.time.LocalDate;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Native image: Thymeleaf's SpEL reads model objects through reflection ({@code day.year},
 * {@code bg.imageUrl()}, {@code 'ABCD'.charAt(i)}), which GraalVM strips unless registered.
 *
 * <p><b>Every type a template touches must be listed here.</b> A missing one only fails in the
 * native binary (HTTP 500, {@code MissingReflectionRegistrationError} / "EL1008E property cannot
 * be found"), never in JVM tests — so check new pages with {@code make native} + a smoke run.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(TemplateHints.Registrar.class)
public class TemplateHints {

    static final Class<?>[] TEMPLATE_TYPES = {
            // view models
            Background.class,
            GameController.DemoQuestion.class,
            // JDK types used in expressions
            LocalDate.class,
            String.class,
            // Thymeleaf helpers used in expressions (#lists, iteration status)
            org.thymeleaf.expression.Lists.class,
            org.thymeleaf.standard.expression.Fragment.class,
    };

    static class Registrar implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            for (Class<?> type : TEMPLATE_TYPES) {
                hints.reflection().registerType(type,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_PUBLIC_FIELDS);
            }
            // th:each status variable (it.index, it.count)
            hints.reflection().registerTypeIfPresent(classLoader,
                    "org.thymeleaf.standard.processor.StandardEachTagProcessor",
                    MemberCategory.INVOKE_PUBLIC_METHODS);
            hints.reflection().registerTypeIfPresent(classLoader,
                    "org.thymeleaf.engine.IterationStatusVar",
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        }
    }
}

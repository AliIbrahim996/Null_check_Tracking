package com.aliibrahim.nullcheck.core;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The source languages the analyzer understands. */
public enum Language {

    /**
     * Java source files ({@code .java}). Null checks are comparisons with {@code null} and
     * {@code Objects.isNull(x)} / {@code Objects.nonNull(x)}. A name without a visible declaration (an
     * inherited field or a static import) counts as a member.
     */
    JAVA("Java", List.of("java")),

    /**
     * PHP source files ({@code .php}, {@code .phtml}). Null checks are comparisons with {@code null} using
     * {@code ==}, {@code ===}, {@code !=}, {@code !==} or {@code <>}, and {@code is_null(...)} and
     * {@code isset(...)}. Inline HTML is skipped, {@code elseif} and the {@code and}, {@code or} and
     * {@code xor} keywords are understood, and property declarations count as definitions of
     * {@code $this->property}.
     */
    PHP("PHP", List.of("php", "phtml")),

    /**
     * C++ source and header files, including {@code .h}, which is parsed as C++ since the C++ grammar also
     * accepts nearly all C headers. Null checks are comparisons with {@code nullptr} or {@code NULL}, and
     * implicit checks of pointers and smart pointers such as {@code if (p)} and {@code if (!p)}. Global and
     * namespace variables, and names without a visible declaration, count as members.
     */
    CPP("C++", List.of("cpp", "cc", "cxx", "c++", "hpp", "hh", "hxx", "h")),

    /**
     * C source files ({@code .c}), parsed with the C grammar so that C code using C++ keywords such as
     * {@code new} or {@code class} as names is read correctly. The rules are the same as for {@link #CPP}.
     */
    C("C", List.of("c"));

    private final String label;
    private final List<String> extensions;

    Language(String label, List<String> extensions) {
        this.label = label;
        this.extensions = extensions;
    }

    /**
     * Returns the name of the language for display.
     *
     * @return for example {@code "PHP"}
     */
    public String label() {
        return label;
    }

    /**
     * Returns the file name extensions of the language, without the dot and in lower case.
     *
     * @return for example {@code ["php", "phtml"]}
     */
    public List<String> extensions() {
        return extensions;
    }

    /**
     * Finds the language of a file from its name.
     *
     * @param fileName a file name such as {@code Customer.java}
     * @return the language, or empty if the extension is not known
     */
    public static Optional<Language> forFileName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (Language language : values()) {
            if (language.extensions.contains(extension)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}

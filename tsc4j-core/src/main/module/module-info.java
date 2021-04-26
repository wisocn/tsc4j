import com.github.tsc4j.api.ConfigValueDecoder;
import com.github.tsc4j.core.BeanMapper;
import com.github.tsc4j.core.ReflectiveBeanMapper;
import com.github.tsc4j.core.ConfigSourceCreator
import com.github.tsc4j.core.impl.ClasspathConfigSource;
import com.github.tsc4j.core.impl.FilesConfigSource;
import com.github.tsc4j.core.impl.UrlConfigSource;

module com.github.tsc4j.core {
    requires static lombok;
    requires static java.desktop;
    requires static java.sql;

    requires transitive org.slf4j;

    requires transitive com.github.tsc4j.api;

    /**
     * Export Test package
     */
    exports com.github.tsc4j.test;

    /**
     * Export Core package for implementations
     */
    exports com.github.tsc4j.core;

    uses ConfigValueDecoder;

    uses ConfigSourceCreator;
    provides ConfigSourceCreator with
        ClasspathConfigSource$Builder,
        FilesConfigSource$Builder,
        UrlConfigSource$Builder;

    uses BeanMapper;
    provides BeanMapper with ReflectiveBeanMapper;
}


package com.github.tsc4j.api

import groovy.util.logging.Slf4j
import lombok.NonNull
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Consumer
import java.util.function.Supplier

@Slf4j
@Unroll
class ReloadableSpec extends Specification {
    def "interface default methods should work as expected"() {
        given:
        def value = 'foo string'
        def reloadable = new MyReloadable(value)

        def consumer = { log.info("called with: {}", it) }

        when:
        reloadable.ifPresentAndRegister(consumer)

        then:
        with(reloadable) {
            isPresent()
            !isEmpty()

            ifPresent.size() == 1
            registered.size() == 1
        }
    }

    static class MyReloadable implements Reloadable {
        final def value

        final List<Consumer> ifPresent = []
        final List<Consumer> registered = []

        MyReloadable(def value) {
            this.value = value
        }


        @Override
        boolean isPresent() {
            return value != null
        }

        @Override
        Object get() {
            return value
        }

        @Override
        Object orElse(Object other) {
            null
        }

        @Override
        Object orElseGet(@NonNull Supplier supplier) {
            null
        }

        @Override
        Reloadable ifPresent(Consumer consumer) {
            ifPresent.add(consumer)
            this
        }

        @Override
        Reloadable register(Consumer consumer) {
            registered.add(consumer)
            this
        }

        @Override
        Reloadable onClose(@NonNull Runnable action) {
            this
        }

        @Override
        void close() {
        }
    }
}

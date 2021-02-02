package com.github.tsc4j.core

import groovy.util.logging.Slf4j
import spock.lang.Specification

@Slf4j
class SystemSpec extends Specification {
    def "display environment"() {
        when:
        def s = ''
        System.getenv()
              .sort()
              .forEach({ k, v -> s += "  $k = $v\n" })
        log.info("System environment:\n$s")

        then:
        true
    }

    def "should display JDK info"() {
        when:
        def s = ''
        System.getProperties()
              .sort()
              .forEach({ k, v -> s += "  $k : $v\n" })
        log.info("JDK system properties:\n$s")

        then:
        true
    }
}

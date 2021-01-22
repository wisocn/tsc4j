/*
 * Copyright 2017 - 2019 tsc4j project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.github.tsc4j.core.impl

import com.typesafe.config.Config
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigObject
import com.typesafe.config.ConfigValue
import com.typesafe.config.ConfigValueFactory
import groovy.util.logging.Slf4j
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.security.cert.X509Certificate
import java.sql.Timestamp
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.regex.Pattern

import static com.github.tsc4j.core.impl.Deserializers.*

@Slf4j
@Unroll
class DeserializersSpec extends Specification {

    def "toBoolean('#value') should return #expected"() {
        expect:
        toBoolean(toValue(value)) == expected

        where:
        value    | expected
        null     | false
        ''       | false
        ' '      | false
        'foo'    | false
        'true'   | true
        ' true ' | true
        ' TRUE ' | true
    }

    def "toString('#value') should return '#expected"() {
        expect:
        toString(toValue(value)) == expected

        where:
        value          | expected
        null           | null
        ''             | ''
        ' '            | ' '
        '  Foo ČĆŽŠĐ ' | '  Foo ČĆŽŠĐ '
    }

    def "toFloat('#value') should return '#expected"() {
        expect:
        toFloat(toValue(value)) == expected

        where:
        value     | expected
        '42.2'    | 42.2F
        ' 42.23 ' | 42.23F
    }

    def "toUUID('#value) should return expected result"() {
        given:
        def expected = UUID.fromString('83842790-73e5-4fdf-968d-cd1805e98a4a')

        expect:
        toUuid(toValue(value)) == expected

        where:
        value << [
            "83842790-73e5-4fdf-968d-cd1805e98a4a",
            " 83842790-73e5-4fdf-968d-cd1805e98a4a",
            "83842790-73e5-4fdf-968d-cd1805e98a4a ",
            " 83842790-73e5-4fdf-968d-cd1805e98a4a ",
            " 83842790-73E5-4FDF-968D-CD1805E98A4A ",
        ]
    }

    def "toUUID('#value) should throw"() {
        when:
        toUuid(toValue(value))

        then:
        thrown(IllegalArgumentException)

        where:
        value << [null, '', '  ', '  foobar']
    }

    def "toCharset('#value') should return '#expected'"() {
        expect:
        toCharset(toValue(value)) == expected

        where:
        value          | expected
        'UTF8'         | StandardCharsets.UTF_8
        ' UTF8 '       | StandardCharsets.UTF_8
        ' utf-8 '      | StandardCharsets.UTF_8
        ' utf8 '       | StandardCharsets.UTF_8
        'ISO_8859_1'   | StandardCharsets.ISO_8859_1
        ' iso_8859_1 ' | StandardCharsets.ISO_8859_1
    }

    def "toCharset('#value') should throw"() {
        when:
        toCharset(toValue(value))

        then:
        thrown(IllegalArgumentException)

        where:
        value << [null, '', ' ', 'foobar']
    }

    def "toFile('#value') should return expected"() {
        expect:
        toFile(toValue(value)) == new File(value)

        where:
        value << ['/', '/etc', '/etc/services']
    }

    def "toFile('#value') should throw"() {
        when:
        toFile(toValue(value))

        then:
        thrown(RuntimeException)

        where:
        value << [null]
    }

    def "toPath('#value') should return expected"() {
        expect:
        toPath(toValue(value)) == Paths.get(value)

        where:
        value << ['/', '/etc', '/etc/services']
    }

    def "toPath('#value') should throw"() {
        when:
        toPath(toValue(value))

        then:
        thrown(RuntimeException)

        where:
        value << [null]
    }

    def "toInetAddress('#value) should return #expected"() {
        expect:
        toInetAddress(toValue(value)) == expected

        where:
        value                        | expected
        '193.2.1.66'                 | InetAddress.getByName('193.2.1.66')
        ' 193.2.1.66 '               | InetAddress.getByName('193.2.1.66')
        '2a00:ee0::2'                | InetAddress.getByName('2a00:ee0::2')
        ' 2A00:EE0::2 '              | InetAddress.getByName('2a00:ee0::2')
        ' ::Ffff:193.2.1.66 '        | InetAddress.getByName('193.2.1.66')
        ' 2a00:1450:400d:808::2004 ' | InetAddress.getByName('2a00:1450:400d:808::2004')
        ' 2A00:1450:400D:808::2004 ' | InetAddress.getByName('2a00:1450:400d:808::2004')
    }

    def "toInetAddress('#value') should throw"() {
        when:
        toInetAddress(toValue(value))

        then:
        thrown(IllegalArgumentException)

        where:
        value << [null, 'foobar', 'foo.example.com', 'x.1.2.3', 'f:2.3.4']
    }

    @Requires({ os.linux })
    def "toNetworkInterface('#value') should return"() {
        expect:
        toNetworkInterface(toValue(value)).name == value.toString().trim()

        where:
        value << ['lo', ' lo ']
    }

    def "toNetworkInterface('#value') should throw"() {
        when:
        toNetworkInterface(toValue(value))

        then:
        thrown(IllegalArgumentException)

        where:
        value << [null, ' ', ' foobar ']
    }

    def "toUri('#value') should return #expected"() {
        expect:
        toUri(toValue(value)) == expected

        where:
        value                      | expected
        'http://www.google.com/'   | URI.create('http://www.google.com/')
    }

    def "toUrl('#value') should return #expected"() {
        expect:
        toUrl(toValue(value)) == expected

        where:
        value                      | expected
        'http://www.google.com/'   | URI.create('http://www.google.com/').toURL()
    }

    def "toUrl'#value') should throw"() {
        when:
        toUrl(toValue(value))

        then:
        thrown(Exception)

        where:
        value << [null, '', ' ', 'foo']
    }

    def "toClass('#value') should return: #expected"() {
        expect:
        toClass(toValue(value)) == expected

        where:
        value                | expected
        'java.lang.String'   | String.class
        ' java.lang.String ' | String.class
        'java.lang.Integer'  | Integer.class
    }

    def "toClass('#value') should throw"() {
        when:
        toClass(toValue(value))

        then:
        thrown(Exception)

        where:
        value << [null, '', ' ', 'java.lang.Blah', 'JAVA.LANG.String']
    }

    def "toBytesFromBase64() should return expected result"() {
        given:
        def expected = '''aaaa&?/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
foo  \\nČĆŽŠĐ
  čćžšđ\\t
bar ÆØÅ\\r
æøå
baz
'''

        when:
        def bytes = Deserializers.toBytesFromBase64(toValue(str))
        def newString = new String(bytes, StandardCharsets.UTF_8)

        then:
        newString == expected

        where:
        str << [
            // url-safe base64
            'YWFhYSY_L2FhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQpmb28gIFxuxIzEhsW9xaDEkAogIMSNxIfFvsWhxJFcdApiYXIgw4bDmMOFXHIKw6bDuMOlCmJhego',
            '   YWFhYSY_L2FhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQpmb28gIFxuxIzEhsW9xaDEkAogIMSNxIfFvsWhxJFcdApiYXIgw4bDmMOFXHIKw6bDuMOlCmJhego   ',

            // mime base64 no delimiter
            'YWFhYSY/L2FhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQpmb28gIFxuxIzEhsW9xaDEkAogIMSNxIfFvsWhxJFcdApiYXIgw4bDmMOFXHIKw6bDuMOlCmJhego=',
            '  \nYWFhYSY/L2FhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQpmb28gIFxuxIzEhsW9xaDEkAogIMSNxIfFvsWhxJFcdApiYXIgw4bDmMOFXHIKw6bDuMOlCmJhego=  \n ',

            // normal mime base64
            "    YWFhYSY/L2FhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFh \n" +
                " YWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYQpmb28gIFxuxIzE \n" +
                "  hsW9xaDEkAogIMSNxIfFvsWhxJFcdApiYXIgw4bDmMOFXHIKw6bDuMOlCmJhego=   \n\n\n\n"
        ]
    }

    def "toZonedDateTime()"() {
        given:
        def str = '2019-09-06T02:15:10Z'
        expect:
        toZonedDateTime(toValue(str)) == ZonedDateTime.parse(str)
        toLocalDateTime(toValue(str)) == LocalDateTime.parse(str.replace('Z', ''))
    }

    def "toDate()"() {
        given:
        def str = '2019-09-06T02:15:10Z'

        when:
        def date = toDate(toValue(str))

        then:
        date.getTime() == 1567736110000
    }

    def "toZoneId()"() {
        given:
        def str = 'Europe/London'
        expect:
        toZoneId(toValue(str)) == ZoneId.of(str)
        toTimeZone(toValue(str)) == TimeZone.getTimeZone(str)
    }

    def "toZoneOffset()"() {
        expect:
        toZoneOffset(toValue("+00:00")) == ZoneOffset.UTC
        toZoneOffset(toValue("Z")) == ZoneOffset.UTC
    }

    def "convertersLightbendConfigValue() should return converter registry"() {
        when:
        def registry = Deserializers.convertersLightbendConfigValue()

        then:
        !registry.isEmpty()
        registry.size() == 7

        registry.get(Config.class).isPresent()
        registry.get(ConfigList.class).isPresent()
        registry.get(ConfigObject.class).isPresent()
        registry.get(ConfigValue.class).isPresent()
        registry.get(Duration.class).isPresent()
        registry.get(Period.class).isPresent()
    }

    def "convertersLightbendConfig() should return converter registry"() {
        when:
        def registry = Deserializers.convertersLightbendConfig()

        then:
        !registry.isEmpty()
        registry.size() == 17

        registry.get(Config.class).isPresent()
        registry.get(ConfigList.class).isPresent()
        registry.get(ConfigObject.class).isPresent()
        registry.get(ConfigValue.class).isPresent()
        registry.get(Duration.class).isPresent()
        registry.get(Period.class).isPresent()
        registry.get(String.class).isPresent()
        registry.get(Integer.class).isPresent()
        registry.get(Long.class).isPresent()
        registry.get(Float.class).isPresent()
        registry.get(Double.class).isPresent()
    }

    def "convertersJavaPrimitives() should return converter registry"() {
        given:
        def required = [
            Boolean, boolean.class,
            Short, short.class,
            Integer, int.class,
            Long, long.class,
            Float, float.class,
            Double, double.class,
            Number,
            String, CharSequence,
            byte[].class,
        ]

        def someStr = ' {"foo": "bar" }'

        when:
        def registry = Deserializers.convertersJavaPrimitives()

        then:
        !registry.isEmpty()
        registry.size() == 16

        required.each { assert registry.get(it).isPresent() }

        when:
        def res = registry.get(CharSequence)
                          .map({ it.apply(toValue(someStr)) })
                          .orElse(null)

        then:
        res == someStr
    }

    def "convertersJava() should return converter registry"() {
        given:
        def required = [
            Class, UUID, Pattern, Locale, Currency,
            Charset, File, Path, ByteOrder,
            Date, TimeZone,
            InetAddress, Inet6Address, Inet4Address,
            URI, URL,
            BigInteger, BigDecimal,
            NetworkInterface,
        ]

        when:
        def registry = Deserializers.convertersJava()

        then:
        !registry.isEmpty()
        registry.size() == 17

        required.each { assert registry.get(it).isPresent() }
    }

    def "convertersJavaTime() should return converter registry"() {
        given:
        def required = [
            ZonedDateTime, LocalDateTime,
            LocalDate, LocalTime,
            ZoneId, ZoneOffset,
        ]

        when:
        def registry = Deserializers.convertersJavaTime()

        then:
        !registry.isEmpty()
        registry.size() == 14

        required.each { assert registry.get(it).isPresent() }
    }

    def "convertersJdbc() should return converter registry"() {
        given:
        def required = [java.sql.Time.class, Timestamp]
        when:
        def registry = Deserializers.convertersJdbc()

        then:
        !registry.isEmpty()
        registry.size() == 2

        required.each { assert registry.get(it).isPresent() }
    }

    def "convertersJavaCrypto() should return converter registry"() {
        given:
        def required = [
            X509Certificate
        ]

        when:
        def registry = Deserializers.convertersJavaCrypto()

        then:
        !registry.isEmpty()

        required.each { assert registry.get(it).isPresent() }
    }

    def "parsePem() should return expected value"() {
        given:
        def str = '''
-----BEGIN RSA PRIVATE KeY-----
  MIIBOQIBAAJBANuSFiKbkdXA3wMUa0IxPS9dOLIveL4dcnKtJw6YHy8DZVMzOwv2
YJw+ssFWs5cVjBxVsCx8xqGJUZpZcCpqLC0CAwEAAQJAfwEdNJ9v14hcdteUwxDg
J0lwxgCXgsBdtt9ZPCPZxcLKprEG/7R7ItG/Sgab25W9d4hs+5nmYLDC2+RQJChb
AQIhAPU1ZERswfedc297IbWrQSoLbV/FlX1vM1ScJ+o/NgLdAiEA5TvZTD9XBXU9
MDtVxc0GNusF1eYdN5EaPTKoZ/VbcZECIDPDmK4lM3FdaMARA1XBmFkS3n0ITf2T
2wcyi+6Ud4d1AiAi2hKTkR296rE+4AaOaDmFp/3fI3lVtW3z1/vasmcQwQIgL5rc
FMmFB+0D3hUYGoGg4MBRP1V/SqV094/gB88ucaY=
-----END RSA PRIVATE KEY-----

-----BEGIN RSA PRIVATE KEY-----
Proc-Type: 4,ENCRYPTED
DEK-Info: DES-CBC,870E40CFACB6338A

YhniorJfGfnnI5AyL7eSo84VgFSn2++dr+FLc3l9VRd4JK0kvqmYBTuu0UrpDzPn
CUXdrr4wwP676+s60N2/aSkoNpQF7j09DXkgRkqSQmPddm24a7kFt1TvZUSk+Lbt
FKADUnHtzb/8kxMZQc8tapq3kAXCNBXi55A4Fq9t77hDMXc5FSS7ZD//u1m88EFo
a9c0+xtc+G2wlqQ3DZf57Wz36glzpVY4xyGzuHOGMKJ/+ByZJgXzhspVlY4cL3u1
QjbWBeWya++F//rzDF8q+6UblOYlLbuQ+w7G9ZjKXfr9mJTQfXkNFVAEOWUZMrVZ
ZZDCuV26PPVHmrRv0CSJP1PFXTf5+R1vtpxKGSEgAlPiryU/LIkcMPZ5NajXUMNU
COq5sBQvpHYrCXmoZsMcjI1sXOSGnazc8xuMuTr8k78=
-----END RSA PRIVATE KEY-----

# Blah blah

        -----BEGIN CERTIFICATE-----
           MIIEvjCCA6agAwIBAgIQMmxrpNeLCEQIAAAAABG6zjANBgkqhkiG9w0BAQsFADBC
        MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZpY2VzMRMw


        -----BEGIN CERTIFICATE-----
           MIIEvjCCA6agAwIBAgIQMmxrpNeLCEQIAAAAABG6zjANBgkqhkiG9w0BAQsFADBC
        MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZpY2VzMRMw
        iXKZmfquI+yK7WvjguJgm1n7E7iDRhrkzy1WkDMgrj6bhEZwn9kBiEVBX3dPZ4Wc
        ibJ31LEp/GFQ1ryrNA7Au2Jj
        -----END CERTIFICATE-----
'''
        def value = ConfigValueFactory.fromAnyRef(str)

        when:
        def values = parsePem(value)

        then:
        values.size() == 3

        def first = values[0]
        first.type == 'RSA PRIVATE KEY'
        first.headers.isEmpty()
        first.encoded.startsWith('MIIBOQIBAAJBANuSFiKbkdXA3wMUa0')

        def second = values[1]
        second.type == 'RSA PRIVATE KEY'
        second.headers.size() == 2
        second.encoded.startsWith('YhniorJfGfnnI5AyL7eSo84VgFSn2')

        def third = values[2]
        third.type == 'CERTIFICATE'
        third.headers.isEmpty()
        third.encoded.startsWith('MIIEvjCCA6agAwIBAgIQMmxrpNeLCE')
    }

    def "toX509Certificate() should return expected value"() {
        given:
        // real www.google.com cert
        def certPem = '''
# foo comment
        -----BEGIN CERTIFICATE-----
           MIIEvjCCA6agAwIBAgIQMmxrpNeLCEQIAAAAABG6zjANBgkqhkiG9w0BAQsFADBC
        MQswCQYDVQQGEwJVUzEeMBwGA1UEChMVR29vZ2xlIFRydXN0IFNlcnZpY2VzMRMw
        EQYDVQQDEwpHVFMgQ0EgMU8xMB4XDTE5MDgyMzEwMjIyOFoXDTE5MTEyMTEwMjIy
        OFowaDELMAkGA1UEBhMCVVMxEzARBgNVBAgTCkNhbGlmb3JuaWExFjAUBgNVBAcT
        DU1vdW50YWluIFZpZXcxEzARBgNVBAoTCkdvb2dsZSBMTEMxFzAVBgNVBAMTDnd3
        dy5nb29nbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEhWJg1IbpNRCw
        fbnlm0EiCzvUS9evx+Hp7Qh0AQ/nRbRJ/+cTdnq9RGNda1OcyTG/M2nYS0juqkZV
        Sw/huffJFKOCAlMwggJPMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUEDDAKBggrBgEF
        BQcDATAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQ6u8OySGKLh9CRiXicnHEJwSiV
        ATAfBgNVHSMEGDAWgBSY0fhuEOvPm+xgnxiQG6DrfQn9KzBkBggrBgEFBQcBAQRY
        MFYwJwYIKwYBBQUHMAGGG2h0dHA6Ly9vY3NwLnBraS5nb29nL2d0czFvMTArBggr
        BgEFBQcwAoYfaHR0cDovL3BraS5nb29nL2dzcjIvR1RTMU8xLmNydDAZBgNVHREE
        EjAQgg53d3cuZ29vZ2xlLmNvbTAhBgNVHSAEGjAYMAgGBmeBDAECAjAMBgorBgEE
        AdZ5AgUDMC8GA1UdHwQoMCYwJKAioCCGHmh0dHA6Ly9jcmwucGtpLmdvb2cvR1RT
        MU8xLmNybDCCAQMGCisGAQQB1nkCBAIEgfQEgfEA7wB2AGPy283oO8wszwtyhCdX
        azOkjWF3j711pjixx2hUS9iNAAABbL42qeQAAAQDAEcwRQIgHtHFfacjUKQbSHOZ
        7k9hTedyoODJeUKjwbNOuL84AgECIQDCJJNJVKGdet3UmQHy/G4Or7CoG2txWNXV
        LRjEhJdn8QB1AHR+2oMxrTMQkSGcziVPQnDCv/1eQiAIxjc1eeYQe8xWAAABbL42
        qgoAAAQDAEYwRAIgIKBGIPBRyaPCYE7pAhVT/u+xw/KTaP2c/Pr4+E2/364CIA0G
        dFIDqCk8MXe58CeP1uZOqkrx7niphCOidoJ5TMIoMA0GCSqGSIb3DQEBCwUAA4IB
        AQChUeMbemnGJkJPFpgZt++Ksyafmd9gB+ovq3r8OfR7uM/PRQK7cyPmtO4hOd+g
        w3uk2yXqJXeLove5yuCCqI7QaHLcHC7ekvMsxYN0pYeHg8dZG+qKCR95M1B2H7vO
        aolwG70CKr/Lrm2HOaQuHOl88tT0dSOea34ElWFqWllJYn5ffnDiAXx85X0M/SRK
        i5zWqop4tk2UiYvCNJq/puu4zLMaBZVQNY0bQxLciudZc3MFrFNNl6IcomuDIqYf
        iXKZmfquI+yK7WvjguJgm1n7E7iDRhrkzy1WkDMgrj6bhEZwn9kBiEVBX3dPZ4Wc
        ibJ31LEp/GFQ1ryrNA7Au2Jj
        -----END CERTIFICATE-----
        '''

        when:
        def cert = toX509Certificate(toValue(certPem))
        //log.info("decoded: {}", cert)

        then:
        cert.getSubjectDN().getName() == 'CN=www.google.com, O=Google LLC, L=Mountain View, ST=California, C=US'
        cert.getSubjectAlternativeNames().toList() == [[2, 'www.google.com']]
        cert.getIssuerDN().getName() == 'CN=GTS CA 1O1, O=Google Trust Services, C=US'
    }

    ConfigValue toValue(def o) {
        ConfigValueFactory.fromAnyRef(o)
    }
}

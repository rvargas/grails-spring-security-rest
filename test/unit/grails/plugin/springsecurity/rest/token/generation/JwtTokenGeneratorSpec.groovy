/*
 * Copyright 2013-2015 Alvaro Sanchez-Mariscal <alvaro.sanchezmariscal@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package grails.plugin.springsecurity.rest.token.generation

import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSADecrypter
import com.nimbusds.jwt.EncryptedJWT
import com.nimbusds.jwt.JWT
import com.nimbusds.jwt.JWTParser
import grails.plugin.springsecurity.rest.token.AccessToken
import grails.plugin.springsecurity.rest.token.generation.jwt.DefaultRSAKeyProvider
import grails.plugin.springsecurity.rest.token.generation.jwt.SignedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.storage.jwt.JwtTokenStorageService
import grails.plugin.springsecurity.rest.token.generation.jwt.EncryptedJwtTokenGenerator
import grails.plugin.springsecurity.rest.token.generation.jwt.RSAKeyProvider
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import spock.lang.Specification
import spock.lang.Unroll

class JwtTokenGeneratorSpec extends Specification {

    @Unroll
    void "#jwtTokenGenerator.class.simpleName generates access tokens with refresh tokens that can be rehydrated back"() {
        given:
        UserDetails userDetails = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])
        //jwtTokenGenerator.class.name

        when:
        AccessToken accessToken = jwtTokenGenerator.generateAccessToken(userDetails)

        then:
        accessToken.accessToken
        accessToken.refreshToken

        when:
        UserDetails parsedUserDetails = jwtTokenGenerator.jwtTokenStorageService.loadUserByToken(accessToken.accessToken)

        then:
        parsedUserDetails == userDetails

        where:
        jwtTokenGenerator << [setupSignedJwtTokenGenerator(), setupEncryptedJwtTokenGenerator()]

    }

    @Unroll
    void "refresh tokens generated by #jwtTokenGenerator.class.simpleName doesn't expire"() {
        given:
        UserDetails userDetails = new User('username', 'password', [new SimpleGrantedAuthority('ROLE_USER')])

        when:
        AccessToken accessToken = jwtTokenGenerator.generateAccessToken(userDetails)
        JWT accessTokenJwt = JWTParser.parse(accessToken.accessToken)
        JWT refreshTokenJwt = JWTParser.parse(accessToken.refreshToken)
        [accessTokenJwt, refreshTokenJwt].each { JWT jwt ->
            if (jwt instanceof EncryptedJWT) {
                EncryptedJWT encryptedJWT = jwt as EncryptedJWT
                RSADecrypter decrypter = new RSADecrypter((jwtTokenGenerator as EncryptedJwtTokenGenerator).keyProvider.privateKey)
                encryptedJWT.decrypt(decrypter)
            }
        }

        then:
        accessTokenJwt.JWTClaimsSet.expirationTime
        !refreshTokenJwt.JWTClaimsSet.expirationTime

        where:
        jwtTokenGenerator << [setupSignedJwtTokenGenerator(), setupEncryptedJwtTokenGenerator()]
    }

    private setupSignedJwtTokenGenerator() {
        String secret = 'foobar'*10
        def jwtTokenStorageService = new JwtTokenStorageService(jwtSecret: secret)
        return new SignedJwtTokenGenerator(expiration: 3600, jwtSecret: secret, signer: new MACSigner(secret), jwtTokenStorageService: jwtTokenStorageService)
    }

    private setupEncryptedJwtTokenGenerator() {
        RSAKeyProvider keyProvider = new DefaultRSAKeyProvider()
        def jwtTokenStorageService = new JwtTokenStorageService(keyProvider: keyProvider)
        return new EncryptedJwtTokenGenerator(expiration: 3600, jwtTokenStorageService: jwtTokenStorageService, keyProvider: keyProvider)
    }

}

package maeda.demo.fluxk

import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter

@SpringBootApplication
@EnableAdminServer
class FluxkApplication


fun main(args: Array<String>) {
    runApplication<FluxkApplication>(*args)
}

@Configuration
class SecurityPermitAllConfig : WebSecurityConfigurerAdapter() {
    @Throws(Exception::class)
    override  protected fun configure(http: HttpSecurity) {
        http.authorizeRequests().anyRequest().permitAll()
                .and().csrf().disable()
    }
}
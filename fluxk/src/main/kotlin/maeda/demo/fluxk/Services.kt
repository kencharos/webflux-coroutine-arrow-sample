package maeda.demo.fluxk

import arrow.core.Either
import arrow.data.EitherT
import arrow.effects.ForMonoK
import arrow.effects.MonoK
import arrow.effects.k
import arrow.effects.monok.applicative.applicative
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.lang.Exception
import java.lang.IllegalArgumentException
import arrow.effects.monok.applicative.*

@Service
class HelloService(@Value("\${endpoint.hello}") val endpoint: String) {

    fun getMessage() :Mono<String> = WebClient.create(endpoint).get()
                .retrieve().bodyToMono()

    // Scala だと higherKind 型は直接していできるが、 kotlinだと、 ForXX が必要
    fun getMessageOrError(name:String) :MonoK<Either<Throwable, String>> {
        val mono:Mono<Either<Throwable, String>> = WebClient.create(endpoint).get().retrieve().bodyToMono(String::class.java)
                .map{ Either.cond(!name.contains("error1"), {it!!}, {IllegalArgumentException("error raised from hello service")}) }
        return mono.k()
    }

}


@Service
class WorldService(@Value("\${endpoint.world}") val endpoint: String) {

    fun getMessage() :Mono<String> = WebClient.create(endpoint).get()
                .retrieve().bodyToMono()

    fun getMessageOrError(name:String) :MonoK<Either<Throwable, String>> {
        val mono:Mono<Either<Throwable, String>> = WebClient.create(endpoint).get().retrieve().bodyToMono(String::class.java)
                .map{ Either.cond(!name.contains("error2"), {it!!}, {IllegalArgumentException("error raised from world service")}) }
        return mono.k()
    }
}


@Service
class ExtraService(@Value("\${endpoint.extra}") val endpoint: String) {

    fun getMessage() :Mono<String> = WebClient.create(endpoint).get()
            .retrieve().bodyToMono()

    fun getMessageOrError(name:String) :MonoK<Either<Throwable, String>> {
        val mono:Mono<Either<Throwable, String>> = WebClient.create(endpoint).get().retrieve().bodyToMono(String::class.java)
                .map{ Either.cond(!name.contains("error3"), {it!!}, {IllegalArgumentException("error raised from extra service")}) }
        return mono.k()
    }
}

package maeda.demo.fluxk

import arrow.core.Either
import arrow.core.flatMap
import arrow.data.EitherT
import arrow.data.fix
import arrow.data.value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.async
import kotlinx.coroutines.reactive.awaitFirst

import kotlinx.coroutines.GlobalScope
import arrow.effects.*
import arrow.effects.monok.applicative.applicative
import arrow.effects.monok.monad.monad
import arrow.effects.monok.monadDefer.monadDefer
import arrow.instances.either.traverse.traverse
import arrow.instances.monad
import arrow.typeclasses.*
import org.springframework.http.ResponseEntity
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono


@RestController
class ApiController(val hello:HelloService, val world:WorldService , val extra:ExtraService) {



    @GetMapping("seq_rx")
    fun rxSampleSeq():Mono<String> {
        val h:Mono<String> = hello.getMessage()
        val w:Mono<String> = world.getMessage()
        val e:Mono<String> = extra.getMessage()

        return h.flatMap { _h ->
            w.flatMap { _w ->
                e.map { _h + _w + it} } }

    }

    @GetMapping("par_rx")
    fun rxSamplePar():Mono<String> {
        val h = hello.getMessage()
        val w = world.getMessage()
        val e = extra.getMessage()

        return Mono.zip(h, w)
            .map { it.t1 + " " + it.t2 }
            .flatMap { hw ->
                e.map{hw + it}}

    }

    @GetMapping("seq")
    fun seqWithCoroutine() = GlobalScope.mono{
        // awaitFirst で、 Rxを コルーチンに
        val h:String = hello.getMessage().awaitFirst()
        val w:String = world.getMessage().awaitFirst()
        val e:String = extra.getMessage().awaitFirst()
        "$h $w $e"
    }


    @GetMapping("par")
    fun parWithCoroutine() = GlobalScope.mono{
        // start hello and world tasks in parallel.
        val hDeffered = async{hello.getMessage().awaitFirst()}
        val wDeffered = async{world.getMessage().awaitFirst()}

        // join 2 tasks
        val h:String = hDeffered.await()
        val w:String = wDeffered.await()
        // start extra
        val e = extra.getMessage().awaitFirst()

        h + " "+ w + " "  + e
    }



    @GetMapping("monad_seq")
    fun seqWithMonad():Mono<String> = MonoK.monad().binding {
            // K() is buidler from Mono to MonoK. MonoK is Monad of Mono in Arrow.
            // bind() is flatMap as Coroutine.
            val h:String = hello.getMessage().k().bind()
            val w:String = world.getMessage().k().bind()
            val e:String = extra.getMessage().k().bind()
            h + w + e
        }.value()


    @GetMapping("monad_par")
    fun parWithMonad():Mono<String> = MonoK.monad().binding {
        /**
         *  h┐
         *   ├ e -> Hello world !!!
         *  w┘
         */
        val h = hello.getMessage()
        val w = world.getMessage()
        val hw = Mono.zip(h, w).map { it.t1 + it.t2 }.k().bind()
        val e = extra.getMessage().k().bind()
        hw + e
    }.value()


    @GetMapping("monad_par2")
    fun monadPar2_misstake():Mono<String> = MonoK.monad().binding {
        val hMonoK = hello.getMessage().k()
        val wMonoK = world.getMessage().k()
        // ココは並列にならない。monok は MonadかつApplicative のため、 mapは flatMapになるので。
        val hw = MonoK.applicative().tupled(hMonoK, wMonoK).map { it.a + it.b }.bind()
        val e = extra.getMessage().k().bind()
        hw + e
    }.value()



    @GetMapping("monad_error")
    fun handlingMonokEither(@RequestParam("query") query:String):Mono<ResponseEntity<String>> {


        val resEither = MonoK.monad().binding {
            val hEither:Either<Throwable, String> = hello.getMessageOrError(query).bind()
            val wEither:Either<Throwable, String> = world.getMessageOrError(query).bind()

            hEither.flatMap { h -> wEither.map{w -> h + w}}
        }.value()

        return  resEither.map { when(it){
            is Either.Right -> ResponseEntity.ok().body(it.b)
            is Either.Left -> ResponseEntity.badRequest().body(it.a.message)
        } }

    }

    @GetMapping("monad_error_seq")
    fun monadTransformSeq(@RequestParam("query") query:String):Mono<ResponseEntity<String>> {
        // ここでは EitherT をモナドとして合成できる。どこかで、Eitherがエラーになったらその場で終了。
        // 直列パターン
        val compositeResult = EitherT.monad<ForMonoK, Throwable>(MonoK.monad()).binding {
            val he = EitherT(hello.getMessageOrError(query)).bind()
            val we = EitherT(world.getMessageOrError(query)).bind()
            val ee = EitherT(extra.getMessageOrError(query)).bind()

            he + we + ee
        }

        // 最終的な戻り値生成のため、EitherT から Mono<Either> へ変換。
        val res:Mono<Either<Throwable, String>> = compositeResult.fix().value().value()

        return  res.map { when(it){
            is Either.Right -> ResponseEntity.ok().body(it.b)
            is Either.Left -> ResponseEntity.badRequest().body(it.a.message)
        } }
    }


    @GetMapping("monad_error_par")
    fun monadTransformPar(@RequestParam("query") query:String):Mono<ResponseEntity<String>> {
        // ここでは EitherT をモナドとして合成できる。どこかで、Eitherがエラーになったらその場で終了。
        // 一部並列パターン。少し面倒。
        val compositeResult = EitherT.monad<ForMonoK, Throwable>(MonoK.monad()).binding {
            val he = hello.getMessageOrError(query)
            val we = world.getMessageOrError(query)
            // 並列化は、MonoK -> Monoの変換があるので少し面倒。
            val hwe = Mono.zip(he.mono, we.mono).map{ it.t1.flatMap { s1 -> it.t2.map { s2 -> s1 + s2 } }}
            val hwek = EitherT(hwe.k()).bind()
            val ee = EitherT(extra.getMessageOrError(query)).bind()

            hwek + ee
        }

        // 最終的な戻り値生成のため、EitherT から Mono<Either> へ変換。
        val res:Mono<Either<Throwable, String>> = compositeResult.fix().value().value()

        return  res.map { when(it){
            is Either.Right -> ResponseEntity.ok().body(it.b)
            is Either.Left -> ResponseEntity.badRequest().body(it.a.message)
        } }
    }

}

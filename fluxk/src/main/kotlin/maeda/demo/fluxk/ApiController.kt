package maeda.demo.fluxk

import arrow.core.Either
import arrow.core.fix
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
import arrow.instances.either.monad.monad
import arrow.instances.either.monadError.monadError
import arrow.instances.monad
import arrow.typeclasses.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Mono
import reactor.core.publisher.Hooks


@RestController
class ApiController(val helloApi:HelloApi, val worldApi:WorldApi, val exclamationApi:ExclamationApi) {




    @GetMapping("seq_rx")
    fun rxSampleSeq():Mono<String> {
        Hooks.onOperatorDebug()
        val h:Mono<String> = helloApi.callApi()
        val w:Mono<String> = worldApi.callApi()
        val e:Mono<String> = exclamationApi.callApi()


        return h.flatMap { _h ->
            w.flatMap { _w ->
                e.map { _h + _w + it} } }.log()

    }

    @GetMapping("par_rx")
    fun rxSamplePar():Mono<String> {
        Hooks.onOperatorDebug()
        val h = helloApi.callApi()
        val w = worldApi.callApi()
        val e = exclamationApi.callApi()

        return Mono.zip(h, w)
            .map { it.t1 + " " + it.t2 }
            .flatMap { hw ->
                e.map{hw + it}}.log()

    }

    @GetMapping("seq")
    fun seqWithCoroutine():Mono<String> = GlobalScope.mono {
            // awaitFirst で、 Rxを コルーチンに
            val hello: String = helloApi.callApi().awaitFirst()
            val world: String = worldApi.callApi().awaitFirst()
            val exclamation: String = exclamationApi.callApi().awaitFirst()
            "$hello $world $exclamation"
    }


    @GetMapping("par")
    fun parWithCoroutine():Mono<String> = GlobalScope.mono {
        // start helloApi and worldApi tasks in parallel.
        val helloDeferred = async { helloApi.callApi().awaitFirst() }
        val worldDeferred = async { worldApi.callApi().awaitFirst() }

        // join 2 tasks
        val hello: String = helloDeferred.await()
        val world: String = worldDeferred.await()
        // start exclamationApi
        val exclamation = exclamationApi.callApi().awaitFirst()

    "$hello $world $exclamation"
    }


    @GetMapping("monad_seq")
    fun seqWithMonad():Mono<String> = MonoK.monad().binding {
        // K() is buidler from Mono to MonoK. MonoK is Monad of Mono in Arrow.
        // bind() is flatMap as Coroutine.
        val h: String = helloApi.callApi().k().bind()
        val w: String = worldApi.callApi().k().bind()
        val e: String = exclamationApi.callApi().k().bind()
        h + w + e
    }.value()

        @GetMapping("monad_par")
        fun parWithMonad():Mono<String> = MonoK.monad().binding {
            /**
             *  h┐
             *   ├ e -> Hello worldApi !!!
             *  w┘
             */
            val hMono = helloApi.callApi()
            val wMono = worldApi.callApi()
            // 並列化は Mono zip に頼る
            val helloWorld = Mono.zip(hMono, wMono).map { it.t1 + it.t2 }.k().bind()
            val exclamation = exclamationApi.callApi().k().bind()
             "$helloWorld $exclamation"
        }.value().log()

        @GetMapping("monad_par2")
        fun monadPar2_misstake():Mono<String> = MonoK.monad().binding {
            val hMonoK = helloApi.callApi().k()
            val wMonoK = worldApi.callApi().k()
            Hooks.onOperatorDebug()
            // ココは並列にならない。monok は MonadかつApplicative のため、 mapは flatMapになるので。
            val hw = MonoK.applicative().tupled(hMonoK, wMonoK).map { it.a + it.b }.bind()
            val e = exclamationApi.callApi().k().bind()
            hw + e
        }.value()



        @GetMapping("monad_error")
        fun handlingMonokEither(@RequestParam("query") query:String):Mono<ResponseEntity<String>> {

            val resEither = MonoK.monad().binding {
                val helloEither:Either<Throwable, String> = helloApi.callApiOrError(query).bind()
                val worldEither:Either<Throwable, String> = worldApi.callApiOrError(query).bind()
                val exclamationEither:Either<Throwable, String> = exclamationApi.callApiOrError(query).bind()

                 // モナドにモナドがダブってしまう
                Either.monadError<Throwable>().binding {
                    val hello:String = helloEither.bind()
                    val world:String = worldEither.bind()
                    val exclamation:String = exclamationEither.bind()
                    "$hello $world $exclamation"
                }.fix()
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
                val hello = EitherT(helloApi.callApiOrError(query)).bind()
                val world = EitherT(worldApi.callApiOrError(query)).bind()
                val exclamation = EitherT(exclamationApi.callApiOrError(query)).bind()

                "$hello $world $exclamation"
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
                val he = helloApi.callApiOrError(query)
                val we = worldApi.callApiOrError(query)
                // 並列化は、MonoK -> Monoの変換があるので少し面倒。
                val hwe = Mono.zip(he.mono, we.mono).map{ it.t1.flatMap { s1 -> it.t2.map { s2 -> s1 + s2 } }}
                val hwek = EitherT(hwe.k()).bind()
                val ee = EitherT(exclamationApi.callApiOrError(query)).bind()

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

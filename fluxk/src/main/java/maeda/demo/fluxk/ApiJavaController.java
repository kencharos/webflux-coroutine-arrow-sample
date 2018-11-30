package maeda.demo.fluxk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ApiJavaController {

    @Autowired
    HelloApi helloApi;
    @Autowired
    WorldApi worldApi;
    @Autowired
    ExclamationApi exclamationApi;

    @GetMapping("/java/rx_seq")
    public Mono<String> Seq() { // APIの逐次実行
        Mono<String> hello = helloApi.callApi();
        Mono<String> world = worldApi.callApi();
        Mono<String> exclamation = exclamationApi.callApi();

        return hello
                .flatMap(h -> world
                        .flatMap(w -> exclamation
                                .map(e -> h + w + e)));
    }


    @GetMapping("/java/rx_seq")
    public Mono<String> Par() { // APIの一部並列実行
        Mono<String> hello = helloApi.callApi();
        Mono<String> world = worldApi.callApi();
        Mono<String> exclamation = exclamationApi.callApi();

        return Mono.zip(hello, world)
                .flatMap(hw -> exclamation
                        .map(e -> hw.getT1() + hw.getT2() + e));
    }

}

package maeda.demo.server;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class SampleController {


    @GetMapping("api1")
    public String api1() throws Exception{
        System.out.println(LocalDateTime.now() + " api1 calling");
        Thread.sleep(500);
        return "hello";
    }
    @GetMapping("api2")
    public String api2() throws Exception{
        System.out.println(LocalDateTime.now() + " api2 calling");
        Thread.sleep(500);
        return "world";
    }
    @GetMapping("api3")
    public String api3() throws Exception{
        System.out.println(LocalDateTime.now() + " api3 calling");
        Thread.sleep(500);
        return "!!!";
    }



}

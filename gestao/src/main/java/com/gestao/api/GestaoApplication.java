package com.gestao.api;

import com.gen.core.tools.GeneratorEntity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.gestao.api",   
        "com.gen.core"      
})
public class GestaoApplication {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = SpringApplication.run(GestaoApplication.class, args);

        new GeneratorEntity().initializeEntities(context.getEnvironment());
    }
}

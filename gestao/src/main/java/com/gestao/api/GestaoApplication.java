package com.gestao.api;

import com.gen.core.tools.GeneratorEntity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.gestao",          // API atual
        "com.gen.core",        // seu core (filter, utils, dao)
        "com.gen.common"       // caso use módulos compartilhados
})
public class GestaoApplication {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext context = SpringApplication.run(GestaoApplication.class, args);

        new GeneratorEntity().initializeEntities(context.getEnvironment());
    }
}

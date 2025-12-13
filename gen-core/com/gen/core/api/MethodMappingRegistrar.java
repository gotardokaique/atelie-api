package com.gen.core.api;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

@Component
public class MethodMappingRegistrar {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @PostConstruct
    public void registerCustomMappings() {

        Map<String, Object> beans = context.getBeansWithAnnotation(EndpointMapping.class);

        for (Object bean : beans.values()) {

            Class<?> clazz = bean.getClass();
            EndpointMapping base = clazz.getAnnotation(EndpointMapping.class);

            String basePath = base.value();

            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(MethodMapping.class)) continue;

                MethodMapping mm = method.getAnnotation(MethodMapping.class);

                String fullPath = basePath + mm.path();

                // Converte seu enum HttpMethod para RequestMethod do Spring
                RequestMethod springMethod = RequestMethod.valueOf(mm.type());

                RequestMappingInfo info = RequestMappingInfo
                        .paths(fullPath)
                        .methods(springMethod)
                        .build();

                handlerMapping.registerMapping(info, bean, method);
            }
        }
    }
}

package com.example.extension.handler;

import com.example.extension.annotation.HttpMethod;
import com.example.extension.condition.CustomMethodCondition;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.condition.RequestCondition;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;

@Component
public class CustomRequestMappingHandlerMapping extends RequestMappingHandlerMapping {

    @Override
    protected RequestCondition<?> getCustomMethodCondition(Method method) {
        HttpMethod annotation = AnnotationUtils.findAnnotation(method, HttpMethod.class);

        if (annotation != null) {
            return new CustomMethodCondition(annotation.value());
        }

        return null;
    }
}

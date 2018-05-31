package it.nexbit.cuba.dynamiccolumns.web.components;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InjectDynamicColumnsAction {
    boolean dynamicCaption() default true;
}

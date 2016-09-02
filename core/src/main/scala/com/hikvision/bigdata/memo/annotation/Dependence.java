package com.hikvision.bigdata.memo.annotation;

import java.lang.annotation.*;

/**
 * Created by dengchangchun on 2016/8/17.
 */
@Documented
@Target({ElementType.TYPE,ElementType.FIELD,ElementType.CONSTRUCTOR,ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Dependence {

    String project() default "Spark";

    String module() default "core";

    String clazz() default "None";

}

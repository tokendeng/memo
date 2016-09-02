package com.hikvision.bigdata.memo.annotation;

import java.lang.annotation.*;

/**
 * Created by dengchangchun on 2016/8/17.
 */
@Documented
@Target({ElementType.TYPE,ElementType.FIELD,ElementType.CONSTRUCTOR,ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface SimilarAs {

    String clazz() default "None";

    String method() default "None";
}

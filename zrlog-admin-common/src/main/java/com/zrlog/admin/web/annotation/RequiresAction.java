package com.zrlog.admin.web.annotation;

import com.zrlog.data.security.AccountAction;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresAction {
    AccountAction value();
    /** Stable key in the frontend access.endpointDescriptions locale catalogue. */
    String descriptionKey();
    /** Additional actions checked by the service when the payload changes publication or membership rank. */
    AccountAction[] conditional() default {};
    /** Resource IDs in query parameters are checked in addition to the route action. JSON writes check inside services. */
    boolean articleQuery() default false;
    boolean articleIds() default false;
}

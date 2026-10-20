package com.zrlog.admin.web.controller.api;

import com.hibegin.http.HttpMethod;
import com.hibegin.http.annotation.RequestMethod;
import com.hibegin.http.annotation.ResponseBody;
import com.zrlog.admin.business.rest.response.AdminPageDataResponse;
import com.zrlog.admin.business.security.AccessModels;
import com.zrlog.admin.business.service.AdminPageDataFactory;
import com.zrlog.admin.web.annotation.RequiresAction;
import com.zrlog.admin.web.security.AdminRequestIdentity;
import com.zrlog.common.controller.BaseController;
import com.zrlog.data.security.AccountAction;
import jakarta.inject.Inject;

import java.util.*;

public class AccessController extends BaseController {
    private final AdminRequestIdentity requestIdentity;
    private final AdminPageDataFactory pages;

    @Inject
    public AccessController(
            AdminPageDataFactory pages, AdminRequestIdentity requestIdentity) {
        this.requestIdentity = requestIdentity;

        this.pages = pages;
    }

    @ResponseBody
    @RequestMethod(method = HttpMethod.GET)
    @RequiresAction(value = AccountAction.PERMISSION_READ, descriptionKey = "permission.inspect")
    public AdminPageDataResponse<AccessModels.Page> index() {
        var actor = requestIdentity.current();
        AccessModels.Page page = new AccessModels.Page();
        page.currentRole = actor.getRole();
        page.roles = List.of("owner", "admin", "editor", "author", "contributor");
        page.actions = new ArrayList<>();
        for (AccountAction action : AccountAction.values()) {
            AccessModels.Action item = new AccessModels.Action();
            item.id = action.getId();
            item.scope = action.getScope();
            item.roles = new ArrayList<>(action.getRoles());
            item.routes = new ArrayList<>();
            item.routeDetails = new ArrayList<>();
            request.getRequestConfig().getRouter().getRouterMap().forEach((route, method) -> {
                RequiresAction binding = method.getAnnotation(RequiresAction.class);
                if (binding != null && (binding.value() == action || Arrays.asList(binding.conditional()).contains(action)) && route.contains("admin")) {
                    item.routes.add(route);
                    AccessModels.Route detail = new AccessModels.Route();
                    detail.path = route;
                    detail.descriptionKey = binding.descriptionKey();
                    item.routeDetails.add(detail);
                }
            });
            Collections.sort(item.routes);
            item.routeDetails.sort(Comparator.comparing(route -> route.path));
            page.actions.add(item);
        }
        return new AdminPageDataResponse<>(page, "", pages.metadata(actor, request.getUri()));
    }
}

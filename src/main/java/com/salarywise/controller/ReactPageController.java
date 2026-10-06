package com.salarywise.controller;

import com.salarywise.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.bind.annotation.*;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;
import java.util.Set;

/**
 * React bridge: the existing Spring/Thymeleaf templates remain the source of
 * truth for markup and calculations. React requests the rendered page body and
 * owns navigation/state on the client. This deliberately keeps the original
 * CSS and HTML unchanged so the React version looks identical.
 */
@RestController
@RequestMapping("/api")
public class ReactPageController {

    @Autowired private FinanceController finance;
    @Autowired private AuthController auth;
    @Autowired private SettingsController settings;
    @Autowired private SpringTemplateEngine templateEngine;

    private static final Set<String> ALLOWED = Set.of(
            "index","login","signup","onboarding","dashboard","transactions",
            "analytics","monthly-plan","planning","budget","reports","settings"
    );

    @GetMapping("/session")
    public ResponseEntity<?> session(HttpSession session) {
        User u = (User) session.getAttribute("loggedInUser");
        if (u == null) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "name", u.getName() == null ? "" : u.getName(),
                "onboardingComplete", u.isOnboardingComplete()
        ));
    }

    @GetMapping("/render/{page}")
    public ResponseEntity<String> render(@PathVariable String page,
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         HttpSession session) {
        if (!ALLOWED.contains(page)) {
            return ResponseEntity.badRequest().body("Unknown page");
        }

        ExtendedModelMap model = new ExtendedModelMap();
        String view;

        switch (page) {
            case "index" -> view = auth.home();
            case "login" -> view = auth.loginPage(session);
            case "signup" -> view = auth.signup(session);
            case "onboarding" -> view = finance.onboarding(session, model);
            case "dashboard" -> view = finance.dashboard(session, model);
            case "transactions" -> view = finance.transactions(session, model);
            case "analytics" -> view = finance.analytics(session, model);
            case "monthly-plan" -> view = finance.monthlyPlan(session, model);
            case "planning" -> view = finance.planning(session, model);
            case "budget" -> view = finance.budget(session, model);
            case "reports" -> view = finance.reports(session, model);
            case "settings" -> view = settings.settings(session, model);
            default -> throw new IllegalStateException("Unsupported page");
        }

        if (view.startsWith("redirect:")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(view);
        }

        // Thymeleaf 3.1 (used by Spring Boot 3.5.x) removed the old
        // WebContext(HttpServletRequest, HttpServletResponse, ServletContext, ...)
        // constructor. The templates in this project only require the model
        // variables, so use the supported context API.
        Context context = new Context(request.getLocale(), model.asMap());
        String html = templateEngine.process(view, context);

        // React only needs the original body markup. Keeping the exact body
        // avoids changing the visual component structure.
        int bodyStart = html.indexOf("<body");
        int openEnd = bodyStart >= 0 ? html.indexOf(">", bodyStart) : -1;
        int bodyEnd = html.lastIndexOf("</body>");
        if (openEnd >= 0 && bodyEnd > openEnd) {
            html = html.substring(openEnd + 1, bodyEnd);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}

package io.aetheris.orchestrator.operator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static io.aetheris.orchestrator.operator.BrowserTypes.*;
import static io.aetheris.orchestrator.operator.SiteSkillTypes.*;

@Service
public class SiteSkillCatalogService {

    private final Set<String> physicallyValidatedSkillIds;
    private final Map<String, SiteSkillDescriptor> skills;

    public SiteSkillCatalogService(@Value("${aetheris.browser.validated-site-skills:}") String validatedSkills) {
        this.physicallyValidatedSkillIds = parseValidatedSkills(validatedSkills);
        this.skills = buildCatalog();
    }

    public List<SiteSkillDescriptor> skills() {
        return List.copyOf(skills.values());
    }

    public SiteSkillDescriptor getRequired(String skillId) {
        if (skillId == null || skillId.isBlank()) throw new IllegalArgumentException("skillId is required");
        SiteSkillDescriptor skill = skills.get(skillId.trim().toLowerCase(Locale.ROOT));
        if (skill == null) throw new NoSuchElementException("Unknown site skill: " + skillId);
        return skill;
    }

    public List<BrowserAction> compileActions(SiteSkillDescriptor skill, Map<String, String> parameters) {
        Map<String, String> params = parameters == null ? Map.of() : parameters;
        validateRequiredParameters(skill, params);
        return switch (skill.id()) {
            case "linkedin.profile.inspect" -> linkedinInspect(params);
            case "linkedin.profile.update-about" -> linkedinUpdateAbout(params);
            case "vercel.deployment.inspect" -> vercelInspect(params);
            case "vercel.deployment.trigger" -> vercelTrigger(params);
            default -> throw new IllegalStateException("No compiler is registered for site skill: " + skill.id());
        };
    }

    public List<String> missingValueRefs(SiteSkillDescriptor skill, Map<String, String> valuesByRef) {
        Set<String> present = valuesByRef == null ? Set.of() : valuesByRef.keySet();
        return skill.requiredValueRefs().stream().filter(ref -> !present.contains(ref)).sorted().toList();
    }

    private Map<String, SiteSkillDescriptor> buildCatalog() {
        Map<String, SiteSkillDescriptor> catalog = new LinkedHashMap<>();
        add(catalog, new SiteSkillDescriptor(
                "linkedin.profile.inspect",
                "LinkedIn profile inspection",
                "LinkedIn",
                SiteSkillKind.OBSERVE,
                Set.of("linkedin.com"),
                true,
                isValidated("linkedin.profile.inspect"),
                Set.of("targetUrl"),
                Set.of(),
                List.of("Record the final LinkedIn URL.", "Capture bounded profile text and screenshot-hash evidence."),
                "Repository template only until the exact LinkedIn flow is validated on the owner PC."));
        add(catalog, new SiteSkillDescriptor(
                "linkedin.profile.update-about",
                "LinkedIn About-section update",
                "LinkedIn",
                SiteSkillKind.MUTATE,
                Set.of("linkedin.com"),
                true,
                isValidated("linkedin.profile.update-about"),
                Set.of("targetUrl"),
                Set.of("linkedin.about"),
                List.of("Capture pre-change evidence.", "Require owner approval before mutation.", "Capture post-save screenshot hash and bounded page text before claiming success."),
                "Selectors are conservative repository templates and must be physically validated before this skill may execute."));
        add(catalog, new SiteSkillDescriptor(
                "vercel.deployment.inspect",
                "Vercel deployment inspection",
                "Vercel",
                SiteSkillKind.OBSERVE,
                Set.of("vercel.com"),
                true,
                isValidated("vercel.deployment.inspect"),
                Set.of("targetUrl"),
                Set.of(),
                List.of("Record the final Vercel URL.", "Capture bounded deployment text and screenshot-hash evidence."),
                "Repository template only until the exact Vercel dashboard flow is validated on the owner PC."));
        add(catalog, new SiteSkillDescriptor(
                "vercel.deployment.trigger",
                "Vercel deployment trigger",
                "Vercel",
                SiteSkillKind.MUTATE,
                Set.of("vercel.com"),
                true,
                isValidated("vercel.deployment.trigger"),
                Set.of("targetUrl"),
                Set.of(),
                List.of("Capture pre-deploy evidence.", "Require owner approval before triggering deployment.", "Capture post-action screenshot hash and deployment-status text before claiming success."),
                "No blind retry is allowed for deployment mutations; the UI selector must be physically validated first."));
        return Map.copyOf(catalog);
    }

    private void add(Map<String, SiteSkillDescriptor> catalog, SiteSkillDescriptor skill) {
        catalog.put(skill.id(), skill);
    }

    private List<BrowserAction> linkedinInspect(Map<String, String> params) {
        return List.of(
                action("open-profile", BrowserActionType.NAVIGATE, required(params, "targetUrl"), "", "", BrowserEffect.OBSERVE, "Open the requested LinkedIn profile"),
                action("profile-text", BrowserActionType.EXTRACT_TEXT, "", selector(params, "contentSelector", "main"), "", BrowserEffect.OBSERVE, "Extract bounded profile text"),
                action("profile-evidence", BrowserActionType.SCREENSHOT, "", "", "", BrowserEffect.OBSERVE, "Capture screenshot-hash evidence")
        );
    }

    private List<BrowserAction> linkedinUpdateAbout(Map<String, String> params) {
        List<BrowserAction> actions = new ArrayList<>();
        actions.add(action("open-profile", BrowserActionType.NAVIGATE, required(params, "targetUrl"), "", "", BrowserEffect.OBSERVE, "Open the requested LinkedIn profile"));
        actions.add(action("before-change", BrowserActionType.SCREENSHOT, "", "", "", BrowserEffect.OBSERVE, "Capture pre-change evidence"));
        actions.add(action("open-about-editor", BrowserActionType.CLICK, "", selector(params, "editSelector", "button[aria-label*='Edit']"), "", BrowserEffect.EXTERNAL_CHANGE, "Open the About editor"));
        actions.add(action("write-about", BrowserActionType.TYPE, "", selector(params, "fieldSelector", "textarea"), "linkedin.about", BrowserEffect.LOCAL_DRAFT, "Populate About text from an ephemeral value reference"));
        actions.add(action("save-about", BrowserActionType.CLICK, "", selector(params, "saveSelector", "button[type='submit']"), "", BrowserEffect.EXTERNAL_CHANGE, "Save the About update"));
        actions.add(action("after-change", BrowserActionType.SCREENSHOT, "", "", "", BrowserEffect.OBSERVE, "Capture post-change screenshot hash"));
        actions.add(action("verify-about", BrowserActionType.EXTRACT_TEXT, "", selector(params, "contentSelector", "main"), "", BrowserEffect.OBSERVE, "Extract bounded post-change text for verification"));
        return List.copyOf(actions);
    }

    private List<BrowserAction> vercelInspect(Map<String, String> params) {
        return List.of(
                action("open-deployment", BrowserActionType.NAVIGATE, required(params, "targetUrl"), "", "", BrowserEffect.OBSERVE, "Open the requested Vercel deployment page"),
                action("deployment-text", BrowserActionType.EXTRACT_TEXT, "", selector(params, "contentSelector", "main"), "", BrowserEffect.OBSERVE, "Extract bounded deployment text"),
                action("deployment-evidence", BrowserActionType.SCREENSHOT, "", "", "", BrowserEffect.OBSERVE, "Capture screenshot-hash evidence")
        );
    }

    private List<BrowserAction> vercelTrigger(Map<String, String> params) {
        return List.of(
                action("open-deployment", BrowserActionType.NAVIGATE, required(params, "targetUrl"), "", "", BrowserEffect.OBSERVE, "Open the requested Vercel deployment page"),
                action("before-deploy", BrowserActionType.SCREENSHOT, "", "", "", BrowserEffect.OBSERVE, "Capture pre-deploy evidence"),
                action("trigger-deploy", BrowserActionType.CLICK, "", selector(params, "deploySelector", "button[data-testid='deploy-button']"), "", BrowserEffect.EXTERNAL_CHANGE, "Trigger the deployment action"),
                new BrowserAction("settle", BrowserActionType.WAIT, "", "", "", "", BrowserEffect.OBSERVE, "Allow bounded UI settlement", 2),
                action("after-deploy", BrowserActionType.SCREENSHOT, "", "", "", BrowserEffect.OBSERVE, "Capture post-action screenshot hash"),
                action("verify-deploy", BrowserActionType.EXTRACT_TEXT, "", selector(params, "statusSelector", "main"), "", BrowserEffect.OBSERVE, "Extract deployment status text for verification")
        );
    }

    private BrowserAction action(String id, BrowserActionType type, String url, String selector, String valueRef, BrowserEffect effect, String description) {
        return new BrowserAction(id, type, url, selector, valueRef, "", effect, description, null);
    }

    private void validateRequiredParameters(SiteSkillDescriptor skill, Map<String, String> parameters) {
        List<String> missing = skill.requiredParameters().stream()
                .filter(key -> parameters.get(key) == null || parameters.get(key).isBlank())
                .sorted()
                .toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("Missing required site-skill parameters: " + String.join(", ", missing));
    }

    private String required(Map<String, String> parameters, String key) {
        String value = parameters.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing required site-skill parameter: " + key);
        return value.trim();
    }

    private String selector(Map<String, String> parameters, String key, String fallback) {
        String value = parameters.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean isValidated(String skillId) {
        return physicallyValidatedSkillIds.contains(skillId.toLowerCase(Locale.ROOT));
    }

    private Set<String> parseValidatedSkills(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .forEach(values::add);
        return Set.copyOf(values);
    }
}

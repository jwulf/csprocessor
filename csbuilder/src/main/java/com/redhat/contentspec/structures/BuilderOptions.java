package com.redhat.contentspec.structures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuilderOptions {

	private boolean ignoreErrors = false;
	private List<String> injectionTypes = new ArrayList<String>();
	private boolean injection = true;
	private boolean permissive = false;
	private Map<String, String> overrides = new HashMap<String, String>();
	private boolean injectBugzillaLinks = true;
	private boolean allowEmptySections = false;
	
	public boolean getIgnoreErrors() {
		return ignoreErrors;
	}
	
	public void setIgnoreErrors(Boolean ignoreErrors) {
		this.ignoreErrors = ignoreErrors;
	}
	
	public List<String> getInjectionTypes() {
		return injectionTypes;
	}
	
	public void setInjectionTypes(List<String> injectionTypes) {
		this.injectionTypes = injectionTypes;
	}
	
	public boolean getInjection() {
		return injection;
	}
	
	public void setInjection(Boolean injection) {
		this.injection = injection;
	}
	
	public boolean getPermissive() {
		return permissive;
	}
	
	public void setPermissive(Boolean permissive) {
		this.permissive = permissive;
	}

	public Map<String, String> getOverrides() {
		return overrides;
	}

	public void setOverrides(Map<String, String> overrides) {
		this.overrides = overrides;
	}

	public boolean getInjectBugzillaLinks() {
		return injectBugzillaLinks;
	}

	public void setInjectBugzillaLinks(Boolean injectBugzillaLinks) {
		this.injectBugzillaLinks = injectBugzillaLinks;
	}

	public boolean isAllowEmptySections() {
		return allowEmptySections;
	}

	public void setAllowEmptySections(boolean allowEmptySections) {
		this.allowEmptySections = allowEmptySections;
	}
}